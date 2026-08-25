package worlds;

import agents.AgentTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Neige sur les sommets (L7). La loi {@code World.nextSnowDepth} est pure → testée
 * directement ; l'intégration via {@code stepSnow} est vérifiée en atteignant
 * l'hiver. Pas de dépendance OpenGL.
 */
class WorldSnowTest {

    private static final double LINE = 0.55;

    @Test
    void laNeigeSAccumuleEnHautEtAuFroid() {
        // Sommet (altitude 0.9), gel (-5°C) → la neige s'accumule.
        double d0 = 0.0;
        double d1 = World.nextSnowDepth(d0, -5.0, 0.9, LINE);
        assertTrue(d1 > d0, "il neige sur les sommets quand il gèle");
        double d2 = World.nextSnowDepth(d1, -5.0, 0.9, LINE);
        assertTrue(d2 > d1, "la neige continue de s'accumuler");
    }

    @Test
    void laNeigeFondAuChaud() {
        double melted = World.nextSnowDepth(0.5, 20.0, 0.9, LINE);
        assertTrue(melted < 0.5, "la neige fond quand il fait chaud, même en altitude");
    }

    @Test
    void pasDeNeigeSousLaLigneDeNeige() {
        // Basse altitude (0.2 < ligne 0.55) même au gel : pas d'accumulation.
        double d = World.nextSnowDepth(0.0, -20.0, 0.20, LINE);
        assertEquals(0.0, d, 1e-9, "la neige ne tient pas sous la ligne de neige");
        // Et une neige résiduelle basse finit par fondre.
        double resid = World.nextSnowDepth(0.3, -20.0, 0.20, LINE);
        assertTrue(resid < 0.3, "la neige résiduelle en bas fond");
    }

    @Test
    void epaisseurPlafonnee() {
        double d = World.nextSnowDepth(World.SNOW_MAX, -30.0, 0.95, LINE);
        assertEquals(World.SNOW_MAX, d, 1e-9, "l'épaisseur de neige est plafonnée");
    }

    @Test
    void stepSnowEnneigeLesSommetsEnHiver() {
        WorldOfCells w = AgentTestSupport.buildWorld();
        // Un sommet à 90% de l'altitude max, sec et net.
        int hx = 25, hy = 25;
        double maxH = w.getMaxEverHeight();
        org.junit.jupiter.api.Assumptions.assumeTrue(maxH > 0);
        w.setCellHeight(hx, hy, maxH * 0.9);

        // Atteint l'hiver, nuit → température bien négative.
        w.setDureeJour(1);
        w.setSeasonLengthDays(1);
        int guard = 0;
        while (w.getCurrentDay() < 3 && guard++ < 100) w.step();
        assertEquals(Season.WINTER, w.currentSeason());
        w.setJour(0); w.setRaining(false);
        assertTrue(w.getTemperature() <= 0.0, "nuit d'hiver → gel");

        double before = w.getSnowDepth(hx, hy);
        for (int k = 0; k < 5; k++) w.stepSnow();
        assertTrue(w.getSnowDepth(hx, hy) > before, "le sommet s'enneige en hiver");
    }
}
