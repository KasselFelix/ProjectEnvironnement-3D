package cellularautomata.ecosystem;

import landscapegenerator.PerlinNoiseLandscapeGenerator;
import org.junit.jupiter.api.Test;
import worlds.WorldOfCells;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests de l'invariant d'altitude de l'herbe (GrassCA).
 * Pattern : vrai WorldOfCells (nb agents = 0) + paysage Perlin, puis appels
 * directs sur world.grassCA.canGrowGrass. Aucune dépendance OpenGL.
 */
class GrassCATest {

    private static final int DX_VIEW = 51, DY_VIEW = 51, DX = 50, DY = 50;

    private WorldOfCells buildWorld() {
        WorldOfCells w = new WorldOfCells();
        w.nbloups = 0; w.nbmoutons = 0; w.nbhumains = 0;
        double[][] ls = PerlinNoiseLandscapeGenerator
                .generatePerlinNoiseLandscape(DX_VIEW, DY_VIEW, 0.7, 0.4, 4);
        w.init(DX, DY, ls);
        return w;
    }

    /**
     * L'herbe ne pousse que dans la bande d'altitude [maxH/12, maxH×0.7].
     * On force une cellule à trois altitudes témoins et on vérifie le prédicat
     * `canGrowGrass` aux bornes : trop bas (littoral), dans la bande, trop haut
     * (sommet).
     */
    @Test
    void herbeSeulementDansLaBandeDAltitude() {
        WorldOfCells w = buildWorld();
        double maxH = w.getMaxEverHeight();
        assertTrue(maxH > 0, "le paysage doit avoir une altitude max > 0");

        int x = 25, y = 25;

        // Trop bas (sous maxH/12) : pas d'herbe.
        w.setCellHeight(x, y, maxH / 12.0 - 0.001);
        assertFalse(w.grassCA.canGrowGrass(x, y),
                "pas d'herbe en dessous de maxH/12 (littoral / bas-fond)");

        // Dans la bande (mi-hauteur) : herbe autorisée.
        w.setCellHeight(x, y, maxH * 0.4);
        assertTrue(w.grassCA.canGrowGrass(x, y),
                "herbe autorisée à mi-altitude (dans la bande)");

        // Trop haut (au-dessus de maxH×0.7) : pas d'herbe.
        w.setCellHeight(x, y, maxH * 0.7 + 0.001);
        assertFalse(w.grassCA.canGrowGrass(x, y),
                "pas d'herbe au-dessus de maxH×0.7 (hauts sommets)");
    }

    /** La cendre d'un incendie enrichit le sol → l'herbe repousse plus vite. */
    @Test
    void cendreFertiliseLeSolPourLHerbe() {
        WorldOfCells w = buildWorld();
        int x = 20, y = 20;
        w.grassCA.pH = 0.01;
        w.setCellHeight(x, y, 1.0);

        double before = w.grassCA.grassGerminationProb(x, y);
        w.grassCA.markAsh(x, y);
        double after = w.grassCA.grassGerminationProb(x, y);

        assertTrue(after > before,
                "la cendre récente doit accélérer la repousse de l'herbe ("
                + after + " > " + before + ")");
    }

    /** L'eau (altitude négative) est toujours hors-bande → jamais d'herbe. */
    @Test
    void pasDHerbeDansLEau() {
        WorldOfCells w = buildWorld();
        int x = 30, y = 30;
        w.setCellHeight(x, y, -1.0);
        assertFalse(w.grassCA.canGrowGrass(x, y),
                "l'eau ne porte jamais d'herbe");
    }
}
