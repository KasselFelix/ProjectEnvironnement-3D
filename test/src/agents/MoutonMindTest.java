package agents;

import agents.ai.AgentState;
import agents.ai.Axis;
import agents.ai.Pole;
import org.junit.jupiter.api.Test;
import worlds.WorldOfCells;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase D — intelligence (Mind) du Mouton (cf. docs/evolution.txt § 6) : chaque
 * mouton porte un esprit initialisé depuis son génome, entraîné selon son
 * activité.
 */
class MoutonMindTest {

    /** Un mouton intelligent démarre avec un score d'esprit plus élevé (§ 6.1). */
    @Test
    void moutonIntelligentADuMindPlusEleve() {
        WorldOfCells world = AgentTestSupport.buildWorld();
        Mouton intelligent = new Mouton(20, 20, world);
        intelligent.genome.set(Axis.INTELLIGENCE, Pole.POSITIVE);
        intelligent.initMind();
        Mouton neutre = new Mouton(20, 20, world);

        assertTrue(intelligent.mind.score() > neutre.mind.score(),
                "l'esprit du mouton intelligent démarre plus haut");
    }

    /** Le niveau d'activité cognitive (§ 6.2) dépend de l'état : les états de
     *  survie/décision sont actifs (1.0), le repos et l'errance passifs (0.0). */
    @Test
    void niveauDActiviteSelonLEtat() {
        WorldOfCells world = AgentTestSupport.buildWorld();
        Mouton m = new Mouton(20, 20, world);

        m.currentState = AgentState.FLEE_PREDATOR;
        assertEquals(1.0, m.activityLevel(), 1e-9, "fuir est une activité cognitive");
        m.currentState = AgentState.SEEK_FOOD;
        assertEquals(1.0, m.activityLevel(), 1e-9, "chercher de la nourriture aussi");
        m.currentState = AgentState.REST;
        assertEquals(0.0, m.activityLevel(), 1e-9, "le repos n'entraîne pas l'esprit");
        m.currentState = AgentState.WANDER;
        assertEquals(0.0, m.activityLevel(), 1e-9, "l'errance non plus");
    }
}
