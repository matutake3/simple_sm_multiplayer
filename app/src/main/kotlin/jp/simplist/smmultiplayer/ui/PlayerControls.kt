package jp.simplist.smmultiplayer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CropFree
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import jp.simplist.smmultiplayer.player.PlayerSlotState
import jp.simplist.smmultiplayer.player.ResizeMode
import jp.simplist.smmultiplayer.ui.theme.Accent
import jp.simplist.smmultiplayer.ui.theme.OverlayScrim
import jp.simplist.smmultiplayer.ui.theme.OverlayScrimSoft

@Composable
fun PlayerControls(
    slot: PlayerSlotState,
    isCompact: Boolean,
    onTogglePlay: () -> Unit,
    onSkip: (Long) -> Unit,
    onSeekTo: (Long) -> Unit,
    onPickVideo: () -> Unit,
    onClearVideo: () -> Unit,
    onCycleResizeMode: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize().background(OverlayScrimSoft)) {
        // Top bar: title + swap/clear
        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = slot.title.orEmpty(),
                color = Color.White,
                fontSize = 12.sp,
                maxLines = 1,
                modifier = Modifier.weight(1f).padding(horizontal = 4.dp),
            )
            IconButton(onClick = onCycleResizeMode, modifier = Modifier.size(32.dp)) {
                val (icon, desc) = when (slot.resizeMode) {
                    ResizeMode.ZOOM -> Icons.Filled.Fullscreen to "ZOOM"
                    ResizeMode.FILL -> Icons.Filled.CropFree to "FILL"
                    else -> Icons.Filled.AspectRatio to "FIT"
                }
                Icon(icon, desc, tint = Color.White, modifier = Modifier.size(20.dp))
            }
            IconButton(onClick = onPickVideo, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Filled.SwapHoriz, "変更", tint = Color.White, modifier = Modifier.size(20.dp))
            }
            IconButton(onClick = onClearVideo, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Filled.Close, "解除", tint = Color.White, modifier = Modifier.size(20.dp))
            }
        }

        // Center play/pause
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .size(if (isCompact) 56.dp else 72.dp)
                .background(OverlayScrim, CircleShape)
                .clickable(onClick = onTogglePlay),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = if (slot.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                contentDescription = if (slot.isPlaying) "一時停止" else "再生",
                tint = Color.White,
                modifier = Modifier.size(if (isCompact) 32.dp else 40.dp),
            )
        }

        // Always render all 6 skip buttons; compact mode just uses smaller
        // typography / padding so the 4-screen cell doesn't crowd them out.
        val columnSpacing = if (isCompact) 6.dp else 10.dp
        val edgePadding = if (isCompact) 8.dp else 12.dp
        Column(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = edgePadding),
            verticalArrangement = Arrangement.spacedBy(columnSpacing),
        ) {
            SkipButton("2分", forward = false, isCompact = isCompact, onClick = { onSkip(-120_000L) })
            SkipButton("30秒", forward = false, isCompact = isCompact, onClick = { onSkip(-30_000L) })
            SkipButton("5秒", forward = false, isCompact = isCompact, onClick = { onSkip(-5_000L) })
        }
        Column(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = edgePadding),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(columnSpacing),
        ) {
            SkipButton("2分", forward = true, isCompact = isCompact, onClick = { onSkip(120_000L) })
            SkipButton("30秒", forward = true, isCompact = isCompact, onClick = { onSkip(30_000L) })
            SkipButton("5秒", forward = true, isCompact = isCompact, onClick = { onSkip(5_000L) })
        }

        // Bottom: time + seekbar.
        //
        // While the user is dragging the slider, we keep the value in a local
        // state and DO NOT call seekTo until they release. Calling seekTo on
        // every onValueChange floods ExoPlayer with seek requests; for large
        // files (multi-GB) those requests queue up and the late ones can land
        // after the user has already released, making the slider jump back to
        // an early-drag position. Single-shot seek on release avoids that.
        var dragValue by remember { mutableStateOf<Float?>(null) }
        val displayValue = dragValue ?: slot.positionMs.toFloat().coerceAtLeast(0f)
        val displayLabel = dragValue?.toLong() ?: slot.positionMs

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(formatTime(displayLabel), color = Color.White, fontSize = 11.sp)
                Slider(
                    value = displayValue,
                    onValueChange = { v -> dragValue = v },
                    onValueChangeFinished = {
                        dragValue?.let { onSeekTo(it.toLong()) }
                        dragValue = null
                    },
                    valueRange = 0f..(slot.durationMs.coerceAtLeast(1L).toFloat()),
                    colors = SliderDefaults.colors(
                        thumbColor = Accent,
                        activeTrackColor = Accent,
                        inactiveTrackColor = Color.White.copy(alpha = 0.4f),
                    ),
                    modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                )
                Text(formatTime(slot.durationMs), color = Color.White, fontSize = 11.sp)
            }
        }
    }
}

@Composable
private fun SkipButton(
    label: String,
    forward: Boolean,
    isCompact: Boolean,
    onClick: () -> Unit,
) {
    val fontSize: TextUnit = if (isCompact) 10.sp else 12.sp
    val iconSize: Dp = if (isCompact) 12.dp else 16.dp
    val padH: Dp = if (isCompact) 6.dp else 10.dp
    val padV: Dp = if (isCompact) 3.dp else 6.dp
    val gap: Dp = if (isCompact) 3.dp else 4.dp
    Row(
        modifier = Modifier
            .background(OverlayScrim, RoundedCornerShape(6.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = padH, vertical = padV),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (!forward) {
            Icon(
                Icons.Filled.FastRewind,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(iconSize),
            )
            Spacer(Modifier.width(gap))
            Text(label, color = Color.White, fontSize = fontSize, fontWeight = FontWeight.Medium)
        } else {
            Text(label, color = Color.White, fontSize = fontSize, fontWeight = FontWeight.Medium)
            Spacer(Modifier.width(gap))
            Icon(
                Icons.Filled.FastForward,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(iconSize),
            )
        }
    }
}

internal fun formatTime(ms: Long): String {
    if (ms <= 0L) return "00:00"
    val totalSec = ms / 1000
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
}
