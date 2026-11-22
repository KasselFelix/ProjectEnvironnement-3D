package scent;

import java.util.Random;

import landscapegenerator.PerlinNoiseLandscapeGenerator;
import org.junit.jupiter.api.Test;
import ui.SimulationConfig;
import worlds.WorldOfCells;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Le cache par tick de {@link ScentField} (pic et étalement de chaque puff
 * calculés une fois) doit rendre EXACTEMENT les mêmes valeurs, au bit près,
 * que la somme directe des {@link ScentField#intensityAt} dans l'ordre des puffs.
 */
class ScentFieldCacheTest {

    private WorldOfCells world() {
        WorldOfCells w = new WorldOfCells();
        w.nbloups = 0; w.nbmoutons = 0; w.nbhumains = 0; w.nbours = 0;
        double[][] ls = PerlinNoiseLandscapeGenerator.generatePerlinNoiseLandscape(51, 51, 0.7, 0.4, 4);
        w.init(50, 50, ls);
        // Relief connu : une bande d'eau (vieillissement accéléré des puffs au-dessus).
        for (int x = 0; x < w.getWidth(); x++)
            for (int y = 0; y < w.getHeight(); y++) w.setCellHeight(x, y, x < 10 ? -1.0 : 5.0);
        w.setWindEnabled(true);
        w.setRaining(false);
        return w;
    }

    /** Lecture de référence : l'ancien algorithme, puff par puff. */
    private static float[] reference(ScentField f, WorldOfCells w, int px, int py, int now) {
        int hz = SimulationConfig.getInstance().simulationHz;
        if (hz <= 0) hz = 20;
        double life = SimulationConfig.getInstance().scentLifetimeSec;
        double wx = w.getWindX(), wy = w.getWindY();
        double mag = Math.hypot(wx, wy);
        float[] per = new float[ScentKind.values().length + 1];
        float domI = 0f;
        for (ScentPuff p : f.puffsView()) {
            float i = ScentField.intensityAt(p, px, py, now, hz, life, wx, wy, mag, w.isRaining(),
                    w.getWidth(), w.getHeight());
            if (i <= 0f) continue;
            per[p.kind.ordinal()] += i;
            if (i > domI) domI = i;
        }
        per[per.length - 1] = domI;
        return per;
    }

    private void checkAgainstReference(double windForce, boolean rain) {
        WorldOfCells w = world();
        w.setWindVector(0.7, windForce);
        w.setRaining(rain);
        ScentField f = w.getScentField();
        Random rnd = new Random(42);
        ScentKind[] kinds = ScentKind.values();
        int now = 0;
        for (int t = 0; t < 400; t++) {
            w.setIteration(now);
            for (int e = 0; e < 3; e++)
                f.emit(rnd.nextInt(100), kinds[rnd.nextInt(kinds.length)], rnd.nextInt(5),
                        rnd.nextInt(50), rnd.nextInt(50), now, 0.2f + rnd.nextFloat() * 2f,
                        rnd.nextBoolean());
            f.step(0.05);
            now++;
        }
        w.setIteration(now);
        assertTrue(f.size() > 100, "assez de puffs pour un test significatif");
        for (int q = 0; q < 300; q++) {
            int px = rnd.nextInt(50), py = rnd.nextInt(50);
            float[] ref = reference(f, w, px, py, now);
            ScentReading r = f.sampleAt(px, py, now);
            for (ScentKind k : kinds)
                assertEquals(Float.floatToIntBits(ref[k.ordinal()]), Float.floatToIntBits(r.of(k)),
                        "somme " + k + " en (" + px + "," + py + ")");
            assertEquals(Float.floatToIntBits(ref[ref.length - 1]), Float.floatToIntBits(r.dominantIntensity));
        }
    }

    @Test
    void sansVentIdentiqueAuBitPres() { checkAgainstReference(0.0, false); }

    @Test
    void avecVentIdentiqueAuBitPres() { checkAgainstReference(6.0, false); }

    @Test
    void sousLaPluieIdentiqueAuBitPres() { checkAgainstReference(3.0, true); }

    @Test
    void leCacheSuitLeChangementDeVentDansLeMemeTick() {
        WorldOfCells w = world();
        w.setWindVector(0.0, 2.0);
        ScentField f = w.getScentField();
        w.setIteration(100);
        f.emit(1, ScentKind.LOUP, 0, 25, 25, 0, 1.5f, false);
        float before = f.sampleAt(27, 25, 100).of(ScentKind.LOUP);
        w.setWindVector(Math.PI / 2, 8.0);             // même tick, autre vent
        float after = f.sampleAt(27, 25, 100).of(ScentKind.LOUP);
        assertEquals(Float.floatToIntBits(reference(f, w, 27, 25, 100)[ScentKind.LOUP.ordinal()]),
                Float.floatToIntBits(after));
        assertNotEquals(before, after);
    }
}
