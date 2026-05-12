package com.hackastic.decmed.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

/**
 * Theme modes that the user can select from Settings.
 * SYSTEM delegates to the OS-level dark mode setting.
 */
enum class ThemeMode {
    LIGHT, DARK, SYSTEM
}

private val DarkColorScheme = darkColorScheme(
    primary = Teal80,
    secondary = Blue80,
    tertiary = Amber80,
    background = DarkSurface,
    surface = DarkSurface,
    surfaceVariant = DarkSurfaceVariant
)

private val LightColorScheme = lightColorScheme(
    primary = Teal40,
    secondary = Blue40,
    tertiary = Amber40,
    background = LightSurface,
    surface = LightSurface,
    surfaceVariant = LightSurfaceVariant
)

/**
 * Root theme composable for the entire app.
 *
 * Change from previous version:
 * - Added [themeMode] parameter to support user-selectable dark/light/system theme
 *   instead of always deferring to isSystemInDarkTheme().
 * - Dynamic color (Material You) is still supported on Android 12+.
 */
@Composable
fun DecMedTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val darkTheme = when (themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }

    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}