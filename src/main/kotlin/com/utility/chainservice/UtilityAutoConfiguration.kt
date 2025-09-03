package com.utility.chainservice

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
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
    var walletAddress: String = "",
    var gasPayerContractAddress: String = "",  // Gas payer contract address for fee collection
    var configFilePath: String = "./config/relay-config.json",
    var useConfigFile: Boolean = true
)

data class GasProperties(
    var priceMultiplier: Double = 1.2,  // Default: 120% of network gas price
    var minimumGasPriceWei: Long = 6,   // Default: 6 wei minimum
    var maxGasCostWei: Long = 540_000_000,  // Default: ~$0.014 USD max total cost
    var maxGasLimit: Long = 1_000_000,      // Default: 1M gas limit maximum
    var maxGasPriceMultiplier: Double = 3.0  // Default: 3x current network gas price maximum
)

@ConfigurationProperties(prefix = "auth")
data class AuthProperties(
    var userServiceUrl: String = "",
    var enabled: Boolean = true
)

@ConfigurationProperties(prefix = "security")
data class SecurityProperties(
    var configPath: String = "./config/security-config.json",
    var enabled: Boolean = true
)

@Configuration
@EnableConfigurationProperties(BlockchainProperties::class, AuthProperties::class, SecurityProperties::class)
@ComponentScan(basePackages = ["com.utility.chainservice"])
class UtilityAutoConfiguration(
    private val blockchainProperties: BlockchainProperties,
    private val authProperties: AuthProperties,
    private val securityProperties: SecurityProperties
) {
    
    @jakarta.annotation.PostConstruct
    fun logConfiguration() {
        println("GAS CONFIGURATION DEBUG:")
        println("  maxGasPriceMultiplier: ${blockchainProperties.gas.maxGasPriceMultiplier}")
        println("  maxGasLimit: ${blockchainProperties.gas.maxGasLimit}")
        println("  maxGasCostWei: ${blockchainProperties.gas.maxGasCostWei}")
    }

    @Bean("web3j")
    @ConditionalOnMissingBean
    fun web3j(): Web3j {
        require(blockchainProperties.rpcUrl.isNotBlank()) { "RPC_URL is required" }
        return Web3j.build(HttpService(blockchainProperties.rpcUrl))
    }

    @Bean("relayerCredentials")
    @ConditionalOnMissingBean
    fun relayerCredentials(): Credentials {
        // Validate gas payer contract address
        val gasPayerContract = blockchainProperties.relayer.gasPayerContractAddress
        require(gasPayerContract.isNotBlank()) { "GAS_PAYER_CONTRACT_ADDRESS is required" }
        require(gasPayerContract.startsWith("0x") && gasPayerContract.length == 42) {
            "GAS_PAYER_CONTRACT_ADDRESS must be a valid Ethereum address (0x + 40 hex chars)"
        }
        
        // Check if we should use config file
        if (blockchainProperties.relayer.useConfigFile) {
            // Load from relay config file
            val privateKey = loadPrivateKeyFromConfigFile(blockchainProperties.relayer.configFilePath)
            require(privateKey.isNotBlank()) { "Private key not found in config file: ${blockchainProperties.relayer.configFilePath}" }
            require(privateKey.startsWith("0x") && privateKey.length == 66) { 
                "Private key in config file must be a 64-character hex string prefixed with 0x" 
            }
            return Credentials.create(privateKey)
        } else {
            // For testing purposes, allow creating credentials with a default test private key
            val testPrivateKey = "0x0000000000000000000000000000000000000000000000000000000000000001"
            println("WARNING: Using test private key for relayer credentials - not for production use!")
            return Credentials.create(testPrivateKey)
        }
    }
    
    private fun loadPrivateKeyFromConfigFile(configFilePath: String): String {
        return try {
            val configFile = java.io.File(configFilePath)
            if (!configFile.exists()) {
                throw IllegalArgumentException("Relay config file not found: $configFilePath")
            }
            
            val objectMapper = ObjectMapper().registerKotlinModule()
            val configContent = objectMapper.readTree(configFile)
            
            val privateKey = configContent.get("walletConfig")?.get("privateKey")?.asText()
            if (privateKey.isNullOrBlank()) {
                throw IllegalArgumentException("Private key not found in config file at walletConfig.privateKey")
            }
            
            privateKey
        } catch (e: Exception) {
            throw IllegalArgumentException("Failed to load private key from config file: ${e.message}", e)
        }
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

    @Bean
    @ConditionalOnMissingBean
    fun securityConfigurationService(): com.utility.chainservice.security.SecurityConfigurationService {
        return com.utility.chainservice.security.SecurityConfigurationService(
            securityConfigPath = securityProperties.configPath
        )
    }
}
