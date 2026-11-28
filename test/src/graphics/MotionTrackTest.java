package graphics;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Courbe de {@link MotionTrack} : on joue un agent de période fixe (un tour tous les
 * {@code P} ticks) sur un tore, on enregistre ses tours comme le fera {@code Agent.step()}
 * et on échantillonne la pose entre deux tours.
 */
class MotionTrackTest {

    private static final int W = 50, H = 40, P = 4;
    private static final double EPS = 1e-9;

    /** Agent simulé : case courante (repliée), tick du prochain tour, journal. */
    private static final class Walker {
        final MotionTrack track = new MotionTrack();
        final int w, h, period;
        int x, y;
        long tick;

        Walker(int x, int y, int w, int h, int period) {
            this.x = x; this.y = y; this.w = w; this.h = h; this.period = period;
            track.record(x, y, tick, tick + period, w, h);
        }

        /** Tour suivant : pas de (dx, dy), puis enregistrement. */
        void turn(int dx, int dy) {
            tick += period;
            x = Math.floorMod(x + dx, w);
            y = Math.floorMod(y + dy, h);
            track.record(x, y, tick, tick + period, w, h);
        }

        /** Pose à la fraction {@code u} du tour en cours. */
        double[] at(double u) {
            assertTrue(track.evaluate(tick + u * period, w, h));
            return new double[] { track.poseX(), track.poseY(), track.tangentX(), track.tangentY() };
        }
    }

    /** Écart signé minimal sur un axe torique (pose repliée). */
    private static double torusDiff(double a, double b, int n) {
        double d = (b - a) % n;
        if (d > n / 2.0) d -= n;
        if (d < -n / 2.0) d += n;
        return d;
    }

    @Test
    void ligneDroitePasseParLesCasesAVitesseConstante() {
        Walker wk = new Walker(10, 5, W, H, P);
        for (int i = 0; i < 6; i++) wk.turn(1, 0);
        for (int t = 0; t < 4; t++) {
            // Début de chaque tour : exactement sur une case (2 cases de retard).
            double[] start = wk.at(0);
            assertEquals(wk.x - 2, start[0], EPS);
            assertEquals(5.0, start[1], EPS);
            // Vitesse constante : la pose avance linéairement d'une case par tour.
            for (int k = 1; k <= 8; k++) {
                double u = k / 8.0;
                double[] p = wk.at(u);
                assertEquals(wk.x - 2 + u, p[0], EPS, "ligne droite à u=" + u);
                assertEquals(5.0, p[1], EPS);
                assertEquals(1.0, p[2], EPS, "vitesse d'une case par tour");
                assertEquals(0.0, p[3], EPS);
            }
            wk.turn(1, 0);
        }
    }

    @Test
    void escalierResteSurUneSeuleDroite() {
        Walker wk = new Walker(20, 20, W, H, P);
        for (int i = 0; i < 3; i++) { wk.turn(1, 0); wk.turn(0, 1); }
        // Le régime est établi : on mesure la distance de chaque pose à la droite
        // passant par la première pose, de direction (1, 1).
        double[] ref = wk.at(0);
        double maxDist = 0;
        boolean stepX = true;
        for (int t = 0; t < 8; t++) {
            for (int k = 0; k <= 20; k++) {
                double[] p = wk.at(k / 20.0);
                double dx = torusDiff(ref[0], p[0], W), dy = torusDiff(ref[1], p[1], H);
                maxDist = Math.max(maxDist, Math.abs(dx - dy) / Math.sqrt(2));
                assertEquals(45.0, Math.toDegrees(Math.atan2(p[3], p[2])), 0.01, "cap constant");
            }
            if (stepX) wk.turn(1, 0); else wk.turn(0, 1);
            stepX = !stepX;
        }
        assertTrue(maxDist < 0.01, "escalier à moins de 0,01 case de la diagonale : " + maxDist);
    }

