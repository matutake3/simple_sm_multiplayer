package jp.simplist.smmultiplayer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
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
    onPlayAll: () -> Unit,
    onPauseAll: () -> Unit,
    onLayoutChange: (Int) -> Unit,
    onToggleSolo: () -> Unit,
    onOpenSettings: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Compact mode (icons only) when the bar would otherwise overflow — typically
    // any portrait phone width.
    val showLabels = LocalConfiguration.current.screenWidthDp >= 600

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(PlayerEmptyBg)
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .height(40.dp),
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

        Spacer(Modifier.width(12.dp))

        // Layout selector
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

        Spacer(Modifier.weight(1f))

        // Solo audio toggle
        BarButton(
            icon = if (soloAudio) Icons.Filled.VolumeOff else Icons.Filled.VolumeUp,
            label = if (showLabels) {
                if (soloAudio) stringResource(R.string.action_solo_audio_on)
                else stringResource(R.string.action_solo_audio_off)
            } else null,
            onClick = onToggleSolo,
            highlight = soloAudio,
        )
        Spacer(Modifier.width(4.dp))
        BarButton(
            icon = Icons.Filled.Settings,
            label = null,
            onClick = onOpenSettings,
        )
        Spacer(Modifier.width(4.dp))
        // Manual close (in addition to the 5s auto-hide).
        BarButton(
            icon = Icons.Filled.KeyboardArrowUp,
            label = null,
            onClick = onClose,
        )
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
