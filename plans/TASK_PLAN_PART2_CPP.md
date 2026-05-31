# TASK PLAN PARTIE 2 — NATIVEGLENGINE (C++ NDK)
## Toutes les tâches C++ détaillées · Code complet · Sources vérifiées

---

## STRUCTURE DES FICHIERS C++ À CRÉER

```
native-gl-engine/src/main/cpp/
├── CMakeLists.txt              (MODIFIER)
├── jni_bridge.cpp              (MODIFIER — ajouter les nouvelles fonctions)
├── memory/
│   ├── arena_allocator.h       (NOUVEAU)
│   ├── arena_allocator.cpp     (NOUVEAU)
│   ├── pool_allocator.h        (NOUVEAU)
│   └── memory_purge.cpp        (NOUVEAU)
├── particles/
│   ├── particle_pool.h         (NOUVEAU)
│   ├── particle_pool.cpp       (NOUVEAU)
│   └── particle_neon.cpp       (NOUVEAU — SIMD)
├── audio/
│   ├── audio_pool.h            (NOUVEAU)
│   └── audio_pool.cpp          (NOUVEAU)
├── system/
│   ├── thermal_monitor.h       (NOUVEAU)
│   ├── thermal_monitor.cpp     (NOUVEAU)
│   ├── perf_hint.h             (NOUVEAU)
│   └── perf_hint.cpp           (NOUVEAU)
├── compression/
│   ├── lz4/lz4.c              (NOUVEAU — copier depuis GitHub lz4)
│   ├── lz4/lz4.h              (NOUVEAU)
│   ├── lz4/lz4hc.c            (NOUVEAU)
│   ├── lz4/lz4hc.h            (NOUVEAU)
│   └── lz4_bridge.cpp         (NOUVEAU)
└── comm/
    └── spsc_queue.h            (NOUVEAU)
```

---

## TÂCHE C1 — CMakeLists.txt Optimisé
**Fichier :** `native-gl-engine/src/main/cpp/CMakeLists.txt`

```cmake
cmake_minimum_required(VERSION 3.22)
project(androidopt_native)

# Sources LZ4 (statique, pas de dépendance externe)
add_library(lz4_static STATIC
    compression/lz4/lz4.c
    compression/lz4/lz4hc.c
)

# Notre bibliothèque native principale
add_library(androidopt_native SHARED
    jni_bridge.cpp
    memory/arena_allocator.cpp
    memory/memory_purge.cpp
    particles/particle_pool.cpp
    particles/particle_neon.cpp
    audio/audio_pool.cpp
    system/thermal_monitor.cpp
    system/perf_hint.cpp
    compression/lz4_bridge.cpp
)

# Flags de performance ARM64
target_compile_options(androidopt_native PRIVATE
    -O3 -march=armv8-a -mtune=cortex-a76
    -ffast-math -fno-exceptions -fno-rtti
    -fvisibility=hidden -flto -DNDEBUG
)
target_link_options(androidopt_native PRIVATE
    -flto -Wl,--gc-sections -Wl,-s
)
target_link_libraries(androidopt_native lz4_static log android)
```

**Source LZ4 :** GitHub `lz4/lz4` → télécharger `lz4.c`, `lz4.h`, `lz4hc.c`, `lz4hc.h`
depuis https://github.com/lz4/lz4/tree/dev/lib

---

## TÂCHE C2 — Arena Allocator
**Fichiers :** `memory/arena_allocator.h` + `.cpp`

```cpp
// memory/arena_allocator.h
#pragma once
#include <cstdint>
#include <cstddef>
#include <sys/mman.h>

class ArenaAllocator {
    uint8_t* buffer_;
    size_t   capacity_;
    size_t   offset_;
public:
    explicit ArenaAllocator(size_t capacity = 2 * 1024 * 1024);
    ~ArenaAllocator();

    void* alloc(size_t size, size_t align = 8);
    void  reset(); // appelé chaque frame
    size_t used() const { return offset_; }
    size_t capacity() const { return capacity_; }
};
```

