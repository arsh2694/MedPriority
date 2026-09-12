package com.medpriority.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColorScheme = lightColorScheme(
    primary = AccentBlue,
    onPrimary = SurfaceCard,
    primaryContainer = AccentBlueSubtle,
    onPrimaryContainer = AccentBlue,
    secondary = TextSecondary,
    onSecondary = SurfaceCard,
    background = BackgroundLight,
    onBackground = TextPrimary,
    surface = SurfaceCard,
    onSurface = TextPrimary,
    surfaceVariant = BackgroundLight,
    onSurfaceVariant = TextSecondary,
    outline = BorderSubtle,
    error = EmergencyRed,
    onError = SurfaceCard,
    errorContainer = EmergencyRedSubtle,
    onErrorContainer = EmergencyRed
)

@Composable
fun MedPriorityTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = LightColorScheme,
        typography = Typography,
        content = content
    )
}
