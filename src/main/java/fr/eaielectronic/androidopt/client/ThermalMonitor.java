package fr.eaielectronic.androidopt.client;

import fr.eaielectronic.androidopt.AndroidOptMod;
import fr.eaielectronic.androidopt.OptConfig;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;

@EventBusSubscriber(modid = AndroidOptMod.MODID, value = Dist.CLIENT)
public class ThermalMonitor {

    private static int ticks = 0;
    private static int currentTempC = -1;
    private static boolean isOverheating = false;
    private static boolean isCritical = false;

    public static boolean isOverheating() { return isOverheating; }
    public static boolean isCritical() { return isCritical; }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (!OptConfig.isActive() || !OptConfig.THERMAL_MONITOR.get()) return;

        if (++ticks < 200) return; // Check every 10 seconds
        ticks = 0;

        currentTempC = getHighestTemperature();

        int criticalTemp = fr.eaielectronic.androidopt.ConfigGuard.isReady() ? OptConfig.THERMAL_CRITICAL_TEMP.get() : 48;
        int warningTemp = fr.eaielectronic.androidopt.ConfigGuard.isReady() ? OptConfig.THERMAL_WARNING_TEMP.get() : 42;

        if (currentTempC > criticalTemp) {
            if (!isCritical) {
                isCritical = true;
                isOverheating = true;
                AndroidOptMod.LOGGER.warn("[AndroidOpt] Thermal CRITICAL: Device is {} C.", currentTempC);
            }
        } else if (currentTempC > warningTemp) {
            if (!isOverheating) {
                isOverheating = true;
                isCritical = false;
                AndroidOptMod.LOGGER.warn("[AndroidOpt] Thermal Warning: Device is {} C.", currentTempC);
            }
        } else {
            if (isOverheating || isCritical) {
                AndroidOptMod.LOGGER.info("[AndroidOpt] Thermal OK: Device cooled down to {} C.", currentTempC);
            }
            isOverheating = false;
            isCritical = false;
        }
    }

    private static int getHighestTemperature() {
        int maxTemp = -1;
        Path thermalDir = Paths.get("/sys/class/thermal");

        if (Files.exists(thermalDir)) {
            try (Stream<Path> paths = Files.list(thermalDir)) {
                maxTemp = paths.filter(p -> p.getFileName().toString().startsWith("thermal_zone"))
                    .mapToInt(p -> {
                        try {
                            String tempStr = Files.readString(p.resolve("temp")).trim();
                            int temp = Integer.parseInt(tempStr);
                            // Some devices report temp in millidegrees Celsius
                            if (temp > 1000) {
                                temp = temp / 1000;
                            }
                            // Ignore absurdly high readings (often errors)
                            if (temp > 150) return -1;
                            return temp;
                        } catch (Exception e) {
                            return -1;
                        }
                    })
                    .max()
                    .orElse(-1);
            } catch (IOException ignored) {}
        }
        return maxTemp;
    }

    public static String getHudDisplay() {
        if (currentTempC < 0) return "";
        int criticalTemp = fr.eaielectronic.androidopt.ConfigGuard.isReady() ? OptConfig.THERMAL_CRITICAL_TEMP.get() : 48;
        int warningTemp = fr.eaielectronic.androidopt.ConfigGuard.isReady() ? OptConfig.THERMAL_WARNING_TEMP.get() : 42;
        String color = currentTempC > criticalTemp ? "§c" : (currentTempC > warningTemp ? "§6" : "§a");
        return String.format("%s%d°C", color, currentTempC);
    }
}
