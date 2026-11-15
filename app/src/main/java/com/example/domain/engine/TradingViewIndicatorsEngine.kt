package com.example.domain.engine

import com.example.domain.model.Candle
import com.example.domain.model.ConsensusRating
import com.example.domain.model.IndicatorCategory
import com.example.domain.model.IndicatorResult
import com.example.domain.model.IndicatorVote
import com.example.domain.model.TradingViewConsensus
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Computes the complete 26-indicator TradingView Technical Analysis consensus:
 * - 11 Oscillators (RSI 14, Stoch %K, CCI 20, ADX 14, AO, Momentum 10, MACD, Stoch RSI, Williams %R, Bull Bear Power, Ultimate Oscillator)
 * - 15 Moving Averages (EMA 10/20/30/50/100/200, SMA 10/20/30/50/100/200, Ichimoku Base Line, VWMA 20, Hull MA 9)
 */
object TradingViewIndicatorsEngine {

    fun evaluateConsensus(candles: List<Candle>, priceDecimals: Int): TradingViewConsensus {
        if (candles.size < 30) {
            return neutralFallback()
        }

        val closes = candles.map { it.close }
        val highs = candles.map { it.high }
        val lows = candles.map { it.low }
        val volumes = candles.map { it.volume }
        val currentPrice = closes.last()

        val oscillators = buildOscillators(closes, highs, lows, priceDecimals)
        val movingAverages = buildMovingAverages(closes, highs, lows, volumes, currentPrice, priceDecimals)

        val oscBuy = oscillators.count { it.vote == IndicatorVote.BUY }
        val oscNeutral = oscillators.count { it.vote == IndicatorVote.NEUTRAL }
        val oscSell = oscillators.count { it.vote == IndicatorVote.SELL }

        val maBuy = movingAverages.count { it.vote == IndicatorVote.BUY }
        val maNeutral = movingAverages.count { it.vote == IndicatorVote.NEUTRAL }
        val maSell = movingAverages.count { it.vote == IndicatorVote.SELL }

        val totalBuy = oscBuy + maBuy
        val totalNeutral = oscNeutral + maNeutral
        val totalSell = oscSell + maSell

        val totalScore = (totalBuy - totalSell).toFloat() / 26f

        return TradingViewConsensus(
            oscillators = oscillators,
            movingAverages = movingAverages,
            buyCount = totalBuy,
            neutralCount = totalNeutral,
            sellCount = totalSell,
            oscillatorBuy = oscBuy,
            oscillatorNeutral = oscNeutral,
            oscillatorSell = oscSell,
            maBuy = maBuy,
            maNeutral = maNeutral,
            maSell = maSell,
            overallRating = ratingFromScore(totalBuy - totalSell, 26),
            oscillatorRating = ratingFromScore(oscBuy - oscSell, 11),
            maRating = ratingFromScore(maBuy - maSell, 15),
            scoreNormalized = totalScore.coerceIn(-1f, 1f)
        )
    }

