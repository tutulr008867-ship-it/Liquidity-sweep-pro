package com.example.domain.model

import java.util.Locale
import kotlin.math.abs

enum class ThemeMode {
    DARK, LIGHT, SYSTEM
}

enum class MarketType(val label: String) {
    FOREX("Forex"),
    CRYPTO("Crypto")
}

enum class Timeframe(
    val label: String,
    val binanceInterval: String,
    val yahooInterval: String,
    val yahooRange: String,
    val durationMillis: Long,
    val roleDescription: String
) {
    M1("1m", "1m", "1m", "1d", 60_000L, "Scalp Execution"),
    M5("5m", "5m", "5m", "5d", 300_000L, "Precision Entry"),
    M15("15m", "15m", "15m", "5d", 900_000L, "Sweep & Entry"),
    M30("30m", "30m", "30m", "1mo", 1_800_000L, "Intraday Swing"),
    H1("1H", "1h", "60m", "1mo", 3_600_000L, "Key Structure"),
    H4("4H", "4h", "60m", "3mo", 14_400_000L, "Holistic Bias")
}

data class TradingPair(
    val symbol: String,
    val displayName: String,
    val marketType: MarketType,
    val binanceSymbol: String?,
    val yahooSymbol: String,
    val decimals: Int,
    val pipSize: Double,
    val typicalSpread: Double,
    val referencePrice: Double
) {
    fun formatPrice(price: Double): String {
        return String.format(Locale.US, "%.${decimals}f", price)
    }

    fun priceDiffToPips(diff: Double): Double {
        return abs(diff) / pipSize
    }
}

data class Candle(
    val timestamp: Long,
    val open: Double,
    val high: Double,
    val low: Double,
    val close: Double,
    val volume: Double
) {
    val isBullish: Boolean get() = close >= open
    val bodySize: Double get() = abs(close - open)
    val upperWick: Double get() = high - maxOf(open, close)
    val lowerWick: Double get() = minOf(open, close) - low
    val totalRange: Double get() = (high - low).coerceAtLeast(1e-9)
}

enum class IndicatorVote {
    BUY, NEUTRAL, SELL
}

enum class IndicatorCategory {
    OSCILLATOR, MOVING_AVERAGE
}

data class IndicatorResult(
    val name: String,
    val valueFormatted: String,
    val vote: IndicatorVote,
    val category: IndicatorCategory
)

enum class ConsensusRating(val label: String) {
    STRONG_SELL("Strong Sell"),
    SELL("Sell"),
    NEUTRAL("Neutral"),
    BUY("Buy"),
    STRONG_BUY("Strong Buy")
}

data class TradingViewConsensus(
    val oscillators: List<IndicatorResult>,
    val movingAverages: List<IndicatorResult>,
    val buyCount: Int,
    val neutralCount: Int,
    val sellCount: Int,
    val oscillatorBuy: Int,
    val oscillatorNeutral: Int,
    val oscillatorSell: Int,
    val maBuy: Int,
    val maNeutral: Int,
    val maSell: Int,
    val overallRating: ConsensusRating,
    val oscillatorRating: ConsensusRating,
    val maRating: ConsensusRating,
    val scoreNormalized: Float // -1f (Strong Sell) .. +1f (Strong Buy)
)

enum class SmcSweepType(val label: String) {
    BULLISH_SSL_SWEEP("SSL Liquidity Sweep"),
    BEARISH_BSL_SWEEP("BSL Liquidity Sweep"),
    NONE("No Active Sweep")
}

enum class MarketBias(val label: String) {
    BULLISH("Bullish"),
    BEARISH("Bearish"),
    RANGING("Consolidating")
}

