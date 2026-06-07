package cellularautomata.ecosystem;

import landscapegenerator.PerlinNoiseLandscapeGenerator;
import org.junit.jupiter.api.Test;
import ui.SimulationConfig;
import worlds.WorldOfCells;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Audit des automates cellulaires (2026-06-07) :
 *  - le décodage de cellule (c → x,y) doit couvrir un monde NON carré sans planter
 *    (avant : i=c%_dx ; j=c/_dy → ArrayIndexOutOfBounds si _dx>_dy) ;
 *  - les durées (cendre/rase) suivent simulationHz (hz-invariant) ;
 *  - EXPÉRIENCE feu de forêt : synchrone (double-buffer = commentaire) vs
 *    asynchrone in-place (mono-buffer = code actuel), pour juger lequel est réaliste.
 */
class CACadenceTest {

    /** RÉGRESSION #2 : un monde non carré (_dx > _dy) ne fait plus planter les CAs. */
    @Test
    void decodageCelluleCouvreUnMondeNonCarre() {
        WorldOfCells w = new WorldOfCells();
        w.nbloups = 0; w.nbmoutons = 0; w.nbhumains = 0; w.nbours = 0;
        // 61×41 vues → 60×40 cellules CA : _dx(60) > _dy(40) → l'ancien décodage
        // (j = c/_dy) débordait la grille [60][40].
        double[][] ls = PerlinNoiseLandscapeGenerator
                .generatePerlinNoiseLandscape(61, 41, 0.7, 0.4, 4);
        w.init(60, 40, ls);

        assertDoesNotThrow(() -> {
            for (int i = 0; i < 5; i++) {
                w.forestCA.step();
                w.grassCA.step();
                w.lavaCA.step();
            }
        }, "les CAs doivent tourner sur un monde non carré sans déborder");
    }

    /** #3 : les durées d'état (ex. herbe rase) se recalculent depuis simulationHz. */
    @Test
    void dureesSuiventSimulationHz() {
        SimulationConfig saved = SimulationConfig.getInstance();
        try {
            WorldOfCells w = new WorldOfCells();
            w.nbloups = 0; w.nbmoutons = 0; w.nbhumains = 0; w.nbours = 0;
            double[][] ls = PerlinNoiseLandscapeGenerator
                    .generatePerlinNoiseLandscape(31, 31, 0.7, 0.4, 4);
            w.init(30, 30, ls);

            SimulationConfig c = new SimulationConfig();
            SimulationConfig.setInstance(c);
            c.simulationHz = 20; assertEquals(60,  w.grassCA.getGrazedDuration(), "3 s × 20 Hz");
            c.simulationHz = 60; assertEquals(180, w.grassCA.getGrazedDuration(), "3 s × 60 Hz");
            c.simulationHz = 10; assertEquals(30,  w.grassCA.getGrazedDuration(), "3 s × 10 Hz");
        } finally {
            SimulationConfig.setInstance(saved);   // ne pas polluer les autres tests
        }
    }

    // ===== EXPÉRIENCE : feu de forêt synchrone vs asynchrone =====
    // Forêt 100 % boisée NxN, foyer au centre. Règle commune : un arbre (1) prend
    // feu (2) si AU MOINS un voisin cardinal brûle. Le feu (2) reste allumé.
    //  - SYNCHRONE  (commentaire « buffering must be true ») : décision sur l'état
    //    du tick précédent → front qui avance d'1 case/tick (anneau régulier).
    //  - ASYNCHRONE (code actuel, mono-buffer + shuffle) : décision sur l'état
    //    courant partiellement mis à jour → une case allumée tôt enflamme ses
    //    voisines DANS le même tick → embrasement en chaîne.

