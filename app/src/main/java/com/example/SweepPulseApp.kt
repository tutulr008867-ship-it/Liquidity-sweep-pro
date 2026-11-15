package com.example

import android.app.Application
import com.example.data.local.TradingDatabase
import com.example.data.local.UserPreferencesRepository
import com.example.data.remote.MarketApiService
import com.example.data.repository.TradingRepository
import com.example.notifications.SignalNotificationManager

class SweepPulseApp : Application() {

    lateinit var database: TradingDatabase
        private set

    lateinit var preferencesRepository: UserPreferencesRepository
        private set

    lateinit var notificationManager: SignalNotificationManager
        private set

    lateinit var tradingRepository: TradingRepository
        private set

    override fun onCreate() {
        super.onCreate()
        database = TradingDatabase.getInstance(this)
        preferencesRepository = UserPreferencesRepository(this)
        notificationManager = SignalNotificationManager(this)
        tradingRepository = TradingRepository(
            apiService = MarketApiService(),
            signalDao = database.signalDao(),
            prefsRepo = preferencesRepository,
            notificationManager = notificationManager
        )
        tradingRepository.startRealtimeEngine()
    }
}
