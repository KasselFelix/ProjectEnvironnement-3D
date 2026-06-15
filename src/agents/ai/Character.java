package agents.ai;

/**
 * Caractère social ÉMERGENT d'un agent (cf. docs/evolution.txt § 7). Aucun agent
 * n'en a à la naissance : il se développe par SESSION selon le comportement
 * soutenu et la SATISFACTION moyenne. Gagné si le comportement est marqué ET la
 * satisfaction haute ; perdu si le comportement change OU si la satisfaction
 * s'effondre (hystérésis S_LOSE &lt;&lt; S_GAIN pour éviter le yo-yo).
 */
public final class Character {

    /** Satisfaction moyenne minimale pour ACQUÉRIR un caractère (seuil haut). */
    public static final double S_GAIN = 0.6;
    /** Satisfaction moyenne sous laquelle on PERD un caractère (seuil bas). */
    public static final double S_LOSE = 0.3;
    /** Fraction de temps « marquée » requise pour acquérir (isolé ou groupé). */
    public static final double PROFILE_GAIN_FRAC = 0.5;
    /** Plancher de maintien du profil : sous lui, le trait est perdu. */
    public static final double PROFILE_KEEP_FLOOR = 0.3;
    /** Opportunités de risque minimales (cumulées, report inter-session) avant de
     *  décider l'axe boldness — évite de trancher sur un échantillon minuscule. */
    public static final int MIN_OPPORTUNITIES = 8;

    private SocialTrait social = SocialTrait.NONE;
    private BoldnessTrait boldness = BoldnessTrait.NONE;

    // Accumulateurs de la session courante.
    private int ticks;
    private int isolatedTicks;
    private double satisfactionSum;

    // Accumulateurs PROPRES à l'axe boldness : NON remis à zéro à chaque session
    // (report tant que MIN_OPPORTUNITIES n'est pas atteint, cf. endSession).
    private int riskOpportunities;
    private int riskTaken;
    private int boldTicks;
    private double boldSatSum;

    public SocialTrait social() {
        return social;
    }

    public BoldnessTrait boldness() {
        return boldness;
    }

    /** Facteur multiplicatif piloté par le trait : BOLD → 1+delta, CAUTIOUS → 1−delta,
     *  NONE → 1.0. Utilisé par les consommateurs pour moduler un seuil. */
    public double boldnessFactor(double delta) {
        switch (boldness) {
            case BOLD:     return 1.0 + delta;
            case CAUTIOUS: return 1.0 - delta;
            default:       return 1.0;
        }
    }

    /** Hook test/debug : force le trait (sinon il n'émerge que par sessions). */
    public void setBoldness(BoldnessTrait b) {
        this.boldness = b;
    }

    /** Enregistre un tick de la session : isolé ou non, et sa satisfaction. */
    public void observe(boolean isolated, double satisfaction) {
        ticks++;
        if (isolated) isolatedTicks++;
        satisfactionSum += satisfaction;
        // Suivi satisfaction pour l'axe boldness (compteurs reportés, cf. endSession).
        boldTicks++;
        boldSatSum += satisfaction;
    }

    /** Enregistre une occasion de risque et si l'agent l'a saisie (sous-projet D).
     *  Appelé chaque tick par trainMindAndCharacter ; ne compte que les opportunités. */
    public void observeRisk(boolean opportunity, boolean took) {
        if (opportunity) {
            riskOpportunities++;
            if (took) riskTaken++;
        }
    }

    /**
     * Clôt la session : décide d'acquérir / conserver / perdre le caractère
     * (§ 7.4), puis remet les accumulateurs à zéro. Sans observation, ne fait rien.
     */
    public void endSession() {
        if (ticks == 0) return;
        double isolatedFrac = (double) isolatedTicks / ticks;
        double groupedFrac = 1.0 - isolatedFrac;
        double avgSatisfaction = satisfactionSum / ticks;

        switch (social) {
            case SOLITARY:
                if (isolatedFrac < PROFILE_KEEP_FLOOR || avgSatisfaction <= S_LOSE)
                    social = SocialTrait.NONE;
                break;
            case GREGARIOUS:
                if (groupedFrac < PROFILE_KEEP_FLOOR || avgSatisfaction <= S_LOSE)
                    social = SocialTrait.NONE;
                break;
            case NONE:
            default:
                if (avgSatisfaction >= S_GAIN) {
                    if (isolatedFrac >= PROFILE_GAIN_FRAC) social = SocialTrait.SOLITARY;
                    else if (groupedFrac >= PROFILE_GAIN_FRAC) social = SocialTrait.GREGARIOUS;
                }
                break;
        }
        // Axe boldness (sous-projet D) : décidé seulement quand assez d'opportunités
        // CUMULÉES ; sinon on reporte (compteurs non remis à zéro). Hystérésis miroir.
        if (riskOpportunities >= MIN_OPPORTUNITIES && boldTicks > 0) {
            double boldFrac = (double) riskTaken / riskOpportunities;
            double avgBoldSat = boldSatSum / boldTicks;
            switch (boldness) {
                case BOLD:
                    if (boldFrac < PROFILE_KEEP_FLOOR || avgBoldSat <= S_LOSE)
                        boldness = BoldnessTrait.NONE;
                    break;
                case CAUTIOUS:
                    if (boldFrac > 1.0 - PROFILE_KEEP_FLOOR || avgBoldSat <= S_LOSE)
                        boldness = BoldnessTrait.NONE;
                    break;
                case NONE:
                default:
                    if (avgBoldSat >= S_GAIN) {
                        boldness = (boldFrac >= PROFILE_GAIN_FRAC)
                                ? BoldnessTrait.BOLD : BoldnessTrait.CAUTIOUS;
                    }
                    break;
            }
            riskOpportunities = 0; riskTaken = 0; boldTicks = 0; boldSatSum = 0.0;
        }
        reset();
    }

    private void reset() {
        ticks = 0;
        isolatedTicks = 0;
        satisfactionSum = 0.0;
    }
}