```cpp
// memory/arena_allocator.cpp
#include "arena_allocator.h"
#include <android/log.h>
#define TAG "AndroidOpt_Arena"

ArenaAllocator::ArenaAllocator(size_t cap) : capacity_(cap), offset_(0) {
    buffer_ = (uint8_t*)mmap(nullptr, cap, PROT_READ | PROT_WRITE,
                              MAP_PRIVATE | MAP_ANONYMOUS, -1, 0);
    if (buffer_ == MAP_FAILED) {
        __android_log_print(ANDROID_LOG_ERROR, TAG, "mmap failed: %zu bytes", cap);
        buffer_ = nullptr; capacity_ = 0;
    } else {
        __android_log_print(ANDROID_LOG_INFO, TAG, "Arena: %zu KB", cap / 1024);
    }
}

ArenaAllocator::~ArenaAllocator() {
    if (buffer_) munmap(buffer_, capacity_);
}

void* ArenaAllocator::alloc(size_t size, size_t align) {
    size_t aligned = (offset_ + align - 1) & ~(align - 1);
    if (aligned + size > capacity_) return nullptr;
    void* ptr = buffer_ + aligned;
    offset_ = aligned + size;
    return ptr;
}

void ArenaAllocator::reset() { offset_ = 0; }
```

**Usage :** Données temporaires par frame (listes de rendu, calculs de visibilité).

---

## TÂCHE C3 — Pool de Particules
**Fichiers :** `particles/particle_pool.h` + `.cpp`

```cpp
// particles/particle_pool.h
#pragma once
#include <cstdint>

struct NativeParticle {
    float x, y, z;          // position (12 octets)
    float vx, vy, vz;       // vélocité (12 octets)
    float age, maxAge;       // durée de vie (8 octets)
    uint16_t texIndex;       // index atlas texture (2 octets)
    uint8_t r, g, b, a;     // couleur RGBA (4 octets)
    uint8_t active;          // slot actif ? (1 octet)
    uint8_t _pad;            // alignement (1 octet)
}; // Total : 40 octets, aligné

class ParticlePool {
    static constexpr int MAX = 4096;
    NativeParticle pool_[MAX];
    int activeCount_;
public:
    ParticlePool();
    int  spawn(float x, float y, float z,
               float vx, float vy, float vz,
               float maxAge, uint16_t tex,
               uint8_t r, uint8_t g, uint8_t b, uint8_t a);
    int  tickAll(float dt); // retourne le nombre de particules vivantes
    void clear();
    int  activeCount() const { return activeCount_; }
    const NativeParticle* data() const { return pool_; }
};
```

```cpp
// particles/particle_pool.cpp
#include "particle_pool.h"
#include <cstring>

ParticlePool::ParticlePool() : activeCount_(0) {
    memset(pool_, 0, sizeof(pool_));
}

int ParticlePool::spawn(float x, float y, float z,
                         float vx, float vy, float vz,
                         float maxAge, uint16_t tex,
                         uint8_t r, uint8_t g, uint8_t b, uint8_t a) {
    for (int i = 0; i < MAX; i++) {
        if (!pool_[i].active) {
            pool_[i] = {x,y,z, vx,vy,vz, 0,maxAge, tex, r,g,b,a, 1, 0};
            activeCount_++;
            return i;
        }
    }
    return -1; // plein
}

int ParticlePool::tickAll(float dt) {
    int alive = 0;
    for (int i = 0; i < MAX; i++) {
        if (!pool_[i].active) continue;
        pool_[i].x += pool_[i].vx * dt;
        pool_[i].y += pool_[i].vy * dt - 0.04f * dt; // gravité
        pool_[i].z += pool_[i].vz * dt;
        pool_[i].age += dt;
        if (pool_[i].age >= pool_[i].maxAge) {
            pool_[i].active = 0;
            activeCount_--;
        } else {
            alive++;
        }
    }
    return alive;
}

void ParticlePool::clear() {
    memset(pool_, 0, sizeof(pool_));
    activeCount_ = 0;
}
```

**Mixin Java nécessaire :** `CreateParticleMixin.java` (existe déjà).
Modifier pour appeler `NativeLib.spawnParticle(...)` au lieu de créer un objet Java.

---

## TÂCHE C4 — Thermal Monitor
**Fichiers :** `system/thermal_monitor.h` + `.cpp`

