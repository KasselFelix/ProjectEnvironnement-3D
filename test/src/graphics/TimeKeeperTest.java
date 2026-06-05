package graphics;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TimeKeeperTest {

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
}
