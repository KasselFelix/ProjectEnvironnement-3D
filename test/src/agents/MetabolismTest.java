package agents;

import agents.ai.AgentState;
import org.junit.jupiter.api.Test;
import worlds.WorldOfCells;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Économie métabolique / faim variable (L8) : le facteur de dépense dépend de
 * l'activité (repos &lt; errance &lt; 1.0 &lt; sprint) et le coût entier reporte
 * sa partie fractionnaire. NB : l'équilibre Lotka-Volterra se valide EN
 * SIMULATION, pas ici — on teste seulement le MÉCANISME.
 */
class MetabolismTest {

    @Test
    void leReposCouteMoinsQueLeSprint() {
        WorldOfCells w = AgentTestSupport.buildWorld();
        Mouton m = new Mouton(10, 10, w);

        m.currentState = AgentState.REST;
        double rest = m.activityEnergyFactor();
        m.currentState = AgentState.WANDER;
        double wander = m.activityEnergyFactor();
        m.currentState = AgentState.SEEK_FOOD;   // état "actif" générique
        double active = m.activityEnergyFactor();
        m.currentState = AgentState.HUNT;
        double sprint = m.activityEnergyFactor();

        assertTrue(rest < wander, "se reposer coûte moins qu'errer");
        assertTrue(wander < active, "errer coûte moins qu'une activité normale");
        assertTrue(active < sprint, "sprinter/fuir/chasser coûte le plus");
    }

    @Test
    void leCoutMetaboliqueReporteLaPartieFractionnaire() {
        WorldOfCells w = AgentTestSupport.buildWorld();
        Mouton m = new Mouton(10, 10, w);
        m.currentState = AgentState.SEEK_FOOD;   // facteur 1.0

        // base 0.5 × 1.0 = 0.5 par appel : 0 puis 1 (le reliquat 0.5 s'accumule).
        assertEquals(0, m.metabolicCost(0.5), "premier 0.5 : rien à retrancher encore");
        assertEquals(1, m.metabolicCost(0.5), "second 0.5 : le reliquat atteint 1 → 1 unité");
    }
}
