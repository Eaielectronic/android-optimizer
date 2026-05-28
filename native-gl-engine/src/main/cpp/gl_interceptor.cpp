#include "gl_interceptor.h"
#include <android/log.h>
#include <atomic>
#include "texture_compressor.h"
#include "bytehook.h"
#include <GLES3/gl3.h>

#define LOG_TAG "NativeGLEngine"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)

static std::atomic<uint64_t> g_total_calls{0};
static std::atomic<uint64_t> g_deduped_calls{0};
static std::atomic<uint64_t> g_deferred_textures{0};
static bool g_installed = false;

// Variables pour vérifier si les optimisations sont activées via OptConfig
extern bool g_enable_tex_compress;
// ... on pourra ajouter d'autres flags ici lus via JNI

// Pointeur vers la fonction originale
typedef void (*glTexImage2D_t)(GLenum, GLint, GLint, GLsizei, GLsizei, GLint, GLenum, GLenum, const void*);
static glTexImage2D_t orig_glTexImage2D = nullptr;

static bytehook_stub_t stub_glTexImage2D = nullptr;

// Notre fonction de remplacement
static void proxy_glTexImage2D(GLenum target, GLint level, GLint internalformat, GLsizei width, GLsizei height, GLint border, GLenum format, GLenum type, const void* pixels) {
    BYTEHOOK_STACK_SCOPE();
    g_total_calls++;
    
    bool handled = false;
    if (g_enable_tex_compress && pixels != nullptr) {
        handled = NativeGLEngine::TextureCompressor::intercept(target, level, internalformat, width, height, format, type, pixels);
    }
    
    if (!handled) {
        // Appeler l'original
        BYTEHOOK_CALL_PREV(proxy_glTexImage2D, target, level, internalformat, width, height, border, format, type, pixels);
    }
}

bool gl_interceptor_install() {
    if (g_installed) return true;
    
    bytehook_init(BYTEHOOK_MODE_AUTOMATIC, false);

    NativeGLEngine::TextureCompressor::detectCapabilities();
    
    // Hook glTexImage2D in liblwjgl_opengl.so (or fallback libGL.so/libGLESv3.so)
    stub_glTexImage2D = bytehook_hook_single(
        "liblwjgl_opengl.so",
        NULL,
        "glTexImage2D",
        (void*)proxy_glTexImage2D,
        NULL,
        NULL
    );
    
    if (!stub_glTexImage2D) {
        // Fallback: try GLESv3 directly if lwjgl just passes through directly without PLT?
        // Actually LWJGL calls `dlsym` so it calls the real libGLESv3.so
        // But some LWJGL versions use PLT, so hooking lwjgl works. 
        // We will just hook libGLESv3.so globally as a fallback.
        stub_glTexImage2D = bytehook_hook_all(
            "libGLESv3.so",
            "glTexImage2D",
            (void*)proxy_glTexImage2D,
            NULL,
            NULL
        );
    }
    
    g_installed = true;
    LOGI("[NativeGLEngine] GL interceptor initialisé (mode bhook C++)");
    return true;
}

void gl_interceptor_uninstall() {
    if (stub_glTexImage2D) {
        bytehook_unhook(stub_glTexImage2D);
        stub_glTexImage2D = nullptr;
    }
    g_installed = false;
    LOGI("[NativeGLEngine] GL interceptor désinstallé");
}

uint64_t gl_interceptor_get_total_calls() { return g_total_calls.load(); }
uint64_t gl_interceptor_get_deduped_calls() { return g_deduped_calls.load(); }
uint64_t gl_interceptor_get_deferred_textures() { return g_deferred_textures.load(); }
