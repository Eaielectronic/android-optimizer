#include "simd_math_engine.h"
#include <cstring>
#include <cmath>

namespace NativeGLEngine {

void SIMDMathEngine::frustumCullSpheres(
    const Frustum& frustum,
    const BoundingSphere* spheres,
    size_t count,
    bool* results
) {
    size_t i = 0;

#ifdef ARM_NEON_ENABLED
    for (; i + 4 <= count; i += 4) {
        const BoundingSphere& s0 = spheres[i];
        const BoundingSphere& s1 = spheres[i+1];
        const BoundingSphere& s2 = spheres[i+2];
        const BoundingSphere& s3 = spheres[i+3];

        float32x4_t cx = { s0.cx, s1.cx, s2.cx, s3.cx };
        float32x4_t cy = { s0.cy, s1.cy, s2.cy, s3.cy };
        float32x4_t cz = { s0.cz, s1.cz, s2.cz, s3.cz };
        float32x4_t r  = { s0.radius, s1.radius, s2.radius, s3.radius };
        float32x4_t neg_r = vnegq_f32(r);

        uint32x4_t visible = vdupq_n_u32(0xFFFFFFFF);

        for (int p = 0; p < 6; p++) {
            const FrustumPlane& plane = frustum.planes[p];
            float32x4_t dist = vdupq_n_f32(plane.d);
            dist = vmlaq_n_f32(dist, cx, plane.nx);
            dist = vmlaq_n_f32(dist, cy, plane.ny);
            dist = vmlaq_n_f32(dist, cz, plane.nz);
            uint32x4_t inside = vcgeq_f32(dist, neg_r);
            visible = vandq_u32(visible, inside);
        }

        uint32_t mask[4];
        vst1q_u32(mask, visible);
        results[i]   = (mask[0] != 0);
        results[i+1] = (mask[1] != 0);
        results[i+2] = (mask[2] != 0);
        results[i+3] = (mask[3] != 0);
    }
#endif

    for (; i < count; i++) {
        const BoundingSphere& s = spheres[i];
        bool vis = true;
        for (int p = 0; p < 6 && vis; p++) {
            const FrustumPlane& plane = frustum.planes[p];
            float dist = plane.nx * s.cx + plane.ny * s.cy + plane.nz * s.cz + plane.d;
            vis = (dist >= -s.radius);
        }
        results[i] = vis;
    }
}

#ifdef ARM_NEON_ENABLED
void SIMDMathEngine::matMul4x4NEON(const float* a, const float* b, float* out) {
    float32x4_t b_col0 = vld1q_f32(b);
    float32x4_t b_col1 = vld1q_f32(b + 4);
    float32x4_t b_col2 = vld1q_f32(b + 8);
    float32x4_t b_col3 = vld1q_f32(b + 12);

    for (int row = 0; row < 4; row++) {
        float32x4_t a_row = vld1q_f32(a + row * 4);
        float32x4_t result = vmulq_laneq_f32(b_col0, a_row, 0);
        result = vmlaq_laneq_f32(result, b_col1, a_row, 1);
        result = vmlaq_laneq_f32(result, b_col2, a_row, 2);
        result = vmlaq_laneq_f32(result, b_col3, a_row, 3);
        vst1q_f32(out + row * 4, result);
    }
}
#endif

void SIMDMathEngine::extractFrustumFromMatrix(const float* vp, Frustum& f) {
    f.planes[0] = { vp[3]+vp[0], vp[7]+vp[4], vp[11]+vp[8],  vp[15]+vp[12] };
    f.planes[1] = { vp[3]-vp[0], vp[7]-vp[4], vp[11]-vp[8],  vp[15]-vp[12] };
    f.planes[2] = { vp[3]+vp[1], vp[7]+vp[5], vp[11]+vp[9],  vp[15]+vp[13] };
    f.planes[3] = { vp[3]-vp[1], vp[7]-vp[5], vp[11]-vp[9],  vp[15]-vp[13] };
    f.planes[4] = { vp[3]+vp[2], vp[7]+vp[6], vp[11]+vp[10], vp[15]+vp[14] };
    f.planes[5] = { vp[3]-vp[2], vp[7]-vp[6], vp[11]-vp[10], vp[15]-vp[14] };

    for (int i = 0; i < 6; i++) {
        float len = std::sqrt(f.planes[i].nx * f.planes[i].nx +
                               f.planes[i].ny * f.planes[i].ny +
                               f.planes[i].nz * f.planes[i].nz);
        if (len > 1e-6f) {
            f.planes[i].nx /= len; f.planes[i].ny /= len;
            f.planes[i].nz /= len; f.planes[i].d  /= len;
        }
    }
}

} // namespace NativeGLEngine
