package net.matsudamper.mastodon.rss.frontend.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily

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
 * 画面全体の配色と文字。
 *
 * 配色は OS の設定に追従する。canvas に描いているので `prefers-color-scheme` の
 * CSS は効かず、色の切り替えは Compose 側でやる必要がある。
 *
 * フォントも同じ理由でブラウザ任せにできない。読み込みは [rememberAppFontFamily] で、
 * 揃うまでは既定のフォントのまま描く。
 */
@Composable
fun AppTheme(content: @Composable () -> Unit) {
    val fontFamily = rememberAppFontFamily()
    val typography = remember(fontFamily) { Typography().withFontFamily(fontFamily) }

    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        typography = typography,
    ) {
        CompositionLocalProvider(
            // typography を通らない Text（style を指定していないもの）にも同じフォントを効かせる
            LocalTextStyle provides
                LocalTextStyle.current
                    .merge(TextStyle(fontFamily = fontFamily))
                    .merge(MaterialTheme.typography.bodyMedium),
            content = content,
        )
    }
}

/**
 * すべての文字の種類に同じフォントを当てる。
 *
 * Material3 の [Typography] は種類ごとに [TextStyle] を持っていて、
 * 1 つでも入れ忘れるとそこだけ既定のフォントになり、日本語が豆腐になる。
 */
private fun Typography.withFontFamily(fontFamily: FontFamily): Typography =
    Typography(
        displayLarge = displayLarge.copy(fontFamily = fontFamily),
        displayMedium = displayMedium.copy(fontFamily = fontFamily),
        displaySmall = displaySmall.copy(fontFamily = fontFamily),
        headlineLarge = headlineLarge.copy(fontFamily = fontFamily),
        headlineMedium = headlineMedium.copy(fontFamily = fontFamily),
        headlineSmall = headlineSmall.copy(fontFamily = fontFamily),
        titleLarge = titleLarge.copy(fontFamily = fontFamily),
        titleMedium = titleMedium.copy(fontFamily = fontFamily),
        titleSmall = titleSmall.copy(fontFamily = fontFamily),
        bodyLarge = bodyLarge.copy(fontFamily = fontFamily),
        bodyMedium = bodyMedium.copy(fontFamily = fontFamily),
        bodySmall = bodySmall.copy(fontFamily = fontFamily),
        labelLarge = labelLarge.copy(fontFamily = fontFamily),
        labelMedium = labelMedium.copy(fontFamily = fontFamily),
        labelSmall = labelSmall.copy(fontFamily = fontFamily),
    )
