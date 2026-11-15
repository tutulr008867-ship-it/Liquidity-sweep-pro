package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.domain.model.ThemeMode

data class TradingColors(
    val buy: Color,
    val buyContainer: Color,
    val sell: Color,
    val sellContainer: Color,
    val sweep: Color,
    val sweepContainer: Color,
    val pulse: Color,
    val pulseContainer: Color,
    val ema9: Color,
    val ema21: Color,
    val ema50: Color,
    val sma200: Color,
    val chartGrid: Color,
    val elevatedCard: Color,
    val isDark: Boolean
)

val LocalTradingColors = staticCompositionLocalOf {
    DarkTradingColors
}

private val DarkTradingColors = TradingColors(
    buy = EmeraldBuy,
    buyContainer = EmeraldContainerDark,
    sell = CrimsonSell,
    sellContainer = CrimsonContainerDark,
    sweep = AmberSweep,
    sweepContainer = AmberContainerDark,
    pulse = CyanPulse,
    pulseContainer = CyanContainerDark,
    ema9 = CyanPulse,
    ema21 = AmberSweep,
    ema50 = VioletEma,
    sma200 = Color(0xFFEC4899),
    chartGrid = Color(0xFF1A2436),
    elevatedCard = TerminalElevated,
    isDark = true
)

private val LightTradingColors = TradingColors(
    buy = EmeraldBuyDark,
    buyContainer = EmeraldContainerLight,
    sell = CrimsonSellDark,
    sellContainer = CrimsonContainerLight,
    sweep = Color(0xFFD97706),
    sweepContainer = AmberContainerLight,
    pulse = CyanPulseDark,
    pulseContainer = CyanContainerLight,
    ema9 = CyanPulseDark,
    ema21 = Color(0xFFD97706),
    ema50 = Color(0xFF7E22CE),
    sma200 = Color(0xFFDB2777),
    chartGrid = Color(0xFFE2E8F0),
    elevatedCard = Color(0xFFF8FAFC),
    isDark = false
)

private val DarkColorScheme = darkColorScheme(
    primary = CyanPulse,
    onPrimary = Color(0xFF001F29),
    primaryContainer = CyanContainerDark,
    onPrimaryContainer = CyanPulse,
    secondary = EmeraldBuy,
    onSecondary = Color(0xFF00210E),
    secondaryContainer = EmeraldContainerDark,
    onSecondaryContainer = EmeraldBuy,
    tertiary = AmberSweep,
    onTertiary = Color(0xFF281900),
    tertiaryContainer = AmberContainerDark,
    onTertiaryContainer = AmberSweep,
    error = CrimsonSell,
    onError = Color.White,
    errorContainer = CrimsonContainerDark,
    onErrorContainer = CrimsonSell,
    background = ObsidianBg,
    onBackground = TextPrimaryDark,
    surface = TerminalSurface,
    onSurface = TextPrimaryDark,
    surfaceVariant = TerminalSurfaceVariant,
    onSurfaceVariant = TextSecondaryDark,
    outline = TerminalOutline,
    outlineVariant = Color(0xFF1E293B)
)

private val LightColorScheme = lightColorScheme(
    primary = CyanPulseDark,
    onPrimary = Color.White,
    primaryContainer = CyanContainerLight,
    onPrimaryContainer = Color(0xFF00344E),
    secondary = EmeraldBuyDark,
    onSecondary = Color.White,
    secondaryContainer = EmeraldContainerLight,
    onSecondaryContainer = Color(0xFF003919),
    tertiary = Color(0xFFD97706),
    onTertiary = Color.White,
    tertiaryContainer = AmberContainerLight,
    onTertiaryContainer = Color(0xFF451A03),
    error = CrimsonSellDark,
    onError = Color.White,
    errorContainer = CrimsonContainerLight,
    onErrorContainer = Color(0xFF5F0010),
    background = LightBg,
    onBackground = TextPrimaryLight,
    surface = LightSurface,
    onSurface = TextPrimaryLight,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = TextSecondaryLight,
    outline = LightOutline,
    outlineVariant = Color(0xFFE2E8F0)
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(18.dp),
    extraLarge = RoundedCornerShape(24.dp)
)

@Composable
fun SweepPulseTheme(
    themeMode: ThemeMode = ThemeMode.DARK,
    content: @Composable () -> Unit
) {
    val useDark = when (themeMode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }

    val colorScheme = if (useDark) DarkColorScheme else LightColorScheme
    val tradingColors = if (useDark) DarkTradingColors else LightTradingColors

    CompositionLocalProvider(LocalTradingColors provides tradingColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = SweepPulseTypography,
            shapes = AppShapes,
            content = content
        )
    }
}
