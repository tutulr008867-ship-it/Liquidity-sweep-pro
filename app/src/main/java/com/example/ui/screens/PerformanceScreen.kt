package com.example.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.model.PerformanceMetrics
import com.example.domain.model.SignalStatus
import com.example.domain.model.Timeframe
import com.example.ui.components.ProfitLossTrendChartCard
import com.example.ui.components.WinRateTrendChartCard
import com.example.ui.theme.LocalTradingColors
import com.example.ui.theme.MonoNumericStyle
import com.example.ui.viewmodel.TradingUiState
import java.util.Locale

private enum class LedgerFilter(val label: String) {
    ALL("All"),
    ACTIVE("Active"),
    WINS("Wins (TP)"),
    LOSSES("Losses (SL)")
}

@Composable
fun PerformanceScreen(
    uiState: TradingUiState,
    onOpenPairTerminal: (String, Timeframe?) -> Unit,
    modifier: Modifier = Modifier
) {
    val perf = uiState.performance
    var ledgerFilter by remember { mutableStateOf(LedgerFilter.ALL) }

    val filteredLedger = remember(uiState.signals, ledgerFilter) {
        uiState.signals.filter { sig ->
            when (ledgerFilter) {
                LedgerFilter.ALL -> true
                LedgerFilter.ACTIVE -> sig.status == SignalStatus.ACTIVE
                LedgerFilter.WINS -> sig.status == SignalStatus.TP1_HIT ||
                    sig.status == SignalStatus.TP2_HIT ||
                    sig.status == SignalStatus.TP3_HIT
                LedgerFilter.LOSSES -> sig.status == SignalStatus.SL_HIT
            }
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Column {
                Text(
                    text = "Performance Dashboard",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "Historical win rates, P&L trends & SMC + 26-TV execution ledger",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (perf != null) {
            // Hero Win Rate & Metrics Card
            item {
                HeroPerformanceCard(perf = perf)
            }

            // Real-Time TP1 / TP2 / TP3 Success Done / SL Hit Performance Bar Chart
            item {
                com.example.ui.components.TargetOutcomeBarChartCard(perf = perf)
            }

            // Vico Profit / Loss Trend Chart (Cumulative R, Cumulative Pips, Per-Trade R)
            item {
                ProfitLossTrendChartCard(points = perf.equityCurve)
            }

            // Vico Historical Win Rate Trend & Timeframe/Market Accuracy Chart
            item {
                WinRateTrendChartCard(perf = perf)
            }

            // Market & Timeframe Win Rate Summary Bars
            item {
                MarketAndTimeframeCard(perf = perf)
            }
        }

        // Ledger Filter Chips
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "SIGNAL EXECUTION LEDGER (${filteredLedger.size})",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    LedgerFilter.entries.forEach { f ->
                        FilterChip(
                            selected = ledgerFilter == f,
                            onClick = { ledgerFilter = f },
                            label = { Text(f.label) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.primary
                            ),
                            modifier = Modifier.testTag("ledger_filter_${f.name.lowercase()}")
                        )
                    }
                }
            }
        }

        items(filteredLedger, key = { it.id }) { sig ->
            SignalSetupCard(
                signal = sig,
                livePrice = uiState.snapshots[sig.pairSymbol]?.currentPrice,
                onClick = { onOpenPairTerminal(sig.pairSymbol, sig.timeframeEntry) }
            )
        }
    }
}

@Composable
private fun HeroPerformanceCard(perf: PerformanceMetrics) {
    val tradingColors = LocalTradingColors.current

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Win Rate Ring
                Box(
                    modifier = Modifier.size(104.dp),
                    contentAlignment = Alignment.Center
                ) {
                    val wrProgress = (perf.winRatePct / 100.0).toFloat().coerceIn(0f, 1f)
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val stroke = 10.dp.toPx()
                        drawArc(
                            color = tradingColors.chartGrid,
                            startAngle = 0f,
                            sweepAngle = 360f,
                            useCenter = false,
                            style = Stroke(width = stroke)
                        )
                        drawArc(
                            color = tradingColors.buy,
                            startAngle = -90f,
                            sweepAngle = 360f * wrProgress,
                            useCenter = false,
                            style = Stroke(width = stroke, cap = StrokeCap.Round)
                        )
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = String.format(Locale.US, "%.1f%%", perf.winRatePct),
                            style = MonoNumericStyle.copy(fontSize = 19.sp, fontWeight = FontWeight.Bold),
                            color = tradingColors.buy
                        )
                        Text(
                            text = "WIN RATE",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        MetricStat("Total Signals", "${perf.totalSignals}", MaterialTheme.colorScheme.onSurface)
                        MetricStat("Active Now", "${perf.activeSignals}", tradingColors.pulse)
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        MetricStat("Wins / Losses", "${perf.wins}W / ${perf.losses}L", tradingColors.buy)
                        MetricStat("Profit Factor", String.format(Locale.US, "%.2f", perf.profitFactor), tradingColors.sweep)
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        MetricStat(
                            "Net Pips",
                            String.format(Locale.US, "%+.1f", perf.netPips),
                            if (perf.netPips >= 0) tradingColors.buy else tradingColors.sell
                        )
                        MetricStat("Top Pair", perf.bestPair, MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
}

@Composable
private fun MetricStat(label: String, value: String, color: Color) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MonoNumericStyle.copy(fontSize = 14.sp, fontWeight = FontWeight.Bold),
            color = color
        )
    }
}

@Composable
private fun MarketAndTimeframeCard(perf: PerformanceMetrics) {
    val tradingColors = LocalTradingColors.current
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "Win Rate by Market & Timeframe",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(10.dp)
                ) {
                    Column {
                        Text("Forex Majors", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            text = String.format(Locale.US, "%.1f%%", perf.forexWinRatePct),
                            style = MonoNumericStyle.copy(fontSize = 17.sp, fontWeight = FontWeight.Bold),
                            color = tradingColors.pulse
                        )
                    }
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(10.dp)
                ) {
                    Column {
                        Text("Crypto Pairs", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            text = String.format(Locale.US, "%.1f%%", perf.cryptoWinRatePct),
                            style = MonoNumericStyle.copy(fontSize = 17.sp, fontWeight = FontWeight.Bold),
                            color = tradingColors.buy
                        )
                    }
                }
            }

            perf.timeframeBreakdown.forEach { tfPerf ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = tfPerf.timeframe.label,
                        style = MonoNumericStyle.copy(fontSize = 12.sp, fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(end = 4.dp)
                    )
                    LinearProgressIndicator(
                        progress = { (tfPerf.winRatePct / 100.0).toFloat().coerceIn(0f, 1f) },
                        modifier = Modifier
                            .weight(1f)
                            .height(8.dp)
                            .clip(RoundedCornerShape(6.dp)),
                        color = tradingColors.buy,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                    Text(
                        text = "${tfPerf.wins}/${tfPerf.total} (${String.format(Locale.US, "%.0f%%", tfPerf.winRatePct)})",
                        style = MonoNumericStyle.copy(fontSize = 11.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
