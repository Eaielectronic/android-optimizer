/**
 * texture_manager.cpp — Queue MPSC pour uploads de texture différés
 * 
 * Quand le budget GPU est tendu (>80%), les uploads glTexImage2D sont
 * mis en queue au lieu d'être exécutés immédiatement.
 * La queue est drainée progressivement entre les frames (max 2ms).
 */

#include "texture_manager.h"
#include <android/log.h>

#define LOG_TAG "NativeGLEngine"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)

void texture_manager_drain(int max_uploads) {
    // TODO: Implémenter la queue de textures différées
    //
    // Algorithme :
    // 1. Vérifier si la queue est non-vide
    // 2. Pour chaque texture en queue (max max_uploads) :
    //    a. Vérifier le budget GPU via VMA
    //    b. Si budget OK → appeler original_glTexImage2D()
    //    c. Si budget tendu → remettre en queue et break
    //    d. Vérifier le temps écoulé → break si > 2ms
    // 3. Libérer la mémoire des données pixel copiées
    
    // STUB — rien à drainer pour l'instant
}
