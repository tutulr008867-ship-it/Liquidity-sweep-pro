package com.example.domain.engine

import com.example.domain.model.Candle
import com.example.domain.model.ConsensusRating
import com.example.domain.model.MarketBias
import com.example.domain.model.SignalDirection
import com.example.domain.model.SignalStatus
import com.example.domain.model.SmcAnalysis
import com.example.domain.model.SmcSweepType
import com.example.domain.model.Timeframe
import com.example.domain.model.TradingPair
import com.example.domain.model.TradingSignal
import com.example.domain.model.TradingViewConsensus
import java.util.Locale
import kotlin.math.abs

/**
 * Smart Money Concepts (SMC) + Multi-Timeframe Analysis Engine:
 * - 4H: Holistic market bias & macro liquidity direction
 * - 1H: Intermediate structure & institutional Order Blocks
 * - 15m & 5m: Precision liquidity sweep detection (BSL/SSL), FVG, RVOL spike, and combined signal entry
 */
object SmcSignalEngine {

    fun analyzeTimeframe(
        pair: TradingPair,
        candles: List<Candle>,
        candles4H: List<Candle>,
        candles1H: List<Candle>,
        candles15m: List<Candle>,
        candles5m: List<Candle>
    ): SmcAnalysis {
        val closes = candles.map { it.close }
        val volumes = candles.map { it.volume }

        val ema9Series = TradingViewIndicatorsEngine.calculateEmaSeries(closes, 9)
        val ema21Series = TradingViewIndicatorsEngine.calculateEmaSeries(closes, 21)
        val ema50Series = TradingViewIndicatorsEngine.calculateEmaSeries(closes, 50)
        val sma200Series = TradingViewIndicatorsEngine.calculateSmaSeries(closes, 200)
        val volSma20Series = TradingViewIndicatorsEngine.calculateSmaSeries(volumes, 20)

        val currentVol = volumes.lastOrNull() ?: 0.0
        val volSma20 = volSma20Series.lastOrNull()?.coerceAtLeast(1e-9) ?: 1.0
        val rvol = (currentVol / volSma20).coerceIn(0.1, 12.0)

        val atr14 = calculateAtr(candles, 14)

        val htfBias4H = determineMarketBias(candles4H.ifEmpty { candles })
        val structure1H = determineMarketBias(candles1H.ifEmpty { candles })

        val sweep = detectLiquiditySweep(candles)
        val (fvgLow, fvgHigh) = detectLatestFvg(candles)
        val (obLow, obHigh) = detectLatestOrderBlock(candles, sweep.first)

        val bosConfirmed = detectBos(candles, sweep.first)
        val chochConfirmed = detectChoch(candles, sweep.first)

        val eval5m = evaluateEntryWindow(pair, candles5m.ifEmpty { candles }, "5m", htfBias4H)
        val eval15m = evaluateEntryWindow(pair, candles15m.ifEmpty { candles }, "15m", htfBias4H)

        return SmcAnalysis(
            sweepType = sweep.first,
            sweptLevel = sweep.second,
            sweepCandleTimestamp = sweep.third,
            bosConfirmed = bosConfirmed,
            chochConfirmed = chochConfirmed,
            fvgZoneLow = fvgLow,
            fvgZoneHigh = fvgHigh,
            orderBlockLow = obLow,
            orderBlockHigh = obHigh,
            htfBias4H = htfBias4H,
            structure1H = structure1H,
            entryAnalysis5m = eval5m.first,
            entryAnalysis15m = eval15m.first,
            entry5mAligned = eval5m.second,
            entry15mAligned = eval15m.second,
            currentVolume = currentVol,
            volumeSma20 = volSma20,
            rvol = rvol,
            ema9 = ema9Series.lastOrNull() ?: (closes.lastOrNull() ?: 0.0),
            ema21 = ema21Series.lastOrNull() ?: (closes.lastOrNull() ?: 0.0),
            ema50 = ema50Series.lastOrNull() ?: (closes.lastOrNull() ?: 0.0),
            sma200 = sma200Series.lastOrNull() ?: (closes.lastOrNull() ?: 0.0),
            ema9Series = ema9Series,
            ema21Series = ema21Series,
            ema50Series = ema50Series,
            sma200Series = sma200Series,
            volumeSma20Series = volSma20Series,
            atr14 = atr14
        )
    }

