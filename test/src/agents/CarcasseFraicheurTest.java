package agents;

import objects.Carcass;
import objects.Species;
import org.junit.jupiter.api.Test;
import worlds.WorldOfCells;
import static org.junit.jupiter.api.Assertions.*;

class CarcasseFraicheurTest {
    private static WorldOfCells flat() {
        WorldOfCells w = AgentTestSupport.buildWorld();
        int W = w.getWidth(), H = w.getHeight();
        for (int x = 0; x < W; x++) for (int y = 0; y < H; y++) { w.setCellHeight(x, y, 0.5); w.setForestCAValue(x, y, 0); }
        return w;
    }

    @Test
    void resteFraichePendantFreshSec() {
        WorldOfCells w = flat();
        w.spawnCarcass(10, 10, 70.0, Species.MOUTON);
        Carcass c = w.carcasses.get(0);
        w.rotCarcasses(Carcass.FRESH_SEC);                 // pile la fin de la fenetre fraiche
        assertEquals(1.0, c.freshness(), 1e-9);
        assertTrue(c.isFresh());
        assertEquals(70.0, c.mass, 1e-9, "la pourriture ne touche pas la masse");
    }

    @Test
    void pourritApresLaFenetreFraiche() {
        WorldOfCells w = flat();
        w.spawnCarcass(10, 10, 70.0, Species.MOUTON);
        Carcass c = w.carcasses.get(0);
        w.rotCarcasses(Carcass.FRESH_SEC + Carcass.ROT_SEC / 2.0);
        assertEquals(0.5, c.freshness(), 0.05);
        assertFalse(c.isFresh());
    }

    @Test
    void disparaitQuandEntierementPourrie() {
        WorldOfCells w = flat();
        w.spawnCarcass(10, 10, 70.0, Species.MOUTON);
        w.rotCarcasses(Carcass.FRESH_SEC + Carcass.ROT_SEC + 1.0);
        assertTrue(w.carcasses.isEmpty(), "fraicheur epuisee => retiree (masse pourtant intacte)");
    }

    @Test
    void mangerReduitMasseSansToucherFraicheur() {
        WorldOfCells w = flat();
        w.spawnCarcass(10, 10, 70.0, Species.MOUTON);
        Carcass c = w.carcasses.get(0);
        c.eat(30.0);
        assertEquals(40.0, c.mass, 1e-9);
        assertEquals(1.0, c.freshness(), 1e-9, "manger ne fait pas pourrir");
    }
}
