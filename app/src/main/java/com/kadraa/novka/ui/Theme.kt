package com.kadraa.novka.ui

import android.os.Build
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

// Material 3 tonal scheme for the teal seed, used below Android 12 where there is no dynamic colour.
private val lightScheme = lightColorScheme(
    primary = Color(0xFF006A60), onPrimary = Color(0xFFFFFFFF), primaryContainer = Color(0xFF9EF2E4), onPrimaryContainer = Color(0xFF005048),
    secondary = Color(0xFF4A635F), onSecondary = Color(0xFFFFFFFF), secondaryContainer = Color(0xFFCCE8E2), onSecondaryContainer = Color(0xFF334B47),
    tertiary = Color(0xFF456179), onTertiary = Color(0xFFFFFFFF), tertiaryContainer = Color(0xFFCCE5FF), onTertiaryContainer = Color(0xFF2D4961),
    error = Color(0xFFBA1A1A), onError = Color(0xFFFFFFFF), errorContainer = Color(0xFFFFDAD6), onErrorContainer = Color(0xFF93000A),
    background = Color(0xFFF4FBF8), onBackground = Color(0xFF161D1C), surface = Color(0xFFF4FBF8), onSurface = Color(0xFF161D1C),
    surfaceVariant = Color(0xFFDAE5E1), onSurfaceVariant = Color(0xFF3F4947), outline = Color(0xFF6F7977), outlineVariant = Color(0xFFBEC9C6),
    scrim = Color(0xFF000000), inverseSurface = Color(0xFF2B3230), inverseOnSurface = Color(0xFFECF2EF), inversePrimary = Color(0xFF82D5C8),
    surfaceDim = Color(0xFFD5DBD9), surfaceBright = Color(0xFFF4FBF8), surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFEFF5F2), surfaceContainer = Color(0xFFE9EFEC), surfaceContainerHigh = Color(0xFFE3EAE7),
    surfaceContainerHighest = Color(0xFFDDE4E1),
)

private val darkScheme = darkColorScheme(
    primary = Color(0xFF82D5C8), onPrimary = Color(0xFF003731), primaryContainer = Color(0xFF005048), onPrimaryContainer = Color(0xFF9EF2E4),
    secondary = Color(0xFFB1CCC6), onSecondary = Color(0xFF1C3531), secondaryContainer = Color(0xFF334B47), onSecondaryContainer = Color(0xFFCCE8E2),
    tertiary = Color(0xFFADCAE6), onTertiary = Color(0xFF153349), tertiaryContainer = Color(0xFF2D4961), onTertiaryContainer = Color(0xFFCCE5FF),
    error = Color(0xFFFFB4AB), onError = Color(0xFF690005), errorContainer = Color(0xFF93000A), onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF0E1513), onBackground = Color(0xFFDDE4E1), surface = Color(0xFF0E1513), onSurface = Color(0xFFDDE4E1),
    surfaceVariant = Color(0xFF3F4947), onSurfaceVariant = Color(0xFFBEC9C6), outline = Color(0xFF899390), outlineVariant = Color(0xFF3F4947),
    scrim = Color(0xFF000000), inverseSurface = Color(0xFFDDE4E1), inverseOnSurface = Color(0xFF2B3230), inversePrimary = Color(0xFF006A60),
    surfaceDim = Color(0xFF0E1513), surfaceBright = Color(0xFF343B39), surfaceContainerLowest = Color(0xFF090F0E),
    surfaceContainerLow = Color(0xFF161D1C), surfaceContainer = Color(0xFF1A2120), surfaceContainerHigh = Color(0xFF252B2A),
    surfaceContainerHighest = Color(0xFF303635),
)

@Composable
fun NovkaTheme(theme: String, content: @Composable () -> Unit) {
    val dark = when (theme) {
        "light" -> false
        "dark" -> true
        else -> isSystemInDarkTheme()
    }
    val context = LocalContext.current
    val scheme = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> darkScheme
        else -> lightScheme
    }
    MaterialTheme(colorScheme = scheme, content = content)
}

/**
 * Material 3 easing with durations from the short end of the scale, so nothing on screen waits on an animation.
 * Use these instead of bare `tween()` numbers.
 */
object Motion {
    const val QUICK = 100
    const val SHORT = 150
    const val MEDIUM = 200

    val EmphasizedDecelerate = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
    val EmphasizedAccelerate = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)
}

/** Chart series colours, taken from the colour scheme so they follow light, dark and dynamic colour. */
data class ChartColors(val earned: Color, val spent: Color, val line: Color, val grid: Color, val muted: Color, val other: Color)

@Composable
@ReadOnlyComposable
fun chartColors(): ChartColors {
    val scheme = MaterialTheme.colorScheme
    return ChartColors(
        earned = scheme.primary, spent = scheme.tertiary, line = scheme.primary,
        grid = scheme.outlineVariant, muted = scheme.onSurfaceVariant, other = scheme.outline,
    )
}
