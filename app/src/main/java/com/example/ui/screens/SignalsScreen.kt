package com.example.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.TrendingDown
import androidx.compose.material.icons.rounded.TrendingUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.remote.MarketPairsCatalog
import com.example.domain.model.MarketBias
import com.example.domain.model.PairMarketSnapshot
import com.example.domain.model.SignalDirection
import com.example.domain.model.SignalStatus
import com.example.domain.model.SmcSweepType
import com.example.domain.model.Timeframe
import com.example.domain.model.TradingSignal
import com.example.ui.theme.LocalTradingColors
import com.example.ui.theme.MonoNumericStyle
import com.example.ui.viewmodel.MarketFilter
import com.example.ui.viewmodel.TradingUiState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SignalsScreen(
    uiState: TradingUiState,
    onSelectMarketFilter: (MarketFilter) -> Unit,
    onToggleActiveOnly: () -> Unit,
    onSearchChange: (String) -> Unit,
    onOpenPairTerminal: (String, Timeframe?) -> Unit,
    onForceScan: () -> Unit,
    onTriggerTestPush: () -> Unit,
    modifier: Modifier = Modifier
) {
    val tradingColors = LocalTradingColors.current

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Top Header with Live 1s Pulse Indicator
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "SweepPulse Signals",
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        LivePulseBadge(lastTickMillis = uiState.lastTickMillis)
                    }
                    Text(
                        text = "SMC Liquidity Sweeps + 26 TradingView Indicators",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconButton(
                        onClick = onTriggerTestPush,
                        modifier = Modifier.testTag("quick_push_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.NotificationsActive,
                            contentDescription = "Send instant signal push notification",
                            tint = tradingColors.sweep
                        )
                    }
                    IconButton(
                        onClick = onForceScan,
                        modifier = Modifier.testTag("force_scan_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Refresh,
                            contentDescription = "Rescan all Forex and Crypto markets",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }

        if (uiState.isScanning) {
            item {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        // KPI Summary Strip
        item {
            val perf = uiState.performance
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                KpiMiniTile(
                    label = "Active Setups",
                    value = "${perf?.activeSignals ?: 0}",
                    accent = tradingColors.pulse,
                    modifier = Modifier.weight(1f)
                )
                KpiMiniTile(
                    label = "Win Rate",
                    value = String.format(Locale.US, "%.1f%%", perf?.winRatePct ?: 0.0),
                    accent = tradingColors.buy,
                    modifier = Modifier.weight(1f)
                )
                KpiMiniTile(
                    label = "Net Pips",
                    value = String.format(Locale.US, "%+.0f", perf?.netPips ?: 0.0),
                    accent = if ((perf?.netPips ?: 0.0) >= 0) tradingColors.buy else tradingColors.sell,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // Search & Filter Bar
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = uiState.searchQuery,
                    onValueChange = onSearchChange,
                    placeholder = { Text("Search Forex or Crypto pair (e.g. EUR/USD, BTC)...") },
                    leadingIcon = {
                        Icon(Icons.Rounded.Search, contentDescription = "Search pairs")
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("pair_search_input")
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    MarketFilter.entries.forEach { filter ->
                        FilterChip(
                            selected = uiState.marketFilter == filter,
                            onClick = { onSelectMarketFilter(filter) },
                            label = { Text(filter.label) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.primary
                            ),
                            modifier = Modifier.testTag("filter_${filter.name.lowercase()}")
                        )
                    }
                    FilterChip(
                        selected = uiState.activeSignalsOnly,
                        onClick = onToggleActiveOnly,
                        label = { Text("Active Only") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = tradingColors.buyContainer,
                            selectedLabelColor = tradingColors.buy
                        ),
                        modifier = Modifier.testTag("filter_active_only")
                    )
                }
            }
        }

        // Live 1-Second Market Ticker Strip (All Filtered Pairs)
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "LIVE MARKET WATCH (1s TICK + MA & 26-TV)",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(uiState.filteredSnapshots, key = { it.pair.symbol }) { snap ->
                        LivePairWatchTile(
                            snapshot = snap,
                            onClick = { onOpenPairTerminal(snap.pair.symbol, Timeframe.M15) }
                        )
                    }
                }
            }
        }

        // Combined SMC + 26 TradingView Signals Section Header
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "COMBINED SMC + 26-INDICATOR SIGNALS",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "${uiState.filteredSignals.size} setups",
                    style = MonoNumericStyle.copy(fontSize = 11.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (uiState.filteredSignals.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Bolt,
                            contentDescription = null,
                            tint = tradingColors.sweep,
                            modifier = Modifier.size(36.dp)
                        )
                        Text(
                            text = "Scanning 5m/15m Liquidity Sweeps...",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "No signals match the current filter. Tap any pair above to inspect live 1s Moving Averages & 26 TradingView indicators.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            items(uiState.filteredSignals, key = { it.id }) { signal ->
                SignalSetupCard(
                    signal = signal,
                    livePrice = uiState.snapshots[signal.pairSymbol]?.currentPrice,
                    onClick = { onOpenPairTerminal(signal.pairSymbol, signal.timeframeEntry) }
                )
            }
        }
    }
}

