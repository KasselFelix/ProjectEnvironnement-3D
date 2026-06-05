package objects.blocks;

import javax.media.opengl.GL2;

import objects.CommonObject;
import objects.LavaLineage;
import worlds.World;

/**
 * Rendu d'un bloc de lave avec couleur basée sur la phase physique
 * (refonte 2026-05-27).
 *
 * 3 phases déterminées par le `state` de la couche LAVA :
 *  - Phase 1 (state ∈ [0, solidifyStartTicks]) : liquide chaud, RGB interp
 *    (1.0, 0.65, 0.0) → (1.0, 0.45, 0.0). Shimmer plein.
 *  - Phase 2 (state ∈ [solidifyStartTicks, solidifyEndTicks]) : cooling visible,
 *    RGB interp (0.45, 0.25, 0.0) → (0.15, 0.02, 0.0). Effet croûte ≥ 85%.
 *  - Persistent : clamp en Phase 1 milieu → (1.0, 0.55, 0.0) constant.
 *
 * Le lineage tint (signature couleur de l'éruption d'origine) est atténué en
 * Phase 2 par `(1 - phase2Progress)` — la lave qui refroidit perd son identité
 * chromatique propre.
 */
public class LavaBlock extends CommonObject {

    /** Influence de la signature lineage sur la couleur. */
    private static final float LINEAGE_HUE_INFLUENCE = 0.5f;
    /** Pour le mélange inter-sources (eruptionId == -1), petite teinte spéciale. */
    private static final float BLEND_HUE_TWEAK = 0.04f;

    /** Fin Phase 2 : à partir de ce ratio, on applique l'effet croûte. */
    private static final float CRUST_THRESHOLD_RATIO = 0.85f;
    /** Seuil du bruit déterministe pour assombrir (= ~30% des cellules au-dessus). */
    private static final float CRUST_NOISE_THRESHOLD = 0.7f;
    /** Facteur d'assombrissement appliqué aux cellules "craquelées". */
    private static final float CRUST_DARKEN_FACTOR = 0.4f;
    /** Amplitude du bruit shimmer en Phase 1 (atténué progressivement en Phase 2). */
    private static final float SHIMMER_NOISE_AMPLITUDE = 0.03f;

    // ── Émission lumineuse (Stefan-Boltzmann, 2026-05-27) ────────────────────
    // La luminosité émise par un corps chaud suit P = ε × σ × T⁴ (loi de
    // Stefan-Boltzmann). σ = 5.67e-8 W/m²/K⁴, ε ≈ 0.95 pour la lave.
    // Normalisé : L = (T / T_LAVA_MAX)⁴ ∈ [0, 1].
    // Contexte : 1 bloc = 1 m³, donc la surface émissive d'une face = 1 m².
    /** Température de la lave fraîche en sortie de cratère (K). 1200°C = 1473K. */
    private static final float T_LAVA_MAX      = 1473f;
    /** Température à laquelle la lave commence à solidifier (K). 700°C = 973K. */
    private static final float T_LAVA_SOLIDIFY = 973f;
    /** Émission OpenGL nulle (pour reset après chaque bloc). */
    private static final float[] ZERO_EMISSION = { 0f, 0f, 0f, 1f };
    /** Buffer réutilisable pour glMaterialfv (évite allocations par frame). */
    private static final float[] EMISSION_BUF  = { 0f, 0f, 0f, 1f };