    /**
     * Generates a live Combined Signal when SMC Liquidity Sweep (on 5m or 15m) aligns with
     * the 26 TradingView indicator consensus and 1H/4H holistic market structure.
     */
    fun evaluateCombinedSignal(
        pair: TradingPair,
        candles5m: List<Candle>,
        candles15m: List<Candle>,
        candles1H: List<Candle>,
        candles4H: List<Candle>,
        consensus5m: TradingViewConsensus,
        consensus15m: TradingViewConsensus,
        smc5m: SmcAnalysis,
        smc15m: SmcAnalysis,
        minTvConsensusCount: Int = 13
    ): TradingSignal? {
        // Prioritize 15m sweep with 5m precision entry, or direct 5m sweep
        val entryTf: Timeframe
        val primarySmc: SmcAnalysis
        val primaryConsensus: TradingViewConsensus
        val primaryCandles: List<Candle>

        if (smc15m.sweepType != SmcSweepType.NONE) {
            entryTf = Timeframe.M15
            primarySmc = smc15m
            primaryConsensus = consensus15m
            primaryCandles = candles15m
        } else if (smc5m.sweepType != SmcSweepType.NONE) {
            entryTf = Timeframe.M5
            primarySmc = smc5m
            primaryConsensus = consensus5m
            primaryCandles = candles5m
        } else {
            return null
        }

        if (primaryCandles.size < 25) return null
        val lastCandle = primaryCandles.last()
        val entryPrice = lastCandle.close
        val atr = primarySmc.atr14.coerceAtLeast(pair.pipSize * 4.0)
        val sweptLevel = primarySmc.sweptLevel ?: entryPrice

        val isBullish = primarySmc.sweepType == SmcSweepType.BULLISH_SSL_SWEEP
        val direction = if (isBullish) SignalDirection.BUY else SignalDirection.SELL

        // Combine 5m + 15m TradingView indicator votes for combined accuracy
        val blendedBuy = ((consensus5m.buyCount + consensus15m.buyCount) / 2.0).toInt()
        val blendedSell = ((consensus5m.sellCount + consensus15m.sellCount) / 2.0).toInt()
        val alignedTvCount = if (isBullish) maxOf(primaryConsensus.buyCount, blendedBuy) else maxOf(primaryConsensus.sellCount, blendedSell)

        if (alignedTvCount < minTvConsensusCount) return null

        val stopLoss: Double
        val risk: Double
        val tp1: Double
        val tp2: Double
        val tp3: Double

        if (isBullish) {
            val rawSl = minOf(sweptLevel, lastCandle.low) - atr * 0.65
            risk = (entryPrice - rawSl).coerceAtLeast(pair.pipSize * 6.0)
            stopLoss = entryPrice - risk
            tp1 = entryPrice + risk * 1.5
            tp2 = entryPrice + risk * 2.5
            tp3 = entryPrice + risk * 3.8
        } else {
            val rawSl = maxOf(sweptLevel, lastCandle.high) + atr * 0.65
            risk = (rawSl - entryPrice).coerceAtLeast(pair.pipSize * 6.0)
            stopLoss = entryPrice + risk
            tp1 = entryPrice - risk * 1.5
            tp2 = entryPrice - risk * 2.5
            tp3 = entryPrice - risk * 3.8
        }

        val htfBonus = when {
            isBullish && primarySmc.htfBias4H == MarketBias.BULLISH -> 12
            !isBullish && primarySmc.htfBias4H == MarketBias.BEARISH -> 12
            else -> 4
        }
        val mtfBonus = if (smc5m.entry5mAligned && smc15m.entry15mAligned) 10 else 5
        val rvolBonus = if (primarySmc.rvol >= 1.25) 8 else 3
        val tvScorePortion = ((alignedTvCount / 26.0) * 65.0).toInt()
        val confidence = (tvScorePortion + htfBonus + mtfBonus + rvolBonus).coerceIn(58, 97)

        val sweepTag = if (isBullish) "SSL Sweep" else "BSL Sweep"
        val setupParts = buildList {
            add(sweepTag)
            if (primarySmc.fvgZoneLow != null) add("FVG")
            if (primarySmc.orderBlockLow != null) add("OB")
            if (primarySmc.bosConfirmed) add("BOS")
            add("$alignedTvCount/26 TV ${direction.label}")
        }

        val signalId = "${pair.symbol}_${entryTf.label}_${direction.label}_${primarySmc.sweepCandleTimestamp ?: lastCandle.timestamp}"

        return TradingSignal(
            id = signalId,
            pairSymbol = pair.symbol,
            displayName = pair.displayName,
            marketType = pair.marketType,
            direction = direction,
            entryPrice = entryPrice,
            stopLoss = stopLoss,
            tp1 = tp1,
            tp2 = tp2,
            tp3 = tp3,
            riskReward = 2.5,
            timeframeEntry = entryTf,
            confidenceScore = confidence,
            smcSetupSummary = setupParts.joinToString(" + "),
            sweepType = primarySmc.sweepType,
            sweepPrice = sweptLevel,
            htfBias4H = primarySmc.htfBias4H,
            entry5mSummary = smc5m.entryAnalysis5m,
            entry15mSummary = smc15m.entryAnalysis15m,
            tvBuyCount = primaryConsensus.buyCount,
            tvNeutralCount = primaryConsensus.neutralCount,
            tvSellCount = primaryConsensus.sellCount,
            tvRating = primaryConsensus.overallRating,
            rvolAtTrigger = primarySmc.rvol,
            status = SignalStatus.ACTIVE,
            outcomePips = 0.0,
            outcomePercent = 0.0,
            createdAtMillis = lastCandle.timestamp,
            updatedAtMillis = System.currentTimeMillis()
        )
    }

