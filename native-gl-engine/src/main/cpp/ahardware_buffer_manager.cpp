#include "ahardware_buffer_manager.h"
#include <android/log.h>
#include <cstring>

#define LOG_TAG "NativeGL-AHB"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO,  LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace NativeGLEngine {

typedef EGLClientBuffer (EGLAPIENTRYP PFNEGLGETNATIVECLIENTBUFFERANDROIDPROC)(const AHardwareBuffer*);
typedef EGLImageKHR (EGLAPIENTRYP PFNEGLCREATEIMAGEKHRPROC)(EGLDisplay, EGLContext, EGLenum, EGLClientBuffer, const EGLint*);
typedef EGLBoolean (EGLAPIENTRYP PFNEGLDESTROYIMAGEKHRPROC)(EGLDisplay, EGLImageKHR);
typedef void (GL_APIENTRYP PFNGLEGLIMAGETARGETTEXTURE2DOESPROC)(GLenum, GLeglImageOES);

static PFNEGLGETNATIVECLIENTBUFFERANDROIDPROC s_eglGetNativeClientBufferANDROID = nullptr;
static PFNEGLCREATEIMAGEKHRPROC               s_eglCreateImageKHR = nullptr;
static PFNEGLDESTROYIMAGEKHRPROC              s_eglDestroyImageKHR = nullptr;
static PFNGLEGLIMAGETARGETTEXTURE2DOESPROC    s_glEGLImageTargetTexture2DOES = nullptr;
static bool s_eglExtLoaded = false;

static void loadEGLExtensions() {
    if (s_eglExtLoaded) return;
    s_eglGetNativeClientBufferANDROID =
        (PFNEGLGETNATIVECLIENTBUFFERANDROIDPROC)eglGetProcAddress("eglGetNativeClientBufferANDROID");
    s_eglCreateImageKHR =
        (PFNEGLCREATEIMAGEKHRPROC)eglGetProcAddress("eglCreateImageKHR");
    s_eglDestroyImageKHR =
        (PFNEGLDESTROYIMAGEKHRPROC)eglGetProcAddress("eglDestroyImageKHR");
    s_glEGLImageTargetTexture2DOES =
        (PFNGLEGLIMAGETARGETTEXTURE2DOESPROC)eglGetProcAddress("glEGLImageTargetTexture2DOES");
    s_eglExtLoaded = true;
    LOGI("EGL extensions: eglGetNativeClientBufferANDROID=%s eglCreateImageKHR=%s glEGLImageTargetTexture2DOES=%s",
         s_eglGetNativeClientBufferANDROID ? "OK" : "MANQUANT",
         s_eglCreateImageKHR ? "OK" : "MANQUANT",
         s_glEGLImageTargetTexture2DOES ? "OK" : "MANQUANT");
}

bool AHardwareBufferManager::isSupported() {
    loadEGLExtensions();
    return (s_eglGetNativeClientBufferANDROID != nullptr &&
            s_eglCreateImageKHR != nullptr &&
            s_glEGLImageTargetTexture2DOES != nullptr);
}

HWTexture* AHardwareBufferManager::createTexture(int width, int height, uint32_t format) {
    loadEGLExtensions();
    if (!isSupported()) { LOGE("AHardwareBuffer non supporté !"); return nullptr; }

    HWTexture* tex = new HWTexture();
    tex->width = width; tex->height = height; tex->format = format;

    AHardwareBuffer_Desc desc = {};
    desc.width  = (uint32_t)width;
    desc.height = (uint32_t)height;
    desc.layers = 1;
    desc.format = format;
    desc.usage  = AHARDWAREBUFFER_USAGE_CPU_WRITE_OFTEN |
                  AHARDWAREBUFFER_USAGE_GPU_SAMPLED_IMAGE;
    desc.stride = 0;

    if (AHardwareBuffer_allocate(&desc, &tex->hwBuffer) != 0) {
        LOGE("AHardwareBuffer_allocate failed"); delete tex; return nullptr;
    }

    EGLClientBuffer clientBuffer = s_eglGetNativeClientBufferANDROID(tex->hwBuffer);
    if (!clientBuffer) {
        LOGE("eglGetNativeClientBufferANDROID failed !");
        AHardwareBuffer_release(tex->hwBuffer); delete tex; return nullptr;
    }

    EGLDisplay display = eglGetCurrentDisplay();
    const EGLint attribs[] = { EGL_NONE };
    tex->eglImage = s_eglCreateImageKHR(
        display, EGL_NO_CONTEXT, EGL_NATIVE_BUFFER_ANDROID, clientBuffer, attribs
    );
    if (tex->eglImage == EGL_NO_IMAGE_KHR) {
        LOGE("eglCreateImageKHR failed: 0x%X", eglGetError());
        AHardwareBuffer_release(tex->hwBuffer); delete tex; return nullptr;
    }

    glGenTextures(1, &tex->glTexId);
    glBindTexture(GL_TEXTURE_2D, tex->glTexId);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);

    s_glEGLImageTargetTexture2DOES(GL_TEXTURE_2D, (GLeglImageOES)tex->eglImage);

    LOGI("AHardwareBuffer texture créée: %dx%d → GL id %u (zero-copy)", width, height, tex->glTexId);
    return tex;
}

void* AHardwareBufferManager::lockBuffer(AHardwareBuffer* hwBuffer) {
    void* data = nullptr;
    int result = AHardwareBuffer_lock(
        hwBuffer, AHARDWAREBUFFER_USAGE_CPU_WRITE_OFTEN,
        -1, nullptr, &data
    );
    if (result != 0) { LOGE("AHardwareBuffer_lock failed: %d", result); return nullptr; }
    return data;
}

void AHardwareBufferManager::unlockBuffer(AHardwareBuffer* hwBuffer) {
    AHardwareBuffer_unlock(hwBuffer, nullptr);
}

void AHardwareBufferManager::destroyTexture(HWTexture* tex) {
    if (!tex) return;
    if (tex->glTexId) glDeleteTextures(1, &tex->glTexId);
    if (tex->eglImage != EGL_NO_IMAGE_KHR) s_eglDestroyImageKHR(eglGetCurrentDisplay(), tex->eglImage);
    if (tex->hwBuffer) AHardwareBuffer_release(tex->hwBuffer);
    delete tex;
}

} // namespace NativeGLEngine
