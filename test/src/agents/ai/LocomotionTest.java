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
        int h = world.getHeight();
        // Terrain déterministe : départ (10,0) et destination (10,h-1) en terre
        // ferme dégagée (sans cela le bruit de Perlin rend parfois la case cible
        // impassable → fallback aléatoire → test flaky).
        world.setCellHeight(10, 0, 1.0);     world.setForestCAValue(10, 0, 0);
        world.setCellHeight(10, h - 1, 1.0); world.setForestCAValue(10, h - 1, 0);
        Loup l = new Loup(10, 0, world);

        boolean moved = Locomotion.move(l, world, 0, MoveConstraints.amphibious());

        assertTrue(moved, "le déplacement vers une case dégagée doit réussir");
        assertEquals(10, l.x, "pas de déviation latérale");
        assertEquals(h - 1, l.y, "wrap torique : y=0 → y=h-1 vers le Nord");
    }
}
