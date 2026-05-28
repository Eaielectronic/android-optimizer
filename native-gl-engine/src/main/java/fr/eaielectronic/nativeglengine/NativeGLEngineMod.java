package fr.eaielectronic.nativeglengine;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * NativeGLEngine — Moteur de rendu natif Android pour Minecraft Java.
 * 
 * Ce mod est indépendant et se connecte optionnellement à Android Optimizer
 * via {@link AndroidOptBridge} pour les réglages unifiés.
 * 
 * Architecture :
 * - ShaderCompiler : compilation async GLSL → SPIR-V → ESSL avec cache SHA-256
 * - NativeMemoryManager : budget GPU réel via VMA + VK_EXT_memory_budget
 * - InterceptLayer : hooks GL (PLT hooking) pour déduplication d'état et throttling
 * - RendererDetector : détection MobileGlues / GL4ES / Zink / ANGLE
 */
@Mod(NativeGLEngineMod.MOD_ID)
public class NativeGLEngineMod {

    public static final String MOD_ID = "nativeglengine";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public NativeGLEngineMod(IEventBus modEventBus, ModContainer modContainer) {
        LOGGER.info("[NativeGLEngine] Initialisation du mod NativeGLEngine v0.1.0");

        // Enregistrement de la configuration client
        modContainer.registerConfig(ModConfig.Type.CLIENT, NativeGLConfig.SPEC);

        modEventBus.addListener(this::onCommonSetup);
        modEventBus.addListener(this::onClientSetup);

        // Tenter le chargement de la bibliothèque native (.so)
        NativeLib.tryLoad();
    }

    /**
     * Setup commun (client + serveur).
     * Détection du SoC et connexion au mod Android Optimizer si présent.
     */
    private void onCommonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            LOGGER.info("[NativeGLEngine] Common setup...");

            // Détecter la présence d'Android Optimizer
            AndroidOptBridge.init();

            if (AndroidOptBridge.isAndroidOptPresent()) {
                LOGGER.info("[NativeGLEngine] Android Optimizer détecté — mode connecté");
            } else {
                LOGGER.info("[NativeGLEngine] Android Optimizer absent — mode autonome");
            }

            // Détection du SoC via Android Optimizer (si présent) ou standalone
            String socName = AndroidOptBridge.getSocName();
            LOGGER.info("[NativeGLEngine] SoC détecté : {}", socName);
        });
    }

    /**
     * Setup client uniquement.
     * Initialise le renderer detector, le shader cache, et le memory manager.
     */
    private void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            LOGGER.info("[NativeGLEngine] Client setup...");

            // Détection du renderer actif (MobileGlues, GL4ES, Zink, ANGLE, Desktop)
            RendererDetector.Renderer renderer = RendererDetector.detect();
            LOGGER.info("[NativeGLEngine] Renderer détecté : {}", renderer);

            // Initialiser le cache shader
            ShaderCacheManager.init();
            LOGGER.info("[NativeGLEngine] Shader cache initialisé ({} entrées en cache disque)",
                ShaderCacheManager.getDiskCacheSize());

            // Initialiser le bridge mémoire native (si .so disponible)
            if (NativeLib.isLoaded()) {
                NativeMemoryBridge.init();
                LOGGER.info("[NativeGLEngine] NativeMemoryManager initialisé (VMA)");

                // Installer les hooks GL
                GLInterceptorBridge.install();
                LOGGER.info("[NativeGLEngine] Hooks GL installés");
            } else {
                LOGGER.warn("[NativeGLEngine] Bibliothèque native non disponible — mode Java seul");
                LOGGER.warn("[NativeGLEngine] Le shader cache et le renderer detector fonctionnent normalement");
                LOGGER.warn("[NativeGLEngine] Le memory manager et les hooks GL sont désactivés");
            }
        });
    }
}
