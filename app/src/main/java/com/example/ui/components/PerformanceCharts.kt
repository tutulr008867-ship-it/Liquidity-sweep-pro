package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.model.EquityCurvePoint
import com.example.domain.model.PerformanceMetrics
import com.example.ui.theme.LocalTradingColors
import com.example.ui.theme.MonoNumericStyle
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberAxisLabelComponent
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberBottom
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberStart
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberColumnCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLine
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.marker.rememberDefaultCartesianMarker
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.common.component.rememberLineComponent
import com.patrykandpatrick.vico.compose.common.component.rememberShapeComponent
import com.patrykandpatrick.vico.compose.common.component.rememberTextComponent
import com.patrykandpatrick.vico.compose.common.fill
import com.patrykandpatrick.vico.compose.common.shader.verticalGradient
import com.patrykandpatrick.vico.core.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.core.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.core.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.core.cartesian.data.CartesianLayerRangeProvider
import com.patrykandpatrick.vico.core.cartesian.data.CartesianValueFormatter
import com.patrykandpatrick.vico.core.cartesian.data.columnSeries
import com.patrykandpatrick.vico.core.cartesian.data.lineSeries
import com.patrykandpatrick.vico.core.cartesian.layer.ColumnCartesianLayer
import com.patrykandpatrick.vico.core.cartesian.layer.LineCartesianLayer
import com.patrykandpatrick.vico.core.common.Insets
import com.patrykandpatrick.vico.core.common.data.ExtraStore
import com.patrykandpatrick.vico.core.common.shader.ShaderProvider
import com.patrykandpatrick.vico.core.common.shape.CorneredShape
import java.util.Locale

private enum class PnlChartMode(val label: String) {
    CUMULATIVE_R("Cum. R"),
    CUMULATIVE_PIPS("Cum. Pips"),
    PER_TRADE_R("Per-Trade R")
}

private enum class WinRateChartMode(val label: String) {
    ROLLING_TREND("Historical Trend"),
    BREAKDOWN("Timeframe & Market")
}

private val BottomLabelsKey = ExtraStore.Key<List<String>>()

@Composable
fun TargetOutcomeBarChartCard(
    perf: PerformanceMetrics,
    modifier: Modifier = Modifier
) {
    val tradingColors = LocalTradingColors.current
    val modelProducer = remember { CartesianChartModelProducer() }
    val total = perf.totalSignals.coerceAtLeast(1)

    LaunchedEffect(
        perf.tp1Hits,
        perf.tp2Hits,
        perf.tp3SuccessDone,
        perf.slHits,
        perf.activeSignals
    ) {
        modelProducer.runTransaction {
            columnSeries {
                series(
                    perf.tp1Hits.toDouble(),
                    perf.tp2Hits.toDouble(),
                    perf.tp3SuccessDone.toDouble(),
                    perf.slHits.toDouble(),
                    perf.activeSignals.toDouble()
                )
            }
            extras {
                it[BottomLabelsKey] = listOf("TP1", "TP2", "TP3 Done", "SL Hit", "Active")
            }
        }
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Real-Time TP1 / TP2 / TP3 & SL Performance",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Live target hit & SL Hit distribution across ${perf.totalSignals} signals",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(tradingColors.buyContainer)
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "${perf.wins} DONE",
                        style = MonoNumericStyle.copy(fontSize = 11.sp, fontWeight = FontWeight.Bold),
                        color = tradingColors.buy
                    )
                }
            }

            // Vico Bar Chart comparing TP1, TP2, TP3 Done, SL Hit, Active
            val axisLabel = rememberAxisLabelComponent(
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textSize = 10.sp
            )
            val bottomFormatter = remember {
                CartesianValueFormatter { context, x, _ ->
                    val labels = context.model.extraStore.getOrNull(BottomLabelsKey)
                    labels?.getOrNull(x.toInt()) ?: x.toInt().toString()
                }
            }
            val countFormatter = remember {
                CartesianValueFormatter { _, y, _ ->
                    y.toInt().toString()
                }
            }
            val marker = rememberChartMarker(
                labelColor = MaterialTheme.colorScheme.onSurface,
                bgColor = MaterialTheme.colorScheme.surfaceVariant
            )

            CartesianChartHost(
                chart = rememberCartesianChart(
                    rememberColumnCartesianLayer(
                        columnProvider = ColumnCartesianLayer.ColumnProvider.series(
                            rememberLineComponent(
                                fill = fill(tradingColors.pulse),
                                thickness = 24.dp,
                                shape = CorneredShape.rounded(topLeftPercent = 30, topRightPercent = 30)
                            )
                        )
                    ),
                    startAxis = VerticalAxis.rememberStart(
                        label = axisLabel,
                        valueFormatter = countFormatter
                    ),
                    bottomAxis = HorizontalAxis.rememberBottom(
                        label = axisLabel,
                        valueFormatter = bottomFormatter
                    ),
                    marker = marker
                ),
                modelProducer = modelProducer,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(165.dp)
                    .testTag("vico_target_outcome_bar_chart")
            )

            // Real-Time Milestone Breakdown Bars
            OutcomeProgressBarRow("TP1 Hit", perf.tp1Hits, total, tradingColors.pulse)
            OutcomeProgressBarRow("TP2 Hit", perf.tp2Hits, total, tradingColors.buy.copy(alpha = 0.85f))
            OutcomeProgressBarRow("TP3 • Success Done", perf.tp3SuccessDone, total, tradingColors.buy)
            OutcomeProgressBarRow("SL Hit", perf.slHits, total, tradingColors.sell)
            OutcomeProgressBarRow("Active Live", perf.activeSignals, total, tradingColors.sweep)
        }
    }
}

