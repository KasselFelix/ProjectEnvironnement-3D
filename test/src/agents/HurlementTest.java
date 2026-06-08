package agents;

import agents.ai.AgentState;
import agents.ai.Genome;
import agents.ai.MemoryKind;
import agents.ai.Percept;
import agents.ai.Perception;
import org.junit.jupiter.api.Test;
import worlds.WorldOfCells;

import static org.junit.jupiter.api.Assertions.*;

class HurlementTest {

    /** Monde plat 50×50 sans obstacle (déterminisme : la position des proies/loups
     *  ne dépend pas du terrain Perlin aléatoire). */
    private static WorldOfCells flatWorld() {
        WorldOfCells w = AgentTestSupport.buildWorld();
        int W = w.getWidth(), H = w.getHeight();
        for (int x = 0; x < W; x++)
            for (int y = 0; y < H; y++) { w.setCellHeight(x, y, 0.5); w.setForestCAValue(x, y, 0); }
        return w;
    }

    @Test
    void noisyLocationBonSensExactDesorienteFlou() {
        WorldOfCells w = flatWorld();
        Loup l = new Loup(25, 25, w);
        java.util.Random rng = new java.util.Random(123);

        // errProb = 0.0 (BON SENS) → position EXACTE, indépendamment du rng.
        int[] exact = l.noisyLocation(20, 20, 0.0, 16.0, rng);
        assertArrayEquals(new int[]{20, 20}, exact, "bon sens => position exacte");

        // errProb = 0.5 (DÉSORIENTÉ) → la position est décalée (offset > 0) et bornée.
        boolean someOffset = false;
        for (int i = 0; i < 50; i++) {
            int[] n = l.noisyLocation(20, 20, 0.5, 16.0, rng);
            double d = w.distance(20, 20, n[0], n[1]);
            assertTrue(d <= 0.5 * 16.0 + 1.5, "offset borné (" + d + ")");   // +1.5 = arrondi
            if (d > 0) someOffset = true;
        }
        assertTrue(someOffset, "desoriente => au moins un tirage decale la position");
    }
}
