#pragma once
#include <GLES3/gl3.h>
#include <vector>
#include <future>
#include <functional>

namespace NativeGLEngine {

struct CompressedResult {
    GLenum format;              // GL_COMPRESSED_RGBA8_ETC2_EAC ou GL_COMPRESSED_RGBA_ASTC_6x6_KHR
    std::vector<uint8_t> data;  // données compressées
    GLsizei width, height;
    bool success;
};

class TextureCompressor {
public:
    // Appelé une fois au démarrage (depuis GL thread)
    static void detectCapabilities();

    // Décide si on doit compresser (filtrage rapide)
    static bool shouldCompress(GLenum internalformat,
                                GLsizei width, GLsizei height,
                                const void* data);

    // Compression synchrone (appelée depuis thread de compression)
    static bool compressETC2(const uint8_t* src_rgba,
                              GLsizei width, GLsizei height,
                              std::vector<uint8_t>& dst_etc2);

    static bool compressASTCIfAvailable(const uint8_t* src_rgba,
                                         GLsizei width, GLsizei height,
                                         std::vector<uint8_t>& dst_astc);

    // Interception principale — remplace glTexImage2D
    // Retourne true si la texture a été compressée et uploadée
    static bool intercept(GLenum target, GLint level,
                           GLint internalformat,
                           GLsizei width, GLsizei height,
                           GLenum format, GLenum type,
                           const void* pixels);

    // Async : compression en arrière-plan, upload via fence GL
    static std::future<CompressedResult> compressAsync(
        const uint8_t* src_rgba,
        GLsizei width, GLsizei height,
        GLenum preferredFormat);

    static bool s_hasASTCLDR;
    static bool s_hasETC2;
};

} // namespace NativeGLEngine
