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

        // ── FORME du front, à couverture comparable (~110 cellules sur 441) ──
        int target = 105;
        int[][] gs = denseForest(n); gs[cx][cy] = 2;
        while (countBurning(gs) < target) gs = stepSync(gs);
        int[][] ga = denseForest(n); ga[cx][cy] = 2;
        Random r2 = new Random(7);
        while (countBurning(ga) < target) stepAsyncInPlace(ga, r2);
        System.out.println("\n[CAFire] FORME du front a couverture comparable "
                + "(# = en feu) :\n  SYNC (" + countBurning(gs) + " cellules) : front en LOSANGE regulier"
                + "        ASYNC (" + countBurning(ga) + " cellules) : tache irreguliere/ordre-dependante");
        System.out.println(renderSideBySide(gs, ga));

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

    /** Rend les deux grilles côte à côte en ASCII ('#' = en feu, '.' = arbre). */
    private static String renderSideBySide(int[][] a, int[][] b) {
        int n = a.length;
        StringBuilder sb = new StringBuilder();
        for (int y = 0; y < n; y++) {
            sb.append("  ");
            for (int x = 0; x < n; x++) sb.append(a[x][y] == 2 ? '#' : '.');
            sb.append("    ");
            for (int x = 0; x < n; x++) sb.append(b[x][y] == 2 ? '#' : '.');
            sb.append('\n');
        }
        return sb.toString();
    }

    /**
     * EXPÉRIENCE forme : compare les TROIS règles de propagation sur une forêt
     * dense, à couverture comparable, en tables ASCII.
     *  - ACTUEL  : asynchrone in-place 4-connexe (le code de prod) ;
     *  - OPTION 1: synchrone 4-connexe déterministe (front borné = losange) ;
     *  - OPTION 2: synchrone 8-connexe probabiliste (front rond, bords digités).
     * Tout ici est en test : aucune modification du ForestCA de production.
     */
    @Test
    void troisFormesDePropagation() {
        int n = 31, cx = n / 2, cy = n / 2, target = 200;

        int[][] async = denseForest(n); async[cx][cy] = 2;
        Random rA = new Random(7);
        while (countBurning(async) < target) stepAsyncInPlace(async, rA);

        int[][] sync4 = denseForest(n); sync4[cx][cy] = 2;
        while (countBurning(sync4) < target) sync4 = stepSync(sync4);

        int[][] sync8 = denseForest(n); sync8[cx][cy] = 2;
        Random r8 = new Random(7);
        while (countBurning(sync8) < target) sync8 = stepSync8Prob(sync8, r8);

        System.out.println("\n[CAFire] FORME du front (# = en feu, . = arbre), "
                + "couverture ~" + target + " cellules sur " + (n * n) + " :");
        System.out.println("  ACTUEL async 4-conn (" + countBurning(async) + ")"
                + "        OPTION 1 sync 4-conn (" + countBurning(sync4) + ")"
                + "        OPTION 2 sync 8-conn proba (" + countBurning(sync8) + ")");
        System.out.println(renderThree(async, sync4, sync8));

        assertTrue(countBurning(sync4) >= target && countBurning(sync8) >= target
                && countBurning(async) >= target, "les trois atteignent la couverture cible");
    }

    /** OPTION 2 : synchrone (double-buffer), 8-connexe, probabiliste. Les voisins
     *  DIAGONAUX (distance √2) ont une proba d'ignition plus faible que les
     *  CARDINAUX → la vitesse devient ~isotrope → front rond aux bords digités
     *  (vs le losange du 4-connexe ou le carré du 8-connexe déterministe). */
    private static final double P_CARD = 0.55, P_DIAG = 0.22;
    private static int[][] stepSync8Prob(int[][] g, Random rng) {
        int n = g.length;
        int[][] nx = new int[n][n];
        for (int x = 0; x < n; x++)
            for (int y = 0; y < n; y++) {
                if (g[x][y] == 2) { nx[x][y] = 2; continue; }
                if (g[x][y] != 1) { nx[x][y] = g[x][y]; continue; }
                double q = 1.0;   // proba de NE PAS s'enflammer
                for (int dx = -1; dx <= 1; dx++)
                    for (int dy = -1; dy <= 1; dy++) {
                        if (dx == 0 && dy == 0) continue;
                        int xn = x + dx, yn = y + dy;
                        if (xn < 0 || xn >= n || yn < 0 || yn >= n) continue;
                        if (g[xn][yn] != 2) continue;
                        q *= 1.0 - ((dx == 0 || dy == 0) ? P_CARD : P_DIAG);
                    }
                nx[x][y] = (rng.nextDouble() < 1.0 - q) ? 2 : 1;
            }
        return nx;
    }

    /** Rend trois grilles côte à côte en ASCII. */
    private static String renderThree(int[][] a, int[][] b, int[][] c) {
        int n = a.length;
        StringBuilder sb = new StringBuilder();
        for (int y = 0; y < n; y++) {
            sb.append("  ");
            for (int x = 0; x < n; x++) sb.append(a[x][y] == 2 ? '#' : '.');
            sb.append("    ");
            for (int x = 0; x < n; x++) sb.append(b[x][y] == 2 ? '#' : '.');
            sb.append("    ");
            for (int x = 0; x < n; x++) sb.append(c[x][y] == 2 ? '#' : '.');
            sb.append('\n');
        }
        return sb.toString();
    }

    /** PRODUCTION (Option 2 appliquée) : le vrai ForestCA borne le feu à ~1 case/
     *  tick (plus de flashover intra-tick) tout en se propageant sur la durée. */
    @Test
    void feuForestCaBorneParTickPuisSeRepand() {
        WorldOfCells w = new WorldOfCells();
        w.nbloups = 0; w.nbmoutons = 0; w.nbhumains = 0; w.nbours = 0;
        double[][] ls = PerlinNoiseLandscapeGenerator
                .generatePerlinNoiseLandscape(31, 31, 0.7, 0.4, 4);
        w.init(30, 30, ls);

        int W = w.getWidth(), H = w.getHeight(), cx = W / 2, cy = H / 2, R = 8;
        for (int x = 0; x < W; x++)
            for (int y = 0; y < H; y++)
                if (Math.max(Math.abs(x - cx), Math.abs(y - cy)) <= R) {
                    w.setCellHeight(x, y, 1.0);
                    w.setForestCAValue(x, y, 1);   // arbre
                }
        w.forestCA.pF = 0.0;                        // pas de feu spontané → périmètre déterministe
        w.setForestCAValue(cx, cy, ForestCA.FIRE_FIRST);   // foyer unique

        w.forestCA.step();                          // 1 tick
        int t1 = countForestFire(w);
        assertTrue(t1 >= 1 && t1 <= 9,
                "feu borne : 1 foyer -> au plus ses 8 voisins en 1 tick (pas de flashover), =" + t1);

        java.util.Set<Long> ever = new java.util.HashSet<>();
        for (int k = 0; k < 80; k++) { w.forestCA.step(); collectForestFire(w, ever); }
        assertTrue(ever.size() > 25,
                "le feu se propage sur la duree (cellules touchees=" + ever.size() + ")");
    }

    private static int countForestFire(WorldOfCells w) {
        int c = 0;
        for (int x = 0; x < w.getWidth(); x++)
            for (int y = 0; y < w.getHeight(); y++) {
                int s = w.getForestCAValue(x, y);
                if (s >= ForestCA.FIRE_FIRST && s <= ForestCA.BURNT) c++;
            }
        return c;
    }

    private static void collectForestFire(WorldOfCells w, java.util.Set<Long> ever) {
        for (int x = 0; x < w.getWidth(); x++)
            for (int y = 0; y < w.getHeight(); y++) {
                int s = w.getForestCAValue(x, y);
                if (s >= ForestCA.FIRE_FIRST && s <= ForestCA.BURNT) ever.add(((long) x << 20) | y);
            }
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

    @Test
    void feuSEtireSousLeVent() {
        WorldOfCells w = new WorldOfCells();
        w.nbloups = 0; w.nbmoutons = 0; w.nbhumains = 0; w.nbours = 0;
        double[][] ls = PerlinNoiseLandscapeGenerator.generatePerlinNoiseLandscape(31, 31, 0.7, 0.4, 4);
        w.init(30, 30, ls);
        // Patch boisé carré rayon R autour du centre. NB tore : cx+R=26 < W=30, donc à
        // la cible de 120 cellules le front n'a pas encore atteint le bord Est ni rebouclé
        // en x=0 (ce qui tirerait meanX vers le bas) — le biais Est reste mesurable.
        int W = w.getWidth(), H = w.getHeight(), cx = W / 2, cy = H / 2, R = 12;
        for (int x = 0; x < W; x++)
            for (int y = 0; y < H; y++)
                if (Math.max(Math.abs(x - cx), Math.abs(y - cy)) <= R) {
                    w.setCellHeight(x, y, 1.0); w.setForestCAValue(x, y, 1);
                }
        w.forestCA.pF = 0.0;                          // pas d'ignition spontanée
        w.setWindEnabled(true);
        w.setWindVector(0.0, 15.0);                   // dirRad=0 → souffle vers +X (Est), force 15 m/s
        w.setForestCAValue(cx, cy, ForestCA.FIRE_FIRST);

        int target = 120;
        java.util.Set<Long> ever = new java.util.HashSet<>();
        for (int k = 0; k < 200 && ever.size() < target; k++) {
            w.forestCA.step();
            for (int x = 0; x < W; x++) for (int y = 0; y < H; y++) {
                int s = w.getForestCAValue(x, y);
                if (s >= ForestCA.FIRE_FIRST && s <= ForestCA.BURNT) ever.add(((long) x << 20) | y);
            }
        }
        long sumX = 0; int n = 0;
        for (long key : ever) { sumX += (int) (key >> 20); n++; }
        double meanX = sumX / (double) Math.max(1, n);
        System.out.println("[Wind] feu sous vent +X : centre de masse X=" + meanX + " (foyer cx=" + cx + ")");
        assertTrue(meanX > cx + 1.0, "le feu s'étend davantage sous le vent (Est) : meanX=" + meanX + " > cx=" + cx);
    }
}
