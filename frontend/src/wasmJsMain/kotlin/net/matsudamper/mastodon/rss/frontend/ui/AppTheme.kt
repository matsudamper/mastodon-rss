package net.matsudamper.mastodon.rss.frontend.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors =
    lightColorScheme(
        primary = Color(0xFF4A3FD1),
        onPrimary = Color.White,
        primaryContainer = Color(0xFFE3E0FF),
        onPrimaryContainer = Color(0xFF17106B),
        secondary = Color(0xFF3F6D53),
        onSecondary = Color.White,
        background = Color(0xFFF5F5FA),
        onBackground = Color(0xFF1A1A22),
        surface = Color.White,
        onSurface = Color(0xFF1A1A22),
        surfaceVariant = Color(0xFFECECF3),
        onSurfaceVariant = Color(0xFF565663),
        outline = Color(0xFFC7C7D3),
        outlineVariant = Color(0xFFE2E2EA),
        error = Color(0xFFB3261E),
    )

private val DarkColors =
    darkColorScheme(
        primary = Color(0xFFB9B2FF),
        onPrimary = Color(0xFF1F1A5C),
        primaryContainer = Color(0xFF332C86),
        onPrimaryContainer = Color(0xFFE3E0FF),
        secondary = Color(0xFF9FD3B2),
        onSecondary = Color(0xFF10371F),
        background = Color(0xFF121219),
        onBackground = Color(0xFFE6E6EE),
        surface = Color(0xFF1B1B24),
        onSurface = Color(0xFFE6E6EE),
        surfaceVariant = Color(0xFF2A2A36),
        onSurfaceVariant = Color(0xFFB6B6C4),
        outline = Color(0xFF4A4A59),
        outlineVariant = Color(0xFF32323F),
        error = Color(0xFFFFB4AB),
    )

/**
 * 画面全体の配色。
 *
 * OS の設定に追従する。canvas に描いているので `prefers-color-scheme` の
 * CSS は効かず、色の切り替えは Compose 側でやる必要がある。
 */
@Composable
fun AppTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        content = content,
    )
}
