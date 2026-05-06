package jp.simplist.smmultiplayer.ui

import androidx.compose.foundation.ScrollState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Draws a thin vertical scrollbar on the right edge of the modified element
 * whenever its [state] reports a non-zero `maxValue`. Compose Foundation
 * doesn't ship a built-in scrollbar for `Column.verticalScroll`, so this is
 * the smallest custom replacement that shows scrollability at a glance.
 */
fun Modifier.simpleVerticalScrollbar(
    state: ScrollState,
    width: Dp = 4.dp,
    color: Color = Color.White.copy(alpha = 0.45f),
    topInset: Dp = 0.dp,
    bottomInset: Dp = 0.dp,
): Modifier = drawWithContent {
    drawContent()
    if (state.maxValue <= 0) return@drawWithContent
    val widthPx = width.toPx()
    val topInsetPx = topInset.toPx()
    val bottomInsetPx = bottomInset.toPx()
    val trackHeight = (size.height - topInsetPx - bottomInsetPx).coerceAtLeast(1f)
    val viewportToContent = trackHeight / (trackHeight + state.maxValue)
    val barHeight = (trackHeight * viewportToContent).coerceAtLeast(16f)
    val barTop = topInsetPx + state.value.toFloat() / state.maxValue * (trackHeight - barHeight)
    drawRoundRect(
        color = color,
        topLeft = Offset(size.width - widthPx, barTop),
        size = Size(widthPx, barHeight),
        cornerRadius = CornerRadius(widthPx / 2f),
    )
}
