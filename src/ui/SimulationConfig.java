package ui;

/**
 * Tous les paramètres de la simulation, regroupés en un POJO modifiable par
 * le menu de lancement (Phase 6) et — pour le sous-ensemble modifiable à chaud —
 * par le menu in-game (Phase 7).
 *
 * Les valeurs par défaut reprennent celles actuellement codées en dur dans
 * `MyEcosystem`, `WorldOfCells`, `Loup`, `Mouton`, `World`. Modifier une valeur
 * ici ne suffit pas — il faut que `WorldOfCells` et `Landscape` lisent la
 * config au moment de leur init (Phase 6).
 */
public class SimulationConfig {

    // ───── Source du paysage ───────────────────────────────────────────────
    public enum LandscapeSource { PERLIN, PNG }
    public LandscapeSource landscapeSource = LandscapeSource.PERLIN;

    // ───── Paysage Perlin (cf. MyEcosystem.main — synchro avec le code) ────
    public int    landscapeDx           = 200;
    public int    landscapeDy           = 200;
    public double landscapeScaling      = 0.7;   // amplitude altitude
    public double landscapeWaterRatio   = 0.4;   // proportion sous l'eau
    public int    landscapeOctaves      = 10;    // Perlin

    /** Rendu 3D : rayon de la fenêtre de rendu en cases (on ne dessine que les
     *  cases à ≤ viewDistanceCells du centre/caméra). Défaut 76 (compromis
     *  perf/portée). 0 ou négatif = « auto » : Landscape le dérive de la distance
     *  de brouillard. Réglable via le slider « Distance de vue ». Note : la vue
     *  de dessus a son propre zoom (Landscape.topDownViewCells), découplé. */
    public int    viewDistanceCells     = 76;

    // ───── Paysage PNG (alternative à Perlin) ──────────────────────────────
    public String landscapePngPath      = "landscapes/landscape_paris-200.png";

    // ───── Populations initiales (cf. WorldOfCells) ────────────────────────
    public int nbLoups    = 5;
    public int nbMoutons  = 20;
    public int nbHumains  = 2;

    // ───── Biologie Loup (cf. Loup.java — défauts synchronisés avec le code) ─
    public int    loupVision      = 12;
    public int    loupEnergieMax  = 1500;     // Loup.java l.16 (tuning utilisateur)
    public double loupPrepro      = 0.020;     // Loup.java l.13 (tuning utilisateur : reproduction désactivée)
    public double loupVpas        = 3;
    public double loupVtrot       = 8;
    public double loupVcourse     = 13.5;
    public double loupMaxAgeDays  = 15.0;    // mort par vieillesse — 0 ou négatif = immortel
    /** Fraction de la vitesse terrestre conservée dans l'eau. Le loup nage bien
     *  (0.6) — bien mieux que le mouton. Plage utile [0.2, 1.0]. */
    public double loupSwimFactor  = 0.6;

    // ───── Biologie Mouton (cf. Mouton.java) ───────────────────────────────
    public int    moutonVision      = 10;
    public double moutonEnergieMax  = 1500;
    public double moutonPrepro      = 0.5;
    public double moutonVmarche     = 2;
    public double moutonVcourse     = 9;
    public double moutonMaxAgeDays  = 8.0;   // mort par vieillesse — 0 ou négatif = immortel
    /** Fraction de la vitesse terrestre conservée dans l'eau. Le mouton nage
     *  très mal (0.25) : laine gorgée d'eau, panique. Plage utile [0.2, 1.0]. */
    public double moutonSwimFactor  = 0.25;

    // ───── Reproduction conditionnée à l'énergie (seuils PAR ESPÈCE) ───────
    // Stabilise l'écosystème (modèle Sugarscape / Lotka-Volterra à énergie) :
    //  - un agent ne se reproduit que s'il est en bonne santé (seuil), et
    //  - il INVESTIT de l'énergie dans le petit (énergie conservée, pas créée).
    // Seuils PAR ESPÈCE car le budget énergétique diffère radicalement : le loup
    // se recharge en gros blocs (+½ jauge par proie) et peut donc se permettre
    // un seuil/coût élevés ; le mouton grappille l'herbe (+1% par case), donc un
    // seuil/coût trop hauts l'empêchent quasiment de se reproduire. On baisse
    // donc seuil ET coût du mouton pour qu'il surpasse le loup (proie prolifique,
    // dynamique Lotka-Volterra réaliste). Ratios = fraction de l'énergie MAX.
    /** Loup — exigeant (prédateur festin/famine) : bien nourri pour se reproduire. */
    public double loupReproEnergyThreshold   = 0.60;
    public double loupReproOffspringRatio    = 0.45;
    /** Mouton — proie prolifique : seuil + coût bas → se reproduit sur un budget
     *  d'herbe modeste et récupère vite (petit né avec peu, parent peu ponctionné). */
    public double moutonReproEnergyThreshold = 0.40;
    public double moutonReproOffspringRatio  = 0.25;

