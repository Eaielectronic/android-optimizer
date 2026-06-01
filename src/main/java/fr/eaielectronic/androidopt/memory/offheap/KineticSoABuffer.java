package fr.eaielectronic.androidopt.memory.offheap;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Stack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Stockage Off-Heap Structure of Arrays (SoA) pour les champs numériques de KineticBlockEntity.
 *
 * Au lieu de stocker speed, stress, capacity sur le tas Java pour des milliers de blocs Create,
 * on alloue un Direct ByteBuffer hors-tas. Chaque entité obtient un index unique.
 *
 * Structure d'un slot (16 octets) :
 *   - Offset 0  : speed (float, 4 octets)
 *   - Offset 4  : stress (float, 4 octets)
 *   - Offset 8  : capacity (float, 4 octets)
 *   - Offset 12 : overload (byte, 1 octet)
 *   - Offset 13 : padding/reserve (3 octets)
 */
public class KineticSoABuffer {
    private static final Logger LOGGER = LoggerFactory.getLogger("AndroidOpt");
    public static final KineticSoABuffer INSTANCE = new KineticSoABuffer(16384); // 16k entités max

    private final int maxEntities;
    private final int slotSize = 16;
    private final ByteBuffer buffer;

    // Gestion des indices libres via une pile pour réutilisation rapide (sans fragmentation)
    private final Stack<Integer> freeIndices = new Stack<>();
    private int nextFreeIndex = 0;

    public KineticSoABuffer(int maxEntities) {
        this.maxEntities = maxEntities;
        this.buffer = ByteBuffer.allocateDirect(maxEntities * slotSize).order(ByteOrder.nativeOrder());
        LOGGER.info("[Kinetic SoA] Allocated {} KB of direct memory for {} kinetic entities", 
                (maxEntities * slotSize) / 1024, maxEntities);
    }

    /**
     * Alloue un index unique pour une nouvelle KineticBlockEntity.
     */
    public synchronized int allocateIndex() {
        if (!freeIndices.isEmpty()) {
            return freeIndices.pop();
        }
        if (nextFreeIndex >= maxEntities) {
            LOGGER.warn("[Kinetic SoA] Buffer limit reached! Reusing index 0.");
            return 0;
        }
        return nextFreeIndex++;
    }

    /**
     * Libère un index pour réutilisation ultérieure.
     */
    public synchronized void freeIndex(int index) {
        if (index > 0 && index < maxEntities) {
            // Réinitialiser le slot à 0
            int baseOffset = index * slotSize;
            buffer.putFloat(baseOffset, 0.0f);
            buffer.putFloat(baseOffset + 4, 0.0f);
            buffer.putFloat(baseOffset + 8, 0.0f);
            buffer.put(baseOffset + 12, (byte) 0);
            
            freeIndices.push(index);
        }
    }

    public float getSpeed(int index) {
        return buffer.getFloat(index * slotSize);
    }

    public void setSpeed(int index, float speed) {
        buffer.putFloat(index * slotSize, speed);
    }

    public float getStress(int index) {
        return buffer.getFloat(index * slotSize + 4);
    }

    public void setStress(int index, float stress) {
        buffer.putFloat(index * slotSize + 4, stress);
    }

    public float getCapacity(int index) {
        return buffer.getFloat(index * slotSize + 8);
    }

    public void setCapacity(int index, float capacity) {
        buffer.putFloat(index * slotSize + 8, capacity);
    }

    public boolean isOverloaded(int index) {
        return buffer.get(index * slotSize + 12) != 0;
    }

    public void setOverloaded(int index, boolean overloaded) {
        buffer.put(index * slotSize + 12, (byte) (overloaded ? 1 : 0));
    }
}
