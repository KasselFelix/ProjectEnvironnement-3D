package graphics;

import java.util.function.LongSupplier;

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
 *
 * L'accumulateur se compte en ticks (et non en secondes) : sa partie
 * fractionnaire est {@link #alpha()}, la fraction du tick en cours que le rendu
 * interpole. Compté ainsi, alpha ne saute pas quand la fréquence change (x1 → x8) ;
 * en pause, {@link #hold()} avance l'horloge sans rien accumuler, donc alpha
 * reste figé puis repart de la même valeur à la reprise.
 */
public class TimeKeeper {

    /** Anti-spirale-de-mort : jamais plus de N steps par frame de rendu. */
    public static final int MAX_STEPS_PER_FRAME = 5;

    /** Plus grande valeur rendue par {@link #alpha()} : strictement sous 1. */
    private static final double ALPHA_MAX = Math.nextDown(1.0);

    private final LongSupplier clockNs;
    private long lastNowNs = 0;
    private double accumulatorTicks = 0.0;
    private boolean initialized = false;

    public TimeKeeper() {
        this(System::nanoTime);
    }

    /** Horloge injectée (nanosecondes), pour les tests. */
    TimeKeeper(LongSupplier clockNs) {
        this.clockNs = clockNs;
    }

    /**
     * Appelé en début de display(). Retourne le nombre de steps de simulation
     * à exécuter cette frame.
     * @param simulationHz fréquence cible (lue depuis SimulationConfig)
     */
    public int stepsToRun(int simulationHz) {
        return stepsToRun(simulationHz, MAX_STEPS_PER_FRAME);
    }

    /**
     * Variante avec cap explicite : l'accélération (x2/x4/x8) relève le plafond
     * de steps/frame proportionnellement pour ne pas être bridée par la garde
     * anti-spirale calibrée pour la vitesse x1.
     * @param maxStepsPerFrame plafond de steps exécutés cette frame (≥ 1)
     */
    public int stepsToRun(int simulationHz, int maxStepsPerFrame) {
        int cap = Math.max(1, maxStepsPerFrame);
        long now = clockNs.getAsLong();
        if (!initialized) {
            lastNowNs = now;
            initialized = true;
            return 0;
        }
        double elapsedSec = (now - lastNowNs) / 1e9;
        lastNowNs = now;
        accumulatorTicks += elapsedSec * Math.max(1, simulationHz);

        int steps = 0;
        while (accumulatorTicks >= 1.0 && steps < cap) {
            accumulatorTicks -= 1.0;
            steps++;
        }
        // Anti-spirale : si on a accumulé trop, on jette le surplus de ticks entiers,
        // en gardant la fraction (alpha) pour que le rendu ne perde pas sa phase.
        if (accumulatorTicks > cap) {
            accumulatorTicks -= Math.floor(accumulatorTicks);
        }
        return steps;
    }

    /**
     * En pause, à la place de {@link #stepsToRun} : l'horloge avance (le temps
     * passé en pause ne sera pas rattrapé à la reprise) sans rien accumuler, donc
     * {@link #alpha()} ne bouge pas.
     */
    public void hold() {
        lastNowNs = clockNs.getAsLong();
        initialized = true;
    }

    /**
     * Fraction du tick en cours, dans [0, 1) : ce que l'accumulateur garde après
     * les steps de la frame. Plafonnée sous 1 quand le plafond de steps laisse un
     * retard (le rendu reste alors au bout du tick, il ne déborde pas sur le suivant).
     */
    public double alpha() {
        return Math.min(Math.max(accumulatorTicks, 0.0), ALPHA_MAX);
    }

    /** Pour debug/tests : remet à zéro l'accumulator. */
    public void reset() {
        accumulatorTicks = 0.0;
        initialized = false;
    }
}
