package com.mysecurepass.ui.theme

import android.os.Build
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mysecurepass.R

/** Colours that Material's scheme doesn't model. */
@Immutable
data class ExtraColors(
    val hairline: Color,
    val digit: Color,
    val symbol: Color,
    val veryWeak: Color,
    val weak: Color,
    val fair: Color,
    val strong: Color,
    val excellent: Color,
)

internal val DarkExtra = ExtraColors(
    hairline = Color(0xFF232328),
    digit = Color(0xFF8AB4FF),
    symbol = Color(0xFFFFB37A),
    veryWeak = Color(0xFFFF6B6B),
    weak = Color(0xFFFF9F5A),
    fair = Color(0xFFF2CC60),
    strong = Color(0xFF7BE0B0),
    excellent = Color(0xFF4FD69C),
)

internal val LightExtra = ExtraColors(
    hairline = Color(0xFFE6E6EA),
    digit = Color(0xFF2254BA),
    symbol = Color(0xFFA3410C),
    veryWeak = Color(0xFFBF2A2A),
    weak = Color(0xFFA14B0A),
    fair = Color(0xFF856000),
    strong = Color(0xFF0A7048),
    excellent = Color(0xFF07603D),
)

internal val Dark = darkColorScheme(
    primary = Color(0xFF7BE0B0),
    onPrimary = Color(0xFF04150D),
    primaryContainer = Color(0xFF173D2C),
    onPrimaryContainer = Color(0xFFBDF5D9),
    secondary = Color(0xFFB9B9C2),
    onSecondary = Color(0xFF111114),
    background = Color(0xFF0B0B0D),
    onBackground = Color(0xFFF3F3F5),
    surface = Color(0xFF0B0B0D),
    onSurface = Color(0xFFF3F3F5),
    surfaceVariant = Color(0xFF17171B),
    onSurfaceVariant = Color(0xFF9A9AA3),
    surfaceContainerLowest = Color(0xFF0B0B0D),
    surfaceContainerLow = Color(0xFF111114),
    surfaceContainer = Color(0xFF141418),
    surfaceContainerHigh = Color(0xFF1A1A1F),
    surfaceContainerHighest = Color(0xFF222228),
    outline = Color(0xFF2E2E35),
    outlineVariant = Color(0xFF232328),
    error = Color(0xFFFF6B6B),
    onError = Color(0xFF1A0505),
    inverseSurface = Color(0xFFF3F3F5),
    inverseOnSurface = Color(0xFF111114),
    inversePrimary = Color(0xFF0A7048),
)

internal val Light = lightColorScheme(
    primary = Color(0xFF0A7048),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD5F5E6),
    onPrimaryContainer = Color(0xFF053D26),
    secondary = Color(0xFF52525B),
    onSecondary = Color(0xFFFFFFFF),
    background = Color(0xFFF6F6F7),
    onBackground = Color(0xFF0B0B0D),
    surface = Color(0xFFF6F6F7),
    onSurface = Color(0xFF0B0B0D),
    surfaceVariant = Color(0xFFEDEDF0),
    onSurfaceVariant = Color(0xFF66666F),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFFFFFF),
    surfaceContainer = Color(0xFFFFFFFF),
    surfaceContainerHigh = Color(0xFFF1F1F3),
    surfaceContainerHighest = Color(0xFFE9E9EC),
    outline = Color(0xFFD9D9DE),
    outlineVariant = Color(0xFFE6E6EA),
    error = Color(0xFFBF2A2A),
    onError = Color(0xFFFFFFFF),
    inverseSurface = Color(0xFF18181B),
    inverseOnSurface = Color(0xFFF3F3F5),
    inversePrimary = Color(0xFF7BE0B0),
)

val MonoFamily = FontFamily(
    Font(R.font.jetbrains_mono_medium, FontWeight.Medium),
    Font(R.font.jetbrains_mono_bold, FontWeight.Bold),
)

private val AppTypography = Typography().run {
    copy(
        displaySmall = displaySmall.copy(fontWeight = FontWeight.SemiBold, letterSpacing = (-0.5).sp),
        headlineLarge = headlineLarge.copy(fontWeight = FontWeight.SemiBold, fontSize = 30.sp, letterSpacing = (-0.6).sp),
        headlineSmall = headlineSmall.copy(fontWeight = FontWeight.SemiBold, letterSpacing = (-0.3).sp),
        titleLarge = titleLarge.copy(fontWeight = FontWeight.SemiBold, letterSpacing = (-0.2).sp),
        titleMedium = titleMedium.copy(fontWeight = FontWeight.SemiBold),
        labelLarge = labelLarge.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.1.sp),
        labelSmall = labelSmall.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 1.1.sp),
    )
}

val PasswordStyle = TextStyle(fontFamily = MonoFamily, fontWeight = FontWeight.Medium, fontSize = 26.sp, lineHeight = 36.sp, letterSpacing = 0.5.sp)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

val LocalExtraColors = staticCompositionLocalOf { DarkExtra }

@Composable
fun MspTheme(darkTheme: Boolean, dynamicColor: Boolean, content: @Composable () -> Unit) {
    val context = LocalContext.current
    val scheme: ColorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        darkTheme -> Dark
        else -> Light
    }
    CompositionLocalProvider(LocalExtraColors provides if (darkTheme) DarkExtra else LightExtra) {
        MaterialTheme(colorScheme = scheme, typography = AppTypography, shapes = AppShapes, content = content)
    }
}

object Msp {
    val extra: ExtraColors @Composable get() = LocalExtraColors.current
}
