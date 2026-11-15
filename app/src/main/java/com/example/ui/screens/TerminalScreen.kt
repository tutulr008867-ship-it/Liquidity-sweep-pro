package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.model.MarketBias
import com.example.domain.model.PairMarketSnapshot
import com.example.domain.model.SmcAnalysis
import com.example.domain.model.SmcSweepType
import com.example.domain.model.Timeframe
import com.example.ui.components.CandlestickChartCanvas
import com.example.ui.components.TradingViewGaugeCard
import com.example.ui.theme.LocalTradingColors
import com.example.ui.theme.MonoNumericStyle
import com.example.ui.viewmodel.TradingUiState
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TerminalScreen(
    uiState: TradingUiState,
    onSelectPair: (String) -> Unit,
    onSelectTimeframe: (Timeframe) -> Unit,
    onToggleEma9: () -> Unit,
    onToggleEma21: () -> Unit,
    onToggleEma50: () -> Unit,
    onToggleSma200: () -> Unit,
    onToggleSmcZones: () -> Unit,
    modifier: Modifier = Modifier
) {
    val tradingColors = LocalTradingColors.current
    val snapshot = uiState.selectedSnapshot
    var showPairPicker by remember { mutableStateOf(false) }

    if (showPairPicker) {
        ModalBottomSheet(
            onDismissRequest = { showPairPicker = false },
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Select Forex / Crypto Market",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                LazyColumn(
                    modifier = Modifier.height(420.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(uiState.allPairs.size) { idx ->
                        val pair = uiState.allPairs[idx]
                        val snap = uiState.snapshots[pair.symbol]
                        val isSelected = pair.symbol == uiState.selectedPairSymbol
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    if (isSelected) MaterialTheme.colorScheme.primaryContainer
                                    else MaterialTheme.colorScheme.surfaceVariant
                                )
                                .clickable {
                                    onSelectPair(pair.symbol)
                                    showPairPicker = false
                                }
                                .padding(horizontal = 14.dp, vertical = 12.dp)
                                .testTag("picker_pair_${pair.symbol}"),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = pair.symbol,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "${pair.marketType.label} • ${pair.displayName}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (snap != null) {
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        text = pair.formatPrice(snap.currentPrice),
                                        style = MonoNumericStyle.copy(fontSize = 14.sp, fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = String.format(Locale.US, "%+.2f%%", snap.priceChange24hPct),
                                        style = MonoNumericStyle.copy(fontSize = 11.sp),
                                        color = if (snap.priceChange24hPct >= 0) tradingColors.buy else tradingColors.sell
                                    )
                                }
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

    if (snapshot == null) {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Synchronizing real-time market feed...",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    val tf = uiState.selectedTimeframe
    val candles = snapshot.candlesByTimeframe[tf] ?: emptyList()
    val smc = snapshot.smcByTimeframe[tf]
    val consensus = snapshot.consensusByTimeframe[tf]

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        val isWide = maxWidth >= 820.dp

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 1. Pair Header & Real-time 1s Quote Strip
            item {
                TerminalPairHeader(
                    snapshot = snapshot,
                    selectedTimeframe = tf,
                    onOpenPicker = { showPairPicker = true }
                )
            }

            // 2. Timeframe Selector Bar (1m, 5m, 15m, 30m, 1H Execution | 4H Holistic)
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Timeframe.entries.forEach { timeframe ->
                        val selected = timeframe == tf
                        val roleColor = if (timeframe == Timeframe.H4) tradingColors.sweep else MaterialTheme.colorScheme.primary
                        Column(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    if (selected) roleColor.copy(alpha = 0.2f)
                                    else MaterialTheme.colorScheme.surface
                                )
                                .border(
                                    width = 1.dp,
                                    color = if (selected) roleColor else MaterialTheme.colorScheme.outlineVariant,
                                    shape = RoundedCornerShape(10.dp)
                                )
                                .clickable { onSelectTimeframe(timeframe) }
                                .padding(horizontal = 12.dp, vertical = 7.dp)
                                .testTag("tf_chip_${timeframe.label}"),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = timeframe.label,
                                style = MonoNumericStyle.copy(fontSize = 13.sp, fontWeight = FontWeight.Bold),
                                color = if (selected) roleColor else MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = timeframe.roleDescription,
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // 3. Real-Time 1s Moving Averages & Volume Indicators Strip
            if (smc != null) {
                item {
                    RealtimeMovingAveragesAndVolumeCard(snapshot = snapshot, smc = smc)
                }
            }

            // 4. Adaptive Chart + Consensus Layout
            if (isWide) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Column(
                            modifier = Modifier.weight(1.35f),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OverlayToggleRow(
                                uiState = uiState,
                                onToggleEma9 = onToggleEma9,
                                onToggleEma21 = onToggleEma21,
                                onToggleEma50 = onToggleEma50,
                                onToggleSma200 = onToggleSma200,
                                onToggleSmcZones = onToggleSmcZones
                            )
                            CandlestickChartCanvas(
                                pair = snapshot.pair,
                                candles = candles,
                                smc = smc,
                                activeSignal = snapshot.activeSignal,
                                overlays = uiState.overlaySettings
                            )
                        }
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            if (consensus != null) {
                                TradingViewGaugeCard(
                                    consensus = consensus,
                                    timeframeLabel = tf.label
                                )
                            }
                            if (smc != null) {
                                SmcMultiTimeframeCard(snapshot = snapshot, smc = smc)
                            }
                        }
                    }
                }
            } else {
                item {
                    OverlayToggleRow(
                        uiState = uiState,
                        onToggleEma9 = onToggleEma9,
                        onToggleEma21 = onToggleEma21,
                        onToggleEma50 = onToggleEma50,
                        onToggleSma200 = onToggleSma200,
                        onToggleSmcZones = onToggleSmcZones
                    )
                }
                item {
                    CandlestickChartCanvas(
                        pair = snapshot.pair,
                        candles = candles,
                        smc = smc,
                        activeSignal = snapshot.activeSignal,
                        overlays = uiState.overlaySettings
                    )
                }
                if (smc != null) {
                    item {
                        SmcMultiTimeframeCard(snapshot = snapshot, smc = smc)
                    }
                }
                if (consensus != null) {
                    item {
                        TradingViewGaugeCard(
                            consensus = consensus,
                            timeframeLabel = tf.label
                        )
                    }
                }
            }

            // 5. Active Combined Signal Card (if present for this pair)
            snapshot.activeSignal?.let { active ->
                item {
                    SignalSetupCard(
                        signal = active,
                        livePrice = snapshot.currentPrice,
                        onClick = {}
                    )
                }
            }
        }
    }
}