data class SmcAnalysis(
    val sweepType: SmcSweepType,
    val sweptLevel: Double?,
    val sweepCandleTimestamp: Long?,
    val bosConfirmed: Boolean,
    val chochConfirmed: Boolean,
    val fvgZoneLow: Double?,
    val fvgZoneHigh: Double?,
    val orderBlockLow: Double?,
    val orderBlockHigh: Double?,
    val htfBias4H: MarketBias,
    val structure1H: MarketBias,
    val entryAnalysis5m: String,
    val entryAnalysis15m: String,
    val entry5mAligned: Boolean,
    val entry15mAligned: Boolean,
    val currentVolume: Double,
    val volumeSma20: Double,
    val rvol: Double,
    val ema9: Double,
    val ema21: Double,
    val ema50: Double,
    val sma200: Double,
    val ema9Series: List<Double>,
    val ema21Series: List<Double>,
    val ema50Series: List<Double>,
    val sma200Series: List<Double>,
    val volumeSma20Series: List<Double>,
    val atr14: Double
)

enum class SignalDirection(val label: String) {
    BUY("BUY"),
    SELL("SELL")
}

enum class SignalStatus(val label: String) {
    ACTIVE("Active"),
    TP1_HIT("TP1 Hit"),
    TP2_HIT("TP2 Hit"),
    TP3_HIT("Success Done"),
    SL_HIT("SL Hit")
}

data class TradingSignal(
    val id: String,
    val pairSymbol: String,
    val displayName: String,
    val marketType: MarketType,
    val direction: SignalDirection,
    val entryPrice: Double,
    val stopLoss: Double,
    val tp1: Double,
    val tp2: Double,
    val tp3: Double,
    val riskReward: Double,
    val timeframeEntry: Timeframe,
    val confidenceScore: Int,
    val smcSetupSummary: String,
    val sweepType: SmcSweepType,
    val sweepPrice: Double,
    val htfBias4H: MarketBias,
    val entry5mSummary: String,
    val entry15mSummary: String,
    val tvBuyCount: Int,
    val tvNeutralCount: Int,
    val tvSellCount: Int,
    val tvRating: ConsensusRating,
    val rvolAtTrigger: Double,
    val status: SignalStatus,
    val outcomePips: Double,
    val outcomePercent: Double,
    val createdAtMillis: Long,
    val updatedAtMillis: Long
)

data class PairMarketSnapshot(
    val pair: TradingPair,
    val currentPrice: Double,
    val previousPrice: Double,
    val priceChange24hPct: Double,
    val bidPrice: Double,
    val askPrice: Double,
    val volume24h: Double,
    val lastUpdatedMillis: Long,
    val isLiveFeed: Boolean,
    val feedLabel: String,
    val candlesByTimeframe: Map<Timeframe, List<Candle>>,
    val smcByTimeframe: Map<Timeframe, SmcAnalysis>,
    val consensusByTimeframe: Map<Timeframe, TradingViewConsensus>,
    val activeSignal: TradingSignal?
)

data class EquityCurvePoint(
    val index: Int,
    val label: String,
    val cumulativePips: Double,
    val cumulativeR: Double,
    val cumulativePercent: Double = 0.0,
    val tradeR: Double = 0.0,
    val tradePips: Double = 0.0,
    val rollingWinRatePct: Double = 0.0
)

data class TimeframePerformance(
    val timeframe: Timeframe,
    val total: Int,
    val wins: Int,
    val winRatePct: Double
)

data class PerformanceMetrics(
    val totalSignals: Int,
    val activeSignals: Int,
    val closedSignals: Int,
    val wins: Int,
    val losses: Int,
    val tp1Hits: Int = 0,
    val tp2Hits: Int = 0,
    val tp3SuccessDone: Int = 0,
    val slHits: Int = 0,
    val winRatePct: Double,
    val profitFactor: Double,
    val avgRiskReward: Double,
    val netPips: Double,
    val netRoiPct: Double,
    val bestPair: String,
    val forexWinRatePct: Double,
    val cryptoWinRatePct: Double,
    val timeframeBreakdown: List<TimeframePerformance>,
    val equityCurve: List<EquityCurvePoint>
)

data class PushAlertRecord(
    val id: String,
    val signalId: String,
    val pairSymbol: String,
    val title: String,
    val body: String,
    val direction: SignalDirection,
    val entryPrice: Double,
    val stopLoss: Double,
    val tp1: Double,
    val timestampMillis: Long,
    val isRead: Boolean
)
