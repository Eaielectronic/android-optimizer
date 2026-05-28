#include "draw_call_batcher.h"
#include <GLES3/gl3.h>
#include <android/log.h>
#include <unordered_map>
#include <vector>
#include <cstring>

#define LOG_TAG "NativeGL-Batcher"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)

namespace NativeGLEngine {

struct InstanceData {
    float modelMatrix[16];
    float color[4];
};

struct Batch {
    GLuint vao, vbo, ibo, ubo, shaderProgram, texture;
    GLsizei indexCount;
    std::vector<InstanceData> instances;
    GLuint instanceUBO;
};

static std::unordered_map<uint64_t, Batch> s_batches;

static uint64_t batchKey(GLuint shader, GLuint tex) {
    return ((uint64_t)shader << 32) | tex;
}

void DrawCallBatcher::addInstance(
    GLuint shader, GLuint texture,
    GLuint vao, GLsizei indexCount,
    const float* modelMatrix44,
    const float* color4
) {
    uint64_t key = batchKey(shader, texture);
    Batch& batch = s_batches[key];

    if (batch.vao == 0) {
        batch.vao         = vao;
        batch.indexCount  = indexCount;
        batch.shaderProgram = shader;
        batch.texture     = texture;
    }

    InstanceData inst;
    memcpy(inst.modelMatrix, modelMatrix44, 64);
    if (color4) memcpy(inst.color, color4, 16);
    else { inst.color[0]=inst.color[1]=inst.color[2]=inst.color[3]=1.0f; }

    batch.instances.push_back(inst);
}

int DrawCallBatcher::flush() {
    int drawCallsSaved = 0;
    int drawCallsEmitted = 0;

    for (auto& pair : s_batches) {
        Batch& batch = pair.second;
        if (batch.instances.empty()) continue;
        int instanceCount = (int)batch.instances.size();

        if (batch.instanceUBO == 0) glGenBuffers(1, &batch.instanceUBO);
        glBindBuffer(GL_UNIFORM_BUFFER, batch.instanceUBO);

        size_t uboSize = instanceCount * sizeof(InstanceData);
        glBufferData(GL_UNIFORM_BUFFER, uboSize, nullptr, GL_DYNAMIC_DRAW);

        void* ptr = glMapBufferRange(
            GL_UNIFORM_BUFFER, 0, uboSize,
            GL_MAP_WRITE_BIT | GL_MAP_INVALIDATE_BUFFER_BIT
        );
        if (ptr) {
            memcpy(ptr, batch.instances.data(), uboSize);
            glUnmapBuffer(GL_UNIFORM_BUFFER);
        }

        glBindBufferBase(GL_UNIFORM_BUFFER, 0, batch.instanceUBO);

        glUseProgram(batch.shaderProgram);
        glBindTexture(GL_TEXTURE_2D, batch.texture);
        glBindVertexArray(batch.vao);

        glDrawElementsInstanced(
            GL_TRIANGLES, batch.indexCount, GL_UNSIGNED_INT,
            nullptr, instanceCount
        );

        drawCallsSaved  += instanceCount - 1;
        drawCallsEmitted++;
        batch.instances.clear();
    }

    if (drawCallsSaved > 0) {
        LOGI("DrawCallBatcher: %d draw calls émis au lieu de %d (économie: %d)",
             drawCallsEmitted, drawCallsEmitted + drawCallsSaved, drawCallsSaved);
    }
    return drawCallsSaved;
}

} // namespace NativeGLEngine
