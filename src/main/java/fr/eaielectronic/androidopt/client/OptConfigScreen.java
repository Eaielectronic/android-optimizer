package fr.eaielectronic.androidopt.client;

import fr.eaielectronic.androidopt.OptConfig;
import fr.eaielectronic.androidopt.SocDetector;
import fr.eaielectronic.androidopt.SocProfile;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.ArrayList;
import java.util.List;


public class OptConfigScreen extends Screen {

    private final Screen parent;
    private Page currentPage = Page.HOME;

    private final List<LabelEntry> pendingLabels = new ArrayList<>();
    private record LabelEntry(int cx, int y, String text) {}
    
    private final java.util.Map<ModConfigSpec.ConfigValue<?>, Object> pendingConfig = new java.util.HashMap<>();

    @SuppressWarnings("unchecked")
    private <T> T getPending(ModConfigSpec.ConfigValue<T> cfg) {
        if (pendingConfig.containsKey(cfg)) {
            return (T) pendingConfig.get(cfg);
        }
        return cfg.get();
    }

    private <T> void setPending(ModConfigSpec.ConfigValue<T> cfg, T value) {
        pendingConfig.put(cfg, value);
    }

    private static final int BTN_W = 204;
    private static final int BTN_H = 20;
    private static final int GAP   = 24;

    public OptConfigScreen(Screen parent) {
        super(Component.literal("Android Optimizer"));
        this.parent = parent;
    }

    enum Page {
        HOME, RENDER, MEMORY, CREATE, SOUND, WORLD, SOC, DIAG, THERMAL, DOC, NATIVEGL
    }

    @Override
    protected void init() {
        clearWidgets();
        pendingLabels.clear();

        switch (currentPage) {
            case HOME   -> buildHome();
            case RENDER -> buildRender();
            case MEMORY -> buildMemory();
            case CREATE -> buildCreate();
            case SOUND  -> buildSound();
            case WORLD  -> buildWorld();
            case SOC    -> buildSoc();
            case DIAG   -> buildDiag();
            case THERMAL-> buildThermal();
            case DOC    -> buildDoc();
            case NATIVEGL -> buildNativeGl();
        }

        if (currentPage != Page.HOME) {
            addBtn(this.width / 2 - 75, this.height - 28, 150, BTN_H,
                "◀ Retour", b -> { currentPage = Page.HOME; init(); });
        } else {
            addRenderableWidget(Button.builder(CommonComponents.GUI_DONE,
                b -> { saveAndClose(); })
                .bounds(this.width / 2 - 75, this.height - 28, 150, BTN_H).build());
        }
    }

    
    @SuppressWarnings("unchecked")
    private void saveAndClose() {
        boolean changed = !pendingConfig.isEmpty();
        for (java.util.Map.Entry<ModConfigSpec.ConfigValue<?>, Object> entry : pendingConfig.entrySet()) {
            ((ModConfigSpec.ConfigValue<Object>) entry.getKey()).set(entry.getValue());
        }
        pendingConfig.clear();
        
        if (changed) {
            java.util.concurrent.CompletableFuture.runAsync(() -> {
                try {
                    OptConfig.SPEC.save();
                    fr.eaielectronic.androidopt.AndroidOptMod.LOGGER.info("[OptConfig] Configuration saved async");
                } catch (Exception e) {
                    fr.eaielectronic.androidopt.AndroidOptMod.LOGGER.error("[OptConfig] Failed to save config", e);
                }
            });
        }
        minecraft.setScreen(parent);
    }


