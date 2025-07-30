package com.utility.chainservice.plugin

import com.utility.chainservice.AuthenticationProvider
import com.utility.chainservice.BlockchainRelayService
import io.swagger.v3.oas.models.tags.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.mockito.kotlin.mock

class BlockchainServicePluginTest {

    private class TestPlugin : BlockchainServicePlugin {
        var isInitialized = false
        
        override fun getPluginName(): String = "test-plugin"
        override fun getApiPrefix(): String = "/api/test"
        override fun getOpenApiTags(): List<Tag> = listOf(
            Tag().apply {
                name = "Test"
                description = "Test plugin"
            }
        )
        
        override fun initialize(relayService: BlockchainRelayService, authProvider: AuthenticationProvider) {
            isInitialized = true
        }
        
        override fun getGasOperations(): List<Pair<String, String>> = listOf(
            "test" to "testOperation"
        )
    }

    @Test
    fun `should implement plugin interface correctly`() {
        val plugin = TestPlugin()
        
        assertEquals("test-plugin", plugin.getPluginName())
        assertEquals("/api/test", plugin.getApiPrefix())
        assertEquals(1, plugin.getOpenApiTags().size)
        assertEquals("Test", plugin.getOpenApiTags()[0].name)
        assertEquals(1, plugin.getGasOperations().size)
        assertEquals("test", plugin.getGasOperations()[0].first)
        assertEquals("testOperation", plugin.getGasOperations()[0].second)
    }

    @Test
    fun `should initialize plugin with services`() {
        val plugin = TestPlugin()
        val mockRelayService = mock<BlockchainRelayService>()
        val mockAuthProvider = mock<AuthenticationProvider>()
        
        assertFalse(plugin.isInitialized)
        
        plugin.initialize(mockRelayService, mockAuthProvider)
        
        assertTrue(plugin.isInitialized)
    }
}