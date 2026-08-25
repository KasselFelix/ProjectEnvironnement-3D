package agents;

import agents.ai.AgentState;
import agents.ai.Percept;
import agents.ai.Perception;
import landscapegenerator.PerlinNoiseLandscapeGenerator;
import objects.Species;
import org.junit.jupiter.api.Test;
import scent.ScentKind;
import worlds.WorldOfCells;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests comportementaux olfactifs du Loup (sous-projet C) + géométrie partagée.
 * Monde plat, sans vent ni pluie ; on pose des puffs et on observe l'état /
 * le déplacement. step() ne touche pas OpenGL.
 */
class LoupScentBehaviorTest {

    private static final int DX_VIEW = 51, DY_VIEW = 51;
    private static final int DX = DX_VIEW - 1, DY = DY_VIEW - 1;

    private WorldOfCells flatWorld() {
        WorldOfCells world = new WorldOfCells();
        world.nbloups = 0; world.nbmoutons = 0; world.nbhumains = 0; world.nbours = 0;
        double[][] landscape = PerlinNoiseLandscapeGenerator
                .generatePerlinNoiseLandscape(DX_VIEW, DY_VIEW, 0.7, 0.4, 4);
        world.init(DX, DY, landscape);
        for (int x = 0; x < world.getWidth(); x++)
            for (int y = 0; y < world.getHeight(); y++)
                world.setCellHeight(x, y, 5.0);
        world.setWindEnabled(true);
        world.setWindVector(0.0, 0.0);
        world.setRaining(false);
        return world;
    }

    /** Rend le loup affamé (sous HUNGER_RATIO) et l'enregistre dans le monde. */
    private Loup hungryLoup(WorldOfCells w, int x, int y) {
        Loup l = new Loup(x, y, w);
        l.energie = (int) (l.energieD * 0.5);   // < energieD*0.7 → affamé
        w.loups.add(l); w.agents.add(l); w.uniqueDynamicObjects.add(l);
        return l;
    }

    @Test
    void projectionWaypointTorus() {
        WorldOfCells w = flatWorld();
        Loup l = new Loup(10, 10, w);
        // 0=N(-y), 1=E(+x), 2=S(+y), 3=O(-x). Projection à 5 cases.
        assertArrayEquals(new int[]{15, 10}, l.projectScentWaypoint(1, 5), "Est = +x");
        assertArrayEquals(new int[]{10, 15}, l.projectScentWaypoint(2, 5), "Sud = +y");
        assertArrayEquals(new int[]{10,  5}, l.projectScentWaypoint(0, 5), "Nord = -y");
        assertArrayEquals(new int[]{ 5, 10}, l.projectScentWaypoint(3, 5), "Ouest = -x");
    }

    @Test
    void etatsOlfactifsExistent() {
        assertNotNull(AgentState.valueOf("SCENT_TRACK"));
        assertNotNull(AgentState.valueOf("WARY"));
    }

	@Test
	void loupPisteLaProieHorsVue() {
		WorldOfCells w = flatWorld();
		Loup l = hungryLoup(w, 10, 10);
		int now = w.getIteration();
		// Trace MOUTON (proie) à l'EST, sur la sonde du loup (probe ≈ 4) : senti,
		// PAS vu (aucun mouton-agent) → doit déclencher le pistage olfactif.
		w.getScentField().emit(99, ScentKind.MOUTON, -1, 14, 10, now, 0.9f);
		Percept p = Perception.sense(l, w, w.humains, w.moutons);
		assertEquals(AgentState.SCENT_TRACK, l.decideState(p), "affamé + trace proie hors vue → SCENT_TRACK");
	}

	@Test
	void loupProgresseVersLaTrace() {
		WorldOfCells w = flatWorld();
		Loup l = hungryLoup(w, 10, 10);
		int startX = l.x;
		// Re-pose la trace à l'EST à chaque tick (proie qui « reste » dans le coin)
		// pour que le loup ait toujours un cap olfactif vers l'Est.
		for (int t = 0; t < 12; t++) {
			w.getScentField().emit(99, ScentKind.MOUTON, -1, 14, 10, w.getIteration(), 0.9f);
			w.step();
		}
		assertTrue(l.x > startX, "le loup s'est rapproché de la trace (déplacement vers l'Est)");
		assertTrue(l._alive, "le loup est toujours vivant");
	}

