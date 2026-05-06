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
fun FaqScreen(onBack: () -> Unit) {
    SettingsScaffold(
        title = stringResource(R.string.title_faq),
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
            QA(R.string.faq_q_format, R.string.faq_a_format)
            QA(R.string.faq_q_stutter, R.string.faq_a_stutter)
            QA(R.string.faq_q_seek, R.string.faq_a_seek)
            QA(R.string.faq_q_io_contention, R.string.faq_a_io_contention)
            QA(R.string.faq_q_loop, R.string.faq_a_loop)
            QA(R.string.faq_q_audio, R.string.faq_a_audio)
            QA(R.string.faq_q_preset, R.string.faq_a_preset)
            QA(R.string.faq_q_oom, R.string.faq_a_oom)
            Spacer(Modifier.fillMaxWidth().height(32.dp))
        }
    }
}

@Composable
private fun QA(questionRes: Int, answerRes: Int) {
    Text(
        text = stringResource(questionRes),
        color = Color.White,
        fontSize = 15.sp,
        fontWeight = FontWeight.Medium,
        modifier = Modifier.padding(top = 16.dp, bottom = 6.dp),
    )
    Text(
        text = stringResource(answerRes),
        color = Color.White.copy(alpha = 0.85f),
        fontSize = 13.sp,
        lineHeight = 20.sp,
    )
}
