package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.domain.model.ConsensusRating
import com.example.domain.model.MarketBias
import com.example.domain.model.MarketType
import com.example.domain.model.SignalDirection
import com.example.domain.model.SignalStatus
import com.example.domain.model.SmcSweepType
import com.example.domain.model.Timeframe
import com.example.domain.model.TradingSignal

@Entity(tableName = "trading_signals")
data class SignalEntity(
    @PrimaryKey val id: String,
    val pairSymbol: String,
    val displayName: String,
    val marketType: String,
    val direction: String,
    val entryPrice: Double,
    val stopLoss: Double,
    val tp1: Double,
    val tp2: Double,
    val tp3: Double,
    val riskReward: Double,
    val timeframeEntry: String,
    val confidenceScore: Int,
    val smcSetupSummary: String,
    val sweepType: String,
    val sweepPrice: Double,
    val htfBias4H: String,
    val entry5mSummary: String,
    val entry15mSummary: String,
    val tvBuyCount: Int,
    val tvNeutralCount: Int,
    val tvSellCount: Int,
    val tvRating: String,
    val rvolAtTrigger: Double,
    val status: String,
    val outcomePips: Double,
    val outcomePercent: Double,
    val createdAtMillis: Long,
    val updatedAtMillis: Long
) {
    fun toDomain(): TradingSignal = TradingSignal(
        id = id,
        pairSymbol = pairSymbol,
        displayName = displayName,
        marketType = MarketType.valueOf(marketType),
        direction = SignalDirection.valueOf(direction),
        entryPrice = entryPrice,
        stopLoss = stopLoss,
        tp1 = tp1,
        tp2 = tp2,
        tp3 = tp3,
        riskReward = riskReward,
        timeframeEntry = Timeframe.valueOf(timeframeEntry),
        confidenceScore = confidenceScore,
        smcSetupSummary = smcSetupSummary,
        sweepType = SmcSweepType.valueOf(sweepType),
        sweepPrice = sweepPrice,
        htfBias4H = MarketBias.valueOf(htfBias4H),
        entry5mSummary = entry5mSummary,
        entry15mSummary = entry15mSummary,
        tvBuyCount = tvBuyCount,
        tvNeutralCount = tvNeutralCount,
        tvSellCount = tvSellCount,
        tvRating = ConsensusRating.valueOf(tvRating),
        rvolAtTrigger = rvolAtTrigger,
        status = SignalStatus.valueOf(status),
        outcomePips = outcomePips,
        outcomePercent = outcomePercent,
        createdAtMillis = createdAtMillis,
        updatedAtMillis = updatedAtMillis
    )

    companion object {
        fun fromDomain(s: TradingSignal): SignalEntity = SignalEntity(
            id = s.id,
            pairSymbol = s.pairSymbol,
            displayName = s.displayName,
            marketType = s.marketType.name,
            direction = s.direction.name,
            entryPrice = s.entryPrice,
            stopLoss = s.stopLoss,
            tp1 = s.tp1,
            tp2 = s.tp2,
            tp3 = s.tp3,
            riskReward = s.riskReward,
            timeframeEntry = s.timeframeEntry.name,
            confidenceScore = s.confidenceScore,
            smcSetupSummary = s.smcSetupSummary,
            sweepType = s.sweepType.name,
            sweepPrice = s.sweepPrice,
            htfBias4H = s.htfBias4H.name,
            entry5mSummary = s.entry5mSummary,
            entry15mSummary = s.entry15mSummary,
            tvBuyCount = s.tvBuyCount,
            tvNeutralCount = s.tvNeutralCount,
            tvSellCount = s.tvSellCount,
            tvRating = s.tvRating.name,
            rvolAtTrigger = s.rvolAtTrigger,
            status = s.status.name,
            outcomePips = s.outcomePips,
            outcomePercent = s.outcomePercent,
            createdAtMillis = s.createdAtMillis,
            updatedAtMillis = s.updatedAtMillis
        )
    }
}
