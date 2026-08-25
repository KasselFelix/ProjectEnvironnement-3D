package agents;

import org.junit.jupiter.api.Test;
import worlds.WorldOfCells;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Effet du vent sur l'Humain + physique de traînée par espèce (masse + surface).
 * La résistance au vent = (masse / surface frontale) normalisée : l'Humain (debout,
 * grande voilure, masse modérée) est le PLUS sensible, l'Ours (lourd) le moins.
 */
class HumainWindTest {

    /** Dos au vent (Est), tous orientés Est : le facteur de poussée doit ordonner
     *  Humain > Mouton > Ours (du plus sensible au moins sensible). */
    @Test
    void sensibiliteAuVentParEspece() {
        WorldOfCells w = AgentTestSupport.buildWorld();
        w.setWindEnabled(true);
        w.setWindVector(0.0, 6.0);           // vent MODÉRÉ (sous le plafond 1.4 pour les 3 espèces,
                                             // sinon elles saturent toutes et l'ordre disparaît)

        Humain h = new Humain(5, 5, w);  h._orient = 1;   // 1 = Est (dos au vent)
        Mouton m = new Mouton(6, 6, w);  m._orient = 1;
        Ours   o = new Ours(7, 7, w);    o._orient = 1;

        double fh = h.windDragFactor(), fm = m.windDragFactor(), fo = o.windDragFactor();
        assertTrue(fo > 1.0, "dos au vent => boost > 1 (ours=" + fo + ")");
        assertTrue(fh > fm, "Humain plus poussé que mouton (" + fh + " > " + fm + ")");
        assertTrue(fm > fo, "Mouton plus poussé qu'ours (" + fm + " > " + fo + ")");
    }

    /** Pilote un Humain plein Est ou plein Ouest sur terrain plat, vent fort plein
     *  Est ; renvoie le nombre de déplacements sur {@value #TICKS} ticks. */
    private static final int TICKS = 6000;
    private static int distanceHumain(int controlDir) {   // 1 = Est (dos), 3 = Ouest (face)
        WorldOfCells w = AgentTestSupport.buildWorld();
        int W = w.getWidth(), H = w.getHeight();
        for (int x = 0; x < W; x++)
            for (int y = 0; y < H; y++) { w.setCellHeight(x, y, 0.0); w.setForestCAValue(x, y, 0); }
        w.setWindEnabled(true);
        w.setWindVector(0.0, 25.0);          // vent fort plein Est

        Humain h = new Humain(W / 2, H / 2, w);
        h.playerControlled = true;
        h.controlDir = controlDir;
        h.controlDx = (controlDir == 1) ? 1 : -1;
        h.controlDy = 0;

        int moves = 0, px = h.x, py = h.y;
        for (int t = 0; t < TICKS; t++) {
            w.setIteration(t);
            h.step();
            if (h.x != px || h.y != py) moves++;
            px = h.x; py = h.y;
        }
        return moves;
    }

    /** Le vent affecte désormais l'Humain : dos au vent il couvre plus de distance
     *  que face au vent (avant, l'Humain ignorait le vent → distances égales). */
    @Test
    void humainAvanceAvecLeVent() {
        int dos  = distanceHumain(1);   // Est = dos au vent
        int face = distanceHumain(3);   // Ouest = face au vent
        assertTrue(dos > face * 1.2,
                "dos au vent l'Humain va plus loin que face (" + dos + " vs " + face + ")");
    }
}
