#include <iostream>
#include <fstream>
#include <cstring>
#include <vector>
#include <thread>
#include <sstream>
#include <sys/time.h>

#include <sys/time.h>
#include "tensorflow/lite/core/interpreter.h"
#include "tensorflow/lite/kernels/register.h"
#include "tensorflow/lite/model.h"
#include "tensorflow/lite/optional_debug_tools.h"
#include "tensorflow/lite/delegates/gpu/delegate.h"
#include "tensorflow/lite/delegates/xnnpack/xnnpack_delegate.h"

#include "input_features.h"
#include "filters_vocab_multilingual.h"
#include "filters_vocab_en.h"
#include "whisper.h"
#include "wav_util.h"
#include "talkandexecute.h"

#include <android/log.h>

// Define constants
#define TIME_DIFF_MS(start, end) (((end.tv_sec - start.tv_sec) * 1000000) + (end.tv_usec - start.tv_usec))/1000

#define INFERENCE_ON_AUDIO_FILE 1
#define TIME_DIFF_MS(start, end) (((end.tv_sec - start.tv_sec) * 1000000) + (end.tv_usec - start.tv_usec))/1000
#define TFLITE_MINIMAL_CHECK(x)                              \
  if (!(x)) {                                                \
    fprintf(stderr, "Error at %s:%d", __FILE__, __LINE__); \
    exit(1);                                                 \
  }

