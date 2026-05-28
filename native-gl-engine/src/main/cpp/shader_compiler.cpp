/**
 * shader_compiler.cpp — Pipeline GLSL → SPIR-V → ESSL
 * 
 * Utilise Shaderc (Google) pour la compilation GLSL → SPIR-V
 * et SPIRV-Cross (Khronos) pour la conversion SPIR-V → ESSL.
 * 
 * Les optimisations SPIR-V par SoC sont déléguées à soc_optimizer.cpp.
 */

#include "shader_compiler.h"
#include "soc_optimizer.h"
#include <android/log.h>

#define LOG_TAG "NativeGLEngine"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)

#include <shaderc/shaderc.hpp>
#include <spirv_glsl.hpp>

bool shader_compiler_compile_glsl(const char* glsl_source, ShaderType type,
                                   SocVendor vendor, std::vector<uint32_t>& out_spirv,
                                   std::string& out_error) {
    shaderc::Compiler compiler;
    shaderc::CompileOptions options;

    // Remplacer dynamiquement les vieilles directives #version de Minecraft par #version 330
    // pour permettre la compilation SPIR-V (Shaderc exige 330 minimum pour Vulkan/SPIRV).
    std::string source_str(glsl_source);
    size_t version_pos = source_str.find("#version");
    if (version_pos != std::string::npos) {
        size_t end_line = source_str.find('\n', version_pos);
        if (end_line != std::string::npos) {
            source_str.replace(version_pos, end_line - version_pos, "#version 330");
        }
    } else {
        source_str = "#version 330\n" + source_str;
    }

    options.SetOptimizationLevel(shaderc_optimization_level_performance);
    options.SetAutoMapLocations(true);
    options.SetAutoBindUniforms(true);
    
    // CRITIQUE : Minecraft génère du GLSL Desktop (ex: #version 150).
    // Si on cible Vulkan par défaut, Shaderc rejette le code car il manque 
    // les "layout(location = X)" sur les entrées/sorties.
    // On doit cibler OpenGL pour que Shaderc ajoute automatiquement les bindings.
    options.SetTargetEnvironment(shaderc_target_env_opengl, shaderc_env_version_opengl_4_5);

    shaderc_shader_kind kind;
    switch (type) {
        case VERTEX: kind = shaderc_glsl_vertex_shader; break;
        case FRAGMENT: kind = shaderc_glsl_fragment_shader; break;
        case GEOMETRY: kind = shaderc_glsl_geometry_shader; break;
        case COMPUTE: kind = shaderc_glsl_compute_shader; break;
        default: kind = shaderc_glsl_infer_from_source; break;
    }

    shaderc::SpvCompilationResult result = compiler.CompileGlslToSpv(
        source_str.c_str(), kind, "shader", options);

    if (result.GetCompilationStatus() != shaderc_compilation_status_success) {
        std::string err = result.GetErrorMessage();
        LOGW("[NativeGLEngine] Shaderc compilation failed: %s", err.c_str());
        out_error = err;
        return false;
    }

    out_spirv.assign(result.cbegin(), result.cend());

    // Passes d'optimisations SoC
    optimize_spirv_for_soc(out_spirv, vendor);

    return true;
}

std::string shader_compiler_spirv_to_essl(const uint32_t* spirv_data, size_t word_count) {
    try {
        spirv_cross::CompilerGLSL glsl_compiler(spirv_data, word_count);
        spirv_cross::CompilerGLSL::Options options;
        
        // ESSL 3.20 (standard pour Android moderne)
        options.version = 320;
        options.es = true;
        
        glsl_compiler.set_common_options(options);
        return glsl_compiler.compile();
    } catch (const spirv_cross::CompilerError& e) {
        LOGW("[NativeGLEngine] SPIRV-Cross conversion failed: %s", e.what());
        return "";
    }
}

std::string shader_compiler_get_driver_version() {
    // TODO: Lire la version du driver via EGL
    //
    // EGLDisplay display = eglGetDisplay(EGL_DEFAULT_DISPLAY);
    // const char* version = eglQueryString(display, EGL_VERSION);
    
    return "unknown";
}
