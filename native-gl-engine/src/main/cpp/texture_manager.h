#pragma once

#include <GLES3/gl3.h>
#include <vector>
#include <cstdint>

/**
 * Async texture compression queue (MPSC).
 *
 * Flow:
 * 1. texture_manager_submit() — called from any thread
 *    Copies raw pixels, submits compression to thread pool
 * 2. Compression worker thread compresses ETC2 via etcpak
 * 3. Result is pushed into a lock-free queue
 * 4. texture_manager_drain_compressed() — called on GL thread
 *    Uploads compressed textures via glCompressedTexImage2D
 */

struct TextureUploadRequest {
    GLenum  target;
    GLint   level;
    GLint   internalFormat;
    GLsizei width;
    GLsizei height;
    GLint   border;
    GLenum  format;
    GLenum  type;
};

struct CompressedUpload {
    TextureUploadRequest    request;
    GLenum                  compressedFormat;   // GL_COMPRESSED_RGBA8_ETC2_EAC
    std::vector<uint8_t>    compressedData;
    bool                    ready;
};

// Submit raw pixels for async compression.
// Copies the pixel data immediately (caller can reuse the buffer).
// Returns true if accepted.
bool texture_manager_submit(
    GLenum target, GLint level, GLint internalFormat,
    GLsizei width, GLsizei height, GLint border,
    GLenum format, GLenum type,
    const void* pixels, size_t pixelsSizeBytes
);

// Drain completed compressed textures onto the GL thread.
// Calls glCompressedTexImage2D for each ready result.
// Returns number of textures uploaded.
int texture_manager_drain_compressed(int max_uploads, int max_time_us);

// Get number of pending results (submitted but not yet uploaded)
int texture_manager_get_pending_count();

// Legacy drain (old stub)
void texture_manager_drain(int max_uploads);
