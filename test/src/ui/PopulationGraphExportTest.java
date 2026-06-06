package ui;

import agents.AgentTestSupport;
import org.junit.jupiter.api.Test;
import worlds.WorldOfCells;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Export CSV de l'historique des populations (V7). Pas de rendu OpenGL : on
 * appelle {@code sample()} puis {@code exportCsv()}.
 */
class PopulationGraphExportTest {

    @Test
    void exportCsvContientEnteteEtLignes() {
        WorldOfCells w = AgentTestSupport.buildWorld();
        PopulationGraph g = new PopulationGraph();

        // sample() n'enregistre qu'aux multiples de SAMPLE_INTERVAL (=10) : on
        // avance la simulation et on échantillonne à chaque tick.
        for (int k = 0; k < 35; k++) { g.sample(w); w.step(); }

        String csv = g.exportCsv();
        assertTrue(csv.startsWith("sample,ours,loups,moutons,humains"),
                "le CSV commence par l'en-tête");
        int lines = csv.split("\n").length;
        assertEquals(g.sampleCount() + 1, lines,
                "une ligne d'en-tête + une par échantillon (" + g.sampleCount() + ")");
        assertTrue(g.sampleCount() >= 1, "au moins un échantillon a été pris");
    }
}
