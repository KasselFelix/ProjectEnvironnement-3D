package agents;

import org.junit.jupiter.api.Test;
import ui.SimulationConfig;
import worlds.WorldOfCells;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Cadence des agents (correctifs d'audit 2026-06-07) :
 *  - isMyTurn() ne doit jamais diviser par zero, meme si la vitesse depasse 28
 *    (la vitesse evolue sans borne haute — c'est voulu — donc le diviseur
 *    (int)((1/vitesse)*28) peut tomber a 0) ;
 *  - les drains « horaires » (feu) suivent simulationHz via ticksPerGameSecond()
 *    au lieu d'un % 20 code en dur.
 */
class AgentCadenceTest {

    /** RÉGRESSION : une vitesse > 28 ne provoque plus d'ArithmeticException (/ 0)
     *  dans isMyTurn() — pour les quatre especes. */
    @Test
    void vitesseElevoeeNeCrashePasIsMyTurn() {
        WorldOfCells w = AgentTestSupport.buildWorld();

        Loup l = new Loup(10, 10, w);   l.vitesse   = 30;   // > 28
        Mouton m = new Mouton(11, 11, w); m.vitesse = 40;
        Ours o = new Ours(12, 12, w);   o.vitesse   = 99;
        Humain h = new Humain(13, 13, w); h.vitesse = 28.5;

        // Avant le correctif : (int)((1/vitesse)*28) == 0 → world.getIteration() % 0
        // → ArithmeticException. Apres : diviseur borne a 1 → agit chaque tick.
        assertDoesNotThrow(l::isMyTurn, "Loup rapide ne crashe pas");
        assertDoesNotThrow(m::isMyTurn, "Mouton rapide ne crashe pas");
        assertDoesNotThrow(o::isMyTurn, "Ours rapide ne crashe pas");
        assertDoesNotThrow(h::isMyTurn, "Humain rapide ne crashe pas");

        // A l'iteration 0 et avec un diviseur de 1, l'agent agit (0 % 1 == 0).
        assertTrue(l.isMyTurn() && m.isMyTurn() && o.isMyTurn() && h.isMyTurn(),
                "au-dela de 28 l'agent agit a chaque tick");
    }

    /** Le pas « 1 seconde-jeu » suit simulationHz (fallback 20 sans config) au lieu
     *  d'un 20 code en dur — sinon la letalite du feu varierait avec le Hz. */
    @Test
    void ticksParSecondeSuitSimulationHz() {
        WorldOfCells w = AgentTestSupport.buildWorld();
        Loup l = new Loup(10, 10, w);

        w.config = null;
        assertEquals(20, l.ticksPerGameSecond(), "fallback 20 sans config");

        SimulationConfig c = new SimulationConfig();
        c.simulationHz = 60;
        w.config = c;
        assertEquals(60, l.ticksPerGameSecond(), "suit simulationHz=60");

        c.simulationHz = 10;
        assertEquals(10, l.ticksPerGameSecond(), "suit simulationHz=10");
    }
}
