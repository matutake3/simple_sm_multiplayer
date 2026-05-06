package jp.simplist.smmultiplayer

import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.mutableLongStateOf
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import jp.simplist.smmultiplayer.billing.BillingManager
import jp.simplist.smmultiplayer.ui.PlayerApp
import jp.simplist.smmultiplayer.ui.theme.SimpleSmMultiplayerTheme
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val viewModel: PlayerViewModel by viewModels()

    /**
     * Bumped on every onResume() and on a verified purchase callback so any
     * Composable observing trial state re-reads from TrialManager. We don't
     * need a Flow because TrialManager.state() is a cheap pure call against
     * SharedPreferences that have already been backed by Auto Backup.
     */
    private val trialTick = mutableLongStateOf(0L)

    private lateinit var billing: BillingManager

    /**
     * Cached "should-block-volume-keys" flag, refreshed from the ViewModel.
     * Read synchronously inside [dispatchKeyEvent] to decide whether to swallow
     * VOLUME_UP / VOLUME_DOWN events. Set true when either:
     *   - the "音量ボタンを無効化" setting is on, or
     *   - the screen is currently in lock-mode.
     */
    @Volatile
    private var blockVolumeKeys: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        // Hide status & nav bars; user can swipe from edge to transiently reveal them.
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        billing = BillingManager(this) {
            // Successful purchase → bump tick so PlayerApp re-evaluates state.
            trialTick.longValue = trialTick.longValue + 1
        }
        billing.connect()

        // Keep blockVolumeKeys in sync with the ViewModel state. Combine of two
        // StateFlows means the latest of either flips the local cache.
        lifecycleScope.launch {
            combine(viewModel.disableVolumeKeys, viewModel.locked) { d, l -> d || l }
                .collect { blockVolumeKeys = it }
        }

        setContent {
            SimpleSmMultiplayerTheme {
                PlayerApp(
                    viewModel = viewModel,
                    billing = billing,
                    trialTick = trialTick,
                    onCloseApp = { finish() },
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Time-based trial state can change while the activity is paused
        // (user crossed the 24h boundary). Re-pull on every resume.
        trialTick.longValue = trialTick.longValue + 1
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (blockVolumeKeys && (event.keyCode == KeyEvent.KEYCODE_VOLUME_UP ||
                event.keyCode == KeyEvent.KEYCODE_VOLUME_DOWN)) {
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onPause() {
        super.onPause()
        // Pause all videos when activity goes to background.
        viewModel.pauseAll()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            WindowInsetsControllerCompat(window, window.decorView).apply {
                hide(WindowInsetsCompat.Type.systemBars())
                systemBarsBehavior =
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::billing.isInitialized) billing.release()
    }
}
