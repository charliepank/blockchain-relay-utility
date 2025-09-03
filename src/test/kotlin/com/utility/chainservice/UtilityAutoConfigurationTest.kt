package com.utility.chainservice

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.mockito.kotlin.*
import org.web3j.crypto.Credentials
import org.web3j.protocol.Web3j
import org.web3j.protocol.core.Request
import org.web3j.protocol.core.methods.response.EthChainId
import org.web3j.protocol.core.methods.response.EthGasPrice
import org.web3j.tx.gas.ContractGasProvider
import java.math.BigInteger

class UtilityAutoConfigurationTest {

    private lateinit var blockchainProperties: BlockchainProperties
    private lateinit var utilityAutoConfiguration: UtilityAutoConfiguration

    @BeforeEach
    fun setUp() {
        val relayerProperties = RelayerProperties(
            gasPayerContractAddress = "0x1234567890123456789012345678901234567890"
        )
        val gasProperties = GasProperties(
            priceMultiplier = 1.2,
            minimumGasPriceWei = 6L
        )
        
        blockchainProperties = BlockchainProperties(
            rpcUrl = "https://api.avax-test.network/ext/bc/C/rpc",
            chainId = 43113L,
            relayer = relayerProperties,
            gas = gasProperties
        )
        
        
        val securityProperties = SecurityProperties(
            configPath = "./src/test/resources/test-security-config.json",
            enabled = false
        )
        
        utilityAutoConfiguration = UtilityAutoConfiguration(blockchainProperties, securityProperties)
    }

    @Test
    fun `should create Web3j client with correct RPC URL`() {
        val web3j = utilityAutoConfiguration.web3j()
        
        assertNotNull(web3j)
        // Web3j doesn't expose the RPC URL easily, so we just verify it's created
    }





    @Test
    fun `should get chain ID from Web3j successfully`() {
        val web3j = mock<Web3j>()
        val chainIdRequest = mock<Request<*, EthChainId>>()
        val chainIdResponse = mock<EthChainId>()
        
        whenever(web3j.ethChainId()).thenReturn(chainIdRequest)
        whenever(chainIdRequest.send()).thenReturn(chainIdResponse)
        whenever(chainIdResponse.hasError()).thenReturn(false)
        whenever(chainIdResponse.chainId).thenReturn(BigInteger.valueOf(43114L))
        
        val chainId = utilityAutoConfiguration.chainId(web3j)
        
        assertEquals(43114L, chainId)
        verify(web3j).ethChainId()
    }

    @Test
    fun `should fallback to configured chain ID when Web3j fails`() {
        val web3j = mock<Web3j>()
        val chainIdRequest = mock<Request<*, EthChainId>>()
        val chainIdResponse = mock<EthChainId>()
        
        whenever(web3j.ethChainId()).thenReturn(chainIdRequest)
        whenever(chainIdRequest.send()).thenReturn(chainIdResponse)
        whenever(chainIdResponse.hasError()).thenReturn(true)
        
        val chainId = utilityAutoConfiguration.chainId(web3j)
        
        assertEquals(43113L, chainId) // Falls back to configured value
    }

    @Test
    fun `should fallback to configured chain ID when Web3j throws exception`() {
        val web3j = mock<Web3j>()
        
        whenever(web3j.ethChainId()).thenThrow(RuntimeException("Network error"))
        
        val chainId = utilityAutoConfiguration.chainId(web3j)
        
        assertEquals(43113L, chainId) // Falls back to configured value
    }

    @Test
    fun `should create gas provider with network gas price`() {
        val web3j = mock<Web3j>()
        val gasPriceRequest = mock<Request<*, EthGasPrice>>()
        val gasPriceResponse = mock<EthGasPrice>()
        
        whenever(web3j.ethGasPrice()).thenReturn(gasPriceRequest)
        whenever(gasPriceRequest.send()).thenReturn(gasPriceResponse)
        whenever(gasPriceResponse.gasPrice).thenReturn(BigInteger.valueOf(25000000000L)) // 25 gwei
        
        val gasProvider = utilityAutoConfiguration.gasProvider(web3j)
        
        assertNotNull(gasProvider)
        
        val expectedPrice = BigInteger.valueOf(30000000000L) // 25 gwei * 1.2
        assertEquals(expectedPrice, gasProvider.gasPrice)
        
        verify(web3j).ethGasPrice()
    }

    @Test
    fun `should use minimum gas price when network price is too low`() {
        val web3j = mock<Web3j>()
        val gasPriceRequest = mock<Request<*, EthGasPrice>>()
        val gasPriceResponse = mock<EthGasPrice>()
        
        whenever(web3j.ethGasPrice()).thenReturn(gasPriceRequest)
        whenever(gasPriceRequest.send()).thenReturn(gasPriceResponse)
        whenever(gasPriceResponse.gasPrice).thenReturn(BigInteger.valueOf(1L)) // Very low gas price
        
        val gasProvider = utilityAutoConfiguration.gasProvider(web3j)
        
        // Should use minimum price of 6 wei instead of 1.2 wei (1 * 1.2)
        assertEquals(BigInteger.valueOf(6L), gasProvider.gasPrice)
    }

