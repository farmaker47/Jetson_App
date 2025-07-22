#ifndef _WHISPER_H_
#define _WHISPER_H_

#include <iostream>
#include <fstream>
#include <vector>
#include <thread>
#include <cmath>
#include <map>
#include <string>
#include <memory>
#include <algorithm> // For std::max and std::fill
#include <atomic>    // For atomic_max in C++20, or custom implementation
#include "tensorflow/lite/core/model_builder.h"
#include "tensorflow/lite/core/kernels/register.h"

// Define constants
#define WHISPER_SAMPLE_RATE 16000
#define WHISPER_N_FFT 400
#define WHISPER_N_MEL 80
#define WHISPER_HOP_LENGTH 160
#define WHISPER_CHUNK_SIZE 30
#define WHISPER_MEL_LEN 3000

// Forward declarations
struct whisper_vocab;
struct whisper_filters;
struct whisper_mel;
const char* whisper_token_to_str(int token);
bool log_mel_spectrogram(const float* samples, const int n_samples, const int sample_rate,
                        const int fft_size, const int fft_step, const int n_mel,
                        const int n_threads, const whisper_filters& filters, whisper_mel& mel);

// whisper_vocab structure
struct whisper_vocab {

    std::map<int, std::string> id_to_token;

    int n_vocab_additional = 51864; 

    int token_eot = 50256;
    int token_sot = 50257;
    int token_prev = 50360;
    int token_solm = 50361; // ??
    int token_not = 50362; // no timestamps
    int token_beg = 50363;

    static const int token_translwordate = 50358;
    static const int token_transcribe = 50359;

    // Reset the whisper_vocab structure
    void reset() {
        id_to_token.clear();
        n_vocab_additional = 51864;
        token_eot = 50256;
        token_sot = 50257;
        token_prev = 50360;
        token_solm = 50361;
        token_not = 50362;
        token_beg = 50363;
    }
};

// Global whisper_vocab instance
whisper_vocab g_vocab;

// whisper_tflite structure
struct whisper_tflite {
    char* buffer = nullptr;
    long size = 0;
    std::unique_ptr<tflite::FlatBufferModel> model;
    tflite::ops::builtin::BuiltinOpResolver resolver;
    std::unique_ptr<tflite::Interpreter> interpreter;
    float* input;

    bool is_whisper_tflite_initialized = false;
};

whisper_tflite g_whisper_tflite;

// whisper_filters structure
struct whisper_filters {
    int n_mel;
    int n_fft;

    std::vector<float> data;
};

whisper_filters filters;

// whisper_mel structure
struct whisper_mel {
    int n_len;
    int n_mel;

    std::vector<float> data;
};

whisper_mel mel;

// Print a vector of float values
void print(const std::vector<float>& a) {
    std::cout << "The vector elements are: ";

    for (int i = 0; i < a.size(); i++)
        std::cout << a.at(i) << ' ';
}

// Convert a token to a string
const char* whisper_token_to_str(int token) {
    return g_vocab.id_to_token.at(token).c_str();
}

// Naive Discrete Fourier Transform
void dft(const std::vector<float>& in, std::vector<float>& out) {
    int N = in.size();
    out.resize(N * 2);

    for (int k = 0; k < N; k++) {
        float re = 0;
        float im = 0;

        for (int n = 0; n < N; n++) {
            float angle = 2 * M_PI * k * n / N;
            re += in[n] * cos(angle);
            im -= in[n] * sin(angle);
        }

        out[k * 2 + 0] = re;
        out[k * 2 + 1] = im;
    }
}

