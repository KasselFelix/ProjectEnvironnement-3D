package agents.ai;

import agents.Humain;
import agents.Loup;
import landscapegenerator.PerlinNoiseLandscapeGenerator;
import org.junit.jupiter.api.Test;
import scent.ScentKind;
import worlds.WorldOfCells;
import static org.junit.jupiter.api.Assertions.*;

/** Le 4e canal olfactif (séduction) dans Percept (sous-projet E). */
class PerceptionMatingTest {

    private WorldOfCells flatWorld() {
        WorldOfCells w = new WorldOfCells();
        w.nbloups = 0; w.nbmoutons = 0; w.nbhumains = 0; w.nbours = 0;
        double[][] ls = PerlinNoiseLandscapeGenerator.generatePerlinNoiseLandscape(51, 51, 0.7, 0.4, 4);
        w.init(50, 50, ls);
        for (int x = 0; x < w.getWidth(); x++)
            for (int y = 0; y < w.getHeight(); y++) w.setCellHeight(x, y, 5.0);
        w.setWindEnabled(true); w.setWindVector(0.0, 0.0); w.setRaining(false);
        return w;
    }

    @Test
    void loupDetecteLaSeductionDUnCongenereAuSud() {
        WorldOfCells w = flatWorld();
        Loup self = new Loup(10, 10, w);   // acuité 1.0 → sonde à 4 cases
        int now = w.getIteration();
        // Odeur de séduction LOUP d'un AUTRE émetteur, posée sur la sonde Sud (10,14).
        w.getScentField().emit(42, ScentKind.LOUP, -1, 10, 14, now, 1.2f, true);
        Percept p = Perception.sense(self, w, w.humains, w.moutons);
        assertTrue(p.scentMateDetected(), "le loup sent un partenaire");
        assertEquals(2, p.scentMateDir, "partenaire au Sud");
    }

    @Test
    void exclutSaPropreSeduction() {
        WorldOfCells w = flatWorld();
        Loup self = new Loup(10, 10, w);
        int now = w.getIteration();
        // Seule odeur de séduction = celle de SELF (même emitterId) → pas détectée.
        w.getScentField().emit(self.agentId, ScentKind.LOUP, -1, 10, 14, now, 1.2f, true);
        Percept p = Perception.sense(self, w, w.humains, w.moutons);
        assertFalse(p.scentMateDetected(), "un loup ne se détecte pas lui-même comme partenaire");
    }

    @Test
    void humainAnosmiqueNeSentPasDeSeduction() {
        WorldOfCells w = flatWorld();
        Humain self = new Humain(10, 10, w);   // sous le gate olfactif
        int now = w.getIteration();
        w.getScentField().emit(42, ScentKind.HUMAIN, -1, 10, 14, now, 1.2f, true);
        Percept p = Perception.sense(self, w, w.loups, w.moutons);
        assertFalse(p.scentMateDetected(), "anosmique → aucun canal de séduction");
    }
}
