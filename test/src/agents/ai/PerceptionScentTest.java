package agents.ai;

import agents.Humain;
import agents.Loup;
import agents.Mouton;
import landscapegenerator.PerlinNoiseLandscapeGenerator;
import org.junit.jupiter.api.Test;
import worlds.WorldOfCells;
import static org.junit.jupiter.api.Assertions.*;

class PerceptionScentTest {

    private static final int DX_VIEW = 51, DY_VIEW = 51;
    private static final int DX = DX_VIEW - 1, DY = DY_VIEW - 1;

    private WorldOfCells flatWorld() {
        WorldOfCells world = new WorldOfCells();
        world.nbloups = 0; world.nbmoutons = 0; world.nbhumains = 0; world.nbours = 0;
        double[][] landscape = PerlinNoiseLandscapeGenerator
                .generatePerlinNoiseLandscape(DX_VIEW, DY_VIEW, 0.7, 0.4, 4);
        world.init(DX, DY, landscape);
        for (int x = 0; x < world.getWidth(); x++)
            for (int y = 0; y < world.getHeight(); y++)
                world.setCellHeight(x, y, 5.0);
        world.setWindEnabled(true);
        world.setWindVector(0.0, 0.0);
        world.setRaining(false);
        return world;
    }

    @Test
    void acuiteEffectiveBaseFoisGenome() {
        WorldOfCells w = flatWorld();
        Loup loup = new Loup(10, 10, w);
        assertEquals(1.0, Perception.olfactionAcuity(loup), 1e-9, "loup neutre = base 1.0");
        loup.genome.set(Axis.OLFACTION, Pole.POSITIVE);
        assertEquals(1.4, Perception.olfactionAcuity(loup), 1e-9, "loup NEZ FIN = 1.4");
    }

    @Test
    void humainSousLeGate() {
        WorldOfCells w = flatWorld();
        Humain h = new Humain(10, 10, w);
        assertTrue(Perception.olfactionAcuity(h) < ui.SimulationConfig.getInstance().olfactionGate,
                "l'humain (0.15) est sous le gate (0.3)");
    }

    @Test
    void loupSentLaProieAEst() {
        WorldOfCells w = flatWorld();
        Loup loup = new Loup(10, 10, w);
        int now = w.getIteration();
        // proie (MOUTON) déposée 4 cases à l'EST (= distance de sonde du loup acuité 1.0)
        w.getScentField().emit(999, scent.ScentKind.MOUTON, -1, 14, 10, now, 0.8f);
        Percept p = Perception.sense(loup, w, null, null);
        assertEquals(1, p.scentPreyDir, "la proie est sentie a l'EST (1)");
        assertTrue(p.scentPreyIntensity > 0, "intensite proie > 0");
        assertTrue(p.canSmell(), "le loup peut sentir");
    }

    @Test
    void humainNeSentRien() {
        WorldOfCells w = flatWorld();
        Humain h = new Humain(10, 10, w);
        int now = w.getIteration();
        w.getScentField().emit(999, scent.ScentKind.LOUP, -1, 12, 10, now, 1.0f);
        Percept p = Perception.sense(h, w, null, null);
        assertFalse(p.canSmell(), "humain sous le gate : anosmique");
        assertEquals(-1, p.scentDangerDir, "aucune direction de danger");
        assertEquals(0.0, p.scentDangerIntensity, 1e-9, "aucune intensite de danger");
    }

    @Test
    void seuilNezFinDetecteLeFaible() {
        WorldOfCells w = flatWorld();
        Loup fin = new Loup(10, 10, w);
        fin.genome.set(Axis.OLFACTION, Pole.POSITIVE);   // acuité 1.4 → seuil 0.107
        Loup anosmique = new Loup(10, 10, w);
        anosmique.genome.set(Axis.OLFACTION, Pole.NEGATIVE); // acuité 0.6 → seuil 0.25
        int now = w.getIteration();
        // trace MOUTON FAIBLE (0.15) à l'EST, à distance de sonde respective.
        // Le loup NEZ FIN sonde à round(1.4*4)=6 cases ; on dépose à 6.
        w.getScentField().emit(999, scent.ScentKind.MOUTON, -1, 16, 10, now, 0.15f);
        Percept pFin = Perception.sense(fin, w, null, null);
        assertTrue(pFin.scentPreyIntensity > 0, "le nez fin detecte la trace faible");
        // Le loup ANOSMIE sonde à round(0.6*4)=2 cases : la trace à 6 est hors de portée
        // ET sous son seuil → rien.
        Percept pAno = Perception.sense(anosmique, w, null, null);
        assertEquals(0.0, pAno.scentPreyIntensity, 1e-9, "l'anosmique ne detecte pas");
    }

    @Test
    void intensiteEncodeLaConcentration() {
        WorldOfCells w = flatWorld();
        Mouton m1 = new Mouton(10, 10, w);
        int now = w.getIteration();
        // 1 puff HUMAIN à l'EST (mouton acuité 0.4 → sonde round(0.4*4)=2)
        w.getScentField().emit(1, scent.ScentKind.HUMAIN, -1, 12, 10, now, 1.0f);
        double i1 = Perception.sense(m1, w, null, null).scentDangerIntensity;
        // un 2e puff HUMAIN empilé au même endroit
        Mouton m2 = new Mouton(10, 10, w);
        w.getScentField().emit(2, scent.ScentKind.HUMAIN, -1, 12, 10, now, 1.0f);
        double i2 = Perception.sense(m2, w, null, null).scentDangerIntensity;
        assertTrue(i2 > i1, "deux odeurs humaines empilees = intensite plus forte");
    }
}
