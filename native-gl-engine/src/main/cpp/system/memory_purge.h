/**
 * memory_purge.h — Interface purge mémoire native.
 */
#pragma once
#include <cstddef>

namespace androidopt {
    long purgeNativeMemory();
    int  releasePages(void* addr, size_t len);
    long getSystemAvailableMemoryMB();
}
