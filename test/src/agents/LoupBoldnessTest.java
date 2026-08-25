package agents;

import agents.ai.AgentState;
import agents.ai.BoldnessTrait;
import agents.ai.Percept;
import agents.ai.Perception;
import landscapegenerator.PerlinNoiseLandscapeGenerator;
import org.junit.jupiter.api.Test;
import worlds.WorldOfCells;
import static org.junit.jupiter.api.Assertions.*;

/** Effet du trait boldness sur l'audace de chasse du Loup (sous-projet D). */
class LoupBoldnessTest {

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

    /** Place une proie en vue et renvoie le Percept du loup. energie réglée AVANT. */
    private Percept senseWithPrey(Loup l, WorldOfCells w) {
        Mouton proie = new Mouton(13, 10, w);
        w.moutons.add(proie); w.agents.add(proie); w.uniqueDynamicObjects.add(proie);
        return Perception.sense(l, w, w.humains, w.moutons);
    }

    @Test
    void loupTemeraireChasseLaOuLePrudentSAbstient() {
        // energie = 0.8*energieD : au-dessus du seuil de base (0.7) mais sous le
        // seuil téméraire (0.7*1.25 = 0.875) → BOLD chasse, NONE/CAUTIOUS non.
        WorldOfCells w = flatWorld();
        Loup bold = new Loup(10, 10, w);
        bold.energie = (int) (bold.energieD * 0.8);
        bold.character.setBoldness(BoldnessTrait.BOLD);
        w.loups.add(bold); w.agents.add(bold); w.uniqueDynamicObjects.add(bold);
        assertEquals(AgentState.HUNT, bold.decideState(senseWithPrey(bold, w)),
                "loup téméraire chasse même peu affamé");

        WorldOfCells w2 = flatWorld();
        Loup cautious = new Loup(10, 10, w2);
        cautious.energie = (int) (cautious.energieD * 0.8);
        cautious.character.setBoldness(BoldnessTrait.CAUTIOUS);
        w2.loups.add(cautious); w2.agents.add(cautious); w2.uniqueDynamicObjects.add(cautious);
        assertNotEquals(AgentState.HUNT, cautious.decideState(senseWithPrey(cautious, w2)),
                "loup prudent n'engage pas à cette énergie");
    }

    @Test
    void killGateEcheleAvecLaTemerite() {
        // À 0.8*energieD : sous le seuil de kill téméraire (0.875) mais au-dessus du
        // neutre (0.7). postMove est déterministe → test fiable (pas de chasse émergente).
        WorldOfCells w = flatWorld();
        Loup bold = new Loup(10, 10, w);
        bold.energie = (int) (bold.energieD * 0.8);
        bold.character.setBoldness(BoldnessTrait.BOLD);
        w.loups.add(bold); w.agents.add(bold); w.uniqueDynamicObjects.add(bold);
        Mouton proie = new Mouton(10, 10, w);                 // même case que le loup
        w.moutons.add(proie); w.agents.add(proie); w.uniqueDynamicObjects.add(proie);
        bold.postMove(Perception.sense(bold, w, w.humains, w.moutons));
        assertFalse(proie._alive, "loup téméraire tue même peu affamé (gate échelonné)");

        WorldOfCells w2 = flatWorld();
        Loup neutre = new Loup(10, 10, w2);                   // trait NONE → seuil kill 0.7
        neutre.energie = (int) (neutre.energieD * 0.8);
        w2.loups.add(neutre); w2.agents.add(neutre); w2.uniqueDynamicObjects.add(neutre);
        Mouton proie2 = new Mouton(10, 10, w2);
        w2.moutons.add(proie2); w2.agents.add(proie2); w2.uniqueDynamicObjects.add(proie2);
        neutre.postMove(Perception.sense(neutre, w2, w2.humains, w2.moutons));
        assertTrue(proie2._alive, "loup neutre à 0.8 (repu) ne tue pas");
    }
}
