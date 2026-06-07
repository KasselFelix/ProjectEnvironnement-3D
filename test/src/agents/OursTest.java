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

    /** Parité avec le loup : SEEK_LAND « commit » — au milieu d'un bras d'eau avec
     *  une côte en vue devant, l'ours termine la traversée au lieu de rebrousser. */
    @Test
    void oursTermineSaTraverseeAuLieuDeRebrousser() {
        WorldOfCells world = AgentTestSupport.buildWorld();
        int cx = 25, cy = 25;
        for (int x = 0; x < world.getWidth(); x++)
            for (int y = 0; y < world.getHeight(); y++) {
                world.setCellHeight(x, y, (x >= 25 && x <= 27) ? -1.0 : 1.0);
                world.setForestCAValue(x, y, 0);
            }
        world.setJour(1);

        Ours o = new Ours(cx, cy, world);          // dans l'eau, rive Ouest proche, Est en vue
        o.energie = o.energieD;                    // repu → SEEK_LAND pur en eau
        o._orient = 1;                             // vers la rive Est
        world.ours.add(o); world.agents.add(o); world.uniqueDynamicObjects.add(o);

        Percept p = Perception.sense(o, world, null, world.loups);
        assertEquals(AgentState.SEEK_LAND, o.decideState(p), "dans l'eau → SEEK_LAND");
        o.applyState(AgentState.SEEK_LAND, p);
        assertEquals(1, o._orient, "côte en vue à l'Est → l'ours continue (commit), pas de demi-tour");
    }

    /** Parité : persistance de piste — l'ours qui perd le loup de vue continue vers
     *  sa dernière position connue quelques ticks, puis abandonne (→ SEARCH). */
    @Test
    void oursPoursuitLoupPerduDeVuePuisAbandonne() {
        WorldOfCells world = AgentTestSupport.buildWorld();
        int cx = 25, cy = 25;
        AgentTestSupport.flattenLandArea(world, cx, cy, 13);
        world.setJour(1);

        Ours o = new Ours(cx, cy, world);
        o.energie = 100;                           // affamé → chasse
        world.ours.add(o); world.agents.add(o); world.uniqueDynamicObjects.add(o);

        Loup prey = new Loup(cx + 4, cy, world);   // proie à l'EST, en vue
        world.loups.add(prey); world.agents.add(prey); world.uniqueDynamicObjects.add(prey);

        Percept p1 = Perception.sense(o, world, null, world.loups);
        assertTrue(p1.preyVisible());
        o.currentState = o.decideState(p1);
        assertEquals(AgentState.HUNT, o.currentState);
        o.applyState(o.currentState, p1);          // enregistre la piste

        prey.x = cx; prey.y = (cy + 24) % world.getHeight();   // hors vue
        Percept p2 = Perception.sense(o, world, null, world.loups);
        assertFalse(p2.preyVisible());
        assertEquals(AgentState.HUNT, o.decideState(p2), "piste fraîche → reste en HUNT");
        o.applyState(AgentState.HUNT, p2);
        assertEquals(1, o._orient, "fonce vers la dernière position connue (Est)");

        for (int i = 0; i < 30; i++) o.applyState(AgentState.HUNT, p2);
        assertEquals(AgentState.SEARCH, o.decideState(p2), "piste épuisée → SEARCH");
    }

    /** Parité (garde-fou anti-régression) : en recherche sur terrain à arbres épars,
     *  l'ours ratisse sans se figer ni tourner en rond (le nouveau contournement ne
     *  régresse pas — un ours figé couvrirait une poignée de cases). */
    @Test
    void oursEnRechercheCouvreLeTerrainSansSeFiger() {
        long total = 0; int n = 0; int worst = Integer.MAX_VALUE;
        for (long seed = 1; seed <= 30; seed++) {
            WorldOfCells w = AgentTestSupport.buildWorld();
            int W = w.getWidth(), H = w.getHeight();
            java.util.Random r = new java.util.Random(seed);
            for (int x = 0; x < W; x++)
                for (int y = 0; y < H; y++) {
                    w.setCellHeight(x, y, 1.0);
                    w.setForestCAValue(x, y, r.nextDouble() < 0.18 ? 1 : 0);
                }
            int sx = -1, sy = -1;
            outer:
            for (int rad = 0; rad < 20; rad++)
                for (int dx = -rad; dx <= rad; dx++)
                    for (int dy = -rad; dy <= rad; dy++) {
                        int x = ((25 + dx) % W + W) % W, y = ((25 + dy) % H + H) % H;
                        if (w.getForestCAValue(x, y) == 0) { sx = x; sy = y; break outer; }
                    }
            if (sx < 0) continue;

            Ours o = new Ours(sx, sy, w);
            o.energie = (int) (o.energieD * 0.5);   // affamé → SEARCH (pas de proie présente)
            o.mem.resetSpiral();
            w.ours.add(o); w.agents.add(o); w.uniqueDynamicObjects.add(o);

            java.util.Set<Long> distinct = new java.util.HashSet<>();
            for (int i = 0; i < 250; i++) { w.setJour(1); o.step(); distinct.add(((long) o.x << 20) | o.y); }
            total += distinct.size(); n++; worst = Math.min(worst, distinct.size());
        }
        long avg = total / Math.max(1, n);
        assertTrue(avg >= 60, "l'ours doit ratisser largement (couverture moyenne = " + avg + ").");
        assertTrue(worst >= 15, "même au pire, l'ours ne reste pas figé (min = " + worst + ").");
    }

    /** Correctif d'audit (2026-06-07) : l'ours subit desormais des degats de feu
     *  (parite avec Loup/Mouton/Humain) — auparavant il etait immunise. */
    @Test
    void oursSubitLeFeuEtPeutEnMourir() {
        WorldOfCells world = AgentTestSupport.buildWorld();
        int cx = 25, cy = 25;
        AgentTestSupport.flattenLandArea(world, cx, cy, 4);   // terre seche
        world.setJour(1);

        Ours o = new Ours(cx, cy, world);
        o.energie = o.energieD;
        world.ours.add(o); world.agents.add(o); world.uniqueDynamicObjects.add(o);
        o.setOnFire();
        assertTrue(o.isOnFire(), "l'ours est en feu");

        // A l'iteration 0, le drain « horaire » se declenche a chaque postTick
        // (0 % ticksPerGameSecond() == 0) ; energieD/10 par declenchement.
        int before = o.energie;
        o.postTick();
        assertTrue(o.energie < before, "le feu fait perdre de l'energie a l'ours");

        for (int i = 0; i < 12 && o._alive; i++) o.postTick();
        assertFalse(o._alive, "un ours qui brule sans atteindre l'eau finit par mourir");
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