// Cooley-Tukey FFT
void fft(const std::vector<float>& in, std::vector<float>& out) {
    out.resize(in.size() * 2);

    int N = in.size();

    if (N == 1) {
        out[0] = in[0];
        out[1] = 0;
        return;
    }

    if (N % 2 == 1) {
        dft(in, out);
        return;
    }

    std::vector<float> even;
    std::vector<float> odd;

    for (int i = 0; i < N; i++) {
        if (i % 2 == 0) {
            even.push_back(in[i]);
        } else {
            odd.push_back(in[i]);
        }
    }

    std::vector<float> even_fft;
    std::vector<float> odd_fft;

    fft(even, even_fft);
    fft(odd, odd_fft);

    for (int k = 0; k < N / 2; k++) {
        float theta = 2 * M_PI * k / N;

        float re = cos(theta);
        float im = -sin(theta);

        float re_odd = odd_fft[2 * k + 0];
        float im_odd = odd_fft[2 * k + 1];

        out[2 * k + 0] = even_fft[2 * k + 0] + re * re_odd - im * im_odd;
        out[2 * k + 1] = even_fft[2 * k + 1] + re * im_odd + im * re_odd;

        out[2 * (k + N / 2) + 0] = even_fft[2 * k + 0] - re * re_odd + im * im_odd;
        out[2 * (k + N / 2) + 1] = even_fft[2 * k + 1] - re * im_odd - im * re_odd;
    }
}

// A helper for parallel max finding, safe for C++11/14/17
// For C++20, you could use std::atomic<double>::fetch_max
void atomic_max(std::atomic<double>& maximum_value, double const value) noexcept {
    double prev_value = maximum_value;
    while (prev_value < value && !maximum_value.compare_exchange_weak(prev_value, value)) {
    }
}

// Assume fft() is defined elsewhere
void fft(const std::vector<float>& in, std::vector<float>& out);

bool log_mel_spectrogram_improved(const float* samples, const int n_samples, const int sample_rate,
                                  const int fft_size, const int fft_step, const int n_mel,
                                  const int n_threads, const whisper_filters& filters, whisper_mel& mel) {
    // --- Pre-computation (mostly unchanged) ---
    std::vector<float> hann(fft_size);
    for (int i = 0; i < fft_size; i++) {
        hann[i] = 0.5f * (1.0f - cosf((2.0f * M_PI * i) / fft_size));
    }

    mel.n_mel = n_mel;
    mel.n_len = n_samples / fft_step;
    mel.data.resize(mel.n_mel * mel.n_len);

    const int n_fft = 1 + fft_size / 2;

    // --- Main computation loop (parallelized) ---
    std::vector<std::thread> workers(n_threads);
    for (int iw = 0; iw < n_threads; ++iw) {
        workers[iw] = std::thread([&](int ith) {
            // Improvement 1: Allocate FFT buffers once per thread, outside the loop
            std::vector<float> fft_in(fft_size);
            std::vector<float> fft_out(2 * fft_size); // For complex output

            for (int i = ith; i < mel.n_len; i += n_threads) {
                const int offset = i * fft_step;

                // Improvement 2: Optimize windowing and padding
                // This avoids the 'if' branch in the inner loop for most samples
                const int frame_end = offset + fft_size;
                if (frame_end < n_samples) {
                    for (int j = 0; j < fft_size; j++) {
                        fft_in[j] = hann[j] * samples[offset + j];
                    }
                } else {
                    // This frame requires padding
                    std::fill(fft_in.begin(), fft_in.end(), 0.0f);
                    for (int j = 0; j < fft_size; j++) {
                        if (offset + j < n_samples) {
                            fft_in[j] = hann[j] * samples[offset + j];
                        }
                    }
                }

                // FFT -> mag^2 (Power Spectrum)
                fft(fft_in, fft_out);

                // Note: The original code for power spectrum calculation was a bit unusual.
                // A more standard approach is shown here, which calculates power for n_fft bins.
                std::vector<float> power_spectrum(n_fft);
                for (int j = 0; j < n_fft; j++) {
                    power_spectrum[j] = (fft_out[2 * j + 0] * fft_out[2 * j + 0] +
                                         fft_out[2 * j + 1] * fft_out[2 * j + 1]);
                }

                // Mel spectrogram calculation
                for (int j = 0; j < mel.n_mel; j++) {
                    // Improvement 3: Use float, and potential for SIMD
                    float sum = 0.0f;
                    const float* filter_row = &filters.data[j * n_fft];

                    // This loop is a dot product, a prime candidate for optimization
                    // #pragma omp simd reduction(+:sum) // Hint for compiler vectorization
                    for (int k = 0; k < n_fft; k++) {
                        sum += power_spectrum[k] * filter_row[k];
                    }

                    sum = (sum < 1e-10f) ? 1e-10f : sum;
                    mel.data[j * mel.n_len + i] = log10f(sum);
                }
            }
        }, iw);
    }

    for (int iw = 0; iw < n_threads; ++iw) {
        workers[iw].join();
    }

    // --- Post-processing (now also parallelized) ---
    // Improvement 4: Parallelize the max-finding and normalization steps

    // 4a. Parallel find max (reduction)
    std::atomic<double> mmax_atomic(-1e20);
    for (int iw = 0; iw < n_threads; ++iw) {
        workers[iw] = std::thread([&](int ith) {
            double local_mmax = -1e20;
            // Each thread processes a chunk of the data
            for (int i = ith; i < mel.data.size(); i += n_threads) {
                if (mel.data[i] > local_mmax) {
                    local_mmax = mel.data[i];
                }
            }
            atomic_max(mmax_atomic, local_mmax);
        }, iw);
    }
    for (auto& worker : workers) {
        worker.join();
    }
    double mmax = mmax_atomic.load() - 8.0;

    // 4b. Parallel clamping and normalization
    for (int iw = 0; iw < n_threads; ++iw) {
        workers[iw] = std::thread([&](int ith) {
            for (int i = ith; i < mel.data.size(); i += n_threads) {
                float val = mel.data[i];
                val = (val < mmax) ? mmax : val;
                mel.data[i] = (val + 4.0f) / 4.0f;
            }
        }, iw);
    }
    for (auto& worker : workers) {
        worker.join();
    }

    return true;
}