    /**
     * Dessine un bloc de lave (6 faces) avec couleur basée sur phase + signature.
     *
     * @param solidifyStartTicks seuil de début de Phase 2 (= 0.6 × solidifyEndTicks)
     * @param solidifyEndTicks seuil de transformation en STONE
     */
    public static void drawAt(World myWorld, GL2 gl,
                              float x, float y, float zBase, float h, int cellState,
                              boolean persistent, LavaLineage lineage,
                              int solidifyStartTicks, int solidifyEndTicks,
                              float offset, float stepX, float stepY,
                              float lenX, float lenY,
                              int movingX, int movingY) {
        if (h <= 0) return;

        float r, g, b;
        float lineageStrength;
        boolean addCrust = false;

        if (persistent) {
            // Persistent : milieu de Phase 1 (clamp dans ageLavaAndSolidify).
            // Couleur orange chaud (moins jaune) — lave en mouvement permanent.
            r = 1.0f; g = 0.28f; b = 0.0f;
            lineageStrength = 1.0f;
        } else if (cellState <= solidifyStartTicks) {
            // PHASE 1 : liquide chaud, interp orange vif → orange-rouge saturé.
            // G ajusté à la baisse (2026-05-27) pour compenser la saturation jaune
            // qu'ajoutait la lumière diffuse du soleil en plein jour (g_final saturait
            // à ~0.95 → jaune citron). Avec g_base réduit, le soleil ne peut plus
            // pousser g au-delà de ~0.55 → orange préservé même de jour.
            float t = (float) cellState / Math.max(1, solidifyStartTicks);
            if (t < 0f) t = 0f;
            if (t > 1f) t = 1f;
            r = 1.0f;
            g = 0.35f - 0.15f * t;  // 0.35 → 0.20 (était 0.50 → 0.30)
            b = 0.0f;
            lineageStrength = 1.0f;
        } else {
            // PHASE 2 : cooling, interp orange-rouge → quasi noir.
            float t = (float)(cellState - solidifyStartTicks)
                    / (float) Math.max(1, solidifyEndTicks - solidifyStartTicks);
            if (t < 0f) t = 0f;
            if (t > 1f) t = 1f;
            r = 0.50f + (0.15f - 0.50f) * t;  // 0.50 → 0.15 (un peu plus chaud au début)
            g = 0.20f - 0.18f * t;            // 0.20 → 0.02
            b = 0.0f;
            lineageStrength = 1.0f - t;  // tint s'efface en refroidissant
            addCrust = t >= CRUST_THRESHOLD_RATIO;
        }

        // Lineage tint (atténué en Phase 2)
        float[] tinted = applyLineageTintScaled(r, g, b, lineage, lineageStrength);
        r = tinted[0]; g = tinted[1]; b = tinted[2];

        // Effet croûte (Phase 2 fin) : pattern déterministe par (x, y).
        if (addCrust) {
            int cx = (int) x + movingX;
            int cy = (int) y + movingY;
            if (crustNoise(cx, cy, 7) > CRUST_NOISE_THRESHOLD) {
                r *= CRUST_DARKEN_FACTOR;
                g *= CRUST_DARKEN_FACTOR;
            }
        }

        // Shimmer noise (atténué en Phase 2).
        float shimmer = SHIMMER_NOISE_AMPLITUDE * lineageStrength;
        if (shimmer > 0) {
            r += (float)((Math.random() - 0.5) * shimmer);
            g += (float)((Math.random() - 0.5) * shimmer * 0.5);
        }

        // Clamp final.
        if (r < 0) r = 0; if (r > 1) r = 1;
        if (g < 0) g = 0; if (g > 1) g = 1;
        if (b < 0) b = 0; if (b > 1) b = 1;

        // Émission lumineuse (Stefan-Boltzmann) — la lave émet de la lumière
        // propre via GL_EMISSION, additive aux composantes ambient/diffuse.
        // Note : glMaterialfv est autorisé entre glBegin/glEnd, contrairement à
        // glEnable/glDisable (qui ne le sont pas et causent des artefacts).
        float emission = computeEmissionLevel(persistent, cellState, solidifyStartTicks, solidifyEndTicks);
        EMISSION_BUF[0] = r * emission;
        EMISSION_BUF[1] = g * emission;
        EMISSION_BUF[2] = b * emission;
        gl.glMaterialfv(GL2.GL_FRONT_AND_BACK, GL2.GL_EMISSION, EMISSION_BUF, 0);

        gl.glColor3f(r, g, b);

        float zTop = zBase + h;
        // 4 faces verticales + dessus + dessous (même topologie que StoneBlock).
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

        gl.glVertex3f(offset+x*stepX-lenX, offset+y*stepY-lenY, zTop);
        gl.glVertex3f(offset+x*stepX-lenX, offset+y*stepY+lenY, zTop);
        gl.glVertex3f(offset+x*stepX+lenX, offset+y*stepY+lenY, zTop);
        gl.glVertex3f(offset+x*stepX+lenX, offset+y*stepY-lenY, zTop);

        gl.glVertex3f(offset+x*stepX-lenX, offset+y*stepY-lenY, zBase);
        gl.glVertex3f(offset+x*stepX-lenX, offset+y*stepY+lenY, zBase);
        gl.glVertex3f(offset+x*stepX+lenX, offset+y*stepY+lenY, zBase);
        gl.glVertex3f(offset+x*stepX+lenX, offset+y*stepY-lenY, zBase);

        // Reset émission pour ne pas contaminer les blocs suivants (autorisé entre glBegin/glEnd).
        gl.glMaterialfv(GL2.GL_FRONT_AND_BACK, GL2.GL_EMISSION, ZERO_EMISSION, 0);
    }

