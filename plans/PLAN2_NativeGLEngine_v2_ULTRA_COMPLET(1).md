# 🔥 PLAN 2 — NativeGLEngine v2 · Suite Ultra-Performance C++
## Objectif : LibreCraft + 60 mods (Create etc.) dans **2100 MB** à **60 FPS stables**
### Nouvelles fonctionnalités C++ bas niveau — La suite du Plan 1

> **Contexte** : Le Plan 1 couvre ShaderCompiler async (Module 1), NativeMemoryManager VMA (Module 2),
> InterceptLayer GL hooks (Module 3), OptimiseurSoC (Module 4), et le bypass Vulkan partiel (Module 5).
> Ce Plan 2 ajoute **6 nouveaux modules** qui s'attaquent aux 3 vrais tueurs de RAM/CPU restants sur mobile :
> le **Garbage Collector Java**, la **bande passante GPU**, et les **draw calls excessifs des mods**.

---

## 🎯 PROBLÈME CIBLE — Pourquoi 2100 MB avec 60 mods c'est difficile

Avec Create + 59 autres mods lourds, la RAM se répartit typiquement ainsi :

| Poste | Sans optimisation | Avec Plan 1+2 |
|---|---|---|
| JVM Heap (Java objects) | ~700 MB | ~500 MB (GC réduit) |
| Textures GPU (RGBA8 non compressées) | ~900 MB | **~180 MB** (ETC2/ASTC ÷5) |
| Géométrie vertex (terrain + entités) | ~250 MB | **~125 MB** (FP16 ÷2) |
| Shaders compilés (Plan 1) | ~120 MB → 20 MB cache | déjà optimisé |
| Moteur JVM + code | ~150 MB | ~150 MB (invariant) |
| **TOTAL** | **~2120 MB** | **~975 MB** |

> **Conclusion** : Le plus gros gain (÷5 sur les textures) vient du Module 6 (Compression ETC2/ASTC).
> C'est le premier à implémenter en priorité absolue.

### Comprendre le pipeline GPU mobile (TBR/TBDR)

Contrairement aux GPU de bureau (Immediate Mode Rendering), **tous les GPU Android sont Tile-Based** (TBR/TBDR) :
- Le framebuffer est découpé en tuiles de **16×16 ou 32×32 pixels**
- Chaque tuile est rendue entièrement en cache on-chip (très rapide, ~100 GB/s)
- Seule la tuile finale est écrite en mémoire principale (lente, ~25 GB/s)
- **Conséquence** : réduire le nombre de textures/vertices en RAM réduit directement la bande passante tuile, donc la consommation et la chaleur

Architecture GPU pertinente pour ce projet :

| GPU (SoC) | Architecture | Taille tuile | Bande passante mémoire |
|---|---|---|---|
| Adreno 750 (SD 8 Gen 3) | TBDR | 32×32 | 77 GB/s |
| Adreno 740 (SD 8 Gen 2) | TBDR | 32×32 | 51 GB/s |
| Mali-G715 (Dimensity 9200) | TBDR | 16×16 | 51 GB/s |
| Adreno 650 (SD 865) | TBDR | 32×32 | 44 GB/s |

---

## 🗺️ APERÇU DES 6 NOUVEAUX MODULES

```
Plan 1 (Modules 1-5) : ShaderCompiler · MemoryManager · InterceptLayer · SoCOptimizer · VulkanPath
                            │
                            ▼
Plan 2 (Modules 6-11) :
  ┌─────────────────────────────────────────────────────────────┐
  │  Module 6  │  TextureCompressor   │ ETC2/ASTC à la volée    │ ← PRIORITÉ 1 : -720MB RAM
  │  Module 7  │  OffHeapArena        │ Zéro GC, zéro copie     │ ← PRIORITÉ 2 : -200MB, zéro freeze
  │  Module 8  │  VertexQuantizer     │ FP16 NEON               │ ← PRIORITÉ 3 : -125MB, +GPU perf
  │  Module 9  │  SIMDMathEngine      │ Frustum / Matrix NEON   │ ← PRIORITÉ 4 : CPU ×5-10
  │  Module 10 │  DrawCallBatcher     │ Unification Create+mods │ ← PRIORITÉ 5 : draw calls ÷10
  │  Module 11 │  AHardwareBuffer     │ Zero-copy GPU upload    │ ← PRIORITÉ 6 : freezes texture 0ms
  └─────────────────────────────────────────────────────────────┘
```

---

## 📦 MODULE 6 — TextureCompressor Natif (PRIORITÉ MAXIMALE — Le Tueur de RAM)

### 6.1 Pourquoi c'est le plus important

Minecraft moddé charge des **atlas de textures RGBA8** (4 octets/pixel non compressé). Avec 60 mods (Create + ses addons, tous leurs textures custom) :
- Atlas principal : 4096×4096 = **67 MB en RGBA8**
- Multiplicateur mods : ×8 à ×12 atlas différents
- **Total non compressé : 500–900 MB juste pour les textures**

ETC2 compresse à **4 bits/pixel** (vs 32 bits RGBA8) → **ratio ×8 sur RGB, ×4 sur RGBA**.
ASTC 6×6 compresse encore mieux (~2,37 bits/pixel) et est supporté sur **tous les GPU Android 2022+**.

#### Comparatif des formats de compression

| Format | Bits/pixel | Ratio vs RGBA8 | GPU cible | Qualité |
|---|---|---|---|---|
| RGBA8 (aucun) | 32 | ×1 | Tous | Parfaite |
| ETC2 RGB | 4 | ×8 | GLES 3.0+ (100% Android) | Bonne |
| ETC2 RGBA (EAC) | 8 | ×4 | GLES 3.0+ (100% Android) | Bonne |
| ASTC 4×4 | 8 | ×4 | Android 2022+ (95%) | Excellente |
| ASTC 6×6 | 2.37 | ×13.5 | Android 2022+ (95%) | Très bonne |
| ASTC 8×8 | 2 | ×16 | Android 2022+ (95%) | Moyenne |

> **Règle d'or** : utiliser ASTC 6×6 quand disponible, sinon ETC2 RGBA. Ne jamais envoyer du RGBA8 non compressé au GPU.

#### Benchmarks etcpak (wolfpld) — chiffres réels 2024

D'après les benchmarks officiels d'etcpak sur Ryzen 7950X (mono-thread / multi-thread) :
- ETC2 RGB : 356 Mpx/s ST / 6378 Mpx/s MT
- ETC2 RGBA : ~180 Mpx/s ST / ~3000 Mpx/s MT

Sur ARM (Odroid C2, cortex-A53 ancien) :
- ETC2 RGB : 12.3 Mpx/s ST / 48.4 Mpx/s MT
- ETC2 RGBA : 2.83 Mpx/s ST / ~11 Mpx/s MT

**Sur Snapdragon 8 Gen 2 (Cortex-X3 @3.2GHz + NEON)**, on peut estimer :
- ETC2 RGBA : ~25-40 Mpx/s ST → une texture 4096×4096 (16M pixels) ≈ **400-640ms**
- Avec 2 threads en arrière-plan sur cores efficaces : ~200-320ms par atlas

→ **On doit impérativement compresser en arrière-plan, JAMAIS sur le thread de rendu**.

### 6.2 Prérequis et installation des dépendances

#### Étape 1 — Ajouter etcpak comme git submodule

```bash
# Depuis la racine du projet Android
cd app/src/main/cpp/deps

# Ajouter etcpak (l'encodeur ETC2 le plus rapide au monde)
git submodule add https://github.com/wolfpld/etcpak.git etcpak
git submodule update --init --recursive

# Vérifier les fichiers importants
ls etcpak/
# ProcessRGB.hpp  ProcessRGB.cpp
# ProcessDxtc.hpp ProcessDxtc.cpp
# Dither.hpp  Bitmap.hpp  ColorSpace.hpp
```

#### Étape 2 — Ajouter astcenc (ARM ASTC Encoder) comme git submodule

```bash
git submodule add https://github.com/ARM-software/astc-encoder.git astcenc
git submodule update --init --recursive

# Vérifier la structure
ls astcenc/Source/
# astcenc.h         ← API publique (le seul header à inclure)
# astcenc_*.cpp     ← implémentation
```

#### Étape 3 — Ajouter fp16 (conversion FP32↔FP16 portable)

```bash
git submodule add https://github.com/Maratyszcza/FP16.git fp16
# Un seul header : fp16/include/fp16.h
```

#### Étape 4 — Vérifier .gitmodules

```ini
[submodule "app/src/main/cpp/deps/etcpak"]
    path = app/src/main/cpp/deps/etcpak
    url = https://github.com/wolfpld/etcpak.git

[submodule "app/src/main/cpp/deps/astcenc"]
    path = app/src/main/cpp/deps/astcenc
    url = https://github.com/ARM-software/astc-encoder.git

[submodule "app/src/main/cpp/deps/fp16"]
    path = app/src/main/cpp/deps/fp16
    url = https://github.com/Maratyszcza/FP16.git
```

### 6.3 Architecture du TextureCompressor

```
glTexImage2D() intercepté par InterceptLayer (Module 3)
    │
    ▼
TextureCompressor::intercept(target, level, internalformat, width, height, data)
    │
    ├── [Test rapide] Peut-on compresser ? (width%4==0, height%4==0, pas de mipmap niveau 0)
    │
    ├── [Check support GPU] glGetString(GL_EXTENSIONS) → cherche GL_KHR_texture_compression_astc_ldr
    │     ├── ASTC disponible  → CompressorASTCThread::submit(data)
    │     └── ASTC indisponible → CompressorETC2Thread::submit(data)  ← toujours dispo sur GLES3
    │
    ├── [Thread de compression] (pool de 2 threads C++, sur cores efficaces)
    │     └── etcpak::CompressBlocksETC2() ou astcenc_compress_image()
    │
    └── [Résultat] glCompressedTexImage2D(GL_COMPRESSED_RGBA8_ETC2_EAC ou GL_COMPRESSED_RGBA_ASTC_6x6)
                   Au lieu du glTexImage2D original → envoi direct au GPU sous forme compressée
```

**Gain mémoire GPU** :
- RGBA8 original : 4096×4096×4 = **67 MB**
- ETC2 RGBA : 4096×4096×1 = **16 MB** (÷4)
- ASTC 6×6 : 4096×4096×(128/(6×6×8)) ≈ **7.3 MB** (÷9)

### 6.4 texture_compressor.h

