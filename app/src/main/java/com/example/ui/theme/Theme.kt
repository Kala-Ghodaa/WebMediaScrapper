package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val PulseGrabDarkColorScheme = darkColorScheme(
    primary = ElectricCyan,
    onPrimary = Color(0xFF002630),
    primaryContainer = ElectricCyanContainer,
    onPrimaryContainer = ElectricCyan,
    secondary = VividViolet,
    onSecondary = Color.White,
    secondaryContainer = VividVioletContainer,
    onSecondaryContainer = VividVioletLight,
    tertiary = EmeraldSuccess,
    onTertiary = Color(0xFF00291D),
    tertiaryContainer = EmeraldContainer,
    onTertiaryContainer = EmeraldSuccess,
    error = CoralError,
    onError = Color.White,
    errorContainer = CoralContainer,
    onErrorContainer = Color(0xFFFFB4AB),
    background = MidnightBg,
    onBackground = TextPrimary,
    surface = MidnightSurface,
    onSurface = TextPrimary,
    surfaceVariant = MidnightSurfaceVariant,
    onSurfaceVariant = TextSecondary,
    surfaceContainer = MidnightSurface,
    surfaceContainerHigh = MidnightSurfaceElevated,
    surfaceContainerHighest = MidnightSurfaceVariant,
    outline = OutlineSlate,
    outlineVariant = Color(0xFF1E293B)
)

private val PulseGrabLightColorScheme = androidx.compose.material3.lightColorScheme(
    primary = Color(0xFF007791),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFCFFAFE),
    onPrimaryContainer = Color(0xFF002630),
    secondary = Color(0xFF6D28D9),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFEDE9FE),
    onSecondaryContainer = Color(0xFF2E1A68),
    tertiary = Color(0xFF059669),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFD1FAE5),
    onTertiaryContainer = Color(0xFF064E3B),
    error = CoralError,
    onError = Color.White,
    errorContainer = Color(0xFFFEE2E2),
    onErrorContainer = Color(0xFF7F1D1D),
    background = DaylightBg,
    onBackground = DaylightTextPrimary,
    surface = DaylightSurface,
    onSurface = DaylightTextPrimary,
    surfaceVariant = DaylightSurfaceVariant,
    onSurfaceVariant = DaylightTextSecondary,
    surfaceContainer = DaylightSurface,
    surfaceContainerHigh = DaylightSurfaceElevated,
    surfaceContainerHighest = DaylightSurfaceVariant,
    outline = DaylightOutline,
    outlineVariant = Color(0xFFCBD5E1)
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = androidx.compose.foundation.isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) PulseGrabDarkColorScheme else PulseGrabLightColorScheme
    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}