    @Test
    void feuForetSyncVsAsync() {
        int n = 21, cx = n / 2, cy = n / 2, maxTicks = n * 2;

        int[][] sync = denseForest(n); sync[cx][cy] = 2;
        int[][] asyn = denseForest(n); asyn[cx][cy] = 2;
        Random rng = new Random(42);   // déterministe

        StringBuilder sb = new StringBuilder("\n[CAFire] feu de foret " + n + "x" + n
                + " (1 foyer central) — cellules en feu par tick :\n  tick :  SYNC  ASYNC\n");
        int syncFull = -1, asynFull = -1;
        for (int t = 1; t <= maxTicks; t++) {
            sync = stepSync(sync);
            stepAsyncInPlace(asyn, rng);
            int sBurn = countBurning(sync), aBurn = countBurning(asyn);
            if (syncFull < 0 && sBurn == n * n) syncFull = t;
            if (asynFull < 0 && aBurn == n * n) asynFull = t;
            if (t <= 6 || sBurn == n * n || aBurn == n * n)
                sb.append(String.format("  %4d :  %4d  %5d%n", t, sBurn, aBurn));
            if (syncFull > 0 && asynFull > 0) break;
        }
        sb.append("  -> foret entierement en feu : SYNC a t=").append(syncFull)
          .append(", ASYNC a t=").append(asynFull).append('\n');
        System.out.print(sb);

        // Démonstration : la forêt part en flammes BIEN plus vite en asynchrone
        // (chaîne intra-tick) qu'en synchrone (front borné ~1 case/tick).
        assertTrue(asynFull > 0 && syncFull > 0, "les deux finissent par tout brûler");
        assertTrue(asynFull < syncFull,
                "l'asynchrone embrase la foret BEAUCOUP plus vite (async t=" + asynFull
                        + " < sync t=" + syncFull + ") — propagation non bornee par tick");
        assertTrue(syncFull >= n - 1,
                "le synchrone progresse en front regulier ~1 case/tick (t=" + syncFull
                        + " >= " + (n - 1) + ")");
    }

    private static int[][] denseForest(int n) {
        int[][] g = new int[n][n];
        for (int x = 0; x < n; x++)
            for (int y = 0; y < n; y++) g[x][y] = 1;   // 1 = arbre
        return g;
    }

    private static int countBurning(int[][] g) {
        int c = 0;
        for (int[] row : g) for (int v : row) if (v == 2) c++;
        return c;
    }

    private static boolean neighborBurning(int[][] g, int x, int y) {
        int n = g.length;
        if (x > 0     && g[x - 1][y] == 2) return true;
        if (x < n - 1 && g[x + 1][y] == 2) return true;
        if (y > 0     && g[x][y - 1] == 2) return true;
        if (y < n - 1 && g[x][y + 1] == 2) return true;
        return false;
    }

    /** Synchrone : nouvelle grille calculée depuis l'ancienne (double-buffer). */
    private static int[][] stepSync(int[][] g) {
        int n = g.length;
        int[][] nx = new int[n][n];
        for (int x = 0; x < n; x++)
            for (int y = 0; y < n; y++) {
                if (g[x][y] == 2) nx[x][y] = 2;
                else if (g[x][y] == 1 && neighborBurning(g, x, y)) nx[x][y] = 2;
                else nx[x][y] = g[x][y];
            }
        return nx;
    }

    /** Asynchrone : mise à jour EN PLACE dans un ordre aléatoire (mono-buffer +
     *  shuffle, comme le code actuel) → propagation en chaîne dans le même tick. */
    private static void stepAsyncInPlace(int[][] g, Random rng) {
        int n = g.length;
        List<int[]> cells = new ArrayList<>(n * n);
        for (int x = 0; x < n; x++) for (int y = 0; y < n; y++) cells.add(new int[]{x, y});
        Collections.shuffle(cells, rng);
        for (int[] cxy : cells) {
            int x = cxy[0], y = cxy[1];
            if (g[x][y] == 1 && neighborBurning(g, x, y)) g[x][y] = 2;
        }
    }
}
