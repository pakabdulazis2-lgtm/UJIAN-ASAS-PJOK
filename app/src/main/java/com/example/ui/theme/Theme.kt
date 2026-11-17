package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private fun schemeForStyle(themeStyle: String) = when (themeStyle) {
    "CRIMSON" -> lightColorScheme(
        primary = CrimsonPrimary,
        onPrimary = CrimsonOnPrimary,
        primaryContainer = CrimsonPrimaryContainer,
        onPrimaryContainer = CrimsonOnPrimaryContainer,
        secondary = CrimsonSecondary,
        onSecondary = CrimsonOnSecondary,
        secondaryContainer = CrimsonSecondaryContainer,
        onSecondaryContainer = CrimsonOnPrimaryContainer,
        background = TealBackground,
        surface = TealSurface,
        onBackground = Color(0xFF0F172A),
        onSurface = Color(0xFF0F172A),
        surfaceVariant = Color(0xFFF1F5F9),
        onSurfaceVariant = Color(0xFF475569),
        outline = Color(0xFFCBD5E1),
        error = StatusUnansweredRed
    )
    "BLUE" -> lightColorScheme(
        primary = BluePrimary,
        onPrimary = BlueOnPrimary,
        primaryContainer = BluePrimaryContainer,
        onPrimaryContainer = BlueOnPrimaryContainer,
        secondary = BlueSecondary,
        onSecondary = BlueOnSecondary,
        secondaryContainer = BlueSecondaryContainer,
        onSecondaryContainer = BlueOnSecondaryContainer,
        background = TealBackground,
        surface = TealSurface,
        onBackground = Color(0xFF0F172A),
        onSurface = Color(0xFF0F172A),
        surfaceVariant = Color(0xFFF1F5F9),
        onSurfaceVariant = Color(0xFF475569),
        outline = Color(0xFFCBD5E1),
        error = StatusUnansweredRed
    )
    "FOREST" -> lightColorScheme(
        primary = ForestPrimary,
        onPrimary = ForestOnPrimary,
        primaryContainer = ForestPrimaryContainer,
        onPrimaryContainer = ForestOnPrimaryContainer,
        secondary = ForestSecondary,
        onSecondary = ForestOnSecondary,
        secondaryContainer = ForestSecondaryContainer,
        onSecondaryContainer = ForestOnSecondaryContainer,
        background = TealBackground,
        surface = TealSurface,
        onBackground = Color(0xFF0F172A),
        onSurface = Color(0xFF0F172A),
        surfaceVariant = Color(0xFFF1F5F9),
        onSurfaceVariant = Color(0xFF475569),
        outline = Color(0xFFCBD5E1),
        error = StatusUnansweredRed
    )
    else -> lightColorScheme(
        primary = TealPrimary,
        onPrimary = TealOnPrimary,
        primaryContainer = TealPrimaryContainer,
        onPrimaryContainer = TealOnPrimaryContainer,
        secondary = TealSecondary,
        onSecondary = TealOnSecondary,
        secondaryContainer = TealSecondaryContainer,
        onSecondaryContainer = TealOnSecondaryContainer,
        background = TealBackground,
        surface = TealSurface,
        onBackground = Color(0xFF0F172A),
        onSurface = Color(0xFF0F172A),
        surfaceVariant = Color(0xFFF1F5F9),
        onSurfaceVariant = Color(0xFF475569),
        outline = Color(0xFFCBD5E1),
        error = StatusUnansweredRed
    )
}

@Composable
fun UjianPjokTheme(
    themeStyle: String = "TEAL",
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = schemeForStyle(themeStyle),
        typography = Typography,
        content = content
    )
}
