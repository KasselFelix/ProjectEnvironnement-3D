package graphics;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests du calcul des bornes de fenêtre de rendu (ViewWindow).
 * Unité pure : aucune dépendance OpenGL.
 */
class ViewWindowTest {

    @Test
    void rayonGrand_couvreToutLeDomaine() {
        int[] w = ViewWindow.of(200, 1000);
        assertEquals(0,   w[0], "lo doit être clampé à 0");
        assertEquals(199, w[1], "hi doit être clampé à dim-1");
    }

    @Test
    void rayonModeste_fenetreCentree() {
        int[] w = ViewWindow.of(200, 50);
        assertEquals(50,  w[0]);
        assertEquals(151, w[1]);
    }

    @Test
    void petitDomaine_clampeEnHaut() {
        int[] w = ViewWindow.of(10, 2);
        assertEquals(3, w[0]);
        assertEquals(8, w[1]);
    }

    @Test
    void rayonZero_fenetreMinimaleAuCentre() {
        int[] w = ViewWindow.of(200, 0);
        assertEquals(100, w[0]);
        assertEquals(101, w[1]);
        assertTrue(w[1] > w[0], "la fenêtre ne doit jamais être vide");
    }
}
