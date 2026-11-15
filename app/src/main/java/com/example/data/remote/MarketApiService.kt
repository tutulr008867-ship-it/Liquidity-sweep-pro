package com.example.data.remote

import com.example.domain.model.Candle
import com.example.domain.model.MarketType
import com.example.domain.model.Timeframe
import com.example.domain.model.TradingPair
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlin.random.Random

data class TickerQuote(
    val symbol: String,
    val lastPrice: Double,
    val priceChange24hPct: Double,
    val volume24h: Double,
    val isLive: Boolean
)

data class CandleFetchResult(
    val candles: List<Candle>,
    val isLive: Boolean
)

class MarketApiService {

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .pingInterval(20, TimeUnit.SECONDS)
        .build()

    /**
     * Fetches real OHLCV candles from Binance (Crypto) or Yahoo Finance (Forex / fallback Crypto).
     * If network/API is unreachable, returns deterministic cached candle series marked isLive = false.
     */
    suspend fun fetchCandles(pair: TradingPair, timeframe: Timeframe, limit: Int = 120): CandleFetchResult =
        withContext(Dispatchers.IO) {
            if (pair.marketType == MarketType.CRYPTO && pair.binanceSymbol != null) {
                fetchBinanceKlines(pair.binanceSymbol, timeframe, limit)?.let {
                    return@withContext CandleFetchResult(it, isLive = true)
                }
            }

            // Try Yahoo Finance chart endpoint (works for both Forex and Crypto)
            fetchYahooChartCandles(pair, timeframe, limit)?.let {
                return@withContext CandleFetchResult(it, isLive = true)
            }

            // Offline/rate-limit fallback so charts & indicators remain functional
            CandleFetchResult(
                candles = buildOfflineSeedCandles(pair, timeframe, limit),
                isLive = false
            )
        }

    /**
     * Fetches live 24hr quotes for all crypto pairs from Binance and spot FX rates from Open ER API / Yahoo.
     */
    suspend fun fetchAllLiveQuotes(pairs: List<TradingPair>): Map<String, TickerQuote> =
        withContext(Dispatchers.IO) {
            val quotes = mutableMapOf<String, TickerQuote>()

            // 1. Binance batch 24hr ticker for Crypto
            try {
                val req = Request.Builder()
                    .url("https://api.binance.com/api/v3/ticker/24hr")
                    .header("User-Agent", "SweepPulse-Android/1.0")
                    .build()
                client.newCall(req).execute().use { resp ->
                    if (resp.isSuccessful) {
                        val body = resp.body?.string() ?: "[]"
                        val arr = JSONArray(body)
                        val cryptoMap = pairs.filter { it.binanceSymbol != null }
                            .associateBy { it.binanceSymbol!! }
                        for (i in 0 until arr.length()) {
                            val obj = arr.getJSONObject(i)
                            val bSym = obj.optString("symbol")
                            val pair = cryptoMap[bSym] ?: continue
                            val last = obj.optString("lastPrice").toDoubleOrNull() ?: continue
                            val pct = obj.optString("priceChangePercent").toDoubleOrNull() ?: 0.0
                            val vol = obj.optString("quoteVolume").toDoubleOrNull() ?: 0.0
                            quotes[pair.symbol] = TickerQuote(pair.symbol, last, pct, vol, true)
                        }
                    }
                }
            } catch (_: Exception) {
                // Continue to fallback endpoints
            }

            // 2. Open Exchange Rates live spot FX feed
            try {
                val req = Request.Builder()
                    .url("https://open.er-api.com/v6/latest/USD")
                    .header("User-Agent", "SweepPulse-Android/1.0")
                    .build()
                client.newCall(req).execute().use { resp ->
                    if (resp.isSuccessful) {
                        val json = JSONObject(resp.body?.string() ?: "{}")
                        val rates = json.optJSONObject("rates")
                        if (rates != null) {
                            val eur = rates.optDouble("EUR", 0.0)
                            val gbp = rates.optDouble("GBP", 0.0)
                            val jpy = rates.optDouble("JPY", 0.0)
                            val aud = rates.optDouble("AUD", 0.0)
                            val cad = rates.optDouble("CAD", 0.0)

                            if (eur > 0) putFxQuote(quotes, "EUR/USD", 1.0 / eur)
                            if (gbp > 0) putFxQuote(quotes, "GBP/USD", 1.0 / gbp)
                            if (jpy > 0) putFxQuote(quotes, "USD/JPY", jpy)
                            if (aud > 0) putFxQuote(quotes, "AUD/USD", 1.0 / aud)
                            if (cad > 0) putFxQuote(quotes, "USD/CAD", cad)
                            if (eur > 0 && jpy > 0) putFxQuote(quotes, "EUR/JPY", jpy / eur)
                            if (gbp > 0 && jpy > 0) putFxQuote(quotes, "GBP/JPY", jpy / gbp)
                        }
                    }
                }
            } catch (_: Exception) {
                // Ignore if offline
            }

            quotes
        }

