package scent;

import landscapegenerator.PerlinNoiseLandscapeGenerator;
import org.junit.jupiter.api.Test;
import worlds.WorldOfCells;
import static org.junit.jupiter.api.Assertions.*;

class ScentFieldTest {

    private static final int DX_VIEW = 51, DY_VIEW = 51;
    private static final int DX = DX_VIEW - 1, DY = DY_VIEW - 1;

    /** Monde plat tout-terre, vent nul, sans pluie : base deterministe. */
    private WorldOfCells flatWorld() {
        WorldOfCells world = new WorldOfCells();
        world.nbloups = 0; world.nbmoutons = 0; world.nbhumains = 0; world.nbours = 0;
        double[][] landscape = PerlinNoiseLandscapeGenerator
                .generatePerlinNoiseLandscape(DX_VIEW, DY_VIEW, 0.7, 0.4, 4);
        world.init(DX, DY, landscape);
        for (int x = 0; x < world.getWidth(); x++)
            for (int y = 0; y < world.getHeight(); y++)
                world.setCellHeight(x, y, 5.0);          // tout-terre, plat
        world.setWindEnabled(true);
        world.setWindVector(0.0, 0.0);                   // vent nul
        world.setRaining(false);
        return world;
    }

    @Test
    void emitThenSampleAtOriginApproxBase() {
        WorldOfCells w = flatWorld();
        ScentField f = new ScentField(w);
        f.emit(1, ScentKind.LOUP, -1, 10, 10, 0, 1.0f);
        assertEquals(1.0f, f.sampleAt(10, 10, 0).of(ScentKind.LOUP), 0.05f);
    }

    @Test
    void decaysToOneOverEAtTauThenVanishes() {
        WorldOfCells w = flatWorld();
        ScentField f = new ScentField(w);
        int hz = ui.SimulationConfig.getInstance().simulationHz;
        double tau = ui.SimulationConfig.getInstance().scentLifetimeSec;
        f.emit(1, ScentKind.LOUP, -1, 10, 10, 0, 1.0f);
        int atTau = (int) Math.round(tau * hz);
        assertEquals(0.368f, f.sampleAt(10, 10, atTau).of(ScentKind.LOUP), 0.05f);
        int far = (int) Math.round(tau * hz * 6);
        assertTrue(f.sampleAt(10, 10, far).of(ScentKind.LOUP) < 0.02f);
    }

    @Test
    void anisotropyAlongWindWiderThanCrosswind() {
        WorldOfCells w = flatWorld();
        w.setWindVector(0.0, 12.0);                      // dirRad=0 -> +X, 12 m/s
        ScentField f = new ScentField(w);
        f.emit(1, ScentKind.LOUP, -1, 10, 10, 0, 1.0f);
        int now = 20 * 10;                               // age ~10 s
        float along = f.sampleAt(13, 10, now).of(ScentKind.LOUP);  // le long de X
        float cross = f.sampleAt(10, 13, now).of(ScentKind.LOUP);  // en travers
        assertTrue(along > cross, "le long du vent doit porter plus loin");
    }

    @Test
    void multiPuffReportsDominantEmitter() {
        WorldOfCells w = flatWorld();
        ScentField f = new ScentField(w);
        f.emit(7, ScentKind.LOUP,   -1, 18, 10, 0, 0.3f);   // faible, loin
        f.emit(9, ScentKind.MOUTON, -1, 10, 10, 0, 1.0f);   // fort, sur place
        ScentReading r = f.sampleAt(10, 10, 0);
        assertEquals(ScentKind.MOUTON, r.dominantKind);
        assertEquals(9, r.dominantEmitterId);
    }

    @Test
    void torusWrapsAcrossEdge() {
        WorldOfCells w = flatWorld();
        ScentField f = new ScentField(w);
        f.emit(1, ScentKind.LOUP, -1, 0, 10, 0, 1.0f);
        float across = f.sampleAt(w.getWidth() - 1, 10, 0).of(ScentKind.LOUP);
        assertTrue(across > 0.1f, "le bord oppose du tore doit sentir l'odeur");
    }

