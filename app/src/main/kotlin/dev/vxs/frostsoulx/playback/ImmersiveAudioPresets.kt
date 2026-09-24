package dev.vxs.frostsoulx.playback

import org.json.JSONArray
import org.json.JSONObject

/** A complete user-saveable snapshot of every runtime-safe engine control. */
data class ImmersiveAudioPreset(
    val name: String,
    val enabled: Boolean,
    val intensity: Float,
    val roomPreset: ImmersiveRoomPreset,
    val roomMix: Float,
    val reflectionAmount: Float,
    val reverbTimeSeconds: Float,
    val roomSize: Float,
    val dampening: Float,
    val stereoWidth: Float,
    val quantumFrames: Int,
    val carFader: Float = 0f,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("name", name.take(64))
        put("enabled", enabled)
        put("intensity", intensity.safeUnit())
        put("roomPreset", roomPreset.nativeValue)
        put("roomMix", roomMix.safeUnit())
        put("reflectionAmount", reflectionAmount.safeUnit())
        put("reverbTimeSeconds", reverbTimeSeconds.safeReverb())
        put("roomSize", roomSize.safeUnit())
        put("dampening", dampening.safeUnit())
        put("stereoWidth", stereoWidth.safeUnit())
        put("carFader", carFader.safeFader())
        put("quantumFrames", quantumFrames.coerceIn(96, 2048))
    }

    companion object {
        fun fromJson(value: JSONObject?): ImmersiveAudioPreset? {
            if (value == null) return null
            val name = value.optString("name").trim().takeIf { it.isNotEmpty() } ?: return null
            return ImmersiveAudioPreset(
                name = name,
                enabled = value.optBoolean("enabled", false),
                intensity = value.optDouble("intensity", 0.5).toFloat().safeUnit(),
                roomPreset = ImmersiveRoomPreset.fromNative(value.optInt("roomPreset", 2)),
                roomMix = value.optDouble("roomMix", 0.18).toFloat().safeUnit(),
                reflectionAmount = value.optDouble("reflectionAmount", 0.28).toFloat().safeUnit(),
                reverbTimeSeconds = value.optDouble("reverbTimeSeconds", 1.35).toFloat().safeReverb(),
                roomSize = value.optDouble("roomSize", 0.5).toFloat().safeUnit(),
                dampening = value.optDouble("dampening", 0.5).toFloat().safeUnit(),
                stereoWidth = value.optDouble("stereoWidth", 0.5).toFloat().safeUnit(),
                carFader = value.optDouble("carFader", 0.0).toFloat().safeFader(),
                quantumFrames = value.optInt("quantumFrames", 384).coerceIn(96, 2048),
            )
        }

        fun decodeAll(raw: String): List<ImmersiveAudioPreset> = runCatching {
            val array = JSONArray(raw)
            buildList(array.length()) {
                for (index in 0 until array.length()) {
                    fromJson(array.optJSONObject(index))?.let(::add)
                }
            }
        }.getOrDefault(emptyList())

        fun encodeAll(presets: List<ImmersiveAudioPreset>): String =
            JSONArray().apply { presets.take(32).forEach { put(it.toJson()) } }.toString()
    }
}

private fun Float.safeUnit(): Float = takeIf(Float::isFinite)?.coerceIn(0f, 1f) ?: 0f
private fun Float.safeFader(): Float = takeIf(Float::isFinite)?.coerceIn(-1f, 1f) ?: 0f
private fun Float.safeReverb(): Float = takeIf(Float::isFinite)?.coerceIn(0.2f, 8f) ?: 1.35f
