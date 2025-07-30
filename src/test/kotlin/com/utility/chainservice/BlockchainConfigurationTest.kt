package com.utility.chainservice

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.mockito.kotlin.*
import org.web3j.protocol.Web3j
import org.web3j.protocol.core.Request
import org.web3j.protocol.core.methods.response.EthChainId
import org.web3j.protocol.core.methods.response.EthGasPrice
import org.web3j.tx.gas.DefaultGasProvider
import java.math.BigInteger

class BlockchainConfigurationTest {

    private lateinit var blockchainConfiguration: BlockchainConfiguration
    private lateinit var web3j: Web3j

    @BeforeEach
    fun setUp() {
        blockchainConfiguration = BlockchainConfiguration()
        web3j = mock()
    }

    @Test
    fun `should create Web3j client with correct RPC URL`() {
        val rpcUrl = "https://api.avax-test.network/ext/bc/C/rpc"
        
        val client = blockchainConfiguration.web3j(rpcUrl)
        
        assertNotNull(client)
        // Web3j doesn't expose the RPC URL directly, but we can verify it was created
    }

    @Test
    fun `should create relayer credentials with valid private key`() {
        val validPrivateKey = "0x1234567890123456789012345678901234567890123456789012345678901234"
        
        val credentials = blockchainConfiguration.relayerCredentials(validPrivateKey)
        
        assertNotNull(credentials)
        // The address is deterministic based on the private key - actual derived address
        assertEquals("0x2e988a386a799f506693793c6a5af6b54dfaabfb", credentials.address.lowercase())
    }

    @Test
    fun `should throw IllegalArgumentException for blank private key`() {
        val exception = assertThrows(IllegalStateException::class.java) {
            blockchainConfiguration.relayerCredentials("")
        }
        
        assertTrue(exception.message!!.contains("RELAYER_PRIVATE_KEY is empty or not set"))
        assertTrue(exception.cause is IllegalArgumentException)
    }

    @Test
    fun `should throw IllegalArgumentException for private key without 0x prefix`() {
        val invalidPrivateKey = "1234567890123456789012345678901234567890123456789012345678901234"
        
        val exception = assertThrows(IllegalStateException::class.java) {
            blockchainConfiguration.relayerCredentials(invalidPrivateKey)
        }
        
        assertTrue(exception.message!!.contains("RELAYER_PRIVATE_KEY must be a 64-character hex string prefixed with 0x"))
        assertTrue(exception.cause is IllegalArgumentException)
    }

    @Test
    fun `should throw IllegalArgumentException for private key with wrong length`() {
        val shortPrivateKey = "0x123456"
        
        val exception = assertThrows(IllegalStateException::class.java) {
            blockchainConfiguration.relayerCredentials(shortPrivateKey)
        }
        
        assertTrue(exception.message!!.contains("RELAYER_PRIVATE_KEY must be a 64-character hex string prefixed with 0x"))
        assertTrue(exception.cause is IllegalArgumentException)
    }

    @Test
    fun `should throw IllegalStateException for invalid hex characters in private key`() {
        val invalidHexKey = "0xGHIJKLMNOPQRSTUVWXYZ1234567890123456789012345678901234567890123456"
        
        val exception = assertThrows(IllegalStateException::class.java) {
            blockchainConfiguration.relayerCredentials(invalidHexKey)
        }
        
        assertTrue(exception.message!!.contains("Invalid RELAYER_PRIVATE_KEY"))
    }

    @Test
    fun `should get chain ID from Web3j successfully`() {
        val chainIdRequest = mock<Request<*, EthChainId>>()
        val chainIdResponse = mock<EthChainId>()
        val fallbackChainId = 43113L
        
        whenever(web3j.ethChainId()).thenReturn(chainIdRequest)
        whenever(chainIdRequest.send()).thenReturn(chainIdResponse)
        whenever(chainIdResponse.hasError()).thenReturn(false)
        whenever(chainIdResponse.chainId).thenReturn(BigInteger.valueOf(43114L))
        
        val chainId = blockchainConfiguration.legacyChainId(web3j, fallbackChainId)
        
        assertEquals(43114L, chainId)
        verify(web3j).ethChainId()
    }

    @Test
    fun `should fallback to configured chain ID when Web3j returns error`() {
        val chainIdRequest = mock<Request<*, EthChainId>>()
        val chainIdResponse = mock<EthChainId>()
        val error = mock<org.web3j.protocol.core.Response.Error>()
        val fallbackChainId = 43113L
        
        whenever(web3j.ethChainId()).thenReturn(chainIdRequest)
        whenever(chainIdRequest.send()).thenReturn(chainIdResponse)
        whenever(chainIdResponse.hasError()).thenReturn(true)
        whenever(chainIdResponse.error).thenReturn(error)
        whenever(error.message).thenReturn("Network error")
        
        val chainId = blockchainConfiguration.legacyChainId(web3j, fallbackChainId)
        
        assertEquals(43113L, chainId)
    }

    @Test
    fun `should fallback to configured chain ID when Web3j throws exception`() {
        val fallbackChainId = 43113L
        
        whenever(web3j.ethChainId()).thenThrow(RuntimeException("Connection failed"))
        
        val chainId = blockchainConfiguration.legacyChainId(web3j, fallbackChainId)
        
        assertEquals(43113L, chainId)
    }

