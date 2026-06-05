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
    void fertilitePlusHauteProcheDeLEau() {
        WorldOfCells w = buildWorld();
        double h0 = w.getMaxEverHeight() * 0.5;
        if (h0 <= 0) h0 = 1.0;

        int ax = 10, ay = 10;   // au bord de l'eau
        int bx = 40, by = 40;   // loin de toute eau

        // Deux régions distinctes aplaties en terre à la MÊME altitude h0, sans
        // arbres voisins ni pierre → seul le facteur « proximité de l'eau » varie.
        for (int[] c : new int[][] { {ax, ay}, {bx, by} }) {
            for (int dx = -6; dx <= 6; dx++)
                for (int dy = -6; dy <= 6; dy++) {
                    int x = (c[0] + dx + DX) % DX, y = (c[1] + dy + DY) % DY;
                    w.setCellHeight(x, y, h0);
                    w.forestCA.setCellState(x, y, 0);
                }
        }
        // Une cellule d'eau juste au Sud de A (B reste au sec dans son disque).
        w.setCellHeight(ax % DX, (ay + 1) % DY, -1.0);

        double fA = w.forestCA.fertility(ax, ay);
        double fB = w.forestCA.fertility(bx, by);

        assertTrue(fB > 0, "la cellule sèche garde une fertilité > 0 (" + fB + ")");
        assertTrue(fA > fB,
                "une cellule au bord de l'eau doit être plus fertile qu'une cellule "
                + "sèche de même altitude (" + fA + " > " + fB + ")");
    }

    @Test
    void dispersionDeGrainesAugmenteGermination() {
        WorldOfCells w = buildWorld();
        int x = 25, y = 25;
        w.forestCA.pA = 0.01;   // proba de base non nulle pour comparer

        // Case vide sur terre, sans pierre ; voisinage vide au départ.
        for (int dx = -1; dx <= 1; dx++)
            for (int dy = -1; dy <= 1; dy++) {
                int cx = (x + dx + DX) % DX, cy = (y + dy + DY) % DY;
                w.setCellHeight(cx, cy, 1.0);
                w.forestCA.setCellState(cx, cy, 0);
                w.forestCA.setGrowth(cx, cy, 0.0);
            }
        double pBare = w.forestCA.germinationProb(x, y);

        // Entoure la case de 8 arbres ADULTES (état 1, maturité 1).
        for (int dx = -1; dx <= 1; dx++)
            for (int dy = -1; dy <= 1; dy++) {
                if (dx == 0 && dy == 0) continue;
                int cx = (x + dx + DX) % DX, cy = (y + dy + DY) % DY;
                w.forestCA.setCellState(cx, cy, 1);
                w.forestCA.setGrowth(cx, cy, 1.0);
            }
        double pSeeded = w.forestCA.germinationProb(x, y);

        assertTrue(pSeeded > pBare,
                "des arbres adultes voisins doivent augmenter la proba de germination ("
                + pSeeded + " > " + pBare + ")");
    }

    @Test
    void cendreFertiliseLeSol() {
        WorldOfCells w = buildWorld();
        int x = 25, y = 25;
        w.forestCA.pA = 0.01;

        // Case vide, sans arbres voisins ni pierre → seule la cendre varie.
        for (int dx = -1; dx <= 1; dx++)
            for (int dy = -1; dy <= 1; dy++) {
                int cx = (x + dx + DX) % DX, cy = (y + dy + DY) % DY;
                w.setCellHeight(cx, cy, 1.0);
                w.forestCA.setCellState(cx, cy, 0);
                w.forestCA.setGrowth(cx, cy, 0.0);
            }
        double pBefore = w.forestCA.germinationProb(x, y);

        w.forestCA.markAsh(x, y);   // sol enrichi par la cendre d'un incendie
        double pAfter = w.forestCA.germinationProb(x, y);

        assertTrue(pAfter > pBefore,
                "la cendre récente doit accélérer la reforestation (" + pAfter
                + " > " + pBefore + ")");
    }

    @Test
    void compteurDeDispersionDesCendres() {
        WorldOfCells w = buildWorld();
        int x = 25, y = 25;
        w.forestCA.pA = 0.01;
        int t = w.forestCA.getTDispertion();
        assertTrue(t > 1, "tDispertion doit être > 1 pour que le cycle cendre soit observable");

        // Cellule cible sur terre ; voisinage IMMERGÉ (height < 0) → aucune
        // germination autour ne peut perturber le compteur ni la pression de
        // graines pendant les t ticks d'observation.
        w.setCellHeight(x, y, 1.0);
        for (int dx = -1; dx <= 1; dx++)
            for (int dy = -1; dy <= 1; dy++) {
                if (dx == 0 && dy == 0) continue;
                int cx = (x + dx + DX) % DX, cy = (y + dy + DY) % DY;
                w.setCellHeight(cx, cy, -1.0);
                w.forestCA.setCellState(cx, cy, 0);
                w.forestCA.setGrowth(cx, cy, 0.0);
            }

        double pAvantCendre = w.forestCA.germinationProb(x, y);

        // Démarre le cycle cendre juste après l'état BURNT (premier sub-state invisible).
        w.forestCA.setCellState(x, y, ForestCA.BURNT + 1);

        // Le cycle cendre avance de +1 par tick (déterministe, sans random) tant
        // que BURNT < state < BURNT+tDispertion. Après (t-1) ticks il atteint
        // exactement BURNT+tDispertion : pas encore dispersé.
        for (int k = 0; k < t - 1; k++) w.forestCA.step();
        assertEquals(ForestCA.BURNT + t, w.forestCA.getCellState(x, y),
                "après " + (t - 1) + " ticks la cellule doit être au seuil de dispersion, pas encore vide");

        // Le tick suivant déclenche la dispersion : la cellule redevient vide (0)
        // ET le sol est enrichi par la cendre.
        w.forestCA.step();
        assertEquals(0, w.forestCA.getCellState(x, y),
                "au tick tDispertion la cendre se disperse → cellule vide");
        double pApresCendre = w.forestCA.germinationProb(x, y);
        assertTrue(pApresCendre > pAvantCendre,
                "la dispersion doit marquer le sol comme cendré (germination boostée : "
                + pApresCendre + " > " + pAvantCendre + ")");
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
