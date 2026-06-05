package objects;

import cellularautomata.ecosystem.LavaSource;

/**
 * Parenté d'un bloc LAVA dans la chaîne de propagation.
 *  - source : référence forte vers l'éruption d'origine
 *  - parentCell : cellule d'où ce flow est venu (-1,-1 pour les blocs SOURCE
 *    directement injectés au cratère)
 *  - generation : 0 pour SOURCE, n+1 pour un bloc né d'un parent gen=n
 *
 * Conservé après solidification LAVA → STONE (cf. Module 4) : le rendu
 * utilise toujours la signature couleur de la source pour teinter le minéral.
 */
public class LavaLineage {

    public LavaSource source;
    public int parentCellX, parentCellY;
    public int generation;

    public LavaLineage(LavaSource source, int parentCellX, int parentCellY, int generation) {
        this.source = source;
        this.parentCellX = parentCellX;
        this.parentCellY = parentCellY;
        this.generation = generation;
    }

    /** Fabrique pour un bloc SOURCE (directement injecté au cratère). */
    public static LavaLineage forSource(LavaSource source) {
        return new LavaLineage(source, -1, -1, 0);
    }

    /** Fabrique pour un bloc dérivé d'un parent à (px, py) de génération parentGen. */
    public static LavaLineage derived(LavaSource source, int px, int py, int parentGen) {
        return new LavaLineage(source, px, py, parentGen + 1);
    }

    public boolean isSource() {
        return generation == 0 && parentCellX == -1 && parentCellY == -1;
    }
}
