package agents;

import agents.ai.MemoryKind;
import org.junit.jupiter.api.Test;
import worlds.WorldOfCells;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase F — apprentissage social & éducation du Mouton (cf. docs/evolution.txt
 * § 8) : un mouton apprend les connaissances de ses congénères proches ; un
 * agneau apprend de son parent ; un orphelin isolé n'apprend rien.
 */
class MoutonSocialTest {

    /** Apprentissage social (§ 8.1) : un mouton recopie le lieu sûr connu d'un
     *  congénère proche. */
    @Test
    void moutonApprendDUnVoisin() {
        WorldOfCells world = AgentTestSupport.buildWorld();
        Mouton savant = new Mouton(20, 20, world);
        savant.memory.remember(MemoryKind.SAFE_PLACE, 40, 40);
        Mouton novice = new Mouton(21, 20, world);          // voisin, ignorant
        world.moutons.add(savant); world.moutons.add(novice);
        world.agents.add(savant); world.agents.add(novice);

        assertFalse(novice.memory.contains(MemoryKind.SAFE_PLACE, 40, 40));
        novice.learnFromNeighbours();
        assertTrue(novice.memory.contains(MemoryKind.SAFE_PLACE, 40, 40),
                "le novice apprend du savant proche");
    }

    /** Éducation (§ 8.2) : un agneau près de son parent acquiert ses savoirs. */
    @Test
    void agneauApprendDeSonParent() {
        WorldOfCells world = AgentTestSupport.buildWorld();
        Mouton parent = new Mouton(20, 20, world);
        parent.memory.remember(MemoryKind.DANGER, 5, 5);
        Mouton lamb = new Mouton(20, 20, world);
        lamb.isFounder = false;
        lamb.parent = parent;
        world.moutons.add(parent); world.moutons.add(lamb);
        world.agents.add(parent); world.agents.add(lamb);

        lamb.learnFromNeighbours();
        assertTrue(lamb.memory.contains(MemoryKind.DANGER, 5, 5),
                "l'agneau apprend le danger connu de son parent");
    }

    /** Un orphelin isolé (aucun congénère à portée) n'apprend rien socialement. */
    @Test
    void orphelinIsoleNApprendRien() {
        WorldOfCells world = AgentTestSupport.buildWorld();
        Mouton orphan = new Mouton(20, 20, world);          // seul dans le monde
        world.moutons.add(orphan); world.agents.add(orphan);

        orphan.learnFromNeighbours();
        assertEquals(0, orphan.memory.size(), "isolé → rien à apprendre");
    }
}
