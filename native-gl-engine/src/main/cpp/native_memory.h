#pragma once
#include <cstdint>

bool native_memory_init();
uint64_t native_memory_get_gpu_budget();
uint64_t native_memory_get_gpu_usage();
float native_memory_get_gpu_pressure();
int64_t native_memory_get_sys_available_mb();
int native_memory_get_temperature();
bool native_memory_is_thermal_throttling();
void native_memory_destroy();
