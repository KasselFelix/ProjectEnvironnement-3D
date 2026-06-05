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
 * Phase E — sens de l'orientation du Mouton (cf. docs/evolution.txt § 9) : la
 * nuit, un mouton ISOLÉ (sans troupeau à suivre) qui connaît un lieu sûr y
 * rentre, en naviguant à l'estime (cap perturbé par l'axe Orientation hors vue).
 */
class MoutonHomingTest {

    /** En passant près de la bergerie, le mouton la mémorise comme lieu sûr. */
    @Test
    void moutonApprendLaBergerieCommeLieuSur() {
        WorldOfCells world = AgentTestSupport.buildWorld();
        int bx = world.getBergerieX(), by = world.getBergerieY();
        Mouton m = new Mouton(bx, by, world);            // sur la bergerie
        world.moutons.add(m); world.agents.add(m);

        m.learnNearbyLandmarks();

        assertTrue(m.memory.contains(MemoryKind.SAFE_PLACE, bx, by),
                "le mouton doit mémoriser la bergerie comme lieu sûr");
    }

    /** La nuit, un mouton ISOLÉ qui connaît un lieu sûr rentre au bercail (HOME). */
    @Test
    void moutonIsoleRentreAuBercailLaNuit() {
        WorldOfCells world = AgentTestSupport.buildWorld();
        int[] land = AgentTestSupport.findLandCell(world, 15, 15);
        world.setJour(0);                                 // NUIT

        Mouton m = new Mouton(land[0], land[1], world);
        m.energie = m.energieMAX * 0.7;
        m.memory.remember(MemoryKind.SAFE_PLACE, 45, 45); // lieu sûr connu, loin
        world.moutons.add(m); world.agents.add(m);        // seul → pas de troupeau

        Percept p = Perception.sense(m, world, world.loups, null);
        assertEquals(AgentState.HOME, m.decideState(p),
                "isolé la nuit + lieu sûr connu → rentre au bercail");
    }

    /** Lieu sûr HORS DE VUE + BON_SENS d'orientation : le mouton vise le cap
     *  exact vers le lieu sûr (l'erreur d'orientation est nulle). */
    @Test
    void moutonBonSensViseExactementLeLieuSurLointain() {
        WorldOfCells world = AgentTestSupport.buildWorld();
        Mouton m = new Mouton(10, 10, world);
        m.genome.set(Axis.ORIENTATION, Pole.POSITIVE);    // BON_SENS
        int tx = 40, ty = 10;                             // hors vue (>vision)
        m.memory.remember(MemoryKind.SAFE_PLACE, tx, ty);
        world.moutons.add(m); world.agents.add(m);

        Percept p = Perception.sense(m, world, world.loups, null);
        m.applyState(AgentState.HOME, p);

        assertEquals(Perception.dirToCell(m, world, tx, ty), m._orient,
                "BON_SENS → cap exact vers le lieu sûr lointain");
    }

    /** Lieu sûr EN VUE : le cap est exact même pour un DÉSORIENTÉ (l'axe
     *  Orientation n'agit qu'en navigation longue distance, § 9). */
    @Test
    void lieuSurEnVueIgnoreLAxeOrientation() {
        WorldOfCells world = AgentTestSupport.buildWorld();
        Mouton m = new Mouton(20, 20, world);
        m.genome.set(Axis.ORIENTATION, Pole.NEGATIVE);    // DÉSORIENTÉ
        int tx = 23, ty = 20;                             // dans la vision (<=10)
        m.memory.remember(MemoryKind.SAFE_PLACE, tx, ty);
        world.moutons.add(m); world.agents.add(m);

        Percept p = Perception.sense(m, world, world.loups, null);
        m.applyState(AgentState.HOME, p);

        assertEquals(Perception.dirToCell(m, world, tx, ty), m._orient,
                "cible en vue → cap exact, l'axe Orientation n'agit pas");
    }
}
