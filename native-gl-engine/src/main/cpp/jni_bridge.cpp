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

bool g_verbose_logging = false;
#define LOGV(...) if (g_verbose_logging) { __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__); }

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
    std::string out_error;
    bool success = shader_compiler_compile_glsl(glsl, (ShaderType)shaderType, 
                                                 (SocVendor)socVendor, spirv, out_error);
    env->ReleaseStringUTFChars(glslSource, glsl);

    if (!success) {
        LOGW("[NativeGLEngine] nativeCompileGLSLtoSPIRV: compilation failed internally: %s", out_error.c_str());
        // Jette une exception RuntimeException en Java pour qu'elle s'affiche dans debug.log
        jclass exClass = env->FindClass("java/lang/RuntimeException");
        if (exClass) {
            env->ThrowNew(exClass, out_error.c_str());
        }
        return nullptr;
    }
    if (spirv.empty()) {
        LOGW("[NativeGLEngine] nativeCompileGLSLtoSPIRV: shaderc returned empty SPIR-V (empty source ?)");
        return nullptr;
    }

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

extern "C" JNIEXPORT void JNICALL
Java_fr_eaielectronic_nativeglengine_NativeMemoryBridge_nativeSetVerbose(
        JNIEnv* env, jclass clazz, jboolean verbose) {
    g_verbose_logging = verbose;
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

// Variables globales pour le config sync
int g_gpu_budget_percent = 75;
bool g_enable_tex_compress = true;
bool g_enable_vertex_quant = true;

extern "C" JNIEXPORT void JNICALL
Java_fr_eaielectronic_nativeglengine_NativeMemoryBridge_nativeUpdateConfig(
        JNIEnv* env, jclass clazz, jint gpuBudget, jboolean texCompress, jboolean vertexQuant) {
    g_gpu_budget_percent = gpuBudget;
    g_enable_tex_compress = texCompress;
    g_enable_vertex_quant = vertexQuant;
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
// Async Texture Compression Pipeline (Java Mixin → C++)
// ════════════════════════════════════════════════════

extern "C" JNIEXPORT jboolean JNICALL
Java_fr_eaielectronic_nativeglengine_GLInterceptorBridge_nativeSubmitAsyncCompress(
        JNIEnv* env, jclass clazz,
        jint target, jint level, jint internalFormat,
        jint width, jint height, jint border,
        jint format, jint type,
        jobject pixelsBuffer) {

    if (!pixelsBuffer) return JNI_FALSE;

    // Récupérer le pointeur direct vers les pixels Java (zéro-copie)
    void* pixels = env->GetDirectBufferAddress(pixelsBuffer);
    jlong bufferCapacity = env->GetDirectBufferCapacity(pixelsBuffer);

    if (!pixels || bufferCapacity <= 0) {
        // IntBuffer non-direct — fallback via GetIntArrayElements
        // (les IntBuffer Minecraft ne sont pas toujours direct)
        LOGV("nativeSubmitAsyncCompress: buffer not direct, using position-based copy");

        // Calculer la taille des pixels : width * height * 4 bytes (RGBA)
        size_t pixelSize = (size_t)width * height * 4;
        if (pixelSize == 0) return JNI_FALSE;

        // Lire les éléments via JNI
        jclass bufferClass = env->GetObjectClass(pixelsBuffer);
        jmethodID getMethod = env->GetMethodID(bufferClass, "get", "([I)Ljava/nio/IntBuffer;");
        jmethodID posMethod = env->GetMethodID(bufferClass, "position", "(I)Ljava/nio/IntBuffer;");
        jmethodID remainingMethod = env->GetMethodID(bufferClass, "remaining", "()I");

        // Save position, rewind, copy, restore
        jint remaining = env->CallIntMethod(pixelsBuffer, remainingMethod);
        if (remaining <= 0) return JNI_FALSE;

        jintArray tempArray = env->NewIntArray(remaining);
        if (!tempArray) return JNI_FALSE;

        env->CallObjectMethod(pixelsBuffer, getMethod, tempArray);

        jint* rawInts = env->GetIntArrayElements(tempArray, nullptr);
        if (!rawInts) {
            env->DeleteLocalRef(tempArray);
            return JNI_FALSE;
        }

        bool accepted = texture_manager_submit(
            target, level, internalFormat,
            width, height, border,
            format, type,
            rawInts, (size_t)remaining * sizeof(jint)
        );

        env->ReleaseIntArrayElements(tempArray, rawInts, JNI_ABORT);
        env->DeleteLocalRef(tempArray);

        return accepted ? JNI_TRUE : JNI_FALSE;
    }

    // Direct buffer — fast path
    size_t pixelSize = (size_t)bufferCapacity;
    bool accepted = texture_manager_submit(
        target, level, internalFormat,
        width, height, border,
        format, type,
        pixels, pixelSize
    );

    return accepted ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_fr_eaielectronic_nativeglengine_GLInterceptorBridge_nativeCompressAndUploadSync(
        JNIEnv* env, jclass clazz,
        jint target, jint level, jint internalFormat,
        jint width, jint height, jint border,
        jint format, jint type,
        jobject pixelsBuffer) {

    if (!pixelsBuffer) return JNI_FALSE;

    void* pixels = env->GetDirectBufferAddress(pixelsBuffer);
    if (!pixels) {
        // Fallback for non-direct buffers is omitted for sync upload
        // as it's meant to be fast and small textures usually use direct buffers
        return JNI_FALSE;
    }

    // Delegate to TextureCompressor::intercept which compresses synchronously and uploads immediately
    bool success = NativeGLEngine::TextureCompressor::intercept(
        target, level, internalFormat, width, height, format, type, pixels
    );
    
    return success ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jint JNICALL
Java_fr_eaielectronic_nativeglengine_GLInterceptorBridge_nativeDrainCompressedQueue(
        JNIEnv* env, jclass clazz,
        jint maxUploads, jint maxTimeUs) {
    return texture_manager_drain_compressed(maxUploads, maxTimeUs);
}

extern "C" JNIEXPORT jint JNICALL
Java_fr_eaielectronic_nativeglengine_GLInterceptorBridge_nativeGetPendingCount(
        JNIEnv* env, jclass clazz) {
    return texture_manager_get_pending_count();
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

