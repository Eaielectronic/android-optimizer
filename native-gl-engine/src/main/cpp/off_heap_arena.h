#pragma once
#include <jni.h>
#include <cstdint>
#include <cstddef>
#include <mutex>
#include <unordered_map>
#include <atomic>

namespace NativeGLEngine {

struct ArenaBlock {
    void*    ptr;
    size_t   size;
    int64_t  allocTime;
    bool     inUse;
    char     tag[32];
};

class OffHeapArena {
public:
    static bool init(size_t poolSizeMB = 150);
    static void destroy();
    static jobject allocate(JNIEnv* env, size_t sizeBytes, const char* tag = "unnamed");
    static void free(JNIEnv* env, jobject directBuffer);
    static void* getNativePtr(JNIEnv* env, jobject directBuffer);
    static void runLeakDetector();
    static size_t getTotalAllocated();
    static size_t getTotalFree();
    static int    getActiveBlocks();

private:
    static uint8_t*  s_poolBase;
    static size_t    s_poolSize;
    static std::atomic<size_t> s_offset;
    static std::unordered_map<void*, ArenaBlock> s_blocks;
    static std::mutex s_mutex;
};

} // namespace NativeGLEngine
