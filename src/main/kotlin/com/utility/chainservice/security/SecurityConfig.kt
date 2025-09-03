package com.utility.chainservice.security

import com.fasterxml.jackson.annotation.JsonProperty
import java.time.Instant

data class SecurityConfiguration(
    @JsonProperty("apiKeys")
    val apiKeys: List<ApiKeyConfig> = emptyList(),
    
    @JsonProperty("globalIpWhitelist")
    val globalIpWhitelist: List<String> = listOf("127.0.0.1", "::1"),
    
    @JsonProperty("settings")
    val settings: SecuritySettings = SecuritySettings(),
    
    @JsonProperty("lastModified")
    val lastModified: String = Instant.now().toString()
)

data class ApiKeyConfig(
    @JsonProperty("key")
    val key: String,
    
    @JsonProperty("name")
    val name: String,
    
    @JsonProperty("allowedIps")
    val allowedIps: List<String> = emptyList(),
    
    @JsonProperty("enabled")
    val enabled: Boolean = true,
    
    @JsonProperty("description")
    val description: String? = null,
    
    @JsonProperty("walletConfig")
    val walletConfig: WalletConfig? = null,
    
    @JsonProperty("createdAt")
    val createdAt: String = Instant.now().toString()
)

data class WalletConfig(
    @JsonProperty("privateKey")
    val privateKey: String,
    
    @JsonProperty("address")
    val address: String? = null
)

data class SecuritySettings(
    @JsonProperty("requireApiKey")
    val requireApiKey: Boolean = true,
    
    @JsonProperty("enforceIpWhitelist")
    val enforceIpWhitelist: Boolean = true,
    
    @JsonProperty("logFailedAttempts")
    val logFailedAttempts: Boolean = true,
    
    @JsonProperty("rateLimitEnabled")
    val rateLimitEnabled: Boolean = false,
    
    @JsonProperty("rateLimitRequestsPerMinute")
    val rateLimitRequestsPerMinute: Int = 60
)

data class SecurityValidationResult(
    val success: Boolean,
    val apiKeyName: String? = null,
    val clientIp: String? = null,
    val error: String? = null,
    val timestamp: Instant = Instant.now()
)