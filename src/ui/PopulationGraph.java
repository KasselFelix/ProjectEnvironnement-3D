package ui;

import javax.media.opengl.GL2;

import worlds.WorldOfCells;

/**
 * Graphe d'évolution des populations dans le temps (Phase 9).
 *
 * Buffer circulaire de {@code CAPACITY} échantillons, alimenté tous les
 * {@code SAMPLE_INTERVAL} ticks. 3 courbes : loups (rouge), moutons (blanc),
 * humains (bleu). L'échelle Y est auto-ajustée sur le max courant.
 *
 * Dessiné en mode 2D ortho, après le HUD. Position : haut-droite, sous le HUD,
 * pour ne pas conflicter avec la fiche agent (bas-gauche). Caché quand le menu
 * in-game est ouvert (le menu prend toute la colonne droite).
 */
public class PopulationGraph {

    private static final int CAPACITY = 600;
    private static final int SAMPLE_INTERVAL = 10;

    private static final int GRAPH_W = 300;
    private static final int GRAPH_H = 110;
    private static final int MARGIN = 20;
    private static final int HUD_HEIGHT = 24;  // doit correspondre à Hud.HEIGHT

    private final int[] loups   = new int[CAPACITY];
    private final int[] moutons = new int[CAPACITY];
    private final int[] humains = new int[CAPACITY];
    private final int[] ours    = new int[CAPACITY];   // L4 super-prédateur
    private int head = 0;
    private int size = 0;
    private int lastSampleIter = -1;

    /** À appeler chaque frame ; n'enregistre que tous les SAMPLE_INTERVAL ticks. */
    public void sample(WorldOfCells world) {
        int iter = world.getIteration();
        if (iter == lastSampleIter) return;
        if (iter % SAMPLE_INTERVAL != 0) return;
        lastSampleIter = iter;

        loups[head]   = world.loups.size();
        moutons[head] = world.moutons.size();
        humains[head] = world.humains.size();
        ours[head]    = world.ours.size();
        head = (head + 1) % CAPACITY;
        if (size < CAPACITY) size++;
    }

    /** Nombre d'échantillons actuellement mémorisés (V7 — export). */
    public int sampleCount() { return size; }

    /**
     * Exporte l'historique des populations en CSV (V7), du plus ancien au plus
     * récent. En-tête : {@code sample,ours,loups,moutons,humains}. L'index
     * {@code sample} est l'ordre chronologique (0 = plus ancien échantillon
     * encore en mémoire). Pur (aucune dépendance OpenGL).
     */
    public String exportCsv() {
        StringBuilder sb = new StringBuilder("sample,ours,loups,moutons,humains\n");
        // Le plus ancien échantillon est à (head - size) modulo CAPACITY.
        int start = ((head - size) % CAPACITY + CAPACITY) % CAPACITY;
        for (int k = 0; k < size; k++) {
            int idx = (start + k) % CAPACITY;
            sb.append(k).append(',')
              .append(ours[idx]).append(',')
              .append(loups[idx]).append(',')
              .append(moutons[idx]).append(',')
              .append(humains[idx]).append('\n');
        }
        return sb.toString();
    }

    /**
     * Exporte le graphe en PNG (V7) via {@code BufferedImage}/{@code ImageIO}
     * (JDK pur, aucune dépendance OpenGL → testable headless). Trace les 4 séries
     * (ours/loups/moutons/humains) sur fond sombre, échelle Y auto sur le max.
     */
    public void exportPng(java.nio.file.Path file) throws java.io.IOException {
        final int W = 640, H = 240, PAD = 30;
        java.awt.image.BufferedImage img =
                new java.awt.image.BufferedImage(W, H, java.awt.image.BufferedImage.TYPE_INT_RGB);
        java.awt.Graphics2D g = img.createGraphics();
        g.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING,
                java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(new java.awt.Color(20, 25, 32));
        g.fillRect(0, 0, W, H);
        g.setColor(new java.awt.Color(70, 80, 100));
        g.drawRect(PAD, PAD, W - 2 * PAD, H - 2 * PAD);

        int maxVal = 1;
        for (int i = 0; i < size; i++)
            maxVal = Math.max(maxVal, Math.max(Math.max(loups[i], moutons[i]),
                    Math.max(humains[i], ours[i])));

        int[][] series = { ours, loups, moutons, humains };
        java.awt.Color[] cols = {
                new java.awt.Color(178, 115, 51), new java.awt.Color(255, 77, 77),
                new java.awt.Color(242, 242, 242), new java.awt.Color(102, 178, 255) };
        int plotW = W - 2 * PAD, plotH = H - 2 * PAD;
        int start = ((head - size) % CAPACITY + CAPACITY) % CAPACITY;
        for (int s = 0; s < series.length; s++) {
            g.setColor(cols[s]);
            int prevX = -1, prevY = -1;
            for (int k = 0; k < size; k++) {
                int idx = (start + k) % CAPACITY;
                int x = PAD + (size <= 1 ? 0 : plotW * k / (size - 1));
                int y = PAD + plotH - (plotH * series[s][idx] / maxVal);
                if (prevX >= 0) g.drawLine(prevX, prevY, x, y);
                prevX = x; prevY = y;
            }
        }
        g.setColor(new java.awt.Color(200, 200, 200));
        g.drawString("max=" + maxVal + "  echantillons=" + size, PAD + 4, PAD + 14);
        g.dispose();
        java.nio.file.Files.createDirectories(file.getParent() == null
                ? java.nio.file.Paths.get(".") : file.getParent());
        javax.imageio.ImageIO.write(img, "png", file.toFile());
    }

