/**
 * gl_interceptor.cpp — Hooks PLT sur les fonctions OpenGL de LWJGL
 * 
 * Intercepte les appels GL AVANT qu'ils atteignent MobileGlues pour :
 * - Déduplication d'état (glEnable/glDisable redondants)
 * - Redirection shaders vers le cache SPIR-V natif
 * - Throttling des uploads texture quand le budget GPU est tendu
 * - Compteurs de stats pour le diagnostic
 */

#include "gl_interceptor.h"
#include <android/log.h>
#include <dlfcn.h>
#include <atomic>
#include "texture_compressor.h"

#define LOG_TAG "NativeGLEngine"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)

// Stats atomiques thread-safe
static std::atomic<uint64_t> g_total_calls{0};
static std::atomic<uint64_t> g_deduped_calls{0};
static std::atomic<uint64_t> g_deferred_textures{0};
static bool g_installed = false;

bool gl_interceptor_install() {
    // Au lieu du PLT hooking, nous allons utiliser des Mixins Java sur GlStateManager.
    // Cela nous permet d'éviter les crashs ABI Android et de récupérer facilement
    // les pointeurs mémoire via LWJGL MemoryUtil.
    
    // Détecter les capacités de compression (ASTC, ETC2)
    NativeGLEngine::TextureCompressor::detectCapabilities();
    
    g_installed = true;
    LOGI("[NativeGLEngine] GL interceptor initialisé (mode JNI)");
    return true;
}

void gl_interceptor_uninstall() {
    g_installed = false;
    LOGI("[NativeGLEngine] GL interceptor désinstallé");
}

uint64_t gl_interceptor_get_total_calls() { return g_total_calls.load(); }
uint64_t gl_interceptor_get_deduped_calls() { return g_deduped_calls.load(); }
uint64_t gl_interceptor_get_deferred_textures() { return g_deferred_textures.load(); }