@Composable
private fun OutcomeProgressBarRow(
    label: String,
    count: Int,
    total: Int,
    color: Color
) {
    val pct = ((count.toDouble() / total.toDouble()) * 100.0).coerceIn(0.0, 100.0)
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.width(116.dp)
        )
        LinearProgressIndicator(
            progress = { (pct / 100.0).toFloat() },
            modifier = Modifier
                .weight(1f)
                .height(9.dp)
                .clip(RoundedCornerShape(6.dp)),
            color = color,
            trackColor = MaterialTheme.colorScheme.surfaceVariant
        )
        Text(
            text = "$count (${String.format(Locale.US, "%.0f%%", pct)})",
            style = MonoNumericStyle.copy(fontSize = 11.sp, fontWeight = FontWeight.SemiBold),
            color = color,
            modifier = Modifier.width(64.dp)
        )
    }
}

@Composable
fun ProfitLossTrendChartCard(
    points: List<EquityCurvePoint>,
    modifier: Modifier = Modifier
) {
    val tradingColors = LocalTradingColors.current
    var mode by remember { mutableStateOf(PnlChartMode.CUMULATIVE_R) }
    val modelProducer = remember { CartesianChartModelProducer() }

    val activePoints = remember(points, mode) {
        if (mode == PnlChartMode.PER_TRADE_R && points.size > 1) points.drop(1) else points
    }

    LaunchedEffect(activePoints, mode) {
        if (activePoints.isEmpty()) return@LaunchedEffect
        val labels = activePoints.map { it.label }
        val values = when (mode) {
            PnlChartMode.CUMULATIVE_R -> activePoints.map { it.cumulativeR }
            PnlChartMode.CUMULATIVE_PIPS -> activePoints.map { it.cumulativePips }
            PnlChartMode.PER_TRADE_R -> activePoints.map { it.tradeR }
        }
        modelProducer.runTransaction {
            if (mode == PnlChartMode.PER_TRADE_R) {
                columnSeries { series(values) }
            } else {
                lineSeries { series(values) }
            }
            extras { it[BottomLabelsKey] = labels }
        }
    }

    val headerValue = when (mode) {
        PnlChartMode.CUMULATIVE_R -> String.format(Locale.US, "%+.1fR", points.lastOrNull()?.cumulativeR ?: 0.0)
        PnlChartMode.CUMULATIVE_PIPS -> String.format(Locale.US, "%+.1f pips", points.lastOrNull()?.cumulativePips ?: 0.0)
        PnlChartMode.PER_TRADE_R -> "${activePoints.size} trades"
    }
    val headerPositive = when (mode) {
        PnlChartMode.CUMULATIVE_R -> (points.lastOrNull()?.cumulativeR ?: 0.0) >= 0.0
        PnlChartMode.CUMULATIVE_PIPS -> (points.lastOrNull()?.cumulativePips ?: 0.0) >= 0.0
        PnlChartMode.PER_TRADE_R -> true
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Profit / Loss Trend",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Vico interactive P&L curve & trade return series",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = headerValue,
                    style = MonoNumericStyle.copy(fontSize = 14.sp, fontWeight = FontWeight.Bold),
                    color = if (headerPositive) tradingColors.buy else tradingColors.sell
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PnlChartMode.entries.forEach { m ->
                    FilterChip(
                        selected = mode == m,
                        onClick = { mode = m },
                        label = { Text(m.label) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.primary
                        ),
                        modifier = Modifier.testTag("pnl_mode_${m.name.lowercase()}")
                    )
                }
            }

            if (activePoints.size < 2 && mode != PnlChartMode.PER_TRADE_R) {
                EmptyChartPlaceholder("Waiting for closed signal history to plot P&L trend...")
            } else if (activePoints.isEmpty()) {
                EmptyChartPlaceholder("No closed trades available yet.")
            } else {
                val axisLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
                val axisLabel = rememberAxisLabelComponent(
                    color = axisLabelColor,
                    textSize = 10.sp
                )
                val bottomFormatter = remember {
                    CartesianValueFormatter { context, x, _ ->
                        val labels = context.model.extraStore.getOrNull(BottomLabelsKey)
                        labels?.getOrNull(x.toInt()) ?: x.toInt().toString()
                    }
                }
                val startFormatter = remember(mode) {
                    CartesianValueFormatter { _, y, _ ->
                        when (mode) {
                            PnlChartMode.CUMULATIVE_R, PnlChartMode.PER_TRADE_R ->
                                String.format(Locale.US, "%+.1fR", y)
                            PnlChartMode.CUMULATIVE_PIPS ->
                                String.format(Locale.US, "%+.0f", y)
                        }
                    }
                }

                val marker = rememberChartMarker(
                    labelColor = MaterialTheme.colorScheme.onSurface,
                    bgColor = MaterialTheme.colorScheme.surfaceVariant
                )

                if (mode == PnlChartMode.PER_TRADE_R) {
                    CartesianChartHost(
                        chart = rememberCartesianChart(
                            rememberColumnCartesianLayer(
                                columnProvider = ColumnCartesianLayer.ColumnProvider.series(
                                    rememberLineComponent(
                                        fill = fill(tradingColors.pulse),
                                        thickness = 12.dp,
                                        shape = CorneredShape.rounded(allPercent = 25)
                                    )
                                )
                            ),
                            startAxis = VerticalAxis.rememberStart(
                                label = axisLabel,
                                valueFormatter = startFormatter
                            ),
                            bottomAxis = HorizontalAxis.rememberBottom(
                                label = axisLabel,
                                valueFormatter = bottomFormatter
                            ),
                            marker = marker
                        ),
                        modelProducer = modelProducer,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(190.dp)
                            .testTag("vico_pnl_column_chart")
                    )
                } else {
                    val topFill = fill(tradingColors.buy)
                    val bottomFill = fill(tradingColors.sell)
                    val topArea = fill(
                        ShaderProvider.verticalGradient(
                            intArrayOf(
                                tradingColors.buy.copy(alpha = 0.35f).hashCode(),
                                tradingColors.buy.copy(alpha = 0.02f).hashCode()
                            )
                        )
                    )
                    val bottomArea = fill(
                        ShaderProvider.verticalGradient(
                            intArrayOf(
                                tradingColors.sell.copy(alpha = 0.02f).hashCode(),
                                tradingColors.sell.copy(alpha = 0.35f).hashCode()
                            )
                        )
                    )
                    CartesianChartHost(
                        chart = rememberCartesianChart(
                            rememberLineCartesianLayer(
                                lineProvider = LineCartesianLayer.LineProvider.series(
                                    LineCartesianLayer.rememberLine(
                                        fill = LineCartesianLayer.LineFill.double(
                                            topFill = topFill,
                                            bottomFill = bottomFill
                                        ),
                                        areaFill = LineCartesianLayer.AreaFill.double(
                                            topFill = topArea,
                                            bottomFill = bottomArea
                                        )
                                    )
                                )
                            ),
                            startAxis = VerticalAxis.rememberStart(
                                label = axisLabel,
                                valueFormatter = startFormatter
                            ),
                            bottomAxis = HorizontalAxis.rememberBottom(
                                label = axisLabel,
                                valueFormatter = bottomFormatter
                            ),
                            marker = marker
                        ),
                        modelProducer = modelProducer,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(190.dp)
                            .testTag("vico_pnl_line_chart")
                    )
                }
            }
        }
    }
}

