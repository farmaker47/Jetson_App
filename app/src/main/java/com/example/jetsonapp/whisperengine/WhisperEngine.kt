package com.example.jetsonapp.whisperengine

import android.content.Context
import android.util.Log
import com.example.jetsonapp.utils.WhisperUtil
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer
import java.io.FileInputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel

class WhisperEngine(private val context: Context) : IWhisperEngine {
    private val mWhisperUtil = WhisperUtil()
    override var isInitialized = false
        private set
    private var interpreter: Interpreter? = null
    private val nativePtr // Native pointer to the TFLiteEngine instance
            : Long

    // Pre‑allocated direct buffers
    private lateinit var inputBuffer: TensorBuffer
    private lateinit var outputBuffer: TensorBuffer
    private lateinit var inputBuf: ByteBuffer

    @Throws(IOException::class)
    override fun initialize(
        modelPath: String?,
        vocabPath: String?,
        multilingual: Boolean
    ): Boolean {
        // Load model
        loadModel(modelPath)
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

    override fun transcribeFile(wavePath: String?): String {
        // Calculate Mel spectrogram
        // Log.d(TAG, "Calculating Mel spectrogram...")
        val time = System.currentTimeMillis()
        val melSpectrogram = getMelSpectrogram(wavePath)
        // Log.d(TAG, "Mel spectrogram is calculated...!")
        // Log.v("time_mel", (System.currentTimeMillis() - time).toString())

        // Perform inference
        // val time2 = System.currentTimeMillis()
        val result = runInference(melSpectrogram)
        // Log.d(TAG, "Inference is executed...!")
        Log.v("time_total_transcribe", (System.currentTimeMillis() - time).toString())
        return result
    }

    @Throws(IOException::class)
    private fun loadModel(modelPath: String?) {
        val fileDescriptor = context.assets.openFd(modelPath!!)
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        val startOffset = fileDescriptor.startOffset
        val declaredLength = fileDescriptor.declaredLength
        val retFile = fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
        fileDescriptor.close()

//        val compatList = CompatibilityList()
//        val options = Interpreter.Options().apply {
//            if (compatList.isDelegateSupportedOnThisDevice) {
//                // if the device has a supported GPU, add the GPU delegate
//                val delegateOptions = compatList.bestOptionsForThisDevice
//                this.addDelegate(GpuDelegate(delegateOptions))
//            } else {
//                // If the GPU is not supported, run on 7 threads
//                // Check instructions on how 7 threads were selected here
//                // https://ai.google.dev/edge/litert/models/measurement#native_benchmark_binary
//                this.setNumThreads(7)
//            }
//        }
//
//        interpreter = Interpreter(retFile, options)

        val tfliteOptions = Interpreter.Options()

        // tfliteOptions.setUseXNNPACK(true)
        tfliteOptions.setNumThreads(Runtime.getRuntime().availableProcessors())

        interpreter = Interpreter(retFile, tfliteOptions)

        // Also create input and output buffers
        val inTensor = interpreter!!.getInputTensor(0)
        val outTensor = interpreter!!.getOutputTensor(0)
        /*check(outTensor.dataType() == DataType.INT32) {
            "Whisper model output must be INT32"
        }*/

        inputBuffer = TensorBuffer.createFixedSize(inTensor.shape(), inTensor.dataType())
        outputBuffer = TensorBuffer.createFixedSize(outTensor.shape(), DataType.FLOAT32)
        val inputSize =
            inTensor.shape()[0] * inTensor.shape()[1] * inTensor.shape()[2] * java.lang.Float.BYTES
        inputBuf = ByteBuffer.allocateDirect(inputSize).order(ByteOrder.nativeOrder())
    }

//    private fun getMelSpectrogram(wavePath: String?): FloatArray {
//        // Get samples in PCM_FLOAT format
//        val samples = getSamples(wavePath)
//
//        val fixedInputSize = WhisperUtil.WHISPER_SAMPLE_RATE * WhisperUtil.WHISPER_CHUNK_SIZE
//        val inputSamples = FloatArray(fixedInputSize)
//        val copyLength = min(samples.size, fixedInputSize)
//        System.arraycopy(samples, 0, inputSamples, 0, copyLength)
//
//        val cores = Runtime.getRuntime().availableProcessors()
//        return mWhisperUtil.getMultiMelSpectrogram(inputSamples, inputSamples.size, cores)
//    }

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

    private fun runInference(inputData: FloatArray): String {

        outputBuffer.buffer.rewind()

        for (input in inputData) {
            inputBuf.putFloat(input)
        }
        inputBuffer.loadBuffer(inputBuf)

        // Run inference
        interpreter!!.run(inputBuffer.buffer, outputBuffer.buffer)

        // Retrieve the results
        val outputLen = outputBuffer.intArray.size
        // Log.d(TAG, "output_len: $outputLen")
        val result = StringBuilder()
        // val time = System.currentTimeMillis()
        for (i in 0 until outputLen) {
            val token = outputBuffer.buffer.getInt()
            if (token == mWhisperUtil.tokenEOT) break

            // Get word for token and Skip additional token
            if (token < mWhisperUtil.tokenEOT) {
                val word = mWhisperUtil.getWordFromToken(token)
                // Log.d(TAG, "Adding token: $token, word: $word")
                result.append(word)
            } else {
                // if (token == mWhisperUtil.tokenTranscribe) Log.d(TAG, "It is Transcription...")
                // if (token == mWhisperUtil.tokenTranslate) Log.d(TAG, "It is Translation...")
                // val word = mWhisperUtil.getWordFromToken(token)
                // Log.d(TAG, "Skipping token: $token, word: $word")
            }
        }
        // Log.v("inference_time_decode", (System.currentTimeMillis() - time).toString())
        return result.toString()
    }

    /*private fun printTensorDump(tensor: Tensor) {
        Log.d(TAG, "  shape.length: " + tensor.shape().size)
        for (i in tensor.shape().indices) Log.d(TAG, "    shape[" + i + "]: " + tensor.shape()[i])
        Log.d(TAG, "  dataType: " + tensor.dataType())
        Log.d(TAG, "  name: " + tensor.name())
        Log.d(TAG, "  numBytes: " + tensor.numBytes())
        Log.d(TAG, "  index: " + tensor.index())
        Log.d(TAG, "  numDimensions: " + tensor.numDimensions())
        Log.d(TAG, "  numElements: " + tensor.numElements())
        Log.d(TAG, "  shapeSignature.length: " + tensor.shapeSignature().size)
        Log.d(TAG, "  quantizationParams.getScale: " + tensor.quantizationParams().scale)
        Log.d(TAG, "  quantizationParams.getZeroPoint: " + tensor.quantizationParams().zeroPoint)
        Log.d(TAG, "==================================================================")
    }*/

    init {
        nativePtr = createEngine()
    }

    // Native methods
    private external fun createEngine(): Long
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