```cpp
// src/main/cpp/texture_compressor.h
#pragma once
#include <GLES3/gl3.h>
#include <vector>
#include <future>
#include <functional>

namespace NativeGLEngine {

struct CompressedResult {
    GLenum format;              // GL_COMPRESSED_RGBA8_ETC2_EAC ou GL_COMPRESSED_RGBA_ASTC_6x6_KHR
    std::vector<uint8_t> data;  // données compressées
    GLsizei width, height;
    bool success;
};

class TextureCompressor {
public:
    // Appelé une fois au démarrage (depuis GL thread)
    static void detectCapabilities();

    // Décide si on doit compresser (filtrage rapide)
    static bool shouldCompress(GLenum internalformat,
                                GLsizei width, GLsizei height,
                                const void* data);

    // Compression synchrone (appelée depuis thread de compression)
    static bool compressETC2(const uint8_t* src_rgba,
                              GLsizei width, GLsizei height,
                              std::vector<uint8_t>& dst_etc2);

    static bool compressASTCIfAvailable(const uint8_t* src_rgba,
                                         GLsizei width, GLsizei height,
                                         std::vector<uint8_t>& dst_astc);

    // Interception principale — remplace glTexImage2D
    // Retourne true si la texture a été compressée et uploadée
    static bool intercept(GLenum target, GLint level,
                           GLint internalformat,
                           GLsizei width, GLsizei height,
                           GLenum format, GLenum type,
                           const void* pixels);

    // Async : compression en arrière-plan, upload via fence GL
    static std::future<CompressedResult> compressAsync(
        const uint8_t* src_rgba,
        GLsizei width, GLsizei height,
        GLenum preferredFormat);

    static bool s_hasASTCLDR;
    static bool s_hasETC2;
};

} // namespace NativeGLEngine
```

### 6.5 texture_compressor.cpp — Code complet annoté

```cpp
// src/main/cpp/texture_compressor.cpp
// Compression ETC2/ASTC à la volée lors de l'interception de glTexImage2D
// Librairies : etcpak (ETC2 ultra-rapide NEON) + astcenc (ARM ASTC encoder)

#include "texture_compressor.h"
#include "gl_interceptor.h"
#include "soc_optimizer.h"
#include <GLES3/gl3.h>
#include <GLES3/gl31.h>
#include <android/log.h>
#include <cstring>
#include <vector>
#include <thread>
#include <atomic>
#include <future>
#include <mutex>

// ─── etcpak headers ───
// https://github.com/wolfpld/etcpak
// IMPORTANT : etcpak a changé son build system de Meson → CMake en 2024
// Utiliser la version CMake directement dans le projet
#include "etcpak/ProcessRGB.hpp"   // CompressEtc2Rgb, CompressEtc2RgbHQ
#include "etcpak/ProcessDxtc.hpp"  // (DXT, pas utilisé ici)

// ─── astcenc header ───
// https://github.com/ARM-software/astc-encoder
// Un seul header public : astcenc.h
#include "astcenc/Source/astcenc.h"

#define LOG_TAG "NativeGL-TexCompress"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO,    LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR,   LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN,    LOG_TAG, __VA_ARGS__)

namespace NativeGLEngine {

// ─── Variables globales (initialisées une fois) ──────────────────────────
bool TextureCompressor::s_hasASTCLDR = false;
bool TextureCompressor::s_hasETC2    = false;
static bool s_detected               = false;
static std::mutex s_initMutex;

// Cache de la table de taille ETC2 RGBA : 16 bytes par bloc 4×4
// glCompressedTexImage2D a besoin de la taille exacte
inline size_t etc2RGBACompressedSize(GLsizei w, GLsizei h) {
    // Chaque bloc 4×4 = 16 bytes pour ETC2 RGBA (EAC pour alpha)
    int bx = (w + 3) / 4;
    int by = (h + 3) / 4;
    return (size_t)bx * by * 16;
}

inline size_t astc6x6CompressedSize(GLsizei w, GLsizei h) {
    // ASTC 6×6 : blocs de 6×6 pixels = 16 bytes chacun
    int bx = (w + 5) / 6;
    int by = (h + 5) / 6;
    return (size_t)bx * by * 16;
}

// ─── Détection des formats compressés supportés ──────────────────────────
void TextureCompressor::detectCapabilities() {
    std::lock_guard<std::mutex> lock(s_initMutex);
    if (s_detected) return;

    const char* extensions = (const char*)glGetString(GL_EXTENSIONS);
    if (!extensions) {
        LOGE("glGetString(GL_EXTENSIONS) a retourné NULL !");
        return;
    }

    // ASTC LDR : extension optionnelle, présente sur ~95% des GPU Android 2022+
    // Snapdragon 8 Gen 1/2/3 : OUI. Mali-G715+ : OUI. Exynos 2200+ : OUI.
    s_hasASTCLDR = (strstr(extensions, "GL_KHR_texture_compression_astc_ldr") != nullptr);

    // ETC2 est OBLIGATOIRE dans OpenGL ES 3.0 (spécification Khronos)
    // Donc s_hasETC2 est toujours true si l'app tourne sur GLES3
    s_hasETC2 = true;

    bool hasASTCHDR = (strstr(extensions, "GL_OES_texture_compression_astc") != nullptr);

    LOGI("TextureCompressor capabilities:");
    LOGI("  ASTC LDR = %s", s_hasASTCLDR ? "OUI ✓" : "NON");
    LOGI("  ASTC HDR = %s", hasASTCHDR   ? "OUI ✓" : "NON");
    LOGI("  ETC2     = OUI ✓ (garanti GLES 3.0)");

    s_detected = true;
}

// ─── Filtrage rapide : doit-on compresser ? ──────────────────────────────
bool TextureCompressor::shouldCompress(
    GLenum internalformat,
    GLsizei width, GLsizei height,
    const void* data
) {
    // 1. Ne pas re-compresser une texture déjà compressée
    switch (internalformat) {
        case GL_COMPRESSED_RGBA8_ETC2_EAC:
        case GL_COMPRESSED_RGB8_ETC2:
        case GL_COMPRESSED_RGB8_PUNCHTHROUGH_ALPHA1_ETC2:
        case 0x93D0: // GL_COMPRESSED_RGBA_ASTC_4x4_KHR
        case 0x93D5: // GL_COMPRESSED_RGBA_ASTC_6x6_KHR
        case 0x93D9: // GL_COMPRESSED_RGBA_ASTC_8x8_KHR
            return false;
        default: break;
    }

    // 2. Seules les textures RGBA8/RGB8 non compressées sont candidates
    bool isRGBA = (internalformat == GL_RGBA8 || internalformat == GL_RGBA ||
                   internalformat == GL_RGBA4 || internalformat == GL_RGB5_A1);
    bool isRGB  = (internalformat == GL_RGB8  || internalformat == GL_RGB);
    if (!isRGBA && !isRGB) return false;

    // 3. ETC2 exige width et height multiples de 4
    if (width % 4 != 0 || height % 4 != 0) return false;

    // 4. Seuil minimum : inutile de compresser des icônes UI 16×16
    if (width < 64 || height < 64) return false;

    // 5. Données présentes
    if (!data) return false;

    return true;
}

// ─── Compression ETC2 via etcpak (API batch — recommandée) ──────────────
// EXPLICATION TECHNIQUE etcpak :
// etcpak traite des blocs de 4×4 pixels.
// L'API "batch" CompressEtc2Rgba() prend toute l'image d'un coup et est
// ~4× plus rapide que de traiter bloc par bloc, grâce aux auto-vectorisations
// et aux instructions NEON (arm64) / AVX2 (x86).
bool TextureCompressor::compressETC2(
    const uint8_t* src_rgba,
    GLsizei width, GLsizei height,
    std::vector<uint8_t>& dst_etc2
) {
    if (!src_rgba || width <= 0 || height <= 0) return false;

    const size_t compressed_size = etc2RGBACompressedSize(width, height);
    dst_etc2.resize(compressed_size);

    // ── API batch etcpak (la plus rapide) ──
    // CompressEtc2Rgba(src, dst, width, height, dither=false)
    // NEON est activé automatiquement sur arm64 par le compilateur
    // avec -march=armv8-a+simd (voir CMakeLists.txt)
    CompressEtc2Rgba(
        (const uint32_t*)src_rgba,   // pixels RGBA8 en uint32_t
        (uint64_t*)dst_etc2.data(),  // sortie : paires de uint64_t (16 bytes/bloc)
        (uint32_t)width,
        (uint32_t)height,
        false  // dither=false pour performance maximale
               // dither=true améliore la qualité sur gradients mais ×2 plus lent
    );

    LOGI("ETC2 compression: %dx%d → %zu bytes (%.1f MB → %.1f MB)",
         width, height, compressed_size,
         (float)(width * height * 4) / 1048576.0f,
         (float)compressed_size / 1048576.0f);
    return true;
}

// ─── Compression ASTC via astcenc (ARM ASTC Encoder) ────────────────────
// astcenc est l'encodeur ASTC officiel d'ARM.
// Il supporte plusieurs preset de qualité : FASTEST, FAST, MEDIUM, THOROUGH, EXHAUSTIVE
// Pour une compression à la volée, utiliser ASTCENC_PRE_FASTEST.
bool TextureCompressor::compressASTCIfAvailable(
    const uint8_t* src_rgba,
    GLsizei width, GLsizei height,
    std::vector<uint8_t>& dst_astc
) {
    if (!s_hasASTCLDR) return false;

    astcenc_config config;
    astcenc_error  status;

    // ASTC 6×6 : bon compromis qualité/taille (2.37 bits/pixel)
    status = astcenc_config_init(
        ASTCENC_PRF_LDR_SRGB,   // profil : LDR sRGB (textures couleur Minecraft)
        6, 6, 1,                 // block size : 6×6×1 (2D)
        ASTCENC_PRE_FASTEST,     // qualité preset
        0,                       // flags
        &config
    );

    if (status != ASTCENC_STS_SUCCESS) {
        LOGE("astcenc_config_init failed: %d", status);
        return false;
    }

    astcenc_context* ctx = nullptr;
    status = astcenc_context_alloc(&config, 1 /*thread_count*/, &ctx);
    if (status != ASTCENC_STS_SUCCESS) {
        LOGE("astcenc_context_alloc failed: %d", status);
        return false;
    }

    astcenc_image image;
    image.dim_x     = width;
    image.dim_y     = height;
    image.dim_z     = 1;
    image.data_type = ASTCENC_TYPE_U8;
    void* data_ptr  = (void*)src_rgba;
    image.data      = &data_ptr;

    const size_t out_size = astc6x6CompressedSize(width, height);
    dst_astc.resize(out_size);

    const astcenc_swizzle swizzle = {
        ASTCENC_SWZ_R, ASTCENC_SWZ_G, ASTCENC_SWZ_B, ASTCENC_SWZ_A
    };

    status = astcenc_compress_image(ctx, &image, &swizzle,
                                     dst_astc.data(), out_size,
                                     0 /*thread_index*/);

    astcenc_context_free(ctx);

    if (status != ASTCENC_STS_SUCCESS) {
        LOGE("astcenc_compress_image failed: %d", status);
        return false;
    }

    LOGI("ASTC 6×6 compression: %dx%d → %zu bytes (%.1f MB → %.1f MB)",
         width, height, out_size,
         (float)(width * height * 4) / 1048576.0f,
         (float)out_size / 1048576.0f);
    return true;
}

// ─── Interception principale de glTexImage2D ──────────────────────────────
// Cette fonction est appelée par InterceptLayer (Module 3) à chaque
// glTexImage2D intercepté depuis Java/GLES.
bool TextureCompressor::intercept(
    GLenum target, GLint level,
    GLint internalformat,
    GLsizei width, GLsizei height,
    GLenum format, GLenum type,
    const void* pixels
) {
    if (!shouldCompress((GLenum)internalformat, width, height, pixels)) {
        return false;
    }

    const size_t src_size = width * height * 4;
    std::vector<uint8_t> src_copy((const uint8_t*)pixels,
                                    (const uint8_t*)pixels + src_size);

    std::vector<uint8_t> compressed;
    GLenum compressedFormat;

    bool ok = false;
    if (s_hasASTCLDR) {
        ok = compressASTCIfAvailable(src_copy.data(), width, height, compressed);
        if (ok) compressedFormat = 0x93D5; // GL_COMPRESSED_RGBA_ASTC_6x6_KHR
    }
    if (!ok) {
        ok = compressETC2(src_copy.data(), width, height, compressed);
        if (ok) compressedFormat = GL_COMPRESSED_RGBA8_ETC2_EAC;
    }

    if (!ok) return false;

    glCompressedTexImage2D(
        target, level,
        compressedFormat,
        width, height,
        0,
        (GLsizei)compressed.size(),
        compressed.data()
    );

    GLenum err = glGetError();
    if (err != GL_NO_ERROR) {
        LOGE("glCompressedTexImage2D error: 0x%X (format=0x%X %dx%d)",
             err, compressedFormat, width, height);
        return false;
    }

    return true;
}

// ─── Compression asynchrone ──────────────────────────────────────────────
std::future<CompressedResult> TextureCompressor::compressAsync(
    const uint8_t* src_rgba,
    GLsizei width, GLsizei height,
    GLenum preferredFormat
) {
    std::vector<uint8_t> src_copy(src_rgba, src_rgba + width * height * 4);

    return std::async(std::launch::async, [=, src = std::move(src_copy)]() mutable {
        CompressedResult result;
        result.width  = width;
        result.height = height;

        bool ok = false;
        if (preferredFormat == 0x93D5 && s_hasASTCLDR) {
            ok = compressASTCIfAvailable(src.data(), width, height, result.data);
            if (ok) result.format = 0x93D5;
        }
        if (!ok) {
            ok = compressETC2(src.data(), width, height, result.data);
            if (ok) result.format = GL_COMPRESSED_RGBA8_ETC2_EAC;
        }

        result.success = ok;
        return result;
    });
}

} // namespace NativeGLEngine
```

