package ui;

import agents.AgentTestSupport;
import agents.Loup;
import agents.Mouton;
import agents.Humain;
import agents.Ours;
import agents.Agent;
import org.junit.jupiter.api.Test;
import worlds.WorldOfCells;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AgentRosterTest {

    /** Monde de test : 2 loups, 3 moutons, 1 humain, 1 ours (positions quelconques). */
    private static WorldOfCells worldWith2L3M1H1O() {
        WorldOfCells w = AgentTestSupport.buildWorld();
        for (int i = 0; i < 2; i++) { Loup l = new Loup(i, 0, w); w.loups.add(l); w.agents.add(l); }
        for (int i = 0; i < 3; i++) { Mouton m = new Mouton(i, 1, w); w.moutons.add(m); w.agents.add(m); }
        Humain h = new Humain(0, 2, w); w.humains.add(h); w.agents.add(h);
        Ours o = new Ours(0, 3, w); w.ours.add(o); w.agents.add(o);
        return w;
    }

    @Test
    void flatListeOrdonneeIncluantLOurs() {
        WorldOfCells w = worldWith2L3M1H1O();
        AgentRoster r = new AgentRoster(w);
        List<Agent> flat = r.flat();
        assertEquals(7, flat.size(), "2+3+1+1 agents");
        assertTrue(flat.get(0) instanceof Loup,  "loups en tête");
        assertTrue(flat.get(2) instanceof Mouton, "puis moutons");
        assertTrue(flat.get(5) instanceof Humain, "puis humains");
        assertTrue(flat.get(6) instanceof Ours,   "ours en dernier (présent !)");
    }

    @Test
    void indexEtAtFontUnRoundTrip() {
        WorldOfCells w = worldWith2L3M1H1O();
        AgentRoster r = new AgentRoster(w);
        List<Agent> flat = r.flat();
        for (int i = 0; i < flat.size(); i++) {
            assertSame(flat.get(i), r.at(i), "at(i) == flat[i]");
            assertEquals(i, r.indexOf(flat.get(i)), "indexOf round-trip");
        }
        assertNull(r.at(-1));
        assertNull(r.at(999));
        assertEquals(-1, r.indexOf(new Loup(9, 9, w)), "agent absent → -1");
    }

    @Test
    void toutReplieMontreQuatreEntetes() {
        WorldOfCells w = worldWith2L3M1H1O();
        AgentRoster r = new AgentRoster(w);
        List<AgentRoster.Row> rows = r.visibleRows(new boolean[]{false, false, false, false});
        assertEquals(4, rows.size());
        assertTrue(rows.get(0).header && rows.get(0).sp == AgentRoster.Species.LOUP);
        assertTrue(rows.get(1).header && rows.get(1).sp == AgentRoster.Species.MOUTON);
        assertTrue(rows.get(2).header && rows.get(2).sp == AgentRoster.Species.HUMAIN);
        assertTrue(rows.get(3).header && rows.get(3).sp == AgentRoster.Species.OURS);
    }

    @Test
    void deplierOursInsereSaLigneApresSonEntete() {
        WorldOfCells w = worldWith2L3M1H1O();
        AgentRoster r = new AgentRoster(w);
        List<AgentRoster.Row> rows = r.visibleRows(new boolean[]{false, false, false, true});
        assertEquals(5, rows.size(), "4 entêtes + 1 ligne ours");
        AgentRoster.Row last = rows.get(4);
        assertFalse(last.header);
        assertEquals(AgentRoster.Species.OURS, last.sp);
        assertEquals(0, last.localIndex);
        assertTrue(last.agent instanceof Ours);
    }

    @Test
    void deplierLoupsInsereSesLignesEnTete() {
        WorldOfCells w = worldWith2L3M1H1O();
        AgentRoster r = new AgentRoster(w);
        List<AgentRoster.Row> rows = r.visibleRows(new boolean[]{true, false, false, false});
        // [entête L][L#0][L#1][entête M][entête H][entête O]
        assertEquals(6, rows.size());
        assertTrue(rows.get(0).header);
        assertFalse(rows.get(1).header); assertEquals(0, rows.get(1).localIndex);
        assertFalse(rows.get(2).header); assertEquals(1, rows.get(2).localIndex);
        assertTrue(rows.get(3).header && rows.get(3).sp == AgentRoster.Species.MOUTON);
    }

    @Test
    void speciesOfReconnaitLesQuatreEspeces() {
        WorldOfCells w = AgentTestSupport.buildWorld();
        assertEquals(AgentRoster.Species.LOUP,   AgentRoster.speciesOf(new Loup(0, 0, w)));
        assertEquals(AgentRoster.Species.MOUTON, AgentRoster.speciesOf(new Mouton(0, 0, w)));
        assertEquals(AgentRoster.Species.HUMAIN, AgentRoster.speciesOf(new Humain(0, 0, w)));
        assertEquals(AgentRoster.Species.OURS,   AgentRoster.speciesOf(new Ours(0, 0, w)));
    }
}
