package com.example.runpodmanager.data.whisper

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.tensorflow.lite.Interpreter
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

class Whisper(private val context: Context) {

    companion object {
        private const val TAG = "Whisper"
        const val ACTION_TRANSCRIBE = 1
    }

    private var interpreter: Interpreter? = null
    private var listener: IWhisperListener? = null
    private var isModelLoaded = false
    private var transcriptionJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Default)

    fun setListener(listener: IWhisperListener) {
        this.listener = listener
    }

    fun loadModel(modelPath: String, vocabPath: String, isMultilingual: Boolean): Boolean {
        return try {
            listener?.onUpdateReceived("Cargando modelo...")

            // Load TFLite model
            val modelFile = File(modelPath)
            if (!modelFile.exists()) {
                Log.e(TAG, "Model file not found: $modelPath")
                return false
            }

            val options = Interpreter.Options().apply {
                setNumThreads(4)
            }

            interpreter = Interpreter(modelFile, options)

            // Load vocabulary and filters
            if (!WhisperUtil.loadFiltersAndVocab(vocabPath, isMultilingual)) {
                Log.e(TAG, "Failed to load vocab")
                return false
            }

            isModelLoaded = true
            listener?.onUpdateReceived("Modelo cargado")
            Log.d(TAG, "Model loaded successfully")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error loading model", e)
            listener?.onUpdateReceived("Error cargando modelo: ${e.message}")
            false
        }
    }

    fun loadModelFromAssets(modelAssetPath: String, vocabAssetPath: String, isMultilingual: Boolean): Boolean {
        return try {
            listener?.onUpdateReceived("Cargando modelo...")

            // Copy model to internal storage if needed
            val modelFile = copyAssetToFile(modelAssetPath, "whisper_model.tflite")
            val vocabFile = copyAssetToFile(vocabAssetPath, "whisper_vocab.bin")

            if (modelFile == null || vocabFile == null) {
                Log.e(TAG, "Failed to copy assets")
                return false
            }

            loadModel(modelFile.absolutePath, vocabFile.absolutePath, isMultilingual)
        } catch (e: Exception) {
            Log.e(TAG, "Error loading model from assets", e)
            false
        }
    }

    private fun copyAssetToFile(assetPath: String, fileName: String): File? {
        return try {
            val outFile = File(context.filesDir, fileName)
            if (outFile.exists() && outFile.length() > 0) {
                return outFile
            }

            context.assets.open(assetPath).use { input ->
                FileOutputStream(outFile).use { output ->
                    input.copyTo(output)
                }
            }
            outFile
        } catch (e: Exception) {
            Log.e(TAG, "Error copying asset: $assetPath", e)
            null
        }
    }

    fun transcribeBuffer(samples: FloatArray) {
        if (!isModelLoaded) {
            listener?.onUpdateReceived("Modelo no cargado")
            return
        }

        transcriptionJob?.cancel()
        transcriptionJob = scope.launch {
            try {
                listener?.onUpdateReceived("Procesando audio...")

                // Generate mel spectrogram
                val melSpec = WhisperUtil.getMelSpectrogram(samples)

                // Run inference
                val result = runInference(melSpec)

                withContext(Dispatchers.Main) {
                    if (result.isNotEmpty()) {
                        listener?.onResultReceived(result)
                    } else {
                        listener?.onUpdateReceived("No se reconocio texto")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Transcription error", e)
                withContext(Dispatchers.Main) {
                    listener?.onUpdateReceived("Error: ${e.message}")
                }
            }
        }
    }

    private fun runInference(melSpec: FloatArray): String {
        val interp = interpreter ?: return ""

        try {
            // Prepare input tensor
            // Input shape: [1, 80, 3000] (batch, mels, time)
            val inputBuffer = ByteBuffer.allocateDirect(4 * WhisperUtil.WHISPER_N_MELS * WhisperUtil.WHISPER_MEL_LEN)
                .order(ByteOrder.nativeOrder())

            for (value in melSpec) {
                inputBuffer.putFloat(value)
            }
            inputBuffer.rewind()

            // Output tensor for tokens
            // Typical output is around 448 tokens max
            val maxTokens = 448
            val outputBuffer = ByteBuffer.allocateDirect(4 * maxTokens)
                .order(ByteOrder.nativeOrder())

            // Run inference
            interp.run(inputBuffer, outputBuffer)

            // Decode output tokens
            outputBuffer.rewind()
            val tokens = IntArray(maxTokens)
            for (i in 0 until maxTokens) {
                tokens[i] = outputBuffer.int
            }

            return WhisperUtil.decodeTokens(tokens)
        } catch (e: Exception) {
            Log.e(TAG, "Inference error", e)
            return ""
        }
    }

    fun stop() {
        transcriptionJob?.cancel()
    }

    fun release() {
        stop()
        interpreter?.close()
        interpreter = null
        isModelLoaded = false
    }

    fun isReady(): Boolean = isModelLoaded
}
