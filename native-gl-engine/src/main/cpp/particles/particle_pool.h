/**
 * particle_pool.h — Pool de particules C++ à taille fixe.
 *
 * Toutes les particules Minecraft sont normalement des objets Java (~96-120 octets chacun)
 * qui stressent le GC. Ce pool les stocke en mémoire native contiguë.
 * 4096 slots × 40 octets = 160 Ko fixe, INVISIBLE pour le GC Java.
 *
 * Technique : Fixed-block pool allocator avec free-list intégrée.
 * Source : technique standard des moteurs de jeu AAA (Unreal, Unity).
 */
#pragma once
#include <cstdint>
#include <cstring>

namespace androidopt {

struct alignas(8) NativeParticle {
    float x, y, z;          // position (12 octets)
    float vx, vy, vz;       // vélocité (12 octets)
    float age;               // âge actuel (4 octets)
    float maxAge;            // durée de vie max (4 octets)
    uint16_t texIndex;       // index dans l'atlas de textures (2 octets)
    uint8_t r, g, b, a;     // couleur RGBA (4 octets)
    uint8_t active;          // slot actif ? (1 octet)
    uint8_t _pad;            // alignement (1 octet)
    // Total : 40 octets, aligné sur 8
};
static_assert(sizeof(NativeParticle) == 40, "NativeParticle must be 40 bytes");

class ParticlePool {
public:
    static constexpr int MAX_PARTICLES = 4096;

private:
    NativeParticle pool_[MAX_PARTICLES];
    int activeCount_;
    // Statistiques
    uint64_t totalSpawned_;
    uint64_t totalRecycled_;

public:
    ParticlePool();

    /**
     * Crée une nouvelle particule dans le pool.
     * @return index du slot, ou -1 si le pool est plein (dégradation gracieuse)
     */
    int spawn(float x, float y, float z,
              float vx, float vy, float vz,
              float maxAge, uint16_t texIndex,
              uint8_t r, uint8_t g, uint8_t b, uint8_t a);

    /**
     * Met à jour TOUTES les particules en une seule boucle.
     * Applique gravité, vélocité, et vérifie la durée de vie.
     * @param dt delta-time en secondes (typiquement 0.05 = 1 tick)
     * @return nombre de particules encore vivantes
     */
    int tickAll(float dt);

    /** Tue toutes les particules (ex: changement de dimension) */
    void clear();

    int activeCount() const { return activeCount_; }
    uint64_t totalSpawned() const { return totalSpawned_; }
    uint64_t totalRecycled() const { return totalRecycled_; }

    /** Accès direct au tableau pour le rendu (lecture seule) */
    const NativeParticle* data() const { return pool_; }
    int maxParticles() const { return MAX_PARTICLES; }
};

} // namespace androidopt
