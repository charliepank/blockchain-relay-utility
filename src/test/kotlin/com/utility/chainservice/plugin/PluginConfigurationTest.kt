package com.utility.chainservice.plugin

import com.utility.chainservice.BlockchainRelayService
import io.swagger.v3.oas.models.tags.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.mockito.kotlin.*

class PluginConfigurationTest {

    private lateinit var blockchainRelayService: BlockchainRelayService
    private lateinit var pluginConfiguration: PluginConfiguration

    @BeforeEach
    fun setUp() {
        blockchainRelayService = mock()
    }

    private open class TestPlugin(
        private val name: String,
        private val shouldFailInit: Boolean = false
    ) : BlockchainServicePlugin {
        var isInitialized = false
        
        override fun getPluginName(): String = name
        override fun getApiPrefix(): String = "/api/$name"
        override fun getOpenApiTags(): List<Tag> {
            val tag = Tag()
            tag.name = name
            tag.description = "Test plugin $name"
            return listOf(tag)
        }
        
        override fun initialize(relayService: BlockchainRelayService) {
            if (shouldFailInit) {
                throw RuntimeException("Plugin initialization failed for $name")
            }
            isInitialized = true
        }
        
        override fun getGasOperations(): List<Pair<String, String>> = listOf(
            "${name}Operation" to "${name}Function"
        )
    }

    @Test
    fun `should initialize plugins successfully`() {
        val plugin1 = TestPlugin("test-plugin-1")
        val plugin2 = TestPlugin("test-plugin-2")
        val plugins = listOf(plugin1, plugin2)
        
        pluginConfiguration = PluginConfiguration(blockchainRelayService)
        
        // Use reflection to set the plugins list
        val pluginsField = PluginConfiguration::class.java.getDeclaredField("plugins")
        pluginsField.isAccessible = true
        pluginsField.set(pluginConfiguration, plugins)
        
        // Call initializePlugins
        pluginConfiguration.initializePlugins()
        
        assertTrue(plugin1.isInitialized)
        assertTrue(plugin2.isInitialized)
        
        val activePlugins = pluginConfiguration.getActivePlugins()
        assertEquals(2, activePlugins.size)
        assertTrue(activePlugins.contains(plugin1))
        assertTrue(activePlugins.contains(plugin2))
    }

    @Test
    fun `should handle no plugins gracefully`() {
        pluginConfiguration = PluginConfiguration(blockchainRelayService)
        
        // Plugins list is empty by default
        
        // Should not throw exception
        assertDoesNotThrow {
            pluginConfiguration.initializePlugins()
        }
        
        val activePlugins = pluginConfiguration.getActivePlugins()
        assertTrue(activePlugins.isEmpty())
    }

    @Test
    fun `should throw exception when plugin initialization fails`() {
        val goodPlugin = TestPlugin("good-plugin")
        val badPlugin = TestPlugin("bad-plugin", shouldFailInit = true)
        val plugins = listOf(goodPlugin, badPlugin)
        
        pluginConfiguration = PluginConfiguration(blockchainRelayService)
        
        // Use reflection to set the plugins list
        val pluginsField = PluginConfiguration::class.java.getDeclaredField("plugins")
        pluginsField.isAccessible = true
        pluginsField.set(pluginConfiguration, plugins)
        
        val exception = assertThrows(IllegalStateException::class.java) {
            pluginConfiguration.initializePlugins()
        }
        
        assertEquals("Plugin initialization failed: bad-plugin", exception.message)
        assertTrue(exception.cause is RuntimeException)
        
        // Good plugin should still be initialized before the failure
        assertTrue(goodPlugin.isInitialized)
        assertFalse(badPlugin.isInitialized)
    }

    @Test
    fun `should initialize plugins with correct services`() {
        val plugin = spy(TestPlugin("spy-plugin"))
        val plugins = listOf(plugin)
        
        pluginConfiguration = PluginConfiguration(blockchainRelayService)
        
        // Use reflection to set the plugins list
        val pluginsField = PluginConfiguration::class.java.getDeclaredField("plugins")
        pluginsField.isAccessible = true
        pluginsField.set(pluginConfiguration, plugins)
        
        pluginConfiguration.initializePlugins()
        
        verify(plugin).initialize(blockchainRelayService)
        assertTrue(plugin.isInitialized)
    }

