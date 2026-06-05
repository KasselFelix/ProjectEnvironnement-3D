package agents.ai;

/** État qui doit survivre d'un tick à l'autre (remplace les flags-int épars). */
public final class BehaviorMemory {
    public int spiralStep = 0;     // Loup : compteur de spirale (ex stepSpi)
    public int spiralPeriod = 1;   // Loup : période courante (ex stepSpiF)
}
