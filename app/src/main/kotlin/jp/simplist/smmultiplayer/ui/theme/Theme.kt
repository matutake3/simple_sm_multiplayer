package jp.simplist.smmultiplayer.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val DarkColors = darkColorScheme(
    primary = Accent,
    onPrimary = PlayerBg,
    secondary = AccentSecondary,
    onSecondary = PlayerBg,
    background = PlayerBg,
    onBackground = androidx.compose.ui.graphics.Color.White,
    surface = PlayerEmptyBg,
    onSurface = androidx.compose.ui.graphics.Color.White,
)

@Composable
fun SimpleSmMultiplayerTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColors,
        typography = AppTypography,
        content = content,
    )
}
