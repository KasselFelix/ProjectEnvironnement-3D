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

    private SocialTrait social = SocialTrait.NONE;

    // Accumulateurs de la session courante.
    private int ticks;
    private int isolatedTicks;
    private double satisfactionSum;

    public SocialTrait social() {
        return social;
    }

    /** Enregistre un tick de la session : isolé ou non, et sa satisfaction. */
    public void observe(boolean isolated, double satisfaction) {
        ticks++;
        if (isolated) isolatedTicks++;
        satisfactionSum += satisfaction;
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
        reset();
    }

    private void reset() {
        ticks = 0;
        isolatedTicks = 0;
        satisfactionSum = 0.0;
    }
}
