/**
 * memory_purge.cpp — Purge agressive de la mémoire native Android.
 *
 * Deux armes complémentaires :
 * 1. mallopt(M_PURGE, 0) : Force l'allocateur (scudo/jemalloc) à rendre
 *    la mémoire libre au kernel immédiatement.
 *    Source : https://cs.android.com/android/platform/superproject/+/main:bionic/libc/bionic/mallopt.cpp
 *
 * 2. madvise(MADV_DONTNEED) : Dit au kernel de reprendre les pages physiques
 *    d'une zone mmap'd qu'on n'utilise plus. Le RSS baisse immédiatement.
 *    Source : man madvise(2), Linux kernel standard.
 *
 * Quand appeler :
 * - Après le chargement complet d'un monde (gros pic temporaire)
 * - Quand le MemoryWatchdog Java détecte un heap critique (>80%)
 * - Quand le thermal detector dit "chaud" (réduire la pression mémoire)
 * - Périodiquement toutes les 5 minutes via le thread d'éviction
 */
#include <malloc.h>
#include <sys/mman.h>
#include <android/log.h>
#include <unistd.h>
#include <stdio.h>
#include <string.h>

#define TAG "AndroidOpt_Purge"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, TAG, __VA_ARGS__)

namespace androidopt {

/**
 * Lit la mémoire RSS actuelle du processus via /proc/self/status.
 * @return RSS en Ko, ou -1 si impossible à lire
 */
static long readRssKb() {
    FILE* f = fopen("/proc/self/status", "r");
    if (!f) return -1;
    char line[128];
    long rss = -1;
    while (fgets(line, sizeof(line), f)) {
        if (strncmp(line, "VmRSS:", 6) == 0) {
            sscanf(line + 6, "%ld", &rss);
            break;
        }
    }
    fclose(f);
    return rss;
}

/**
 * Force l'allocateur natif à rendre toute la mémoire libre au kernel.
 * Utilise mallopt(M_PURGE, 0) disponible depuis Android API 28.
 *
 * @return la quantité de RSS récupérée en Ko (estimation), ou 0 si non disponible
 */
long purgeNativeMemory() {
    long rssBefore = readRssKb();

#if defined(__ANDROID_API__) && __ANDROID_API__ >= 28
    // M_PURGE = force purge des pages libérées mais pas encore rendues
    int result = mallopt(M_PURGE, 0);
    if (result == 0) {
        LOGW("mallopt(M_PURGE) returned failure (may not be supported on this ROM)");
    }
#else
    LOGI("mallopt(M_PURGE) not available (API < 28), skipping");
    return 0;
#endif

    long rssAfter = readRssKb();
    long recovered = 0;
    if (rssBefore > 0 && rssAfter > 0 && rssBefore > rssAfter) {
        recovered = rssBefore - rssAfter;
    }

    LOGI("purgeNativeMemory: RSS %ld KB -> %ld KB (recovered ~%ld KB)",
         rssBefore, rssAfter, recovered);
    return recovered;
}

/**
 * Libère les pages physiques d'un buffer mmap'd sans le démapper.
 * Le buffer reste valide en mémoire virtuelle mais ne consomme plus de RAM.
 * Si on y accède à nouveau, le kernel fournira des pages zéro.
 *
 * @param addr adresse du buffer (doit être alignée sur la page)
 * @param len taille en octets
 * @return 0 si succès, -1 si erreur
 */
int releasePages(void* addr, size_t len) {
    if (!addr || len == 0) return -1;

    // Aligner sur la taille de page
    long pageSize = sysconf(_SC_PAGESIZE);
    uintptr_t aligned = ((uintptr_t)addr) & ~(pageSize - 1);
    size_t offset = (uintptr_t)addr - aligned;
    size_t alignedLen = len + offset;

    int result = madvise((void*)aligned, alignedLen, MADV_DONTNEED);
    if (result != 0) {
        LOGW("madvise(MADV_DONTNEED) failed for %p (%zu bytes)", addr, len);
        return -1;
    }

    LOGI("releasePages: freed %zu KB at %p", len / 1024, addr);
    return 0;
}

/**
 * Lit la mémoire disponible du système via /proc/meminfo.
 * @return mémoire disponible en Mo
 */
long getSystemAvailableMemoryMB() {
    FILE* f = fopen("/proc/meminfo", "r");
    if (!f) return -1;
    char line[128];
    long available = -1;
    while (fgets(line, sizeof(line), f)) {
        if (strncmp(line, "MemAvailable:", 13) == 0) {
            sscanf(line + 13, "%ld", &available);
            break;
        }
    }
    fclose(f);
    return (available > 0) ? (available / 1024) : -1; // Ko → Mo
}

} // namespace androidopt