    /**
     * Calcule l'intensité d'émission lumineuse d'une couche LAVA selon sa phase.
     * Suit la loi de Stefan-Boltzmann : L = (T / T_max)⁴ ∈ [0, 1].
     *
     * Températures physiques :
     *  - Phase 1 (jeune lave) : T ≈ T_LAVA_MAX (1473K = 1200°C) → L = 1.0
     *  - Phase 2 (cooling) : T interp T_LAVA_MAX → T_LAVA_SOLIDIFY (973K = 700°C)
     *    → L décroît de 1.0 à (973/1473)⁴ ≈ 0.19
     *  - Persistent (lac cratère) : T ≈ 0.95 × T_LAVA_MAX → L ≈ 0.81
     *
     * Au-delà de solidifyEndTicks, la couche est convertie en STONE/BASALT/etc.
     * (cf. LavaCA.ageLavaAndSolidify) — n'est plus rendue par LavaBlock.
     */
    private static float computeEmissionLevel(boolean persistent, int state,
                                              int solidifyStartTicks, int solidifyEndTicks) {
        float T;
        if (persistent) {
            T = T_LAVA_MAX * 0.95f;
        } else if (state <= solidifyStartTicks) {
            T = T_LAVA_MAX;
        } else if (state <= solidifyEndTicks) {
            float p = (float)(state - solidifyStartTicks)
                    / Math.max(1, solidifyEndTicks - solidifyStartTicks);
            T = T_LAVA_MAX + (T_LAVA_SOLIDIFY - T_LAVA_MAX) * p;
        } else {
            T = T_LAVA_SOLIDIFY;
        }
        float ratio = T / T_LAVA_MAX;
        return ratio * ratio * ratio * ratio;  // T⁴ normalisé
    }

    /**
     * Applique la teinte de la signature `lineage` à un RGB de base avec
     * une intensité variable (= 1.0 en Phase 1, dégradé en Phase 2).
     */
    private static float[] applyLineageTintScaled(float r, float g, float b,
                                                   LavaLineage lineage, float strength) {
        if (lineage == null || lineage.source == null || strength <= 0) return new float[]{r, g, b};
        float shift = lineage.source.colorHueShift * LINEAGE_HUE_INFLUENCE * strength;
        if (lineage.source.eruptionId == -1) shift += BLEND_HUE_TWEAK * strength;
        r = Math.max(0f, Math.min(1f, r + shift));
        g = Math.max(0f, Math.min(1f, g - shift * 0.5f));
        float satMod = 1f + (lineage.source.colorSatFactor - 1f) * strength;
        if (satMod != 1f) {
            float avg = (r + g + b) / 3f;
            r = Math.max(0f, Math.min(1f, avg + (r - avg) * satMod));
            g = Math.max(0f, Math.min(1f, avg + (g - avg) * satMod));
            b = Math.max(0f, Math.min(1f, avg + (b - avg) * satMod));
        }
        return new float[]{r, g, b};
    }

    /** Hash déterministe pour bruit stable par cellule (pas de scintillement frame-à-frame). */
    private static float crustNoise(int x, int y, int seed) {
        int h = x * 374761393 + y * 668265263 + seed * 1274126177;
        h = (h ^ (h >>> 13)) * 1274126177;
        h = h ^ (h >>> 16);
        return ((h & 0x7FFFFFFF) % 1000) / 1000f;
    }

    /**
     * Wrapper de rétro-compatibilité. WorldOfCells.displayObjectLave continue
     * de l'appeler ; désormais no-op puisque le rendu lave passe par drawAt
     * depuis la boucle de stack dans Landscape.display.
     */
    public static void displayObjectAt(World myWorld, GL2 gl, int cellState,
                                       float x, float y, double height,
                                       float offset, float stepX, float stepY,
                                       float lenX, float lenY,
                                       float normalizeHeight,
                                       int movingX, int movingY) {
        // No-op : voir drawAt.
    }
}
