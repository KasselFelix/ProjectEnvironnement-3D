package ui;

import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import javax.media.opengl.GL2;

/**
 * Menu modal de lancement (Phase 6, Pass B).
 *
 * Affiché en overlay 2D au-dessus d'un fond neutre tant que
 * {@link SimulationConfig#awaitingStart} est vrai. L'utilisateur navigue avec
 * les flèches haut/bas et ajuste la valeur sélectionnée avec gauche/droite.
 * Entrée bascule {@code awaitingStart=false} → la simulation démarre.
 *
 * Le menu ne possède pas la souris ; la navigation est 100% clavier dans
 * cette première version. Les clics sur les boutons « ◀ ▶ » et « DÉMARRER »
 * peuvent être ajoutés en P7 si besoin.
 */
public class LaunchMenu {

    /**
     * Une ligne éditable. {@code value} retourne la valeur formatée pour
     * affichage, {@code dec/inc} sont les actions sur ←/→.
     */
    private static class Row {
        final String label;
        final Supplier<String> value;
        final Runnable dec;
        final Runnable inc;
        Row(String label, Supplier<String> value, Runnable dec, Runnable inc) {
            this.label = label;
            this.value = value;
            this.dec   = dec;
            this.inc   = inc;
        }
    }

    /** Marqueur de séparateur de section (titre, non sélectionnable). */
    private static class Section extends Row {
        Section(String title) { super(title, () -> "", () -> {}, () -> {}); }
    }

    private final List<Row> rows = new ArrayList<>();
    private final SimulationConfig config;
    private int selectedIndex = 0;

    public LaunchMenu(SimulationConfig config) {
        this.config = config;
        buildRows();
        moveToFirstSelectable();
    }