### 6.6 CMakeLists.txt — Intégration etcpak et astcenc

```cmake
# ─── etcpak (ETC2 ultra-rapide, build direct source) ───
# Note : etcpak a migré de Meson vers CMake en 2024
add_library(etcpak_lib STATIC
    deps/etcpak/ProcessRGB.cpp
    deps/etcpak/ProcessDxtc.cpp
)
target_include_directories(etcpak_lib PUBLIC deps/etcpak)
target_compile_options(etcpak_lib PRIVATE -O3 -ffast-math)

# ─── astcenc (ARM ASTC Encoder) ───
# ISA_NEON=ON active les intrinsics NEON ARM pour arm64
add_subdirectory(deps/astcenc EXCLUDE_FROM_ALL)
# La cible s'appelle "astcenc-neon-static" pour arm64

# ─── Bibliothèque principale ───
add_library(NativeGLEngine SHARED
    texture_compressor.cpp
    # ... autres fichiers ...
)

target_link_libraries(NativeGLEngine
    etcpak_lib
    astcenc-neon-static
    android log EGL GLESv3 vulkan jnigraphics
)

target_include_directories(NativeGLEngine PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}
    ${CMAKE_CURRENT_SOURCE_DIR}/deps/etcpak
    ${CMAKE_CURRENT_SOURCE_DIR}/deps/astcenc/Source
    ${CMAKE_CURRENT_SOURCE_DIR}/deps/fp16/include
)
```

### 6.7 Métriques attendues — Module 6

| Opération | RGBA8 brut (avant) | ETC2 (après) | ASTC 6×6 (après+) |
|---|---|---|---|
| RAM GPU par atlas 4096×4096 | 67 MB | **16 MB** | **7.3 MB** |
| RAM GPU totale (×10 atlas) | ~670 MB | **~160 MB** | **~73 MB** |
| Temps compression 4096×4096 (2 threads) | 0ms (pas de compression) | ~300ms async | ~500ms async |
| Impact FPS (compression en background) | N/A | **Zéro** (async) | **Zéro** (async) |
| Qualité visuelle | Parfaite | ~PSNR 35 dB (bonne) | ~PSNR 38 dB (très bonne) |

---

## 🗑️ MODULE 7 — OffHeapArena (Zéro GC Java — Zéro Freeze)

### 7.1 Le problème du Garbage Collector Java sur Android

Le GC Android (ART depuis Lollipop, utilisant CMS puis G1 partiellement) fait des **"stop-the-world"** partiels :
- Chaque GC bloque le jeu **5 à 50ms**
- Avec 60 mods, le heap Java grossit à ~700 MB → GC toutes les **~5 secondes**
- Résultat : **12 freezes/minute** visibles → jeu injouable

**La solution** : déplacer les gros buffers Java vers la **mémoire native** (off-heap). Le GC ne voit plus ces données → il se déclenche moins souvent → moins de freezes.

**Mécanisme technique** :
- Java : `ByteBuffer.allocateDirect(n)` alloue en mémoire native, non gérée par GC
- C++ : `malloc()` / VMA alloue hors heap Java également
- JNI : `NewDirectByteBuffer()` crée un wrapper Java d'un buffer natif C++

### 7.2 Fonctionnement détaillé de DirectByteBuffer

```
Heap Java                     Mémoire Native (Off-Heap)
┌─────────────────────┐       ┌──────────────────────────┐
│ DirectByteBuffer    │       │                          │
│  ┌───────────────┐  │       │  malloc() / VMA          │
│  │ address: 0x.. │──┼──────►│  [données réelles]       │
│  │ capacity: 64MB│  │       │  64 MB                   │
│  │ Cleaner ref   │  │       │                          │
│  └───────────────┘  │       └──────────────────────────┘
│  (petit objet ~50B) │
│  GC gère uniquement │       GC ne voit PAS ces 64 MB !
│  le wrapper Java    │       → Pression GC réduite
└─────────────────────┘
```

**Points importants** :
- `NewDirectByteBuffer(addr, capacity)` : crée un wrapper Java sans copie
- `GetDirectBufferAddress(env, buffer)` : récupère le pointeur natif depuis C++
- La mémoire doit rester valide tant que le ByteBuffer Java existe
- La libération est manuelle côté C++ (pas gérée par GC)

### 7.3 off_heap_arena.h

```cpp
// src/main/cpp/off_heap_arena.h
#pragma once
#include <jni.h>
#include <cstdint>
#include <cstddef>
#include <mutex>
#include <unordered_map>
#include <atomic>

namespace NativeGLEngine {

struct ArenaBlock {
    void*    ptr;
    size_t   size;
    int64_t  allocTime;
    bool     inUse;
    char     tag[32];
};

class OffHeapArena {
public:
    static bool init(size_t poolSizeMB = 150);
    static void destroy();
    static jobject allocate(JNIEnv* env, size_t sizeBytes, const char* tag = "unnamed");
    static void free(JNIEnv* env, jobject directBuffer);
    static void* getNativePtr(JNIEnv* env, jobject directBuffer);
    static void runLeakDetector();
    static size_t getTotalAllocated();
    static size_t getTotalFree();
    static int    getActiveBlocks();

private:
    static uint8_t*  s_poolBase;
    static size_t    s_poolSize;
    static std::atomic<size_t> s_offset;
    static std::unordered_map<void*, ArenaBlock> s_blocks;
    static std::mutex s_mutex;
};

} // namespace NativeGLEngine
```

### 7.4 off_heap_arena.cpp

