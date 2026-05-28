/**
 * jni_bridge.cpp — Points d'entrée JNI pour NativeGLEngine.
 * 
 * Tous les appels Java → natif passent par ici.
 * Les noms de fonctions suivent la convention JNI :
 * Java_<package>_<class>_<method> avec les points remplacés par des underscores.
 */

#include <jni.h>
#include <android/log.h>
#include <string>

#define LOG_TAG "NativeGLEngine"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)

// Forward declarations
#include "shader_compiler.h"
#include "shader_cache.h"
#include "native_memory.h"
#include "gl_interceptor.h"
#include "texture_manager.h"
#include "soc_optimizer.h"
#include "texture_compressor.h"
#include "off_heap_arena.h"

// ════════════════════════════════════════════════════
// ShaderCompilerBridge
// ════════════════════════════════════════════════════

extern "C" JNIEXPORT jbyteArray JNICALL
Java_fr_eaielectronic_nativeglengine_ShaderCompilerBridge_nativeCompileGLSLtoSPIRV(
        JNIEnv* env, jclass clazz,
        jstring glslSource, jint shaderType, jint socVendor) {
    
    const char* glsl = env->GetStringUTFChars(glslSource, nullptr);
    if (!glsl) return nullptr;

    std::vector<uint32_t> spirv;
    bool success = shader_compiler_compile_glsl(glsl, (ShaderType)shaderType, 
                                                 (SocVendor)socVendor, spirv);
    env->ReleaseStringUTFChars(glslSource, glsl);

    if (!success || spirv.empty()) return nullptr;

    size_t byteSize = spirv.size() * sizeof(uint32_t);
    jbyteArray result = env->NewByteArray(byteSize);
    env->SetByteArrayRegion(result, 0, byteSize, reinterpret_cast<const jbyte*>(spirv.data()));
    return result;
}

extern "C" JNIEXPORT jstring JNICALL
Java_fr_eaielectronic_nativeglengine_ShaderCompilerBridge_nativeConvertSPIRVtoESSL(
        JNIEnv* env, jclass clazz, jbyteArray spirvBytes) {
    
    jsize len = env->GetArrayLength(spirvBytes);
    jbyte* bytes = env->GetByteArrayElements(spirvBytes, nullptr);

    std::string essl = shader_compiler_spirv_to_essl(
        reinterpret_cast<const uint32_t*>(bytes), len / sizeof(uint32_t));
    
    env->ReleaseByteArrayElements(spirvBytes, bytes, JNI_ABORT);

    if (essl.empty()) return nullptr;
    return env->NewStringUTF(essl.c_str());
}

extern "C" JNIEXPORT jstring JNICALL
Java_fr_eaielectronic_nativeglengine_ShaderCompilerBridge_nativeGetDriverVersion(
        JNIEnv* env, jclass clazz) {
    std::string version = shader_compiler_get_driver_version();
    return env->NewStringUTF(version.c_str());
}

// ════════════════════════════════════════════════════
// NativeMemoryBridge
// ════════════════════════════════════════════════════

