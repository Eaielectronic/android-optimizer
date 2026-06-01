/**
 * spsc_queue.h — Lock-free Single Producer Single Consumer Queue.
 *
 * Communication sans mutex entre le thread Java (producteur) et le thread C++ (consommateur).
 * Utilisé pour passer des commandes (spawn particule, jouer son) sans bloquer.
 *
 * Principes :
 * - Capacité fixe, puissance de 2 (masque binaire au lieu de modulo)
 * - alignas(64) pour éviter le false sharing entre head et tail
 * - memory_order_acquire/release pour la synchronisation minimale
 *
 * Source : Cameron Desrochers "moodycamel::ReaderWriterQueue" (concept),
 *          Erik Rigtorp "SPSCQueue" (pattern d'implémentation)
 */
#pragma once
#include <atomic>
#include <cstdint>
#include <new> // for std::hardware_destructive_interference_size fallback

namespace androidopt {

template<typename T, uint32_t Capacity>
class SPSCQueue {
    static_assert((Capacity & (Capacity - 1)) == 0,
                  "Capacity must be a power of 2");
    static constexpr uint32_t MASK = Capacity - 1;

    // Chaque index sur sa propre cache line pour éviter le false sharing
    // (quand deux cœurs CPU se battent pour la même ligne de cache)
    alignas(64) std::atomic<uint32_t> head_{0}; // index de lecture (consumer)
    alignas(64) std::atomic<uint32_t> tail_{0}; // index d'écriture (producer)
    T buffer_[Capacity];

public:
    SPSCQueue() = default;

    /**
     * Pousse un élément dans la queue (côté producteur uniquement).
     * @return true si succès, false si la queue est pleine
     */
    bool push(const T& item) {
        const uint32_t t = tail_.load(std::memory_order_relaxed);
        const uint32_t next = (t + 1) & MASK;
        // Si next == head, la queue est pleine
        if (next == head_.load(std::memory_order_acquire))
            return false;
        buffer_[t] = item;
        tail_.store(next, std::memory_order_release);
        return true;
    }

    /**
     * Retire un élément de la queue (côté consommateur uniquement).
     * @return true si un élément a été retiré, false si la queue est vide
     */
    bool pop(T& item) {
        const uint32_t h = head_.load(std::memory_order_relaxed);
        // Si head == tail, la queue est vide
        if (h == tail_.load(std::memory_order_acquire))
            return false;
        item = buffer_[h];
        head_.store((h + 1) & MASK, std::memory_order_release);
        return true;
    }

    /**
     * @return nombre approximatif d'éléments dans la queue
     * (approximatif car head et tail peuvent changer pendant la lecture)
     */
    uint32_t size() const {
        uint32_t t = tail_.load(std::memory_order_relaxed);
        uint32_t h = head_.load(std::memory_order_relaxed);
        return (t - h) & MASK;
    }

    bool empty() const {
        return head_.load(std::memory_order_relaxed) ==
               tail_.load(std::memory_order_relaxed);
    }

    bool full() const {
        uint32_t next = (tail_.load(std::memory_order_relaxed) + 1) & MASK;
        return next == head_.load(std::memory_order_relaxed);
    }

    static constexpr uint32_t capacity() { return Capacity; }
};

// ─── Types de commandes pour la queue Java→C++ ───

enum class CommandType : uint8_t {
    SPAWN_PARTICLE = 0,
    CLEAR_PARTICLES = 1,
    PURGE_MEMORY = 2,
    BOOST_THREAD = 3,
};

struct alignas(8) NativeCommand {
    CommandType type;
    uint8_t _pad[3];
    union {
        struct {
            float x, y, z;
            float vx, vy, vz;
            float maxAge;
            uint16_t tex;
            uint8_t r, g, b, a;
        } particle;
        struct {
            int32_t param;
        } generic;
    } data;
};

// Queue de 1024 commandes (taille fixe, ~40 Ko)
using CommandQueue = SPSCQueue<NativeCommand, 1024>;

} // namespace androidopt
