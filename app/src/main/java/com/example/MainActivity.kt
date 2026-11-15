package com.example

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Analytics
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.CandlestickChart
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.Analytics
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.CandlestickChart
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.notifications.SignalNotificationManager
import com.example.ui.screens.AlertsScreen
import com.example.ui.screens.PerformanceScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.SignalsScreen
import com.example.ui.screens.TerminalScreen
import com.example.ui.theme.SweepPulseTheme
import com.example.ui.viewmodel.AppTab
import com.example.ui.viewmodel.TradingViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: TradingViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleDeepLink(intent)

        setContent {
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()

            SweepPulseTheme(themeMode = uiState.settings.themeMode) {
                if (uiState.selectedTab != AppTab.SIGNALS) {
                    BackHandler {
                        viewModel.selectTab(AppTab.SIGNALS)
                    }
                }

                BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                    val useRail = maxWidth >= 720.dp

                    if (useRail) {
                        Scaffold(
                            contentWindowInsets = WindowInsets(0, 0, 0, 0),
                            containerColor = MaterialTheme.colorScheme.background
                        ) { innerPadding ->
                            Row(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(innerPadding)
                            ) {
                                AppNavigationRail(
                                    selectedTab = uiState.selectedTab,
                                    unreadAlerts = uiState.unreadAlertCount,
                                    onSelectTab = viewModel::selectTab
                                )
                                TabContentHost(
                                    viewModel = viewModel,
                                    uiState = uiState,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    } else {
                        Scaffold(
                            containerColor = MaterialTheme.colorScheme.background,
                            bottomBar = {
                                AppBottomNavigationBar(
                                    selectedTab = uiState.selectedTab,
                                    unreadAlerts = uiState.unreadAlertCount,
                                    onSelectTab = viewModel::selectTab
                                )
                            }
                        ) { innerPadding ->
                            TabContentHost(
                                viewModel = viewModel,
                                uiState = uiState,
                                modifier = Modifier.padding(innerPadding)
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleDeepLink(intent)
    }

    private fun handleDeepLink(intent: Intent?) {
        val pairSymbol = intent?.getStringExtra(SignalNotificationManager.EXTRA_PAIR_SYMBOL)
        if (!pairSymbol.isNullOrBlank()) {
            viewModel.openPairInTerminal(pairSymbol)
        }
    }
}

@Composable
private fun TabContentHost(
    viewModel: TradingViewModel,
    uiState: com.example.ui.viewmodel.TradingUiState,
    modifier: Modifier = Modifier
) {
    when (uiState.selectedTab) {
        AppTab.SIGNALS -> SignalsScreen(
            uiState = uiState,
            onSelectMarketFilter = viewModel::setMarketFilter,
            onToggleActiveOnly = viewModel::toggleActiveSignalsOnly,
            onSearchChange = viewModel::setSearchQuery,
            onOpenPairTerminal = viewModel::openPairInTerminal,
            onForceScan = viewModel::forceMarketScan,
            onTriggerTestPush = viewModel::triggerLiveTestPush,
            modifier = modifier
        )

        AppTab.TERMINAL -> TerminalScreen(
            uiState = uiState,
            onSelectPair = viewModel::selectPair,
            onSelectTimeframe = viewModel::selectTimeframe,
            onToggleEma9 = viewModel::toggleEma9,
            onToggleEma21 = viewModel::toggleEma21,
            onToggleEma50 = viewModel::toggleEma50,
            onToggleSma200 = viewModel::toggleSma200,
            onToggleSmcZones = viewModel::toggleSmcZones,
            modifier = modifier
        )

        AppTab.PERFORMANCE -> PerformanceScreen(
            uiState = uiState,
            onOpenPairTerminal = viewModel::openPairInTerminal,
            modifier = modifier
        )

        AppTab.ALERTS -> AlertsScreen(
            uiState = uiState,
            onOpenSettings = { viewModel.selectTab(AppTab.SETTINGS) },
            onTriggerLivePush = viewModel::triggerLiveTestPush,
            onClearAlerts = viewModel::clearAlerts,
            onOpenPairTerminal = viewModel::openPairInTerminal,
            modifier = modifier
        )

        AppTab.SETTINGS -> SettingsScreen(
            uiState = uiState,
            onSetPushAlertsEnabled = viewModel::setPushAlertsEnabled,
            onSetNotifyOnTargetHits = viewModel::setNotifyOnTargetHits,
            onToggleAlertTimeframe = viewModel::toggleAlertTimeframe,
            onSetTimeframesPreset = viewModel::setAlertTimeframesPreset,
            onSetMinConsensus = viewModel::setMinConsensusCount,
            onSetMinConfidence = viewModel::setMinConfidencePercent,
            onSetAlertForex = viewModel::setAlertForex,
            onSetAlertCrypto = viewModel::setAlertCrypto,
            onSetThemeMode = viewModel::setThemeMode,
            onTriggerLivePush = viewModel::triggerLiveTestPush,
            modifier = modifier
        )
    }
}

@Composable
private fun AppBottomNavigationBar(
    selectedTab: AppTab,
    unreadAlerts: Int,
    onSelectTab: (AppTab) -> Unit
) {
    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        AppTab.entries.forEach { tab ->
            val selected = selectedTab == tab
            NavigationBarItem(
                selected = selected,
                onClick = { onSelectTab(tab) },
                icon = { TabIcon(tab = tab, selected = selected, unreadAlerts = unreadAlerts) },
                label = { Text(tab.label) },
                modifier = Modifier.testTag("nav_tab_${tab.name.lowercase()}")
            )
        }
    }
}

@Composable
private fun AppNavigationRail(
    selectedTab: AppTab,
    unreadAlerts: Int,
    onSelectTab: (AppTab) -> Unit
) {
    NavigationRail(
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        AppTab.entries.forEach { tab ->
            val selected = selectedTab == tab
            NavigationRailItem(
                selected = selected,
                onClick = { onSelectTab(tab) },
                icon = { TabIcon(tab = tab, selected = selected, unreadAlerts = unreadAlerts) },
                label = { Text(tab.label) },
                modifier = Modifier.testTag("rail_tab_${tab.name.lowercase()}")
            )
        }
    }
}

@Composable
private fun TabIcon(tab: AppTab, selected: Boolean, unreadAlerts: Int) {
    val icon = when (tab) {
        AppTab.SIGNALS -> if (selected) Icons.Rounded.Bolt else Icons.Outlined.Bolt
        AppTab.TERMINAL -> if (selected) Icons.Rounded.CandlestickChart else Icons.Outlined.CandlestickChart
        AppTab.PERFORMANCE -> if (selected) Icons.Rounded.Analytics else Icons.Outlined.Analytics
        AppTab.ALERTS -> if (selected) Icons.Rounded.Notifications else Icons.Outlined.Notifications
        AppTab.SETTINGS -> if (selected) Icons.Rounded.Settings else Icons.Outlined.Settings
    }
    if (tab == AppTab.ALERTS && unreadAlerts > 0) {
        BadgedBox(
            badge = {
                Badge { Text(unreadAlerts.coerceAtMost(99).toString()) }
            }
        ) {
            Icon(imageVector = icon, contentDescription = tab.label)
        }
    } else {
        Icon(imageVector = icon, contentDescription = tab.label)
    }
}