    private fun buildOscillators(
        closes: List<Double>,
        highs: List<Double>,
        lows: List<Double>,
        decimals: Int
    ): List<IndicatorResult> {
        val list = ArrayList<IndicatorResult>(11)

        // 1. RSI (14)
        val rsiSeries = calculateRsiSeries(closes, 14)
        val rsi = rsiSeries.lastOrNull() ?: 50.0
        val rsiPrev = rsiSeries.getOrNull(rsiSeries.lastIndex - 1) ?: rsi
        val rsiVote = when {
            rsi < 30.0 && rsi > rsiPrev -> IndicatorVote.BUY
            rsi > 70.0 && rsi < rsiPrev -> IndicatorVote.SELL
            rsi <= 35.0 -> IndicatorVote.BUY
            rsi >= 65.0 -> IndicatorVote.SELL
            else -> IndicatorVote.NEUTRAL
        }
        list.add(IndicatorResult("RSI (14)", fmt(rsi, 2), rsiVote, IndicatorCategory.OSCILLATOR))

        // 2. Stochastic %K (14, 3, 3)
        val stochSeries = calculateStochKSeries(closes, highs, lows, 14, 3)
        val stochK = stochSeries.lastOrNull() ?: 50.0
        val stochD = if (stochSeries.size >= 3) stochSeries.takeLast(3).average() else stochK
        val stochVote = when {
            stochK < 20.0 && stochK > stochD -> IndicatorVote.BUY
            stochK > 80.0 && stochK < stochD -> IndicatorVote.SELL
            stochK < 25.0 -> IndicatorVote.BUY
            stochK > 75.0 -> IndicatorVote.SELL
            else -> IndicatorVote.NEUTRAL
        }
        list.add(IndicatorResult("Stochastic %K (14,3,3)", fmt(stochK, 2), stochVote, IndicatorCategory.OSCILLATOR))

        // 3. CCI (20)
        val cciSeries = calculateCciSeries(closes, highs, lows, 20)
        val cci = cciSeries.lastOrNull() ?: 0.0
        val cciPrev = cciSeries.getOrNull(cciSeries.lastIndex - 1) ?: cci
        val cciVote = when {
            cci < -100.0 && cci > cciPrev -> IndicatorVote.BUY
            cci > 100.0 && cci < cciPrev -> IndicatorVote.SELL
            cci < -120.0 -> IndicatorVote.BUY
            cci > 120.0 -> IndicatorVote.SELL
            else -> IndicatorVote.NEUTRAL
        }
        list.add(IndicatorResult("CCI (20)", fmt(cci, 2), cciVote, IndicatorCategory.OSCILLATOR))

        // 4. ADX (14)
        val (adx, plusDi, minusDi) = calculateAdx(closes, highs, lows, 14)
        val adxVote = when {
            adx > 20.0 && plusDi > minusDi -> IndicatorVote.BUY
            adx > 20.0 && minusDi > plusDi -> IndicatorVote.SELL
            else -> IndicatorVote.NEUTRAL
        }
        list.add(IndicatorResult("ADX (14)", fmt(adx, 2), adxVote, IndicatorCategory.OSCILLATOR))

        // 5. Awesome Oscillator
        val midpoints = highs.indices.map { (highs[it] + lows[it]) / 2.0 }
        val ao5 = calculateSmaSeries(midpoints, 5)
        val ao34 = calculateSmaSeries(midpoints, 34)
        val aoCurr = (ao5.lastOrNull() ?: 0.0) - (ao34.lastOrNull() ?: 0.0)
        val aoPrev = (ao5.getOrNull(ao5.lastIndex - 1) ?: 0.0) - (ao34.getOrNull(ao34.lastIndex - 1) ?: 0.0)
        val aoVote = when {
            aoCurr > 0.0 && aoCurr > aoPrev -> IndicatorVote.BUY
            aoCurr < 0.0 && aoCurr < aoPrev -> IndicatorVote.SELL
            else -> IndicatorVote.NEUTRAL
        }
        list.add(IndicatorResult("Awesome Oscillator", fmt(aoCurr, decimals.coerceAtLeast(4)), aoVote, IndicatorCategory.OSCILLATOR))

        // 6. Momentum (10)
        val momCurr = if (closes.size > 10) closes.last() - closes[closes.size - 11] else 0.0
        val momPrev = if (closes.size > 11) closes[closes.size - 2] - closes[closes.size - 12] else 0.0
        val momVote = when {
            momCurr > 0.0 && momCurr >= momPrev -> IndicatorVote.BUY
            momCurr < 0.0 && momCurr <= momPrev -> IndicatorVote.SELL
            else -> IndicatorVote.NEUTRAL
        }
        list.add(IndicatorResult("Momentum (10)", fmt(momCurr, decimals.coerceAtLeast(4)), momVote, IndicatorCategory.OSCILLATOR))

        // 7. MACD Level (12, 26)
        val ema12 = calculateEmaSeries(closes, 12)
        val ema26 = calculateEmaSeries(closes, 26)
        val macdLine = ema12.indices.map { ema12[it] - ema26[it] }
        val signalLine = calculateEmaSeries(macdLine, 9)
        val macdCurr = macdLine.lastOrNull() ?: 0.0
        val sigCurr = signalLine.lastOrNull() ?: 0.0
        val macdVote = when {
            macdCurr > sigCurr -> IndicatorVote.BUY
            macdCurr < sigCurr -> IndicatorVote.SELL
            else -> IndicatorVote.NEUTRAL
        }
        list.add(IndicatorResult("MACD Level (12,26)", fmt(macdCurr, decimals.coerceAtLeast(4)), macdVote, IndicatorCategory.OSCILLATOR))

        // 8. Stochastic RSI Fast (3, 3, 14, 14)
        val stochRsi = calculateStochRsiFast(rsiSeries, 14, 3)
        val stochRsiVote = when {
            stochRsi < 20.0 -> IndicatorVote.BUY
            stochRsi > 80.0 -> IndicatorVote.SELL
            else -> IndicatorVote.NEUTRAL
        }
        list.add(IndicatorResult("Stoch RSI Fast (3,3,14,14)", fmt(stochRsi, 2), stochRsiVote, IndicatorCategory.OSCILLATOR))

        // 9. Williams %R (14)
        val willR = calculateWilliamsR(closes.last(), highs.takeLast(14), lows.takeLast(14))
        val willRVote = when {
            willR < -80.0 -> IndicatorVote.BUY
            willR > -20.0 -> IndicatorVote.SELL
            else -> IndicatorVote.NEUTRAL
        }
        list.add(IndicatorResult("Williams %R (14)", fmt(willR, 2), willRVote, IndicatorCategory.OSCILLATOR))

        // 10. Bull Bear Power (13)
        val ema13 = calculateEmaSeries(closes, 13).lastOrNull() ?: closes.last()
        val bullPower = highs.last() - ema13
        val bearPower = lows.last() - ema13
        val bbp = bullPower + bearPower
        val bbpVote = when {
            bbp > 0.0 && bearPower > -abs(bullPower) * 0.5 -> IndicatorVote.BUY
            bbp < 0.0 && bullPower < abs(bearPower) * 0.5 -> IndicatorVote.SELL
            else -> IndicatorVote.NEUTRAL
        }
        list.add(IndicatorResult("Bull Bear Power (13)", fmt(bbp, decimals.coerceAtLeast(4)), bbpVote, IndicatorCategory.OSCILLATOR))

        // 11. Ultimate Oscillator (7, 14, 28)
        val uo = calculateUltimateOscillator(closes, highs, lows)
        val uoVote = when {
            uo > 65.0 -> IndicatorVote.BUY
            uo < 35.0 -> IndicatorVote.SELL
            else -> IndicatorVote.NEUTRAL
        }
        list.add(IndicatorResult("Ultimate Osc (7,14,28)", fmt(uo, 2), uoVote, IndicatorCategory.OSCILLATOR))

        return list
    }

