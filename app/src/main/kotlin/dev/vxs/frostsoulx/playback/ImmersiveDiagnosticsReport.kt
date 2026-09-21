package dev.vxs.frostsoulx.playback

import android.os.Build
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID
import kotlin.math.log10

data class ImmersiveDiagnosticSample(
    val elapsedSeconds: Float,
    val inputPeakL: Float,
    val inputPeakR: Float,
    val outputPeakL: Float,
    val outputPeakR: Float,
    val inputRmsL: Float,
    val inputRmsR: Float,
    val outputRmsL: Float,
    val outputRmsR: Float,
)

data class ImmersiveDiagnosticCapture(
    val id: String = UUID.randomUUID().toString(),
    val processorOn: Boolean,
    val durationSeconds: Int,
    val startedAtMillis: Long,
    val samples: List<ImmersiveDiagnosticSample>,
    val finalDiagnostics: ImmersiveAudioDiagnostics,
) {
    fun fileName(): String = "frostsoulx-dsp-${if (processorOn) "on" else "off"}-$id.txt"

    fun toText(device: String, androidVersion: String, audioRoute: String, hostBufferFrames: Int): String {
        val d = finalDiagnostics
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss XXX", Locale.US).apply {
            timeZone = TimeZone.getDefault()
        }.format(Date(startedAtMillis))
        val state = if (processorOn) "ON" else "OFF"
        fun db(value: Float): String = if (!value.isFinite() || value <= 1.0e-9f) "-inf dB" else String.format(Locale.US, "%.2f dB", 20.0 * log10(value.toDouble()))
        fun raw(value: Float): String = String.format(Locale.US, "%.6f", value)
        fun row(label: String, left: Float, right: Float): String = String.format(Locale.US, "%-18s L=%-14s R=%s\n", label, db(left), db(right))

        return buildString {
            appendLine("FROSTSOULX DSP A/B SIGNAL REPORT")
            appendLine("Capture ID: ${id}")
            appendLine("Timestamp: $timestamp")
            appendLine("Device: $device")
            appendLine("Android version: $androidVersion")
            appendLine("Audio route: $audioRoute")
            appendLine("Sample rate: ${d.sampleRate} Hz")
            appendLine("Channel count: 2")
            appendLine("PCM format: ${if (d.pcmEncoding == 4) "PCM float" else if (d.pcmEncoding == 2) "PCM 16-bit" else "Unknown"}")
            appendLine("Processor state: $state")
            appendLine("Signal path: Input → ${if (processorOn) "Native DSP" else "Bypass"} → Output")
            appendLine("Input measurement boundary: PCM immediately before native DSP/bypass")
            appendLine("Capture duration: ${durationSeconds}s")
            appendLine("Processing quantum: ${d.quantumFrames} frames")
            appendLine("Host callback size: ${d.hostCallbackFrames} frames")
            appendLine()
            appendLine("INPUT")
            append(row("RMS", d.inputRmsL, d.inputRmsR))
            append(row("Sample peak", d.inputPeakL, d.inputPeakR))
            append(row("True peak", d.inputTruePeakL, d.inputTruePeakR))
            appendLine("Minimum: L=${raw(d.inputMinL)} R=${raw(d.inputMinR)}")
            appendLine("Maximum: L=${raw(d.inputMaxL)} R=${raw(d.inputMaxR)}")
            appendLine("NaN: ${d.nanCount}  Inf: ${d.infCount}  Clipped: ${d.clippedInput}")
            appendLine()
            appendLine("OUTPUT")
            append(row("RMS", d.outputRmsL, d.outputRmsR))
            append(row("Sample peak", d.outputPeakL, d.outputPeakR))
            append(row("True peak", d.outputTruePeakL, d.outputTruePeakR))
            appendLine("Minimum: L=${raw(d.outputMinL)} R=${raw(d.outputMinR)}")
            appendLine("Maximum: L=${raw(d.outputMaxL)} R=${raw(d.outputMaxR)}")
            appendLine("NaN: ${d.nanCount}  Inf: ${d.infCount}  Clipped: ${d.clippedOutput}")
            appendLine()
            appendLine("SIGNAL TRANSFORMATION")
            appendLine("Average input RMS: L=${db(d.inputRmsL)} R=${db(d.inputRmsR)}")
            appendLine("Average output RMS: L=${db(d.outputRmsL)} R=${db(d.outputRmsR)}")
            appendLine("Peak input: ${db(maxOf(d.inputPeakL, d.inputPeakR))}")
            appendLine("Peak output: ${db(maxOf(d.outputPeakL, d.outputPeakR))}")
            appendLine("Maximum input-output difference: ${raw(d.maxAbsDifference)}")
            appendLine("Average input-output difference: ${raw(d.averageAbsDifference)}")
            appendLine("Changed samples: ${String.format(Locale.US, "%.3f", d.changedPercentage)}%")
            appendLine("Gain/attenuation estimate: ${db(d.outputRms / d.inputRms)}")
            appendLine()
            appendLine("PROCESSING")
            appendLine("Total blocks processed: ${d.totalBlocks}")
            appendLine("Total frames: ${d.processedFrames}")
            appendLine("Processing time total: ${String.format(Locale.US, "%.3f", d.processingTimeMs)} ms")
            appendLine("Processing time average: ${String.format(Locale.US, "%.3f", d.averageProcessingTimeMs)} ms")
            appendLine("Processing time maximum: ${String.format(Locale.US, "%.3f", d.maxProcessingTimeMs)} ms")
            appendLine("Deadline misses: ${d.deadlineMisses}")
            appendLine("Underruns: unavailable from AudioProcessor boundary")
            appendLine("Native process failures: ${d.nativeProcessFailures}")
            appendLine()
            appendLine("TRUE-PEAK WARNING")
            appendLine(if (d.truePeakWarningSource() == "NONE") "None ($state)" else "WARNING: above -0.1 dBTP at ${d.truePeakWarningSource()} ($state)")
            appendLine("True-peak max: input=${db(maxOf(d.inputTruePeakL, d.inputTruePeakR))}, output=${db(maxOf(d.outputTruePeakL, d.outputTruePeakR))}")
            appendLine("Hard-clipping source: ${d.clippingSource()}")
            appendLine()
            appendLine("TIME SERIES (elapsed_s,input_peak_L,input_peak_R,output_peak_L,output_peak_R,input_rms_L,input_rms_R,output_rms_L,output_rms_R)")
            samples.forEach { sample ->
                appendLine(String.format(Locale.US, "%.3f,%.6f,%.6f,%.6f,%.6f,%.6f,%.6f,%.6f,%.6f", sample.elapsedSeconds, sample.inputPeakL, sample.inputPeakR, sample.outputPeakL, sample.outputPeakR, sample.inputRmsL, sample.inputRmsR, sample.outputRmsL, sample.outputRmsR))
            }
        }
    }
}

fun defaultDeviceDescription(): String = "${Build.MANUFACTURER} ${Build.MODEL}".trim()
fun defaultAndroidDescription(): String = "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"

data class ImmersiveActiveCapture(
    val processorOn: Boolean,
    val durationSeconds: Int,
    val startedAtMillis: Long,
    val elapsedSeconds: Float = 0f,
    val samples: List<ImmersiveDiagnosticSample> = emptyList(),
)
