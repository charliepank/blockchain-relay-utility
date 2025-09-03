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
            gasPayerContractAddress = "0x1234567890123456789012345678901234567890"
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
        
        assertEquals("", properties.gasPayerContractAddress)
    }

    @Test
    fun `should create RelayerProperties with custom values`() {
        val gasPayerContractAddress = "0x1234567890123456789012345678901234567890"
        
        val properties = RelayerProperties(
            gasPayerContractAddress = gasPayerContractAddress
        )
        
        assertEquals(gasPayerContractAddress, properties.gasPayerContractAddress)
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
    fun `should handle BlockchainProperties equality`() {
        val relayerProps = RelayerProperties("0x1234567890123456789012345678901234567890")
        val gasProps = GasProperties(1.5, 10L)
        
        val props1 = BlockchainProperties("url1", 1L, relayerProps, gasProps)
        val props2 = BlockchainProperties("url1", 1L, relayerProps, gasProps)
        val props3 = BlockchainProperties("url2", 1L, relayerProps, gasProps)
        
        assertEquals(props1, props2)
        assertNotEquals(props1, props3)
    }

    @Test
    fun `should handle RelayerProperties equality`() {
        val props1 = RelayerProperties("0x1234567890123456789012345678901234567890")
        val props2 = RelayerProperties("0x1234567890123456789012345678901234567890")
        val props3 = RelayerProperties("0xabcdef1234567890123456789012345678901234")
        
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
    fun `should generate consistent hash codes`() {
        val relayerProps = RelayerProperties("0x1234567890123456789012345678901234567890")
        val gasProps = GasProperties(1.5, 10L)
        val blockchainProps1 = BlockchainProperties("url1", 1L, relayerProps, gasProps)
        val blockchainProps2 = BlockchainProperties("url1", 1L, relayerProps, gasProps)
        
        assertEquals(blockchainProps1.hashCode(), blockchainProps2.hashCode())
        
    }

    @Test
    fun `should provide meaningful toString representations`() {
        val relayerProps = RelayerProperties("0x1234567890123456789012345678901234567890")
        val gasProps = GasProperties(1.5, 10L)
        val blockchainProps = BlockchainProperties("testUrl", 1L, relayerProps, gasProps)
        
        val blockchainString = blockchainProps.toString()
        
        assertTrue(blockchainString.contains("BlockchainProperties"))
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
        val emptyRelayer = RelayerProperties("")
        val emptyBlockchain = BlockchainProperties("", 0L, emptyRelayer, GasProperties())
        
        assertEquals("", emptyRelayer.gasPayerContractAddress)
        assertEquals("", emptyBlockchain.rpcUrl)
        assertEquals(0L, emptyBlockchain.chainId)
    }
}