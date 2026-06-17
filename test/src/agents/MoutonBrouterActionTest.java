package agents;

import input.HotbarAction;
import landscapegenerator.PerlinNoiseLandscapeGenerator;
import org.junit.jupiter.api.Test;
import worlds.WorldOfCells;
import static org.junit.jupiter.api.Assertions.*;

/** Action BROUTER en mode contrôle (Phase 2). */
class MoutonBrouterActionTest {

    private WorldOfCells flatWorld() {
        WorldOfCells w = new WorldOfCells();
        w.nbloups = 0; w.nbmoutons = 0; w.nbhumains = 0; w.nbours = 0;
        double[][] ls = PerlinNoiseLandscapeGenerator.generatePerlinNoiseLandscape(51, 51, 0.7, 0.4, 4);
        w.init(50, 50, ls);
        for (int x = 0; x < w.getWidth(); x++)
            for (int y = 0; y < w.getHeight(); y++) w.setCellHeight(x, y, w.getMaxEverHeight() * 0.4);
        return w;
    }

    @Test
    void brouterEstImplementeEtDisponibleSurHerbe() {
        assertTrue(HotbarAction.BROUTER.isImplemented(), "BROUTER implementee");
        WorldOfCells w = flatWorld();
        w.setGrassBrins(10, 10, 3);
        Mouton m = new Mouton(10, 10, w);
        m.energie = (int) (m.energieMAX * 0.3);
        assertTrue(HotbarAction.BROUTER.available(m), "disponible : herbe adjacente + cooldown pret");
    }

    @Test
    void brouterPoseLeFlagJoueur() {
        WorldOfCells w = flatWorld();
        w.setGrassBrins(10, 10, 3);
        Mouton m = new Mouton(10, 10, w);
        m.energie = (int) (m.energieMAX * 0.3);
        HotbarAction.BROUTER.execute(m);
        assertTrue(m.playerWantsGraze, "execute pose playerWantsGraze");
    }
}
