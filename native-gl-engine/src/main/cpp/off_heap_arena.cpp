#include "off_heap_arena.h"
#include <android/log.h>
#include <sys/mman.h>
#include <cstring>
#include <chrono>
#include <malloc.h>

#define LOG_TAG "NativeGL-OffHeap"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO,  LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN,  LOG_TAG, __VA_ARGS__)

namespace NativeGLEngine {

uint8_t*             OffHeapArena::s_poolBase = nullptr;
size_t               OffHeapArena::s_poolSize = 0;
std::atomic<size_t>  OffHeapArena::s_offset   {0};
std::unordered_map<void*, ArenaBlock> OffHeapArena::s_blocks;
std::mutex           OffHeapArena::s_mutex;

bool OffHeapArena::init(size_t poolSizeMB) {
    s_poolSize = poolSizeMB * 1024 * 1024;

    s_poolBase = (uint8_t*)mmap(
        nullptr,
        s_poolSize,
        PROT_READ | PROT_WRITE,
        MAP_PRIVATE | MAP_ANONYMOUS,
        -1, 0
    );

    if (s_poolBase == MAP_FAILED) {
        LOGE("mmap(%zu MB) failed: %s", poolSizeMB, strerror(errno));
        s_poolBase = nullptr;
        return false;
    }

    madvise(s_poolBase, s_poolSize, MADV_SEQUENTIAL);
    madvise(s_poolBase, s_poolSize, MADV_WILLNEED);

    s_offset.store(0);
    LOGI("OffHeapArena initialisé : %zu MB @ %p", poolSizeMB, (void*)s_poolBase);
    return true;
}

void OffHeapArena::destroy() {
    std::lock_guard<std::mutex> lock(s_mutex);
    if (s_poolBase) {
        munmap(s_poolBase, s_poolSize);
        s_poolBase = nullptr;
        LOGI("OffHeapArena libéré (%zu MB)", s_poolSize / 1048576);
    }
    s_blocks.clear();
}

jobject OffHeapArena::allocate(JNIEnv* env, size_t sizeBytes, const char* tag) {
    const size_t alignment = 64;
    sizeBytes = (sizeBytes + alignment - 1) & ~(alignment - 1);

    void* ptr = nullptr;

    if (s_poolBase) {
        size_t offset = s_offset.fetch_add(sizeBytes);
        if (offset + sizeBytes <= s_poolSize) {
            ptr = s_poolBase + offset;
        }
    }

    if (!ptr) {
        ptr = memalign(alignment, sizeBytes);
        if (!ptr) {
            LOGE("OffHeap allocation %zu bytes failed !", sizeBytes);
            return nullptr;
        }
        LOGW("Pool plein ! Fallback malloc pour %s (%zu bytes)", tag, sizeBytes);
    }

    jobject directBuf = env->NewDirectByteBuffer(ptr, (jlong)sizeBytes);
    if (!directBuf) {
        LOGE("NewDirectByteBuffer(%p, %zu) failed", ptr, sizeBytes);
        if (ptr < (void*)s_poolBase || ptr >= (void*)(s_poolBase + s_poolSize)) {
            ::free(ptr);
        }
        return nullptr;
    }

    {
        std::lock_guard<std::mutex> lock(s_mutex);
        ArenaBlock block;
        block.ptr       = ptr;
        block.size      = sizeBytes;
        block.allocTime = std::chrono::duration_cast<std::chrono::milliseconds>(
            std::chrono::steady_clock::now().time_since_epoch()
        ).count();
        block.inUse     = true;
        strncpy(block.tag, tag, sizeof(block.tag) - 1);
        s_blocks[ptr] = block;
    }

    LOGI("OffHeap alloc: %s → %zu bytes @ %p", tag, sizeBytes, ptr);
    return directBuf;
}

void OffHeapArena::free(JNIEnv* env, jobject directBuffer) {
    if (!directBuffer) return;
    void* ptr = env->GetDirectBufferAddress(directBuffer);
    if (!ptr) { LOGE("GetDirectBufferAddress failed !"); return; }

    std::lock_guard<std::mutex> lock(s_mutex);
    auto it = s_blocks.find(ptr);
    if (it != s_blocks.end()) {
        it->second.inUse = false;
        LOGI("OffHeap free: %s @ %p (%zu bytes)", it->second.tag, ptr, it->second.size);
        if (ptr < (void*)s_poolBase || ptr >= (void*)(s_poolBase + s_poolSize)) {
            ::free(ptr);
            s_blocks.erase(it);
        }
    }
}

void OffHeapArena::runLeakDetector() {
    std::lock_guard<std::mutex> lock(s_mutex);
    int64_t now = std::chrono::duration_cast<std::chrono::milliseconds>(
        std::chrono::steady_clock::now().time_since_epoch()
    ).count();
    for (auto& pair : s_blocks) {
        auto& block = pair.second;
        if (block.inUse && (now - block.allocTime) > 60000) {
            LOGW("POTENTIAL LEAK: %s @ %p (%zu bytes) alive for %lld ms",
                 block.tag, pair.first, block.size, (long long)(now - block.allocTime));
        }
    }
}

void* OffHeapArena::getNativePtr(JNIEnv* env, jobject directBuffer) {
    return env->GetDirectBufferAddress(directBuffer);
}

size_t OffHeapArena::getTotalAllocated() { return s_offset.load(); }
size_t OffHeapArena::getTotalFree() { return s_poolSize > s_offset.load() ? s_poolSize - s_offset.load() : 0; }
int OffHeapArena::getActiveBlocks() {
    std::lock_guard<std::mutex> lock(s_mutex);
    int count = 0;
    for (const auto& pair : s_blocks) { if(pair.second.inUse) count++; }
    return count;
}

} // namespace NativeGLEngine
