package agents;

import org.junit.jupiter.api.Test;
import worlds.WorldOfCells;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Fiabilisation des fuites de DANGER (feu/lave) dans une concavité : les états
 * ON_FIRE / FLEE_LAVA utilisent steerAroundObstacles (anti-revisite) au lieu d'un
 * dodge réactif, pour ne pas dithérer au fond d'une poche en U. Ici un mouton en
 * feu, coincé dans un U, doit contourner l'obstacle pour rejoindre l'eau (qui
 * éteint le feu) au lieu de tourner en rond jusqu'à se consumer.
 */
class FleeDangerTrapTest {

    @Test
    void moutonEnFeuContourneUnObstaclePourAtteindreLEau() {
        int cx = 25, cy = 25;
        WorldOfCells w = AgentTestSupport.buildWorld();
        int W = w.getWidth(), H = w.getHeight();
        for (int x = 0; x < W; x++) for (int y = 0; y < H; y++) { w.setCellHeight(x, y, 0.5); w.setForestCAValue(x, y, 0); }
        // Mur d'arbres COURT entre le mouton et l'eau (concavité modérée, contournable
        // dans le champ de vision — c'est là que l'anti-revisite évite de dithérer).
        for (int x = cx - 3; x <= cx + 3; x++) w.setForestCAValue(x, cy - 2, 1);
        // Bassin d'EAU au NORD, derrière le mur court (le mouton doit contourner par un
        // bout du mur pour l'atteindre).
        for (int x = cx - 1; x <= cx + 1; x++) for (int y = cy - 5; y <= cy - 4; y++) w.setCellHeight(x, y, -1.0);

        Mouton m = new Mouton(cx, cy, w); w.moutons.add(m);
        m.setOnFire();   // déclenche l'état ON_FIRE

        boolean extinguished = false;
        for (int t = 0; t < 3000; t++) {
            m.energie = m.energieMAX;   // isole la navigation (le feu ne le tue pas avant d'arriver)
            w.setIteration(t);
            m.step();
            if (!m.isOnFire()) { extinguished = true; break; }
        }
        assertTrue(extinguished,
                "le mouton en feu doit contourner l'obstacle et atteindre l'eau (feu éteint), pas dithérer devant le mur");
    }
}
