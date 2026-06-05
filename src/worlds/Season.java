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
    SPRING(1.15, "Printemps", 0.30,  8.0),
    SUMMER(1.00, "Ete",       0.10, 22.0),
    AUTUMN(0.65, "Automne",   0.45, 10.0),
    WINTER(0.30, "Hiver",     0.35, -2.0);

    /** Multiplicateur de fertilité appliqué aux CA de végétation. */
    public final double fertilityFactor;
    /** Libellé ASCII pour le HUD. */
    public final String label;
    /** Probabilité qu'un jour de cette saison soit pluvieux (L6). */
    public final double rainProbability;
    /** Température de base de la saison en °C, à mi-journée (L6). Modulée par le
     *  cycle jour/nuit et la pluie dans {@code World.getTemperature()}. */
    public final double baseTemperatureC;

    Season(double fertilityFactor, String label, double rainProbability, double baseTemperatureC) {
        this.fertilityFactor = fertilityFactor;
        this.label = label;
        this.rainProbability = rainProbability;
        this.baseTemperatureC = baseTemperatureC;
    }
}
