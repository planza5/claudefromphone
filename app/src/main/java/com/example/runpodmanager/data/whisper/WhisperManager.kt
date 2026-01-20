package com.example.runpodmanager.data.whisper

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

interface WhisperManagerListener {
    fun onStateChanged(state: WhisperState)
    fun onTranscriptionResult(text: String)
    fun onError(message: String)
    fun onAmplitudeUpdate(amplitude: Float)
}

sealed class WhisperState {
    object Idle : WhisperState()
    object Loading : WhisperState()
    object Ready : WhisperState()
    object Listening : WhisperState()
    object Processing : WhisperState()
    data class Error(val message: String) : WhisperState()
}

class WhisperManager(private val context: Context) {

    companion object {
        private const val TAG = "WhisperManager"
        private const val MODEL_PATH = "models/whisper-tiny.tflite"
        private const val VOCAB_PATH = "models/filters_vocab_multilingual.bin"
    }

    private var whisper: Whisper? = null
    private var recorder: AudioRecorder? = null
    private var listener: WhisperManagerListener? = null
    private var currentState: WhisperState = WhisperState.Idle
    private val scope = CoroutineScope(Dispatchers.Main)

    fun setListener(listener: WhisperManagerListener) {
        this.listener = listener
    }

    fun initialize() {
        if (whisper != null && whisper!!.isReady()) {
            updateState(WhisperState.Ready)
            return
        }

        updateState(WhisperState.Loading)

        scope.launch(Dispatchers.IO) {
            try {
                whisper = Whisper(context).apply {
                    setListener(object : IWhisperListener {
                        override fun onUpdateReceived(message: String) {
                            Log.d(TAG, "Whisper update: $message")
                        }

                        override fun onResultReceived(result: String) {
                            scope.launch(Dispatchers.Main) {
                                updateState(WhisperState.Ready)
                                listener?.onTranscriptionResult(result)
                            }
                        }
                    })
                }

                val success = whisper!!.loadModelFromAssets(MODEL_PATH, VOCAB_PATH, true)

                scope.launch(Dispatchers.Main) {
                    if (success) {
                        recorder = AudioRecorder(context).apply {
                            setListener(object : IRecorderListener {
                                override fun onUpdateReceived(message: String) {
                                    Log.d(TAG, "Recorder update: $message")
                                }

                                override fun onRecordingComplete(samples: FloatArray) {
                                    updateState(WhisperState.Processing)
                                    whisper?.transcribeBuffer(samples)
                                }

                                override fun onAmplitudeUpdate(amplitude: Float) {
                                    listener?.onAmplitudeUpdate(amplitude)
                                }
                            })
                        }
                        updateState(WhisperState.Ready)
                    } else {
                        updateState(WhisperState.Error("Error cargando modelo"))
                        listener?.onError("Error cargando modelo Whisper")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Init error", e)
                scope.launch(Dispatchers.Main) {
                    updateState(WhisperState.Error(e.message ?: "Error desconocido"))
                    listener?.onError(e.message ?: "Error desconocido")
                }
            }
        }
    }

    fun startListening() {
        when (currentState) {
            is WhisperState.Ready -> {
                if (recorder?.hasPermission() != true) {
                    listener?.onError("Se requiere permiso de microfono")
                    return
                }
                updateState(WhisperState.Listening)
                recorder?.startRecording(maxDurationSeconds = 15)
            }
            is WhisperState.Loading -> {
                listener?.onError("Modelo cargando, espera un momento")
            }
            is WhisperState.Listening -> {
                // Already listening, stop and process
                stopListening()
            }
            is WhisperState.Processing -> {
                listener?.onError("Procesando audio anterior")
            }
            else -> {
                listener?.onError("Whisper no inicializado")
            }
        }
    }

    fun stopListening() {
        if (currentState is WhisperState.Listening) {
            recorder?.stopRecording()
            // State will be updated by recorder callback
        }
    }

    fun isListening(): Boolean = currentState is WhisperState.Listening

    fun isReady(): Boolean = currentState is WhisperState.Ready

    fun isLoading(): Boolean = currentState is WhisperState.Loading

    private fun updateState(state: WhisperState) {
        currentState = state
        listener?.onStateChanged(state)
    }

    fun release() {
        recorder?.release()
        whisper?.release()
        recorder = null
        whisper = null
        updateState(WhisperState.Idle)
    }
}
