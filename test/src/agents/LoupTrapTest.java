package agents;

import org.junit.jupiter.api.Test;
import worlds.WorldOfCells;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Piège concave (obstacle en U) pendant la CHASSE (HUNT). Un loup qui poursuit
 * gloutonnement une proie située derrière le fond d'une poche en U s'y enfonce et
 * s'y bloque : à chaque pas il vise la case la plus proche de la proie (le fond du
 * U), et l'issue (par le sud, autour des bras) sort de son champ de vision → le BFS
 * de contournement ne la trouve pas. La proie reste visible → il oscille au fond
 * indéfiniment sans jamais l'atteindre.
 *
 * Le correctif (détection de blocage → longe-mur d'évasion) doit lui permettre de
 * sortir de la poche et de rejoindre la proie.
 */
class LoupTrapTest {

    /** Construit un U de forêt (mur nord + deux bras sud longs) autour du centre,
     *  proie au nord juste derrière le mur, loup dans la poche. */
    private static WorldOfCells uTrapWorld(int cx, int cy, Loup[] outWolf, Mouton[] outPrey) {
        WorldOfCells w = AgentTestSupport.buildWorld();
        int W = w.getWidth(), H = w.getHeight();
        for (int x = 0; x < W; x++)
            for (int y = 0; y < H; y++) { w.setCellHeight(x, y, 0.0); w.setForestCAValue(x, y, 0); }

        // Mur de fond (nord) : y = cy-2, de x=cx-6 à cx+6.
        for (int x = cx - 6; x <= cx + 6; x++) w.setForestCAValue(x, cy - 2, 1);
        // Deux bras (est/ouest) descendant vers le sud, longs (> vision) : y de cy-2 à cy+10.
        for (int y = cy - 2; y <= cy + 10; y++) { w.setForestCAValue(cx - 6, y, 1); w.setForestCAValue(cx + 6, y, 1); }

        Loup loup = new Loup(cx, cy, w);
        loup.energie = (int) (loup.energieD * 0.6);   // affamé → chasse au sprint
        Mouton prey = new Mouton(cx, cy - 4, w);       // 4 cases au NORD, derrière le mur (visible : dist 4 ≤ vision 10)
        w.moutons.add(prey);

        outWolf[0] = loup; outPrey[0] = prey;
        return w;
    }

    @Test
    void loupSeSortDUnPiegeEnUEtRejointSaProie() {
        int cx = 25, cy = 25;
        Loup[] wolf = new Loup[1]; Mouton[] prey = new Mouton[1];
        WorldOfCells w = uTrapWorld(cx, cy, wolf, prey);
        Loup loup = wolf[0]; Mouton mouton = prey[0];

        double minDist = Double.MAX_VALUE;
        for (int t = 0; t < 4000; t++) {
            loup.energie = (int) (loup.energieD * 0.6);   // reste affamé (isole la nav, pas la famine)
            w.setIteration(t);
            loup.step();
            double d = w.distance(loup.x, loup.y, mouton.x, mouton.y);
            if (d < minDist) minDist = d;
            if (minDist <= 1.0) break;                     // a rejoint la proie
        }
        // Code actuel : bloqué au fond du U → minDist ≈ 3 (le mur l'empêche d'approcher).
        // Correctif : il contourne et rejoint la proie → minDist ≤ 1.
        assertTrue(minDist <= 1.5,
                "le loup doit sortir du piège en U et rejoindre sa proie (minDist=" + minDist + ")");
    }
}