    @Test
    void virageDe90DegresAuCapMonotone() {
        Walker wk = new Walker(10, 10, W, H, P);
        for (int i = 0; i < 4; i++) wk.turn(1, 0);
        double prev = Double.NEGATIVE_INFINITY;
        double first = Double.NaN, last = Double.NaN;
        for (int t = 0; t < 5; t++) {
            wk.turn(0, 1);
            for (int k = 0; k < 20; k++) {
                double[] p = wk.at(k / 20.0);
                double h = Math.toDegrees(Math.atan2(p[3], p[2]));
                assertTrue(h >= prev - 1e-9, "cap monotone : " + prev + " puis " + h);
                prev = h;
                if (Double.isNaN(first)) first = h;
                last = h;
            }
        }
        assertEquals(0.0, first, 1e-9, "part vers l'Est");
        assertEquals(90.0, last, 1e-9, "finit vers le Nord");
    }

    @Test
    void departEtArretSansACoup() {
        Walker wk = new Walker(10, 10, W, H, P);
        wk.turn(0, 0);
        wk.turn(0, 0);
        // Départ : vitesse nulle au premier instant, puis continue à chaque entrée.
        wk.turn(1, 0);
        assertEquals(0.0, wk.at(0)[2], EPS, "départ à vitesse nulle");
        double prevEnd = wk.at(1)[2];
        int[][] moves = { {1, 0}, {1, 0}, {1, 0}, {0, 0}, {0, 0}, {0, 0} };
        for (int[] m : moves) {
            wk.turn(m[0], m[1]);
            double start = wk.at(0)[2];
            assertEquals(prevEnd, start, EPS, "vitesse continue à l'entrée");
            prevEnd = wk.at(1)[2];
        }
        assertEquals(0.0, prevEnd, EPS, "arrêt à vitesse nulle");
        double[] p = wk.at(0.5);
        assertEquals(wk.x, p[0], EPS, "arrêté sur la case de l'agent");
        assertEquals(0.0, p[2], EPS);
    }

    @Test
    void poseContinueAChaqueEntreeEtAuBordDuTore() {
        int w = 12, h = 9;
        Walker wk = new Walker(9, 7, w, h, P);
        int[][] moves = { {1, 0}, {1, 0}, {1, 0}, {0, 1}, {0, 1}, {0, 1}, {1, 0}, {-1, 0},
                {0, -1}, {0, -1}, {0, -1}, {0, -1}, {-1, 0}, {-1, 0}, {-1, 0}, {1, 1} };
        double[] prev = wk.at(0);
        for (int[] m : moves) {
            double[] end = wk.at(1);
            wk.turn(m[0], m[1]);
            double[] start = wk.at(0);
            assertEquals(0.0, torusDiff(end[0], start[0], w), EPS, "X continu à l'entrée");
            assertEquals(0.0, torusDiff(end[1], start[1], h), EPS, "Y continu à l'entrée");
            for (int k = 0; k <= 10; k++) {
                double[] p = wk.at(k / 10.0);
                assertTrue(p[0] >= 0 && p[0] < w && p[1] >= 0 && p[1] < h, "pose repliée sur le tore");
                double step = Math.hypot(torusDiff(prev[0], p[0], w), torusDiff(prev[1], p[1], h));
                assertTrue(step < 0.3, "aucune traversée de carte : pas de " + step);
                prev = p;
            }
        }
    }

    @Test
    void recalageDuTourGardeLaPoseEtFinitALaNouvelleEcheance() {
        MotionTrack m = new MotionTrack();
        m.record(10, 10, 0, 14, W, H);
        m.record(11, 10, 14, 28, W, H);     // pas de 14 ticks vers +X
        m.evaluate(20, W, H, true);
        double x0 = m.poseX(), y0 = m.poseY();
        assertEquals(10 + 6.0 / 14, x0, 1e-9);
        m.retimeLast(20, 23);               // le reste du pas en 3 ticks (changement d'allure)
        m.evaluate(20, W, H, true);
        assertEquals(x0, m.poseX(), 1e-9, "pas de saut au recalage");
        assertEquals(y0, m.poseY(), 1e-9);
        m.evaluate(21.5, W, H, true);
        assertEquals(x0 + (11 - x0) * 0.5, m.poseX(), 1e-9, "reste du pas à vitesse constante");
        m.evaluate(23, W, H, true);
        assertEquals(11.0, m.poseX(), 1e-9, "pas fini à la nouvelle échéance");
        assertEquals(23, m.lastNextTick());
        m.retimeLast(30, 40);               // segment déjà fini : sans effet
        assertEquals(23, m.lastNextTick());
    }

