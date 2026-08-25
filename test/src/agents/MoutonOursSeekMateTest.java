package agents;

import agents.ai.AgentState;
import agents.ai.Percept;
import agents.ai.Perception;
import landscapegenerator.PerlinNoiseLandscapeGenerator;
import org.junit.jupiter.api.Test;
import scent.ScentKind;
import worlds.WorldOfCells;
import static org.junit.jupiter.api.Assertions.*;

/** Parité SEEK_MATE pour le mouton (automne) et l'ours (printemps) — sous-projet E. */
class MoutonOursSeekMateTest {

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

    private void setSeason(WorldOfCells w, int idx) {
        w.setDureeJour(1); w.setSeasonLengthDays(1); w.setIteration(idx * 2);
    }

    @Test
    void moutonReepuEnAutomneChercheUnPartenaire() {
        WorldOfCells w = flatWorld();
        setSeason(w, 2);                          // AUTUMN
        w.setJour(1);                             // jour (évite le repli HERD nocturne)
        Mouton m = new Mouton(10, 10, w); m.energie = m.energieMAX;   // repu → en rut
        w.moutons.add(m); w.agents.add(m); w.uniqueDynamicObjects.add(m);
        // Acuité mouton 0.4 → sonde à 2 cases : odeur sur (10,12).
        w.getScentField().emit(77, ScentKind.MOUTON, -1, 10, 12, w.getIteration(), 1.2f, true);
        Percept p = Perception.sense(m, w, w.loups, w.moutons);
        assertEquals(AgentState.SEEK_MATE, m.decideState(p), "mouton repu en automne → SEEK_MATE");
    }

    @Test
    void oursReepuAuPrintempsChercheUnPartenaire() {
        WorldOfCells w = flatWorld();
        setSeason(w, 0);                          // SPRING
        Ours o = new Ours(10, 10, w); o.energie = o.energieD;   // repu → en rut
        w.ours.add(o); w.agents.add(o); w.uniqueDynamicObjects.add(o);
        // Acuité ours 1.2 → sonde à 5 cases : odeur sur (10,15).
        w.getScentField().emit(88, ScentKind.OURS, -1, 10, 15, w.getIteration(), 1.5f, true);
        Percept p = Perception.sense(o, w, java.util.Collections.emptyList(), w.loups);
        assertEquals(AgentState.SEEK_MATE, o.decideState(p), "ours repu au printemps → SEEK_MATE");
    }

    @Test
    void oursHorsSaisonNeCherchePas() {
        WorldOfCells w = flatWorld();
        setSeason(w, 3);                          // WINTER (hors saison de l'ours)
        Ours o = new Ours(10, 10, w); o.energie = o.energieD;
        w.ours.add(o); w.agents.add(o); w.uniqueDynamicObjects.add(o);
        w.getScentField().emit(88, ScentKind.OURS, -1, 10, 15, w.getIteration(), 1.5f, true);
        Percept p = Perception.sense(o, w, java.util.Collections.emptyList(), w.loups);
        assertNotEquals(AgentState.SEEK_MATE, o.decideState(p), "hors saison → pas de SEEK_MATE");
    }
}
