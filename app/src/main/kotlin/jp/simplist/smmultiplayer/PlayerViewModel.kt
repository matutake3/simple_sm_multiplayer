package jp.simplist.smmultiplayer

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import jp.simplist.smmultiplayer.data.SettingsRepository
import jp.simplist.smmultiplayer.player.PlayerSlotState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

const val SLOT_COUNT = 4

@OptIn(UnstableApi::class)
class PlayerViewModel(app: Application) : AndroidViewModel(app) {
    private val context = app.applicationContext
    private val repo = SettingsRepository(context)

    /**
     * Aggressively reduce LoadControl buffers — defaults are 50 s of compressed
     * buffer per player (≈25-50 MB for 1080p), so 4 simultaneous players blow
     * past the 384 MB heap and cause OutOfMemoryError. For local file playback
     * we don't need much buffer ahead.
     */
    private val sharedLoadControl: DefaultLoadControl =
        DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                // ExoPlayer constraints: minBuffer >= max(bufferForPlayback,
                // bufferForPlaybackAfterRebuffer); maxBuffer >= minBuffer.
                //
                // Local-file playback fills buffers very quickly, so we keep
                // the playback-resume thresholds low. This makes seek feel
                // snappy: after release, ExoPlayer only needs ~500 ms of
                // re-buffered data before it resumes playing instead of the
                // 2.5 s we used originally.
                /* minBufferMs = */ 2_500,
                /* maxBufferMs = */ 5_000,
                /* bufferForPlaybackMs = */ 500,
                /* bufferForPlaybackAfterRebufferMs = */ 500,
            )
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()

    private val players: List<ExoPlayer> = List(SLOT_COUNT) {
        ExoPlayer.Builder(context)
            .setLoadControl(sharedLoadControl)
            .build()
            .apply {
                playWhenReady = false
                repeatMode = Player.REPEAT_MODE_ONE
            }
    }

    fun getPlayer(index: Int): ExoPlayer = players[index]

    /**
     * State for the MediaCodec-release optimisation. When a slot becomes
     * invisible (because the user shrinks the layout), we tear down its
     * MediaCodec instance to free memory; on re-show we restore from the
     * cached position.
     */
    private val cachedPositions = LongArray(SLOT_COUNT) { 0L }
    private val isStopped = BooleanArray(SLOT_COUNT) { false }

    /**
     * Tracks which slots currently show their on-cell controls overlay. Position
     * polling skips slots whose controls are hidden so we don't burn CPU
     * recomposing seekbars nobody can see.
     */
    private val visibleControlsByIndex = BooleanArray(SLOT_COUNT) { false }

    private val _slots = MutableStateFlow(List(SLOT_COUNT) { PlayerSlotState(index = it) })
    val slots: StateFlow<List<PlayerSlotState>> = _slots.asStateFlow()

    val showVolumeIndicator: StateFlow<Boolean> =
        repo.showVolumeIndicator.stateIn(viewModelScope, SharingStarted.Eagerly, true)
    val showSeekIndicator: StateFlow<Boolean> =
        repo.showSeekIndicator.stateIn(viewModelScope, SharingStarted.Eagerly, true)
    val controlsAlwaysVisible: StateFlow<Boolean> =
        repo.controlsAlwaysVisible.stateIn(viewModelScope, SharingStarted.Eagerly, false)
    val layoutMode: StateFlow<Int> =
        repo.layoutMode.stateIn(viewModelScope, SharingStarted.Eagerly, 1)
    val soloAudio: StateFlow<Boolean> =
        repo.soloAudio.stateIn(viewModelScope, SharingStarted.Eagerly, false)
    val soloIndex: StateFlow<Int> =
        repo.soloIndex.stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    init {
        // Player listeners → state
        players.forEachIndexed { idx, player ->
            player.addListener(object : Player.Listener {
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    updateSlot(idx) { it.copy(isPlaying = isPlaying) }
                }

                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (playbackState == Player.STATE_READY) {
                        updateSlot(idx) {
                            it.copy(
                                durationMs = player.duration.coerceAtLeast(0L),
                                isReady = true,
                            )
                        }
                    } else if (playbackState == Player.STATE_ENDED) {
                        // REPEAT_MODE_ONE handles loop, so this rarely fires.
                    }
                }

                override fun onPlayerError(error: PlaybackException) {
                    // Surface the error in logcat instead of crashing the app.
                    Log.e(
                        "PlayerViewModel",
                        "Slot $idx playback error (${error.errorCodeName})",
                        error,
                    )
                    updateSlot(idx) { it.copy(isPlaying = false, isReady = false) }
                }
            })
        }

        // Restore persisted URIs.
        viewModelScope.launch {
            val saved = withContext(Dispatchers.IO) {
                (0 until SLOT_COUNT).map { repo.savedUri(it).first() }
            }
            saved.forEachIndexed { i, uri ->
                if (!uri.isNullOrEmpty()) {
                    runCatching { setVideoUri(i, Uri.parse(uri), persist = false) }
                }
            }
        }

        // React to layout-mode changes:
        //  - Slots becoming visible: ensure the player is prepared (re-allocate
        //    MediaCodec and seek to cached position if it had been torn down).
        //  - Slots becoming hidden: pause + tear down MediaCodec to free memory.
        viewModelScope.launch {
            layoutMode.collect { mode ->
                for (i in 0 until SLOT_COUNT) {
                    if (i < mode) {
                        ensurePrepared(i)
                    } else {
                        players[i].pause()
                        stopAndCache(i)
                    }
                }
            }
        }

        // Apply effective volume per slot (respect solo audio mode).
        viewModelScope.launch {
            combine(_slots, soloAudio, soloIndex) { s, solo, sIdx ->
                Triple(s, solo, sIdx)
            }.collect { (slotList, solo, sIdx) ->
                slotList.forEach { slot ->
                    val effective = if (solo) {
                        if (slot.index == sIdx) slot.volume else 0f
                    } else {
                        slot.volume
                    }
                    players[slot.index].volume = effective.coerceIn(0f, 1f)
                }
            }
        }

        // Periodic position polling. Wrap each tick — a transient ExoPlayer
        // exception here must NOT take down the entire ViewModel. Skip slots
        // whose controls overlay is hidden (or whose codec is torn down): the
        // seekbar that would consume positionMs isn't on-screen, so updating
        // would just trigger wasted recomposition.
        viewModelScope.launch {
            while (true) {
                delay(200L)
                runCatching {
                    val mode = layoutMode.value
                    val current = _slots.value
                    val next = current.toMutableList()
                    var dirty = false
                    for (i in 0 until SLOT_COUNT) {
                        if (i < mode && visibleControlsByIndex[i] && !isStopped[i]) {
                            val newPos = players[i].currentPosition
                            if (next[i].positionMs != newPos) {
                                next[i] = next[i].copy(positionMs = newPos)
                                dirty = true
                            }
                        }
                    }
                    if (dirty) _slots.value = next
                }.onFailure {
                    Log.w("PlayerViewModel", "Position poll tick failed", it)
                }
            }
        }
    }

    fun setVideoUri(slotIndex: Int, uri: Uri, persist: Boolean = true) {
        val player = players[slotIndex]
        runCatching {
            player.setMediaItem(MediaItem.fromUri(uri))
            player.prepare()
        }.onFailure {
            Log.e("PlayerViewModel", "Slot $slotIndex setMediaItem/prepare failed", it)
            return
        }
        // A fresh MediaItem invalidates any cached "stopped" state.
        isStopped[slotIndex] = false
        cachedPositions[slotIndex] = 0L
        val title = queryDisplayName(uri)
        updateSlot(slotIndex) {
            it.copy(uri = uri, title = title, durationMs = 0, positionMs = 0, isReady = false)
        }
        if (persist) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            viewModelScope.launch { repo.setUri(slotIndex, uri.toString()) }
        }
    }

    fun clearVideo(slotIndex: Int) {
        val player = players[slotIndex]
        player.stop()
        player.clearMediaItems()
        isStopped[slotIndex] = false
        cachedPositions[slotIndex] = 0L
        updateSlot(slotIndex) { PlayerSlotState(index = slotIndex) }
        viewModelScope.launch { repo.setUri(slotIndex, null) }
    }

    /**
     * Tear down the MediaCodec instance for a hidden slot to free memory,
     * remembering the playback position so it can be restored on re-show.
     * No-op if the slot has no media or is already stopped.
     */
    private fun stopAndCache(idx: Int) {
        if (isStopped[idx]) return
        val player = players[idx]
        if (_slots.value[idx].uri == null) {
            // No media to remember. Just stop to be safe.
            runCatching { player.stop() }
            return
        }
        cachedPositions[idx] = player.currentPosition
        runCatching {
            player.stop()
            player.clearMediaItems()
        }
        isStopped[idx] = true
    }

    /**
     * Re-prepare a previously-stopped slot, seeking back to its cached
     * playback position. No-op if the slot was never stopped or has no URI.
     */
    private fun ensurePrepared(idx: Int) {
        if (!isStopped[idx]) return
        val uri = _slots.value[idx].uri
        if (uri == null) {
            isStopped[idx] = false
            return
        }
        val player = players[idx]
        runCatching {
            // Pass the cached position into the MediaItem so prepare resumes
            // there directly instead of starting at 0 and seeking afterwards.
            player.setMediaItem(MediaItem.fromUri(uri), cachedPositions[idx])
            player.prepare()
        }.onFailure {
            Log.e("PlayerViewModel", "Slot $idx ensurePrepared failed", it)
        }
        isStopped[idx] = false
    }

    /**
     * Called by [PlayerCell] whenever its on-cell controls overlay shows or
     * hides. Used to gate position polling on slots with no visible seekbar.
     */
    fun setControlsVisible(idx: Int, visible: Boolean) {
        if (idx !in 0 until SLOT_COUNT) return
        visibleControlsByIndex[idx] = visible
        if (visible && !isStopped[idx]) {
            // Refresh the seekbar immediately on open so the user sees the
            // current position rather than the last value from when the
            // controls were last visible.
            updateSlot(idx) { it.copy(positionMs = players[idx].currentPosition) }
        }
    }

    fun togglePlay(slotIndex: Int) {
        val p = players[slotIndex]
        if (p.isPlaying) p.pause() else p.play()
    }

    fun playAll() {
        val mode = layoutMode.value
        for (i in 0 until mode) {
            if (_slots.value[i].uri != null) players[i].play()
        }
    }

    fun pauseAll() {
        for (i in 0 until SLOT_COUNT) players[i].pause()
    }

    fun seekTo(slotIndex: Int, positionMs: Long) {
        val p = players[slotIndex]
        val target = positionMs.coerceIn(0L, p.duration.coerceAtLeast(0L))
        p.seekTo(target)
        updateSlot(slotIndex) { it.copy(positionMs = target) }
    }

    fun skipBy(slotIndex: Int, deltaMs: Long) {
        val p = players[slotIndex]
        val target = (p.currentPosition + deltaMs).coerceIn(0L, p.duration.coerceAtLeast(0L))
        p.seekTo(target)
        updateSlot(slotIndex) { it.copy(positionMs = target) }
    }

    fun setVolume(slotIndex: Int, v: Float) {
        updateSlot(slotIndex) { it.copy(volume = v.coerceIn(0f, 1f)) }
    }

    fun setLayoutMode(n: Int) {
        viewModelScope.launch { repo.setLayoutMode(n) }
    }

    fun toggleSoloAudio() {
        viewModelScope.launch { repo.setSoloAudio(!soloAudio.value) }
    }

    fun selectSoloAudioSlot(slotIndex: Int) {
        viewModelScope.launch { repo.setSoloIndex(slotIndex) }
    }

    fun setShowVolumeIndicator(v: Boolean) =
        viewModelScope.launch { repo.setShowVolumeIndicator(v) }.let { Unit }

    fun setShowSeekIndicator(v: Boolean) =
        viewModelScope.launch { repo.setShowSeekIndicator(v) }.let { Unit }

    fun setControlsAlwaysVisible(v: Boolean) =
        viewModelScope.launch { repo.setControlsAlwaysVisible(v) }.let { Unit }

    private fun updateSlot(idx: Int, transform: (PlayerSlotState) -> PlayerSlotState) {
        val list = _slots.value.toMutableList()
        list[idx] = transform(list[idx])
        _slots.value = list
    }

    private fun queryDisplayName(uri: Uri): String? = runCatching {
        context.contentResolver.query(
            uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null
        )?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    }.getOrNull()

    override fun onCleared() {
        super.onCleared()
        players.forEach { it.release() }
    }
}
