package com.utility.chainservice

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Configuration
import org.web3j.crypto.Credentials
import org.web3j.protocol.Web3j
import org.web3j.protocol.http.HttpService
import org.web3j.tx.gas.ContractGasProvider
import org.web3j.tx.gas.DefaultGasProvider
import java.math.BigInteger

@ConfigurationProperties(prefix = "blockchain")
data class BlockchainProperties(
    var rpcUrl: String = "",
    var chainId: Long = 43113,
    var relayer: RelayerProperties = RelayerProperties(),
    var gas: GasProperties = GasProperties()
)

data class RelayerProperties(
    var privateKey: String = "",
    var walletAddress: String = "",
    var gasPayerContractAddress: String = ""  // Gas payer contract address for fee collection
)

data class GasProperties(
    var priceMultiplier: Double = 1.2,  // Default: 120% of network gas price
    var minimumGasPriceWei: Long = 6,   // Default: 6 wei minimum
    var maxGasCostWei: Long = 540_000_000,  // Default: ~$0.014 USD max total cost
    var maxGasLimit: Long = 1_000_000,      // Default: 1M gas limit maximum
    var maxGasPriceMultiplier: Int = 3      // Default: 3x current network gas price maximum
)

@ConfigurationProperties(prefix = "auth")
data class AuthProperties(
    var userServiceUrl: String = "",
    var enabled: Boolean = true
)

@Configuration
@EnableConfigurationProperties(BlockchainProperties::class, AuthProperties::class)
@ComponentScan(basePackages = ["com.utility.chainservice"])
class UtilityAutoConfiguration(
    private val blockchainProperties: BlockchainProperties,
    private val authProperties: AuthProperties
) {

    @Bean("web3j")
    @ConditionalOnMissingBean
    fun web3j(): Web3j {
        require(blockchainProperties.rpcUrl.isNotBlank()) { "RPC_URL is required" }
        return Web3j.build(HttpService(blockchainProperties.rpcUrl))
    }

    @Bean("relayerCredentials")
    @ConditionalOnMissingBean
    fun relayerCredentials(): Credentials {
        val privateKey = blockchainProperties.relayer.privateKey
        require(privateKey.isNotBlank()) { "RELAYER_PRIVATE_KEY is required" }
        require(privateKey.startsWith("0x") && privateKey.length == 66) { 
            "RELAYER_PRIVATE_KEY must be a 64-character hex string prefixed with 0x" 
        }
        
        // Validate gas payer contract address
        val gasPayerContract = blockchainProperties.relayer.gasPayerContractAddress
        require(gasPayerContract.isNotBlank()) { "GAS_PAYER_CONTRACT_ADDRESS is required" }
        require(gasPayerContract.startsWith("0x") && gasPayerContract.length == 42) {
            "GAS_PAYER_CONTRACT_ADDRESS must be a valid Ethereum address (0x + 40 hex chars)"
        }
        
        return Credentials.create(privateKey)
    }

    @Bean("chainId")
    fun chainId(web3j: Web3j): Long {
        return try {
            val chainIdResponse = web3j.ethChainId().send()
            if (chainIdResponse.hasError()) {
                blockchainProperties.chainId
            } else {
                chainIdResponse.chainId.toLong()
            }
        } catch (e: Exception) {
            blockchainProperties.chainId
        }
    }

    @Bean("gasProvider")
    @ConditionalOnMissingBean
    fun gasProvider(web3j: Web3j): ContractGasProvider {
        return object : ContractGasProvider {
            override fun getGasPrice(contractFunc: String?): BigInteger {
                return try {
                    val networkGasPrice = web3j.ethGasPrice().send().gasPrice
                    val multipliedPrice = networkGasPrice.multiply(
                        BigInteger.valueOf((blockchainProperties.gas.priceMultiplier * 100).toLong())
                    ).divide(BigInteger.valueOf(100))
                    
                    val minimumPrice = BigInteger.valueOf(blockchainProperties.gas.minimumGasPriceWei)
                    maxOf(multipliedPrice, minimumPrice)
                } catch (e: Exception) {
                    BigInteger.valueOf(blockchainProperties.gas.minimumGasPriceWei)
                }
            }

            override fun getGasPrice(): BigInteger = getGasPrice(null)
            override fun getGasLimit(contractFunc: String?): BigInteger = DefaultGasProvider.GAS_LIMIT
            override fun getGasLimit(): BigInteger = getGasLimit(null)
        }
    }

    @Bean
    @ConditionalOnMissingBean
    fun authenticationProvider(): AuthenticationProvider {
        return HttpAuthenticationProvider(
            userServiceUrl = authProperties.userServiceUrl,
            enabled = authProperties.enabled
        )
    }
}
