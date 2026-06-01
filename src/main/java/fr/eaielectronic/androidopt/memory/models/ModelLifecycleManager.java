package fr.eaielectronic.androidopt.memory.models;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Gestionnaire du cycle de vie des modèles 3D avec éviction intelligente.
 *
 * Machine à états par modèle :
 *   LOADED → EVICTED → REBAKING → LOADED
 *
 * Quand un modèle est EVICTED, le rendu utilise un FlatSpriteModel (texture 2D plate)
 * au lieu du cube rose/noir. Le re-bake 3D se fait en thread de fond.
 *
 * Le joueur voit l'icône 2D pendant 1-3 frames, puis la 3D revient sans stutter.
 *
 * Ce gestionnaire est appelé périodiquement par le MemoryWatchdog
 * quand le heap dépasse le seuil configuré.
 */
public class ModelLifecycleManager {
    private static final Logger LOGGER = LoggerFactory.getLogger("AndroidOpt");

    public static final ModelLifecycleManager INSTANCE = new ModelLifecycleManager();

    private final ModelEvictionPolicy policy = ModelEvictionPolicy.INSTANCE;

    // Thread de fond pour le re-bake asynchrone (priorité basse)
    private final ExecutorService rebakeExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "AndroidOpt-ModelRebaker");
        t.setDaemon(true);
        t.setPriority(Thread.MIN_PRIORITY);
        return t;
    });

    private boolean enabled = true;
    private long lastEvictionRun = 0;
    private static final long EVICTION_COOLDOWN_MS = 5000; // 5 secondes entre les cycles

    private ModelLifecycleManager() {}

    /**
     * Exécute un cycle d'éviction si nécessaire.
     * Appelé par le MemoryWatchdog toutes les secondes.
     *
     * @param currentModelCount nombre de modèles actuellement chargés
     * @param heapUsagePercent pourcentage d'utilisation du heap Java
     */
    public void runEvictionCycle(int currentModelCount, float heapUsagePercent) {
        if (!enabled) return;

        long now = System.currentTimeMillis();
        if (now - lastEvictionRun < EVICTION_COOLDOWN_MS) return;
        lastEvictionRun = now;

        // Ajuster dynamiquement le budget selon la pression mémoire
        int dynamicBudget = policy.getMaxModelsInRam();
        if (heapUsagePercent > 85) {
            dynamicBudget = (int)(dynamicBudget * 0.7); // Urgence : réduire de 30%
        } else if (heapUsagePercent > 75) {
            dynamicBudget = (int)(dynamicBudget * 0.85); // Pression : réduire de 15%
        }

        // Obtenir les candidats à l'éviction
        policy.setMaxModelsInRam(dynamicBudget);
        List<String> candidates = policy.getEvictionCandidates(currentModelCount);

        if (!candidates.isEmpty()) {
            LOGGER.info("[Model Eviction] Evicting {} models (heap at {:.1f}%, budget={})",
                    candidates.size(), heapUsagePercent, dynamicBudget);

            for (String modelId : candidates) {
                evictModel(modelId);
            }
        }
    }

    /**
     * Évicte un modèle spécifique. Remplace son BakedModel par un FlatSpriteModel.
     */
    private void evictModel(String modelId) {
        policy.markEvicted(modelId);
        try {
            net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
            if (mc != null && mc.getModelManager() instanceof IEvictableModelManager evictable) {
                int hashIdx = modelId.indexOf('#');
                net.minecraft.resources.ResourceLocation rl;
                String variant;
                if (hashIdx != -1) {
                    rl = net.minecraft.resources.ResourceLocation.tryParse(modelId.substring(0, hashIdx));
                    variant = modelId.substring(hashIdx + 1);
                } else {
                    rl = net.minecraft.resources.ResourceLocation.tryParse(modelId);
                    variant = "inventory";
                }
                if (rl != null) {
                    net.minecraft.client.resources.model.ModelResourceLocation mrl =
                        new net.minecraft.client.resources.model.ModelResourceLocation(rl, variant);
                    evictable.androidopt$evictModel(mrl);
                }
            }
        } catch (Exception e) {
            LOGGER.error("[Model Eviction] Failed to apply eviction for " + modelId, e);
        }
        LOGGER.debug("[Model Eviction] Evicted: {}", modelId);
    }

    /**
     * Demande le re-bake d'un modèle évicté en arrière-plan.
     * Appelé quand le joueur accède à un modèle qui a été évicté
     * (ex: survol dans JEI, ouverture d'un coffre contenant l'item).
     *
     * @param modelId le modèle à restaurer
     * @param rebakeTask le Runnable qui effectue le re-bake réel
     *                   (doit être posté sur le render thread via mc.tell())
     */
    public void requestRebake(String modelId, Runnable rebakeTask) {
        if (!policy.isEvicted(modelId)) return;

        rebakeExecutor.submit(() -> {
            try {
                LOGGER.debug("[Model Rebake] Starting async rebake: {}", modelId);
                rebakeTask.run();
                policy.markRestored(modelId);
                LOGGER.debug("[Model Rebake] Restored: {}", modelId);
            } catch (Exception e) {
                LOGGER.warn("[Model Rebake] Failed for {}: {}", modelId, e.getMessage());
            }
        });
    }

    /**
     * Met à jour la whitelist avec les items de l'inventaire du joueur.
     * Les modèles whitelistés ne sont JAMAIS évictés.
     *
     * @param inventoryModelIds les ResourceLocation des items dans l'inventaire
     */
    public void updateInventoryWhitelist(java.util.Set<String> inventoryModelIds) {
        // La whitelist est gérée par la policy
        // On clear et on remet tout
        // (pas de méthode clearWhitelist dans la policy, donc on ajoute simplement)
        for (String id : inventoryModelIds) {
            policy.addToWhitelist(id);
        }
    }

    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public boolean isEnabled() { return enabled; }

    public String getStats() {
        return policy.getStats();
    }
}
