package scent;

import agents.Loup;
import landscapegenerator.PerlinNoiseLandscapeGenerator;
import objects.Carcass;
import objects.Species;
import org.junit.jupiter.api.Test;
import worlds.WorldOfCells;
import static org.junit.jupiter.api.Assertions.*;

class ScentIntegrationTest {

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
    void stepEmitsAgentScent() {
        WorldOfCells w = flatWorld();
        Loup loup = new Loup(10, 10, w);
        w.loups.add(loup); w.agents.add(loup); w.uniqueDynamicObjects.add(loup);
        w.step();
        assertTrue(w.getScentField().size() >= 1, "le loup doit avoir depose une odeur");
    }

    @Test
    void stepEmitsCarcassScent() {
        WorldOfCells w = flatWorld();
        w.carcasses.add(new Carcass(20, 20, w, 10.0, Species.MOUTON));
        w.step();
        float here = w.getScentField().sampleAt(20, 20, w.getIteration()).of(ScentKind.CARCASS);
        assertTrue(here > 0.5f, "la carcasse doit degager une odeur CARCASS");
    }
}