extern "C" JNIEXPORT jboolean JNICALL
Java_fr_eaielectronic_nativeglengine_NativeMemoryBridge_nativeInit(
        JNIEnv* env, jclass clazz) {
    return native_memory_init() ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jlong JNICALL
Java_fr_eaielectronic_nativeglengine_NativeMemoryBridge_nativeGetGPUBudget(
        JNIEnv* env, jclass clazz) {
    return (jlong) native_memory_get_gpu_budget();
}

extern "C" JNIEXPORT jlong JNICALL
Java_fr_eaielectronic_nativeglengine_NativeMemoryBridge_nativeGetGPUUsage(
        JNIEnv* env, jclass clazz) {
    return (jlong) native_memory_get_gpu_usage();
}

extern "C" JNIEXPORT jfloat JNICALL
Java_fr_eaielectronic_nativeglengine_NativeMemoryBridge_nativeGetGPUPressure(
        JNIEnv* env, jclass clazz) {
    return native_memory_get_gpu_pressure();
}

extern "C" JNIEXPORT jlong JNICALL
Java_fr_eaielectronic_nativeglengine_NativeMemoryBridge_nativeGetSystemAvailableMB(
        JNIEnv* env, jclass clazz) {
    return (jlong) native_memory_get_sys_available_mb();
}

extern "C" JNIEXPORT jint JNICALL
Java_fr_eaielectronic_nativeglengine_NativeMemoryBridge_nativeGetTemperature(
        JNIEnv* env, jclass clazz) {
    return native_memory_get_temperature();
}

extern "C" JNIEXPORT jboolean JNICALL
Java_fr_eaielectronic_nativeglengine_NativeMemoryBridge_nativeIsThermalThrottling(
        JNIEnv* env, jclass clazz) {
    return native_memory_is_thermal_throttling() ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT void JNICALL
Java_fr_eaielectronic_nativeglengine_NativeMemoryBridge_nativeDestroy(
        JNIEnv* env, jclass clazz) {
    native_memory_destroy();
}

// ════════════════════════════════════════════════════
// GLInterceptorBridge
// ════════════════════════════════════════════════════

extern "C" JNIEXPORT jboolean JNICALL
Java_fr_eaielectronic_nativeglengine_GLInterceptorBridge_nativeInstallHooks(
        JNIEnv* env, jclass clazz) {
    return gl_interceptor_install() ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT void JNICALL
Java_fr_eaielectronic_nativeglengine_GLInterceptorBridge_nativeUninstallHooks(
        JNIEnv* env, jclass clazz) {
    gl_interceptor_uninstall();
}

extern "C" JNIEXPORT jlong JNICALL
Java_fr_eaielectronic_nativeglengine_GLInterceptorBridge_nativeGetTotalGLCalls(
        JNIEnv* env, jclass clazz) {
    return (jlong) gl_interceptor_get_total_calls();
}

extern "C" JNIEXPORT jlong JNICALL
Java_fr_eaielectronic_nativeglengine_GLInterceptorBridge_nativeGetDedupedCalls(
        JNIEnv* env, jclass clazz) {
    return (jlong) gl_interceptor_get_deduped_calls();
}

extern "C" JNIEXPORT jlong JNICALL
Java_fr_eaielectronic_nativeglengine_GLInterceptorBridge_nativeGetDeferredTextures(
        JNIEnv* env, jclass clazz) {
    return (jlong) gl_interceptor_get_deferred_textures();
}

extern "C" JNIEXPORT void JNICALL
Java_fr_eaielectronic_nativeglengine_GLInterceptorBridge_nativeDrainTextureQueue(
        JNIEnv* env, jclass clazz, jint maxUploads) {
    texture_manager_drain(maxUploads);
}

// ════════════════════════════════════════════════════
// OffHeapArenaBridge (Module 7)
// ════════════════════════════════════════════════════

extern "C" JNIEXPORT jboolean JNICALL
Java_fr_eaielectronic_nativeglengine_NativeBufferManager_nativeInitArena(
        JNIEnv* env, jclass clazz, jlong poolSizeMB) {
    return NativeGLEngine::OffHeapArena::init(poolSizeMB) ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT void JNICALL
Java_fr_eaielectronic_nativeglengine_NativeBufferManager_nativeDestroyArena(
        JNIEnv* env, jclass clazz) {
    NativeGLEngine::OffHeapArena::destroy();
}

extern "C" JNIEXPORT jobject JNICALL
Java_fr_eaielectronic_nativeglengine_NativeBufferManager_nativeAllocate(
        JNIEnv* env, jclass clazz, jlong sizeBytes, jstring tagStr) {
    const char* tag = env->GetStringUTFChars(tagStr, nullptr);
    jobject buffer = NativeGLEngine::OffHeapArena::allocate(env, sizeBytes, tag);
    env->ReleaseStringUTFChars(tagStr, tag);
    return buffer;
}

extern "C" JNIEXPORT void JNICALL
Java_fr_eaielectronic_nativeglengine_NativeBufferManager_nativeFree(
        JNIEnv* env, jclass clazz, jobject buffer) {
    NativeGLEngine::OffHeapArena::free(env, buffer);
}

