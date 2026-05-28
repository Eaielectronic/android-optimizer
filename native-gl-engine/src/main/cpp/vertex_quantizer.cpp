#include "vertex_quantizer.h"
#include <android/log.h>
#include <cmath>
#include <algorithm>
#include "fp16.h"

#define LOG_TAG "NativeGL-VertexQ"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)

extern bool g_verbose_logging;
#define LOGV(...) if (g_verbose_logging) { __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__); }

namespace NativeGLEngine {

uint16_t VertexQuantizer::f32_to_f16(float f) {
    return fp16_ieee_from_fp32_value(f);
}

uint16_t VertexQuantizer::f32_to_unorm16(float f) {
    float clamped = std::max(0.0f, std::min(1.0f, f));
    return (uint16_t)(clamped * 65535.0f + 0.5f);
}

#ifdef ARM_NEON_ENABLED
void VertexQuantizer::quantizeNEON(
    const float* src_positions,
    uint16_t*    dst_fp16,
    size_t       float_count
) {
    size_t i = 0;

    for (; i + 8 <= float_count; i += 8) {
        float32x4_t f32_0 = vld1q_f32(src_positions + i);
        float32x4_t f32_1 = vld1q_f32(src_positions + i + 4);
        float16x4_t f16_0 = vcvt_f16_f32(f32_0);
        float16x4_t f16_1 = vcvt_f16_f32(f32_1);
        vst1_f16((__fp16*)(dst_fp16 + i),     f16_0);
        vst1_f16((__fp16*)(dst_fp16 + i + 4), f16_1);
    }

    for (; i + 4 <= float_count; i += 4) {
        float32x4_t f32 = vld1q_f32(src_positions + i);
        float16x4_t f16 = vcvt_f16_f32(f32);
        vst1_f16((__fp16*)(dst_fp16 + i), f16);
    }

    for (; i < float_count; i++) {
        dst_fp16[i] = f32_to_f16(src_positions[i]);
    }
}
#endif

void VertexQuantizer::quantize(
    const VertexFP32* src,
    VertexQuantized*  dst,
    size_t            count
) {
    if (!src || !dst || count == 0) return;

    for (size_t i = 0; i < count; ++i) {
        const VertexFP32&  s = src[i];
        VertexQuantized&   d = dst[i];

        d.x = fp16_ieee_from_fp32_value(s.x);
        d.y = fp16_ieee_from_fp32_value(s.y);
        d.z = fp16_ieee_from_fp32_value(s.z);

        d.u = f32_to_unorm16(s.u);
        d.v = f32_to_unorm16(s.v);

        d.nx = (int8_t)(s.nx * 127.0f);
        d.ny = (int8_t)(s.ny * 127.0f);
        d.nz = (int8_t)(s.nz * 127.0f);
        d._pad = 0;

        d.r = s.r; d.g = s.g; d.b = s.b; d.a = s.a;
    }

    LOGV("VertexQuantizer: %zu vertices quantifiés (%zu bytes → %zu bytes, ÷%.1f)",
         count,
         count * sizeof(VertexFP32),
         count * sizeof(VertexQuantized),
         (float)sizeof(VertexFP32) / sizeof(VertexQuantized));
}

bool VertexQuantizer::checkFP16Safe(const VertexFP32* vertices, size_t count) {
    const float FP16_MAX = 65504.0f;
    for (size_t i = 0; i < count; i++) {
        if (std::abs(vertices[i].x) > FP16_MAX ||
            std::abs(vertices[i].y) > FP16_MAX ||
            std::abs(vertices[i].z) > FP16_MAX) {
            return false;
        }
    }
    return true;
}

} // namespace NativeGLEngine
