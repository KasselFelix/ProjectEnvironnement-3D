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
                     int fps) {

        // Fond noir semi-transparent.
        ui.drawQuad(gl, 0, 0, viewportWidth, HEIGHT, 0f, 0f, 0f, 0.55f);

        int iter    = world.getIteration();
        int loups   = world.loups.size();
        int moutons = world.moutons.size();
        int humains = world.humains.size();

        String text = String.format(
                "Iter:%d  |  %s %s  |  Loups:%d  Moutons:%d  Humains:%d  |  FPS:%d",
                iter, dayLabel, gameTime, loups, moutons, humains, fps);

        ui.drawText(gl, PADDING_X, TEXT_Y_OFFSET, viewportHeight, text, 1f, 1f, 1f);
    }

    public int getHeight() {
        return HEIGHT;
    }
}
