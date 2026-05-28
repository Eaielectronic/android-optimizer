/**
 * texture_manager.cpp — MPSC async texture compression queue.
 *
 * Architecture :
 * 1. texture_manager_submit() copie les pixels et lance un worker thread
 * 2. Le worker compresse via TextureCompressor::compressETC2 (etcpak, ~1-5ms pour 256x256)
 * 3. Le résultat est pushé dans g_readyQueue (mutex-protégé)
 * 4. texture_manager_drain_compressed() est appelé sur le GL thread chaque tick
 *    et appelle glCompressedTexImage2D pour chaque résultat prêt
 *
 * Contraintes :
 * - Les pixels doivent être copiés immédiatement dans submit() car le buffer Java
 *   sera réutilisé par Minecraft
 * - glCompressedTexImage2D DOIT être appelé sur le GL thread
 * - Budget temps de drain : 2ms max par tick pour éviter les stutters
 */

#include "texture_manager.h"
#include "texture_compressor.h"
#include <android/log.h>
#include <mutex>
#include <deque>
#include <thread>
#include <atomic>
#include <chrono>
#include <cstring>

#define LOG_TAG "NativeGL-TexManager"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO,  LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN,  LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

extern bool g_verbose_logging;
#define LOGV(...) if (g_verbose_logging) { __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__); }

// ═══ Queue de résultats compressés (GL thread consomme) ═══
static std::mutex g_readyMutex;
static std::deque<CompressedUpload> g_readyQueue;

// ═══ Stats ═══
static std::atomic<int> g_pendingCount{0};
static std::atomic<long> g_totalSubmitted{0};
static std::atomic<long> g_totalUploaded{0};
static std::atomic<long> g_totalFailed{0};

// ═══ Queue size limit — prevent memory explosion ═══
static const int MAX_PENDING = 32;


bool texture_manager_submit(
    GLenum target, GLint level, GLint internalFormat,
    GLsizei width, GLsizei height, GLint border,
    GLenum format, GLenum type,
    const void* pixels, size_t pixelsSizeBytes
) {
    // Guard : queue pas trop pleine
    if (g_pendingCount.load() >= MAX_PENDING) {
        LOGV("texture_manager_submit: queue full (%d pending), rejecting %dx%d",
             g_pendingCount.load(), width, height);
        return false;
    }

    // Guard : pixels valides
    if (!pixels || pixelsSizeBytes == 0) return false;

    // Copier les pixels MAINTENANT (le buffer Java sera réutilisé)
    std::vector<uint8_t> pixelCopy(pixelsSizeBytes);
    memcpy(pixelCopy.data(), pixels, pixelsSizeBytes);

    TextureUploadRequest req;
    req.target         = target;
    req.level          = level;
    req.internalFormat = internalFormat;
    req.width          = width;
    req.height         = height;
    req.border         = border;
    req.format         = format;
    req.type           = type;

    g_pendingCount++;
    g_totalSubmitted++;

    LOGV("texture_manager_submit: %dx%d (%zu bytes) → worker thread [pending=%d]",
         width, height, pixelsSizeBytes, g_pendingCount.load());

    // Lancer un thread detaché pour la compression
    // (Le thread pool serait mieux mais pour la V1, detach est suffisant)
    std::thread([req, pixelData = std::move(pixelCopy)]() mutable {

        CompressedUpload result;
        result.request = req;
        result.ready = false;

        // Appeler TextureCompressor::compressETC2
        std::vector<uint8_t> compressed;
        bool ok = NativeGLEngine::TextureCompressor::compressETC2(
            pixelData.data(), req.width, req.height, compressed
        );

        if (ok) {
            result.compressedFormat = GL_COMPRESSED_RGBA8_ETC2_EAC;
            result.compressedData   = std::move(compressed);
            result.ready            = true;

            // Push dans la queue (mutex)
            {
                std::lock_guard<std::mutex> lock(g_readyMutex);
                g_readyQueue.push_back(std::move(result));
            }

            LOGV("texture_manager: compressed %dx%d → %zu bytes ETC2 [queue=%zu]",
                 req.width, req.height, result.compressedData.size(),
                 g_readyQueue.size());
        } else {
            LOGW("texture_manager: compression failed for %dx%d", req.width, req.height);
            g_totalFailed++;
            // On ne met PAS dans la queue — la texture restera sans compression
            // MC a déjà été cancel() côté Mixin, donc la texture sera noire
            // TODO: fallback — uploader les pixels bruts si la compression échoue
        }

        g_pendingCount--;
    }).detach();

    return true;
}


int texture_manager_drain_compressed(int max_uploads, int max_time_us) {
    std::lock_guard<std::mutex> lock(g_readyMutex);

    if (g_readyQueue.empty()) return 0;

    auto start = std::chrono::steady_clock::now();
    int uploaded = 0;

    while (!g_readyQueue.empty() && uploaded < max_uploads) {
        // Vérifier le budget temps
        auto elapsed = std::chrono::steady_clock::now() - start;
        int elapsed_us = (int)std::chrono::duration_cast<std::chrono::microseconds>(elapsed).count();
        if (elapsed_us > max_time_us) break;

        CompressedUpload& item = g_readyQueue.front();
        if (!item.ready) {
            // Pas encore prêt — skip (ne devrait pas arriver vu qu'on push seulement quand ready)
            break;
        }

        // Upload sur le GL thread via glCompressedTexImage2D
        glCompressedTexImage2D(
            item.request.target,
            item.request.level,
            item.compressedFormat,
            item.request.width,
            item.request.height,
            item.request.border,
            (GLsizei)item.compressedData.size(),
            item.compressedData.data()
        );

        GLenum err = glGetError();
        if (err != GL_NO_ERROR) {
            LOGE("glCompressedTexImage2D error: 0x%X (format=0x%X %dx%d)",
                 err, item.compressedFormat, item.request.width, item.request.height);
        } else {
            LOGV("texture_manager: uploaded %dx%d ETC2 (%zu bytes)",
                 item.request.width, item.request.height, item.compressedData.size());
            g_totalUploaded++;
        }

        g_readyQueue.pop_front();
        uploaded++;
    }

    return uploaded;
}


int texture_manager_get_pending_count() {
    return g_pendingCount.load() + (int)g_readyQueue.size();
}


// Legacy drain — keep for backward compat
void texture_manager_drain(int max_uploads) {
    // Old pipeline — now handled by drain_compressed
    texture_manager_drain_compressed(max_uploads, 2000);
}
