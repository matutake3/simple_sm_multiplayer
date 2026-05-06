package jp.simplist.smmultiplayer.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsRepository(context: Context) {
    private val ds: DataStore<Preferences> = context.applicationContext.dataStore

    private val keyVolumeIndicator = booleanPreferencesKey("show_volume_indicator")
    private val keySeekIndicator = booleanPreferencesKey("show_seek_indicator")
    private val keyControlsAlwaysVisible = booleanPreferencesKey("controls_always_visible")
    private val keyFastSeek = booleanPreferencesKey("fast_seek")
    private val keySyncSpeed = booleanPreferencesKey("sync_speed")
    private val keyAutoLoop = booleanPreferencesKey("auto_loop")
    private val keyVolumeGesture = booleanPreferencesKey("volume_gesture")
    private val keySeekGesture = booleanPreferencesKey("seek_gesture")
    private val keyLayoutMode = intPreferencesKey("layout_mode")
    private val keySoloAudio = booleanPreferencesKey("solo_audio")
    private val keySoloIndex = intPreferencesKey("solo_index")
    private val keySyncPlayback = booleanPreferencesKey("sync_playback")
    private val keyPresets = stringPreferencesKey("layout_presets_json")
    private fun keyUri(idx: Int) = stringPreferencesKey("uri_$idx")
    private fun keyResizeMode(idx: Int) = intPreferencesKey("resize_mode_$idx")
    private fun keyPlaybackSpeed(idx: Int) = floatPreferencesKey("playback_speed_$idx")

    val showVolumeIndicator: Flow<Boolean> =
        ds.data.map { it[keyVolumeIndicator] ?: true }
    val showSeekIndicator: Flow<Boolean> =
        ds.data.map { it[keySeekIndicator] ?: true }
    val controlsAlwaysVisible: Flow<Boolean> =
        ds.data.map { it[keyControlsAlwaysVisible] ?: false }
    val fastSeek: Flow<Boolean> =
        ds.data.map { it[keyFastSeek] ?: false }
    val syncSpeed: Flow<Boolean> =
        ds.data.map { it[keySyncSpeed] ?: false }
    val autoLoop: Flow<Boolean> =
        ds.data.map { it[keyAutoLoop] ?: true }
    val volumeGesture: Flow<Boolean> =
        ds.data.map { it[keyVolumeGesture] ?: true }
    val seekGesture: Flow<Boolean> =
        ds.data.map { it[keySeekGesture] ?: true }
    val layoutMode: Flow<Int> =
        ds.data.map { (it[keyLayoutMode] ?: 1).coerceIn(1, 4) }
    val soloAudio: Flow<Boolean> =
        ds.data.map { it[keySoloAudio] ?: false }
    val soloIndex: Flow<Int> =
        ds.data.map { (it[keySoloIndex] ?: 0).coerceIn(0, 3) }
    val syncPlayback: Flow<Boolean> =
        ds.data.map { it[keySyncPlayback] ?: false }

    fun savedUri(idx: Int): Flow<String?> =
        ds.data.map { it[keyUri(idx)] }

    fun savedResizeMode(idx: Int): Flow<Int> =
        ds.data.map { it[keyResizeMode(idx)] ?: 0 }

    fun savedPlaybackSpeed(idx: Int): Flow<Float> =
        ds.data.map { it[keyPlaybackSpeed(idx)] ?: 1f }

    val presetsJson: Flow<String> =
        ds.data.map { it[keyPresets] ?: "[]" }

    suspend fun setShowVolumeIndicator(v: Boolean) { ds.edit { it[keyVolumeIndicator] = v } }
    suspend fun setShowSeekIndicator(v: Boolean) { ds.edit { it[keySeekIndicator] = v } }
    suspend fun setControlsAlwaysVisible(v: Boolean) { ds.edit { it[keyControlsAlwaysVisible] = v } }
    suspend fun setFastSeek(v: Boolean) { ds.edit { it[keyFastSeek] = v } }
    suspend fun setSyncSpeed(v: Boolean) { ds.edit { it[keySyncSpeed] = v } }
    suspend fun setAutoLoop(v: Boolean) { ds.edit { it[keyAutoLoop] = v } }
    suspend fun setVolumeGesture(v: Boolean) { ds.edit { it[keyVolumeGesture] = v } }
    suspend fun setSeekGesture(v: Boolean) { ds.edit { it[keySeekGesture] = v } }
    suspend fun setLayoutMode(n: Int) { ds.edit { it[keyLayoutMode] = n.coerceIn(1, 4) } }
    suspend fun setSoloAudio(v: Boolean) { ds.edit { it[keySoloAudio] = v } }
    suspend fun setSoloIndex(idx: Int) { ds.edit { it[keySoloIndex] = idx.coerceIn(0, 3) } }
    suspend fun setSyncPlayback(v: Boolean) { ds.edit { it[keySyncPlayback] = v } }
    suspend fun setUri(idx: Int, uri: String?) {
        ds.edit { prefs ->
            if (uri == null) prefs.remove(keyUri(idx)) else prefs[keyUri(idx)] = uri
        }
    }

    suspend fun setResizeMode(idx: Int, mode: Int) {
        ds.edit { it[keyResizeMode(idx)] = mode }
    }

    suspend fun setPlaybackSpeed(idx: Int, speed: Float) {
        ds.edit { it[keyPlaybackSpeed(idx)] = speed }
    }

    suspend fun setPresetsJson(json: String) {
        ds.edit { it[keyPresets] = json }
    }
}