int talkandexecute::loadModel(const char *modelPath, const bool isMultilingual) {

    const auto processor_count = std::thread::hardware_concurrency();

    std::cout << "Entering " << __func__ << "()" << std::endl;

    timeval start_time{}, end_time{};
    if (!g_whisper_tflite.is_whisper_tflite_initialized) {

        gettimeofday(&start_time, NULL);
        std::cout << "Initializing TFLite..." << std::endl;
        __android_log_print(ANDROID_LOG_INFO, "Initializing tflite...", "%s", "start");

        /////////////// Load filters and vocab data ///////////////

        const char *vocabData = nullptr;
        if (isMultilingual)
            vocabData = reinterpret_cast<const char *>(filters_vocab_multilingual);
        else
            vocabData = reinterpret_cast<const char *>(filters_vocab_en);

        // Read the magic number
        int magic = 0;
        std::memcpy(&magic, vocabData, sizeof(magic));
        vocabData += sizeof(magic);

        // Check the magic number
        if (magic != 0x57535052) { // 'WSPR'
            std::cerr << "Invalid vocab data (bad magic)" << std::endl;
            return -1;
        }

        // Load mel filters
        std::memcpy(&filters.n_mel, vocabData, sizeof(filters.n_mel));
        vocabData += sizeof(filters.n_mel);

        std::memcpy(&filters.n_fft, vocabData, sizeof(filters.n_fft));
        vocabData += sizeof(filters.n_fft);

        std::cout << "n_mel:" << filters.n_mel << " n_fft:" << filters.n_fft << std::endl;

        filters.data.resize(filters.n_mel * filters.n_fft);
        std::memcpy(filters.data.data(), vocabData, filters.data.size() * sizeof(float));
        vocabData += filters.data.size() * sizeof(float);

        // Load vocab
        int n_vocab = 0;
        std::memcpy(&n_vocab, vocabData, sizeof(n_vocab));
        vocabData += sizeof(n_vocab);

        std::cout << "n_vocab:" << n_vocab << std::endl;

        for (int i = 0; i < n_vocab; i++) {
            int len = 0;
            std::memcpy(&len, vocabData, sizeof(len));
            vocabData += sizeof(len);

            std::string word(vocabData, len);
            vocabData += len;

            g_vocab.id_to_token[i] = word;
        }

        // add additional vocab ids
        int n_vocab_additional = 51864;
        if (isMultilingual) {
            n_vocab_additional = 51865;
            g_vocab.token_eot++;
            g_vocab.token_sot++;
            g_vocab.token_prev++;
            g_vocab.token_solm++;
            g_vocab.token_not++;
            g_vocab.token_beg++;
        }

        for (int i = n_vocab; i < n_vocab_additional; i++) {
            std::string word;
            if (i > g_vocab.token_beg) {
                word = "[_TT_" + std::to_string(i - g_vocab.token_beg) + "]";
            } else if (i == g_vocab.token_eot) {
                word = "[_EOT_]";
            } else if (i == g_vocab.token_sot) {
                word = "[_SOT_]";
            } else if (i == g_vocab.token_prev) {
                word = "[_PREV_]";
            } else if (i == g_vocab.token_not) {
                word = "[_NOT_]";
            } else if (i == g_vocab.token_beg) {
                word = "[_BEG_]";
            } else {
                word = "[_extra_token_" + std::to_string(i) + "]";
            }
            g_vocab.id_to_token[i] = word;
            // printf("%s: g_vocab[%d] = '%s'", __func__, i, word.c_str());
        }


        /////////////// Load tflite model buffer ///////////////

        std::ifstream modelFile(modelPath, std::ios::binary | std::ios::ate);
        if (!modelFile.is_open()) {
            std::cerr << "Unable to open model file: " << modelPath << std::endl;
            __android_log_print(ANDROID_LOG_INFO, "Unable to open model file:", "%s", modelPath);
            return -1;
        }
        std::streamsize size = modelFile.tellg();
        modelFile.seekg(0, std::ios::beg);
        char *buffer = new char[size];

        // Read the model data into the buffer
        if (modelFile.read(buffer, size)) {
            modelFile.close();
        } else {
            std::cerr << "Error reading model data from file." << std::endl;
        }

        g_whisper_tflite.size = size;
        g_whisper_tflite.buffer = buffer;

        g_whisper_tflite.model = tflite::FlatBufferModel::BuildFromBuffer(g_whisper_tflite.buffer,
                                                                          g_whisper_tflite.size);
        TFLITE_MINIMAL_CHECK(g_whisper_tflite.model != nullptr);

        // auto* xnnpack_delegate = TfLiteXNNPackDelegateCreate(nullptr);

        // 2. Build the interpreter, adding the delegate
        tflite::ops::builtin::BuiltinOpResolver resolver;
        tflite::InterpreterBuilder builder(*(g_whisper_tflite.model), resolver);

        // XNNPACK delegate options
        TfLiteXNNPackDelegateOptions xnnpack_options = TfLiteXNNPackDelegateOptionsDefault();
        xnnpack_options.num_threads = processor_count;  // Optional: set number of threads

        // Create the XNNPACK delegate
        TfLiteDelegate *xnnpack_delegate = TfLiteXNNPackDelegateCreate(&xnnpack_options);
        if (xnnpack_delegate == nullptr) {
            std::cerr << "Failed to create XNNPACK delegate" << std::endl;
            __android_log_print(ANDROID_LOG_INFO, "Failed to create XNNPACK delegate", "time",
                                "TIME_DIFF_MS(start_time, end_time)");
        } else {
            // builder.AddDelegate(xnnpack_delegate); ###### Crashing.
            __android_log_print(ANDROID_LOG_INFO, "Successful creation of XNNPACK delegate", "timi",
                                "TIME_DIFF_MS(start_time, end_time)");
        }

        builder(&(g_whisper_tflite.interpreter));
        TFLITE_MINIMAL_CHECK(g_whisper_tflite.interpreter != nullptr);

        // Allocate tensors.
        TFLITE_MINIMAL_CHECK(g_whisper_tflite.interpreter->AllocateTensors() == kTfLiteOk);

        // Optional: set interpreter threading (XNNPACK handles threading internally)
        g_whisper_tflite.interpreter->SetNumThreads(processor_count);

        g_whisper_tflite.input = g_whisper_tflite.interpreter->typed_input_tensor<float>(0);
        g_whisper_tflite.is_whisper_tflite_initialized = true;

        // gettimeofday(&end_time, NULL);
        // std::cout << "Time taken for TFLite initialization: " << TIME_DIFF_MS(start_time, end_time) << " ms" << std::endl;
        // __android_log_print(ANDROID_LOG_INFO, "Time taken for TFLite initialization:", "%ld", TIME_DIFF_MS(start_time, end_time));
    }

    // std::cout << "Exiting " << __func__ << "()" << std::endl;
    return 0;
}

