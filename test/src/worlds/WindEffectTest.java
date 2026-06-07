package worlds;

import landscapegenerator.PerlinNoiseLandscapeGenerator;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class WindEffectTest {

    private static WorldOfCells windyWorld(double dirRad, double force) {
        WorldOfCells w = new WorldOfCells();
        w.nbloups = 0; w.nbmoutons = 0; w.nbhumains = 0; w.nbours = 0;
        double[][] ls = PerlinNoiseLandscapeGenerator.generatePerlinNoiseLandscape(21, 21, 0.7, 0.4, 4);
        w.init(20, 20, ls);
        w.setWindEnabled(true);
        w.setWindVector(dirRad, force);
        return w;
    }

    @Test
    void dosFaceTraversAuVent() {
        WorldOfCells w = windyWorld(0.0, 12.0);
        double dos    = w.windSpeedFactor(1, 0, 1.0);
        double face   = w.windSpeedFactor(-1, 0, 1.0);
        double travers= w.windSpeedFactor(0, 1, 1.0);
        assertTrue(dos > 1.0, "dos au vent → bonus (" + dos + ")");
        assertTrue(face < 1.0, "face au vent → malus (" + face + ")");
        assertEquals(1.0, travers, 1e-9, "travers → neutre");
    }

    @Test
    void grosAnimalMoinsAffecte() {
        WorldOfCells w = windyWorld(0.0, 12.0);
        double mouton = w.windSpeedFactor(-1, 0, 1.0);
        double ours   = w.windSpeedFactor(-1, 0, 2.0);
        assertTrue(ours > mouton, "le gros animal subit moins le malus (" + ours + " > " + mouton + ")");
    }

    @Test
    void croissantAvecLaForceEtBorne() {
        double faible = windyWorld(0.0, 4.0).windSpeedFactor(1, 0, 1.0);
        double fort   = windyWorld(0.0, 20.0).windSpeedFactor(1, 0, 1.0);
        assertTrue(fort > faible, "plus de vent → plus de bonus");
        assertTrue(fort <= World.WIND_SPEED_FACTOR_MAX + 1e-9, "borné en haut");
        double contreFort = windyWorld(0.0, 25.0).windSpeedFactor(-1, 0, 1.0);
        assertTrue(contreFort >= World.WIND_SPEED_FACTOR_MIN - 1e-9, "borné en bas");
    }

    @Test
    void neutreSiVentNulOuDeplacementNul() {
        WorldOfCells w = windyWorld(0.0, 12.0);
        assertEquals(1.0, w.windSpeedFactor(0, 0, 1.0), 1e-9, "déplacement nul → neutre");
        w.setWindEnabled(false);
        assertEquals(1.0, w.windSpeedFactor(1, 0, 1.0), 1e-9, "vent off → neutre");
    }

    @Test
    void feuPousseSousLeVentEtFreineContre() {
        WorldOfCells w = windyWorld(0.0, 12.0);
        double downwind = w.fireWindFactor(1, 0);
        double upwind   = w.fireWindFactor(-1, 0);
        double cross    = w.fireWindFactor(0, 1);
        assertTrue(downwind > 1.0, "feu accéléré sous le vent (" + downwind + ")");
        assertTrue(upwind < 1.0, "feu freiné contre le vent (" + upwind + ")");
        assertTrue(downwind > cross && cross >= upwind, "monotonie directionnelle");
    }

    @Test
    void feuWindFactorNeutreSansVent() {
        WorldOfCells w = windyWorld(0.0, 0.0);
        assertEquals(1.0, w.fireWindFactor(1, 0), 1e-9);
        w.setWindVector(0.0, 12.0); w.setWindEnabled(false);
        assertEquals(1.0, w.fireWindFactor(1, 0), 1e-9, "vent off → neutre");
    }
}
