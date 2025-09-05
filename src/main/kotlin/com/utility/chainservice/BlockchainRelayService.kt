package com.utility.chainservice

import com.utility.chainservice.models.OperationGasCost
import com.utility.chainservice.models.TransactionResult
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.context.request.ServletRequestAttributes
import org.web3j.crypto.Credentials
import org.web3j.crypto.RawTransaction
import org.web3j.crypto.SignedRawTransaction
import org.web3j.crypto.TransactionDecoder
import org.web3j.protocol.Web3j
import org.web3j.protocol.core.DefaultBlockParameterName
import org.web3j.protocol.core.methods.request.Transaction
import org.web3j.protocol.core.methods.response.TransactionReceipt
import org.web3j.tx.gas.ContractGasProvider
import org.web3j.utils.Numeric
import com.utility.chainservice.contracts.GasPayerContract
import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode

@Service
class BlockchainRelayService(
    private val web3j: Web3j,
    private val gasProvider: ContractGasProvider,
    private val chainId: Long,
    private val blockchainProperties: BlockchainProperties
) {


    private val logger = LoggerFactory.getLogger(BlockchainRelayService::class.java)

    suspend fun relayTransaction(signedTransactionHex: String): TransactionResult {
        logger.error("relayTransaction() method is disabled - this method spends relayer funds and breaks user transaction signatures")
        return TransactionResult(
            success = false,
            transactionHash = null,
            error = "relayTransaction() is disabled - use processTransactionWithGasTransfer() instead"
        )
        
        /* DISABLED - This method was dangerous:
        return try {
            logger.info("Relaying transaction: ${signedTransactionHex.substring(0, 20)}...")

            val txInfo = decodeTransactionWithSender(signedTransactionHex, "")
            val decodedTx = txInfo.transaction
            
            val nonce = web3j.ethGetTransactionCount(
                "0x0000000000000000000000000000000000000000", // disabled method
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
                null // disabled method
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
        */
    }

    suspend fun processTransactionWithGasTransfer(
        userWalletAddress: String, 
        signedTransactionHex: String, 
        operationName: String,
        expectedGasLimit: BigInteger = BigInteger.ZERO,
        clientCredentials: Credentials? = null
    ): TransactionResult {
        return try {
            logger.info("Processing transaction with gas transfer for user: $userWalletAddress, operation: $operationName")

            // Extract transaction details and sender address in one operation
            val txInfo = decodeTransactionWithSender(signedTransactionHex, userWalletAddress)
            val decodedTx = txInfo.transaction
            val actualWalletAddress = txInfo.senderAddress
            
            // SECURITY: Validate gas limits against maximum allowed costs before funding
            val gasValidationResult = validateGasLimits(decodedTx, operationName, expectedGasLimit)
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
            val baseGasCost = try {
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
                    val fallbackGasPrice = gasProvider.getGasPrice(operationName)
                    fallbackGasPrice.multiply(userGasLimit)
                }
            }
            
            // Apply price multiplier to ensure user has enough funds when transaction is executed
            val gasCost = baseGasCost.multiply(
                BigInteger.valueOf((blockchainProperties.gas.priceMultiplier * 100).toLong())
            ).divide(BigInteger.valueOf(100))
            
            logger.debug("Gas cost calculation: baseGasCost=$baseGasCost, priceMultiplier=${blockchainProperties.gas.priceMultiplier}, adjustedGasCost=$gasCost")
            
            // Total amount needed = gas cost + transaction value
            val totalAmountNeeded = gasCost.add(transactionValue)
            
            logger.info("Transaction requires: gasLimit=$userGasLimit, gasCost=$gasCost wei (with ${blockchainProperties.gas.priceMultiplier}x multiplier), transactionValue=$transactionValue wei, totalNeeded=$totalAmountNeeded wei")

            // Handle conditional funding if needed
            val fundingResult = conditionalFunding(actualWalletAddress, totalAmountNeeded, clientCredentials)
            if (!fundingResult.success) {
                return TransactionResult(
                    success = false,
                    transactionHash = null,
                    error = fundingResult.error,
                    contractAddress = decodedTx.to
                )
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

    suspend fun transferGasToUser(
        userAddress: String, 
        gasAmount: BigInteger,
        clientCredentials: Credentials? = null
    ): TransactionResult {
        return try {
            // Require client credentials - no fallback to relayer wallet
            val credentials = clientCredentials 
                ?: return TransactionResult(
                    success = false,
                    transactionHash = null,
                    error = "Client wallet credentials required - no wallet configured for this API key"
                )
            
            logger.info("Transferring $gasAmount wei to user: $userAddress via Gas Payer Contract using client wallet ${credentials.address}")
            
            // Load contract with the appropriate credentials
            val contractAddress = blockchainProperties.relayer.gasPayerContractAddress
            if (contractAddress.isBlank()) {
                return TransactionResult(
                    success = false,
                    transactionHash = null,
                    error = "Gas Payer Contract not configured (GAS_PAYER_CONTRACT_ADDRESS missing)"
                )
            }
            
            val contract = GasPayerContract.load(
                contractAddress,
                web3j,
                credentials,  // Use the client credentials (required)
                gasProvider
            )
            
            // Calculate fee that will be charged by the contract
            val fee = contract.calculateFee(gasAmount).send()
            val totalAmount = gasAmount.add(fee)
            
            logger.info("Gas transfer: amount=$gasAmount wei, fee=$fee wei, total=$totalAmount wei from client wallet ${credentials.address}")
            
            // Call fundAndRelay with the total amount (gas + fee)
            val receipt = contract.fundAndRelay(
                userAddress,
                gasAmount,
                totalAmount
            ).send()

            if (receipt?.isStatusOK == true) {
                logger.info("Gas transferred successfully to user via contract: ${receipt.transactionHash}")
                TransactionResult(
                    success = true,
                    transactionHash = receipt.transactionHash
                )
            } else {
                logger.error("Gas transfer via contract failed: ${receipt?.revertReason ?: "Unknown error"}")
                TransactionResult(
                    success = false,
                    transactionHash = receipt?.transactionHash,
                    error = "Gas transfer transaction failed: ${receipt?.revertReason ?: "Unknown error"}"
                )
            }

        } catch (e: Exception) {
            logger.error("Error transferring gas to user via contract", e)
            TransactionResult(
                success = false,
                transactionHash = null,
                error = e.message ?: "Unknown error occurred"
            )
        }
    }

    suspend fun conditionalFunding(
        walletAddress: String,
        totalAmountNeededWei: BigInteger
    ): TransactionResult {
        val clientCredentials = try {
            val requestAttributes = RequestContextHolder.currentRequestAttributes() as ServletRequestAttributes
            requestAttributes.request.getAttribute("client.credentials") as? Credentials
        } catch (e: Exception) {
            logger.warn("Could not retrieve client credentials from request context: ${e.message}")
            null
        }
        return conditionalFunding(walletAddress, totalAmountNeededWei, clientCredentials)
    }

    private suspend fun conditionalFunding(
        walletAddress: String,
        totalAmountNeededWei: BigInteger,
        clientCredentials: Credentials?
    ): TransactionResult {
        return try {
            // Check user's current AVAX balance
            val currentBalance = web3j.ethGetBalance(walletAddress, DefaultBlockParameterName.LATEST).send().balance
            
            // Only transfer if user doesn't have enough for the entire transaction
            if (currentBalance < totalAmountNeededWei) {
                val amountNeeded = totalAmountNeededWei.subtract(currentBalance)
                logger.info("User has $currentBalance wei, needs $totalAmountNeededWei wei, transferring $amountNeeded wei")
                
                val gasTransferResult = transferGasToUser(walletAddress, amountNeeded, clientCredentials)
                if (!gasTransferResult.success) {
                    logger.error("Failed to transfer gas to user: ${gasTransferResult.error}")
                    return TransactionResult(
                        success = false,
                        transactionHash = null,
                        error = "Failed to transfer gas to user: ${gasTransferResult.error}",
                        contractAddress = null
                    )
                }
                
                // Wait for balance to update with retry mechanism
                val updatedBalance = waitForBalanceUpdate(walletAddress, currentBalance, totalAmountNeededWei)
                if (updatedBalance == null) {
                    logger.error("Balance update timeout after gas transfer")
                    return TransactionResult(
                        success = false,
                        transactionHash = null,
                        error = "Balance update timeout after gas transfer",
                        contractAddress = null
                    )
                }
                
                logger.info("User balance after gas transfer: $updatedBalance wei (was $currentBalance wei)")
            } else {
                logger.info("User already has sufficient balance ($currentBalance wei >= $totalAmountNeededWei wei), skipping transfer")
            }
            
            TransactionResult(success = true, transactionHash = null, error = null, contractAddress = null)
        } catch (e: Exception) {
            logger.error("Error during conditional funding", e)
            TransactionResult(
                success = false,
                transactionHash = null,
                error = "Conditional funding failed: ${e.message}",
                contractAddress = null
            )
        }
    }

    private suspend fun waitForBalanceUpdate(
        userAddress: String, 
        previousBalance: BigInteger, 
        requiredAmount: BigInteger
    ): BigInteger? {
        return try {
            var attempts = 0
            val maxAttempts = 15 // 30 seconds max (15 * 2 seconds)
            
            while (attempts < maxAttempts) {
                val currentBalance = web3j.ethGetBalance(userAddress, DefaultBlockParameterName.LATEST).send().balance
                
                // Check if balance has increased and is sufficient
                if (currentBalance > previousBalance && currentBalance >= requiredAmount) {
                    logger.info("Balance updated successfully after ${attempts + 1} attempts")
                    return currentBalance
                }
                
                // Even if balance increased but still not sufficient, continue waiting
                if (currentBalance > previousBalance) {
                    logger.debug("Balance partially updated ($currentBalance wei), still waiting for full update (need $requiredAmount wei)")
                } else {
                    logger.debug("Balance not yet updated (attempt ${attempts + 1}/$maxAttempts): $currentBalance wei")
                }
                
                Thread.sleep(2000)
                attempts++
            }
            
            // Final check after all attempts
            val finalBalance = web3j.ethGetBalance(userAddress, DefaultBlockParameterName.LATEST).send().balance
            if (finalBalance >= requiredAmount) {
                logger.info("Balance sufficient after timeout period: $finalBalance wei")
                return finalBalance
            }
            
            logger.warn("Balance update timeout after $maxAttempts attempts. Final balance: $finalBalance wei, required: $requiredAmount wei")
            null
        } catch (e: Exception) {
            logger.error("Error waiting for balance update", e)
            null
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

    data class DecodedTransactionInfo(
        val transaction: RawTransaction,
        val senderAddress: String
    )

    private fun decodeTransactionWithSender(signedTransactionHex: String, fallbackAddress: String): DecodedTransactionInfo {
        val decodedTx = TransactionDecoder.decode(signedTransactionHex)
        val senderAddress = try {
            (decodedTx as SignedRawTransaction).from
        } catch (e: Exception) {
            logger.warn("Could not extract wallet address from signed transaction, using fallback address")
            fallbackAddress
        }
        return DecodedTransactionInfo(decodedTx, senderAddress)
    }

    suspend fun validateGasLimits(
        decodedTx: RawTransaction,
        operationName: String = "",
        expectedGasLimit: BigInteger = BigInteger.ZERO
    ): TransactionResult {
        return try {
            logger.debug("Validating gas limits for transaction to: ${decodedTx.to}, operation: $operationName")
            
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
            
            // Calculate total cost and check against maximum
            val totalCost = userProvidedGas.multiply(userGasPrice)
            val maxAllowedCost = BigInteger.valueOf(blockchainProperties.gas.maxGasCostWei)
            
            // Determine gas limit to validate against
            val gasLimitToValidate = if (expectedGasLimit > BigInteger.ZERO) {
                // Use operation-specific limit with 20% buffer for gas price fluctuations
                val bufferMultiplier = BigInteger.valueOf(120)
                val divisor = BigInteger.valueOf(100)
                expectedGasLimit.multiply(bufferMultiplier).divide(divisor)
            } else {
                // Fall back to configured maximum gas limit
                BigInteger.valueOf(blockchainProperties.gas.maxGasLimit)
            }
            
            // Get current network gas price for comparison
            val currentNetworkGasPrice = web3j.ethGasPrice().send().gasPrice
            // Apply multiplier properly with decimal precision (e.g., 3.2 -> 320/100)
            val multiplierScaled = (blockchainProperties.gas.maxGasPriceMultiplier * 100).toLong()
            val maxAllowedGasPrice = currentNetworkGasPrice.multiply(BigInteger.valueOf(multiplierScaled)).divide(BigInteger.valueOf(100))
            
            logger.info("Gas validation: operation=$operationName, userGasLimit=$userProvidedGas, expectedGasLimit=$expectedGasLimit, maxAllowedGasLimit=$gasLimitToValidate, userGasPrice=$userGasPrice, maxGasPrice=$maxAllowedGasPrice, totalCost=$totalCost, maxCost=$maxAllowedCost")

            // Check total cost - only use fallback limit if no operation-specific limit provided
            if (expectedGasLimit == BigInteger.ZERO && totalCost > maxAllowedCost) {
                logger.warn("Transaction exceeds maximum fallback cost: $totalCost > $maxAllowedCost wei")
                return TransactionResult(
                    success = false,
                    transactionHash = null,
                    error = "Transaction cost too high: $totalCost wei, maximum allowed $maxAllowedCost wei"
                )
            }

            // Check gas limit against operation-specific or maximum limit
            if (userProvidedGas > gasLimitToValidate) {
                val errorMsg = if (expectedGasLimit > BigInteger.ZERO) {
                    "Gas limit exceeds expected for operation '$operationName': provided $userProvidedGas, maximum allowed $gasLimitToValidate (includes 20% buffer)"
                } else {
                    "Gas limit too high: provided $userProvidedGas, maximum allowed $gasLimitToValidate"
                }
                logger.warn(errorMsg)
                return TransactionResult(
                    success = false,
                    transactionHash = null,
                    error = errorMsg
                )
            }

            // Check gas price (prevent paying excessive gas prices)
            if (userGasPrice > maxAllowedGasPrice) {
                logger.warn("User provided excessive gas price: $userGasPrice > $maxAllowedGasPrice")
                return TransactionResult(
                    success = false,
                    transactionHash = null,
                    error = "Gas price too high: provided $userGasPrice, maximum allowed $maxAllowedGasPrice (current network: $currentNetworkGasPrice)"
                )
            }

            logger.debug("Gas limits validation passed")
            TransactionResult(success = true, transactionHash = null)
            
        } catch (e: Exception) {
            logger.error("Error validating gas limits", e)
            TransactionResult(
                success = false,
                transactionHash = null,
                error = "Gas validation failed: ${e.message}"
            )
        }
    }
}