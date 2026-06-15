package agents;

import agents.ai.AgentState;
import agents.ai.Percept;
import agents.ai.Perception;
import landscapegenerator.PerlinNoiseLandscapeGenerator;
import org.junit.jupiter.api.Test;
import scent.ScentKind;
import worlds.WorldOfCells;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests comportementaux olfactifs du Mouton (sous-projet C). Monde plat,
 * sans vent ni pluie ; on pose des puffs d'odeur à la main et on lit l'état
 * décidé / le déplacement. step() ne touche pas OpenGL.
 */
class MoutonScentBehaviorTest {

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
    void moutonIgnoreOdeurHumaine() {
        WorldOfCells w = flatWorld();
        Mouton m = new Mouton(10, 10, w);
        int now = w.getIteration();
        // Forte odeur HUMAIN sur la sonde du mouton (probe ≈ 2 cases).
        w.getScentField().emit(1, ScentKind.HUMAIN, -1, 12, 10, now, 1.0f);
        Percept p = Perception.sense(m, w, w.loups, w.moutons);
        assertEquals(0.0, p.scentDangerIntensity, 1e-9,
                "l'odeur humaine ne doit PAS compter comme danger (berger)");
        assertFalse(p.scentDangerDetected(), "aucun danger olfactif sur une trace humaine");
    }

    @Test
    void moutonDetecteOdeurLoup() {
        WorldOfCells w = flatWorld();
        Mouton m = new Mouton(10, 10, w);
        int now = w.getIteration();
        w.getScentField().emit(1, ScentKind.LOUP, -1, 12, 10, now, 1.0f);
        Percept p = Perception.sense(m, w, w.loups, w.moutons);
        assertTrue(p.scentDangerDetected(), "l'odeur de loup est bien un danger pour le mouton");
    }
}
