package scent;

import agents.Loup;
import landscapegenerator.PerlinNoiseLandscapeGenerator;
import org.junit.jupiter.api.Test;
import worlds.WorldOfCells;
import static org.junit.jupiter.api.Assertions.*;

class AgentEmitScentTest {

    private static final int DX_VIEW = 51, DY_VIEW = 51;
    private static final int DX = DX_VIEW - 1, DY = DY_VIEW - 1;

    private WorldOfCells flatWorld() {
        WorldOfCells world = new WorldOfCells();
        world.nbloups = 0; world.nbmoutons = 0; world.nbhumains = 0; world.nbours = 0;
        double[][] landscape = PerlinNoiseLandscapeGenerator
                .generatePerlinNoiseLandscape(DX_VIEW, DY_VIEW, 0.7, 0.4, 4);
        world.init(DX, DY, landscape);
        for (int x = 0; x < world.getWidth(); x++)
            for (int y = 0; y < world.getHeight(); y++)
                world.setCellHeight(x, y, 5.0);
        world.setWindEnabled(true);
        world.setWindVector(0.0, 0.0);
        world.setRaining(false);
        return world;
    }

    @Test
    void emitsOnCadenceNotEveryTick() {
        WorldOfCells w = flatWorld();
        ScentField f = w.getScentField();
        Loup loup = new Loup(10, 10, w);
        loup.emitScent(f, 0);   // emet
        loup.emitScent(f, 1);   // periode 3 -> non
        loup.emitScent(f, 2);   // non
        loup.emitScent(f, 3);   // emet
        assertEquals(2, f.size());
    }

    @Test
    void noEmissionInWater() {
        WorldOfCells w = flatWorld();
        w.setCellHeight(10, 10, -1.0);
        ScentField f = w.getScentField();
        Loup loup = new Loup(10, 10, w);
        loup.emitScent(f, 0);
        assertEquals(0, f.size());
    }

    @Test
    void wetSuppressionWeakensEmissionAfterWater() {
        WorldOfCells w = flatWorld();
        w.setCellHeight(10, 10, -1.0);          // case d'eau
        ScentField f = w.getScentField();
        Loup loup = new Loup(10, 10, w);
        loup.emitScent(f, 0);                   // dans l'eau -> mouille arme, rien emis
        assertEquals(0, f.size());
        loup.x = 11; loup.y = 10;               // sort sur terre (case plate)
        loup.emitScent(f, 5);                   // emet attenue (x0.2)
        assertEquals(1, f.size());
        assertEquals(0.2f, f.sampleAt(11, 10, 5).of(ScentKind.LOUP), 0.05f);
    }
}