    private void buildRows() {
        rows.add(new Section("POPULATIONS"));
        rows.add(intRow("Loups",   () -> config.nbLoups,   v -> config.nbLoups   = v, 0, 200, 1));
        rows.add(intRow("Moutons", () -> config.nbMoutons, v -> config.nbMoutons = v, 0, 500, 1));
        rows.add(intRow("Humains", () -> config.nbHumains, v -> config.nbHumains = v, 0, 100, 1));

        rows.add(new Section("PAYSAGE"));
        rows.add(intRow("Largeur (dx)", () -> config.landscapeDx, v -> config.landscapeDx = v, 50, 500, 10));
        rows.add(intRow("Hauteur (dy)", () -> config.landscapeDy, v -> config.landscapeDy = v, 50, 500, 10));
        rows.add(doubleRow("Ratio eau",  () -> config.landscapeWaterRatio,
                v -> config.landscapeWaterRatio = v, 0.0, 1.0, 0.05, "%.2f"));
        rows.add(doubleRow("Echelle",    () -> config.landscapeScaling,
                v -> config.landscapeScaling   = v, 0.1, 3.0, 0.1, "%.1f"));

        rows.add(new Section("BIOLOGIE - Loup"));
        rows.add(intRow("Vision",         () -> config.loupVision,     v -> config.loupVision     = v, 1, 50, 1));
        rows.add(intRow("Energie max",    () -> config.loupEnergieMax, v -> config.loupEnergieMax = v, 50, 5000, 50));
        rows.add(doubleRow("Reproduction", () -> config.loupPrepro,
                v -> config.loupPrepro = v, 0.0, 0.05, 0.0005, "%.4f"));
        rows.add(doubleRow("Esperance vie (j)", () -> config.loupMaxAgeDays,
                v -> config.loupMaxAgeDays = v, 0.0, 200.0, 1.0, "%.1f"));

        rows.add(new Section("BIOLOGIE - Mouton"));
        rows.add(intRow("Vision",         () -> config.moutonVision,        v -> config.moutonVision        = v, 1, 50, 1));
        rows.add(doubleRow("Energie max",  () -> config.moutonEnergieMax,
                v -> config.moutonEnergieMax = v, 50.0, 5000.0, 50.0, "%.0f"));
        rows.add(doubleRow("Reproduction", () -> config.moutonPrepro,
                v -> config.moutonPrepro = v, 0.0, 0.2, 0.005, "%.3f"));
        rows.add(doubleRow("Esperance vie (j)", () -> config.moutonMaxAgeDays,
                v -> config.moutonMaxAgeDays = v, 0.0, 200.0, 1.0, "%.1f"));

        rows.add(new Section("TEMPS"));
        rows.add(intRow("Simulation Hz",   () -> config.simulationHz,   v -> config.simulationHz   = v, 10, 60, 5));
        rows.add(intRow("Distance de vue", () -> config.viewDistanceCells, v -> config.viewDistanceCells = v, 10, 200, 5));
        rows.add(doubleRow("Cycle complet (sec)", () -> (double) config.cycleTotalSec,
                v -> config.cycleTotalSec = (float) v, 60.0, 1200.0, 30.0, "%.0f"));
        rows.add(doubleRow("Ratio jour/cycle", () -> (double) config.dayFractionRatio,
                v -> config.dayFractionRatio = (float) v, 0.30, 0.80, 0.05, "%.2f"));
        rows.add(doubleRow("Transition jour (sec)", () -> (double) config.transitionJourSec,
                v -> config.transitionJourSec = (float) v, 0.5, 30.0, 0.5, "%.1f"));

        rows.add(new Section("FORET"));
        rows.add(doubleRow("Densite initiale",   () -> config.forestDensite,
                v -> config.forestDensite = v, 0.0, 1.0, 0.05, "%.2f"));
        rows.add(doubleRow("Croissance / tick",  () -> config.forestProbApparition,
                v -> config.forestProbApparition = v, 0.0, 0.001, 0.000002, "%.6f"));
        rows.add(doubleRow("Combustion / tick",  () -> config.forestProbFeu,
                v -> config.forestProbFeu = v, 0.0, 0.01, 0.00001, "%.5f"));
        rows.add(doubleRow("Croissance arbre (j)", () -> config.treeGrowthDays,
                v -> config.treeGrowthDays = v, 1.0, 100.0, 1.0, "%.0f"));

        rows.add(new Section("HERBE"));
        rows.add(doubleRow("Densite initiale",   () -> config.herbeDensite,
                v -> config.herbeDensite = v, 0.0, 1.0, 0.05, "%.2f"));
        rows.add(doubleRow("Croissance / tick",  () -> config.herbeProbApparition,
                v -> config.herbeProbApparition = v, 0.0, 0.001, 0.000002, "%.6f"));
        rows.add(doubleRow("Combustion / tick",  () -> config.herbeProbFeu,
                v -> config.herbeProbFeu = v, 0.0, 0.001, 0.0000001, "%.7f"));

        rows.add(new Section("LAVE"));
        rows.add(doubleRow("Proba eruption",     () -> config.laveProbErruption,
                v -> config.laveProbErruption = v, 0.0, 0.05, 0.0005, "%.4f"));
        rows.add(doubleRow("Profondeur cratere", () -> (double) config.craterHoleDepth,
                v -> config.craterHoleDepth = (float) v, 0.5, 8.0, 0.5, "%.1f"));
        rows.add(doubleRow("Duree eruption (sec)", () -> (double) config.eruptionDurationSec,
                v -> config.eruptionDurationSec = (float) v, 1.0, 30.0, 0.5, "%.1f"));
        rows.add(doubleRow("Solidification (sec)", () -> (double) config.solidifyEndSec,
                v -> config.solidifyEndSec = (float) v, 0.5, 30.0, 0.5, "%.1f"));
        rows.add(doubleRow("Drainage (sec)",       () -> (double) config.subsidenceIntervalSec,
                v -> config.subsidenceIntervalSec = (float) v, 0.05, 2.0, 0.05, "%.2f"));
        rows.add(doubleRow("Viscosite lave",        () -> (double) config.lavaViscosity,
                v -> config.lavaViscosity = (float) v, 1.0, 2.0, 0.1, "%.1f"));
        rows.add(doubleRow("Puissance min eruption", () -> (double) config.erruptionPowerMin,
                v -> config.erruptionPowerMin = (float) v, 0.1, 5.0, 0.1, "%.2f"));
        rows.add(doubleRow("Puissance max eruption", () -> (double) config.erruptionPowerMax,
                v -> config.erruptionPowerMax = (float) v, 0.5, 10.0, 0.1, "%.2f"));
    }

    private interface IntGet { int get(); }
    private interface IntSet { void set(int v); }
    private interface DblGet { double get(); }
    private interface DblSet { void set(double v); }

