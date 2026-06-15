package agents;

import agents.ai.AgentState;
import landscapegenerator.PerlinNoiseLandscapeGenerator;
import org.junit.jupiter.api.Test;
import scent.ScentKind;
import worlds.WorldOfCells;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests comportementaux olfactifs du Loup (sous-projet C) + géométrie partagée.
 * Monde plat, sans vent ni pluie ; on pose des puffs et on observe l'état /
 * le déplacement. step() ne touche pas OpenGL.
 */
class LoupScentBehaviorTest {

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

    /** Rend le loup affamé (sous HUNGER_RATIO) et l'enregistre dans le monde. */
    private Loup hungryLoup(WorldOfCells w, int x, int y) {
        Loup l = new Loup(x, y, w);
        l.energie = (int) (l.energieD * 0.5);   // < energieD*0.7 → affamé
        w.loups.add(l); w.agents.add(l); w.uniqueDynamicObjects.add(l);
        return l;
    }

    @Test
    void projectionWaypointTorus() {
        WorldOfCells w = flatWorld();
        Loup l = new Loup(10, 10, w);
        // 0=N(-y), 1=E(+x), 2=S(+y), 3=O(-x). Projection à 5 cases.
        assertArrayEquals(new int[]{15, 10}, l.projectScentWaypoint(1, 5), "Est = +x");
        assertArrayEquals(new int[]{10, 15}, l.projectScentWaypoint(2, 5), "Sud = +y");
        assertArrayEquals(new int[]{10,  5}, l.projectScentWaypoint(0, 5), "Nord = -y");
        assertArrayEquals(new int[]{ 5, 10}, l.projectScentWaypoint(3, 5), "Ouest = -x");
    }

    @Test
    void etatsOlfactifsExistent() {
        assertNotNull(AgentState.valueOf("SCENT_TRACK"));
        assertNotNull(AgentState.valueOf("WARY"));
    }
}
