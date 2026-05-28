#pragma once
#ifdef ARM_NEON_ENABLED
#include <arm_neon.h>
#endif
#include <cstdint>
#include <cstddef>

namespace NativeGLEngine {

struct FrustumPlane { float nx, ny, nz, d; };
struct Frustum { FrustumPlane planes[6]; };
struct BoundingSphere { float cx, cy, cz, radius; };

class SIMDMathEngine {
public:
    static void frustumCullSpheres(
        const Frustum& frustum,
        const BoundingSphere* spheres,
        size_t count,
        bool* results
    );
#ifdef ARM_NEON_ENABLED
    static void matMul4x4NEON(const float* a, const float* b, float* out);
    static void transformVec4ArrayNEON(
        const float* matrix,
        const float* vectors,
        float* out,
        size_t count
    );
#endif
    static void extractFrustumFromMatrix(const float* viewProj, Frustum& frustum);
};

} // namespace NativeGLEngine
