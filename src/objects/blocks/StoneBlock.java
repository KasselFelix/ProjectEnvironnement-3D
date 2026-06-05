package objects.blocks;

import javax.media.opengl.GL2;

import objects.CommonObject;
import objects.LavaLineage;
import objects.Material;
import worlds.World;
import cellularautomata.ecosystem.LavaCA;

public class StoneBlock extends CommonObject {

    // === Modulation couleur (Sujet D2) ===
    // Couleurs de référence pour le blend altitude/distance. Chaque matériau
    // a une couleur "centre de palier" et blend partiellement vers le palier
    // voisin selon la position de la cellule. Plafond de blend = 30% pour
    // que chaque matériau reste identifiable visuellement.
    private static final float[] STONE_RGB    = {0.55f, 0.55f, 0.60f};
    private static final float[] GRANITE_RGB  = {0.75f, 0.55f, 0.55f};
    private static final float[] BASALT_RGB   = {0.30f, 0.32f, 0.40f};
    private static final float BLEND_MAX      = 0.30f;

    /**
     * Dessine un bloc minéral (6 faces) à la position cellule (x, y), base
     * à `zBase`, hauteur `h`. La couleur dépend du `material` passé :
     * STONE (pierre grise par défaut) ou OBSIDIAN (noir vitreux). Couleur
     * déterministe via stableNoise (pas de scintillement par frame).
     *
     * Doit être appelé dans un glBegin(GL_QUADS) déjà ouvert.
     */
    public static void drawAt(World myWorld, GL2 gl, Material material,
                              float x, float y, float zBase, float h,
                              float offset, float stepX, float stepY,
                              float lenX, float lenY,
                              int movingX, int movingY) {
        drawAt(myWorld, gl, material, x, y, zBase, h, null, offset, stepX, stepY, lenX, lenY, movingX, movingY);
    }

    /** Variante avec lineage (Module 4) — teinte le minéral selon la signature de la source d'origine. */
    public static void drawAt(World myWorld, GL2 gl, Material material,
                              float x, float y, float zBase, float h, LavaLineage lineage,
                              float offset, float stepX, float stepY,
                              float lenX, float lenY,
                              int movingX, int movingY) {
        if (h <= 0) return;
        double height = myWorld.getCellHeight((int)x + movingX, (int)y + movingY);
        int cx = (int) x + movingX;
        int cy = (int) y + movingY;
        applyMaterialColor(gl, material, height, myWorld.getMaxEverHeight(), cx, cy, lineage);

        float zTop = zBase + h;
        // 4 faces verticales + dessus + dessous
        gl.glVertex3f(offset+x*stepX-lenX, offset+y*stepY-lenY, zBase);
        gl.glVertex3f(offset+x*stepX-lenX, offset+y*stepY-lenY, zTop);
        gl.glVertex3f(offset+x*stepX+lenX, offset+y*stepY-lenY, zTop);
        gl.glVertex3f(offset+x*stepX+lenX, offset+y*stepY-lenY, zBase);

        gl.glVertex3f(offset+x*stepX+lenX, offset+y*stepY+lenY, zBase);
        gl.glVertex3f(offset+x*stepX+lenX, offset+y*stepY+lenY, zTop);
        gl.glVertex3f(offset+x*stepX-lenX, offset+y*stepY+lenY, zTop);
        gl.glVertex3f(offset+x*stepX-lenX, offset+y*stepY+lenY, zBase);

        gl.glVertex3f(offset+x*stepX+lenX, offset+y*stepY-lenY, zBase);
        gl.glVertex3f(offset+x*stepX+lenX, offset+y*stepY-lenY, zTop);
        gl.glVertex3f(offset+x*stepX+lenX, offset+y*stepY+lenY, zTop);
        gl.glVertex3f(offset+x*stepX+lenX, offset+y*stepY+lenY, zBase);

        gl.glVertex3f(offset+x*stepX-lenX, offset+y*stepY+lenY, zBase);
        gl.glVertex3f(offset+x*stepX-lenX, offset+y*stepY+lenY, zTop);
        gl.glVertex3f(offset+x*stepX-lenX, offset+y*stepY-lenY, zTop);
        gl.glVertex3f(offset+x*stepX-lenX, offset+y*stepY-lenY, zBase);

        // Dessus
        gl.glVertex3f(offset+x*stepX-lenX, offset+y*stepY-lenY, zTop);
        gl.glVertex3f(offset+x*stepX-lenX, offset+y*stepY+lenY, zTop);
        gl.glVertex3f(offset+x*stepX+lenX, offset+y*stepY+lenY, zTop);
        gl.glVertex3f(offset+x*stepX+lenX, offset+y*stepY-lenY, zTop);

        // Dessous
        gl.glVertex3f(offset+x*stepX-lenX, offset+y*stepY-lenY, zBase);
        gl.glVertex3f(offset+x*stepX-lenX, offset+y*stepY+lenY, zBase);
        gl.glVertex3f(offset+x*stepX+lenX, offset+y*stepY+lenY, zBase);
        gl.glVertex3f(offset+x*stepX+lenX, offset+y*stepY-lenY, zBase);
    }