```cpp
// src/main/cpp/off_heap_arena.cpp
#include "off_heap_arena.h"
#include <android/log.h>
#include <sys/mman.h>
#include <cstring>
#include <chrono>

#define LOG_TAG "NativeGL-OffHeap"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO,  LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace NativeGLEngine {

uint8_t*             OffHeapArena::s_poolBase = nullptr;
size_t               OffHeapArena::s_poolSize = 0;
std::atomic<size_t>  OffHeapArena::s_offset   = 0;
std::unordered_map<void*, ArenaBlock> OffHeapArena::s_blocks;
std::mutex           OffHeapArena::s_mutex;

bool OffHeapArena::init(size_t poolSizeMB) {
    s_poolSize = poolSizeMB * 1024 * 1024;

    // ── Stratégie : mmap anonyme (préféré pour les gros pools) ──
    // mmap est plus efficace que malloc pour les gros blocs :
    // - Pas de fragmentation interne
    // - L'OS n'alloue réellement la mémoire physique qu'à la première utilisation
    // - On peut donner des hints via madvise()
    s_poolBase = (uint8_t*)mmap(
        nullptr,
        s_poolSize,
        PROT_READ | PROT_WRITE,
        MAP_PRIVATE | MAP_ANONYMOUS,
        -1, 0
    );

    if (s_poolBase == MAP_FAILED) {
        LOGE("mmap(%zu MB) failed: %s", poolSizeMB, strerror(errno));
        s_poolBase = nullptr;
        return false;
    }

    // ── MADV_SEQUENTIAL + MADV_WILLNEED : hints noyau ──
    madvise(s_poolBase, s_poolSize, MADV_SEQUENTIAL);
    madvise(s_poolBase, s_poolSize, MADV_WILLNEED);

    s_offset.store(0);
    LOGI("OffHeapArena initialisé : %zu MB @ %p", poolSizeMB, (void*)s_poolBase);
    return true;
}

void OffHeapArena::destroy() {
    std::lock_guard<std::mutex> lock(s_mutex);
    if (s_poolBase) {
        munmap(s_poolBase, s_poolSize);
        s_poolBase = nullptr;
        LOGI("OffHeapArena libéré (%zu MB)", s_poolSize / 1048576);
    }
    s_blocks.clear();
}

jobject OffHeapArena::allocate(JNIEnv* env, size_t sizeBytes, const char* tag) {
    // Alignement 64 bytes (optimal pour NEON et cache lines)
    const size_t alignment = 64;
    sizeBytes = (sizeBytes + alignment - 1) & ~(alignment - 1);

    void* ptr = nullptr;

    // ── Bump allocator O(1) sur le pool pré-alloué ──
    if (s_poolBase) {
        size_t offset = s_offset.fetch_add(sizeBytes);
        if (offset + sizeBytes <= s_poolSize) {
            ptr = s_poolBase + offset;
        }
    }

    // ── Fallback : malloc aligné si pool plein ──
    if (!ptr) {
        ptr = aligned_alloc(alignment, sizeBytes);
        if (!ptr) {
            LOGE("OffHeap allocation %zu bytes failed !", sizeBytes);
            return nullptr;
        }
        LOGW("Pool plein ! Fallback malloc pour %s (%zu bytes)", tag, sizeBytes);
    }

    // ── Créer le wrapper DirectByteBuffer Java (ZÉRO COPIE) ──
    jobject directBuf = env->NewDirectByteBuffer(ptr, (jlong)sizeBytes);
    if (!directBuf) {
        LOGE("NewDirectByteBuffer(%p, %zu) failed", ptr, sizeBytes);
        if (ptr < (void*)s_poolBase || ptr >= (void*)(s_poolBase + s_poolSize)) {
            ::free(ptr);
        }
        return nullptr;
    }

    // ── Enregistrement pour tracking / leak detection ──
    {
        std::lock_guard<std::mutex> lock(s_mutex);
        ArenaBlock block;
        block.ptr       = ptr;
        block.size      = sizeBytes;
        block.allocTime = std::chrono::duration_cast<std::chrono::milliseconds>(
            std::chrono::steady_clock::now().time_since_epoch()
        ).count();
        block.inUse     = true;
        strncpy(block.tag, tag, sizeof(block.tag) - 1);
        s_blocks[ptr] = block;
    }

    LOGI("OffHeap alloc: %s → %zu bytes @ %p", tag, sizeBytes, ptr);
    return directBuf;
}

void OffHeapArena::free(JNIEnv* env, jobject directBuffer) {
    if (!directBuffer) return;
    void* ptr = env->GetDirectBufferAddress(directBuffer);
    if (!ptr) { LOGE("GetDirectBufferAddress failed !"); return; }

    std::lock_guard<std::mutex> lock(s_mutex);
    auto it = s_blocks.find(ptr);
    if (it != s_blocks.end()) {
        it->second.inUse = false;
        LOGI("OffHeap free: %s @ %p (%zu bytes)", it->second.tag, ptr, it->second.size);
        if (ptr < (void*)s_poolBase || ptr >= (void*)(s_poolBase + s_poolSize)) {
            ::free(ptr);
            s_blocks.erase(it);
        }
    }
}

void OffHeapArena::runLeakDetector() {
    std::lock_guard<std::mutex> lock(s_mutex);
    int64_t now = std::chrono::duration_cast<std::chrono::milliseconds>(
        std::chrono::steady_clock::now().time_since_epoch()
    ).count();
    for (auto& [ptr, block] : s_blocks) {
        if (block.inUse && (now - block.allocTime) > 60000) {
            LOGW("POTENTIAL LEAK: %s @ %p (%zu bytes) alive for %lld ms",
                 block.tag, ptr, block.size, (now - block.allocTime));
        }
    }
}

void* OffHeapArena::getNativePtr(JNIEnv* env, jobject directBuffer) {
    return env->GetDirectBufferAddress(directBuffer);
}

} // namespace NativeGLEngine
```

### 7.5 Côté Java — Utilisation de l'OffHeapArena

```java
public class NativeBufferManager {
    static { System.loadLibrary("NativeGLEngine"); }

    private static native ByteBuffer nativeAllocate(long sizeBytes, String tag);
    private static native void nativeFree(ByteBuffer buffer);

    private static final int CHUNK_BUFFER_SIZE = 2 * 1024 * 1024; // 2 MB par chunk
    private static final Queue<ByteBuffer> chunkPool = new ConcurrentLinkedQueue<>();

    public static ByteBuffer acquireChunkBuffer() {
        ByteBuffer buf = chunkPool.poll();
        if (buf == null) {
            buf = nativeAllocate(CHUNK_BUFFER_SIZE, "ChunkData");
        }
        buf.clear();
        return buf;
    }

    public static void releaseChunkBuffer(ByteBuffer buf) {
        if (buf != null && buf.isDirect()) {
            chunkPool.offer(buf);
        }
    }

    public static void freeAllBuffers() {
        ByteBuffer buf;
        while ((buf = chunkPool.poll()) != null) {
            nativeFree(buf);
        }
    }
}
```

### 7.6 Métriques attendues — Module 7

| Métrique | Sans off-heap | Avec off-heap |
|---|---|---|
| GC frequency (60 mods actifs) | Toutes les 5s | **Toutes les 60s** (×12 moins) |
| Durée freeze GC | 20-50ms | **5-10ms** (GC plus petit) |
| RAM Java heap sous pression | ~700 MB | **~450 MB** (-35%) |
| RAM native off-heap utilisée | 0 MB | ~150 MB (hors GC) |
| Pertes de frames/minute dues GC | ~12 | **~1** |

---

## 📐 MODULE 8 — VertexQuantizer FP16 (Gain Mémoire + Bande Passante)

### 8.1 Contexte : pourquoi FP16 pour les vertices ?

Les vertex buffers de Minecraft + 60 mods stockent positions, UVs, normales en **FP32** (4 bytes/float). Passer en **FP16** (2 bytes/float) divise la taille par 2.

D'après la documentation Android officielle ("Vertex Data Management") :
- Les implémentations typiques utilisent FP32 pour tout → opportunité majeure
- Le passage FP16 peut améliorer la bande passante mémoire de vertex jusqu'à **50%**
- Réduction de la contention sur le bus mémoire (partagé CPU/GPU sur mobile)

**Attention** : les coordonnées UV en FP16 ont des problèmes de précision pour les textures > 1024×1024.
→ **Solution** : utiliser UNORM16 (uint16_t normalisé [0,65535]) pour les UVs.

### 8.2 Tableau des précisions FP16 vs FP32

| Attribut vertex | FP32 (avant) | FP16 (après) | Risque précision | Solution |
|---|---|---|---|---|
| Position XYZ | 4×3 = 12 bytes | 2×3 = 6 bytes | ±65504 max | Clamp coords ou FP32 si > 1000 blocs |
| UV (texture coord) | 4×2 = 8 bytes | UNORM16 × 2 = 4 bytes | Précision sub-pixel | UNORM16 (meilleur que FP16 pour UVs) |
| Normale XYZ | 4×3 = 12 bytes | INT8 ×3 = 3 bytes | ±127 normalisé | Parfait (normales normalisées) |
| Couleur RGBA | 4 bytes (RGBA8) | 4 bytes (inchangé) | Aucun | Déjà optimal |
| **Total par vertex** | **36 bytes** | **17 bytes** | | **÷2.1** |

### 8.3 vertex_quantizer.h

```cpp
// src/main/cpp/vertex_quantizer.h
#pragma once
#include <cstdint>
#include <cstddef>
#include <arm_neon.h>

namespace NativeGLEngine {

struct VertexFP32 {
    float x, y, z;
    float u, v;
    float nx, ny, nz;
    uint8_t r, g, b, a;
};

struct VertexQuantized {
    uint16_t x, y, z;   // Position FP16
    uint16_t u, v;       // UV UNORM16
    int8_t   nx, ny, nz; // Normale INT8
    uint8_t  _pad;
    uint8_t  r, g, b, a;
    // Total : 16 bytes (vs 36 avant)
};

class VertexQuantizer {
public:
    static void quantize(
        const VertexFP32* src,
        VertexQuantized*  dst,
        size_t            count
    );
    static void quantizeNEON(
        const float* src_positions,
        uint16_t*    dst_fp16,
        size_t       count
    );
    static uint16_t f32_to_f16(float f);
    static uint16_t f32_to_unorm16(float f);
    static bool checkFP16Safe(const VertexFP32* vertices, size_t count);
};

} // namespace NativeGLEngine
```

### 8.4 vertex_quantizer.cpp — NEON arm64

```cpp
// src/main/cpp/vertex_quantizer.cpp
// Conversion FP32→FP16 via intrinsics NEON arm64
// Référence ARM : https://developer.android.com/games/optimize/vertex-data-management

#include "vertex_quantizer.h"
#include <android/log.h>
#include <cmath>
#include <algorithm>
#include "fp16/include/fp16.h"

#define LOG_TAG "NativeGL-VertexQ"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)

namespace NativeGLEngine {

uint16_t VertexQuantizer::f32_to_f16(float f) {
    return fp16_ieee_from_fp32_value(f);
}

// IMPORTANT : FP16 a une précision insuffisante pour les UVs > 1024px
// UNORM16 = float * 65535, donne une précision uniforme
// Compatible avec GL_UNSIGNED_SHORT + GL_TRUE (normalized) dans glVertexAttribPointer
uint16_t VertexQuantizer::f32_to_unorm16(float f) {
    float clamped = std::max(0.0f, std::min(1.0f, f));
    return (uint16_t)(clamped * 65535.0f + 0.5f);
}

// ─── Conversion NEON : FP32[] → FP16[] (positions XYZ) ───────────────────
// NEON sur arm64 a une instruction native vcvt_f16_f32 :
// - Convertit 4 floats en 4 half-floats en UNE instruction
// - Latence : 2 cycles, débit : 1/cycle sur Cortex-X3
// - 4× plus rapide que la conversion scalaire
void VertexQuantizer::quantizeNEON(
    const float* src_positions,
    uint16_t*    dst_fp16,
    size_t       float_count
) {
    size_t i = 0;

    // ── Traitement par blocs de 8 floats (= 2 registres NEON) ──
    for (; i + 8 <= float_count; i += 8) {
        float32x4_t f32_0 = vld1q_f32(src_positions + i);
        float32x4_t f32_1 = vld1q_f32(src_positions + i + 4);
        float16x4_t f16_0 = vcvt_f16_f32(f32_0);
        float16x4_t f16_1 = vcvt_f16_f32(f32_1);
        vst1_f16((__fp16*)(dst_fp16 + i),     f16_0);
        vst1_f16((__fp16*)(dst_fp16 + i + 4), f16_1);
    }

    // ── Traitement par blocs de 4 floats ──
    for (; i + 4 <= float_count; i += 4) {
        float32x4_t f32 = vld1q_f32(src_positions + i);
        float16x4_t f16 = vcvt_f16_f32(f32);
        vst1_f16((__fp16*)(dst_fp16 + i), f16);
    }

    // ── Reste scalaire ──
    for (; i < float_count; i++) {
        dst_fp16[i] = f32_to_f16(src_positions[i]);
    }
}

void VertexQuantizer::quantize(
    const VertexFP32* src,
    VertexQuantized*  dst,
    size_t            count
) {
    if (!src || !dst || count == 0) return;

    for (size_t i = 0; i < count; ++i) {
        const VertexFP32&  s = src[i];
        VertexQuantized&   d = dst[i];

        // ── Position : FP32 → FP16 ──
        d.x = fp16_ieee_from_fp32_value(s.x);
        d.y = fp16_ieee_from_fp32_value(s.y);
        d.z = fp16_ieee_from_fp32_value(s.z);

        // ── UV : FP32 → UNORM16 (meilleure précision que FP16 pour UVs) ──
        d.u = f32_to_unorm16(s.u);
        d.v = f32_to_unorm16(s.v);

        // ── Normale : FP32 → INT8 normalisé (-127..+127) ──
        d.nx = (int8_t)(s.nx * 127.0f);
        d.ny = (int8_t)(s.ny * 127.0f);
        d.nz = (int8_t)(s.nz * 127.0f);
        d._pad = 0;

        // ── Couleur : RGBA8 inchangée ──
        d.r = s.r; d.g = s.g; d.b = s.b; d.a = s.a;
    }

    LOGI("VertexQuantizer: %zu vertices quantifiés (%zu bytes → %zu bytes, ÷%.1f)",
         count,
         count * sizeof(VertexFP32),
         count * sizeof(VertexQuantized),
         (float)sizeof(VertexFP32) / sizeof(VertexQuantized));
}

bool VertexQuantizer::checkFP16Safe(const VertexFP32* vertices, size_t count) {
    const float FP16_MAX = 65504.0f;
    for (size_t i = 0; i < count; i++) {
        if (std::abs(vertices[i].x) > FP16_MAX ||
            std::abs(vertices[i].y) > FP16_MAX ||
            std::abs(vertices[i].z) > FP16_MAX) {
            return false;
        }
    }
    return true;
}

} // namespace NativeGLEngine
```

