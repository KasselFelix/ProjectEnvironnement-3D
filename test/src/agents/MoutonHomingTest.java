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
        // Chemin dégagé vers la cible : le ralliement en vue suit alors le cap exact
        // (le contournement/repli ne s'active qu'en cas d'obstacle).
        for (int xx = 19; xx <= 24; xx++)
            for (int yy = 19; yy <= 21; yy++) {
                world.setCellHeight(xx, yy, 1.0);
                world.setForestCAValue(xx, yy, 0);
            }
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

    /**
     * RÉGRESSION (même cause racine que le loup) : un mouton isolé qui, la nuit,
     * rentre vers un lieu sûr INATTEIGNABLE (muré par un mur d'arbres dans son
     * champ de vision, AUCUNE eau) ne doit PAS rester à osciller au pied de
     * l'obstacle. Le ralliement HOME doit constater l'absence de progrès et CÉDER
     * à l'errance (anti-pin), au lieu de re-pointer le lieu sûr à chaque tick.
     *
     * <p>Avant correctif : HOME re-cible le lieu sûr en boucle → le mouton bat le
     * mur (poignée de cases). Après : il ratisse le terrain.</p>
     */
    @Test
    void moutonNeBloquePasSurLieuSurInaccessible() {
        // Moyenne sur plusieurs essais : le pas réel passe par le repli ALÉATOIRE de
        // Locomotion (Math.random global) → un run isolé est bruité. Le bug (gel /
        // oscillation) donne ~4 cases distinctes ; corrigé, le mouton en couvre
        // largement plus. On moyenne pour une assertion robuste, pas flaky.
        long totalDistinct = 0; int trials = 10; int worst = Integer.MAX_VALUE;
        for (int t = 0; t < trials; t++) {
            WorldOfCells w = AgentTestSupport.buildWorld();
            int W = w.getWidth(), H = w.getHeight();
            for (int x = 0; x < W; x++)
                for (int y = 0; y < H; y++) {
                    w.setCellHeight(x, y, 1.0);                              // TOUT terre
                    w.setForestCAValue(x, y, (x >= 28 && x <= 31) ? 1 : 0); // mur d'arbres
                    w.setGrassCAValue(x, y, 0);
                }

            Mouton m = new Mouton(24, 25, w);
            m.energie = m.energieMAX * 0.7;
            m.memory.remember(MemoryKind.SAFE_PLACE, 40, 25);              // lieu sûr derrière le mur
            w.moutons.add(m); w.agents.add(m); w.uniqueDynamicObjects.add(m); // seul → HOME (pas de troupeau)

            java.util.Set<Long> distinct = new java.util.HashSet<>();
            for (int i = 0; i < 200; i++) {
                w.setJour(0);                                              // NUIT → HOME
                m.step();
                distinct.add(((long) m.x << 20) | m.y);
            }
            assertEquals(AgentState.HOME, m.currentState,
                    "isolé la nuit + lieu sûr connu → état HOME");
            totalDistinct += distinct.size();
            worst = Math.min(worst, distinct.size());
        }
        long avg = totalDistinct / trials;
        assertTrue(avg >= 15,
                "Le mouton ne doit pas osciller au pied d'un lieu sûr inatteignable "
                + "(couverture moyenne = " + avg + " cases ; symptôme du bug ≈ 4).");
        assertTrue(worst >= 8,
                "Même au pire, le mouton ne reste pas figé (couverture min = " + worst + ").");
    }
}
