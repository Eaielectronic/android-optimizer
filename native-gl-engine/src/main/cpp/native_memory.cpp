/**
 * native_memory.cpp — Gestionnaire mémoire GPU via VMA + VK_EXT_memory_budget
 * 
 * Crée un VkInstance + VkDevice minimal (pas de rendu) juste pour
 * interroger le budget mémoire GPU réel sur Android.
 */

#include "native_memory.h"
#include <android/log.h>
#include <cstdio>
#include <cstring>
#include <cstdlib>

#define LOG_TAG "NativeGLEngine"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)

// TODO: Inclure les vrais headers Vulkan + VMA quand le NDK sera configuré
// #include <vulkan/vulkan.h>
// #define VMA_IMPLEMENTATION
// #include "vk_mem_alloc.h"

static bool g_initialized = false;

bool native_memory_init() {
    LOGI("[NativeGLEngine] native_memory_init: Utilisation d'heuristiques de mémoire unifiée (pas de Vulkan requis)");
    g_initialized = true;
    return true;
}

extern int g_gpu_budget_percent;

uint64_t native_memory_get_gpu_budget() {
    // Heuristique pour Android (Unified Memory Architecture) :
    // Le GPU partage la RAM système. On alloue virtuellement le pourcentage configuré
    // de la RAM disponible restante comme "budget GPU".
    int64_t avail_mb = native_memory_get_sys_available_mb();
    if (avail_mb <= 0) return 256 * 1024 * 1024; // Fallback 256MB
    
    // Fallback au cas où la valeur est invalide
    int percent = g_gpu_budget_percent > 0 ? g_gpu_budget_percent : 75;
    
    return (avail_mb * 1024 * 1024) * percent / 100;
}

uint64_t native_memory_get_gpu_usage() {
    // Difficile à tracker sans extensions EGL/VK.
    // On pourrait tracker GLInterceptor mais pour l'instant on retourne 0.
    return 0;
}

float native_memory_get_gpu_pressure() {
    uint64_t budget = native_memory_get_gpu_budget();
    uint64_t usage = native_memory_get_gpu_usage();
    if (budget == 0) return 1.0f;
    return (float)usage / (float)budget;
}

int64_t native_memory_get_sys_available_mb() {
    // Lecture de /proc/meminfo pour la mémoire système disponible
    FILE* f = fopen("/proc/meminfo", "r");
    if (!f) return -1;
    
    char line[256];
    while (fgets(line, sizeof(line), f)) {
        if (strncmp(line, "MemAvailable:", 13) == 0) {
            long kb = 0;
            sscanf(line + 13, "%ld", &kb);
            fclose(f);
            return kb / 1024; // Convertir kB → MB
        }
    }
    fclose(f);
    return -1;
}

int native_memory_get_temperature() {
    // Lecture de /sys/class/thermal/thermal_zone*/temp
    int max_temp = -1;
    char path[128];
    char buf[32];
    
    for (int zone = 0; zone < 20; zone++) {
        snprintf(path, sizeof(path), "/sys/class/thermal/thermal_zone%d/temp", zone);
        FILE* f = fopen(path, "r");
        if (!f) continue;
        
        if (fgets(buf, sizeof(buf), f)) {
            int temp = atoi(buf);
            if (temp > 1000) temp = temp / 1000; // millidegrés → degrés
            if (temp > 0 && temp < 120 && temp > max_temp) {
                max_temp = temp;
            }
        }
        fclose(f);
    }
    return max_temp;
}

bool native_memory_is_thermal_throttling() {
    int temp = native_memory_get_temperature();
    return temp > 50; // Seuil de throttle typique
}

void native_memory_destroy() {
    // TODO: Détruire VMA allocator + VkDevice + VkInstance
    g_initialized = false;
    LOGI("[NativeGLEngine] native_memory_destroy");
}