    private fun buildMovingAverages(
        closes: List<Double>,
        highs: List<Double>,
        lows: List<Double>,
        volumes: List<Double>,
        price: Double,
        decimals: Int
    ): List<IndicatorResult> {
        val list = ArrayList<IndicatorResult>(15)
        val periods = listOf(10, 20, 30, 50, 100, 200)

        for (p in periods) {
            val emaVal = calculateEmaSeries(closes, p).lastOrNull() ?: price
            list.add(
                IndicatorResult(
                    name = "EMA ($p)",
                    valueFormatted = fmt(emaVal, decimals),
                    vote = maVote(price, emaVal),
                    category = IndicatorCategory.MOVING_AVERAGE
                )
            )
            val smaVal = calculateSmaSeries(closes, p).lastOrNull() ?: price
            list.add(
                IndicatorResult(
                    name = "SMA ($p)",
                    valueFormatted = fmt(smaVal, decimals),
                    vote = maVote(price, smaVal),
                    category = IndicatorCategory.MOVING_AVERAGE
                )
            )
        }

        // 13th MA: Ichimoku Base Line (Kijun-sen 26)
        val kijunWindow = minOf(26, highs.size)
        val kijunHigh = highs.takeLast(kijunWindow).maxOrNull() ?: price
        val kijunLow = lows.takeLast(kijunWindow).minOrNull() ?: price
        val ichimokuBase = (kijunHigh + kijunLow) / 2.0
        list.add(
            IndicatorResult(
                name = "Ichimoku Base (9,26,52,26)",
                valueFormatted = fmt(ichimokuBase, decimals),
                vote = maVote(price, ichimokuBase),
                category = IndicatorCategory.MOVING_AVERAGE
            )
        )

        // 14th MA: VWMA (20)
        val vwmaWindow = minOf(20, closes.size)
        val cSlice = closes.takeLast(vwmaWindow)
        val vSlice = volumes.takeLast(vwmaWindow)
        val volSum = vSlice.sum().coerceAtLeast(1e-9)
        val vwma = cSlice.indices.sumOf { cSlice[it] * vSlice[it] } / volSum
        list.add(
            IndicatorResult(
                name = "VWMA (20)",
                valueFormatted = fmt(vwma, decimals),
                vote = maVote(price, vwma),
                category = IndicatorCategory.MOVING_AVERAGE
            )
        )

        // 15th MA: Hull Moving Average (9)
        val hma = calculateHma(closes, 9)
        list.add(
            IndicatorResult(
                name = "Hull MA (9)",
                valueFormatted = fmt(hma, decimals),
                vote = maVote(price, hma),
                category = IndicatorCategory.MOVING_AVERAGE
            )
        )

        return list
    }

