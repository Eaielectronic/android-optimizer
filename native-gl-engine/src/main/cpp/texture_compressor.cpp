#include "texture_compressor.h"
#include "gl_interceptor.h"
#include "soc_optimizer.h"
#include <GLES3/gl3.h>
#include <GLES3/gl31.h>
#include <android/log.h>
#include <cstring>
#include <vector>
#include <thread>
#include <atomic>
#include <future>
#include <mutex>

// ─── etcpak headers ───
#include "ProcessRGB.hpp"
#include "ProcessDxtc.hpp"

// ─── astcenc header ───
#include "astcenc.h"

#define LOG_TAG "NativeGL-TexCompress"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO,    LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR,   LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN,    LOG_TAG, __VA_ARGS__)

namespace NativeGLEngine {

bool TextureCompressor::s_hasASTCLDR = false;
bool TextureCompressor::s_hasETC2    = false;
static bool s_detected               = false;
static std::mutex s_initMutex;

inline size_t etc2RGBACompressedSize(GLsizei w, GLsizei h) {
    int bx = (w + 3) / 4;
    int by = (h + 3) / 4;
    return (size_t)bx * by * 16;
}

inline size_t astc6x6CompressedSize(GLsizei w, GLsizei h) {
    int bx = (w + 5) / 6;
    int by = (h + 5) / 6;
    return (size_t)bx * by * 16;
}

void TextureCompressor::detectCapabilities() {
    std::lock_guard<std::mutex> lock(s_initMutex);
    if (s_detected) return;

    const char* extensions = (const char*)glGetString(GL_EXTENSIONS);
    if (!extensions) {
        LOGE("glGetString(GL_EXTENSIONS) a retourné NULL !");
        return;
    }

    s_hasASTCLDR = (strstr(extensions, "GL_KHR_texture_compression_astc_ldr") != nullptr);
    s_hasETC2 = true;

    bool hasASTCHDR = (strstr(extensions, "GL_OES_texture_compression_astc") != nullptr);

    LOGI("TextureCompressor capabilities:");
    LOGI("  ASTC LDR = %s", s_hasASTCLDR ? "OUI ✓" : "NON");
    LOGI("  ASTC HDR = %s", hasASTCHDR   ? "OUI ✓" : "NON");
    LOGI("  ETC2     = OUI ✓ (garanti GLES 3.0)");

    s_detected = true;
}

bool TextureCompressor::shouldCompress(
    GLenum internalformat,
    GLsizei width, GLsizei height,
    const void* data
) {
    switch (internalformat) {
        case GL_COMPRESSED_RGBA8_ETC2_EAC:
        case GL_COMPRESSED_RGB8_ETC2:
        case GL_COMPRESSED_RGB8_PUNCHTHROUGH_ALPHA1_ETC2:
        case 0x93D0: // GL_COMPRESSED_RGBA_ASTC_4x4_KHR
        case 0x93D5: // GL_COMPRESSED_RGBA_ASTC_6x6_KHR
        case 0x93D9: // GL_COMPRESSED_RGBA_ASTC_8x8_KHR
            return false;
        default: break;
    }

    bool isRGBA = (internalformat == GL_RGBA8 || internalformat == GL_RGBA ||
                   internalformat == GL_RGBA4 || internalformat == GL_RGB5_A1);
    bool isRGB  = (internalformat == GL_RGB8  || internalformat == GL_RGB);
    if (!isRGBA && !isRGB) return false;

    if (width % 4 != 0 || height % 4 != 0) return false;
    if (width < 64 || height < 64) return false;
    if (!data) return false;

    return true;
}

bool TextureCompressor::compressETC2(
    const uint8_t* src_rgba,
    GLsizei width, GLsizei height,
    std::vector<uint8_t>& dst_etc2
) {
    if (!src_rgba || width <= 0 || height <= 0) return false;

    const size_t compressed_size = etc2RGBACompressedSize(width, height);
    dst_etc2.resize(compressed_size);

    CompressEtc2Rgba(
        (const uint32_t*)src_rgba,
        (uint64_t*)dst_etc2.data(),
        (uint32_t)width,
        (uint32_t)height,
        false
    );

    LOGI("ETC2 compression: %dx%d → %zu bytes (%.1f MB → %.1f MB)",
         width, height, compressed_size,
         (float)(width * height * 4) / 1048576.0f,
         (float)compressed_size / 1048576.0f);
    return true;
}

bool TextureCompressor::compressASTCIfAvailable(
    const uint8_t* src_rgba,
    GLsizei width, GLsizei height,
    std::vector<uint8_t>& dst_astc
) {
    if (!s_hasASTCLDR) return false;

    astcenc_config config;
    astcenc_error  status;

    status = astcenc_config_init(
        ASTCENC_PRF_LDR_SRGB,
        6, 6, 1,
        ASTCENC_PRE_FASTEST,
        0,
        &config
    );

    if (status != ASTCENC_SUCCESS) {
        LOGE("astcenc_config_init failed: %d", status);
        return false;
    }

    astcenc_context* ctx = nullptr;
    status = astcenc_context_alloc(&config, 1, &ctx, nullptr);
    if (status != ASTCENC_SUCCESS) {
        LOGE("astcenc_context_alloc failed: %d", status);
        return false;
    }

    astcenc_image image;
    image.dim_x     = width;
    image.dim_y     = height;
    image.dim_z     = 1;
    image.data_type = ASTCENC_TYPE_U8;
    void* data_ptr  = (void*)src_rgba;
    image.data      = &data_ptr;

    const size_t out_size = astc6x6CompressedSize(width, height);
    dst_astc.resize(out_size);

    const astcenc_swizzle swizzle = {
        ASTCENC_SWZ_R, ASTCENC_SWZ_G, ASTCENC_SWZ_B, ASTCENC_SWZ_A
    };

    status = astcenc_compress_image(ctx, &image, &swizzle,
                                     dst_astc.data(), out_size,
                                     0);

    astcenc_context_free(ctx);

    if (status != ASTCENC_SUCCESS) {
        LOGE("astcenc_compress_image failed: %d", status);
        return false;
    }

    LOGI("ASTC 6×6 compression: %dx%d → %zu bytes (%.1f MB → %.1f MB)",
         width, height, out_size,
         (float)(width * height * 4) / 1048576.0f,
         (float)out_size / 1048576.0f);
    return true;
}

bool TextureCompressor::intercept(
    GLenum target, GLint level,
    GLint internalformat,
    GLsizei width, GLsizei height,
    GLenum format, GLenum type,
    const void* pixels
) {
    if (!shouldCompress((GLenum)internalformat, width, height, pixels)) {
        return false;
    }

    const size_t src_size = width * height * 4;
    std::vector<uint8_t> src_copy((const uint8_t*)pixels,
                                    (const uint8_t*)pixels + src_size);

    std::vector<uint8_t> compressed;
    GLenum compressedFormat;

    bool ok = false;
    if (s_hasASTCLDR) {
        ok = compressASTCIfAvailable(src_copy.data(), width, height, compressed);
        if (ok) compressedFormat = 0x93D5; // GL_COMPRESSED_RGBA_ASTC_6x6_KHR
    }
    if (!ok) {
        ok = compressETC2(src_copy.data(), width, height, compressed);
        if (ok) compressedFormat = GL_COMPRESSED_RGBA8_ETC2_EAC;
    }

    if (!ok) return false;

    glCompressedTexImage2D(
        target, level,
        compressedFormat,
        width, height,
        0,
        (GLsizei)compressed.size(),
        compressed.data()
    );

    GLenum err = glGetError();
    if (err != GL_NO_ERROR) {
        LOGE("glCompressedTexImage2D error: 0x%X (format=0x%X %dx%d)",
             err, compressedFormat, width, height);
        return false;
    }

    return true;
}

std::future<CompressedResult> TextureCompressor::compressAsync(
    const uint8_t* src_rgba,
    GLsizei width, GLsizei height,
    GLenum preferredFormat
) {
    std::vector<uint8_t> src_copy(src_rgba, src_rgba + width * height * 4);

    return std::async(std::launch::async, [=, src = std::move(src_copy)]() mutable {
        CompressedResult result;
        result.width  = width;
        result.height = height;

        bool ok = false;
        if (preferredFormat == 0x93D5 && s_hasASTCLDR) {
            ok = compressASTCIfAvailable(src.data(), width, height, result.data);
            if (ok) result.format = 0x93D5;
        }
        if (!ok) {
            ok = compressETC2(src.data(), width, height, result.data);
            if (ok) result.format = GL_COMPRESSED_RGBA8_ETC2_EAC;
        }

        result.success = ok;
        return result;
    });
}

} // namespace NativeGLEngine
