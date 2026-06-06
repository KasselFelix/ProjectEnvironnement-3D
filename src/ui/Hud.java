package ui;

import javax.media.opengl.GL2;

import worlds.World;

/**
 * Bandeau d'informations permanent en haut de l'écran.
 *
 * Affiche : itération courante, libellé jour/nuit + heure du jeu, compteurs
 * (loups, moutons, humains), FPS. Fond semi-transparent pour rester lisible
 * par-dessus le paysage.
 */
public class Hud {

    private static final int HEIGHT = 24;
    private static final int PADDING_X = 8;
    private static final int TEXT_Y_OFFSET = 16;  // distance entre le haut de la barre et la baseline du texte

    public void draw(GL2 gl, UiRenderer ui,
                     int viewportWidth, int viewportHeight,
                     World world,
                     String dayLabel, String gameTime,
                     int fps,
                     String playbackLabel, boolean paused) {

        // Fond noir semi-transparent.
        ui.drawQuad(gl, 0, 0, viewportWidth, HEIGHT, 0f, 0f, 0f, 0.55f);

        int iter    = world.getIteration();
        int loups   = world.loups.size();
        int moutons = world.moutons.size();
        int humains = world.humains.size();
        int oursN   = world.ours.size();

        // L5/L6 — saison courante (+ jour-jeu) et météo (température, pluie).
        String saison = world.currentSeason().label;
        String meteo  = String.format(java.util.Locale.US, "%.0fC%s",
                world.getTemperature(), world.isRaining() ? " pluie" : "");

        String text = String.format(
                "Iter:%d  |  %s %s  |  %s J%d %s  |  Ours:%d  Loups:%d  Moutons:%d  Humains:%d  |  FPS:%d  |  ",
                iter, dayLabel, gameTime, saison, world.getCurrentDay(), meteo, oursN, loups, moutons, humains, fps);

        ui.drawText(gl, PADDING_X, TEXT_Y_OFFSET, viewportHeight, text, 1f, 1f, 1f);

        // État de lecture en fin de bandeau : jaune vif en pause (attire l'œil),
        // vert tendre en lecture. Positionné après le texte principal (largeur
        // GLUT bitmap ≈ 6 px/char pour Helvetica 12).
        int labelX = PADDING_X + text.length() * 6;
        if (paused) ui.drawText(gl, labelX, TEXT_Y_OFFSET, viewportHeight, playbackLabel, 1f, 0.85f, 0.2f);
        else        ui.drawText(gl, labelX, TEXT_Y_OFFSET, viewportHeight, playbackLabel, 0.6f, 1f, 0.6f);

        // V6 — seconde ligne : compteurs de dommages cumulés.
        worlds.EventLog ev = world.events;
        String dmg = String.format(
                "Degats  |  Arbres brules:%d   Agents morts:%d   Lave emise:%d",
                ev.treesBurned, ev.agentDeaths, ev.lavaEmitted);
        ui.drawQuad(gl, 0, HEIGHT, viewportWidth, 18, 0f, 0f, 0f, 0.40f);
        ui.drawText(gl, PADDING_X, HEIGHT + 14, viewportHeight, dmg, 1f, 0.7f, 0.5f);

        // V6 — notifications transitoires (« 12 moutons morts ! »), empilées
        // sous le bandeau, en fondu sur leur durée de vie.
        int ny = HEIGHT + 18 + 22;
        for (worlds.EventLog.Notif n : ev.activeNotifications(iter, NOTIF_TTL)) {
            float age = (iter - n.iteration) / (float) NOTIF_TTL;     // 0 → 1
            float k = Math.max(0.25f, 1f - age);   // fondu par assombrissement (drawText sans alpha)
            ui.drawText(gl, PADDING_X + 4, ny, viewportHeight, n.message, 1f * k, 0.85f * k, 0.2f * k);
            ny += 18;
            if (ny > viewportHeight - 40) break;   // garde-fou
        }
    }

    /** Durée de vie d'une notification, en ticks de simulation (V6). */
    private static final int NOTIF_TTL = 120;

    public int getHeight() {
        return HEIGHT;
    }
}
