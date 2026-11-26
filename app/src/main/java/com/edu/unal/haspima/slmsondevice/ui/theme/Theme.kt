package com.edu.unal.haspima.slmsondevice.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = UnalGreen,
    onPrimary = Color.Black,
    primaryContainer = UnalGreenDark,
    onPrimaryContainer = Color.White,
    secondary = UnalBlueLight,
    onSecondary = Color.Black,
    secondaryContainer = UnalBlue,
    onSecondaryContainer = Color.White,
    background = Night,
    onBackground = OnNight,
    surface = Night,
    onSurface = OnNight,
    surfaceVariant = NightSurfaceHigh,
    onSurfaceVariant = OnNightMuted,
    surfaceContainer = NightSurface,
    surfaceContainerHigh = NightSurfaceHigh,
    error = ErrorRed,
)

private val LightColorScheme = lightColorScheme(
    primary = UnalGreenDark,
    secondary = UnalBlue,
)

/** Tema de la app: oscuro por defecto (para coincidir con la presentación). */
@Composable
fun SLMsOnDeviceTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme || isSystemInDarkTheme()) DarkColorScheme else LightColorScheme,
        typography = Typography,
        content = content
    )
}