	@Test
	void loupNePingPongPasSurLaTrace() {
		WorldOfCells w = flatWorld();
		Loup l = hungryLoup(w, 10, 10);
		java.util.Set<Long> visited = new java.util.HashSet<>();
		for (int t = 0; t < 40; t++) {
			w.getScentField().emit(99, ScentKind.MOUTON, -1, 14, 10, w.getIteration(), 0.6f);
			w.step();
			visited.add(((long) l.x << 20) | l.y);
		}
		assertTrue(l._alive, "le loup ne meurt pas en oscillant");
		assertTrue(visited.size() >= 4,
				"pistage : pas d'aller-retour sur 1-2 cases (cases visitées=" + visited.size() + ")");
	}

	@Test
	void loupSansOdeurResteEnRecherche() {
		WorldOfCells w = flatWorld();
		Loup l = hungryLoup(w, 10, 10);
		Percept p = Perception.sense(l, w, w.humains, w.moutons);   // aucune odeur posée
		assertNotEquals(AgentState.SCENT_TRACK, l.decideState(p),
				"sans trace, pas de pistage olfactif (→ SEARCH)");
	}

	// ====================================================================
	// Vérif « pister → tuer → manger » (demande utilisateur). La CHASSE
	// complète (poursuite émergente) est STOCHASTIQUE par nature (jitter
	// d'errance, cadence, fuite de la proie) : mesurée ~55/60 même dans des
	// scénarios serrés, donc non assertable sans rendre la suite instable.
	// On vérifie donc chaque MAILLON de façon DÉTERMINISTE.
	// ====================================================================

	/** Cas extrême : un loup quasi-AVEUGLE (vision=1) ne voit pas la proie mais
	 *  la PISTE à l'odeur (la portée de sonde dépend de l'acuité, pas de la vue). */
	@Test
	void loupAveuglePisteUneProieParOdorat() {
		WorldOfCells w = flatWorld();
		Loup l = hungryLoup(w, 10, 10);
		l.vision = 1;                                   // quasi-aveugle
		int now = w.getIteration();
		// Trace MOUTON à 4 cases (sonde du loup), bien au-delà de sa vue (1).
		w.getScentField().emit(99, ScentKind.MOUTON, -1, 14, 10, now, 0.9f);
		Percept p = Perception.sense(l, w, w.humains, w.moutons);
		assertFalse(p.preyVisible(), "vision=1 : la proie à 4 cases n'est PAS vue");
		assertTrue(p.scentPreyDetected(), "mais elle est SENTIE (odorat indépendant de la vue)");
		assertEquals(AgentState.SCENT_TRACK, l.decideState(p),
				"aveugle + trace proie → pistage olfactif");
	}

	/** Maillon « tuer » (déterministe) : un loup AFFAMÉ sur la même case qu'un
	 *  mouton le tue et laisse une carcasse. On exerce la mécanique de prédation
	 *  (postMove) directement, sans dépendre de la poursuite stochastique. */
	@Test
	void loupAffameTueProieEtLaisseCarcasse() {
		WorldOfCells w = flatWorld();
		Loup l = hungryLoup(w, 10, 10);                 // energie < HUNGER_RATIO → tue
		Mouton proie = new Mouton(10, 10, w);           // MÊME case que le loup
		w.moutons.add(proie); w.agents.add(proie); w.uniqueDynamicObjects.add(proie);
		assertTrue(w.carcasses.isEmpty(), "aucune carcasse au départ");
		l.postMove(Perception.sense(l, w, w.humains, w.moutons));   // déclenche la prédation
		assertFalse(proie._alive, "le mouton sur la case du loup affamé est tué");
		assertFalse(w.carcasses.isEmpty(), "la mise à mort laisse une carcasse");
	}

	/** Maillon « manger » (déterministe) : un loup affamé avec une carcasse
	 *  adjacente la consomme par bouchées et son énergie remonte. */
	@Test
	void loupMangeCarcasseEtGagneEnergie() {
		WorldOfCells w = flatWorld();
		Loup l = hungryLoup(w, 10, 10);
		w.spawnCarcass(11, 10, 40.0, Species.MOUTON);   // carcasse adjacente (Est)
		int energieDepart = l.energie, energieMax = l.energie;
		for (int t = 0; t < 60 && l._alive; t++) {
			w.step();
			energieMax = Math.max(energieMax, l.energie);
		}
		assertTrue(energieMax > energieDepart,
				"le loup a mangé la carcasse (energie passée de " + energieDepart + " à " + energieMax + ")");
	}
}
