package jp.simplist.smmultiplayer.ui

import android.content.res.Configuration
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import jp.simplist.smmultiplayer.PlayerViewModel
import jp.simplist.smmultiplayer.player.PlayerSlotState

@Composable
fun PlayerGrid(
    slots: List<PlayerSlotState>,
    layoutMode: Int,
    viewModel: PlayerViewModel,
    showVolumeIndicator: Boolean,
    showSeekIndicator: Boolean,
    controlsAlwaysVisible: Boolean,
    soloAudio: Boolean,
    soloIndex: Int,
    onPickForSlot: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val isPortrait =
        LocalConfiguration.current.orientation == Configuration.ORIENTATION_PORTRAIT

    val cellAt: @Composable (Int, Boolean, Modifier) -> Unit = { index, compact, mod ->
        PlayerCell(
            slot = slots[index],
            player = viewModel.getPlayer(index),
            isCompact = compact,
            showVolumeIndicator = showVolumeIndicator,
            showSeekIndicator = showSeekIndicator,
            controlsAlwaysVisible = controlsAlwaysVisible,
            soloAudio = soloAudio,
            isSoloTarget = soloAudio && soloIndex == index,
            onTogglePlay = { viewModel.togglePlay(index) },
            onSkip = { d -> viewModel.skipBy(index, d) },
            onSeekTo = { p -> viewModel.seekTo(index, p) },
            onVolumeChange = { v -> viewModel.setVolume(index, v) },
            onActivateSolo = { viewModel.selectSoloAudioSlot(index) },
            onPickVideo = { onPickForSlot(index) },
            onClearVideo = { viewModel.clearVideo(index) },
            onControlsVisibilityChange = { v -> viewModel.setControlsVisible(index, v) },
            modifier = mod,
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
