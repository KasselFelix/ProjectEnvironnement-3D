package agents.ai;

import agents.AgentTestSupport;
import agents.Loup;
import agents.Mouton;
import org.junit.jupiter.api.Test;
import worlds.WorldOfCells;
import static org.junit.jupiter.api.Assertions.*;

class PerceptionTest {

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
