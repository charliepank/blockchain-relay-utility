package com.utility.chainservice.models

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class TransactionResultTest {

    @Test
    fun `should create successful transaction result`() {
        val result = TransactionResult(
            success = true,
            transactionHash = "0x123456789"
        )
        
        assertTrue(result.success)
        assertEquals("0x123456789", result.transactionHash)
        assertNull(result.error)
        assertNull(result.contractAddress)
    }

    @Test
    fun `should create failed transaction result with error`() {
        val result = TransactionResult(
            success = false,
            transactionHash = null,
            error = "Transaction failed"
        )
        
        assertFalse(result.success)
        assertNull(result.transactionHash)
        assertEquals("Transaction failed", result.error)
        assertNull(result.contractAddress)
    }

    @Test
    fun `should create failed transaction result with hash and error`() {
        val result = TransactionResult(
            success = false,
            transactionHash = "0x987654321",
            error = "Transaction reverted"
        )
        
        assertFalse(result.success)
        assertEquals("0x987654321", result.transactionHash)
        assertEquals("Transaction reverted", result.error)
        assertNull(result.contractAddress)
    }

    @Test
    fun `should create transaction result with contract address`() {
        val result = TransactionResult(
            success = true,
            transactionHash = "0xabc123",
            contractAddress = "0x742d35Cc6634C0532925a3b844Bc9e7595f8E65"
        )
        
        assertTrue(result.success)
        assertEquals("0xabc123", result.transactionHash)
        assertNull(result.error)
        assertEquals("0x742d35Cc6634C0532925a3b844Bc9e7595f8E65", result.contractAddress)
    }
}