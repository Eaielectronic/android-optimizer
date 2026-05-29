package fr.eaielectronic.nativeglengine;

import java.nio.ByteBuffer;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * NativeBufferManager (Module 7: OffHeapArena)
 * 
 * Interagit avec le système d'allocation natif C++ pour créer des ByteBuffer "directs"
 * dont la mémoire réside hors du Heap Java (Off-Heap).
 * Cela permet de réduire drastiquement les pauses du Garbage Collector.
 */
public class NativeBufferManager {
    private static boolean initialized = false;

    // ─── Méthodes JNI Natives ───
    private static native boolean nativeInitArena(long poolSizeMB);
    private static native void nativeDestroyArena();
    private static native ByteBuffer nativeAllocate(long sizeBytes, String tag);
    private static native void nativeFree(ByteBuffer buffer);

    // Pool de recyclage des buffers de 2MB (pour les Chunks)
    private static final int CHUNK_BUFFER_SIZE = 2 * 1024 * 1024;
    private static final Queue<ByteBuffer> chunkPool = new ConcurrentLinkedQueue<>();

    public static void init(long poolSizeMB) {
        if (initialized) return;
        try {
            // Demande l'arène Off-Heap au système Android
            boolean success = nativeInitArena(poolSizeMB);
            if (success) {
                System.out.println("[NativeBufferManager] OffHeapArena " + poolSizeMB + "MB allouée avec succès.");
                initialized = true;
            } else {
                System.err.println("[NativeBufferManager] ECHEC: Impossible d'allouer l'OffHeapArena !");
            }
        } catch (UnsatisfiedLinkError e) {
            System.err.println("[NativeBufferManager] Erreur JNI lors de l'initialisation: " + e.getMessage());
        }
    }

    public static boolean isInitialized() {
        return initialized;
    }

    /**
     * Alloue ou recycle un buffer natif de 2 Mo.
     * Cette mémoire ne sera JAMAIS vue par le Java Garbage Collector.
     */
    public static ByteBuffer acquireChunkBuffer() {
        if (!initialized) {
            // Fallback standard si l'arène a échoué (sera soumis au GC)
            return ByteBuffer.allocateDirect(CHUNK_BUFFER_SIZE);
        }

        ByteBuffer buf = chunkPool.poll();
        if (buf == null) {
            buf = nativeAllocate(CHUNK_BUFFER_SIZE, "ChunkData");
            if (buf == null) {
                System.err.println("[NativeBufferManager] Arène pleine ! Fallback sur ByteBuffer standard.");
                return ByteBuffer.allocateDirect(CHUNK_BUFFER_SIZE);
            }
        }
        buf.clear();
        return buf;
    }

    /**
     * Libère un buffer pour recyclage. 
     * Ne pas utiliser le buffer après cet appel !
     */
    public static void releaseChunkBuffer(ByteBuffer buf) {
        if (!initialized) return;
        
        if (buf != null && buf.isDirect() && buf.capacity() == CHUNK_BUFFER_SIZE) {
            chunkPool.offer(buf);
        }
    }

    /**
     * Alloue une mémoire personnalisée Off-Heap (ne passe pas par le pool).
     */
    public static ByteBuffer allocateCustom(long sizeBytes, String tag) {
        if (!initialized) return ByteBuffer.allocateDirect((int)sizeBytes);
        ByteBuffer buf = nativeAllocate(sizeBytes, tag);
        return (buf != null) ? buf : ByteBuffer.allocateDirect((int)sizeBytes);
    }

    /**
     * Libère définitivement une mémoire personnalisée.
     */
    public static void freeCustom(ByteBuffer buf) {
        if (initialized && buf != null && buf.isDirect()) {
            nativeFree(buf);
        }
    }

    public static void destroy() {
        if (!initialized) return;
        
        ByteBuffer buf;
        while ((buf = chunkPool.poll()) != null) {
            nativeFree(buf);
        }
        nativeDestroyArena();
        initialized = false;
    }
}
