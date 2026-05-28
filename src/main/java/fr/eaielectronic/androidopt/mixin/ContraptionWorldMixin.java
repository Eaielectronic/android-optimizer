package fr.eaielectronic.androidopt.mixin;

import fr.eaielectronic.androidopt.AndroidOptMod;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Field;


@Mixin(targets = "com.simibubi.create.content.contraptions.Contraption", remap = false)
public class ContraptionWorldMixin {

    private static Field worldField = null;
    private static boolean searched = false;

    @Inject(method = "stop", at = @At("RETURN"), require = 0, remap = false)
    private void androidopt$freeContraptionWorld(Level world, CallbackInfo ci) {
        if (!fr.eaielectronic.androidopt.ConfigGuard.isReady() || !fr.eaielectronic.androidopt.OptConfig.CONTRAPTION_MEMORY_CLEANUP.get()) return;

        if (!searched) {
            searched = true;
            Class<?> clazz = this.getClass();
            while (clazz != null && clazz != Object.class) {
                for (Field f : clazz.getDeclaredFields()) {
                    String name = f.getName().toLowerCase();
                    Class<?> type = f.getType();
                    
                    if (Level.class.isAssignableFrom(type) || 
                        type.getSimpleName().contains("World") || 
                        type.getSimpleName().contains("Level")) {
                        
                        // Ignorer les champs primitifs ou chaines
                        if (type.isPrimitive() || type == String.class) continue;

                        f.setAccessible(true);
                        worldField = f;
                        AndroidOptMod.LOGGER.info("[AndroidOpt] ContraptionWorldMixin: Vrai champ trouvé -> {} (Type: {})", f.getName(), type.getSimpleName());
                        break;
                    }
                }
                if (worldField != null) break;
                clazz = clazz.getSuperclass();
            }
            if (worldField == null) {
                AndroidOptMod.LOGGER.warn("[AndroidOpt] ContraptionWorldMixin: Aucun champ world/level trouvé dans Create 6.x/Sable.");
            }
        }

        if (worldField != null) {
            try {
                worldField.set(this, null);
            } catch (Exception ignored) {}
        }
    }
}
