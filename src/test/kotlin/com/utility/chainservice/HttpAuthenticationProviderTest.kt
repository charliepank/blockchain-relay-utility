package com.utility.chainservice

import com.utility.chainservice.models.AuthenticationResult
import com.utility.chainservice.models.UserIdentity
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach

class HttpAuthenticationProviderTest {

    private lateinit var httpAuthProvider: HttpAuthenticationProvider

    private val userServiceUrl = "https://user-service.example.com"

    @BeforeEach
    fun setUp() {
        httpAuthProvider = HttpAuthenticationProvider(userServiceUrl, true)
    }

    @Test
    fun `should return true when authentication is enabled`() {
        val provider = HttpAuthenticationProvider(userServiceUrl, true)
        assertTrue(provider.isAuthEnabled())
    }

    @Test
    fun `should return false when authentication is disabled`() {
        val provider = HttpAuthenticationProvider(userServiceUrl, false)
        assertFalse(provider.isAuthEnabled())
    }

    @Test
    fun `should handle authentication when disabled`() {
        val disabledProvider = HttpAuthenticationProvider(userServiceUrl, false)
        
        // When authentication is disabled, we can test the basic properties
        assertFalse(disabledProvider.isAuthEnabled())
        
        // For disabled auth, we would expect anonymous user behavior
        // The actual validateToken method is complex and relies on WebClient
        // So we test the configuration aspect instead
        assertEquals(userServiceUrl, getProviderUrl(disabledProvider))
    }

    @Test
    fun `should create provider with correct configuration`() {
        val enabledProvider = HttpAuthenticationProvider("http://test.com", true)
        val disabledProvider = HttpAuthenticationProvider("http://test.com", false)
        
        assertTrue(enabledProvider.isAuthEnabled())
        assertFalse(disabledProvider.isAuthEnabled())
        
        assertEquals("http://test.com", getProviderUrl(enabledProvider))
        assertEquals("http://test.com", getProviderUrl(disabledProvider))
    }

    @Test
    fun `should handle empty user service URL`() {
        val provider = HttpAuthenticationProvider("", true)
        assertTrue(provider.isAuthEnabled())
        assertEquals("", getProviderUrl(provider))
    }

    @Test
    fun `should handle different user service URLs`() {
        val urls = listOf(
            "https://api.example.com",
            "http://localhost:8080",
            "https://auth.service.internal:9000"
        )
        
        urls.forEach { url ->
            val provider = HttpAuthenticationProvider(url, true)
            assertTrue(provider.isAuthEnabled())
            assertEquals(url, getProviderUrl(provider))
        }
    }

    @Test
    fun `should maintain enabled state correctly`() {
        val enabledProvider = HttpAuthenticationProvider(userServiceUrl, true)
        val disabledProvider = HttpAuthenticationProvider(userServiceUrl, false)
        
        assertTrue(enabledProvider.isAuthEnabled())
        assertFalse(disabledProvider.isAuthEnabled())
        
        // State should be immutable after creation
        assertTrue(enabledProvider.isAuthEnabled())
        assertFalse(disabledProvider.isAuthEnabled())
    }

    @Test
    fun `should create UserIdentityResponse with correct fields`() {
        val response = HttpAuthenticationProvider.UserIdentityResponse(
            userId = "user123",
            email = "test@example.com", 
            walletAddress = "0xabcdef123456",
            userType = "standard"
        )
        
        assertEquals("user123", response.userId)
        assertEquals("test@example.com", response.email)
        assertEquals("0xabcdef123456", response.walletAddress)
        assertEquals("standard", response.userType)
    }

    @Test
    fun `should handle UserIdentityResponse with different user types`() {
        val adminResponse = HttpAuthenticationProvider.UserIdentityResponse(
            userId = "admin1",
            email = "admin@example.com",
            walletAddress = "0x1234567890",
            userType = "admin"
        )
        
        val standardResponse = HttpAuthenticationProvider.UserIdentityResponse(
            userId = "user1", 
            email = "user@example.com",
            walletAddress = "0x0987654321",
            userType = "standard"
        )
        
        assertEquals("admin", adminResponse.userType)
        assertEquals("standard", standardResponse.userType)
        assertNotEquals(adminResponse.userId, standardResponse.userId)
    }

    // Helper method to get the user service URL from the provider
    // We use reflection since the field is private
    private fun getProviderUrl(provider: HttpAuthenticationProvider): String {
        val field = HttpAuthenticationProvider::class.java.getDeclaredField("userServiceUrl")
        field.isAccessible = true
        return field.get(provider) as String
    }
}