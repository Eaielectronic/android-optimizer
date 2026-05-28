#include "shader_cache.h"
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <android/log.h>

#define LOG_TAG "NativeGLEngine"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)

bool shader_cache_save(const char* cache_dir, const char* hash,
                       const void* data, size_t size) {
    char path[512];
    snprintf(path, sizeof(path), "%s/%s.spv", cache_dir, hash);
    
    FILE* f = fopen(path, "wb");
    if (!f) return false;
    
    size_t written = fwrite(data, 1, size, f);
    fclose(f);
    return written == size;
}

bool shader_cache_load(const char* cache_dir, const char* hash,
                       void** out_data, size_t* out_size) {
    char path[512];
    snprintf(path, sizeof(path), "%s/%s.spv", cache_dir, hash);
    
    FILE* f = fopen(path, "rb");
    if (!f) return false;
    
    fseek(f, 0, SEEK_END);
    long file_size = ftell(f);
    fseek(f, 0, SEEK_SET);
    
    if (file_size <= 0) { fclose(f); return false; }
    
    void* buffer = malloc(file_size);
    if (!buffer) { fclose(f); return false; }
    
    size_t read = fread(buffer, 1, file_size, f);
    fclose(f);
    
    if (read != (size_t)file_size) { free(buffer); return false; }
    
    *out_data = buffer;
    *out_size = file_size;
    return true;
}

bool shader_cache_exists(const char* cache_dir, const char* hash) {
    char path[512];
    snprintf(path, sizeof(path), "%s/%s.spv", cache_dir, hash);
    FILE* f = fopen(path, "rb");
    if (f) { fclose(f); return true; }
    return false;
}
