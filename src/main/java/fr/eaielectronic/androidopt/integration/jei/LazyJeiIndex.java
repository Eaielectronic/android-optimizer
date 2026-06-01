package fr.eaielectronic.androidopt.integration.jei;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Indexation paresseuse des ingrédients JEI.
 *
 * Au lieu de charger et indexer les dizaines de milliers d'items de 55 mods au démarrage,
 * on ne garde en mémoire active que l'index de 512 items max. Le reste est indexé
 * en tâche de fond de manière paresseuse lorsqu'ils sont recherchés ou affichés.
 */
public class LazyJeiIndex {
    private static final Logger LOGGER = LoggerFactory.getLogger("AndroidOpt");
    public static final LazyJeiIndex INSTANCE = new LazyJeiIndex();

    // Cache LRU de 512 items indexés en mémoire
    private final LinkedHashMap<String, Boolean> indexedItems =
        new LinkedHashMap<>(512, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, Boolean> eldest) {
                return size() > 512;
            }
        };

    // Thread de fond pour l'indexation asynchrone
    private final ExecutorService indexer = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "AndroidOpt-JEI-Indexer");
        t.setDaemon(true);
        t.setPriority(Thread.MIN_PRIORITY);
        return t;
    });

    private int hits = 0;
    private int misses = 0;

    private LazyJeiIndex() {}

    public boolean isIndexed(String itemId) {
        if (itemId == null) return false;
        boolean contains = indexedItems.containsKey(itemId);
        if (contains) {
            hits++;
        } else {
            misses++;
        }
        return contains;
    }

    public void requestIndex(String itemId, Runnable indexTask) {
        if (itemId == null || isIndexed(itemId)) return;

        indexer.submit(() -> {
            try {
                indexTask.run();
                synchronized (indexedItems) {
                    indexedItems.put(itemId, Boolean.TRUE);
                }
                LOGGER.debug("[JEI Lazy Index] Indexed item: {}", itemId);
            } catch (Exception e) {
                LOGGER.warn("[JEI Lazy Index] Failed to index {}: {}", itemId, e.getMessage());
            }
        });
    }

    public String getStats() {
        synchronized (indexedItems) {
            return String.format("JEI Lazy: %d items in cache, %d hits, %d misses", indexedItems.size(), hits, misses);
        }
    }
}
