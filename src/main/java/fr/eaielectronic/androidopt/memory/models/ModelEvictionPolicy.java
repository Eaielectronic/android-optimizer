package fr.eaielectronic.androidopt.memory.models;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Politique d'éviction LFU-LRU hybride pour les BakedModels.
 *
 * ModernFix fait de l'éviction par TTL (Time To Live) simple.
 * Notre politique est plus intelligente : on combine la FRÉQUENCE d'accès
 * (LFU = Least Frequently Used) avec la RÉCENCE (LRU = Least Recently Used).
 *
 * Score = fréquence × décroissance_temporelle
 * Le modèle avec le score le plus bas est évicté en premier.
 *
 * Whitelist : les items dans l'inventaire du joueur ne sont JAMAIS évictés.
 * Budget configurable : nombre max de modèles en RAM (défaut 2000).
 *
 * Quand un modèle est évicté, on retourne un FlatSpriteModel (texture 2D plate)
 * au lieu du cube rose/noir de ModernFix. Le re-bake 3D se fait en thread de fond.
 *
 * Gain estimé : 80-200 Mo de heap selon le nombre de mods.
 */
public class ModelEvictionPolicy {
    private static final Logger LOGGER = LoggerFactory.getLogger("AndroidOpt");

    public static final ModelEvictionPolicy INSTANCE = new ModelEvictionPolicy();

    // Clé = ResourceLocation du modèle (en String pour éviter les refs RL)
    // Valeur = métadonnées d'accès
    private final ConcurrentHashMap<String, ModelAccessInfo> accessMap = new ConcurrentHashMap<>();

    // Whitelist des modèles protégés (inventaire joueur, main hand, etc.)
    private final Set<String> whitelist = ConcurrentHashMap.newKeySet();

    // Configuration
    private int maxModelsInRam = 2000;

    // Statistiques
    private final AtomicInteger totalEvictions = new AtomicInteger(0);
    private final AtomicInteger totalRestores = new AtomicInteger(0);
    private final AtomicLong estimatedBytesFreed = new AtomicLong(0);

    private ModelEvictionPolicy() {}

    /**
     * Métadonnées d'accès pour un modèle 3D.
     */
    public static class ModelAccessInfo {
        public final String modelId;
        public long lastAccessTime;     // timestamp du dernier accès
        public int accessCount;          // nombre total d'accès
        public boolean isEvicted;        // true si le modèle est actuellement évicté
        public int estimatedSizeBytes;   // taille estimée en heap (calculée au premier chargement)

        public ModelAccessInfo(String modelId) {
            this.modelId = modelId;
            this.lastAccessTime = System.currentTimeMillis();
            this.accessCount = 1;
            this.isEvicted = false;
            this.estimatedSizeBytes = 0;
        }

        /**
         * Calcule le score d'éviction LFU-LRU hybride.
         * Score bas = candidat à l'éviction.
         * Score = accessCount × décroissance exponentielle basée sur l'âge.
         */
        public double evictionScore() {
            long ageMs = System.currentTimeMillis() - lastAccessTime;
            // Décroissance : le score diminue de moitié toutes les 30 secondes d'inactivité
            double decay = Math.pow(0.5, ageMs / 30000.0);
            return accessCount * decay;
        }
    }

    /**
     * Enregistre un accès à un modèle (appelé par le Mixin sur ModelManager).
     * Met à jour le timestamp et le compteur de fréquence.
     */
    public void recordAccess(String modelId) {
        accessMap.compute(modelId, (key, info) -> {
            if (info == null) {
                return new ModelAccessInfo(modelId);
            }
            info.lastAccessTime = System.currentTimeMillis();
            info.accessCount++;
            return info;
        });
    }

    /**
     * Enregistre la taille estimée d'un modèle au premier chargement.
     */
    public void recordSize(String modelId, int sizeBytes) {
        ModelAccessInfo info = accessMap.get(modelId);
        if (info != null) {
            info.estimatedSizeBytes = sizeBytes;
        }
    }

    /**
     * Ajoute un modèle à la whitelist (ne sera jamais évicté).
     * Appelé quand le joueur a un item dans son inventaire.
     */
    public void addToWhitelist(String modelId) {
        whitelist.add(modelId);
    }

    /**
     * Retire un modèle de la whitelist.
     */
    public void removeFromWhitelist(String modelId) {
        whitelist.remove(modelId);
    }

    /**
     * Détermine quels modèles doivent être évictés pour rester sous le budget.
     * Retourne les modèles à évicter, triés par score d'éviction croissant.
     *
     * @param currentCount nombre de modèles actuellement en RAM
     * @return liste des modelId à évicter (peut être vide)
     */
    public java.util.List<String> getEvictionCandidates(int currentCount) {
        if (currentCount <= maxModelsInRam) {
            return java.util.Collections.emptyList();
        }

        int toEvict = currentCount - maxModelsInRam;

        // Trier tous les modèles non-whitelistés par score croissant
        return accessMap.values().stream()
                .filter(info -> !info.isEvicted)
                .filter(info -> !whitelist.contains(info.modelId))
                .sorted((a, b) -> Double.compare(a.evictionScore(), b.evictionScore()))
                .limit(toEvict)
                .map(info -> info.modelId)
                .collect(java.util.stream.Collectors.toList());
    }

    /**
     * Marque un modèle comme évicté.
     */
    public void markEvicted(String modelId) {
        ModelAccessInfo info = accessMap.get(modelId);
        if (info != null) {
            info.isEvicted = true;
            totalEvictions.incrementAndGet();
            estimatedBytesFreed.addAndGet(info.estimatedSizeBytes);
        }
    }

    /**
     * Marque un modèle comme restauré (re-baké en arrière-plan).
     */
    public void markRestored(String modelId) {
        ModelAccessInfo info = accessMap.get(modelId);
        if (info != null) {
            info.isEvicted = false;
            info.lastAccessTime = System.currentTimeMillis();
            info.accessCount++; // boost car le joueur l'a demandé
            totalRestores.incrementAndGet();
            estimatedBytesFreed.addAndGet(-info.estimatedSizeBytes);
        }
    }

    /**
     * Vérifie si un modèle est actuellement évicté.
     */
    public boolean isEvicted(String modelId) {
        ModelAccessInfo info = accessMap.get(modelId);
        return info != null && info.isEvicted;
    }

    // ═══ Configuration ═══

    public void setMaxModelsInRam(int max) {
        this.maxModelsInRam = Math.max(500, max); // minimum 500
    }

    public int getMaxModelsInRam() { return maxModelsInRam; }

    // ═══ Statistiques ═══

    public int getTrackedModelCount() { return accessMap.size(); }
    public int getEvictedCount() {
        return (int) accessMap.values().stream().filter(i -> i.isEvicted).count();
    }
    public int getWhitelistSize() { return whitelist.size(); }
    public int getTotalEvictions() { return totalEvictions.get(); }
    public int getTotalRestores() { return totalRestores.get(); }

    public String getStats() {
        long freedMB = estimatedBytesFreed.get() / (1024 * 1024);
        return String.format("Models: %d tracked, %d evicted, %d whitelisted, ~%d MB freed (%d evictions, %d restores)",
                accessMap.size(), getEvictedCount(), whitelist.size(), freedMB,
                totalEvictions.get(), totalRestores.get());
    }
}
