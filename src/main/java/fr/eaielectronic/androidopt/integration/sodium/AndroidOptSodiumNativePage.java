package fr.eaielectronic.androidopt.integration.sodium;

import fr.eaielectronic.androidopt.OptConfig;
import net.caffeinemc.mods.sodium.client.gui.options.OptionImpl;
import net.caffeinemc.mods.sodium.client.gui.options.OptionGroup;
import net.caffeinemc.mods.sodium.client.gui.options.OptionPage;
import net.caffeinemc.mods.sodium.client.gui.options.control.TickBoxControl;
import net.caffeinemc.mods.sodium.client.gui.options.control.SliderControl;
import net.caffeinemc.mods.sodium.client.gui.options.control.ControlValueFormatter;
import net.caffeinemc.mods.sodium.client.gui.options.storage.OptionStorage;
import net.caffeinemc.mods.sodium.client.gui.options.OptionImpact;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

public class AndroidOptSodiumNativePage {

    public static class AndroidOptStorage implements OptionStorage<Object> {
        public static final AndroidOptStorage INSTANCE = new AndroidOptStorage();
        @Override public Object getData() { return this; }
        @Override public void save() { }
    }

    public static OptionPage createPage() {
        List<OptionGroup> groups = new ArrayList<>();
        
        // --- PERFORMANCE GROUP ---
        OptionGroup.Builder perfGroup = OptionGroup.createBuilder();
        
        perfGroup.add(OptionImpl.createBuilder(Integer.class, AndroidOptStorage.INSTANCE)
            .setName(Component.literal("Render Distance"))
            .setTooltip(Component.literal("Distance de rendu forcée (chunks). 0 = ne pas forcer."))
            .setControl(option -> new SliderControl(option, 0, 32, 1, ControlValueFormatter.number()))
            .setBinding(
                (opts, value) -> OptConfig.RENDER_DISTANCE.set(value),
                (opts) -> OptConfig.RENDER_DISTANCE.get()
            )
            .setImpact(OptionImpact.HIGH)
            .build()
        );
        
        perfGroup.add(OptionImpl.createBuilder(Integer.class, AndroidOptStorage.INSTANCE)
            .setName(Component.literal("BER Cull Distance"))
            .setTooltip(Component.literal("Distance de rendu des machines Create (blocs)"))
            .setControl(option -> new SliderControl(option, 4, 64, 1, ControlValueFormatter.number()))
            .setBinding(
                (opts, value) -> OptConfig.BER_CULL_DISTANCE.set(value),
                (opts) -> OptConfig.BER_CULL_DISTANCE.get()
            )
            .setImpact(OptionImpact.HIGH)
            .build()
        );

        perfGroup.add(OptionImpl.createBuilder(Integer.class, AndroidOptStorage.INSTANCE)
            .setName(Component.literal("Max RAM Budget (MB)"))
            .setTooltip(Component.literal("RAM totale maximale allouable à Java (Mo)"))
            .setControl(option -> new SliderControl(option, 1500, 4096, 50, ControlValueFormatter.number()))
            .setBinding(
                (opts, value) -> OptConfig.MAX_TOTAL_RAM_MB.set(value),
                (opts) -> OptConfig.MAX_TOTAL_RAM_MB.get()
            )
            .build()
        );

        perfGroup.add(OptionImpl.createBuilder(Integer.class, AndroidOptStorage.INSTANCE)
            .setName(Component.literal("Off-Heap Arena (MB)"))
            .setTooltip(Component.literal("Taille du cache natif (C++) pour éviter le Java Garbage Collector. Baissez si crash (Out of Memory)."))
            .setControl(option -> new SliderControl(option, 50, 500, 10, ControlValueFormatter.number()))
            .setBinding(
                (opts, value) -> OptConfig.NATIVE_GL_OFF_HEAP_SIZE_MB.set(value),
                (opts) -> OptConfig.NATIVE_GL_OFF_HEAP_SIZE_MB.get()
            )
            .setImpact(OptionImpact.HIGH)
            .build()
        );

        perfGroup.add(OptionImpl.createBuilder(Integer.class, AndroidOptStorage.INSTANCE)
            .setName(Component.literal("GC Threshold (%)"))
            .setTooltip(Component.literal("Seuil déclenchement du Garbage Collector préventif"))
            .setControl(option -> new SliderControl(option, 50, 95, 1, ControlValueFormatter.percentage()))
            .setBinding(
                (opts, value) -> OptConfig.GC_THRESHOLD_PERCENT.set(value),
                (opts) -> OptConfig.GC_THRESHOLD_PERCENT.get()
            )
            .build()
        );

        groups.add(perfGroup.build());

        // --- TOGGLES GROUP ---
        OptionGroup.Builder toggleGroup = OptionGroup.createBuilder();

        toggleGroup.add(OptionImpl.createBuilder(Boolean.class, AndroidOptStorage.INSTANCE)
            .setName(Component.literal("Texture Downscale"))
            .setTooltip(Component.literal("Divise la résolution des textures par 2 (Redémarrage requis)"))
            .setControl(TickBoxControl::new)
            .setBinding(
                (opts, value) -> OptConfig.TEXTURE_DOWNSCALE_ENABLED.set(value),
                (opts) -> OptConfig.TEXTURE_DOWNSCALE_ENABLED.get()
            )
            .setImpact(OptionImpact.HIGH)
            .build()
        );

        toggleGroup.add(OptionImpl.createBuilder(Boolean.class, AndroidOptStorage.INSTANCE)
            .setName(Component.literal("Render Scale (75%)"))
            .setTooltip(Component.literal("Réduit la résolution de rendu (flou mais plus de FPS)"))
            .setControl(TickBoxControl::new)
            .setBinding(
                (opts, value) -> OptConfig.RENDER_SCALE_ENABLED.set(value),
                (opts) -> OptConfig.RENDER_SCALE_ENABLED.get()
            )
            .setImpact(OptionImpact.HIGH)
            .build()
        );

        toggleGroup.add(OptionImpl.createBuilder(Boolean.class, AndroidOptStorage.INSTANCE)
            .setName(Component.literal("Sable UDP Fix"))
            .setTooltip(Component.literal("Élimine les freezes réseau sur Sable"))
            .setControl(TickBoxControl::new)
            .setBinding(
                (opts, value) -> OptConfig.SABLE_UDP_FIX.set(value),
                (opts) -> OptConfig.SABLE_UDP_FIX.get()
            )
            .build()
        );

        groups.add(toggleGroup.build());

        // --- NATIVE GL GROUP ---
        OptionGroup.Builder nativeGlGroup = OptionGroup.createBuilder();

        nativeGlGroup.add(OptionImpl.createBuilder(Boolean.class, AndroidOptStorage.INSTANCE)
            .setName(Component.literal("Off-Heap Arena"))
            .setTooltip(Component.literal("Active l'arène de mémoire Off-Heap pour réduire la pression sur le GC Java."))
            .setControl(TickBoxControl::new)
            .setBinding(
                (opts, value) -> OptConfig.NATIVE_GL_OFF_HEAP_ENABLED.set(value),
                (opts) -> OptConfig.NATIVE_GL_OFF_HEAP_ENABLED.get()
            )
            .setImpact(OptionImpact.HIGH)
            .build()
        );

        nativeGlGroup.add(OptionImpl.createBuilder(Boolean.class, AndroidOptStorage.INSTANCE)
            .setName(Component.literal("Shader Compiler"))
            .setTooltip(Component.literal("Active la compilation asynchrone des shaders via SPIR-V/Shaderc."))
            .setControl(TickBoxControl::new)
            .setBinding(
                (opts, value) -> OptConfig.NATIVE_GL_SHADER_COMPILER.set(value),
                (opts) -> OptConfig.NATIVE_GL_SHADER_COMPILER.get()
            )
            .setImpact(OptionImpact.HIGH)
            .build()
        );

        nativeGlGroup.add(OptionImpl.createBuilder(Boolean.class, AndroidOptStorage.INSTANCE)
            .setName(Component.literal("GL Interceptor"))
            .setTooltip(Component.literal("Active l'interception et la réécriture des commandes OpenGL bas niveau."))
            .setControl(TickBoxControl::new)
            .setBinding(
                (opts, value) -> OptConfig.NATIVE_GL_GL_INTERCEPTOR.set(value),
                (opts) -> OptConfig.NATIVE_GL_GL_INTERCEPTOR.get()
            )
            .build()
        );

        nativeGlGroup.add(OptionImpl.createBuilder(Boolean.class, AndroidOptStorage.INSTANCE)
            .setName(Component.literal("Async Buffers"))
            .setTooltip(Component.literal("Active l'envoi asynchrone des buffers de chunks vers le GPU."))
            .setControl(TickBoxControl::new)
            .setBinding(
                (opts, value) -> OptConfig.NATIVE_GL_ASYNC_BUFFERS.set(value),
                (opts) -> OptConfig.NATIVE_GL_ASYNC_BUFFERS.get()
            )
            .build()
        );

        nativeGlGroup.add(OptionImpl.createBuilder(Boolean.class, AndroidOptStorage.INSTANCE)
            .setName(Component.literal("Hardware TexCompress"))
            .setTooltip(Component.literal("Active la compression matérielle des textures (ASTC/ETC2) via C++."))
            .setControl(TickBoxControl::new)
            .setBinding(
                (opts, value) -> OptConfig.NATIVE_GL_TEX_COMPRESS.set(value),
                (opts) -> OptConfig.NATIVE_GL_TEX_COMPRESS.get()
            )
            .build()
        );

        nativeGlGroup.add(OptionImpl.createBuilder(Boolean.class, AndroidOptStorage.INSTANCE)
            .setName(Component.literal("Vertex Quantizer"))
            .setTooltip(Component.literal("Active la quantisation des vertices en C++."))
            .setControl(TickBoxControl::new)
            .setBinding(
                (opts, value) -> OptConfig.NATIVE_GL_VERTEX_QUANT.set(value),
                (opts) -> OptConfig.NATIVE_GL_VERTEX_QUANT.get()
            )
            .build()
        );

        groups.add(nativeGlGroup.build());

        // --- THERMAL GROUP ---
        OptionGroup.Builder thermalGroup = OptionGroup.createBuilder();

        thermalGroup.add(OptionImpl.createBuilder(Integer.class, AndroidOptStorage.INSTANCE)
            .setName(Component.literal("Warning Temp (°C)"))
            .setTooltip(Component.literal("Température pour activer le Frame Budget REDUCED"))
            .setControl(option -> new SliderControl(option, 35, 99, 1, ControlValueFormatter.number()))
            .setBinding(
                (opts, value) -> OptConfig.THERMAL_WARNING_TEMP.set(value),
                (opts) -> OptConfig.THERMAL_WARNING_TEMP.get()
            )
            .build()
        );

        thermalGroup.add(OptionImpl.createBuilder(Integer.class, AndroidOptStorage.INSTANCE)
            .setName(Component.literal("Critical Temp (°C)"))
            .setTooltip(Component.literal("Température pour forcer le Frame Budget CRITICAL"))
            .setControl(option -> new SliderControl(option, 40, 99, 1, ControlValueFormatter.number()))
            .setBinding(
                (opts, value) -> OptConfig.THERMAL_CRITICAL_TEMP.set(value),
                (opts) -> OptConfig.THERMAL_CRITICAL_TEMP.get()
            )
            .build()
        );

        groups.add(thermalGroup.build());

        return new OptionPage(Component.literal("Android Opt"), com.google.common.collect.ImmutableList.copyOf(groups));
    }
}
