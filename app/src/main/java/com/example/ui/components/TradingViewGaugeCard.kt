package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.model.ConsensusRating
import com.example.domain.model.IndicatorResult
import com.example.domain.model.IndicatorVote
import com.example.domain.model.TradingViewConsensus
import com.example.ui.theme.LocalTradingColors
import com.example.ui.theme.MonoNumericStyle
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun TradingViewGaugeCard(
    consensus: TradingViewConsensus,
    timeframeLabel: String,
    modifier: Modifier = Modifier
) {
    val tradingColors = LocalTradingColors.current
    var expanded by remember { mutableStateOf(false) }

    val ratingColor = when (consensus.overallRating) {
        ConsensusRating.STRONG_BUY, ConsensusRating.BUY -> tradingColors.buy
        ConsensusRating.STRONG_SELL, ConsensusRating.SELL -> tradingColors.sell
        ConsensusRating.NEUTRAL -> tradingColors.sweep
    }

    val animatedScore by animateFloatAsState(
        targetValue = consensus.scoreNormalized,
        animationSpec = spring(stiffness = 280f),
        label = "tv_gauge_needle"
    )

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "26 TradingView Indicators",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Live 1s consensus on $timeframeLabel (11 Osc + 15 MA)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(ratingColor.copy(alpha = 0.16f))
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    Text(
                        text = consensus.overallRating.label.uppercase(),
                        style = MaterialTheme.typography.labelLarge,
                        color = ratingColor
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Semi-Circular Gauge
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(118.dp),
                contentAlignment = Alignment.BottomCenter
            ) {
                Canvas(
                    modifier = Modifier
                        .width(220.dp)
                        .height(110.dp)
                ) {
                    val strokeW = 16.dp.toPx()
                    val arcSize = Size(size.width - strokeW, (size.width - strokeW))
                    val topLeft = Offset(strokeW / 2f, strokeW / 2f)

                    val segmentColors = listOf(
                        tradingColors.sell,
                        tradingColors.sell.copy(alpha = 0.65f),
                        tradingColors.sweep,
                        tradingColors.buy.copy(alpha = 0.65f),
                        tradingColors.buy
                    )
                    val sweepPerSeg = 32f
                    val gap = 4f
                    segmentColors.forEachIndexed { idx, col ->
                        val startAngle = 182f + idx * (sweepPerSeg + gap)
                        drawArc(
                            color = col,
                            startAngle = startAngle,
                            sweepAngle = sweepPerSeg,
                            useCenter = false,
                            topLeft = topLeft,
                            size = arcSize,
                            style = Stroke(width = strokeW, cap = StrokeCap.Butt)
                        )
                    }

                    // Needle pointing from -1 (180 deg) to +1 (0 deg)
                    val center = Offset(size.width / 2f, size.height - 6.dp.toPx())
                    val radius = (size.width / 2f) - strokeW * 1.15f
                    val angleRad = ((180.0 - ((animatedScore + 1f) * 90.0)) * PI / 180.0)
                    val tip = Offset(
                        x = center.x + (radius * cos(angleRad)).toFloat(),
                        y = center.y - (radius * sin(angleRad)).toFloat()
                    )
                    drawLine(
                        color = ratingColor,
                        start = center,
                        end = tip,
                        strokeWidth = 3.5.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                    drawCircle(
                        color = ratingColor,
                        radius = 6.dp.toPx(),
                        center = center
                    )
                }

                Text(
                    text = "${consensus.buyCount} Buy • ${consensus.neutralCount} Neu • ${consensus.sellCount} Sell",
                    style = MonoNumericStyle.copy(fontSize = 12.sp, fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Sub-Meters: Oscillators (11) & Moving Averages (15)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                SubConsensusBox(
                    title = "Oscillators (11)",
                    rating = consensus.oscillatorRating,
                    buy = consensus.oscillatorBuy,
                    neutral = consensus.oscillatorNeutral,
                    sell = consensus.oscillatorSell,
                    modifier = Modifier.weight(1f)
                )
                SubConsensusBox(
                    title = "Moving Avgs (15)",
                    rating = consensus.maRating,
                    buy = consensus.maBuy,
                    neutral = consensus.maNeutral,
                    sell = consensus.maSell,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Expandable 26-Indicator Breakdown
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .clickable { expanded = !expanded }
                    .padding(horizontal = 12.dp, vertical = 10.dp)
                    .testTag("toggle_26_indicators_btn"),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (expanded) "Hide All 26 Indicator Values" else "Inspect All 26 Indicators (11 Osc + 15 MA)",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                Icon(
                    imageVector = if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                    contentDescription = "Toggle 26 indicators list",
                    tint = MaterialTheme.colorScheme.primary
                )
            }

            AnimatedVisibility(visible = expanded) {
                Column(modifier = Modifier.padding(top = 10.dp)) {
                    Text(
                        text = "OSCILLATORS (11)",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                    consensus.oscillators.forEach { ind ->
                        IndicatorRow(ind)
                    }
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 8.dp),
                        color = MaterialTheme.colorScheme.outlineVariant
                    )
                    Text(
                        text = "MOVING AVERAGES (15)",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                    consensus.movingAverages.forEach { ind ->
                        IndicatorRow(ind)
                    }
                }
            }
        }
    }
}

@Composable
private fun SubConsensusBox(
    title: String,
    rating: ConsensusRating,
    buy: Int,
    neutral: Int,
    sell: Int,
    modifier: Modifier = Modifier
) {
    val tradingColors = LocalTradingColors.current
    val rCol = when (rating) {
        ConsensusRating.STRONG_BUY, ConsensusRating.BUY -> tradingColors.buy
        ConsensusRating.STRONG_SELL, ConsensusRating.SELL -> tradingColors.sell
        ConsensusRating.NEUTRAL -> tradingColors.sweep
    }
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(10.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = rating.label,
            style = MaterialTheme.typography.titleSmall,
            color = rCol
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "B:$buy  N:$neutral  S:$sell",
            style = MonoNumericStyle.copy(fontSize = 11.sp),
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun IndicatorRow(ind: IndicatorResult) {
    val tradingColors = LocalTradingColors.current
    val (voteLabel, voteColor) = when (ind.vote) {
        IndicatorVote.BUY -> "BUY" to tradingColors.buy
        IndicatorVote.SELL -> "SELL" to tradingColors.sell
        IndicatorVote.NEUTRAL -> "NEU" to tradingColors.sweep
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = ind.name,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = ind.valueFormatted,
            style = MonoNumericStyle.copy(fontSize = 12.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(end = 10.dp)
        )
        Box(
            modifier = Modifier
                .width(50.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(voteColor.copy(alpha = 0.16f))
                .padding(vertical = 3.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = voteLabel,
                style = MonoNumericStyle.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold),
                color = voteColor
            )
        }
    }
}
