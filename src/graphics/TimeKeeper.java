package graphics;

/**
 * Gestionnaire de temps fixed-timestep avec accumulator (pattern Glenn Fiedler).
 *
 * À chaque appel de display(), on mesure le temps réel écoulé depuis le dernier
 * appel, on accumule, puis on exécute autant de steps de simulation que
 * nécessaire pour rattraper le wall clock (avec garde-fou MAX_STEPS_PER_FRAME).
 *
 * Conséquence : la simulation tourne TOUJOURS à simulationHz steps/seconde
 * peu importe le framerate de rendu. Si le rendu rame, on rattrape ; si le
 * rendu va vite, on saute des steps mais on n'en fait jamais plus que sim
 * ne le demande.
 */
public class TimeKeeper {

    /** Anti-spirale-de-mort : jamais plus de N steps par frame de rendu. */
    public static final int MAX_STEPS_PER_FRAME = 5;

    private long lastNowNs = 0;
    private double accumulatorSec = 0.0;
    private boolean initialized = false;

    /**
     * Appelé en début de display(). Retourne le nombre de steps de simulation
     * à exécuter cette frame.
     * @param simulationHz fréquence cible (lue depuis SimulationConfig)
     */
    public int stepsToRun(int simulationHz) {
        long now = System.nanoTime();
        if (!initialized) {
            lastNowNs = now;
            initialized = true;
            return 0;
        }
        double elapsedSec = (now - lastNowNs) / 1e9;
        lastNowNs = now;
        accumulatorSec += elapsedSec;

        double tickDurationSec = 1.0 / Math.max(1, simulationHz);
        int steps = 0;
        while (accumulatorSec >= tickDurationSec && steps < MAX_STEPS_PER_FRAME) {
            accumulatorSec -= tickDurationSec;
            steps++;
        }
        // Anti-spirale : si on a accumulé trop, on jette le surplus
        if (accumulatorSec > tickDurationSec * MAX_STEPS_PER_FRAME) {
            accumulatorSec = 0;
        }
        return steps;
    }

    /** Pour debug/tests : remet à zéro l'accumulator. */
    public void reset() {
        accumulatorSec = 0.0;
        initialized = false;
    }
}