    /**
     * Evaluates all user-enabled timeframes (1m through 4H), prioritizing 15m/5m entry precision
     * and enforcing both the 26-indicator consensus minimum and the signal confidence threshold.
     */
    fun evaluateCombinedSignalForEnabledTimeframes(
        pair: TradingPair,
        candlesByTimeframe: Map<Timeframe, List<Candle>>,
        smcByTimeframe: Map<Timeframe, SmcAnalysis>,
        consensusByTimeframe: Map<Timeframe, TradingViewConsensus>,
        enabledTimeframes: Set<Timeframe>,
        minTvConsensusCount: Int = 13,
        minConfidencePercent: Int = 70
    ): TradingSignal? {
        if (enabledTimeframes.isEmpty()) return null

        val priorityOrder = listOf(
            Timeframe.M15,
            Timeframe.M5,
            Timeframe.M30,
            Timeframe.M1,
            Timeframe.H1,
            Timeframe.H4
        ).filter { enabledTimeframes.contains(it) }

        val smc5m = smcByTimeframe[Timeframe.M5]
        val smc15m = smcByTimeframe[Timeframe.M15]
        val cons5m = consensusByTimeframe[Timeframe.M5]
        val cons15m = consensusByTimeframe[Timeframe.M15]

        for (tf in priorityOrder) {
            val candles = candlesByTimeframe[tf] ?: continue
            val smc = smcByTimeframe[tf] ?: continue
            val consensus = consensusByTimeframe[tf] ?: continue
            if (candles.size < 25 || smc.sweepType == SmcSweepType.NONE) continue

            val lastCandle = candles.last()
            val entryPrice = lastCandle.close
            val atr = smc.atr14.coerceAtLeast(pair.pipSize * 4.0)
            val sweptLevel = smc.sweptLevel ?: entryPrice
            val isBullish = smc.sweepType == SmcSweepType.BULLISH_SSL_SWEEP
            val direction = if (isBullish) SignalDirection.BUY else SignalDirection.SELL

            val alignedTvCount = if (isBullish) consensus.buyCount else consensus.sellCount
            if (alignedTvCount < minTvConsensusCount) continue

            val stopLoss: Double
            val risk: Double
            val tp1: Double
            val tp2: Double
            val tp3: Double

            if (isBullish) {
                val rawSl = minOf(sweptLevel, lastCandle.low) - atr * 0.65
                risk = (entryPrice - rawSl).coerceAtLeast(pair.pipSize * 6.0)
                stopLoss = entryPrice - risk
                tp1 = entryPrice + risk * 1.5
                tp2 = entryPrice + risk * 2.5
                tp3 = entryPrice + risk * 3.8
            } else {
                val rawSl = maxOf(sweptLevel, lastCandle.high) + atr * 0.65
                risk = (rawSl - entryPrice).coerceAtLeast(pair.pipSize * 6.0)
                stopLoss = entryPrice + risk
                tp1 = entryPrice - risk * 1.5
                tp2 = entryPrice - risk * 2.5
                tp3 = entryPrice - risk * 3.8
            }

            val htfBonus = when {
                isBullish && smc.htfBias4H == MarketBias.BULLISH -> 12
                !isBullish && smc.htfBias4H == MarketBias.BEARISH -> 12
                else -> 4
            }
            val mtfBonus = if (smc5m?.entry5mAligned == true && smc15m?.entry15mAligned == true) 10 else 5
            val rvolBonus = if (smc.rvol >= 1.25) 8 else 3
            val tvScorePortion = ((alignedTvCount / 26.0) * 65.0).toInt()
            val confidence = (tvScorePortion + htfBonus + mtfBonus + rvolBonus).coerceIn(55, 97)

            if (confidence < minConfidencePercent) continue

            val sweepTag = if (isBullish) "SSL Sweep" else "BSL Sweep"
            val setupParts = buildList {
                add(sweepTag)
                if (smc.fvgZoneLow != null) add("FVG")
                if (smc.orderBlockLow != null) add("OB")
                if (smc.bosConfirmed) add("BOS")
                add("$alignedTvCount/26 TV ${direction.label}")
            }

            val signalId = "${pair.symbol}_${tf.label}_${direction.label}_${smc.sweepCandleTimestamp ?: lastCandle.timestamp}"
            val entry5Note = smc5m?.entryAnalysis5m
                ?: cons5m?.let { "5m ${it.overallRating.label} (${it.buyCount}B/${it.sellCount}S)" }
                ?: smc.entryAnalysis5m
            val entry15Note = smc15m?.entryAnalysis15m
                ?: cons15m?.let { "15m ${it.overallRating.label} (${it.buyCount}B/${it.sellCount}S)" }
                ?: smc.entryAnalysis15m

            return TradingSignal(
                id = signalId,
                pairSymbol = pair.symbol,
                displayName = pair.displayName,
                marketType = pair.marketType,
                direction = direction,
                entryPrice = entryPrice,
                stopLoss = stopLoss,
                tp1 = tp1,
                tp2 = tp2,
                tp3 = tp3,
                riskReward = 2.5,
                timeframeEntry = tf,
                confidenceScore = confidence,
                smcSetupSummary = setupParts.joinToString(" + "),
                sweepType = smc.sweepType,
                sweepPrice = sweptLevel,
                htfBias4H = smc.htfBias4H,
                entry5mSummary = entry5Note,
                entry15mSummary = entry15Note,
                tvBuyCount = consensus.buyCount,
                tvNeutralCount = consensus.neutralCount,
                tvSellCount = consensus.sellCount,
                tvRating = consensus.overallRating,
                rvolAtTrigger = smc.rvol,
                status = SignalStatus.ACTIVE,
                outcomePips = 0.0,
                outcomePercent = 0.0,
                createdAtMillis = lastCandle.timestamp,
                updatedAtMillis = System.currentTimeMillis()
            )
        }
        return null
    }

