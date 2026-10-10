package dev.vxs.frostsoulx.playback

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

/** Stereo diagonal IR, or four input-major transfers: LL, LR, RL, RR. */
data class WavImpulseResponse(
    val sampleRate: Int,
    val left: FloatArray,
    val right: FloatArray,
    val leftToRight: FloatArray? = null,
    val rightToLeft: FloatArray? = null,
) {
    fun resampled(targetRate: Int, maxTaps: Int): Pair<FloatArray, FloatArray> {
        val matrix = resampledMatrix(targetRate, maxTaps)
        return matrix[0] to matrix[3]
    }

    fun resampledMatrix(targetRate: Int, maxTaps: Int): Array<FloatArray> {
        require(sampleRate in 8000..384000 && targetRate in 8000..384000 && maxTaps > 0)
        require(left.isNotEmpty() && left.size == right.size)
        require(leftToRight == null || leftToRight.size == left.size)
        require(rightToLeft == null || rightToLeft.size == left.size)
        val count = min(maxTaps, max(1, ceil(left.size.toDouble() * targetRate / sampleRate).toInt()))
        fun convert(input: FloatArray?): FloatArray {
            if (input == null) return FloatArray(count)
            if (count == input.size && targetRate == sampleRate) return input
            val ratio = sampleRate.toDouble() / targetRate
            return FloatArray(count) { i ->
                val position = i * ratio
                val a = min(input.lastIndex, position.toInt())
                val b = min(input.lastIndex, a + 1)
                input[a] + (input[b] - input[a]) * (position - a).toFloat()
            }
        }
        return arrayOf(convert(left), convert(leftToRight), convert(rightToLeft), convert(right))
    }

    companion object {
        fun decode(bytes: ByteArray): WavImpulseResponse {
            require(bytes.size in 44..(32 * 1024 * 1024)) { "WAV must be between 44 bytes and 32 MB" }
            fun ascii(offset: Int, text: String) = offset >= 0 && offset + text.length <= bytes.size &&
                text.indices.all { bytes[offset + it].toInt().toChar() == text[it] }
            fun u16(o: Int) = (bytes[o].toInt() and 255) or ((bytes[o + 1].toInt() and 255) shl 8)
            fun u32(o: Int) = (bytes[o].toLong() and 255L) or ((bytes[o + 1].toLong() and 255L) shl 8) or
                ((bytes[o + 2].toLong() and 255L) shl 16) or ((bytes[o + 3].toLong() and 255L) shl 24)
            require(ascii(0, "RIFF") && ascii(8, "WAVE")) { "Choose a standard RIFF/WAVE file" }
            var format = 0
            var channels = 0
            var rate = 0
            var bits = 0
            var alignment = 0
            var dataAt = -1
            var dataSize = 0
            var offset = 12
            while (offset + 8 <= bytes.size) {
                val length = u32(offset + 4)
                require(length <= Int.MAX_VALUE) { "Invalid WAV chunk size" }
                val size = length.toInt()
                val start = offset + 8
                require(size <= bytes.size - start) { "Truncated WAV chunk" }
                when {
                    ascii(offset, "fmt ") -> {
                        require(size >= 16) { "Invalid WAV format chunk" }
                        format = u16(start)
                        channels = u16(start + 2)
                        rate = u32(start + 4).toInt()
                        alignment = u16(start + 12)
                        bits = u16(start + 14)
                        if (format == 0xfffe) {
                            require(size >= 40 && u16(start + 16) >= 22) { "Invalid extensible WAV format" }
                            format = u16(start + 24)
                        }
                    }
                    ascii(offset, "data") -> { dataAt = start; dataSize = size }
                }
                offset = start + size + (size and 1)
            }
            require(format == 1 || format == 3) { "Only PCM or IEEE-float WAV is supported" }
            require(channels == 1 || channels == 2 || channels == 4) { "IR WAV must contain 1, 2 or 4 paths" }
            require(rate in 8000..384000) { "Unsupported IR sample rate" }
            require((format == 1 && bits in listOf(16, 24, 32)) || (format == 3 && bits == 32)) {
                "Supported: PCM 16/24/32-bit or float32 WAV"
            }
            require(dataAt >= 0) { "WAV has no audio data" }
            val bytesPerSample = bits / 8
            val frameBytes = channels * bytesPerSample
            require(alignment == frameBytes && dataSize % frameBytes == 0) { "Invalid WAV frame alignment" }
            val frames = dataSize / frameBytes
            require(frames in 1..32768) { "IR must have 1–32,768 taps per path; shorten the response before importing" }
            val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
            fun sample(at: Int): Float {
                val value = when {
                    format == 3 -> buffer.getFloat(at).toDouble()
                    bits == 16 -> buffer.getShort(at).toDouble() / 32768.0
                    bits == 24 -> {
                        var x = (bytes[at].toInt() and 255) or ((bytes[at + 1].toInt() and 255) shl 8) or
                            ((bytes[at + 2].toInt() and 255) shl 16)
                        if ((x and 0x800000) != 0) x = x or -0x1000000
                        x / 8388608.0
                    }
                    else -> buffer.getInt(at).toDouble() / 2147483648.0
                }
                require(value.isFinite()) { "IR contains NaN or infinity" }
                return value.coerceIn(-4.0, 4.0).toFloat()
            }
            val paths = Array(channels) { channel ->
                FloatArray(frames) { frame -> sample(dataAt + frame * frameBytes + channel * bytesPerSample) }
            }
            return when (channels) {
                1 -> WavImpulseResponse(rate, paths[0], paths[0])
                2 -> WavImpulseResponse(rate, paths[0], paths[1])
                else -> WavImpulseResponse(rate, paths[0], paths[3], paths[1], paths[2])
            }
        }
    }
}
