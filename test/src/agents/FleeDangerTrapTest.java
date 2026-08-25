package agents;

import org.junit.jupiter.api.Test;
import worlds.WorldOfCells;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Fiabilisation des fuites de DANGER (feu/lave). ON_FIRE vise la case d'EAU la plus
 * proche via pursuitStep (détection de blocage + BFS élargi) → sortie GARANTIE des
 * pièges concaves, même un U profond. FLEE_LAVA utilise steerAroundObstacles
 * (anti-revisite). Un mouton en feu doit rejoindre l'eau au lieu de se consumer.
 */
class FleeDangerTrapTest {

    @Test
    void moutonEnFeuContourneUnObstaclePourAtteindreLEau() {
        int cx = 25, cy = 25;
        WorldOfCells w = AgentTestSupport.buildWorld();
        int W = w.getWidth(), H = w.getHeight();
        for (int x = 0; x < W; x++) for (int y = 0; y < H; y++) { w.setCellHeight(x, y, 0.5); w.setForestCAValue(x, y, 0); }
        // Mur d'arbres COURT entre le mouton et l'eau : le chemin direct (nord) est
        // bloqué, il faut contourner par un bout du mur. C'est exactement le cas où
        // dodgeObstacles peut osciller (N→E→N…) faute de mémoire, et où l'anti-revisite
        // de steerAroundObstacles fait progresser proprement vers l'eau.
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

    /** Garantie forte : un mouton en feu au fond d'un U PROFOND (issue hors vision)
     *  rejoint quand même l'eau située derrière le mur, grâce à la replanif élargie de
     *  pursuitStep. (Avec steerAroundObstacles seul, réactif, il restait piégé.) */
    @Test
    void moutonEnFeuSortDUnUProfondPourAtteindreLEau() {
        int cx = 25, cy = 25;
        WorldOfCells w = AgentTestSupport.buildWorld();
        int W = w.getWidth(), H = w.getHeight();
        for (int x = 0; x < W; x++) for (int y = 0; y < H; y++) { w.setCellHeight(x, y, 0.5); w.setForestCAValue(x, y, 0); }
        // U profond : mur de fond au NORD + deux bras est/ouest longs vers le sud.
        for (int x = cx - 6; x <= cx + 6; x++) w.setForestCAValue(x, cy - 2, 1);
        for (int y = cy - 2; y <= cy + 10; y++) { w.setForestCAValue(cx - 6, y, 1); w.setForestCAValue(cx + 6, y, 1); }
        // EAU au NORD, derrière le mur (atteignable seulement en sortant par le sud puis
        // en contournant un bras et en remontant à l'extérieur).
        for (int x = cx - 1; x <= cx + 1; x++) for (int y = cy - 6; y <= cy - 4; y++) w.setCellHeight(x, y, -1.0);

        Mouton m = new Mouton(cx, cy + 6, w); w.moutons.add(m);   // au fond de la poche
        m.setOnFire();

        boolean extinguished = false;
        for (int t = 0; t < 4000; t++) {
            m.energie = m.energieMAX;
            w.setIteration(t);
            m.step();
            if (!m.isOnFire()) { extinguished = true; break; }
        }
        assertTrue(extinguished,
                "le mouton en feu doit s'extraire du U profond et rejoindre l'eau derrière le mur");
    }
}