    private void buildHome() {
        // Bouton Documentation (en haut à droite)
        addBtn(this.width - 110, 8, 100, 20, "Docs", b -> { currentPage = Page.DOC; init(); });

        int cx = this.width / 2;
        int y  = 34;
        int bw = 180;
        int bh = 20;
        int gap = 24;

        addToggle("androidopt.config.force_pc", OptConfig.FORCE_ENABLE, cx - bw / 2, y, bw, bh);
        y += gap;

        int pw = bw * 2 + 8;
        addBtn(cx - pw / 2, y, pw, bh, "§cMax FPS (désactive RAM/Create lourds)", b -> {
            setPending(OptConfig.MEMORY_WATCHDOG, false);
            setPending(OptConfig.TEXTURE_CACHE_EVICTOR, false);
            setPending(OptConfig.SECTION_BUFFER_LIMIT, false);
            setPending(OptConfig.PONDER_CACHE_CLEAR, false);
            setPending(OptConfig.FLYWHEEL_BUFFER_CULLER, false);
            setPending(OptConfig.BER_CULL_DISTANCE, 8);
            setPending(OptConfig.FLUID_CULL_DISTANCE, 6);
            setPending(OptConfig.GOGGLE_CULL_DISTANCE, 4);
            setPending(OptConfig.ENTITY_THROTTLE_DISTANCE, 16);
            setPending(OptConfig.TICK_SKIP_DISTANCE, 12);
            setPending(OptConfig.RENDER_DISTANCE, 4);
            setPending(OptConfig.CHUNK_REBUILDS_PER_FRAME, 1);
            setPending(OptConfig.CREATE_RENDER_FPS, 10);
            b.setMessage(Component.literal("§a✓ Preset Max FPS appliqué !"));
            init();
        });
        y += gap;

        addBtn(cx - pw / 2, y, pw, bh, "§aTout activer (recommandé)", b -> {
            setPending(OptConfig.RENDER_OPTS, true);
            setPending(OptConfig.DISABLE_CLOUDS, true);
            setPending(OptConfig.DISABLE_AMBIENT_OCCLUSION, true);
            setPending(OptConfig.DISABLE_ENTITY_SHADOWS, true);
            setPending(OptConfig.MINIMAL_PARTICLES, true);
            setPending(OptConfig.CREATE_PARTICLES_FILTER, true);
            setPending(OptConfig.MEMORY_WATCHDOG, true);
            setPending(OptConfig.TEXTURE_CACHE_EVICTOR, true);
            setPending(OptConfig.SECTION_BUFFER_LIMIT, true);
            setPending(OptConfig.PONDER_CACHE_CLEAR, true);
            setPending(OptConfig.CREATE_BER_CULLER, true);
            setPending(OptConfig.CHUNK_REBUILD_THROTTLER, true);
            setPending(OptConfig.FLYWHEEL_BUFFER_CULLER, true);
            setPending(OptConfig.FLYWHEEL_BACKEND_FORCE, true);
            setPending(OptConfig.FLUID_RENDER_CULLER, true);
            setPending(OptConfig.STOPPED_CONTRAPTION_SKIP, true);
            setPending(OptConfig.ENTITY_THROTTLER, true);
            setPending(OptConfig.TICK_SKIPPER, true);
            setPending(OptConfig.AMBIENT_SOUND_SUPPRESSOR, true);
            setPending(OptConfig.WEATHER_SUPPRESSOR, true);
            setPending(OptConfig.CHUNK_UNLOADER, true);
            setPending(OptConfig.ASYNC_WORLD_SAVE, true);
            setPending(OptConfig.BER_CULL_DISTANCE, 16);
            setPending(OptConfig.FLUID_CULL_DISTANCE, 12);
            setPending(OptConfig.GOGGLE_CULL_DISTANCE, 8);
            setPending(OptConfig.ENTITY_THROTTLE_DISTANCE, 32);
            setPending(OptConfig.TICK_SKIP_DISTANCE, 24);
            setPending(OptConfig.RENDER_DISTANCE, 6);
            setPending(OptConfig.CHUNK_REBUILDS_PER_FRAME, 1);
            setPending(OptConfig.CREATE_RENDER_FPS, 20);
            setPending(OptConfig.TEXTURE_DOWNSCALE_ENABLED, true);
            setPending(OptConfig.CONTRAPTION_MEMORY_CLEANUP, true);
            setPending(OptConfig.SABLE_UDP_FIX, true);
            b.setMessage(Component.literal("§a✓ Tout activé !"));
            init();
        });
        y += gap + 4;

        // Boutons thématiques — 2 colonnes
        int lx = cx - bw - 4;
        int rx = cx + 4;

        addPageBtn("androidopt.page.render",    Page.RENDER, lx, y, bw, bh);
        addPageBtn("androidopt.page.memory",  Page.MEMORY, rx, y, bw, bh);
        y += gap;
        Button createBtn = addPageBtn("androidopt.page.create", Page.CREATE, lx, y, bw, bh);
        if (!net.neoforged.fml.ModList.get().isLoaded("create")) {
            createBtn.active = false;
            createBtn.setMessage(Component.literal("§7Create (Non installé)"));
        }
        addPageBtn("androidopt.page.sound",            Page.SOUND,  rx, y, bw, bh);
        y += gap;
        addPageBtn("androidopt.page.world",Page.WORLD,  lx, y, bw, bh);
        addPageBtn("androidopt.page.soc",     Page.SOC,    rx, y, bw, bh);
        y += gap;
        addPageBtn("androidopt.page.diag",     Page.DIAG,   lx, y, bw, bh);
        addPageBtn("androidopt.page.thermal",  Page.THERMAL,rx, y, bw, bh);
        y += gap;
        if (net.neoforged.fml.ModList.get().isLoaded("nativeglengine")) {
            addPageBtn("NativeGL Engine", Page.NATIVEGL, cx - bw / 2, y, bw, bh);
        }
        y += gap + 2;

        // Résumé
        String state = OptConfig.isActive() ? "§aOptimisations ACTIVES" : "§7INACTIF — forceEnable=false";
        label(cx, y, state);
        SocProfile soc = SocDetector.DETECTED_PROFILE;
        String socColor = SocDetector.detectionSucceeded ? "§a" : "§e";
        label(cx, y + 10, "§7SoC : " + socColor + soc.name);
    }


