#pragma once
#include <android/hardware_buffer.h>
#include <EGL/egl.h>
#include <EGL/eglext.h>
#include <GLES3/gl3.h>
#include <GLES2/gl2ext.h>

namespace NativeGLEngine {

struct HWTexture {
    AHardwareBuffer* hwBuffer;
    EGLImageKHR      eglImage;
    GLuint           glTexId;
    int              width, height;
    uint32_t         format;
};

class AHardwareBufferManager {
public:
    static HWTexture* createTexture(int width, int height,
                                     uint32_t format = AHARDWAREBUFFER_FORMAT_R8G8B8A8_UNORM);
    static void* lockBuffer(AHardwareBuffer* hwBuffer);
    static void  unlockBuffer(AHardwareBuffer* hwBuffer);
    static void  destroyTexture(HWTexture* tex);
    static bool  isSupported();
};

} // namespace NativeGLEngine