bool log_mel_spectrogram(const float* samples, const int n_samples, const int sample_rate,
                        const int fft_size, const int fft_step, const int n_mel,
                        const int n_threads, const whisper_filters& filters, whisper_mel& mel) {
    std::vector<float> hann;
    hann.resize(fft_size);

    for (int i = 0; i < fft_size; i++) {
        hann[i] = 0.5 * (1.0 - cos((2.0 * M_PI * i) / fft_size));
    }

    mel.n_mel = n_mel;
    mel.n_len = (n_samples) / fft_step;
    mel.data.resize(mel.n_mel * mel.n_len);

    // std::cout << "n_mel: " << mel.n_mel << std::endl;
    // std::cout << "n_len: " << mel.n_len << std::endl;

    const int n_fft = 1 + fft_size / 2;

    std::vector<std::thread> workers(n_threads);
    for (int iw = 0; iw < n_threads; ++iw) {
        workers[iw] = std::thread([&](int ith) {
            std::vector<float> fft_in;
            fft_in.resize(fft_size);
            for (int i = 0; i < fft_size; i++) {
                fft_in[i] = 0.0;
            }

            std::vector<float> fft_out;
            fft_out.resize(2 * fft_size);

            for (int i = ith; i < mel.n_len; i += n_threads) {
                const int offset = i * fft_step;

                // apply Hanning window
                for (int j = 0; j < fft_size; j++) {
                    if (offset + j < n_samples) {
                        fft_in[j] = hann[j] * samples[offset + j];
                    } else {
                        fft_in[j] = 0.0;
                    }
                }

                // FFT -> mag^2
                fft(fft_in, fft_out);

                for (int j = 0; j < fft_size; j++) {
                    fft_out[j] = (fft_out[2 * j + 0] * fft_out[2 * j + 0] + fft_out[2 * j + 1] * fft_out[2 * j + 1]);
                }

                for (int j = 1; j < fft_size / 2; j++) {
                    fft_out[j] += fft_out[fft_size - j];
                }

                // mel spectrogram
                for (int j = 0; j < mel.n_mel; j++) {
                    double sum = 0.0;

                    for (int k = 0; k < n_fft; k++) {
                        sum += fft_out[k] * filters.data[j * n_fft + k];
                    }

                    if (sum < 1e-10) {
                        sum = 1e-10;
                    }

                    sum = log10(sum);

                    mel.data[j * mel.n_len + i] = sum;
                }
            }
        }, iw);
    }

    // Wait for all threads to finish
    for (int iw = 0; iw < n_threads; ++iw) {
        workers[iw].join();
    }

    // clamping and normalization
    double mmax = -1e20;
    for (int i = 0; i < mel.n_mel * mel.n_len; i++) {
        if (mel.data[i] > mmax) {
            mmax = mel.data[i];
        }
    }

    mmax -= 8.0;

    for (int i = 0; i < mel.n_mel * mel.n_len; i++) {
        if (mel.data[i] < mmax) {
            mel.data[i] = mmax;
        }

        mel.data[i] = (mel.data[i] + 4.0) / 4.0;
    }

    return true;
}


