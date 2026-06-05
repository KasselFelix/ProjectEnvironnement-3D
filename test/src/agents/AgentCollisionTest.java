package agents;

import agents.ai.Locomotion;
import agents.ai.MoveConstraints;
import org.junit.jupiter.api.Test;
import worlds.WorldOfCells;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Les agents sont des OBSTACLES les uns pour les autres : ils ne peuvent plus se
 * superposer. Exception : un prédateur peut entrer sur la case de sa proie (pour
 * la dévorer — le chevauchement est transitoire, la proie meurt le tick même).
 */
class AgentCollisionTest {

    /** Une case occupée par un autre agent vivant est bloquée ; une case vide ou
     *  sa propre case ne l'est pas. */
    @Test
    void caseOccupeeParUnCongenereEstBloquee() {
        WorldOfCells world = AgentTestSupport.buildWorld();
        Mouton a = new Mouton(20, 20, world);
        Mouton b = new Mouton(21, 20, world);
        world.moutons.add(a); world.moutons.add(b);
        world.agents.add(a); world.agents.add(b);

        assertTrue(world.cellBlockedByAgent(21, 20, a), "la case du congénère est bloquée");
        assertFalse(world.cellBlockedByAgent(22, 20, a), "une case vide est libre");
        assertFalse(world.cellBlockedByAgent(20, 20, a), "sa propre case ne se bloque pas");
    }

    /** Un agent mort ne bloque plus le passage. */
    @Test
    void unAgentMortNeBloquePas() {
        WorldOfCells world = AgentTestSupport.buildWorld();
        Mouton a = new Mouton(20, 20, world);
        Mouton dead = new Mouton(21, 20, world);
        dead._alive = false;
        world.moutons.add(a); world.moutons.add(dead);
        world.agents.add(a); world.agents.add(dead);

        assertFalse(world.cellBlockedByAgent(21, 20, a), "un cadavre ne bloque pas");
    }

    /** Exception prédateur : un loup peut entrer sur la case d'un mouton (proie),
     *  mais un mouton ne peut pas entrer sur la case d'un autre mouton. */
    @Test
    void leLoupPeutEntrerSurLaCaseDeLaProie() {
        WorldOfCells world = AgentTestSupport.buildWorld();
        Mouton prey = new Mouton(21, 20, world);
        Loup loup = new Loup(20, 20, world);
        Mouton other = new Mouton(20, 21, world);
        world.moutons.add(prey); world.moutons.add(other);
        world.loups.add(loup);
        world.agents.add(prey); world.agents.add(other); world.agents.add(loup);

        assertFalse(world.cellBlockedByAgent(21, 20, loup),
                "un prédateur peut entrer sur la case de sa proie");
        assertTrue(world.cellBlockedByAgent(21, 20, other),
                "un mouton ne peut pas se superposer à un autre mouton");
    }

    /** À la locomotion, un agent ne se déplace jamais sur la case d'un congénère. */
    @Test
    void laLocomotionNeSuperposeJamaisDeuxAgents() {
        WorldOfCells world = AgentTestSupport.buildWorld();
        AgentTestSupport.flattenLandArea(world, 20, 20, 5);
        Mouton a = new Mouton(20, 20, world);
        Mouton b = new Mouton(21, 20, world);
        world.moutons.add(a); world.moutons.add(b);
        world.agents.add(a); world.agents.add(b);

        a._orient = 1;   // vers l'est = la case de b
        Locomotion.move(a, world, 1, MoveConstraints.landBound());

        assertFalse(a.x == b.x && a.y == b.y, "a ne doit pas finir sur la case de b");
    }
}
