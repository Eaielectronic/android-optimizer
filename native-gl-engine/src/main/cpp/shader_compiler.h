#pragma once
#include <vector>
#include <string>
#include <cstdint>

enum ShaderType { VERTEX = 0, FRAGMENT = 1, GEOMETRY = 2, COMPUTE = 3 };
enum SocVendor { VENDOR_UNKNOWN = 0, VENDOR_QUALCOMM = 1, VENDOR_ARM = 2, 
                 VENDOR_MEDIATEK = 3, VENDOR_SAMSUNG = 4, VENDOR_GOOGLE_TENSOR = 5, VENDOR_HUAWEI = 6 };

/**
 * Compile un shader GLSL vers du SPIR-V binaire via Shaderc.
 * Applique des optimisations SPIR-V spécifiques au SoC via spirv-opt.
 */
bool shader_compiler_compile_glsl(const char* glsl_source, ShaderType type,
                                   SocVendor vendor, std::vector<uint32_t>& out_spirv,
                                   std::string& out_error);

/**
 * Convertit du SPIR-V vers du ESSL (GLSL for OpenGL ES) via SPIRV-Cross.
 */
std::string shader_compiler_spirv_to_essl(const uint32_t* spirv_data, size_t word_count);

/**
 * Retourne la version du driver GPU détectée via EGL.
 */
std::string shader_compiler_get_driver_version();
