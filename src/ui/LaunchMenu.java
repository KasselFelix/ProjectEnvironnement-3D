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
        // Source UNIQUE : le registre déclaratif (ParamRegistry). On insère un
        // séparateur de section à chaque changement de section, et on filtre les
        // lignes purement in-game (INGAME_ONLY). L'ordre du registre EST l'ordre
        // d'affichage historique du LaunchMenu.
        String currentSection = null;
        for (ParamRegistry.ParamDef d : ParamRegistry.build(config)) {
            if (d.visibility == ParamRegistry.Visibility.INGAME_ONLY) continue;
            if (!d.section.equals(currentSection)) {
                rows.add(new Section(d.section));
                currentSection = d.section;
            }
            rows.add(new Row(d.label, d.value, d.dec, d.inc));
        }
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
