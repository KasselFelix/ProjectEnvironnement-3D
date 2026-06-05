package agents;

import agents.ai.AgentState;
import agents.ai.MemoryKind;
import agents.ai.Percept;
import landscapegenerator.PerlinNoiseLandscapeGenerator;
import org.junit.jupiter.api.Test;
import worlds.WorldOfCells;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests comportementaux du Loup.
 *
 * Stratégie : on instancie un vrai WorldOfCells avec nbloups/moutons/humains=0
 * pour éviter le spawn aléatoire d'agents, puis on ajoute manuellement les
 * agents de test à des positions contrôlées.
 *
 * Les agents.step() ne touchent pas à OpenGL (seul Landscape.display() le fait),
 * donc on peut exécuter step() sans contexte GL.
 */
class LoupTest {

    // Convention du projet (cf. graphics/Landscape.java:192,220) :
    // on génère un paysage (dxView, dyView) puis init le World avec (dxView-1, dyView-1)
    // car World.init lit landscape[x+1][y+1].
    private static final int DX_VIEW = 51;
    private static final int DY_VIEW = 51;
    private static final int DX = DX_VIEW - 1;
    private static final int DY = DY_VIEW - 1;

    private WorldOfCells buildWorld() {
        WorldOfCells world = new WorldOfCells();
        world.nbloups = 0;
        world.nbmoutons = 0;
        world.nbhumains = 0;
        double[][] landscape = PerlinNoiseLandscapeGenerator
                .generatePerlinNoiseLandscape(DX_VIEW, DY_VIEW, 0.7, 0.4, 4);
        world.init(DX, DY, landscape);
        return world;
    }

    /**
     * C3 — Rééquilibrage du gain énergétique à la prédation.
     *
     * Attendu après correctif : un loup à énergie 100 qui mange un mouton
     * doit voir son énergie passer à 100 + energieD/2 = 600, et non à
     * energieD = 1000 (restauration totale, code original).
     *
     * Robustesse : on place un mouton à la position du loup ET sur les 4
     * cases cardinales adjacentes, donc quel que soit le mouvement du loup
     * dans son step(), il finira sur une case contenant un mouton et le mangera.
     */
    @Test
    void loupGainEnergieBornéEnMangeantMouton() {
        WorldOfCells world = buildWorld();
        int cx = 10, cy = 10;
        // Terrain aplani autour du loup : sans cela, le bruit de Perlin peut
        // rendre la case cardinale visée impassable → le fallback aléatoire de
        // Locomotion.move envoie le loup sur une DIAGONALE non couverte par un
        // mouton → aucune prédation → test flaky. Sur terre plate, le loup se
        // déplace toujours sur une case cardinale (toutes occupées par un mouton).
        AgentTestSupport.flattenLandArea(world, cx, cy, 3);

        Loup loup = new Loup(cx, cy, world);
        loup.energie = 100;
        world.loups.add(loup);
        world.agents.add(loup);
        world.uniqueDynamicObjects.add(loup);

        int[][] positions = {
                {cx,     cy    },
                {cx,     cy - 1},
                {cx,     cy + 1},
                {cx - 1, cy    },
                {cx + 1, cy    }
        };
        Mouton[] moutons = new Mouton[positions.length];
        for (int i = 0; i < positions.length; i++) {
            moutons[i] = new Mouton(positions[i][0], positions[i][1], world);
            world.moutons.add(moutons[i]);
            world.agents.add(moutons[i]);
            world.uniqueDynamicObjects.add(moutons[i]);
        }

        loup.step();

        int dead = 0;
        for (Mouton m : moutons) if (!m._alive) dead++;
        assertEquals(1, dead,
                "Exactement un mouton doit être mangé par step (boucle break dans Loup.step).");

        // Après prédation, le bloc "mise à jour énergie" de Loup.step() décrémente
        // energie de 1 à 4 unités (base + eau + descente). On vérifie donc la borne :
        // C3 impose un gain plafonné à energieD/2, donc energie ≤ 100 + energieD/2,
        // alors que le code original ramène energie à energieD = 1000.
        int gainMaxAttendu = 100 + loup.energieD / 2;
        assertTrue(loup.energie > 100,
                "Le loup a dû gagner de l'énergie en mangeant le mouton (energie > 100 initial).");
        assertTrue(loup.energie <= gainMaxAttendu,
                "C3 : gain plafonné à energieD/2. Attendu energie ≤ "
                + gainMaxAttendu + ", observé " + loup.energie
                + " (probable restauration totale à energieD du code original).");
    }