### 8.5 Déclaration OpenGL ES des attributs quantifiés

```cpp
// ── Position : FP16 (GL_HALF_FLOAT) ──
glVertexAttribPointer(
    0, 3, GL_HALF_FLOAT, GL_FALSE,
    sizeof(VertexQuantized),
    (void*)offsetof(VertexQuantized, x)
);
glEnableVertexAttribArray(0);

// ── UV : UNORM16 (GL_UNSIGNED_SHORT, normalisé) ──
glVertexAttribPointer(
    1, 2, GL_UNSIGNED_SHORT, GL_TRUE,
    sizeof(VertexQuantized),
    (void*)offsetof(VertexQuantized, u)
);
glEnableVertexAttribArray(1);

// ── Normale : INT8 normalisé ──
glVertexAttribPointer(
    2, 3, GL_BYTE, GL_TRUE,
    sizeof(VertexQuantized),
    (void*)offsetof(VertexQuantized, nx)
);
glEnableVertexAttribArray(2);

// ── Shader GLSL : aucun changement nécessaire ! ──
// Le GPU normalise automatiquement les entiers vers float
// layout(location=0) in vec3 position;  // reçoit fp16, traité comme float
// layout(location=1) in vec2 uv;        // reçoit UNORM16 normalisé [0,1]
// layout(location=2) in vec3 normal;    // reçoit INT8 normalisé [-1,1]
```

### 8.6 Métriques attendues — Module 8

| Métrique | FP32 (avant) | FP16/UNORM16 (après) |
|---|---|---|
| Taille par vertex | 36 bytes | **16 bytes** |
| RAM vertex buffer 1M vertices | 36 MB | **16 MB** |
| RAM totale vertices (terrain+entités) | ~250 MB | **~110 MB** |
| Bande passante GPU vertex fetch | baseline | **-50-55%** |

---

## ⚡ MODULE 9 — SIMDMathEngine (Frustum Culling NEON ×10)

### 9.1 Concept : Frustum Culling SIMD

Le **frustum culling** élimine les objets hors du champ de vision AVANT de les envoyer au GPU. Avec 60 mods, Minecraft teste ~5000-10000 entités/chunks par frame. En scalaire : ~3ms/frame. Avec NEON : **~0.3ms/frame**.

**Principe mathématique** : Un frustum est défini par 6 plans. Une sphère (centre + rayon) est visible si elle est du côté positif des 6 plans :
```
∀ plan P : dot(P.normal, sphere.center) + P.d > -sphere.radius
```

NEON permet de tester **4 sphères contre 1 plan en UNE instruction** (`vmlaq_f32`), soit **4×6 = 24 tests en ~6 cycles** au lieu de **24 cycles scalaires**.

### 9.2 simd_math_engine.h

```cpp
// src/main/cpp/simd_math_engine.h
#pragma once
#include <arm_neon.h>
#include <cstdint>
#include <cstddef>

namespace NativeGLEngine {

struct FrustumPlane { float nx, ny, nz, d; };
struct Frustum { FrustumPlane planes[6]; };
struct BoundingSphere { float cx, cy, cz, radius; };

class SIMDMathEngine {
public:
    static void frustumCullSpheres(
        const Frustum& frustum,
        const BoundingSphere* spheres,
        size_t count,
        bool* results
    );
    static void matMul4x4NEON(const float* a, const float* b, float* out);
    static void transformVec4ArrayNEON(
        const float* matrix,
        const float* vectors,
        float* out,
        size_t count
    );
    static void extractFrustumFromMatrix(const float* viewProj, Frustum& frustum);
};

} // namespace NativeGLEngine
```

### 9.3 simd_math_engine.cpp — NEON arm64

```cpp
// src/main/cpp/simd_math_engine.cpp
#include "simd_math_engine.h"
#include <cstring>
#include <cmath>

namespace NativeGLEngine {

// ─── Frustum Culling NEON : 4 sphères × 6 plans ───────────────────────────
// Pour chaque plan P et chaque sphère S :
//   dist = dot(P.normal, S.center) + P.d
//   visible = (dist >= -S.radius)
//
// NEON permet de calculer dist pour 4 sphères en parallèle :
//   float32x4_t cx = {S0.cx, S1.cx, S2.cx, S3.cx}
//   float32x4_t dist = vmlaq_n_f32(vmlaq_n_f32(vmulq_n_f32(cx, P.nx), cy, P.ny), cz, P.nz)
void SIMDMathEngine::frustumCullSpheres(
    const Frustum& frustum,
    const BoundingSphere* spheres,
    size_t count,
    bool* results
) {
    size_t i = 0;

    // ── Traitement par blocs de 4 sphères (NEON) ──
    for (; i + 4 <= count; i += 4) {
        const BoundingSphere& s0 = spheres[i];
        const BoundingSphere& s1 = spheres[i+1];
        const BoundingSphere& s2 = spheres[i+2];
        const BoundingSphere& s3 = spheres[i+3];

        float32x4_t cx = { s0.cx, s1.cx, s2.cx, s3.cx };
        float32x4_t cy = { s0.cy, s1.cy, s2.cy, s3.cy };
        float32x4_t cz = { s0.cz, s1.cz, s2.cz, s3.cz };
        float32x4_t r  = { s0.radius, s1.radius, s2.radius, s3.radius };
        float32x4_t neg_r = vnegq_f32(r);

        uint32x4_t visible = vdupq_n_u32(0xFFFFFFFF);

        for (int p = 0; p < 6; p++) {
            const FrustumPlane& plane = frustum.planes[p];
            float32x4_t dist = vdupq_n_f32(plane.d);
            dist = vmlaq_n_f32(dist, cx, plane.nx);
            dist = vmlaq_n_f32(dist, cy, plane.ny);
            dist = vmlaq_n_f32(dist, cz, plane.nz);
            uint32x4_t inside = vcgeq_f32(dist, neg_r);
            visible = vandq_u32(visible, inside);
        }

        uint32_t mask[4];
        vst1q_u32(mask, visible);
        results[i]   = (mask[0] != 0);
        results[i+1] = (mask[1] != 0);
        results[i+2] = (mask[2] != 0);
        results[i+3] = (mask[3] != 0);
    }

    // ── Reste scalaire ──
    for (; i < count; i++) {
        const BoundingSphere& s = spheres[i];
        bool vis = true;
        for (int p = 0; p < 6 && vis; p++) {
            const FrustumPlane& plane = frustum.planes[p];
            float dist = plane.nx * s.cx + plane.ny * s.cy + plane.nz * s.cz + plane.d;
            vis = (dist >= -s.radius);
        }
        results[i] = vis;
    }
}

// ─── Multiplication de matrices 4×4 via NEON ──────────────────────────────
void SIMDMathEngine::matMul4x4NEON(const float* a, const float* b, float* out) {
    float32x4_t b_col0 = vld1q_f32(b);
    float32x4_t b_col1 = vld1q_f32(b + 4);
    float32x4_t b_col2 = vld1q_f32(b + 8);
    float32x4_t b_col3 = vld1q_f32(b + 12);

    for (int row = 0; row < 4; row++) {
        float32x4_t a_row = vld1q_f32(a + row * 4);
        float32x4_t result = vmulq_laneq_f32(b_col0, a_row, 0);
        result = vmlaq_laneq_f32(result, b_col1, a_row, 1);
        result = vmlaq_laneq_f32(result, b_col2, a_row, 2);
        result = vmlaq_laneq_f32(result, b_col3, a_row, 3);
        vst1q_f32(out + row * 4, result);
    }
}

// ─── Extraction du frustum depuis la matrice VP ────────────────────────────
// Algorithme de Gribb & Hartmann (1998 — référence standard)
void SIMDMathEngine::extractFrustumFromMatrix(const float* vp, Frustum& f) {
    f.planes[0] = { vp[3]+vp[0], vp[7]+vp[4], vp[11]+vp[8],  vp[15]+vp[12] };
    f.planes[1] = { vp[3]-vp[0], vp[7]-vp[4], vp[11]-vp[8],  vp[15]-vp[12] };
    f.planes[2] = { vp[3]+vp[1], vp[7]+vp[5], vp[11]+vp[9],  vp[15]+vp[13] };
    f.planes[3] = { vp[3]-vp[1], vp[7]-vp[5], vp[11]-vp[9],  vp[15]-vp[13] };
    f.planes[4] = { vp[3]+vp[2], vp[7]+vp[6], vp[11]+vp[10], vp[15]+vp[14] };
    f.planes[5] = { vp[3]-vp[2], vp[7]-vp[6], vp[11]-vp[10], vp[15]-vp[14] };

    for (int i = 0; i < 6; i++) {
        float len = std::sqrt(f.planes[i].nx * f.planes[i].nx +
                               f.planes[i].ny * f.planes[i].ny +
                               f.planes[i].nz * f.planes[i].nz);
        if (len > 1e-6f) {
            f.planes[i].nx /= len; f.planes[i].ny /= len;
            f.planes[i].nz /= len; f.planes[i].d  /= len;
        }
    }
}

} // namespace NativeGLEngine
```

### 9.4 Métriques attendues — Module 9

| Métrique | Scalaire (avant) | NEON (après) |
|---|---|---|
| Frustum cull 10000 sphères | ~3ms/frame | **~0.3ms/frame** (×10) |
| MatMul 4×4 | ~20ns | **~5ns** (×4) |
| Throughput (sphères/sec) | ~3.3M | **~33M** |

