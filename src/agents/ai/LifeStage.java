package agents.ai;

/**
 * Stade de vie d'un agent (cf. docs/evolution.txt § 10.1). Déduit de l'âge
 * rapporté à la durée de vie effective. Bébé et jeune ne se reproduisent pas ;
 * le vieux entre en sénescence.
 */
public enum LifeStage {
    BABY, JUVENILE, ADULT, OLD;

    // Seuils en fraction de la durée de vie (§ 10.1), calés sur la biologie du
    // mouton : sevrage ~3 %, maturité sexuelle ~6 %, sénescence ~80 %.
    public static final double WEAN_FRAC = 0.03;
    public static final double MATURITY_FRAC = 0.06;
    public static final double SENESCENCE_FRAC = 0.80;

    /** Durée de vie de référence pour les agents immortels (maxAgeDays ≤ 0). */
    public static final double REFERENCE_LIFESPAN_DAYS = 100.0;

    /**
     * Stade d'un agent d'âge {@code ageDays} pour une durée de vie
     * {@code lifespanDays}. Si la durée de vie est ≤ 0 (immortel), on rapporte
     * l'âge à une durée de vie de référence.
     */
    public static LifeStage of(double ageDays, double lifespanDays) {
        double lifespan = lifespanDays > 0 ? lifespanDays : REFERENCE_LIFESPAN_DAYS;
        double f = ageDays / lifespan;
        if (f < WEAN_FRAC)        return BABY;
        if (f < MATURITY_FRAC)    return JUVENILE;
        if (f < SENESCENCE_FRAC)  return ADULT;
        return OLD;
    }

    /** true si ce stade permet la reproduction (§ 10.1) : ADULTE et VIEUX
     *  (le VIEUX à taux réduit, cf. {@link #fertilityFactor()}). Bébé/jeune : non. */
    public boolean canReproduce() {
        return this == ADULT || this == OLD;
    }

    /** Taux de fertilité réduit pour le VIEUX en sénescence (§ 10.1) : ADULTE 1.0,
     *  VIEUX 0.5, bébé/jeune 0.0 (le gate dur reste {@link #canReproduce()}). */
    public double fertilityFactor() {
        if (this == ADULT) return 1.0;
        if (this == OLD)   return 0.5;
        return 0.0;
    }

    // ===== Courbe de croissance de la taille (§ 10.2) =====
    /** Taille linéaire du nouveau-né en fraction de l'adulte (~racine cubique du
     *  ratio de masse pour un mammifère). */
    public static final double BIRTH_SIZE_FRAC = 0.40;
    /** Raideur de la sigmoïde de Gompertz (atteint ~1.0 vers la maturité). */
    public static final double GROWTH_K = 4.0;

    /**
     * Facteur de croissance ∈ [BIRTH_SIZE_FRAC, 1.0] selon l'âge (modèle de
     * Gompertz, § 10.2) : {@code exp(ln(birthFrac) · exp(-K·u))} où
     * {@code u = ageDays / (maturité en jours)}. Vaut birthFrac à la naissance et
     * ~1.0 dès la maturité.
     */
    public static double gompertzGrowth(double ageDays, double lifespanDays) {
        double lifespan = lifespanDays > 0 ? lifespanDays : REFERENCE_LIFESPAN_DAYS;
        double maturityDays = MATURITY_FRAC * lifespan;
        double u = maturityDays > 0 ? Math.max(0.0, ageDays) / maturityDays : 0.0;
        double g = Math.exp(Math.log(BIRTH_SIZE_FRAC) * Math.exp(-GROWTH_K * u));
        return Math.max(BIRTH_SIZE_FRAC, Math.min(1.0, g));
    }
}
