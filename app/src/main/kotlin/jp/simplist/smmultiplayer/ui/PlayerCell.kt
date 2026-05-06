package jp.simplist.smmultiplayer.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventTimeoutCancellationException
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
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
import kotlinx.coroutines.withTimeout

private const val LONG_PRESS_MS: Long = 400L

/**
 * Vertical strip at the very top of the window (in dp) that is reserved for
 * the TopBar pull-down gesture in [PlayerApp]. A pointer DOWN landing in
 * this zone must not be interpreted as a cell-level volume drag — otherwise
 * the swipe-down-to-open-the-bar gesture leaks into the cell underneath and
 * silently slides the volume to zero before the user even sees the bar.
 */
private val TOP_EDGE_RESERVED_DP = 40.dp

/** Outcome of the cell's gesture-decision phase. */
private enum class GestureOutcome { Cancel, Tap, HDrag, VDrag, LongPress }

@OptIn(UnstableApi::class)
@Composable
fun PlayerCell(
    slot: PlayerSlotState,
    player: ExoPlayer,
    isCompact: Boolean,
    showVolumeIndicator: Boolean,
    showSeekIndicator: Boolean,
    controlsAlwaysVisible: Boolean,
    volumeGestureEnabled: Boolean,
    seekGestureEnabled: Boolean,
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
    onSetPlaybackSpeed: (Float) -> Unit,
    onSetLoopA: () -> Unit,
    onSetLoopB: () -> Unit,
    onClearLoop: () -> Unit,
    onControlsVisibilityChange: (Boolean) -> Unit = {},
    /**
     * Long-press → drag-and-drop reorder hooks. Cell-local pointer position
     * is converted to window coordinates (`localPos + cellWindowOrigin`)
     * before being passed to the parent so it can hit-test against other
     * cells. The parent is responsible for visual lift / target-highlight /
     * actually committing the swap on drop.
     */
    onReorderStart: () -> Unit = {},
    onReorderMove: (windowPos: Offset, dragDelta: Offset) -> Unit = { _, _ -> },
    onReorderEnd: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var controlsVisible by remember { mutableStateOf(false) }
    var volumeIndicator by remember { mutableStateOf<Float?>(null) }
    var seekIndicator by remember { mutableStateOf<SeekIndicatorState?>(null) }
    val effectivelyVisible = controlsAlwaysVisible || controlsVisible

    // Bumped on any user interaction with the controls overlay (tap to show,
    // skip / seek / play-toggle / resize / speed / loop button presses, drag
    // gestures). Each bump resets the auto-hide timer below.
    var interactionTick by remember { mutableIntStateOf(0) }
    val markInteraction: () -> Unit = { interactionTick++ }

    // Auto-hide the overlay 3 s after the most recent interaction. Skipped
    // when the user has explicitly opted into "controls always visible".
    LaunchedEffect(controlsVisible, interactionTick, controlsAlwaysVisible) {
        if (controlsVisible && !controlsAlwaysVisible) {
            delay(3_000)
            controlsVisible = false
        }
    }

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
    val latestVolumeGesture by rememberUpdatedState(volumeGestureEnabled)
    val latestSeekGesture by rememberUpdatedState(seekGestureEnabled)
    // Reorder callbacks change every parent composition (their closures pull
    // grid-level drag state). pointerInput keeps the same coroutine across
    // recompositions, so we MUST forward through rememberUpdatedState —
    // otherwise the running gesture invokes stale callbacks captured at
    // first composition and the drop never lands.
    val latestOnReorderStart by rememberUpdatedState(onReorderStart)
    val latestOnReorderMove by rememberUpdatedState(onReorderMove)
    val latestOnReorderEnd by rememberUpdatedState(onReorderEnd)

    // Origin of this cell in the window coordinate space, refreshed on every
    // layout pass. Used to translate cell-local pointer positions into window
    // coordinates the parent grid can hit-test against other cells' bounds.
    var cellWindowOrigin by remember { mutableStateOf(Offset.Zero) }
    val latestCellOrigin by rememberUpdatedState(cellWindowOrigin)

    Box(
        modifier = modifier
            .background(if (hasVideo) PlayerBg else PlayerEmptyBg)
            .onGloballyPositioned { coords ->
                cellWindowOrigin = coords.positionInWindow()
            }
            .pointerInput(hasVideo) {
                // Single unified gesture detector. The first ~400 ms of a
                // press is a "decision window" — based on what happens we
                // route into one of four outcomes:
                //   - Tap          : finger lifts before the long-press timeout
                //   - Horizontal drag (seek)   : finger moves past slop, dx > dy
                //   - Vertical drag   (volume) : finger moves past slop, dy > dx
                //   - Long-press   : 400 ms of no movement → enter reorder mode
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    if (!hasVideo) {
                        // Empty slots: the only meaningful gesture is a tap
                        // to launch the file picker.
                        val up = waitForUpOrCancellation()
                        if (up != null) {
                            onPickVideo()
                            markInteraction()
                        }
                        return@awaitEachGesture
                    }

                    val slop = viewConfiguration.touchSlop
                    // NOTE: inside awaitEachGesture, `withTimeout` resolves to
                    // the AwaitPointerEventScope overload, which throws
                    // PointerEventTimeoutCancellationException (NOT the kotlinx
                    // TimeoutCancellationException) when the deadline fires.
                    // Catching the wrong type means LongPress never triggers.
                    val outcome: GestureOutcome = try {
                        withTimeout(LONG_PRESS_MS) {
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes
                                    .firstOrNull { it.id == down.id } ?: return@withTimeout GestureOutcome.Cancel
                                if (!change.pressed) return@withTimeout GestureOutcome.Tap
                                val total = change.position - down.position
                                if (total.getDistance() > slop) {
                                    return@withTimeout if (abs(total.x) > abs(total.y))
                                        GestureOutcome.HDrag else GestureOutcome.VDrag
                                }
                            }
                            @Suppress("UNREACHABLE_CODE") GestureOutcome.Cancel
                        }
                    } catch (_: PointerEventTimeoutCancellationException) {
                        GestureOutcome.LongPress
                    }

                    when (outcome) {
                        GestureOutcome.Cancel -> Unit
                        GestureOutcome.Tap -> {
                            if (latestSoloAudio && !latestIsSoloTarget) {
                                // Solo audio mode: tapping a non-target cell makes
                                // it the audio source. Force-show controls so the
                                // ♪ 音声中 chip on this cell becomes the visual
                                // confirmation of the switch.
                                onActivateSolo()
                                if (!controlsVisible) controlsVisible = true
                            } else {
                                controlsVisible = !controlsVisible
                            }
                            markInteraction()
                        }
                        GestureOutcome.VDrag -> {
                            // Suppress volume drag when the gesture started in
                            // the top-edge reservation zone — that strip is
                            // owned by the PlayerApp swipe-down handler that
                            // opens the TopBar. Without this guard the same
                            // swipe drags the cell's volume to zero on its
                            // way to revealing the bar.
                            val downWindowY = latestCellOrigin.y + down.position.y
                            val inTopEdge = downWindowY < TOP_EDGE_RESERVED_DP.toPx()
                            if (inTopEdge) {
                                // Drain the gesture to release; do nothing.
                                while (true) {
                                    val event = awaitPointerEvent()
                                    val change = event.changes
                                        .firstOrNull { it.id == down.id } ?: break
                                    if (!change.pressed) break
                                    change.consume()
                                }
                                return@awaitEachGesture
                            }

                            // Vertical drag → volume.
                            val dragStartVolume = latestSlot.volume
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes
                                    .firstOrNull { it.id == down.id } ?: break
                                if (!change.pressed) break
                                if (latestVolumeGesture) {
                                    val totalDy = change.position.y - down.position.y
                                    val cellHeight = size.height.toFloat().coerceAtLeast(1f)
                                    val deltaVol = -totalDy / cellHeight
                                    val newVol = (dragStartVolume + deltaVol).coerceIn(0f, 1f)
                                    onVolumeChange(newVol)
                                    if (latestSoloAudio && !latestIsSoloTarget) onActivateSolo()
                                    volumeIndicator = newVol
                                    markInteraction()
                                }
                                change.consume()
                            }
                        }
                        GestureOutcome.HDrag -> {
                            // Horizontal drag → seek scrub. Snapshot the start
                            // position from the player itself (not stale slot.positionMs).
                            val dragStartPosMs = player.currentPosition
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes
                                    .firstOrNull { it.id == down.id } ?: break
                                if (!change.pressed) {
                                    if (latestSeekGesture) {
                                        seekIndicator?.let { s ->
                                            onSeekTo(s.target)
                                            seekIndicator = s.copy(committed = true)
                                            markInteraction()
                                        }
                                    }
                                    break
                                }
                                if (latestSeekGesture) {
                                    val totalDx = change.position.x - down.position.x
                                    val cellWidth = size.width.toFloat().coerceAtLeast(1f)
                                    val durationMs = latestSlot.durationMs.coerceAtLeast(1L)
                                    // 1 screen width ≈ 60 s seek
                                    val totalDeltaMs = (totalDx / cellWidth * 60_000f).toLong()
                                    val target = (dragStartPosMs + totalDeltaMs).coerceIn(0L, durationMs)
                                    seekIndicator = SeekIndicatorState(dragStartPosMs, target, committed = false)
                                    markInteraction()
                                }
                                change.consume()
                            }
                        }
                        GestureOutcome.LongPress -> {
                            // Reorder mode. The parent owns the visual lift and
                            // hit-testing; we just translate cell-local pointer
                            // positions to window coordinates and forward.
                            latestOnReorderStart()
                            markInteraction()
                            try {
                                while (true) {
                                    val event = awaitPointerEvent()
                                    val change = event.changes
                                        .firstOrNull { it.id == down.id } ?: break
                                    if (!change.pressed) break
                                    val windowPos = latestCellOrigin + change.position
                                    val dragDelta = change.position - down.position
                                    latestOnReorderMove(windowPos, dragDelta)
                                    change.consume()
                                }
                            } finally {
                                latestOnReorderEnd()
                            }
                        }
                    }
                }
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
                // Wrap each callback so any control press / drag resets the
                // 3-second auto-hide timer rather than letting it expire while
                // the user is actively interacting with the overlay.
                onTogglePlay = { onTogglePlay(); markInteraction() },
                onSkip = { ms -> onSkip(ms); markInteraction() },
                onSeekTo = { pos -> onSeekTo(pos); markInteraction() },
                onPickVideo = { onPickVideo(); markInteraction() },
                onClearVideo = { onClearVideo(); markInteraction() },
                onCycleResizeMode = { onCycleResizeMode(); markInteraction() },
                onSetPlaybackSpeed = { s -> onSetPlaybackSpeed(s); markInteraction() },
                onSetLoopA = { onSetLoopA(); markInteraction() },
                onSetLoopB = { onSetLoopB(); markInteraction() },
                onClearLoop = { onClearLoop(); markInteraction() },
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
