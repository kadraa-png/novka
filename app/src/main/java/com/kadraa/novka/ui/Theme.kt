package com.kadraa.novka.ui

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext

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
        dark -> darkColorScheme(primary = Color(0xFF4DB6AC), secondary = Color(0xFF80CBC4))
        else -> lightColorScheme(primary = Color(0xFF00695C), secondary = Color(0xFF00897B))
    }
    MaterialTheme(colorScheme = scheme, content = content)
}

/** Chart series colours, checked for colour-blind separation on light and dark surfaces. */
data class ChartColors(val earned: Color, val spent: Color, val line: Color, val grid: Color, val muted: Color, val other: Color)

@Composable
@ReadOnlyComposable
fun chartColors(): ChartColors {
    val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    return if (dark) {
        ChartColors(Color(0xFF3987E5), Color(0xFFD95926), Color(0xFF3987E5), Color(0xFF2C2C2A), Color(0xFF898781), Color(0xFF6B6A66))
    } else {
        ChartColors(Color(0xFF2A78D6), Color(0xFFEB6834), Color(0xFF2A78D6), Color(0xFFE1E0D9), Color(0xFF898781), Color(0xFFB5B4AE))
    }
}