    private void buildRender() {
        int cx = this.width / 2;
        int lx = cx - BTN_W - 4, rx = cx + 4;
        int y  = 44;

        row(lx, rx, y,       "androidopt.config.render_opts",  OptConfig.RENDER_OPTS,
                              "androidopt.config.disable_clouds",          OptConfig.DISABLE_CLOUDS);
        row(lx, rx, y+GAP,   "androidopt.config.disable_ao",              OptConfig.DISABLE_AMBIENT_OCCLUSION,
                              "androidopt.config.disable_entity_shadows",  OptConfig.DISABLE_ENTITY_SHADOWS);
        row(lx, rx, y+GAP*2, "androidopt.config.minimal_particles",        OptConfig.MINIMAL_PARTICLES,
                              "androidopt.config.create_particles_filter",  OptConfig.CREATE_PARTICLES_FILTER);
        row(lx, rx, y+GAP*3, "androidopt.config.render_scale",OptConfig.RENDER_SCALE_ENABLED,
                              "androidopt.config.show_hud",               OptConfig.SHOW_HUD);

        // Sliders rendu
        addIntSlider("androidopt.config.render_distance", OptConfig.RENDER_DISTANCE,
            2, 32, lx, y+GAP*4, BTN_W, BTN_H);
        addIntSlider("androidopt.config.mipmap_levels", OptConfig.MIPMAP_LEVELS,
            0, 4, rx, y+GAP*4, BTN_W, BTN_H);

        label(cx, y+GAP*6+4, "§7Frame budget : " + FrameBudgetManager.getBudgetLevelDisplay());
    }


    private void buildMemory() {
        int cx = this.width / 2;
        int lx = cx - BTN_W - 4, rx = cx + 4;
        int y  = 44;

        row(lx, rx, y,       "androidopt.config.memory_watchdog",  OptConfig.MEMORY_WATCHDOG,
                              "androidopt.config.texture_cache_evictor",    OptConfig.TEXTURE_CACHE_EVICTOR);
        row(lx, rx, y+GAP,   "androidopt.config.section_buffer_limit", OptConfig.SECTION_BUFFER_LIMIT,
                              "androidopt.config.ponder_cache_clear",  OptConfig.PONDER_CACHE_CLEAR);
        
        addToggle("androidopt.config.texture_downscale", OptConfig.TEXTURE_DOWNSCALE_ENABLED, cx - BTN_W / 2, y+GAP*2, BTN_W, BTN_H);

        addIntSlider("androidopt.config.max_total_ram", OptConfig.MAX_TOTAL_RAM_MB,
            1500, 4096, cx - BTN_W / 2, y+GAP*3, BTN_W, BTN_H);
        addIntSlider("androidopt.config.gc_threshold", OptConfig.GC_THRESHOLD_PERCENT,
            50, 95, cx - BTN_W / 2, y+GAP*4, BTN_W, BTN_H);

        Runtime rt = Runtime.getRuntime();
        long usedMB = (rt.totalMemory() - rt.freeMemory()) / (1024*1024);
        long maxMB  = rt.maxMemory() / (1024*1024);
        long budgetMB = getPending(OptConfig.MAX_TOTAL_RAM_MB);
        long safeXmx = budgetMB - 250;
        double ratio = (double) usedMB / maxMB;
        String heapColor = ratio < 0.7 ? "§a" : ratio < 0.85 ? "§e" : "§c";

        label(cx, y+GAP*5+4,  "§7Heap : " + heapColor + usedMB + "§f/" + maxMB + " Mo");
        label(cx, y+GAP*5+16, "§7Xmx safe max : " + safeXmx + " Mo");
    }