    private fun maVote(price: Double, ma: Double): IndicatorVote {
        val diffRatio = (price - ma) / ma.coerceAtLeast(1e-9)
        return when {
            diffRatio > 0.00015 -> IndicatorVote.BUY
            diffRatio < -0.00015 -> IndicatorVote.SELL
            else -> IndicatorVote.NEUTRAL
        }
    }

    private fun ratingFromScore(net: Int, total: Int): ConsensusRating {
        val ratio = net.toDouble() / total.toDouble()
        return when {
            ratio >= 0.45 -> ConsensusRating.STRONG_BUY
            ratio >= 0.12 -> ConsensusRating.BUY
            ratio <= -0.45 -> ConsensusRating.STRONG_SELL
            ratio <= -0.12 -> ConsensusRating.SELL
            else -> ConsensusRating.NEUTRAL
        }
    }

    fun calculateEmaSeries(values: List<Double>, period: Int): List<Double> {
        if (values.isEmpty()) return emptyList()
        val effectivePeriod = period.coerceAtLeast(1)
        val k = 2.0 / (effectivePeriod + 1.0)
        val result = ArrayList<Double>(values.size)
        var ema = values.first()
        for (i in values.indices) {
            ema = if (i < effectivePeriod) {
                values.subList(0, i + 1).average()
            } else {
                values[i] * k + ema * (1.0 - k)
            }
            result.add(ema)
        }
        return result
    }

    fun calculateSmaSeries(values: List<Double>, period: Int): List<Double> {
        if (values.isEmpty()) return emptyList()
        val p = period.coerceAtLeast(1)
        val result = ArrayList<Double>(values.size)
        var rollingSum = 0.0
        for (i in values.indices) {
            rollingSum += values[i]
            if (i >= p) {
                rollingSum -= values[i - p]
            }
            val count = minOf(i + 1, p)
            result.add(rollingSum / count)
        }
        return result
    }

    private fun calculateWma(values: List<Double>, period: Int): Double {
        if (values.isEmpty()) return 0.0
        val slice = values.takeLast(minOf(period, values.size))
        var num = 0.0
        var den = 0.0
        for (i in slice.indices) {
            val weight = (i + 1).toDouble()
            num += slice[i] * weight
            den += weight
        }
        return if (den > 0.0) num / den else slice.last()
    }

