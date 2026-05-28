package fr.eaielectronic.androidopt.client;

import fr.eaielectronic.androidopt.AndroidDetector;
import fr.eaielectronic.androidopt.AndroidOptMod;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;


public class ArmThreadAffinity {

    private static final String BIG_CORES_MASK = "f0";
    private static final String ALL_CORES_MASK = "ff";

    private static boolean applied = false;

    
    public static void applyToCurrentThread() {
        if (!AndroidDetector.IS_ANDROID) return;
        if (applied) return;
        applied = true; 

        long tid = getLinuxTid();
        if (tid < 0) {
            AndroidOptMod.LOGGER.warn("[AndroidOpt] ArmThreadAffinity : TID introuvable.");
            return;
        }

        String tidStr = String.valueOf(tid);

        if (tryTaskset(BIG_CORES_MASK, tidStr)) {
            AndroidOptMod.LOGGER.info(
                "[AndroidOpt] ArmThreadAffinity : thread {} (TID={}) → gros cœurs (0x{})",
                Thread.currentThread().getName(), tid, BIG_CORES_MASK);
        } else if (tryTaskset(ALL_CORES_MASK, tidStr)) {
            AndroidOptMod.LOGGER.info(
                "[AndroidOpt] ArmThreadAffinity : fallback tous cœurs (TID={}, 0x{})",
                tid, ALL_CORES_MASK);
        } else {
            AndroidOptMod.LOGGER.warn(
                "[AndroidOpt] ArmThreadAffinity : taskset indisponible (TID={}).", tid);
        }
    }

    
    private static long getLinuxTid() {
        try {
            Path taskDir = Paths.get("/proc/self/task");
            if (!Files.exists(taskDir)) return -1;

            String currentThreadName = Thread.currentThread().getName();
            String truncatedName = currentThreadName.length() > 15
                ? currentThreadName.substring(0, 15)
                : currentThreadName;

            File[] tidDirs = taskDir.toFile().listFiles();
            if (tidDirs == null) return -1;

            for (File tidDir : tidDirs) {
                if (!tidDir.isDirectory()) continue;
                try {
                    long tid = Long.parseLong(tidDir.getName());
                    Path commFile = tidDir.toPath().resolve("comm");
                    if (Files.exists(commFile)) {
                        String comm = Files.readString(commFile).trim();
                        if (comm.equals(truncatedName) || truncatedName.startsWith(comm)) {
                            return tid;
                        }
                    }
                } catch (Exception ignored) {}
            }

            long pid = ProcessHandle.current().pid();
            for (File tidDir : tidDirs) {
                try {
                    long tid = Long.parseLong(tidDir.getName());
                    if (tid == pid) return tid; // thread principal
                } catch (Exception ignored) {}
            }

        } catch (Exception e) {
            AndroidOptMod.LOGGER.debug("[AndroidOpt] ArmThreadAffinity : erreur lecture TID : {}", e.getMessage());
        }
        return -1;
    }

    
    private static boolean tryTaskset(String mask, String tid) {
        if (!fr.eaielectronic.androidopt.AndroidDetector.IS_ANDROID) return false;
        try {
            ProcessBuilder pb = new ProcessBuilder("taskset", "-p", mask, tid);
            pb.redirectErrorStream(true);
            Process proc = pb.start();
            boolean done = proc.waitFor(500, java.util.concurrent.TimeUnit.MILLISECONDS);
            if (!done) { proc.destroyForcibly(); return false; }
            return proc.exitValue() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    private ArmThreadAffinity() {}
}
