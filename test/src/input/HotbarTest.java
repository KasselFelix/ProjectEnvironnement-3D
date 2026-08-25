package input;

import agents.Loup;
import org.junit.jupiter.api.Test;
import worlds.WorldOfCells;
import static org.junit.jupiter.api.Assertions.*;

class HotbarTest {
    private static WorldOfCells flat() {
        WorldOfCells w = agents.AgentTestSupport.buildWorld();
        int W = w.getWidth(), H = w.getHeight();
        for (int x = 0; x < W; x++) for (int y = 0; y < H; y++) { w.setCellHeight(x, y, 0.5); w.setForestCAValue(x, y, 0); }
        return w;
    }

    @Test
    void mangerDisponibleSeulementSiCarcasseAdjacente() {
        WorldOfCells w = flat();
        Loup l = new Loup(10, 10, w); w.loups.add(l);
        assertFalse(HotbarAction.MANGER.available(l), "pas de carcasse => grise");
        w.spawnCarcass(11, 10, 70.0, objects.Species.MOUTON);
        assertTrue(HotbarAction.MANGER.available(l), "carcasse adjacente + cooldown 0 => dispo");
    }

    @Test
    void hurlerSuitLeCooldown() {
        WorldOfCells w = flat();
        Loup l = new Loup(10, 10, w); w.loups.add(l);
        assertTrue(HotbarAction.HURLER.available(l), "cooldown initial 0 => dispo");
        assertFalse(HotbarAction.HURLER.available(new agents.Mouton(5, 5, w)), "pas un loup => indisponible");
    }

    @Test
    void actionsNonImplementeesToujoursGrisees() {
        WorldOfCells w = flat();
        Loup l = new Loup(10, 10, w); w.loups.add(l);
        assertFalse(HotbarAction.FRAPPER.isImplemented());
        assertFalse(HotbarAction.FRAPPER.available(l), "non implementee => jamais disponible");
    }

    @org.junit.jupiter.api.Test
    void layoutsParDefautParEspece() {
        HotbarLayout h = new HotbarLayout();
        assertEquals(HotbarAction.FRAPPER, h.slot(objects.Species.LOUP, 0));
        assertEquals(HotbarAction.MANGER,  h.slot(objects.Species.LOUP, 1));
        assertEquals(HotbarAction.HURLER,  h.slot(objects.Species.LOUP, 2));
        assertEquals(HotbarAction.VIDE,    h.slot(objects.Species.LOUP, 8));
        assertEquals(HotbarAction.MANGER,  h.slot(objects.Species.OURS, 1));
        assertEquals(HotbarAction.BROUTER, h.slot(objects.Species.MOUTON, 0));
    }

    @org.junit.jupiter.api.Test
    void assignationEtRoundTripProperties() {
        HotbarLayout h = new HotbarLayout();
        h.assign(objects.Species.LOUP, 4, HotbarAction.SE_REPOSER);
        java.util.Properties p = new java.util.Properties();
        h.writeTo(p);
        HotbarLayout h2 = new HotbarLayout();
        h2.readFrom(p);
        assertEquals(HotbarAction.SE_REPOSER, h2.slot(objects.Species.LOUP, 4));
        assertEquals(HotbarAction.FRAPPER,    h2.slot(objects.Species.LOUP, 0), "slots non touches = defauts");
    }
}
