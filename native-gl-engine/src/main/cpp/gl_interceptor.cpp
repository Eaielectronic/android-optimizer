#include "gl_interceptor.h"
#include <android/log.h>
#include <atomic>
#include "texture_compressor.h"
#include "bytehook.h"
#include <GLES3/gl3.h>
#include <GLES3/gl3.h>
#include <dlfcn.h>
#include <cstring>
#define LOG_TAG "NativeGLEngine"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

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

typedef void* (*eglGetProcAddress_t)(const char*);

static eglGetProcAddress_t orig_eglGetProcAddress = nullptr;

static bytehook_stub_t stub_glTexImage2D = nullptr;
static bytehook_stub_t stub_eglGetProcAddress = nullptr;

// Forward declaration
static void proxy_glTexImage2D(GLenum target, GLint level, GLint internalformat, GLsizei width, GLsizei height, GLint border, GLenum format, GLenum type, const void* pixels);

// Proxy eglGetProcAddress
static void* proxy_eglGetProcAddress(const char* procname) {
    BYTEHOOK_STACK_SCOPE();
    void* ret = BYTEHOOK_CALL_PREV(proxy_eglGetProcAddress, procname);

    // On hook uniquement glTexImage2D pour ne pas casser la traduction shader de MobileGlues
    if (ret && procname && strcmp(procname, "glTexImage2D") == 0) {
        orig_glTexImage2D = (glTexImage2D_t)ret;
        return (void*)proxy_glTexImage2D;
    }
    
    return ret;
}

// Notre fonction de remplacement
static void proxy_glTexImage2D(GLenum target, GLint level, GLint internalformat, GLsizei width, GLsizei height, GLint border, GLenum format, GLenum type, const void* pixels) {
    g_total_calls++;
    
    bool handled = false;
    if (g_enable_tex_compress && pixels != nullptr) {
        handled = NativeGLEngine::TextureCompressor::intercept(target, level, internalformat, width, height, format, type, pixels);
    }
    
    if (!handled && orig_glTexImage2D) {
        // Appeler l'original directement, cela évite les problèmes avec BYTEHOOK_CALL_PREV
        // si la fonction a été appelée via pointeur de fonction direct.
        orig_glTexImage2D(target, level, internalformat, width, height, border, format, type, pixels);
    }
}

bool gl_interceptor_install() {
    if (g_installed) return true;
    
    bytehook_init(BYTEHOOK_MODE_AUTOMATIC, false);

    NativeGLEngine::TextureCompressor::detectCapabilities();
    
    // 1. Récupérer le vrai pointeur de glTexImage2D et eglGetProcAddress
    void* handleEGL = dlopen("libEGL.so", RTLD_LAZY);
    if (handleEGL) {
        orig_eglGetProcAddress = (eglGetProcAddress_t)dlsym(handleEGL, "eglGetProcAddress");
        if (orig_eglGetProcAddress) {
            orig_glTexImage2D = (glTexImage2D_t)orig_eglGetProcAddress("glTexImage2D");
        }
        dlclose(handleEGL);
    }
    if (!orig_glTexImage2D) {
        void* handleGLES = dlopen("libGLESv3.so", RTLD_LAZY);
        if (handleGLES) {
            orig_glTexImage2D = (glTexImage2D_t)dlsym(handleGLES, "glTexImage2D");
            dlclose(handleGLES);
        }
    }
    
    if (!orig_glTexImage2D) {
        LOGE("[NativeGLEngine] Impossible de trouver orig_glTexImage2D !");
        return false;
    }

    // 2. Hook eglGetProcAddress pour bypasser le masquage des wrappers comme MobileGlues
    stub_eglGetProcAddress = bytehook_hook_all(
        NULL,
        "eglGetProcAddress",
        (void*)proxy_eglGetProcAddress,
        NULL,
        NULL
    );

    // 3. Hook standard PLT
    stub_glTexImage2D = bytehook_hook_all(
        NULL,
        "glTexImage2D",
        (void*)proxy_glTexImage2D,
        NULL,
        NULL
    );
    
    g_installed = true;
    LOGI("[NativeGLEngine] GL interceptor initialisé (mode bhook C++)");
    return true;
}

void gl_interceptor_uninstall() {
    if (stub_eglGetProcAddress) {
        bytehook_unhook(stub_eglGetProcAddress);
        stub_eglGetProcAddress = nullptr;
    }
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
