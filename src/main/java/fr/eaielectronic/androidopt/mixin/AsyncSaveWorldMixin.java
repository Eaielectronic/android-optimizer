package fr.eaielectronic.androidopt.mixin;

import fr.eaielectronic.androidopt.ConfigGuard;
import fr.eaielectronic.androidopt.OptConfig;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = MinecraftServer.class, remap = true)
public class AsyncSaveWorldMixin {

    @Inject(method = "saveEverything", at = @At("HEAD"), cancellable = true)
    private void androidopt$asyncSaveWorld(boolean suppressLog, boolean flush, boolean force, CallbackInfoReturnable<Boolean> cir) {
        if (!ConfigGuard.isReady() || !ConfigGuard.getBool(OptConfig.ASYNC_WORLD_SAVE, true)) {
            return;
        }

        // Si on est déjà dans notre thread asynchrone, on laisse la méthode originale s'exécuter.
        if (Thread.currentThread().getName().startsWith("AsyncWorldSave")) {
            return;
        }

        // Si le jeu nous demande de "flush" (écrire absolument maintenant) ou force,
        // c'est souvent parce qu'on quitte le monde. On le garde en synchrone pour éviter la corruption.
        if (flush || force) {
            return;
        }

        MinecraftServer server = (MinecraftServer) (Object) this;

        // On lance la sauvegarde sur un thread secondaire pour débloquer le Server Thread
        // (et par extension, empêcher le Render Thread de freeze en attendant la fin de la sauvegarde).
        Thread asyncSaveThread = new Thread(() -> {
            try {
                // On rappelle saveEverything, mais cette fois sur ce thread secondaire.
                // L'exécution passera le premier if (Thread.currentThread() == "AsyncWorldSave...")
                server.saveEverything(suppressLog, flush, force);
                fr.eaielectronic.androidopt.AndroidOptMod.LOGGER.info("[AndroidOpt] Sauvegarde asynchrone du monde terminée avec succès.");
            } catch (Exception e) {
                fr.eaielectronic.androidopt.AndroidOptMod.LOGGER.error("[AndroidOpt] Erreur mineure (CME) lors de la sauvegarde asynchrone du monde.", e);
            }
        }, "AsyncWorldSave-" + System.currentTimeMillis());
        
        asyncSaveThread.setPriority(Thread.MIN_PRIORITY); // Priorité basse pour ne pas gêner le rendu
        asyncSaveThread.start();

        // On dit à Minecraft "Oui oui, j'ai sauvegardé"
        cir.setReturnValue(true);
    }
}
