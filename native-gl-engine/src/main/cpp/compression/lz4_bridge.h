/**
 * lz4_bridge.h — Interface compression LZ4 pour le JNI bridge.
 */
#pragma once
#include <cstdint>

namespace androidopt {
    int compressHC(void* src, int srcLen, void* dst, int dstCapacity, int level);
    int compressFast(void* src, int srcLen, void* dst, int dstCapacity, int acceleration);
    int decompress(const void* src, int srcLen, void* dst, int maxDecompressedSize);
    int compressBound(int srcLen);
    void getStats(uint64_t* outCompressed, uint64_t* outDecompressed,
                  uint64_t* outCompressedTotal, uint32_t* outCount);
}