```cpp
// system/thermal_monitor.cpp
#include <stdio.h>
#include <android/log.h>
#define TAG "AndroidOpt_Thermal"

static int readTemp(int zone) {
    char path[64];
    snprintf(path, 64, "/sys/class/thermal/thermal_zone%d/temp", zone);
    FILE* f = fopen(path, "r");
    if (!f) return -1;
    int t; fscanf(f, "%d", &t); fclose(f);
    return t; // millièmes de degré (42000 = 42°C)
}

// Retourne : 0=froid, 1=tiède, 2=chaud, 3=critique
extern "C" int getThermalLevel() {
    int best = -1;
    for (int z = 0; z < 15; z++) {
        int t = readTemp(z);
        if (t > best) best = t;
    }
    if (best < 0) return 0;
    int c = best / 1000;
    if (c < 38) return 0;
    if (c < 43) return 1;
    if (c < 48) return 2;
    return 3;
}
```

---

## TÂCHE C5 — Memory Purge
**Fichier :** `memory/memory_purge.cpp`

```cpp
// memory/memory_purge.cpp
#include <malloc.h>
#include <sys/mman.h>
#include <android/log.h>
#define TAG "AndroidOpt_Purge"

extern "C" void purgeNativeMemory() {
    #if __ANDROID_API__ >= 28
    mallopt(M_PURGE, 0);
    __android_log_print(ANDROID_LOG_INFO, TAG, "mallopt M_PURGE done");
    #endif
}

extern "C" void releasePages(void* addr, size_t len) {
    if (addr && len > 0) {
        madvise(addr, len, MADV_DONTNEED);
    }
}
```

---

## TÂCHE C6 — LZ4 Compression Bridge
**Fichier :** `compression/lz4_bridge.cpp`
**Prérequis :** Copier `lz4.c`, `lz4.h`, `lz4hc.c`, `lz4hc.h` depuis GitHub `lz4/lz4`

```cpp
// compression/lz4_bridge.cpp
#include "lz4/lz4.h"
#include "lz4/lz4hc.h"
#include <jni.h>
#include <android/log.h>
#define TAG "AndroidOpt_LZ4"

extern "C" JNIEXPORT jint JNICALL
Java_fr_eaielectronic_nativeglengine_NativeLib_lz4Compress(
    JNIEnv* env, jclass, jobject srcBuf, jint srcLen, jobject dstBuf) {
    auto* src = (const char*)env->GetDirectBufferAddress(srcBuf);
    auto* dst = (char*)env->GetDirectBufferAddress(dstBuf);
    if (!src || !dst) return -1;
    int maxDst = LZ4_compressBound(srcLen);
    if (env->GetDirectBufferCapacity(dstBuf) < maxDst) return -1;
    int r = LZ4_compress_HC(src, dst, srcLen, maxDst, 9);
    __android_log_print(ANDROID_LOG_INFO, TAG,
        "Compress: %d -> %d (%.1fx)", srcLen, r, (float)srcLen/r);
    return r;
}

extern "C" JNIEXPORT jint JNICALL
Java_fr_eaielectronic_nativeglengine_NativeLib_lz4Decompress(
    JNIEnv* env, jclass, jobject srcBuf, jint srcLen,
    jobject dstBuf, jint maxDst) {
    auto* src = (const char*)env->GetDirectBufferAddress(srcBuf);
    auto* dst = (char*)env->GetDirectBufferAddress(dstBuf);
    if (!src || !dst) return -1;
    return LZ4_decompress_safe(src, dst, srcLen, maxDst);
}

extern "C" JNIEXPORT jint JNICALL
Java_fr_eaielectronic_nativeglengine_NativeLib_lz4CompressBound(
    JNIEnv*, jclass, jint srcLen) {
    return LZ4_compressBound(srcLen);
}
```

**Java-side :** Ajouter dans `NativeLib.java` :
```java
public static native int lz4Compress(ByteBuffer src, int srcLen, ByteBuffer dst);
public static native int lz4Decompress(ByteBuffer src, int srcLen, ByteBuffer dst, int maxDst);
public static native int lz4CompressBound(int srcLen);
```

---