// Log mel spectrogram computation
bool log_mel_spectrogramJava(const float* samples, const int n_samples, const int sample_rate,
                             const int fft_size, const int fft_step, const int n_mel,
                             const int n_threads, const std::vector<float> filtersJava, whisper_mel& mel) {
    std::vector<float> hann;
    hann.resize(fft_size);

    for (int i = 0; i < fft_size; i++) {
        hann[i] = 0.5 * (1.0 - cos((2.0 * M_PI * i) / fft_size));
    }

    mel.n_mel = n_mel;
    mel.n_len = (n_samples) / fft_step;
    mel.data.resize(mel.n_mel * mel.n_len);

    // std::cout << "n_mel: " << mel.n_mel << std::endl;
    // std::cout << "n_len: " << mel.n_len << std::endl;

    const int n_fft = 1 + fft_size / 2;

    std::vector<std::thread> workers(n_threads);
    for (int iw = 0; iw < n_threads; ++iw) {
        workers[iw] = std::thread([&](int ith) {
            std::vector<float> fft_in;
            fft_in.resize(fft_size);
            for (int i = 0; i < fft_size; i++) {
                fft_in[i] = 0.0;
            }

            std::vector<float> fft_out;
            fft_out.resize(2 * fft_size);

            for (int i = ith; i < mel.n_len; i += n_threads) {
                const int offset = i * fft_step;

                // apply Hanning window
                for (int j = 0; j < fft_size; j++) {
                    if (offset + j < n_samples) {
                        fft_in[j] = hann[j] * samples[offset + j];
                    } else {
                        fft_in[j] = 0.0;
                    }
                }

                // FFT -> mag^2
                fft(fft_in, fft_out);

                for (int j = 0; j < fft_size; j++) {
                    fft_out[j] = (fft_out[2 * j + 0] * fft_out[2 * j + 0] + fft_out[2 * j + 1] * fft_out[2 * j + 1]);
                }

                for (int j = 1; j < fft_size / 2; j++) {
                    fft_out[j] += fft_out[fft_size - j];
                }

                // mel spectrogram
                for (int j = 0; j < mel.n_mel; j++) {
                    double sum = 0.0;

                    for (int k = 0; k < n_fft; k++) {
                        sum += fft_out[k] * filtersJava[j * n_fft + k];
                    }

                    if (sum < 1e-10) {
                        sum = 1e-10;
                    }

                    sum = log10(sum);

                    mel.data[j * mel.n_len + i] = sum;
                }
            }
        }, iw);
    }

    // Wait for all threads to finish
    for (int iw = 0; iw < n_threads; ++iw) {
        workers[iw].join();
    }

    // clamping and normalization
    double mmax = -1e20;
    for (int i = 0; i < mel.n_mel * mel.n_len; i++) {
        if (mel.data[i] > mmax) {
            mmax = mel.data[i];
        }
    }

    mmax -= 8.0;

    for (int i = 0; i < mel.n_mel * mel.n_len; i++) {
        if (mel.data[i] < mmax) {
            mel.data[i] = mmax;
        }

        mel.data[i] = (mel.data[i] + 4.0) / 4.0;
    }

    return true;
}

#endif // _WHISPER_H_
