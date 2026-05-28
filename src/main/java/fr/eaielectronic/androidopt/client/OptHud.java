package fr.eaielectronic.androidopt.client;

import fr.eaielectronic.androidopt.AndroidDetector;
import fr.eaielectronic.androidopt.AndroidOptMod;
import fr.eaielectronic.androidopt.OptConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiEvent;

import java.util.ArrayList;
import java.util.List;


@EventBusSubscriber(modid = AndroidOptMod.MODID, value = Dist.CLIENT)
public class OptHud {

    private static final int COLOR_INFO = 0xFFFFFFFF;

    private static long cachedUsedMB = 0;
    private static long cachedMaxMB  = 0;
    private static int  heapTick     = 0;

    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post event) {
        if (!OptConfig.isActive()) return;
        if (!OptConfig.SHOW_HUD.get()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.getDebugOverlay().showDebugScreen()) return;

        // Heap — mise à jour 1x/s
        if (++heapTick >= 20) {
            heapTick = 0;
            Runtime rt   = Runtime.getRuntime();
            cachedUsedMB = (rt.totalMemory() - rt.freeMemory()) / (1024 * 1024);
            cachedMaxMB  = rt.maxMemory() / (1024 * 1024);
        }

        GuiGraphics gfx  = event.getGuiGraphics();
        Font        font = mc.font;
        int         fps  = mc.getFps();

        List<String> lines = new ArrayList<>();

        lines.add("§6[AndroidOpt]");

        // FPS
        if (OptConfig.HUD_SHOW_FPS.get()) {
            String fc = fps >= 25 ? "§a" : fps >= 15 ? "§e" : "§c";
            lines.add("§fFPS: " + fc + fps);
        }

        // Heap
        if (OptConfig.HUD_SHOW_HEAP.get()) {
            double hr = cachedMaxMB > 0 ? (double) cachedUsedMB / cachedMaxMB : 0;
            String hc = hr < 0.70 ? "§a" : hr < 0.85 ? "§e" : "§c";
            lines.add("§fRAM: " + hc + cachedUsedMB + "§f/" + cachedMaxMB + "Mo");
        }

        // Frame budget et thermal
        if (OptConfig.HUD_SHOW_BUDGET.get()) {
            String thermalDisplay = ThermalMonitor.getHudDisplay();
            String thermalPart = thermalDisplay.isEmpty() ? "" : " §7| " + thermalDisplay;
            lines.add("§fBudget: " + FrameBudgetManager.getBudgetLevelDisplay() + thermalPart);
        }

        // Mode + SoC
        if (OptConfig.HUD_SHOW_SOC.get()) {
            String mode = AndroidDetector.IS_ANDROID ? "§aAndroid"
                        : OptConfig.FORCE_ENABLE.get() ? "§ePC forcé" : "§7Inactif";
            lines.add("§fMode: " + mode);

            if (AndroidDetector.IS_ANDROID || OptConfig.FORCE_ENABLE.get()) {
                fr.eaielectronic.androidopt.SocProfile soc = fr.eaielectronic.androidopt.SocDetector.DETECTED_PROFILE;
                String socColor = fr.eaielectronic.androidopt.SocDetector.detectionSucceeded ? "§a" : "§e";
                lines.add("§fSoC: " + socColor + soc.name);
            }
        }

        // FreezeDebugger stats
        if (OptConfig.HUD_SHOW_FREEZE.get()) {
            lines.add(FreezeDebugger.getHudSummary());
        }

        // Stats dynamiques
        int berCulled  = CreateBerCuller.CULLED_POSITIONS.size();
        int beSkipped  = TickSkipper.THROTTLED_POSITIONS.size();
        if (berCulled > 0 || beSkipped > 0) {
            lines.add("§7BER: §e-" + berCulled + " §7BE: §e-" + beSkipped);
        }

        if (OptConfig.HUD_SHOW_OPTIMS.get()) {
            lines.add("§7──────────────");

            // Rendu
            lines.add(t(OptConfig.RENDER_OPTS.get(),             "Rendu")
                    + " " + t(OptConfig.DISABLE_CLOUDS.get(),    "Clouds"));
            lines.add(t(OptConfig.MINIMAL_PARTICLES.get(),       "Ptcl")
                    + " " + t(OptConfig.DISABLE_AMBIENT_OCCLUSION.get(), "AO"));
            lines.add(t(OptConfig.WEATHER_SUPPRESSOR.get(),      "Météo")
                    + " " + t(OptConfig.RENDER_SCALE_ENABLED.get(), "Scale75"));

            // Mémoire
            lines.add(t(OptConfig.MEMORY_WATCHDOG.get(),         "GC")
                    + " " + t(OptConfig.TEXTURE_CACHE_EVICTOR.get(), "TexEvict"));
            lines.add(t(OptConfig.SECTION_BUFFER_LIMIT.get(),    "BufLim")
                    + " " + t(OptConfig.CHUNK_UNLOADER.get(),    "Chunks"));

            // Entités & Create
            lines.add(t(OptConfig.ENTITY_THROTTLER.get(),        "Entity")
                    + " " + t(OptConfig.TICK_SKIPPER.get(),      "BESkip"));
            lines.add(t(OptConfig.CREATE_BER_CULLER.get(),       "BERCull")
                    + " " + t(OptConfig.CHUNK_REBUILD_THROTTLER.get(), "Rebuild"));
            lines.add(t(OptConfig.CREATE_PARTICLES_FILTER.get(), "CrPtcl")
                    + " " + t(OptConfig.STOPPED_CONTRAPTION_SKIP.get(), "StopCtr"));

            // Son
            lines.add(t(OptConfig.AMBIENT_SOUND_SUPPRESSOR.get(), "Sound"));
        }

        if (lines.size() <= 1) return; // rien à afficher sauf l'en-tête

        int screenW = mc.getWindow().getGuiScaledWidth();
        int lineH   = font.lineHeight + 1;
        int w       = 118;
        int x       = screenW - w - 2;
        int y       = 4;

        gfx.fill(x - 2, y - 2, screenW - 2, y + lines.size() * lineH + 2, 0x99000000);

        for (String line : lines) {
            gfx.drawString(font, line, x, y, COLOR_INFO);
            y += lineH;
        }
    }

    
    private static String t(boolean on, String label) {
        return (on ? "§a✓" : "§c✗") + "§7" + label;
    }
}
