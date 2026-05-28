package fr.eaielectronic.androidopt;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.resources.ResourceLocation;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;


public class SocDetector {

    
    public static SocProfile DETECTED_PROFILE = SocProfile.generic();

    
    public static final List<SocProfile> ALL_PROFILES = new ArrayList<>();

    
    private static final java.util.Map<String, List<String>> PROFILE_KEYWORDS = new java.util.HashMap<>();
    
    public static boolean detectionSucceeded = false;

    
    public static String rawSocName = "Inconnu";

    
    public static void init(ResourceManager resourceManager) {
        loadProfiles(resourceManager);
        detect();
    }


    private static void loadProfiles(ResourceManager resourceManager) {
        ALL_PROFILES.clear();
        try {
            ResourceLocation loc = ResourceLocation.fromNamespaceAndPath(
                AndroidOptMod.MODID, "soc_profiles.json");
            try (InputStream is = resourceManager.open(loc);
                 InputStreamReader reader = new InputStreamReader(is, StandardCharsets.UTF_8)) {

                JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
                JsonArray profiles = root.getAsJsonArray("profiles");

                for (JsonElement el : profiles) {
                    JsonObject p = el.getAsJsonObject();
                    JsonObject rec = p.getAsJsonObject("recommended");
                    String id = p.get("id").getAsString();

                    List<String> keywords = new ArrayList<>();
                    if (p.has("keywords")) {
                        for (JsonElement kw : p.getAsJsonArray("keywords")) {
                            keywords.add(kw.getAsString().toLowerCase());
                        }
                    }
                    PROFILE_KEYWORDS.put(id, keywords);

                    ALL_PROFILES.add(new SocProfile(
                        id,
                        p.get("name").getAsString(),
                        keywords,
                        p.has("big_cores_mask") ? p.get("big_cores_mask").getAsString() : "f0",
                        p.has("big_core_count") ? p.get("big_core_count").getAsInt() : 4,
                        p.has("little_core_count") ? p.get("little_core_count").getAsInt() : 4,
                        p.has("big_core_name") ? p.get("big_core_name").getAsString() : null,
                        p.has("little_core_name") ? p.get("little_core_name").getAsString() : null,
                        rec.get("render_distance").getAsInt(),
                        rec.get("section_buffer_count").getAsInt(),
                        rec.get("gc_threshold_percent").getAsInt()
                    ));
                }
                AndroidOptMod.LOGGER.info(
                    "[AndroidOpt] SocDetector : {} profils chargés.", ALL_PROFILES.size());
            }
        } catch (Exception e) {
            AndroidOptMod.LOGGER.warn(
                "[AndroidOpt] SocDetector : impossible de charger soc_profiles.json : {}", e.getMessage());
            // Ajoute au moins le profil générique
            ALL_PROFILES.add(SocProfile.generic());
        }
    }


