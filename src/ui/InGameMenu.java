package ui;

import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import javax.media.opengl.GL2;

import agents.Agent;
import agents.Loup;
import agents.Mouton;
import worlds.WorldOfCells;
import graphics.Landscape;

/**
 * Menu latéral in-game (Phase 7). Ouvert/fermé par la touche `m`.
 *
 * Deux sections accessibles via Tab :
 *   - PARAMS : sous-ensemble des sliders de SimulationConfig modifiables à
 *     chaud (proba reproduction, vision, vitesses, CA, durée jour). Les
 *     densités initiales et le paysage ne sont pas exposés ici (déjà figés).
 *   - AGENTS : liste depliable par espece (loups/moutons/humains/ours), via AgentRoster.
 *
 * Tout est en ASCII (GLUT bitmap ne supporte que ça de manière fiable).
 */
public class InGameMenu {

    public enum Tab { AGENTS, AIDE, PARAMS }

    /** Raccourcis affichés dans l'onglet AIDE (label → action). */
    private static final String[][] SHORTCUTS = {
        {"m",       "Ouvrir/fermer ce menu"},
        {"clic in", "Re-focaliser le menu"},
        {"clic out","Defocaliser (clavier au jeu)"},
        {"Tab",     "Changer d'onglet"},
        {"Enter",   "(AGENTS) Suivre l'agent"},
        {"f",       "Toggle camera-follow"},
        {"c",       "Piloter agent (3D: ZS av, QD strafe, AE tourne)"},
        {"g",       "Afficher/masquer graphe pop"},
        {"v",       "Vue de dessus / 3D"},
        {"o",       "Objets on/off"},
        {"l",       "Eclairage"},
        {"p",       "Eclairage haute qualite"},
        {"n",       "Basculer Jour <-> Nuit"},
        {"Home",    "Pause / lecture de la simulation"},
        {"+ / PgUp","Avance rapide (x1->x2->x4->x8)"},
        {"r",       "Eruption volcanique"},
        {"1 / 2",   "+/- amplitude altitude"},
        {"ZQSD",    "Naviguer (ou fleches)"},
        {"Space",   "Camera vers le bas (long)"},
        {"Shift",   "Camera vers le haut (long)"},
        {"LMB clic","Picking / Focus menu / Onglet"},
        {"LMB 2x",  "(menu AGENTS) Suivre l'agent"},
        {"LMB drag","Rotation 3D / Pan / Orbit"},
        {"RMB drag","Pan 3D (sort du suivi)"},
        {"Molette", "Zoom (menu : scroll lignes)"},
        {"F12",     "Capture ecran -> screenshots/"},
        {"Esc",     "Quitter"},
    };

    private static class Row {
        final String label;
        final Supplier<String> value;
        final Runnable dec;
        final Runnable inc;
        String help = "";   // V7 — bulle d'aide affichée pour la ligne sélectionnée
        Row(String label, Supplier<String> value, Runnable dec, Runnable inc) {
            this.label = label;
            this.value = value;
            this.dec   = dec;
            this.inc   = inc;
        }
        Row withHelp(String h) { this.help = h; return this; }
    }

    private static final int ROW_HEIGHT = 16;
    private static final int PANEL_WIDTH = 260;
    private static final int HEADER_HEIGHT = 26;   // bandeau noir HUD en haut

    private final SimulationConfig config;
    private final WorldOfCells world;
    private final Landscape landscape;       // pour pousser la sélection d'agent (Phase 8)
    private final List<Row> paramRows = new ArrayList<>();
    private boolean open = false;
    private Tab activeTab = Tab.AGENTS;
    private int selectedIndex = 0;
    private int agentScroll = 0;

    /** Depliage par espece, indexe par AgentRoster.Species.ordinal() (L,M,H,O). */
    private final boolean[] expanded = new boolean[AgentRoster.Species.values().length];

    public InGameMenu(SimulationConfig config, WorldOfCells world, Landscape landscape) {
        this.config = config;
        this.world  = world;
        this.landscape = landscape;
        buildParamRows();
    }

