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

    @Test
    void exportPngEcritUnFichierNonVide() throws Exception {
        WorldOfCells w = AgentTestSupport.buildWorld();
        PopulationGraph g = new PopulationGraph();
        for (int k = 0; k < 25; k++) { g.sample(w); w.step(); }

        java.nio.file.Path out = java.nio.file.Files.createTempFile("popgraph", ".png");
        g.exportPng(out);
        assertTrue(java.nio.file.Files.size(out) > 0, "le PNG exporté n'est pas vide");
        // En-tête PNG : octets 0x89 'P' 'N' 'G'.
        byte[] head = java.util.Arrays.copyOf(java.nio.file.Files.readAllBytes(out), 4);
        assertEquals((byte) 0x89, head[0]);
        assertEquals('P', head[1]);
        assertEquals('N', head[2]);
        assertEquals('G', head[3]);
        java.nio.file.Files.deleteIfExists(out);
    }
}
