import re

with open('/home/ubuntu/android-optimizer/src/main/java/com/example/androidopt/client/OptConfigScreen.java', 'r', encoding='utf-8') as f:
    code = f.read()

replacements = {
    '"Forcer sur PC (test)"': '"androidopt.config.force_pc"',
    
    '"🎨  Rendu & FPS"': '"🎨  ", "androidopt.page.render"',
    '"💾  Mémoire & RAM"': '"💾  ", "androidopt.page.memory"',
    '"⚙️  Create & Flywheel"': '"⚙️  ", "androidopt.page.create"',
    '"🔊  Son"': '"🔊  ", "androidopt.page.sound"',
    '"🌍  Monde & Entités"': '"🌍  ", "androidopt.page.world"',
    '"📱  Profil SoC"': '"📱  ", "androidopt.page.soc"',
    '"🔧  Diagnostic"': '"🔧  ", "androidopt.page.diag"',
    
    '"Rendu réduit au démarrage"': '"androidopt.config.render_opts"',
    '"Désactiver nuages"': '"androidopt.config.disable_clouds"',
    '"Désactiver AO"': '"androidopt.config.disable_ao"',
    '"Désactiver ombres entités"': '"androidopt.config.disable_entity_shadows"',
    '"Particles minimales"': '"androidopt.config.minimal_particles"',
    '"Filtrer particules Create"': '"androidopt.config.create_particles_filter"',
    '"Render scale 75% (+40% FPS)"': '"androidopt.config.render_scale"',
    '"Afficher HUD"': '"androidopt.config.show_hud"',
    
    '"Surveillance mémoire (GC)"': '"androidopt.config.memory_watchdog"',
    '"Éviction cache textures"': '"androidopt.config.texture_cache_evictor"',
    '"Limiter buffers chunks (4)"': '"androidopt.config.section_buffer_limit"',
    '"Vider cache Ponder Create"': '"androidopt.config.ponder_cache_clear"',
    
    '"BER culler Create (>16 blocs)"': '"androidopt.config.create_ber_culler"',
    '"Chunk rebuild throttle"': '"androidopt.config.chunk_rebuild_throttler"',
    '"Buffers Flywheel (culler)"': '"androidopt.config.flywheel_buffer_culler"',
    '"Backend Flywheel Instancing"': '"androidopt.config.flywheel_backend_force"',
    '"Skip contraptions arrêtées"': '"androidopt.config.stopped_contraption_skip"',
    '"Fluides Create (>12 blocs)"': '"androidopt.config.fluid_render_culler"',
    '"Goggle overlays (>8 blocs)"': '"androidopt.config.goggle_overlays"',
    '"Réseau cinétique (solo)"': '"androidopt.config.kinetic_network_skip"',
    
    '"Sons ambiants OFF"': '"androidopt.config.ambient_sound_suppressor"',
    '"Supprimer météo"': '"androidopt.config.weather_suppressor"',
    
    '"Throttle entités lointaines"': '"androidopt.config.entity_throttler"',
    '"Tick skipper (BEs lointains)"': '"androidopt.config.tick_skipper"',
    '"Décharger chunks"': '"androidopt.config.chunk_unloader"',
    '"Tick BEs serveur (solo ÷4)"': '"androidopt.config.server_be_tick_throttle"',
    
    '"Appliquer profil auto au démarrage"': '"androidopt.config.auto_apply_soc_profile"',
    
    '"FreezeDebugger (logs)"': '"androidopt.config.freeze_debugger"',
    '"Logs détaillés (verbose)"': '"androidopt.config.debug_verbose_log"',
    '"Afficher FPS"': '"androidopt.config.hud_show_fps"',
    '"Afficher Heap/RAM"': '"androidopt.config.hud_show_heap"',
    '"Afficher Budget frame"': '"androidopt.config.hud_show_budget"',
    '"Afficher SoC + Mode"': '"androidopt.config.hud_show_soc"',
    '"Afficher états optims"': '"androidopt.config.hud_show_optims"',
    '"Afficher stats Freeze"': '"androidopt.config.hud_show_freeze"',
    
    '"FPS max global"': '"androidopt.config.fps_limit"',
    '"Render distance (chunks)"': '"androidopt.config.render_distance"',
    '"Mipmap levels"': '"androidopt.config.mipmap_levels"',
    '"Budget RAM total (Mo)"': '"androidopt.config.max_total_ram"',
    '"Seuil GC (%)"': '"androidopt.config.gc_threshold"',
    '"FPS rendu Create"': '"androidopt.config.create_render_fps"',
    '"Rebuilds chunks/frame"': '"androidopt.config.chunk_rebuilds_per_frame"'
}

for old, new in replacements.items():
    code = code.replace(old, new)

# Patch helpers
code = code.replace(
    'private void addPageBtn(String label, Page page, int x, int y, int w, int h) {',
    'private void addPageBtn(String emoji, String key, Page page, int x, int y, int w, int h) {'
)
code = code.replace(
    'Component.literal("§e" + label),',
    'Component.literal("§e" + emoji + net.minecraft.client.resources.language.I18n.get(key)),'
)

code = code.replace(
    'private static Component buildLabel(String label, boolean value) {',
    'private static Component buildLabel(String key, boolean value) {'
)
code = code.replace(
    'return Component.literal((value ? "§a[ON]  " : "§c[OFF] ") + "§f" + label);',
    'return Component.literal((value ? "§a[ON]  " : "§c[OFF] ") + "§f" + net.minecraft.client.resources.language.I18n.get(key));'
)

code = code.replace(
    'Component.literal("§f" + label + " : §e" + current)',
    'Component.literal("§f" + net.minecraft.client.resources.language.I18n.get(label) + " : §e" + current)'
)
code = code.replace(
    'b.setMessage(Component.literal("§f" + label + " : §e" + next));',
    'b.setMessage(Component.literal("§f" + net.minecraft.client.resources.language.I18n.get(label) + " : §e" + next));'
)

# Patch pageTitles array
code = code.replace(
    'String[] pageTitles = {"", "Rendu & FPS", "Mémoire & RAM",\n            "Create & Flywheel", "Son", "Monde & Entités", "Profil SoC", "Debug & HUD"};',
    'String[] pageTitles = {"", net.minecraft.client.resources.language.I18n.get("androidopt.page.render"), net.minecraft.client.resources.language.I18n.get("androidopt.page.memory"),\n            net.minecraft.client.resources.language.I18n.get("androidopt.page.create"), net.minecraft.client.resources.language.I18n.get("androidopt.page.sound"), net.minecraft.client.resources.language.I18n.get("androidopt.page.world"), net.minecraft.client.resources.language.I18n.get("androidopt.page.soc"), net.minecraft.client.resources.language.I18n.get("androidopt.page.diag")};'
)

with open('/home/ubuntu/android-optimizer/src/main/java/com/example/androidopt/client/OptConfigScreen.java', 'w', encoding='utf-8') as f:
    f.write(code)

print("OptConfigScreen patched for I18n.")
