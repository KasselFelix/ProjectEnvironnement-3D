package agents.ai;

import agents.AgentTestSupport;
import agents.Loup;
import agents.Mouton;
import org.junit.jupiter.api.Test;
import worlds.WorldOfCells;
import static org.junit.jupiter.api.Assertions.*;

class DecisionTest {

    /** Le feu prime sur la fuite : un mouton en feu ET un loup en vue → ON_FIRE. */
    @Test
    void feuPrimeSurFuite() {
        WorldOfCells world = AgentTestSupport.buildWorld();
        Mouton m = new Mouton(20, 20, world);
        m.setOnFire();
        world.moutons.add(m); world.agents.add(m);
        Loup l = new Loup(21, 20, world);
        world.loups.add(l); world.agents.add(l);

        Percept p = Perception.sense(m, world, world.loups, null);
        assertEquals(AgentState.ON_FIRE, m.decideState(p));
    }

    /** Prédateur visible et pas de feu → FLEE_PREDATOR. */
    @Test
    void predateurVuDeclencheFuite() {
        WorldOfCells world = AgentTestSupport.buildWorld();
        Mouton m = new Mouton(20, 20, world);
        world.moutons.add(m); world.agents.add(m);
        Loup l = new Loup(22, 20, world);
        world.loups.add(l); world.agents.add(l);

        Percept p = Perception.sense(m, world, world.loups, null);
        assertEquals(AgentState.FLEE_PREDATOR, m.decideState(p));
    }

    /** Loup affamé avec proie en vue → HUNT. */
    @Test
    void loupAffameChasse() {
        WorldOfCells world = AgentTestSupport.buildWorld();
        Loup l = new Loup(20, 20, world);
        l.energie = (int) (l.energieD * 0.5); // < 0.7 → affamé
        world.loups.add(l); world.agents.add(l);
        Mouton m = new Mouton(22, 21, world);  // proie diagonale en vue
        world.moutons.add(m); world.agents.add(m);

        Percept p = Perception.sense(l, world, null, world.moutons);
        assertEquals(AgentState.HUNT, l.decideState(p));
    }
}
