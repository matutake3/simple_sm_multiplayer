package jp.simplist.smmultiplayer.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import jp.simplist.smmultiplayer.R
import jp.simplist.smmultiplayer.player.PlayerSlotState
import jp.simplist.smmultiplayer.ui.theme.Accent
import jp.simplist.smmultiplayer.ui.theme.PlayerBg
import jp.simplist.smmultiplayer.ui.theme.PlayerEmptyBg
import kotlin.math.abs
import kotlinx.coroutines.delay

@OptIn(UnstableApi::class)
@Composable
fun PlayerCell(
    slot: PlayerSlotState,
    player: ExoPlayer,
    isCompact: Boolean,
    showVolumeIndicator: Boolean,
    showSeekIndicator: Boolean,
    controlsAlwaysVisible: Boolean,
    soloAudio: Boolean,
    isSoloTarget: Boolean,
    onTogglePlay: () -> Unit,
    onSkip: (Long) -> Unit,
    onSeekTo: (Long) -> Unit,
    onVolumeChange: (Float) -> Unit,
    onActivateSolo: () -> Unit,
    onPickVideo: () -> Unit,
    onClearVideo: () -> Unit,
    onCycleResizeMode: () -> Unit,
    onControlsVisibilityChange: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var controlsVisible by remember { mutableStateOf(false) }
    var volumeIndicator by remember { mutableStateOf<Float?>(null) }
    var seekIndicator by remember { mutableStateOf<SeekIndicatorState?>(null) }
    val effectivelyVisible = controlsAlwaysVisible || controlsVisible

    // Notify the ViewModel so it can skip position polling for cells whose
    // seekbar isn't currently on screen.
    LaunchedEffect(effectivelyVisible) {
        onControlsVisibilityChange(effectivelyVisible)
    }
    DisposableEffect(Unit) {
        onDispose { onControlsVisibilityChange(false) }
    }

    LaunchedEffect(volumeIndicator) {
        if (volumeIndicator != null) {
            delay(700)
            volumeIndicator = null
        }
    }
    LaunchedEffect(seekIndicator) {
        if (seekIndicator != null && seekIndicator?.committed == true) {
            delay(700)
            seekIndicator = null
        }
    }

    val hasVideo = slot.uri != null
    // Read fresh slot values from inside long-lived pointerInput coroutines.
    val latestSlot by rememberUpdatedState(slot)
    val latestSoloAudio by rememberUpdatedState(soloAudio)
    val latestIsSoloTarget by rememberUpdatedState(isSoloTarget)

    Box(
        modifier = modifier
            .background(if (hasVideo) PlayerBg else PlayerEmptyBg)
            .pointerInput(hasVideo) {
                detectTapGestures(
                    onTap = {
                        if (!hasVideo) onPickVideo() else controlsVisible = !controlsVisible
                    },
                )
            }
            .pointerInput(hasVideo) {
                if (!hasVideo) return@pointerInput
                var dragStartVolume = 0f
                var dragStartPosMs = 0L
                detectDirectionalDrag(
                    onVerticalStart = { dragStartVolume = latestSlot.volume },
                    onVerticalDelta = { totalDy ->
                        val cellHeight = size.height.toFloat().coerceAtLeast(1f)
                        val deltaVol = -totalDy / cellHeight
                        val newVol = (dragStartVolume + deltaVol).coerceIn(0f, 1f)
                        onVolumeChange(newVol)
                        if (latestSoloAudio && !latestIsSoloTarget) onActivateSolo()
                        volumeIndicator = newVol
                    },
                    onHorizontalStart = {
                        // Read position directly from the player rather than
                        // from the state — when the controls overlay is hidden
                        // the position-polling loop skips updates, so
                        // `latestSlot.positionMs` can be stale by minutes.
                        dragStartPosMs = player.currentPosition
                    },
                    onHorizontalDelta = { totalDx ->
                        val cellWidth = size.width.toFloat().coerceAtLeast(1f)
                        val durationMs = latestSlot.durationMs.coerceAtLeast(1L)
                        // 1 screen width ≈ 60 s seek
                        val totalDeltaMs = (totalDx / cellWidth * 60_000f).toLong()
                        val target = (dragStartPosMs + totalDeltaMs).coerceIn(0L, durationMs)
                        seekIndicator = SeekIndicatorState(dragStartPosMs, target, committed = false)
                    },
                    onHorizontalEnd = {
                        seekIndicator?.let { s ->
                            onSeekTo(s.target)
                            seekIndicator = s.copy(committed = true)
                        }
                    },
                )
            },
    ) {
        // Video surface
        if (hasVideo) {
            AndroidView(
                // Inflate from XML so we can set surface_type="texture_view".
                // TextureView clips to parent bounds correctly even when
                // AspectRatioFrameLayout's ZOOM mode resizes the view past the
                // cell's bounds; the default SurfaceView would let video
                // bleed into adjacent cells.
                factory = { ctx ->
                    val view = LayoutInflater.from(ctx)
                        .inflate(R.layout.exo_player_cell_view, null) as PlayerView
                    view.useController = false
                    view.setShutterBackgroundColor(android.graphics.Color.BLACK)
                    view.layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    )
                    view.resizeMode = slot.resizeMode
                    view
                },
                update = { v ->
                    v.player = player
                    if (v.resizeMode != slot.resizeMode) v.resizeMode = slot.resizeMode
                },
                onRelease = { v -> v.player = null },
                // Belt-and-suspenders: also clip at the Compose layer so any
                // residual overflow in unusual resize-mode combinations stays
                // within the cell.
                modifier = Modifier.fillMaxSize().clipToBounds(),
            )
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(
                    imageVector = Icons.Outlined.AddCircle,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(48.dp),
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.empty_slot_hint),
                    color = Color.White,
                    fontSize = 13.sp,
                )
            }
        }

        // Solo audio chip — shown only with controls overlay (gated on tap),
        // so the chip doesn't permanently clutter the screen.
        AnimatedVisibility(
            visible = soloAudio && hasVideo && effectivelyVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopStart),
        ) {
            Box(
                modifier = Modifier
                    .padding(8.dp)
                    .background(
                        color = if (isSoloTarget) Accent else Color.Black.copy(alpha = 0.6f),
                        shape = RoundedCornerShape(6.dp),
                    )
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            ) {
                Text(
                    text = if (isSoloTarget) "♪ 音声中" else "♪ ミュート",
                    color = if (isSoloTarget) Color.Black else Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
        }

        // Controls overlay
        AnimatedVisibility(
            visible = hasVideo && effectivelyVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize(),
        ) {
            PlayerControls(
                slot = slot,
                isCompact = isCompact,
                onTogglePlay = onTogglePlay,
                onSkip = onSkip,
                onSeekTo = onSeekTo,
                onPickVideo = onPickVideo,
                onClearVideo = onClearVideo,
                onCycleResizeMode = onCycleResizeMode,
            )
        }

        // Indicators (above everything)
        if (showVolumeIndicator) {
            volumeIndicator?.let { v ->
                VolumeIndicator(
                    volume = v,
                    modifier = Modifier.align(Alignment.Center),
                )
            }
        }
        if (showSeekIndicator) {
            seekIndicator?.let { s ->
                SeekIndicator(
                    currentMs = s.startMs,
                    targetMs = s.target,
                    modifier = Modifier.align(Alignment.Center),
                )
            }
        }
    }
}

