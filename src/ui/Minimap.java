package ui;

import javax.media.opengl.GL2;

import worlds.WorldOfCells;

/**
 * Mini-carte d'ensemble (V7) : vue top-down compacte du monde dans un coin, avec
 * la terre/l'eau et la position des agents (points colorés par espèce). Dessinée
 * en 2D ortho dans la passe overlay. Pour limiter le coût (rendu logiciel), la
 * terre est un fond uni et seules les cellules d'EAU (échantillonnées) + les
 * agents sont tracés par-dessus.
 */
public class Minimap {

    private static final int SIZE = 150;     // côté en pixels
    private static final int MARGIN = 12;
    private static final int WATER_STRIDE = 3; // sous-échantillonnage de l'eau

    /** Dessine la minimap en haut-droite, sous le HUD. */
    public void draw(GL2 gl, UiRenderer ui, WorldOfCells world,
                     int viewportWidth, int viewportHeight, int hudHeight) {
        int w = world.getWidth(), h = world.getHeight();
        if (w <= 0 || h <= 0) return;
        int ox = viewportWidth - SIZE - MARGIN;
        int oy = hudHeight + MARGIN;

        // Cadre + fond (terre).
        ui.drawQuad(gl, ox - 2, oy - 2, SIZE + 4, SIZE + 4, 0.5f, 0.6f, 0.75f, 0.9f);
        ui.drawQuad(gl, ox, oy, SIZE, SIZE, 0.18f, 0.32f, 0.16f, 0.95f);

        float sx = SIZE / (float) w, sy = SIZE / (float) h;
        float cellW = Math.max(1f, sx * WATER_STRIDE);
        float cellH = Math.max(1f, sy * WATER_STRIDE);

        // Eau (bleu) — échantillonnée.
        for (int x = 0; x < w; x += WATER_STRIDE)
            for (int y = 0; y < h; y += WATER_STRIDE)
                if (world.getCellHeight(x, y) < 0) {
                    int mx = ox + (int) (x * sx);
                    int my = oy + (int) (y * sy);
                    ui.drawQuad(gl, mx, my, (int) cellW, (int) cellH, 0.13f, 0.30f, 0.6f, 1f);
                }

        // Agents : points colorés par espèce.
        for (agents.Ours o : world.ours)   plot(gl, ui, o.x, o.y, ox, oy, sx, sy, 0.7f, 0.45f, 0.2f);
        for (agents.Loup l : world.loups)  plot(gl, ui, l.x, l.y, ox, oy, sx, sy, 1f, 0.25f, 0.25f);
        for (agents.Mouton m : world.moutons) plot(gl, ui, m.x, m.y, ox, oy, sx, sy, 0.95f, 0.95f, 0.95f);
        for (agents.Humain hu : world.humains) plot(gl, ui, hu.x, hu.y, ox, oy, sx, sy, 0.4f, 0.7f, 1f);
    }

    private void plot(GL2 gl, UiRenderer ui, int ax, int ay, int ox, int oy,
                      float sx, float sy, float r, float g, float b) {
        int mx = ox + (int) (ax * sx);
        int my = oy + (int) (ay * sy);
        ui.drawQuad(gl, mx - 1, my - 1, 3, 3, r, g, b, 1f);
    }
}
