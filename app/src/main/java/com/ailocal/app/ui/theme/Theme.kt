package com.ailocal.app.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

val BrandPrimary = Color(0xFF4F6BFF)
val BrandPrimaryVariant = Color(0xFF3451D1)
val BrandSecondary = Color(0xFF00C9A7)

private val LightColors = lightColorScheme(
    primary = BrandPrimary,
    secondary = BrandSecondary,
    background = Color(0xFFF7F8FC),
    surface = Color(0xFFFFFFFF),
    onPrimary = Color.White,
    onBackground = Color(0xFF1A1C27),
    onSurface = Color(0xFF1A1C27),
    surfaceVariant = Color(0xFFEEF0F7)
)

private val DarkColors = darkColorScheme(
    primary = BrandPrimary,
    secondary = BrandSecondary,
    background = Color(0xFF0F1117),
    surface = Color(0xFF1A1D29),
    onPrimary = Color.White,
    onBackground = Color(0xFFEDEEF4),
    onSurface = Color(0xFFEDEEF4),
    surfaceVariant = Color(0xFF262A3B)
)

@Composable
fun AiLocalTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColors else LightColors
    val view = LocalView.current

    if (!view.isInEditMode) {
        SideEffect {
            val activity = view.context as? Activity ?: return@SideEffect
            activity.window.statusBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(activity.window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = AiLocalTypography,
        content = content
    )
}