@Composable
fun WinRateTrendChartCard(
    perf: PerformanceMetrics,
    modifier: Modifier = Modifier
) {
    val tradingColors = LocalTradingColors.current
    var mode by remember { mutableStateOf(WinRateChartMode.ROLLING_TREND) }
    val modelProducer = remember { CartesianChartModelProducer() }

    val rollingPoints = remember(perf.equityCurve) {
        if (perf.equityCurve.size > 1) perf.equityCurve.drop(1) else emptyList()
    }

    LaunchedEffect(perf, mode, rollingPoints) {
        when (mode) {
            WinRateChartMode.ROLLING_TREND -> {
                if (rollingPoints.isEmpty()) return@LaunchedEffect
                modelProducer.runTransaction {
                    lineSeries { series(rollingPoints.map { it.rollingWinRatePct }) }
                    extras { it[BottomLabelsKey] = rollingPoints.map { "#${it.index} ${it.label}" } }
                }
            }
            WinRateChartMode.BREAKDOWN -> {
                val labels = perf.timeframeBreakdown.map { it.timeframe.label } + listOf("Forex", "Crypto")
                val values = perf.timeframeBreakdown.map { it.winRatePct } +
                    listOf(perf.forexWinRatePct, perf.cryptoWinRatePct)
                modelProducer.runTransaction {
                    columnSeries { series(values) }
                    extras { it[BottomLabelsKey] = labels }
                }
            }
        }
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Historical Win Rate Analytics",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Chronological win % progression & timeframe accuracy",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = String.format(Locale.US, "%.1f%%", perf.winRatePct),
                    style = MonoNumericStyle.copy(fontSize = 14.sp, fontWeight = FontWeight.Bold),
                    color = tradingColors.pulse
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                WinRateChartMode.entries.forEach { m ->
                    FilterChip(
                        selected = mode == m,
                        onClick = { mode = m },
                        label = { Text(m.label) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.primary
                        ),
                        modifier = Modifier.testTag("winrate_mode_${m.name.lowercase()}")
                    )
                }
            }

            if (mode == WinRateChartMode.ROLLING_TREND && rollingPoints.size < 2) {
                EmptyChartPlaceholder("Waiting for at least 2 closed signals to plot rolling win rate...")
            } else {
                val axisLabel = rememberAxisLabelComponent(
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textSize = 10.sp
                )
                val bottomFormatter = remember {
                    CartesianValueFormatter { context, x, _ ->
                        val labels = context.model.extraStore.getOrNull(BottomLabelsKey)
                        labels?.getOrNull(x.toInt()) ?: x.toInt().toString()
                    }
                }
                val percentFormatter = remember {
                    CartesianValueFormatter { _, y, _ ->
                        String.format(Locale.US, "%.0f%%", y)
                    }
                }
                val fixedRange = remember {
                    CartesianLayerRangeProvider.fixed(minY = 0.0, maxY = 100.0)
                }
                val marker = rememberChartMarker(
                    labelColor = MaterialTheme.colorScheme.onSurface,
                    bgColor = MaterialTheme.colorScheme.surfaceVariant
                )

                if (mode == WinRateChartMode.ROLLING_TREND) {
                    CartesianChartHost(
                        chart = rememberCartesianChart(
                            rememberLineCartesianLayer(
                                lineProvider = LineCartesianLayer.LineProvider.series(
                                    LineCartesianLayer.rememberLine(
                                        fill = LineCartesianLayer.LineFill.single(fill(tradingColors.pulse)),
                                        areaFill = LineCartesianLayer.AreaFill.single(
                                            fill(
                                                ShaderProvider.verticalGradient(
                                                    intArrayOf(
                                                        tradingColors.pulse.copy(alpha = 0.35f).hashCode(),
                                                        tradingColors.pulse.copy(alpha = 0.02f).hashCode()
                                                    )
                                                )
                                            )
                                        )
                                    )
                                ),
                                rangeProvider = fixedRange
                            ),
                            startAxis = VerticalAxis.rememberStart(
                                label = axisLabel,
                                valueFormatter = percentFormatter
                            ),
                            bottomAxis = HorizontalAxis.rememberBottom(
                                label = axisLabel,
                                valueFormatter = bottomFormatter
                            ),
                            marker = marker
                        ),
                        modelProducer = modelProducer,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(190.dp)
                            .testTag("vico_winrate_line_chart")
                    )
                } else {
                    CartesianChartHost(
                        chart = rememberCartesianChart(
                            rememberColumnCartesianLayer(
                                columnProvider = ColumnCartesianLayer.ColumnProvider.series(
                                    rememberLineComponent(
                                        fill = fill(tradingColors.buy),
                                        thickness = 22.dp,
                                        shape = CorneredShape.rounded(topLeftPercent = 30, topRightPercent = 30)
                                    )
                                ),
                                rangeProvider = fixedRange
                            ),
                            startAxis = VerticalAxis.rememberStart(
                                label = axisLabel,
                                valueFormatter = percentFormatter
                            ),
                            bottomAxis = HorizontalAxis.rememberBottom(
                                label = axisLabel,
                                valueFormatter = bottomFormatter
                            ),
                            marker = marker
                        ),
                        modelProducer = modelProducer,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(190.dp)
                            .testTag("vico_winrate_bar_chart")
                    )
                }
            }
        }
    }
}

@Composable
private fun rememberChartMarker(
    labelColor: Color,
    bgColor: Color
) = rememberDefaultCartesianMarker(
    label = rememberTextComponent(
        color = labelColor,
        textSize = 11.sp,
        padding = Insets(horizontalDp = 8f, verticalDp = 4f),
        background = rememberShapeComponent(
            fill = fill(bgColor),
            shape = CorneredShape.rounded(allPercent = 25)
        )
    )
)

@Composable
private fun EmptyChartPlaceholder(message: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(150.dp)
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                shape = RoundedCornerShape(12.dp)
            )
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
