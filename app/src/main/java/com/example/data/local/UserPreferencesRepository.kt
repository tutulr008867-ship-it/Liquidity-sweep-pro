package com.example.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.domain.model.ThemeMode
import com.example.domain.model.Timeframe
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "sweeppulse_prefs")

data class UserSettings(
    val themeMode: ThemeMode = ThemeMode.DARK,
    val pushAlertsEnabled: Boolean = true,
    val notifyOnTargetHits: Boolean = true,
    val minConsensusCount: Int = 13,
    val minConfidencePercent: Int = 70,
    val enabledAlertTimeframes: Set<Timeframe> = setOf(
        Timeframe.M5,
        Timeframe.M15,
        Timeframe.H1,
        Timeframe.H4
    ),
    val alertForex: Boolean = true,
    val alertCrypto: Boolean = true,
    val selectedPairSymbol: String = "BTC/USDT"
)

class UserPreferencesRepository(private val context: Context) {

    private object Keys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val PUSH_ALERTS_ENABLED = booleanPreferencesKey("push_alerts_enabled")
        val NOTIFY_ON_TARGET_HITS = booleanPreferencesKey("notify_on_target_hits")
        val MIN_CONSENSUS = intPreferencesKey("min_consensus_count")
        val MIN_CONFIDENCE = intPreferencesKey("min_confidence_percent")
        val ENABLED_TIMEFRAMES = stringSetPreferencesKey("enabled_alert_timeframes")
        val ALERT_FOREX = booleanPreferencesKey("alert_forex")
        val ALERT_CRYPTO = booleanPreferencesKey("alert_crypto")
        val SELECTED_PAIR = stringPreferencesKey("selected_pair_symbol")
    }

    val settingsFlow: Flow<UserSettings> = context.dataStore.data.map { prefs ->
        val themeStr = prefs[Keys.THEME_MODE] ?: ThemeMode.DARK.name
        val themeMode = runCatching { ThemeMode.valueOf(themeStr) }.getOrDefault(ThemeMode.DARK)
        val rawTfs = prefs[Keys.ENABLED_TIMEFRAMES]
        val parsedTfs = if (rawTfs == null) {
            setOf(Timeframe.M5, Timeframe.M15, Timeframe.H1, Timeframe.H4)
        } else {
            rawTfs.mapNotNull { name -> runCatching { Timeframe.valueOf(name) }.getOrNull() }.toSet()
        }
        UserSettings(
            themeMode = themeMode,
            pushAlertsEnabled = prefs[Keys.PUSH_ALERTS_ENABLED] ?: true,
            notifyOnTargetHits = prefs[Keys.NOTIFY_ON_TARGET_HITS] ?: true,
            minConsensusCount = prefs[Keys.MIN_CONSENSUS] ?: 13,
            minConfidencePercent = prefs[Keys.MIN_CONFIDENCE] ?: 70,
            enabledAlertTimeframes = parsedTfs,
            alertForex = prefs[Keys.ALERT_FOREX] ?: true,
            alertCrypto = prefs[Keys.ALERT_CRYPTO] ?: true,
            selectedPairSymbol = prefs[Keys.SELECTED_PAIR] ?: "BTC/USDT"
        )
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.dataStore.edit { it[Keys.THEME_MODE] = mode.name }
    }

    suspend fun setPushAlertsEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.PUSH_ALERTS_ENABLED] = enabled }
    }

    suspend fun setNotifyOnTargetHits(enabled: Boolean) {
        context.dataStore.edit { it[Keys.NOTIFY_ON_TARGET_HITS] = enabled }
    }

    suspend fun setMinConsensusCount(count: Int) {
        context.dataStore.edit { it[Keys.MIN_CONSENSUS] = count.coerceIn(10, 24) }
    }

    suspend fun setMinConfidencePercent(percent: Int) {
        context.dataStore.edit { it[Keys.MIN_CONFIDENCE] = percent.coerceIn(55, 95) }
    }

    suspend fun toggleAlertTimeframe(timeframe: Timeframe) {
        context.dataStore.edit { prefs ->
            val current = prefs[Keys.ENABLED_TIMEFRAMES]
                ?: setOf(Timeframe.M5.name, Timeframe.M15.name, Timeframe.H1.name, Timeframe.H4.name)
            val mutable = current.toMutableSet()
            if (mutable.contains(timeframe.name)) {
                mutable.remove(timeframe.name)
            } else {
                mutable.add(timeframe.name)
            }
            prefs[Keys.ENABLED_TIMEFRAMES] = mutable
        }
    }

    suspend fun setEnabledAlertTimeframes(timeframes: Set<Timeframe>) {
        context.dataStore.edit { prefs ->
            prefs[Keys.ENABLED_TIMEFRAMES] = timeframes.map { it.name }.toSet()
        }
    }

    suspend fun setAlertForex(enabled: Boolean) {
        context.dataStore.edit { it[Keys.ALERT_FOREX] = enabled }
    }

    suspend fun setAlertCrypto(enabled: Boolean) {
        context.dataStore.edit { it[Keys.ALERT_CRYPTO] = enabled }
    }

    suspend fun setSelectedPairSymbol(symbol: String) {
        context.dataStore.edit { it[Keys.SELECTED_PAIR] = symbol }
    }
}