    @Test
    void teleportationReinitialiseSurPlace() {
        Walker wk = new Walker(10, 10, W, H, P);
        for (int i = 0; i < 3; i++) wk.turn(1, 0);
        wk.tick += P;
        wk.x = 30; wk.y = 25;  // respawn loin
        wk.track.record(wk.x, wk.y, wk.tick, wk.tick + P, W, H);
        for (int k = 0; k <= 4; k++) {
            double[] p = wk.at(k / 4.0);
            assertEquals(30.0, p[0], EPS);
            assertEquals(25.0, p[1], EPS);
            assertEquals(0.0, Math.hypot(p[2], p[3]), EPS);
        }
        // Saut de deux cases : aussi une téléportation.
        wk.turn(2, 0);
        assertEquals(32.0, wk.at(0.3)[0], EPS);
    }

    @Test
    void pasDiagonalGlisseSansReinitialiser() {
        Walker wk = new Walker(10, 10, W, H, P);
        for (int i = 0; i < 3; i++) wk.turn(1, 1);
        double[] p = wk.at(0);
        assertEquals(11.0, p[0], EPS, "deux cases de retard sur la diagonale");
        assertEquals(11.0, p[1], EPS);
    }

    @Test
    void caseCouranteDifferenteReinitialise() {
        Walker wk = new Walker(10, 10, W, H, P);
        wk.turn(1, 0);
        assertFalse(wk.track.ensureCell(11, 10, W, H), "dernière entrée = case courante");
        assertTrue(wk.track.ensureCell(14, 3, W, H), "agent déplacé hors de son tour");
        double[] p = wk.at(0.5);
        assertEquals(14.0, p[0], EPS);
        assertEquals(3.0, p[1], EPS);

        MotionTrack empty = new MotionTrack();
        assertFalse(empty.evaluate(0, W, H), "journal vide : rien à évaluer");
        assertTrue(empty.ensureCell(5, 6, W, H));
        assertTrue(empty.evaluate(123.4, W, H));
        assertEquals(5.0, empty.poseX(), EPS);
        assertEquals(6.0, empty.poseY(), EPS);
    }

    @Test
    void memeNowMemePose() {
        Walker a = new Walker(10, 10, W, H, P), b = new Walker(10, 10, W, H, P);
        int[][] moves = { {1, 0}, {0, 1}, {0, 1}, {-1, 0} };
        for (int[] m : moves) { a.turn(m[0], m[1]); b.turn(m[0], m[1]); }
        double now = a.tick + 1.37;
        a.track.evaluate(now, W, H);
        double x = a.track.poseX(), y = a.track.poseY();
        a.track.evaluate(now + 2.1, W, H);  // une autre frame entre-temps
        a.track.evaluate(now, W, H);
        b.track.evaluate(now, W, H);
        assertEquals(x, a.track.poseX(), 0.0);
        assertEquals(y, a.track.poseY(), 0.0);
        assertEquals(x, b.track.poseX(), 0.0);
        assertEquals(y, b.track.poseY(), 0.0);
    }

