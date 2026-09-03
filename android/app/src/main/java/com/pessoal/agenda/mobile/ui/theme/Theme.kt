package com.pessoal.agenda.mobile.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat

private val LightColors = lightColorScheme(
    primary = Color(0xFF1D6554),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFA9F2D9),
    onPrimaryContainer = Color(0xFF002019),
    secondary = Color(0xFF4D635B),
    secondaryContainer = Color(0xFFD0E8DD),
    onSecondaryContainer = Color(0xFF0A1F18),
    tertiary = Color(0xFF765A00),
    tertiaryContainer = Color(0xFFFFE08A),
    surface = Color(0xFFFBFDF9),
    surfaceVariant = Color(0xFFDBE5DF),
    background = Color(0xFFFBFDF9),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF8CD6BE),
    onPrimary = Color(0xFF00382C),
    primaryContainer = Color(0xFF005140),
    onPrimaryContainer = Color(0xFFA9F2D9),
    secondary = Color(0xFFB4CCC0),
    secondaryContainer = Color(0xFF354B43),
    onSecondaryContainer = Color(0xFFD0E8DD),
    tertiary = Color(0xFFF1C84B),
    tertiaryContainer = Color(0xFF594400),
    surface = Color(0xFF101412),
    surfaceVariant = Color(0xFF404944),
    background = Color(0xFF101412),
)

private val AppShapes = Shapes(
    small = androidx.compose.foundation.shape.RoundedCornerShape(6.dp),
    medium = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
    large = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
)

@Composable
fun AgendaMobileTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val colors = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColors
        else -> LightColors
    }
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            (view.context as? Activity)?.window?.let { window ->
                WindowCompat.getInsetsController(window, view).apply {
                    isAppearanceLightStatusBars = !darkTheme
                    isAppearanceLightNavigationBars = !darkTheme
                }
            }
        }
    }
    MaterialTheme(
        colorScheme = colors,
        shapes = AppShapes,
        content = content,
    )
}
