package fr.eaielectronic.nativeglengine;

/**
 * Rapport mémoire GPU + système.
 * 
 * Les valeurs viennent soit de VMA (natif, précis à ~5%),
 * soit du Runtime Java (fallback, précis à ~15%).
 */
public record MemoryReport(
    /** Budget total GPU accordé par Android au processus (bytes) */
    long gpuBudgetBytes,

    /** Usage GPU actuel par ce processus (bytes) */
    long gpuUsageBytes,

    /** Pression GPU : 0.0 = libre, 1.0 = au max du budget */
    float gpuPressure,

    /** Mémoire système disponible (MB), ou -1 si inconnue */
    long sysAvailableMB,

    /** Température max du SoC (°C), ou -1 si inconnue */
    int tempCelsius,

    /** true si le device est en throttle thermique */
    boolean thermalThrottling
) {
    /** @return usage GPU en MB */
    public long gpuUsageMB() { return gpuUsageBytes / (1024 * 1024); }

    /** @return budget GPU en MB */
    public long gpuBudgetMB() { return gpuBudgetBytes / (1024 * 1024); }

    /** @return true si les données viennent du natif (précis) */
    public boolean isNative() { return sysAvailableMB >= 0; }

    /** @return pression GPU en pourcentage (0-100) */
    public int gpuPressurePercent() { return (int) (gpuPressure * 100); }
}
