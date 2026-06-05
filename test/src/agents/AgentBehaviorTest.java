package agents;

import agents.ai.AgentState;
import org.junit.jupiter.api.Test;
import worlds.WorldOfCells;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Deterministic FSM behaviour tests for Loup / Mouton / Humain.
 *
 * Strategy: build a real WorldOfCells (no GL, no random agent spawn), force
 * terrain cells with setCellHeight / setForestCAValue so every scenario is
 * RNG-independent, then call agent.step() directly.  Iteration stays at 0,
 * so isMyTurn() always passes (0 % n == 0 for any n).
 */
class AgentBehaviorTest {

    // -----------------------------------------------------------------------
    // Test 1 — mouton flees predator that is North-adjacent
    // -----------------------------------------------------------------------

    /**
     * Place a Mouton at (cx,cy) and a Loup at (cx, cy-1) [North].
     * After one step the Mouton must:
     *   - have entered FLEE_PREDATOR state, and
     *   - be facing South (orient=2), i.e. the direction opposite North.
     */
    @Test
    void moutonFuitLoupAdjacent() {
        WorldOfCells world = AgentTestSupport.buildWorld();
        int cx = 20, cy = 20;

        // Force land on the 3×3 neighbourhood AND clear forest: the flee logic
        // now avoids both water and forest, so the South escape must be clear
        // land for the sheep to flee straight South (opposite the predator).
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                world.setCellHeight(cx + dx, cy + dy, 1.0);
                world.setForestCAValue(cx + dx, cy + dy, 0);
            }
        }

        world.setJour(1);

        Mouton m = new Mouton(cx, cy, world);
        world.moutons.add(m);
        world.agents.add(m);
        world.uniqueDynamicObjects.add(m);

        // Predator is one cell North: (cx, cy-1).
        Loup l = new Loup(cx, cy - 1, world);
        world.loups.add(l);
        world.agents.add(l);
        world.uniqueDynamicObjects.add(l);

        m.step();

        assertEquals(AgentState.FLEE_PREDATOR, m.currentState,
                "Mouton must enter FLEE_PREDATOR when a Loup is North-adjacent.");
        assertEquals(2, m._orient,
                "Mouton must face South (orient=2) to flee a North predator.");
    }

    // -----------------------------------------------------------------------
    // Test 2 — hungry Loup hunts an East-adjacent Mouton
    // -----------------------------------------------------------------------

    /**
     * Place a Loup at (cx,cy) with energie well below the hunt threshold,
     * and a Mouton at (cx+1, cy) [East].  After one step the Loup must be
     * in HUNT state.
     */
    @Test
    void loupAffameChasseMoutonAdjacent() {
        WorldOfCells world = AgentTestSupport.buildWorld();
        int cx = 20, cy = 20;

        // Force land on the 3×3 neighbourhood.
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                world.setCellHeight(cx + dx, cy + dy, 1.0);
            }
        }

        world.setJour(1);

        Loup l = new Loup(cx, cy, world);
        l.energie = 100; // 100 < energieD(500)*0.7 = 350 → hungry
        world.loups.add(l);
        world.agents.add(l);
        world.uniqueDynamicObjects.add(l);

        Mouton m = new Mouton(cx + 1, cy, world);
        world.moutons.add(m);
        world.agents.add(m);
        world.uniqueDynamicObjects.add(m);

        l.step();

        assertEquals(AgentState.HUNT, l.currentState,
                "Hungry Loup with East-adjacent prey must enter HUNT state.");
    }

    // -----------------------------------------------------------------------
    // Test 3 — WANDER spiral increments deterministically
    // -----------------------------------------------------------------------

    /**
     * Un loup AFFAMÉ sans proie en vue entre en SEARCH et balaie en spirale.
     * On vérifie le compteur de spirale (spiralStep / spiralPeriod) et le
     * changement d'orientation, tick par tick.
     *
     * Mécanique de spirale (Loup.spiralSearch) :
     *   Tick 1: spiralStep(0) != spiralPeriod(1) → spiralStep++ → spiralStep=1, _orient reste 0.
     *   Tick 2: spiralStep(1) == spiralPeriod(1) → _orient=(0+1)%4=1,
     *           spiralPeriod += vision/2 = 1+5 = 6, spiralStep=0.
     */
    @Test
    void loupAffameChercheEnSpirale() {
        WorldOfCells world = AgentTestSupport.buildWorld();
        int cx = 25, cy = 25;

        // Force a 5×5 land block and clear forest/lava there.
        for (int dx = -2; dx <= 2; dx++) {
            for (int dy = -2; dy <= 2; dy++) {
                world.setCellHeight(cx + dx, cy + dy, 1.0);
                world.setForestCAValue(cx + dx, cy + dy, 0);
            }
        }

        world.setJour(1); // ensure attaqueNuit stays 0

        // No moutons → preyVisible() = false ; affamé → SEARCH (spirale) garanti.
        Loup l = new Loup(cx, cy, world);
        l.energie = 100; // < energieD*0.7 = 350 → affamé → SEARCH
        world.loups.add(l);
        world.agents.add(l);
        world.uniqueDynamicObjects.add(l);

        // Sanity: freshly constructed Loup faces North.
        assertEquals(0, l._orient, "Default orient must be 0 (North).");

        // ── Tick 1 ──
        l.step();
        assertEquals(AgentState.SEARCH, l.currentState,
                "Affamé sans proie → état SEARCH.");
        assertEquals(1, l.mem.spiralStep,
                "After tick 1: spiralStep must be 1 (incremented, no turn yet).");
        assertEquals(0, l._orient,
                "After tick 1: orient must remain 0 (no turn on first SEARCH tick).");

        // ── Tick 2 ──
        l.step();
        assertEquals(1, l._orient,
                "After tick 2: spiralStep==spiralPeriod → turn → orient must be 1 (East).");
        assertEquals(1 + l.vision / 2, l.mem.spiralPeriod,
                "After tick 2: spiralPeriod must be 1 + vision/2.");
    }

    /**
     * Un loup REPU (énergie pleine, de jour, sans proie) flâne : il est en WANDER,
     * pas en SEARCH — il ne balaie donc PAS en spirale (économie d'énergie).
     */
    @Test
    void loupRepuFlaneSansSpirale() {
        WorldOfCells world = AgentTestSupport.buildWorld();
        int cx = 25, cy = 25;
        for (int dx = -2; dx <= 2; dx++)
            for (int dy = -2; dy <= 2; dy++) {
                world.setCellHeight(cx + dx, cy + dy, 1.0);
                world.setForestCAValue(cx + dx, cy + dy, 0);
            }
        world.setJour(1);

        Loup l = new Loup(cx, cy, world); // energie=energieD(500) → repu
        world.loups.add(l);
        world.agents.add(l);
        world.uniqueDynamicObjects.add(l);

        l.step();
        assertEquals(AgentState.WANDER, l.currentState,
                "Repu, de jour, sans proie → WANDER (flânerie), pas SEARCH.");
    }

    // -----------------------------------------------------------------------
    // Test — un agent piloté suit la direction imposée, sans décider lui-même
    // -----------------------------------------------------------------------

    /**
     * Un Mouton piloté (playerControlled) avec un Loup adjacent ne fuit PAS :
     * il avance dans le cap imposé (controlDir). Vérifie le court-circuit FSM
     * tout en gardant l'agent soumis à son environnement.
     */
    @Test
    void agentPiloteSuitLeCapImpose() {
        WorldOfCells world = AgentTestSupport.buildWorld();
        int cx = 20, cy = 20;
        for (int dx = -1; dx <= 1; dx++)
            for (int dy = -1; dy <= 1; dy++) {
                world.setCellHeight(cx + dx, cy + dy, 1.0);
                world.setForestCAValue(cx + dx, cy + dy, 0); // pas d'arbre bloquant
            }
        world.setJour(1);

        Mouton m = new Mouton(cx, cy, world);
        m.playerControlled = true;
        m.controlDir = 1;     // cap Est (facing)
        m.controlDx = 1;      // pas vers l'Est
        m.controlDy = 0;
        world.moutons.add(m);
        world.agents.add(m);
        world.uniqueDynamicObjects.add(m);

        // Loup adjacent au Nord : en mode autonome, le mouton fuirait (orient Sud).
        Loup l = new Loup(cx, cy - 1, world);
        world.loups.add(l);
        world.agents.add(l);
        world.uniqueDynamicObjects.add(l);

        m.step();

        assertEquals(AgentState.CONTROLLED, m.currentState,
                "Un agent piloté est en état CONTROLLED, pas en FLEE_PREDATOR.");
        assertEquals(cx + 1, m.x, "Avance vers l'Est (controlDir=1).");
        assertEquals(cy, m.y, "Pas de déplacement vertical : il ne fuit pas le loup au Nord.");
    }

    /** controlDir = -1 : l'agent piloté reste immobile (aucune touche tenue). */
    @Test
    void agentPiloteImmobileSiAucunCap() {
        WorldOfCells world = AgentTestSupport.buildWorld();
        int cx = 20, cy = 20;
        for (int dx = -1; dx <= 1; dx++)
            for (int dy = -1; dy <= 1; dy++)
                world.setCellHeight(cx + dx, cy + dy, 1.0);
        world.setJour(1);

        Mouton m = new Mouton(cx, cy, world);
        m.playerControlled = true;
        m.controlDir = -1; // aucune direction
        world.moutons.add(m);
        world.agents.add(m);
        world.uniqueDynamicObjects.add(m);

        m.step();

        assertEquals(AgentState.CONTROLLED, m.currentState);
        assertEquals(cx, m.x, "controlDir=-1 → immobile.");
        assertEquals(cy, m.y, "controlDir=-1 → immobile.");
    }

    // -----------------------------------------------------------------------
    // Test 4 — Mouton catches fire from a state-3 forest neighbour (fix I2)
    // -----------------------------------------------------------------------

    /**
     * Surround a Mouton on all 4 cardinal sides with forest fire state 3.
     * After one step the Mouton must have _fireState == 1.
     *
     * This would fail with the old "== 2" check; it passes only if the code
     * uses ForestCA.isTreeOnFire() which covers states 2, 3, and 4.
     */
    @Test
    void moutonPrendFeuDeForetEtat3() {
        WorldOfCells world = AgentTestSupport.buildWorld();
        int cx = 30, cy = 30;

        // Force land on the Mouton's cell.
        world.setCellHeight(cx, cy, 1.0);

        // Set the 8 surrounding cells to forest fire state 3 (all must be land first).
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                if (dx == 0 && dy == 0) continue;
                world.setCellHeight(cx + dx, cy + dy, 1.0);
                world.setForestCAValue(cx + dx, cy + dy, 3);
            }
        }

        // No predators, day cycle.
        world.setJour(1);

        Mouton m = new Mouton(cx, cy, world);
        world.moutons.add(m);
        world.agents.add(m);
        world.uniqueDynamicObjects.add(m);

        assertEquals(0, m._fireState, "fireState must be 0 before the step.");

        m.step();

        assertEquals(1, m._fireState,
                "Mouton must catch fire (_fireState=1) from a state-3 forest "
                + "neighbour — requires ForestCA.isTreeOnFire() not a bare '==2' check.");
    }

    // -----------------------------------------------------------------------
    // Test 5 — Humain on fire moves to adjacent water and is extinguished (fix I1)
    // -----------------------------------------------------------------------

    /**
     * Place a Humain at (cx,cy) on land, with the North neighbour (cx, cy-1)
     * as the only water cell in the vision radius.  Set the Humain on fire.
     * After one step:
     *   - The Humain must have moved North to (cx, cy-1), and
     *   - _fireState must be 0 (extinguished by postTick on water).
     */
    @Test
    void humainEnFeuRejointEauEtSEteint() {
        WorldOfCells world = AgentTestSupport.buildWorld();
        int cx = 15, cy = 15;

        // Force a land disk of radius = vision(10) around (cx,cy) AND clear any
        // forest there (init may have placed trees on cells we flatten — a tree on
        // the path would block the move and make the test flaky).
        for (int dx = -10; dx <= 10; dx++) {
            for (int dy = -10; dy <= 10; dy++) {
                if (dx * dx + dy * dy <= 100) {
                    world.setCellHeight(cx + dx, cy + dy, 1.0);
                    world.setForestCAValue(cx + dx, cy + dy, 0);
                }
            }
        }

        // The unique water cell: one step North (nearest water in any direction).
        world.setCellHeight(cx, cy - 1, -1.0);
        world.setForestCAValue(cx, cy - 1, 0);

        world.setJour(1);

        Humain h = new Humain(cx, cy, world);
        h.setOnFire();
        world.humains.add(h);
        world.agents.add(h);
        world.uniqueDynamicObjects.add(h);

        assertTrue(h.isOnFire(), "Humain must be on fire before the step.");

        h.step();

        assertEquals(cy - 1, h.y,
                "Humain on fire must move North onto the water cell (cy-1).");
        assertEquals(0, h._fireState,
                "Humain must be extinguished (_fireState=0) after landing on water.");
    }

    // -----------------------------------------------------------------------
    // Test — hungry Mouton heads toward the nearest visible grass (SEEK_FOOD)
    // -----------------------------------------------------------------------

    /**
     * Hungry Mouton (energie < 50% of max), no predator, on land, with a single
     * grass tuft 3 cells East. After one step it must:
     *   - enter SEEK_FOOD (no longer random WANDER), and
     *   - face East (orient=1), toward the grass.
     */
    @Test
    void moutonAffameChercheHerbeEnVue() {
        WorldOfCells world = AgentTestSupport.buildWorld();
        int cx = 25, cy = 25;

        // Land disk + clear forest (movement) over the vision radius, and clear
        // ALL grass in the scan square so the only tuft is the one we place.
        for (int dx = -11; dx <= 11; dx++) {
            for (int dy = -11; dy <= 11; dy++) {
                world.setGrassCAValue(cx + dx, cy + dy, 0);
                if (dx * dx + dy * dy <= 100) {
                    world.setCellHeight(cx + dx, cy + dy, 1.0);
                    world.setForestCAValue(cx + dx, cy + dy, 0);
                }
            }
        }
        // Single grass tuft 3 cells East (nearest — and only — food in view).
        world.setGrassCAValue(cx + 3, cy, 1);

        world.setJour(1);

        Mouton m = new Mouton(cx, cy, world);
        m.energie = m.energieMAX * 0.25;   // bien en dessous du seuil de 50%
        world.moutons.add(m);
        world.agents.add(m);
        world.uniqueDynamicObjects.add(m);

        m.step();

        assertEquals(AgentState.SEEK_FOOD, m.currentState,
                "Hungry Mouton with grass in view must enter SEEK_FOOD.");
        assertEquals(1, m._orient,
                "Mouton must face East (orient=1) toward the only grass tuft.");
    }

    // -----------------------------------------------------------------------
    // Test — well-fed Mouton ignores grass and just wanders (threshold guard)
    // -----------------------------------------------------------------------

    /**
     * Same layout as above but the Mouton is well fed (full energy). It must NOT
     * switch to SEEK_FOOD — food seeking only kicks in below 50% energy.
     */
    @Test
    void moutonRepuNeCherchePasLHerbe() {
        WorldOfCells world = AgentTestSupport.buildWorld();
        int cx = 25, cy = 25;

        for (int dx = -11; dx <= 11; dx++) {
            for (int dy = -11; dy <= 11; dy++) {
                world.setGrassCAValue(cx + dx, cy + dy, 0);
                if (dx * dx + dy * dy <= 100) {
                    world.setCellHeight(cx + dx, cy + dy, 1.0);
                    world.setForestCAValue(cx + dx, cy + dy, 0);
                }
            }
        }
        world.setGrassCAValue(cx + 3, cy, 1);

        world.setJour(1);

        Mouton m = new Mouton(cx, cy, world);
        m.energie = m.energieMAX;          // repu : au-dessus du seuil
        world.moutons.add(m);
        world.agents.add(m);
        world.uniqueDynamicObjects.add(m);

        m.step();

        assertNotEquals(AgentState.SEEK_FOOD, m.currentState,
                "Well-fed Mouton must NOT seek food.");
        assertEquals(AgentState.WANDER, m.currentState,
                "Well-fed Mouton with no threat must WANDER.");
    }

    // -----------------------------------------------------------------------
    // Test — energy-gated reproduction: healthy Mouton breeds and pays the cost
    // -----------------------------------------------------------------------

    /**
     * A full-energy Mouton with reproduction forced (Prepro=1) must:
     *   - produce exactly one offspring (moutons grows 1 → 2),
     *   - the lamb is born with reproOffspringRatio × energieMAX (parental
     *     investment, energy conserved),
     *   - the parent pays that cost (loses at least the invested amount).
     */
    @Test
    void moutonRepuSeReproduitEtPaieLeCout() {
        WorldOfCells world = AgentTestSupport.buildWorld();
        int cx = 25, cy = 25;
        for (int dx = -1; dx <= 1; dx++)
            for (int dy = -1; dy <= 1; dy++) {
                world.setCellHeight(cx + dx, cy + dy, 1.0);
                world.setForestCAValue(cx + dx, cy + dy, 0);
            }
        world.setJour(1);

        Mouton m = new Mouton(cx, cy, world);
        m.energie = m.energieMAX;          // plein → au-dessus du seuil 60%
        m.Prepro  = 1.0;                   // reproduction forcée ce tick
        world.moutons.add(m);
        world.agents.add(m);
        world.uniqueDynamicObjects.add(m);

        double expectedLamb = m.energieMAX * m.reproOffspringRatio;
        double energyBefore = m.energie;

        m.step();

        assertEquals(2, world.moutons.size(),
                "Healthy Mouton with Prepro=1 must produce exactly one lamb.");
        Mouton lamb = world.moutons.get(1);
        assertEquals(expectedLamb, lamb.energie, 0.001,
                "Lamb must be born with reproOffspringRatio × energieMAX.");
        assertTrue(m.energie <= energyBefore - expectedLamb + 0.001,
                "Parent must pay the reproduction cost (energy invested in lamb).");
    }

    // -----------------------------------------------------------------------
    // Test — energy-gated reproduction: starving Mouton cannot breed
    // -----------------------------------------------------------------------

    /**
     * A low-energy Mouton (below the 60% threshold) must NOT reproduce even
     * with Prepro forced to 1 — energy gating blocks the birth.
     */
    @Test
    void moutonAffameNeSeReproduitPas() {
        WorldOfCells world = AgentTestSupport.buildWorld();
        int cx = 25, cy = 25;
        for (int dx = -1; dx <= 1; dx++)
            for (int dy = -1; dy <= 1; dy++) {
                world.setCellHeight(cx + dx, cy + dy, 1.0);
                world.setForestCAValue(cx + dx, cy + dy, 0);
            }
        world.setJour(1);

        Mouton m = new Mouton(cx, cy, world);
        m.energie = m.energieMAX * 0.30;   // sous le seuil mouton (reproEnergyThreshold)
        m.Prepro  = 1.0;
        world.moutons.add(m);
        world.agents.add(m);
        world.uniqueDynamicObjects.add(m);

        m.step();

        assertEquals(1, world.moutons.size(),
                "Starving Mouton (below energy threshold) must NOT reproduce.");
    }

    // -----------------------------------------------------------------------
    // Test — per-species repro thresholds: at 50% energy the sheep breeds,
    //        the wolf doesn't (sheep threshold 40% < wolf threshold 60%)
    // -----------------------------------------------------------------------

    /**
     * Same relative energy (50% of max) for both species, reproduction forced
     * (Prepro=1). The Mouton (threshold 40%) must reproduce; the Loup
     * (threshold 60%) must NOT — proving the per-species rebalance that lets
     * prey out-breed predators.
     */
    @Test
    void seuilsReproParEspece() {
        // --- sheep at 50% breeds ---
        WorldOfCells w1 = AgentTestSupport.buildWorld();
        int cx = 22, cy = 22;
        for (int dx = -1; dx <= 1; dx++)
            for (int dy = -1; dy <= 1; dy++) {
                w1.setCellHeight(cx + dx, cy + dy, 1.0);
                w1.setForestCAValue(cx + dx, cy + dy, 0);
            }
        w1.setJour(1);
        Mouton m = new Mouton(cx, cy, w1);
        m.energie = m.energieMAX * 0.50;     // 50% > seuil mouton (40%)
        m.Prepro  = 1.0;
        w1.moutons.add(m); w1.agents.add(m); w1.uniqueDynamicObjects.add(m);
        m.step();
        assertEquals(2, w1.moutons.size(),
                "Mouton à 50% (≥ seuil 40%) doit se reproduire.");

        // --- wolf at 50% does NOT breed ---
        WorldOfCells w2 = AgentTestSupport.buildWorld();
        for (int dx = -1; dx <= 1; dx++)
            for (int dy = -1; dy <= 1; dy++) {
                w2.setCellHeight(cx + dx, cy + dy, 1.0);
                w2.setForestCAValue(cx + dx, cy + dy, 0);
            }
        w2.setJour(1);
        Loup l = new Loup(cx, cy, w2);
        l.energie = (int) (l.energieD * 0.50);   // 50% < seuil loup (60%)
        l.Prepro  = 1.0;
        w2.loups.add(l); w2.agents.add(l); w2.uniqueDynamicObjects.add(l);
        l.step();
        assertEquals(1, w2.loups.size(),
                "Loup à 50% (< seuil 60%) ne doit PAS se reproduire.");
    }

    // -----------------------------------------------------------------------
    // Test — water swim penalty: wolf out-swims sheep (realistic, not a refuge)
    // -----------------------------------------------------------------------

    /**
     * After one step spent in water, the effective speed (vitesse) must reflect
     * the species swim factor: Loup keeps 60% of vcourse, Mouton only 25%. The
     * key invariant: a wolf in water is FASTER than a sheep in water, so fleeing
     * into water is no longer an escape for the prey.
     */
    @Test
    void vitesseDansLEauFavoriseLeLoup() {
        WorldOfCells world = AgentTestSupport.buildWorld();
        int cx = 30, cy = 30;
        // Whole neighbourhood is water; clear forest just in case.
        for (int dx = -1; dx <= 1; dx++)
            for (int dy = -1; dy <= 1; dy++) {
                world.setCellHeight(cx + dx, cy + dy, -1.0);
                world.setForestCAValue(cx + dx, cy + dy, 0);
            }
        world.setJour(1);

        Loup l = new Loup(cx, cy, world);
        l.energie = l.energieD;            // évite les pénalités basse-énergie
        world.loups.add(l);
        world.agents.add(l);
        world.uniqueDynamicObjects.add(l);

        Mouton m = new Mouton(cx, cy, world);
        m.energie = (int) m.energieMAX;
        world.moutons.add(m);
        world.agents.add(m);
        world.uniqueDynamicObjects.add(m);

        l.step();
        m.step();

        // Loup : vcourse(13.5) × 0.6 = 8.1 ; Mouton : vcourse(9) × 0.25 = 2.25.
        assertEquals(l.vcourse * l.swimFactor, l.vitesse, 0.001,
                "Loup dans l'eau doit nager à vcourse × swimFactor.");
        assertEquals(m.vcourse * m.swimFactor, m.vitesse, 0.001,
                "Mouton dans l'eau doit nager à vcourse × swimFactor.");
        assertTrue(l.vitesse > m.vitesse,
                "Dans l'eau, le loup doit rester plus rapide que le mouton.");
    }

    // -----------------------------------------------------------------------
    // Test — fleeing Mouton avoids water when a land escape exists
    // -----------------------------------------------------------------------

    /**
     * Predator North of the Mouton, so the "straight" flee direction is South.
     * We make the South cell WATER but keep East/West/North as land. The sheep
     * must NOT pick South (into water) — it must flee laterally onto land
     * (East or West). It only enters water if cornered, which isn't the case.
     */
    @Test
    void moutonEnFuiteEviteLEau() {
        WorldOfCells world = AgentTestSupport.buildWorld();
        int cx = 35, cy = 35;
        // Land everywhere around, no forest, then carve water due South.
        for (int dx = -2; dx <= 2; dx++)
            for (int dy = -2; dy <= 2; dy++) {
                world.setCellHeight(cx + dx, cy + dy, 1.0);
                world.setForestCAValue(cx + dx, cy + dy, 0);
            }
        world.setCellHeight(cx, cy + 1, -1.0);   // Sud = eau (la fuite "tout droit")

        world.setJour(1);

        Mouton m = new Mouton(cx, cy, world);
        world.moutons.add(m);
        world.agents.add(m);
        world.uniqueDynamicObjects.add(m);

        // Predator one cell North → flee-straight = South, which is water.
        Loup l = new Loup(cx, cy - 1, world);
        world.loups.add(l);
        world.agents.add(l);
        world.uniqueDynamicObjects.add(l);

        m.step();

        assertEquals(AgentState.FLEE_PREDATOR, m.currentState,
                "Mouton must be fleeing the North predator.");
        assertNotEquals(2, m._orient,
                "Mouton must NOT flee South into water when land escapes exist.");
        assertTrue(m._orient == 1 || m._orient == 3,
                "Mouton must flee laterally onto land (East or West).");
        assertTrue(world.getCellHeight(m.x, m.y) >= 0,
                "Mouton must not have moved onto a water cell.");
    }

    // -----------------------------------------------------------------------
    // Test — a well-fed wolf does NOT eat (no surplus killing), hungry one does
    // -----------------------------------------------------------------------

    /**
     * Consumption is gated by hunger (energie < HUNGER_RATIO × energieD), not by
     * 90% as before. We surround the wolf with sheep on its cell + 4 cardinals
     * (so it lands on prey whichever way it moves).
     *   - A wolf at 80% energy (above the 70% hunger threshold) must kill NONE
     *     (it only scares the herd — leaves prey for the pack).
     *   - A wolf at 40% energy (hungry) must kill exactly one.
     */
    @Test
    void loupRepuNeMangePasMaisAffameOui() {
        // --- well-fed wolf: eats nothing ---
        WorldOfCells world = AgentTestSupport.buildWorld();
        int cx = 12, cy = 12;
        for (int dx = -1; dx <= 1; dx++)
            for (int dy = -1; dy <= 1; dy++) {
                world.setCellHeight(cx + dx, cy + dy, 1.0);
                world.setForestCAValue(cx + dx, cy + dy, 0);
            }
        world.setJour(1);

        Loup fed = new Loup(cx, cy, world);
        fed.energie = (int) (fed.energieD * 0.80);   // repu : au-dessus de HUNGER_RATIO (0.7)
        world.loups.add(fed);
        world.agents.add(fed);
        world.uniqueDynamicObjects.add(fed);

        Mouton[] herd = addSheepCross(world, cx, cy);

        fed.step();

        int dead = 0;
        for (Mouton s : herd) if (!s._alive) dead++;
        assertEquals(0, dead,
                "Un loup repu (≥70%) ne doit tuer AUCUN mouton, même au contact.");

        // --- hungry wolf: eats exactly one ---
        WorldOfCells world2 = AgentTestSupport.buildWorld();
        for (int dx = -1; dx <= 1; dx++)
            for (int dy = -1; dy <= 1; dy++) {
                world2.setCellHeight(cx + dx, cy + dy, 1.0);
                world2.setForestCAValue(cx + dx, cy + dy, 0);
            }
        world2.setJour(1);

        Loup hungry = new Loup(cx, cy, world2);
        hungry.energie = (int) (hungry.energieD * 0.40);   // affamé : sous le seuil
        world2.loups.add(hungry);
        world2.agents.add(hungry);
        world2.uniqueDynamicObjects.add(hungry);

        Mouton[] herd2 = addSheepCross(world2, cx, cy);

        hungry.step();

        int dead2 = 0;
        for (Mouton s : herd2) if (!s._alive) dead2++;
        assertEquals(1, dead2,
                "Un loup affamé (<70%) doit manger exactement un mouton.");
    }

    // -----------------------------------------------------------------------
    // Test — hunting speed: hungry wolf sprints, well-fed (night) wolf only trots
    // -----------------------------------------------------------------------

    /**
     * In HUNT, the wolf's speed depends on hunger: a hungry wolf (< HUNGER_RATIO)
     * sprints (vcourse) to catch and kill; a well-fed wolf (only hunting because
     * it's night) trots (vtrot) to scare/disperse the herd without burning out.
     * We call applyState(HUNT, ·) directly so no night state is needed.
     */
    @Test
    void loupRepuChasseAuTrotAffameAuSprint() {
        WorldOfCells world = AgentTestSupport.buildWorld();
        int cx = 18, cy = 18;
        for (int dx = -1; dx <= 1; dx++)
            for (int dy = -1; dy <= 1; dy++) {
                world.setCellHeight(cx + dx, cy + dy, 1.0);
                world.setForestCAValue(cx + dx, cy + dy, 0);
            }
        world.setJour(1);

        Loup l = new Loup(cx, cy, world);
        world.loups.add(l);
        world.agents.add(l);
        world.uniqueDynamicObjects.add(l);

        Mouton prey = new Mouton(cx + 1, cy, world);   // East-adjacent → preyVisible
        world.moutons.add(prey);
        world.agents.add(prey);
        world.uniqueDynamicObjects.add(prey);

        agents.ai.Percept p = agents.ai.Perception.sense(l, world, null, world.moutons);
        assertTrue(p.preyVisible(), "Le mouton adjacent doit être visible (HUNT possible).");

        l.energie = (int) (l.energieD * 0.80);   // repu → trot
        l.applyState(AgentState.HUNT, p);
        assertEquals(l.vtrot, l.vitesse, 0.001,
                "Loup repu en HUNT (nuit) doit trotter (vtrot), pas sprinter.");

        l.energie = (int) (l.energieD * 0.40);   // affamé → sprint
        l.applyState(AgentState.HUNT, p);
        assertEquals(l.vcourse, l.vitesse, 0.001,
                "Loup affamé en HUNT doit sprinter (vcourse).");
    }

    /** Place un mouton sur (cx,cy) + les 4 cases cardinales ; renvoie le troupeau. */
    private static Mouton[] addSheepCross(WorldOfCells world, int cx, int cy) {
        int[][] pos = { {cx, cy}, {cx, cy - 1}, {cx, cy + 1}, {cx - 1, cy}, {cx + 1, cy} };
        Mouton[] herd = new Mouton[pos.length];
        for (int i = 0; i < pos.length; i++) {
            herd[i] = new Mouton(pos[i][0], pos[i][1], world);
            world.moutons.add(herd[i]);
            world.agents.add(herd[i]);
            world.uniqueDynamicObjects.add(herd[i]);
        }
        return herd;
    }
}