    /**
     * L1 — Mémoire de chasse (MemoryKind.HUNTING). Après une prédation réussie,
     * le loup mémorise la cellule comme zone de chasse fertile : la prochaine
     * fois qu'il sera affamé sans proie en vue, il pourra y retourner (homing,
     * cf. loupRetourneVersSaZoneDeChasse).
     */
    @Test
    void loupMemoriseSaZoneDeChasse() {
        WorldOfCells world = buildWorld();
        int cx = 10, cy = 10;
        AgentTestSupport.flattenLandArea(world, cx, cy, 3);

        Loup loup = new Loup(cx, cy, world);
        loup.energie = 100;   // affamé (< energieD * HUNGER_RATIO)
        world.loups.add(loup);
        world.agents.add(loup);
        world.uniqueDynamicObjects.add(loup);

        int[][] positions = {
                {cx, cy}, {cx, cy - 1}, {cx, cy + 1}, {cx - 1, cy}, {cx + 1, cy}
        };
        for (int[] pos : positions) {
            Mouton mt = new Mouton(pos[0], pos[1], world);
            world.moutons.add(mt);
            world.agents.add(mt);
            world.uniqueDynamicObjects.add(mt);
        }

        loup.step();

        assertTrue(loup.memory.contains(MemoryKind.HUNTING, loup.x, loup.y),
                "Après une prédation réussie, le loup mémorise sa position comme "
                + "zone de chasse (HUNTING) en (" + loup.x + "," + loup.y + ").");
    }

    /**
     * L1 — Retour vers la zone de chasse mémorisée. Un loup affamé qui ne voit
     * aucune proie mais connaît une zone HUNTING s'oriente vers elle (homing)
     * plutôt que de balayer en spirale aveugle.
     */
    @Test
    void loupRetourneVersSaZoneDeChasse() {
        WorldOfCells world = buildWorld();
        int cx = 25, cy = 25;
        AgentTestSupport.flattenLandArea(world, cx, cy, 12);

        Loup loup = new Loup(cx, cy, world);
        loup.energie = 100;   // affamé → state SEARCH si aucune proie visible
        world.loups.add(loup);
        world.agents.add(loup);
        world.uniqueDynamicObjects.add(loup);

        // Zone de chasse mémorisée plein EST (cap 1), à distance > vision.
        int hx = (cx + loup.vision + 4) % world.getWidth(), hy = cy;
        loup.memory.remember(MemoryKind.HUNTING, hx, hy);

        // Aucun mouton dans le monde → pas de proie en vue.
        Percept p = agents.ai.Perception.sense(loup, world, world.humains, world.moutons);
        AgentState s = loup.decideState(p);
        assertEquals(AgentState.SEARCH, s, "loup affamé sans proie visible → SEARCH");
        loup.applyState(s, p);
        assertEquals(1, loup._orient,
                "le loup oriente vers la zone HUNTING mémorisée à l'EST (cap 1), "
                + "observé cap " + loup._orient);
    }

    /**
     * L2 — Fuite active de la lave : un loup qui voit une coulée de lave dans sa
     * vision adopte l'état FLEE_LAVA et s'oriente à l'opposé (la lave plein EST →
     * fuite plein OUEST, cap 3), au-dessus de toute autre priorité hors feu.
     */
    @Test
    void loupFuitLaLaveAVue() {
        WorldOfCells world = buildWorld();
        int cx = 25, cy = 25;
        AgentTestSupport.flattenLandArea(world, cx, cy, 12);

        Loup loup = new Loup(cx, cy, world);
        loup.energie = 100;   // affamé : sans lave il chasserait/chercherait
        world.loups.add(loup);
        world.agents.add(loup);
        world.uniqueDynamicObjects.add(loup);

        // Coulée de lave plein EST, dans la vision (vision=10) : on empile une
        // couche LAVA (state>0) → getLavaCAValue renverra >0 sur cette cellule.
        int lx = (cx + 4) % world.getWidth();
        world.pushLayer(lx, cy, objects.Material.LAVA, 1f, 1);

        Percept p = agents.ai.Perception.sense(loup, world, world.humains, world.moutons);
        assertTrue(p.lavaVisible(), "la lave doit être perçue dans la vision");
        AgentState s = loup.decideState(p);
        assertEquals(AgentState.FLEE_LAVA, s, "lave en vue → FLEE_LAVA (priorité haute)");
        loup.applyState(s, p);
        assertEquals(3, loup._orient,
                "le loup fuit à l'opposé de la lave EST → cap OUEST (3), observé " + loup._orient);
    }

