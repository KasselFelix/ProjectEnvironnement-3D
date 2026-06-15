package scent;

/**
 * Un evenement d'emission d'odeur (modele lagrangien). Identite IMMUABLE
 * (emetteur, classe, famille, naissance, intensite de base, drapeau de
 * seduction) ; centre advecte {@code cx,cy} et sur-vieillissement
 * {@code extraAgeSec} MUTABLES, mis a jour par {@link ScentField#step}.
 */
public final class ScentPuff {
    public final int       emitterId;
    public final ScentKind kind;
    public final int       familyId;
    public final int       birthTick;
    public final float     baseIntensity;
    /** Sous-projet E : ce puff porte-t-il l'odeur de SEDUCTION (agent en rut) ? */
    public final boolean   mating;

    /** Centre courant (advecte par le vent), en coordonnees cellule. */
    public float cx, cy;
    /** Vieillissement supplementaire accumule (sec) — ex. au-dessus de l'eau. */
    public float extraAgeSec;

    public ScentPuff(int emitterId, ScentKind kind, int familyId,
                     int x0, int y0, int birthTick, float baseIntensity, boolean mating) {
        this.emitterId = emitterId;
        this.kind = kind;
        this.familyId = familyId;
        this.birthTick = birthTick;
        this.baseIntensity = baseIntensity;
        this.mating = mating;
        this.cx = x0;
        this.cy = y0;
        this.extraAgeSec = 0f;
    }
}