private data class SeekIndicatorState(
    val startMs: Long,
    val target: Long,
    val committed: Boolean,
)

/**
 * Distinguishes vertical-drag (volume) vs horizontal-drag (seek) per gesture.
 *
 * Reports total accumulated deltas from the gesture start (NOT per-frame deltas) so
 * callers can compute absolute targets without accumulation drift. `onVerticalStart`
 * / `onHorizontalStart` fire once per gesture, when the orientation is first decided.
 *
 * Allows consumed downs so taps captured by overlay buttons don't break drag detection.
 */
private suspend fun PointerInputScope.detectDirectionalDrag(
    onVerticalStart: () -> Unit,
    onVerticalDelta: (totalDy: Float) -> Unit,
    onHorizontalStart: () -> Unit,
    onHorizontalDelta: (totalDx: Float) -> Unit,
    onHorizontalEnd: () -> Unit,
) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        val pointerId = down.id
        var dragOrient = 0 // 0 undetermined, 1 = horizontal, 2 = vertical
        val startPos = down.position
        val slop = viewConfiguration.touchSlop

        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Main)
            val change = event.changes.firstOrNull { it.id == pointerId } ?: break
            if (!change.pressed) {
                if (dragOrient == 1) onHorizontalEnd()
                break
            }
            val total: Offset = change.position - startPos
            if (dragOrient == 0) {
                if (total.getDistance() > slop) {
                    dragOrient = if (abs(total.x) > abs(total.y)) 1 else 2
                    if (dragOrient == 1) onHorizontalStart() else onVerticalStart()
                }
            }
            if (dragOrient != 0) {
                if (dragOrient == 1) onHorizontalDelta(total.x) else onVerticalDelta(total.y)
                change.consume()
            }
        }
    }
}
