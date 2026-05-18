package com.miseservice.smsovh.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF0057B7), // smsovh_primary foncé
    onPrimary = Color.White,
    background = Color(0xFF121212),
    onBackground = Color.White
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF0057B7), // smsovh_primary clair
    onPrimary = Color.White,
    background = Color(0xFFFFFFFF),
    onBackground = Color.Black
)

@Composable
fun SmsOvhTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
        typography = MaterialTheme.typography,
        content = content
    )
}

