package agents.ai;

/**
 * Les axes génétiques d'un agent (cf. docs/evolution.txt § 4). Chaque axe est un
 * unique emplacement à trois états (cf. {@link Pole}) : impossible d'être à la
 * fois au pôle POSITIF et NÉGATIF d'un même axe (exclusion mutuelle).
 */
public enum Axis {
    INTELLIGENCE,
    FERTILITY,
    ENDURANCE,
    STRENGTH,
    WISDOM,
    LONGEVITY,
    ORIENTATION,
    OLFACTION;
}
