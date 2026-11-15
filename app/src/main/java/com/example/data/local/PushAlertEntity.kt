package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.domain.model.PushAlertRecord
import com.example.domain.model.SignalDirection

@Entity(tableName = "push_alerts")
data class PushAlertEntity(
    @PrimaryKey val id: String,
    val signalId: String,
    val pairSymbol: String,
    val title: String,
    val body: String,
    val direction: String,
    val entryPrice: Double,
    val stopLoss: Double,
    val tp1: Double,
    val timestampMillis: Long,
    val isRead: Boolean
) {
    fun toDomain(): PushAlertRecord = PushAlertRecord(
        id = id,
        signalId = signalId,
        pairSymbol = pairSymbol,
        title = title,
        body = body,
        direction = SignalDirection.valueOf(direction),
        entryPrice = entryPrice,
        stopLoss = stopLoss,
        tp1 = tp1,
        timestampMillis = timestampMillis,
        isRead = isRead
    )

    companion object {
        fun fromDomain(a: PushAlertRecord): PushAlertEntity = PushAlertEntity(
            id = a.id,
            signalId = a.signalId,
            pairSymbol = a.pairSymbol,
            title = a.title,
            body = a.body,
            direction = a.direction.name,
            entryPrice = a.entryPrice,
            stopLoss = a.stopLoss,
            tp1 = a.tp1,
            timestampMillis = a.timestampMillis,
            isRead = a.isRead
        )
    }
}
