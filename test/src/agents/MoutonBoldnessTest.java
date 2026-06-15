package agents;

import agents.ai.AgentState;
import agents.ai.BoldnessTrait;
import agents.ai.Percept;
import agents.ai.Perception;
import landscapegenerator.PerlinNoiseLandscapeGenerator;
import org.junit.jupiter.api.Test;
import scent.ScentKind;
import worlds.WorldOfCells;
import static org.junit.jupiter.api.Assertions.*;

/** Effet du trait boldness sur la réaction olfactive du Mouton (sous-projet D). */
class MoutonBoldnessTest {

    private WorldOfCells flatWorld() {
        WorldOfCells world = new WorldOfCells();
        world.nbloups = 0; world.nbmoutons = 0; world.nbhumains = 0; world.nbours = 0;
        double[][] ls = PerlinNoiseLandscapeGenerator.generatePerlinNoiseLandscape(51, 51, 0.7, 0.4, 4);
        world.init(50, 50, ls);
        for (int x = 0; x < world.getWidth(); x++)
            for (int y = 0; y < world.getHeight(); y++) world.setCellHeight(x, y, 5.0);
        world.setWindEnabled(true); world.setWindVector(0.0, 0.0); world.setRaining(false);
        return world;
    }

    /** Odeur de loup d'intensité intermédiaire (≈0.45) sur la sonde (2 cases EST).
     *  Choisie ENTRE les deux seuils modulés (défauts scentFleeIntensity=0.5,
     *  fleeBoldnessDelta=0.5) : seuil PRUDENT = 0.5×0.5 = 0.25, seuil TÉMÉRAIRE =
     *  0.5×1.5 = 0.75 → 0.25 < 0.45 < 0.75, donc PRUDENT fuit et TÉMÉRAIRE non. */
    private Percept senseWithWolfScent(Mouton m, WorldOfCells w) {
        w.getScentField().emit(7, ScentKind.LOUP, -1, 12, 10, w.getIteration(), 0.45f);
        return Perception.sense(m, w, w.loups, w.moutons);
    }

    @Test
    void moutonTemeraireResteMefiantLaOuLePrudentFuit() {
        WorldOfCells w = flatWorld();
        Mouton temeraire = new Mouton(10, 10, w);
        temeraire.character.setBoldness(BoldnessTrait.BOLD);
        assertEquals(AgentState.WARY, temeraire.decideState(senseWithWolfScent(temeraire, w)),
                "seuil de fuite relevé → reste WARY sur une odeur intermédiaire");

        WorldOfCells w2 = flatWorld();
        Mouton prudent = new Mouton(10, 10, w2);
        prudent.character.setBoldness(BoldnessTrait.CAUTIOUS);
        assertEquals(AgentState.FLEE_PREDATOR, prudent.decideState(senseWithWolfScent(prudent, w2)),
                "seuil de fuite abaissé → fuit la même odeur");
    }

    @Test
    void laVueDUnLoupFaitToujoursFuirMemeTemeraire() {
        WorldOfCells w = flatWorld();
        Mouton temeraire = new Mouton(10, 10, w);
        temeraire.character.setBoldness(BoldnessTrait.BOLD);
        Loup loup = new Loup(12, 10, w);
        w.loups.add(loup); w.agents.add(loup); w.uniqueDynamicObjects.add(loup);
        Percept p = Perception.sense(temeraire, w, w.loups, w.moutons);
        assertTrue(p.predatorVisible(), "le loup est en vue");
        assertEquals(AgentState.FLEE_PREDATOR, temeraire.decideState(p),
                "voir un loup → fuite, quel que soit le trait (garde-fou R3)");
    }
}
