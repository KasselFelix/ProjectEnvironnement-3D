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
    /** Cellule de la proie la plus proche en vue (pour le pathfinding) ; -1 si aucune. */
    public final int preyX, preyY;
    /** Carcasse comestible la plus proche en vue ; carcassDir == -1 si aucune. */
    public final int carcassDir;  public final double carcassDist;
    public final int carcassX, carcassY;
    public final int waterDir;    public final double waterDist;
    public final int landDir;     public final double landDist;
    public final int grassDir;    public final double grassDist;
    /** Direction vers la coulée de LAVE la plus proche en vue (L2) ; -1 si aucune. */
    public final int lavaDir;     public final double lavaDist;
    public final boolean fireAdjacent;
    public final boolean lavaAdjacent;
    public final boolean inWater;
    public final boolean onLava;
    /** [N,E,S,O] : true si la case cardinale est franchissable a priori
     *  (ni forêt, ni lave). La contrainte eau dépend de l'état → MoveConstraints. */
    public final boolean[] cardinalFree;

    // ── Olfaction (sous-projet B). dir = 0=N/1=E/2=S/3=O vers le pic senti ;
    //    -1 si rien (ou senti sur place sans direction). intensity = pic perçu.
    public final int    scentPreyDir;     public final double scentPreyIntensity;
    public final int    scentDangerDir;   public final double scentDangerIntensity;
    public final int    scentCarcassDir;  public final double scentCarcassIntensity;
    /** Sous-projet E : direction/intensité de l'odeur de SÉDUCTION d'un congénère
     *  (canal de la propre espèce, hors soi). dir -1 si rien ; intensity = pic. */
    public final int    scentMateDir;     public final double scentMateIntensity;
    /** Acuité olfactive effective de l'agent. Un agent sous le gate garde sa
     *  vraie acuité (faible, p.ex. 0.15 pour l'humain) ; seul un non-agent vaut 0. */
    public final double olfactionAcuity;
    /** Gelé à la construction (= au moment du sense) pour rester cohérent avec
     *  le gate utilisé lors de l'échantillonnage ; cf. {@link #canSmell()}. */
    private final boolean canSmellFrozen;

    public Percept(int predatorDir, double predatorDist,
                   int preyDir, double preyDist, int preyX, int preyY,
                   int carcassDir, double carcassDist, int carcassX, int carcassY,
                   int waterDir, double waterDist,
                   int landDir, double landDist,
                   int grassDir, double grassDist,
                   int lavaDir, double lavaDist,
                   boolean fireAdjacent, boolean lavaAdjacent,
                   boolean inWater, boolean onLava,
                   boolean[] cardinalFree,
                   int scentPreyDir, double scentPreyIntensity,
                   int scentDangerDir, double scentDangerIntensity,
                   int scentCarcassDir, double scentCarcassIntensity,
                   int scentMateDir, double scentMateIntensity,
                   double olfactionAcuity) {
        this.predatorDir = predatorDir; this.predatorDist = predatorDist;
        this.preyDir = preyDir;         this.preyDist = preyDist;
        this.preyX = preyX;             this.preyY = preyY;
        this.carcassDir = carcassDir;   this.carcassDist = carcassDist;
        this.carcassX = carcassX;       this.carcassY = carcassY;
        this.waterDir = waterDir;       this.waterDist = waterDist;
        this.landDir = landDir;         this.landDist = landDist;
        this.grassDir = grassDir;       this.grassDist = grassDist;
        this.lavaDir = lavaDir;         this.lavaDist = lavaDist;
        this.fireAdjacent = fireAdjacent; this.lavaAdjacent = lavaAdjacent;
        this.inWater = inWater;         this.onLava = onLava;
        this.cardinalFree = Arrays.copyOf(cardinalFree, cardinalFree.length);
        this.scentPreyDir = scentPreyDir;       this.scentPreyIntensity = scentPreyIntensity;
        this.scentDangerDir = scentDangerDir;   this.scentDangerIntensity = scentDangerIntensity;
        this.scentCarcassDir = scentCarcassDir; this.scentCarcassIntensity = scentCarcassIntensity;
        this.scentMateDir = scentMateDir;       this.scentMateIntensity = scentMateIntensity;
        this.olfactionAcuity = olfactionAcuity;
        this.canSmellFrozen = olfactionAcuity >= ui.SimulationConfig.getInstance().olfactionGate;
    }

    public boolean predatorVisible()  { return predatorDir >= 0; }
    public boolean preyVisible()      { return preyDir >= 0; }
    public boolean carcassVisible()   { return carcassDir >= 0; }
    public boolean grassVisible()     { return grassDir >= 0; }
    /** L2 — true si une coulée de lave est en vue (l'agent doit fuir à l'opposé). */
    public boolean lavaVisible()      { return lavaDir >= 0; }

    public boolean scentPreyDetected()    { return scentPreyIntensity > 0; }
    public boolean scentDangerDetected()  { return scentDangerIntensity > 0; }
    public boolean scentCarcassDetected() { return scentCarcassIntensity > 0; }
    public boolean scentMateDetected() { return scentMateIntensity > 0; }
    /** true si l'agent a un odorat fonctionnel (au-dessus du gate au moment du
     *  sense). Valeur gelée à la construction → stable pour la vie du Percept. */
    public boolean canSmell() {
        return canSmellFrozen;
    }
}
