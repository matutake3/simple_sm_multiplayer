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
import androidx.compose.foundation.layout.width
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
    onPlayAll: () -> Unit,
    onPauseAll: () -> Unit,
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
        Row(
            modifier = Modifier.fillMaxWidth().height(36.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
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

            Spacer(Modifier.width(8.dp))

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

            Spacer(Modifier.width(8.dp))

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
                // Single icon for both states — highlight indicates ON.
                // Headphones = "this is the audio source you're listening to".
                // Avoids the previous VolumeOff/VolumeUp pair which read as
                // "muted" on small screens.
                icon = Icons.Filled.Headphones,
                label = if (showLabels) {
                    if (soloAudio) stringResource(R.string.action_solo_audio_on)
                    else stringResource(R.string.action_solo_audio_off)
                } else null,
                onClick = onToggleSolo,
                highlight = soloAudio,
            )
            Spacer(Modifier.width(4.dp))
            BarButton(icon = Icons.Filled.Bookmarks, label = null, onClick = onOpenPresets)
            Spacer(Modifier.width(4.dp))
            // Lock button is placed BEFORE Settings so that ⚙ Settings remains
            // the rightmost button on Row 1 (per the user's design intent —
            // Settings must always sit at the inner-rightmost position, with
            // empty trailing space to avoid the system gesture strip).
            BarButton(icon = Icons.Filled.Lock, label = null, onClick = onLock)
            Spacer(Modifier.width(4.dp))
            BarButton(icon = Icons.Filled.Settings, label = null, onClick = onOpenSettings)

            // Trailing flex space so any leftover width sits on the right —
            // keeps left-alignment of the control row consistent across widths.
            Spacer(Modifier.weight(1f))
        }

        // Row 2: only the close button, centred. (No other elements here —
        // anything additional would either offset the close button or land
        // on top of the system gesture area, neither of which is desired.)
        Row(
            modifier = Modifier.fillMaxWidth().height(32.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Spacer(Modifier.weight(1f))
            BarButton(
                icon = Icons.Filled.KeyboardArrowUp,
                label = null,
                onClick = onClose,
            )
            Spacer(Modifier.weight(1f))
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