## TÂCHE C7 — APerformanceHint + Fallback sched_setaffinity
**Fichier :** `system/perf_hint.cpp`

```cpp
// system/perf_hint.cpp
#include <android/api-level.h>
#include <sched.h>
#include <unistd.h>
#include <android/log.h>
#define TAG "AndroidOpt_Perf"

// Fallback pour Android < 12
static void fallbackAffinity() {
    cpu_set_t set;
    CPU_ZERO(&set);
    int n = sysconf(_SC_NPROCESSORS_ONLN);
    // Les P-Cores sont généralement les derniers
    for (int i = n > 2 ? n - 2 : 0; i < n; i++)
        CPU_SET(i, &set);
    if (sched_setaffinity(0, sizeof(set), &set) == 0)
        __android_log_print(ANDROID_LOG_INFO, TAG, "Affinity set to P-Cores");
    else
        __android_log_print(ANDROID_LOG_WARN, TAG, "Affinity failed");
}

extern "C" void boostCurrentThread() {
    #if __ANDROID_API__ >= 31
    // APerformanceHint disponible — utiliser l'API moderne
    // Nécessite #include <android/performance_hint.h>
    // et -landroid dans le linker
    // (voir Plan V9 pour le code complet)
    __android_log_print(ANDROID_LOG_INFO, TAG, "Using APerformanceHint API");
    #else
    fallbackAffinity();
    #endif
}
```

---

