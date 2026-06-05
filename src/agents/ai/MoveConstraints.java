package agents.ai;

/** Ce qu'un déplacement a le droit de traverser ce tick. */
public final class MoveConstraints {
    public final boolean allowWater;   // eau franchissable (fuite/poursuite/recherche terre)
    public final boolean avoidForest;  // toujours true dans le code actuel
    public final boolean avoidLava;    // toujours true dans le code actuel

    public MoveConstraints(boolean allowWater, boolean avoidForest, boolean avoidLava) {
        this.allowWater = allowWater;
        this.avoidForest = avoidForest;
        this.avoidLava = avoidLava;
    }

    public static MoveConstraints landBound() { return new MoveConstraints(false, true, true); }
    public static MoveConstraints amphibious() { return new MoveConstraints(true, true, true); }

    /**
     * Contraintes du déplacement piloté par le joueur : l'eau ET la lave sont
     * franchissables (avec leurs conséquences — drain d'énergie, mort dans la
     * lave, gérées en postMove), mais les arbres restent solides. « Soumis à
     * l'environnement » au sens fort : le joueur peut commettre une erreur fatale.
     */
    public static MoveConstraints playerControlled() { return new MoveConstraints(true, true, false); }
}
