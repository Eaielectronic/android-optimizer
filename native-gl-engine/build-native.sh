#!/bin/bash
# ═══════════════════════════════════════════════════════════════
# build-native.sh — Compile libNativeGLEngine.so pour ARM64
# ═══════════════════════════════════════════════════════════════
#
# Usage : ./build-native.sh [release|debug]
#
# Prérequis :
#   - Android NDK r26+ (variable ANDROID_NDK_HOME ou ANDROID_NDK_ROOT)
#   - CMake 3.22+
#
# La .so compilée est automatiquement copiée dans :
#   src/main/resources/assets/nativeglengine/native/arm64-v8a/
#

set -euo pipefail

# ─── Couleurs pour les logs ───
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

log_info()  { echo -e "${BLUE}[INFO]${NC} $1"; }
log_ok()    { echo -e "${GREEN}[OK]${NC} $1"; }
log_warn()  { echo -e "${YELLOW}[WARN]${NC} $1"; }
log_error() { echo -e "${RED}[ERROR]${NC} $1"; }

# ─── Déterminer le répertoire du script ───
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
CPP_DIR="${SCRIPT_DIR}/src/main/cpp"
RESOURCE_DIR="${SCRIPT_DIR}/src/main/resources/assets/nativeglengine/native/arm64-v8a"

# ─── Mode de build (release par défaut) ───
BUILD_TYPE="${1:-Release}"
case "$BUILD_TYPE" in
    release|Release) BUILD_TYPE="Release" ;;
    debug|Debug)     BUILD_TYPE="Debug" ;;
    *)
        log_error "Mode invalide : $BUILD_TYPE (utiliser 'release' ou 'debug')"
        exit 1
        ;;
esac

log_info "Build NativeGLEngine — Mode: ${BUILD_TYPE}"
log_info "Répertoire C++ : ${CPP_DIR}"

# ─── Vérifier le NDK ───
NDK_HOME="${ANDROID_NDK_HOME:-${ANDROID_NDK_ROOT:-}}"
if [ -z "$NDK_HOME" ]; then
    # Chercher dans le répertoire parent (pour le dev local)
    if [ -d "${SCRIPT_DIR}/../android-ndk-r26d" ]; then
        NDK_HOME="${SCRIPT_DIR}/../android-ndk-r26d"
        log_warn "ANDROID_NDK_HOME non défini, utilisation de ${NDK_HOME}"
    else
        log_error "ANDROID_NDK_HOME ou ANDROID_NDK_ROOT non défini"
        log_error "Installez Android NDK r26+ et définissez la variable d'environnement"
        exit 1
    fi
fi

TOOLCHAIN_FILE="${NDK_HOME}/build/cmake/android.toolchain.cmake"
if [ ! -f "$TOOLCHAIN_FILE" ]; then
    log_error "Fichier toolchain non trouvé : ${TOOLCHAIN_FILE}"
    log_error "Vérifiez votre installation NDK"
    exit 1
fi
log_ok "NDK trouvé : ${NDK_HOME}"

# ─── Vérifier CMake ───
if ! command -v cmake &> /dev/null; then
    log_error "CMake non trouvé. Installez CMake 3.22+"
    exit 1
fi
CMAKE_VERSION=$(cmake --version | head -1 | cut -d' ' -f3)
log_ok "CMake : ${CMAKE_VERSION}"

# ─── Configurer le build ───
BUILD_DIR="${CPP_DIR}/build"
log_info "Configuration CMake..."

cmake \
    -DCMAKE_TOOLCHAIN_FILE="${TOOLCHAIN_FILE}" \
    -DANDROID_ABI=arm64-v8a \
    -DANDROID_PLATFORM=android-26 \
    -DCMAKE_BUILD_TYPE="${BUILD_TYPE}" \
    -S "${CPP_DIR}" \
    -B "${BUILD_DIR}" \
    2>&1

log_ok "CMake configuré"

# ─── Compiler ───
NPROC=$(nproc 2>/dev/null || echo 4)
log_info "Compilation avec ${NPROC} threads..."

cmake --build "${BUILD_DIR}" -j"${NPROC}" 2>&1

log_ok "Compilation terminée"

# ─── Copier la .so dans les resources ───
SO_FILE="${BUILD_DIR}/libNativeGLEngine.so"
if [ ! -f "$SO_FILE" ]; then
    log_error "libNativeGLEngine.so non trouvée dans ${BUILD_DIR}"
    exit 1
fi

mkdir -p "${RESOURCE_DIR}"
cp "${SO_FILE}" "${RESOURCE_DIR}/"

# ─── Afficher les infos ───
SO_SIZE=$(du -h "${RESOURCE_DIR}/libNativeGLEngine.so" | cut -f1)
SO_ARCH=$(file "${RESOURCE_DIR}/libNativeGLEngine.so" | grep -o 'ARM aarch64' || echo "unknown")

echo ""
echo "═══════════════════════════════════════════════════════════"
log_ok "Build terminé avec succès !"
echo ""
echo "  Fichier   : ${RESOURCE_DIR}/libNativeGLEngine.so"
echo "  Taille    : ${SO_SIZE}"
echo "  Arch      : ${SO_ARCH}"
echo "  Mode      : ${BUILD_TYPE}"
echo ""
echo "  Pour builder le JAR :"
echo "    cd ${SCRIPT_DIR} && ./gradlew build"
echo "═══════════════════════════════════════════════════════════"
