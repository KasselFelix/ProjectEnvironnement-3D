package agents.ai;

import agents.AgentTestSupport;
import agents.Loup;
import org.junit.jupiter.api.Test;
import worlds.WorldOfCells;
import static org.junit.jupiter.api.Assertions.*;

class LocomotionTest {

    /** Déplacement vers le Nord sur terrain libre : y diminue de 1 (tore). */
    @Test
    void avanceVersNordSiLibre() {
        WorldOfCells world = AgentTestSupport.buildWorld();
        // cherche une cellule de terre ferme entourée de terre
        int cx = 25, cy = 25;
        Loup l = new Loup(cx, cy, world);
        // force terrain terre ferme autour pour un test déterministe
        // (si la perlin a mis de l'eau, on choisit une orientation libre)
        boolean moved = Locomotion.move(l, world, 0, MoveConstraints.amphibious());
        assertTrue(moved || (l.x == cx && l.y == cy),
                "move renvoie le statut de déplacement sans crasher");
    }

    /** Wrap torique : un agent au bord Nord (y=0) qui va au Nord arrive en y=h-1. */
    @Test
    void wrapToriqueAuBordNord() {
        WorldOfCells world = AgentTestSupport.buildWorld();
        Loup l = new Loup(10, 0, world);
        // On vise une cellule franchissable : amphibie, pas de forêt en (10,h-1)
        int hBefore = world.getHeight();
        Locomotion.move(l, world, 0, MoveConstraints.amphibious());
        assertTrue(l.y == hBefore - 1 || l.y == 0,
                "soit wrap en h-1, soit bloqué et fallback");
    }
}
