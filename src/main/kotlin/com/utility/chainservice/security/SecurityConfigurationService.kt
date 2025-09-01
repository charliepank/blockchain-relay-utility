package com.utility.chainservice.security

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.io.File
import java.nio.file.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference
import jakarta.annotation.PostConstruct
import jakarta.annotation.PreDestroy
import kotlin.concurrent.thread

@Service
class SecurityConfigurationService(
    private val securityConfigPath: String
) {
    private val logger = LoggerFactory.getLogger(SecurityConfigurationService::class.java)
    private val objectMapper = ObjectMapper().registerKotlinModule()
    
    private val currentConfig = AtomicReference<SecurityConfiguration>()
    private val apiKeyCache = ConcurrentHashMap<String, ApiKeyConfig>()
    private var watchService: WatchService? = null
    private var watchThread: Thread? = null
    
    @PostConstruct
    fun initialize() {
        try {
            // Load initial configuration
            loadConfiguration()
            
            // Start file watcher
            startFileWatcher()
            
            logger.info("Security configuration service initialized with ${currentConfig.get()?.apiKeys?.size ?: 0} API keys")
        } catch (e: Exception) {
            logger.error("Failed to initialize security configuration service", e)
            // Set default empty configuration to prevent null pointer issues
            currentConfig.set(SecurityConfiguration())
        }
    }
    
    @PreDestroy
    fun cleanup() {
        try {
            watchService?.close()
            watchThread?.interrupt()
            logger.info("Security configuration service cleaned up")
        } catch (e: Exception) {
            logger.warn("Error during cleanup", e)
        }
    }
    
    private fun loadConfiguration() {
        try {
            val configFile = File(securityConfigPath)
            
            if (!configFile.exists()) {
                logger.warn("Security configuration file not found at: $securityConfigPath")
                createDefaultConfigFile(configFile)
                return
            }
            
            val config = objectMapper.readValue(configFile, SecurityConfiguration::class.java)
            currentConfig.set(config)
            
            // Update cache
            apiKeyCache.clear()
            config.apiKeys.forEach { apiKey ->
                if (apiKey.enabled) {
                    apiKeyCache[apiKey.key] = apiKey
                }
            }
            
            logger.info("Loaded security configuration: ${config.apiKeys.size} API keys, ${config.globalIpWhitelist.size} global IPs")
        } catch (e: Exception) {
            logger.error("Failed to load security configuration from: $securityConfigPath", e)
            throw e
        }
    }
    
    private fun createDefaultConfigFile(configFile: File) {
        try {
            configFile.parentFile?.mkdirs()
            
            val defaultConfig = SecurityConfiguration(
                apiKeys = listOf(
                    ApiKeyConfig(
                        key = "example_api_key_replace_me",
                        name = "Example Client",
                        allowedIps = listOf("127.0.0.1"),
                        description = "Example API key - replace with real keys"
                    )
                ),
                globalIpWhitelist = listOf("127.0.0.1", "::1"),
                settings = SecuritySettings(
                    requireApiKey = true,
                    enforceIpWhitelist = true,
                    logFailedAttempts = true
                )
            )
            
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(configFile, defaultConfig)
            currentConfig.set(defaultConfig)
            
            logger.info("Created default security configuration file at: $securityConfigPath")
        } catch (e: Exception) {
            logger.error("Failed to create default security configuration file", e)
            throw e
        }
    }
    
    private fun startFileWatcher() {
        try {
            val configFile = File(securityConfigPath)
            val parentDir = configFile.parentFile?.toPath() ?: return
            
            watchService = FileSystems.getDefault().newWatchService()
            parentDir.register(watchService, StandardWatchEventKinds.ENTRY_MODIFY)
            
            watchThread = thread(name = "security-config-watcher") {
                while (!Thread.currentThread().isInterrupted) {
                    try {
                        val watchKey = watchService?.take() ?: break
                        
                        for (event in watchKey.pollEvents()) {
                            val changedFile = event.context() as Path
                            if (changedFile.toString() == configFile.name) {
                                logger.info("Security configuration file changed, reloading...")
                                Thread.sleep(100) // Brief delay to ensure file write is complete
                                loadConfiguration()
                            }
                        }
                        
                        val valid = watchKey.reset()
                        if (!valid) {
                            break
                        }
                    } catch (e: InterruptedException) {
                        logger.info("File watcher interrupted")
                        break
                    } catch (e: Exception) {
                        logger.error("Error in file watcher", e)
                    }
                }
            }
            
            logger.info("Started file watcher for security configuration")
        } catch (e: Exception) {
            logger.error("Failed to start file watcher", e)
        }
    }
    
    fun validateApiKey(apiKey: String?): ApiKeyConfig? {
        if (apiKey == null || apiKey.isBlank()) {
            return null
        }
        return apiKeyCache[apiKey]
    }
    
    fun isIpAllowed(clientIp: String, apiKeyConfig: ApiKeyConfig? = null): Boolean {
        try {
            val config = currentConfig.get() ?: return false
            
            // Check global whitelist first
            if (isIpInWhitelist(clientIp, config.globalIpWhitelist)) {
                return true
            }
            
            // Check API key specific whitelist
            apiKeyConfig?.let { keyConfig ->
                if (keyConfig.allowedIps.isEmpty()) {
                    return true // No IP restrictions for this key
                }
                return isIpInWhitelist(clientIp, keyConfig.allowedIps)
            }
            
            return false
        } catch (e: Exception) {
            logger.error("Error validating IP address: $clientIp", e)
            return false
        }
    }
    
    private fun isIpInWhitelist(clientIp: String, whitelist: List<String>): Boolean {
        for (allowedIp in whitelist) {
            if (isIpMatching(clientIp, allowedIp)) {
                return true
            }
        }
        return false
    }
    
    private fun isIpMatching(clientIp: String, allowedPattern: String): Boolean {
        try {
            // Exact match
            if (clientIp == allowedPattern) {
                return true
            }
            
            // CIDR notation support
            if (allowedPattern.contains("/")) {
                return isIpInCidr(clientIp, allowedPattern)
            }
            
            // Wildcard support (e.g., 192.168.1.*)
            if (allowedPattern.contains("*")) {
                val pattern = allowedPattern.replace("*", ".*")
                return clientIp.matches(Regex(pattern))
            }
            
            return false
        } catch (e: Exception) {
            logger.warn("Error matching IP $clientIp against pattern $allowedPattern", e)
            return false
        }
    }
    
    private fun isIpInCidr(clientIp: String, cidr: String): Boolean {
        try {
            val parts = cidr.split("/")
            if (parts.size != 2) return false
            
            val networkAddress = parts[0]
            val prefixLength = parts[1].toInt()
            
            // For IPv4 addresses
            if (networkAddress.contains(".") && clientIp.contains(".")) {
                val clientBytes = ipv4ToBytes(clientIp)
                val networkBytes = ipv4ToBytes(networkAddress)
                
                val mask = (-1L shl (32 - prefixLength)).toInt()
                val clientNetwork = bytesToInt(clientBytes) and mask
                val targetNetwork = bytesToInt(networkBytes) and mask
                
                return clientNetwork == targetNetwork
            }
            
            // IPv6 support could be added here if needed
            return false
        } catch (e: Exception) {
            logger.warn("Error processing CIDR: $cidr for IP: $clientIp", e)
            return false
        }
    }
    
    private fun ipv4ToBytes(ip: String): ByteArray {
        return ip.split(".").map { it.toInt().toByte() }.toByteArray()
    }
    
    private fun bytesToInt(bytes: ByteArray): Int {
        return ((bytes[0].toInt() and 0xFF) shl 24) or
               ((bytes[1].toInt() and 0xFF) shl 16) or
               ((bytes[2].toInt() and 0xFF) shl 8) or
               (bytes[3].toInt() and 0xFF)
    }
    
    fun getConfiguration(): SecurityConfiguration? {
        return currentConfig.get()
    }
    
    fun isSecurityEnabled(): Boolean {
        return currentConfig.get()?.settings?.requireApiKey ?: true
    }
    
    fun shouldEnforceIpWhitelist(): Boolean {
        return currentConfig.get()?.settings?.enforceIpWhitelist ?: true
    }
}