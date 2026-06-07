package worlds;

/**
 * Saison courante du monde (L5). Un « an » de jeu enchaîne PRINTEMPS → ÉTÉ →
 * AUTOMNE → HIVER, chacune durant {@code World.seasonLengthDays} jours-jeu. La
 * saison module la FERTILITÉ des automates de végétation (ForestCA / GrassCA) :
 * la croissance ralentit fortement l'hiver, explose au printemps. Elle porte
 * aussi un facteur de teinte pour le rendu de la végétation (verdoyant l'été,
 * jauni l'automne, terne l'hiver) et un facteur de force du vent (plus fort en
 * automne/hiver, calme l'été).
 */
public enum Season {
    //      fert   label        rain  tempC   teinte feuillage (R,G,B)                  wind
    SPRING(1.15, "Printemps", 0.30,  8.0,  new float[]{0.55f, 0.95f, 0.45f}, 1.10),  // vert tendre
    SUMMER(1.00, "Ete",       0.10, 22.0,  new float[]{0.20f, 0.70f, 0.20f}, 0.80),  // vert profond
    AUTUMN(0.65, "Automne",   0.45, 10.0,  new float[]{0.85f, 0.55f, 0.15f}, 1.40),  // jaune-orangé
    WINTER(0.30, "Hiver",     0.35, -2.0,  new float[]{0.55f, 0.58f, 0.50f}, 1.30);  // terne / dégarni

    /** Multiplicateur de fertilité appliqué aux CA de végétation. */
    public final double fertilityFactor;
    /** Libellé ASCII pour le HUD. */
    public final String label;
    /** Probabilité qu'un jour de cette saison soit pluvieux (L6). */
    public final double rainProbability;
    /** Température de base de la saison en °C, à mi-journée (L6). Modulée par le
     *  cycle jour/nuit et la pluie dans {@code World.getTemperature()}. */
    public final double baseTemperatureC;
    /** Teinte de feuillage de la saison (V5/L5) : la végétation est blendée vers
     *  cette couleur au rendu (verdoyant l'été, jauni l'automne, terne l'hiver). */
    public final float[] foliageTint;
    /** Multiplicateur de force du vent par saison (automne/hiver venteux, été calme). */
    public final double windFactor;

    Season(double fertilityFactor, String label, double rainProbability,
           double baseTemperatureC, float[] foliageTint, double windFactor) {
        this.fertilityFactor = fertilityFactor;
        this.label = label;
        this.rainProbability = rainProbability;
        this.baseTemperatureC = baseTemperatureC;
        this.foliageTint = foliageTint;
        this.windFactor = windFactor;
    }
}