@Composable
private fun TerminalPairHeader(
    snapshot: PairMarketSnapshot,
    selectedTimeframe: Timeframe,
    onOpenPicker: () -> Unit
) {
    val tradingColors = LocalTradingColors.current
    val isUp = snapshot.priceChange24hPct >= 0
    val tickUp = snapshot.currentPrice >= snapshot.previousPrice
    val priceColor = if (tickUp) tradingColors.buy else tradingColors.sell

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .clickable(onClick = onOpenPicker)
                .padding(end = 8.dp)
                .testTag("pair_selector_button")
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = snapshot.pair.symbol,
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Icon(
                    imageVector = Icons.Rounded.KeyboardArrowDown,
                    contentDescription = "Switch pair",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${snapshot.pair.marketType.label} • ${selectedTimeframe.label}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = snapshot.feedLabel,
                    style = MonoNumericStyle.copy(fontSize = 9.sp, fontWeight = FontWeight.Bold),
                    color = if (snapshot.isLiveFeed) tradingColors.buy else tradingColors.sweep
                )
            }
        }

        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = snapshot.pair.formatPrice(snapshot.currentPrice),
                style = MonoNumericStyle.copy(fontSize = 22.sp, fontWeight = FontWeight.Bold),
                color = priceColor
            )
            Text(
                text = "Bid ${snapshot.pair.formatPrice(snapshot.bidPrice)}  •  ${String.format(Locale.US, "%+.2f%%", snapshot.priceChange24hPct)}",
                style = MonoNumericStyle.copy(fontSize = 11.sp),
                color = if (isUp) tradingColors.buy else tradingColors.sell
            )
        }
    }
}

@Composable
private fun RealtimeMovingAveragesAndVolumeCard(
    snapshot: PairMarketSnapshot,
    smc: SmcAnalysis
) {
    val tradingColors = LocalTradingColors.current
    val pair = snapshot.pair
    val rvolColor = when {
        smc.rvol >= 1.5 -> tradingColors.buy
        smc.rvol >= 1.0 -> tradingColors.pulse
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "1s MOVING AVERAGES & VOLUME",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "RVOL ${String.format(Locale.US, "%.2fx", smc.rvol)} • Vol ${formatCompactVolume(smc.currentVolume)}",
                    style = MonoNumericStyle.copy(fontSize = 11.sp, fontWeight = FontWeight.Bold),
                    color = rvolColor
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                MaValueCell("EMA 9", pair.formatPrice(smc.ema9), snapshot.currentPrice >= smc.ema9, tradingColors.ema9)
                MaValueCell("EMA 21", pair.formatPrice(smc.ema21), snapshot.currentPrice >= smc.ema21, tradingColors.ema21)
                MaValueCell("EMA 50", pair.formatPrice(smc.ema50), snapshot.currentPrice >= smc.ema50, tradingColors.ema50)
                MaValueCell("SMA 200", pair.formatPrice(smc.sma200), snapshot.currentPrice >= smc.sma200, tradingColors.sma200)
            }
        }
    }
}

