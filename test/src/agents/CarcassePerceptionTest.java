package agents;

import agents.ai.Percept;
import agents.ai.Perception;
import objects.Carcass;
import objects.Species;
import org.junit.jupiter.api.Test;
import worlds.WorldOfCells;
import static org.junit.jupiter.api.Assertions.*;

class CarcassePerceptionTest {
    private static WorldOfCells flat() {
        WorldOfCells w = AgentTestSupport.buildWorld();
        int W = w.getWidth(), H = w.getHeight();
        for (int x = 0; x < W; x++) for (int y = 0; y < H; y++) { w.setCellHeight(x, y, 0.5); w.setForestCAValue(x, y, 0); }
        return w;
    }

    @Test
    void leLoupPercoitLaCarcasseLaPlusProcheDansSaVision() {
        WorldOfCells w = flat();
        Loup l = new Loup(25, 25, w); w.loups.add(l);
        w.spawnCarcass(28, 25, 70.0, Species.MOUTON);   // dist 3 <= vision 10
        Percept p = Perception.sense(l, w, l.predators(), l.prey());
        assertTrue(p.carcassVisible(), "carcasse a portee => percue");
        assertEquals(28, p.carcassX);
        assertEquals(25, p.carcassY);
    }

    @Test
    void carcasseHorsVisionOuEpuiseeNonPercue() {
        WorldOfCells w = flat();
        Loup l = new Loup(25, 25, w); w.loups.add(l);
        w.spawnCarcass(45, 25, 70.0, Species.MOUTON);   // dist 20 > vision 10
        assertFalse(Perception.sense(l, w, l.predators(), l.prey()).carcassVisible());
        w.carcasses.clear();
        // Carcasse épuisée (mass=0 via eat) encore présente dans la liste => doit être ignorée par la perception.
        Carcass depletee = new Carcass(27, 25, w, 70.0, Species.MOUTON);
        depletee.eat(70.0);                      // isGone() == true, mais l'objet reste dans la liste
        w.carcasses.add(depletee);
        assertFalse(Perception.sense(l, w, l.predators(), l.prey()).carcassVisible(),
                "carcasse epuisee encore en liste => non percue");
    }
}
