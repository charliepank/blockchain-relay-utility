package com.utility.chainservice

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class ConfigurationPropertiesTest {

    @Test
    fun `should create BlockchainProperties with default values`() {
        val properties = BlockchainProperties()
        
        assertEquals("", properties.rpcUrl)
        assertEquals(43113L, properties.chainId)
        assertNotNull(properties.relayer)
        assertNotNull(properties.gas)
    }

    @Test
    fun `should create BlockchainProperties with custom values`() {
        val relayerProps = RelayerProperties(
            privateKey = "0x1234567890123456789012345678901234567890123456789012345678901234",
            walletAddress = "0xabcdef1234567890"
        )
        val gasProps = GasProperties(
            priceMultiplier = 1.5,
            minimumGasPriceWei = 10L
        )
        
        val properties = BlockchainProperties(
            rpcUrl = "https://api.avax.network/ext/bc/C/rpc",
            chainId = 43114L,
            relayer = relayerProps,
            gas = gasProps
        )
        
        assertEquals("https://api.avax.network/ext/bc/C/rpc", properties.rpcUrl)
        assertEquals(43114L, properties.chainId)
        assertEquals(relayerProps, properties.relayer)
        assertEquals(gasProps, properties.gas)
    }

    @Test
    fun `should create RelayerProperties with default values`() {
        val properties = RelayerProperties()
        
        assertEquals("", properties.privateKey)
        assertEquals("", properties.walletAddress)
    }

    @Test
    fun `should create RelayerProperties with custom values`() {
        val privateKey = "0x1234567890123456789012345678901234567890123456789012345678901234"
        val walletAddress = "0xabcdef1234567890"
        
        val properties = RelayerProperties(
            privateKey = privateKey,
            walletAddress = walletAddress
        )
        
        assertEquals(privateKey, properties.privateKey)
        assertEquals(walletAddress, properties.walletAddress)
    }

    @Test
    fun `should create GasProperties with default values`() {
        val properties = GasProperties()
        
        assertEquals(1.2, properties.priceMultiplier)
        assertEquals(6L, properties.minimumGasPriceWei)
    }

    @Test
    fun `should create GasProperties with custom values`() {
        val priceMultiplier = 2.0
        val minimumGasPriceWei = 15L
        
        val properties = GasProperties(
            priceMultiplier = priceMultiplier,
            minimumGasPriceWei = minimumGasPriceWei
        )
        
        assertEquals(priceMultiplier, properties.priceMultiplier)
        assertEquals(minimumGasPriceWei, properties.minimumGasPriceWei)
    }

    @Test
    fun `should create AuthProperties with default values`() {
        val properties = AuthProperties()
        
        assertEquals("", properties.userServiceUrl)
        assertTrue(properties.enabled)
    }

    @Test
    fun `should create AuthProperties with custom values`() {
        val userServiceUrl = "https://user-service.example.com"
        val enabled = false
        
        val properties = AuthProperties(
            userServiceUrl = userServiceUrl,
            enabled = enabled
        )
        
        assertEquals(userServiceUrl, properties.userServiceUrl)
        assertEquals(enabled, properties.enabled)
    }

    @Test
    fun `should handle BlockchainProperties equality`() {
        val relayerProps = RelayerProperties("key1", "addr1")
        val gasProps = GasProperties(1.5, 10L)
        
        val props1 = BlockchainProperties("url1", 1L, relayerProps, gasProps)
        val props2 = BlockchainProperties("url1", 1L, relayerProps, gasProps)
        val props3 = BlockchainProperties("url2", 1L, relayerProps, gasProps)
        
        assertEquals(props1, props2)
        assertNotEquals(props1, props3)
    }

    @Test
    fun `should handle RelayerProperties equality`() {
        val props1 = RelayerProperties("key1", "addr1")
        val props2 = RelayerProperties("key1", "addr1")
        val props3 = RelayerProperties("key2", "addr1")
        
        assertEquals(props1, props2)
        assertNotEquals(props1, props3)
    }

    @Test
    fun `should handle GasProperties equality`() {
        val props1 = GasProperties(1.5, 10L)
        val props2 = GasProperties(1.5, 10L)
        val props3 = GasProperties(2.0, 10L)
        
        assertEquals(props1, props2)
        assertNotEquals(props1, props3)
    }

    @Test
    fun `should handle AuthProperties equality`() {
        val props1 = AuthProperties("url1", true)
        val props2 = AuthProperties("url1", true)
        val props3 = AuthProperties("url1", false)
        
        assertEquals(props1, props2)
        assertNotEquals(props1, props3)
    }

    @Test
    fun `should generate consistent hash codes`() {
        val relayerProps = RelayerProperties("key1", "addr1")
        val gasProps = GasProperties(1.5, 10L)
        val blockchainProps1 = BlockchainProperties("url1", 1L, relayerProps, gasProps)
        val blockchainProps2 = BlockchainProperties("url1", 1L, relayerProps, gasProps)
        
        assertEquals(blockchainProps1.hashCode(), blockchainProps2.hashCode())
        
        val authProps1 = AuthProperties("url1", true)
        val authProps2 = AuthProperties("url1", true)
        
        assertEquals(authProps1.hashCode(), authProps2.hashCode())
    }

    @Test
    fun `should provide meaningful toString representations`() {
        val relayerProps = RelayerProperties("testKey", "testAddr")
        val gasProps = GasProperties(1.5, 10L)
        val blockchainProps = BlockchainProperties("testUrl", 1L, relayerProps, gasProps)
        val authProps = AuthProperties("testUserUrl", false)
        
        val blockchainString = blockchainProps.toString()
        val authString = authProps.toString()
        
        assertTrue(blockchainString.contains("BlockchainProperties"))
        assertTrue(authString.contains("AuthProperties"))
    }

    @Test
    fun `should handle edge case values in GasProperties`() {
        // Test with very small multiplier
        val smallMultiplier = GasProperties(0.1, 1L)
        assertEquals(0.1, smallMultiplier.priceMultiplier)
        assertEquals(1L, smallMultiplier.minimumGasPriceWei)
        
        // Test with large multiplier
        val largeMultiplier = GasProperties(10.0, 1000000L)
        assertEquals(10.0, largeMultiplier.priceMultiplier)
        assertEquals(1000000L, largeMultiplier.minimumGasPriceWei)
    }

    @Test
    fun `should handle different chain IDs in BlockchainProperties`() {
        val mainnetProps = BlockchainProperties(chainId = 43114L)
        val testnetProps = BlockchainProperties(chainId = 43113L)
        val localProps = BlockchainProperties(chainId = 1337L)
        
        assertEquals(43114L, mainnetProps.chainId)
        assertEquals(43113L, testnetProps.chainId)
        assertEquals(1337L, localProps.chainId)
    }

    @Test
    fun `should handle empty and null-like values`() {
        val emptyRelayer = RelayerProperties("", "")
        val emptyAuth = AuthProperties("", false)
        val emptyBlockchain = BlockchainProperties("", 0L, emptyRelayer, GasProperties())
        
        assertEquals("", emptyRelayer.privateKey)
        assertEquals("", emptyRelayer.walletAddress)
        assertEquals("", emptyAuth.userServiceUrl)
        assertFalse(emptyAuth.enabled)
        assertEquals("", emptyBlockchain.rpcUrl)
        assertEquals(0L, emptyBlockchain.chainId)
    }
}