package worlds;

import agents.AgentTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Eau qui s'écoule (L7) : ruissellement vers le voisin le plus bas, pooling en
 * cuvette, drainage par l'océan, source par la pluie. Pas de dépendance OpenGL.
 */
class WorldWaterTest {

    /** Aplanit une pente régulière décroissante vers l'EST sur une bande. */
    private void slopeEastward(WorldOfCells w, int cy) {
        for (int x = 10; x <= 20; x++)
            for (int dy = -1; dy <= 1; dy++)
                w.setCellHeight(x, cy + dy, 20.0 - (x - 10));   // 20 à x=10 → 10 à x=20
    }

    @Test
    void lEauCouleVersLeVoisinPlusBas() {
        WorldOfCells w = AgentTestSupport.buildWorld();
        int cy = 25;
        slopeEastward(w, cy);
        w.setRaining(false);

        // Dépose de l'eau sur la cellule haute (x=12).
        w.setWaterDepth(12, cy, 5.0);
        double startEast = w.getWaterDepth(13, cy);
        w.stepWater();

        assertTrue(w.getWaterDepth(13, cy) > startEast,
                "l'eau doit s'écouler vers la cellule plus basse à l'EST");
        assertTrue(w.getWaterDepth(12, cy) < 5.0,
                "la cellule source perd de l'eau en s'écoulant");
    }

    @Test
    void lEauSeConserveSurTerreHorsEvaporation() {
        WorldOfCells w = AgentTestSupport.buildWorld();
        int cy = 25;
        slopeEastward(w, cy);
        w.setRaining(false);
        w.setWaterDepth(12, cy, 5.0);

        double before = totalWaterOnLand(w);
        w.stepWater();
        double after = totalWaterOnLand(w);
        // Seule l'évaporation (très faible) réduit le total ; pas de création/perte
        // massive due au transfert (conservation à l'évaporation près).
        assertEquals(before, after, before * 0.05 + 0.5,
                "le transfert conserve l'eau (à l'évaporation près)");
    }

    @Test
    void lOceanDraineLEau() {
        WorldOfCells w = AgentTestSupport.buildWorld();
        int x = 30, y = 30;
        w.setCellHeight(x, y, -2.0);     // océan
        w.setWaterDepth(x, y, 4.0);
        w.setRaining(false);
        w.stepWater();
        assertEquals(0.0, w.getWaterDepth(x, y), 1e-9,
                "l'eau qui atteint l'océan (height<0) est absorbée");
    }

    @Test
    void laPluieAlimenteLeRuissellement() {
        WorldOfCells w = AgentTestSupport.buildWorld();
        int[] c = AgentTestSupport.findLandCell(w, 25, 25);
        AgentTestSupport.flattenLandArea(w, c[0], c[1], 2);
        w.setWaterDepth(c[0], c[1], 0.0);
        w.setRaining(true);
        w.stepWater();
        // Au moins une cellule de terre autour porte désormais de l'eau.
        double sum = 0;
        for (int dx = -2; dx <= 2; dx++)
            for (int dy = -2; dy <= 2; dy++)
                sum += w.getWaterDepth(c[0] + dx, c[1] + dy);
        assertTrue(sum > 0, "la pluie dépose de l'eau de ruissellement sur la terre");
    }

    @Test
    void uneCuvetteIsoleeSecheParEvaporationEnUnTempsRaisonnable() {
        WorldOfCells w = AgentTestSupport.buildWorld();
        int[] c = AgentTestSupport.findLandCell(w, 25, 25);
        int cx = c[0], cy = c[1];
        // Vraie cuvette AU-DESSUS du niveau de la mer : plateau haut tout autour,
        // fond bas mais positif → l'eau du centre n'a aucun voisin plus bas où
        // s'écouler ET n'est pas absorbée par l'océan : elle ne part QUE par évaporation.
        for (int dx = -3; dx <= 3; dx++)
            for (int dy = -3; dy <= 3; dy++)
                w.setCellHeight(cx + dx, cy + dy, 10.0);  // plateau haut
        w.setCellHeight(cx, cy, 0.5);                     // fond de cuvette (>0 = terre, pas océan)
        w.setRaining(false);
        w.setWaterDepth(cx, cy, 1.2);                // sol détrempé, orage qui vient de cesser
        // Le sol doit redevenir sec en au plus 30 s (600 ticks), pas ~96 min (1500 ticks).
        int ticks = 0;
        while (w.getWaterDepth(cx, cy) > 0.01 && ticks < 5000) { w.stepWater(); ticks++; }
        assertTrue(ticks <= 600,
                "une flaque piégée doit sécher en ~30 s (≤600 ticks), pas en heures : " + ticks);
    }

    @Test
    void leTintDEauResteDoux() {
        WorldOfCells w = AgentTestSupport.buildWorld();
        int cx = 25, cy = 25;
        // Sature l'eau sur le bloc échantillonné par applyWaterTint(cx,cy).
        for (int dx = -1; dx <= 0; dx++)
            for (int dy = -1; dy <= 0; dy++)
                w.setWaterDepth(cx + dx, cy + dy, 5.0);
        float[] col = { 0.20f, 0.60f, 0.20f };   // vert herbe
        w.applyWaterTint(cx, cy, col);
        // Même saturée, l'eau ne doit pas noyer la couleur du sol : le canal vert
        // (couleur dominante de l'herbe) garde la majeure partie de sa valeur.
        assertTrue(col[1] >= 0.45f,
                "le tint d'eau doit rester doux (sol reconnaissable), vert=" + col[1]);
    }

    private double totalWaterOnLand(WorldOfCells w) {
        double s = 0;
        for (int x = 0; x < AgentTestSupport.DX; x++)
            for (int y = 0; y < AgentTestSupport.DY; y++)
                if (w.getCellHeight(x, y) >= 0) s += w.getWaterDepth(x, y);
        return s;
    }
}
