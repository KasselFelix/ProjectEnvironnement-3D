package objects;

/**
 * Une couche d'un matériau empilée dans une cellule. Système Layer (cf. plan
 * de refactor) : chaque cellule porte une `List<Layer>` ordonnée du bas vers
 * le haut.
 *
 * Mutable volontairement — la simulation modifie `state` à chaque step pour
 * la lave en cours de solidification, et fusionne les couches consécutives
 * du même matériau en augmentant `thickness`. POJO simple sans accesseurs
 * superflus : on accède directement aux champs publics (un POJO de data).
 */
public class Layer {

    public Material material;

    /** Épaisseur en unités-monde (ex. ~3.0 pour une coulée fraîche au centre). */
    public float thickness;

    /**
     * État interne, interprété selon `material`.
     *  - LAVA : cellState ∈ [1, solidifyEnd] (1 = lave fraîche, solidifyEnd = pierre).
     *  - STONE et autres matériaux statiques : ignoré (laisser à 0).
     */
    public int state;

    /**
     * Si vrai, cette couche ne sera jamais transformée par les processus de
     * solidification / fonte / érosion. Marqué à la naissance (cf.
     * LavaCA.pushLaveAndIgnite pour la lave dans la zone du cratère). N'a pas
     * de signification pour les couches non-LAVA mais reste benign (jamais lu
     * hors LAVA).
     */
    public boolean persistent = false;

    /**
     * Parenté pour blocs LAVA et leur descendance solidifiée (Module 3, refonte
     * 2026-05). Non-null si material == LAVA. Conservé après solidification
     * (Module 4 lit la signature couleur de la source pour teinter le minéral).
     */
    public LavaLineage lineage = null;

    public Layer(Material material, float thickness, int state) {
        this.material = material;
        this.thickness = thickness;
        this.state = state;
    }

    public Layer(Material material, float thickness, int state, LavaLineage lineage) {
        this(material, thickness, state);
        this.lineage = lineage;
    }
}
