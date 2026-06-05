package agents.ai;

/**
 * Intelligence d'un agent vue comme un SCORE ∈ [0, 1] (cf. docs/evolution.txt
 * § 6) : poids sur la capacité d'améliorer ses autres compétences. Démarre selon
 * l'axe Intelligence, dégénère avec l'âge (atténué par la longévité) et est
 * entretenu par l'activité cognitive (entraînement cérébral).
 */
public final class Mind {

    /** Score de départ d'un axe NEUTRE. */
    public static final double BASE_SCORE = 0.5;
    /** Écart de score de départ par cran de l'axe Intelligence. */
    public static final double START_STEP = 0.2;
    /** Gain de score par unité d'activité cognitive (§ 6.2). */
    public static final double TRAIN_GAIN = 0.02;
    /** Dégénérescence de base par tick au grand âge (§ 6.2). */
    public static final double DEGEN_BASE = 0.03;

    private double score;

    public Mind(double start) {
        this.score = clamp01(start);
    }

    /** Score initial selon l'axe Intelligence du génome (§ 6.1). */
    public static double startScore(Genome genome) {
        return BASE_SCORE + genome.get(Axis.INTELLIGENCE).sign * START_STEP;
    }

    public static Mind fromGenome(Genome genome) {
        return new Mind(startScore(genome));
    }

    public double score() {
        return score;
    }

    /**
     * Poids d'apprentissage (§ 6.1) : multiplicateur sur la vitesse d'amélioration
     * des autres compétences. Centré sur 1.0 pour un esprit neutre (score 0.5),
     * &gt; 1 pour un intelligent, &lt; 1 pour un stupide.
     */
    public double learningRate() {
        return 0.5 + score;
    }

    /**
     * Avance l'esprit d'un tick (§ 6.2) : l'activité cognitive ajoute du score,
     * l'âge en retire (dégénérescence atténuée par la longévité). Borné [0, 1].
     *
     * @param activity        niveau d'activité cognitive ∈ [0, 1] sur la période
     * @param ageFraction     âge rapporté à la durée de vie ∈ [0, 1]
     * @param longevityFactor facteur de longévité du génome (1.0 neutre)
     */
    public void train(double activity, double ageFraction, double longevityFactor) {
        double degen = DEGEN_BASE * clamp01(ageFraction) / longevityFactor;
        score = clamp01(score + TRAIN_GAIN * clamp01(activity) - degen);
    }

    private static double clamp01(double v) {
        return Math.max(0.0, Math.min(1.0, v));
    }
}
