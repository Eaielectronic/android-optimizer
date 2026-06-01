/**
 * particle_pool.cpp — Implémentation du pool de particules natif.
 *
 * Principes de performance :
 * - Boucle linéaire sur mémoire contiguë (cache-friendly)
 * - Aucune allocation dynamique après l'initialisation
 * - Gravité appliquée inline (pas d'appel de fonction par particule)
 * - Branchless death check possible avec NEON (futur)
 */
#include "particle_pool.h"
#include <android/log.h>

#define TAG "AndroidOpt_Particles"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)

namespace androidopt {

ParticlePool::ParticlePool()
    : activeCount_(0), totalSpawned_(0), totalRecycled_(0) {
    memset(pool_, 0, sizeof(pool_));
    LOGI("ParticlePool initialized: %d slots, %zu KB",
         MAX_PARTICLES, sizeof(pool_) / 1024);
}

int ParticlePool::spawn(float x, float y, float z,
                         float vx, float vy, float vz,
                         float maxAge, uint16_t texIndex,
                         uint8_t r, uint8_t g, uint8_t b, uint8_t a) {
    // Recherche linéaire du premier slot libre
    // Optimisation future : free-list chaînée pour O(1)
    for (int i = 0; i < MAX_PARTICLES; i++) {
        if (!pool_[i].active) {
            NativeParticle& p = pool_[i];
            p.x = x;   p.y = y;   p.z = z;
            p.vx = vx;  p.vy = vy;  p.vz = vz;
            p.age = 0.0f;
            p.maxAge = maxAge;
            p.texIndex = texIndex;
            p.r = r;  p.g = g;  p.b = b;  p.a = a;
            p.active = 1;
            p._pad = 0;
            activeCount_++;
            totalSpawned_++;
            return i;
        }
    }
    // Pool plein — dégradation gracieuse (la particule n'est pas créée)
    return -1;
}

int ParticlePool::tickAll(float dt) {
    int alive = 0;
    const float gravity = -0.04f; // Minecraft standard gravity per tick

    for (int i = 0; i < MAX_PARTICLES; i++) {
        NativeParticle& p = pool_[i];
        if (!p.active) continue;

        // Mise à jour position
        p.x += p.vx * dt;
        p.y += p.vy * dt + gravity * dt;
        p.z += p.vz * dt;

        // Friction (air drag) — Minecraft applique ×0.98 par tick
        p.vx *= 0.98f;
        p.vy *= 0.98f;
        p.vz *= 0.98f;

        // Avancer l'âge
        p.age += dt;

        // Vérifier la mort
        if (p.age >= p.maxAge) {
            p.active = 0;
            activeCount_--;
            totalRecycled_++;
        } else {
            alive++;
        }
    }
    return alive;
}

void ParticlePool::clear() {
    for (int i = 0; i < MAX_PARTICLES; i++) {
        if (pool_[i].active) {
            pool_[i].active = 0;
            totalRecycled_++;
        }
    }
    activeCount_ = 0;
    LOGI("ParticlePool cleared");
}

} // namespace androidopt
