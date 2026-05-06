package jp.simplist.smmultiplayer.player

import android.net.Uri

/**
 * UI state for a single player slot.
 *
 * `volume` is the user-set volume in [0,1]. The effective volume applied to
 * the underlying ExoPlayer also depends on solo-audio mode (handled by the
 * ViewModel — not stored here).
 *
 * `resizeMode` mirrors `androidx.media3.ui.AspectRatioFrameLayout`'s constants
 * (FIT/ZOOM/FILL/etc.) so we can pass the value straight through to PlayerView.
 */
data class PlayerSlotState(
    val index: Int,
    /**
     * Index into `PlayerViewModel.players` of the underlying ExoPlayer that
     * drives this UI slot. Defaults to `index` (identity mapping). Drag-and-
     * drop reorder swaps this between two slots so the players stay running
     * with their current MediaItem rather than re-loading on every shuffle.
     */
    val playerIndex: Int = index,
    val uri: Uri? = null,
    val title: String? = null,
    val isPlaying: Boolean = false,
    val durationMs: Long = 0L,
    val positionMs: Long = 0L,
    val volume: Float = 1f,
    val isReady: Boolean = false,
    val resizeMode: Int = ResizeMode.FIT,
    val playbackSpeed: Float = 1f,
    /** Loop A point (ms). Null = unset. Ephemeral, not persisted. */
    val loopAMs: Long? = null,
    /** Loop B point (ms). Null = unset. */
    val loopBMs: Long? = null,
    /** When true and both A/B are set with A < B, loop A↔B during playback. */
    val loopEnabled: Boolean = false,
)

/**
 * The discrete playback speeds that the speed-picker UI cycles through.
 */
val PlaybackSpeeds: List<Float> = listOf(0.25f, 0.5f, 1.0f, 1.5f, 2.0f)

/**
 * Convenience constants for the subset of `AspectRatioFrameLayout` resize
 * modes we expose in UI. Kept here (rather than referencing media3's class)
 * so non-UI layers don't need a media3 dependency.
 */
object ResizeMode {
    const val FIT: Int = 0    // AspectRatioFrameLayout.RESIZE_MODE_FIT
    const val ZOOM: Int = 4   // AspectRatioFrameLayout.RESIZE_MODE_ZOOM
    const val FILL: Int = 3   // AspectRatioFrameLayout.RESIZE_MODE_FILL

    val ALL: List<Int> = listOf(FIT, ZOOM, FILL)
}
