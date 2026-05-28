package fr.eaielectronic.nativeglengine;

public class NativeEngineState {
    /**
     * Devient true après quelques ticks de rendu.
     * Utilisé pour éviter que le Mixin GlStateManagerMixin n'intercepte
     * et n'annule les uploads de texture initiaux des mods (ex: Veil).
     */
    public static volatile boolean renderingReady = false;
}
