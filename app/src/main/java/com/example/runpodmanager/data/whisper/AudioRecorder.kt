package com.example.runpodmanager.data.whisper

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

interface IRecorderListener {
    fun onUpdateReceived(message: String)
    fun onRecordingComplete(samples: FloatArray)
    fun onAmplitudeUpdate(amplitude: Float)
}

class AudioRecorder(private val context: Context) {

    companion object {
        private const val TAG = "AudioRecorder"
        const val SAMPLE_RATE = 16000
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
    }

    private var audioRecord: AudioRecord? = null
    private var listener: IRecorderListener? = null
    private var recordingJob: Job? = null
    private var isRecording = false
    private val scope = CoroutineScope(Dispatchers.IO)

    private val bufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
        .coerceAtLeast(SAMPLE_RATE * 2) // At least 1 second buffer

    fun setListener(listener: IRecorderListener) {
        this.listener = listener
    }

    fun hasPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun startRecording(maxDurationSeconds: Int = 10) {
        if (isRecording) {
            Log.w(TAG, "Already recording")
            return
        }

        if (!hasPermission()) {
            listener?.onUpdateReceived("Permiso de microfono no concedido")
            return
        }

        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT,
                bufferSize
            )

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                listener?.onUpdateReceived("Error inicializando grabacion")
                return
            }

            isRecording = true
            audioRecord?.startRecording()
            listener?.onUpdateReceived("Escuchando...")

            recordingJob = scope.launch {
                recordAudio(maxDurationSeconds)
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "Security exception", e)
            listener?.onUpdateReceived("Error de permisos")
        } catch (e: Exception) {
            Log.e(TAG, "Error starting recording", e)
            listener?.onUpdateReceived("Error: ${e.message}")
        }
    }

    private suspend fun recordAudio(maxDurationSeconds: Int) {
        val maxSamples = SAMPLE_RATE * maxDurationSeconds
        val allSamples = mutableListOf<Float>()
        val buffer = ShortArray(bufferSize / 2)

        var silenceCount = 0
        val silenceThreshold = 500 // Amplitude threshold for silence
        val maxSilenceFrames = 15 // ~1.5 seconds of silence to stop

        try {
            while (currentCoroutineContext().isActive && isRecording && allSamples.size < maxSamples) {
                val readCount = audioRecord?.read(buffer, 0, buffer.size) ?: 0

                if (readCount > 0) {
                    // Convert to float and calculate amplitude
                    var maxAmplitude = 0
                    for (i in 0 until readCount) {
                        val sample = buffer[i].toInt()
                        allSamples.add(sample / 32768f)
                        if (kotlin.math.abs(sample) > maxAmplitude) {
                            maxAmplitude = kotlin.math.abs(sample)
                        }
                    }

                    // Update amplitude for UI feedback
                    withContext(Dispatchers.Main) {
                        listener?.onAmplitudeUpdate(maxAmplitude / 32768f)
                    }

                    // Voice activity detection
                    if (maxAmplitude < silenceThreshold) {
                        silenceCount++
                        if (silenceCount >= maxSilenceFrames && allSamples.size > SAMPLE_RATE) {
                            // Stop after silence detected (and we have at least 1 second of audio)
                            Log.d(TAG, "Silence detected, stopping recording")
                            break
                        }
                    } else {
                        silenceCount = 0
                    }
                }
            }

            isRecording = false
            audioRecord?.stop()

            val samples = allSamples.toFloatArray()
            Log.d(TAG, "Recording complete: ${samples.size} samples (${samples.size / SAMPLE_RATE.toFloat()}s)")

            withContext(Dispatchers.Main) {
                if (samples.size > SAMPLE_RATE / 2) { // At least 0.5 seconds
                    listener?.onRecordingComplete(samples)
                } else {
                    listener?.onUpdateReceived("Grabacion muy corta")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Recording error", e)
            withContext(Dispatchers.Main) {
                listener?.onUpdateReceived("Error de grabacion: ${e.message}")
            }
        }
    }

    fun stopRecording() {
        isRecording = false
        recordingJob?.cancel()
        try {
            audioRecord?.stop()
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping recording", e)
        }
    }

    fun release() {
        stopRecording()
        audioRecord?.release()
        audioRecord = null
    }

    fun isRecording(): Boolean = isRecording
}