    @Test
    fun `should create gas provider with network gas price`() {
        val gasPriceRequest = mock<Request<*, EthGasPrice>>()
        val gasPriceResponse = mock<EthGasPrice>()
        val gasLimitsConfig = mapOf("transfer" to 21000L, "approve" to 45000L)
        
        whenever(web3j.ethGasPrice()).thenReturn(gasPriceRequest)
        whenever(gasPriceRequest.send()).thenReturn(gasPriceResponse)
        whenever(gasPriceResponse.gasPrice).thenReturn(BigInteger.valueOf(25000000000L)) // 25 gwei
        
        val gasProvider = blockchainConfiguration.gasProvider(
            web3j, 1.2, 6L, gasLimitsConfig
        )
        
        assertNotNull(gasProvider)
        
        // Test gas price calculation (25 gwei * 1.2 = 30 gwei)
        val expectedPrice = BigInteger.valueOf(30000000000L)
        assertEquals(expectedPrice, gasProvider.gasPrice)
        assertEquals(expectedPrice, gasProvider.getGasPrice("transfer"))
    }

    @Test
    fun `should use minimum gas price when network price is too low`() {
        val gasPriceRequest = mock<Request<*, EthGasPrice>>()
        val gasPriceResponse = mock<EthGasPrice>()
        val gasLimitsConfig = emptyMap<String, Long>()
        val minimumGasPriceWei = 6L
        
        whenever(web3j.ethGasPrice()).thenReturn(gasPriceRequest)
        whenever(gasPriceRequest.send()).thenReturn(gasPriceResponse)
        whenever(gasPriceResponse.gasPrice).thenReturn(BigInteger.valueOf(1L)) // Very low gas price
        
        val gasProvider = blockchainConfiguration.gasProvider(
            web3j, 1.2, minimumGasPriceWei, gasLimitsConfig
        )
        
        // Should use minimum price of 6 wei instead of 1.2 wei (1 * 1.2)
        assertEquals(BigInteger.valueOf(6L), gasProvider.gasPrice)
    }

    @Test
    fun `should fallback to minimum gas price when Web3j fails`() {
        val gasLimitsConfig = emptyMap<String, Long>()
        val minimumGasPriceWei = 10L
        
        whenever(web3j.ethGasPrice()).thenThrow(RuntimeException("Network error"))
        
        val gasProvider = blockchainConfiguration.gasProvider(
            web3j, 1.5, minimumGasPriceWei, gasLimitsConfig
        )
        
        assertEquals(BigInteger.valueOf(10L), gasProvider.gasPrice)
    }

    @Test
    fun `should return configured gas limit for known function`() {
        val gasPriceRequest = mock<Request<*, EthGasPrice>>()
        val gasPriceResponse = mock<EthGasPrice>()
        val gasLimitsConfig = mapOf("transfer" to 21000L, "approve" to 45000L)
        
        whenever(web3j.ethGasPrice()).thenReturn(gasPriceRequest)
        whenever(gasPriceRequest.send()).thenReturn(gasPriceResponse)
        whenever(gasPriceResponse.gasPrice).thenReturn(BigInteger.valueOf(25000000000L))
        
        val gasProvider = blockchainConfiguration.gasProvider(
            web3j, 1.2, 6L, gasLimitsConfig
        )
        
        assertEquals(BigInteger.valueOf(21000L), gasProvider.getGasLimit("transfer"))
        assertEquals(BigInteger.valueOf(45000L), gasProvider.getGasLimit("approve"))
    }

    @Test
    fun `should return default gas limit for unknown function`() {
        val gasPriceRequest = mock<Request<*, EthGasPrice>>()
        val gasPriceResponse = mock<EthGasPrice>()
        val gasLimitsConfig = mapOf("transfer" to 21000L)
        
        whenever(web3j.ethGasPrice()).thenReturn(gasPriceRequest)
        whenever(gasPriceRequest.send()).thenReturn(gasPriceResponse)
        whenever(gasPriceResponse.gasPrice).thenReturn(BigInteger.valueOf(25000000000L))
        
        val gasProvider = blockchainConfiguration.gasProvider(
            web3j, 1.2, 6L, gasLimitsConfig
        )
        
        assertEquals(DefaultGasProvider.GAS_LIMIT, gasProvider.getGasLimit("unknownFunction"))
        assertEquals(DefaultGasProvider.GAS_LIMIT, gasProvider.getGasLimit(null))
        assertEquals(DefaultGasProvider.GAS_LIMIT, gasProvider.gasLimit)
    }

    @Test
    fun `should handle null contract function in gas price`() {
        val gasPriceRequest = mock<Request<*, EthGasPrice>>()
        val gasPriceResponse = mock<EthGasPrice>()
        val gasLimitsConfig = emptyMap<String, Long>()
        
        whenever(web3j.ethGasPrice()).thenReturn(gasPriceRequest)
        whenever(gasPriceRequest.send()).thenReturn(gasPriceResponse)
        whenever(gasPriceResponse.gasPrice).thenReturn(BigInteger.valueOf(25000000000L))
        
        val gasProvider = blockchainConfiguration.gasProvider(
            web3j, 1.2, 6L, gasLimitsConfig
        )
        
        // Both methods should return the same price
        assertEquals(gasProvider.gasPrice, gasProvider.getGasPrice(null))
    }

    @Test
    fun `should handle complex gas price multiplier calculations`() {
        val gasPriceRequest = mock<Request<*, EthGasPrice>>()
        val gasPriceResponse = mock<EthGasPrice>()
        val gasLimitsConfig = emptyMap<String, Long>()
        
        whenever(web3j.ethGasPrice()).thenReturn(gasPriceRequest)
        whenever(gasPriceRequest.send()).thenReturn(gasPriceResponse)
        whenever(gasPriceResponse.gasPrice).thenReturn(BigInteger.valueOf(100000000000L)) // 100 gwei
        
        val gasProvider = blockchainConfiguration.gasProvider(
            web3j, 1.5, 6L, gasLimitsConfig
        )
        
        // 100 gwei * 1.5 = 150 gwei
        val expectedPrice = BigInteger.valueOf(150000000000L)
        assertEquals(expectedPrice, gasProvider.gasPrice)
    }
}