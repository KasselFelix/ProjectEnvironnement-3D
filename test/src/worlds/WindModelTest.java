package worlds;

import landscapegenerator.PerlinNoiseLandscapeGenerator;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class WindModelTest {

    private static WorldOfCells world() {
        WorldOfCells w = new WorldOfCells();
        w.nbloups = 0; w.nbmoutons = 0; w.nbhumains = 0; w.nbours = 0;
        double[][] ls = PerlinNoiseLandscapeGenerator.generatePerlinNoiseLandscape(31, 31, 0.7, 0.4, 4);
        w.init(30, 30, ls);
        return w;
    }

    @Test
    void cibleDeForceReagitSaisonEtPluie() {
        WorldOfCells w = world();
        w.setWindEnabled(true);
        w.setBaseWindForce(5.0);
        w.setRaining(false);
        double ete   = w.targetWindForce(Season.SUMMER, false);
        double hiver = w.targetWindForce(Season.WINTER, false);
        double hiverPluie = w.targetWindForce(Season.WINTER, true);
        assertTrue(hiver > ete, "hiver plus venteux que l'été");
        assertTrue(hiverPluie > hiver, "la pluie renforce le vent");
        assertEquals(5.0 * Season.SUMMER.windFactor, ete, 1e-9);
    }

    @Test
    void forceResteBorneeEtDeterministeParSeed() {
        WorldOfCells w = world();
        w.setWindEnabled(true);
        w.setBaseWindForce(8.0);
        w.setWindSeed(123);
        for (int i = 0; i < 5000; i++) { w.setJour(1); w.updateWind(); }
        double f1 = w.getWindForce();
        assertTrue(f1 >= 0.0 && f1 <= World.WIND_FORCE_MAX, "force bornée [0, MAX] = " + f1);

        WorldOfCells w2 = world();
        w2.setWindEnabled(true); w2.setBaseWindForce(8.0); w2.setWindSeed(123);
        for (int i = 0; i < 5000; i++) { w2.setJour(1); w2.updateWind(); }
        assertEquals(f1, w2.getWindForce(), 1e-9, "déterministe à seed égal");
        assertEquals(w.getWindDirRad(), w2.getWindDirRad(), 1e-9, "direction déterministe");
    }

    @Test
    void desactiveDonneForceNulleEtHelpersNeutres() {
        WorldOfCells w = world();
        w.setWindEnabled(false);
        for (int i = 0; i < 100; i++) w.updateWind();
        assertEquals(0.0, w.getWindForce(), 1e-9, "vent désactivé → force 0");
        assertEquals(1.0, w.windSpeedFactor(1, 0, 1.0), 1e-9, "helper agent neutre");
        assertEquals(1.0, w.fireWindFactor(1, 0), 1e-9, "helper feu neutre");
    }
}