@Composable
private fun LivePulseBadge(lastTickMillis: Long) {
    val tradingColors = LocalTradingColors.current
    val pulseOn = (lastTickMillis / 1000L) % 2L == 0L
    val dotColor by animateColorAsState(
        targetValue = if (pulseOn) tradingColors.buy else tradingColors.buy.copy(alpha = 0.45f),
        label = "live_tick_dot"
    )
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(tradingColors.buyContainer)
            .padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(dotColor)
        )
        Text(
            text = "1s LIVE",
            style = MonoNumericStyle.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold),
            color = tradingColors.buy
        )
    }
}

@Composable
private fun KpiMiniTile(
    label: String,
    value: String,
    accent: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(14.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = value,
            style = MonoNumericStyle.copy(fontSize = 18.sp, fontWeight = FontWeight.Bold),
            color = accent
        )
    }
}

@Composable
private fun LivePairWatchTile(
    snapshot: PairMarketSnapshot,
    onClick: () -> Unit
) {
    val tradingColors = LocalTradingColors.current
    val isUp = snapshot.priceChange24hPct >= 0
    val pctColor = if (isUp) tradingColors.buy else tradingColors.sell
    val cons15 = snapshot.consensusByTimeframe[Timeframe.M15]
    val smc15 = snapshot.smcByTimeframe[Timeframe.M15]
    val hasSweep = smc15 != null && smc15.sweepType != SmcSweepType.NONE

    Card(
        modifier = Modifier
            .width(166.dp)
            .clickable(onClick = onClick)
            .testTag("pair_tile_${snapshot.pair.symbol}"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = snapshot.pair.symbol,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(5.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(horizontal = 5.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = snapshot.pair.marketType.label.uppercase(),
                        style = MonoNumericStyle.copy(fontSize = 9.sp, fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Text(
                text = snapshot.pair.formatPrice(snapshot.currentPrice),
                style = MonoNumericStyle.copy(fontSize = 16.sp, fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = String.format(Locale.US, "%+.2f%%", snapshot.priceChange24hPct),
                    style = MonoNumericStyle.copy(fontSize = 11.sp, fontWeight = FontWeight.SemiBold),
                    color = pctColor
                )
                if (cons15 != null) {
                    val tvCol = when {
                        cons15.buyCount > cons15.sellCount -> tradingColors.buy
                        cons15.sellCount > cons15.buyCount -> tradingColors.sell
                        else -> tradingColors.sweep
                    }
                    Text(
                        text = "${maxOf(cons15.buyCount, cons15.sellCount)}/26 TV",
                        style = MonoNumericStyle.copy(fontSize = 10.sp, fontWeight = FontWeight.SemiBold),
                        color = tvCol
                    )
                }
            }

            if (hasSweep && smc15 != null) {
                Text(
                    text = smc15.sweepType.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = tradingColors.sweep,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            } else if (smc15 != null) {
                Text(
                    text = "RVOL ${String.format(Locale.US, "%.1fx", smc15.rvol)} • 4H ${smc15.htfBias4H.label}",
                    style = MonoNumericStyle.copy(fontSize = 10.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun SignalSetupCard(
    signal: TradingSignal,
    livePrice: Double?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val tradingColors = LocalTradingColors.current
    val pair = MarketPairsCatalog.findBySymbol(signal.pairSymbol)
    val isBuy = signal.direction == SignalDirection.BUY
    val dirColor = if (isBuy) tradingColors.buy else tradingColors.sell
    val dirContainer = if (isBuy) tradingColors.buyContainer else tradingColors.sellContainer
    val timeStr = SimpleDateFormat("MMM dd, HH:mm", Locale.US).format(Date(signal.createdAtMillis))

    val statusColor = when (signal.status) {
        SignalStatus.ACTIVE -> tradingColors.pulse
        SignalStatus.TP1_HIT, SignalStatus.TP2_HIT, SignalStatus.TP3_HIT -> tradingColors.buy
        SignalStatus.SL_HIT -> tradingColors.sell
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("signal_card_${signal.id}"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Header: Direction + Pair + Timeframe + Status
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(dirContainer)
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = if (isBuy) Icons.Rounded.TrendingUp else Icons.Rounded.TrendingDown,
                            contentDescription = signal.direction.label,
                            tint = dirColor,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = signal.direction.label,
                            style = MonoNumericStyle.copy(fontSize = 12.sp, fontWeight = FontWeight.Bold),
                            color = dirColor
                        )
                    }
                    Column {
                        Text(
                            text = "${signal.pairSymbol} • ${signal.timeframeEntry.label}",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "${signal.marketType.label} • $timeStr",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Column(horizontalAlignment = Alignment.End) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(statusColor.copy(alpha = 0.16f))
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = signal.status.label.uppercase(),
                            style = MonoNumericStyle.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold),
                            color = statusColor
                        )
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "${signal.confidenceScore}% Conf • 1:${String.format(Locale.US, "%.1f", signal.riskReward)} RR",
                        style = MonoNumericStyle.copy(fontSize = 10.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Entry / SL / TP1 / TP2 / TP3 Grid
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(10.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                PriceTargetCol("SL", pair.formatPrice(signal.stopLoss), tradingColors.sell)
                PriceTargetCol("ENTRY", pair.formatPrice(signal.entryPrice), tradingColors.pulse)
                PriceTargetCol("TP1", pair.formatPrice(signal.tp1), tradingColors.buy)
                PriceTargetCol("TP2", pair.formatPrice(signal.tp2), tradingColors.buy)
                PriceTargetCol("TP3", pair.formatPrice(signal.tp3), tradingColors.buy)
            }

            // Real-Time TP1 / TP2 / TP3 / Success Done / SL Hit Progress Bar
            RealTimeTargetProgressBar(
                signal = signal,
                livePrice = livePrice,
                pair = pair
            )

            // SMC + 5m/15m & 4H + 26-TV Consensus Badges
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(tradingColors.sweepContainer)
                            .padding(horizontal = 7.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = signal.sweepType.label,
                            style = MaterialTheme.typography.labelSmall,
                            color = tradingColors.sweep
                        )
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(MaterialTheme.colorScheme.primaryContainer)
                            .padding(horizontal = 7.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = "4H ${signal.htfBias4H.label}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                val tvDominant = if (isBuy) "${signal.tvBuyCount}/26 BUY" else "${signal.tvSellCount}/26 SELL"
                Text(
                    text = "TV $tvDominant",
                    style = MonoNumericStyle.copy(fontSize = 11.sp, fontWeight = FontWeight.Bold),
                    color = dirColor
                )
            }

            // 5m & 15m Entry Accuracy Analysis Line
            Text(
                text = "5m/15m Entry: ${signal.entry5mSummary} | ${signal.entry15mSummary}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            // Live Unrealized / Closed Outcome Pips Strip
            if (livePrice != null && (signal.status == SignalStatus.ACTIVE || signal.status == SignalStatus.TP1_HIT || signal.status == SignalStatus.TP2_HIT)) {
                val diff = if (isBuy) livePrice - signal.entryPrice else signal.entryPrice - livePrice
                val pips = diff / pair.pipSize
                val pnlColor = if (pips >= 0) tradingColors.buy else tradingColors.sell
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Live Mark: ${pair.formatPrice(livePrice)}",
                        style = MonoNumericStyle.copy(fontSize = 11.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = String.format(Locale.US, "Live PnL: %+.1f pips", pips),
                        style = MonoNumericStyle.copy(fontSize = 11.sp, fontWeight = FontWeight.Bold),
                        color = pnlColor
                    )
                }
            } else {
                val pnlColor = if (signal.outcomePips >= 0) tradingColors.buy else tradingColors.sell
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = if (signal.status == SignalStatus.TP3_HIT) "Status: Success Done (TP3)" else "Status: ${signal.status.label}",
                        style = MonoNumericStyle.copy(fontSize = 11.sp, fontWeight = FontWeight.SemiBold),
                        color = pnlColor
                    )
                    Text(
                        text = String.format(Locale.US, "Outcome: %+.1f pips (%+.2f%%)", signal.outcomePips, signal.outcomePercent),
                        style = MonoNumericStyle.copy(fontSize = 11.sp, fontWeight = FontWeight.Bold),
                        color = pnlColor
                    )
                }
            }
        }
    }
}

@Composable
private fun RealTimeTargetProgressBar(
    signal: TradingSignal,
    livePrice: Double?,
    pair: com.example.domain.model.TradingPair
) {
    val tradingColors = LocalTradingColors.current
    val isBuy = signal.direction == SignalDirection.BUY
    val mark = livePrice ?: signal.entryPrice

    // Normalized progress along SL (0f) .. Entry (0.20f) .. TP1 (0.47f) .. TP2 (0.73f) .. TP3 Done (1.0f)
    val progress = when (signal.status) {
        SignalStatus.SL_HIT -> 0.0f
        SignalStatus.TP3_HIT -> 1.0f
        else -> {
            val signedMove = if (isBuy) mark - signal.entryPrice else signal.entryPrice - mark
            if (signedMove < 0.0) {
                val slDist = kotlin.math.abs(signal.entryPrice - signal.stopLoss).coerceAtLeast(pair.pipSize)
                val ratio = (kotlin.math.abs(signedMove) / slDist).toFloat().coerceIn(0f, 1f)
                (0.20f * (1f - ratio)).coerceIn(0f, 0.20f)
            } else {
                val tp3Dist = kotlin.math.abs(signal.tp3 - signal.entryPrice).coerceAtLeast(pair.pipSize)
                val ratio = (signedMove / tp3Dist).toFloat().coerceIn(0f, 1f)
                (0.20f + 0.80f * ratio).coerceIn(0.20f, 1.0f)
            }
        }
    }

    val tp1Reached = signal.status == SignalStatus.TP1_HIT ||
        signal.status == SignalStatus.TP2_HIT ||
        signal.status == SignalStatus.TP3_HIT ||
        (signal.status != SignalStatus.SL_HIT && ((isBuy && mark >= signal.tp1) || (!isBuy && mark <= signal.tp1)))
    val tp2Reached = signal.status == SignalStatus.TP2_HIT ||
        signal.status == SignalStatus.TP3_HIT ||
        (signal.status != SignalStatus.SL_HIT && ((isBuy && mark >= signal.tp2) || (!isBuy && mark <= signal.tp2)))
    val tp3Done = signal.status == SignalStatus.TP3_HIT ||
        (signal.status != SignalStatus.SL_HIT && ((isBuy && mark >= signal.tp3) || (!isBuy && mark <= signal.tp3)))
    val slHit = signal.status == SignalStatus.SL_HIT

    val barColor = when {
        slHit || progress < 0.20f -> tradingColors.sell
        tp3Done -> tradingColors.buy
        tp1Reached -> tradingColors.buy
        else -> tradingColors.pulse
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            MilestoneChip("SL HIT", slHit, tradingColors.sell)
            MilestoneChip("TP1", tp1Reached, tradingColors.pulse)
            MilestoneChip("TP2", tp2Reached, tradingColors.buy)
            MilestoneChip(if (tp3Done) "SUCCESS DONE" else "TP3", tp3Done, tradingColors.buy)
        }

        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(6.dp))
                .testTag("signal_progress_bar_${signal.id}"),
            color = barColor,
            trackColor = MaterialTheme.colorScheme.outlineVariant
        )
    }
}

@Composable
private fun MilestoneChip(
    label: String,
    active: Boolean,
    activeColor: androidx.compose.ui.graphics.Color
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(5.dp))
            .background(
                if (active) activeColor.copy(alpha = 0.22f)
                else MaterialTheme.colorScheme.surface
            )
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            text = label,
            style = MonoNumericStyle.copy(
                fontSize = 9.sp,
                fontWeight = if (active) FontWeight.Bold else FontWeight.Medium
            ),
            color = if (active) activeColor else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun PriceTargetCol(
    label: String,
    value: String,
    color: androidx.compose.ui.graphics.Color
) {
    Column(horizontalAlignment = Alignment.Start) {
        Text(
            text = label,
            style = MonoNumericStyle.copy(fontSize = 9.sp, fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = value,
            style = MonoNumericStyle.copy(fontSize = 12.sp, fontWeight = FontWeight.Bold),
            color = color
        )
    }
}