## TÂCHE C8 — JNI Bridge Principal
**Fichier :** `jni_bridge.cpp` (MODIFIER l'existant)
Ajouter toutes les fonctions JNI exportées :

```cpp
// Ajouter dans jni_bridge.cpp existant :
#include "particles/particle_pool.h"
#include "system/thermal_monitor.h"

static ParticlePool g_particles;

extern "C" {

// === PARTICULES ===
JNIEXPORT jint JNICALL
Java_fr_eaielectronic_nativeglengine_NativeLib_spawnParticle(
    JNIEnv*, jclass, jfloat x, jfloat y, jfloat z,
    jfloat vx, jfloat vy, jfloat vz, jfloat maxAge,
    jint tex, jint r, jint g, jint b, jint a) {
    return g_particles.spawn(x,y,z, vx,vy,vz, maxAge,
        (uint16_t)tex, (uint8_t)r,(uint8_t)g,(uint8_t)b,(uint8_t)a);
}

JNIEXPORT jint JNICALL
Java_fr_eaielectronic_nativeglengine_NativeLib_tickParticles(
    JNIEnv*, jclass, jfloat dt) {
    return g_particles.tickAll(dt);
}

JNIEXPORT void JNICALL
Java_fr_eaielectronic_nativeglengine_NativeLib_clearParticles(
    JNIEnv*, jclass) {
    g_particles.clear();
}

// === THERMAL ===
JNIEXPORT jint JNICALL
Java_fr_eaielectronic_nativeglengine_NativeLib_getThermalLevel(
    JNIEnv*, jclass) {
    return getThermalLevel();
}

// === MEMORY PURGE ===
JNIEXPORT void JNICALL
Java_fr_eaielectronic_nativeglengine_NativeLib_purgeNativeMemory(
    JNIEnv*, jclass) {
    purgeNativeMemory();
}

// === PERF BOOST ===
JNIEXPORT void JNICALL
Java_fr_eaielectronic_nativeglengine_NativeLib_boostCurrentThread(
    JNIEnv*, jclass) {
    boostCurrentThread();
}

} // extern "C"
```

**Java-side :** Ajouter dans `NativeLib.java` :
```java
// Particules
public static native int spawnParticle(float x, float y, float z,
    float vx, float vy, float vz, float maxAge,
    int tex, int r, int g, int b, int a);
public static native int tickParticles(float dt);
public static native void clearParticles();

// Système
public static native int getThermalLevel();
public static native void purgeNativeMemory();
public static native void boostCurrentThread();
```

---

## TÂCHE C9 — SPSC Queue (Communication Java→C++)
**Fichier :** `comm/spsc_queue.h` (header-only)

```cpp
// comm/spsc_queue.h
#pragma once
#include <atomic>
#include <cstdint>

template<typename T, uint32_t N>
class SPSCQueue {
    static_assert((N & (N-1)) == 0, "N must be power of 2");
    alignas(64) std::atomic<uint32_t> head_{0};
    alignas(64) std::atomic<uint32_t> tail_{0};
    T buffer_[N];
public:
    bool push(const T& item) {
        uint32_t t = tail_.load(std::memory_order_relaxed);
        uint32_t next = (t + 1) & (N - 1);
        if (next == head_.load(std::memory_order_acquire)) return false;
        buffer_[t] = item;
        tail_.store(next, std::memory_order_release);
        return true;
    }
    bool pop(T& item) {
        uint32_t h = head_.load(std::memory_order_relaxed);
        if (h == tail_.load(std::memory_order_acquire)) return false;
        item = buffer_[h];
        head_.store((h + 1) & (N - 1), std::memory_order_release);
        return true;
    }
};
```

Usage futur : remplacer les appels JNI individuels de spawn par un batch.

---

## ORDRE D'IMPLÉMENTATION COMPLET (Java + C++)

```
SEMAINE 1-2 : FONDATIONS
  [ ] T1  ResourceLocation Intern (Java, facile)
  [ ] C1  CMakeLists.txt optimisé
  [ ] C2  Arena Allocator
  [ ] C5  Memory Purge (mallopt/madvise)

SEMAINE 3-4 : PARTICULES
  [ ] C3  Pool de Particules (C++)
  [ ] C8  JNI Bridge (ajouter spawnParticle, tickParticles)
  [ ]     Modifier CreateParticleMixin pour appeler le C++

SEMAINE 5-6 : MÉMOIRE JAVA
  [ ] T3  JEI Lazy Index (le PLUS GROS gain)
  [ ] T2  Atlas Polices Paginé
  [ ] T9  Configuration (ajouter les options dans OptConfig)

SEMAINE 7-8 : MODÈLES 3D
  [ ] T4  BakedModel Eviction + FlatSpriteModel
  [ ]     ModelEvictionPolicy (LFU-LRU)
  [ ]     ModelLifecycleManager (FSM)
  [ ]     Mixin sur ModelManager

SEMAINE 9-10 : OFF-HEAP
  [ ] T5  Agrona SoA (KineticBlockEntity)
  [ ] T6  NBT Entités Off-Heap
  [ ] T8  MemoryWatchdog amélioré

SEMAINE 11-12 : SYSTÈME
  [ ] C4  Thermal Monitor
  [ ] C7  APerformanceHint + fallback affinity
  [ ] T7  Audio Pool Bridge (Java)

SEMAINE 13-14 : COMPRESSION
  [ ] C6  LZ4 Bridge (copier lz4.c depuis GitHub)
  [ ]     Intégrer avec NbtCompressor (Java)
  [ ]     Compresser les réseaux Create inactifs

SEMAINE 15-16 : POLISH
  [ ] C9  SPSC Queue
  [ ] T10 Mettre à jour mixins.json
  [ ]     Tests sur appareil Android réel
  [ ]     Benchmarks Spark + adb dumpsys meminfo
```

---

## SOURCES PRINCIPALES

| Ressource | URL | Usage |
|-----------|-----|-------|
| LZ4 source C | github.com/lz4/lz4/tree/dev/lib | Copier lz4.c, lz4.h, lz4hc.c, lz4hc.h |
| Agrona Java | github.com/real-logic/agrona | Dépendance gradle, UnsafeBuffer |
| JEI source | github.com/mezz/JustEnoughItems | Comprendre IngredientListElementList |
| Create source | github.com/Creators-of-Create/Create | KineticBlockEntity.java |
| ModernFix source | github.com/embeddedt/ModernFix | DynamicBakedModelProvider.java |
| FerriteCore source | github.com/malte0811/FerriteCore | Vérifier non-duplication |
| Android NDK docs | developer.android.com/ndk | APerformanceHint, JNI tips |
| Spark profiler | github.com/lucko/spark | Outil de mesure obligatoire |

---

*Task Plan Complet · Parties 1+2 · 31/05/2026*
*10 tâches Java + 9 tâches C++ = 19 tâches au total*
*Ordre : fondations → particules → mémoire → modèles → off-heap → système → compression*