---

## 🎨 MODULE 10 — DrawCallBatcher (Create + Mods ÷10)

### 10.1 Le problème des draw calls

Avec Create + 60 mods, Minecraft génère **800–1500 draw calls par frame**. Chaque draw call :
1. Envoie une commande au driver GPU (overhead CPU ~0.01-0.1ms)
2. Potentiellement change l'état OpenGL (shader, texture, blend mode)
3. Crée une barrière pipeline sur le GPU (flush partial)

**Sur mobile TBR**, les draw calls excessifs sont encore plus coûteux :
- Chaque changement d'état peut forcer un flush du tile buffer
- Sur Adreno 740 : >500 draw calls/frame = dégradation visible

### 10.2 Stratégies de batching

**Technique 1 — Instanced Rendering** (Module 10 principal)
- `glDrawElementsInstanced(mode, count, type, indices, instanceCount)`
- Un seul draw call pour N instances du même mesh
- Les transforms sont passées via un Uniform Buffer Object (UBO)
- Disponible depuis GLES 3.0 (100% Android 2022+)

**Technique 2 — Texture Atlas**
- Regrouper plusieurs textures en un seul atlas 4096×4096
- Évite les changements de texture binding entre draw calls

**Technique 3 — Multi-Draw Indirect** (GLES 3.1+)
- `glMultiDrawElementsIndirectEXT` : N draw calls en UN
- Paramètres dans un buffer GPU (zéro overhead CPU)
- Disponible sur ~85% des Android 2022+

### 10.3 draw_call_batcher.cpp — Instanced Rendering

```cpp
// src/main/cpp/draw_call_batcher.cpp
// Regroupement de draw calls via instanced rendering (GLES 3.0)
// Référence : https://android-developers.googleblog.com/2015/05/game-performance-geometry-instancing.html

#include "draw_call_batcher.h"
#include <GLES3/gl3.h>
#include <android/log.h>
#include <unordered_map>
#include <vector>
#include <cstring>

#define LOG_TAG "NativeGL-Batcher"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)

namespace NativeGLEngine {

// ─── Structure d'une instance (données par instance dans le UBO) ────────
// Passée au vertex shader via gl_InstanceID
struct InstanceData {
    float modelMatrix[16];  // Matrice model 4×4 (64 bytes)
    float color[4];          // Tint RGBA (16 bytes)
    // Total : 80 bytes/instance
    // UBO max typique : 64 KB → max 819 instances/UBO
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

    for (auto& [key, batch] : s_batches) {
        if (batch.instances.empty()) continue;
        int instanceCount = (int)batch.instances.size();

        // ── Mettre à jour le UBO avec les données d'instance ──
        if (batch.instanceUBO == 0) glGenBuffers(1, &batch.instanceUBO);
        glBindBuffer(GL_UNIFORM_BUFFER, batch.instanceUBO);

        size_t uboSize = instanceCount * sizeof(InstanceData);
        glBufferData(GL_UNIFORM_BUFFER, uboSize, nullptr, GL_DYNAMIC_DRAW);

        // glMapBufferRange : accès direct au buffer GPU (zéro copie supplémentaire)
        void* ptr = glMapBufferRange(
            GL_UNIFORM_BUFFER, 0, uboSize,
            GL_MAP_WRITE_BIT | GL_MAP_INVALIDATE_BUFFER_BIT
        );
        if (ptr) {
            memcpy(ptr, batch.instances.data(), uboSize);
            glUnmapBuffer(GL_UNIFORM_BUFFER);
        }

        glBindBufferBase(GL_UNIFORM_BUFFER, 0, batch.instanceUBO);

        // ── Draw call unique pour toutes les instances ──
        glUseProgram(batch.shaderProgram);
        glBindTexture(GL_TEXTURE_2D, batch.texture);
        glBindVertexArray(batch.vao);

        // UNE seule commande pour N instances !
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

// ─── Vertex Shader GLSL correspondant ─────────────────────────────────────
/*
    #version 300 es
    layout(location = 0) in vec3 position;
    layout(location = 1) in vec2 uv;

    layout(std140) uniform InstanceBlock {
        mat4 modelMatrix[819];   // MAX_INSTANCES = 64KB / 80bytes = 819
        vec4 instanceColor[819];
    };

    uniform mat4 viewProjMatrix;
    out vec2 fragUV;
    out vec4 fragColor;

    void main() {
        // gl_InstanceID : index de l'instance actuelle (0 à instanceCount-1)
        mat4 model = modelMatrix[gl_InstanceID];
        vec4 color = instanceColor[gl_InstanceID];
        gl_Position = viewProjMatrix * model * vec4(position, 1.0);
        fragUV    = uv;
        fragColor = color;
    }
*/

} // namespace NativeGLEngine
```

### 10.4 Métriques attendues — Module 10

| Métrique | Avant (individuel) | Après (batché) |
|---|---|---|
| Draw calls Create scene | 800–1500 | **80–150** (÷10) |
| CPU overhead draw calls | ~8ms/frame | **~0.8ms/frame** |
| GPU state changes | ~800/frame | **~80/frame** |
| Gains FPS (SD 8 Gen 2) | 25-35 FPS | **45-60 FPS** |

---

## 🔌 MODULE 11 — AHardwareBuffer (Upload Texture Zero-Copy)

### 11.1 Contexte : le problème de glTexSubImage2D

`glTexSubImage2D` est la fonction standard pour mettre à jour une texture. Sur mobile, elle déclenche une **copie CPU→GPU** via le bus mémoire :
- 512×512 RGBA8 : ~4ms
- 1024×1024 RGBA8 : ~15ms
- 10 textures/frame : **~80ms de stalls** → impossibilité de tenir 60 FPS

**AHardwareBuffer** (Android 8.0 / API 26+) permet la **mémoire partagée CPU/GPU** :
- Alloue un buffer dans la mémoire unifiée (UMA architecture mobile)
- CPU écrit, GPU lit SANS COPIE via EGL image
- Latence : ~0.1-0.3ms (100× plus rapide)

### 11.2 Architecture zero-copy

```
CPU                          GPU
 │                            │
 │    ┌──────────────────┐   │
 └───►│  AHardwareBuffer │◄──┘
      │  (mémoire unifiée│
      │  CPU + GPU UMA)  │
      └──────────────────┘
           │
           ▼
      EGLImageKHR
           │
           ▼
      GL Texture (glEGLImageTargetTexture2DOES)
```

### 11.3 ahardware_buffer_manager.h

```cpp
// src/main/cpp/ahardware_buffer_manager.h
#pragma once
#include <android/hardware_buffer.h>
#include <EGL/egl.h>
#include <EGL/eglext.h>
#include <GLES3/gl3.h>

namespace NativeGLEngine {

struct HWTexture {
    AHardwareBuffer* hwBuffer;
    EGLImageKHR      eglImage;
    GLuint           glTexId;
    int              width, height;
    uint32_t         format;
};

class AHardwareBufferManager {
public:
    static HWTexture* createTexture(int width, int height,
                                     uint32_t format = AHARDWAREBUFFER_FORMAT_R8G8B8A8_UNORM);
    static void* lockBuffer(AHardwareBuffer* hwBuffer);
    static void  unlockBuffer(AHardwareBuffer* hwBuffer);
    static void  destroyTexture(HWTexture* tex);
    static bool  isSupported();
};

} // namespace NativeGLEngine
```

### 11.4 ahardware_buffer_manager.cpp

