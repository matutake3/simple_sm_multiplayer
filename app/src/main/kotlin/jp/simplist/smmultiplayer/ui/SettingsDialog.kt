package jp.simplist.smmultiplayer.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import jp.simplist.smmultiplayer.R

@Composable
fun SettingsDialog(
    showVolumeIndicator: Boolean,
    showSeekIndicator: Boolean,
    controlsAlwaysVisible: Boolean,
    fastSeek: Boolean,
    syncSpeed: Boolean,
    autoLoop: Boolean,
    volumeGesture: Boolean,
    seekGesture: Boolean,
    onShowVolumeIndicator: (Boolean) -> Unit,
    onShowSeekIndicator: (Boolean) -> Unit,
    onControlsAlwaysVisible: (Boolean) -> Unit,
    onFastSeek: (Boolean) -> Unit,
    onSyncSpeed: (Boolean) -> Unit,
    onAutoLoop: (Boolean) -> Unit,
    onVolumeGesture: (Boolean) -> Unit,
    onSeekGesture: (Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    val scrollState = rememberScrollState()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.title_settings)) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 400.dp)
                    .verticalScroll(scrollState)
                    .simpleVerticalScrollbar(scrollState),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SettingRow(
                    title = stringResource(R.string.setting_volume_indicator),
                    summary = stringResource(R.string.setting_volume_indicator_summary),
                    checked = showVolumeIndicator,
                    onCheckedChange = onShowVolumeIndicator,
                )
                SettingRow(
                    title = stringResource(R.string.setting_seek_indicator),
                    summary = stringResource(R.string.setting_seek_indicator_summary),
                    checked = showSeekIndicator,
                    onCheckedChange = onShowSeekIndicator,
                )
                SettingRow(
                    title = stringResource(R.string.setting_controls_always_visible),
                    summary = stringResource(R.string.setting_controls_always_visible_summary),
                    checked = controlsAlwaysVisible,
                    onCheckedChange = onControlsAlwaysVisible,
                )
                SettingRow(
                    title = stringResource(R.string.setting_volume_gesture),
                    summary = stringResource(R.string.setting_volume_gesture_summary),
                    checked = volumeGesture,
                    onCheckedChange = onVolumeGesture,
                )
                SettingRow(
                    title = stringResource(R.string.setting_seek_gesture),
                    summary = stringResource(R.string.setting_seek_gesture_summary),
                    checked = seekGesture,
                    onCheckedChange = onSeekGesture,
                )
                SettingRow(
                    title = stringResource(R.string.setting_sync_speed),
                    summary = stringResource(R.string.setting_sync_speed_summary),
                    checked = syncSpeed,
                    onCheckedChange = onSyncSpeed,
                )
                SettingRow(
                    title = stringResource(R.string.setting_auto_loop),
                    summary = stringResource(R.string.setting_auto_loop_summary),
                    checked = autoLoop,
                    onCheckedChange = onAutoLoop,
                )
                SettingRow(
                    title = stringResource(R.string.setting_fast_seek),
                    summary = stringResource(R.string.setting_fast_seek_summary),
                    checked = fastSeek,
                    onCheckedChange = onFastSeek,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) }
        },
    )
}

@Composable
private fun SettingRow(
    title: String,
    summary: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(2.dp))
            Text(summary, fontSize = 11.sp)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