    public void draw(GL2 gl, UiRenderer ui, int viewportWidth, int viewportHeight) {
        int gx = MARGIN;                       // coin haut-GAUCHE (sous le HUD)
        int gy = HUD_HEIGHT + MARGIN;
        int gw = GRAPH_W;
        int gh = GRAPH_H;

        // Fond + bordure.
        ui.drawQuad(gl, gx, gy, gw, gh, 0.08f, 0.10f, 0.14f, 0.85f);
        ui.drawBorder(gl, gx, gy, gw, gh, 0.5f, 0.6f, 0.75f, 1f);

        // Titre avec légende colorée.
        ui.drawText(gl, gx + 8, gy + 14, viewportHeight, "Populations", 1f, 1f, 0.7f);
        int legX = gx + 95;
        ui.drawText(gl, legX,        gy + 14, viewportHeight, "Ours",    0.7f, 0.45f, 0.2f);
        ui.drawText(gl, legX + 42,   gy + 14, viewportHeight, "Loups",   1f,   0.3f, 0.3f);
        ui.drawText(gl, legX + 92,   gy + 14, viewportHeight, "Moutons", 0.95f,0.95f,0.95f);
        ui.drawText(gl, legX + 157,  gy + 14, viewportHeight, "Humains", 0.4f, 0.7f, 1f);

        if (size < 2) {
            ui.drawText(gl, gx + 8, gy + gh / 2, viewportHeight,
                    "(echantillons en cours...)", 0.6f, 0.6f, 0.6f);
            return;
        }

        // Max courant pour l'échelle Y (au moins 1 pour éviter /0).
        int maxVal = 1;
        for (int i = 0; i < size; i++) {
            if (loups[i]   > maxVal) maxVal = loups[i];
            if (moutons[i] > maxVal) maxVal = moutons[i];
            if (humains[i] > maxVal) maxVal = humains[i];
            if (ours[i]    > maxVal) maxVal = ours[i];
        }

        // Axe Y à gauche : gouttière pour labels "max" en haut et "0" en bas, puis ligne verticale.
        String maxLabel = String.valueOf(maxVal);
        int yAxisW = Math.max(maxLabel.length(), 1) * 6 + 6;

        int plotX = gx + 6 + yAxisW;
        int plotY = gy + 22;
        int plotW = gw - 12 - yAxisW;
        int plotH = gh - 30;

        // Cadre du plot (lignes de référence haute et basse).
        ui.drawLine(gl, plotX, plotY,         plotX + plotW, plotY,         0.3f, 0.3f, 0.4f, 1f);
        ui.drawLine(gl, plotX, plotY + plotH, plotX + plotW, plotY + plotH, 0.3f, 0.3f, 0.4f, 1f);
        // Axe Y vertical.
        ui.drawLine(gl, plotX, plotY,         plotX,         plotY + plotH, 0.3f, 0.3f, 0.4f, 1f);

        // Labels d'échelle alignés à droite contre l'axe Y.
        int maxLabelW = maxLabel.length() * 6;
        ui.drawText(gl, plotX - maxLabelW - 3, plotY + 4, viewportHeight,
                maxLabel, 0.7f, 0.7f, 0.7f);
        ui.drawText(gl, plotX - 6 - 3, plotY + plotH + 4, viewportHeight,
                "0", 0.7f, 0.7f, 0.7f);

        drawSeries(gl, ours,    plotX, plotY, plotW, plotH, maxVal, 0.7f,  0.45f, 0.2f);
        drawSeries(gl, loups,   plotX, plotY, plotW, plotH, maxVal, 1f,    0.3f, 0.3f);
        drawSeries(gl, moutons, plotX, plotY, plotW, plotH, maxVal, 0.95f, 0.95f, 0.95f);
        drawSeries(gl, humains, plotX, plotY, plotW, plotH, maxVal, 0.4f,  0.7f,  1f);
    }

    private void drawSeries(GL2 gl, int[] data, int x, int y, int w, int h,
                            int maxVal, float r, float g, float b) {
        gl.glColor4f(r, g, b, 1f);
        gl.glLineWidth(1.5f);
        gl.glBegin(GL2.GL_LINE_STRIP);
        for (int i = 0; i < size; i++) {
            int idx = (head - size + i + CAPACITY) % CAPACITY;
            float px = x + (i / (float)(size - 1)) * w;
            float py = y + h - (data[idx] / (float) maxVal) * h;
            gl.glVertex2f(px, py);
        }
        gl.glEnd();
        gl.glLineWidth(1f);
    }
}
