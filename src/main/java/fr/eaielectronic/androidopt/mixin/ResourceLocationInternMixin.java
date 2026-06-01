package fr.eaielectronic.androidopt.mixin;

import fr.eaielectronic.androidopt.memory.ResourceLocationPool;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ResourceLocation.class)
public class ResourceLocationInternMixin {

    @ModifyVariable(method = "<init>(Ljava/lang/String;Ljava/lang/String;Lnet/minecraft/resources/ResourceLocation$Dummy;)V", at = @At("HEAD"), ordinal = 0, argsOnly = true)
    private static String androidopt$internNamespace(String namespace) {
        return namespace != null ? namespace.intern() : null;
    }

    @ModifyVariable(method = "<init>(Ljava/lang/String;Ljava/lang/String;Lnet/minecraft/resources/ResourceLocation$Dummy;)V", at = @At("HEAD"), ordinal = 1, argsOnly = true)
    private static String androidopt$internPath(String path) {
        return path != null ? path.intern() : null;
    }

    @Inject(method = "tryParse", at = @At("RETURN"), cancellable = true)
    private static void androidopt$onTryParse(String string, CallbackInfoReturnable<ResourceLocation> cir) {
        ResourceLocation rl = cir.getReturnValue();
        if (rl != null) {
            cir.setReturnValue(ResourceLocationPool.INSTANCE.intern(rl));
        }
    }

    @Inject(method = "tryBuild", at = @At("RETURN"), cancellable = true)
    private static void androidopt$onTryBuild(String namespace, String path, CallbackInfoReturnable<ResourceLocation> cir) {
        ResourceLocation rl = cir.getReturnValue();
        if (rl != null) {
            cir.setReturnValue(ResourceLocationPool.INSTANCE.intern(rl));
        }
    }

    @Inject(method = "fromNamespaceAndPath", at = @At("RETURN"), cancellable = true)
    private static void androidopt$onFromNamespaceAndPath(String namespace, String path, CallbackInfoReturnable<ResourceLocation> cir) {
        ResourceLocation rl = cir.getReturnValue();
        if (rl != null) {
            cir.setReturnValue(ResourceLocationPool.INSTANCE.intern(rl));
        }
    }

    @Inject(method = "parse", at = @At("RETURN"), cancellable = true)
    private static void androidopt$onParse(String string, CallbackInfoReturnable<ResourceLocation> cir) {
        ResourceLocation rl = cir.getReturnValue();
        if (rl != null) {
            cir.setReturnValue(ResourceLocationPool.INSTANCE.intern(rl));
        }
    }

    @Inject(method = "withDefaultNamespace", at = @At("RETURN"), cancellable = true)
    private static void androidopt$onWithDefaultNamespace(String string, CallbackInfoReturnable<ResourceLocation> cir) {
        ResourceLocation rl = cir.getReturnValue();
        if (rl != null) {
            cir.setReturnValue(ResourceLocationPool.INSTANCE.intern(rl));
        }
    }
}
