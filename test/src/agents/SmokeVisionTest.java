package agents;

import agents.ai.Percept;
import agents.ai.Perception;
import cellularautomata.ecosystem.ForestCA;
import org.junit.jupiter.api.Test;
import worlds.WorldOfCells;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Fumée → visibilité réduite (L7). Un agent entouré de feu/lave voit moins loin :
 * une cible visible par temps clair disparaît dans la fumée.
 */
class SmokeVisionTest {

    @Test
    void laFumeeReduitLaPorteeDeVision() {
        WorldOfCells world = AgentTestSupport.buildWorld();
        int cx = 25, cy = 25;
        AgentTestSupport.flattenLandArea(world, cx, cy, 12);

        Mouton mouton = new Mouton(cx, cy, world);   // vision 10 par défaut
        world.moutons.add(mouton);
        world.agents.add(mouton);
        world.uniqueDynamicObjects.add(mouton);

        // Loup à 7 cases au Nord : dans la vision claire (10), hors de la vision
        // enfumée (10 × 0.5 = 5).
        Loup loup = new Loup(cx, (cy - 7 + world.getHeight()) % world.getHeight(), world);
        world.loups.add(loup);
        world.agents.add(loup);
        world.uniqueDynamicObjects.add(loup);

        // Temps clair : le mouton voit le loup.
        Percept clair = Perception.sense(mouton, world, world.loups, world.moutons);
        assertTrue(clair.predatorVisible(), "par temps clair le mouton voit le loup à 7 cases");

        // Un arbre en feu juste à côté du mouton → fumée → vision divisée par deux.
        world.setForestCAValue(cx + 1, cy, ForestCA.FIRE_FIRST);
        Percept enfume = Perception.sense(mouton, world, world.loups, world.moutons);
        assertFalse(enfume.predatorVisible(),
                "dans la fumée, la portée tombe à 5 → le loup à 7 cases n'est plus vu");
    }
}