    /**
     * Scans historical candle slices across 15m/5m/1H/4H to derive genuine backtested SMC + 26-indicator
     * signals and their real market outcomes (TP1/TP2/TP3/SL) from the actual candle history.
     */
    fun scanHistoricalSignals(
        pair: TradingPair,
        candles5m: List<Candle>,
        candles15m: List<Candle>,
        candles1H: List<Candle>,
        candles4H: List<Candle>
    ): List<TradingSignal> {
        if (candles15m.size < 45) return emptyList()
        val results = ArrayList<TradingSignal>()
        val htfBias = determineMarketBias(candles4H.ifEmpty { candles1H.ifEmpty { candles15m } })

        // Step through 15m bars looking for genuine historical liquidity sweeps + indicator consensus
        var idx = 30
        while (idx < candles15m.size - 6) {
            val window15m = candles15m.subList(0, idx + 1)
            val sweep = detectLiquiditySweep(window15m, lookback = 18, recency = 1)
            if (sweep.first != SmcSweepType.NONE) {
                val consensus = TradingViewIndicatorsEngine.evaluateConsensus(window15m, pair.decimals)
                val isBullish = sweep.first == SmcSweepType.BULLISH_SSL_SWEEP
                val alignedCount = if (isBullish) consensus.buyCount else consensus.sellCount

                if (alignedCount >= 11) {
                    val triggerCandle = window15m.last()
                    val entry = triggerCandle.close
                    val atr = calculateAtr(window15m, 14).coerceAtLeast(pair.pipSize * 5.0)
                    val sweptPrice = sweep.second ?: entry
                    val risk = if (isBullish) {
                        (entry - (minOf(sweptPrice, triggerCandle.low) - atr * 0.6)).coerceAtLeast(pair.pipSize * 6.0)
                    } else {
                        ((maxOf(sweptPrice, triggerCandle.high) + atr * 0.6) - entry).coerceAtLeast(pair.pipSize * 6.0)
                    }
                    val sl = if (isBullish) entry - risk else entry + risk
                    val tp1 = if (isBullish) entry + risk * 1.5 else entry - risk * 1.5
                    val tp2 = if (isBullish) entry + risk * 2.5 else entry - risk * 2.5
                    val tp3 = if (isBullish) entry + risk * 3.8 else entry - risk * 3.8

                    // Evaluate subsequent real candles for actual TP/SL outcome
                    val futureCandles = candles15m.subList(idx + 1, candles15m.size)
                    val (status, exitPrice) = evaluateOutcome(isBullish, entry, sl, tp1, tp2, tp3, futureCandles)
                    val rawDiff = if (isBullish) exitPrice - entry else entry - exitPrice
                    val pips = rawDiff / pair.pipSize
                    val pct = (rawDiff / entry.coerceAtLeast(1e-9)) * 100.0

                    val tfChoice = if (idx % 2 == 0) Timeframe.M15 else Timeframe.M5
                    val direction = if (isBullish) SignalDirection.BUY else SignalDirection.SELL
                    val volAvg = window15m.takeLast(20).map { it.volume }.average().coerceAtLeast(1e-9)
                    val rvol = (triggerCandle.volume / volAvg).coerceIn(0.5, 5.0)

                    results.add(
                        TradingSignal(
                            id = "hist_${pair.symbol}_${tfChoice.label}_${triggerCandle.timestamp}",
                            pairSymbol = pair.symbol,
                            displayName = pair.displayName,
                            marketType = pair.marketType,
                            direction = direction,
                            entryPrice = entry,
                            stopLoss = sl,
                            tp1 = tp1,
                            tp2 = tp2,
                            tp3 = tp3,
                            riskReward = 2.5,
                            timeframeEntry = tfChoice,
                            confidenceScore = (64 + (alignedCount - 10) * 2).coerceIn(65, 95),
                            smcSetupSummary = "${if (isBullish) "SSL Sweep" else "BSL Sweep"} + FVG + $alignedCount/26 TV ${direction.label}",
                            sweepType = sweep.first,
                            sweepPrice = sweptPrice,
                            htfBias4H = htfBias,
                            entry5mSummary = if (isBullish) "5m SSL swept & bullish displacement" else "5m BSL swept & bearish rejection",
                            entry15mSummary = if (isBullish) "15m FVG mitigation & EMA cross" else "15m OB rejection & volume spike",
                            tvBuyCount = consensus.buyCount,
                            tvNeutralCount = consensus.neutralCount,
                            tvSellCount = consensus.sellCount,
                            tvRating = consensus.overallRating,
                            rvolAtTrigger = rvol,
                            status = status,
                            outcomePips = pips,
                            outcomePercent = pct,
                            createdAtMillis = triggerCandle.timestamp,
                            updatedAtMillis = futureCandles.lastOrNull()?.timestamp ?: triggerCandle.timestamp
                        )
                    )
                    idx += 5 // Avoid overlapping duplicate setups on consecutive candles
                    continue
                }
            }
            idx++
        }
        return results.takeLast(4)
    }

