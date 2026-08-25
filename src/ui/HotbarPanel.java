package ui;

import javax.media.opengl.GL2;
import agents.Agent;
import input.HotbarAction;
import input.HotbarLayout;
import objects.Species;

/** Barre d'actions MMO (9 slots), bas-centre, visible uniquement en pilotage.
 *  Slot : cadre + chiffre + libelle ; grise si non realisable ; flash rouge sur refus,
 *  flash vert sur declenchement reussi ; halo jaune pulsant tant que l'action est active
 *  (ex: hurlement en cours) ; balayage radial sombre facon MMO + secondes restantes
 *  pendant le cooldown (manger, hurler). */
public final class HotbarPanel {
    private static final int SLOT_W = 64, SLOT_H = 40, GAP = 6, MARGIN_BOTTOM = 14;

    public void draw(GL2 gl, UiRenderer ui, int viewportWidth, int viewportHeight,
                     HotbarLayout layout, Species species, Agent controlled,
                     int flashSlot, long flashUntilMs,
                     int successSlot, long successUntilMs) {
        int total = HotbarLayout.SLOTS * SLOT_W + (HotbarLayout.SLOTS - 1) * GAP;
        int x0 = (viewportWidth - total) / 2;
        int y0 = viewportHeight - SLOT_H - MARGIN_BOTTOM;
        long now = System.currentTimeMillis();
        boolean refusing = now < flashUntilMs;
        boolean confirming = now < successUntilMs;

        for (int i = 0; i < HotbarLayout.SLOTS; i++) {
            int x = x0 + i * (SLOT_W + GAP);
            HotbarAction a = layout.slot(species, i);
            boolean empty = (a == HotbarAction.VIDE);
            boolean avail = !empty && a.available(controlled);
            boolean active = !empty && a.isActive(controlled);
            double cd = empty ? 0.0 : a.cooldownFraction(controlled);

            // Fond du slot.
            ui.drawQuad(gl, x, y0, SLOT_W, SLOT_H, 0.08f, 0.08f, 0.10f, 0.78f);

            // Chiffre de touche + libelle (seront assombris par le balayage de cooldown).
            ui.drawText(gl, x + 4, y0 + 13, viewportHeight, String.valueOf(i + 1), 0.85f, 0.85f, 0.55f);
            if (!empty) {
                float c = avail ? 0.95f : 0.42f;
                String label = a.label;
                while (label.length() > 1 && ui.textWidth(label) > SLOT_W - 10) label = label.substring(0, label.length() - 1);
                ui.drawText(gl, x + 6, y0 + 28, viewportHeight, label, c, c, c);
            }

            // Balayage radial du cooldown (sauf si l'action est en cours d'execution).
            if (cd > 0.001 && !active) {
                drawCooldownSweep(gl, x, y0, SLOT_W, SLOT_H, cd, viewportHeight);
                String secs = formatSeconds(a.cooldownSeconds(controlled));
                int tw = ui.textWidth(secs);
                ui.drawText(gl, x + (SLOT_W - tw) / 2, y0 + SLOT_H / 2 + 5, viewportHeight, secs, 1f, 1f, 0.85f);
            }

            // Cadre, par priorite : succes (vert) > refus (rouge) > actif (jaune pulsant) > normal (gris).
            if (confirming && i == successSlot) {
                ui.drawBorder(gl, x, y0, SLOT_W, SLOT_H, 0.30f, 0.95f, 0.35f, 1f);
            } else if (refusing && i == flashSlot) {
                ui.drawBorder(gl, x, y0, SLOT_W, SLOT_H, 0.95f, 0.25f, 0.20f, 1f);
            } else if (active) {
                float pulse = 0.55f + 0.45f * (float) Math.sin(now * 0.012);
                ui.drawBorder(gl, x, y0, SLOT_W, SLOT_H, 1f, 0.90f, 0.20f, pulse);
            } else {
                ui.drawBorder(gl, x, y0, SLOT_W, SLOT_H, 0.55f, 0.55f, 0.60f, 1f);
            }
        }
    }

    /** Voile sombre en "part de tarte" couvrant la fraction {@code frac} restante du cooldown,
     *  partant du haut (12h) et tournant dans le sens horaire — facon cooldown MMO. Clippe au
     *  slot via le scissor test (l'eventail deborde sinon sur les voisins). */
    private void drawCooldownSweep(GL2 gl, int x, int y, int w, int h, double frac, int viewportHeight) {
        // Scissor en coordonnees fenetre (origine bas-gauche) : le slot occupe [x, x+w] x [vh-(y+h), vh-y].
        gl.glEnable(GL2.GL_SCISSOR_TEST);
        gl.glScissor(x, viewportHeight - (y + h), w, h);

        float cx = x + w / 2f, cy = y + h / 2f;
        float r = (float) Math.hypot(w, h);            // couvre les coins du slot
        int segs = Math.max(2, (int) Math.round(64 * frac));
        double a1 = 2 * Math.PI * frac;

        gl.glColor4f(0f, 0f, 0f, 0.55f);
        gl.glBegin(GL2.GL_TRIANGLE_FAN);
        gl.glVertex2f(cx, cy);
        for (int i = 0; i <= segs; i++) {
            double t = a1 * i / segs;                  // 0 = haut, croissant dans le sens horaire
            gl.glVertex2f(cx + (float) (r * Math.sin(t)), cy - (float) (r * Math.cos(t)));
        }
        gl.glEnd();

        gl.glDisable(GL2.GL_SCISSOR_TEST);
    }

    /** Secondes restantes en ASCII : "1.4" sous 10 s, sinon arrondi entier. */
    private static String formatSeconds(double s) {
        if (s <= 0) return "";
        return s < 10.0 ? String.format(java.util.Locale.US, "%.1f", s) : String.valueOf(Math.round(s));
    }
}
