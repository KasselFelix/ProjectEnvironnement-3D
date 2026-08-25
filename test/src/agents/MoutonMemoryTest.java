package agents;

import agents.ai.AgentState;
import agents.ai.Axis;
import agents.ai.MemoryKind;
import agents.ai.Percept;
import agents.ai.Perception;
import agents.ai.Pole;
import org.junit.jupiter.api.Test;
import worlds.WorldOfCells;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase C — mémoire sémantique du Mouton (cf. docs/evolution.txt § 5) :
 * il mémorise les zones de danger en voyant un loup, les évite en errance, et
 * sa capacité de mémoire dépend de son intelligence.
 */
class MoutonMemoryTest {

    /** En voyant un loup, le mouton mémorise sa position comme zone de DANGER. */
    @Test
    void moutonMemoriseLaZoneDeDangerEnVoyantUnLoup() {
        WorldOfCells world = AgentTestSupport.buildWorld();
        Mouton m = new Mouton(20, 20, world);
        world.moutons.add(m); world.agents.add(m);
        Loup l = new Loup(22, 20, world);              // à portée de vision
        world.loups.add(l); world.agents.add(l);

        Percept p = Perception.sense(m, world, world.loups, null);
        assertTrue(p.predatorVisible(), "le loup doit être vu (sanity)");
        m.applyState(AgentState.FLEE_PREDATOR, p);

        assertTrue(m.memory.contains(MemoryKind.DANGER, 22, 20),
                "la position du loup doit être mémorisée comme danger");
    }

    /** En errance, le mouton détourne sa route si la case devant est une zone de
     *  danger mémorisée. */
    @Test
    void moutonEnErranceEviteUneZoneDeDangerMemorisee() {
        WorldOfCells world = AgentTestSupport.buildWorld();
        Mouton m = new Mouton(20, 20, world);
        world.moutons.add(m); world.agents.add(m);

        m._orient = 1;                                 // cap vers l'est → case (21,20)
        m.memory.remember(MemoryKind.DANGER, 21, 20);  // danger droit devant

        Percept p = Perception.sense(m, world, world.loups, null);  // pas de loup
        m.applyState(AgentState.WANDER, p);

        assertNotEquals(1, m._orient, "le mouton doit éviter la case de danger devant lui");
    }

    /** La capacité de mémoire suit l'intelligence (§ 5.1) : un mouton intelligent
     *  retient plus de lieux qu'un mouton neutre du même âge. */
    @Test
    void moutonIntelligentRetientPlusDeLieux() {
        WorldOfCells world = AgentTestSupport.buildWorld();
        Mouton intelligent = new Mouton(20, 20, world);
        intelligent.genome.set(Axis.INTELLIGENCE, Pole.POSITIVE);
        intelligent.maxAgeDays = 100.0;
        Mouton neutre = new Mouton(20, 20, world);
        neutre.maxAgeDays = 100.0;

        intelligent.refreshMemoryCapacity();
        neutre.refreshMemoryCapacity();
        for (int i = 0; i < 40; i++) {
            intelligent.memory.remember(MemoryKind.DANGER, i, 0);
            neutre.memory.remember(MemoryKind.DANGER, i, 0);
        }

        assertTrue(intelligent.memory.size() > neutre.memory.size(),
                "l'intelligent retient davantage de lieux");
    }
}
