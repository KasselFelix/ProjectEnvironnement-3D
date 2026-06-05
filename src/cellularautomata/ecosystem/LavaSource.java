package cellularautomata.ecosystem;

/**
 * Source racine d'une éruption. Partagée par tous les blocs LAVA qui en
 * descendent (via leur LavaLineage). Porte la signature "chimique" tirée au
 * début de l'éruption : décalage de teinte et facteur de saturation qui
 * permettent de distinguer visuellement les coulées de différentes éruptions.
 *
 * Cas spécial `eruptionId == -1` : source virtuelle créée lors d'un merge
 * auto entre deux sources différentes (cf. World.pushLayer Option B).
 */
public class LavaSource {

    public final int sourceCellX, sourceCellY;
    public final int eruptionId;       // 1, 2, 3, ... ou -1 si mélange
    public final int birthTick;        // tick global de naissance
    public final float pressure;       // = currentPower de cette éruption

    /** Décalage de teinte HSV ∈ [-0.05, +0.05]. */
    public final float colorHueShift;

    /** Facteur multiplicatif de saturation HSV ∈ [0.9, 1.1]. */
    public final float colorSatFactor;

    public LavaSource(int sourceCellX, int sourceCellY, int eruptionId, int birthTick,
                      float pressure, float colorHueShift, float colorSatFactor) {
        this.sourceCellX = sourceCellX;
        this.sourceCellY = sourceCellY;
        this.eruptionId = eruptionId;
        this.birthTick = birthTick;
        this.pressure = pressure;
        this.colorHueShift = colorHueShift;
        this.colorSatFactor = colorSatFactor;
    }

    /**
     * Fabrique pour une nouvelle éruption naturelle (eruptionId > 0).
     * Randomise hueShift dans [-0.05, +0.05] et satFactor dans [0.9, 1.1].
     */
    public static LavaSource forEruption(int sourceX, int sourceY, int eruptionId,
                                          int birthTick, float pressure) {
        float hueShift = (float)((Math.random() - 0.5) * 0.10); // [-0.05, +0.05]
        float satFactor = 0.9f + (float)Math.random() * 0.2f;   // [0.9, 1.1]
        return new LavaSource(sourceX, sourceY, eruptionId, birthTick,
                              pressure, hueShift, satFactor);
    }

    /**
     * Fabrique pour un mélange pondéré par épaisseur (Option B du design).
     * Sources différentes qui fusionnent (typiquement éruption nouvelle sur lac
     * résiduel) créent une source virtuelle avec eruptionId=-1 et propriétés
     * moyennées selon les poids passés.
     */
    public static LavaSource blend(LavaSource a, LavaSource b,
                                    float weightA, float weightB, int currentTick) {
        float total = weightA + weightB;
        float wA, wB;
        if (total <= 0f) {
            // Cas pathologique : poids nuls → fallback à 50/50
            wA = wB = 0.5f;
        } else {
            wA = weightA / total;
            wB = weightB / total;
        }
        return new LavaSource(
                a.sourceCellX, a.sourceCellY,
                -1,
                currentTick,
                a.pressure * wA + b.pressure * wB,
                a.colorHueShift * wA + b.colorHueShift * wB,
                a.colorSatFactor * wA + b.colorSatFactor * wB
        );
    }
}
