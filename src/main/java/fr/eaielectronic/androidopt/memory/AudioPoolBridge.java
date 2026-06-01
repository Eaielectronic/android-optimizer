package fr.eaielectronic.androidopt.memory;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import org.lwjgl.openal.AL10;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Pool de buffers OpenAL.
 *
 * Évite le churn d'allocation/libération de buffers natifs OpenAL
 * lors de la lecture fréquente de sons dans Minecraft.
 */
public class AudioPoolBridge {
    private static final Logger LOGGER = LoggerFactory.getLogger("AndroidOpt");
    private static final Queue<Integer> FREE_BUFFERS = new ConcurrentLinkedQueue<>();
    private static final int MAX_POOL_SIZE = 128; // Limite pour ne pas consommer trop de VRAM OpenAL

    public static int getBuffer() {
        Integer id = FREE_BUFFERS.poll();
        if (id != null) {
            return id;
        }
        int[] arr = new int[1];
        AL10.alGenBuffers(arr);
        LOGGER.debug("[Audio Pool] Generated new OpenAL buffer ID: {}", arr[0]);
        return arr[0];
    }

    public static void releaseBuffer(int bufferId) {
        if (bufferId <= 0) return;
        if (FREE_BUFFERS.size() < MAX_POOL_SIZE) {
            FREE_BUFFERS.offer(bufferId);
            LOGGER.debug("[Audio Pool] Recycled OpenAL buffer ID: {} (Pool size: {})", bufferId, FREE_BUFFERS.size());
        } else {
            AL10.alDeleteBuffers(new int[]{bufferId});
            LOGGER.debug("[Audio Pool] Pool full. Deleted OpenAL buffer ID: {}", bufferId);
        }
    }

    public static void clear() {
        int count = FREE_BUFFERS.size();
        Integer id;
        while ((id = FREE_BUFFERS.poll()) != null) {
            AL10.alDeleteBuffers(new int[]{id});
        }
        if (count > 0) {
            LOGGER.info("[Audio Pool] Cleared {} OpenAL buffers", count);
        }
    }
}
