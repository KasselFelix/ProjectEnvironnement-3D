package agents;

import agents.ai.AgentState;
import agents.ai.Percept;
import agents.ai.Perception;
import landscapegenerator.PerlinNoiseLandscapeGenerator;
import org.junit.jupiter.api.Test;
import scent.ScentKind;
import worlds.WorldOfCells;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests comportementaux olfactifs du Mouton (sous-projet C). Monde plat,
 * sans vent ni pluie ; on pose des puffs d'odeur à la main et on lit l'état
 * décidé / le déplacement. step() ne touche pas OpenGL.
 */
class MoutonScentBehaviorTest {

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

    @Test
    void moutonIgnoreOdeurHumaine() {
        WorldOfCells w = flatWorld();
        Mouton m = new Mouton(10, 10, w);
        int now = w.getIteration();
        // Forte odeur HUMAIN sur la sonde du mouton (probe ≈ 2 cases).
        w.getScentField().emit(1, ScentKind.HUMAIN, -1, 12, 10, now, 1.0f);
        Percept p = Perception.sense(m, w, w.loups, w.moutons);
        assertEquals(0.0, p.scentDangerIntensity, 1e-9,
                "l'odeur humaine ne doit PAS compter comme danger (berger)");
        assertFalse(p.scentDangerDetected(), "aucun danger olfactif sur une trace humaine");
    }

    @Test
    void moutonDetecteOdeurLoup() {
        WorldOfCells w = flatWorld();
        Mouton m = new Mouton(10, 10, w);
        int now = w.getIteration();
        w.getScentField().emit(1, ScentKind.LOUP, -1, 12, 10, now, 1.0f);
        Percept p = Perception.sense(m, w, w.loups, w.moutons);
        assertTrue(p.scentDangerDetected(), "l'odeur de loup est bien un danger pour le mouton");
    }

	/** Enregistre un mouton dans le monde pour le faire vivre via step(). */
	private Mouton herdMouton(WorldOfCells w, int x, int y) {
		Mouton m = new Mouton(x, y, w);
		w.moutons.add(m); w.agents.add(m); w.uniqueDynamicObjects.add(m);
		return m;
	}

	@Test
	void moutonFuitOdeurLoupForte() {
		WorldOfCells w = flatWorld();
		Mouton m = new Mouton(10, 10, w);
		int now = w.getIteration();
		// Trace LOUP FORTE (≥ scentFleeIntensity 0.5) à l'EST (sonde mouton ≈ 2).
		w.getScentField().emit(7, ScentKind.LOUP, -1, 12, 10, now, 1.0f);
		Percept p = Perception.sense(m, w, w.loups, w.moutons);
		assertEquals(AgentState.FLEE_PREDATOR, m.decideState(p), "trace de loup forte → fuite");
	}

	@Test
	void moutonSeMefieOdeurLoupFaible() {
		WorldOfCells w = flatWorld();
		Mouton m = new Mouton(10, 10, w);
		int now = w.getIteration();
		// Trace LOUP FAIBLE : perçue ≈ 0.45 ∈ [seuil détection 0.375, fuite 0.5) → méfiance.
		w.getScentField().emit(7, ScentKind.LOUP, -1, 12, 10, now, 0.45f);
		Percept p = Perception.sense(m, w, w.loups, w.moutons);
		assertEquals(AgentState.WARY, m.decideState(p), "trace de loup faible → méfiance");
	}

	@Test
	void moutonMefiantNeBroutePas() {
		WorldOfCells w = flatWorld();
		Mouton m = herdMouton(w, 10, 10);
		// Re-pose une trace faible à chaque tick → reste méfiant.
		for (int t = 0; t < 6; t++) {
			w.getScentField().emit(7, ScentKind.LOUP, -1, 12, 10, w.getIteration(), 0.45f);
			w.step();
		}
		assertEquals(0, m.m, "un mouton méfiant ne broute pas (m == 0)");
	}

	@Test
	void moutonBergerNeReagitPasAOdeurHumaine() {
		WorldOfCells w = flatWorld();
		Mouton m = new Mouton(10, 10, w);
		int now = w.getIteration();
		w.getScentField().emit(7, ScentKind.HUMAIN, -1, 12, 10, now, 1.0f);
		AgentState s = m.decideState(Perception.sense(m, w, w.loups, w.moutons));
		assertNotEquals(AgentState.WARY, s, "pas de méfiance sur une trace humaine (berger)");
		assertNotEquals(AgentState.FLEE_PREDATOR, s, "pas de fuite sur une trace humaine (berger)");
	}
}