    private void buildCreate() {
        int cx = this.width / 2;
        int lx = cx - BTN_W - 4, rx = cx + 4;
        int y  = 44;

        row(lx, rx, y,       "androidopt.config.create_ber_culler",OptConfig.CREATE_BER_CULLER,
                              "androidopt.config.chunk_rebuild_throttler",      OptConfig.CHUNK_REBUILD_THROTTLER);
        row(lx, rx, y+GAP,   "androidopt.config.flywheel_buffer_culler",   OptConfig.FLYWHEEL_BUFFER_CULLER,
                              "androidopt.config.flywheel_backend_force", OptConfig.FLYWHEEL_BACKEND_FORCE);
        row(lx, rx, y+GAP*2, "androidopt.config.stopped_contraption_skip",  OptConfig.STOPPED_CONTRAPTION_SKIP,
                              "androidopt.config.fluid_render_culler",  OptConfig.FLUID_RENDER_CULLER);
        row(lx, rx, y+GAP*3, "androidopt.config.create_particles_filter",  OptConfig.CREATE_PARTICLES_FILTER,
                              "androidopt.config.kinetic_network_skip",     OptConfig.KINETIC_NETWORK_SKIP);
        addToggle("androidopt.config.contraption_cleanup", OptConfig.CONTRAPTION_MEMORY_CLEANUP, cx - BTN_W / 2, y+GAP*4, BTN_W, BTN_H);

        // Sliders — distances et FPS
        addIntSlider("androidopt.config.create_render_fps", OptConfig.CREATE_RENDER_FPS,
            1, 60, lx, y+GAP*5, BTN_W, BTN_H);
        addIntSlider("androidopt.config.chunk_rebuilds_per_frame", OptConfig.CHUNK_REBUILDS_PER_FRAME,
            1, 16, rx, y+GAP*5, BTN_W, BTN_H);
        addIntSlider("androidopt.config.ber_cull_distance", OptConfig.BER_CULL_DISTANCE,
            4, 64, lx, y+GAP*6, BTN_W, BTN_H);
        addIntSlider("androidopt.config.fluid_cull_distance", OptConfig.FLUID_CULL_DISTANCE,
            4, 64, rx, y+GAP*6, BTN_W, BTN_H);
        addIntSlider("androidopt.config.goggle_cull_distance", OptConfig.GOGGLE_CULL_DISTANCE,
            2, 32, cx - BTN_W / 2, y+GAP*7, BTN_W, BTN_H);

        label(cx, y+GAP*8+4,  "§7BERs culled : §e" + CreateBerCuller.CULLED_POSITIONS.size());
    }


    private void buildSound() {
        int cx = this.width / 2;
        int lx = cx - BTN_W - 4, rx = cx + 4;
        int y  = 44;

        row(lx, rx, y,       "androidopt.config.ambient_sound_suppressor",           OptConfig.AMBIENT_SOUND_SUPPRESSOR,
                              "androidopt.config.weather_suppressor",             OptConfig.WEATHER_SUPPRESSOR);

        label(cx, y+GAP+4,   "§7Sons ambiants : cave sounds + pluie coupés");
        label(cx, y+GAP+16,  "§7Musique réduite à 30% (si activé)");
        label(cx, y+GAP+28,  "§7Skip total si volume système = 0 (Android)");

        boolean muted = AndroidVolumeDetector.isSystemMuted();
        label(cx, y+GAP+44,  "§7Volume système : " + (muted ? "§cMUET" : "§aActif"));
    }


