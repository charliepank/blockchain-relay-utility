package com.utility.chainservice.plugin

import com.utility.chainservice.AuthenticationProvider
import com.utility.chainservice.BlockchainRelayService
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Configuration
import jakarta.annotation.PostConstruct

@Configuration
class PluginConfiguration(
    private val blockchainRelayService: BlockchainRelayService,
    private val authenticationProvider: AuthenticationProvider
) {

    private val logger = LoggerFactory.getLogger(PluginConfiguration::class.java)

    @Autowired(required = false)
    private var plugins: List<BlockchainServicePlugin> = emptyList()

    @PostConstruct
    fun initializePlugins() {
        logger.info("Initializing ${plugins.size} blockchain service plugins")
        
        plugins.forEach { plugin ->
            try {
                logger.info("Initializing plugin: ${plugin.getPluginName()}")
                plugin.initialize(blockchainRelayService, authenticationProvider)
                logger.info("Successfully initialized plugin: ${plugin.getPluginName()}")
            } catch (e: Exception) {
                logger.error("Failed to initialize plugin: ${plugin.getPluginName()}", e)
                throw IllegalStateException("Plugin initialization failed: ${plugin.getPluginName()}", e)
            }
        }
        
        if (plugins.isEmpty()) {
            logger.warn("No blockchain service plugins found - only generic relay functionality will be available")
        }
    }

    fun getActivePlugins(): List<BlockchainServicePlugin> = plugins
}