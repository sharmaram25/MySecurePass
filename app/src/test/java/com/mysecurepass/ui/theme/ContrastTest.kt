package com.mysecurepass.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * WCAG 2.1 contrast checks for every text/background pairing the UI uses.
 * Body text must reach 4.5:1 (AA). Fails the build if a palette change makes any text hard to read.
 */
class ContrastTest {

    private fun channel(c: Float): Double = if (c <= 0.03928) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
    private fun luminance(c: Color) = 0.2126 * channel(c.red) + 0.7152 * channel(c.green) + 0.0722 * channel(c.blue)
    private fun ratio(a: Color, b: Color): Double {
        val l1 = luminance(a); val l2 = luminance(b)
        return (max(l1, l2) + 0.05) / (min(l1, l2) + 0.05)
    }

    private fun pairs(s: ColorScheme, e: ExtraColors): List<Triple<String, Color, Color>> {
        val backgrounds = listOf("background" to s.background, "surfaceContainer" to s.surfaceContainer, "surfaceContainerHigh" to s.surfaceContainerHigh)
        val texts = listOf(
            "onBackground" to s.onBackground, "onSurface" to s.onSurface, "onSurfaceVariant" to s.onSurfaceVariant,
            "primary" to s.primary, "error" to s.error,
            "digit" to e.digit, "symbol" to e.symbol, "veryWeak" to e.veryWeak, "weak" to e.weak,
            "fair" to e.fair, "strong" to e.strong, "excellent" to e.excellent,
        )
        return texts.flatMap { (tn, t) -> backgrounds.map { (bn, b) -> Triple("$tn on $bn", t, b) } } + listOf(
            Triple("onPrimary on primary", s.onPrimary, s.primary),
            Triple("onPrimaryContainer on primaryContainer", s.onPrimaryContainer, s.primaryContainer),
            Triple("onError on error", s.onError, s.error),
            Triple("surface on onSurface (filled buttons, selected chips)", s.surface, s.onSurface),
            Triple("inverseOnSurface on inverseSurface (snackbar)", s.inverseOnSurface, s.inverseSurface),
            Triple("inversePrimary on inverseSurface (snackbar action)", s.inversePrimary, s.inverseSurface),
        )
    }

    private fun check(name: String, s: ColorScheme, e: ExtraColors) {
        val failures = pairs(s, e).mapNotNull { (label, fg, bg) ->
            val r = ratio(fg, bg)
            if (r < 4.5) "$name: $label = ${"%.2f".format(r)}:1" else null
        }
        assertTrue("Contrast below WCAG AA (4.5:1):\n" + failures.joinToString("\n"), failures.isEmpty())
    }

    @Test fun darkThemeMeetsWcagAA() = check("dark", Dark, DarkExtra)
    @Test fun lightThemeMeetsWcagAA() = check("light", Light, LightExtra)
}
