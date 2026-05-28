#pragma once
#include <string>

bool shader_cache_save(const char* cache_dir, const char* hash, 
                       const void* data, size_t size);
bool shader_cache_load(const char* cache_dir, const char* hash,
                       void** out_data, size_t* out_size);
bool shader_cache_exists(const char* cache_dir, const char* hash);
