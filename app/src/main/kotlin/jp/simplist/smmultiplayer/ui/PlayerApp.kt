package jp.simplist.smmultiplayer.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsIgnoringVisibility
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import jp.simplist.smmultiplayer.PlayerViewModel
import jp.simplist.smmultiplayer.R
import jp.simplist.smmultiplayer.ui.theme.PlayerBg
import kotlinx.coroutines.delay

private const val TOP_BAR_AUTO_HIDE_MS = 5_000L

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PlayerApp(viewModel: PlayerViewModel) {
    val slots by viewModel.slots.collectAsStateWithLifecycle()
    val layoutMode by viewModel.layoutMode.collectAsStateWithLifecycle()
    val soloAudio by viewModel.soloAudio.collectAsStateWithLifecycle()
    val soloIndex by viewModel.soloIndex.collectAsStateWithLifecycle()
    val showVol by viewModel.showVolumeIndicator.collectAsStateWithLifecycle()
    val showSeek by viewModel.showSeekIndicator.collectAsStateWithLifecycle()
    val ctrlAlways by viewModel.controlsAlwaysVisible.collectAsStateWithLifecycle()

    var settingsOpen by remember { mutableStateOf(false) }
    var presetsOpen by remember { mutableStateOf(false) }
    var clearAllConfirmOpen by remember { mutableStateOf(false) }
    var pickerForSlot by remember { mutableStateOf<Int?>(null) }

    // Overlay TopBar — hidden by default; pull down from the top edge to reveal.
    var topBarVisible by remember { mutableStateOf(false) }
    // Bumped on any TopBar interaction to reset the auto-hide timer.
    var interactionTick by remember { mutableIntStateOf(0) }
    val touch: () -> Unit = { interactionTick++ }

    LaunchedEffect(topBarVisible, interactionTick) {
        if (topBarVisible) {
            delay(TOP_BAR_AUTO_HIDE_MS)
            topBarVisible = false
        }
    }

    val pickVideoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        val target = pickerForSlot
        pickerForSlot = null
        if (uri != null && target != null) {
            viewModel.setVideoUri(target, uri)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(PlayerBg)
            // Top-edge swipe-down trigger. Runs in the Initial pass so that we can
            // intercept *only* gestures starting in the top 40dp zone with a clear
            // downward intent — every other gesture flows through to the cells.
            .pointerInput(Unit) {
                val triggerHeightPx = 40.dp.toPx()
                val swipeThresholdPx = 24.dp.toPx()
                awaitEachGesture {
                    val down = awaitFirstDown(
                        requireUnconsumed = false,
                        pass = PointerEventPass.Initial,
                    )
                    if (down.position.y > triggerHeightPx) return@awaitEachGesture
                    val pointerId = down.id
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val change = event.changes.firstOrNull { it.id == pointerId } ?: break
                        if (!change.pressed) break
                        val dy = change.position.y - down.position.y
                        if (dy > swipeThresholdPx) {
                            topBarVisible = true
                            interactionTick++
                            change.consume()
                            break
                        }
                    }
                }
            },
    ) {
        PlayerGrid(
            slots = slots,
            layoutMode = layoutMode,
            viewModel = viewModel,
            showVolumeIndicator = showVol,
            showSeekIndicator = showSeek,
            controlsAlwaysVisible = ctrlAlways,
            soloAudio = soloAudio,
            soloIndex = soloIndex,
            onPickForSlot = { idx ->
                pickerForSlot = idx
                pickVideoLauncher.launch(arrayOf("video/*"))
            },
            modifier = Modifier.fillMaxSize(),
        )

        AnimatedVisibility(
            visible = topBarVisible,
            enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
            // Use *IgnoringVisibility so the offset is constant regardless of
            // whether the system status bar is currently shown, hidden, or in a
            // transient reveal — this keeps the TopBar consistently anchored
            // below the status bar slot across repeated open/close cycles.
            modifier = Modifier
                .align(Alignment.TopCenter)
                .windowInsetsPadding(WindowInsets.statusBarsIgnoringVisibility),
        ) {
            TopBar(
                layoutMode = layoutMode,
                soloAudio = soloAudio,
                onPlayAll = { viewModel.playAll(); touch() },
                onPauseAll = { viewModel.pauseAll(); touch() },
                onClearAll = { clearAllConfirmOpen = true; touch() },
                onLayoutChange = { viewModel.setLayoutMode(it); touch() },
                onToggleSolo = { viewModel.toggleSoloAudio(); touch() },
                onOpenPresets = { presetsOpen = true; touch() },
                onOpenSettings = { settingsOpen = true; touch() },
                onClose = { topBarVisible = false },
            )
        }
    }

    if (settingsOpen) {
        SettingsDialog(
            showVolumeIndicator = showVol,
            showSeekIndicator = showSeek,
            controlsAlwaysVisible = ctrlAlways,
            onShowVolumeIndicator = { viewModel.setShowVolumeIndicator(it) },
            onShowSeekIndicator = { viewModel.setShowSeekIndicator(it) },
            onControlsAlwaysVisible = { viewModel.setControlsAlwaysVisible(it) },
            onDismiss = { settingsOpen = false },
        )
    }

    if (clearAllConfirmOpen) {
        ConfirmDialog(
            title = stringResource(R.string.dialog_clear_all_title),
            message = stringResource(R.string.dialog_clear_all_message),
            confirmLabel = stringResource(R.string.action_clear_all),
            onConfirm = {
                viewModel.clearAllVideos()
                clearAllConfirmOpen = false
            },
            onDismiss = { clearAllConfirmOpen = false },
        )
    }

    if (presetsOpen) {
        PresetsDialog(
            viewModel = viewModel,
            onDismiss = { presetsOpen = false },
        )
    }
}