```cpp
// src/main/cpp/ahardware_buffer_manager.cpp
// Upload texture zéro-copie via AHardwareBuffer + EGLImage
// Référence :
// - https://developer.android.com/ndk/reference/group/a-hardware-buffer
// - https://github.com/kiryldz/android-hardware-buffer-camera (exemple réel)

#include "ahardware_buffer_manager.h"
#include <android/log.h>
#include <cstring>

#define LOG_TAG "NativeGL-AHB"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO,  LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace NativeGLEngine {

// ─── Pointeurs de fonctions EGL (extensions, chargés dynamiquement) ──────
typedef EGLClientBuffer (EGLAPIENTRYP PFNEGLGETNATIVECLIENTBUFFERANDROIDPROC)(const AHardwareBuffer*);
typedef EGLImageKHR (EGLAPIENTRYP PFNEGLCREATEIMAGEKHRPROC)(EGLDisplay, EGLContext, EGLenum, EGLClientBuffer, const EGLint*);
typedef EGLBoolean (EGLAPIENTRYP PFNEGLDESTROYIMAGEKHRPROC)(EGLDisplay, EGLImageKHR);
typedef void (GL_APIENTRYP PFNGLEGLIMAGETARGETTEXTURE2DOESPROC)(GLenum, GLeglImageOES);

static PFNEGLGETNATIVECLIENTBUFFERANDROIDPROC s_eglGetNativeClientBufferANDROID = nullptr;
static PFNEGLCREATEIMAGEKHRPROC               s_eglCreateImageKHR = nullptr;
static PFNEGLDESTROYIMAGEKHRPROC              s_eglDestroyImageKHR = nullptr;
static PFNGLEGLIMAGETARGETTEXTURE2DOESPROC    s_glEGLImageTargetTexture2DOES = nullptr;
static bool s_eglExtLoaded = false;

static void loadEGLExtensions() {
    if (s_eglExtLoaded) return;
    s_eglGetNativeClientBufferANDROID =
        (PFNEGLGETNATIVECLIENTBUFFERANDROIDPROC)eglGetProcAddress("eglGetNativeClientBufferANDROID");
    s_eglCreateImageKHR =
        (PFNEGLCREATEIMAGEKHRPROC)eglGetProcAddress("eglCreateImageKHR");
    s_eglDestroyImageKHR =
        (PFNEGLDESTROYIMAGEKHRPROC)eglGetProcAddress("eglDestroyImageKHR");
    s_glEGLImageTargetTexture2DOES =
        (PFNGLEGLIMAGETARGETTEXTURE2DOESPROC)eglGetProcAddress("glEGLImageTargetTexture2DOES");
    s_eglExtLoaded = true;
    LOGI("EGL extensions: eglGetNativeClientBufferANDROID=%s eglCreateImageKHR=%s glEGLImageTargetTexture2DOES=%s",
         s_eglGetNativeClientBufferANDROID ? "OK" : "MANQUANT",
         s_eglCreateImageKHR ? "OK" : "MANQUANT",
         s_glEGLImageTargetTexture2DOES ? "OK" : "MANQUANT");
}

bool AHardwareBufferManager::isSupported() {
    loadEGLExtensions();
    return (s_eglGetNativeClientBufferANDROID != nullptr &&
            s_eglCreateImageKHR != nullptr &&
            s_glEGLImageTargetTexture2DOES != nullptr);
}

HWTexture* AHardwareBufferManager::createTexture(int width, int height, uint32_t format) {
    loadEGLExtensions();
    if (!isSupported()) { LOGE("AHardwareBuffer non supporté !"); return nullptr; }

    HWTexture* tex = new HWTexture();
    tex->width = width; tex->height = height; tex->format = format;

    // ── Étape 1 : Allouer le AHardwareBuffer ──
    AHardwareBuffer_Desc desc = {};
    desc.width  = (uint32_t)width;
    desc.height = (uint32_t)height;
    desc.layers = 1;
    desc.format = format;
    // CPU peut écrire souvent, GPU peut lire comme texture samplée
    desc.usage  = AHARDWAREBUFFER_USAGE_CPU_WRITE_OFTEN |
                  AHARDWAREBUFFER_USAGE_GPU_SAMPLED_IMAGE;
    desc.stride = 0; // Calculé automatiquement

    if (AHardwareBuffer_allocate(&desc, &tex->hwBuffer) != 0) {
        LOGE("AHardwareBuffer_allocate failed"); delete tex; return nullptr;
    }

    // ── Étape 2 : Obtenir le EGLClientBuffer ──
    EGLClientBuffer clientBuffer = s_eglGetNativeClientBufferANDROID(tex->hwBuffer);
    if (!clientBuffer) {
        LOGE("eglGetNativeClientBufferANDROID failed !");
        AHardwareBuffer_release(tex->hwBuffer); delete tex; return nullptr;
    }

    // ── Étape 3 : Créer une EGLImage ──
    EGLDisplay display = eglGetCurrentDisplay();
    const EGLint attribs[] = { EGL_NONE };
    tex->eglImage = s_eglCreateImageKHR(
        display, EGL_NO_CONTEXT, EGL_NATIVE_BUFFER_ANDROID, clientBuffer, attribs
    );
    if (tex->eglImage == EGL_NO_IMAGE_KHR) {
        LOGE("eglCreateImageKHR failed: 0x%X", eglGetError());
        AHardwareBuffer_release(tex->hwBuffer); delete tex; return nullptr;
    }

    // ── Étape 4 : Lier l'EGLImage à une texture OpenGL ES ──
    glGenTextures(1, &tex->glTexId);
    glBindTexture(GL_TEXTURE_2D, tex->glTexId);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);

    // À partir de cet appel, le GPU lit DIRECTEMENT depuis le AHardwareBuffer
    // ZÉRO copie lors des updates
    s_glEGLImageTargetTexture2DOES(GL_TEXTURE_2D, (GLeglImageOES)tex->eglImage);

    LOGI("AHardwareBuffer texture créée: %dx%d → GL id %u (zero-copy)", width, height, tex->glTexId);
    return tex;
}

void* AHardwareBufferManager::lockBuffer(AHardwareBuffer* hwBuffer) {
    void* data = nullptr;
    int result = AHardwareBuffer_lock(
        hwBuffer, AHARDWAREBUFFER_USAGE_CPU_WRITE_OFTEN,
        -1, nullptr, &data
    );
    if (result != 0) { LOGE("AHardwareBuffer_lock failed: %d", result); return nullptr; }
    return data;
}

void AHardwareBufferManager::unlockBuffer(AHardwareBuffer* hwBuffer) {
    AHardwareBuffer_unlock(hwBuffer, nullptr);
    // À partir de cet instant, le GPU voit les nouvelles données.
    // Aucun glTexSubImage2D, aucune copie CPU→GPU.
    // Latence effective : ~0.1-0.3ms (vs ~4-15ms avec glTexSubImage2D)
}

void AHardwareBufferManager::destroyTexture(HWTexture* tex) {
    if (!tex) return;
    if (tex->glTexId) glDeleteTextures(1, &tex->glTexId);
    if (tex->eglImage != EGL_NO_IMAGE_KHR) s_eglDestroyImageKHR(eglGetCurrentDisplay(), tex->eglImage);
    if (tex->hwBuffer) AHardwareBuffer_release(tex->hwBuffer);
    delete tex;
}

} // namespace NativeGLEngine
```

### 11.5 Exemple d'utilisation complet

```cpp
// ── Initialisation (une fois) ──
HWTexture* inventoryTex = AHardwareBufferManager::createTexture(512, 512);

// ── Chaque frame : mise à jour du contenu ──
if (inventoryTex) {
    uint8_t* pixels = (uint8_t*)AHardwareBufferManager::lockBuffer(inventoryTex->hwBuffer);
    if (pixels) {
        // Écrire directement dans la mémoire partagée CPU/GPU
        for (int y = 0; y < 512; y++) {
            for (int x = 0; x < 512; x++) {
                int idx = (y * 512 + x) * 4;
                pixels[idx]   = inventoryData[x][y].r;
                pixels[idx+1] = inventoryData[x][y].g;
                pixels[idx+2] = inventoryData[x][y].b;
                pixels[idx+3] = inventoryData[x][y].a;
            }
        }
        // Déverrouiller → GPU voit les nouvelles données (~0.1ms)
        AHardwareBufferManager::unlockBuffer(inventoryTex->hwBuffer);
    }

    glActiveTexture(GL_TEXTURE0);
    glBindTexture(GL_TEXTURE_2D, inventoryTex->glTexId);
    glUniform1i(uniformLocation, 0);
}
```

### 11.6 Métriques attendues — Module 11

| Opération | glTexSubImage2D (avant) | AHardwareBuffer (après) |
|---|---|---|
| Update 512×512 texture/frame | ~4ms | **~0.1ms** |
| Update 1024×1024 texture/frame | ~15ms | **~0.3ms** |
| CPU overhead upload 10 textures | ~80ms/frame | **~1ms/frame** |
| Freeze "texture upload" visible | Oui (>16ms = drop frame) | **Non (invisible)** |

---

## 🗂️ STRUCTURE FICHIERS C++ — Plan 2 (Complète)

```
app/src/main/cpp/
├── texture_compressor.cpp      ← Module 6
├── texture_compressor.h
├── off_heap_arena.cpp          ← Module 7
├── off_heap_arena.h
├── vertex_quantizer.cpp        ← Module 8
├── vertex_quantizer.h
├── simd_math_engine.cpp        ← Module 9
├── simd_math_engine.h
├── draw_call_batcher.cpp       ← Module 10
├── draw_call_batcher.h
├── ahardware_buffer_manager.cpp ← Module 11
├── ahardware_buffer_manager.h
│
├── jni_bridge.cpp              ← JNI: expose tous les modules au Java/Kotlin
│
├── deps/
│   ├── etcpak/                 ← git submodule (MIT)
│   │   ├── ProcessRGB.hpp/.cpp
│   │   └── ProcessDxtc.hpp/.cpp
│   ├── astcenc/                ← git submodule (Apache 2.0)
│   │   └── Source/astcenc.h + *.cpp
│   └── fp16/                   ← git submodule (MIT)
│       └── include/fp16.h
│
└── CMakeLists.txt
```

### CMakeLists.txt complet Plan 1+2

```cmake
cmake_minimum_required(VERSION 3.22)
project(NativeGLEngine)

set(CMAKE_CXX_STANDARD 17)

# ─── Options de compilation agressives ───
add_compile_options(
    -O3
    -ffast-math
    -fno-exceptions      # Réduction binaire (pas d'exceptions C++ = +5% perf)
    -fno-rtti            # Réduction binaire
    -march=armv8-a+simd  # Active NEON sur arm64 (intrinsics arm_neon.h)
    -DARM_NEON_ENABLED
)

# ─── etcpak (ETC2 encoder) ───
add_library(etcpak_lib STATIC
    deps/etcpak/ProcessRGB.cpp
    deps/etcpak/ProcessDxtc.cpp
)
target_include_directories(etcpak_lib PUBLIC deps/etcpak)
target_compile_options(etcpak_lib PRIVATE -O3 -ffast-math)

# ─── astcenc (ARM ASTC Encoder) ───
# ISA_NEON=ON : utilise les intrinsics NEON arm64
# DENABLE_SHARED=OFF : bibliothèque statique
add_subdirectory(deps/astcenc EXCLUDE_FROM_ALL)
# La cible NEON arm64 s'appelle "astcenc-neon-static"

# ─── Bibliothèque principale NativeGLEngine ───
add_library(NativeGLEngine SHARED
    # Plan 1 (existants)
    shader_compiler.cpp
    shader_cache.cpp
    soc_optimizer.cpp
    gl_interceptor.cpp
    native_memory.cpp
    texture_manager.cpp
    jni_bridge.cpp

    # Plan 2 (nouveaux)
    texture_compressor.cpp
    off_heap_arena.cpp
    vertex_quantizer.cpp
    simd_math_engine.cpp
    draw_call_batcher.cpp
    ahardware_buffer_manager.cpp
)

# ─── LTO (Link Time Optimization) ───
# Réduit la taille du .so et améliore les performances ~5-10%
set_property(TARGET NativeGLEngine PROPERTY INTERPROCEDURAL_OPTIMIZATION TRUE)

# ─── Librairies Android ───
target_link_libraries(NativeGLEngine
    android        # ANativeWindow, AHardwareBuffer, etc.
    log            # __android_log_print
    EGL            # EGLContext, EGLDisplay, EGLImageKHR
    GLESv3         # OpenGL ES 3.0+
    vulkan         # Bypass Vulkan (Module 5)
    jnigraphics    # android/bitmap.h
    etcpak_lib
    astcenc-neon-static
)

# ─── Include dirs ───
target_include_directories(NativeGLEngine PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}
    ${CMAKE_CURRENT_SOURCE_DIR}/deps/etcpak
    ${CMAKE_CURRENT_SOURCE_DIR}/deps/astcenc/Source
    ${CMAKE_CURRENT_SOURCE_DIR}/deps/fp16/include
)
```

### build.gradle (app) — Extrait pertinent

```groovy
android {
    defaultConfig {
        minSdk 26      // API 26 = Android 8.0 (AHardwareBuffer requis)
        targetSdk 35

        ndk {
            // ⚠️ Compiler UNIQUEMENT arm64-v8a
            // armeabi-v7a n'a pas toutes les intrinsics NEON arm64
            abiFilters "arm64-v8a"
        }

        externalNativeBuild {
            cmake {
                cppFlags "-std=c++17 -O3 -march=armv8-a+simd"
                arguments "-DANDROID_STL=c++_shared",
                          "-DANDROID_ARM_NEON=TRUE",
                          "-DCMAKE_BUILD_TYPE=Release"
            }
        }
    }

    externalNativeBuild {
        cmake {
            path "src/main/cpp/CMakeLists.txt"
            version "3.22.1"
        }
    }
}
```

---

## 🔬 PROFILING & OUTILS DE MESURE

Mesurer avant ET après chaque module est **obligatoire**.

### Outils GPU

**Qualcomm Snapdragon Profiler** (pour Snapdragon 8 Gen 1/2/3) :
- Profil GPU : temps de rendu par draw call, overdraw, shader occupancy
- Profil mémoire : usage VRAM, bande passante texture
- Disponible sur : https://developer.qualcomm.com/software/snapdragon-profiler