    private void buildParamRows() {
        paramRows.add(intRow("Vision loup",       () -> config.loupVision,     v -> config.loupVision     = v, 1, 50, 1)
                .withHelp("Portee de detection du loup (cases)"));
        paramRows.add(intRow("EnergieMax loup",   () -> config.loupEnergieMax, v -> config.loupEnergieMax = v, 50, 5000, 50));
        paramRows.add(doubleRow("Prepro loup",     () -> config.loupPrepro,
                v -> config.loupPrepro = v, 0.0, 0.05, 0.0005, "%.4f"));
        paramRows.add(doubleRow("Age max loup",    () -> config.loupMaxAgeDays,
                v -> config.loupMaxAgeDays = v, 0.0, 200.0, 1.0, "%.1f"));
        paramRows.add(intRow("Portee cri loup",   () -> config.howlRadius, v -> config.howlRadius = v, 5, 80, 5)
                .withHelp("Distance (cases) a laquelle un hurlement de meute est entendu"));
        paramRows.add(intRow("Vision mouton",     () -> config.moutonVision, v -> config.moutonVision = v, 1, 50, 1));
        paramRows.add(doubleRow("EnergieMax mouton", () -> config.moutonEnergieMax,
                v -> config.moutonEnergieMax = v, 50.0, 5000.0, 50.0, "%.0f"));
        paramRows.add(doubleRow("Prepro mouton",    () -> config.moutonPrepro,
                v -> config.moutonPrepro = v, 0.0, 0.2, 0.005, "%.3f"));
        paramRows.add(doubleRow("Age max mouton",  () -> config.moutonMaxAgeDays,
                v -> config.moutonMaxAgeDays = v, 0.0, 200.0, 1.0, "%.1f"));
        // L3 — bascule berger / chasseur (toggle 0/1, libellé explicite).
        paramRows.add(new Row("Humain mode",
                () -> config.humainChasseur == 1 ? "CHASSEUR" : "Berger",
                () -> config.humainChasseur = 0,
                () -> config.humainChasseur = 1)
                .withHelp("Berger garde le troupeau / Chasseur traque les loups"));
        paramRows.add(intRow("Saison (jours)",    () -> config.seasonLengthDays, v -> config.seasonLengthDays = v, 0, 30, 1)
                .withHelp("Duree d'une saison en jours-jeu ; 0 = ete perpetuel"));
        // VENT
        paramRows.add(new Row("Vent actif",
                () -> config.windEnabled ? "Oui" : "Non",
                () -> config.windEnabled = false,
                () -> config.windEnabled = true)
                .withHelp("Active ou desactive le vent (propagation feu, graines)"));
        paramRows.add(doubleRow("Force vent",       () -> config.baseWindForce,
                v -> config.baseWindForce = v, 0.0, 25.0, 1.0, "%.1f")
                .withHelp("Force de base du vent en m/s"));
        paramRows.add(doubleRow("Variabilite vent", () -> config.windVariability,
                v -> config.windVariability = v, 0.0, 3.0, 0.25, "%.2f")
                .withHelp("Amplitude des rafales (0 = vent constant)"));
        paramRows.add(intRow("Simulation Hz",     () -> config.simulationHz,   v -> config.simulationHz   = v, 10, 60, 5));
        paramRows.add(intRow("Distance de vue",   () -> config.viewDistanceCells, v -> config.viewDistanceCells = v, 10, 200, 5));
        paramRows.add(new Row("HUD degats",
                () -> config.showDamageHud ? "ON" : "OFF",
                () -> config.showDamageHud = false,
                () -> config.showDamageHud = true)
                .withHelp("2e ligne HUD : arbres brules, agents morts, lave emise"));
        paramRows.add(doubleRow("Sensibilite souris", () -> (double) config.mouseLookSensitivity,
                v -> config.mouseLookSensitivity = (float) v, 0.02, 0.40, 0.02, "%.2f"));
        paramRows.add(doubleRow("Cycle complet (sec)", () -> (double) config.cycleTotalSec,
                v -> config.cycleTotalSec = (float) v, 60.0, 1200.0, 30.0, "%.0f"));
        paramRows.add(doubleRow("Ratio jour/cycle", () -> (double) config.dayFractionRatio,
                v -> config.dayFractionRatio = (float) v, 0.30, 0.80, 0.05, "%.2f"));
        paramRows.add(doubleRow("Croissance foret", () -> config.forestProbApparition,
                v -> config.forestProbApparition = v, 0.0, 0.001, 0.000002, "%.6f"));
        paramRows.add(doubleRow("Croissance arbre", () -> config.treeGrowthDays,
                v -> config.treeGrowthDays = v, 1.0, 100.0, 1.0, "%.0f"));
        paramRows.add(doubleRow("Croissance herbe", () -> config.herbeProbApparition,
                v -> config.herbeProbApparition = v, 0.0, 0.001, 0.000002, "%.6f"));
        paramRows.add(doubleRow("Proba eruption",  () -> config.laveProbErruption,
                v -> config.laveProbErruption = v, 0.0, 0.05, 0.0005, "%.4f"));
        paramRows.add(doubleRow("Profondeur cratere", () -> (double) config.craterHoleDepth,
                v -> config.craterHoleDepth = (float) v, 0.5, 8.0, 0.5, "%.1f"));
        paramRows.add(doubleRow("Duree eruption (sec)", () -> (double) config.eruptionDurationSec,
                v -> config.eruptionDurationSec = (float) v, 1.0, 30.0, 0.5, "%.1f"));
        paramRows.add(doubleRow("Solidification (sec)", () -> (double) config.solidifyEndSec,
                v -> config.solidifyEndSec = (float) v, 0.5, 30.0, 0.5, "%.1f"));
        paramRows.add(doubleRow("Drainage (sec)",   () -> (double) config.subsidenceIntervalSec,
                v -> config.subsidenceIntervalSec = (float) v, 0.05, 2.0, 0.05, "%.2f"));
        paramRows.add(doubleRow("Viscosite lave",   () -> (double) config.lavaViscosity,
                v -> config.lavaViscosity = (float) v, 1.0, 2.0, 0.1, "%.1f"));
        paramRows.add(doubleRow("Puissance min eruption", () -> (double) config.erruptionPowerMin,
                v -> config.erruptionPowerMin = (float) v, 0.1, 5.0, 0.1, "%.2f"));
        paramRows.add(doubleRow("Puissance max eruption", () -> (double) config.erruptionPowerMax,
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
    private static double round(double v) { return Math.round(v * 1_000_000.0) / 1_000_000.0; }

    public void toggle() { open = !open; }
    public boolean isOpen() { return open; }

    /** Ouvre le menu sur un onglet donné (utilisé au démarrage de la simulation). */
    public void openOnTab(Tab tab) { open = true; activeTab = tab; }

    /**
     * True si le point écran (x, y) tombe dans le rectangle du panneau menu.
     * Utilisé par Landscape pour décider si un clic doit re-focaliser le menu
     * ou bien défocaliser et déclencher un picking.
     */
    public boolean containsScreenPoint(int viewportWidth, int viewportHeight, int x, int y) {
        if (!open) return false;
        int px = viewportWidth - PANEL_WIDTH;
        int py = HEADER_HEIGHT;
        return x >= px && x < viewportWidth && y >= py && y < viewportHeight;
    }

    /** Constantes géométriques utilisées par draw() ET par les helpers de hit-test. */
    private static final int TAB_W       = 78;
    private static final int TAB_SPACING = 4;
    private static final int TAB_TOP_OFFSET = 8;  // py + 8 dans draw()
    private static final int TAB_HEIGHT  = 20;
    private static final int CONTENT_TOP_OFFSET = 64;  // py + 64 dans draw()
    // drawAgents() : header à `y` (baseline), 1ère ligne agent à rowY=y+20.
    // Le highlight rect ligne 0 est dessiné à (rowY-11, ROW_HEIGHT-2) =
    // (y+9, 14). Donc la zone cliquable de la ligne 0 commence à y+9.
    private static final int AGENT_LIST_TOP_OFFSET = 9;
    private static final int VISIBLE_AGENT_ROWS = 22;   // capacite par defaut (avant 1er rendu)

    /** Nb de lignes agent reellement affichables : calcule depuis la hauteur du
     *  panneau a chaque frame dans drawAgents, puis lu par rowAt / clampAgentSelection
     *  / moveSelection. Remplace le cap fixe pour exploiter toute la hauteur du panneau. */
    private int agentRowCapacity = VISIBLE_AGENT_ROWS;

    /** Dernier agent suivi pour lequel on a auto-deplie son espece. Permet de ne
     *  deplier qu'au CHANGEMENT de suivi : sinon l'utilisateur ne pourrait jamais
     *  replier l'espece de l'agent qu'il suit (re-depliee a chaque frame). */
    private Agent lastAutoExpanded = null;

    /**
     * Retourne l'onglet sous le point écran (x, y), ou null si pas sur un
     * onglet. Utilisé par Landscape pour gérer le clic sur les onglets.
     */
    public Tab tabAt(int viewportWidth, int viewportHeight, int x, int y) {
        if (!open) return null;
        int px = viewportWidth - PANEL_WIDTH;
        int py = HEADER_HEIGHT;
        int tabsY = py + TAB_TOP_OFFSET;
        if (y < tabsY || y >= tabsY + TAB_HEIGHT) return null;
        // Trois onglets : AGENTS, AIDE (RACCOURCIS), PARAMS dans cet ordre.
        Tab[] order = { Tab.AGENTS, Tab.AIDE, Tab.PARAMS };
        for (int i = 0; i < order.length; i++) {
            int tx = px + 8 + i * (TAB_W + TAB_SPACING);
            if (x >= tx && x < tx + TAB_W) return order[i];
        }
        return null;
    }

    /**
     * Sélectionne directement un onglet (utilisé par Landscape sur clic).
     * Retourne true si effectivement changé d'onglet (utile pour reset
     * selectedIndex / agentScroll si besoin côté appelant).
     */
    public boolean setActiveTab(Tab tab) {
        if (!open || tab == null) return false;
        if (activeTab == tab) return false;
        activeTab = tab;
        selectedIndex = 0;
        agentScroll = 0;
        return true;
    }

    /**
     * Si l'onglet AGENTS est actif et que (x,y) tombe sur une ligne VISIBLE du
     * panneau, retourne son index dans les lignes visibles ; sinon -1.
     */
    public int rowAt(int viewportWidth, int viewportHeight, int x, int y) {
        if (!open || activeTab != Tab.AGENTS) return -1;
        int px = viewportWidth - PANEL_WIDTH;
        if (x < px || x >= viewportWidth) return -1;
        int contentY = HEADER_HEIGHT + CONTENT_TOP_OFFSET;
        int firstRowY = contentY + AGENT_LIST_TOP_OFFSET;
        if (y < firstRowY) return -1;
        int visualRow = (y - firstRowY) / ROW_HEIGHT;
        if (visualRow < 0 || visualRow >= agentRowCapacity) return -1;
        int idx = agentScroll + visualRow;
        int rowCount = new AgentRoster(world).visibleRows(expanded).size();
        if (idx >= rowCount) return -1;
        return idx;
    }

    /** Vrai si la ligne visible a cet index est une entete d'espece. */
    public boolean isHeaderRow(int rowIndex) {
        java.util.List<AgentRoster.Row> rows = new AgentRoster(world).visibleRows(expanded);
        return rowIndex >= 0 && rowIndex < rows.size() && rows.get(rowIndex).header;
    }

    /** Deplie/replie l'espece de la ligne entete a cet index (no-op si espece vide
     *  ou si la ligne n'est pas une entete). Met aussi le curseur sur cette ligne. */
    public void toggleHeader(int rowIndex) {
        AgentRoster roster = new AgentRoster(world);
        java.util.List<AgentRoster.Row> rows = roster.visibleRows(expanded);
        if (rowIndex < 0 || rowIndex >= rows.size()) return;
        AgentRoster.Row row = rows.get(rowIndex);
        if (!row.header) return;
        if (roster.groups().get(row.sp.ordinal()).agents.size() == 0) return;
        expanded[row.sp.ordinal()] = !expanded[row.sp.ordinal()];
        selectedIndex = rowIndex;
    }

    /** Suit l'agent de la ligne (non-entete) a cet index. Retourne true si un agent
     *  a ete selectionne (-> l'appelant active le suivi camera et defocalise). */
    public boolean followAgentRow(int rowIndex) {
        java.util.List<AgentRoster.Row> rows = new AgentRoster(world).visibleRows(expanded);
        if (rowIndex < 0 || rowIndex >= rows.size()) return false;
        AgentRoster.Row row = rows.get(rowIndex);
        if (row.header || row.agent == null) return false;
        landscape.setSelectedAgent(row.agent, row.localIndex);
        selectedIndex = rowIndex;
        return true;
    }

    /** Synchro vue 3D -> menu : deplie l'espece de l'agent et place le curseur sur
     *  sa ligne. Appele apres un picking dans la scene. */
    public void syncToAgent(Agent a) {
        if (a == null) return;
        AgentRoster.Species sp = AgentRoster.speciesOf(a);
        if (sp == null) return;
        expanded[sp.ordinal()] = true;
        java.util.List<AgentRoster.Row> rows = new AgentRoster(world).visibleRows(expanded);
        for (int i = 0; i < rows.size(); i++) {
            if (rows.get(i).agent == a) { selectedIndex = i; break; }
        }
    }

    /** Retourne true si la touche a été consommée par le menu. */
    public boolean handleKey(int keyCode) {
        if (!open) return false;
        switch (keyCode) {
            case KeyEvent.VK_M:
                open = false;
                return true;
            case KeyEvent.VK_TAB:
                // Cycle AGENTS -> RACCOURCIS (AIDE) -> PARAMS -> AGENTS.
                // AGENTS en premier car le plus consulté ; PARAMS en dernier
                // pour éviter les manipulations involontaires des sliders.
                if (activeTab == Tab.AGENTS)       activeTab = Tab.AIDE;
                else if (activeTab == Tab.AIDE)    activeTab = Tab.PARAMS;
                else                                activeTab = Tab.AGENTS;
                selectedIndex = 0;
                agentScroll = 0;
                return true;
            case KeyEvent.VK_UP:
            case KeyEvent.VK_Z:     moveSelection(-1); return true;
            case KeyEvent.VK_DOWN:
            case KeyEvent.VK_S:     moveSelection(+1); return true;
            case KeyEvent.VK_LEFT:
            case KeyEvent.VK_Q:
                if (activeTab == Tab.PARAMS && !paramRows.isEmpty()) {
                    paramRows.get(selectedIndex).dec.run();
                    world.applyLiveConfig();
                }
                return true;
            case KeyEvent.VK_RIGHT:
            case KeyEvent.VK_D:
                if (activeTab == Tab.PARAMS && !paramRows.isEmpty()) {
                    paramRows.get(selectedIndex).inc.run();
                    world.applyLiveConfig();
                }
                return true;
            case KeyEvent.VK_ENTER:
                if (activeTab == Tab.AGENTS) {
                    if (isHeaderRow(selectedIndex)) {
                        toggleHeader(selectedIndex);
                    } else {
                        followAgentRow(selectedIndex);
                    }
                }
                return true;
            default: return false;
        }
    }

    /**
     * Avance/recule la sélection de `notch` pas (positif = vers le bas,
     * comme la molette). Utilisé pour le scroll molette sur le panneau focalisé.
     */
    public void scroll(int notch) {
        if (!open) return;
        moveSelection(notch);
    }

    private void moveSelection(int delta) {
        int n;
        if (activeTab == Tab.PARAMS)        n = paramRows.size();
        else if (activeTab == Tab.AGENTS)   n = new AgentRoster(world).visibleRows(expanded).size();
        else                                 n = 0;
        if (n == 0) return;
        selectedIndex = (selectedIndex + delta + n) % n;
        // Petit auto-scroll dans la vue Agents.
        if (activeTab == Tab.AGENTS) {
            if (selectedIndex < agentScroll) agentScroll = selectedIndex;
            if (selectedIndex >= agentScroll + agentRowCapacity)
                agentScroll = selectedIndex - agentRowCapacity + 1;
        }
    }

    public void draw(GL2 gl, UiRenderer ui, int viewportWidth, int viewportHeight) {
        draw(gl, ui, viewportWidth, viewportHeight, true);
    }

    /**
     * focused : true = capture le clavier (alpha normal), false = panneau
     * « parqué » plus transparent pour signaler que les raccourcis du jeu sont
     * de nouveau actifs en dehors du menu.
     */
    public void draw(GL2 gl, UiRenderer ui, int viewportWidth, int viewportHeight, boolean focused) {
        if (!open) return;

        int px = viewportWidth - PANEL_WIDTH;
        int py = HEADER_HEIGHT;
        int pw = PANEL_WIDTH;
        int ph = viewportHeight - HEADER_HEIGHT;

        // Fond semi-transparent — on voit la simulation derrière.
        float bgAlpha = focused ? 0.85f : 0.55f;
        ui.drawQuad(gl, px, py, pw, ph, 0.08f, 0.10f, 0.14f, bgAlpha);
        ui.drawBorder(gl, px, py, pw, ph, 0.4f, 0.5f, 0.65f, 1f);

        // Onglets (largeur ajustée pour faire tenir 3 onglets dans le panneau).
        int tabW = 78;
        int tabSpacing = 4;
        drawTab(gl, ui, px + 8,                              py + 8, tabW, 20, "AGENTS",    activeTab == Tab.AGENTS, viewportHeight);
        drawTab(gl, ui, px + 8 + tabW + tabSpacing,          py + 8, tabW, 20, "RACCOURCIS",activeTab == Tab.AIDE,   viewportHeight);
        drawTab(gl, ui, px + 8 + 2*(tabW + tabSpacing),      py + 8, tabW, 20, "PARAMS",    activeTab == Tab.PARAMS, viewportHeight);

        // Hint clavier — déplacé juste sous les onglets pour être visible.
        ui.drawText(gl, px + 10, py + 46, viewportHeight,
                "Tab: changer onglet   m: fermer", 0.65f, 0.75f, 0.95f);

        // Contenu de l'onglet.
        int contentY = py + 64;
        int contentH = ph - 80;
        switch (activeTab) {
            case PARAMS: drawParams(gl, ui, px, contentY, pw, contentH, viewportHeight); break;
            case AGENTS: drawAgents(gl, ui, px, contentY, pw, contentH, viewportHeight); break;
            case AIDE:   drawShortcuts(gl, ui, px, contentY, pw, contentH, viewportHeight); break;
        }
    }

    private void drawShortcuts(GL2 gl, UiRenderer ui, int px, int y, int pw, int ph, int viewportHeight) {
        int rowY = y + ROW_HEIGHT;
        for (String[] s : SHORTCUTS) {
            ui.drawText(gl, px + 10, rowY, viewportHeight, "[" + s[0] + "]", 1f, 0.9f, 0.5f);
            ui.drawText(gl, px + 80, rowY, viewportHeight, s[1], 0.9f, 0.9f, 0.9f);
            rowY += ROW_HEIGHT;
        }
    }

    private void drawTab(GL2 gl, UiRenderer ui, int x, int y, int w, int h, String label,
                         boolean active, int viewportHeight) {
        if (active) {
            ui.drawQuad(gl, x, y, w, h, 0.25f, 0.35f, 0.50f, 1f);
        } else {
            ui.drawQuad(gl, x, y, w, h, 0.12f, 0.16f, 0.22f, 1f);
        }
        ui.drawBorder(gl, x, y, w, h, 0.35f, 0.45f, 0.60f, 1f);
        int textW = ui.textWidth(label);
        ui.drawText(gl, x + (w - textW) / 2, y + h - 6, viewportHeight,
                label, 0.95f, 0.95f, 0.95f);
    }

    private void drawParams(GL2 gl, UiRenderer ui, int px, int y, int pw, int ph, int viewportHeight) {
        int rowY = y + ROW_HEIGHT;
        for (int i = 0; i < paramRows.size(); i++) {
            Row r = paramRows.get(i);
            boolean sel = (i == selectedIndex);
            if (sel) {
                ui.drawQuad(gl, px + 4, rowY - 12, pw - 8, ROW_HEIGHT - 2,
                        0.20f, 0.30f, 0.45f, 1f);
            }
            ui.drawText(gl, px + 10, rowY, viewportHeight, r.label, 0.95f, 0.95f, 0.95f);
            String val = (sel ? "< " : "  ") + r.value.get() + (sel ? " >" : "  ");
            int textW = ui.textWidth(val);
            ui.drawText(gl, px + pw - 12 - textW, rowY, viewportHeight,
                    val, 1f, 1f, 0.7f);
            rowY += ROW_HEIGHT;
        }
        // V7 — bulle d'aide de la ligne sélectionnée, en bas du panneau.
        if (selectedIndex >= 0 && selectedIndex < paramRows.size()) {
            String help = paramRows.get(selectedIndex).help;
            if (help != null && !help.isEmpty()) {
                int hy = y + ph - 8;
                ui.drawQuad(gl, px + 4, hy - 12, pw - 8, 16, 0.10f, 0.13f, 0.18f, 0.95f);
                ui.drawText(gl, px + 8, hy, viewportHeight, help, 0.7f, 0.85f, 1f);
            }
        }
    }

    private void drawAgents(GL2 gl, UiRenderer ui, int px, int y, int pw, int ph, int viewportHeight) {
        AgentRoster roster = new AgentRoster(world);
        autoExpandFollowed(roster);
        java.util.List<AgentRoster.Row> rows = roster.visibleRows(expanded);
        // Capacite = ce que la hauteur du panneau permet (1ere ligne a y+20).
        agentRowCapacity = Math.max(1, (ph - 20) / ROW_HEIGHT);
        clampAgentSelection(rows.size());

        int visible = Math.min(agentRowCapacity, Math.max(0, rows.size() - agentScroll));
        int rowY = y + 20;
        for (int vr = 0; vr < visible; vr++) {
            int idx = agentScroll + vr;
            AgentRoster.Row row = rows.get(idx);
            AgentRoster.Group g = roster.groups().get(row.sp.ordinal());
            boolean sel = (idx == selectedIndex);
            if (sel) {
                ui.drawQuad(gl, px + 4, rowY - 11, pw - 8, ROW_HEIGHT - 2,
                        0.20f, 0.30f, 0.45f, 1f);
            }
            if (row.header) {
                int count = g.agents.size();
                String marker = (count == 0) ? "[ ]" : (expanded[row.sp.ordinal()] ? "[-]" : "[+]");
                String line = marker + " " + g.name + " " + count;
                if (count == 0) ui.drawText(gl, px + 8, rowY, viewportHeight, line, 0.5f, 0.5f, 0.5f);
                else            ui.drawText(gl, px + 8, rowY, viewportHeight, line, g.color[0], g.color[1], g.color[2]);
            } else {
                Agent a = row.agent;
                String line = String.format(" %c#%d E%d/%d (%d,%d) %s",
                        g.prefix, row.localIndex, AgentRoster.energy(a), AgentRoster.maxEnergy(a),
                        a.x, a.y, a.getCurrentBehavior());
                ui.drawText(gl, px + 14, rowY, viewportHeight, line, g.color[0], g.color[1], g.color[2]);
            }
            rowY += ROW_HEIGHT;
        }
    }

    /** Deplie l'espece de l'agent suivi UNE SEULE FOIS, au changement de suivi
     *  (on voit qui on suit, sans empecher l'utilisateur de replier ensuite). */
    private void autoExpandFollowed(AgentRoster roster) {
        Agent sel = landscape.getSelectedAgent();
        if (sel == lastAutoExpanded) return;   // deja traite -> laisse l'utilisateur replier
        lastAutoExpanded = sel;
        if (sel == null) return;
        AgentRoster.Species sp = AgentRoster.speciesOf(sel);
        if (sp != null) expanded[sp.ordinal()] = true;
    }

    /** Reclampe curseur + scroll sur le nb de lignes visibles (qui change quand on
     *  deplie/replie ou que la population evolue). */
    private void clampAgentSelection(int rowCount) {
        if (rowCount <= 0) { selectedIndex = 0; agentScroll = 0; return; }
        if (selectedIndex >= rowCount) selectedIndex = rowCount - 1;
        if (selectedIndex < 0) selectedIndex = 0;
        int maxScroll = Math.max(0, rowCount - agentRowCapacity);
        if (agentScroll > maxScroll) agentScroll = maxScroll;
        if (selectedIndex < agentScroll) agentScroll = selectedIndex;
        else if (selectedIndex >= agentScroll + agentRowCapacity) agentScroll = selectedIndex - agentRowCapacity + 1;
    }
}
