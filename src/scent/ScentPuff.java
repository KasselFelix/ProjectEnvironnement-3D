package scent;

/**
 * Un evenement d'emission d'odeur (modele lagrangien). Identite IMMUABLE
 * (emetteur, classe, famille, naissance, intensite de base) ; centre advecte
 * {@code cx,cy} et sur-vieillissement {@code extraAgeSec} MUTABLES, mis a jour
 * par {@link ScentField#step}. Intensite et etalement derivent de l'age a la
 * lecture (puff sans etat de diffusion a maintenir).
 */
public final class ScentPuff {
    public final int       emitterId;
    public final ScentKind kind;
    public final int       familyId;
    public final int       birthTick;
    public final float     baseIntensity;

    /** Centre courant (advecte par le vent), en coordonnees cellule. */
    public float cx, cy;
    /** Vieillissement supplementaire accumule (sec) — ex. au-dessus de l'eau. */
    public float extraAgeSec;

    public ScentPuff(int emitterId, ScentKind kind, int familyId,
                     int x0, int y0, int birthTick, float baseIntensity) {
        this.emitterId = emitterId;
        this.kind = kind;
        this.familyId = familyId;
        this.birthTick = birthTick;
        this.baseIntensity = baseIntensity;
        this.cx = x0;
        this.cy = y0;
        this.extraAgeSec = 0f;
    }
}
