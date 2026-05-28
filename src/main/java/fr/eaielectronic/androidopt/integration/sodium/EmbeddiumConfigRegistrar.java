package fr.eaielectronic.androidopt.integration.sodium;

import org.embeddedt.embeddium.api.OptionGUIConstructionEvent;

public class EmbeddiumConfigRegistrar {

    public static void register() {
        try {
            OptionGUIConstructionEvent.BUS.addListener(event -> {
                event.addPage(AndroidOptSodiumPage.createPage());
            });
            fr.eaielectronic.androidopt.AndroidOptMod.LOGGER.info("[AndroidOpt] Intégration Native Embeddium activée !");
        } catch (Throwable t) {
            fr.eaielectronic.androidopt.AndroidOptMod.LOGGER.error("[AndroidOpt] Erreur lors de l'intégration Embeddium", t);
        }
    }
}
