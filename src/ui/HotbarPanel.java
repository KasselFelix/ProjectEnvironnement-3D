package ui;

import javax.media.opengl.GL2;
import agents.Agent;
import input.HotbarAction;
import input.HotbarLayout;
import objects.Species;

/** Barre d'actions MMO (9 slots), bas-centre, visible uniquement en pilotage.
 *  Slot : cadre + chiffre + libelle ; grise si non realisable ; flash rouge sur refus. */
public final class HotbarPanel {
    private static final int SLOT_W = 64, SLOT_H = 40, GAP = 6, MARGIN_BOTTOM = 14;

    public void draw(GL2 gl, UiRenderer ui, int viewportWidth, int viewportHeight,
                     HotbarLayout layout, Species species, Agent controlled,
                     int flashSlot, long flashUntilMs) {
        int total = HotbarLayout.SLOTS * SLOT_W + (HotbarLayout.SLOTS - 1) * GAP;
        int x0 = (viewportWidth - total) / 2;
        int y0 = viewportHeight - SLOT_H - MARGIN_BOTTOM;
        boolean flashing = System.currentTimeMillis() < flashUntilMs;

        for (int i = 0; i < HotbarLayout.SLOTS; i++) {
            int x = x0 + i * (SLOT_W + GAP);
            HotbarAction a = layout.slot(species, i);
            boolean avail = a != HotbarAction.VIDE && a.available(controlled);

            ui.drawQuad(gl, x, y0, SLOT_W, SLOT_H, 0.08f, 0.08f, 0.10f, 0.78f);
            if (flashing && i == flashSlot) ui.drawBorder(gl, x, y0, SLOT_W, SLOT_H, 0.95f, 0.25f, 0.20f, 1f);
            else ui.drawBorder(gl, x, y0, SLOT_W, SLOT_H, 0.55f, 0.55f, 0.60f, 1f);

            ui.drawText(gl, x + 4, y0 + 13, viewportHeight, String.valueOf(i + 1), 0.85f, 0.85f, 0.55f);

            if (a != HotbarAction.VIDE) {
                float c = avail ? 0.95f : 0.42f;
                String label = a.label;
                // tronque si trop large pour le slot
                while (label.length() > 1 && ui.textWidth(label) > SLOT_W - 10) label = label.substring(0, label.length() - 1);
                ui.drawText(gl, x + 6, y0 + 28, viewportHeight, label, c, c, c);
            }
        }
    }
}
