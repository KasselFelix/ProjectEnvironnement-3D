package agents;

import agents.ai.Axis;
import agents.ai.Genome;
import agents.ai.Pole;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import worlds.WorldOfCells;

class RecallFoodTest {
    @Test
    void loupAffameRetourneVersCarcasseMemorisee() {
        WorldOfCells w = flat();
        Loup l = new Loup(10, 25, w); l.isFounder = true; w.loups.add(l);
        l.energie = 50;
        // souvenir FOOD consolide (usage 2) a l'EST, sans carcasse en vue
        l.memory.reinforce(agents.ai.MemoryKind.FOOD, 40, 25, 200.0, 0, 3, l.memDistanceForTest());
        l.memory.reinforce(agents.ai.MemoryKind.FOOD, 40, 25, 200.0, 0, 3, l.memDistanceForTest());
        double d0 = w.distance(l.x, l.y, 40, 25);
        for (int t = 1; t < 400; t++) { w.setIteration(t); l.step(); if (w.distance(l.x,l.y,40,25) < d0 - 2) break; }
        assertTrue(w.distance(l.x, l.y, 40, 25) < d0, "le loup affame se rapproche du lieu memorise");
    }

    @Test
    void oublieLeLieuSiCarcasseAbsenteALArrivee() {
        WorldOfCells w = flat();
        Loup l = new Loup(24, 25, w); l.isFounder = true; w.loups.add(l);
        l.energie = 50;
        l.memory.reinforce(agents.ai.MemoryKind.FOOD, 25, 25, 70.0, 0, 3, l.memDistanceForTest());
        l.memory.reinforce(agents.ai.MemoryKind.FOOD, 25, 25, 70.0, 0, 3, l.memDistanceForTest());
        for (int t = 1; t < 200; t++) { w.setIteration(t); l.step(); if (!l.memory.contains(agents.ai.MemoryKind.FOOD, 25, 25)) break; }
        assertFalse(l.memory.contains(agents.ai.MemoryKind.FOOD, 25, 25), "arrive, aucune carcasse => oubli (lose-shift)");
    }

    @Test
    void proieVisiblePrimeSurSouvenir() {
        WorldOfCells w = flat();
        Loup l = new Loup(25, 25, w); l.isFounder = true; w.loups.add(l);
        l.energie = 50;
        l.memory.reinforce(agents.ai.MemoryKind.FOOD, 45, 25, 200.0, 0, 3, l.memDistanceForTest());
        l.memory.reinforce(agents.ai.MemoryKind.FOOD, 45, 25, 200.0, 0, 3, l.memDistanceForTest());
        Mouton proie = new Mouton(27, 25, w); proie.isFounder = true; w.moutons.add(proie);
        agents.ai.Percept p = agents.ai.Perception.sense(l, w, l.predators(), l.prey());
        assertEquals(agents.ai.AgentState.HUNT, l.decideState(p), "proie en vue prime sur le souvenir");
    }

    @Test
    void audaceFourragementSuitEndurance() {
        Genome neutre = new Genome();
        assertEquals(1.0, neutre.foragingBoldnessFactor(), 1e-9);
        Genome endurant = new Genome();
        endurant.set(Axis.ENDURANCE, Pole.POSITIVE);
        assertTrue(endurant.foragingBoldnessFactor() > 1.0, "endurant => va plus loin");
        Genome fragile = new Genome();
        fragile.set(Axis.ENDURANCE, Pole.NEGATIVE);
        assertTrue(fragile.foragingBoldnessFactor() < 1.0);
    }

    @Test
    void oursAffameRetourneVersCarcasseMemorisee() {
        WorldOfCells w = flat();
        Ours o = new Ours(10, 25, w); o.isFounder = true; w.ours.add(o);
        o.energie = 50;
        o.memory.reinforce(agents.ai.MemoryKind.FOOD, 40, 25, 300.0, 0, 3, o.memDistanceForTest());
        o.memory.reinforce(agents.ai.MemoryKind.FOOD, 40, 25, 300.0, 0, 3, o.memDistanceForTest());
        double d0 = w.distance(o.x, o.y, 40, 25);
        for (int t = 1; t < 600; t++) { w.setIteration(t); o.step(); if (w.distance(o.x,o.y,40,25) < d0 - 2) break; }
        assertTrue(w.distance(o.x, o.y, 40, 25) < d0, "l'ours affame se rapproche du lieu memorise");
    }

    @Test
    void oursOublieLeLieuSiCarcasseAbsenteALArrivee() {
        // Test DISCRIMINANT du chemin RECALL_FOOD : seul recallFoodStep appelle memory.forget(FOOD),
        // jamais SEARCH (qui home sur HUNTING). Un oubli prouve donc que RECALL_FOOD s'est execute.
        WorldOfCells w = flat();
        Ours o = new Ours(24, 25, w); o.isFounder = true; w.ours.add(o);
        o.energie = 50;
        o.memory.reinforce(agents.ai.MemoryKind.FOOD, 25, 25, 300.0, 0, 3, o.memDistanceForTest());
        o.memory.reinforce(agents.ai.MemoryKind.FOOD, 25, 25, 300.0, 0, 3, o.memDistanceForTest());
        for (int t = 1; t < 300; t++) { w.setIteration(t); o.step(); if (!o.memory.contains(agents.ai.MemoryKind.FOOD, 25, 25)) break; }
        assertFalse(o.memory.contains(agents.ai.MemoryKind.FOOD, 25, 25), "arrive, aucune carcasse => oubli (lose-shift)");
    }

    private static WorldOfCells flat() {
        WorldOfCells w = AgentTestSupport.buildWorld();
        int W = w.getWidth(), H = w.getHeight();
        for (int x = 0; x < W; x++) for (int y = 0; y < H; y++) { w.setCellHeight(x, y, 0.5); w.setForestCAValue(x, y, 0); }
        return w;
    }

    @Test
    void observationRepeteeNeGonflePasUsageMaisPorteLaMasse() {
        WorldOfCells w = flat();
        Loup l = new Loup(10, 25, w); l.isFounder = true; w.loups.add(l);
        l.energie = 50;
        w.spawnCarcass(15, 25, 70.0, objects.Species.MOUTON);   // en vue, fixe
        for (int t = 0; t < 30; t++) { w.setIteration(t); l.step(); }
        // edge-trigger : une seule entree d'usage 1 malgre 30 ticks de vue continue (pas d'inflation)
        assertEquals(1, l.memory.usageOf(agents.ai.MemoryKind.FOOD, 15, 25),
                "vue continue = 1 evenement (edge-triggered), pas 30");
        // param 4 : le souvenir porte la MASSE de la carcasse (payoff) — l'ancien remember() la perdait
        assertEquals(70.0, l.memory.valueOf(agents.ai.MemoryKind.FOOD, 15, 25), 1e-9,
                "le souvenir FOOD porte la masse observee");
    }
}