    @Test
    void advectionShiftsCenterDownwind() {
        WorldOfCells w = flatWorld();
        w.setWindVector(0.0, 25.0);                      // dirRad=0 -> +X, fort
        ScentField f = new ScentField(w);
        f.emit(1, ScentKind.LOUP, -1, 10, 10, 0, 1.0f);
        for (int i = 0; i < 60; i++) f.step(0.05);       // iteration reste 0 -> pas de decroissance
        float downwind = f.sampleAt(12, 10, 0).of(ScentKind.LOUP);
        float upwind   = f.sampleAt(8, 10, 0).of(ScentKind.LOUP);
        assertTrue(downwind > upwind, "le centre doit avoir derive vers +X");
    }

    @Test
    void faintPuffsPurgedByStep() {
        WorldOfCells w = flatWorld();
        ScentField f = new ScentField(w);
        f.emit(1, ScentKind.LOUP, -1, 10, 10, 0, 1.0f);
        int far = (int) Math.round(ui.SimulationConfig.getInstance().scentLifetimeSec * 20 * 6);
        w.setIteration(far);
        f.step(0.05);
        assertEquals(0, f.size());
    }

    @Test
    void budgetNeverExceeded() {
        WorldOfCells w = flatWorld();
        ScentField f = new ScentField(w);
        for (int i = 0; i < ScentField.MAX_PUFFS + 50; i++)
            f.emit(i, ScentKind.LOUP, -1, 10, 10, 0, 1.0f);
        f.step(0.05);
        assertTrue(f.size() <= ScentField.MAX_PUFFS);
    }

    @Test
    void waterFadeOutpacesLand() {
        WorldOfCells w = flatWorld();
        w.setCellHeight(30, 30, -1.0);                   // une cellule d'eau
        ScentField f = new ScentField(w);
        f.emit(1, ScentKind.LOUP, -1, 10, 10, 0, 1.0f);  // sur terre
        f.emit(2, ScentKind.LOUP, -1, 30, 30, 0, 1.0f);  // sur eau
        for (int i = 0; i < 40; i++) f.step(0.05);
        float land  = f.sampleAt(10, 10, 0).of(ScentKind.LOUP);
        float water = f.sampleAt(30, 30, 0).of(ScentKind.LOUP);
        assertTrue(water < land, "l'odeur sur l'eau doit s'estomper plus vite");
    }

    @Test
    void gradientPointsTowardSource() {
        WorldOfCells w = flatWorld();
        ScentField f = new ScentField(w);
        f.emit(1, ScentKind.LOUP, -1, 12, 10, 0, 1.0f);  // source a l'Est de (10,10), assez proche
        assertEquals(1, f.gradientToward(10, 10, 0, ScentKind.LOUP)); // 1 = Est
    }

    @Test
    void gradientIsMinusOneWhenEmpty() {
        WorldOfCells w = flatWorld();
        ScentField f = new ScentField(w);
        assertEquals(-1, f.gradientToward(10, 10, 0, ScentKind.LOUP));
    }

    @Test
    void debugIntensitySumsKinds() {
        WorldOfCells w = flatWorld();
        ScentField f = new ScentField(w);
        f.emit(1, ScentKind.LOUP,   -1, 10, 10, 0, 1.0f);
        f.emit(2, ScentKind.MOUTON, -1, 10, 10, 0, 1.0f);
        assertEquals(2.0f, f.debugIntensityAt(10, 10, 0), 0.1f);
    }

    @Test
    void rainAcceleratesDecay() {
        WorldOfCells w = flatWorld();
        ScentField f = new ScentField(w);
        f.emit(1, ScentKind.LOUP, -1, 10, 10, 0, 1.0f);
        int now = 20 * 30;                                   // ~30 s d'age
        float dry = f.sampleAt(10, 10, now).of(ScentKind.LOUP);
        w.setRaining(true);
        float wet = f.sampleAt(10, 10, now).of(ScentKind.LOUP);
        assertTrue(wet < dry, "la pluie doit accelerer la disparition de l'odeur");
    }
}