    private static void detect() {
        try {
            if (OptConfig.SPEC.isLoaded()) {
                String manualId = OptConfig.MANUAL_SOC_ID.get();
                if (!manualId.isEmpty() && !manualId.equals("auto")) {
                    SocProfile manual = findById(manualId);
                    if (manual != null) {
                        DETECTED_PROFILE = manual;
                        detectionSucceeded = true;
                        rawSocName = manual.name + " (manuel)";
                        AndroidOptMod.LOGGER.info(
                            "[AndroidOpt] SocDetector : profil manuel → {}", manual.name);
                        return;
                    }
                }
            }
        } catch (Throwable t) {
            AndroidOptMod.LOGGER.debug("[AndroidOpt] SocDetector : config pas encore chargée, détection auto.");
        }

        // Collecte les indices de détection
        String cpuInfo    = readCpuInfo();
        String hardware   = readAndroidProp("ro.hardware");
        String board      = readAndroidProp("ro.product.board");
        String chipname   = readAndroidProp("ro.soc.model");
        String osVersion  = System.getProperty("os.version", "").toLowerCase();

        // Construit une chaîne de recherche combinée
        String searchStr = (cpuInfo + " " + hardware + " " + board + " "
                          + chipname + " " + osVersion).toLowerCase();

        rawSocName = extractRawName(cpuInfo, hardware, board, chipname);

        AndroidOptMod.LOGGER.info(
            "[AndroidOpt] SocDetector : recherche dans '{}'", rawSocName);

        for (SocProfile profile : ALL_PROFILES) {
            if (profile.id.equals("generic_arm64_high") || profile.id.equals("generic_arm64_mid")) {
                continue;
            }
            for (String kw : profile.keywords) {
                if (searchStr.contains(kw)) {
                    DETECTED_PROFILE = profile;
                    detectionSucceeded = true;
                    AndroidOptMod.LOGGER.info(
                        "[AndroidOpt] SocDetector : SoC détecté → {} (keyword='{}')",
                        profile.name, kw);
                    return;
                }
            }
        }

        // Fallback : profil générique ARM64
        DETECTED_PROFILE = findById("generic_arm64_high");
        if (DETECTED_PROFILE == null) DETECTED_PROFILE = SocProfile.generic();
        AndroidOptMod.LOGGER.info(
            "[AndroidOpt] SocDetector : SoC non reconnu ({}), profil générique utilisé.", rawSocName);
    }


    
    private static String readCpuInfo() {
        try {
            Path cpuinfo = Paths.get("/proc/cpuinfo");
            if (!Files.exists(cpuinfo)) return "";
            String content = Files.readString(cpuinfo, StandardCharsets.UTF_8).toLowerCase();
            // Extrait les lignes "Hardware" et "model name"
            StringBuilder sb = new StringBuilder();
            for (String line : content.split("\n")) {
                if (line.startsWith("hardware") || line.startsWith("model name")
                        || line.startsWith("processor")) {
                    sb.append(line).append(" ");
                }
            }
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }

    
    private static String readAndroidProp(String prop) {
        try {
            ProcessBuilder pb = new ProcessBuilder("getprop", prop);
            pb.redirectErrorStream(true);
            Process proc = pb.start();
            boolean done = proc.waitFor(200, java.util.concurrent.TimeUnit.MILLISECONDS);
            if (!done) { proc.destroyForcibly(); return ""; }
            return new String(proc.getInputStream().readAllBytes(),
                StandardCharsets.UTF_8).trim().toLowerCase();
        } catch (Exception e) {
            return "";
        }
    }

    private static String extractRawName(String cpuInfo, String hardware,
                                          String board, String chipname) {
        if (!chipname.isEmpty()) return chipname;
        if (!hardware.isEmpty()) return hardware;
        if (!board.isEmpty()) return board;
        // Extrait la ligne Hardware de cpuinfo
        for (String line : cpuInfo.split("\n")) {
            if (line.startsWith("hardware")) {
                String[] parts = line.split(":");
                if (parts.length > 1) return parts[1].trim();
            }
        }
        return "ARM64";
    }


    public static SocProfile findById(String id) {
        for (SocProfile p : ALL_PROFILES) {
            if (p.id.equals(id)) return p;
        }
        return null;
    }

    
    public static void applyRecommendedSettings() {
        if (!ConfigGuard.isReady()) return;
        if (!ConfigGuard.getBool(OptConfig.AUTO_APPLY_SOC_PROFILE, true)) return;

        SocProfile p = DETECTED_PROFILE;
        AndroidOptMod.LOGGER.info(
            "[AndroidOpt] SocDetector : application du profil {} → " +
            "renderDist={}, buffers={}, gc={}%",
            p.name, p.renderDistance, p.sectionBufferCount, p.gcThresholdPercent);

        OptConfig.RENDER_DISTANCE.set(p.renderDistance);
        OptConfig.GC_THRESHOLD_PERCENT.set(p.gcThresholdPercent);
    }

    private SocDetector() {}
}