    private void buildWorld() {
        int cx = this.width / 2;
        int lx = cx - BTN_W - 4, rx = cx + 4;
        int y  = 44;

        row(lx, rx, y,       "androidopt.config.entity_throttler", OptConfig.ENTITY_THROTTLER,
                              "androidopt.config.tick_skipper",OptConfig.TICK_SKIPPER);
        row(lx, rx, y+GAP,   "androidopt.config.chunk_unloader",            OptConfig.CHUNK_UNLOADER,
                              "androidopt.config.async_world_save",        OptConfig.ASYNC_WORLD_SAVE);
        row(lx, rx, y+GAP*2, "androidopt.config.server_be_tick_throttle",  OptConfig.SERVER_BE_TICK_THROTTLE,
                              "androidopt.config.sable_udp_fix",           OptConfig.SABLE_UDP_FIX);

        // Sliders distances
        addIntSlider("androidopt.config.entity_throttle_distance", OptConfig.ENTITY_THROTTLE_DISTANCE,
            8, 128, lx, y+GAP*3, BTN_W, BTN_H);
        addIntSlider("androidopt.config.tick_skip_distance", OptConfig.TICK_SKIP_DISTANCE,
            8, 128, rx, y+GAP*3, BTN_W, BTN_H);

        String mode = ServerModeDetector.isSolo() ? "§aSolo"
                    : ServerModeDetector.isRemoteServer() ? "§eServeur distant" : "§7Inconnu";
        label(cx, y+GAP*4+4, "§7Mode : " + mode);
    }


    private void buildSoc() {
        int cx = this.width / 2;
        int lx = cx - BTN_W - 4;
        int y  = 44;

        SocProfile soc = SocDetector.DETECTED_PROFILE;
        boolean ok = SocDetector.detectionSucceeded;

        label(cx, y,      "§6── Détection automatique ──");
        label(cx, y+10,   "§7SoC brut : §f" + SocDetector.rawSocName);
        label(cx, y+20,   "§7Profil : " + (ok ? "§a" : "§e") + soc.name);
        label(cx, y+30,   "§7Cœurs : §a" + soc.bigCoreCount + "× " + soc.bigCoreName
                        + " §7+ " + soc.littleCoreCount + "× " + soc.littleCoreName);
        label(cx, y+40,   "§7Masque affinité : §e0x" + soc.bigCoresMask);
        label(cx, y+50,   "§7Recommandé : §fRD=" + soc.renderDistance
                        + "  GC=" + soc.gcThresholdPercent + "%");

        addToggle("androidopt.config.auto_apply_soc_profile",
            OptConfig.AUTO_APPLY_SOC_PROFILE, cx - BTN_W / 2, y + 64, BTN_W, BTN_H);

        addBtn(cx - 75, y + 88, 150, BTN_H, "§eAppliquer maintenant", b -> {
            SocDetector.applyRecommendedSettings();
            b.setMessage(Component.literal("§aAppliqué !"));
        });

        // Liste des SoCs disponibles
        label(cx, y + 114, "§6── Sélection manuelle ──");
        int btnY = y + 126;
        int col  = 0;
        int rx   = cx + 4;
        for (SocProfile p : SocDetector.ALL_PROFILES) {
            if (p.id.startsWith("generic")) continue;
            if (btnY > this.height - 50) break;
            boolean current = p.id.equals(soc.id);
            int bx = col == 0 ? lx : rx;
            final SocProfile fp = p;
            addBtn(bx, btnY, BTN_W, BTN_H - 2,
                (current ? "§a▶ " : "§7  ") + p.name,
                b -> { OptConfig.MANUAL_SOC_ID.set(fp.id); init(); });
            col++;
            if (col >= 2) { col = 0; btnY += GAP - 2; }
        }
    }


