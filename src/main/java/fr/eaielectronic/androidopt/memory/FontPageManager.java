package fr.eaielectronic.androidopt.memory;

import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Gestionnaire d'atlas de polices paresseux.
 *
 * Évite de garder en mémoire les pages de glyphes unicode (chinois, japonais, cyrillique...)
 * si elles ne sont pas affichées. Les pages ASCII (0-255) restent toujours chargées.
 */
public class FontPageManager {
    private static final Logger LOGGER = LoggerFactory.getLogger("AndroidOpt");
    public static final FontPageManager INSTANCE = new FontPageManager();

    // Cache LRU de 32 pages maximum (1 page = 256 caractères)
    private final LinkedHashMap<Integer, Boolean> loadedPages =
        new LinkedHashMap<>(32, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<Integer, Boolean> eldest) {
                if (size() > 32) {
                    LOGGER.debug("[Font Page Manager] Evicting font page {}", eldest.getKey());
                    return true;
                }
                return false;
            }
        };

    private int hits = 0;
    private int misses = 0;

    private FontPageManager() {}

    public boolean isPageLoaded(int codepoint) {
        int page = codepoint / 256;
        if (page == 0) return true; // ASCII page is always loaded
        return loadedPages.containsKey(page);
    }

    public void loadPage(int codepoint) {
        int page = codepoint / 256;
        if (page == 0) return;
        
        if (!loadedPages.containsKey(page)) {
            misses++;
            loadedPages.put(page, Boolean.TRUE);
            LOGGER.debug("[Font Page Manager] Loaded font page {}", page);
        } else {
            hits++;
            // Re-insérer pour mettre à jour la récence LRU
            loadedPages.put(page, Boolean.TRUE);
        }
    }

    public String getStats() {
        return String.format("Font Cache: %d pages, %d hits, %d misses", loadedPages.size(), hits, misses);
    }
}
