package agents;

import agents.ai.Axis;
import agents.ai.Genome;
import agents.ai.Pole;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import worlds.WorldOfCells;

class RecallFoodTest {
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
