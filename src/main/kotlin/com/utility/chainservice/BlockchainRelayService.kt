package com.utility.chainservice

import com.utility.chainservice.models.OperationGasCost
import com.utility.chainservice.models.TransactionResult
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.web3j.crypto.Credentials
import org.web3j.crypto.RawTransaction
import org.web3j.crypto.TransactionDecoder
import org.web3j.protocol.Web3j
import org.web3j.protocol.core.DefaultBlockParameterName
import org.web3j.protocol.core.methods.request.Transaction
import org.web3j.protocol.core.methods.response.TransactionReceipt
import org.web3j.tx.gas.ContractGasProvider
import org.web3j.utils.Numeric
import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode

@Service
class BlockchainRelayService(
    private val web3j: Web3j,
    private val relayerCredentials: Credentials,
    private val gasProvider: ContractGasProvider,
    private val chainId: Long,
    private val blockchainProperties: BlockchainProperties
) {

    private val logger = LoggerFactory.getLogger(BlockchainRelayService::class.java)

    suspend fun relayTransaction(signedTransactionHex: String): TransactionResult {
        return try {
            logger.info("Relaying transaction: ${signedTransactionHex.substring(0, 20)}...")

            val decodedTx = TransactionDecoder.decode(signedTransactionHex)
            
            val nonce = web3j.ethGetTransactionCount(
                relayerCredentials.address,
                DefaultBlockParameterName.PENDING
            ).send().transactionCount

            val gasPrice = gasProvider.gasPrice
            val gasLimit = gasProvider.gasLimit

            val rawTransaction = RawTransaction.createTransaction(
                nonce,
                gasPrice,
                gasLimit,
                decodedTx.to,
                decodedTx.value,
                decodedTx.data
            )

            val signedTransaction = org.web3j.crypto.TransactionEncoder.signMessage(
                rawTransaction,
                chainId,
                relayerCredentials
            )

            val transactionHash = web3j.ethSendRawTransaction(
                Numeric.toHexString(signedTransaction)
            ).send()

            if (transactionHash.hasError()) {
                logger.error("Transaction relay failed: ${transactionHash.error.message}")
                return TransactionResult(
                    success = false,
                    transactionHash = null,
                    error = transactionHash.error.message
                )
            }

            val txHash = transactionHash.transactionHash
            logger.info("Transaction relayed successfully: $txHash")

            val receipt = waitForTransactionReceipt(txHash)
            if (receipt?.isStatusOK == true) {
                TransactionResult(
                    success = true,
                    transactionHash = txHash
                )
            } else {
                TransactionResult(
                    success = false,
                    transactionHash = txHash,
                    error = "Transaction failed on blockchain"
                )
            }

        } catch (e: Exception) {
            logger.error("Error relaying transaction", e)
            TransactionResult(
                success = false,
                transactionHash = null,
                error = e.message ?: "Unknown error occurred"
            )
        }
    }

    suspend fun processTransactionWithGasTransfer(
        userWalletAddress: String, 
        signedTransactionHex: String, 
        fallbackGasOperation: String
    ): TransactionResult {
        return try {
            logger.info("Processing transaction with gas transfer for user: $userWalletAddress")

            // Extract exact transaction cost from user's signed transaction
            val decodedTx = TransactionDecoder.decode(signedTransactionHex)
            
            // SECURITY: Validate gas limit against on-chain estimation before funding
            val gasValidationResult = validateGasLimit(decodedTx, blockchainProperties.gas.validationTolerancePercent)
            if (!gasValidationResult.success) {
                logger.error("Gas validation failed: ${gasValidationResult.error}")
                return TransactionResult(
                    success = false,
                    transactionHash = null,
                    error = "Gas validation failed: ${gasValidationResult.error}",
                    contractAddress = decodedTx.to
                )
            }
            
            val userGasLimit = decodedTx.gasLimit
            val transactionValue = decodedTx.value ?: BigInteger.ZERO
            
            // Calculate exact gas cost based on transaction type
            val gasCost = try {
                // Legacy transaction: gasPrice * gasLimit
                decodedTx.gasPrice.multiply(userGasLimit)
            } catch (e: UnsupportedOperationException) {
                // EIP-1559 transaction: need to access the underlying transaction
                val transaction = decodedTx.transaction
                if (transaction is org.web3j.crypto.transaction.type.Transaction1559) {
                    transaction.maxFeePerGas.multiply(userGasLimit)
                } else {
                    // Fallback: use our gas provider estimate
                    logger.warn("Could not determine gas cost from transaction, using fallback estimate")
                    val fallbackGasPrice = gasProvider.getGasPrice(fallbackGasOperation)
                    fallbackGasPrice.multiply(userGasLimit)
                }
            }
            
            // Total amount needed = gas cost + transaction value
            val totalAmountNeeded = gasCost.add(transactionValue)
            
            logger.info("Transaction requires: gasLimit=$userGasLimit, gasCost=$gasCost wei, transactionValue=$transactionValue wei, totalNeeded=$totalAmountNeeded wei")

            // Check user's current AVAX balance
            val currentBalance = web3j.ethGetBalance(userWalletAddress, DefaultBlockParameterName.LATEST).send().balance
            
            // Only transfer if user doesn't have enough for the entire transaction
            if (currentBalance < totalAmountNeeded) {
                val amountNeeded = totalAmountNeeded.subtract(currentBalance)
                logger.info("User has $currentBalance wei, needs $totalAmountNeeded wei, transferring $amountNeeded wei")
                
                val gasTransferResult = transferGasToUser(userWalletAddress, amountNeeded)
                if (!gasTransferResult.success) {
                    logger.error("Failed to transfer gas to user: ${gasTransferResult.error}")
                    return TransactionResult(
                        success = false,
                        transactionHash = null,
                        error = "Failed to transfer gas to user: ${gasTransferResult.error}",
                        contractAddress = decodedTx.to
                    )
                }
            } else {
                logger.info("User already has sufficient balance ($currentBalance wei >= $totalAmountNeeded wei), skipping transfer")
            }

            // Forward the original signed transaction unchanged
            val transactionHash = web3j.ethSendRawTransaction(signedTransactionHex).send()

            if (transactionHash.hasError()) {
                logger.error("Transaction forwarding failed: ${transactionHash.error.message}")
                return TransactionResult(
                    success = false,
                    transactionHash = null,
                    error = transactionHash.error.message,
                    contractAddress = decodedTx.to
                )
            }

            val txHash = transactionHash.transactionHash
            logger.info("Transaction forwarded successfully: $txHash")

            val receipt = waitForTransactionReceipt(txHash)
            if (receipt?.isStatusOK == true) {
                TransactionResult(
                    success = true,
                    transactionHash = txHash,
                    contractAddress = decodedTx.to
                )
            } else {
                TransactionResult(
                    success = false,
                    transactionHash = txHash,
                    error = "Transaction failed on blockchain",
                    contractAddress = decodedTx.to
                )
            }

        } catch (e: Exception) {
            logger.error("Error processing transaction with gas transfer", e)
            TransactionResult(
                success = false,
                transactionHash = null,
                error = e.message ?: "Unknown error occurred",
                contractAddress = null
            )
        }
    }

    suspend fun transferGasToUser(userAddress: String, gasAmount: BigInteger): TransactionResult {
        return try {
            logger.info("Transferring $gasAmount wei to user: $userAddress")
            
            val nonce = web3j.ethGetTransactionCount(
                relayerCredentials.address,
                DefaultBlockParameterName.PENDING
            ).send().transactionCount
            
            val gasPrice = gasProvider.gasPrice
            val gasLimit = BigInteger.valueOf(21000) // Standard gas limit for ETH transfer

            val rawTransaction = RawTransaction.createEtherTransaction(
                nonce,
                gasPrice,
                gasLimit,
                userAddress,
                gasAmount
            )

            val signedTransaction = org.web3j.crypto.TransactionEncoder.signMessage(
                rawTransaction,
                chainId,
                relayerCredentials
            )

            val transactionHash = web3j.ethSendRawTransaction(
                Numeric.toHexString(signedTransaction)
            ).send()

            if (transactionHash.hasError()) {
                logger.error("Gas transfer failed: ${transactionHash.error.message}")
                return TransactionResult(
                    success = false,
                    transactionHash = null,
                    error = transactionHash.error.message
                )
            }

            val txHash = transactionHash.transactionHash
            logger.info("Gas transfer transaction sent: $txHash")

            val receipt = waitForTransactionReceipt(txHash)
            if (receipt?.isStatusOK == true) {
                logger.info("Gas transferred successfully to user")
                TransactionResult(
                    success = true,
                    transactionHash = txHash
                )
            } else {
                TransactionResult(
                    success = false,
                    transactionHash = txHash,
                    error = "Gas transfer transaction failed"
                )
            }

        } catch (e: Exception) {
            logger.error("Error transferring gas to user", e)
            TransactionResult(
                success = false,
                transactionHash = null,
                error = e.message ?: "Unknown error occurred"
            )
        }
    }

    suspend fun waitForTransactionReceipt(transactionHash: String): TransactionReceipt? {
        return try {
            var attempts = 0
            val maxAttempts = 30

            while (attempts < maxAttempts) {
                val receiptResponse = web3j.ethGetTransactionReceipt(transactionHash).send()
                val receipt = receiptResponse.result

                if (receipt != null) {
                    return receipt
                }

                Thread.sleep(2000)
                attempts++
            }

            logger.warn("Transaction receipt not found after $maxAttempts attempts for tx: $transactionHash")
            null
        } catch (e: Exception) {
            logger.error("Error waiting for transaction receipt", e)
            null
        }
    }

    fun getOperationGasCosts(operations: List<Pair<String, String>>): List<OperationGasCost> {
        return operations.map { (operation, gasFunction) ->
            val gasLimit = gasProvider.getGasLimit(gasFunction).toLong()
            val gasPrice = gasProvider.getGasPrice(gasFunction)
            val totalCostWei = gasPrice.multiply(BigInteger.valueOf(gasLimit))
            val totalCostAvax = weiToAvax(totalCostWei)

            OperationGasCost(
                operation = operation,
                gasLimit = gasLimit,
                gasPriceWei = gasPrice,
                totalCostWei = totalCostWei,
                totalCostAvax = totalCostAvax
            )
        }
    }

    private fun weiToAvax(wei: BigInteger): String {
        val weiDecimal = BigDecimal(wei)
        val avaxDecimal = weiDecimal.divide(BigDecimal("1000000000000000000"), 18, RoundingMode.HALF_UP)
        return avaxDecimal.stripTrailingZeros().toPlainString()
    }

    suspend fun validateGasLimit(decodedTx: RawTransaction, maxTolerancePercent: Int = 50): TransactionResult {
        return try {
            logger.debug("Validating gas limit for transaction to: ${decodedTx.to}")
            
            // Create Transaction object for gas estimation
            val transaction = Transaction.createFunctionCallTransaction(
                relayerCredentials.address,  // Use relayer as from address for estimation
                null,                        // nonce not needed for estimation
                null,                        // gasPrice not needed for estimation  
                null,                        // gasLimit will be estimated
                decodedTx.to,
                decodedTx.value,
                decodedTx.data
            )

            // Get on-chain gas estimate
            val estimateResponse = web3j.ethEstimateGas(transaction).send()
            if (estimateResponse.hasError()) {
                logger.error("Gas estimation failed: ${estimateResponse.error.message}")
                return TransactionResult(
                    success = false,
                    transactionHash = null,
                    error = "Gas estimation failed: ${estimateResponse.error.message}"
                )
            }

            val estimatedGas = estimateResponse.amountUsed
            val userProvidedGas = decodedTx.gasLimit
            
            // Get user's gas price from transaction
            val userGasPrice = try {
                decodedTx.gasPrice
            } catch (e: UnsupportedOperationException) {
                // EIP-1559 transaction
                val transaction = decodedTx.transaction
                if (transaction is org.web3j.crypto.transaction.type.Transaction1559) {
                    transaction.maxFeePerGas
                } else {
                    gasProvider.gasPrice // Fallback
                }
            }
            
            // Calculate total cost limit
            val totalCost = userProvidedGas.multiply(userGasPrice)
            val maxAllowedCost = BigInteger.valueOf(blockchainProperties.gas.maxGasCostWei)
            
            // Calculate tolerance - allow user to provide up to X% more gas than estimated
            val maxAllowedGas = estimatedGas.multiply(BigInteger.valueOf(100 + maxTolerancePercent.toLong())).divide(BigInteger.valueOf(100))
            
            logger.info("Gas validation: estimated=$estimatedGas, userProvided=$userProvidedGas, maxAllowed=$maxAllowedGas, totalCost=$totalCost, maxCost=$maxAllowedCost")

            // Check total cost first (hard limit)
            if (totalCost > maxAllowedCost) {
                logger.warn("Transaction exceeds maximum cost: $totalCost > $maxAllowedCost wei")
                return TransactionResult(
                    success = false,
                    transactionHash = null,
                    error = "Transaction cost too high: $totalCost wei, maximum allowed $maxAllowedCost wei"
                )
            }

            if (userProvidedGas > maxAllowedGas) {
                logger.warn("User provided excessive gas limit: $userProvidedGas > $maxAllowedGas (${maxTolerancePercent}% tolerance)")
                return TransactionResult(
                    success = false,
                    transactionHash = null,
                    error = "Gas limit too high: provided $userProvidedGas, maximum allowed $maxAllowedGas (estimated: $estimatedGas)"
                )
            }

            if (userProvidedGas < estimatedGas) {
                logger.warn("User provided insufficient gas limit: $userProvidedGas < $estimatedGas")
                return TransactionResult(
                    success = false,
                    transactionHash = null,
                    error = "Gas limit too low: provided $userProvidedGas, minimum required $estimatedGas"
                )
            }

            logger.debug("Gas limit validation passed")
            TransactionResult(success = true, transactionHash = null)
            
        } catch (e: Exception) {
            logger.error("Error validating gas limit", e)
            TransactionResult(
                success = false,
                transactionHash = null,
                error = "Gas validation failed: ${e.message}"
            )
        }
    }
}