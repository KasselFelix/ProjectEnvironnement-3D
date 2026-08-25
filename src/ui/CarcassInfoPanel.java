package ui;

import javax.media.opengl.GL2;
import objects.Carcass;
import objects.Species;

/**
 * Fiche detaillee d'une carcasse selectionnee (Task 4.2). Affichee en bas-gauche
 * quand une carcasse est selectionnee par double-clic.
 *
 * Layout (ASCII strict — GLUT Helvetica ne supporte pas les accents) :
 *   Carcasse
 *   Espece   : <mouton|loup|ours|humain>
 *   Poids    : <mass> / <initialMass> kg
 *   Energie  : <ENERGY_PER_KG>/kg  (total <energyValue>)
 *   Fraicheur: <displayFreshness%>%        (ou "Etat : Pourrie" une fois la pourriture entamee)
 */
public class CarcassInfoPanel {

    private static final int PANEL_WIDTH  = 260;
    private static final int PANEL_HEIGHT = 110;
    private static final int MARGIN       = 10;
    private static final int ROW_HEIGHT   = 16;

    /**
     * Dessine la fiche de la carcasse.
     *
     * @param gl            contexte GL courant
     * @param ui            renderer 2D (begin2D deja appele)
     * @param viewportWidth  largeur fenetre en pixels
     * @param viewportHeight hauteur fenetre en pixels
     * @param c             carcasse a afficher (ne pas appeler si isGone())
     */
    public void draw(GL2 gl, UiRenderer ui, int viewportWidth, int viewportHeight,
                     Carcass c) {
        int px = MARGIN;
        int py = viewportHeight - PANEL_HEIGHT - MARGIN;

        // Fond sombre brun-rougeâtre pour distinguer visuellement de AgentInfoPanel
        ui.drawQuad(gl, px, py, PANEL_WIDTH, PANEL_HEIGHT,
                    0.14f, 0.08f, 0.07f, 0.85f);
        ui.drawBorder(gl, px, py, PANEL_WIDTH, PANEL_HEIGHT,
                      0.75f, 0.45f, 0.35f, 1f);

        // Titre
        ui.drawTitle(gl, px + 10, py + 18, viewportHeight,
                     "Carcasse", 1f, 0.75f, 0.55f);

        int textY = py + 36;

        ui.drawText(gl, px + 10, textY, viewportHeight,
                    "Espece   : " + speciesLabel(c.source),
                    0.95f, 0.90f, 0.85f);
        textY += ROW_HEIGHT;

        ui.drawText(gl, px + 10, textY, viewportHeight,
                    String.format("Poids    : %d / %d kg",
                                  Math.round(c.mass),
                                  Math.round(c.initialMass)),
                    0.95f, 0.90f, 0.85f);
        textY += ROW_HEIGHT;

        ui.drawText(gl, px + 10, textY, viewportHeight,
                    String.format("Energie  : %d/kg  (total %d)",
                                  Math.round(Carcass.ENERGY_PER_KG),
                                  Math.round(c.energyValue())),
                    0.95f, 0.90f, 0.85f);
        textY += ROW_HEIGHT;

        // État : une seule jauge de fraîcheur qui décroît sur la fenêtre fraîche ;
        // une fois la pourriture entamée on affiche "Pourrie" et la barre reste à 0%.
        // (Ligne placée APRÈS Energie, juste au-dessus de la barre.)
        boolean rotten = c.isRotten();
        float barFresh = rotten ? 0f : (float) c.displayFreshness();
        if (rotten) {
            ui.drawText(gl, px + 10, textY, viewportHeight,
                        "Etat     : Pourrie",
                        0.55f, 0.45f, 0.30f);
        } else {
            int freshPct = (int) Math.round(barFresh * 100.0);
            ui.drawText(gl, px + 10, textY, viewportHeight,
                        String.format("Fraicheur: %d%%", freshPct),
                        freshPct > 50 ? 0.65f : 0.85f,
                        freshPct > 50 ? 0.90f : 0.65f,
                        freshPct > 50 ? 0.60f : 0.40f);
        }
        textY += ROW_HEIGHT;

        // Barre de fraicheur (0% une fois pourrie)
        drawFreshnessBar(gl, ui, px + 10, textY - 10, PANEL_WIDTH - 20, 5, barFresh);
    }

    /** Barre horizontale verte→rouge selon la fraicheur. */
    private void drawFreshnessBar(GL2 gl, UiRenderer ui,
                                  int x, int y, int w, int h, float fresh) {
        ui.drawQuad(gl, x, y, w, h, 0.20f, 0.18f, 0.15f, 1f);
        if (w <= 0) return;
        float r = 0.2f + 0.75f * (1f - fresh);  // rouge monte en pourrissant
        float g = 0.7f * fresh;                  // vert baisse en pourrissant
        float b = 0.10f;
        ui.drawQuad(gl, x, y, (int)(w * Math.max(0f, Math.min(1f, fresh))), h, r, g, b, 1f);
    }

    /** Etiquette d'espece ASCII. Miroir de speciesLabel dans Landscape. */
    static String speciesLabel(Species s) {
        switch (s) {
            case MOUTON: return "mouton";
            case LOUP:   return "loup";
            case OURS:   return "ours";
            case HUMAIN: return "humain";
            default:     return "animal";
        }
    }
}
