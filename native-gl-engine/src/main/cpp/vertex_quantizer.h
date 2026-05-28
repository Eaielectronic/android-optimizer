#pragma once
#include <cstdint>
#include <cstddef>
#ifdef ARM_NEON_ENABLED
#include <arm_neon.h>
#endif

namespace NativeGLEngine {

struct VertexFP32 {
    float x, y, z;
    float u, v;
    float nx, ny, nz;
    uint8_t r, g, b, a;
};

struct VertexQuantized {
    uint16_t x, y, z;   // Position FP16
    uint16_t u, v;       // UV UNORM16
    int8_t   nx, ny, nz; // Normale INT8
    uint8_t  _pad;
    uint8_t  r, g, b, a;
    // Total : 16 bytes (vs 36 avant)
};

class VertexQuantizer {
public:
    static void quantize(
        const VertexFP32* src,
        VertexQuantized*  dst,
        size_t            count
    );
#ifdef ARM_NEON_ENABLED
    static void quantizeNEON(
        const float* src_positions,
        uint16_t*    dst_fp16,
        size_t       count
    );
#endif
    static uint16_t f32_to_f16(float f);
    static uint16_t f32_to_unorm16(float f);
    static bool checkFP16Safe(const VertexFP32* vertices, size_t count);
};

} // namespace NativeGLEngine