@Composable
private fun MaValueCell(
    label: String,
    value: String,
    priceAbove: Boolean,
    lineColor: androidx.compose.ui.graphics.Color
) {
    val tradingColors = LocalTradingColors.current
    Column {
        Text(
            text = label,
            style = MonoNumericStyle.copy(fontSize = 10.sp, fontWeight = FontWeight.SemiBold),
            color = lineColor
        )
        Text(
            text = value,
            style = MonoNumericStyle.copy(fontSize = 12.sp, fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = if (priceAbove) "Above" else "Below",
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
            color = if (priceAbove) tradingColors.buy else tradingColors.sell
        )
    }
}

@Composable
private fun OverlayToggleRow(
    uiState: TradingUiState,
    onToggleEma9: () -> Unit,
    onToggleEma21: () -> Unit,
    onToggleEma50: () -> Unit,
    onToggleSma200: () -> Unit,
    onToggleSmcZones: () -> Unit
) {
    val ov = uiState.overlaySettings
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        OverlayChip("SMC Zones", ov.showSmcZones, onToggleSmcZones, "toggle_smc_zones")
        OverlayChip("EMA 9", ov.showEma9, onToggleEma9, "toggle_ema9")
        OverlayChip("EMA 21", ov.showEma21, onToggleEma21, "toggle_ema21")
        OverlayChip("EMA 50", ov.showEma50, onToggleEma50, "toggle_ema50")
        OverlayChip("SMA 200", ov.showSma200, onToggleSma200, "toggle_sma200")
    }
}

@Composable
private fun OverlayChip(label: String, selected: Boolean, onClick: () -> Unit, tag: String) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label, style = MaterialTheme.typography.labelMedium) },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
            selectedLabelColor = MaterialTheme.colorScheme.primary
        ),
        modifier = Modifier.testTag(tag)
    )
}

@Composable
private fun SmcMultiTimeframeCard(
    snapshot: PairMarketSnapshot,
    smc: SmcAnalysis
) {
    val tradingColors = LocalTradingColors.current
    val pair = snapshot.pair

    Card(
        modifier = Modifier.fillMaxWidth(),
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
                Text(
                    text = "SMC Multi-Timeframe Confluence",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                val sweepCol = if (smc.sweepType == SmcSweepType.NONE) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else tradingColors.sweep
                Text(
                    text = smc.sweepType.label,
                    style = MaterialTheme.typography.labelMedium,
                    color = sweepCol
                )
            }

            // 4H Holistic & 1H Structure
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                BiasPill(
                    title = "4H Holistic Bias",
                    bias = smc.htfBias4H,
                    modifier = Modifier.weight(1f)
                )
                BiasPill(
                    title = "1H Structure",
                    bias = smc.structure1H,
                    modifier = Modifier.weight(1f)
                )
            }

            // 5m & 15m Precision Entry Accuracy Matrix
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "5m & 15m PRECISION SIGNAL ENTRY ANALYSIS",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "• 5m: ${smc.entryAnalysis5m}",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (smc.entry5mAligned) tradingColors.buy else MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "• 15m: ${smc.entryAnalysis15m}",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (smc.entry15mAligned) tradingColors.buy else MaterialTheme.colorScheme.onSurface
                )
            }

            // Liquidity Sweep / FVG / Order Block Levels
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                val sweptTxt = smc.sweptLevel?.let { pair.formatPrice(it) } ?: "Scanning"
                val fvgTxt = if (smc.fvgZoneLow != null && smc.fvgZoneHigh != null) {
                    "${pair.formatPrice(smc.fvgZoneLow)}–${pair.formatPrice(smc.fvgZoneHigh)}"
                } else "None"
                Column {
                    Text("Swept Liquidity", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(sweptTxt, style = MonoNumericStyle.copy(fontSize = 12.sp, fontWeight = FontWeight.SemiBold), color = tradingColors.sweep)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("Fair Value Gap", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(fvgTxt, style = MonoNumericStyle.copy(fontSize = 12.sp, fontWeight = FontWeight.SemiBold), color = tradingColors.pulse)
                }
            }
        }
    }
}

@Composable
private fun BiasPill(
    title: String,
    bias: MarketBias,
    modifier: Modifier = Modifier
) {
    val tradingColors = LocalTradingColors.current
    val color = when (bias) {
        MarketBias.BULLISH -> tradingColors.buy
        MarketBias.BEARISH -> tradingColors.sell
        MarketBias.RANGING -> tradingColors.sweep
    }
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = 10.dp, vertical = 8.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = bias.label.uppercase(),
            style = MonoNumericStyle.copy(fontSize = 13.sp, fontWeight = FontWeight.Bold),
            color = color
        )
    }
}

private fun formatCompactVolume(vol: Double): String {
    return when {
        vol >= 1_000_000_000 -> String.format(Locale.US, "%.2fB", vol / 1_000_000_000)
        vol >= 1_000_000 -> String.format(Locale.US, "%.2fM", vol / 1_000_000)
        vol >= 1_000 -> String.format(Locale.US, "%.1fK", vol / 1_000)
        else -> String.format(Locale.US, "%.0f", vol)
    }
}
