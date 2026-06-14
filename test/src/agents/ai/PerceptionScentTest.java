package agents.ai;

import agents.Humain;
import agents.Loup;
import landscapegenerator.PerlinNoiseLandscapeGenerator;
import org.junit.jupiter.api.Test;
import worlds.WorldOfCells;
import static org.junit.jupiter.api.Assertions.*;

class PerceptionScentTest {

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
    void acuiteEffectiveBaseFoisGenome() {
        WorldOfCells w = flatWorld();
        Loup loup = new Loup(10, 10, w);
        assertEquals(1.0, Perception.olfactionAcuity(loup), 1e-9, "loup neutre = base 1.0");
        loup.genome.set(Axis.OLFACTION, Pole.POSITIVE);
        assertEquals(1.4, Perception.olfactionAcuity(loup), 1e-9, "loup NEZ FIN = 1.4");
    }

    @Test
    void humainSousLeGate() {
        WorldOfCells w = flatWorld();
        Humain h = new Humain(10, 10, w);
        assertTrue(Perception.olfactionAcuity(h) < ui.SimulationConfig.getInstance().olfactionGate,
                "l'humain (0.15) est sous le gate (0.3)");
    }
}
