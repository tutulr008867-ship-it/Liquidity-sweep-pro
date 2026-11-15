package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.SweepPulseApp
import com.example.data.local.UserSettings
import com.example.data.remote.MarketPairsCatalog
import com.example.domain.model.MarketType
import com.example.domain.model.PairMarketSnapshot
import com.example.domain.model.PerformanceMetrics
import com.example.domain.model.PushAlertRecord
import com.example.domain.model.ThemeMode
import com.example.domain.model.Timeframe
import com.example.domain.model.TradingPair
import com.example.domain.model.TradingSignal
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class AppTab(val label: String) {
    SIGNALS("Signals"),
    TERMINAL("Terminal"),
    PERFORMANCE("Metrics"),
    ALERTS("Alerts"),
    SETTINGS("Settings")
}

enum class MarketFilter(val label: String) {
    ALL("All Markets"),
    FOREX("Forex"),
    CRYPTO("Crypto")
}

data class ChartOverlaySettings(
    val showEma9: Boolean = true,
    val showEma21: Boolean = true,
    val showEma50: Boolean = true,
    val showSma200: Boolean = false,
    val showSmcZones: Boolean = true
)

data class TradingUiState(
    val selectedTab: AppTab = AppTab.SIGNALS,
    val selectedPairSymbol: String = "BTC/USDT",
    val selectedTimeframe: Timeframe = Timeframe.M15,
    val marketFilter: MarketFilter = MarketFilter.ALL,
    val activeSignalsOnly: Boolean = false,
    val searchQuery: String = "",
    val snapshots: Map<String, PairMarketSnapshot> = emptyMap(),
    val signals: List<TradingSignal> = emptyList(),
    val pushAlerts: List<PushAlertRecord> = emptyList(),
    val performance: PerformanceMetrics? = null,
    val settings: UserSettings = UserSettings(),
    val overlaySettings: ChartOverlaySettings = ChartOverlaySettings(),
    val isScanning: Boolean = true,
    val lastTickMillis: Long = System.currentTimeMillis()
) {
    val allPairs: List<TradingPair> get() = MarketPairsCatalog.allPairs

    val selectedSnapshot: PairMarketSnapshot?
        get() = snapshots[selectedPairSymbol]

    val unreadAlertCount: Int
        get() = pushAlerts.count { !it.isRead }

    val filteredSignals: List<TradingSignal>
        get() = signals.filter { sig ->
            val marketOk = when (marketFilter) {
                MarketFilter.ALL -> true
                MarketFilter.FOREX -> sig.marketType == MarketType.FOREX
                MarketFilter.CRYPTO -> sig.marketType == MarketType.CRYPTO
            }
            val activeOk = if (activeSignalsOnly) {
                sig.status == com.example.domain.model.SignalStatus.ACTIVE
            } else true
            val queryOk = searchQuery.isBlank() ||
                sig.pairSymbol.contains(searchQuery, ignoreCase = true) ||
                sig.displayName.contains(searchQuery, ignoreCase = true)
            marketOk && activeOk && queryOk
        }

    val filteredSnapshots: List<PairMarketSnapshot>
        get() = MarketPairsCatalog.allPairs.mapNotNull { pair ->
            val snap = snapshots[pair.symbol] ?: return@mapNotNull null
            val marketOk = when (marketFilter) {
                MarketFilter.ALL -> true
                MarketFilter.FOREX -> pair.marketType == MarketType.FOREX
                MarketFilter.CRYPTO -> pair.marketType == MarketType.CRYPTO
            }
            val queryOk = searchQuery.isBlank() ||
                pair.symbol.contains(searchQuery, ignoreCase = true) ||
                pair.displayName.contains(searchQuery, ignoreCase = true)
            if (marketOk && queryOk) snap else null
        }
}

class TradingViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as SweepPulseApp
    private val repo = app.tradingRepository
    private val prefs = app.preferencesRepository

    private val selectedTab = MutableStateFlow(AppTab.SIGNALS)
    private val selectedTimeframe = MutableStateFlow(Timeframe.M15)
    private val marketFilter = MutableStateFlow(MarketFilter.ALL)
    private val activeSignalsOnly = MutableStateFlow(false)
    private val searchQuery = MutableStateFlow("")
    private val overlaySettings = MutableStateFlow(ChartOverlaySettings())

    private data class NavFilterTuple(
        val tab: AppTab,
        val timeframe: Timeframe,
        val filter: MarketFilter,
        val activeOnly: Boolean,
        val query: String,
        val overlays: ChartOverlaySettings
    )

    private val navFilterFlow = combine(
        selectedTab,
        selectedTimeframe,
        marketFilter,
        activeSignalsOnly,
        searchQuery
    ) { tab, tf, filter, activeOnly, query ->
        NavFilterTuple(tab, tf, filter, activeOnly, query, overlaySettings.value)
    }.combine(overlaySettings) { tuple, overlays ->
        tuple.copy(overlays = overlays)
    }

    private data class MarketDataTuple(
        val snapshots: Map<String, PairMarketSnapshot>,
        val signals: List<TradingSignal>,
        val alerts: List<PushAlertRecord>,
        val perf: PerformanceMetrics,
        val scanning: Boolean,
        val tick: Long
    )

    private val marketDataFlow = combine(
        repo.snapshots,
        repo.allSignals,
        repo.pushAlerts,
        repo.performanceMetrics,
        repo.isScanning
    ) { snaps, sigs, alerts, perf, scanning ->
        MarketDataTuple(snaps, sigs, alerts, perf, scanning, System.currentTimeMillis())
    }.combine(repo.lastSecondTickMillis) { tuple, tick ->
        tuple.copy(tick = tick)
    }

    val uiState: StateFlow<TradingUiState> = combine(
        navFilterFlow,
        marketDataFlow,
        repo.userSettings
    ) { nav, market, settings ->
        TradingUiState(
            selectedTab = nav.tab,
            selectedPairSymbol = settings.selectedPairSymbol,
            selectedTimeframe = nav.timeframe,
            marketFilter = nav.filter,
            activeSignalsOnly = nav.activeOnly,
            searchQuery = nav.query,
            snapshots = market.snapshots,
            signals = market.signals,
            pushAlerts = market.alerts,
            performance = market.perf,
            settings = settings,
            overlaySettings = nav.overlays,
            isScanning = market.scanning,
            lastTickMillis = market.tick
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000L),
        initialValue = TradingUiState()
    )

    fun selectTab(tab: AppTab) {
        selectedTab.value = tab
        if (tab == AppTab.ALERTS) {
            viewModelScope.launch { repo.markAllAlertsRead() }
        }
    }

    fun openPairInTerminal(pairSymbol: String, timeframe: Timeframe? = null) {
        viewModelScope.launch {
            prefs.setSelectedPairSymbol(pairSymbol)
            val tf = timeframe ?: selectedTimeframe.value
            selectedTimeframe.value = tf
            selectedTab.value = AppTab.TERMINAL
            repo.refreshSelectedPair(pairSymbol, tf)
        }
    }

    fun selectPair(pairSymbol: String) {
        viewModelScope.launch {
            prefs.setSelectedPairSymbol(pairSymbol)
            repo.refreshSelectedPair(pairSymbol, selectedTimeframe.value)
        }
    }

    fun selectTimeframe(timeframe: Timeframe) {
        selectedTimeframe.value = timeframe
        viewModelScope.launch {
            repo.refreshSelectedPair(uiState.value.selectedPairSymbol, timeframe)
        }
    }

    fun setMarketFilter(filter: MarketFilter) {
        marketFilter.value = filter
    }

    fun toggleActiveSignalsOnly() {
        activeSignalsOnly.update { !it }
    }

    fun setSearchQuery(query: String) {
        searchQuery.value = query
    }

    fun toggleEma9() = overlaySettings.update { it.copy(showEma9 = !it.showEma9) }
    fun toggleEma21() = overlaySettings.update { it.copy(showEma21 = !it.showEma21) }
    fun toggleEma50() = overlaySettings.update { it.copy(showEma50 = !it.showEma50) }
    fun toggleSma200() = overlaySettings.update { it.copy(showSma200 = !it.showSma200) }
    fun toggleSmcZones() = overlaySettings.update { it.copy(showSmcZones = !it.showSmcZones) }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { prefs.setThemeMode(mode) }
    }

    fun setPushAlertsEnabled(enabled: Boolean) {
        viewModelScope.launch { prefs.setPushAlertsEnabled(enabled) }
    }

    fun setNotifyOnTargetHits(enabled: Boolean) {
        viewModelScope.launch { prefs.setNotifyOnTargetHits(enabled) }
    }

    fun setMinConsensusCount(count: Int) {
        viewModelScope.launch { prefs.setMinConsensusCount(count) }
    }

    fun setMinConfidencePercent(percent: Int) {
        viewModelScope.launch { prefs.setMinConfidencePercent(percent) }
    }

    fun toggleAlertTimeframe(timeframe: Timeframe) {
        viewModelScope.launch { prefs.toggleAlertTimeframe(timeframe) }
    }

    fun setAlertTimeframesPreset(timeframes: Set<Timeframe>) {
        viewModelScope.launch { prefs.setEnabledAlertTimeframes(timeframes) }
    }

    fun setAlertForex(enabled: Boolean) {
        viewModelScope.launch { prefs.setAlertForex(enabled) }
    }

    fun setAlertCrypto(enabled: Boolean) {
        viewModelScope.launch { prefs.setAlertCrypto(enabled) }
    }

    fun triggerLiveTestPush() {
        viewModelScope.launch { repo.triggerLiveSignalAlertDemo() }
    }

    fun forceMarketScan() {
        viewModelScope.launch { repo.forceFullMarketScan() }
    }

    fun clearAlerts() {
        viewModelScope.launch { repo.clearAlerts() }
    }
}
