package agents.ai;

import agents.AgentTestSupport;
import agents.Loup;
import agents.Mouton;
import org.junit.jupiter.api.Test;
import worlds.WorldOfCells;
import static org.junit.jupiter.api.Assertions.*;

class PerceptionTest {

    /** navigate() (§ 9) : avec une probabilité d'erreur nulle (BON_SENS), le cap
     *  vers une cible lointaine est EXACT (== dirToCell). Avec une probabilité 1
     *  (très désorienté), il DÉVIE toujours d'un cran. Cible = soi → -1. */
    @Test
    void navigationLongueDistancePerturbeeParLOrientation() {
        WorldOfCells world = AgentTestSupport.buildWorld();
        Mouton m = new Mouton(10, 10, world);
        world.moutons.add(m); world.agents.add(m);
        int tx = 40, ty = 10;                       // cible lointaine à l'Est
        int base = Perception.dirToCell(m, world, tx, ty);

        java.util.Random rng = new java.util.Random(1);
        for (int i = 0; i < 50; i++) {
            assertEquals(base, Perception.navigate(m, world, tx, ty, 0.0, rng),
                    "erreur 0 → cap exact");
        }
        for (int i = 0; i < 50; i++) {
            int d = Perception.navigate(m, world, tx, ty, 1.0, rng);
            assertNotEquals(base, d, "erreur 1 → dévie toujours d'un cran");
            assertTrue(d >= 0 && d <= 3);
        }
        assertEquals(-1, Perception.navigate(m, world, 10, 10, 0.0, rng),
                "cible = position courante → pas de cap");
    }

    /** Un mouton doit percevoir un loup placé EN DIAGONALE (correctif C1). */
    @Test
    void moutonPercoitPredateurEnDiagonale() {
        WorldOfCells world = AgentTestSupport.buildWorld();
        int cx = 20, cy = 20;
        Mouton mouton = new Mouton(cx, cy, world);
        world.moutons.add(mouton); world.agents.add(mouton);
        // loup 2 cases au NE (dx=+2, dy=-2) : ni même ligne ni même colonne
        Loup loup = new Loup(cx + 2, cy - 2, world);
        world.loups.add(loup); world.agents.add(loup);

        Percept p = Perception.sense(mouton, world,
                /*predators*/ world.loups, /*prey*/ null);

        assertTrue(p.predatorVisible(), "le prédateur diagonal doit être vu");
        // tie |dx|==|dy|=2 → dominantDir renvoie Est ; on n'assert que la distance ~2.83 :
        // on exige au moins que ce ne soit pas -1 et que la dist soit ~2.83
        assertEquals(2.0 * Math.sqrt(2), p.predatorDist, 0.5);
    }

    /** Un prédateur hors du rayon de vision (10) ne doit PAS être perçu. */
    @Test
    void predateurHorsVisionNEstPasVu() {
        WorldOfCells world = AgentTestSupport.buildWorld();
        int cx = 20, cy = 20;
        Mouton mouton = new Mouton(cx, cy, world);   // vision = 10 par défaut
        world.moutons.add(mouton); world.agents.add(mouton);
        Loup loup = new Loup(cx + 12, cy, world);     // distance 12 > 10
        world.loups.add(loup); world.agents.add(loup);

        Percept p = Perception.sense(mouton, world, world.loups, null);

        assertFalse(p.predatorVisible(), "un loup à distance 12 dépasse la vision 10");
    }

    /** Un loup doit repérer le mouton le plus proche parmi plusieurs. */
    @Test
    void loupRepereLaProieLaPlusProche() {
        WorldOfCells world = AgentTestSupport.buildWorld();
        int cx = 15, cy = 15;
        Loup loup = new Loup(cx, cy, world);
        world.loups.add(loup); world.agents.add(loup);
        Mouton loin = new Mouton(cx + 5, cy, world);     // Est, dist 5
        Mouton pres = new Mouton(cx, cy - 2, world);      // Nord, dist 2
        world.moutons.add(loin); world.moutons.add(pres);
        world.agents.add(loin);  world.agents.add(pres);

        Percept p = Perception.sense(loup, world, null, world.moutons);

        assertTrue(p.preyVisible());
        assertEquals(0, p.preyDir, "la proie la plus proche est au Nord");
        assertEquals(2.0, p.preyDist, 0.01);
    }
}