    private fun calculateHma(closes: List<Double>, period: Int): Double {
        if (closes.size < period) return closes.lastOrNull() ?: 0.0
        val halfPeriod = (period / 2).coerceAtLeast(1)
        val sqrtPeriod = sqrt(period.toDouble()).roundToInt().coerceAtLeast(1)
        val diffSeries = ArrayList<Double>(sqrtPeriod)
        val startIdx = (closes.size - sqrtPeriod).coerceAtLeast(0)
        for (idx in startIdx until closes.size) {
            val sub = closes.subList(0, idx + 1)
            val wmaHalf = calculateWma(sub, halfPeriod)
            val wmaFull = calculateWma(sub, period)
            diffSeries.add(2.0 * wmaHalf - wmaFull)
        }
        return calculateWma(diffSeries, sqrtPeriod)
    }

    private fun calculateRsiSeries(closes: List<Double>, period: Int): List<Double> {
        if (closes.size <= period) return listOf(50.0)
        val rsis = MutableList(closes.size) { 50.0 }
        var gainSum = 0.0
        var lossSum = 0.0
        for (i in 1..period) {
            val diff = closes[i] - closes[i - 1]
            if (diff >= 0) gainSum += diff else lossSum -= diff
        }
        var avgGain = gainSum / period
        var avgLoss = lossSum / period
        rsis[period] = if (avgLoss == 0.0) 100.0 else 100.0 - (100.0 / (1.0 + avgGain / avgLoss))
        for (i in (period + 1) until closes.size) {
            val diff = closes[i] - closes[i - 1]
            val gain = if (diff > 0) diff else 0.0
            val loss = if (diff < 0) -diff else 0.0
            avgGain = (avgGain * (period - 1) + gain) / period
            avgLoss = (avgLoss * (period - 1) + loss) / period
            rsis[i] = if (avgLoss == 0.0) 100.0 else 100.0 - (100.0 / (1.0 + avgGain / avgLoss))
        }
        return rsis
    }

    private fun calculateStochKSeries(
        closes: List<Double>,
        highs: List<Double>,
        lows: List<Double>,
        kPeriod: Int,
        smoothK: Int
    ): List<Double> {
        val rawK = ArrayList<Double>(closes.size)
        for (i in closes.indices) {
            val start = (i - kPeriod + 1).coerceAtLeast(0)
            val h = highs.subList(start, i + 1).maxOrNull() ?: closes[i]
            val l = lows.subList(start, i + 1).minOrNull() ?: closes[i]
            val denom = (h - l).coerceAtLeast(1e-9)
            rawK.add(((closes[i] - l) / denom) * 100.0)
        }
        return calculateSmaSeries(rawK, smoothK)
    }

    private fun calculateCciSeries(
        closes: List<Double>,
        highs: List<Double>,
        lows: List<Double>,
        period: Int
    ): List<Double> {
        val tp = closes.indices.map { (highs[it] + lows[it] + closes[it]) / 3.0 }
        val smaTp = calculateSmaSeries(tp, period)
        val result = ArrayList<Double>(tp.size)
        for (i in tp.indices) {
            val start = (i - period + 1).coerceAtLeast(0)
            val window = tp.subList(start, i + 1)
            val mean = smaTp[i]
            val md = window.map { abs(it - mean) }.average().coerceAtLeast(1e-9)
            result.add((tp[i] - mean) / (0.015 * md))
        }
        return result
    }

