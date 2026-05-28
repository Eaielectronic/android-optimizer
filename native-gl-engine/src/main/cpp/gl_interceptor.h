#pragma once
#include <cstdint>

bool gl_interceptor_install();
void gl_interceptor_uninstall();
uint64_t gl_interceptor_get_total_calls();
uint64_t gl_interceptor_get_deduped_calls();
uint64_t gl_interceptor_get_deferred_textures();