std::string talkandexecute::transcribeBuffer(std::vector<float> samples) {
    const auto processor_count = std::thread::hardware_concurrency();

    // --- Optimization 1: Process actual audio size, avoid padding ---
    // Instead of forcing all audio to 30s, we process the real length.
    // This dramatically reduces the workload for both the spectrogram and the TFLite model.
    // NOTE: This assumes your Whisper TFLite model supports dynamic input shapes
    // for the time dimension, which is standard for Whisper models. If your model
    // has a fixed input size (e.g., [1, 80, 3000]), you MUST keep the original padding.
    if (samples.empty()) {
        return "";
    }

    // Optional: If you must cap the audio length at 30 seconds
    if (samples.size() > WHISPER_SAMPLE_RATE * WHISPER_CHUNK_SIZE) {
        samples.resize(WHISPER_SAMPLE_RATE * WHISPER_CHUNK_SIZE);
    }

    // Calculate Mel spectrogram on the actual (potentially shorter) audio
    if (!log_mel_spectrogram_improved(samples.data(), samples.size(), WHISPER_SAMPLE_RATE,
                                      WHISPER_N_FFT,
                                      WHISPER_HOP_LENGTH, WHISPER_N_MEL, processor_count, filters,
                                      mel)) {
        std::cerr << "Failed to compute mel spectrogram" << std::endl;
        return "";
    }

    if (INFERENCE_ON_AUDIO_FILE) {
        // --- Part of Optimization 1: Resize TFLite input tensor ---
        // Tell the interpreter the actual shape of our input Mel spectrogram
        TfLiteStatus resize_status = g_whisper_tflite.interpreter->ResizeInputTensor(0,
                                                                                     {1, mel.n_mel,
                                                                                      mel.n_len});
        if (resize_status != kTfLiteOk) {
            std::cerr << "Failed to resize TFLite input tensor." << std::endl;
            return "";
        }

        // We MUST re-allocate tensors after resizing the input
        TfLiteStatus allocate_status = g_whisper_tflite.interpreter->AllocateTensors();
        if (allocate_status != kTfLiteOk) {
            std::cerr << "Failed to allocate TFLite tensors after resize." << std::endl;
            return "";
        }

        // Get the input tensor pointer *again* after allocation, as it might have changed
        float *input_tensor_ptr = g_whisper_tflite.interpreter->typed_input_tensor<float>(0);
        memcpy(input_tensor_ptr, mel.data.data(), mel.data.size() * sizeof(float));

    } else {
        // This branch uses a pre-generated, fixed-size feature set.
        // We assume the model is already sized correctly for this.
        memcpy(g_whisper_tflite.input, _content_input_features_bin,
               WHISPER_N_MEL * WHISPER_MEL_LEN * sizeof(float));
    }

    // --- Optimization 2: Remove redundant SetNumThreads call ---
    // This is now set once during initialization when we add the XNNPACK delegate.
    // Calling it every time is unnecessary.
    // g_whisper_tflite.interpreter->SetNumThreads(processor_count);

    // Run inference (now much faster on shorter audio thanks to XNNPACK + dynamic sizing)
    if (g_whisper_tflite.interpreter->Invoke() != kTfLiteOk) {
        return "";
    }

    const int *output_tokens = g_whisper_tflite.interpreter->typed_output_tensor<int>(0);
    const auto *output_tensor = g_whisper_tflite.interpreter->tensor(
            g_whisper_tflite.interpreter->outputs()[0]);
    const auto output_size = output_tensor->dims->data[output_tensor->dims->size - 1];

    // --- Optimization 3: Efficient string building ---
    // Use std::stringstream to avoid costly reallocations from std::string::operator+=
    std::stringstream ss;
    for (int i = 0; i < output_size; i++) {
        const int token = output_tokens[i];
        if (token == g_vocab.token_eot) {
            break;
        }

        if (token < g_vocab.token_eot) {
            // Append the token string to the stream
            ss << whisper_token_to_str(token);
        }
    }

    // Convert the stream to a string once at the very end
    return ss.str();
}

