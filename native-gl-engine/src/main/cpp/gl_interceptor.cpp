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

#define LOG_TAG "NativeGLEngine"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)

// Stats atomiques thread-safe
static std::atomic<uint64_t> g_total_calls{0};
static std::atomic<uint64_t> g_deduped_calls{0};
static std::atomic<uint64_t> g_deferred_textures{0};
static bool g_installed = false;

bool gl_interceptor_install() {
    // TODO: Implémenter le PLT hooking quand le NDK sera configuré
    //
    // Algorithme :
    // 1. Trouver le handle LWJGL via dlopen(RTLD_NOLOAD)
    //    Tester : "liblwjgl_opengl.so", "libGL.so", "libGLESv3.so"
    // 2. Pour chaque fonction à hooker :
    //    - Trouver l'adresse originale via dlsym()
    //    - Sauvegarder le pointeur original
    //    - Remplacer dans la PLT par notre function patched_*
    // 3. Fonctions à hooker :
    //    - glShaderSource + glCompileShader → redirection cache SPIR-V
    //    - glTexImage2D + glTexSubImage2D → queue si budget GPU tendu
    //    - glEnable + glDisable → state dedup
    //    - glDrawArrays + glDrawElements → compteurs stats
    
    // Tenter de trouver le handle LWJGL
    void* handle = nullptr;
    const char* names[] = {
        "liblwjgl_opengl.so",
        "libGL.so",
        "libGLESv3.so",
        nullptr
    };
    
    for (int i = 0; names[i]; i++) {
        handle = dlopen(names[i], RTLD_NOLOAD | RTLD_LAZY);
        if (handle) {
            LOGI("[NativeGLEngine] Handle GL trouvé : %s", names[i]);
            break;
        }
    }
    
    if (!handle) {
        LOGW("[NativeGLEngine] Aucun handle GL trouvé — hooks non installés");
        return false;
    }
    
    // TODO: Installer les hooks PLT réels ici
    // Pour l'instant, on marque comme installé pour les stats
    g_installed = true;
    LOGI("[NativeGLEngine] GL interceptor installé (mode STUB)");
    return true;
}

void gl_interceptor_uninstall() {
    // TODO: Restaurer les pointeurs originaux dans la PLT
    g_installed = false;
    LOGI("[NativeGLEngine] GL interceptor désinstallé");
}

uint64_t gl_interceptor_get_total_calls() { return g_total_calls.load(); }
uint64_t gl_interceptor_get_deduped_calls() { return g_deduped_calls.load(); }
uint64_t gl_interceptor_get_deferred_textures() { return g_deferred_textures.load(); }
