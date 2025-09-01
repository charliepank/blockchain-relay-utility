package com.utility.chainservice.security

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.web.servlet.FilterRegistrationBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.Ordered

@Configuration
@ConditionalOnProperty(name = ["security.enabled"], havingValue = "true", matchIfMissing = true)
class SecurityWebConfiguration {

    @Bean
    fun apiKeyAuthenticationFilterRegistration(
        apiKeyAuthenticationFilter: ApiKeyAuthenticationFilter
    ): FilterRegistrationBean<ApiKeyAuthenticationFilter> {
        val registration = FilterRegistrationBean<ApiKeyAuthenticationFilter>()
        registration.filter = apiKeyAuthenticationFilter
        registration.addUrlPatterns("/api/*", "/blockchain/*", "/relay/*") // Adjust patterns as needed
        registration.order = Ordered.HIGHEST_PRECEDENCE
        registration.setName("apiKeyAuthenticationFilter")
        return registration
    }
}