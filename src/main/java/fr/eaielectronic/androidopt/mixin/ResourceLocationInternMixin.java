package fr.eaielectronic.androidopt.mixin;

import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;


@Mixin(ResourceLocation.class)
public class ResourceLocationInternMixin {

    @ModifyVariable(method = "<init>(Ljava/lang/String;Ljava/lang/String;Lnet/minecraft/resources/ResourceLocation$Dummy;)V", at = @At("HEAD"), ordinal = 0, argsOnly = true)
    private String androidopt$internNamespace(String namespace) {
        return namespace != null ? namespace.intern() : null;
    }

    @ModifyVariable(method = "<init>(Ljava/lang/String;Ljava/lang/String;Lnet/minecraft/resources/ResourceLocation$Dummy;)V", at = @At("HEAD"), ordinal = 1, argsOnly = true)
    private String androidopt$internPath(String path) {
        return path != null ? path.intern() : null;
    }
}
