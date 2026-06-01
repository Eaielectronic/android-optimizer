package fr.eaielectronic.androidopt.integration.sodium;

import fr.eaielectronic.androidopt.OptConfig;
import org.embeddedt.embeddium.api.options.structure.OptionImpl;
import org.embeddedt.embeddium.api.options.structure.OptionGroup;
import org.embeddedt.embeddium.api.options.structure.OptionPage;
import org.embeddedt.embeddium.api.options.control.TickBoxControl;
import org.embeddedt.embeddium.api.options.control.SliderControl;
import org.embeddedt.embeddium.api.options.control.ControlValueFormatter;
import org.embeddedt.embeddium.api.options.structure.OptionStorage;
import org.embeddedt.embeddium.api.options.OptionIdentifier;
import org.embeddedt.embeddium.api.options.structure.OptionImpact;
import net.minecraft.network.chat.Component;
import net.neoforged.fml.ModList;

import java.util.ArrayList;
import java.util.List;

public class AndroidOptSodiumPage {

    public static class AndroidOptStorage implements OptionStorage<Object> {
        public static final AndroidOptStorage INSTANCE = new AndroidOptStorage();
        @Override public Object getData() { return this; }
        @Override public void save() { 
            OptConfig.SPEC.save();
        }
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

        toggleGroup.add(OptionImpl.createBuilder(Boolean.class, AndroidOptStorage.INSTANCE)
            .setName(Component.literal("Lazy JEI Index"))
            .setTooltip(Component.literal("Indexation paresseuse des items JEI pour éviter le freeze au démarrage"))
            .setControl(TickBoxControl::new)
            .setBinding(
                (opts, value) -> OptConfig.LAZY_JEI_INDEX.set(value),
                (opts) -> OptConfig.LAZY_JEI_INDEX.get()
            )
            .build()
        );

        toggleGroup.add(OptionImpl.createBuilder(Boolean.class, AndroidOptStorage.INSTANCE)
            .setName(Component.literal("Lazy Fonts"))
            .setTooltip(Component.literal("Chargement à la demande des pages de glyphes Unicode"))
            .setControl(TickBoxControl::new)
            .setBinding(
                (opts, value) -> OptConfig.LAZY_FONTS.set(value),
                (opts) -> OptConfig.LAZY_FONTS.get()
            )
            .build()
        );

        toggleGroup.add(OptionImpl.createBuilder(Boolean.class, AndroidOptStorage.INSTANCE)
            .setName(Component.literal("Model Eviction"))
            .setTooltip(Component.literal("Éviction des modèles 3D complexes non utilisés en RAM"))
            .setControl(TickBoxControl::new)
            .setBinding(
                (opts, value) -> OptConfig.MODEL_EVICTION.set(value),
                (opts) -> OptConfig.MODEL_EVICTION.get()
            )
            .build()
        );

        toggleGroup.add(OptionImpl.createBuilder(Boolean.class, AndroidOptStorage.INSTANCE)
            .setName(Component.literal("NBT Off-Heap"))
            .setTooltip(Component.literal("Compression et stockage hors-tas des NBT d'entités distantes"))
            .setControl(TickBoxControl::new)
            .setBinding(
                (opts, value) -> OptConfig.NBT_OFFHEAP.set(value),
                (opts) -> OptConfig.NBT_OFFHEAP.get()
            )
            .build()
        );

        groups.add(toggleGroup.build());

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

        // --- NATIVE GL ENGINE GROUP (CONDITIONNEL) ---
        if (ModList.get().isLoaded("nativeglengine")) {
            OptionGroup.Builder nativeGlGroup = OptionGroup.createBuilder();

            nativeGlGroup.add(OptionImpl.createBuilder(Integer.class, AndroidOptStorage.INSTANCE)
                .setName(Component.literal("GPU Budget (%)"))
                .setTooltip(Component.literal("Pourcentage de la RAM unifiée allouée au GPU (NativeGL)"))
                .setControl(option -> new SliderControl(option, 10, 100, 5, ControlValueFormatter.percentage()))
                .setBinding(
                    (opts, value) -> OptConfig.NATIVE_GL_GPU_BUDGET_PERCENT.set(value),
                    (opts) -> OptConfig.NATIVE_GL_GPU_BUDGET_PERCENT.get()
                )
                .build()
            );

            nativeGlGroup.add(OptionImpl.createBuilder(Boolean.class, AndroidOptStorage.INSTANCE)
                .setName(Component.literal("Hardware TexCompress"))
                .setTooltip(Component.literal("Active la compression ASTC/ETC2 matérielle en C++"))
                .setControl(TickBoxControl::new)
                .setBinding(
                    (opts, value) -> OptConfig.NATIVE_GL_TEX_COMPRESS.set(value),
                    (opts) -> OptConfig.NATIVE_GL_TEX_COMPRESS.get()
                )
                .setImpact(OptionImpact.HIGH)
                .build()
            );

            nativeGlGroup.add(OptionImpl.createBuilder(Boolean.class, AndroidOptStorage.INSTANCE)
                .setName(Component.literal("Vertex Quantizer"))
                .setTooltip(Component.literal("Active la quantisation SIMD des Vertex en C++"))
                .setControl(TickBoxControl::new)
                .setBinding(
                    (opts, value) -> OptConfig.NATIVE_GL_VERTEX_QUANT.set(value),
                    (opts) -> OptConfig.NATIVE_GL_VERTEX_QUANT.get()
                )
                .setImpact(OptionImpact.MEDIUM)
                .build()
            );

            groups.add(nativeGlGroup.build());
        }

        return new OptionPage(OptionIdentifier.create(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("androidopt", "settings")), Component.literal("Android Opt"), com.google.common.collect.ImmutableList.copyOf(groups));
    }
}
