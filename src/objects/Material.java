package objects;

/**
 * Matériaux empilables dans une cellule (système Layer).
 *
 * Aujourd'hui : STONE, LAVA — couvre les besoins du volcanisme. Le reste de la
 * surface vivante (forêt, herbe, feu) reste géré par les CAs 2D dédiés
 * (ForestCA, GrassCA) — ils ne s'empilent pas, ils représentent un état de
 * surface au sommet du sol ou du tas de minéraux.
 *
 * Enum volontairement extensible : on ajoutera EARTH, SAND, BASALT, GRANITE,
 * BRICK, SNOW… au fil des besoins. Pour préserver la rétro-compat des
 * sauvegardes futures, ne PAS réordonner les valeurs existantes — toujours
 * append à la fin.
 */
public enum Material {
    /**
     * Pierre volcanique standard — la majorité des coulées qui durcissent
     * loin du cratère et en altitude moyenne donnent du STONE. Gris bleuté.
     * Densité 1.5 (≈ pierre froide, perte de gaz vs lave fondue).
     */
    STONE(1.5f),
    /**
     * Lave solidifiée dans l'eau. Verre vitreux noir violacé. Densité 1.2
     * (verre = pas autant compacté que pierre cristallisée).
     */
    OBSIDIAN(1.2f),
    /**
     * Lave solidifiée près du cratère. Cœur dense, refroidi sous pression.
     * Densité 1.7 — le matériau le plus lourd (gameplay : limite naturelle
     * sur ce qu'une éruption peut soulever quand le cône s'accumule).
     */
    BASALT(1.7f),
    /**
     * Lave solidifiée en haute altitude. Refroidissement lent, cristallisation
     * visible. Densité 1.4 (cristallisation grossière = un peu moins dense).
     */
    GRANITE(1.4f),
    /**
     * Lave fondue. Densité de référence 1.0 (par convention, tous les autres
     * matériaux sont exprimés relativement à elle). En réalité physique, la
     * lave est ~10% moins dense que la pierre froide équivalente (gaz dissous,
     * dilatation thermique) — on amplifie l'écart pour visibilité gameplay.
     */
    LAVA(1.0f);

    /** Densité normalisée (LAVA = 1.0). Utilisée pour l'équilibre hydrostatique
     *  d'injection au cratère : poids_colonne = somme(thickness × densité). */
    public final float density;

    Material(float density) {
        this.density = density;
    }
}