    // ───── Contrôle manuel d'agent (touche 'c') ───────────────────────────
    /** Sensibilité du mouse-look en pilotage 1ère personne (degrés de rotation
     *  caméra par pixel de souris). Plus bas = plus lent/précis. Plage UI [0.02, 0.40]. */
    public float  mouseLookSensitivity = 0.06f;

    // ───── Temps (refonte 2026-05 — cycle complet + ratio jour/nuit) ───────
    /** Fréquence cible de la simulation en Hz. Plage UI [10, 60] pas 5. */
    public int    simulationHz       = 20;
    /** Durée totale d'un cycle 24h jeu en secondes réelles (jour + nuit cumulés).
     *  Default 240s ⇒ 1h jeu = 10s réelles. */
    public float  cycleTotalSec      = 240f;
    /** Fraction du cycle où le soleil est au-dessus de l'horizon (jour visible).
     *  Default 14/24 ≈ 0.583 ⇒ jour 14h / nuit 10h. Aube fixée à 6h dans l'horloge ;
     *  crépuscule = 6h + ratio × 24h. Plage utile [0.30, 0.80]. */
    public float  dayFractionRatio   = 14f / 24f;
    /** Durée de la transition aube/crépuscule en secondes (anciennement transitionJour en iter). */
    public float  transitionJourSec  = 5f;

    // ───── Forêt (cf. ForestCA.java) ───────────────────────────────────────
    public double forestDensite        = 0.1;       // darbre   — densité initiale d'arbres
    public double forestProbApparition = 0.000006;  // pA       — proba de pousse / tick / case
    public double forestProbFeu        = 0.0000003;   // pF       — proba qu'un arbre prenne feu spontanément
    public double treeGrowthDays      = 10.0;      // durée (jours-jeu) pour qu'un arbre atteigne sa taille adulte (conditions normales)

    // ───── Herbe (cf. GrassCA.java) ────────────────────────────────────────
    public double herbeDensite         = 0.55;      // dherbe   — densité initiale d'herbe
    public double herbeProbApparition  = 0.000006;  // pH       — proba de pousse / tick / case
    public double herbeProbFeu         = 0.0000003; // pF       — proba que l'herbe prenne feu spontanément

    // ───── Lave (cf. LavaCA.java) ──────────────────────────────────────────
    public double laveProbErruption    = 0.0;       // pErruption — 0 = jamais (sauf touche `r`)
    @Deprecated // remplacé par constante SPREAD_FACTOR + propagation gravitaire dans LavaCA
    public int    laveVitesseEtalement = 100;
    public float  craterHoleDepth      = 3.0f;      // profondeur du trou au centre du volcan, en × STONE_BLOCK_HEIGHT
    @Deprecated // remplacé par subsidenceIntervalSec (Module 1)
    public int    subsidencePeriod     = 6;
    public float  erruptionPowerMin    = 1.5f;      // borne basse du tirage power
    public float  erruptionPowerMax    = 5.0f;      // borne haute du tirage power
    /** Durée pendant laquelle la pression d'une éruption reste active (en sec). */
    public float  eruptionDurationSec  = 5f;
    /** État solidification complète d'une couche LAVA, en sec depuis création.
     *  Plus haut = lave reste orange plus longtemps avant de devenir pierre. */
    public float  solidifyEndSec       = 20.0f;
    /** Période entre deux décrémentations du drainage résiduel, en sec. */
    public float  subsidenceIntervalSec = 0.1f;
    /** Coefficient de viscosité de la lave FRAÎCHE (= fraction du surplus altitude transférée
     *  par tick au voisin, à state=1). Dégradé automatiquement par le state au runtime
     *  (lave qui refroidit coule plus lentement). Bas = lave épaisse, haut = lave fluide. */
    public float  lavaViscosity        = 2.0f;

    // ───── Drapeaux UI ─────────────────────────────────────────────────────
    /** true tant que l'utilisateur n'a pas cliqué Start dans le menu de lancement. */
    public boolean awaitingStart = true;

    // ───── Singleton-like accès (Module 1 refonte 2026-05) ─────────────────
    // La config est typiquement créée dans MyEcosystem et passée par référence,
    // mais LavaCA + helpers ont besoin d'y accéder sans la traîner partout.
    // Pattern : setInstance() au démarrage, getInstance() partout ailleurs.

    private static SimulationConfig INSTANCE = null;

    public static void setInstance(SimulationConfig config) {
        INSTANCE = config;
    }

    public static SimulationConfig getInstance() {
        if (INSTANCE == null) INSTANCE = new SimulationConfig();
        return INSTANCE;
    }
}