    /**
     * Wrapper de rétro-compatibilité (no-op). Le rendu pierre passe par
     * drawAt depuis la boucle de stack dans Landscape.display.
     */
    public static void displayObjectAt(World myWorld, GL2 gl, int cellState,
                                       float x, float y, double height,
                                       float offset, float stepX, float stepY,
                                       float lenX, float lenY,
                                       float normalizeHeight,
                                       int movingX, int movingY) {
        // No-op
    }

    /**
     * Applique la couleur GL appropriée pour le matériau donné. Centralisé
     * ici pour que tout l'ajout futur de variétés (BASALT, GRANITE, …) ne
     * touche que cette méthode. La couleur intègre un léger bruit
     * déterministe par cellule (via stableNoise) pour donner du grain sans
     * scintillement.
     */
    private static void applyMaterialColor(GL2 gl, Material material,
                                           double height, double maxHeight,
                                           int cx, int cy) {
        applyMaterialColor(gl, material, height, maxHeight, cx, cy, null);
    }

    private static void applyMaterialColor(GL2 gl, Material material,
                                           double height, double maxHeight,
                                           int cx, int cy, LavaLineage lineage) {
        double fracAlt = Math.max(0, Math.min(1, height / maxHeight));
        float r = 0.5f, g = 0.5f, b = 0.5f;

        switch (material) {
            case STONE: {
                double factor = Math.max(0, Math.min(1, (fracAlt - 0.3) / 0.3)) * BLEND_MAX;
                r = (float)(STONE_RGB[0] + (GRANITE_RGB[0] - STONE_RGB[0]) * factor)
                        + (float)(0.02 * stableNoise(cx, cy, 1));
                g = (float)(STONE_RGB[1] + (GRANITE_RGB[1] - STONE_RGB[1]) * factor)
                        + (float)(0.02 * stableNoise(cx, cy, 2));
                b = (float)(STONE_RGB[2] + (GRANITE_RGB[2] - STONE_RGB[2]) * factor)
                        + (float)(0.02 * stableNoise(cx, cy, 3));
                break;
            }
            case OBSIDIAN: {
                r = 0.08f + (float)(0.03 * stableNoise(cx, cy, 1));
                g = 0.04f + (float)(0.03 * stableNoise(cx, cy, 2));
                b = 0.14f + (float)(0.04 * stableNoise(cx, cy, 3));
                break;
            }
            case BASALT: {
                double dist = Math.hypot(cx - LavaCA.sourceX, cy - LavaCA.sourceY);
                double distFrac = dist / Math.max(1, LavaCA.eruptionRadius);
                double factor = Math.max(0, Math.min(1, (distFrac - 0.3) / 0.3)) * BLEND_MAX;
                r = (float)(BASALT_RGB[0] + (STONE_RGB[0] - BASALT_RGB[0]) * factor)
                        + (float)(0.03 * stableNoise(cx, cy, 1));
                g = (float)(BASALT_RGB[1] + (STONE_RGB[1] - BASALT_RGB[1]) * factor)
                        + (float)(0.03 * stableNoise(cx, cy, 2));
                b = (float)(BASALT_RGB[2] + (STONE_RGB[2] - BASALT_RGB[2]) * factor)
                        + (float)(0.04 * stableNoise(cx, cy, 3));
                break;
            }
            case GRANITE: {
                double factor = (1.0 - Math.max(0, Math.min(1, (fracAlt - 0.6) / 0.4))) * BLEND_MAX;
                r = (float)(GRANITE_RGB[0] + (STONE_RGB[0] - GRANITE_RGB[0]) * factor)
                        + (float)(0.05 * stableNoise(cx, cy, 1));
                g = (float)(GRANITE_RGB[1] + (STONE_RGB[1] - GRANITE_RGB[1]) * factor)
                        + (float)(0.05 * stableNoise(cx, cy, 2));
                b = (float)(GRANITE_RGB[2] + (STONE_RGB[2] - GRANITE_RGB[2]) * factor)
                        + (float)(0.05 * stableNoise(cx, cy, 3));
                break;
            }
            default:
                break;
        }

        // Module 4 : si le bloc est issu d'une coulée LAVA (lineage != null),
        // applique une teinte selon la signature de l'éruption d'origine.
        // OBSIDIAN exclu (couleur de référence intentionnellement uniforme).
        if (lineage != null && lineage.source != null && material != Material.OBSIDIAN) {
            float shift = lineage.source.colorHueShift * 0.4f;
            r = Math.max(0f, Math.min(1f, r + shift));
            g = Math.max(0f, Math.min(1f, g - shift * 0.3f));
        }

        gl.glColor3f(r, g, b);
    }
}
