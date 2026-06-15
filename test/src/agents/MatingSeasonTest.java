package agents;

import landscapegenerator.PerlinNoiseLandscapeGenerator;
import org.junit.jupiter.api.Test;
import worlds.Season;
import worlds.WorldOfCells;
import static org.junit.jupiter.api.Assertions.*;

/** Cartographie saisonnière + rut (sous-projet E). */
class MatingSeasonTest {

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

    /** Force la saison du monde de façon déterministe (idx : 0=SPRING..3=WINTER). */
    private void setSeason(WorldOfCells w, int idx) {
        w.setDureeJour(1); w.setSeasonLengthDays(1); w.setIteration(idx * 2);
        assertEquals(Season.values()[idx], w.currentSeason(), "saison forcée");
    }

    @Test
    void loupEnRutSeulementEnHiver() {
        WorldOfCells w = flatWorld();
        Loup l = new Loup(10, 10, w);
        setSeason(w, 3); assertTrue(l.inMatingSeason(), "loup : hiver");
        setSeason(w, 0); assertFalse(l.inMatingSeason(), "loup : pas au printemps");
        setSeason(w, 1); assertFalse(l.inMatingSeason(), "loup : pas en été");
    }

    @Test
    void moutonEnRutSeulementEnAutomne() {
        WorldOfCells w = flatWorld();
        Mouton m = new Mouton(10, 10, w);
        setSeason(w, 2); assertTrue(m.inMatingSeason(), "mouton : automne");
        setSeason(w, 3); assertFalse(m.inMatingSeason(), "mouton : pas en hiver");
    }

    @Test
    void oursEnRutAuPrintempsEtEnEte() {
        WorldOfCells w = flatWorld();
        Ours o = new Ours(10, 10, w);
        setSeason(w, 0); assertTrue(o.inMatingSeason(), "ours : printemps");
        setSeason(w, 1); assertTrue(o.inMatingSeason(), "ours : été");
        setSeason(w, 2); assertFalse(o.inMatingSeason(), "ours : pas en automne");
        setSeason(w, 3); assertFalse(o.inMatingSeason(), "ours : pas en hiver");
    }

    @Test
    void humainNonSaisonnier() {
        WorldOfCells w = flatWorld();
        Humain h = new Humain(10, 10, w);
        for (int idx = 0; idx < 4; idx++) { setSeason(w, idx); assertTrue(h.inMatingSeason(), "humain : toute saison"); }
    }

    @Test
    void inRutComposeSaisonEtAptitude() {
        WorldOfCells w = flatWorld();
        Loup l = new Loup(10, 10, w);
        l.energie = l.energieD;                  // fondateur adulte, rassasié → matingReady
        setSeason(w, 3); assertTrue(l.inRut(), "hiver + rassasié → en rut");
        setSeason(w, 1); assertFalse(l.inRut(), "été → hors saison → pas en rut");
        setSeason(w, 3);
        l.energie = (int) (l.energieD * 0.1);    // affamé → matingReady false
        assertFalse(l.inRut(), "hiver mais affamé → pas en rut");
    }
}
