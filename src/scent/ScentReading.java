package scent;

/**
 * Resultat d'un echantillonnage du champ d'odeur en une cellule : intensite
 * cumulee PAR classe + l'emetteur dominant (le puff le plus intense au point lu).
 */
public final class ScentReading {
    private final float[]   perKind;            // indexe par ScentKind.ordinal()
    public final int        dominantEmitterId;
    public final int        dominantFamilyId;
    public final ScentKind  dominantKind;       // null si rien senti
    public final float      dominantIntensity;

    ScentReading(float[] perKind, int dominantEmitterId, int dominantFamilyId,
                 ScentKind dominantKind, float dominantIntensity) {
        this.perKind = perKind;
        this.dominantEmitterId = dominantEmitterId;
        this.dominantFamilyId = dominantFamilyId;
        this.dominantKind = dominantKind;
        this.dominantIntensity = dominantIntensity;
    }

    /** Intensite cumulee de la classe demandee (0 si absente). */
    public float of(ScentKind k) { return perKind[k.ordinal()]; }

    /** Acces direct au tableau par classe (somme pour l'overlay). */
    public float[] perKind() { return perKind; }
}
