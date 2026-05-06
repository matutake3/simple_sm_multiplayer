package jp.simplist.smmultiplayer.ui

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import jp.simplist.smmultiplayer.R
import jp.simplist.smmultiplayer.ui.theme.Accent
import jp.simplist.smmultiplayer.ui.theme.OverlayScrim
import jp.simplist.smmultiplayer.ui.theme.PlayerEmptyBg

@Composable
fun TopBar(
    layoutMode: Int,
    soloAudio: Boolean,
    syncPlayback: Boolean,
    isAnyPlaying: Boolean,
    onTogglePlayPause: () -> Unit,
    onClearAll: () -> Unit,
    onLayoutChange: (Int) -> Unit,
    onToggleSolo: () -> Unit,
    onToggleSync: () -> Unit,
    onOpenPresets: () -> Unit,
    onOpenSettings: () -> Unit,
    onLock: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Labels are only shown on tablet-class widths so the single-row of
    // controls always fits on phone portrait/landscape.
    val configuration = LocalConfiguration.current
    val showLabels = configuration.screenWidthDp >= 600
    // In portrait we collapse the layout-selector to a single ▦ icon that
    // pops a dropdown of 1〜4, otherwise the row 1 doesn't fit.
    val isPortrait = configuration.orientation == Configuration.ORIENTATION_PORTRAIT

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(PlayerEmptyBg)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        // Row 1: every control except the close button.
        // Cluster all items in the centre by sandwiching them between two
        // weight(1f) spacers — the leftover horizontal space splits evenly
        // into the left and right margins. This keeps the rightmost ⚙
        // Settings button safely away from the system gesture strip on the
        // right edge in landscape, and centres the row in portrait too.
        Row(
            modifier = Modifier.fillMaxWidth().height(36.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Spacer(Modifier.weight(1f))

            // Combined play / pause toggle — icon flips based on whether any
            // slot is currently playing. Single button keeps the row uncluttered.
            BarButton(
                icon = if (isAnyPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                label = if (showLabels) {
                    if (isAnyPlaying) stringResource(R.string.action_pause_all)
                    else stringResource(R.string.action_play_all)
                } else null,
                onClick = onTogglePlayPause,
            )
            BarButton(
                icon = Icons.Filled.DeleteSweep,
                label = stringResource(R.string.action_clear_all).takeIf { showLabels },
                onClick = onClearAll,
            )

            if (isPortrait) {
                LayoutDropdownButton(
                    layoutMode = layoutMode,
                    onLayoutChange = onLayoutChange,
                )
            } else {
                // Landscape: 1-4 chips inline (no leading icon, tight padding).
                Row(
                    modifier = Modifier
                        .background(OverlayScrim, RoundedCornerShape(6.dp))
                        .padding(horizontal = 2.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    for (n in 1..4) {
                        LayoutChip(
                            n = n,
                            selected = n == layoutMode,
                            onClick = { onLayoutChange(n) },
                        )
                    }
                }
            }

            BarButton(
                icon = if (syncPlayback) Icons.Filled.Sync else Icons.Filled.LinkOff,
                label = stringResource(R.string.action_sync_playback).takeIf { showLabels },
                onClick = onToggleSync,
                highlight = syncPlayback,
            )
            BarButton(
                // Single icon for both states — highlight indicates ON.
                // Headphones = "this is the audio source you're listening to".
                // Avoids the previous VolumeOff/VolumeUp pair which read as
                // "muted" on small screens.
                icon = Icons.Filled.Headphones,
                label = stringResource(R.string.action_solo_audio).takeIf { showLabels },
                onClick = onToggleSolo,
                highlight = soloAudio,
            )
            BarButton(icon = Icons.Filled.Bookmarks, label = null, onClick = onOpenPresets)
            // Lock button is placed BEFORE Settings so that ⚙ Settings remains
            // the rightmost button on Row 1.
            BarButton(icon = Icons.Filled.Lock, label = null, onClick = onLock)
            BarButton(icon = Icons.Filled.Settings, label = null, onClick = onOpenSettings)

            Spacer(Modifier.weight(1f))
        }

        // Row 2: full-width tappable bar to close the TopBar — anywhere on
        // the bar acts as the close affordance, with the chevron centred for
        // visual reinforcement.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(32.dp)
                .clickable(onClick = onClose),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.KeyboardArrowUp,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun BarButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String?,
    onClick: () -> Unit,
    highlight: Boolean = false,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
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

/**
 * Portrait-only collapsed layout selector: a single ▦ icon button (sized
 * identically to the other BarButtons) that pops a horizontal row of 1〜4
 * chips below itself.
 */
@Composable
private fun LayoutDropdownButton(
    layoutMode: Int,
    onLayoutChange: (Int) -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val density = LocalDensity.current
    Box {
        // Anchor button — exactly the same dimensions as a label-less BarButton
        // (icon-only with horizontal=8.dp / vertical=5.dp padding, 18.dp icon).
        Row(
            modifier = Modifier
                .background(OverlayScrim, RoundedCornerShape(6.dp))
                .clickable { menuOpen = !menuOpen }
                .padding(horizontal = 8.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Filled.GridView,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(18.dp),
            )
        }
        if (menuOpen) {
            Popup(
                alignment = Alignment.TopStart,
                onDismissRequest = { menuOpen = false },
                // Place the popup just below the button (button height ~28dp +
                // a 6dp gap looks balanced).
                offset = IntOffset(
                    x = 0,
                    y = with(density) { 34.dp.toPx() }.toInt(),
                ),
                properties = PopupProperties(focusable = true),
            ) {
                Row(
                    modifier = Modifier
                        .background(PlayerEmptyBg, RoundedCornerShape(6.dp))
                        .padding(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    for (n in 1..4) {
                        LayoutChip(
                            n = n,
                            selected = n == layoutMode,
                            onClick = {
                                onLayoutChange(n)
                                menuOpen = false
                            },
                        )
                    }
                }
            }
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
