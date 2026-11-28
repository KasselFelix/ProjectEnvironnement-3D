package graphics;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TimeKeeperTest {

    /** Horloge pilotée à la main (nanosecondes). */
    private static final class FakeClock {
        long ns = 1_000_000_000L;
        void advanceMs(double ms) { ns += Math.round(ms * 1e6); }
    }

    private static TimeKeeper started(FakeClock c, int hz) {
        TimeKeeper tk = new TimeKeeper(() -> c.ns);
        assertEquals(0, tk.stepsToRun(hz));
        return tk;
    }

    @Test
    void premierAppelRetourneZeroIniteAccumulator() {
        TimeKeeper tk = new TimeKeeper();
        assertEquals(0, tk.stepsToRun(20),
                "Premier appel sert d'init, doit retourner 0.");
    }

    @Test
    void stepsToRunRespecteHzSurDureeFixe() throws Exception {
        TimeKeeper tk = new TimeKeeper();
        int hz = 20;
        tk.stepsToRun(hz); // init

        int totalSteps = 0;
        long t0 = System.nanoTime();
        while ((System.nanoTime() - t0) / 1e9 < 1.0) {
            totalSteps += tk.stepsToRun(hz);
            Thread.sleep(15); // ~66 FPS rendu
        }
        // Attendu ~20 steps en 1 sec à 20 Hz. Tolérance ±4 pour jitter d'OS.
        assertTrue(totalSteps >= 16 && totalSteps <= 24,
                "Attendu ~20 steps en 1 sec à 20 Hz, obtenu " + totalSteps);
    }

    @Test
    void stepsToRunRattrapeApresLag() throws Exception {
        TimeKeeper tk = new TimeKeeper();
        int hz = 20;
        tk.stepsToRun(hz); // init

        Thread.sleep(50); // ~1 tick à 20 Hz
        int s1 = tk.stepsToRun(hz);
        Thread.sleep(200); // ~4 ticks à 20 Hz
        int s2 = tk.stepsToRun(hz);
        assertTrue(s1 >= 1 && s1 <= 2,
                "50ms doit donner 1-2 ticks, obtenu " + s1);
        assertTrue(s2 >= 3 && s2 <= 5,
                "200ms doit donner 3-5 ticks, obtenu " + s2);
    }

    @Test
    void maxStepsPerFramePlafonneEnCasDeLagExtreme() throws Exception {
        TimeKeeper tk = new TimeKeeper();
        int hz = 20;
        tk.stepsToRun(hz); // init
        Thread.sleep(2000); // 2 sec de lag = 40 ticks théoriques
        int s = tk.stepsToRun(hz);
        assertTrue(s <= TimeKeeper.MAX_STEPS_PER_FRAME,
                "Doit être plafonné à MAX_STEPS_PER_FRAME=" + TimeKeeper.MAX_STEPS_PER_FRAME
                + ", obtenu " + s);
    }

    @Test
    void resetReinitialiseAccumulator() throws Exception {
        TimeKeeper tk = new TimeKeeper();
        tk.stepsToRun(20);
        Thread.sleep(100);
        tk.stepsToRun(20);
        tk.reset();
        // Après reset, le premier appel re-init et retourne 0
        assertEquals(0, tk.stepsToRun(20));
    }

    @Test
    void alphaEstLaFractionDuTickEnCours() {
        FakeClock c = new FakeClock();
        TimeKeeper tk = started(c, 20);  // 1 tick = 50 ms
        assertEquals(0.0, tk.alpha(), 1e-12);
        c.advanceMs(20);
        assertEquals(0, tk.stepsToRun(20));
        assertEquals(0.4, tk.alpha(), 1e-9);
        c.advanceMs(45);  // 65 ms en tout : 1 step, reste 0,3 tick
        assertEquals(1, tk.stepsToRun(20));
        assertEquals(0.3, tk.alpha(), 1e-9);
        java.util.Random r = new java.util.Random(7);
        for (int i = 0; i < 500; i++) {
            c.advanceMs(r.nextDouble() * 120);
            tk.stepsToRun(20, 3);
            double a = tk.alpha();
            assertTrue(a >= 0.0 && a < 1.0, "alpha dans [0,1) : " + a);
        }
    }

    @Test
    void antiSpiraleGardeLaFractionDuTick() {
        FakeClock c = new FakeClock();
        TimeKeeper tk = started(c, 20);
        c.advanceMs(1015);  // 20,3 ticks : 5 joués, surplus jeté, fraction gardée
        assertEquals(TimeKeeper.MAX_STEPS_PER_FRAME, tk.stepsToRun(20));
        assertEquals(0.3, tk.alpha(), 1e-9, "alpha garde sa phase après un gros lag");
        c.advanceMs(40);    // 0,3 + 0,8 = 1,1 tick
        assertEquals(1, tk.stepsToRun(20));
        assertEquals(0.1, tk.alpha(), 1e-9);
    }

    @Test
    void holdFigeAlphaEtNeRattrapePasLaPause() {
        FakeClock c = new FakeClock();
        TimeKeeper tk = started(c, 20);
        c.advanceMs(30);
        tk.stepsToRun(20);
        double a = tk.alpha();
        assertEquals(0.6, a, 1e-9);
        for (int i = 0; i < 100; i++) {  // 5 s de pause
            c.advanceMs(50);
            tk.hold();
            assertEquals(a, tk.alpha(), 0.0, "alpha figé en pause");
        }
        c.advanceMs(10);  // reprise : seuls les 10 ms d'après la pause comptent
        assertEquals(0, tk.stepsToRun(20), "aucun rattrapage de la pause");
        assertEquals(0.8, tk.alpha(), 1e-9, "alpha repart de sa valeur");
        c.advanceMs(15);
        assertEquals(1, tk.stepsToRun(20));
        assertEquals(0.1, tk.alpha(), 1e-9);
    }

    @Test
    void holdAvantLePremierAppelInitialiseLHorloge() {
        FakeClock c = new FakeClock();
        TimeKeeper tk = new TimeKeeper(() -> c.ns);
        tk.hold();
        c.advanceMs(25);
        assertEquals(0, tk.stepsToRun(20));
        assertEquals(0.5, tk.alpha(), 1e-9);
    }

    @Test
    void changementDeVitesseNeFaitPasSauterAlpha() {
        FakeClock c = new FakeClock();
        TimeKeeper tk = started(c, 20);
        c.advanceMs(35);
        tk.stepsToRun(20);
        double a = tk.alpha();
        assertEquals(0.7, a, 1e-9);
        // Passage en x8 (160 Hz) : rien d'écoulé, alpha inchangé.
        assertEquals(0, tk.stepsToRun(160, TimeKeeper.MAX_STEPS_PER_FRAME * 8));
        assertEquals(a, tk.alpha(), 1e-12, "alpha inchangé au changement de vitesse");
        c.advanceMs(5);  // 0,8 tick à 160 Hz : 0,7 + 0,8 = 1,5
        assertEquals(1, tk.stepsToRun(160, TimeKeeper.MAX_STEPS_PER_FRAME * 8));
        assertEquals(0.5, tk.alpha(), 1e-9);
        // Retour en x1 : idem.
        assertEquals(0, tk.stepsToRun(20));
        assertEquals(0.5, tk.alpha(), 1e-9);
    }

    @Test
    void alphaResteSousUnQuandLePlafondLaisseUnRetard() {
        FakeClock c = new FakeClock();
        TimeKeeper tk = started(c, 20);
        c.advanceMs(190);  // 3,8 ticks, plafond 2 : il reste 1,8 tick
        assertEquals(2, tk.stepsToRun(20, 2));
        assertTrue(tk.alpha() < 1.0, "alpha plafonné sous 1 : " + tk.alpha());
        c.advanceMs(0);
        assertEquals(1, tk.stepsToRun(20, 2), "le retard se rattrape à la frame suivante");
        assertEquals(0.8, tk.alpha(), 1e-9);
    }
}