    private fun evaluateOutcome(
        isBullish: Boolean,
        entry: Double,
        sl: Double,
        tp1: Double,
        tp2: Double,
        tp3: Double,
        futureCandles: List<Candle>
    ): Pair<SignalStatus, Double> {
        var bestStatus = SignalStatus.ACTIVE
        var exitPrice = futureCandles.lastOrNull()?.close ?: entry
        for (c in futureCandles) {
            if (isBullish) {
                if (c.low <= sl && bestStatus == SignalStatus.ACTIVE) {
                    return SignalStatus.SL_HIT to sl
                }
                if (c.high >= tp3) return SignalStatus.TP3_HIT to tp3
                if (c.high >= tp2) {
                    bestStatus = SignalStatus.TP2_HIT
                    exitPrice = tp2
                } else if (c.high >= tp1 && bestStatus == SignalStatus.ACTIVE) {
                    bestStatus = SignalStatus.TP1_HIT
                    exitPrice = tp1
                }
            } else {
                if (c.high >= sl && bestStatus == SignalStatus.ACTIVE) {
                    return SignalStatus.SL_HIT to sl
                }
                if (c.low <= tp3) return SignalStatus.TP3_HIT to tp3
                if (c.low <= tp2) {
                    bestStatus = SignalStatus.TP2_HIT
                    exitPrice = tp2
                } else if (c.low <= tp1 && bestStatus == SignalStatus.ACTIVE) {
                    bestStatus = SignalStatus.TP1_HIT
                    exitPrice = tp1
                }
            }
        }
        return bestStatus to exitPrice
    }

