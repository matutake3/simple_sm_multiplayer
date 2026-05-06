package jp.simplist.smmultiplayer.ui

import android.content.res.Configuration
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import jp.simplist.smmultiplayer.PlayerViewModel
import jp.simplist.smmultiplayer.player.PlayerSlotState
import jp.simplist.smmultiplayer.ui.theme.Accent

@Composable
fun PlayerGrid(
    slots: List<PlayerSlotState>,
    layoutMode: Int,
    viewModel: PlayerViewModel,
    showVolumeIndicator: Boolean,
    showSeekIndicator: Boolean,
    controlsAlwaysVisible: Boolean,
    volumeGestureEnabled: Boolean,
    seekGestureEnabled: Boolean,
    soloAudio: Boolean,
    soloIndex: Int,
    onPickForSlot: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val isPortrait =
        LocalConfiguration.current.orientation == Configuration.ORIENTATION_PORTRAIT

    // ---- Drag-and-drop reorder state (grid-level) ----
    // Map of slot index → cell bounds in window coords. Updated by each cell's
    // onGloballyPositioned. Used to hit-test the dragged finger against other
    // cells' rectangles to figure out the drop target.
    val cellBounds = remember { mutableStateMapOf<Int, Rect>() }
    // Index of the slot currently being dragged (null = no drag in progress).
    var dragSourceIdx by remember { mutableStateOf<Int?>(null) }
    // Total accumulated drag delta (cell-local). Applied as a graphicsLayer
    // translation so the source cell visually follows the finger.
    var dragOffset by remember { mutableStateOf(Offset.Zero) }
    // Current finger position in window coords. Used to derive drop target.
    var dragHotspot by remember { mutableStateOf(Offset.Zero) }
    // Slot the finger is currently hovering over (null if outside any cell or
    // hovering over the source itself — both no-op on drop).
    val dragTargetIdx: Int? = dragSourceIdx?.let { src ->
        if (src !in 0 until layoutMode) null
        else cellBounds.entries.firstOrNull { (idx, rect) ->
            idx != src && idx < layoutMode && rect.contains(dragHotspot)
        }?.key
    }

    val cellAt: @Composable (Int, Boolean, Modifier) -> Unit = { index, compact, mod ->
        val isDragSource = dragSourceIdx == index
        val isDragTarget = dragTargetIdx == index
        // Visual: translate + scale + alpha for the source cell, highlight
        // border for the active drop target. Keep the source above other
        // cells so it visibly sits on top while dragged.
        val cellModifier = mod
            .onGloballyPositioned { coords ->
                cellBounds[index] = coords.boundsInWindow()
            }
            .zIndex(if (isDragSource) 10f else 0f)
            .graphicsLayer {
                if (isDragSource) {
                    translationX = dragOffset.x
                    translationY = dragOffset.y
                    scaleX = 1.05f
                    scaleY = 1.05f
                    alpha = 0.85f
                }
            }
            .then(
                if (isDragTarget) Modifier.border(3.dp, Accent) else Modifier
            )

        PlayerCell(
            slot = slots[index],
            player = viewModel.getPlayer(index),
            isCompact = compact,
            showVolumeIndicator = showVolumeIndicator,
            showSeekIndicator = showSeekIndicator,
            controlsAlwaysVisible = controlsAlwaysVisible,
            volumeGestureEnabled = volumeGestureEnabled,
            seekGestureEnabled = seekGestureEnabled,
            soloAudio = soloAudio,
            isSoloTarget = soloAudio && soloIndex == index,
            onTogglePlay = { viewModel.togglePlay(index) },
            onSkip = { d -> viewModel.skipBy(index, d) },
            onSeekTo = { p -> viewModel.seekTo(index, p) },
            onVolumeChange = { v -> viewModel.setVolume(index, v) },
            onActivateSolo = { viewModel.selectSoloAudioSlot(index) },
            onPickVideo = { onPickForSlot(index) },
            onClearVideo = { viewModel.clearVideo(index) },
            onCycleResizeMode = { viewModel.cycleResizeMode(index) },
            onSetPlaybackSpeed = { s -> viewModel.setPlaybackSpeed(index, s) },
            onSetLoopA = { viewModel.setLoopA(index) },
            onSetLoopB = { viewModel.setLoopB(index) },
            onClearLoop = { viewModel.clearLoop(index) },
            onControlsVisibilityChange = { v -> viewModel.setControlsVisible(index, v) },
            onReorderStart = {
                dragSourceIdx = index
                dragOffset = Offset.Zero
                // Seed hotspot from the cell's centre so the very first
                // hit-test, before any onReorderMove fires, doesn't claim
                // some neighbouring cell as a target.
                cellBounds[index]?.let { dragHotspot = it.center }
            },
            onReorderMove = { windowPos, delta ->
                dragHotspot = windowPos
                dragOffset = delta
            },
            onReorderEnd = {
                // Re-resolve the drop target from CURRENT state instead of
                // closing over the captured `dragTargetIdx` val — that local
                // is computed at composition time and would still be null
                // (the value at long-press start) by the time we land here.
                val src = dragSourceIdx
                if (src != null) {
                    val tgt = cellBounds.entries.firstOrNull { (idx, rect) ->
                        idx != src && idx < layoutMode && rect.contains(dragHotspot)
                    }?.key
                    if (tgt != null) {
                        viewModel.swapSlots(src, tgt)
                    }
                }
                dragSourceIdx = null
                dragOffset = Offset.Zero
                dragHotspot = Offset.Zero
            },
            modifier = cellModifier,
        )
    }

    when (layoutMode) {
        // 1 cell — fullscreen, identical in both orientations.
        1 -> Box(modifier.fillMaxSize()) {
            cellAt(0, false, Modifier.fillMaxSize())
        }
        // 2 cells — landscape: side-by-side; portrait: top/bottom split.
        2 -> if (isPortrait) {
            Column(modifier.fillMaxSize()) {
                cellAt(0, false, Modifier.weight(1f).fillMaxWidth())
                cellAt(1, false, Modifier.weight(1f).fillMaxWidth())
            }
        } else {
            Row(modifier.fillMaxSize()) {
                cellAt(0, false, Modifier.weight(1f).fillMaxHeight())
                cellAt(1, false, Modifier.weight(1f).fillMaxHeight())
            }
        }
        // 3 cells — landscape: 3 columns; portrait: 3 stacked rows.
        3 -> if (isPortrait) {
            Column(modifier.fillMaxSize()) {
                cellAt(0, false, Modifier.weight(1f).fillMaxWidth())
                cellAt(1, false, Modifier.weight(1f).fillMaxWidth())
                cellAt(2, false, Modifier.weight(1f).fillMaxWidth())
            }
        } else {
            Row(modifier.fillMaxSize()) {
                cellAt(0, false, Modifier.weight(1f).fillMaxHeight())
                cellAt(1, false, Modifier.weight(1f).fillMaxHeight())
                cellAt(2, false, Modifier.weight(1f).fillMaxHeight())
            }
        }
        // 4 cells — 2×2, identical in both orientations.
        else -> Column(modifier.fillMaxSize()) {
            Row(Modifier.weight(1f).fillMaxWidth()) {
                cellAt(0, true, Modifier.weight(1f).fillMaxHeight())
                cellAt(1, true, Modifier.weight(1f).fillMaxHeight())
            }
            Row(Modifier.weight(1f).fillMaxWidth()) {
                cellAt(2, true, Modifier.weight(1f).fillMaxHeight())
                cellAt(3, true, Modifier.weight(1f).fillMaxHeight())
            }
        }
    }
}
