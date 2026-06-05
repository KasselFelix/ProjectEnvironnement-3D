package worlds;

import agents.AgentTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Saisons (L5) : cycle long PRINTEMPS→ÉTÉ→AUTOMNE→HIVER modulant la fertilité
 * des CA de végétation. Pas de dépendance OpenGL.
 */
class WorldSeasonTest {

    @Test
    void facteursDeFertiliteOrdonnes() {
        // La croissance ralentit de l'été à l'hiver, repart au printemps.
        assertTrue(Season.SPRING.fertilityFactor > Season.SUMMER.fertilityFactor);
        assertTrue(Season.SUMMER.fertilityFactor > Season.AUTUMN.fertilityFactor);
        assertTrue(Season.AUTUMN.fertilityFactor > Season.WINTER.fertilityFactor);
    }

    @Test
    void laSaisonAvanceAvecLesJours() {
        WorldOfCells w = AgentTestSupport.buildWorld();
        w.setDureeJour(1);          // un jour-jeu = 2 itérations
        w.setSeasonLengthDays(1);   // une saison = un jour-jeu

        assertEquals(0, w.getCurrentDay());
        assertEquals(Season.SPRING, w.currentSeason(), "jour 0 → printemps");

        // Avance jusqu'au jour 3 (hiver) — 6 itérations suffisent.
        int guard = 0;
        while (w.getCurrentDay() < 3 && guard++ < 100) w.step();
        assertEquals(Season.WINTER, w.currentSeason(), "jour 3 → hiver");
        assertTrue(w.seasonalFertility() < Season.SPRING.fertilityFactor,
                "la fertilité hivernale est inférieure au printemps");
    }

    @Test
    void seasonsDesactivablesEtePerpetuel() {
        WorldOfCells w = AgentTestSupport.buildWorld();
        w.setSeasonLengthDays(0);   // désactivé
        assertEquals(Season.SUMMER, w.currentSeason());
        assertEquals(1.0, w.seasonalFertility(), 1e-9);
    }

    /** {x,y} de la cellule terrestre de fertilité maximale (été perpétuel pour
     *  neutraliser la modulation saisonnière pendant la recherche). */
    private static int[] bestFertileCell(WorldOfCells w) {
        w.setSeasonLengthDays(0);
        double best = -1; int bx = 0, by = 0;
        for (int x = 0; x < AgentTestSupport.DX; x++)
            for (int y = 0; y < AgentTestSupport.DY; y++)
                if (w.getCellHeight(x, y) >= 0) {
                    double f = w.forestCA.fertility(x, y);
                    if (f > best) { best = f; bx = x; by = y; }
                }
        assertTrue(best > 0, "il doit exister une cellule de fertilité > 0");
        return new int[] { bx, by };
    }

    @Test
    void fertiliteForetModuleeParLaSaison() {
        WorldOfCells w = AgentTestSupport.buildWorld();
        // Cellule de fertilité maximale (garantie > 0 sur un Perlin 50²).
        int[] c = bestFertileCell(w);

        w.setSeasonLengthDays(0);                       // été perpétuel (×1.0)
        double fSummer = w.forestCA.fertility(c[0], c[1]);
        assertTrue(fSummer > 0, "la cellule de référence doit être fertile");

        w.setSeasonLengthDays(3);                       // itération 0 → printemps (×1.15)
        double fSpring = w.forestCA.fertility(c[0], c[1]);

        assertEquals(Season.SPRING.fertilityFactor, fSpring / fSummer, 1e-6,
                "la fertilité de la forêt doit être multipliée par le facteur saisonnier");
    }
}