    private fun calculateAdx(
        closes: List<Double>,
        highs: List<Double>,
        lows: List<Double>,
        period: Int
    ): Triple<Double, Double, Double> {
        if (closes.size <= period + 1) return Triple(20.0, 20.0, 20.0)
        var trSmoothed = 0.0
        var plusDmSmoothed = 0.0
        var minusDmSmoothed = 0.0
        val dxList = ArrayList<Double>()

        for (i in 1 until closes.size) {
            val tr = maxOf(
                highs[i] - lows[i],
                abs(highs[i] - closes[i - 1]),
                abs(lows[i] - closes[i - 1])
            )
            val upMove = highs[i] - highs[i - 1]
            val downMove = lows[i - 1] - lows[i]
            val plusDm = if (upMove > downMove && upMove > 0) upMove else 0.0
            val minusDm = if (downMove > upMove && downMove > 0) downMove else 0.0

            if (i <= period) {
                trSmoothed += tr
                plusDmSmoothed += plusDm
                minusDmSmoothed += minusDm
            } else {
                trSmoothed = trSmoothed - (trSmoothed / period) + tr
                plusDmSmoothed = plusDmSmoothed - (plusDmSmoothed / period) + plusDm
                minusDmSmoothed = minusDmSmoothed - (minusDmSmoothed / period) + minusDm
            }
            if (i >= period && trSmoothed > 1e-9) {
                val pdi = 100.0 * (plusDmSmoothed / trSmoothed)
                val mdi = 100.0 * (minusDmSmoothed / trSmoothed)
                val sum = (pdi + mdi).coerceAtLeast(1e-9)
                dxList.add(100.0 * abs(pdi - mdi) / sum)
            }
        }
        val adx = if (dxList.isEmpty()) 20.0 else dxList.takeLast(period).average()
        val pdi = if (trSmoothed > 1e-9) 100.0 * (plusDmSmoothed / trSmoothed) else 20.0
        val mdi = if (trSmoothed > 1e-9) 100.0 * (minusDmSmoothed / trSmoothed) else 20.0
        return Triple(adx, pdi, mdi)
    }

    private fun calculateStochRsiFast(rsiSeries: List<Double>, period: Int, smooth: Int): Double {
        if (rsiSeries.size < period) return 50.0
        val kList = ArrayList<Double>()
        for (i in (rsiSeries.size - smooth - 2).coerceAtLeast(0) until rsiSeries.size) {
            val start = (i - period + 1).coerceAtLeast(0)
            val window = rsiSeries.subList(start, i + 1)
            val minR = window.minOrNull() ?: 0.0
            val maxR = window.maxOrNull() ?: 100.0
            val denom = (maxR - minR).coerceAtLeast(1e-9)
            kList.add(((rsiSeries[i] - minR) / denom) * 100.0)
        }
        return kList.takeLast(smooth).average()
    }

    private fun calculateWilliamsR(close: Double, highs: List<Double>, lows: List<Double>): Double {
        val hh = highs.maxOrNull() ?: close
        val ll = lows.minOrNull() ?: close
        val denom = (hh - ll).coerceAtLeast(1e-9)
        return ((hh - close) / denom) * -100.0
    }

    private fun calculateUltimateOscillator(
        closes: List<Double>,
        highs: List<Double>,
        lows: List<Double>
    ): Double {
        if (closes.size < 29) return 50.0
        val bp = ArrayList<Double>(closes.size)
        val tr = ArrayList<Double>(closes.size)
        bp.add(0.0)
        tr.add(highs[0] - lows[0])
        for (i in 1 until closes.size) {
            val minLowPrevClose = minOf(lows[i], closes[i - 1])
            val maxHighPrevClose = maxOf(highs[i], closes[i - 1])
            bp.add(closes[i] - minLowPrevClose)
            tr.add((maxHighPrevClose - minLowPrevClose).coerceAtLeast(1e-9))
        }
        fun avgRatio(len: Int): Double {
            val bpSum = bp.takeLast(len).sum()
            val trSum = tr.takeLast(len).sum().coerceAtLeast(1e-9)
            return bpSum / trSum
        }
        return 100.0 * ((4.0 * avgRatio(7)) + (2.0 * avgRatio(14)) + avgRatio(28)) / 7.0
    }

    private fun fmt(value: Double, decimals: Int): String {
        return String.format(Locale.US, "%.${decimals.coerceIn(2, 6)}f", value)
    }

    private fun neutralFallback(): TradingViewConsensus {
        return TradingViewConsensus(
            oscillators = emptyList(),
            movingAverages = emptyList(),
            buyCount = 0,
            neutralCount = 26,
            sellCount = 0,
            oscillatorBuy = 0,
            oscillatorNeutral = 11,
            oscillatorSell = 0,
            maBuy = 0,
            maNeutral = 15,
            maSell = 0,
            overallRating = ConsensusRating.NEUTRAL,
            oscillatorRating = ConsensusRating.NEUTRAL,
            maRating = ConsensusRating.NEUTRAL,
            scoreNormalized = 0f
        )
    }
}
