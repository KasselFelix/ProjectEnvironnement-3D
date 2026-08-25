package objects.dynamic;

import agents.AgentTestSupport;
import objects.Material;
import org.junit.jupiter.api.Test;
import worlds.WorldOfCells;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Rebond des bombes de lave (V8) : une bombe LAVA avec bouncesLeft>0 ne se pose
 * pas net — elle relance un projectile-enfant qui repart en avant. Pas de rendu.
 */
class TephraBounceTest {

    @Test
    void uneBombeDeLaveRebonditAvantDeSePoser() {
        WorldOfCells w = AgentTestSupport.buildWorld();
        AgentTestSupport.flattenLandArea(w, 25, 25, 8);
        int before = w.uniqueDynamicObjects.size();

        // Bombe LAVA volant de (20,25) vers (28,25), 3 ticks de vol, 1 rebond.
        TephraProjectile bomb = new TephraProjectile(w,
                20f, 25f, 5f, 28f, 25f, 1f, 28, 25,
                1f, 4f, 3, Material.LAVA, null);
        bomb.bouncesLeft = 1;
        w.uniqueDynamicObjects.add(bomb);

        // Avance jusqu'à la fin du vol → doit rebondir (enfant ajouté, parent mort).
        for (int k = 0; k < 3; k++) bomb.step();

        assertFalse(bomb._alive, "la bombe parent meurt après son arc");
        // Un projectile-enfant a été ajouté (rebond) : taille de liste augmentée.
        assertTrue(w.uniqueDynamicObjects.size() > before + 1,
                "le rebond doit avoir relancé un projectile-enfant");

        // L'enfant a un rebond de moins.
        TephraProjectile child = null;
        for (objects.UniqueDynamicObject o : w.uniqueDynamicObjects) {
            if (o instanceof TephraProjectile && o != bomb) child = (TephraProjectile) o;
        }
        assertNotNull(child, "un projectile-enfant existe");
        assertEquals(0, child.bouncesLeft, "l'enfant a un rebond de moins (0)");
    }
}
