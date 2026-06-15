package agents;

import agents.ai.Perception;
import landscapegenerator.PerlinNoiseLandscapeGenerator;
import org.junit.jupiter.api.Test;
import worlds.WorldOfCells;
import static org.junit.jupiter.api.Assertions.*;

/** Hard-gate saisonnier de la reproduction (sous-projet E). */
class MatingGateTest {

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

    /** Deux loups fondateurs adultes adjacents, rassasiés, Prepro saturé (repro
     *  déterministe si la condition passe). */
    private Loup founderLoup(WorldOfCells w, int x, int y) {
        Loup l = new Loup(x, y, w);
        l.energie = l.energieD;
        l.Prepro = 100;                 // Math.random()<100*… toujours vrai
        w.loups.add(l); w.agents.add(l); w.uniqueDynamicObjects.add(l);
        return l;
    }

    @Test
    void loupSeReproduitEnHiver() {
        WorldOfCells w = flatWorld();
        setSeason(w, 3);                // WINTER
        Loup a = founderLoup(w, 10, 10);
        founderLoup(w, 10, 11);         // partenaire à portée (REPRO_RADIUS)
        int before = w.loups.size();
        a.postMove(Perception.sense(a, w, w.humains, w.moutons));
        assertTrue(w.loups.size() > before, "loup se reproduit en hiver (sa saison)");
    }

    @Test
    void loupNeSeReproduitPasHorsHiver() {
        WorldOfCells w = flatWorld();
        setSeason(w, 0);                // SPRING (hors saison du loup)
        Loup a = founderLoup(w, 10, 10);
        founderLoup(w, 10, 11);
        int before = w.loups.size();
        a.postMove(Perception.sense(a, w, w.humains, w.moutons));
        assertEquals(before, w.loups.size(), "loup ne se reproduit pas au printemps");
    }

    @Test
    void oursSeReproduitAuPrintemps() {
        WorldOfCells w = flatWorld();
        setSeason(w, 0);                // SPRING (saison de l'ours)
        Ours a = new Ours(10, 10, w); a.energie = a.energieD; a.Prepro = 100;
        w.ours.add(a); w.agents.add(a); w.uniqueDynamicObjects.add(a);
        Ours b = new Ours(10, 11, w); b.energie = b.energieD; b.Prepro = 100;
        w.ours.add(b); w.agents.add(b); w.uniqueDynamicObjects.add(b);
        int before = w.ours.size();
        a.postMove(Perception.sense(a, w, java.util.Collections.emptyList(), w.loups));
        assertTrue(w.ours.size() > before, "ours se reproduit au printemps (sa saison)");
    }
}
