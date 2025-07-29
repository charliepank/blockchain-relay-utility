package com.utility.chainservice

import com.utility.chainservice.models.AuthenticationResult
import com.utility.chainservice.models.UserIdentity
import org.slf4j.LoggerFactory
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientResponseException
import reactor.core.publisher.Mono

class HttpAuthenticationProvider(
    private val userServiceUrl: String,
    private val enabled: Boolean
) : AuthenticationProvider {

    private val logger = LoggerFactory.getLogger(HttpAuthenticationProvider::class.java)
    
    private val webClient = WebClient.builder()
        .baseUrl(userServiceUrl)
        .build()

    data class UserIdentityResponse(
        val userId: String,
        val email: String,
        val walletAddress: String,
        val userType: String
    )

    override fun validateToken(authToken: String, httpOnlyToken: String?): Mono<AuthenticationResult> {
        return try {
            val request = webClient.get()
                .uri("/api/user/identity")
                .header("Authorization", authToken)
                
            // Add http-only token as header if provided
            val requestWithCookie = if (httpOnlyToken != null) {
                request.header("Cookie", "session=$httpOnlyToken")
            } else {
                request
            }
                
            requestWithCookie
                .retrieve()
                .bodyToMono(UserIdentityResponse::class.java)
                .map { userIdentity ->
                    AuthenticationResult(
                        success = true,
                        userIdentity = UserIdentity(
                            userId = userIdentity.userId,
                            email = userIdentity.email,
                            walletAddress = userIdentity.walletAddress,
                            userType = userIdentity.userType
                        )
                    )
                }
                .doOnSuccess { response ->
                    logger.debug("Token validation successful for user: ${response.userIdentity?.userId}")
                }
                .onErrorResume { error ->
                    when (error) {
                        is WebClientResponseException -> {
                            logger.warn("Token validation failed with status: ${error.statusCode}")
                            Mono.just(AuthenticationResult(success = false, error = "Invalid token"))
                        }
                        else -> {
                            logger.error("Error communicating with user service", error)
                            Mono.just(AuthenticationResult(success = false, error = "Authentication service unavailable"))
                        }
                    }
                }
        } catch (e: Exception) {
            logger.error("Error validating token", e)
            Mono.just(AuthenticationResult(success = false, error = "Authentication failed"))
        }
    }

    override fun isAuthEnabled(): Boolean = enabled
}