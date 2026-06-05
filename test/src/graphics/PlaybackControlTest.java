package graphics;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Contrôle de lecture de la simulation (pause + vitesse), façon lecteur vidéo.
 * Logique pure (pas d'OpenGL) → testable directement.
 */
class PlaybackControlTest {

    @Test
    void demarreEnLectureVitesseUn() {
        PlaybackControl pc = new PlaybackControl();
        assertFalse(pc.isPaused());
        assertTrue(pc.isRunning());
        assertEquals(1, pc.multiplier());
    }

    @Test
    void togglePauseBasculeLEtat() {
        PlaybackControl pc = new PlaybackControl();
        pc.togglePause();
        assertTrue(pc.isPaused());
        assertFalse(pc.isRunning());
        pc.togglePause();
        assertFalse(pc.isPaused());
    }

    @Test
    void fasterCycleLesVitessesEtBoucle() {
        PlaybackControl pc = new PlaybackControl();
        assertEquals(1, pc.multiplier());
        pc.faster(); assertEquals(2, pc.multiplier());
        pc.faster(); assertEquals(4, pc.multiplier());
        pc.faster(); assertEquals(8, pc.multiplier());
        pc.faster(); assertEquals(1, pc.multiplier(), "boucle x8 -> x1");
    }

    @Test
    void accelererReprendAutomatiquementLaLecture() {
        PlaybackControl pc = new PlaybackControl();
        pc.togglePause();
        assertTrue(pc.isPaused());
        pc.faster();
        assertFalse(pc.isPaused(), "accelerer pendant la pause relance la lecture");
        assertEquals(2, pc.multiplier());
    }

    @Test
    void multiplicateurEffectifNulEnPause() {
        PlaybackControl pc = new PlaybackControl();
        pc.faster(); // x2
        assertEquals(2, pc.effectiveMultiplier());
        pc.togglePause();
        assertEquals(0, pc.effectiveMultiplier(), "en pause aucun step ne doit tourner");
    }

    @Test
    void libelleAsciiLisible() {
        PlaybackControl pc = new PlaybackControl();
        assertTrue(pc.statusLabel().chars().allMatch(c -> c < 128), "ASCII pur (GLUT bitmap)");
        assertTrue(pc.statusLabel().contains("x1"));
        pc.togglePause();
        assertTrue(pc.statusLabel().toUpperCase().contains("PAUSE"));
        pc.faster();
        assertTrue(pc.statusLabel().contains("x2"));
    }
}
