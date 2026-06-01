/**
 * thermal_monitor.h — Interface pour le monitoring thermique.
 */
#pragma once

namespace androidopt {
    int getCpuTemperatureCelsius();
    int getThermalLevel();
    int getRawTemperature();
}
