package com.utility.chainservice

import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.web3j.crypto.Credentials
import org.web3j.protocol.Web3j
import org.web3j.protocol.http.HttpService
import org.web3j.tx.gas.ContractGasProvider
import org.web3j.tx.gas.DefaultGasProvider
import java.math.BigInteger

@Configuration
class BlockchainConfiguration {

    private val logger = LoggerFactory.getLogger(BlockchainConfiguration::class.java)

    @Bean
    fun web3jClient(
        rpcUrl: String
    ): Web3j {
        return Web3j.build(HttpService(rpcUrl))
    }

    @Bean
    fun relayerCredentialsBean(
        relayerPrivateKey: String
    ): Credentials {
        try {
            if (relayerPrivateKey.isBlank()) {
                throw IllegalArgumentException("RELAYER_PRIVATE_KEY is empty or not set")
            }
            
            // Validate private key format
            if (!relayerPrivateKey.startsWith("0x") || relayerPrivateKey.length != 66) {
                throw IllegalArgumentException("RELAYER_PRIVATE_KEY must be a 64-character hex string prefixed with 0x")
            }
            
            return Credentials.create(relayerPrivateKey)
        } catch (e: Exception) {
            logger.error("Failed to create relayer credentials: ${e.message}")
            throw IllegalStateException("Invalid RELAYER_PRIVATE_KEY: ${e.message}", e)
        }
    }

    @Bean
    fun chainIdBean(web3j: Web3j, fallbackChainId: Long): Long {
        return try {
            val chainIdResponse = web3j.ethChainId().send()
            if (chainIdResponse.hasError()) {
                logger.warn("Failed to retrieve chain ID from RPC: ${chainIdResponse.error.message}, using configured value: $fallbackChainId")
                fallbackChainId
            } else {
                val rpcChainId = chainIdResponse.chainId.toLong()
                logger.info("Retrieved chain ID from RPC: $rpcChainId")
                rpcChainId
            }
        } catch (e: Exception) {
            logger.warn("Error retrieving chain ID from RPC: ${e.message}, using configured value: $fallbackChainId")
            fallbackChainId
        }
    }

    @Bean
    fun genericGasProvider(
        web3j: Web3j,
        gasPriceMultiplier: Double,
        minimumGasPriceWei: Long,
        gasLimitsConfig: Map<String, Long>
    ): ContractGasProvider {
        return object : ContractGasProvider {
            override fun getGasPrice(contractFunc: String?): BigInteger {
                return try {
                    val networkGasPrice = web3j.ethGasPrice().send().gasPrice
                    val multipliedPrice = networkGasPrice.multiply(
                        BigInteger.valueOf((gasPriceMultiplier * 100).toLong())
                    ).divide(BigInteger.valueOf(100))
                    
                    // Enforce minimum gas price
                    val minimumPrice = BigInteger.valueOf(minimumGasPriceWei)
                    maxOf(multipliedPrice, minimumPrice)
                } catch (e: Exception) {
                    logger.warn("Failed to fetch gas price from network, using minimum: ${e.message}")
                    BigInteger.valueOf(minimumGasPriceWei)
                }
            }

            override fun getGasPrice(): BigInteger {
                return getGasPrice(null)
            }

            override fun getGasLimit(contractFunc: String?): BigInteger {
                return contractFunc?.let { func ->
                    gasLimitsConfig[func]?.let { BigInteger.valueOf(it) }
                } ?: DefaultGasProvider.GAS_LIMIT
            }

            override fun getGasLimit(): BigInteger {
                return getGasLimit(null)
            }
        }
    }
}