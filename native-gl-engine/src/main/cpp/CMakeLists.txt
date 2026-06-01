cmake_minimum_required(VERSION 3.22)
project(NativeGLEngine VERSION 0.1.1 LANGUAGES C CXX)

set(CMAKE_C_STANDARD 11)
set(CMAKE_C_STANDARD_REQUIRED ON)
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# ─── Flags de compilation ARM64 optimisés ───
set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -O3 -ffast-math -fvisibility=hidden")
set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -fomit-frame-pointer -funwind-tables")

# ─── Dépendances via FetchContent ───
include(FetchContent)

# Shaderc (Google) — Compilation GLSL → SPIR-V
FetchContent_Declare(shaderc
    GIT_REPOSITORY https://github.com/google/shaderc.git
    GIT_TAG        v2024.1
    GIT_SHALLOW    TRUE
)
set(SHADERC_SKIP_TESTS ON)
set(SHADERC_SKIP_EXAMPLES ON)

# SPIRV-Cross (Khronos) — Conversion SPIR-V → ESSL
FetchContent_Declare(spirv_cross
    GIT_REPOSITORY https://github.com/KhronosGroup/SPIRV-Cross.git
    GIT_TAG        vulkan-sdk-1.3.268.0
    GIT_SHALLOW    TRUE
)
set(SPIRV_CROSS_ENABLE_TESTS OFF)
set(SPIRV_CROSS_CLI OFF)

# VulkanMemoryAllocator — Gestion mémoire GPU
FetchContent_Declare(vma
    GIT_REPOSITORY https://github.com/GPUOpen-LibrariesAndSDKs/VulkanMemoryAllocator.git
    GIT_TAG        v3.1.0
    GIT_SHALLOW    TRUE
)

# ByteHook (bhook) — PLT Hooking Android
FetchContent_Declare(bhook
    GIT_REPOSITORY https://github.com/bytedance/bhook.git
    GIT_TAG        v1.0.9
    GIT_SHALLOW    TRUE
)

FetchContent_MakeAvailable(shaderc spirv_cross vma bhook)
add_subdirectory(${bhook_SOURCE_DIR}/bytehook/src/main/cpp ${CMAKE_CURRENT_BINARY_DIR}/bhook_build)


# ─── etcpak (ETC2 encoder) ───
add_library(etcpak_lib STATIC
    deps/etcpak/ProcessRGB.cpp
    deps/etcpak/ProcessDxtc.cpp
    deps/etcpak/Tables.cpp
)
target_include_directories(etcpak_lib PUBLIC deps/etcpak)
target_compile_options(etcpak_lib PRIVATE -O3 -ffast-math)

# ─── astcenc (ARM ASTC Encoder) ───
# ISA_NEON=ON : utilise les intrinsics NEON arm64
set(ASTCENC_ISA_NEON ON CACHE BOOL "" FORCE)
add_subdirectory(deps/astcenc EXCLUDE_FROM_ALL)

# ─── LZ4 Compression (statique, zéro dépendance externe) ───
add_library(lz4_static STATIC
    compression/lz4/lz4.c
    compression/lz4/lz4hc.c
)
target_compile_options(lz4_static PRIVATE -O3 -ffast-math)

# ─── Bibliothèque principale ───
add_library(NativeGLEngine SHARED
    jni_bridge.cpp
    shader_compiler.cpp
    shader_cache.cpp
    soc_optimizer.cpp
    gl_interceptor.cpp
    native_memory.cpp
    texture_manager.cpp
    texture_compressor.cpp
    off_heap_arena.cpp
    vertex_quantizer.cpp
    simd_math_engine.cpp
    draw_call_batcher.cpp
    ahardware_buffer_manager.cpp
    # V9 — Arsenal C++ NDK
    particles/particle_pool.cpp
    system/thermal_monitor.cpp
    system/memory_purge.cpp
    system/perf_hint.cpp
    compression/lz4_bridge.cpp
)

# ─── Headers ───
target_include_directories(NativeGLEngine PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}
    ${CMAKE_CURRENT_SOURCE_DIR}/compression
    ${vma_SOURCE_DIR}/include
    ${bhook_SOURCE_DIR}/bytehook/src/main/cpp/include
    ${CMAKE_CURRENT_SOURCE_DIR}/deps/etcpak
    ${CMAKE_CURRENT_SOURCE_DIR}/deps/astcenc/Source
    ${CMAKE_CURRENT_SOURCE_DIR}/deps/fp16/include
)

# ─── Librairies Android système ───
target_link_libraries(NativeGLEngine
    shaderc
    spirv-cross-glsl
    spirv-cross-core
    bytehook
    lz4_static
    vulkan
    log
    android
    EGL
    GLESv3
    jnigraphics
    etcpak_lib
    astcenc-neon-static
)

# ─── Strip en release ───
if(CMAKE_BUILD_TYPE STREQUAL "Release")
    target_link_options(NativeGLEngine PRIVATE -s)
endif()
