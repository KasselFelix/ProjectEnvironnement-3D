package agents;

import agents.ai.AgentState;
import agents.ai.BoldnessTrait;
import agents.ai.Percept;
import agents.ai.Perception;
import landscapegenerator.PerlinNoiseLandscapeGenerator;
import org.junit.jupiter.api.Test;
import worlds.WorldOfCells;
import static org.junit.jupiter.api.Assertions.*;

/** Le berger téméraire confronte un loup proche (sous-projet D). */
class HumainBoldnessTest {

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

    /** Loup à 5 cases EST du berger (rayon base 4 ; BOLD→6, CAUTIOUS→2). */
    private Percept senseWithWolfAt(Humain h, WorldOfCells w, int lx, int ly) {
        Loup loup = new Loup(lx, ly, w);
        w.loups.add(loup); w.agents.add(loup); w.uniqueDynamicObjects.add(loup);
        return Perception.sense(h, w, w.loups, w.moutons);
    }

    @Test
    void bergerTemeraireConfronteLeLoupProche() {
        WorldOfCells w = flatWorld();
        Humain h = new Humain(10, 10, w);     // berger (chasseur=false par défaut)
        h.character.setBoldness(BoldnessTrait.BOLD);
        Percept p = senseWithWolfAt(h, w, 15, 10);   // dist 5 ≤ rayon BOLD (6)
        assertTrue(p.predatorVisible(), "le loup est en vue");
        assertEquals(AgentState.CONFRONT, h.decideState(p), "berger téméraire confronte");
    }

    @Test
    void bergerPrudentNeConfrontePas() {
        WorldOfCells w = flatWorld();
        Humain h = new Humain(10, 10, w);
        h.character.setBoldness(BoldnessTrait.CAUTIOUS);
        Percept p = senseWithWolfAt(h, w, 15, 10);   // dist 5 > rayon CAUTIOUS (2)
        assertNotEquals(AgentState.CONFRONT, h.decideState(p), "berger prudent ne confronte pas un loup lointain");
    }

    @Test
    void bergerNeutreConfronteAuRayonBase() {
        // Contrat bootstrap : un berger SANS trait acquis (NONE → facteur 1.0 →
        // rayon = confrontRadiusBase = 4) confronte déjà un loup au rayon base.
        WorldOfCells w = flatWorld();
        Humain h = new Humain(10, 10, w);
        h.character.setBoldness(BoldnessTrait.NONE);
        Percept p = senseWithWolfAt(h, w, 14, 10);   // dist 4 ≤ rayon NONE (4)
        assertTrue(p.predatorVisible(), "le loup est en vue");
        assertEquals(AgentState.CONFRONT, h.decideState(p),
                "berger neutre confronte au rayon base (amorçage du trait)");
    }

    @Test
    void bergerNeutreNeConfrontePasHorsRayonBase() {
        // Pendant du test précédent : à 5 cases (> 4), le berger NONE ne confronte pas.
        WorldOfCells w = flatWorld();
        Humain h = new Humain(10, 10, w);
        h.character.setBoldness(BoldnessTrait.NONE);
        Percept p = senseWithWolfAt(h, w, 15, 10);   // dist 5 > rayon NONE (4)
        assertNotEquals(AgentState.CONFRONT, h.decideState(p),
                "berger neutre ne confronte pas un loup au-delà du rayon base");
    }

    @Test
    void chasseurGardeSaChasseInchangee() {
        WorldOfCells w = flatWorld();
        Humain h = new Humain(10, 10, w);
        h.chasseur = true;                            // mode chasseur, pas berger
        Percept p = senseWithWolfAt(h, w, 15, 10);
        assertEquals(AgentState.HUNT, h.decideState(p),
                "le chasseur poursuit le loup (HUNT) ; CONFRONT ne concerne que le berger");
    }
}
