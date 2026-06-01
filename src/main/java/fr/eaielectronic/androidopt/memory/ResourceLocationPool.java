package fr.eaielectronic.androidopt.memory;

import net.minecraft.resources.ResourceLocation;

import java.lang.ref.WeakReference;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Pool d'interning pour les objets ResourceLocation.
 *
 * FerriteCore déduplique les String internes (namespace, path) mais PAS
 * les objets ResourceLocation eux-mêmes. Chaque objet RL = 16 octets de header
 * Java + 2 références String = ~40 octets par objet.
 *
 * Avec 55 mods, des milliers de RL identiques sont créés séparément.
 * Ce pool les déduplique via WeakReference pour que le GC puisse toujours
 * récupérer les RL qui ne sont plus utilisées nulle part.
 *
 * Gain estimé : 20-45 Mo de heap sur un modpack industriel.
 */
public class ResourceLocationPool {
    public static final ResourceLocationPool INSTANCE = new ResourceLocationPool();

    private final ConcurrentHashMap<String, WeakReference<ResourceLocation>> pool;
    private final AtomicLong hits = new AtomicLong(0);
    private final AtomicLong misses = new AtomicLong(0);

    // Nettoyage périodique des WeakReferences expirées
    private long lastCleanup = 0;
    private static final long CLEANUP_INTERVAL_MS = 60_000; // 1 minute

    private ResourceLocationPool() {
        // Pré-dimensionner pour éviter les rehash initiaux
        this.pool = new ConcurrentHashMap<>(4096);
    }

    /**
     * Internat un ResourceLocation. Si un objet identique existe déjà dans le pool
     * et n'a pas été GC'd, on retourne l'existant. Sinon on stocke le nouveau.
     *
     * @param rl le ResourceLocation à interner
     * @return l'instance partagée (peut être == rl si c'est nouveau)
     */
    public ResourceLocation intern(ResourceLocation rl) {
        if (rl == null) return null;

        String key = rl.getNamespace() + ":" + rl.getPath();

        // Fast path : chercher dans le cache
        WeakReference<ResourceLocation> ref = pool.get(key);
        if (ref != null) {
            ResourceLocation existing = ref.get();
            if (existing != null) {
                hits.incrementAndGet();
                return existing;
            }
            // La WeakReference est morte, on la remplace
        }

        // Slow path : stocker le nouveau RL
        pool.put(key, new WeakReference<>(rl));
        misses.incrementAndGet();

        // Nettoyage périodique des entrées mortes
        long now = System.currentTimeMillis();
        if (now - lastCleanup > CLEANUP_INTERVAL_MS) {
            lastCleanup = now;
            cleanupExpired();
        }

        return rl;
    }

    /**
     * Supprime les WeakReferences dont l'objet a été GC'd.
     * Appelé automatiquement toutes les minutes.
     */
    private void cleanupExpired() {
        int removed = 0;
        var iterator = pool.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            if (entry.getValue().get() == null) {
                iterator.remove();
                removed++;
            }
        }
        if (removed > 0) {
            org.slf4j.LoggerFactory.getLogger("AndroidOpt")
                .debug("[RL Pool] Cleaned {} expired entries, {} remaining", removed, pool.size());
        }
    }

    /**
     * @return statistiques du pool pour le HUD de debug
     */
    public String getStats() {
        long h = hits.get();
        long m = misses.get();
        long total = h + m;
        float hitRate = total > 0 ? (h * 100.0f / total) : 0;
        return String.format("RL Pool: %d entries, %d hits, %d misses (%.1f%% hit rate)",
                pool.size(), h, m, hitRate);
    }

    /**
     * @return nombre d'entrées actuellement dans le pool
     */
    public int size() {
        return pool.size();
    }

    /**
     * @return estimation de la mémoire économisée en octets
     * (chaque hit = ~40 octets d'objet RL non créé)
     */
    public long estimatedSavedBytes() {
        return hits.get() * 40L;
    }
}
