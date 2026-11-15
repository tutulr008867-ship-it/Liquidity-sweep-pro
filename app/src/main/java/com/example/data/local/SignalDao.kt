package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SignalDao {
    @Query("SELECT * FROM trading_signals ORDER BY createdAtMillis DESC")
    fun observeAllSignals(): Flow<List<SignalEntity>>

    @Query("SELECT * FROM trading_signals ORDER BY createdAtMillis DESC")
    suspend fun getAllSignalsOnce(): List<SignalEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSignals(signals: List<SignalEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSignal(signal: SignalEntity)

    @Query("SELECT COUNT(*) FROM trading_signals")
    suspend fun countSignals(): Int

    @Query("SELECT * FROM push_alerts ORDER BY timestampMillis DESC LIMIT 100")
    fun observePushAlerts(): Flow<List<PushAlertEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAlert(alert: PushAlertEntity)

    @Query("UPDATE push_alerts SET isRead = 1")
    suspend fun markAllAlertsRead()

    @Query("DELETE FROM push_alerts")
    suspend fun clearAlerts()
}