    private void buildDoc() {
        int cx = this.width / 2;
        int y = 44;
        
        label(cx, y, "§6── Guide des Optimisations ──");
        y += 16;
        label(cx, y, "§eMax FPS vs Tout activer :");
        label(cx, y + 10, "§7Max FPS désactive toutes les options gourmandes, idéal pour petits téléphones.");
        y += 26;
        label(cx, y, "§eDistances Cull (BER / Fluides / Goggles) :");
        label(cx, y + 10, "§7Masque les éléments Create au-delà de la distance. Économise beaucoup de CPU/GPU.");
        y += 26;
        label(cx, y, "§eThrottle / Skip Tick :");
        label(cx, y + 10, "§7Gèle les entités et stoppe les calculs des machines lointaines pour la RAM.");
        y += 26;
        label(cx, y, "§eCreate Render FPS :");
        label(cx, y + 10, "§7Limite le framerate des animations de machines. 20 est fluide et très léger.");
        y += 26;
        label(cx, y, "§eChunk Rebuilds / Frame :");
        label(cx, y + 10, "§7Limite le nombre de blocs mis à jour par image. Empêche les micro-freezes.");
        y += 26;
        label(cx, y, "§eWatchdog Mémoire :");
        label(cx, y + 10, "§7Force le vidage de la RAM si elle dépasse le seuil critique (évite les crash OOM).");
    }


    private void row(int lx, int rx, int y,
                     String lLabel, ModConfigSpec.BooleanValue lCfg,
                     String rLabel, ModConfigSpec.BooleanValue rCfg) {
        addToggle(lLabel, lCfg, lx, y, BTN_W, BTN_H);
        addToggle(rLabel, rCfg, rx, y, BTN_W, BTN_H);
    }

    private void addToggle(String label, ModConfigSpec.BooleanValue cfg, int x, int y, int w, int h) {
        addRenderableWidget(Button.builder(
            buildLabel(label, getPending(cfg)),
            b -> { boolean v = !getPending(cfg); setPending(cfg, v); b.setMessage(buildLabel(label, v)); }
        ).bounds(x, y, w, h).build());
    }

    private Button addPageBtn(String key, Page page, int x, int y, int w, int h) {
        return addRenderableWidget(Button.builder(
            Component.literal("§e" + I18n.get(key)),
            b -> { currentPage = page; init(); }
        ).bounds(x, y, w, h).build());
    }

    private void addBtn(int x, int y, int w, int h, String label, Button.OnPress action) {
        addRenderableWidget(Button.builder(Component.literal(label), action)
            .bounds(x, y, w, h).build());
    }

    private void addIntSlider(String key, ModConfigSpec.IntValue cfg,
                               int min, int max, int x, int y, int w, int h) {
        addRenderableWidget(new net.minecraft.client.gui.components.AbstractSliderButton(
                x, y, w, h, Component.empty(), 0.0) {
            
            private int pendingValue = getPending(cfg);

            {
                this.value = (double) (pendingValue - min) / (max - min);
                this.updateMessage();
            }
            @Override
            protected void updateMessage() {
                pendingValue = min + (int) Math.round(this.value * (max - min));
                setMessage(Component.literal("§f" + I18n.get(key) + " : §e" + pendingValue));
            }
            @Override
            protected void applyValue() {
                pendingValue = min + (int) Math.round(this.value * (max - min));
                setPending(cfg, pendingValue);
            }
            
            @Override
            public void onRelease(double mouseX, double mouseY) {
                super.onRelease(mouseX, mouseY);
                setPending(cfg, pendingValue);
            }
        });
    }

    private void label(int cx, int y, String text) {
        pendingLabels.add(new LabelEntry(cx, y, text));
    }

    private static Component buildLabel(String key, boolean value) {
        return Component.literal((value ? "§a[ON]  " : "§c[OFF] ") + "§f" + I18n.get(key));
    }

    private void buildThermal() {
        int cx = this.width / 2;
        int lx = cx - BTN_W - 4, rx = cx + 4;
        int y  = 44;

        addToggle("androidopt.config.thermal_monitor", OptConfig.THERMAL_MONITOR, cx - BTN_W / 2, y, BTN_W, BTN_H);
        
        addIntSlider("androidopt.config.thermal_warning", OptConfig.THERMAL_WARNING_TEMP,
            35, 99, lx, y+GAP*2, BTN_W, BTN_H);
        addIntSlider("androidopt.config.thermal_critical", OptConfig.THERMAL_CRITICAL_TEMP,
            40, 99, rx, y+GAP*2, BTN_W, BTN_H);

        label(cx, y+GAP*4, "§7Température actuelle : " + ThermalMonitor.getHudDisplay());
    }


