#pragma once
#include <GLES3/gl3.h>

namespace NativeGLEngine {

class DrawCallBatcher {
public:
    static void addInstance(
        GLuint shader, GLuint texture,
        GLuint vao, GLsizei indexCount,
        const float* modelMatrix44,
        const float* color4
    );

    static int flush();
};

} // namespace NativeGLEngine
