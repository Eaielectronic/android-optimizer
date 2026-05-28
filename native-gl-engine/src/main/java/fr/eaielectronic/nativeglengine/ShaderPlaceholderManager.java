package fr.eaielectronic.nativeglengine;

import net.minecraft.client.renderer.ShaderInstance;

/**
 * Gère les shaders de remplacement (placeholders) pendant la compilation asynchrone.
 * 
 * Si un shader est en cours de compilation en arrière-plan, cette classe
 * fournit un shader "magenta" temporaire pour éviter que le jeu ne freeze
 * ou ne plante, tout en indiquant visuellement que la compilation est en cours.
 */
public final class ShaderPlaceholderManager {

    private ShaderPlaceholderManager() {}

    /**
     * @return un code source ESSL/GLSL très simple qui affiche du magenta.
     */
    public static String getMagentaFragmentShader() {
        return "#version 150\n" +
               "out vec4 fragColor;\n" +
               "void main() {\n" +
               "    fragColor = vec4(1.0, 0.0, 1.0, 1.0);\n" +
               "}\n";
    }

    /**
     * @return un code source ESSL/GLSL très simple pour un vertex shader basique.
     */
    public static String getPassthroughVertexShader() {
        return "#version 150\n" +
               "in vec3 Position;\n" +
               "uniform mat4 ModelViewMat;\n" +
               "uniform mat4 ProjMat;\n" +
               "void main() {\n" +
               "    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);\n" +
               "}\n";
    }

    // TODO: Intégrer avec l'API NeoForge / Minecraft pour retourner
    // dynamiquement une instance de ShaderInstance valide.
}
