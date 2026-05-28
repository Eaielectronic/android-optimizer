package fr.eaielectronic.androidopt.mixin;

import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

public class AndroidOptMixinPlugin implements IMixinConfigPlugin {

    private boolean shouldApplySodiumMixin = false;

    @Override
    public void onLoad(String mixinPackage) {
        try {
            Class.forName("net.caffeinemc.mods.sodium.client.gui.SodiumOptionsGUI", false, this.getClass().getClassLoader());
            shouldApplySodiumMixin = true;
        } catch (ClassNotFoundException e) {
            shouldApplySodiumMixin = false;
        }
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        if (mixinClassName.contains("SodiumOptionsGUIMixin")) {
            return shouldApplySodiumMixin;
        }
        return true;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {}

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}
}