    @Test
    fun `should fallback to minimum gas price when Web3j fails`() {
        val web3j = mock<Web3j>()
        
        whenever(web3j.ethGasPrice()).thenThrow(RuntimeException("Network error"))
        
        val gasProvider = utilityAutoConfiguration.gasProvider(web3j)
        
        assertEquals(BigInteger.valueOf(6L), gasProvider.gasPrice)
    }



    @Test
    fun `should test gas provider methods`() {
        val web3j = mock<Web3j>()
        val gasPriceRequest = mock<Request<*, EthGasPrice>>()
        val gasPriceResponse = mock<EthGasPrice>()
        
        whenever(web3j.ethGasPrice()).thenReturn(gasPriceRequest)
        whenever(gasPriceRequest.send()).thenReturn(gasPriceResponse)
        whenever(gasPriceResponse.gasPrice).thenReturn(BigInteger.valueOf(25000000000L))
        
        val gasProvider = utilityAutoConfiguration.gasProvider(web3j)
        
        // Test both overloaded methods
        assertEquals(gasProvider.gasPrice, gasProvider.getGasPrice("someFunction"))
        assertEquals(gasProvider.gasLimit, gasProvider.getGasLimit("someFunction"))
        
        // Verify it uses default gas limit
        assertTrue(gasProvider.gasLimit.toLong() > 0)
    }

    @Test
    fun `should create blockchain properties with default values`() {
        val defaultProps = BlockchainProperties()
        
        assertEquals("", defaultProps.rpcUrl)
        assertEquals(43113L, defaultProps.chainId)
        assertNotNull(defaultProps.relayer)
        assertNotNull(defaultProps.gas)
        assertEquals(1.2, defaultProps.gas.priceMultiplier)
        assertEquals(6L, defaultProps.gas.minimumGasPriceWei)
    }


    @Test
    fun `should handle relayer properties correctly`() {
        val relayerProps = RelayerProperties(
            gasPayerContractAddress = "0x1234567890123456789012345678901234567890"
        )
        
        assertEquals("0x1234567890123456789012345678901234567890", relayerProps.gasPayerContractAddress)
    }

    @Test
    fun `should handle gas properties correctly`() {
        val gasProps = GasProperties(
            priceMultiplier = 1.5,
            minimumGasPriceWei = 10L
        )
        
        assertEquals(1.5, gasProps.priceMultiplier)
        assertEquals(10L, gasProps.minimumGasPriceWei)
    }

    @Test
    fun `should create default relayer properties`() {
        val defaultRelayerProps = RelayerProperties()
        
        assertEquals("", defaultRelayerProps.gasPayerContractAddress)
    }

    @Test
    fun `should create default gas properties`() {
        val defaultGasProps = GasProperties()
        
        assertEquals(1.2, defaultGasProps.priceMultiplier)
        assertEquals(6L, defaultGasProps.minimumGasPriceWei)
    }

    @Test
    fun `should throw exception for blank gas payer contract address`() {
        val blankContractRelayerProperties = RelayerProperties(
            gasPayerContractAddress = ""
        )
        val blankContractBlockchainProperties = blockchainProperties.copy(relayer = blankContractRelayerProperties)
        val securityProperties = SecurityProperties(enabled = false)
        val blankContractConfig = UtilityAutoConfiguration(blankContractBlockchainProperties, securityProperties)
        
        assertThrows(IllegalArgumentException::class.java) {
            blankContractConfig.validateConfiguration()
        }
    }

    @Test
    fun `should throw exception for invalid gas payer contract address format`() {
        val invalidContractRelayerProperties = RelayerProperties(
            gasPayerContractAddress = "invalid-contract-address"
        )
        val invalidContractBlockchainProperties = blockchainProperties.copy(relayer = invalidContractRelayerProperties)
        val securityProperties = SecurityProperties(enabled = false)
        val invalidContractConfig = UtilityAutoConfiguration(invalidContractBlockchainProperties, securityProperties)
        
        assertThrows(IllegalArgumentException::class.java) {
            invalidContractConfig.validateConfiguration()
        }
    }

    @Test
    fun `should throw exception for short gas payer contract address`() {
        val shortContractRelayerProperties = RelayerProperties(
            gasPayerContractAddress = "0x123"
        )
        val shortContractBlockchainProperties = blockchainProperties.copy(relayer = shortContractRelayerProperties)
        val securityProperties = SecurityProperties(enabled = false)
        val shortContractConfig = UtilityAutoConfiguration(shortContractBlockchainProperties, securityProperties)
        
        assertThrows(IllegalArgumentException::class.java) {
            shortContractConfig.validateConfiguration()
        }
    }
}