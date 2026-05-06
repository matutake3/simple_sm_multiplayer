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
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.SeekParameters
import jp.simplist.smmultiplayer.data.LayoutPreset
import jp.simplist.smmultiplayer.data.PresetSlot
import jp.simplist.smmultiplayer.data.SettingsRepository
import jp.simplist.smmultiplayer.data.toJsonArray
import jp.simplist.smmultiplayer.data.toPresetList
import jp.simplist.smmultiplayer.player.PlayerSlotState
import jp.simplist.smmultiplayer.player.ResizeMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
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

    /**
     * Resolve the underlying ExoPlayer driving the given UI slot. Honours
     * [PlayerSlotState.playerIndex] so that drag-and-drop reorder
     * (which swaps the playerIndex between two slots) is an instant remap
     * rather than a costly URI reload.
     */
    fun getPlayer(slotIndex: Int): ExoPlayer =
        players[_slots.value[slotIndex].playerIndex]

    /** Same lookup as [getPlayer] but used internally where intent is clearer. */
    private fun playerOf(slotIndex: Int): ExoPlayer =
        players[_slots.value[slotIndex].playerIndex]

    /**
     * Reverse map: which slot does player [pIdx] currently drive? Used by
     * ExoPlayer listeners that fire by player identity but need to update
     * the corresponding slot's UI state.
     */
    private fun slotForPlayer(pIdx: Int): Int =
        _slots.value.indexOfFirst { it.playerIndex == pIdx }

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
    val fastSeek: StateFlow<Boolean> =
        repo.fastSeek.stateIn(viewModelScope, SharingStarted.Eagerly, false)
    val syncSpeed: StateFlow<Boolean> =
        repo.syncSpeed.stateIn(viewModelScope, SharingStarted.Eagerly, false)
    val autoLoop: StateFlow<Boolean> =
        repo.autoLoop.stateIn(viewModelScope, SharingStarted.Eagerly, true)
    val volumeGesture: StateFlow<Boolean> =
        repo.volumeGesture.stateIn(viewModelScope, SharingStarted.Eagerly, true)
    val seekGesture: StateFlow<Boolean> =
        repo.seekGesture.stateIn(viewModelScope, SharingStarted.Eagerly, true)
    val disableVolumeKeys: StateFlow<Boolean> =
        repo.disableVolumeKeys.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    /** Ephemeral lock-mode flag (not persisted). */
    private val _locked = MutableStateFlow(false)
    val locked: StateFlow<Boolean> = _locked.asStateFlow()

    /**
     * True iff any visible slot is currently playing. Powers the combined
     * play/pause toggle button on the TopBar — one button instead of two,
     * icon flips based on this value.
     */
    val anyPlaying: StateFlow<Boolean> =
        _slots.map { list -> list.any { it.isPlaying } }
            .stateIn(viewModelScope, SharingStarted.Eagerly, false)
    val layoutMode: StateFlow<Int> =
        repo.layoutMode.stateIn(viewModelScope, SharingStarted.Eagerly, 1)
    val soloAudio: StateFlow<Boolean> =
        repo.soloAudio.stateIn(viewModelScope, SharingStarted.Eagerly, false)
    val soloIndex: StateFlow<Int> =
        repo.soloIndex.stateIn(viewModelScope, SharingStarted.Eagerly, 0)
    val syncPlayback: StateFlow<Boolean> =
        repo.syncPlayback.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val presets: StateFlow<List<LayoutPreset>> =
        repo.presetsJson
            .map { it.toPresetList() }
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    init {
        // Player listeners → state. Listeners fire keyed by *player* index;
        // we translate to the current *slot* index via slotForPlayer() so
        // post-swap reorders update the correct UI slot.
        players.forEachIndexed { pIdx, player ->
            player.addListener(object : Player.Listener {
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    val sIdx = slotForPlayer(pIdx)
                    if (sIdx >= 0) updateSlot(sIdx) { it.copy(isPlaying = isPlaying) }
                }

                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (playbackState == Player.STATE_READY) {
                        val sIdx = slotForPlayer(pIdx)
                        if (sIdx >= 0) updateSlot(sIdx) {
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
                    val sIdx = slotForPlayer(pIdx)
                    Log.e(
                        "PlayerViewModel",
                        "Player $pIdx (slot $sIdx) playback error (${error.errorCodeName})",
                        error,
                    )
                    if (sIdx >= 0) {
                        updateSlot(sIdx) { it.copy(isPlaying = false, isReady = false) }
                    }
                }
            })
        }

        // Restore persisted URIs, resize modes and playback speeds.
        viewModelScope.launch {
            data class Saved(val uri: String?, val resizeMode: Int, val speed: Float)
            val saved = withContext(Dispatchers.IO) {
                (0 until SLOT_COUNT).map { i ->
                    Saved(
                        uri = repo.savedUri(i).first(),
                        resizeMode = repo.savedResizeMode(i).first(),
                        speed = repo.savedPlaybackSpeed(i).first(),
                    )
                }
            }
            saved.forEachIndexed { i, s ->
                if (s.resizeMode != 0) {
                    updateSlot(i) { it.copy(resizeMode = s.resizeMode) }
                }
                if (s.speed != 1f) {
                    updateSlot(i) { it.copy(playbackSpeed = s.speed) }
                    runCatching {
                        playerOf(i).playbackParameters = PlaybackParameters(s.speed)
                    }
                }
                if (!s.uri.isNullOrEmpty()) {
                    runCatching { setVideoUri(i, Uri.parse(s.uri), persist = false) }
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
                        playerOf(i).pause()
                        stopAndCache(i)
                    }
                }
            }
        }

        // Apply seek-parameters mode (exact vs nearest-keyframe) to all
        // ExoPlayers whenever the toggle changes.
        viewModelScope.launch {
            fastSeek.collect { fast ->
                val params = if (fast) SeekParameters.CLOSEST_SYNC else SeekParameters.DEFAULT
                players.forEach { runCatching { it.setSeekParameters(params) } }
            }
        }

        // Apply auto-loop (REPEAT_MODE_ONE) toggle to all ExoPlayers.
        viewModelScope.launch {
            autoLoop.collect { loop ->
                val mode = if (loop) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
                players.forEach { runCatching { it.repeatMode = mode } }
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
                    // Volume is applied to whichever player is currently
                    // driving this slot (post-reorder this may differ from
                    // slot.index).
                    players[slot.playerIndex].volume = effective.coerceIn(0f, 1f)
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
                        if (i < mode && !isStopped[i]) {
                            val slot = next[i]
                            val player = players[slot.playerIndex]
                            val newPos = player.currentPosition
                            // A/B-loop boundary: if past B, jump back to A.
                            // This must run regardless of controls overlay
                            // visibility — the loop should keep working even
                            // when the user has dismissed the cell controls.
                            val a = slot.loopAMs
                            val b = slot.loopBMs
                            if (slot.loopEnabled && a != null && b != null && a < b && newPos >= b) {
                                player.seekTo(a)
                                next[i] = slot.copy(positionMs = a)
                                dirty = true
                            } else if (visibleControlsByIndex[i] && slot.positionMs != newPos) {
                                next[i] = slot.copy(positionMs = newPos)
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
        val player = playerOf(slotIndex)
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
            it.copy(
                uri = uri,
                title = title,
                durationMs = 0,
                positionMs = 0,
                isReady = false,
                // Loop bounds are tied to the previous video — drop them.
                loopAMs = null,
                loopBMs = null,
                loopEnabled = false,
            )
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
        // Preserve the post-reorder playerIndex assignment so that clearing
        // a swapped slot doesn't undo the visual mapping.
        val pIdx = _slots.value[slotIndex].playerIndex
        val player = players[pIdx]
        player.stop()
        player.clearMediaItems()
        // Reset playback parameters to default so a fresh video doesn't
        // inherit the previous slot occupant's speed setting.
        runCatching { player.playbackParameters = PlaybackParameters(1f) }
        isStopped[slotIndex] = false
        cachedPositions[slotIndex] = 0L
        updateSlot(slotIndex) {
            PlayerSlotState(index = slotIndex, playerIndex = pIdx)
        }
        viewModelScope.launch {
            repo.setUri(slotIndex, null)
            repo.setPlaybackSpeed(slotIndex, 1f)
        }
    }

    /** Clear every slot's video at once. */
    fun clearAllVideos() {
        for (i in 0 until SLOT_COUNT) clearVideo(i)
    }

    /**
     * Swap the contents of two slots. Implemented as an instant remap of
     * which underlying ExoPlayer drives each slot — the players stay
     * running with their current MediaItem, so no codec teardown / rebuffer
     * happens. The *visible* (`index`) field of each slot is preserved so
     * UI position stays anchored; everything else (uri / title / position /
     * volume / resize / speed / loop / isPlaying / playerIndex / etc.) is
     * exchanged.
     *
     * Persisted per-slot settings (URI, resizeMode, playbackSpeed) are
     * synced to the repo so app restart preserves the new arrangement.
     *
     * No-op when i == j or either index is out of range.
     */
    fun swapSlots(i: Int, j: Int) {
        if (i == j || i !in 0 until SLOT_COUNT || j !in 0 until SLOT_COUNT) return
        val list = _slots.value
        val a = list[i]
        val b = list[j]
        // Slot's `index` field stays equal to its position in the list.
        // Everything else (including playerIndex) crosses over.
        val newA = b.copy(index = a.index)
        val newB = a.copy(index = b.index)
        val updated = list.toMutableList().apply {
            this[i] = newA
            this[j] = newB
        }
        _slots.value = updated

        // The cached "stopped" / position state is keyed by slot position,
        // and after the swap the URI (and therefore which player should be
        // driving that position) has moved. Swap those entries too so
        // subsequent stopAndCache / ensurePrepared cycles act on the right
        // values for each slot.
        val cachedI = cachedPositions[i]
        cachedPositions[i] = cachedPositions[j]
        cachedPositions[j] = cachedI
        val stoppedI = isStopped[i]
        isStopped[i] = isStopped[j]
        isStopped[j] = stoppedI

        // Persist per-slot fields so app restart sees the new layout.
        viewModelScope.launch {
            repo.setUri(i, newA.uri?.toString())
            repo.setUri(j, newB.uri?.toString())
            repo.setResizeMode(i, newA.resizeMode)
            repo.setResizeMode(j, newB.resizeMode)
            repo.setPlaybackSpeed(i, newA.playbackSpeed)
            repo.setPlaybackSpeed(j, newB.playbackSpeed)
        }
    }

    /**
     * Tear down the MediaCodec instance for a hidden slot to free memory,
     * remembering the playback position so it can be restored on re-show.
     * No-op if the slot has no media or is already stopped.
     */
    private fun stopAndCache(idx: Int) {
        if (isStopped[idx]) return
        val player = playerOf(idx)
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
        val player = playerOf(idx)
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
            updateSlot(idx) { it.copy(positionMs = playerOf(idx).currentPosition) }
        }
    }

    fun togglePlay(slotIndex: Int) {
        val p = playerOf(slotIndex)
        val nowPlaying = p.isPlaying
        if (syncPlayback.value) {
            // Sync mode: ALL visible slots toggle together based on the
            // tapped slot's current state.
            val mode = layoutMode.value
            for (i in 0 until mode) {
                val pp = playerOf(i)
                if (_slots.value[i].uri == null) continue
                if (nowPlaying) pp.pause() else pp.play()
            }
        } else {
            if (nowPlaying) p.pause() else p.play()
        }
    }

    fun playAll() {
        val mode = layoutMode.value
        for (i in 0 until mode) {
            if (_slots.value[i].uri != null) playerOf(i).play()
        }
    }

    fun pauseAll() {
        for (i in 0 until SLOT_COUNT) playerOf(i).pause()
    }

    fun seekTo(slotIndex: Int, positionMs: Long) {
        if (syncPlayback.value) {
            // Sync seek: all visible slots jump to the same absolute position
            // (clamped per-slot so shorter videos cap at their own end).
            val mode = layoutMode.value
            for (i in 0 until mode) {
                val pp = playerOf(i)
                val target = positionMs.coerceIn(0L, pp.duration.coerceAtLeast(0L))
                pp.seekTo(target)
                updateSlot(i) { it.copy(positionMs = target) }
            }
        } else {
            val p = playerOf(slotIndex)
            val target = positionMs.coerceIn(0L, p.duration.coerceAtLeast(0L))
            p.seekTo(target)
            updateSlot(slotIndex) { it.copy(positionMs = target) }
        }
    }

    fun skipBy(slotIndex: Int, deltaMs: Long) {
        if (syncPlayback.value) {
            // Sync skip: each slot advances by the same delta from its own
            // position, preserving any pre-existing offsets between slots.
            val mode = layoutMode.value
            for (i in 0 until mode) {
                val pp = playerOf(i)
                val target = (pp.currentPosition + deltaMs)
                    .coerceIn(0L, pp.duration.coerceAtLeast(0L))
                pp.seekTo(target)
                updateSlot(i) { it.copy(positionMs = target) }
            }
        } else {
            val p = playerOf(slotIndex)
            val target = (p.currentPosition + deltaMs)
                .coerceIn(0L, p.duration.coerceAtLeast(0L))
            p.seekTo(target)
            updateSlot(slotIndex) { it.copy(positionMs = target) }
        }
    }

    fun setVolume(slotIndex: Int, v: Float) {
        updateSlot(slotIndex) { it.copy(volume = v.coerceIn(0f, 1f)) }
    }

    /* ---------- A/B loop ---------- */

    /**
     * Set the slot's A point to its current playback position. The loop turns
     * ON automatically the moment both A and B are set with A < B (and OFF if
     * the range becomes invalid).
     */
    fun setLoopA(slotIndex: Int) {
        if (slotIndex !in 0 until SLOT_COUNT) return
        val pos = playerOf(slotIndex).currentPosition.coerceAtLeast(0L)
        updateSlot(slotIndex) {
            val b = it.loopBMs
            val valid = b != null && pos < b
            it.copy(loopAMs = pos, loopEnabled = valid)
        }
    }

    /** Set the slot's B point to its current playback position. See [setLoopA]. */
    fun setLoopB(slotIndex: Int) {
        if (slotIndex !in 0 until SLOT_COUNT) return
        val pos = playerOf(slotIndex).currentPosition.coerceAtLeast(0L)
        updateSlot(slotIndex) {
            val a = it.loopAMs
            val valid = a != null && a < pos
            it.copy(loopBMs = pos, loopEnabled = valid)
        }
    }

    /** Clear A, B, and loop flag for the slot. */
    fun clearLoop(slotIndex: Int) {
        if (slotIndex !in 0 until SLOT_COUNT) return
        updateSlot(slotIndex) {
            it.copy(loopAMs = null, loopBMs = null, loopEnabled = false)
        }
    }

    /**
     * Apply a new playback speed. Normally affects only the targeted slot;
     * if both sync-playback and the "sync speed in sync mode" option are on,
     * the speed is broadcast to every visible slot.
     */
    fun setPlaybackSpeed(slotIndex: Int, speed: Float) {
        if (slotIndex !in 0 until SLOT_COUNT) return
        val coerced = speed.coerceIn(0.25f, 4.0f)
        val targets: List<Int> = if (syncPlayback.value && syncSpeed.value) {
            (0 until layoutMode.value).toList()
        } else {
            listOf(slotIndex)
        }
        targets.forEach { i ->
            updateSlot(i) { it.copy(playbackSpeed = coerced) }
            runCatching {
                playerOf(i).playbackParameters = PlaybackParameters(coerced)
            }
        }
        viewModelScope.launch {
            targets.forEach { i -> repo.setPlaybackSpeed(i, coerced) }
        }
    }

    /** Cycle through the available resize modes for the given slot. */
    fun cycleResizeMode(slotIndex: Int) {
        if (slotIndex !in 0 until SLOT_COUNT) return
        val current = _slots.value[slotIndex].resizeMode
        val ordered = ResizeMode.ALL
        val nextIdx = (ordered.indexOf(current).takeIf { it >= 0 } ?: 0) + 1
        val next = ordered[nextIdx % ordered.size]
        updateSlot(slotIndex) { it.copy(resizeMode = next) }
        viewModelScope.launch { repo.setResizeMode(slotIndex, next) }
    }

    /* ---------- Layout presets ---------- */

    /** Snapshot the current player setup into a new preset. */
    fun savePreset(name: String) {
        val cur = _slots.value
        val preset = LayoutPreset(
            id = LayoutPreset.newId(),
            name = name.trim().ifEmpty { defaultPresetName() },
            layoutMode = layoutMode.value,
            soloAudio = soloAudio.value,
            soloIndex = soloIndex.value,
            slots = List(SLOT_COUNT) { i ->
                val s = cur[i]
                PresetSlot(
                    uri = s.uri?.toString(),
                    title = s.title,
                    volume = s.volume,
                    resizeMode = s.resizeMode,
                    playbackSpeed = s.playbackSpeed,
                )
            },
        )
        viewModelScope.launch {
            val updated = presets.value + preset
            repo.setPresetsJson(updated.toJsonArray().toString())
        }
    }

    fun deletePreset(id: String) {
        viewModelScope.launch {
            val updated = presets.value.filterNot { it.id == id }
            repo.setPresetsJson(updated.toJsonArray().toString())
        }
    }

    /** Change the display name of an existing preset. Empty/blank ignored. */
    fun renamePreset(id: String, newName: String) {
        val trimmed = newName.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            val updated = presets.value.map { p ->
                if (p.id == id) p.copy(name = trimmed) else p
            }
            repo.setPresetsJson(updated.toJsonArray().toString())
        }
    }

    /**
     * Apply a preset: clear current slots, restore each slot's URI / volume /
     * resize mode, and apply the layout / solo-audio settings.
     */
    fun loadPreset(id: String) {
        val preset = presets.value.firstOrNull { it.id == id } ?: return
        viewModelScope.launch {
            // First wipe each player so the codec resources are released and any
            // cached "stopped" state is reset. Iterate by *player* index here
            // (as opposed to slot index) so reorder mappings don't leave
            // stragglers behind.
            for (i in 0 until SLOT_COUNT) {
                players[i].stop()
                players[i].clearMediaItems()
                isStopped[i] = false
                cachedPositions[i] = 0L
            }
            // Reset the slot ↔ player mapping back to identity. A preset is
            // a logical layout, not a runtime mapping, so loading one starts
            // fresh: slot N is driven by player N.
            _slots.value = _slots.value.mapIndexed { i, s -> s.copy(playerIndex = i) }
            // Persist top-level settings.
            repo.setLayoutMode(preset.layoutMode)
            repo.setSoloAudio(preset.soloAudio)
            repo.setSoloIndex(preset.soloIndex)
            // Per-slot.
            preset.slots.forEachIndexed { i, ps ->
                repo.setResizeMode(i, ps.resizeMode)
                repo.setPlaybackSpeed(i, ps.playbackSpeed)
                updateSlot(i) {
                    it.copy(
                        volume = ps.volume,
                        resizeMode = ps.resizeMode,
                        playbackSpeed = ps.playbackSpeed,
                        title = ps.title,
                        durationMs = 0L,
                        positionMs = 0L,
                        isReady = false,
                    )
                }
                runCatching {
                    playerOf(i).playbackParameters = PlaybackParameters(ps.playbackSpeed)
                }
                if (!ps.uri.isNullOrEmpty()) {
                    runCatching { setVideoUri(i, Uri.parse(ps.uri), persist = true) }
                } else {
                    repo.setUri(i, null)
                    updateSlot(i) { it.copy(uri = null, title = null) }
                }
            }
        }
    }

    /** Default name for a freshly created preset, like "プリセット 3". */
    fun defaultPresetName(): String = "プリセット ${presets.value.size + 1}"

    fun setLayoutMode(n: Int) {
        viewModelScope.launch { repo.setLayoutMode(n) }
    }

    fun toggleSoloAudio() {
        viewModelScope.launch { repo.setSoloAudio(!soloAudio.value) }
    }

    fun toggleSyncPlayback() {
        viewModelScope.launch { repo.setSyncPlayback(!syncPlayback.value) }
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

    fun setFastSeek(v: Boolean) =
        viewModelScope.launch { repo.setFastSeek(v) }.let { Unit }

    fun setSyncSpeed(v: Boolean) =
        viewModelScope.launch { repo.setSyncSpeed(v) }.let { Unit }

    fun setAutoLoop(v: Boolean) =
        viewModelScope.launch { repo.setAutoLoop(v) }.let { Unit }

    fun setVolumeGesture(v: Boolean) =
        viewModelScope.launch { repo.setVolumeGesture(v) }.let { Unit }

    fun setSeekGesture(v: Boolean) =
        viewModelScope.launch { repo.setSeekGesture(v) }.let { Unit }

    fun setDisableVolumeKeys(v: Boolean) =
        viewModelScope.launch { repo.setDisableVolumeKeys(v) }.let { Unit }

    fun setLocked(v: Boolean) {
        _locked.value = v
    }

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
