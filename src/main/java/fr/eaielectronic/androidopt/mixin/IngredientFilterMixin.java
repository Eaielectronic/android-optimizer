package fr.eaielectronic.androidopt.mixin;

import fr.eaielectronic.androidopt.integration.jei.LazyJeiIndex;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = {
    "mezz.jei.gui.ingredients.IngredientFilter",
    "mezz.jei.library.ingredients.IngredientFilter",
    "mezz.jei.ingredients.IngredientFilter"
}, remap = false)
public abstract class IngredientFilterMixin {

    @Shadow(remap = false)
    public abstract void addIngredient(Object element);

    @Unique
    private static final ThreadLocal<Boolean> androidopt$inBypass = ThreadLocal.withInitial(() -> false);

    @Inject(method = "addIngredient", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void androidopt$onAddIngredient(Object element, CallbackInfo ci) {
        if (androidopt$inBypass.get()) return;

        if (fr.eaielectronic.androidopt.OptConfig.isActive() && fr.eaielectronic.androidopt.OptConfig.LAZY_JEI_INDEX.get()) {
            try {
                // Obtenir l'ingrédient de l'élément par réflexion
                java.lang.reflect.Method m = element.getClass().getMethod("getIngredient");
                Object ingredient = m.invoke(element);
                if (ingredient != null) {
                    String itemId = ingredient.toString();
                    if (!LazyJeiIndex.INSTANCE.isIndexed(itemId)) {
                        ci.cancel();
                        // Demander l'indexation asynchrone en tâche de fond
                        LazyJeiIndex.INSTANCE.requestIndex(itemId, () -> {
                            androidopt$inBypass.set(true);
                            try {
                                this.addIngredient(element);
                            } finally {
                                androidopt$inBypass.set(false);
                            }
                        });
                    }
                }
            } catch (Exception e) {
                // Fallback direct en cas de problème de réflexion
            }
        }
    }
}
