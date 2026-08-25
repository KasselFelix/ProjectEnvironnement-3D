package scent;

import landscapegenerator.PerlinNoiseLandscapeGenerator;
import org.junit.jupiter.api.Test;
import worlds.WorldOfCells;
import static org.junit.jupiter.api.Assertions.*;

/** Le snapshot d'overlay expose le drapeau mating (5e colonne) — sous-projet E. */
class ScentSnapshotMatingTest {

    private WorldOfCells flatWorld() {
        WorldOfCells w = new WorldOfCells();
        w.nbloups = 0; w.nbmoutons = 0; w.nbhumains = 0; w.nbours = 0;
        double[][] ls = PerlinNoiseLandscapeGenerator.generatePerlinNoiseLandscape(51, 51, 0.7, 0.4, 4);
        w.init(50, 50, ls);
        for (int x = 0; x < w.getWidth(); x++)
            for (int y = 0; y < w.getHeight(); y++) w.setCellHeight(x, y, 5.0);
        w.setWindEnabled(true); w.setWindVector(0.0, 0.0); w.setRaining(false);
        return w;
    }

    @Test
    void snapshotPorteLeDrapeauMating() {
        WorldOfCells w = flatWorld();
        ScentField f = w.getScentField();
        int now = w.getIteration();
        f.emit(1, ScentKind.LOUP, -1, 10, 10, now, 1.0f, true);    // mating
        f.emit(2, ScentKind.LOUP, -1, 20, 20, now, 1.0f, false);   // ordinaire
        float[][] snap = f.debugPuffSnapshot(now);
        assertEquals(2, snap.length, "2 puffs");
        for (float[] row : snap) {
            assertEquals(5, row.length, "5 colonnes (cx,cy,peak,kind,mating)");
        }
        // Au moins un puff mating (col 4 == 1) et un non-mating (col 4 == 0).
        boolean hasMating = false, hasPlain = false;
        for (float[] row : snap) { if (row[4] > 0.5f) hasMating = true; else hasPlain = true; }
        assertTrue(hasMating, "un puff mating present");
        assertTrue(hasPlain, "un puff ordinaire present");
    }
}
