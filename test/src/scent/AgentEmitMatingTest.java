package scent;

import agents.Loup;
import landscapegenerator.PerlinNoiseLandscapeGenerator;
import org.junit.jupiter.api.Test;
import worlds.WorldOfCells;
import static org.junit.jupiter.api.Assertions.*;

/** Un agent EN RUT dépose une odeur de séduction ; sinon non (sous-projet E). */
class AgentEmitMatingTest {

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
    void loupEnRutDeposeUneOdeurDeSeduction() {
        WorldOfCells w = flatWorld();
        setSeason(w, 3);                 // WINTER → saison du loup
        Loup l = new Loup(10, 10, w); l.energie = l.energieD;   // rassasié → en rut
        ScentField f = w.getScentField();
        l.emitScent(f, w.getIteration());
        // Un AUTRE agent (id 999) sent l'odeur de séduction du loup à sa position.
        assertTrue(f.matingIntensityAt(10, 10, w.getIteration(), ScentKind.LOUP, 999) > 0f,
                "loup en rut → odeur de séduction présente");
    }

    @Test
    void loupHorsSaisonNeDeposePasDeSeduction() {
        WorldOfCells w = flatWorld();
        setSeason(w, 1);                 // SUMMER → hors saison du loup
        Loup l = new Loup(10, 10, w); l.energie = l.energieD;
        ScentField f = w.getScentField();
        l.emitScent(f, w.getIteration());
        assertEquals(0f, f.matingIntensityAt(10, 10, w.getIteration(), ScentKind.LOUP, 999), 1e-6,
                "hors saison → pas d'odeur de séduction");
        assertTrue(f.sampleAt(10, 10, w.getIteration()).of(ScentKind.LOUP) > 0f,
                "mais l'odeur ORDINAIRE est toujours déposée");
    }
}