    @Test
    void agentImmobileRenduDAujourdHui() {
        MotionTrack t = new MotionTrack();
        t.record(17, 23, 40, 44, W, H);
        for (int i = 1; i <= 5; i++) {
            t.record(17, 23, 40 + 4L * i, 44 + 4L * i, W, H);
            for (double f = 0; f < 4; f += 0.5) {
                t.evaluate(40 + 4L * i + f, W, H);
                assertEquals(17.0, t.poseX(), 0.0, "position exacte de la case");
                assertEquals(23.0, t.poseY(), 0.0);
            }
        }
        // Cap : celui du dernier déplacement (getLastUnit), au premier appel comme ensuite.
        double s = Math.sqrt(0.5);
        t.updateHeading(60.0, -s, s, MotionTrack.DEFAULT_MAX_TURN_DEG_PER_TICK);
        assertEquals(-s, t.headingX(), 1e-12);
        assertEquals(s, t.headingY(), 1e-12);
        t.updateHeading(61.0, -s, s, MotionTrack.DEFAULT_MAX_TURN_DEG_PER_TICK);
        assertEquals(-s, t.headingX(), 1e-12);
        assertEquals(s, t.headingY(), 1e-12);
        // Altitude : sur une case entière, la bilinéaire rend la valeur de la case, sans voisines.
        MotionTrack.CellSampler alt = (x, y) -> {
            assertEquals(17, x, "aucune case voisine lue");
            assertEquals(23, y);
            return 0.123456f;
        };
        assertEquals(0.123456f, MotionTrack.bilinear(t.poseX(), t.poseY(), alt), 0.0f);
    }

    @Test
    void bilineaireEntreQuatreCases() {
        MotionTrack.CellSampler s = (x, y) -> 10f * x + y;  // plan : la bilinéaire est exacte
        assertEquals(10f * 3.25f + 7.5f, MotionTrack.bilinear(3.25, 7.5, s), 1e-5f);
        assertEquals(10f * 3.5f + 7f, MotionTrack.bilinear(3.5, 7.0, s), 1e-5f);
        assertEquals(10f * 3f + 7.75f, MotionTrack.bilinear(3.0, 7.75, s), 1e-5f);
    }

    @Test
    void capSuitLaTangenteEtTourneABorne() {
        Walker wk = new Walker(10, 10, W, H, P);
        for (int i = 0; i < 4; i++) wk.turn(1, 0);
        MotionTrack t = wk.track;
        double now = wk.tick + 1;
        t.evaluate(now, W, H);
        t.updateHeading(now, 0, 1, 30);
        assertEquals(0.0, t.headingRad(), 1e-12, "cap = tangente (Est) dès le premier appel");
        // Arrêt : la courbe ralentit puis s'arrête, le cap reste à l'Est (repli = dernier pas).
        for (int i = 0; i < 3; i++) {
            wk.turn(0, 0);
            for (int k = 0; k < P; k++) {
                now = wk.tick + k;
                t.evaluate(now, W, H);
                t.updateHeading(now, 1, 0, 30);
                assertEquals(0.0, t.headingRad(), 1e-12, "cap Est pendant l'arrêt");
            }
        }
        assertEquals(0.0, Math.hypot(t.tangentX(), t.tangentY()), EPS, "arrêté");
        // Demi-tour sur place (repli vers l'Ouest) : au plus 30° par tick.
        t.updateHeading(now + 1, -1, 0, 30);
        double before = Math.toDegrees(t.headingRad());
        assertEquals(30.0, Math.abs(before), 1e-9, "rotation bornée à 30° par tick");
        // Pause : même now, cap figé.
        t.updateHeading(now + 1, -1, 0, 30);
        assertEquals(before, Math.toDegrees(t.headingRad()), 1e-12, "figé en pause");
        // Demi-tick : 15° de plus.
        t.updateHeading(now + 1.5, -1, 0, 30);
        assertEquals(45.0, Math.abs(Math.toDegrees(t.headingRad())), 1e-9);
        for (int k = 2; k < 10; k++) t.updateHeading(now + k, -1, 0, 30);
        assertEquals(-1.0, t.headingX(), 1e-12, "finit sur la direction de repli");
    }

    @Test
    void prochainTourEstLeMultipleSuivantDeLaPeriode() {
        assertEquals(28, MotionTrack.nextTurnTick(0, 28));
        assertEquals(28, MotionTrack.nextTurnTick(27, 28));
        assertEquals(56, MotionTrack.nextTurnTick(28, 28));
        assertEquals(30, MotionTrack.nextTurnTick(28, 5), "période changée en fin de tour");
        assertEquals(29, MotionTrack.nextTurnTick(28, 1));
        assertEquals(29, MotionTrack.nextTurnTick(28, 0), "garde à 1");
    }

    // ---------------------------------------------------------------- agent piloté