    /**
     * L1 — Isolement de meute : un loup seul est isolé ; un loup avec un
     * congénère proche ne l'est pas. Alimente l'émergence du caractère social.
     */
    @Test
    void loupIsolementDeMeute() {
        WorldOfCells world = buildWorld();
        Loup solo = new Loup(20, 20, world);
        world.loups.add(solo);
        world.agents.add(solo);
        world.uniqueDynamicObjects.add(solo);
        assertTrue(solo.isIsolated(), "un loup seul dans le monde est isolé");

        Loup mate = new Loup(22, 20, world);   // à 2 cases (< PACK_NEAR_RADIUS)
        world.loups.add(mate);
        world.agents.add(mate);
        world.uniqueDynamicObjects.add(mate);
        assertFalse(solo.isIsolated(), "un loup avec un congénère proche n'est pas isolé");
    }

    /**
     * L1 — Cohésion de meute : un loup repu (state WANDER) NON solitaire qui voit
     * un congénère dérive vers lui (le barycentre de meute), au lieu de flâner au
     * hasard. Le congénère est placé plein SUD pour un cap déterministe.
     */
    @Test
    void loupRepuRejointLaMeute() {
        WorldOfCells world = buildWorld();
        int cx = 25, cy = 25;
        AgentTestSupport.flattenLandArea(world, cx, cy, 12);

        Loup loup = new Loup(cx, cy, world);
        // Ni affamé (> HUNGER_RATIO×energieD) ni plein (< energieD) → state WANDER,
        // qui déclenche lazyWander → packCohesion.
        loup.energie = (int) (loup.energieD * 0.9);
        world.loups.add(loup);
        world.agents.add(loup);
        world.uniqueDynamicObjects.add(loup);

        Loup mate = new Loup(cx, cy + 4, world);   // plein SUD, dans la vision
        world.loups.add(mate);
        world.agents.add(mate);
        world.uniqueDynamicObjects.add(mate);

        Percept p = agents.ai.Perception.sense(loup, world, world.humains, world.moutons);
        AgentState s = loup.decideState(p);
        assertEquals(AgentState.WANDER, s, "loup repu sans proie ni danger → WANDER");
        loup.applyState(s, p);
        assertEquals(2, loup._orient,
                "le loup repu (caractère non solitaire) s'oriente vers la meute au SUD "
                + "(cap 2), observé cap " + loup._orient);
    }

    /**
     * Mort par vieillesse — le loup doit être marqué `_alive=false` quand son âge
     * dépasse `maxAgeDays`. Configuré très court (0.05 jour ≈ 200 itérations) pour
     * que le check d'âge déclenche bien AVANT toute autre cause possible de mort
     * (famine — l'énergie est aussi gonflée par sécurité).
     */
    @Test
    void loupMeurtParVieillesse() {
        WorldOfCells world = buildWorld();

        Loup loup = new Loup(10, 10, world);
        loup.maxAgeDays = 0.05;          // ≈ 200 ticks avec dureeJour=2000
        loup.energieD   = 1_000_000;     // empêche toute mort par famine
        loup.energie    = loup.energieD;
        world.loups.add(loup);
        world.agents.add(loup);
        world.uniqueDynamicObjects.add(loup);

        assertTrue(loup._alive, "Au démarrage le loup est vivant.");

        int targetIter = 500;            // ~2× plus que l'âge max pour absorber le jitter
        while (world.getIteration() < targetIter && loup._alive) {
            world.step();
        }

        assertFalse(loup._alive,
                "Après " + world.getIteration() + " ticks (age " + loup.getAgeDays()
                + " jours, max " + loup.maxAgeDays + "), le loup doit être mort.");
        assertTrue(loup.getAgeDays() > loup.maxAgeDays,
                "L'âge final " + loup.getAgeDays() + " ne dépasse pas maxAgeDays "
                + loup.maxAgeDays + " — le loup est mort d'autre chose.");
    }
}
