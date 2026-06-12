package ui;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Registre déclaratif UNIQUE des paramètres de simulation exposés aux menus.
 * LaunchMenu et InGameMenu itèrent ce registre (filtré par visibilité) au lieu
 * de déclarer chacun leurs lignes — une seule définition par paramètre.
 *
 * <p>L'ordre du registre EST l'ordre d'affichage (= ordre des lignes du
 * LaunchMenu). Les libellés / bornes / pas / formats sont transcrits 1:1 depuis
 * les deux menus. Quand un paramètre apparaît dans les deux menus avec des
 * bornes/pas différents, on retient ceux du LaunchMenu (cf. plan B1).
 *
 * <p>Visibilité :
 * <ul>
 *   <li>{@code LAUNCH_ONLY} — populations, paysage, densités initiales, durée
 *       de transition jour, esp. de vie loup… (figés une fois la sim lancée).</li>
 *   <li>{@code INGAME_ONLY} — HUD dégâts + sensibilité souris (réglages de
 *       confort, pas exposés au lancement).</li>
 *   <li>{@code BOTH} — biologie tweakable à chaud + Hz + temps + vent + CA.</li>
 * </ul>
 */
public final class ParamRegistry {

    public enum Visibility { LAUNCH_ONLY, INGAME_ONLY, BOTH }

    public static final class ParamDef {
        public final String section;
        public final String label;
        public final Supplier<String> value;
        public final Runnable dec, inc;
        public final Visibility visibility;
        /** Bulle d'aide (V7, InGameMenu PARAMS). "" = pas de bulle. */
        public final String help;
        ParamDef(String section, String label, Supplier<String> value,
                 Runnable dec, Runnable inc, Visibility visibility) {
            this(section, label, value, dec, inc, visibility, "");
        }
        ParamDef(String section, String label, Supplier<String> value,
                 Runnable dec, Runnable inc, Visibility visibility, String help) {
            this.section = section; this.label = label; this.value = value;
            this.dec = dec; this.inc = inc; this.visibility = visibility;
            this.help = help;
        }
        /** Retourne une copie identique enrichie de la bulle d'aide (champs finals). */
        ParamDef withHelp(String h) {
            return new ParamDef(section, label, value, dec, inc, visibility, h);
        }
    }

    private ParamRegistry() {}

