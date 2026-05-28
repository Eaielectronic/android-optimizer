#pragma once
#include <vector>
#include <cstdint>
#include "shader_compiler.h"

/**
 * Applique des optimisations SPIR-V spécifiques au SoC.
 * Utilise spirv-opt (SPIRV-Tools) pour des passes ciblées.
 */
void optimize_spirv_for_soc(std::vector<uint32_t>& spirv, SocVendor vendor);
