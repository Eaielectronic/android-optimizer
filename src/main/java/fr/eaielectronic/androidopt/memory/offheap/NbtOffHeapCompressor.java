package fr.eaielectronic.androidopt.memory.offheap;

import fr.eaielectronic.androidopt.OptConfig;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.ByteBuffer;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Compresse les arbres NBT des entités distantes en mémoire off-heap.
 *
 * Problème : Chaque entité chargée maintient un arbre NBT complet en heap Java
 * (CompoundTag, NbtList, NbtString... des dizaines d'objets par entité).
 * Avec 500 entités dont seulement 50 sont visibles, 450 arbres NBT inutiles
 * restent en RAM et stressent le GC.
 *
 * Solution : Quand une entité est loin du joueur et inactive, on sérialise
 * son NBT en bytes → on le stocke dans un DirectByteBuffer (off-heap).
 * L'arbre Java original est libéré et collecté par le GC.
 * Si l'entité est soudainement accédée, on désérialise (< 1 ms).
 *
 * Conditions de compression :
 * - Distance au joueur > NBT_COMPRESS_DISTANCE blocs (configurable)
 * - Aucune modification NBT depuis NBT_COMPRESS_DELAY_TICKS ticks
 * - Entité non montée, non en laisse, non en combat
 *
 * Gain estimé : 60-180 Mo de heap sur un modpack avec beaucoup d'entités.
 */
public class NbtOffHeapCompressor {
    private static final Logger LOGGER = LoggerFactory.getLogger("AndroidOpt");

    public static final NbtOffHeapCompressor INSTANCE = new NbtOffHeapCompressor();

    // UUID de l'entité → données NBT compressées en off-heap
    private final ConcurrentHashMap<UUID, ByteBuffer> compressedData = new ConcurrentHashMap<>();

    // Statistiques
    private final AtomicInteger compressedCount = new AtomicInteger(0);
    private final AtomicInteger decompressedCount = new AtomicInteger(0);
    private final AtomicLong totalBytesOffHeap = new AtomicLong(0);
    private final AtomicLong totalBytesFreedFromHeap = new AtomicLong(0);

    private NbtOffHeapCompressor() {}

    /**
     * Compresse le NBT d'une entité vers un DirectByteBuffer off-heap.
     * L'arbre CompoundTag original peut ensuite être libéré par le GC.
     *
     * @param entityId UUID de l'entité
     * @param nbt le CompoundTag à compresser
     * @return true si la compression a réussi
     */
    public boolean compress(UUID entityId, CompoundTag nbt) {
        if (entityId == null || nbt == null || nbt.isEmpty()) return false;
        if (compressedData.containsKey(entityId)) return false; // déjà compressé

        try {
            // Sérialiser le NBT en bytes
            ByteArrayOutputStream baos = new ByteArrayOutputStream(256);
            DataOutputStream dos = new DataOutputStream(baos);
            NbtIo.write(nbt, dos);
            dos.flush();
            byte[] bytes = baos.toByteArray();

            // Stocker dans un DirectByteBuffer (off-heap, invisible pour le GC)
            ByteBuffer directBuf = ByteBuffer.allocateDirect(bytes.length);
            directBuf.put(bytes);
            directBuf.flip();

            // Remplacer l'ancienne entrée si elle existe
            ByteBuffer old = compressedData.put(entityId, directBuf);
            if (old != null) {
                totalBytesOffHeap.addAndGet(-old.capacity());
            }

            compressedCount.incrementAndGet();
            totalBytesOffHeap.addAndGet(bytes.length);
            // L'arbre NBT Java fait ~5-10x la taille sérialisée en heap
            totalBytesFreedFromHeap.addAndGet(bytes.length * 6L);

            LOGGER.debug("[NBT Off-Heap] Compressed entity {}: {} bytes → off-heap", entityId, bytes.length);
            return true;
        } catch (IOException e) {
            LOGGER.warn("[NBT Off-Heap] Failed to compress entity {}: {}", entityId, e.getMessage());
            return false;
        }
    }

    /**
     * Décompresse le NBT d'une entité depuis l'off-heap.
     * Appelé quand l'entité redevient active (joueur s'approche, combat, etc.)
     *
     * @param entityId UUID de l'entité
     * @return le CompoundTag restauré, ou null si pas compressé
     */
    public CompoundTag decompress(UUID entityId) {
        if (entityId == null) return null;

        ByteBuffer buf = compressedData.remove(entityId);
        if (buf == null) return null;

        try {
            // Lire les bytes du DirectByteBuffer
            byte[] bytes = new byte[buf.remaining()];
            buf.get(bytes);

            totalBytesOffHeap.addAndGet(-buf.capacity());
            decompressedCount.incrementAndGet();

            // Désérialiser le NBT
            ByteArrayInputStream bais = new ByteArrayInputStream(bytes);
            DataInputStream dis = new DataInputStream(bais);
            CompoundTag nbt = NbtIo.read(dis);

            LOGGER.debug("[NBT Off-Heap] Decompressed entity {}: {} bytes", entityId, bytes.length);
            return nbt;
        } catch (IOException e) {
            LOGGER.warn("[NBT Off-Heap] Failed to decompress entity {}: {}", entityId, e.getMessage());
            return null;
        }
    }

    /**
     * Vérifie si une entité a son NBT compressé en off-heap.
     */
    public boolean isCompressed(UUID entityId) {
        return compressedData.containsKey(entityId);
    }

    /**
     * Supprime les données compressées d'une entité (ex: entité supprimée du monde).
     */
    public void remove(UUID entityId) {
        ByteBuffer buf = compressedData.remove(entityId);
        if (buf != null) {
            totalBytesOffHeap.addAndGet(-buf.capacity());
        }
    }

    /**
     * Libère toutes les données compressées (ex: changement de dimension).
     */
    public void clear() {
        int count = compressedData.size();
        compressedData.clear();
        totalBytesOffHeap.set(0);
        if (count > 0) {
            LOGGER.info("[NBT Off-Heap] Cleared {} compressed entities", count);
        }
    }

    // ═══ Statistiques ═══

    public int getCompressedEntityCount() { return compressedData.size(); }
    public long getTotalBytesOffHeap() { return totalBytesOffHeap.get(); }
    public long getTotalBytesFreedFromHeap() { return totalBytesFreedFromHeap.get(); }
    public int getTotalCompressions() { return compressedCount.get(); }
    public int getTotalDecompressions() { return decompressedCount.get(); }

    public String getStats() {
        long offHeapKB = totalBytesOffHeap.get() / 1024;
        long freedMB = totalBytesFreedFromHeap.get() / (1024 * 1024);
        return String.format("NBT Off-Heap: %d entities (%d KB off-heap, ~%d MB freed from heap, %d compress, %d decompress)",
                compressedData.size(), offHeapKB, freedMB,
                compressedCount.get(), decompressedCount.get());
    }
}
