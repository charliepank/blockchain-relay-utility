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
    private val blockchainProperties: BlockchainProperties,
    private val cryptoPriceService: CryptoPriceService? = null
) {


    private val logger = LoggerFactory.getLogger(BlockchainRelayService::class.java)
    
    private suspend fun formatGasAmount(weiAmount: BigInteger): String {
        return try {
            if (cryptoPriceService == null) {
                return "$weiAmount wei"
            }
            
            val nativeCoin = cryptoPriceService.getNativeCoinSymbol(chainId.toInt())
            val nativeAmount = BigDecimal(weiAmount).divide(BigDecimal("1000000000000000000"), 18, RoundingMode.HALF_UP)
            
            // Format in scientific notation
            val nativeFormatted = "%.2E".format(nativeAmount)
            
            // Get USD value (this already handles failures gracefully)
            val usdValue = cryptoPriceService.convertWeiToUsd(weiAmount.toString(), chainId.toInt())
            val usdFormatted = "%.2E".format(usdValue)
            
            "$nativeCoin:$nativeFormatted(USD:$usdFormatted)"
        } catch (e: Exception) {
            logger.debug("Failed to format gas amount with prices: ${e.message}")
            "$weiAmount wei"
        }
    }

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
            
            // Apply configured safety margin to gas funding
            val multiplier = BigInteger.valueOf((blockchainProperties.gas.priceMultiplier * 100).toLong())
            val exactGasCost = baseGasCost.multiply(multiplier).divide(BigInteger.valueOf(100))

            val baseGasCostFormatted = formatGasAmount(baseGasCost)
            val exactGasCostFormatted = formatGasAmount(exactGasCost)
            logger.info("Applied gas price multiplier ${blockchainProperties.gas.priceMultiplier}x: baseGasCost=$baseGasCostFormatted, fundingAmount=$exactGasCostFormatted")

            // Calculate the service fee that will be charged by the contract
            // We need to check this upfront to ensure user has enough for gas + fee
            val serviceFee = try {
                // Only calculate fee if we have client credentials (required for contract calls)
                if (clientCredentials != null) {
                    val contract = GasPayerContract.load(
                        blockchainProperties.relayer.gasPayerContractAddress,
                        web3j,
                        clientCredentials,
                        gasProvider
                    )
                    contract.calculateFee(exactGasCost).send()
                } else {
                    // Fallback: estimate 5% fee if no credentials available
                    exactGasCost.multiply(BigInteger.valueOf(5)).divide(BigInteger.valueOf(100))
                }
            } catch (e: Exception) {
                logger.warn("Could not calculate service fee upfront, using estimate: ${e.message}")
                // Fallback: estimate 5% fee if contract call fails
                exactGasCost.multiply(BigInteger.valueOf(5)).divide(BigInteger.valueOf(100))
            }
            
            logger.debug("Gas cost calculation: exactGasCost=$exactGasCost, serviceFee=$serviceFee")
            
            // Total amount needed = exact gas cost + service fee + transaction value
            val totalAmountNeeded = exactGasCost.add(serviceFee).add(transactionValue)
            
            val gasCostFormatted = formatGasAmount(exactGasCost)
            val serviceFeeFormatted = formatGasAmount(serviceFee)
            val transactionValueFormatted = formatGasAmount(transactionValue)
            val totalNeededFormatted = formatGasAmount(totalAmountNeeded)
            
            logger.info("Transaction requires: gasLimit=$userGasLimit, exactGasCost=$gasCostFormatted, serviceFee=$serviceFeeFormatted, transactionValue=$transactionValueFormatted, totalNeeded=$totalNeededFormatted")

            // Handle conditional funding if needed - pass exact gas cost separately
            val fundingResult = conditionalFundingWithGas(actualWalletAddress, exactGasCost, transactionValue, clientCredentials)
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
            
            val gasAmountFormatted = formatGasAmount(gasAmount)
            logger.info("Transferring $gasAmountFormatted to user: $userAddress via Gas Payer Contract using client wallet ${credentials.address}")
            
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
            val feeFormatted = formatGasAmount(fee)
            val totalFormatted = formatGasAmount(totalAmount)
            logger.info("Gas transfer: amount=$gasAmountFormatted, fee=$feeFormatted, total=$totalFormatted from client wallet ${credentials.address}")
            
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

    private suspend fun conditionalFundingWithGas(
        walletAddress: String,
        exactGasCost: BigInteger,
        transactionValue: BigInteger,
        clientCredentials: Credentials?
    ): TransactionResult {
        return try {
            // Check user's current AVAX balance
            val currentBalance = web3j.ethGetBalance(walletAddress, DefaultBlockParameterName.LATEST).send().balance
            
            // Calculate what we need for gas + transaction value
            val totalNeededForTransaction = exactGasCost.add(transactionValue)
            
            // Only transfer if user doesn't have enough for the gas + transaction value
            if (currentBalance < totalNeededForTransaction) {
                // Calculate the base amount we need to transfer
                val baseAmountNeeded = totalNeededForTransaction.subtract(currentBalance)
                
                // Calculate the service fee that will be charged on this amount
                val estimatedFee = try {
                    if (clientCredentials != null) {
                        val contract = GasPayerContract.load(
                            blockchainProperties.relayer.gasPayerContractAddress,
                            web3j,
                            clientCredentials,
                            gasProvider
                        )
                        contract.calculateFee(baseAmountNeeded).send()
                    } else {
                        // Fallback: estimate 5% fee if no credentials available
                        baseAmountNeeded.multiply(BigInteger.valueOf(5)).divide(BigInteger.valueOf(100))
                    }
                } catch (e: Exception) {
                    logger.warn("Could not calculate service fee for funding, using estimate: ${e.message}")
                    // Fallback: estimate 5% fee if contract call fails
                    baseAmountNeeded.multiply(BigInteger.valueOf(5)).divide(BigInteger.valueOf(100))
                }
                
                // We need to account for the fee when calculating how much to request
                // If user needs X and there's a fee of F, we need to transfer (X + F) so user gets X
                val gasAmountToTransfer = baseAmountNeeded.add(estimatedFee)
                
                val currentBalanceFormatted = formatGasAmount(currentBalance)
                val totalNeededFormatted = formatGasAmount(totalNeededForTransaction)
                val gasTransferFormatted = formatGasAmount(gasAmountToTransfer)
                val estimatedFeeFormatted = formatGasAmount(estimatedFee)
                logger.info("User has $currentBalanceFormatted, needs $totalNeededFormatted for transaction, transferring $gasTransferFormatted as gas (includes estimated fee $estimatedFeeFormatted)")
                
                val gasTransferResult = transferGasToUser(walletAddress, gasAmountToTransfer, clientCredentials)
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
                // The user should have at least totalNeededForTransaction after the transfer
                val updatedBalance = waitForBalanceUpdate(walletAddress, currentBalance, totalNeededForTransaction)
                if (updatedBalance == null) {
                    logger.error("Balance update timeout after gas transfer")
                    return TransactionResult(
                        success = false,
                        transactionHash = null,
                        error = "Balance update timeout after gas transfer",
                        contractAddress = null
                    )
                }
                
                val updatedBalanceFormatted = formatGasAmount(updatedBalance)
                val previousBalanceFormatted = formatGasAmount(currentBalance)
                logger.info("User balance after gas transfer: $updatedBalanceFormatted (was $previousBalanceFormatted)")
            } else {
                val currentBalanceFormatted = formatGasAmount(currentBalance)
                val totalNeededFormatted = formatGasAmount(totalNeededForTransaction)
                logger.info("User already has sufficient balance ($currentBalanceFormatted >= $totalNeededFormatted), skipping transfer")
            }
            
            TransactionResult(success = true, transactionHash = null, error = null, contractAddress = null)
            
        } catch (e: Exception) {
            logger.error("Error in conditional funding", e)
            TransactionResult(
                success = false,
                transactionHash = null,
                error = e.message ?: "Unknown error in funding",
                contractAddress = null
            )
        }
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
                val currentBalanceFormatted = formatGasAmount(currentBalance)
                val totalNeededFormatted = formatGasAmount(totalAmountNeededWei)
                val amountNeededFormatted = formatGasAmount(amountNeeded)
                logger.info("User has $currentBalanceFormatted, needs $totalNeededFormatted, transferring $amountNeededFormatted")
                
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
                
                val updatedBalanceFormatted = formatGasAmount(updatedBalance)
                val previousBalanceFormatted = formatGasAmount(currentBalance)
                logger.info("User balance after gas transfer: $updatedBalanceFormatted (was $previousBalanceFormatted)")
            } else {
                val currentBalanceFormatted = formatGasAmount(currentBalance)
                val totalNeededFormatted = formatGasAmount(totalAmountNeededWei)
                logger.info("User already has sufficient balance ($currentBalanceFormatted >= $totalNeededFormatted), skipping transfer")
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
                    logger.debug("Balance partially updated (${formatGasAmount(currentBalance)}), still waiting for full update (need ${formatGasAmount(requiredAmount)})")
                } else {
                    logger.debug("Balance not yet updated (attempt ${attempts + 1}/$maxAttempts): ${formatGasAmount(currentBalance)}")
                }
                
                Thread.sleep(2000)
                attempts++
            }
            
            // Final check after all attempts
            val finalBalance = web3j.ethGetBalance(userAddress, DefaultBlockParameterName.LATEST).send().balance
            if (finalBalance >= requiredAmount) {
                val finalBalanceFormatted = formatGasAmount(finalBalance)
                logger.info("Balance sufficient after timeout period: $finalBalanceFormatted")
                return finalBalance
            }
            
            logger.warn("Balance update timeout after $maxAttempts attempts. Final balance: ${formatGasAmount(finalBalance)}, required: ${formatGasAmount(requiredAmount)}")
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
            
            val userGasPriceFormatted = formatGasAmount(userGasPrice)
            val maxGasPriceFormatted = formatGasAmount(maxAllowedGasPrice)
            val totalCostFormatted = formatGasAmount(totalCost)
            val maxCostFormatted = formatGasAmount(maxAllowedCost)
            
            logger.info("Gas validation: operation=$operationName, userGasLimit=$userProvidedGas, expectedGasLimit=$expectedGasLimit, maxAllowedGasLimit=$gasLimitToValidate, userGasPrice=$userGasPriceFormatted, maxGasPrice=$maxGasPriceFormatted, totalCost=$totalCostFormatted, maxCost=$maxCostFormatted")

            // Check total cost - only use fallback limit if no operation-specific limit provided
            if (expectedGasLimit == BigInteger.ZERO && totalCost > maxAllowedCost) {
                logger.warn("Transaction exceeds maximum fallback cost: ${formatGasAmount(totalCost)} > ${formatGasAmount(maxAllowedCost)}")
                return TransactionResult(
                    success = false,
                    transactionHash = null,
                    error = "Transaction cost too high: ${formatGasAmount(totalCost)}, maximum allowed ${formatGasAmount(maxAllowedCost)}"
                )
            }

            // Check gas limit against operation-specific or maximum limit
            if (userProvidedGas > gasLimitToValidate) {
                val errorMsg = if (expectedGasLimit > BigInteger.ZERO) {
                    "Gas limit exceeds expected for operation '$operationName': provided ${formatGasAmount(userProvidedGas.multiply(userGasPrice))}, maximum allowed ${formatGasAmount(gasLimitToValidate.multiply(userGasPrice))} (includes 20% buffer)"
                } else {
                    "Gas limit too high: provided ${formatGasAmount(userProvidedGas.multiply(userGasPrice))}, maximum allowed ${formatGasAmount(gasLimitToValidate.multiply(userGasPrice))}"
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
                    error = "Gas price too high: provided ${formatGasAmount(userGasPrice)}, maximum allowed ${formatGasAmount(maxAllowedGasPrice)} (current network: ${formatGasAmount(currentNetworkGasPrice)})"
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