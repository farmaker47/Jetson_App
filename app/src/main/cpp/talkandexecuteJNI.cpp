#include <jni.h>
#include <vector>
#include "talkandexecute.h"
#include <android/log.h>

extern "C" {

JNIEXPORT jlong JNICALL
Java_com_example_jetsonapp_whisperengine_WhisperEngine_createEngine(JNIEnv *env,
                                                                    jobject thiz) {
    __android_log_print(ANDROID_LOG_INFO, "TRACKERS_init1", "%s", "start");
    return reinterpret_cast<jlong>(new talkandexecute());
}

JNIEXPORT jlong JNICALL
Java_com_example_jetsonapp_whisperengine_WhisperEngineNative_createTFLiteEngine(JNIEnv *env,
                                                                                jobject thiz) {
    __android_log_print(ANDROID_LOG_INFO, "TRACKERS_init2", "%s", "start");
    return reinterpret_cast<jlong>(new talkandexecute());
}

JNIEXPORT jint JNICALL
Java_com_example_jetsonapp_whisperengine_WhisperEngineNative_loadModel(JNIEnv *env, jobject thiz, jlong nativePtr, jstring modelPath, jboolean isMultilingual) {
    talkandexecute *engine = reinterpret_cast<talkandexecute *>(nativePtr);
    const char *cModelPath = env->GetStringUTFChars(modelPath, NULL);
    int result = engine->loadModel(cModelPath, isMultilingual);
    env->ReleaseStringUTFChars(modelPath, cModelPath);
    return static_cast<jint>(result);
}

JNIEXPORT jfloatArray JNICALL
Java_com_example_jetsonapp_whisperengine_WhisperEngine_transcribeFileWithMel(JNIEnv *env,
                                                                             jobject thiz,
                                                                             jlong nativePtr,
                                                                             jstring waveFile,
                                                                             jfloatArray filtersJava) {
    talkandexecute *engine = reinterpret_cast<talkandexecute *>(nativePtr);
    const char *cWaveFile = env->GetStringUTFChars(waveFile, NULL);

    // Step 1: Get the native array from jfloatArray
    jfloat *nativeFiltersArray = env->GetFloatArrayElements(filtersJava, NULL);
    jsize filtersSize = env->GetArrayLength(filtersJava);

    // Step 2: Convert the native array to std::vector<float>
    std::vector<float> filtersVector(nativeFiltersArray, nativeFiltersArray + filtersSize);

    // Release the native array
    env->ReleaseFloatArrayElements(filtersJava, nativeFiltersArray, JNI_ABORT);

    // Call the engine method to transcribe the file and get the result as a vector of floats
    std::vector<float> result = engine->transcribeFileWithMel(cWaveFile, filtersVector);

    env->ReleaseStringUTFChars(waveFile, cWaveFile);

    // Convert the result vector to a jfloatArray
    jfloatArray resultArray = env->NewFloatArray(result.size());
    env->SetFloatArrayRegion(resultArray, 0, result.size(), result.data());

    return resultArray;
}


} // extern "C"

extern "C"
JNIEXPORT void JNICALL
Java_com_example_jetsonapp_whisperengine_WhisperEngineNative_freeModel(JNIEnv *env, jobject thiz, jlong nativePtr) {
    talkandexecute *engine = reinterpret_cast<talkandexecute *>(nativePtr);
    engine->freeModel();
    delete engine;
}
extern "C"
JNIEXPORT jstring JNICALL
Java_com_example_jetsonapp_whisperengine_WhisperEngineNative_transcribeBuffer(JNIEnv *env, jobject thiz, jlong nativePtr, jfloatArray samples) {
    talkandexecute *engine = reinterpret_cast<talkandexecute *>(nativePtr);

    // Convert jfloatArray to std::vector<float>
    jsize len = env->GetArrayLength(samples);
    jfloat *data = env->GetFloatArrayElements(samples, 0);
    std::vector<float> sampleVector(data, data + len);
    env->ReleaseFloatArrayElements(samples, data, 0);

    std::string result = engine->transcribeBuffer(sampleVector);
    return env->NewStringUTF(result.c_str());
}

extern "C"
JNIEXPORT jstring JNICALL
Java_com_example_jetsonapp_whisperengine_WhisperEngineNative_transcribeFile(JNIEnv *env, jobject thiz, jlong nativePtr, jstring waveFile) {
    talkandexecute *engine = reinterpret_cast<talkandexecute *>(nativePtr);
    const char *cWaveFile = env->GetStringUTFChars(waveFile, NULL);
    std::string result = engine->transcribeFile(cWaveFile);
    env->ReleaseStringUTFChars(waveFile, cWaveFile);
    return env->NewStringUTF(result.c_str());
}

extern "C"
JNIEXPORT jfloatArray JNICALL
Java_com_example_jetsonapp_whisperengine_WhisperEngineNative_transcribeFileWithMel(JNIEnv *env,
                                                                                   jobject thiz,
                                                                                   jlong nativePtr,
                                                                                   jstring waveFile,
                                                                                   jfloatArray filtersJava) {
    talkandexecute *engine = reinterpret_cast<talkandexecute *>(nativePtr);
    const char *cWaveFile = env->GetStringUTFChars(waveFile, NULL);

    // Step 1: Get the native array from jfloatArray
    jfloat *nativeFiltersArray = env->GetFloatArrayElements(filtersJava, NULL);
    jsize filtersSize = env->GetArrayLength(filtersJava);

    // Step 2: Convert the native array to std::vector<float>
    std::vector<float> filtersVector(nativeFiltersArray, nativeFiltersArray + filtersSize);

    // Release the native array
    env->ReleaseFloatArrayElements(filtersJava, nativeFiltersArray, JNI_ABORT);

    // Call the engine method to transcribe the file and get the result as a vector of floats
    std::vector<float> result = engine->transcribeFileWithMel(cWaveFile, filtersVector);

    env->ReleaseStringUTFChars(waveFile, cWaveFile);

    // Convert the result vector to a jfloatArray
    jfloatArray resultArray = env->NewFloatArray(result.size());
    env->SetFloatArrayRegion(resultArray, 0, result.size(), result.data());

    return resultArray;
}
