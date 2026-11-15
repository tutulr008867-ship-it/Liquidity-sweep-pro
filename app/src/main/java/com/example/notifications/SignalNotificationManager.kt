package com.example.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import com.example.data.remote.MarketPairsCatalog
import com.example.domain.model.SignalDirection
import com.example.domain.model.TradingSignal
import java.util.Locale

class SignalNotificationManager(private val context: Context) {

    companion object {
        const val CHANNEL_ID = "sweep_pulse_signals_v1"
        const val EXTRA_PAIR_SYMBOL = "extra_pair_symbol"
    }

    init {
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.notification_channel_signals_name),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = context.getString(R.string.notification_channel_signals_desc)
                enableVibration(true)
                enableLights(true)
            }
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    fun hasNotificationPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    fun sendSignalNotification(signal: TradingSignal): Boolean {
        if (!hasNotificationPermission()) return false

        val pair = MarketPairsCatalog.findBySymbol(signal.pairSymbol)
        val dirEmoji = if (signal.direction == SignalDirection.BUY) "BUY" else "SELL"
        val title = "$dirEmoji ${signal.pairSymbol} (${signal.timeframeEntry.label}) • ${signal.tvBuyCount}/26 TV"
        val body = buildString {
            append("Entry ${pair.formatPrice(signal.entryPrice)} | ")
            append("SL ${pair.formatPrice(signal.stopLoss)} | ")
            append("TP1 ${pair.formatPrice(signal.tp1)} | ")
            append("TP2 ${pair.formatPrice(signal.tp2)} ")
            append("(RR 1:${String.format(Locale.US, "%.1f", signal.riskReward)})\n")
            append(signal.smcSetupSummary)
        }

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_PAIR_SYMBOL, signal.pairSymbol)
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            signal.id.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText("Entry ${pair.formatPrice(signal.entryPrice)} • SL ${pair.formatPrice(signal.stopLoss)} • TP1 ${pair.formatPrice(signal.tp1)}")
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        return try {
            NotificationManagerCompat.from(context).notify(signal.id.hashCode(), notification)
            true
        } catch (_: SecurityException) {
            false
        }
    }

    fun sendStatusUpdateNotification(signal: TradingSignal, statusLabel: String): Boolean {
        if (!hasNotificationPermission()) return false
        val pair = MarketPairsCatalog.findBySymbol(signal.pairSymbol)
        val title = "${signal.pairSymbol} ${signal.direction.label} • $statusLabel"
        val pipsStr = String.format(Locale.US, "%+.1f pips", signal.outcomePips)
        val body = "Entry ${pair.formatPrice(signal.entryPrice)} • Outcome: $pipsStr (${signal.timeframeEntry.label})"

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_PAIR_SYMBOL, signal.pairSymbol)
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            (signal.id + statusLabel).hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        return try {
            NotificationManagerCompat.from(context).notify((signal.id + statusLabel).hashCode(), notification)
            true
        } catch (_: SecurityException) {
            false
        }
    }
}
