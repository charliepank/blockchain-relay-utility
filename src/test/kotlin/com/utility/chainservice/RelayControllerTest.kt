package com.utility.chainservice

import com.utility.chainservice.models.OperationGasCost
import com.utility.chainservice.plugin.BlockchainServicePlugin
import com.utility.chainservice.plugin.PluginConfiguration
import io.swagger.v3.oas.models.tags.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.mockito.kotlin.*
import org.springframework.http.HttpStatus
import java.math.BigInteger

class RelayControllerTest {

    private lateinit var blockchainRelayService: BlockchainRelayService
    private lateinit var pluginConfiguration: PluginConfiguration
    private lateinit var relayController: RelayController

    @BeforeEach
    fun setUp() {
        blockchainRelayService = mock()
        pluginConfiguration = mock()
        relayController = RelayController(blockchainRelayService, pluginConfiguration)
    }

    @Test
    fun `should return health check with active plugins`() {
        val mockPlugin1 = mock<BlockchainServicePlugin>()
        val mockPlugin2 = mock<BlockchainServicePlugin>()
        
        whenever(mockPlugin1.getPluginName()).thenReturn("escrow-plugin")
        whenever(mockPlugin2.getPluginName()).thenReturn("nft-plugin")
        
        whenever(pluginConfiguration.getActivePlugins()).thenReturn(listOf(mockPlugin1, mockPlugin2))

        val response = relayController.healthCheck()

        assertEquals(HttpStatus.OK, response.statusCode)
        
        val body = response.body as Map<*, *>
        assertEquals("healthy", body["status"])
        assertTrue(body.containsKey("timestamp"))
        assertEquals("blockchain-relay-utility", body["service"])
        
        val plugins = body["plugins"] as List<*>
        assertEquals(2, plugins.size)
        assertTrue(plugins.contains("escrow-plugin"))
        assertTrue(plugins.contains("nft-plugin"))
    }

    @Test
    fun `should return health check with no plugins`() {
        whenever(pluginConfiguration.getActivePlugins()).thenReturn(emptyList())

        val response = relayController.healthCheck()

        assertEquals(HttpStatus.OK, response.statusCode)
        
        val body = response.body as Map<*, *>
        assertEquals("healthy", body["status"])
        assertEquals("blockchain-relay-utility", body["service"])
        
        val plugins = body["plugins"] as List<*>
        assertTrue(plugins.isEmpty())
    }

    @Test
    fun `should return gas costs for all plugin operations`() {
        val mockPlugin1 = mock<BlockchainServicePlugin>()
        val mockPlugin2 = mock<BlockchainServicePlugin>()
        
        whenever(mockPlugin1.getPluginName()).thenReturn("escrow-plugin")
        whenever(mockPlugin1.getGasOperations()).thenReturn(listOf(
            "createEscrow" to "createEscrowFunction",
            "releaseEscrow" to "releaseEscrowFunction"
        ))
        
        whenever(mockPlugin2.getPluginName()).thenReturn("nft-plugin")
        whenever(mockPlugin2.getGasOperations()).thenReturn(listOf(
            "mintNFT" to "mintFunction"
        ))
        
        whenever(pluginConfiguration.getActivePlugins()).thenReturn(listOf(mockPlugin1, mockPlugin2))
        
        val mockGasCosts = listOf(
            OperationGasCost(
                operation = "createEscrow",
                gasLimit = 150000L,
                gasPriceWei = BigInteger.valueOf(25000000000L),
                totalCostWei = BigInteger.valueOf(3750000000000000L),
                totalCostAvax = "0.00375"
            ),
            OperationGasCost(
                operation = "releaseEscrow",
                gasLimit = 100000L,
                gasPriceWei = BigInteger.valueOf(25000000000L),
                totalCostWei = BigInteger.valueOf(2500000000000000L),
                totalCostAvax = "0.0025"
            ),
            OperationGasCost(
                operation = "mintNFT",
                gasLimit = 200000L,
                gasPriceWei = BigInteger.valueOf(25000000000L),
                totalCostWei = BigInteger.valueOf(5000000000000000L),
                totalCostAvax = "0.005"
            )
        )
        
        whenever(blockchainRelayService.getOperationGasCosts(any())).thenReturn(mockGasCosts)

        val response = relayController.getGasCosts()

        assertEquals(HttpStatus.OK, response.statusCode)
        
        val body = response.body as RelayController.GasCostsResponse
        assertNotNull(body.timestamp)
        assertEquals(3, body.operations.size)
        
        val createEscrowCost = body.operations.find { it.operation == "createEscrow" }
        assertNotNull(createEscrowCost)
        assertEquals(150000L, createEscrowCost!!.gasLimit)
        assertEquals("0.00375", createEscrowCost.totalCostAvax)
        
        val mintNFTCost = body.operations.find { it.operation == "mintNFT" }
        assertNotNull(mintNFTCost)
        assertEquals(200000L, mintNFTCost!!.gasLimit)
        assertEquals("0.005", mintNFTCost.totalCostAvax)
    }

    @Test
    fun `should return empty gas costs when no plugins active`() {
        whenever(pluginConfiguration.getActivePlugins()).thenReturn(emptyList())
        whenever(blockchainRelayService.getOperationGasCosts(emptyList())).thenReturn(emptyList())

        val response = relayController.getGasCosts()

        assertEquals(HttpStatus.OK, response.statusCode)
        
        val body = response.body as RelayController.GasCostsResponse
        assertNotNull(body.timestamp)
        assertTrue(body.operations.isEmpty())
    }

