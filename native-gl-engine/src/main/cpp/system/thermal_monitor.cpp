/**
 * thermal_monitor.cpp — Lecture de la température du SoC Android.
 *
 * Lit les zones thermiques via sysfs (/sys/class/thermal/).
 * Accessible sans root sur tous les Android stock.
 * Retourne un niveau de throttling pour ajuster le render distance et les ticks Create.
 *
 * Source : Documentation Android NDK + sysfs standard Linux.
 */
#include <stdio.h>
#include <string.h>
#include <android/log.h>

#define TAG "AndroidOpt_Thermal"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, TAG, __VA_ARGS__)

namespace androidopt {

/**
 * Lit la température d'une zone thermique spécifique.
 * @param zone numéro de zone (0-15 typiquement)
 * @return température en millièmes de degré (42000 = 42.0°C), ou -1 si erreur
 */
static int readThermalZone(int zone) {
    char path[64];
    snprintf(path, sizeof(path), "/sys/class/thermal/thermal_zone%d/temp", zone);
    FILE* f = fopen(path, "r");
    if (!f) return -1;
    int temp = -1;
    if (fscanf(f, "%d", &temp) != 1) temp = -1;
    fclose(f);
    return temp;
}

/**
 * Lit le type d'une zone thermique pour identifier le CPU.
 * @return true si c'est une zone CPU
 */
static bool isCpuZone(int zone) {
    char path[64], type[64];
    snprintf(path, sizeof(path), "/sys/class/thermal/thermal_zone%d/type", zone);
    FILE* f = fopen(path, "r");
    if (!f) return false;
    memset(type, 0, sizeof(type));
    if (fgets(type, sizeof(type), f) == nullptr) { fclose(f); return false; }
    fclose(f);
    // Les zones CPU ont souvent "cpu" ou "tsens" dans leur nom
    return (strstr(type, "cpu") != nullptr ||
            strstr(type, "tsens") != nullptr ||
            strstr(type, "CPU") != nullptr);
}

/**
 * Obtient la température CPU la plus élevée en degrés Celsius.
 * Scanne jusqu'à 20 zones thermiques, priorise les zones CPU.
 * @return température en °C, ou -1 si aucune zone lisible
 */
int getCpuTemperatureCelsius() {
    int maxCpuTemp = -1;
    int maxAnyTemp = -1;

    for (int z = 0; z < 20; z++) {
        int temp = readThermalZone(z);
        if (temp < 0) continue;

        // Certains SoC retournent en millidegrés, d'autres en degrés
        int celsius = (temp > 1000) ? (temp / 1000) : temp;

        if (celsius > maxAnyTemp) maxAnyTemp = celsius;
        if (isCpuZone(z) && celsius > maxCpuTemp) maxCpuTemp = celsius;
    }

    // Prioriser la zone CPU si trouvée, sinon la plus chaude
    return (maxCpuTemp > 0) ? maxCpuTemp : maxAnyTemp;
}

/**
 * Retourne un niveau de throttling intelligent.
 * @return 0=froid (aucune action), 1=tiède (réduire render distance),
 *         2=chaud (throttle Create), 3=critique (mode survie)
 */
int getThermalLevel() {
    int celsius = getCpuTemperatureCelsius();
    if (celsius < 0) return 0; // impossible de lire

    if (celsius < 38) return 0; // FROID : performances maximales
    if (celsius < 43) return 1; // TIÈDE : réduire render distance
    if (celsius < 48) return 2; // CHAUD : throttle Create + réduire rendu
    return 3;                    // CRITIQUE : mode survie minimal
}

/**
 * Retourne la température brute en degrés Celsius.
 * Utilisé par le Java-side pour afficher dans le HUD de debug.
 */
int getRawTemperature() {
    return getCpuTemperatureCelsius();
}

} // namespace androidopt