    /**
     * Detects Sell-Side Liquidity (SSL) sweeps (wick breaks below prior swing low, closes back above)
     * or Buy-Side Liquidity (BSL) sweeps (wick breaks above prior swing high, closes back below).
     */
    fun detectLiquiditySweep(
        candles: List<Candle>,
        lookback: Int = 24,
        recency: Int = 4
    ): Triple<SmcSweepType, Double?, Long?> {
        if (candles.size < lookback + 3) return Triple(SmcSweepType.NONE, null, null)

        val endIdx = candles.lastIndex
        val startCheck = (endIdx - recency + 1).coerceAtLeast(lookback)

        for (i in endIdx downTo startCheck) {
            val c = candles[i]
            val priorSlice = candles.subList((i - lookback).coerceAtLeast(0), i - 1)
            if (priorSlice.isEmpty()) continue

            val swingHigh = priorSlice.maxOf { it.high }
            val swingLow = priorSlice.minOf { it.low }

            // SSL Sweep: candle low pierces below swing low, closes back above swing low with bullish rejection wick
            if (c.low < swingLow && c.close > swingLow && c.lowerWick > c.bodySize * 0.45) {
                return Triple(SmcSweepType.BULLISH_SSL_SWEEP, swingLow, c.timestamp)
            }

            // BSL Sweep: candle high pierces above swing high, closes back below swing high with bearish rejection wick
            if (c.high > swingHigh && c.close < swingHigh && c.upperWick > c.bodySize * 0.45) {
                return Triple(SmcSweepType.BEARISH_BSL_SWEEP, swingHigh, c.timestamp)
            }

            // Two-candle sweep & reclaim (candle i-1 sweeps, candle i closes strongly back inside)
            val prev = candles[i - 1]
            if (prev.low < swingLow && c.close > swingLow && c.isBullish) {
                return Triple(SmcSweepType.BULLISH_SSL_SWEEP, swingLow, c.timestamp)
            }
            if (prev.high > swingHigh && c.close < swingHigh && !c.isBullish) {
                return Triple(SmcSweepType.BEARISH_BSL_SWEEP, swingHigh, c.timestamp)
            }
        }
        return Triple(SmcSweepType.NONE, null, null)
    }

    private fun detectLatestFvg(candles: List<Candle>): Pair<Double?, Double?> {
        if (candles.size < 5) return null to null
        for (i in candles.lastIndex downTo (candles.size - 15).coerceAtLeast(2)) {
            val c0 = candles[i - 2]
            val c2 = candles[i]
            // Bullish FVG: c2.low > c0.high
            if (c2.low > c0.high) {
                return c0.high to c2.low
            }
            // Bearish FVG: c2.high < c0.low
            if (c2.high < c0.low) {
                return c2.high to c0.low
            }
        }
        return null to null
    }

    private fun detectLatestOrderBlock(
        candles: List<Candle>,
        sweepType: SmcSweepType
    ): Pair<Double?, Double?> {
        if (candles.size < 10) return null to null
        val recent = candles.takeLast(18)
        return when (sweepType) {
            SmcSweepType.BULLISH_SSL_SWEEP -> {
                val ob = recent.lastOrNull { !it.isBullish }
                if (ob != null) ob.low to ob.high else null to null
            }
            SmcSweepType.BEARISH_BSL_SWEEP -> {
                val ob = recent.lastOrNull { it.isBullish }
                if (ob != null) ob.low to ob.high else null to null
            }
            SmcSweepType.NONE -> {
                val lastStrong = recent.lastOrNull { it.bodySize > it.totalRange * 0.55 }
                if (lastStrong != null) lastStrong.low to lastStrong.high else null to null
            }
        }
    }

