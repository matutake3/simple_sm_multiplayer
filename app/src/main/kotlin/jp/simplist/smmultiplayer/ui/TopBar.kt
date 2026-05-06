package jp.simplist.smmultiplayer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import jp.simplist.smmultiplayer.R
import jp.simplist.smmultiplayer.ui.theme.Accent
import jp.simplist.smmultiplayer.ui.theme.OverlayScrim
import jp.simplist.smmultiplayer.ui.theme.PlayerEmptyBg

@Composable
fun TopBar(
    layoutMode: Int,
    soloAudio: Boolean,
    syncPlayback: Boolean,
    onPlayAll: () -> Unit,
    onPauseAll: () -> Unit,
    onClearAll: () -> Unit,
    onLayoutChange: (Int) -> Unit,
    onToggleSolo: () -> Unit,
    onToggleSync: () -> Unit,
    onOpenPresets: () -> Unit,
    onOpenSettings: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Width thresholds:
    //   < 500 dp: two-row layout (typical phone portrait — wouldn't fit in one row)
    //   500-599 dp: one row, icons only
    //   >= 600 dp: one row, with text labels
    val widthDp = LocalConfiguration.current.screenWidthDp
    val twoRows = widthDp < 500
    val showLabels = widthDp >= 600

    @Composable
    fun PlaybackControls() {
        BarButton(
            icon = Icons.Filled.PlayArrow,
            label = stringResource(R.string.action_play_all).takeIf { showLabels },
            onClick = onPlayAll,
        )
        Spacer(Modifier.width(4.dp))
        BarButton(
            icon = Icons.Filled.Pause,
            label = stringResource(R.string.action_pause_all).takeIf { showLabels },
            onClick = onPauseAll,
        )
        Spacer(Modifier.width(4.dp))
        BarButton(
            icon = Icons.Filled.DeleteSweep,
            label = stringResource(R.string.action_clear_all).takeIf { showLabels },
            onClick = onClearAll,
        )
    }

    @Composable
    fun LayoutSelector() {
        Row(
            modifier = Modifier
                .background(OverlayScrim, RoundedCornerShape(6.dp))
                .padding(horizontal = 4.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Filled.GridView,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(16.dp).padding(start = 4.dp, end = 6.dp),
            )
            for (n in 1..4) {
                LayoutChip(n = n, selected = n == layoutMode, onClick = { onLayoutChange(n) })
            }
        }
    }

    @Composable
    fun ModeToggles() {
        BarButton(
            icon = if (syncPlayback) Icons.Filled.Sync else Icons.Filled.LinkOff,
            label = if (showLabels) {
                if (syncPlayback) stringResource(R.string.action_sync_on)
                else stringResource(R.string.action_sync_off)
            } else null,
            onClick = onToggleSync,
            highlight = syncPlayback,
        )
        Spacer(Modifier.width(4.dp))
        BarButton(
            icon = if (soloAudio) Icons.Filled.VolumeOff else Icons.Filled.VolumeUp,
            label = if (showLabels) {
                if (soloAudio) stringResource(R.string.action_solo_audio_on)
                else stringResource(R.string.action_solo_audio_off)
            } else null,
            onClick = onToggleSolo,
            highlight = soloAudio,
        )
    }

    @Composable
    fun ToolButtons() {
        BarButton(icon = Icons.Filled.Bookmarks, label = null, onClick = onOpenPresets)
        Spacer(Modifier.width(4.dp))
        BarButton(icon = Icons.Filled.Settings, label = null, onClick = onOpenSettings)
    }

    @Composable
    fun CloseButton() {
        BarButton(
            icon = Icons.Filled.KeyboardArrowUp,
            label = null,
            onClick = onClose,
        )
    }

    if (twoRows) {
        // Narrow widths (e.g. phone portrait): split into two rows so nothing
        // is pushed off-screen. Row 1: most-frequent actions + close.
        // Row 2: mode toggles + tools.
        Column(
            modifier = modifier
                .fillMaxWidth()
                .background(PlayerEmptyBg)
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().height(36.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PlaybackControls()
                Spacer(Modifier.width(8.dp))
                LayoutSelector()
                Spacer(Modifier.weight(1f))
                CloseButton()
            }
            Row(
                modifier = Modifier.fillMaxWidth().height(36.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ModeToggles()
                Spacer(Modifier.weight(1f))
                ToolButtons()
            }
        }
    } else {
        Row(
            modifier = modifier
                .fillMaxWidth()
                .background(PlayerEmptyBg)
                .padding(horizontal = 8.dp, vertical = 4.dp)
                .height(40.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PlaybackControls()
            Spacer(Modifier.width(12.dp))
            LayoutSelector()
            Spacer(Modifier.weight(1f))
            ModeToggles()
            Spacer(Modifier.width(4.dp))
            ToolButtons()
            Spacer(Modifier.width(4.dp))
            CloseButton()
        }
    }
}

@Composable
private fun BarButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String?,
    onClick: () -> Unit,
    highlight: Boolean = false,
) {
    Row(
        modifier = Modifier
            .background(
                color = if (highlight) Accent.copy(alpha = 0.85f) else OverlayScrim,
                shape = RoundedCornerShape(6.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (highlight) Color.Black else Color.White,
            modifier = Modifier.size(18.dp),
        )
        if (label != null) {
            Text(
                text = label,
                color = if (highlight) Color.Black else Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

@Composable
private fun LayoutChip(n: Int, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .background(
                color = if (selected) Accent else Color.Transparent,
                shape = RoundedCornerShape(4.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Text(
            text = n.toString(),
            color = if (selected) Color.Black else Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}