    private void buildDiag() {
        int cx = this.width / 2;
        int lx = cx - BTN_W - 4, rx = cx + 4;
        int y  = 44;

        row(lx, rx, y,       "androidopt.config.freeze_debugger",       OptConfig.FREEZE_DEBUGGER,
                              "androidopt.config.debug_verbose_log",    OptConfig.DEBUG_VERBOSE_LOG);
        y += GAP;

        label(cx, y, "§6── Affichage HUD ──");
        y += 12;

        row(lx, rx, y,       "androidopt.config.hud_show_fps",                OptConfig.HUD_SHOW_FPS,
                              "androidopt.config.hud_show_heap",           OptConfig.HUD_SHOW_HEAP);
        row(lx, rx, y+GAP,   "androidopt.config.hud_show_budget",       OptConfig.HUD_SHOW_BUDGET,
                              "androidopt.config.hud_show_soc",         OptConfig.HUD_SHOW_SOC);
        row(lx, rx, y+GAP*2, "androidopt.config.hud_show_optims",       OptConfig.HUD_SHOW_OPTIMS,
                              "androidopt.config.hud_show_freeze",       OptConfig.HUD_SHOW_FREEZE);
        y += GAP*3 - 4;

        label(cx, y, FreezeDebugger.getHudSummary());

        Runtime rt = Runtime.getRuntime();
        long usedMB = (rt.totalMemory() - rt.freeMemory()) / (1024*1024);
        long maxMB  = rt.maxMemory() / (1024*1024);
        int pct = (int) ((usedMB * 100) / maxMB);
        String mode = ServerModeDetector.isSolo() ? "§aSolo"
                    : ServerModeDetector.isRemoteServer() ? "§eServeur distant" : "§7Inconnu";

        label(cx, y+12, "§7Heap " + usedMB + "/" + maxMB + " Mo (" + pct + "%) | Mode: " + mode);
    }

    private void buildNativeGl() {
        int cx = this.width / 2;
        int lx = cx - BTN_W - 4, rx = cx + 4;
        int y  = 44;

        addIntSlider("GPU Budget (%)", OptConfig.NATIVE_GL_GPU_BUDGET_PERCENT,
            10, 100, cx - BTN_W / 2, y, BTN_W, BTN_H);
        
        y += GAP + 8;
        row(lx, rx, y,       "Hardware TexCompress",  OptConfig.NATIVE_GL_TEX_COMPRESS,
                              "Vertex Quantizer",      OptConfig.NATIVE_GL_VERTEX_QUANT);

        y += GAP * 2;
        label(cx, y, "§7NativeGL Engine Configuration");
        label(cx, y + 12, "§7Le GPU Budget contrôle la quantité de RAM allouée au moteur C++.");
        label(cx, y + 24, "§7La compression matérielle (ETC2) utilise etcpak natif pour de meilleures perfs.");
    }

    @Override
    public void render(GuiGraphics gfx, int mouseX, int mouseY, float delta) {
        renderBackground(gfx, mouseX, mouseY, delta);

        gfx.drawCenteredString(font, title, width / 2, 8, 0xFFFFAA00);

        String[] pageTitles = {"", I18n.get("androidopt.page.render"), I18n.get("androidopt.page.memory"),
            I18n.get("androidopt.page.create"), I18n.get("androidopt.page.sound"), I18n.get("androidopt.page.world"),
            I18n.get("androidopt.page.soc"), I18n.get("androidopt.page.diag"), I18n.get("androidopt.page.thermal"), "Documentation", "NativeGL Engine"};
        String sub = currentPage == Page.HOME
            ? (OptConfig.isActive() ? "§aACTIF" : "§7INACTIF")
            : "§e── " + pageTitles[currentPage.ordinal()] + " ──";
        gfx.drawCenteredString(font, Component.literal(sub), width / 2, 20, 0xFFFFFFFF);

        super.render(gfx, mouseX, mouseY, delta);

        for (LabelEntry e : pendingLabels) {
            gfx.drawCenteredString(font, Component.literal(e.text()), e.cx(), e.y(), 0xFFAAAAAA);
        }
    }

    @Override
    public void onClose() {
        saveAndClose();
    }
}
