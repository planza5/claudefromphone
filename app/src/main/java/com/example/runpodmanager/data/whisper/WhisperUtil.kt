package com.example.runpodmanager.data.whisper

import android.util.Log
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.sqrt

object WhisperUtil {
    private const val TAG = "WhisperUtil"

    // Whisper constants
    const val WHISPER_SAMPLE_RATE = 16000
    const val WHISPER_N_FFT = 400
    const val WHISPER_HOP_LENGTH = 160
    const val WHISPER_N_MELS = 80
    const val WHISPER_CHUNK_SIZE = 30 // seconds
    const val WHISPER_MEL_LEN = 3000 // (WHISPER_SAMPLE_RATE * WHISPER_CHUNK_SIZE) / WHISPER_HOP_LENGTH

    private var melFilters: Array<FloatArray>? = null
    private var vocabulary: List<String>? = null

    fun loadFiltersAndVocab(vocabPath: String, isMultilingual: Boolean): Boolean {
        return try {
            RandomAccessFile(vocabPath, "r").use { file ->
                // Read mel filters
                val nMels = file.readIntLE()
                val nFft = file.readIntLE()

                Log.d(TAG, "Loading filters: nMels=$nMels, nFft=$nFft")

                melFilters = Array(nMels) { FloatArray(nFft) }
                for (i in 0 until nMels) {
                    for (j in 0 until nFft) {
                        melFilters!![i][j] = file.readFloatLE()
                    }
                }

                // Read vocabulary
                val vocabSize = file.readIntLE()
                Log.d(TAG, "Loading vocabulary: size=$vocabSize")

                val vocabList = mutableListOf<String>()
                for (i in 0 until vocabSize) {
                    val wordLen = file.readIntLE()
                    val wordBytes = ByteArray(wordLen)
                    file.readFully(wordBytes)
                    vocabList.add(String(wordBytes, Charsets.UTF_8))
                }
                vocabulary = vocabList

                Log.d(TAG, "Loaded ${vocabulary!!.size} vocabulary items")
                true
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading filters and vocab", e)
            false
        }
    }

    private fun RandomAccessFile.readIntLE(): Int {
        val bytes = ByteArray(4)
        readFully(bytes)
        return ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).int
    }

    private fun RandomAccessFile.readFloatLE(): Float {
        val bytes = ByteArray(4)
        readFully(bytes)
        return ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).float
    }

    fun getMelSpectrogram(samples: FloatArray): FloatArray {
        val filters = melFilters ?: throw IllegalStateException("Filters not loaded")

        // Pad samples to 30 seconds
        val targetLen = WHISPER_SAMPLE_RATE * WHISPER_CHUNK_SIZE
        val paddedSamples = if (samples.size < targetLen) {
            samples.copyOf(targetLen)
        } else {
            samples.copyOf(targetLen)
        }

        // Calculate number of frames
        val nFrames = (paddedSamples.size - WHISPER_N_FFT) / WHISPER_HOP_LENGTH + 1

        // Output mel spectrogram
        val melSpec = FloatArray(WHISPER_N_MELS * WHISPER_MEL_LEN)

        // Hanning window
        val window = FloatArray(WHISPER_N_FFT) { i ->
            (0.5 * (1 - cos(2.0 * PI * i / WHISPER_N_FFT))).toFloat()
        }

        // Process each frame
        for (frame in 0 until minOf(nFrames, WHISPER_MEL_LEN)) {
            val start = frame * WHISPER_HOP_LENGTH

            // Apply window and get FFT
            val windowed = FloatArray(WHISPER_N_FFT) { i ->
                if (start + i < paddedSamples.size) {
                    paddedSamples[start + i] * window[i]
                } else {
                    0f
                }
            }

            // Compute FFT magnitude spectrum
            val fftMag = computeFFTMagnitude(windowed)

            // Apply mel filterbank
            for (mel in 0 until WHISPER_N_MELS) {
                var sum = 0f
                for (k in 0 until minOf(fftMag.size, filters[mel].size)) {
                    sum += filters[mel][k] * fftMag[k]
                }
                // Convert to log scale
                val logMel = log10(maxOf(sum, 1e-10f))
                melSpec[mel * WHISPER_MEL_LEN + frame] = logMel
            }
        }

        // Normalize
        var maxVal = Float.MIN_VALUE
        for (v in melSpec) {
            if (v > maxVal) maxVal = v
        }

        for (i in melSpec.indices) {
            melSpec[i] = maxOf((melSpec[i] - maxVal) / 4f + 1f, 0f)
        }

        return melSpec
    }

    private fun computeFFTMagnitude(input: FloatArray): FloatArray {
        val n = input.size
        val real = input.copyOf()
        val imag = FloatArray(n)

        // Simple DFT (for small FFT size this is acceptable)
        val halfN = n / 2 + 1
        val magnitude = FloatArray(halfN)

        for (k in 0 until halfN) {
            var sumReal = 0f
            var sumImag = 0f
            for (t in 0 until n) {
                val angle = 2.0 * PI * k * t / n
                sumReal += (real[t] * cos(angle)).toFloat()
                sumImag -= (real[t] * kotlin.math.sin(angle)).toFloat()
            }
            magnitude[k] = sqrt(sumReal * sumReal + sumImag * sumImag)
        }

        return magnitude
    }

    fun decodeTokens(tokens: IntArray): String {
        val vocab = vocabulary ?: return ""
        val result = StringBuilder()

        for (token in tokens) {
            if (token < 0 || token >= vocab.size) continue
            if (token == 50257 || token == 50256) break // EOT tokens

            val word = vocab[token]
            // Skip special tokens
            if (word.startsWith("<|") && word.endsWith("|>")) continue

            result.append(word)
        }

        return result.toString()
            .replace("Ġ", " ")
            .replace("▁", " ")
            .trim()
    }

    fun getVocabulary(): List<String>? = vocabulary
}