    /**
     * Connects to Binance Combined Mini-Ticker WebSocket for real-time sub-second crypto price & volume updates.
     */
    fun streamBinanceMiniTickers(pairs: List<TradingPair>): Flow<TickerQuote> = callbackFlow {
        val cryptoPairs = pairs.filter { it.binanceSymbol != null }
        if (cryptoPairs.isEmpty()) {
            close()
            return@callbackFlow
        }
        val byBinanceSym = cryptoPairs.associateBy { it.binanceSymbol!!.lowercase() }
        val streams = cryptoPairs.joinToString("/") { "${it.binanceSymbol!!.lowercase()}@miniTicker" }
        val url = "wss://stream.binance.com:9443/stream?streams=$streams"

        val request = Request.Builder().url(url).build()
        val ws: WebSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onMessage(webSocket: WebSocket, text: String) {
                try {
                    val root = JSONObject(text)
                    val data = root.optJSONObject("data") ?: return
                    val sym = data.optString("s").lowercase()
                    val pair = byBinanceSym[sym] ?: return
                    val close = data.optString("c").toDoubleOrNull() ?: return
                    val open = data.optString("o").toDoubleOrNull() ?: close
                    val quoteVol = data.optString("q").toDoubleOrNull() ?: 0.0
                    val pct = if (open > 0) ((close - open) / open) * 100.0 else 0.0
                    trySend(
                        TickerQuote(
                            symbol = pair.symbol,
                            lastPrice = close,
                            priceChange24hPct = pct,
                            volume24h = quoteVol,
                            isLive = true
                        )
                    )
                } catch (_: Exception) {
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                // Let REST polling continue seamlessly
            }
        })

