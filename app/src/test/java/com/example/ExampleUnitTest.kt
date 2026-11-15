package com.example

import com.example.data.remote.MarketPairsCatalog
import com.example.domain.engine.SmcSignalEngine
import com.example.domain.engine.TradingViewIndicatorsEngine
import com.example.domain.model.Candle
import com.example.domain.model.SignalDirection
import com.example.domain.model.SmcSweepType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.sin

class ExampleUnitTest {

    @Test
    fun `26 TradingView indicators evaluate 11 oscillators and 15 moving averages`() {
        val candles = (0 until 80).map { i ->
            val base = 100.0 + i * 0.45
            Candle(
                timestamp = 1_700_000_000_000L + i * 300_000L,
                open = base - 0.2,
                high = base + 0.6,
                low = base - 0.4,
                close = base + 0.3,
                volume = 1500.0 + i * 25.0
            )
        }

        val consensus = TradingViewIndicatorsEngine.evaluateConsensus(candles, 2)
        assertEquals(11, consensus.oscillators.size)
        assertEquals(15, consensus.movingAverages.size)
        assertEquals(26, consensus.buyCount + consensus.neutralCount + consensus.sellCount)
        assertTrue(consensus.buyCount > consensus.sellCount)
    }

    @Test
    fun `SMC liquidity sweep and 26-TV consensus generate valid BUY signal`() {
        val pair = MarketPairsCatalog.findBySymbol("EUR/USD")
        val candles = MutableList(55) { i ->
            val p = 1.0840 + (i * 0.00004) + sin(i * 0.4) * 0.0005
            Candle(
                timestamp = 1_700_000_000_000L + i * 900_000L,
                open = p - 0.0001,
                high = p + 0.0004,
                low = p - 0.0004,
                close = p + 0.0001,
                volume = 1000.0
            )
        }
        val priorLow = candles.subList(candles.size - 24, candles.size - 1).minOf { it.low }
        val priorHigh = candles.takeLast(20).maxOf { it.high }
        // Inject explicit SSL sweep candle: wicks below prior swing low, reclaims & closes bullish above range
        candles.add(
            Candle(
                timestamp = 1_700_000_000_000L + 55 * 900_000L,
                open = priorLow + 0.0003,
                high = priorHigh + 0.0006,
                low = priorLow - 0.0014,
                close = priorHigh + 0.0004,
                volume = 2900.0
            )
        )

        val sweep = SmcSignalEngine.detectLiquiditySweep(candles)
        assertEquals(SmcSweepType.BULLISH_SSL_SWEEP, sweep.first)

        val smc = SmcSignalEngine.analyzeTimeframe(pair, candles, candles, candles, candles, candles)
        val consensus = TradingViewIndicatorsEngine.evaluateConsensus(candles, pair.decimals)
        val signal = SmcSignalEngine.evaluateCombinedSignal(
            pair = pair,
            candles5m = candles,
            candles15m = candles,
            candles1H = candles,
            candles4H = candles,
            consensus5m = consensus,
            consensus15m = consensus,
            smc5m = smc,
            smc15m = smc,
            minTvConsensusCount = 12
        )

        assertNotNull("Expected signal with consensus buy=${consensus.buyCount}", signal)
        assertEquals(SignalDirection.BUY, signal!!.direction)
        assertTrue(signal.stopLoss < signal.entryPrice)
        assertTrue(signal.tp1 > signal.entryPrice)
        assertTrue(signal.tp2 > signal.tp1)
        assertTrue(signal.tp3 > signal.tp2)
    }

    @Test
    fun `Settings enabled timeframes and confidence threshold filter signals accurately`() {
        val pair = MarketPairsCatalog.findBySymbol("BTC/USDT")
        val candles = MutableList(55) { i ->
            val p = 64000.0 + (i * 25.0) + sin(i * 0.4) * 180.0
            Candle(
                timestamp = 1_700_000_000_000L + i * 900_000L,
                open = p - 30.0,
                high = p + 120.0,
                low = p - 120.0,
                close = p + 35.0,
                volume = 1200.0
            )
        }
        val priorLow = candles.subList(candles.size - 24, candles.size - 1).minOf { it.low }
        val priorHigh = candles.takeLast(20).maxOf { it.high }
        candles.add(
            Candle(
                timestamp = 1_700_000_000_000L + 55 * 900_000L,
                open = priorHigh - 120.0,
                high = priorHigh + 220.0,
                low = priorLow - 420.0,
                close = priorHigh + 150.0,
                volume = 3800.0
            )
        )

        val sweep = SmcSignalEngine.detectLiquiditySweep(candles)
        assertEquals(SmcSweepType.BULLISH_SSL_SWEEP, sweep.first)

        val smc = SmcSignalEngine.analyzeTimeframe(pair, candles, candles, candles, candles, candles)
        val consensus = TradingViewIndicatorsEngine.evaluateConsensus(candles, pair.decimals)

        val candlesMap = mapOf(com.example.domain.model.Timeframe.M15 to candles)
        val smcMap = mapOf(com.example.domain.model.Timeframe.M15 to smc)
        val consMap = mapOf(com.example.domain.model.Timeframe.M15 to consensus)

        // When only 1m is enabled, a 15m-only setup must be filtered out
        val blockedByTf = SmcSignalEngine.evaluateCombinedSignalForEnabledTimeframes(
            pair = pair,
            candlesByTimeframe = candlesMap,
            smcByTimeframe = smcMap,
            consensusByTimeframe = consMap,
            enabledTimeframes = setOf(com.example.domain.model.Timeframe.M1),
            minTvConsensusCount = 12,
            minConfidencePercent = 60
        )
        assertEquals(null, blockedByTf)

        // When 15m is enabled and thresholds are satisfied, signal is emitted
        val matched = SmcSignalEngine.evaluateCombinedSignalForEnabledTimeframes(
            pair = pair,
            candlesByTimeframe = candlesMap,
            smcByTimeframe = smcMap,
            consensusByTimeframe = consMap,
            enabledTimeframes = setOf(com.example.domain.model.Timeframe.M15),
            minTvConsensusCount = 12,
            minConfidencePercent = 60
        )
        assertNotNull("Expected matched signal with buy=${consensus.buyCount}", matched)
        assertEquals(com.example.domain.model.Timeframe.M15, matched!!.timeframeEntry)

        // When confidence threshold is higher than possible (>98%), signal is filtered out
        val blockedByConf = SmcSignalEngine.evaluateCombinedSignalForEnabledTimeframes(
            pair = pair,
            candlesByTimeframe = candlesMap,
            smcByTimeframe = smcMap,
            consensusByTimeframe = consMap,
            enabledTimeframes = setOf(com.example.domain.model.Timeframe.M15),
            minTvConsensusCount = 12,
            minConfidencePercent = 99
        )
        assertEquals(null, blockedByConf)
    }
}
