package agents;

import agents.ai.AgentState;
import agents.ai.BoldnessTrait;
import agents.ai.Percept;
import agents.ai.Perception;
import landscapegenerator.PerlinNoiseLandscapeGenerator;
import org.junit.jupiter.api.Test;
import worlds.WorldOfCells;
import static org.junit.jupiter.api.Assertions.*;

/** Effet du trait boldness + satisfaction de l'Ours (sous-projet D). */
class OursBoldnessTest {

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

    @Test
    void satisfactionChuteAvecLaFaim() {
        WorldOfCells w = flatWorld();
        Ours o = new Ours(10, 10, w);
        o.energie = o.energieD;
        assertEquals(1.0, o.satisfaction(), 1e-9, "rassasié + sûr → satisfaction max");
        o.energie = 0;
        assertEquals(0.5, o.satisfaction(), 1e-9, "affamé → satisfaction chute");
        o.energie = o.energieD;
        o.setOnFire();
        assertEquals(0.5, o.satisfaction(), 1e-9, "en feu + rassasié → sécurité 0 fait chuter");
    }

    @Test
    void oursTemeraireChasseLaOuLePrudentSAbstient() {
        WorldOfCells w = flatWorld();
        Ours bold = new Ours(10, 10, w);
        bold.energie = (int) (bold.energieD * 0.8);    // entre 0.7 et 0.875
        bold.character.setBoldness(BoldnessTrait.BOLD);
        w.ours.add(bold); w.agents.add(bold); w.uniqueDynamicObjects.add(bold);
        Loup proie = new Loup(13, 10, w);
        w.loups.add(proie); w.agents.add(proie); w.uniqueDynamicObjects.add(proie);
        Percept pb = Perception.sense(bold, w, java.util.Collections.emptyList(), w.loups);
        assertEquals(AgentState.HUNT, bold.decideState(pb), "ours téméraire chasse peu affamé");

        WorldOfCells w2 = flatWorld();
        Ours cautious = new Ours(10, 10, w2);
        cautious.energie = (int) (cautious.energieD * 0.8);
        cautious.character.setBoldness(BoldnessTrait.CAUTIOUS);
        w2.ours.add(cautious); w2.agents.add(cautious); w2.uniqueDynamicObjects.add(cautious);
        Loup proie2 = new Loup(13, 10, w2);
        w2.loups.add(proie2); w2.agents.add(proie2); w2.uniqueDynamicObjects.add(proie2);
        Percept pc = Perception.sense(cautious, w2, java.util.Collections.emptyList(), w2.loups);
        assertNotEquals(AgentState.HUNT, cautious.decideState(pc), "ours prudent s'abstient");
    }
}