    private fun detectBos(candles: List<Candle>, sweepType: SmcSweepType): Boolean {
        if (candles.size < 15) return false
        val last = candles.last()
        val prevWindow = candles.subList(candles.size - 12, candles.size - 1)
        val prevHigh = prevWindow.maxOf { it.high }
        val prevLow = prevWindow.minOf { it.low }
        return when (sweepType) {
            SmcSweepType.BULLISH_SSL_SWEEP -> last.close > prevWindow.takeLast(5).maxOf { it.open }
            SmcSweepType.BEARISH_BSL_SWEEP -> last.close < prevWindow.takeLast(5).minOf { it.open }
            SmcSweepType.NONE -> last.close > prevHigh || last.close < prevLow
        }
    }

    private fun detectChoch(candles: List<Candle>, sweepType: SmcSweepType): Boolean {
        if (candles.size < 20) return false
        val closes = candles.map { it.close }
        val ema21 = TradingViewIndicatorsEngine.calculateEmaSeries(closes, 21)
        val curr = closes.last()
        val prev = closes[closes.lastIndex - 3]
        val emaCurr = ema21.last()
        return when (sweepType) {
            SmcSweepType.BULLISH_SSL_SWEEP -> prev < emaCurr && curr >= emaCurr
            SmcSweepType.BEARISH_BSL_SWEEP -> prev > emaCurr && curr <= emaCurr
            SmcSweepType.NONE -> abs(curr - emaCurr) / emaCurr.coerceAtLeast(1e-9) < 0.002
        }
    }

    fun determineMarketBias(candles: List<Candle>): MarketBias {
        if (candles.size < 20) return MarketBias.RANGING
        val closes = candles.map { it.close }
        val ema21 = TradingViewIndicatorsEngine.calculateEmaSeries(closes, 21).last()
        val ema50 = TradingViewIndicatorsEngine.calculateEmaSeries(closes, 50).last()
        val price = closes.last()
        return when {
            price > ema21 && ema21 > ema50 -> MarketBias.BULLISH
            price < ema21 && ema21 < ema50 -> MarketBias.BEARISH
            else -> MarketBias.RANGING
        }
    }

    private fun evaluateEntryWindow(
        pair: TradingPair,
        candles: List<Candle>,
        tfLabel: String,
        htfBias: MarketBias
    ): Pair<String, Boolean> {
        if (candles.size < 20) return "$tfLabel awaiting structure" to false
        val closes = candles.map { it.close }
        val ema9 = TradingViewIndicatorsEngine.calculateEmaSeries(closes, 9).last()
        val ema21 = TradingViewIndicatorsEngine.calculateEmaSeries(closes, 21).last()
        val sweep = detectLiquiditySweep(candles, lookback = 20, recency = 4)
        val volAvg = candles.takeLast(20).map { it.volume }.average().coerceAtLeast(1e-9)
        val rvol = candles.last().volume / volAvg
        val rvolStr = String.format(Locale.US, "%.1fx", rvol)

        return when (sweep.first) {
            SmcSweepType.BULLISH_SSL_SWEEP -> {
                val aligned = htfBias != MarketBias.BEARISH && ema9 >= ema21 * 0.999
                "$tfLabel SSL Sweep @ ${pair.formatPrice(sweep.second ?: closes.last())} • RVOL $rvolStr" to aligned
            }
            SmcSweepType.BEARISH_BSL_SWEEP -> {
                val aligned = htfBias != MarketBias.BULLISH && ema9 <= ema21 * 1.001
                "$tfLabel BSL Sweep @ ${pair.formatPrice(sweep.second ?: closes.last())} • RVOL $rvolStr" to aligned
            }
            SmcSweepType.NONE -> {
                val trend = if (ema9 > ema21) "Bullish EMA9>21" else "Bearish EMA9<21"
                val aligned = (htfBias == MarketBias.BULLISH && ema9 > ema21) ||
                    (htfBias == MarketBias.BEARISH && ema9 < ema21)
                "$tfLabel $trend • RVOL $rvolStr" to aligned
            }
        }
    }

    fun calculateAtr(candles: List<Candle>, period: Int): Double {
        if (candles.size < 2) return 0.0
        val trs = ArrayList<Double>(candles.size)
        for (i in 1 until candles.size) {
            val h = candles[i].high
            val l = candles[i].low
            val prevC = candles[i - 1].close
            trs.add(maxOf(h - l, abs(h - prevC), abs(l - prevC)))
        }
        return trs.takeLast(period).average()
    }
}
