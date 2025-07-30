package com.utility.chainservice.models

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class AuthenticationModelsTest {

    @Test
    fun `should create successful authentication result`() {
        val userIdentity = UserIdentity(
            userId = "user123",
            email = "test@example.com",
            walletAddress = "0xabcdef",
            userType = "standard"
        )
        
        val result = AuthenticationResult(
            success = true,
            userIdentity = userIdentity
        )
        
        assertTrue(result.success)
        assertNotNull(result.userIdentity)
        assertEquals("user123", result.userIdentity?.userId)
        assertEquals("test@example.com", result.userIdentity?.email)
        assertEquals("0xabcdef", result.userIdentity?.walletAddress)
        assertEquals("standard", result.userIdentity?.userType)
        assertNull(result.error)
    }

    @Test
    fun `should create failed authentication result`() {
        val result = AuthenticationResult(
            success = false,
            error = "Invalid token"
        )
        
        assertFalse(result.success)
        assertNull(result.userIdentity)
        assertEquals("Invalid token", result.error)
    }

    @Test
    fun `should create user identity`() {
        val userIdentity = UserIdentity(
            userId = "testUser",
            email = "user@test.com",
            walletAddress = "0x1234567890",
            userType = "admin"
        )
        
        assertEquals("testUser", userIdentity.userId)
        assertEquals("user@test.com", userIdentity.email)
        assertEquals("0x1234567890", userIdentity.walletAddress)
        assertEquals("admin", userIdentity.userType)
    }
}