    private Row intRow(String label, IntGet get, IntSet set, int min, int max, int step) {
        return new Row(label,
                () -> Integer.toString(get.get()),
                () -> set.set(Math.max(min, get.get() - step)),
                () -> set.set(Math.min(max, get.get() + step)));
    }
    private Row doubleRow(String label, DblGet get, DblSet set, double min, double max, double step, String fmt) {
        return new Row(label,
                () -> String.format(fmt, get.get()),
                () -> set.set(Math.max(min, round(get.get() - step))),
                () -> set.set(Math.min(max, round(get.get() + step))));
    }
    private static double round(double v) {
        // Arrondi à 6 décimales pour éviter les dérives flottantes lors des ±.
        return Math.round(v * 1_000_000.0) / 1_000_000.0;
    }

    private void moveToFirstSelectable() {
        for (int i = 0; i < rows.size(); i++) {
            if (!(rows.get(i) instanceof Section)) { selectedIndex = i; return; }
        }
    }

    /** Gère une touche pressée. Retourne true si le menu doit se fermer. */
    public boolean handleKey(int keyCode) {
        switch (keyCode) {
            case KeyEvent.VK_UP:    moveSelection(-1); return false;
            case KeyEvent.VK_DOWN:  moveSelection(+1); return false;
            case KeyEvent.VK_LEFT:  rows.get(selectedIndex).dec.run(); return false;
            case KeyEvent.VK_RIGHT: rows.get(selectedIndex).inc.run(); return false;
            case KeyEvent.VK_ENTER: config.awaitingStart = false; return true;
            default: return false;
        }
    }

    private void moveSelection(int delta) {
        int n = rows.size();
        int i = selectedIndex;
        for (int step = 0; step < n; step++) {
            i = (i + delta + n) % n;
            if (!(rows.get(i) instanceof Section)) { selectedIndex = i; return; }
        }
    }

    private static final int ROW_HEIGHT  = 18;
    private static final int HEADER_TOP  = 65;   // hauteur du bandeau titre + aide
    private static final int FOOTER_AREA = 35;   // espace réservé au pied de page

    public void draw(GL2 gl, UiRenderer ui, int viewportWidth, int viewportHeight) {
        // Voile sombre plein écran : on cache complètement la scène 3D.
        ui.drawQuad(gl, 0, 0, viewportWidth, viewportHeight, 0.05f, 0.07f, 0.10f, 1f);

        // Panneau central. Hauteur dimensionnée pour absorber le nombre de lignes.
        int panelW = 500;
        int panelH = HEADER_TOP + rows.size() * ROW_HEIGHT + FOOTER_AREA;
        int panelX = (viewportWidth - panelW) / 2;
        int panelY = Math.max(5, (viewportHeight - panelH) / 2);
        ui.drawQuad(gl, panelX, panelY, panelW, panelH, 0.10f, 0.13f, 0.18f, 1f);
        ui.drawBorder(gl, panelX, panelY, panelW, panelH, 0.4f, 0.5f, 0.65f, 1f);

        ui.drawTitle(gl, panelX + 20, panelY + 26, viewportHeight,
                "Configuration de la simulation", 0.9f, 0.95f, 1f);
        ui.drawText(gl, panelX + 20, panelY + 48, viewportHeight,
                "Up/Down : Naviguer    Left/Right : Modifier    Entree : DEMARRER",
                0.65f, 0.7f, 0.8f);

        int rowY = panelY + HEADER_TOP + 12;
        for (int i = 0; i < rows.size(); i++) {
            Row r = rows.get(i);
            if (r instanceof Section) {
                ui.drawText(gl, panelX + 20, rowY, viewportHeight,
                        r.label, 0.5f, 0.85f, 1f);
            } else {
                boolean sel = (i == selectedIndex);
                if (sel) {
                    ui.drawQuad(gl, panelX + 10, rowY - 13, panelW - 20, ROW_HEIGHT - 2,
                            0.20f, 0.30f, 0.45f, 1f);
                }
                ui.drawText(gl, panelX + 30, rowY, viewportHeight,
                        r.label, 0.95f, 0.95f, 0.95f);
                String val = (sel ? "< " : "  ") + r.value.get() + (sel ? " >" : "  ");
                int textW = ui.textWidth(val);
                ui.drawText(gl, panelX + panelW - 30 - textW, rowY, viewportHeight,
                        val, 1f, 1f, 0.7f);
            }
            rowY += ROW_HEIGHT;
        }

        // Pied de page : rappel touche Start.
        String hint = "[ Appuie sur ENTREE pour demarrer ]";
        int hintW = ui.textWidth(hint);
        ui.drawText(gl, panelX + (panelW - hintW) / 2, panelY + panelH - 15,
                viewportHeight, hint, 0.6f, 1f, 0.6f);
    }
}
