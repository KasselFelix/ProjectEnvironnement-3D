package agents;

import landscapegenerator.PerlinNoiseLandscapeGenerator;
import org.junit.jupiter.api.Test;
import agents.ai.Perception;
import worlds.WorldOfCells;
import static org.junit.jupiter.api.Assertions.*;

/** Broutage en brins du mouton (Phase 2). */
class MoutonBrinsGrazingTest {

    private WorldOfCells flatWorld() {
        WorldOfCells w = new WorldOfCells();
        w.nbloups = 0; w.nbmoutons = 0; w.nbhumains = 0; w.nbours = 0;
        double[][] ls = PerlinNoiseLandscapeGenerator.generatePerlinNoiseLandscape(51, 51, 0.7, 0.4, 4);
        w.init(50, 50, ls);
        for (int x = 0; x < w.getWidth(); x++)
            for (int y = 0; y < w.getHeight(); y++) w.setCellHeight(x, y, w.getMaxEverHeight() * 0.4);
        w.setWindEnabled(true); w.setWindVector(0.0, 0.0); w.setRaining(false);
        return w;
    }

    @Test
    void unBouchonRetireUnBrinEtDonneEnergie() {
        WorldOfCells w = flatWorld();
        w.setGrassBrins(10, 10, 5);                // setBrins pose aussi cellState=1 (invariant)
        Mouton m = new Mouton(10, 10, w);
        m.energie = (int) (m.energieMAX * 0.3);    // affamé → broute
        double e0 = m.energie; int b0 = w.getGrassBrins(10, 10);   // energie est un double
        m.postMove(Perception.sense(m, w, w.loups, w.moutons));
        assertEquals(b0 - 1, w.getGrassBrins(10, 10), "une bouchée retire 1 brin");
        assertEquals(e0 + (int) ui.SimulationConfig.getInstance().energyPerBrin, m.energie, 1e-6,
                "gain = energyPerBrin");
    }

    @Test
    void cooldownEmpechePlusDUneBouchéeParFenetre() {
        WorldOfCells w = flatWorld();
        w.setGrassBrins(10, 10, 5);                // setBrins pose aussi cellState=1 (invariant)
        Mouton m = new Mouton(10, 10, w);
        m.energie = (int) (m.energieMAX * 0.3);
        m.postMove(Perception.sense(m, w, w.loups, w.moutons));   // bouchée 1 → pose cooldown
        int bMid = w.getGrassBrins(10, 10);
        m.postMove(Perception.sense(m, w, w.loups, w.moutons));   // cooldown actif → pas de 2e bouchée
        assertEquals(bMid, w.getGrassBrins(10, 10), "le cooldown bloque la 2e bouchée immediate");
    }
}