std::string talkandexecute::transcribeFile(const char *waveFile) {
    std::vector<float> pcmf32 = readWAVFile(waveFile);
    size_t originalSize = pcmf32.size();

    // Determine the number of chunks required to process the entire file
    size_t totalChunks = 1; //(originalSize + (WHISPER_SAMPLE_RATE * WHISPER_CHUNK_SIZE) - 1) /(WHISPER_SAMPLE_RATE * WHISPER_CHUNK_SIZE);

    std::string text;
    for (size_t chunkIndex = 0; chunkIndex < totalChunks; ++chunkIndex) {
        // __android_log_print(ANDROID_LOG_INFO, "Times this is running", "%s", "start");
        // Extract a chunk of audio data
        size_t startSample = chunkIndex * WHISPER_SAMPLE_RATE * WHISPER_CHUNK_SIZE;
        size_t endSample = std::min(startSample + (WHISPER_SAMPLE_RATE * WHISPER_CHUNK_SIZE),
                                    originalSize);
        std::vector<float> chunk(pcmf32.begin() + startSample, pcmf32.begin() + endSample);

        // Pad the chunk if it's smaller than the expected size
        if (chunk.size() < WHISPER_SAMPLE_RATE * WHISPER_CHUNK_SIZE) {
            chunk.resize(WHISPER_SAMPLE_RATE * WHISPER_CHUNK_SIZE, 0);
        }

        // Transcribe the chunk and append the result to the text
        std::string chunkText = transcribeBuffer(chunk);
        text += chunkText;
    }
    return text;
}

void talkandexecute::freeModel() {
    std::cout << "Entering " << __func__ << "()" << std::endl;

    if (g_whisper_tflite.interpreter)
        g_whisper_tflite.interpreter.reset();  // Reset interpreter to release resources

    if (g_whisper_tflite.model)
        g_whisper_tflite.model.reset();        // Reset model to free memory

    if (g_whisper_tflite.buffer) {
        std::cout << __func__ << ": free buffer " << g_whisper_tflite.buffer << " memory"
                  << std::endl;
        delete[] g_whisper_tflite.buffer;
    }

    // Set the flag to false to avoid issues in the re-initialization of the model
    if (g_whisper_tflite.is_whisper_tflite_initialized) {
        g_whisper_tflite.is_whisper_tflite_initialized = false;
    }

    // Reset the whisper_vocab structure to clear the vocab data
    g_vocab.reset();

    std::cout << "Exiting " << __func__ << "()" << std::endl;
}


std::vector<float>
talkandexecute::returnMelSpectrogram(std::vector<float> samples, std::vector<float> filtersJava) {
    // timeval start_time{}, end_time{};
    // gettimeofday(&start_time, NULL);

    // Hack if the audio file size is less than 30ms append with 0's
    samples.resize((WHISPER_SAMPLE_RATE * WHISPER_CHUNK_SIZE), 0);
    const auto processor_count = std::thread::hardware_concurrency();
    // __android_log_print(ANDROID_LOG_INFO, "TRACKERS_mel", "%s", "before");

    log_mel_spectrogramJava(samples.data(), samples.size(), WHISPER_SAMPLE_RATE, WHISPER_N_FFT,
                            WHISPER_HOP_LENGTH, WHISPER_N_MEL, processor_count, filtersJava, mel);
    // __android_log_print(ANDROID_LOG_INFO, "TRACKERS_mel", "%s", "after");

    // gettimeofday(&end_time, NULL);
    // std::cout << "Time taken for Spectrogram: " << TIME_DIFF_MS(start_time, end_time) << " ms" << std::endl;
    // target_link_libraries(audioEngine log) AT CMAKELIST.txt
    // __android_log_print(ANDROID_LOG_INFO, "TRACKERS_mel", "Time taken for Spectrogram: %ld ms", TIME_DIFF_MS(start_time, end_time));

    return mel.data;
}

std::vector<float>
talkandexecute::transcribeFileWithMel(const char *waveFile, std::vector<float> filtersJava) {
    std::vector<float> pcmf32 = readWAVFile(waveFile);
    // __android_log_print(ANDROID_LOG_INFO, "TRACKERS_mel", "%s", "wav");

    pcmf32.resize((WHISPER_SAMPLE_RATE * WHISPER_CHUNK_SIZE), 0);
    // __android_log_print(ANDROID_LOG_INFO, "TRACKERS_mel", "%s", "resize");
    std::vector<float> mel_spectrogram = returnMelSpectrogram(pcmf32, filtersJava);
    // __android_log_print(ANDROID_LOG_INFO, "TRACKERS_mel", "%s", "mel-end");
    return mel_spectrogram;
}
