package cellularautomata.ecosystem;

import landscapegenerator.PerlinNoiseLandscapeGenerator;
import org.junit.jupiter.api.Test;
import worlds.WorldOfCells;
import static org.junit.jupiter.api.Assertions.*;

/** Modèle de brins d'herbe (Phase 2). */
class GrassBrinsTest {

    /** Monde plat à une fraction de maxEverHeight (robuste vs Perlin non-déterministe,
     *  cf. ForestCATest). fracOfMaxH=0.4 → mi-bande (herbe OK) ; 0.001 → hors bande. */
    private WorldOfCells flatWorld(double fracOfMaxH) {
        WorldOfCells w = new WorldOfCells();
        w.nbloups = 0; w.nbmoutons = 0; w.nbhumains = 0; w.nbours = 0;
        double[][] ls = PerlinNoiseLandscapeGenerator.generatePerlinNoiseLandscape(51, 51, 0.7, 0.4, 4);
        w.init(50, 50, ls);
        double h = w.getMaxEverHeight() * fracOfMaxH;
        for (int x = 0; x < w.getWidth(); x++)
            for (int y = 0; y < w.getHeight(); y++) w.setCellHeight(x, y, h);
        return w;
    }

    @Test
    void maxBrinsZeroHorsBandeAltitude() {
        // Sous maxH/12 (bande basse) → hors bande → 0 brin possible.
        WorldOfCells w = flatWorld(0.001);
        assertEquals(0, w.getGrassMaxBrins(10, 10), "hors bande d'altitude → max 0");
    }

    @Test
    void maxBrinsPositifDansLaBande() {
        WorldOfCells w = flatWorld(0.4);
        int max = w.getGrassMaxBrins(10, 10);
        assertTrue(max >= 1 && max <= 5, "dans la bande → max entre 1 et 5 (mesuré=" + max + ")");
    }
}