    public static List<ParamDef> build(SimulationConfig config) {
        List<ParamDef> defs = new ArrayList<>();

        // ───── POPULATIONS (LaunchMenu only) ───────────────────────────────
        defs.add(intDef("POPULATIONS", "Loups", Visibility.LAUNCH_ONLY,
                () -> config.nbLoups, v -> config.nbLoups = v, 0, 200, 1));
        defs.add(intDef("POPULATIONS", "Moutons", Visibility.LAUNCH_ONLY,
                () -> config.nbMoutons, v -> config.nbMoutons = v, 0, 500, 1));
        defs.add(intDef("POPULATIONS", "Humains", Visibility.LAUNCH_ONLY,
                () -> config.nbHumains, v -> config.nbHumains = v, 0, 100, 1));
        defs.add(intDef("POPULATIONS", "Ours", Visibility.LAUNCH_ONLY,
                () -> config.nbOurs, v -> config.nbOurs = v, 0, 50, 1));
        // "mode Humain" — toggle Berger(0)/Chasseur(1). dec=0, inc=1 (verbatim LaunchMenu).
        defs.add(new ParamDef("POPULATIONS", "mode Humain",
                () -> config.humainChasseur == 1 ? "CHASSEUR" : "Berger",
                () -> config.humainChasseur = 0,
                () -> config.humainChasseur = 1,
                Visibility.BOTH,   // editable au lancement ET en jeu (restaure le "Humain mode" live d'avant la refonte)
                "Berger garde le troupeau / Chasseur traque les loups"));

        // ───── PAYSAGE (LaunchMenu only) ───────────────────────────────────
        defs.add(intDef("PAYSAGE", "Largeur (dx)", Visibility.LAUNCH_ONLY,
                () -> config.landscapeDx, v -> config.landscapeDx = v, 50, 500, 10));
        defs.add(intDef("PAYSAGE", "Hauteur (dy)", Visibility.LAUNCH_ONLY,
                () -> config.landscapeDy, v -> config.landscapeDy = v, 50, 500, 10));
        defs.add(doubleDef("PAYSAGE", "Ratio eau", Visibility.LAUNCH_ONLY,
                () -> config.landscapeWaterRatio, v -> config.landscapeWaterRatio = v, 0.0, 1.0, 0.05, "%.2f"));
        defs.add(doubleDef("PAYSAGE", "Echelle", Visibility.LAUNCH_ONLY,
                () -> config.landscapeScaling, v -> config.landscapeScaling = v, 0.1, 3.0, 0.1, "%.1f"));

        // ───── BIOLOGIE - Loup ─────────────────────────────────────────────
        // Vision / EnergieMax / Reproduction / Esp. vie / Portee cri sont aussi
        // dans InGameMenu (labels differents la-bas) ⇒ BOTH. On garde le libelle
        // ET les bornes du LaunchMenu (identiques de toute facon ici).
        defs.add(intDef("BIOLOGIE - Loup", "Vision", Visibility.BOTH,
                () -> config.loupVision, v -> config.loupVision = v, 1, 50, 1)
                .withHelp("Portee de detection du loup (cases)"));
        defs.add(intDef("BIOLOGIE - Loup", "Energie max", Visibility.BOTH,
                () -> config.loupEnergieMax, v -> config.loupEnergieMax = v, 50, 5000, 50));
        defs.add(doubleDef("BIOLOGIE - Loup", "Reproduction", Visibility.BOTH,
                () -> config.loupPrepro, v -> config.loupPrepro = v, 0.0, 0.05, 0.0005, "%.4f"));
        defs.add(doubleDef("BIOLOGIE - Loup", "Esperance vie (j)", Visibility.BOTH,
                () -> config.loupMaxAgeDays, v -> config.loupMaxAgeDays = v, 0.0, 200.0, 1.0, "%.1f"));
        defs.add(intDef("BIOLOGIE - Loup", "Portee cri", Visibility.BOTH,
                () -> config.howlRadius, v -> config.howlRadius = v, 5, 80, 5)
                .withHelp("Distance (cases) a laquelle un hurlement de meute est entendu"));

        // ───── BIOLOGIE - Mouton ───────────────────────────────────────────
        defs.add(intDef("BIOLOGIE - Mouton", "Vision", Visibility.BOTH,
                () -> config.moutonVision, v -> config.moutonVision = v, 1, 50, 1));
        defs.add(doubleDef("BIOLOGIE - Mouton", "Energie max", Visibility.BOTH,
                () -> config.moutonEnergieMax, v -> config.moutonEnergieMax = v, 50.0, 5000.0, 50.0, "%.0f"));
        defs.add(doubleDef("BIOLOGIE - Mouton", "Reproduction", Visibility.BOTH,
                () -> config.moutonPrepro, v -> config.moutonPrepro = v, 0.0, 0.2, 0.005, "%.3f"));
        defs.add(doubleDef("BIOLOGIE - Mouton", "Esperance vie (j)", Visibility.BOTH,
                () -> config.moutonMaxAgeDays, v -> config.moutonMaxAgeDays = v, 0.0, 200.0, 1.0, "%.1f"));

        // ───── TEMPS ───────────────────────────────────────────────────────
        defs.add(intDef("TEMPS", "Simulation Hz", Visibility.BOTH,
                () -> config.simulationHz, v -> config.simulationHz = v, 10, 60, 5));
        defs.add(intDef("TEMPS", "Distance de vue", Visibility.BOTH,
                () -> config.viewDistanceCells, v -> config.viewDistanceCells = v, 10, 200, 5));
        defs.add(doubleDef("TEMPS", "Cycle complet (sec)", Visibility.BOTH,
                () -> (double) config.cycleTotalSec, v -> config.cycleTotalSec = (float) v, 60.0, 1200.0, 30.0, "%.0f"));
        defs.add(doubleDef("TEMPS", "Ratio jour/cycle", Visibility.BOTH,
                () -> (double) config.dayFractionRatio, v -> config.dayFractionRatio = (float) v, 0.30, 0.80, 0.05, "%.2f"));
        defs.add(doubleDef("TEMPS", "Transition jour (sec)", Visibility.LAUNCH_ONLY,
                () -> (double) config.transitionJourSec, v -> config.transitionJourSec = (float) v, 0.5, 30.0, 0.5, "%.1f"));
        defs.add(intDef("TEMPS", "Saison (jours-jeu)", Visibility.BOTH,
                () -> config.seasonLengthDays, v -> config.seasonLengthDays = v, 0, 30, 1)
                .withHelp("Duree d'une saison en jours-jeu ; 0 = ete perpetuel"));
        // INGAME_ONLY : reglages de confort affiches dans l'onglet PARAMS in-game.
        // "HUD degats" — toggle ON/OFF (dec=false, inc=true, verbatim InGameMenu).
        defs.add(new ParamDef("TEMPS", "HUD degats",
                () -> config.showDamageHud ? "ON" : "OFF",
                () -> config.showDamageHud = false,
                () -> config.showDamageHud = true,
                Visibility.INGAME_ONLY,
                "2e ligne HUD : arbres brules, agents morts, lave emise"));
        defs.add(doubleDef("TEMPS", "Sensibilite souris", Visibility.INGAME_ONLY,
                () -> (double) config.mouseLookSensitivity, v -> config.mouseLookSensitivity = (float) v, 0.02, 0.40, 0.02, "%.2f"));

        // ───── VENT ────────────────────────────────────────────────────────
        // "Vent actif" — toggle Oui/Non (dec=false, inc=true, verbatim).
        defs.add(new ParamDef("VENT", "Vent actif",
                () -> config.windEnabled ? "Oui" : "Non",
                () -> config.windEnabled = false,
                () -> config.windEnabled = true,
                Visibility.BOTH,
                "Active ou desactive le vent (propagation feu, graines)"));
        defs.add(doubleDef("VENT", "Force vent", Visibility.BOTH,
                () -> config.baseWindForce, v -> config.baseWindForce = v, 0.0, 25.0, 1.0, "%.1f")
                .withHelp("Force de base du vent en m/s"));
        defs.add(doubleDef("VENT", "Variabilite vent", Visibility.BOTH,
                () -> config.windVariability, v -> config.windVariability = v, 0.0, 3.0, 0.25, "%.2f")
                .withHelp("Amplitude des rafales (0 = vent constant)"));

        // ───── FORET ───────────────────────────────────────────────────────
        defs.add(doubleDef("FORET", "Densite initiale", Visibility.LAUNCH_ONLY,
                () -> config.forestDensite, v -> config.forestDensite = v, 0.0, 1.0, 0.05, "%.2f"));
        defs.add(doubleDef("FORET", "Croissance / tick", Visibility.BOTH,
                () -> config.forestProbApparition, v -> config.forestProbApparition = v, 0.0, 0.001, 0.000002, "%.6f"));
        defs.add(doubleDef("FORET", "Combustion / tick", Visibility.LAUNCH_ONLY,
                () -> config.forestProbFeu, v -> config.forestProbFeu = v, 0.0, 0.01, 0.00001, "%.5f"));
        defs.add(doubleDef("FORET", "Croissance arbre (j)", Visibility.BOTH,
                () -> config.treeGrowthDays, v -> config.treeGrowthDays = v, 1.0, 100.0, 1.0, "%.0f"));

        // ───── HERBE ───────────────────────────────────────────────────────
        defs.add(doubleDef("HERBE", "Densite initiale", Visibility.LAUNCH_ONLY,
                () -> config.herbeDensite, v -> config.herbeDensite = v, 0.0, 1.0, 0.05, "%.2f"));
        defs.add(doubleDef("HERBE", "Croissance / tick", Visibility.BOTH,
                () -> config.herbeProbApparition, v -> config.herbeProbApparition = v, 0.0, 0.001, 0.000002, "%.6f"));
        defs.add(doubleDef("HERBE", "Combustion / tick", Visibility.LAUNCH_ONLY,
                () -> config.herbeProbFeu, v -> config.herbeProbFeu = v, 0.0, 0.001, 0.0000001, "%.7f"));

        // ───── LAVE ────────────────────────────────────────────────────────
        defs.add(doubleDef("LAVE", "Proba eruption", Visibility.BOTH,
                () -> config.laveProbErruption, v -> config.laveProbErruption = v, 0.0, 0.05, 0.0005, "%.4f"));
        defs.add(doubleDef("LAVE", "Profondeur cratere", Visibility.BOTH,
                () -> (double) config.craterHoleDepth, v -> config.craterHoleDepth = (float) v, 0.5, 8.0, 0.5, "%.1f"));
        defs.add(doubleDef("LAVE", "Duree eruption (sec)", Visibility.BOTH,
                () -> (double) config.eruptionDurationSec, v -> config.eruptionDurationSec = (float) v, 1.0, 30.0, 0.5, "%.1f"));
        defs.add(doubleDef("LAVE", "Solidification (sec)", Visibility.BOTH,
                () -> (double) config.solidifyEndSec, v -> config.solidifyEndSec = (float) v, 0.5, 30.0, 0.5, "%.1f"));
        defs.add(doubleDef("LAVE", "Drainage (sec)", Visibility.BOTH,
                () -> (double) config.subsidenceIntervalSec, v -> config.subsidenceIntervalSec = (float) v, 0.05, 2.0, 0.05, "%.2f"));
        defs.add(doubleDef("LAVE", "Viscosite lave", Visibility.BOTH,
                () -> (double) config.lavaViscosity, v -> config.lavaViscosity = (float) v, 1.0, 2.0, 0.1, "%.1f"));
        defs.add(doubleDef("LAVE", "Puissance min eruption", Visibility.BOTH,
                () -> (double) config.erruptionPowerMin, v -> config.erruptionPowerMin = (float) v, 0.1, 5.0, 0.1, "%.2f"));
        defs.add(doubleDef("LAVE", "Puissance max eruption", Visibility.BOTH,
                () -> (double) config.erruptionPowerMax, v -> config.erruptionPowerMax = (float) v, 0.5, 10.0, 0.1, "%.2f"));

        return defs;
    }

    private interface IntGet { int get(); }   private interface IntSet { void set(int v); }
    private interface DblGet { double get(); } private interface DblSet { void set(double v); }

    private static ParamDef intDef(String section, String label, Visibility vis,
            IntGet get, IntSet set, int min, int max, int step) {
        return new ParamDef(section, label, () -> String.valueOf(get.get()),
                () -> set.set(Math.max(min, get.get() - step)),
                () -> set.set(Math.min(max, get.get() + step)), vis);
    }

    private static ParamDef doubleDef(String section, String label, Visibility vis,
            DblGet get, DblSet set, double min, double max, double step, String fmt) {
        return new ParamDef(section, label, () -> String.format(fmt, get.get()),
                () -> set.set(Math.max(min, round(get.get() - step))),
                () -> set.set(Math.min(max, round(get.get() + step))), vis);
    }

    /** Arrondi à 6 décimales pour éviter les dérives flottantes lors des ± (idem menus). */
    private static double round(double v) {
        return Math.round(v * 1_000_000.0) / 1_000_000.0;
    }
}
