package jp.simplist.smmultiplayer.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import jp.simplist.smmultiplayer.R

@Composable
fun UsageGuideScreen(onBack: () -> Unit) {
    SettingsScaffold(
        title = stringResource(R.string.title_usage_guide),
        onBack = onBack,
    ) {
        val scrollState = rememberScrollState()
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .simpleVerticalScrollbar(scrollState)
                .padding(horizontal = 16.dp, vertical = 16.dp),
        ) {
            Topic(R.string.usage_intro_heading, R.string.usage_intro_body)
            Topic(R.string.usage_pick_heading, R.string.usage_pick_body)
            Topic(R.string.usage_layout_heading, R.string.usage_layout_body)
            Topic(R.string.usage_topbar_heading, R.string.usage_topbar_body)
            Topic(R.string.usage_gesture_heading, R.string.usage_gesture_body)
            Topic(R.string.usage_ab_heading, R.string.usage_ab_body)
            Topic(R.string.usage_sync_heading, R.string.usage_sync_body)
            Topic(R.string.usage_solo_heading, R.string.usage_solo_body)
            Topic(R.string.usage_speed_heading, R.string.usage_speed_body)
            Topic(R.string.usage_resize_heading, R.string.usage_resize_body)
            Topic(R.string.usage_preset_heading, R.string.usage_preset_body)
            Topic(R.string.usage_lock_heading, R.string.usage_lock_body)
            Spacer(Modifier.fillMaxWidth().height(32.dp))
        }
    }
}

@Composable
private fun Topic(headingRes: Int, bodyRes: Int) {
    Text(
        text = stringResource(headingRes),
        color = Color.White,
        fontSize = 16.sp,
        fontWeight = FontWeight.Medium,
        modifier = Modifier.padding(top = 16.dp, bottom = 6.dp),
    )
    Text(
        text = stringResource(bodyRes),
        color = Color.White.copy(alpha = 0.85f),
        fontSize = 14.sp,
        lineHeight = 22.sp,
    )
}