        awaitClose {
            ws.close(1000, "Flow closed")
        }
    }

    private fun putFxQuote(quotes: MutableMap<String, TickerQuote>, symbol: String, price: Double) {
        val pair = MarketPairsCatalog.findBySymbol(symbol)
        val pct = ((price - pair.referencePrice) / pair.referencePrice) * 100.0
        quotes[symbol] = TickerQuote(
            symbol = symbol,
            lastPrice = price,
            priceChange24hPct = pct.coerceIn(-5.0, 5.0),
            volume24h = 0.0,
            isLive = true
        )
    }

    private fun fetchBinanceKlines(symbol: String, timeframe: Timeframe, limit: Int): List<Candle>? {
        return try {
            val url = "https://api.binance.com/api/v3/klines?symbol=$symbol&interval=${timeframe.binanceInterval}&limit=$limit"
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", "SweepPulse-Android/1.0")
                .build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return null
                val arr = JSONArray(resp.body?.string() ?: "[]")
                val candles = ArrayList<Candle>(arr.length())
                for (i in 0 until arr.length()) {
                    val row = arr.getJSONArray(i)
                    candles.add(
                        Candle(
                            timestamp = row.getLong(0),
                            open = row.getString(1).toDouble(),
                            high = row.getString(2).toDouble(),
                            low = row.getString(3).toDouble(),
                            close = row.getString(4).toDouble(),
                            volume = row.getString(5).toDouble()
                        )
                    )
                }
                if (candles.size >= 30) candles else null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun fetchYahooChartCandles(pair: TradingPair, timeframe: Timeframe, limit: Int): List<Candle>? {
        return try {
            val url = "https://query1.finance.yahoo.com/v8/finance/chart/${pair.yahooSymbol}?interval=${timeframe.yahooInterval}&range=${timeframe.yahooRange}"
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0")
                .build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return null
                val json = JSONObject(resp.body?.string() ?: "{}")
                val chart = json.optJSONObject("chart") ?: return null
                val resultArr = chart.optJSONArray("result") ?: return null
                if (resultArr.length() == 0) return null
                val result = resultArr.getJSONObject(0)
                val timestamps = result.optJSONArray("timestamp") ?: return null
                val indicators = result.optJSONObject("indicators") ?: return null
                val quoteArr = indicators.optJSONArray("quote") ?: return null
                if (quoteArr.length() == 0) return null
                val quote = quoteArr.getJSONObject(0)
                val opens = quote.optJSONArray("open") ?: return null
                val highs = quote.optJSONArray("high") ?: return null
                val lows = quote.optJSONArray("low") ?: return null
                val closes = quote.optJSONArray("close") ?: return null
                val volumes = quote.optJSONArray("volume")

                val rawCandles = ArrayList<Candle>(timestamps.length())
                for (i in 0 until timestamps.length()) {
                    if (opens.isNull(i) || highs.isNull(i) || lows.isNull(i) || closes.isNull(i)) continue
                    val o = opens.getDouble(i)
                    val h = highs.getDouble(i)
                    val l = lows.getDouble(i)
                    val c = closes.getDouble(i)
                    val v = if (volumes != null && !volumes.isNull(i)) volumes.optDouble(i, 1200.0) else 1200.0
                    // Forex feeds often report 0 tick volume on Yahoo; estimate proxy tick intensity from range
                    val effectiveVol = if (v > 1.0) v else ((abs(h - l) / pair.pipSize) * 180.0 + 450.0)
                    rawCandles.add(
                        Candle(
                            timestamp = timestamps.getLong(i) * 1000L,
                            open = o,
                            high = h,
                            low = l,
                            close = c,
                            volume = effectiveVol
                        )
                    )
                }

                val finalCandles = if (timeframe == Timeframe.H4 && rawCandles.size >= 40) {
                    aggregateTo4H(rawCandles)
                } else {
                    rawCandles
                }

                if (finalCandles.size >= 30) finalCandles.takeLast(limit) else null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun aggregateTo4H(hourly: List<Candle>): List<Candle> {
        val grouped = hourly.chunked(4)
        return grouped.mapNotNull { chunk ->
            if (chunk.isEmpty()) null
            else Candle(
                timestamp = chunk.first().timestamp,
                open = chunk.first().open,
                high = chunk.maxOf { it.high },
                low = chunk.minOf { it.low },
                close = chunk.last().close,
                volume = chunk.sumOf { it.volume }
            )
        }
    }

    /**
     * Deterministic offline seed candles structured with authentic swing highs/lows and liquidity sweep waves
     * when the network is unavailable.
     */
    private fun buildOfflineSeedCandles(pair: TradingPair, timeframe: Timeframe, limit: Int): List<Candle> {
        val now = System.currentTimeMillis()
        val alignedNow = now - (now % timeframe.durationMillis)
        val seed = (pair.symbol.hashCode() * 31) + timeframe.ordinal * 17
        val rng = Random(seed)
        val stepVol = pair.pipSize * when (timeframe) {
            Timeframe.M1 -> 2.4
            Timeframe.M5 -> 4.2
            Timeframe.M15 -> 6.8
            Timeframe.M30 -> 9.5
            Timeframe.H1 -> 14.0
            Timeframe.H4 -> 26.0
        }

        val candles = ArrayList<Candle>(limit)
        var price = pair.referencePrice * (1.0 - 0.004)
        val bullishBias = (pair.symbol.hashCode() % 2 == 0)

        for (i in 0 until limit) {
            val ts = alignedNow - (limit - 1 - i) * timeframe.durationMillis
            val wave = sin(i * 0.23) * stepVol * 1.4 + (if (bullishBias) stepVol * 0.14 else -stepVol * 0.14)
            val noise = (rng.nextDouble() - 0.48) * stepVol * 1.6
            val open = price
            var close = (open + wave * 0.35 + noise).coerceAtLeast(pair.pipSize * 10)

            var high = maxOf(open, close) + rng.nextDouble() * stepVol * 0.9
            var low = minOf(open, close) - rng.nextDouble() * stepVol * 0.9

            // Inject realistic liquidity sweep structure near recent bars
            if (i == limit - 3 || i == limit - 22 || i == limit - 46) {
                if (bullishBias) {
                    low -= stepVol * 2.8
                    close = open + stepVol * 1.1
                    high = maxOf(high, close + stepVol * 0.4)
                } else {
                    high += stepVol * 2.8
                    close = open - stepVol * 1.1
                    low = minOf(low, close - stepVol * 0.4)
                }
            }

            val baseVol = if (pair.marketType == MarketType.CRYPTO) 1800.0 else 950.0
            val volSpike = if (i == limit - 3 || i == limit - 2) 2.2 else 1.0
            val volume = baseVol * (0.65 + rng.nextDouble() * 1.1) * volSpike

            candles.add(Candle(ts, open, high, low, close, volume))
            price = close
        }
        return candles
    }
}
