package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material.icons.rounded.Send
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.domain.model.ThemeMode
import com.example.domain.model.Timeframe
import com.example.ui.theme.LocalTradingColors
import com.example.ui.theme.MonoNumericStyle
import com.example.ui.viewmodel.TradingUiState

@Composable
fun SettingsScreen(
    uiState: TradingUiState,
    onSetPushAlertsEnabled: (Boolean) -> Unit,
    onSetNotifyOnTargetHits: (Boolean) -> Unit,
    onToggleAlertTimeframe: (Timeframe) -> Unit,
    onSetTimeframesPreset: (Set<Timeframe>) -> Unit,
    onSetMinConsensus: (Int) -> Unit,
    onSetMinConfidence: (Int) -> Unit,
    onSetAlertForex: (Boolean) -> Unit,
    onSetAlertCrypto: (Boolean) -> Unit,
    onSetThemeMode: (ThemeMode) -> Unit,
    onTriggerLivePush: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val tradingColors = LocalTradingColors.current
    val settings = uiState.settings

    var hasNotifPerm by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
            } else {
                true
            }
        )
    }

    val permLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasNotifPerm = granted
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Header
        item {
            Column {
                Text(
                    text = "Signal & Alert Settings",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "Configure push notifications, monitored timeframes (1m–4H) & 26-indicator thresholds",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // System Push Notification Permission Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (hasNotifPerm) MaterialTheme.colorScheme.surface else tradingColors.sweepContainer
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = if (hasNotifPerm) Icons.Rounded.CheckCircle else Icons.Rounded.NotificationsOff,
                            contentDescription = null,
                            tint = if (hasNotifPerm) tradingColors.buy else tradingColors.sweep
                        )
                        Column {
                            Text(
                                text = if (hasNotifPerm) "Push Notifications Authorized" else "Permission Required",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = if (hasNotifPerm) {
                                    "Heads-up alerts active for Entry, SL & TP targets."
                                } else {
                                    "Enable Android notifications to receive instant signal alerts."
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    if (!hasNotifPerm && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        Button(
                            onClick = { permLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) },
                            colors = ButtonDefaults.buttonColors(containerColor = tradingColors.sweep),
                            modifier = Modifier.testTag("settings_grant_notif_btn")
                        ) {
                            Text("Allow", color = MaterialTheme.colorScheme.onTertiary)
                        }
                    }
                }
            }
        }

        // 1. Push Notification Delivery Toggles
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.NotificationsActive,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Push Notification Channels",
                            style = MaterialTheme.typography.titleMedium
                        )
                    }

                    SettingSwitchRow(
                        title = "New Setup Push Alerts",
                        subtitle = "Instant push when SMC Liquidity Sweep + 26-TV triggers",
                        checked = settings.pushAlertsEnabled,
                        onCheckedChange = onSetPushAlertsEnabled,
                        testTag = "settings_push_master_switch"
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                    SettingSwitchRow(
                        title = "TP1 / TP2 / TP3 & SL Hit Alerts",
                        subtitle = "Notify when open signals reach Take Profit or Stop Loss",
                        checked = settings.notifyOnTargetHits,
                        onCheckedChange = onSetNotifyOnTargetHits,
                        testTag = "settings_target_hits_switch"
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                    Text(
                        text = "Monitored Markets",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        FilterChip(
                            selected = settings.alertForex,
                            onClick = { onSetAlertForex(!settings.alertForex) },
                            label = { Text("Forex Majors (8 Pairs)") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.primary
                            ),
                            modifier = Modifier.testTag("settings_forex_chip")
                        )
                        FilterChip(
                            selected = settings.alertCrypto,
                            onClick = { onSetAlertCrypto(!settings.alertCrypto) },
                            label = { Text("Crypto Pairs (8 Pairs)") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.primary
                            ),
                            modifier = Modifier.testTag("settings_crypto_chip")
                        )
                    }
                }
            }
        }

        // 2. Timeframe Alert Toggles (1m to 4H)
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Alert Timeframes (1m to 4H)",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = "Choose which chart timeframes can trigger push notifications",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            text = "${settings.enabledAlertTimeframes.size}/6",
                            style = MonoNumericStyle.copy(fontSize = 13.sp, fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    // Quick Presets
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        AssistChip(
                            onClick = { onSetTimeframesPreset(setOf(Timeframe.M5, Timeframe.M15)) },
                            label = { Text("5m & 15m Precision") },
                            modifier = Modifier.testTag("preset_5m_15m")
                        )
                        AssistChip(
                            onClick = { onSetTimeframesPreset(Timeframe.entries.toSet()) },
                            label = { Text("All (1m–4H)") },
                            modifier = Modifier.testTag("preset_all_tf")
                        )
                        AssistChip(
                            onClick = { onSetTimeframesPreset(setOf(Timeframe.H1, Timeframe.H4)) },
                            label = { Text("1H & 4H Swing") },
                            modifier = Modifier.testTag("preset_swing_tf")
                        )
                    }

                    // Individual 1m..4H Timeframe Toggle Tiles
                    val rows = Timeframe.entries.chunked(2)
                    rows.forEach { pairRow ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            pairRow.forEach { tf ->
                                val enabled = settings.enabledAlertTimeframes.contains(tf)
                                TimeframeToggleTile(
                                    timeframe = tf,
                                    enabled = enabled,
                                    onClick = { onToggleAlertTimeframe(tf) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
            }
        }

        // 3. 26-Indicator Consensus & Signal Confidence Thresholds
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Tune,
                            contentDescription = null,
                            tint = tradingColors.sweep
                        )
                        Text(
                            text = "26-Indicator & Confidence Thresholds",
                            style = MaterialTheme.typography.titleMedium
                        )
                    }

                    // 26-Indicator Consensus Threshold
                    val tvTierLabel = when {
                        settings.minConsensusCount >= 21 -> "Institutional"
                        settings.minConsensusCount >= 17 -> "Strong"
                        else -> "Balanced"
                    }
                    val tvTierColor = when {
                        settings.minConsensusCount >= 21 -> tradingColors.buy
                        settings.minConsensusCount >= 17 -> tradingColors.pulse
                        else -> tradingColors.sweep
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Min 26-Indicator Consensus",
                                style = MaterialTheme.typography.labelLarge
                            )
                            Text(
                                text = "${settings.minConsensusCount} / 26 ($tvTierLabel)",
                                style = MonoNumericStyle.copy(fontSize = 13.sp, fontWeight = FontWeight.Bold),
                                color = tvTierColor
                            )
                        }
                        Slider(
                            value = settings.minConsensusCount.toFloat(),
                            onValueChange = { onSetMinConsensus(it.toInt()) },
                            valueRange = 10f..24f,
                            steps = 13,
                            colors = SliderDefaults.colors(
                                thumbColor = tvTierColor,
                                activeTrackColor = tvTierColor
                            ),
                            modifier = Modifier.testTag("settings_consensus_slider")
                        )
                        Text(
                            text = "Requires at least ${settings.minConsensusCount} of 26 TradingView indicators (11 Oscillators + 15 Moving Averages) to align with the liquidity sweep.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // Combined Signal Confidence Threshold (%)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Min Combined Confidence Score",
                                style = MaterialTheme.typography.labelLarge
                            )
                            Text(
                                text = "${settings.minConfidencePercent}%",
                                style = MonoNumericStyle.copy(fontSize = 13.sp, fontWeight = FontWeight.Bold),
                                color = tradingColors.buy
                            )
                        }
                        Slider(
                            value = settings.minConfidencePercent.toFloat(),
                            onValueChange = { onSetMinConfidence(it.toInt()) },
                            valueRange = 55f..95f,
                            steps = 7,
                            colors = SliderDefaults.colors(
                                thumbColor = tradingColors.buy,
                                activeTrackColor = tradingColors.buy
                            ),
                            modifier = Modifier.testTag("settings_confidence_slider")
                        )
                        Text(
                            text = "Blends 26-indicator score, 4H holistic bias, 5m/15m entry alignment, and RVOL volume spike.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Button(
                        onClick = {
                            if (!hasNotifPerm && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                permLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            }
                            onTriggerLivePush()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("settings_test_push_btn"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Send,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.size(8.dp))
                        Text("Send Test Push Notification (${uiState.selectedPairSymbol})")
                    }
                }
            }
        }

        // 4. Trading Session Theme Selector
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Low-Light Trading Theme",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = "OLED Dark Mode reduces eye strain during extended trading sessions.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ThemeMode.entries.forEach { mode ->
                            FilterChip(
                                selected = settings.themeMode == mode,
                                onClick = { onSetThemeMode(mode) },
                                label = { Text(mode.name.lowercase().replaceFirstChar { it.uppercase() }) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.primary
                                ),
                                modifier = Modifier.testTag("settings_theme_${mode.name.lowercase()}")
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    testTag: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.testTag(testTag)
        )
    }
}

@Composable
private fun TimeframeToggleTile(
    timeframe: Timeframe,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val tradingColors = LocalTradingColors.current
    val accent = if (timeframe == Timeframe.H4) tradingColors.sweep else MaterialTheme.colorScheme.primary

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (enabled) accent.copy(alpha = 0.14f)
                else MaterialTheme.colorScheme.surfaceVariant
            )
            .border(
                width = 1.dp,
                color = if (enabled) accent else MaterialTheme.colorScheme.outlineVariant,
                shape = RoundedCornerShape(12.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .testTag("settings_tf_${timeframe.label}"),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = timeframe.label,
                style = MonoNumericStyle.copy(fontSize = 14.sp, fontWeight = FontWeight.Bold),
                color = if (enabled) accent else MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = timeframe.roleDescription,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(if (enabled) accent.copy(alpha = 0.22f) else MaterialTheme.colorScheme.surface)
                .padding(horizontal = 6.dp, vertical = 2.dp)
        ) {
            Text(
                text = if (enabled) "ON" else "OFF",
                style = MonoNumericStyle.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold),
                color = if (enabled) accent else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
