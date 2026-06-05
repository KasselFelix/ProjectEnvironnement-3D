import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Random;
import javax.imageio.ImageIO;

/**
 * Génère deux textures dans textures/ :
 *  - noise.png : bruit grayscale 256x256 tileable, fort contraste. Plaqué sur tout
 *               le terrain et modulé par la couleur de cellule (sable/montagne/eau).
 *               C'est la texture utilisée par Landscape.java.
 *  - atlas.png : ancien atlas 512x512 (sable/herbe/placeholders). Conservé pour
 *               référence/debug ; plus utilisé par le rendu actuel.
 *
 *   javac tools/GenAtlas.java -d tools/bin
 *   java -cp tools/bin GenAtlas
 *
 * À lancer depuis ProjectEnvironnement-3D/.
 */
public class GenAtlas {
    static final int SIZE = 512;
    static final int TILE = 256;

    public static void main(String[] args) throws Exception {
        // ----- noise.png : bruit grayscale tileable -----
        BufferedImage noise = new BufferedImage(TILE, TILE, BufferedImage.TYPE_INT_RGB);
        fillNoise(noise, 42L);
        File noiseOut = new File("textures/noise.png");
        noiseOut.getParentFile().mkdirs();
        ImageIO.write(noise, "png", noiseOut);
        System.out.println("wrote " + noiseOut.getAbsolutePath());

        // ----- atlas.png : référence historique -----
        BufferedImage img = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_RGB);
        fillTile(img, 0,    0,    new int[]{218, 195, 145}, 1L, 0.14f);
        fillTile(img, TILE, 0,    new int[]{ 92, 138,  58}, 2L, 0.22f);
        fillTile(img, 0,    TILE, new int[]{128, 128, 128}, 3L, 0.00f);
        fillTile(img, TILE, TILE, new int[]{128, 128, 128}, 4L, 0.00f);
        File out = new File("textures/atlas.png");
        ImageIO.write(img, "png", out);
        System.out.println("wrote " + out.getAbsolutePath());
    }

    /**
     * Bruit grayscale "highlights" : la plupart des pixels sont noirs (donc
     * additionnent zéro à la couleur de cellule), et des taches éparses ajoutent
     * un léger éclat (+0.08 max) — type "grains de sable qui captent la lumière".
     * Utilisé en mode GL_ADD côté rendu (texture + cellule), pas en MODULATE.
     *
     * Pour assombrir comme avant, repasser à fillNoiseModulate.
     */
    static void fillNoise(BufferedImage img, long seed) {
        Random rng = new Random(seed);
        float[][] o1 = randGrid(rng, 16); // moyennes fréquences : taches
        float[][] o2 = randGrid(rng, 64); // hautes : grain fin

        for (int y = 0; y < TILE; y++) {
            for (int x = 0; x < TILE; x++) {
                float u = (float) x / TILE;
                float v = (float) y / TILE;
                float n = sampleWrap(o1, u, v) * 0.65f
                        + sampleWrap(o2, u, v) * 0.35f;
                // Seuil ~0.55 : ~40% des pixels sont noirs, le reste est positif.
                // Multiplié par 0.09 : peaks ~+0.04 max après clamp — visible aux
                // angles rasants (matin/soir) mais ne sature pas à midi.
                n = Math.max(0f, n - 0.55f) * 0.04f;
                int g = clamp((int) (n * 255f));
                img.setRGB(x, y, (g << 16) | (g << 8) | g);
            }
        }
    }

    /** Remplit une tuile TILE*TILE avec une couleur de base modulée par du bruit seamless 2 octaves. */
    static void fillTile(BufferedImage img, int x0, int y0, int[] base, long seed, float strength) {
        Random rng = new Random(seed);
        float[][] noiseLow  = randGrid(rng, 16);
        float[][] noiseHigh = randGrid(rng, 64);

        for (int yy = 0; yy < TILE; yy++) {
            for (int xx = 0; xx < TILE; xx++) {
                float u = (float) xx / TILE;
                float v = (float) yy / TILE;
                float n1 = sampleWrap(noiseLow,  u, v);
                float n2 = sampleWrap(noiseHigh, u, v);
                float n  = (n1 * 0.7f + n2 * 0.3f) * 2f - 1f; // [-1, +1]
                int r = clamp((int) (base[0] * (1f + n * strength)));
                int g = clamp((int) (base[1] * (1f + n * strength)));
                int b = clamp((int) (base[2] * (1f + n * strength)));
                img.setRGB(x0 + xx, y0 + yy, (r << 16) | (g << 8) | b);
            }
        }
    }

    static float[][] randGrid(Random rng, int n) {
        float[][] g = new float[n][n];
        for (int j = 0; j < n; j++)
            for (int i = 0; i < n; i++)
                g[j][i] = rng.nextFloat();
        return g;
    }

    /** Échantillonnage bilinéaire avec wrap-around : garantit la tuilabilité. */
    static float sampleWrap(float[][] grid, float u, float v) {
        int n = grid.length;
        float fx = u * n, fy = v * n;
        int x0 = ((int) Math.floor(fx) % n + n) % n;
        int y0 = ((int) Math.floor(fy) % n + n) % n;
        int x1 = (x0 + 1) % n;
        int y1 = (y0 + 1) % n;
        float dx = fx - (float) Math.floor(fx);
        float dy = fy - (float) Math.floor(fy);
        float a = grid[y0][x0] * (1 - dx) + grid[y0][x1] * dx;
        float b = grid[y1][x0] * (1 - dx) + grid[y1][x1] * dx;
        return a * (1 - dy) + b * dy;
    }

    static int clamp(int v) { return Math.max(0, Math.min(255, v)); }
}
