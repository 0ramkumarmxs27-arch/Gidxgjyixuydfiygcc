package com.example.voice

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import com.example.data.SpeakerAuthStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

data class VoiceSampleAnalysis(
    val featureVector: FloatArray,
    val isLiveHumanSpeech: Boolean,
    val livenessReason: String
)

data class SpeakerVerificationOutcome(
    val status: SpeakerAuthStatus,
    val similarityPercent: Int,
    val message: String
)

class SpeakerVerificationEngine {

    @SuppressLint("MissingPermission")
    suspend fun recordAndExtractVoiceprint(durationMillis: Long = 2400L): Result<VoiceSampleAnalysis> =
        withContext(Dispatchers.IO) {
            val sampleRate = 16000
            val minBuffer = AudioRecord.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )
            if (minBuffer <= 0) {
                return@withContext Result.failure(IllegalStateException("AudioRecord unavailable on this device"))
            }

            val totalSamples = ((sampleRate * durationMillis) / 1000L).toInt()
            val pcmBuffer = ShortArray(totalSamples)
            var recorder: AudioRecord? = null

            try {
                recorder = AudioRecord(
                    MediaRecorder.AudioSource.VOICE_RECOGNITION,
                    sampleRate,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    minBuffer * 2
                )
                if (recorder.state != AudioRecord.STATE_INITIALIZED) {
                    return@withContext Result.failure(IllegalStateException("Microphone AudioRecord failed to initialize"))
                }

                recorder.startRecording()
                var offset = 0
                while (offset < totalSamples) {
                    val read = recorder.read(pcmBuffer, offset, (totalSamples - offset).coerceAtMost(1024))
                    if (read <= 0) break
                    offset += read
                }
                recorder.stop()

                if (offset < sampleRate / 2) {
                    return@withContext Result.failure(IllegalStateException("Voice sample too short. Please speak clearly."))
                }

                val activeSamples = pcmBuffer.copyOf(offset)
                val analysis = analyzePcm(activeSamples, sampleRate)
                Result.success(analysis)
            } catch (e: Exception) {
                Result.failure(e)
            } finally {
                runCatching { recorder?.release() }
            }
        }

    fun averageVectors(samples: List<FloatArray>): FloatArray {
        if (samples.isEmpty()) return FloatArray(12)
        val dim = samples.first().size
        val avg = FloatArray(dim)
        samples.forEach { vec ->
            for (i in 0 until dim) {
                avg[i] += vec[i]
            }
        }
        for (i in 0 until dim) {
            avg[i] /= samples.size.toFloat()
        }
        return normalize(avg)
    }

    fun verifyAgainstEnrolled(
        enrolledCsv: String,
        candidate: VoiceSampleAnalysis
    ): SpeakerVerificationOutcome {
        if (!candidate.isLiveHumanSpeech) {
            return SpeakerVerificationOutcome(
                status = SpeakerAuthStatus.SPOOF_SUSPECTED,
                similarityPercent = 0,
                message = "Anti-spoof check rejected sample: ${candidate.livenessReason}"
            )
        }
        val enrolled = enrolledCsv.split(",").mapNotNull { it.toFloatOrNull() }.toFloatArray()
        if (enrolled.size != candidate.featureVector.size) {
            return SpeakerVerificationOutcome(
                status = SpeakerAuthStatus.NOT_ENROLLED,
                similarityPercent = 0,
                message = "Owner voice profile not enrolled."
            )
        }
        val sim = cosineSimilarity(enrolled, candidate.featureVector)
        val pct = (sim * 100f).toInt().coerceIn(0, 100)
        return if (sim >= 0.84f) {
            SpeakerVerificationOutcome(
                status = SpeakerAuthStatus.VERIFIED_OWNER,
                similarityPercent = pct,
                message = "Owner voice verified ($pct% acoustic match)."
            )
        } else {
            SpeakerVerificationOutcome(
                status = SpeakerAuthStatus.REJECTED_UNKNOWN,
                similarityPercent = pct,
                message = "Unrecognized speaker ($pct% match). Please use PIN or Biometric."
            )
        }
    }

    private fun analyzePcm(samples: ShortArray, sampleRate: Int): VoiceSampleAnalysis {
        val frameSize = 512
        val frameEnergies = mutableListOf<Double>()
        var zeroCrossings = 0
        var totalEnergy = 0.0

        for (i in 1 until samples.size) {
            if ((samples[i - 1] >= 0 && samples[i] < 0) || (samples[i - 1] < 0 && samples[i] >= 0)) {
                zeroCrossings++
            }
            val norm = samples[i] / 32768.0
            totalEnergy += norm * norm
        }

        for (start in 0 until samples.size - frameSize step frameSize) {
            var e = 0.0
            for (j in 0 until frameSize) {
                val norm = samples[start + j] / 32768.0
                e += norm * norm
            }
            frameEnergies.add(sqrt(e / frameSize))
        }

        val rms = sqrt(totalEnergy / samples.size.coerceAtLeast(1))
        val zcr = zeroCrossings.toDouble() / samples.size.coerceAtLeast(1)

        // Anti-spoof liveness checks:
        // 1) Sufficient vocal loudness (> 0.008)
        // 2) Natural syllable modulation variance across frames (rejects static/looped hum or silence)
        val meanFrame = frameEnergies.average().takeIf { !it.isNaN() } ?: 0.0
        val frameVariance = frameEnergies.map { (it - meanFrame) * (it - meanFrame) }.average().takeIf { !it.isNaN() } ?: 0.0
        val dynamicRange = (frameEnergies.maxOrNull() ?: 0.0) - (frameEnergies.minOrNull() ?: 0.0)

        val isLive = when {
            rms < 0.006 -> false
            dynamicRange < 0.015 -> false
            frameVariance < 1e-6 -> false
            else -> true
        }
        val reason = when {
            rms < 0.006 -> "Audio too quiet or silent"
            dynamicRange < 0.015 || frameVariance < 1e-6 -> "Flat non-human cadence detected"
            else -> "Live human vocal dynamics confirmed"
        }

        // 8-band Goertzel / DFT spectral energy profile across speech formants (150Hz - 3600Hz)
        val bandFreqs = floatArrayOf(180f, 320f, 540f, 850f, 1250f, 1750f, 2400f, 3200f)
        val window = samples.copyOfRange(0, samples.size.coerceAtMost(4096))
        val bandMagnitudes = FloatArray(bandFreqs.size) { idx ->
            goertzelMagnitude(window, sampleRate, bandFreqs[idx])
        }
        val spectralSum = bandMagnitudes.sum().coerceAtLeast(1e-6f)
        var centroid = 0f
        for (i in bandFreqs.indices) {
            centroid += bandFreqs[i] * (bandMagnitudes[i] / spectralSum)
        }

        val rawFeatures = FloatArray(12)
        for (i in 0 until 8) {
            rawFeatures[i] = bandMagnitudes[i] / spectralSum
        }
        rawFeatures[8] = (rms * 10.0).toFloat().coerceIn(0f, 1f)
        rawFeatures[9] = (zcr * 5.0).toFloat().coerceIn(0f, 1f)
        rawFeatures[10] = (centroid / 3600f).coerceIn(0f, 1f)
        rawFeatures[11] = (dynamicRange * 4.0).toFloat().coerceIn(0f, 1f)

        return VoiceSampleAnalysis(
            featureVector = normalize(rawFeatures),
            isLiveHumanSpeech = isLive,
            livenessReason = reason
        )
    }

    private fun goertzelMagnitude(samples: ShortArray, sampleRate: Int, targetFreq: Float): Float {
        val k = (0.5 + (samples.size * targetFreq) / sampleRate).toInt()
        val omega = (2.0 * PI * k) / samples.size
        val coeff = 2.0 * cos(omega)
        var q0 = 0.0
        var q1 = 0.0
        var q2 = 0.0
        for (s in samples) {
            q0 = coeff * q1 - q2 + (s / 32768.0)
            q2 = q1
            q1 = q0
        }
        val real = q1 - q2 * cos(omega)
        val imag = q2 * sin(omega)
        return sqrt(real * real + imag * imag).toFloat()
    }

    private fun normalize(v: FloatArray): FloatArray {
        var norm = 0f
        for (x in v) norm += x * x
        val denom = sqrt(norm).coerceAtLeast(1e-6f)
        return FloatArray(v.size) { v[it] / denom }
    }

    private fun cosineSimilarity(a: FloatArray, b: FloatArray): Float {
        var dot = 0f
        var na = 0f
        var nb = 0f
        for (i in a.indices) {
            dot += a[i] * b[i]
            na += a[i] * a[i]
            nb += b[i] * b[i]
        }
        val denom = (sqrt(na) * sqrt(nb)).coerceAtLeast(1e-6f)
        return (dot / denom).coerceIn(-1f, 1f)
    }
}
