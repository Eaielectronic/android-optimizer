package fr.eaielectronic.androidopt.mixin;

import fr.eaielectronic.androidopt.AndroidOptMod;
import fr.eaielectronic.androidopt.ConfigGuard;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Field;
import java.util.Map;


@Mixin(targets = "com.simibubi.create.foundation.ponder.CreatePonderPlugin",
       remap = false)
public class PonderCacheClearMixin {

    @Inject(
        method = "register",
        at = @At("RETURN"),
        require = 0,
        remap = false
    )
    private void androidopt$clearPonderCache(CallbackInfo ci) {
        if (!ConfigGuard.isReady()) return;

        int cleared = 0;

        String[] registryClasses = {
            "net.createmod.ponder.foundation.PonderRegistry",
            "net.createmod.ponder.PonderRegistry",
            "com.simibubi.create.foundation.ponder.PonderRegistry"
        };

        for (String className : registryClasses) {
            try {
                Class<?> reg = Class.forName(className);
                for (Field f : reg.getDeclaredFields()) {
                    if (Map.class.isAssignableFrom(f.getType())) {
                        f.setAccessible(true);
                        Object val = f.get(null);
                        if (val instanceof Map<?,?> map) {
                            String name = f.getName().toLowerCase();
                            if (name.contains("scene") || name.contains("compiled") || name.contains("cache")) {
                                map.clear();
                                cleared++;
                            }
                        }
                    }
                }
                if (cleared > 0) break;
            } catch (ClassNotFoundException ignored) {
            } catch (Exception e) {
                AndroidOptMod.LOGGER.debug("[AndroidOpt] PonderCacheClearMixin : {}", e.getMessage());
            }
        }

        if (cleared > 0) {
            AndroidOptMod.LOGGER.info("[AndroidOpt] PonderCacheClearMixin : {} caches Ponder vidés.", cleared);
        }
    }
}