    /** Pose du trajet droit (agent piloté) à la fraction {@code u} du tour en cours. */
    private static double[] linearAt(Walker wk, double u) {
        assertTrue(wk.track.evaluate(wk.tick + u * wk.period, wk.w, wk.h, true));
        return new double[] { wk.track.poseX(), wk.track.poseY() };
    }

    @Test
    void pilotageTrajetDroitAuPlusUnTourDeRetard() {
        Walker wk = new Walker(10, 10, W, H, P);
        wk.turn(1, 0);
        wk.turn(1, 0);
        // Ligne droite : de l'avant-dernière case à la dernière pendant le tour.
        for (int k = 0; k <= 8; k++) {
            double u = k / 8.0;
            double[] p = linearAt(wk, u);
            assertEquals(wk.x - 1 + u, p[0], EPS, "trajet droit à u=" + u);
            assertEquals(10.0, p[1], EPS);
        }
        // Virage de 90° : coude net, la pose reste sur le segment vertical.
        wk.turn(0, 1);
        for (int k = 0; k <= 8; k++) {
            double u = k / 8.0;
            double[] p = linearAt(wk, u);
            assertEquals(wk.x, p[0], EPS, "virage : X figé sur la case du coude");
            assertEquals(wk.y - 1 + u, p[1], EPS);
        }
        // Fin de tour = case de la simulation (retard nul) ; continuité au tour suivant.
        double[] end = linearAt(wk, 1.0);
        wk.turn(0, 1);
        double[] next = linearAt(wk, 0.0);
        assertEquals(end[0], next[0], EPS);
        assertEquals(end[1], next[1], EPS);
    }

    @Test
    void basculeCourbeVersTrajetDroitSansSaut() {
        Walker wk = new Walker(10, 10, W, H, P);
        for (int i = 0; i < 4; i++) wk.turn(1, 0);
        double now = wk.tick + 0.5 * P;
        assertTrue(wk.track.evaluate(now, W, H, false));
        double cx = wk.track.poseX();
        // Même instant, tracé changé : aucune différence visible.
        assertTrue(wk.track.evaluate(now, W, H, true));
        assertEquals(cx, wk.track.poseX(), EPS, "pas de saut à la prise du pilotage");
        // Le fondu se résorbe : après MODE_BLEND_TICKS, trajet droit pur.
        double later = now + MotionTrack.MODE_BLEND_TICKS;
        assertTrue(wk.track.evaluate(later, W, H, true));
        double u = Math.min(1.0, (later - wk.tick) / P);
        assertEquals(wk.x - 1 + u, wk.track.poseX(), EPS, "fondu terminé");
        // Et retour à la courbe sans saut non plus.
        double back = wk.track.poseX();
        assertTrue(wk.track.evaluate(later, W, H, false));
        assertEquals(back, wk.track.poseX(), EPS, "pas de saut à la remise du pilotage");
    }

    @Test
    void capPiloteSuitLaDirectionDemandeeAvecRotationRapide() {
        MotionTrack t = new MotionTrack();
        t.resetAt(5, 5, 0, 4);
        t.updateHeadingToward(0, 1, 0, MotionTrack.PILOT_MAX_TURN_DEG_PER_TICK);   // pose initiale : +X
        assertEquals(0.0, t.headingRad(), EPS);
        // Demi-tour demandé : 60°/tick → atteint en 3 ticks, jamais plus vite.
        t.updateHeadingToward(1, -1, 0, MotionTrack.PILOT_MAX_TURN_DEG_PER_TICK);
        assertEquals(60.0, Math.abs(Math.toDegrees(t.headingRad())), 1e-6);
        t.updateHeadingToward(3, -1, 0, MotionTrack.PILOT_MAX_TURN_DEG_PER_TICK);
        assertEquals(180.0, Math.abs(Math.toDegrees(t.headingRad())), 1e-6, "demi-tour en 3 ticks");
        // Cible nulle : cap inchangé.
        t.updateHeadingToward(4, 0, 0, MotionTrack.PILOT_MAX_TURN_DEG_PER_TICK);
        assertEquals(180.0, Math.abs(Math.toDegrees(t.headingRad())), 1e-6);
    }
}
