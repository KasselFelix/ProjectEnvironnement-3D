package graphics;

/**
 * Contrôle de lecture de la simulation, façon lecteur vidéo : pause/lecture et
 * accélération (x1 → x2 → x4 → x8, en boucle). Pilote le nombre de steps de
 * simulation exécutés par {@link TimeKeeper} sans toucher au rendu : en pause la
 * caméra reste libre, seule l'avancée du monde (world.step) est gelée.
 *
 * Logique pure (aucun appel OpenGL) → testée par {@code PlaybackControlTest}.
 */
public class PlaybackControl {

    /** Paliers de vitesse proposés (multiplicateurs de la fréquence de sim). */
    private static final int[] SPEEDS = { 1, 2, 4, 8 };

    private int speedIndex = 0;
    private boolean paused = false;

    /** True si la simulation est en pause (aucun step ne tourne). */
    public boolean isPaused() { return paused; }

    /** True si la simulation avance (équivalent {@code !isPaused()}). */
    public boolean isRunning() { return !paused; }

    /** Multiplicateur de vitesse courant (x1/x2/x4/x8), indépendant de la pause. */
    public int multiplier() { return SPEEDS[speedIndex]; }

    /** Multiplicateur EFFECTIF appliqué aux steps : 0 en pause, sinon {@link #multiplier()}. */
    public int effectiveMultiplier() { return paused ? 0 : multiplier(); }

    /** Bascule pause ⇄ lecture. */
    public void togglePause() { paused = !paused; }

    /**
     * Monte d'un palier de vitesse (boucle x8 → x1). Reprend automatiquement la
     * lecture si on était en pause — appuyer sur « avance rapide » relance.
     */
    public void faster() {
        speedIndex = (speedIndex + 1) % SPEEDS.length;
        paused = false;
    }

    /** Libellé ASCII compact pour le HUD (GLUT bitmap = ASCII uniquement). */
    public String statusLabel() {
        if (paused) return "|| PAUSE (x" + multiplier() + ")";
        return (multiplier() == 1 ? "> x1" : ">> x" + multiplier());
    }
}
