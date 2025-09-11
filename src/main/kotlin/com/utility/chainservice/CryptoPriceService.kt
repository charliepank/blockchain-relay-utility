package com.utility.chainservice

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.awaitBody
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

data class PriceData(
    val price: BigDecimal,
    val timestamp: Instant
)

data class CoinGeckoResponse(
    val ethereum: Map<String, BigDecimal>? = null,
    val `avalanche-2`: Map<String, BigDecimal>? = null,
    val `matic-network`: Map<String, BigDecimal>? = null,
    val `binancecoin`: Map<String, BigDecimal>? = null
)

enum class NativeCoin(
    val symbol: String,
    val coingeckoId: String,
    val chainIds: Set<Int>
) {
    ETH("ETH", "ethereum", setOf(1, 5, 11155111, 8453, 84531, 84532)), // Mainnet, Goerli, Sepolia, Base, Base Goerli, Base Sepolia
    AVAX("AVAX", "avalanche-2", setOf(43114, 43113)), // Avalanche C-Chain mainnet and fuji testnet
    MATIC("MATIC", "matic-network", setOf(137, 80001)), // Polygon mainnet and mumbai testnet
    BNB("BNB", "binancecoin", setOf(56, 97)); // BSC mainnet and testnet
    
    companion object {
        fun fromChainId(chainId: Int): NativeCoin {
            return values().find { chainId in it.chainIds } ?: ETH
        }
    }
}

@Service
class CryptoPriceService(
    @Value("\${blockchain.chain.id:1}") private val defaultChainId: Int,
    @Value("\${crypto.price.api.enabled:true}") private val priceApiEnabled: Boolean,
    @Value("\${crypto.price.api.url:https://api.coingecko.com}") private val apiBaseUrl: String,
    @Value("\${crypto.price.cache.duration.minutes:5}") private val cacheDurationMinutes: Long,
    @Value("\${crypto.price.fallback.usd:4418}") private val fallbackPrice: BigDecimal
) {
    private val logger = LoggerFactory.getLogger(CryptoPriceService::class.java)
    private val webClient = WebClient.builder()
        .baseUrl(apiBaseUrl)
        .build()
    
    private val priceCache = ConcurrentHashMap<String, PriceData>()
    
    suspend fun getNativeCoinPrice(chainId: Int? = null): BigDecimal = withContext(Dispatchers.IO) {
        val actualChainId = chainId ?: defaultChainId
        val nativeCoin = NativeCoin.fromChainId(actualChainId)
        
        if (!priceApiEnabled) {
            logger.debug("Price API disabled, using fallback price for ${nativeCoin.symbol}: $fallbackPrice")
            return@withContext fallbackPrice
        }
        
        val cacheKey = "${nativeCoin.symbol}_USD"
        val cached = priceCache[cacheKey]
        
        if (cached != null && !isCacheExpired(cached)) {
            logger.debug("Using cached ${nativeCoin.symbol} price: ${cached.price}")
            return@withContext cached.price
        }
        
        try {
            val response = webClient.get()
                .uri("/api/v3/simple/price?ids=${nativeCoin.coingeckoId}&vs_currencies=usd")
                .retrieve()
                .awaitBody<Map<String, Map<String, BigDecimal>>>()
            
            val price = response[nativeCoin.coingeckoId]?.get("usd") ?: fallbackPrice
            
            priceCache[cacheKey] = PriceData(price, Instant.now())
            logger.info("Updated ${nativeCoin.symbol} price: $price USD (chain ID: $actualChainId)")
            
            price
        } catch (e: Exception) {
            logger.error("Failed to fetch ${nativeCoin.symbol} price for chain $actualChainId, using fallback: $fallbackPrice", e)
            fallbackPrice
        }
    }
    
    suspend fun convertWeiToUsd(weiAmount: String, chainId: Int? = null): BigDecimal = withContext(Dispatchers.IO) {
        val nativeCoinPrice = getNativeCoinPrice(chainId)
        val nativeAmount = BigDecimal(weiAmount).divide(BigDecimal("1000000000000000000"), 18, RoundingMode.HALF_UP)
        nativeAmount.multiply(nativeCoinPrice).setScale(4, RoundingMode.HALF_UP)
    }
    
    suspend fun convertWeiToUsd(weiAmount: BigDecimal, chainId: Int? = null): BigDecimal = 
        convertWeiToUsd(weiAmount.toString(), chainId)
    
    suspend fun convertGweiToUsd(gweiAmount: String, chainId: Int? = null): BigDecimal = withContext(Dispatchers.IO) {
        val nativeCoinPrice = getNativeCoinPrice(chainId)
        val nativeAmount = BigDecimal(gweiAmount).divide(BigDecimal("1000000000"), 9, RoundingMode.HALF_UP)
        nativeAmount.multiply(nativeCoinPrice).setScale(6, RoundingMode.HALF_UP)
    }
    
    suspend fun convertGweiToUsd(gweiAmount: BigDecimal, chainId: Int? = null): BigDecimal = 
        convertGweiToUsd(gweiAmount.toString(), chainId)
    
    fun getNativeCoinSymbol(chainId: Int? = null): String {
        val actualChainId = chainId ?: defaultChainId
        return NativeCoin.fromChainId(actualChainId).symbol
    }
    
    private fun isCacheExpired(priceData: PriceData): Boolean {
        val age = Duration.between(priceData.timestamp, Instant.now())
        return age.toMinutes() >= cacheDurationMinutes
    }
    
    fun clearCache() {
        priceCache.clear()
        logger.info("Price cache cleared")
    }
}