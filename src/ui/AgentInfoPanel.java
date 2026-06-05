package ui;

import javax.media.opengl.GL2;

import agents.Agent;
import agents.Loup;
import agents.Mouton;
import agents.Humain;

/**
 * Fiche détaillée de l'agent suivi (Phase 8). Affichée en bas-gauche tant
 * qu'un agent est sélectionné. Lit l'âge, l'énergie, le comportement courant
 * et l'orientation via l'API publique d'Agent.
 *
 * Tracée en ASCII pour rester compatible GLUT bitmap.
 */
public class AgentInfoPanel {

    private static final int PANEL_WIDTH  = 230;
    private static final int PANEL_HEIGHT = 150;
    private static final int MARGIN = 10;
    private static final int ROW_HEIGHT = 16;

    public void draw(GL2 gl, UiRenderer ui, int viewportWidth, int viewportHeight,
                     Agent agent, int agentIndex, boolean cameraFollow) {
        int px = MARGIN;
        int py = viewportHeight - PANEL_HEIGHT - MARGIN;

        ui.drawQuad(gl, px, py, PANEL_WIDTH, PANEL_HEIGHT, 0.08f, 0.10f, 0.14f, 0.85f);
        ui.drawBorder(gl, px, py, PANEL_WIDTH, PANEL_HEIGHT, 0.5f, 0.6f, 0.75f, 1f);

        // Titre : type + index.
        String title = agent.getTypeName() + " #" + agentIndex;
        ui.drawTitle(gl, px + 10, py + 18, viewportHeight, title, 1f, 1f, 0.7f);

        int textY = py + 38;
        int energieCur = energieOf(agent);
        int energieMax = energieMaxOf(agent);

        ui.drawText(gl, px + 10, textY, viewportHeight,
                String.format("Energie  : %d / %d", energieCur, energieMax),
                0.95f, 0.95f, 0.95f);
        textY += ROW_HEIGHT;

        // Barre d'énergie sur toute la largeur du panneau.
        drawEnergyBar(gl, ui, px + 10, textY - 12, PANEL_WIDTH - 20, 6, energieCur, energieMax);
        textY += 8;

        ui.drawText(gl, px + 10, textY, viewportHeight,
                String.format("Position : (%d, %d)", agent.x, agent.y),
                0.95f, 0.95f, 0.95f);
        textY += ROW_HEIGHT;

        ui.drawText(gl, px + 10, textY, viewportHeight,
                "Etat     : " + agent.getCurrentBehavior(),
                0.95f, 0.95f, 0.95f);
        textY += ROW_HEIGHT;

        ui.drawText(gl, px + 10, textY, viewportHeight,
                String.format("Age      : %.1f jours", agent.getAgeDays()),
                0.95f, 0.95f, 0.95f);
        textY += ROW_HEIGHT;

        ui.drawText(gl, px + 10, textY, viewportHeight,
                "Orient.  : " + agent.getOrientLabel(),
                0.95f, 0.95f, 0.95f);
        textY += ROW_HEIGHT;

        // Indicateur caméra-follow.
        if (cameraFollow) {
            ui.drawText(gl, px + 10, py + PANEL_HEIGHT - 24, viewportHeight,
                    "[f] suivi camera : ON", 0.6f, 1f, 0.6f);
        } else {
            ui.drawText(gl, px + 10, py + PANEL_HEIGHT - 24, viewportHeight,
                    "[f] suivi camera : OFF", 0.6f, 0.7f, 0.85f);
        }

        // Indicateur de pilotage manuel — jaune vif quand actif (ZQSD/flèches :
        // Z/S avance-recule, Q/D pivote). Cohérent avec la flèche jaune en scène.
        if (agent.playerControlled) {
            ui.drawText(gl, px + 10, py + PANEL_HEIGHT - 6, viewportHeight,
                    "[c] CONTROLE MANUEL : ON", 1f, 1f, 0.2f);
        } else {
            ui.drawText(gl, px + 10, py + PANEL_HEIGHT - 6, viewportHeight,
                    "[c] controle manuel : OFF", 0.6f, 0.7f, 0.85f);
        }
    }

    private void drawEnergyBar(GL2 gl, UiRenderer ui, int x, int y, int w, int h,
                               int cur, int max) {
        // Fond gris foncé.
        ui.drawQuad(gl, x, y, w, h, 0.20f, 0.22f, 0.25f, 1f);
        // Barre remplie : vert si > 50%, jaune si > 20%, rouge sinon.
        if (max <= 0) return;
        float ratio = Math.max(0f, Math.min(1f, cur / (float) max));
        float r, g, b;
        if      (ratio > 0.5f) { r = 0.3f; g = 0.85f; b = 0.4f; }
        else if (ratio > 0.2f) { r = 0.9f; g = 0.75f; b = 0.2f; }
        else                   { r = 0.9f; g = 0.3f;  b = 0.3f; }
        ui.drawQuad(gl, x, y, (int)(w * ratio), h, r, g, b, 1f);
    }

    private int energieOf(Agent a) {
        if (a instanceof Loup)   return ((Loup) a).getEnergie();
        if (a instanceof Mouton) return (int) ((Mouton) a).getEnergie();
        if (a instanceof Humain) return ((Humain) a).getEnergie();
        return 0;
    }
    private int energieMaxOf(Agent a) {
        if (a instanceof Loup)   return ((Loup) a).getEnergieMax();
        if (a instanceof Mouton) return (int) ((Mouton) a).getEnergieMax();
        if (a instanceof Humain) return ((Humain) a).getEnergieMax();
        return 1;
    }
}
