package com.miseservice.msmms.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// ── Palette Material Design 3 - Jour ────────────────────────────
private val LightColorScheme = lightColorScheme(
    primary = Color(0xFFF08A3C),           // smsovh_primary (orange chaleureux)
    onPrimary = Color(0xFF2A1408),         // smsovh_on_primary
    primaryContainer = Color(0xFF4D2712),  // smsovh_primary_container
    onPrimaryContainer = Color(0xFFFFEAD9),
    secondary = Color(0xFFD8B39A),         // smsovh_secondary (beige)
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFF43342B),// smsovh_secondary_container
    onSecondaryContainer = Color(0xFFFFEAD9),
    tertiary = Color(0xFFCEC6BF),          // smsovh_tertiary
    onTertiary = Color(0xFF413935),
    tertiaryContainer = Color(0xFF5D5147),
    onTertiaryContainer = Color(0xFFFFEAD9),
    background = Color(0xFFF3F5F7),        // smsovh_light_background
    onBackground = Color(0xFF1E252C),      // smsovh_on_light
    surface = Color(0xFFFFFFFF),           // smsovh_light_surface
    onSurface = Color(0xFF1E252C),
    surfaceVariant = Color(0xFFE7ECF1),    // smsovh_light_surface_alt
    onSurfaceVariant = Color(0xFF49454E),
    outline = Color(0xFF5D5147),
    outlineVariant = Color(0xFFD8B39A),
    error = Color(0xFFF87171),             // Feedback error
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFF87171),
    onErrorContainer = Color(0xFFFFFFFF)
)

// ── Palette Material Design 3 - Nuit ────────────────────────────
private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFFF08A3C),           // smsovh_primary (même en mode sombre)
    onPrimary = Color(0xFF14100D),         // smsovh_on_primary sombre
    primaryContainer = Color(0xFF4D2712),  // smsovh_primary_container
    onPrimaryContainer = Color(0xFFFFEAD9),
    secondary = Color(0xFFD8B39A),         // smsovh_secondary
    onSecondary = Color(0xFF2A1F18),
    secondaryContainer = Color(0xFF43342B),// smsovh_secondary_container
    onSecondaryContainer = Color(0xFFFFEAD9),
    tertiary = Color(0xFFCEC6BF),          // smsovh_tertiary
    onTertiary = Color(0xFF2C2620),
    tertiaryContainer = Color(0xFF5D5147),
    onTertiaryContainer = Color(0xFFFFEAD9),
    background = Color(0xFF121212),        // smsovh_background (très sombre)
    onBackground = Color(0xFFF7F1EB),      // smsovh_on_dark
    surface = Color(0xFF1F1915),           // smsovh_surface
    onSurface = Color(0xFFF7F1EB),
    surfaceVariant = Color(0xFF2A221D),    // smsovh_surface_alt
    onSurfaceVariant = Color(0xFFD9CBC1),  // smsovh_on_muted
    outline = Color(0xFFE0B6A8),
    outlineVariant = Color(0xFF524238),    // smsovh_divider
    error = Color(0xFFF87171),             // Feedback error
    onError = Color(0xFF2A1408),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFEAEA)
)

@Composable
fun SmsOvhTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
        typography = MaterialTheme.typography,
        shapes = MaterialTheme.shapes,
        content = content
    )
}

