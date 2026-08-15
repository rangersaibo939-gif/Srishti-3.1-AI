package com.opendroid.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Charcoal900 = Color(0xFF090D16)
val Charcoal800 = Color(0xFF131B2E)
val Charcoal700 = Color(0xFF1E293B)
val CyanAccent = Color(0xFF38BDF8)
val AmberGlow = Color(0xFFF59E0B)
val EmeraldGreen = Color(0xFF10B981)
val RosePink = Color(0xFFEC4899)
val PurpleGlow = Color(0xFF8B5CF6)
val TextLight = Color(0xFFF8FAFC)
val TextMuted = Color(0xFF94A3B8)
val ErrorRed = Color(0xFFEF4444)

private val SrishtiColorScheme = darkColorScheme(
    primary = CyanAccent,
    onPrimary = Charcoal900,
    secondary = AmberGlow,
    onSecondary = Charcoal900,
    tertiary = EmeraldGreen,
    background = Charcoal900,
    onBackground = TextLight,
    surface = Charcoal800,
    onSurface = TextLight,
    surfaceVariant = Charcoal700,
    onSurfaceVariant = TextMuted,
    error = ErrorRed,
    onError = TextLight
)

@Composable
fun SrishtiTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = SrishtiColorScheme,
        content = content
    )
}
