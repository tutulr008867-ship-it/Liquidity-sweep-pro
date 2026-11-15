package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.model.Candle
import com.example.domain.model.SmcAnalysis
import com.example.domain.model.SmcSweepType
import com.example.domain.model.TradingPair
import com.example.domain.model.TradingSignal
import com.example.ui.theme.LocalTradingColors
import com.example.ui.theme.MonoNumericStyle
import com.example.ui.viewmodel.ChartOverlaySettings
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun CandlestickChartCanvas(
    pair: TradingPair,
    candles: List<Candle>,
    smc: SmcAnalysis?,
    activeSignal: TradingSignal?,
    overlays: ChartOverlaySettings,
    modifier: Modifier = Modifier
) {
    val tradingColors = LocalTradingColors.current
    val visibleCount = 54
    val visibleCandles = remember(candles) { candles.takeLast(visibleCount) }
    val offsetStart = (candles.size - visibleCandles.size).coerceAtLeast(0)

    var selectedIdx by remember(pair.symbol, candles.size) { mutableStateOf<Int?>(null) }

    val inspectedCandle = selectedIdx?.let { visibleCandles.getOrNull(it) } ?: visibleCandles.lastOrNull()
    val timeFormatter = remember { SimpleDateFormat("HH:mm", Locale.US) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(12.dp)
    ) {
        // Crosshair OHLCV Readout Strip
        if (inspectedCandle != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val cColor = if (inspectedCandle.isBullish) tradingColors.buy else tradingColors.sell
                Text(
                    text = "O ${pair.formatPrice(inspectedCandle.open)}  H ${pair.formatPrice(inspectedCandle.high)}  L ${pair.formatPrice(inspectedCandle.low)}  C ${pair.formatPrice(inspectedCandle.close)}",
                    style = MonoNumericStyle.copy(fontSize = 11.sp, fontWeight = FontWeight.Medium),
                    color = cColor,
                    maxLines = 1
                )
                Text(
                    text = timeFormatter.format(Date(inspectedCandle.timestamp)),
                    style = MonoNumericStyle.copy(fontSize = 11.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(265.dp)
        ) {
            if (visibleCandles.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = "Loading market candles...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(visibleCandles.size) {
                            detectTapGestures { offset ->
                                val idx = ((offset.x / size.width) * visibleCandles.size)
                                    .roundToInt()
                                    .coerceIn(0, visibleCandles.lastIndex)
                                selectedIdx = if (selectedIdx == idx) null else idx
                            }
                        }
                        .pointerInput(visibleCandles.size) {
                            detectDragGestures(
                                onDragEnd = {},
                                onDragCancel = {},
                                onDrag = { change, _ ->
                                    val idx = ((change.position.x / size.width) * visibleCandles.size)
                                        .roundToInt()
                                        .coerceIn(0, visibleCandles.lastIndex)
                                    selectedIdx = idx
                                }
                            )
                        }
                ) {
                    val chartHeight = size.height * 0.76f
                    val volTop = size.height * 0.80f
                    val volHeight = size.height * 0.19f
                    val chartWidth = size.width

                    val rawMin = visibleCandles.minOf { it.low }
                    val rawMax = visibleCandles.maxOf { it.high }

                    // Include signal levels in price range if present so lines stay visible
                    val signalPrices = listOfNotNull(
                        activeSignal?.entryPrice,
                        activeSignal?.stopLoss,
                        activeSignal?.tp1,
                        activeSignal?.tp2,
                        smc?.sweptLevel
                    )
                    val minPrice = minOf(rawMin, signalPrices.minOrNull() ?: rawMin)
                    val maxPrice = maxOf(rawMax, signalPrices.maxOrNull() ?: rawMax)
                    val priceSpan = (maxPrice - minPrice).coerceAtLeast(pair.pipSize * 2.0)
                    val paddedMin = minPrice - priceSpan * 0.06
                    val paddedMax = maxPrice + priceSpan * 0.06
                    val paddedSpan = (paddedMax - paddedMin).coerceAtLeast(1e-9)

                    fun priceToY(p: Double): Float {
                        val norm = ((p - paddedMin) / paddedSpan).toFloat()
                        return chartHeight - (norm * chartHeight)
                    }

                    // Horizontal Grid Lines
                    val gridSteps = 4
                    for (g in 0..gridSteps) {
                        val y = (chartHeight / gridSteps) * g
                        drawLine(
                            color = tradingColors.chartGrid,
                            start = Offset(0f, y),
                            end = Offset(chartWidth, y),
                            strokeWidth = 1f
                        )
                    }

                    // SMC Zones (FVG & Order Block)
                    if (overlays.showSmcZones && smc != null) {
                        if (smc.fvgZoneLow != null && smc.fvgZoneHigh != null) {
                            val topY = priceToY(maxOf(smc.fvgZoneLow, smc.fvgZoneHigh))
                            val botY = priceToY(minOf(smc.fvgZoneLow, smc.fvgZoneHigh))
                            drawRect(
                                color = tradingColors.pulse.copy(alpha = 0.10f),
                                topLeft = Offset(chartWidth * 0.45f, topY),
                                size = Size(chartWidth * 0.55f, (botY - topY).coerceAtLeast(4f))
                            )
                        }
                        if (smc.orderBlockLow != null && smc.orderBlockHigh != null) {
                            val topY = priceToY(maxOf(smc.orderBlockLow, smc.orderBlockHigh))
                            val botY = priceToY(minOf(smc.orderBlockLow, smc.orderBlockHigh))
                            drawRect(
                                color = tradingColors.sweep.copy(alpha = 0.12f),
                                topLeft = Offset(chartWidth * 0.30f, topY),
                                size = Size(chartWidth * 0.70f, (botY - topY).coerceAtLeast(4f))
                            )
                        }
                        if (smc.sweepType != SmcSweepType.NONE && smc.sweptLevel != null) {
                            val sweepY = priceToY(smc.sweptLevel)
                            drawLine(
                                color = tradingColors.sweep,
                                start = Offset(0f, sweepY),
                                end = Offset(chartWidth, sweepY),
                                strokeWidth = 2f,
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f), 0f)
                            )
                        }
                    }

                    // Signal Entry / SL / TP1 / TP2 Levels
                    if (activeSignal != null) {
                        val dash = PathEffect.dashPathEffect(floatArrayOf(14f, 8f), 0f)
                        drawLine(
                            color = tradingColors.pulse,
                            start = Offset(0f, priceToY(activeSignal.entryPrice)),
                            end = Offset(chartWidth, priceToY(activeSignal.entryPrice)),
                            strokeWidth = 2.5f
                        )
                        drawLine(
                            color = tradingColors.sell,
                            start = Offset(0f, priceToY(activeSignal.stopLoss)),
                            end = Offset(chartWidth, priceToY(activeSignal.stopLoss)),
                            strokeWidth = 2.2f,
                            pathEffect = dash
                        )
                        drawLine(
                            color = tradingColors.buy,
                            start = Offset(0f, priceToY(activeSignal.tp1)),
                            end = Offset(chartWidth, priceToY(activeSignal.tp1)),
                            strokeWidth = 2f,
                            pathEffect = dash
                        )
                        drawLine(
                            color = tradingColors.buy.copy(alpha = 0.75f),
                            start = Offset(0f, priceToY(activeSignal.tp2)),
                            end = Offset(chartWidth, priceToY(activeSignal.tp2)),
                            strokeWidth = 1.8f,
                            pathEffect = dash
                        )
                    }

                    // Candlesticks & Volume Bars
                    val stepX = chartWidth / visibleCandles.size.toFloat()
                    val candleWidth = (stepX * 0.62f).coerceIn(3f, 16f)
                    val maxVol = visibleCandles.maxOf { it.volume }.coerceAtLeast(1.0)

                    visibleCandles.forEachIndexed { idx, c ->
                        val centerX = idx * stepX + stepX / 2f
                        val cColor = if (c.isBullish) tradingColors.buy else tradingColors.sell

                        // Wick
                        drawLine(
                            color = cColor,
                            start = Offset(centerX, priceToY(c.high)),
                            end = Offset(centerX, priceToY(c.low)),
                            strokeWidth = 2f,
                            cap = StrokeCap.Round
                        )

                        // Body
                        val openY = priceToY(c.open)
                        val closeY = priceToY(c.close)
                        val top = minOf(openY, closeY)
                        val bodyH = kotlin.math.abs(closeY - openY).coerceAtLeast(2.5f)
                        drawRect(
                            color = cColor,
                            topLeft = Offset(centerX - candleWidth / 2f, top),
                            size = Size(candleWidth, bodyH)
                        )

                        // Volume Histogram Bar
                        val volRatio = (c.volume / maxVol).toFloat().coerceIn(0.04f, 1f)
                        val barH = volHeight * volRatio
                        drawRect(
                            color = cColor.copy(alpha = 0.45f),
                            topLeft = Offset(centerX - candleWidth / 2f, volTop + volHeight - barH),
                            size = Size(candleWidth, barH)
                        )
                    }

                    // Moving Average Overlays (EMA 9, EMA 21, EMA 50, SMA 200)
                    if (smc != null) {
                        fun drawMaLine(series: List<Double>, color: Color, stroke: Float) {
                            if (series.size < candles.size) return
                            val path = Path()
                            for (i in visibleCandles.indices) {
                                val x = i * stepX + stepX / 2f
                                val y = priceToY(series[offsetStart + i])
                                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                            }
                            drawPath(path = path, color = color, style = Stroke(width = stroke, cap = StrokeCap.Round))
                        }

                        if (overlays.showEma9) drawMaLine(smc.ema9Series, tradingColors.ema9, 2.4f)
                        if (overlays.showEma21) drawMaLine(smc.ema21Series, tradingColors.ema21, 2.4f)
                        if (overlays.showEma50) drawMaLine(smc.ema50Series, tradingColors.ema50, 2.6f)
                        if (overlays.showSma200) drawMaLine(smc.sma200Series, tradingColors.sma200, 2.8f)

                        // Volume SMA 20 line
                        if (smc.volumeSma20Series.size >= candles.size) {
                            val vPath = Path()
                            for (i in visibleCandles.indices) {
                                val x = i * stepX + stepX / 2f
                                val vRatio = (smc.volumeSma20Series[offsetStart + i] / maxVol).toFloat().coerceIn(0f, 1f)
                                val y = volTop + volHeight - (volHeight * vRatio)
                                if (i == 0) vPath.moveTo(x, y) else vPath.lineTo(x, y)
                            }
                            drawPath(
                                path = vPath,
                                color = tradingColors.sweep.copy(alpha = 0.85f),
                                style = Stroke(width = 2f)
                            )
                        }
                    }

                    // Live Current Price Marker Line
                    val lastCloseY = priceToY(visibleCandles.last().close)
                    drawLine(
                        color = tradingColors.pulse.copy(alpha = 0.55f),
                        start = Offset(0f, lastCloseY),
                        end = Offset(chartWidth, lastCloseY),
                        strokeWidth = 1.5f,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f)
                    )

                    // Touch Crosshair
                    selectedIdx?.let { idx ->
                        val c = visibleCandles[idx]
                        val cx = idx * stepX + stepX / 2f
                        val cy = priceToY(c.close)
                        drawLine(
                            color = Color.White.copy(alpha = 0.45f),
                            start = Offset(cx, 0f),
                            end = Offset(cx, size.height),
                            strokeWidth = 1.5f
                        )
                        drawLine(
                            color = Color.White.copy(alpha = 0.45f),
                            start = Offset(0f, cy),
                            end = Offset(chartWidth, cy),
                            strokeWidth = 1.5f
                        )
                    }
                }
            }
        }

        // Chart Legend Strip (MA colors + Active Levels)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (overlays.showEma9) LegendDot("EMA9", tradingColors.ema9)
                if (overlays.showEma21) LegendDot("EMA21", tradingColors.ema21)
                if (overlays.showEma50) LegendDot("EMA50", tradingColors.ema50)
                if (overlays.showSma200) LegendDot("SMA200", tradingColors.sma200)
            }
            Text(
                text = "VOL + MA20",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun LegendDot(label: String, color: Color) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(color)
                .padding(3.5.dp)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = color
        )
    }
}
