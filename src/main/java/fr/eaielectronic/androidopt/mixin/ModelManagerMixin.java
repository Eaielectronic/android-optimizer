package fr.eaielectronic.androidopt.mixin;

import fr.eaielectronic.androidopt.memory.models.FlatSpriteModel;
import fr.eaielectronic.androidopt.memory.models.IEvictableModelManager;
import fr.eaielectronic.androidopt.memory.models.ModelEvictionPolicy;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelManager;
import net.minecraft.client.resources.model.ModelResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.lang.ref.SoftReference;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Mixin(ModelManager.class)
public class ModelManagerMixin implements IEvictableModelManager {

    @Shadow
    private Map<ModelResourceLocation, BakedModel> bakedRegistry;

    // Cache SoftReference des modèles originaux évictés
    private final Map<ModelResourceLocation, SoftReference<BakedModel>> androidopt$softCache = new ConcurrentHashMap<>();

    @Inject(method = "getModel", at = @At("HEAD"), cancellable = true)
    private void androidopt$onGetModel(ModelResourceLocation location, CallbackInfoReturnable<BakedModel> cir) {
        if (!fr.eaielectronic.androidopt.OptConfig.isActive() || !fr.eaielectronic.androidopt.OptConfig.MODEL_EVICTION.get()) return;

        String locStr = location.toString();
        // Enregistrer l'accès pour la politique LRU-LFU
        ModelEvictionPolicy.INSTANCE.recordAccess(locStr);

        // Si le modèle est marqué comme évicté
        if (ModelEvictionPolicy.INSTANCE.isEvicted(locStr)) {
            SoftReference<BakedModel> ref = androidopt$softCache.get(location);
            BakedModel original = (ref != null) ? ref.get() : null;

            if (original != null) {
                // Le GC ne l'a pas encore collecté ! On le restaure
                bakedRegistry.put(location, original);
                ModelEvictionPolicy.INSTANCE.markRestored(locStr);
                cir.setReturnValue(original);
            } else {
                // Le modèle a été collecté ou n'est pas en cache soft.
                // On s'assure que la registry contient bien notre FlatSpriteModel
                BakedModel registered = bakedRegistry.get(location);
                if (!(registered instanceof FlatSpriteModel)) {
                    FlatSpriteModel fallback = new FlatSpriteModel(
                        registered != null ? registered.getParticleIcon() : null
                    );
                    bakedRegistry.put(location, fallback);
                    cir.setReturnValue(fallback);
                }
            }
        }
    }

    // Clear le cache lors des rechargements de ressources
    @Inject(method = "apply", at = @At("RETURN"))
    private void androidopt$onApplyReload(@Coerce Object reloadState, net.minecraft.util.profiling.ProfilerFiller profiler, CallbackInfo ci) {
        androidopt$softCache.clear();
    }

    @Override
    public void androidopt$evictModel(ModelResourceLocation location) {
        if (!fr.eaielectronic.androidopt.OptConfig.isActive() || !fr.eaielectronic.androidopt.OptConfig.MODEL_EVICTION.get()) return;
        if (bakedRegistry == null) return;
        BakedModel original = bakedRegistry.get(location);
        if (original != null && !(original instanceof FlatSpriteModel)) {
            // Sauvegarder dans le cache soft
            androidopt$softCache.put(location, new SoftReference<>(original));
            // Remplacer par le modèle de secours
            FlatSpriteModel fallback = new FlatSpriteModel(original.getParticleIcon());
            bakedRegistry.put(location, fallback);
            ModelEvictionPolicy.INSTANCE.markEvicted(location.toString());
        }
    }
}
