package worlds;

import agents.AgentTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Météo & température (L6) : pluie probabiliste par saison, température dérivée
 * de la saison + jour/nuit + pluie, et couplages (pluie → feu, froid → vitesse).
 * Pas de dépendance OpenGL.
 */
class WorldWeatherTest {

    @Test
    void laPluieAmortitLaPropagationDuFeu() {
        WorldOfCells w = AgentTestSupport.buildWorld();
        w.setRaining(false);
        assertEquals(1.0, w.fireSpreadFactor(), 1e-9, "à sec, le feu se propage normalement");
        w.setRaining(true);
        assertTrue(w.fireSpreadFactor() < 1.0, "sous la pluie, la propagation du feu est amortie");
    }

    @Test
    void temperaturePlusBasseLaNuitEtSousLaPluie() {
        WorldOfCells w = AgentTestSupport.buildWorld();
        w.setSeasonLengthDays(0);   // été perpétuel (base stable)

        w.setJour(1); w.setRaining(false);
        double jourSec = w.getTemperature();
        w.setJour(0);
        double nuitSec = w.getTemperature();
        assertTrue(nuitSec < jourSec, "il fait plus froid la nuit que le jour");

        w.setJour(1); w.setRaining(true);
        double jourPluie = w.getTemperature();
        assertTrue(jourPluie < jourSec, "la pluie rafraîchit");
    }

    @Test
    void hiverPlusFroidQueEte() {
        WorldOfCells w = AgentTestSupport.buildWorld();
        w.setJour(1); w.setRaining(false);
        // L'enum porte les bases ; vérifie l'ordre via getTemperature à saison forcée.
        assertTrue(Season.WINTER.baseTemperatureC < Season.SUMMER.baseTemperatureC,
                "l'hiver est plus froid que l'été");
    }

    @Test
    void grandFroidRalentitLesAgents() {
        WorldOfCells w = AgentTestSupport.buildWorld();
        // Été, jour, sec → tiède → pas de ralentissement.
        w.setSeasonLengthDays(0);
        w.setJour(1); w.setRaining(false);
        assertEquals(1.0, w.coldSpeedFactor(), 1e-9, "par temps doux, aucun ralentissement");

        // Atteint l'hiver (base -2°C) par stepping, puis force nuit + pluie pour
        // descendre bien sous 0 → le facteur de froid passe sous 1.0, borné à 0.7.
        w.setDureeJour(1);
        w.setSeasonLengthDays(1);
        int guard = 0;
        while (w.getCurrentDay() < 3 && guard++ < 100) w.step();
        assertEquals(Season.WINTER, w.currentSeason());
        w.setJour(0); w.setRaining(true);
        assertTrue(w.getTemperature() < 0, "nuit d'hiver pluvieuse → température négative");
        double f = w.coldSpeedFactor();
        assertTrue(f < 1.0, "par grand froid, les agents s'engourdissent (facteur " + f + ")");
        assertTrue(f >= 0.7, "le ralentissement reste borné à 0.7");
    }
}
