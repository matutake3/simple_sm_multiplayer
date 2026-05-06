package jp.simplist.smmultiplayer.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import jp.simplist.smmultiplayer.BuildConfig
import jp.simplist.smmultiplayer.R

/**
 * Full-screen settings overlay that mirrors the layout convention used by
 * the rest of the Morphyca apps: top-of-screen Usage Guide / FAQ shortcuts,
 * followed by toggles grouped into sections.
 */
@Composable
fun SettingsScreen(
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
    onOpenUsageGuide: () -> Unit,
    onOpenFaq: () -> Unit,
    onBack: () -> Unit,
) {
    SettingsScaffold(
        title = stringResource(R.string.title_settings),
        onBack = onBack,
    ) {
        val scrollState = rememberScrollState()
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .simpleVerticalScrollbar(scrollState)
                .padding(bottom = 32.dp),
        ) {
            // Top-level shortcuts (chevron rows), exactly like other Morphyca apps.
            SettingsNavigationRow(
                title = stringResource(R.string.title_usage_guide),
                onClick = onOpenUsageGuide,
            )
            SettingsNavigationRow(
                title = stringResource(R.string.title_faq),
                onClick = onOpenFaq,
            )

            // ---- Section: 表示 ----
            SettingsSectionHeader(stringResource(R.string.settings_section_display))
            SettingsToggleRow(
                title = stringResource(R.string.setting_volume_indicator),
                summary = stringResource(R.string.setting_volume_indicator_summary),
                checked = showVolumeIndicator,
                onCheckedChange = onShowVolumeIndicator,
            )
            SettingsToggleRow(
                title = stringResource(R.string.setting_seek_indicator),
                summary = stringResource(R.string.setting_seek_indicator_summary),
                checked = showSeekIndicator,
                onCheckedChange = onShowSeekIndicator,
            )
            SettingsToggleRow(
                title = stringResource(R.string.setting_controls_always_visible),
                summary = stringResource(R.string.setting_controls_always_visible_summary),
                checked = controlsAlwaysVisible,
                onCheckedChange = onControlsAlwaysVisible,
            )

            // ---- Section: 操作 ----
            SettingsSectionHeader(stringResource(R.string.settings_section_gesture))
            SettingsToggleRow(
                title = stringResource(R.string.setting_volume_gesture),
                summary = stringResource(R.string.setting_volume_gesture_summary),
                checked = volumeGesture,
                onCheckedChange = onVolumeGesture,
            )
            SettingsToggleRow(
                title = stringResource(R.string.setting_seek_gesture),
                summary = stringResource(R.string.setting_seek_gesture_summary),
                checked = seekGesture,
                onCheckedChange = onSeekGesture,
            )

            // ---- Section: 再生 ----
            SettingsSectionHeader(stringResource(R.string.settings_section_playback))
            SettingsToggleRow(
                title = stringResource(R.string.setting_auto_loop),
                summary = stringResource(R.string.setting_auto_loop_summary),
                checked = autoLoop,
                onCheckedChange = onAutoLoop,
            )
            SettingsToggleRow(
                title = stringResource(R.string.setting_sync_speed),
                summary = stringResource(R.string.setting_sync_speed_summary),
                checked = syncSpeed,
                onCheckedChange = onSyncSpeed,
            )
            SettingsToggleRow(
                title = stringResource(R.string.setting_fast_seek),
                summary = stringResource(R.string.setting_fast_seek_summary),
                checked = fastSeek,
                onCheckedChange = onFastSeek,
            )

            // ---- Section: アプリ情報 ----
            SettingsSectionHeader(stringResource(R.string.settings_section_app_info))
            SettingsInfoRow(
                title = stringResource(R.string.settings_app_version),
                value = BuildConfig.VERSION_NAME,
            )

            Spacer(modifier = Modifier.fillMaxWidth().height(24.dp))
        }
    }
}
