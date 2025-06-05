
# 🧠 Local Mobile Execution of STT, Function Calling, VLM, and TTS  
**Author**: Georgios Soloupis, AI and Android GDE  

## 🚀 Overview
Running complex AI workflows—like converting speech to text (STT), triggering app functions, processing images with vision-language models (VLMs), and synthesizing speech (TTS)—entirely on a **mobile device** is now a reality. This project shows how to combine these capabilities into a single Android application, all executed **locally**, with **no internet required**.

Key benefits include:
- 🔒 **Enhanced privacy** (no cloud uploads)
- ⚡ **Lower latency**
- 📱 **Real-time, personal AI experiences**
- 🌐 **Multilingual readiness** (140+ languages supported)

---

## 🧩 Components and Models Used

### 🗣️ Speech-to-Text (STT) with Whisper
- Model: [Whisper Tiny English](https://github.com/openai/whisper)
- Execution: On-device with TensorFlow Lite
- Preprocessing: Mel spectrogram in C++ for speed
- Files required:
  - `vocab.bin`
  - `whisper.tflite`

```cpp
JNIEXPORT jfloatArray JNICALL
Java_com_example_jetsonapp_whisperengine_WhisperEngine_transcribeFileWithMel(...) {
    // C++ code for spectrogram processing and Whisper inference
}
```

📍 *Full implementation is integrated in the Android project.*

---

### ⚙️ Function Calling (FC) with MediaPipe & Hammer 2.1
- Model: **Hammer 2.1 - 1.5B**
- Platform: MediaPipe LLM Inference API
- Example use: Open camera or photo gallery via voice command
- Backend: Configurable for **GPU** or **CPU**

```kotlin
val getCameraImage = FunctionDeclaration.newBuilder()
    .setName("getCameraImage")
    .setDescription("Function to open the camera")
    .build()
// Initialization and function call trigger logic...
```

📍 *See `JetsonViewModel.kt` for detailed logic.*

---

### 🖼️ Vision-Language Model (VLM) with Gemma 3n
- Model: **Gemma 3n**, available in **2B** and **4B**
- Features: Built-in vision support
- Inference Engine: MediaPipe LLM Inference
- Backend: GPU-accelerated
- Language support: 140+ languages

```kotlin
val options = LlmInferenceOptions.builder()
    .setModelPath("/data/local/tmp/gemma-3n-E2B-it-int4.task")
    .setPreferredBackend(Backend.GPU)
    .setMaxNumImages(1)
    .build()
```

📍 *Full code in the MediaPipe-backed session builder.*

---

### 🔈 Text-to-Speech (TTS) with ML Kit Language Detection
- Detects output language using ML Kit:
```kotlin
languageIdentifier.identifyLanguage(text)
    .addOnSuccessListener { languageCode ->
        val locale = Locale(languageCode)
        textToSpeech.setLanguage(locale)
        textToSpeech.speak(text, ...)
    }
```
- Supports offline speech synthesis
- Handles multilingual output dynamically

---

## 📹 Demo
🎥 **Watch a short demo video** recorded on a **Samsung S24 (12GB RAM)** with both VLM and function-calling models loaded on GPU.

[🔗 YouTube Demo](https://www.youtube.com/shorts/MvBJX54nkas)

---

## 🧪 Summary of Models
| Task              | Model                | Details                              |
|------------------|----------------------|--------------------------------------|
| Speech-to-Text   | Whisper Tiny English | vocab.bin, whisper.tflite            |
| Function Calling | Hammer 2.1 1.5B      | Supports GPU via MediaPipe           |
| VLM              | Gemma 3n (2B)        | Vision support, multilingual output  |
| Language ID      | ML Kit               | Google’s on-device API               |

---

## 📦 Setup & Build
Clone this project and switch to the correct branch:

```bash
git clone https://github.com/farmaker47/Jetson_App/tree/whisper_gemma3n_tts
```

Ensure you download and include the required model files in the correct folders (`assets`, `/data/local/tmp`, etc.)

---

## 🧭 Conclusion
This project brings together cutting-edge AI technologies to deliver fully local, multimodal AI on mobile. With no need for cloud infrastructure, this solution paves the way for fast, secure, and personalized user experiences—right in your pocket.

## #AISprint Google Cloud credits are provided for this project.
