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
    // Test 1b (L2) — mouton flees an approaching lava flow
    // -----------------------------------------------------------------------

    /**
     * Place a Mouton at (cx,cy) with a LAVA cell two steps East. After one step
     * the Mouton must enter FLEE_LAVA and face West (orient=3), opposite the lava.
     * Lava avoidance outranks every behaviour except being on fire.
     */
    @Test
    void moutonFuitLaLave() {
        WorldOfCells world = AgentTestSupport.buildWorld();
        int cx = 20, cy = 20;

        // Flatten + clear forest around so the westward escape is open land.
        for (int dx = -2; dx <= 2; dx++)
            for (int dy = -2; dy <= 2; dy++) {
                world.setCellHeight(cx + dx, cy + dy, 1.0);
                world.setForestCAValue(cx + dx, cy + dy, 0);
            }

        // Lava flow two cells East (within vision).
        world.pushLayer(cx + 2, cy, objects.Material.LAVA, 1f, 1);

        Mouton m = new Mouton(cx, cy, world);
        world.moutons.add(m);
        world.agents.add(m);
        world.uniqueDynamicObjects.add(m);

        m.step();

        assertEquals(AgentState.FLEE_LAVA, m.currentState,
                "Mouton must enter FLEE_LAVA when a lava flow is in sight.");
        assertEquals(3, m._orient,
                "Mouton must face West (orient=3) to flee lava located East.");
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
     * Un loup AFFAMÉ sans proie en vue entre en SEARCH, et la recherche balaie en
     * SPIRALE CARRÉE EXTENSIBLE : bras de longueurs {@code L, L, 2L, 2L, 3L…}
     * (L = 2·vision+1), pour couvrir la zone sans trou ni recouvrement.
     */
    @Test
    void loupAffameChercheEnSpiraleExtensible() {
        WorldOfCells world = AgentTestSupport.buildWorld();
        int cx = 25, cy = 25;

        // 5×5 de terre dégagée (ni forêt ni lave) autour du loup.
        for (int dx = -2; dx <= 2; dx++) {
            for (int dy = -2; dy <= 2; dy++) {
                world.setCellHeight(cx + dx, cy + dy, 1.0);
                world.setForestCAValue(cx + dx, cy + dy, 0);
            }
        }
        world.setJour(1); // attaqueNuit reste 0

        Loup l = new Loup(cx, cy, world);
        l.energie = 100; // < energieD*0.7 = 350 → affamé → SEARCH
        world.loups.add(l);
        world.agents.add(l);
        world.uniqueDynamicObjects.add(l);

        // Affamé sans proie → état SEARCH.
        l.step();
        assertEquals(AgentState.SEARCH, l.currentState,
                "Affamé sans proie → état SEARCH.");

        // Motif des bras : L, L, 2L, 2L, 3L… spiralSearch fait avancer le CAP VOULU
        // (mem.spiralHeading) ; à chaque virage de cap, la longueur du nouveau bras
        // vaut spiralStepsLeft+1 (posée puis décrémentée une fois).
        final int L = 2 * l.vision + 1;
        l._orient = 0;
        l.mem.resetSpiral();
        java.util.List<Integer> legs = new java.util.ArrayList<>();
        int prevHeading = l.mem.spiralHeading;
        for (int i = 0; i < 60 * L && legs.size() < 6; i++) {
            l.spiralSearch(l.vision);
            if (l.mem.spiralHeading != prevHeading) {     // virage de cap → nouveau bras
                legs.add(l.mem.spiralStepsLeft + 1);
                prevHeading = l.mem.spiralHeading;
            }
        }
        assertTrue(legs.size() >= 5, "Au moins 5 bras balayés.");
        assertEquals(L,     (int) legs.get(0), "Bras 1 = L");
        assertEquals(L,     (int) legs.get(1), "Bras 2 = L");
        assertEquals(2 * L, (int) legs.get(2), "Bras 3 = 2L");
        assertEquals(2 * L, (int) legs.get(3), "Bras 4 = 2L");
        assertEquals(3 * L, (int) legs.get(4), "Bras 5 = 3L");
    }

    /**
     * Anti-entêtement : face à un arbre droit devant, le loup se DÉCALE sur le
     * côté (latéral) au lieu de foncer dedans ou de faire demi-tour. (Bug filmé :
     * allers-retours entre deux arbres.)
     */
    @Test
    void loupContourneArbreParLeCote() {
        WorldOfCells world = AgentTestSupport.buildWorld();
        int cx = 25, cy = 25;
        for (int dx = -2; dx <= 2; dx++)
            for (int dy = -2; dy <= 2; dy++) {
                world.setCellHeight(cx + dx, cy + dy, 1.0);
                world.setForestCAValue(cx + dx, cy + dy, 0);
            }
        world.setForestCAValue(cx + 1, cy, 1);   // arbre PILE à l'est

        Loup l = new Loup(cx, cy, world);
        l._orient = 1;                           // cap est (vers l'arbre)
        l.mem.resetSpiral();
        world.loups.add(l); world.agents.add(l); world.uniqueDynamicObjects.add(l);

        java.util.List<objects.UniqueDynamicObject> none = java.util.Collections.emptyList();
        agents.ai.Percept p = agents.ai.Perception.sense(l, world, none, none);
        l.steerAroundObstacles(p, true, l.vision);

        assertEquals(2, l._orient,
                "Arbre à l'est → décalage latéral (sud), PAS de demi-tour vers l'ouest.");
    }

    /**
     * Esquive d'errance (WANDER, steerAroundObstacles) : impasse est+nord+sud,
     * seule issue ouest → l'agent prend l'ouest (pas de blocage).
     */
    @Test
    void loupSEngageDansLaSortieDImpasse() {
        WorldOfCells world = AgentTestSupport.buildWorld();
        int cx = 25, cy = 25;
        for (int dx = -2; dx <= 2; dx++)
            for (int dy = -2; dy <= 2; dy++) {
                world.setCellHeight(cx + dx, cy + dy, 1.0);
                world.setForestCAValue(cx + dx, cy + dy, 0);
            }
        world.setForestCAValue(cx + 1, cy, 1);   // est
        world.setForestCAValue(cx, cy - 1, 1);   // nord
        world.setForestCAValue(cx, cy + 1, 1);   // sud

        Loup l = new Loup(cx, cy, world);
        l._orient = 1;                           // cap est (impasse)
        l.mem.resetSpiral();
        world.loups.add(l); world.agents.add(l); world.uniqueDynamicObjects.add(l);

        java.util.List<objects.UniqueDynamicObject> none = java.util.Collections.emptyList();
        agents.ai.Percept p = agents.ai.Perception.sense(l, world, none, none);
        l.steerAroundObstacles(p, true, l.vision);

        assertEquals(3, l._orient, "Seule issue = ouest.");
    }

    /**
     * Anti-blocage : face à une impasse (est/nord/sud bloqués par la forêt, seule
     * l'ouest — et les diagonales — ouvertes), le loup affamé en SEARCH ne reste pas
     * coincé : le repli de Locomotion lui fait quitter sa case de départ en quelques
     * pas. (Le contournement n'est plus « calculé » : il émerge du repli aléatoire,
     * approche simple et robuste héritée du code d'origine.)
     */
    @Test
    void loupSpiraleTourneApresImpasse() {
        WorldOfCells world = AgentTestSupport.buildWorld();
        int cx = 25, cy = 25;
        for (int dx = -2; dx <= 2; dx++)
            for (int dy = -2; dy <= 2; dy++) {
                world.setCellHeight(cx + dx, cy + dy, 1.0);
                world.setForestCAValue(cx + dx, cy + dy, 0);
            }
        world.setForestCAValue(cx + 1, cy, 1);   // est
        world.setForestCAValue(cx, cy - 1, 1);   // nord
        world.setForestCAValue(cx, cy + 1, 1);   // sud

        Loup l = new Loup(cx, cy, world);
        l.energie = 100;                         // affamé → SEARCH
        l.mem.resetSpiral();
        world.loups.add(l); world.agents.add(l); world.uniqueDynamicObjects.add(l);

        boolean leftStart = false;
        for (int i = 0; i < 15 && !leftStart; i++) {
            l.step();
            if (l.x != cx || l.y != cy) leftStart = true;
        }
        assertTrue(leftStart, "Le loup doit sortir de l'impasse (ne pas rester bloqué).");
    }

    /**
     * Régression anti-blocage : en SEARCH la NUIT, sur du terrain navigable (arbres
     * épars, espace libre connexe), le loup ne doit jamais RESTER FIGÉ — il ratisse
     * le terrain. La spirale suit son cap et le repli de Locomotion gère les
     * obstacles, si bien que l'agent couvre largement la carte. On mesure la
     * couverture moyenne (nb de cases distinctes) sur de nombreux terrains : un loup
     * figé couvrirait une poignée de cases, donc un seuil élevé garantit l'absence
     * de gel (le bug observé : loups immobiles sur la plage / en forêt).
     */
    @Test
    void loupEnRechercheCouvreLeTerrainSansSeFiger() {
        long totalDistinct = 0; int n = 0; int worstCoverage = Integer.MAX_VALUE;
        for (long seed = 1; seed <= 50; seed++) {
            WorldOfCells w = AgentTestSupport.buildWorld();
            int W = w.getWidth(), H = w.getHeight();
            java.util.Random r = new java.util.Random(seed);
            // Terrain navigable : arbres épars (~18 %) à terre, espace libre connexe.
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

            Loup l = new Loup(sx, sy, w);
            l.energie = l.energieD;                                   // repu : seule la NUIT cherche
            l.mem.resetSpiral();
            w.loups.add(l); w.agents.add(l); w.uniqueDynamicObjects.add(l);

            java.util.Set<Long> distinct = new java.util.HashSet<>();
            for (int i = 0; i < 250; i++) {
                w.setJour(0); l.attaqueNuit = 1;                      // NUIT
                l.step();
                distinct.add(((long) l.x << 20) | l.y);
            }
            totalDistinct += distinct.size(); n++;
            worstCoverage = Math.min(worstCoverage, distinct.size());
        }
        long avg = totalDistinct / Math.max(1, n);
        assertTrue(avg >= 80,
                "Le loup doit ratisser largement, pas rester figé (couverture moyenne = "
                + avg + " cases/250 ticks).");
        assertTrue(worstCoverage >= 20,
                "Même au pire, le loup ne reste pas figé (couverture min = "
                + worstCoverage + " cases/250 ticks).");
    }

    /**
     * RÉGRESSION — cause racine du gel en SEARCH (bug filmé : loups "Cherche proie"
     * figés, énergie pleine, au bord de l'eau ET en pleine forêt). Un loup qui a
     * mémorisé une zone de chasse INATTEIGNABLE (ici derrière un mur d'arbres
     * infranchissable dans son champ de vision, AUCUNE eau) ne doit PAS rester à
     * osciller au pied de l'obstacle : {@code huntHoming} doit constater l'absence
     * de progrès vers la zone et rendre la main à la spirale, qui ratisse le terrain.
     *
     * <p>Avant correctif : huntHoming garde le cap vers la zone à chaque tick et
     * renvoie {@code true} → la spirale ne s'exécute jamais → le loup bat le mur
     * (4 cases distinctes sur 200 ticks). Après : il s'en échappe et couvre la zone.</p>
     */
    @Test
    void loupNeBloquePasSurZoneDeChasseInaccessible() {
        WorldOfCells w = AgentTestSupport.buildWorld();
        int W = w.getWidth(), H = w.getHeight();
        for (int x = 0; x < W; x++)
            for (int y = 0; y < H; y++) {
                w.setCellHeight(x, y, 1.0);                              // TOUT terre (pas d'eau)
                w.setForestCAValue(x, y, (x >= 28 && x <= 31) ? 1 : 0); // mur d'arbres vertical
                w.setGrassCAValue(x, y, 0);
            }

        Loup l = new Loup(24, 25, w);
        l.energie = l.energieD;                                          // repu (≠ famine)
        l.mem.resetSpiral();
        // Zone de chasse mémorisée DE L'AUTRE CÔTÉ du mur, inatteignable en vision.
        l.memory.remember(agents.ai.MemoryKind.HUNTING, 40, 25);
        w.loups.add(l); w.agents.add(l); w.uniqueDynamicObjects.add(l);

        java.util.Set<Long> distinct = new java.util.HashSet<>();
        for (int i = 0; i < 200; i++) {
            w.setJour(0); l.attaqueNuit = 1;                            // NUIT → SEARCH
            l.step();
            distinct.add(((long) l.x << 20) | l.y);
        }
        assertTrue(distinct.size() >= 30,
                "Le loup ne doit pas osciller au pied d'une zone inatteignable "
                + "(cases distinctes = " + distinct.size() + " ; symptôme du bug = 4).");
    }

    /**
     * Persistance de piste : un loup affamé qui PERD sa proie de vue (elle se cache
     * derrière des arbres / sort du champ) ne retombe pas instantanément en SEARCH —
     * il reste en HUNT quelques ticks et fonce vers sa DERNIÈRE position connue, puis
     * abandonne (→ SEARCH) si le contact n'est pas rétabli. Une proie VISIBLE reste
     * toujours prioritaire (opportunisme préservé).
     */
    @Test
    void loupPoursuitProiePerdueDeVuePuisAbandonne() {
        WorldOfCells world = AgentTestSupport.buildWorld();
        int cx = 25, cy = 25;
        for (int dx = -12; dx <= 12; dx++)
            for (int dy = -12; dy <= 12; dy++) {
                world.setCellHeight(cx + dx, cy + dy, 1.0);
                world.setForestCAValue(cx + dx, cy + dy, 0);
            }
        world.setJour(1);

        Loup l = new Loup(cx, cy, world);
        l.energie = 200;                                  // affamé → chasse
        world.loups.add(l); world.agents.add(l); world.uniqueDynamicObjects.add(l);

        // Proie en vue à l'EST (dist 4) : le loup la voit → HUNT, et mémorise la piste.
        Mouton prey = new Mouton(cx + 4, cy, world);
        world.moutons.add(prey); world.agents.add(prey); world.uniqueDynamicObjects.add(prey);

        agents.ai.Percept p1 = agents.ai.Perception.sense(l, world, null, world.moutons);
        assertTrue(p1.preyVisible(), "proie à dist 4 doit être visible");
        l.currentState = l.decideState(p1);
        assertEquals(AgentState.HUNT, l.currentState, "proie en vue → HUNT");
        l.applyState(l.currentState, p1);                 // enregistre la piste (dernière pos connue)

        // La proie disparaît loin (hors vision) → plus visible.
        prey.x = cx; prey.y = (cy + 22) % world.getHeight();
        agents.ai.Percept p2 = agents.ai.Perception.sense(l, world, null, world.moutons);
        assertFalse(p2.preyVisible(), "proie à dist 22 ne doit plus être visible");

        // Persistance : encore HUNT, et le loup vise la dernière position connue (Est).
        assertEquals(AgentState.HUNT, l.decideState(p2),
                "proie perdue de vue mais piste fraîche → reste en HUNT (persistance)");
        l.applyState(AgentState.HUNT, p2);
        assertEquals(1, l._orient,
                "le loup fonce vers la dernière position connue de la proie (Est)");

        // Sans recontact, la piste s'épuise → retour en SEARCH.
        for (int i = 0; i < 30; i++) l.applyState(AgentState.HUNT, p2);
        assertEquals(AgentState.SEARCH, l.decideState(p2),
                "piste épuisée sans recontact → le loup abandonne et repart en SEARCH");
    }

    /**
     * Biais mémoire de la recherche : au sortir d'une chasse (spirale « fraîche »,
     * cap non initialisé), le loup affamé sans proie en vue doit amorcer sa spirale
     * vers la DERNIÈRE POSITION CONNUE de la proie — pas reprendre un cap périmé. Ici
     * la dernière proie a été vue à l'EST : la 1re branche doit partir à l'Est, même
     * si le cap courant pointait à l'Ouest.
     */
    @Test
    void loupRechercheBiaiseeVersDerniereProieConnue() {
        WorldOfCells world = AgentTestSupport.buildWorld();
        int cx = 25, cy = 25;
        for (int dx = -12; dx <= 12; dx++)
            for (int dy = -12; dy <= 12; dy++) {
                world.setCellHeight(cx + dx, cy + dy, 1.0);
                world.setForestCAValue(cx + dx, cy + dy, 0);
            }
        world.setJour(1);

        Loup l = new Loup(cx, cy, world);
        l.energie = 200;                                  // affamé → SEARCH
        world.loups.add(l); world.agents.add(l); world.uniqueDynamicObjects.add(l);

        // État post-chasse simulé : dernière proie connue à l'EST, piste expirée,
        // spirale marquée « à ré-amorcer » (comme le fait HUNT), cap courant à l'OUEST.
        l.lastPreyX = cx + 8; l.lastPreyY = cy; l.pursuitTrackTtl = 0;
        l.mem.spiralHeading = -1;
        l._orient = 3;                                    // Ouest (périmé)

        agents.ai.Percept p = agents.ai.Perception.sense(l, world, null, world.moutons);
        assertFalse(p.preyVisible(), "aucune proie en vue → SEARCH");
        l.applyState(AgentState.SEARCH, p);

        assertEquals(1, l._orient,
                "la spirale doit s'amorcer vers la dernière proie connue (Est), pas le cap périmé (Ouest)");
    }

    /**
     * SEEK_LAND « commit » : un loup au MILIEU d'un bras d'eau, avec une côte EN VUE
     * droit devant, doit TERMINER sa traversée au lieu de rebrousser vers la rive la
     * plus proche (souvent celle qu'il vient de quitter) → anti-oscillation au littoral.
     */
    @Test
    void loupTermineSaTraverseeAuLieuDeRebrousser() {
        WorldOfCells world = AgentTestSupport.buildWorld();
        int cx = 25, cy = 25;
        for (int x = 0; x < world.getWidth(); x++)
            for (int y = 0; y < world.getHeight(); y++) {
                // Bande d'eau verticale x∈[25,27] ; terre ailleurs.
                world.setCellHeight(x, y, (x >= 25 && x <= 27) ? -1.0 : 1.0);
                world.setForestCAValue(x, y, 0);
            }
        world.setJour(1);

        Loup l = new Loup(cx, cy, world);          // DANS l'eau (x=25), rive Ouest à x=24 (proche)
        l.energie = l.energieD;                    // repu : pas enChasse → SEEK_LAND pur
        l._orient = 1;                             // Est = vers la rive lointaine (x=28, en vue)
        world.loups.add(l); world.agents.add(l); world.uniqueDynamicObjects.add(l);

        agents.ai.Percept p = agents.ai.Perception.sense(l, world, null, null);
        assertEquals(AgentState.SEEK_LAND, l.decideState(p), "dans l'eau → SEEK_LAND");
        l.applyState(AgentState.SEEK_LAND, p);
        assertEquals(1, l._orient,
                "côte en vue à l'Est → le loup continue (commit), il ne rebrousse pas vers l'Ouest plus proche");
    }

    /**
     * Intégration (anti-oscillation cross-tick) : un loup en recherche, cap biaisé
     * vers l'autre rive, doit TRAVERSER une rivière étroite (SEARCH plonge → SEEK_LAND
     * « commit » termine la traversée) et finir de l'AUTRE CÔTÉ, au lieu d'osciller au
     * bord de l'eau. C'est le scénario où l'ancienne bascule SEARCH↔SEEK_LAND coinçait.
     */
    @Test
    void loupTraverseUneRiviereEnRecherche() {
        WorldOfCells world = AgentTestSupport.buildWorld();
        for (int x = 0; x < world.getWidth(); x++)
            for (int y = 0; y < world.getHeight(); y++) {
                world.setCellHeight(x, y, (x == 27 || x == 28) ? -1.0 : 1.0);  // rivière 2 cases
                world.setForestCAValue(x, y, 0);
            }
        world.setJour(1);

        Loup l = new Loup(25, 25, world);          // rive Ouest
        l.energie = 200;                           // affamé → SEARCH
        l.lastPreyX = 35; l.lastPreyY = 25;        // souvenir à l'Est → spirale amorcée vers l'Est
        l.mem.spiralHeading = -1;
        world.loups.add(l); world.agents.add(l); world.uniqueDynamicObjects.add(l);

        boolean crossed = false;
        for (int i = 0; i < 80 && !crossed; i++) {
            l.energie = 200;
            l.step();
            if (l.x >= 29) crossed = true;         // a atteint la rive Est (au-delà de la rivière)
        }
        assertTrue(crossed,
                "le loup doit traverser la rivière étroite vers la rive Est, pas osciller au bord");
    }

    /**
     * Contournement de la recherche : face à de l'eau « vers le large » (aucune côte
     * en vue droit devant), le loup LONGE (décalage latéral) ; face à un BRAS d'eau
     * étroit (côte en vue), il TRAVERSE (garde le cap). Même `steerAroundObstacles`
     * que pour un mur d'arbres.
     */
    @Test
    void loupRechercheLongeLeLargeMaisTraverseUnBras() {
        WorldOfCells world = AgentTestSupport.buildWorld();
        int cx = 25, cy = 25;
        for (int dx = -12; dx <= 12; dx++)
            for (int dy = -12; dy <= 12; dy++) {
                world.setCellHeight(cx + dx, cy + dy, 1.0);
                world.setForestCAValue(cx + dx, cy + dy, 0);
            }
        Loup l = new Loup(cx, cy, world);
        world.loups.add(l); world.agents.add(l); world.uniqueDynamicObjects.add(l);

        // (a) « Vers le large » : toute la ligne Est dans la vision = eau → pas de côte.
        for (int r = 1; r <= l.vision; r++) world.setCellHeight(cx + r, cy, -1.0);
        l._orient = 1;
        agents.ai.Percept pa = agents.ai.Perception.sense(l, world, null, null);
        l.steerAroundObstacles(pa, true, l.vision);
        assertTrue(l._orient == 0 || l._orient == 2,
                "eau vers le large à l'Est → le loup LONGE (Nord/Sud), il ne plonge pas vers le large");

        // (b) Bras étroit : eau à cx+1..cx+2, côte à cx+3 (en vue) → il TRAVERSE.
        for (int dx = -12; dx <= 12; dx++)
            for (int dy = -12; dy <= 12; dy++) world.setCellHeight(cx + dx, cy + dy, 1.0);
        world.setCellHeight(cx + 1, cy, -1.0);
        world.setCellHeight(cx + 2, cy, -1.0);   // côte ferme à cx+3
        l._orient = 1;
        agents.ai.Percept pb = agents.ai.Perception.sense(l, world, null, null);
        l.steerAroundObstacles(pb, true, l.vision);
        assertEquals(1, l._orient, "bras d'eau étroit avec côte en vue → le loup TRAVERSE (garde le cap Est)");
    }

    /**
     * SEEK_LAND du MOUTON après une fuite : un mouton sorti dans l'eau pour échapper à
     * un loup ne doit pas regagner la rive du PRÉDATEUR. S'il a un danger mémorisé à
     * l'Ouest et une rive en vue à l'Est, il vise l'Est (loin du danger), pas la rive
     * Ouest plus proche.
     */
    @Test
    void moutonEnFuiteNeRegagnePasLaRiveDuPredateur() {
        WorldOfCells world = AgentTestSupport.buildWorld();
        int cx = 25, cy = 25;
        for (int x = 0; x < world.getWidth(); x++)
            for (int y = 0; y < world.getHeight(); y++) {
                world.setCellHeight(x, y, (x >= 24 && x <= 27) ? -1.0 : 1.0);   // rivière x∈[24,27]
                world.setForestCAValue(x, y, 0);
            }
        world.setJour(1);

        Mouton m = new Mouton(cx, cy, world);      // dans l'eau ; rive Ouest x=23 (proche), Est x=28 (en vue)
        m.energie = m.energieMAX;
        m._orient = 1;
        world.moutons.add(m); world.agents.add(m); world.uniqueDynamicObjects.add(m);
        // Danger mémorisé à l'OUEST (là où était le loup) — aucun prédateur visible ici.
        m.memory.remember(agents.ai.MemoryKind.DANGER, 20, cy);

        agents.ai.Percept p = agents.ai.Perception.sense(m, world, world.loups, null);
        assertEquals(AgentState.SEEK_LAND, m.decideState(p), "dans l'eau, sans prédateur visible → SEEK_LAND");
        m.applyState(AgentState.SEEK_LAND, p);
        assertEquals(1, m._orient,
                "fuite : viser la rive Est (loin du danger mémorisé à l'Ouest), pas la rive Ouest plus proche");
    }

    /**
     * Traque (BFS borné à la vision) : chemin dégagé → pas direct ; arbre entre le
     * loup et la proie → contournement latéral (le loup ne fonce plus dans l'arbre).
     */
    @Test
    void loupContourneVersLaProieAvecBFS() {
        WorldOfCells world = AgentTestSupport.buildWorld();
        int cx = 25, cy = 25;
        for (int dx = -2; dx <= 2; dx++)
            for (int dy = -2; dy <= 2; dy++) {
                world.setCellHeight(cx + dx, cy + dy, 1.0);
                world.setForestCAValue(cx + dx, cy + dy, 0);
            }
        Loup l = new Loup(cx, cy, world);
        world.loups.add(l); world.agents.add(l); world.uniqueDynamicObjects.add(l);

        // Chemin dégagé vers une proie 2 cases à l'est → premier pas = est (1).
        assertEquals(1, l.bfsStepToward(cx + 2, cy, l.vision, true),
                "Chemin dégagé → pas direct vers l'est.");

        // Arbre PILE à l'est, proie derrière → contournement latéral (nord ou sud),
        // jamais dans l'arbre (est) ni à l'opposé (ouest).
        world.setForestCAValue(cx + 1, cy, 1);
        int dir = l.bfsStepToward(cx + 2, cy, l.vision, true);
        assertTrue(dir == 0 || dir == 2,
                "Arbre devant la proie → contournement latéral (nord/sud).");
    }

    /**
     * Évitement en FUITE (dodgeObstacles) : contourne un arbre droit devant, et
     * respecte la contrainte d'eau selon {@code waterPassable} (un amphibie plonge,
     * un craintif de l'eau dévie).
     */
    @Test
    void fuiteContourneObstacleEtRespecteEau() {
        WorldOfCells world = AgentTestSupport.buildWorld();
        int cx = 25, cy = 25;
        for (int dx = -2; dx <= 2; dx++)
            for (int dy = -2; dy <= 2; dy++) {
                world.setCellHeight(cx + dx, cy + dy, 1.0);
                world.setForestCAValue(cx + dx, cy + dy, 0);
            }
        Loup l = new Loup(cx, cy, world);
        world.loups.add(l); world.agents.add(l); world.uniqueDynamicObjects.add(l);

        // (a) Arbre PILE à l'est → contournement latéral (sud).
        world.setForestCAValue(cx + 1, cy, 1);
        l._orient = 1;
        l.dodgeObstacles(true);
        assertEquals(2, l._orient, "Arbre devant en fuite → décalage latéral (sud).");

        // (b) Eau à l'est, eau INTERDITE → dévie (ne plonge pas).
        world.setForestCAValue(cx + 1, cy, 0);
        world.setCellHeight(cx + 1, cy, -1.0);
        l._orient = 1;
        l.dodgeObstacles(false);
        assertEquals(2, l._orient, "Eau interdite devant → dévie latéralement (reste à terre).");

        // (c) Même eau, eau PERMISE (amphibie) → fuit tout droit dans l'eau.
        l._orient = 1;
        l.dodgeObstacles(true);
        assertEquals(1, l._orient, "Eau permise → fuit tout droit dans l'eau.");
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

        Loup l = new Loup(cx, cy, world);
        // Repu mais PAS à pleine énergie (sinon REST) : au-dessus du seuil de
        // chasse (HUNGER_RATIO) → ni SEARCH ni REST → WANDER (flânerie).
        l.energie = (int) (l.energieD * 0.8);
        world.loups.add(l);
        world.agents.add(l);
        world.uniqueDynamicObjects.add(l);

        l.step();
        assertEquals(AgentState.WANDER, l.currentState,
                "Repu (non plein), de jour, sans proie → WANDER (flânerie), pas SEARCH.");
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
        // Au-dessus du seuil de quête (50%) mais PAS à énergie maximale : sinon
        // le mouton se reposerait (état REST) au lieu d'errer. On isole ici le
        // seul invariant du seuil de nourriture (un mouton rassasié ignore l'herbe).
        m.energie = m.energieMAX * 0.8;
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

        // Reproduction sexuée : il faut un partenaire à portée (Prepro=0 → il ne
        // se reproduit pas lui-même, il sert juste de partenaire).
        Mouton partner = new Mouton(cx + 1, cy, world);
        partner.Prepro = 0;
        world.moutons.add(partner);
        world.agents.add(partner);
        world.uniqueDynamicObjects.add(partner);

        double expectedLamb = m.energieMAX * m.reproOffspringRatio;
        double energyBefore = m.energie;

        m.step();

        assertEquals(3, world.moutons.size(),
                "Healthy Mouton with a partner and Prepro=1 must produce exactly one lamb (2 → 3).");
        Mouton lamb = world.moutons.get(2);
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
        // Reproduction sexuée : partenaire à portée requis (Prepro=0).
        Mouton mPartner = new Mouton(cx + 1, cy, w1);
        mPartner.Prepro = 0;
        w1.moutons.add(mPartner); w1.agents.add(mPartner); w1.uniqueDynamicObjects.add(mPartner);
        m.step();
        assertEquals(3, w1.moutons.size(),
                "Mouton à 50% (≥ seuil 40%) avec partenaire doit se reproduire (2 → 3).");

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

    // -----------------------------------------------------------------------
    // Test — Humain berger : suit le CENTRE DE MASSE du troupeau visible
    // -----------------------------------------------------------------------

    /**
     * Un Humain voit un troupeau dispersé : un mouton isolé juste à l'Ouest
     * (le plus PROCHE) et un gros amas à l'Est (le centre de masse). Le berger
     * doit s'orienter vers le centre de masse (Est, orient=1), PAS vers le
     * mouton isolé le plus proche (Ouest, orient=3). Ce contraste prouve qu'on
     * suit bien le barycentre du troupeau et non la cible la plus proche.
     */
    @Test
    void humainBergerSuitLeCentreDeMasseDuTroupeau() {
        WorldOfCells world = AgentTestSupport.buildWorld();
        int cx = 25, cy = 25;
        for (int dx = -11; dx <= 11; dx++)
            for (int dy = -11; dy <= 11; dy++)
                if (dx * dx + dy * dy <= 121) {
                    world.setCellHeight(cx + dx, cy + dy, 1.0);
                    world.setForestCAValue(cx + dx, cy + dy, 0);
                }
        world.setJour(1);

        Humain h = new Humain(cx, cy, world);
        world.humains.add(h);
        world.agents.add(h);
        world.uniqueDynamicObjects.add(h);

        // Mouton isolé à l'Ouest (dist 2) + amas dense à l'Est (barycentre Est).
        int[][] flock = {
                {cx - 2, cy},
                {cx + 6, cy - 1}, {cx + 6, cy + 1},
                {cx + 7, cy},
                {cx + 8, cy - 1}, {cx + 8, cy + 1}
        };
        for (int[] pos : flock) {
            Mouton m = new Mouton(pos[0], pos[1], world);
            world.moutons.add(m);
            world.agents.add(m);
            world.uniqueDynamicObjects.add(m);
        }

        h.step();

        assertEquals(AgentState.HERD, h.currentState,
                "Humain qui voit le troupeau doit passer en HERD (berger).");
        assertEquals(1, h._orient,
                "Le berger doit s'orienter vers le centre de masse (Est), pas "
                + "vers le mouton isolé le plus proche (Ouest).");
    }

    // -----------------------------------------------------------------------
    // Test — Le Loup FUIT l'Humain (le berger protège le troupeau)
    // -----------------------------------------------------------------------

    /**
     * Un Loup affamé a une proie (Mouton) à l'Est — il voudrait chasser. Mais un
     * Humain (berger) est au Nord. La peur de l'Humain doit PRIMER sur la faim :
     * le Loup passe en FLEE_PREDATOR et s'oriente au Sud (à l'opposé du berger),
     * au lieu de chasser le mouton. C'est ce qui donne un sens écologique au
     * berger : un troupeau gardé tient les loups à distance.
     */
    @Test
    void loupFuitHumainMemeAffameAvecProie() {
        WorldOfCells world = AgentTestSupport.buildWorld();
        int cx = 20, cy = 20;
        for (int dx = -1; dx <= 1; dx++)
            for (int dy = -1; dy <= 1; dy++) {
                world.setCellHeight(cx + dx, cy + dy, 1.0);
                world.setForestCAValue(cx + dx, cy + dy, 0);
            }
        world.setJour(1);

        Loup l = new Loup(cx, cy, world);
        l.energie = (int) (l.energieD * 0.40);   // affamé → chasserait normalement
        world.loups.add(l);
        world.agents.add(l);
        world.uniqueDynamicObjects.add(l);

        Mouton prey = new Mouton(cx + 1, cy, world);   // proie à l'Est
        world.moutons.add(prey);
        world.agents.add(prey);
        world.uniqueDynamicObjects.add(prey);

        Humain h = new Humain(cx, cy - 1, world);       // berger au Nord
        world.humains.add(h);
        world.agents.add(h);
        world.uniqueDynamicObjects.add(h);

        l.step();

        assertEquals(AgentState.FLEE_PREDATOR, l.currentState,
                "Un Loup qui voit un Humain doit fuir, même affamé avec une proie en vue.");
        assertEquals(2, l._orient,
                "Le Loup doit fuir au Sud (opposé de l'Humain au Nord).");
    }

    /** Sans Humain en vue, le Loup affamé chasse normalement (garde-fou : la
     *  peur de l'humain ne doit pas casser la chasse quand aucun humain n'est là). */
    @Test
    void loupSansHumainChasseNormalement() {
        WorldOfCells world = AgentTestSupport.buildWorld();
        int cx = 28, cy = 28;
        for (int dx = -1; dx <= 1; dx++)
            for (int dy = -1; dy <= 1; dy++) {
                world.setCellHeight(cx + dx, cy + dy, 1.0);
                world.setForestCAValue(cx + dx, cy + dy, 0);
            }
        world.setJour(1);

        Loup l = new Loup(cx, cy, world);
        l.energie = (int) (l.energieD * 0.40);
        world.loups.add(l);
        world.agents.add(l);
        world.uniqueDynamicObjects.add(l);

        Mouton prey = new Mouton(cx + 1, cy, world);
        world.moutons.add(prey);
        world.agents.add(prey);
        world.uniqueDynamicObjects.add(prey);

        l.step();

        assertEquals(AgentState.HUNT, l.currentState,
                "Sans Humain en vue, le Loup affamé doit chasser (HUNT).");
    }

    /** Un Loup à pleine énergie, de jour, sans proie ni danger, se repose (REST)
     *  et ne se déplace pas — symétrique du Mouton au repos. */
    @Test
    void loupRepuPleineEnergieSeRepose() {
        WorldOfCells world = AgentTestSupport.buildWorld();
        int cx = 15, cy = 15;
        for (int dx = -1; dx <= 1; dx++)
            for (int dy = -1; dy <= 1; dy++) {
                world.setCellHeight(cx + dx, cy + dy, 1.0);
                world.setForestCAValue(cx + dx, cy + dy, 0);
            }
        world.setJour(1);

        Loup l = new Loup(cx, cy, world);   // energie = energieD (pleine) par défaut
        world.loups.add(l);
        world.agents.add(l);
        world.uniqueDynamicObjects.add(l);

        l.step();

        assertEquals(AgentState.REST, l.currentState,
                "Loup à pleine énergie, de jour, sans proie → REST.");
        assertEquals(cx, l.x, "Un loup au repos ne se déplace pas.");
        assertEquals(cy, l.y, "Un loup au repos ne se déplace pas.");
    }

    /**
     * La NUIT, l'Humain rentre au foyer (bergerie) : il passe en HOME et
     * s'oriente vers la cellule de la bergerie. On place l'Humain 5 cases à
     * l'Ouest du foyer → il doit viser l'Est (orient=1).
     */
    @Test
    void humainRentreAuFoyerLaNuit() {
        WorldOfCells world = AgentTestSupport.buildWorld();
        int hx = world.getBergerieX(), hy = world.getBergerieY();
        int ax = (hx - 5 + AgentTestSupport.DX) % AgentTestSupport.DX, ay = hy;

        world.setJour(0);   // NUIT

        Humain h = new Humain(ax, ay, world);
        world.humains.add(h);
        world.agents.add(h);
        world.uniqueDynamicObjects.add(h);

        h.step();

        assertEquals(AgentState.HOME, h.currentState,
                "La nuit, l'Humain rentre au foyer (HOME).");
        assertEquals(1, h._orient,
                "Le foyer est à l'Est (5 cases) → l'Humain s'oriente vers l'Est.");
    }

    /** Sans troupeau en vue, le berger erre (WANDER). */
    @Test
    void humainSansTroupeauEnVueErre() {
        WorldOfCells world = AgentTestSupport.buildWorld();
        int cx = 40, cy = 40;
        for (int dx = -1; dx <= 1; dx++)
            for (int dy = -1; dy <= 1; dy++) {
                world.setCellHeight(cx + dx, cy + dy, 1.0);
                world.setForestCAValue(cx + dx, cy + dy, 0);
            }
        world.setJour(1);

        Humain h = new Humain(cx, cy, world);
        world.humains.add(h);
        world.agents.add(h);
        world.uniqueDynamicObjects.add(h);

        h.step();

        assertEquals(AgentState.WANDER, h.currentState,
                "Sans troupeau en vue, le berger erre (WANDER).");
    }

    // -----------------------------------------------------------------------
    // Test — Mouton repu (énergie pleine) au REPOS : il s'arrête au lieu d'errer
    // -----------------------------------------------------------------------

    /**
     * Un Mouton à énergie MAXIMALE, sans prédateur ni danger, doit se reposer
     * (état REST) et NE PAS bouger ce tick — comportement de rumination, plus
     * réaliste qu'une errance perpétuelle. Note : économie d'énergie au sens
     * métabolique (coût de déplacement < coût au repos) volontairement non
     * traitée ici (impacte l'équilibre Lotka-Volterra, à valider en simulation).
     */
    @Test
    void moutonRepuSeReposeEtNeBougePas() {
        WorldOfCells world = AgentTestSupport.buildWorld();
        int cx = 33, cy = 33;
        for (int dx = -1; dx <= 1; dx++)
            for (int dy = -1; dy <= 1; dy++) {
                world.setCellHeight(cx + dx, cy + dy, 1.0);
                world.setForestCAValue(cx + dx, cy + dy, 0);
                world.setGrassCAValue(cx + dx, cy + dy, 0);
            }
        world.setJour(1);

        Mouton m = new Mouton(cx, cy, world);
        m.energie = m.energieMAX;          // pleine énergie → repos
        world.moutons.add(m);
        world.agents.add(m);
        world.uniqueDynamicObjects.add(m);

        m.step();

        assertEquals(AgentState.REST, m.currentState,
                "Mouton à énergie maximale, sans danger → REST.");
        assertEquals(cx, m.x, "Un mouton au repos ne se déplace pas (x inchangé).");
        assertEquals(cy, m.y, "Un mouton au repos ne se déplace pas (y inchangé).");
    }

    /** Garde-fou : un Mouton non-repu (énergie < max) ne se repose PAS. */
    @Test
    void moutonNonRepuNeSeReposePas() {
        WorldOfCells world = AgentTestSupport.buildWorld();
        int cx = 36, cy = 36;
        for (int dx = -1; dx <= 1; dx++)
            for (int dy = -1; dy <= 1; dy++) {
                world.setCellHeight(cx + dx, cy + dy, 1.0);
                world.setForestCAValue(cx + dx, cy + dy, 0);
                world.setGrassCAValue(cx + dx, cy + dy, 0);
            }
        world.setJour(1);

        Mouton m = new Mouton(cx, cy, world);
        m.energie = m.energieMAX * 0.8;    // pas pleine → ne se repose pas
        world.moutons.add(m);
        world.agents.add(m);
        world.uniqueDynamicObjects.add(m);

        m.step();

        assertNotEquals(AgentState.REST, m.currentState,
                "Un Mouton non-repu ne doit PAS se reposer.");
    }

    // -----------------------------------------------------------------------
    // Test — Alarme du troupeau : un Mouton qui voit le Loup alerte ses voisins
    // -----------------------------------------------------------------------

    /**
     * Loup en (10,cy). Mouton A en (20,cy) : à distance 10 = portée de vision →
     * il VOIT le loup et fuit. Mouton B en (25,cy) : à distance 15 du loup
     * (hors vision) mais à distance 5 de A (≤ rayon d'alerte) → il ne voit PAS
     * le loup mais doit être ALERTÉ par A, puis fuir comme le troupeau. Prouve
     * la propagation de l'alarme (communication entre proies).
     */
    @Test
    void moutonAlerteSesVoisinsDUnLoup() {
        WorldOfCells world = AgentTestSupport.buildWorld();
        int cy = 25;
        // Bande de terre dégagée de x=8 à x=27 (ligne cy ± 1).
        for (int x = 8; x <= 27; x++)
            for (int dy = -1; dy <= 1; dy++) {
                world.setCellHeight(x, cy + dy, 1.0);
                world.setForestCAValue(x, cy + dy, 0);
            }
        world.setJour(1);

        Loup l = new Loup(10, cy, world);
        world.loups.add(l);
        world.agents.add(l);
        world.uniqueDynamicObjects.add(l);

        Mouton a = new Mouton(20, cy, world);   // voit le loup (dist 10 = vision)
        world.moutons.add(a);
        world.agents.add(a);
        world.uniqueDynamicObjects.add(a);

        Mouton b = new Mouton(25, cy, world);   // hors vision du loup (dist 15), près de A (dist 5)
        world.moutons.add(b);
        world.agents.add(b);
        world.uniqueDynamicObjects.add(b);

        a.step();   // A voit le loup, fuit, alerte le voisinage

        assertEquals(AgentState.FLEE_PREDATOR, a.currentState,
                "A voit le loup (dist = portée de vision) et doit fuir.");
        assertTrue(b.alertTtl > 0,
                "B (hors vision du loup) doit être alerté par A.");

        b.step();   // B fuit grâce à l'alerte, sans voir le loup

        assertEquals(AgentState.FLEE_PREDATOR, b.currentState,
                "B alerté doit fuir même sans voir le loup directement.");
    }

    // -----------------------------------------------------------------------
    // Test — Comportement nocturne : les Moutons se regroupent la nuit
    // -----------------------------------------------------------------------

    /**
     * La NUIT (jour=0), un Mouton qui voit des congénères se regroupe (état HERD,
     * orienté vers le centre de masse du troupeau) — sécurité du nombre. Le JOUR,
     * le même mouton ne se regroupe pas (il broute/erre librement). On vérifie les
     * deux faces du comportement avec un troupeau à l'Est.
     */
    @Test
    void moutonSeRegroupeLaNuitPasLeJour() {
        // --- la nuit : regroupement ---
        WorldOfCells night = AgentTestSupport.buildWorld();
        int cx = 25, cy = 25;
        prepareGroupingArea(night, cx, cy);
        night.setJour(0);   // NUIT

        Mouton m = new Mouton(cx, cy, night);
        m.energie = m.energieMAX * 0.7;   // ni affamé ni plein
        night.moutons.add(m);
        night.agents.add(m);
        night.uniqueDynamicObjects.add(m);
        addFlockEast(night, cx, cy);

        m.step();

        assertEquals(AgentState.HERD, m.currentState,
                "La nuit, un Mouton voyant le troupeau doit se regrouper (HERD).");
        assertEquals(1, m._orient,
                "Le regroupement nocturne oriente vers le troupeau (Est).");

        // --- le jour : pas de regroupement ---
        WorldOfCells day = AgentTestSupport.buildWorld();
        prepareGroupingArea(day, cx, cy);
        day.setJour(1);   // JOUR

        Mouton m2 = new Mouton(cx, cy, day);
        m2.energie = m2.energieMAX * 0.7;
        day.moutons.add(m2);
        day.agents.add(m2);
        day.uniqueDynamicObjects.add(m2);
        addFlockEast(day, cx, cy);

        m2.step();

        assertNotEquals(AgentState.HERD, m2.currentState,
                "Le jour, le Mouton ne se regroupe pas (il n'est pas berger).");
    }

    private static void prepareGroupingArea(WorldOfCells w, int cx, int cy) {
        for (int dx = -11; dx <= 11; dx++)
            for (int dy = -11; dy <= 11; dy++)
                if (dx * dx + dy * dy <= 121) {
                    w.setCellHeight(cx + dx, cy + dy, 1.0);
                    w.setForestCAValue(cx + dx, cy + dy, 0);
                    w.setGrassCAValue(cx + dx, cy + dy, 0);
                }
    }

    private static void addFlockEast(WorldOfCells w, int cx, int cy) {
        int[][] flock = { {cx + 5, cy}, {cx + 6, cy - 1}, {cx + 6, cy + 1}, {cx + 7, cy} };
        for (int[] pos : flock) {
            Mouton f = new Mouton(pos[0], pos[1], w);
            w.moutons.add(f);
            w.agents.add(f);
            w.uniqueDynamicObjects.add(f);
        }
    }

    // -----------------------------------------------------------------------
    // Test — Mémoire spatiale : l'agent retient ses dernières cellules visitées
    // -----------------------------------------------------------------------

    /**
     * La « carte mentale » est un buffer circulaire des 8 dernières positions.
     * On vérifie : mémorisation, absence de faux positif, et oubli des plus
     * anciennes au-delà de la capacité.
     */
    @Test
    void memoireSpatialeRetientLesCellulesRecentes() {
        WorldOfCells world = AgentTestSupport.buildWorld();
        Mouton m = new Mouton(5, 5, world);

        m.recordVisit(1, 1);
        m.recordVisit(2, 2);
        m.recordVisit(3, 3);
        assertTrue(m.hasVisitedRecently(2, 2), "une cellule visitée récemment doit être mémorisée");
        assertFalse(m.hasVisitedRecently(9, 9), "une cellule jamais visitée n'est pas mémorisée");

        // Au-delà de la capacité (8 cellules), les plus anciennes sont oubliées.
        for (int k = 0; k < 8; k++) m.recordVisit(10 + k, 10 + k);
        assertFalse(m.hasVisitedRecently(1, 1), "la cellule la plus ancienne doit être oubliée");
        assertTrue(m.hasVisitedRecently(17, 17), "la cellule la plus récente reste mémorisée");
    }

    /**
     * Mémoire spatiale en action : un Mouton en errance dont la case DROIT DEVANT
     * a déjà été visitée doit tourner plutôt que d'y retourner (anti-boucle).
     * Cap Est, case Est mémorisée → il bascule au Sud (orient=2).
     */
    @Test
    void moutonEviteDeRevenirSurSesPas() {
        WorldOfCells world = AgentTestSupport.buildWorld();
        int cx = 25, cy = 25;
        for (int dx = -1; dx <= 1; dx++)
            for (int dy = -1; dy <= 1; dy++) {
                world.setCellHeight(cx + dx, cy + dy, 1.0);
                world.setForestCAValue(cx + dx, cy + dy, 0);
                world.setGrassCAValue(cx + dx, cy + dy, 0);
            }
        world.setJour(1);   // jour : ni regroupement nocturne

        Mouton m = new Mouton(cx, cy, world);
        m.energie = m.energieMAX * 0.7;     // ni affamé (SEEK_FOOD) ni plein (REST)
        m._orient = 1;                      // cap Est
        m.recordVisit(cx + 1, cy);          // la case Est a déjà été visitée
        world.moutons.add(m);
        world.agents.add(m);
        world.uniqueDynamicObjects.add(m);

        m.step();

        assertEquals(AgentState.WANDER, m.currentState, "le mouton doit être en errance");
        assertEquals(2, m._orient,
                "case Est déjà visitée → le mouton tourne (Sud) au lieu d'y retourner");
    }

    // -----------------------------------------------------------------------
    // Test — Reproduction SEXUÉE : il faut un partenaire à portée
    // -----------------------------------------------------------------------

    /**
     * La reproduction du Mouton exige désormais un congénère à portée (rayon de
     * reproduction), en plus des gardes énergie + Prepro. Un mouton ISOLÉ, même
     * plein et avec Prepro=1, ne peut pas se reproduire ; avec un partenaire
     * adjacent, un agneau naît.
     */
    @Test
    void moutonSeReproduitSeulementAvecPartenaire() {
        // --- isolé : pas de reproduction ---
        WorldOfCells w1 = AgentTestSupport.buildWorld();
        int cx = 22, cy = 22;
        for (int dx = -1; dx <= 1; dx++)
            for (int dy = -1; dy <= 1; dy++) {
                w1.setCellHeight(cx + dx, cy + dy, 1.0);
                w1.setForestCAValue(cx + dx, cy + dy, 0);
            }
        w1.setJour(1);
        Mouton lone = new Mouton(cx, cy, w1);
        lone.energie = lone.energieMAX;
        lone.Prepro = 1.0;
        w1.moutons.add(lone);
        w1.agents.add(lone);
        w1.uniqueDynamicObjects.add(lone);

        lone.step();
        assertEquals(1, w1.moutons.size(),
                "un mouton isolé ne peut pas se reproduire (aucun partenaire à portée).");

        // --- avec partenaire à portée : un agneau naît ---
        WorldOfCells w2 = AgentTestSupport.buildWorld();
        for (int dx = -1; dx <= 1; dx++)
            for (int dy = -1; dy <= 1; dy++) {
                w2.setCellHeight(cx + dx, cy + dy, 1.0);
                w2.setForestCAValue(cx + dx, cy + dy, 0);
            }
        w2.setJour(1);
        Mouton a = new Mouton(cx, cy, w2);
        a.energie = a.energieMAX;
        a.Prepro = 1.0;
        w2.moutons.add(a);
        w2.agents.add(a);
        w2.uniqueDynamicObjects.add(a);
        Mouton partner = new Mouton(cx + 1, cy, w2);   // partenaire adjacent
        partner.Prepro = 0;                            // ne se reproduit pas (isole le test)
        w2.moutons.add(partner);
        w2.agents.add(partner);
        w2.uniqueDynamicObjects.add(partner);

        a.step();
        assertEquals(3, w2.moutons.size(),
                "avec un partenaire à portée, le mouton se reproduit (2 → 3).");
    }

    // -----------------------------------------------------------------------
    // Test — Héritage évolutif : l'agneau hérite des traits moyennés + mutation
    // -----------------------------------------------------------------------

    /**
     * À la reproduction, l'agneau hérite des traits MOYENNÉS de ses deux parents
     * avec une légère mutation (±10%) — base de la sélection naturelle. Deux
     * parents à vision=30 / vcourse=20 produisent un agneau dont vision ≈ 30 (et
     * non la valeur par défaut 10) et vcourse ≈ 20, dans les bornes de mutation.
     */
    @Test
    void agneauHeriteDesTraitsMoyennesAvecMutation() {
        WorldOfCells w = AgentTestSupport.buildWorld();
        int cx = 22, cy = 22;
        for (int dx = -1; dx <= 1; dx++)
            for (int dy = -1; dy <= 1; dy++) {
                w.setCellHeight(cx + dx, cy + dy, 1.0);
                w.setForestCAValue(cx + dx, cy + dy, 0);
            }
        w.setJour(1);

        Mouton a = new Mouton(cx, cy, w);
        a.energie = a.energieMAX;
        a.Prepro = 1.0;
        a.vision = 30;
        a.vcourse = 20;
        w.moutons.add(a);
        w.agents.add(a);
        w.uniqueDynamicObjects.add(a);

        Mouton mate = new Mouton(cx + 1, cy, w);
        mate.Prepro = 0;
        mate.vision = 30;
        mate.vcourse = 20;
        w.moutons.add(mate);
        w.agents.add(mate);
        w.uniqueDynamicObjects.add(mate);

        a.step();

        assertEquals(3, w.moutons.size(), "un agneau doit naître (2 → 3).");
        Mouton lamb = w.moutons.get(2);
        assertTrue(lamb.vision >= 27 && lamb.vision <= 33,
                "vision héritée ~30 (±10%), pas le défaut 10 : " + lamb.vision);
        assertTrue(lamb.vcourse >= 18.0 && lamb.vcourse <= 22.0,
                "vcourse héritée ~20 (±10%) : " + lamb.vcourse);
    }

    /**
     * Reproduction sexuée + héritage évolutif côté Loup : deux loups au-dessus du
     * seuil énergétique, avec partenaire à portée, produisent un louveteau qui
     * hérite des traits moyennés des parents (vision ~24, vcourse ~18) ± mutation.
     */
    @Test
    void louveteauHeriteDesTraitsDesParents() {
        WorldOfCells w = AgentTestSupport.buildWorld();
        int cx = 18, cy = 18;
        for (int dx = -1; dx <= 1; dx++)
            for (int dy = -1; dy <= 1; dy++) {
                w.setCellHeight(cx + dx, cy + dy, 1.0);
                w.setForestCAValue(cx + dx, cy + dy, 0);
            }
        w.setJour(1);

        Loup a = new Loup(cx, cy, w);
        a.energie = (int) (a.energieD * 0.9);   // au-dessus du seuil 60%
        a.Prepro = 1.0;
        a.vision = 24;
        a.vcourse = 18;
        w.loups.add(a);
        w.agents.add(a);
        w.uniqueDynamicObjects.add(a);

        Loup mate = new Loup(cx + 1, cy, w);
        mate.Prepro = 0;
        mate.energie = a.energieD;
        mate.vision = 24;
        mate.vcourse = 18;
        w.loups.add(mate);
        w.agents.add(mate);
        w.uniqueDynamicObjects.add(mate);

        a.step();

        assertEquals(3, w.loups.size(),
                "avec partenaire et énergie suffisante, un louveteau naît (2 → 3).");
        Loup cub = w.loups.get(2);
        assertTrue(cub.vision >= 21 && cub.vision <= 27,
                "vision héritée ~24 (±10%), pas le défaut 10 : " + cub.vision);
        assertTrue(cub.vcourse >= 16.0 && cub.vcourse <= 20.0,
                "vcourse héritée ~18 (±10%) : " + cub.vcourse);
    }

    /**
     * Mémoire spatiale côté Loup : un loup repu (mais pas plein) qui flâne et dont
     * la case droit devant a déjà été visitée tourne plutôt que d'y retourner.
     * Cap Est, case Est mémorisée → bascule au Sud.
     */
    @Test
    void loupEviteDeRevenirSurSesPas() {
        WorldOfCells world = AgentTestSupport.buildWorld();
        int cx = 14, cy = 14;
        for (int dx = -1; dx <= 1; dx++)
            for (int dy = -1; dy <= 1; dy++) {
                world.setCellHeight(cx + dx, cy + dy, 1.0);
                world.setForestCAValue(cx + dx, cy + dy, 0);
            }
        world.setJour(1);

        Loup l = new Loup(cx, cy, world);
        l.energie = (int) (l.energieD * 0.8);   // repu mais pas plein → WANDER (lazyWander)
        l._orient = 1;                          // cap Est
        l.recordVisit(cx + 1, cy);              // case Est déjà visitée
        world.loups.add(l);
        world.agents.add(l);
        world.uniqueDynamicObjects.add(l);

        l.step();

        assertEquals(AgentState.WANDER, l.currentState, "le loup repu doit flâner (WANDER)");
        assertEquals(2, l._orient,
                "case Est déjà visitée → le loup tourne (Sud) au lieu d'y retourner");
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
