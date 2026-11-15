package com.example.data.repository

import com.example.data.local.PushAlertEntity
import com.example.data.local.SignalDao
import com.example.data.local.SignalEntity
import com.example.data.local.UserPreferencesRepository
import com.example.data.local.UserSettings
import com.example.data.remote.MarketApiService
import com.example.data.remote.MarketPairsCatalog
import com.example.data.remote.TickerQuote
import com.example.domain.engine.SmcSignalEngine
import com.example.domain.engine.TradingViewIndicatorsEngine
import com.example.domain.model.Candle
import com.example.domain.model.EquityCurvePoint
import com.example.domain.model.MarketType
import com.example.domain.model.PairMarketSnapshot
import com.example.domain.model.PerformanceMetrics
import com.example.domain.model.PushAlertRecord
import com.example.domain.model.SignalDirection
import com.example.domain.model.SignalStatus
import com.example.domain.model.SmcAnalysis
import com.example.domain.model.Timeframe
import com.example.domain.model.TimeframePerformance
import com.example.domain.model.TradingPair
import com.example.domain.model.TradingSignal
import com.example.domain.model.TradingViewConsensus
import com.example.notifications.SignalNotificationManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

class TradingRepository(
    private val apiService: MarketApiService,
    private val signalDao: SignalDao,
    private val prefsRepo: UserPreferencesRepository,
    private val notificationManager: SignalNotificationManager
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _snapshots = MutableStateFlow<Map<String, PairMarketSnapshot>>(emptyMap())
    val snapshots: StateFlow<Map<String, PairMarketSnapshot>> = _snapshots.asStateFlow()

    private val _isScanning = MutableStateFlow(true)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _lastSecondTickMillis = MutableStateFlow(System.currentTimeMillis())
    val lastSecondTickMillis: StateFlow<Long> = _lastSecondTickMillis.asStateFlow()

    val allSignals: StateFlow<List<TradingSignal>> = signalDao.observeAllSignals()
        .map { entities -> entities.map { it.toDomain() } }
        .stateIn(scope, SharingStarted.Eagerly, emptyList())

    val pushAlerts: StateFlow<List<PushAlertRecord>> = signalDao.observePushAlerts()
        .map { list -> list.map { it.toDomain() } }
        .stateIn(scope, SharingStarted.Eagerly, emptyList())

    val performanceMetrics: StateFlow<PerformanceMetrics> = allSignals
        .map { computePerformanceMetrics(it) }
        .stateIn(scope, SharingStarted.Eagerly, computePerformanceMetrics(emptyList()))

    val userSettings: Flow<UserSettings> = prefsRepo.settingsFlow

    private val liveQuotesCache = ConcurrentHashMap<String, TickerQuote>()
    private val candleCache = ConcurrentHashMap<Pair<String, Timeframe>, MutableList<Candle>>()
    private val liveFlagCache = ConcurrentHashMap<String, Boolean>()
    private val alertedSignalIds = ConcurrentHashMap.newKeySet<String>()

    @Volatile
    private var started = false

    fun startRealtimeEngine() {
        if (started) return
        started = true

        // 1. Initial multi-timeframe hydration & historical SMC signal scan
        scope.launch {
            hydrateInitialMarkets()
            _isScanning.value = false
        }

        // 2. WebSocket sub-second stream for Crypto pairs
        scope.launch {
            apiService.streamBinanceMiniTickers(MarketPairsCatalog.allPairs).collect { quote ->
                liveQuotesCache[quote.symbol] = quote
                liveFlagCache[quote.symbol] = true
            }
        }

        // 3. Dedicated 1-second tick loop updating prices, Moving Averages, Volume indicators, and 26-TV consensus
        scope.launch {
            var tickCounter = 0
            while (isActive) {
                delay(1000L)
                tickCounter++
                try {
                    if (tickCounter % 5 == 1) {
                        val restQuotes = apiService.fetchAllLiveQuotes(MarketPairsCatalog.allPairs)
                        restQuotes.forEach { (sym, q) ->
                            liveQuotesCache[sym] = q
                            liveFlagCache[sym] = true
                        }
                    }

                    // Periodically refresh candles for active selected pair & enabled alert timeframes
                    if (tickCounter % 15 == 0) {
                        val currentSettings = prefsRepo.settingsFlow.first()
                        val selected = currentSettings.selectedPairSymbol
                        refreshPairTimeframes(MarketPairsCatalog.findBySymbol(selected), Timeframe.entries)
                        val extraTfs = currentSettings.enabledAlertTimeframes.filter {
                            it == Timeframe.M1 || it == Timeframe.M30
                        }
                        if (extraTfs.isNotEmpty()) {
                            val nextIdx = (tickCounter / 15) % MarketPairsCatalog.allPairs.size
                            val nextPair = MarketPairsCatalog.allPairs[nextIdx]
                            if (nextPair.symbol != selected) {
                                refreshPairTimeframes(nextPair, extraTfs)
                            }
                        }
                    }

                    updateOneSecondSnapshotsAndEvaluateSignals()
                    _lastSecondTickMillis.value = System.currentTimeMillis()
                } catch (_: Exception) {
                }
            }
        }
    }

    suspend fun refreshSelectedPair(pairSymbol: String, timeframe: Timeframe) {
        val pair = MarketPairsCatalog.findBySymbol(pairSymbol)
        // Always refresh requested timeframe + 5m, 15m, 1H, 4H for full multi-timeframe SMC analysis
        val needed = listOf(timeframe, Timeframe.M5, Timeframe.M15, Timeframe.H1, Timeframe.H4).distinct()
        refreshPairTimeframes(pair, needed)
        rebuildSnapshotForPair(pair)
    }

    suspend fun forceFullMarketScan() {
        _isScanning.value = true
        try {
            val restQuotes = apiService.fetchAllLiveQuotes(MarketPairsCatalog.allPairs)
            restQuotes.forEach { (sym, q) ->
                liveQuotesCache[sym] = q
                liveFlagCache[sym] = true
            }
            val enabledTfs = prefsRepo.settingsFlow.first().enabledAlertTimeframes
            val scanTfs = (setOf(Timeframe.M5, Timeframe.M15, Timeframe.H1, Timeframe.H4) + enabledTfs).toList()
            MarketPairsCatalog.allPairs.chunked(4).forEach { batch ->
                batch.map { pair ->
                    scope.async {
                        refreshPairTimeframes(pair, scanTfs)
                    }
                }.awaitAll()
            }
            updateOneSecondSnapshotsAndEvaluateSignals()
        } finally {
            _isScanning.value = false
        }
    }

    suspend fun triggerLiveSignalAlertDemo() {
        val settings = prefsRepo.settingsFlow.first()
        val pair = MarketPairsCatalog.findBySymbol(settings.selectedPairSymbol)
        val snap = _snapshots.value[pair.symbol] ?: return
        val consensus15 = snap.consensusByTimeframe[Timeframe.M15]
            ?: snap.consensusByTimeframe[Timeframe.M5]
            ?: return
        val smc15 = snap.smcByTimeframe[Timeframe.M15]
            ?: snap.smcByTimeframe[Timeframe.M5]
            ?: return

        val direction = if (consensus15.buyCount >= consensus15.sellCount) {
            SignalDirection.BUY
        } else {
            SignalDirection.SELL
        }
        val entry = snap.currentPrice
        val atr = smc15.atr14.coerceAtLeast(pair.pipSize * 5.0)
        val sl = if (direction == SignalDirection.BUY) entry - atr * 1.1 else entry + atr * 1.1
        val risk = kotlin.math.abs(entry - sl)
        val tp1 = if (direction == SignalDirection.BUY) entry + risk * 1.5 else entry - risk * 1.5
        val tp2 = if (direction == SignalDirection.BUY) entry + risk * 2.5 else entry - risk * 2.5
        val tp3 = if (direction == SignalDirection.BUY) entry + risk * 3.8 else entry - risk * 3.8

        val now = System.currentTimeMillis()
        val signal = TradingSignal(
            id = "live_${pair.symbol}_${now}",
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
            timeframeEntry = Timeframe.M5,
            confidenceScore = 88,
            smcSetupSummary = "${if (direction == SignalDirection.BUY) "SSL Sweep" else "BSL Sweep"} + 5m/15m FVG + ${maxOf(consensus15.buyCount, consensus15.sellCount)}/26 TV ${direction.label}",
            sweepType = if (direction == SignalDirection.BUY) com.example.domain.model.SmcSweepType.BULLISH_SSL_SWEEP else com.example.domain.model.SmcSweepType.BEARISH_BSL_SWEEP,
            sweepPrice = if (direction == SignalDirection.BUY) entry - atr * 0.45 else entry + atr * 0.45,
            htfBias4H = smc15.htfBias4H,
            entry5mSummary = smc15.entryAnalysis5m,
            entry15mSummary = smc15.entryAnalysis15m,
            tvBuyCount = consensus15.buyCount,
            tvNeutralCount = consensus15.neutralCount,
            tvSellCount = consensus15.sellCount,
            tvRating = consensus15.overallRating,
            rvolAtTrigger = smc15.rvol,
            status = SignalStatus.ACTIVE,
            outcomePips = 0.0,
            outcomePercent = 0.0,
            createdAtMillis = now,
            updatedAtMillis = now
        )
        persistAndPushSignal(signal, forcePush = true)
    }

    suspend fun markAllAlertsRead() {
        signalDao.markAllAlertsRead()
    }

    suspend fun clearAlerts() {
        signalDao.clearAlerts()
    }

    private suspend fun hydrateInitialMarkets() {
        val restQuotes = apiService.fetchAllLiveQuotes(MarketPairsCatalog.allPairs)
        restQuotes.forEach { (sym, q) ->
            liveQuotesCache[sym] = q
            liveFlagCache[sym] = true
        }

        val initialSettings = prefsRepo.settingsFlow.first()
        val initialScanTfs = (setOf(Timeframe.M5, Timeframe.M15, Timeframe.H1, Timeframe.H4) + initialSettings.enabledAlertTimeframes).toList()

        // Load core + user-enabled alert timeframes for all 16 Forex & Crypto pairs in parallel batches
        MarketPairsCatalog.allPairs.chunked(4).forEach { batch ->
            batch.map { pair ->
                scope.async {
                    refreshPairTimeframes(pair, initialScanTfs)
                }
            }.awaitAll()
        }

        // Also preload remaining 1m & 30m for default selected pair
        val selected = initialSettings.selectedPairSymbol
        val missingForSelected = listOf(Timeframe.M1, Timeframe.M30).filterNot { initialScanTfs.contains(it) }
        if (missingForSelected.isNotEmpty()) {
            refreshPairTimeframes(MarketPairsCatalog.findBySymbol(selected), missingForSelected)
        }

        // If database has no signals yet, scan genuine historical candles across all 16 pairs
        if (signalDao.countSignals() == 0) {
            val historicalSignals = ArrayList<TradingSignal>()
            for (pair in MarketPairsCatalog.allPairs) {
                val c5 = candleCache[pair.symbol to Timeframe.M5] ?: emptyList()
                val c15 = candleCache[pair.symbol to Timeframe.M15] ?: emptyList()
                val c1h = candleCache[pair.symbol to Timeframe.H1] ?: emptyList()
                val c4h = candleCache[pair.symbol to Timeframe.H4] ?: emptyList()
                historicalSignals.addAll(
                    SmcSignalEngine.scanHistoricalSignals(pair, c5, c15, c1h, c4h)
                )
            }
            if (historicalSignals.isNotEmpty()) {
                signalDao.upsertSignals(historicalSignals.map { SignalEntity.fromDomain(it) })
                historicalSignals.forEach { alertedSignalIds.add(it.id) }
            }
        } else {
            signalDao.getAllSignalsOnce().forEach { alertedSignalIds.add(it.id) }
        }

        updateOneSecondSnapshotsAndEvaluateSignals()
    }

    private suspend fun refreshPairTimeframes(pair: TradingPair, timeframes: List<Timeframe>) {
        for (tf in timeframes) {
            val result = apiService.fetchCandles(pair, tf, limit = 120)
            candleCache[pair.symbol to tf] = result.candles.toMutableList()
            if (result.isLive) {
                liveFlagCache[pair.symbol] = true
            }
        }
    }

    private suspend fun updateOneSecondSnapshotsAndEvaluateSignals() {
        val settings = prefsRepo.settingsFlow.first()
        val existingSignals = signalDao.getAllSignalsOnce().map { it.toDomain() }
        val activeSignalsByPair = existingSignals
            .filter {
                it.status == SignalStatus.ACTIVE ||
                    it.status == SignalStatus.TP1_HIT ||
                    it.status == SignalStatus.TP2_HIT
            }
            .associateBy { it.pairSymbol }

        val updatedMap = LinkedHashMap<String, PairMarketSnapshot>(MarketPairsCatalog.allPairs.size)

        for (pair in MarketPairsCatalog.allPairs) {
            val quote = liveQuotesCache[pair.symbol]
            val previousSnap = _snapshots.value[pair.symbol]
            val baseCandles15 = candleCache[pair.symbol to Timeframe.M15]

            val livePrice = quote?.lastPrice
                ?: baseCandles15?.lastOrNull()?.close
                ?: pair.referencePrice
            val prevPrice = previousSnap?.currentPrice ?: livePrice

            // Update active candle close/high/low/volume across cached timeframes on this 1-second tick
            for (tf in Timeframe.entries) {
                val list = candleCache[pair.symbol to tf] ?: continue
                if (list.isNotEmpty()) {
                    val last = list.last()
                    val volIncrement = if (quote != null && quote.isLive) {
                        (last.volume * 0.002).coerceAtLeast(1.0)
                    } else 0.0
                    val updatedLast = last.copy(
                        close = livePrice,
                        high = maxOf(last.high, livePrice),
                        low = minOf(last.low, livePrice),
                        volume = last.volume + volIncrement
                    )
                    list[list.lastIndex] = updatedLast
                }
            }

            val snapshot = buildSnapshot(pair, livePrice, prevPrice, quote, activeSignalsByPair[pair.symbol])
            updatedMap[pair.symbol] = snapshot

            // Evaluate active signal TP/SL progress on every 1-second tick
            activeSignalsByPair[pair.symbol]?.let { activeSig ->
                evaluateActiveSignalTick(pair, activeSig, livePrice, settings)
            } ?: run {
                // Scan all user-enabled timeframes (1m to 4H) for Combined SMC + 26-Indicator setups
                val newSig = SmcSignalEngine.evaluateCombinedSignalForEnabledTimeframes(
                    pair = pair,
                    candlesByTimeframe = snapshot.candlesByTimeframe,
                    smcByTimeframe = snapshot.smcByTimeframe,
                    consensusByTimeframe = snapshot.consensusByTimeframe,
                    enabledTimeframes = settings.enabledAlertTimeframes,
                    minTvConsensusCount = settings.minConsensusCount,
                    minConfidencePercent = settings.minConfidencePercent
                )
                if (newSig != null && !alertedSignalIds.contains(newSig.id)) {
                    val marketAllowed = (pair.marketType == MarketType.FOREX && settings.alertForex) ||
                        (pair.marketType == MarketType.CRYPTO && settings.alertCrypto)
                    persistAndPushSignal(newSig, forcePush = settings.pushAlertsEnabled && marketAllowed)
                }
            }
        }

        _snapshots.value = updatedMap
    }

    private suspend fun rebuildSnapshotForPair(pair: TradingPair) {
        val existingSignals = signalDao.getAllSignalsOnce().map { it.toDomain() }
        val active = existingSignals.firstOrNull { it.pairSymbol == pair.symbol && it.status == SignalStatus.ACTIVE }
        val quote = liveQuotesCache[pair.symbol]
        val prev = _snapshots.value[pair.symbol]?.currentPrice ?: pair.referencePrice
        val price = quote?.lastPrice ?: candleCache[pair.symbol to Timeframe.M15]?.lastOrNull()?.close ?: prev
        val snap = buildSnapshot(pair, price, prev, quote, active)
        _snapshots.value = _snapshots.value.toMutableMap().apply { put(pair.symbol, snap) }
    }

    private fun buildSnapshot(
        pair: TradingPair,
        livePrice: Double,
        prevPrice: Double,
        quote: TickerQuote?,
        activeSignal: TradingSignal?
    ): PairMarketSnapshot {
        val candlesMap = LinkedHashMap<Timeframe, List<Candle>>()
        for (tf in Timeframe.entries) {
            candleCache[pair.symbol to tf]?.let { candlesMap[tf] = it.toList() }
        }

        val c5 = candlesMap[Timeframe.M5] ?: emptyList()
        val c15 = candlesMap[Timeframe.M15] ?: emptyList()
        val c1h = candlesMap[Timeframe.H1] ?: emptyList()
        val c4h = candlesMap[Timeframe.H4] ?: emptyList()

        val smcMap = LinkedHashMap<Timeframe, SmcAnalysis>()
        val consensusMap = LinkedHashMap<Timeframe, TradingViewConsensus>()

        for ((tf, candles) in candlesMap) {
            smcMap[tf] = SmcSignalEngine.analyzeTimeframe(pair, candles, c4h, c1h, c15, c5)
            consensusMap[tf] = TradingViewIndicatorsEngine.evaluateConsensus(candles, pair.decimals)
        }

        val pct24h = quote?.priceChange24hPct ?: run {
            val ref = c15.firstOrNull()?.open ?: pair.referencePrice
            if (ref > 0) ((livePrice - ref) / ref) * 100.0 else 0.0
        }
        val vol24h = quote?.volume24h?.takeIf { it > 0 }
            ?: (c15.takeLast(24).sumOf { it.volume } * livePrice)

        val isLive = liveFlagCache[pair.symbol] == true
        return PairMarketSnapshot(
            pair = pair,
            currentPrice = livePrice,
            previousPrice = prevPrice,
            priceChange24hPct = pct24h,
            bidPrice = livePrice - pair.typicalSpread * 0.5,
            askPrice = livePrice + pair.typicalSpread * 0.5,
            volume24h = vol24h,
            lastUpdatedMillis = System.currentTimeMillis(),
            isLiveFeed = isLive,
            feedLabel = if (isLive) "LIVE" else "CACHED",
            candlesByTimeframe = candlesMap,
            smcByTimeframe = smcMap,
            consensusByTimeframe = consensusMap,
            activeSignal = activeSignal
        )
    }

    private suspend fun evaluateActiveSignalTick(
        pair: TradingPair,
        signal: TradingSignal,
        livePrice: Double,
        settings: UserSettings
    ) {
        val isBuy = signal.direction == SignalDirection.BUY
        val rawDiff = if (isBuy) livePrice - signal.entryPrice else signal.entryPrice - livePrice
        val livePips = rawDiff / pair.pipSize
        val livePct = (rawDiff / signal.entryPrice.coerceAtLeast(1e-9)) * 100.0

        val newStatus = when {
            isBuy && livePrice >= signal.tp3 -> SignalStatus.TP3_HIT
            isBuy && livePrice >= signal.tp2 && signal.status != SignalStatus.TP3_HIT -> SignalStatus.TP2_HIT
            isBuy && livePrice >= signal.tp1 && signal.status == SignalStatus.ACTIVE -> SignalStatus.TP1_HIT
            isBuy && livePrice <= signal.stopLoss && signal.status == SignalStatus.ACTIVE -> SignalStatus.SL_HIT
            !isBuy && livePrice <= signal.tp3 -> SignalStatus.TP3_HIT
            !isBuy && livePrice <= signal.tp2 && signal.status != SignalStatus.TP3_HIT -> SignalStatus.TP2_HIT
            !isBuy && livePrice <= signal.tp1 && signal.status == SignalStatus.ACTIVE -> SignalStatus.TP1_HIT
            !isBuy && livePrice >= signal.stopLoss && signal.status == SignalStatus.ACTIVE -> SignalStatus.SL_HIT
            else -> signal.status
        }

        if (newStatus != signal.status) {
            val updated = signal.copy(
                status = newStatus,
                outcomePips = livePips,
                outcomePercent = livePct,
                updatedAtMillis = System.currentTimeMillis()
            )
            signalDao.upsertSignal(SignalEntity.fromDomain(updated))
            if (settings.pushAlertsEnabled && settings.notifyOnTargetHits) {
                notificationManager.sendStatusUpdateNotification(updated, newStatus.label)
                signalDao.insertAlert(
                    PushAlertEntity.fromDomain(
                        PushAlertRecord(
                            id = "alert_${updated.id}_${newStatus.name}",
                            signalId = updated.id,
                            pairSymbol = updated.pairSymbol,
                            title = "${updated.pairSymbol} ${updated.direction.label} • ${newStatus.label}",
                            body = "Target reached at ${pair.formatPrice(livePrice)} (${String.format(Locale.US, "%+.1f", livePips)} pips)",
                            direction = updated.direction,
                            entryPrice = updated.entryPrice,
                            stopLoss = updated.stopLoss,
                            tp1 = updated.tp1,
                            timestampMillis = System.currentTimeMillis(),
                            isRead = false
                        )
                    )
                )
            }
        }
    }

    private suspend fun persistAndPushSignal(signal: TradingSignal, forcePush: Boolean) {
        alertedSignalIds.add(signal.id)
        signalDao.upsertSignal(SignalEntity.fromDomain(signal))
        val pair = MarketPairsCatalog.findBySymbol(signal.pairSymbol)
        val alert = PushAlertRecord(
            id = "alert_${signal.id}",
            signalId = signal.id,
            pairSymbol = signal.pairSymbol,
            title = "${signal.direction.label} ${signal.pairSymbol} (${signal.timeframeEntry.label}) • ${signal.tvBuyCount}/26 TV",
            body = "Entry ${pair.formatPrice(signal.entryPrice)} | SL ${pair.formatPrice(signal.stopLoss)} | TP1 ${pair.formatPrice(signal.tp1)} • ${signal.smcSetupSummary}",
            direction = signal.direction,
            entryPrice = signal.entryPrice,
            stopLoss = signal.stopLoss,
            tp1 = signal.tp1,
            timestampMillis = System.currentTimeMillis(),
            isRead = false
        )
        signalDao.insertAlert(PushAlertEntity.fromDomain(alert))
        if (forcePush) {
            notificationManager.sendSignalNotification(signal)
        }
    }

    private fun computePerformanceMetrics(signals: List<TradingSignal>): PerformanceMetrics {
        val active = signals.count { it.status == SignalStatus.ACTIVE }
        val closed = signals.filter { it.status != SignalStatus.ACTIVE }
        val winsList = closed.filter {
            it.status == SignalStatus.TP1_HIT || it.status == SignalStatus.TP2_HIT || it.status == SignalStatus.TP3_HIT
        }
        val lossList = closed.filter { it.status == SignalStatus.SL_HIT }
        val winRate = if (closed.isNotEmpty()) (winsList.size.toDouble() / closed.size) * 100.0 else 0.0

        val grossGainPips = winsList.sumOf { kotlin.math.abs(it.outcomePips) }
        val grossLossPips = lossList.sumOf { kotlin.math.abs(it.outcomePips) }.coerceAtLeast(1.0)
        val profitFactor = if (closed.isNotEmpty()) grossGainPips / grossLossPips else 0.0
        val avgRr = if (signals.isNotEmpty()) signals.map { it.riskReward }.average() else 2.5
        val netPips = closed.sumOf { it.outcomePips }
        val netRoi = closed.sumOf { it.outcomePercent }

        val forexClosed = closed.filter { it.marketType == MarketType.FOREX }
        val forexWins = forexClosed.count { it.status != SignalStatus.SL_HIT }
        val forexWr = if (forexClosed.isNotEmpty()) (forexWins.toDouble() / forexClosed.size) * 100.0 else 0.0

        val cryptoClosed = closed.filter { it.marketType == MarketType.CRYPTO }
        val cryptoWins = cryptoClosed.count { it.status != SignalStatus.SL_HIT }
        val cryptoWr = if (cryptoClosed.isNotEmpty()) (cryptoWins.toDouble() / cryptoClosed.size) * 100.0 else 0.0

        val bestPair = closed.groupBy { it.pairSymbol }
            .maxByOrNull { (_, list) -> list.sumOf { it.outcomePips } }?.key ?: "BTC/USDT"

        val tfBreakdown = listOf(Timeframe.M5, Timeframe.M15, Timeframe.H1, Timeframe.H4).map { tf ->
            val tfSignals = closed.filter { it.timeframeEntry == tf }
            val tfWins = tfSignals.count { it.status != SignalStatus.SL_HIT }
            val wr = if (tfSignals.isNotEmpty()) (tfWins.toDouble() / tfSignals.size) * 100.0 else 0.0
            TimeframePerformance(tf, tfSignals.size, tfWins, wr)
        }

        val sortedClosed = closed.sortedBy { it.createdAtMillis }
        var cumPips = 0.0
        var cumR = 0.0
        var cumPct = 0.0
        var rollingWins = 0
        val curve = ArrayList<EquityCurvePoint>(sortedClosed.size + 1)
        curve.add(EquityCurvePoint(0, "Start", 0.0, 0.0, 0.0, 0.0, 0.0, 0.0))
        sortedClosed.forEachIndexed { index, sig ->
            cumPips += sig.outcomePips
            cumPct += sig.outcomePercent
            val rDelta = when (sig.status) {
                SignalStatus.TP3_HIT -> 3.8
                SignalStatus.TP2_HIT -> 2.5
                SignalStatus.TP1_HIT -> 1.5
                SignalStatus.SL_HIT -> -1.0
                SignalStatus.ACTIVE -> 0.0
            }
            if (sig.status != SignalStatus.SL_HIT) rollingWins++
            val rollingWr = (rollingWins.toDouble() / (index + 1)) * 100.0
            cumR += rDelta
            curve.add(
                EquityCurvePoint(
                    index = index + 1,
                    label = sig.pairSymbol.substringBefore("/"),
                    cumulativePips = cumPips,
                    cumulativeR = cumR,
                    cumulativePercent = cumPct,
                    tradeR = rDelta,
                    tradePips = sig.outcomePips,
                    rollingWinRatePct = rollingWr
                )
            )
        }

        val tp1Count = signals.count { it.status == SignalStatus.TP1_HIT }
        val tp2Count = signals.count { it.status == SignalStatus.TP2_HIT }
        val tp3Count = signals.count { it.status == SignalStatus.TP3_HIT }
        val slCount = signals.count { it.status == SignalStatus.SL_HIT }

        return PerformanceMetrics(
            totalSignals = signals.size,
            activeSignals = active,
            closedSignals = closed.size,
            wins = winsList.size,
            losses = lossList.size,
            tp1Hits = tp1Count,
            tp2Hits = tp2Count,
            tp3SuccessDone = tp3Count,
            slHits = slCount,
            winRatePct = winRate,
            profitFactor = profitFactor,
            avgRiskReward = avgRr,
            netPips = netPips,
            netRoiPct = netRoi,
            bestPair = bestPair,
            forexWinRatePct = forexWr,
            cryptoWinRatePct = cryptoWr,
            timeframeBreakdown = tfBreakdown,
            equityCurve = curve
        )
    }
}
