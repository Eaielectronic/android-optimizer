package fr.eaielectronic.androidopt;

import java.util.List;


public class SocProfile {

    public final String       id;
    public final String       name;
    public final List<String> keywords;   // noms internes /proc/cpuinfo + getprop
    public final String       bigCoresMask;
    public final int          bigCoreCount;
    public final int          littleCoreCount;
    public final String       bigCoreName;
    public final String       littleCoreName;

    // Paramètres recommandés
    public final int renderDistance;
    public final int sectionBufferCount;
    public final int gcThresholdPercent;

    public SocProfile(String id, String name, List<String> keywords,
                      String bigCoresMask, int bigCoreCount, int littleCoreCount,
                      String bigCoreName, String littleCoreName,
                      int renderDistance,
                      int sectionBufferCount, int gcThresholdPercent) {
        this.id                 = id;
        this.name               = name;
        this.keywords           = keywords != null ? keywords : List.of();
        this.bigCoresMask       = bigCoresMask;
        this.bigCoreCount       = bigCoreCount;
        this.littleCoreCount    = littleCoreCount;
        this.bigCoreName        = bigCoreName  != null ? bigCoreName  : "Cortex-A";
        this.littleCoreName     = littleCoreName != null ? littleCoreName : "Cortex-A55";
        this.renderDistance     = renderDistance;
        this.sectionBufferCount = sectionBufferCount;
        this.gcThresholdPercent = gcThresholdPercent;
    }

    public static SocProfile generic() {
        return new SocProfile(
            "generic", "ARM64 générique", List.of(),
            "f0", 4, 4, "Cortex-A", "Cortex-A55",
            4, 4, 80
        );
    }

    @Override
    public String toString() {
        return name + " (big=" + bigCoreName + "×" + bigCoreCount
             + ", little=" + littleCoreName + "×" + littleCoreCount + ")";
    }
}
