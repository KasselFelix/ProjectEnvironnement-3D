package worlds;

/**
 * Saison courante du monde (L5). Un « an » de jeu enchaîne PRINTEMPS → ÉTÉ →
 * AUTOMNE → HIVER, chacune durant {@code World.seasonLengthDays} jours-jeu. La
 * saison module la FERTILITÉ des automates de végétation (ForestCA / GrassCA) :
 * la croissance ralentit fortement l'hiver, explose au printemps. Elle porte
 * aussi un facteur de teinte pour le rendu de la végétation (verdoyant l'été,
 * jauni l'automne, terne l'hiver).
 */
public enum Season {
    SPRING(1.15, "Printemps"),
    SUMMER(1.00, "Ete"),
    AUTUMN(0.65, "Automne"),
    WINTER(0.30, "Hiver");

    /** Multiplicateur de fertilité appliqué aux CA de végétation. */
    public final double fertilityFactor;
    /** Libellé ASCII pour le HUD. */
    public final String label;

    Season(double fertilityFactor, String label) {
        this.fertilityFactor = fertilityFactor;
        this.label = label;
    }
}