    @Test
    fun `should handle exception in health check gracefully`() {
        whenever(pluginConfiguration.getActivePlugins()).thenThrow(RuntimeException("Plugin error"))

        val response = relayController.healthCheck()

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.statusCode)
        
        val body = response.body as Map<*, *>
        assertEquals("unhealthy", body["status"])
        assertEquals("Plugin error", body["error"])
        assertTrue(body.containsKey("timestamp"))
        assertEquals("blockchain-relay-utility", body["service"])
    }

    @Test
    fun `should handle exception in gas costs gracefully`() {
        val mockPlugin = mock<BlockchainServicePlugin>()
        whenever(mockPlugin.getPluginName()).thenReturn("test-plugin")
        whenever(mockPlugin.getGasOperations()).thenReturn(listOf("test" to "testFunction"))
        
        whenever(pluginConfiguration.getActivePlugins()).thenReturn(listOf(mockPlugin))
        whenever(blockchainRelayService.getOperationGasCosts(any()))
            .thenThrow(RuntimeException("Gas calculation error"))

        val response = relayController.getGasCosts()

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.statusCode)
        
        val body = response.body
        assertNotNull(body)
        // The error response should be a GasCostsResponse with empty operations
        assertTrue(body is RelayController.GasCostsResponse)
        val errorResponse = body as RelayController.GasCostsResponse
        assertTrue(errorResponse.operations.isEmpty())
        assertNotNull(errorResponse.timestamp)
    }

    @Test
    fun `should collect all gas operations from multiple plugins`() {
        val mockPlugin1 = mock<BlockchainServicePlugin>()
        val mockPlugin2 = mock<BlockchainServicePlugin>()
        val mockPlugin3 = mock<BlockchainServicePlugin>()
        
        whenever(mockPlugin1.getPluginName()).thenReturn("plugin1")
        whenever(mockPlugin1.getGasOperations()).thenReturn(listOf(
            "operation1" to "function1",
            "operation2" to "function2"
        ))
        
        whenever(mockPlugin2.getPluginName()).thenReturn("plugin2")
        whenever(mockPlugin2.getGasOperations()).thenReturn(listOf(
            "operation3" to "function3"
        ))
        
        whenever(mockPlugin3.getPluginName()).thenReturn("plugin3")
        whenever(mockPlugin3.getGasOperations()).thenReturn(emptyList())
        
        whenever(pluginConfiguration.getActivePlugins()).thenReturn(listOf(mockPlugin1, mockPlugin2, mockPlugin3))
        whenever(blockchainRelayService.getOperationGasCosts(any())).thenReturn(emptyList())

        relayController.getGasCosts()

        verify(blockchainRelayService).getOperationGasCosts(argThat { operations ->
            operations.size == 3 &&
            operations.contains("operation1" to "function1") &&
            operations.contains("operation2" to "function2") &&
            operations.contains("operation3" to "function3")
        })
    }

    @Test
    fun `should verify timestamp format in responses`() {
        whenever(pluginConfiguration.getActivePlugins()).thenReturn(emptyList())

        val response = relayController.healthCheck()
        val body = response.body as Map<*, *>
        val timestamp = body["timestamp"] as String
        
        // Verify timestamp is in ISO format (basic check) - matches Instant.now().toString() format
        assertTrue(timestamp.matches(Regex("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(\\.\\d+)?Z")))
    }

    @Test
    fun `should return gas costs response with correct structure`() {
        val mockPlugin = mock<BlockchainServicePlugin>()
        whenever(mockPlugin.getPluginName()).thenReturn("test-plugin")
        whenever(mockPlugin.getGasOperations()).thenReturn(listOf("test" to "testFunction"))
        
        whenever(pluginConfiguration.getActivePlugins()).thenReturn(listOf(mockPlugin))
        
        val mockGasCost = OperationGasCost(
            operation = "test",
            gasLimit = 21000L,
            gasPriceWei = BigInteger.valueOf(25000000000L),
            totalCostWei = BigInteger.valueOf(525000000000000L),
            totalCostAvax = "0.000525"
        )
        
        whenever(blockchainRelayService.getOperationGasCosts(any())).thenReturn(listOf(mockGasCost))

        val response = relayController.getGasCosts()

        assertEquals(HttpStatus.OK, response.statusCode)
        assertNotNull(response.body)
        assertTrue(response.body is RelayController.GasCostsResponse)
        
        val body = response.body as RelayController.GasCostsResponse
        assertEquals(1, body.operations.size)
        assertEquals("test", body.operations[0].operation)
        assertNotNull(body.timestamp)
    }

    @Test
    fun `should handle single plugin with multiple operations`() {
        val mockPlugin = mock<BlockchainServicePlugin>()
        whenever(mockPlugin.getPluginName()).thenReturn("multi-op-plugin")
        whenever(mockPlugin.getGasOperations()).thenReturn(listOf(
            "op1" to "function1",
            "op2" to "function2",
            "op3" to "function3"
        ))
        
        whenever(pluginConfiguration.getActivePlugins()).thenReturn(listOf(mockPlugin))
        whenever(blockchainRelayService.getOperationGasCosts(any())).thenReturn(emptyList())

        relayController.getGasCosts()

        verify(blockchainRelayService).getOperationGasCosts(argThat { operations ->
            operations.size == 3 &&
            operations.map { it.first }.containsAll(listOf("op1", "op2", "op3"))
        })
    }
}