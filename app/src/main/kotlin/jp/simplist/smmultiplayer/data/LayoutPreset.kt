package jp.simplist.smmultiplayer.data

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * Single video slot's data within a saved preset.
 */
data class PresetSlot(
    val uri: String?,
    val title: String?,
    val volume: Float,
    val resizeMode: Int,
)

/**
 * Named, persistable bundle that describes a complete player setup the user
 * wants to be able to restore on demand.
 */
data class LayoutPreset(
    val id: String,
    val name: String,
    val layoutMode: Int,
    val soloAudio: Boolean,
    val soloIndex: Int,
    val slots: List<PresetSlot>, // size 4, ordered by slot index 0..3
) {
    val videoCount: Int get() = slots.count { !it.uri.isNullOrEmpty() }

    companion object {
        fun newId(): String = UUID.randomUUID().toString()
    }
}

/* ---------- JSON (de)serialisation via org.json (no extra dep) ---------- */

internal fun LayoutPreset.toJson(): JSONObject = JSONObject().apply {
    put("id", id)
    put("name", name)
    put("layoutMode", layoutMode)
    put("soloAudio", soloAudio)
    put("soloIndex", soloIndex)
    put("slots", JSONArray().apply {
        slots.forEach { put(it.toJson()) }
    })
}

private fun PresetSlot.toJson(): JSONObject = JSONObject().apply {
    put("uri", uri ?: JSONObject.NULL)
    put("title", title ?: JSONObject.NULL)
    put("volume", volume.toDouble())
    put("resizeMode", resizeMode)
}

internal fun JSONObject.toLayoutPreset(): LayoutPreset? = runCatching {
    LayoutPreset(
        id = optString("id").ifEmpty { LayoutPreset.newId() },
        name = optString("name", ""),
        layoutMode = optInt("layoutMode", 1).coerceIn(1, 4),
        soloAudio = optBoolean("soloAudio", false),
        soloIndex = optInt("soloIndex", 0).coerceIn(0, 3),
        slots = (optJSONArray("slots") ?: JSONArray())
            .let { arr -> List(arr.length()) { i -> arr.optJSONObject(i)?.toPresetSlot() } }
            .filterNotNull()
            .let { parsed ->
                // Pad/trim to exactly 4 slots so callers can index safely.
                List(4) { i ->
                    parsed.getOrNull(i)
                        ?: PresetSlot(uri = null, title = null, volume = 1f, resizeMode = 0)
                }
            },
    )
}.getOrNull()

private fun JSONObject.toPresetSlot(): PresetSlot = PresetSlot(
    uri = if (isNull("uri")) null else optString("uri").ifEmpty { null },
    title = if (isNull("title")) null else optString("title").ifEmpty { null },
    volume = optDouble("volume", 1.0).toFloat().coerceIn(0f, 1f),
    resizeMode = optInt("resizeMode", 0),
)

internal fun List<LayoutPreset>.toJsonArray(): JSONArray = JSONArray().apply {
    forEach { put(it.toJson()) }
}

internal fun String.toPresetList(): List<LayoutPreset> = runCatching {
    val arr = JSONArray(this)
    List(arr.length()) { i -> arr.optJSONObject(i)?.toLayoutPreset() }.filterNotNull()
}.getOrDefault(emptyList())
