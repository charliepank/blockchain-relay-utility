package com.utility.chainservice.security

import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse

@Component
class ApiKeyAuthenticationFilter(
    private val securityConfigurationService: SecurityConfigurationService
) : OncePerRequestFilter() {

    private val logger = LoggerFactory.getLogger(ApiKeyAuthenticationFilter::class.java)
    private val objectMapper = ObjectMapper()

    companion object {
        const val API_KEY_HEADER = "X-API-Key"
        const val API_KEY_QUERY_PARAM = "api_key"
    }

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        try {
            // Skip security for health check endpoints
            if (isHealthCheckEndpoint(request.requestURI)) {
                filterChain.doFilter(request, response)
                return
            }

            // Check if security is enabled
            if (!securityConfigurationService.isSecurityEnabled()) {
                this.logger.debug("Security disabled, allowing request")
                filterChain.doFilter(request, response)
                return
            }

            val clientIp = getClientIpAddress(request)
            val apiKey = extractApiKey(request)

            val validationResult = validateRequest(apiKey, clientIp)

            if (validationResult.success) {
                // Add client info to request attributes for logging
                request.setAttribute("client.ip", clientIp)
                request.setAttribute("client.name", validationResult.apiKeyName)
                
                this.logger.debug("Request authorized: client=${validationResult.apiKeyName}, ip=$clientIp")
                filterChain.doFilter(request, response)
            } else {
                this.logger.warn("Unauthorized request: ip=$clientIp, error=${validationResult.error}")
                sendUnauthorizedResponse(response, validationResult.error ?: "Unauthorized")
            }
        } catch (e: Exception) {
            this.logger.error("Error in authentication filter", e)
            sendUnauthorizedResponse(response, "Authentication error")
        }
    }

    private fun isHealthCheckEndpoint(uri: String): Boolean {
        val healthEndpoints = listOf(
            "/actuator/health",
            "/health",
            "/ping",
            "/status"
        )
        return healthEndpoints.any { uri.startsWith(it) }
    }

    private fun extractApiKey(request: HttpServletRequest): String? {
        // Try header first
        var apiKey = request.getHeader(API_KEY_HEADER)
        
        // Try Authorization header with "Bearer" prefix
        if (apiKey == null) {
            val authHeader = request.getHeader("Authorization")
            if (authHeader?.startsWith("Bearer ") == true) {
                apiKey = authHeader.substring(7)
            }
        }
        
        // Try query parameter as fallback
        if (apiKey == null) {
            apiKey = request.getParameter(API_KEY_QUERY_PARAM)
        }

        return apiKey?.trim()?.takeIf { it.isNotEmpty() }
    }

    private fun getClientIpAddress(request: HttpServletRequest): String {
        // Check common proxy headers
        val headerNames = arrayOf(
            "X-Forwarded-For",
            "X-Real-IP",
            "X-Client-IP",
            "CF-Connecting-IP", // Cloudflare
            "True-Client-IP"
        )

        for (headerName in headerNames) {
            val headerValue = request.getHeader(headerName)
            if (!headerValue.isNullOrEmpty() && headerValue != "unknown") {
                // X-Forwarded-For can contain multiple IPs, take the first one
                val ip = headerValue.split(",")[0].trim()
                if (ip.isNotEmpty()) {
                    return ip
                }
            }
        }

        return request.remoteAddr ?: "unknown"
    }

    private fun validateRequest(apiKey: String?, clientIp: String): SecurityValidationResult {
        try {
            // Validate API key
            val apiKeyConfig = securityConfigurationService.validateApiKey(apiKey)
            if (apiKeyConfig == null) {
                return SecurityValidationResult(
                    success = false,
                    clientIp = clientIp,
                    error = "Invalid or missing API key"
                )
            }

            // Validate IP whitelist if enforced
            if (securityConfigurationService.shouldEnforceIpWhitelist()) {
                if (!securityConfigurationService.isIpAllowed(clientIp, apiKeyConfig)) {
                    return SecurityValidationResult(
                        success = false,
                        clientIp = clientIp,
                        apiKeyName = apiKeyConfig.name,
                        error = "IP address not allowed"
                    )
                }
            }

            return SecurityValidationResult(
                success = true,
                clientIp = clientIp,
                apiKeyName = apiKeyConfig.name
            )
        } catch (e: Exception) {
            this.logger.error("Error validating request", e)
            return SecurityValidationResult(
                success = false,
                clientIp = clientIp,
                error = "Validation error"
            )
        }
    }

    private fun sendUnauthorizedResponse(response: HttpServletResponse, errorMessage: String) {
        response.status = HttpStatus.UNAUTHORIZED.value()
        response.contentType = MediaType.APPLICATION_JSON_VALUE

        val errorResponse = mapOf(
            "error" to "Unauthorized",
            "message" to errorMessage,
            "timestamp" to System.currentTimeMillis()
        )

        try {
            objectMapper.writeValue(response.outputStream, errorResponse)
        } catch (e: Exception) {
            this.logger.error("Error writing unauthorized response", e)
        }
    }
}