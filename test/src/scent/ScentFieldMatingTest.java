package scent;

import landscapegenerator.PerlinNoiseLandscapeGenerator;
import org.junit.jupiter.api.Test;
import worlds.WorldOfCells;
import static org.junit.jupiter.api.Assertions.*;

/** Canal de séduction au niveau du champ (sous-projet E). */
class ScentFieldMatingTest {

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
    void matingPuffLuSeulementSurLeCanalSeduction() {
        WorldOfCells w = flatWorld();
        ScentField f = w.getScentField();
        int now = w.getIteration();
        f.emit(1, ScentKind.LOUP, -1, 10, 10, now, 1.0f, true);    // mating
        f.emit(2, ScentKind.LOUP, -1, 10, 10, now, 1.0f, false);   // ordinaire
        // Le canal de séduction ne voit QUE le puff mating (intensité ~1, pas ~2).
        float mate = f.matingIntensityAt(10, 10, now, ScentKind.LOUP, 999);
        assertTrue(mate > 0.5f && mate < 1.5f, "canal mating = 1 seul puff (mesuré=" + mate + ")");
    }

    @Test
    void exclutSonPropreEmetteur() {
        WorldOfCells w = flatWorld();
        ScentField f = w.getScentField();
        int now = w.getIteration();
        f.emit(7, ScentKind.LOUP, -1, 10, 10, now, 1.0f, true);
        assertEquals(0f, f.matingIntensityAt(10, 10, now, ScentKind.LOUP, 7), 1e-6,
                "un agent ne sent pas sa propre odeur de séduction");
        assertTrue(f.matingIntensityAt(10, 10, now, ScentKind.LOUP, 8) > 0f,
                "un AUTRE agent la sent");
    }

    @Test
    void emit7argResteOrdinaire() {
        WorldOfCells w = flatWorld();
        ScentField f = w.getScentField();
        int now = w.getIteration();
        f.emit(3, ScentKind.LOUP, -1, 10, 10, now, 1.0f);          // 7-arg → mating=false
        assertEquals(0f, f.matingIntensityAt(10, 10, now, ScentKind.LOUP, 999), 1e-6,
                "l'émission 7-arg n'est pas une odeur de séduction");
        assertTrue(f.sampleAt(10, 10, now).of(ScentKind.LOUP) > 0f, "mais elle reste une odeur ordinaire");
    }

    @Test
    void gradientMatingVersLeSud() {
        WorldOfCells w = flatWorld();
        ScentField f = w.getScentField();
        int now = w.getIteration();
        f.emit(5, ScentKind.LOUP, -1, 10, 13, now, 1.0f, true);    // 3 cases au Sud de (10,10)
        assertEquals(2, f.matingGradientToward(10, 10, now, ScentKind.LOUP, 999),
                "gradient de séduction pointe au Sud (2)");
    }
}
