package cellularautomata.ecosystem;

import landscapegenerator.PerlinNoiseLandscapeGenerator;
import objects.Material;
import org.junit.jupiter.api.Test;
import worlds.WorldOfCells;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests de la fertilité et de la croissance des arbres (ForestCA).
 * Pattern : vrai WorldOfCells (nb agents = 0) + paysage Perlin, puis appels
 * directs sur world.forestCA. Aucune dépendance OpenGL.
 */
class ForestCATest {

    private static final int DX_VIEW = 51, DY_VIEW = 51, DX = 50, DY = 50;

    private WorldOfCells buildWorld() {
        WorldOfCells w = new WorldOfCells();
        w.nbloups = 0; w.nbmoutons = 0; w.nbhumains = 0;
        double[][] ls = PerlinNoiseLandscapeGenerator
                .generatePerlinNoiseLandscape(DX_VIEW, DY_VIEW, 0.7, 0.4, 4);
        w.init(DX, DY, ls);
        return w;
    }

    /** Renvoie {x,y} de la cellule terrestre de fertilité maximale (>0 garanti sur un Perlin 50²). */
    private int[] bestFertileCell(WorldOfCells w) {
        double best = -1; int bx = 0, by = 0;
        for (int x = 0; x < DX; x++)
            for (int y = 0; y < DY; y++)
                if (w.getCellHeight(x, y) >= 0) {
                    double f = w.forestCA.fertility(x, y);
                    if (f > best) { best = f; bx = x; by = y; }
                }
        assertTrue(best > 0, "il doit exister une cellule de fertilité > 0");
        return new int[] { bx, by };
    }

    @Test
    void fertilitePlusBasseSurPierre() {
        WorldOfCells w = buildWorld();
        int[] c = bestFertileCell(w);
        double f0 = w.forestCA.fertility(c[0], c[1]);
        w.pushLayer(c[0], c[1], Material.STONE, 5f, 0); // sol minéral
        double f1 = w.forestCA.fertility(c[0], c[1]);
        assertTrue(f1 < f0, "la pierre doit réduire la fertilité (" + f1 + " < " + f0 + ")");
    }

    @Test
    void fertilitePlusBasseQuandDense() {
        WorldOfCells w = buildWorld();
        int[] c = bestFertileCell(w);
        int x = c[0], y = c[1];
        // voisins vides
        for (int dx = -1; dx <= 1; dx++)
            for (int dy = -1; dy <= 1; dy++)
                if (!(dx == 0 && dy == 0))
                    w.forestCA.setCellState((x + dx + DX) % DX, (y + dy + DY) % DY, 0);
        double fSparse = w.forestCA.fertility(x, y);
        // voisins pleins d'arbres
        for (int dx = -1; dx <= 1; dx++)
            for (int dy = -1; dy <= 1; dy++)
                if (!(dx == 0 && dy == 0))
                    w.forestCA.setCellState((x + dx + DX) % DX, (y + dy + DY) % DY, 1);
        double fDense = w.forestCA.fertility(x, y);
        assertTrue(fDense < fSparse, "la compétition doit réduire la fertilité (" + fDense + " < " + fSparse + ")");
    }

    @Test
    void croissanceAugmenteEtPlafonne() {
        WorldOfCells w = buildWorld();
        int[] c = bestFertileCell(w);
        int x = c[0], y = c[1];
        w.forestCA.treeGrowthDays = 1.0; // accélère pour le test

        // depuis 0 → strictement positif (fertilité > 0 garantie par bestFertileCell)
        w.forestCA.setGrowth(x, y, 0.0);
        assertTrue(w.forestCA.grownValue(x, y) > 0.0, "la croissance doit progresser depuis 0");

        // depuis 1 → plafonné à 1 (taux >= 0)
        w.forestCA.setGrowth(x, y, 1.0);
        assertEquals(1.0, w.forestCA.grownValue(x, y), 1e-9, "la croissance est plafonnée à 1");
    }
}
