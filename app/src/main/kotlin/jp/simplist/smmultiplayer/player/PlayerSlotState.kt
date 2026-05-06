package jp.simplist.smmultiplayer.player

import android.net.Uri

/**
 * UI state for a single player slot.
 *
 * `volume` is the user-set volume in [0,1]. The effective volume applied to
 * the underlying ExoPlayer also depends on solo-audio mode (handled by the
 * ViewModel — not stored here).
 */
data class PlayerSlotState(
    val index: Int,
    val uri: Uri? = null,
    val title: String? = null,
    val isPlaying: Boolean = false,
    val durationMs: Long = 0L,
    val positionMs: Long = 0L,
    val volume: Float = 1f,
    val isReady: Boolean = false,
)
