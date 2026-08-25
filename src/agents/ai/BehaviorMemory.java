package agents.ai;

/** État qui doit survivre d'un tick à l'autre (remplace les flags-int épars). */
public final class BehaviorMemory {
    // ---- Spirale carrée extensible de recherche (Loup / Ours) ----
    // Bras de longueurs L, L, 2L, 2L, 3L, 3L… (L = 2·vision+1) → balaie la zone
    // sans recouvrement ni trou en terrain dégagé. Voir Agent.spiralSearch().
    public int spiralStepsLeft = 0;   // pas restants sur le bras courant
    public int spiralLegCount  = 0;   // nb de bras déjà parcourus (donne la longueur)

    // CAP VOULU de la spirale (0=N/1=E/2=S/3=O), -1 = non initialisé. Donne la
    // DIRECTION GÉNÉRALE du ratissage ; le pas réel est choisi par
    // Agent.followSpiralHeading (case adjacente la moins récemment visitée), qui
    // utilise ce cap seulement comme départage des ex-æquo.
    public int spiralHeading = -1;

    // ---- Ralliement vers une zone de chasse mémorisée (Loup.huntHoming) ----
    // MEILLEURE distance (tore) jamais atteinte vers la zone actuellement visée.
    // Le loup ne « rallie » (homing) que s'il peut faire MIEUX que ce record ;
    // sinon il est coincé à un minimum local (zone murée par eau/forêt) et rend la
    // main à la spirale — anti-oscillation, cf. cause racine du gel en SEARCH.
    public int homingZoneX = Integer.MIN_VALUE;
    public int homingZoneY = Integer.MIN_VALUE;
    public double homingBestDist = Double.MAX_VALUE;

    /** Réinitialise la spirale ET le suivi de ralliement (à appeler quand l'agent
     *  cesse de chercher). */
    public void resetSpiral() {
        spiralStepsLeft = 0;
        spiralLegCount  = 0;
        spiralHeading   = -1;
        resetHoming();
    }

    /** Réinitialise le seul suivi de ralliement (record de distance). À appeler dès
     *  que l'agent n'est PLUS dans un état de ralliement, sinon un record périmé
     *  (ex. distance 0 après une arrivée) empêcherait tout futur ralliement vers la
     *  même cible. */
    public void resetHoming() {
        homingZoneX    = Integer.MIN_VALUE;
        homingZoneY    = Integer.MIN_VALUE;
        homingBestDist = Double.MAX_VALUE;
    }
}
