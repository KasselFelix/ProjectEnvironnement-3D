package agents;

import agents.ai.AgentState;
import agents.ai.Percept;
import agents.ai.Perception;
import org.junit.jupiter.api.Test;
import worlds.WorldOfCells;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Super-prédateur Ours (L4) : 3e niveau trophique (Ours → Loup → Mouton).
 * L'ours chasse et dévore le loup ; le loup, lui, craint désormais l'ours.
 */
class OursTest {

    @Test
    void oursAffameChasseLeLoup() {
        WorldOfCells world = AgentTestSupport.buildWorld();
        int cx = 25, cy = 25;
        AgentTestSupport.flattenLandArea(world, cx, cy, 12);

        Ours ours = new Ours(cx, cy, world);
        ours.energie = 100;   // affamé
        world.ours.add(ours);
        world.agents.add(ours);
        world.uniqueDynamicObjects.add(ours);

        Loup loup = new Loup((cx + 4) % world.getWidth(), cy, world);   // proie à l'EST
        world.loups.add(loup);
        world.agents.add(loup);
        world.uniqueDynamicObjects.add(loup);

        Percept p = Perception.sense(ours, world, null, world.loups);
        assertTrue(p.preyVisible(), "l'ours voit le loup");
        AgentState s = ours.decideState(p);
        assertEquals(AgentState.HUNT, s, "ours affamé + loup en vue → HUNT");
        ours.applyState(s, p);
        assertEquals(1, ours._orient, "l'ours fonce vers le loup à l'EST (cap 1)");
    }

    @Test
    void oursDevoreLeLoupAuContact() {
        WorldOfCells world = AgentTestSupport.buildWorld();
        int cx = 25, cy = 25;
        AgentTestSupport.flattenLandArea(world, cx, cy, 4);

        Ours ours = new Ours(cx, cy, world);
        ours.energie = 100;   // affamé → dévore
        world.ours.add(ours);
        world.agents.add(ours);
        world.uniqueDynamicObjects.add(ours);

        Loup loup = new Loup(cx, cy, world);   // même cellule
        world.loups.add(loup);
        world.agents.add(loup);
        world.uniqueDynamicObjects.add(loup);

        int before = ours.energie;
        Percept p = Perception.sense(ours, world, null, world.loups);
        ours.postMove(p);
        assertFalse(loup._alive, "l'ours dévore le loup présent sur sa cellule");
        assertTrue(ours.energie > before, "l'ours gagne de l'énergie en dévorant");
        assertTrue(ours.energie <= 100 + ours.energieD / 2, "gain plafonné à energieD/2");
    }

    @Test
    void leLoupCraintLOurs() {
        WorldOfCells world = AgentTestSupport.buildWorld();
        int cx = 25, cy = 25;
        AgentTestSupport.flattenLandArea(world, cx, cy, 12);

        Loup loup = new Loup(cx, cy, world);
        loup.energie = loup.energieD;   // repu : sans menace il se reposerait/errerait
        world.loups.add(loup);
        world.agents.add(loup);
        world.uniqueDynamicObjects.add(loup);

        // Ours menaçant plein NORD, dans la vision du loup.
        Ours ours = new Ours(cx, (cy - 4 + world.getHeight()) % world.getHeight(), world);
        world.ours.add(ours);
        world.agents.add(ours);
        world.uniqueDynamicObjects.add(ours);

        Percept p = Perception.sense(loup, world, loup_predators(world), world.moutons);
        assertTrue(p.predatorVisible(), "le loup perçoit l'ours comme une menace");
        assertEquals(AgentState.FLEE_PREDATOR, loup.decideState(p),
                "le loup fuit l'ours");
        loup.applyState(AgentState.FLEE_PREDATOR, p);
        assertEquals(2, loup._orient, "le loup fuit vers le SUD, à l'opposé de l'ours au NORD");
    }

    /** Reconstruit la liste de menaces vue par le loup (humains + ours), comme
     *  le fait Loup.predators() en interne. */
    private static java.util.List<objects.UniqueDynamicObject> loup_predators(WorldOfCells w) {
        java.util.List<objects.UniqueDynamicObject> l = new java.util.ArrayList<>();
        l.addAll(w.humains);
        l.addAll(w.ours);
        return l;
    }
}
