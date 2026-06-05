package agents.ai;

import java.util.Arrays;

/**
 * Snapshot immuable de ce qu'un agent perçoit à un tick donné, produit en UN
 * seul balayage par Perception.sense. Les directions sont des orientations
 * 0=N/1=E/2=S/3=O vers la cible la plus proche ; -1 si rien en vue.
 */
public final class Percept {
    public final int predatorDir; public final double predatorDist;
    public final int preyDir;     public final double preyDist;
    public final int waterDir;    public final double waterDist;
    public final int landDir;     public final double landDist;
    public final int grassDir;    public final double grassDist;
    public final boolean fireAdjacent;
    public final boolean lavaAdjacent;
    public final boolean inWater;
    public final boolean onLava;
    /** [N,E,S,O] : true si la case cardinale est franchissable a priori
     *  (ni forêt, ni lave). La contrainte eau dépend de l'état → MoveConstraints. */
    public final boolean[] cardinalFree;

    public Percept(int predatorDir, double predatorDist,
                   int preyDir, double preyDist,
                   int waterDir, double waterDist,
                   int landDir, double landDist,
                   int grassDir, double grassDist,
                   boolean fireAdjacent, boolean lavaAdjacent,
                   boolean inWater, boolean onLava,
                   boolean[] cardinalFree) {
        this.predatorDir = predatorDir; this.predatorDist = predatorDist;
        this.preyDir = preyDir;         this.preyDist = preyDist;
        this.waterDir = waterDir;       this.waterDist = waterDist;
        this.landDir = landDir;         this.landDist = landDist;
        this.grassDir = grassDir;       this.grassDist = grassDist;
        this.fireAdjacent = fireAdjacent; this.lavaAdjacent = lavaAdjacent;
        this.inWater = inWater;         this.onLava = onLava;
        this.cardinalFree = Arrays.copyOf(cardinalFree, cardinalFree.length);
    }

    public boolean predatorVisible() { return predatorDir >= 0; }
    public boolean preyVisible()     { return preyDir >= 0; }
    public boolean grassVisible()    { return grassDir >= 0; }
}