    @Test
    fun `should get active plugins after initialization`() {
        val plugin1 = TestPlugin("plugin-1")
        val plugin2 = TestPlugin("plugin-2")
        val plugin3 = TestPlugin("plugin-3")
        val plugins = listOf(plugin1, plugin2, plugin3)
        
        pluginConfiguration = PluginConfiguration(blockchainRelayService)
        
        // Use reflection to set the plugins list
        val pluginsField = PluginConfiguration::class.java.getDeclaredField("plugins")
        pluginsField.isAccessible = true
        pluginsField.set(pluginConfiguration, plugins)
        
        pluginConfiguration.initializePlugins()
        
        val activePlugins = pluginConfiguration.getActivePlugins()
        assertEquals(3, activePlugins.size)
        assertEquals(plugins, activePlugins)
        
        // Verify all plugins are initialized
        plugins.forEach { plugin ->
            assertTrue(plugin.isInitialized)
        }
    }

    @Test
    fun `should handle plugin initialization order`() {
        val initOrder = mutableListOf<String>()
        
        val plugin1 = object : TestPlugin("first-plugin") {
            override fun initialize(relayService: BlockchainRelayService) {
                super.initialize(relayService)
                initOrder.add("first-plugin")
            }
        }
        
        val plugin2 = object : TestPlugin("second-plugin") {
            override fun initialize(relayService: BlockchainRelayService) {
                super.initialize(relayService)
                initOrder.add("second-plugin")
            }
        }
        
        val plugin3 = object : TestPlugin("third-plugin") {
            override fun initialize(relayService: BlockchainRelayService) {
                super.initialize(relayService)
                initOrder.add("third-plugin")
            }
        }
        
        val plugins = listOf(plugin1, plugin2, plugin3)
        
        pluginConfiguration = PluginConfiguration(blockchainRelayService)
        
        // Use reflection to set the plugins list
        val pluginsField = PluginConfiguration::class.java.getDeclaredField("plugins")
        pluginsField.isAccessible = true
        pluginsField.set(pluginConfiguration, plugins)
        
        pluginConfiguration.initializePlugins()
        
        assertEquals(listOf("first-plugin", "second-plugin", "third-plugin"), initOrder)
    }

    @Test
    fun `should handle mixed successful and failed plugin initialization`() {
        val plugin1 = TestPlugin("success-plugin-1")
        val plugin2 = TestPlugin("failure-plugin", shouldFailInit = true)
        val plugin3 = TestPlugin("success-plugin-2") // This should not be initialized due to failure
        val plugins = listOf(plugin1, plugin2, plugin3)
        
        pluginConfiguration = PluginConfiguration(blockchainRelayService)
        
        // Use reflection to set the plugins list
        val pluginsField = PluginConfiguration::class.java.getDeclaredField("plugins")
        pluginsField.isAccessible = true
        pluginsField.set(pluginConfiguration, plugins)
        
        assertThrows(IllegalStateException::class.java) {
            pluginConfiguration.initializePlugins()
        }
        
        // First plugin should be initialized
        assertTrue(plugin1.isInitialized)
        // Failed plugin should not be initialized
        assertFalse(plugin2.isInitialized)
        // Third plugin should not be reached due to failure
        assertFalse(plugin3.isInitialized)
    }

    @Test
    fun `should provide plugin information through interface methods`() {
        val plugin = TestPlugin("info-plugin")
        val plugins = listOf(plugin)
        
        pluginConfiguration = PluginConfiguration(blockchainRelayService)
        
        // Use reflection to set the plugins list
        val pluginsField = PluginConfiguration::class.java.getDeclaredField("plugins")
        pluginsField.isAccessible = true
        pluginsField.set(pluginConfiguration, plugins)
        
        pluginConfiguration.initializePlugins()
        
        val activePlugins = pluginConfiguration.getActivePlugins()
        val retrievedPlugin = activePlugins[0]
        
        assertEquals("info-plugin", retrievedPlugin.getPluginName())
        assertEquals("/api/info-plugin", retrievedPlugin.getApiPrefix())
        assertEquals(1, retrievedPlugin.getGasOperations().size)
        assertEquals("info-pluginOperation", retrievedPlugin.getGasOperations()[0].first)
        assertEquals("info-pluginFunction", retrievedPlugin.getGasOperations()[0].second)
        
        val tags = retrievedPlugin.getOpenApiTags()
        assertEquals(1, tags.size)
        assertEquals("info-plugin", tags[0].name)
        assertEquals("Test plugin info-plugin", tags[0].description)
    }
}