**RenderDoc Android** (capture frame + debug shader) :
```bash
# Installer RenderDoc sur Android (debug build uniquement)
adb shell settings put global debug.renderdoc.enable 1
# Puis lancer RenderDoc desktop et se connecter en USB
```

**Android GPU Inspector** (officiel Google) :
```bash
# Installer AGI : https://gpuinspector.dev/
# Capture automatique des draw calls, textures, GPU counters
```

### Outils CPU — simpleperf (NDK intégré)

```bash
# Profiler le processus NativeGLEngine sur le device
# 1. Démarrer le profiling (30 secondes)
adb shell
python3 /path/to/ndk/simpleperf/app_profiler.py \
    --app com.yourapp.librecraft \
    -r "-g --duration 30 -e cpu-cycles,cache-misses,branch-misses" \
    --ndk_path /path/to/ndk

# 2. Analyser les résultats
simpleperf report --dsos libNativeGLEngine.so --sort symbol

# 3. Exemple sortie :
# 45.2%  TextureCompressor::compressETC2
# 23.1%  SIMDMathEngine::frustumCullSpheres
# 12.8%  DrawCallBatcher::flush

# 4. Générer un flame graph HTML interactif
python3 report_html.py -i perf.data -o perf_report.html
```

### Outils mémoire — Android Studio Memory Profiler

```bash
# Vérifier la RAM totale du processus
adb shell dumpsys meminfo com.yourapp.librecraft

# Output type :
# Native Heap:    150 MB  ← off-heap arena (Module 7)
# Dalvik Heap:    450 MB  ← JVM heap (doit être < 500 MB)
# Dalvik Other:   50 MB
# GL mtrack:      110 MB  ← VRAM textures (doit être < 200 MB après Module 6)
# Total PSS:      ~810 MB ← idéal < 2100 MB

# Surveiller en continu
watch -n 1 "adb shell dumpsys meminfo com.yourapp.librecraft | grep -E 'Total|GL|Native'"
```

### Mesure FPS — Android Frametime

```bash
# SurfaceFlinger : mesurer les frametime réels
adb shell dumpsys SurfaceFlinger --latency com.yourapp.librecraft/MainActivity

# Ou utiliser le systrace
adb shell atrace --async_start -c gfx view
# ... jouer pendant 30 secondes ...
adb shell atrace --async_stop -z -o /data/local/tmp/trace.ctrace
adb pull /data/local/tmp/trace.ctrace
# Ouvrir dans https://ui.perfetto.dev/
```

---

## ⚠️ RISQUES PLAN 2 — Détail et Mitigations

| Risque | Module | Probabilité | Impact | Mitigation détaillée |
|---|---|---|---|---|
| Artefacts visuels ETC2 sur textures custom | 6 | Faible | Moyen | Whitelist textures par namespace mod (ex : `create:*` ok, `specialmod:*` → skip) |
| FP16 overflow sur coordonnées > ±65504 | 8 | Faible | Haut | `checkFP16Safe()` avant quantification, fallback FP32 si unsafe |
| AHardwareBuffer indisponible (API < 26) | 11 | Très faible | Faible | Android 8.0 est le minimum absolu, `isSupported()` check au démarrage |
| Draw call batcher perturbe ordre de rendu | 10 | Moyen | Moyen | Mode `STRICT` (désactivable) qui respecte l'ordre original |
| Off-heap arena leak si mod ne libère pas | 7 | Moyen | Moyen | Watchdog timer 60s + nettoyage forcé à chaque changement de monde |
| astcenc crash sur GPU qui déclare ASTC mais est bugué | 6 | Rare | Bas | Test de compression/décompression d'une texture 4×4 au démarrage, blacklist GPU |
| NEON intrinsics: undefined behavior sur data non alignée | 8/9 | Faible | Haut | Aligner tous les buffers à 16 bytes (`alignas(16)` ou `aligned_alloc(16, ...)`) |
| Interférence avec mods qui patchent OpenGL (Sodium-like) | 6/10 | Moyen | Variable | Détection de hooks conflictuels, désactivation sélective par module |

---

## 📊 TABLEAU D'IMPACT CUMULÉ — Plan 1 + Plan 2

| Métrique | Baseline | Après Plan 1 | Après Plan 1+2 |
|---|---|---|---|
| RAM totale avec 60 mods | ~2800 MB ❌ | ~2200 MB ⚠️ | **~975 MB ✅** |
| RAM textures GPU | ~900 MB | ~900 MB | **~110 MB (ASTC)** |
| RAM vertex buffers | ~250 MB | ~250 MB | **~125 MB (FP16)** |
| RAM Java heap | ~700 MB | ~700 MB | **~480 MB (off-heap)** |
| Stutter shader (premier load) | 200ms–2s | **< 50ms** | **< 10ms** |
| Freezes GC | toutes les 5s | toutes les 5s | **toutes les 60s** |
| Freezes texture upload | 5–15ms/frame | 2–5ms/frame | **< 0.5ms/frame** |
| Draw calls Create scene | 800–1500 | 800–1500 | **80–150** |
| Frustum culling CPU | ~3ms/frame | ~3ms/frame | **~0.3ms/frame** |
| FPS stable 60 mods (SD 8 Gen 2) | 15–25 FPS | 25–40 FPS | **50–60 FPS** |
| Budget mémoire (objectif 2100 MB) | ❌ Dépassé | ❌ Léger | **✅ 975 MB = marge ×2** |

---

## 🏆 ORDRE D'IMPLÉMENTATION RECOMMANDÉ

```
Semaine 1-2  : Module 6 (TextureCompressor ETC2/ASTC)
               → Setup submodules etcpak + astcenc
               → Intégration dans InterceptLayer
               → Test sur atlas Minecraft vanilla puis Create
               → Gain attendu : -720 MB RAM → objectif 2100 MB atteint

Semaine 3    : Module 7 (OffHeapArena)
               → Pool 150 MB, DirectByteBuffer JNI
               → Remplacer les gros buffers Java (chunks, entities)
               → Valider avec dumpsys meminfo
               → Gain attendu : GC ÷12

Semaine 4    : Module 8 (VertexQuantizer FP16/UNORM16)
               → Quantification FP32→FP16 NEON
               → Mise à jour des VAO/glVertexAttribPointer
               → Tests de précision (pas d'artefacts > 1000 blocs)
               → Gain attendu : -125 MB RAM + GPU bandwidth -50%

Semaine 5    : Module 9 (SIMDMathEngine)
               → Frustum culling NEON
               → matMul4x4 NEON
               → extractFrustumFromMatrix
               → Valider avec simpleperf
               → Gain attendu : CPU ×10 sur culling

Semaine 6-7  : Module 10 (DrawCallBatcher)
               → Instanced rendering GLES 3.0
               → UBO pour transforms
               → Intégration avec Create (gears, belts, fans)
               → Gain attendu : draw calls ÷10

Semaine 8    : Module 11 (AHardwareBuffer)
               → Zero-copy texture upload
               → EGLImage + AHardwareBuffer_lock/unlock
               → Remplacement glTexSubImage2D pour textures dynamiques
               → Gain attendu : texture uploads < 0.5ms/frame

Total : ~2 mois pour les 6 modules du Plan 2
        → LibreCraft + 60 mods dans 975 MB à 50-60 FPS sur Snapdragon 8 Gen 2
```

---

## 🔗 DÉPENDANCES SUPPLÉMENTAIRES — Plan 2

| Librairie | URL | Licence | Usage |
|---|---|---|---|
| **etcpak** | https://github.com/wolfpld/etcpak | MIT | Compression ETC2 NEON ultra-rapide (~238 Mpx/s arm64) |
| **astcenc** (ARM) | https://github.com/ARM-software/astc-encoder | Apache 2.0 | Compression ASTC (GPU haut de gamme, preset FASTEST) |
| **fp16** (Maratyszcza) | https://github.com/Maratyszcza/FP16 | MIT | Conversion FP32↔FP16 portable (NEON sur arm64) |
| **AHardwareBuffer NDK** | https://developer.android.com/ndk/reference/group/a-hardware-buffer | Android SDK | Upload GPU zéro-copie (API 26+) |
| **ARM Neon NDK** | https://developer.android.com/ndk/guides/cpu-arm-neon | Android SDK | SIMD ARM intrinsics (activé par défaut arm64) |
| **sse2neon** (optionnel) | https://github.com/DLTcollab/sse2neon | MIT | Porter code SSE x86 vers NEON arm64 si besoin |

---

## 📚 RÉFÉRENCES TECHNIQUES COMPLÈTES

- **etcpak benchmarks** : ETC2 ST: 238 Mpx/s / MT: 984 Mpx/s sur Apple M2 ; Odroid C2 (ARM A53) : 12.3 Mpx/s ST — https://github.com/wolfpld/etcpak
- **Android Vertex Data Management** (Google) : FP16 réduit bande passante vertex jusqu'à 50% — https://developer.android.com/games/optimize/vertex-data-management
- **ARM NEON intrinsics reference** — https://developer.arm.com/documentation/102467
- **AHardwareBuffer NDK reference** — https://developer.android.com/ndk/reference/group/a-hardware-buffer
- **ASTC encoder (ARM)** documentation et building guide — https://github.com/ARM-software/astc-encoder/blob/main/Docs/Building.md
- **OpenGL ES 3.0 Instanced Rendering** (Android Developer Blog, 2015) — https://android-developers.googleblog.com/2015/05/game-performance-geometry-instancing.html
- **LearnOpenGL — Instancing** (référence code complet VAO/VBO/UBO) — https://learnopengl.com/Advanced-OpenGL/Instancing
- **Simpleperf NDK profiling** — https://developer.android.com/ndk/guides/simpleperf
- **Frustum culling Gribb & Hartmann** — algorithme extractFrustum de la matrice VP
- **Tile-Based Rendering mobile** (ARM, Qualcomm, Imagination) — architecture GPU mobile
- **ImmediatelyFast** (RaphiMC/GitHub) — source d'inspiration pour le batching de mods Minecraft
- **AcceleratedRendering** (Argon4W/CurseForge) — référence implémentation GL hooks Minecraft
- **Android JNI Tips** — https://developer.android.com/training/articles/perf-jni
- **VK_KHR_shader_float16_int8** — FP16 arithmetic dans shaders Vulkan/GLES — https://docs.vulkan.org/samples/latest/samples/performance/16bit_arithmetic/README.html

---

*Plan 2 — NativeGLEngine v2 ULTRA COMPLET*
*Document enrichi avec recherches approfondies : etcpak GitHub, ARM astcenc, Android NDK docs,*
*OpenGL ES instancing Android Developer Blog, ARM NEON intrinsics guide, AHardwareBuffer NDK,*
*simpleperf NDK profiling, Android Vertex Data Management, VK FP16 arithmetic guide*
