package com.example.jetsonapp.whisperengine

import android.content.Context
import android.util.Log
import com.example.jetsonapp.utils.WhisperUtil

class WhisperEngineNative {
    private val nativePtr // Native pointer to the TFLiteEngine instance
            : Long
    private lateinit var context: Context
    private var isInitialized = false
    private val mWhisperUtil = WhisperUtil()

    init {
        nativePtr = createTFLiteEngine()
    }

    fun initialize(
        modelPath: String,
        vocabPath: String?,
        multilingual: Boolean
    ): Boolean {
        // Load model
        loadModel(nativePtr, modelPath, multilingual)
        // Log.d(TAG, "Model is loaded...$modelPath")

        // Load filters and vocab
        val ret = mWhisperUtil.loadFiltersAndVocab(multilingual, vocabPath!!)
        if (ret) {
            this.isInitialized = true
            // Log.d(TAG, "Filters and Vocab are loaded...$vocabPath")
        } else {
            this.isInitialized = false
            // Log.d(TAG, "Failed to load Filters and Vocab...")
        }
        return this.isInitialized
    }

    fun deinitialize() {
        freeModel()
    }

    fun transcribeBuffer(samples: FloatArray?): String? {
        return transcribeBuffer(nativePtr, samples!!)
    }

    fun transcribeFile(waveFile: String?): String? {
        val time = System.currentTimeMillis()
        // val melSpectrogram = getMelSpectrogram(waveFile)
        val result = transcribeFile(nativePtr, waveFile!!)
        // val result = transcribeBuffer(nativePtr, melSpectrogram)
        Log.v("time_total_transcribe", (System.currentTimeMillis() - time).toString())
        return result
    }

    private fun getMelSpectrogram(wavePath: String?): FloatArray {
        // Get samples in PCM_FLOAT format
        // val time = System.currentTimeMillis()
        /*val samples = getSamples(wavePath)
        // Log.v("inference_get_samples", (System.currentTimeMillis() - time).toString())
        val fixedInputSize = WhisperUtil.WHISPER_SAMPLE_RATE * WhisperUtil.WHISPER_CHUNK_SIZE
        val inputSamples = FloatArray(fixedInputSize)
        val copyLength = min(samples.size, fixedInputSize)
        System.arraycopy(samples, 0, inputSamples, 0, copyLength)*/
        // val time2 = System.currentTimeMillis()

        val value = transcribeFileWithMel(nativePtr, wavePath, mWhisperUtil.getFilters())
        // Log.v("inference_get_mel", (System.currentTimeMillis() - time2).toString())
        return value
    }

    private fun loadModel(modelPath: String, isMultilingual: Boolean): Int {
        return loadModel(nativePtr, modelPath, isMultilingual)
    }

    fun freeModel() {
        freeModel(nativePtr)
    }

    private external fun createTFLiteEngine(): Long
    private external fun loadModel(nativePtr: Long, modelPath: String, isMultilingual: Boolean): Int
    private external fun freeModel(nativePtr: Long)
    private external fun transcribeBuffer(nativePtr: Long, samples: FloatArray): String?
    private external fun transcribeFile(nativePtr: Long, waveFile: String): String?
    private external fun transcribeFileWithMel(
        nativePtr: Long,
        waveFile: String?,
        filters: FloatArray
    ): FloatArray


    companion object {
        init {
            System.loadLibrary("jetsonapp")
        }
    }
}