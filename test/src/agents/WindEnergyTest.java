package agents;

import org.junit.jupiter.api.Test;
import worlds.WorldOfCells;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Découplage vent ↔ énergie. Le vent est une propulsion EXTERNE : il change la
 * DISTANCE parcourue par l'agent, mais pas l'effort propre de l'agent. Donc
 * l'énergie dépensée par unité de temps doit rester ~invariante au vent — dos au
 * vent on va plus loin pour la même énergie, face au vent moins loin pour la même
 * énergie. (Avant le correctif, la cadence accrue par le vent prélevait le coût
 * métabolique plus souvent → l'énergie/temps montait ~proportionnellement à la
 * vitesse, ce qui n'est pas physique.)
 *
 * Méthode : un mouton piloté plein Est sur terrain plat, vent fort plein Est
 * (dos au vent). On pilote l'itération à la main (cadence sans dérouler step()).
 */
class WindEnergyTest {

    private static final int TICKS = 6000;

    /** Mouton piloté plein Est sur terrain plat ; renvoie {énergie dépensée, nb de déplacements}. */
    private static double[] runEast(boolean wind) {
        WorldOfCells w = AgentTestSupport.buildWorld();
        int W = w.getWidth(), H = w.getHeight();
        for (int x = 0; x < W; x++)
            for (int y = 0; y < H; y++) {
                w.setCellHeight(x, y, 0.0);     // plat, hors eau : pas de coût terrain ni de pénalité d'altitude
                w.setForestCAValue(x, y, 0);    // pas d'arbre : le mode pilote évite la forêt (sinon bloqué)
            }
        if (wind) { w.setWindEnabled(true); w.setWindVector(0.0, 25.0); }  // plein Est (dos au vent), saturé à windF=1.4
        else      { w.setWindEnabled(false); }

        Mouton m = new Mouton(W / 2, H / 2, w);
        m.energie = 1_000_000;        // assez haut : jamais sous les seuils qui brident la vitesse
        m.playerControlled = true;
        m.controlDir = 1;             // 1 = Est
        m.controlDx = 1; m.controlDy = 0;

        double e0 = m.energie;
        int moves = 0, px = m.x, py = m.y;
        for (int t = 0; t < TICKS; t++) {
            w.setIteration(t);
            m.step();
            if (m.x != px || m.y != py) moves++;
            px = m.x; py = m.y;
        }
        return new double[]{ e0 - m.energie, moves };
    }

    @Test
    void dosAuVentMemeEnergiePlusDeDistance() {
        double[] sansVent = runEast(false);
        double[] dosVent  = runEast(true);
        System.out.println("[WindEnergy] sansVent energie=" + sansVent[0] + " moves=" + sansVent[1]
                + " | dosVent energie=" + dosVent[0] + " moves=" + dosVent[1]
                + " | ratio=" + (dosVent[0] / sansVent[0]));

        // 1) Le vent accroît la cadence → l'agent parcourt PLUS de distance.
        assertTrue(dosVent[1] > sansVent[1] * 1.2,
                "dos au vent => plus de deplacements (" + dosVent[1] + " vs " + sansVent[1] + ")");

        // 2) ... pour ~LA MEME energie (effort propre constant). Avant le correctif,
        //    le ratio montait avec la vitesse (~1.5) ; après, il reste proche de 1.
        double ratio = dosVent[0] / sansVent[0];
        assertEquals(1.0, ratio, 0.20,
                "energie depensee ~invariante au vent (ratio=" + ratio + ")");
    }
}
