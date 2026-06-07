package ui;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import agents.Agent;
import worlds.WorldOfCells;

/**
 * Source UNIQUE des espèces d'agents pour la couche UI (menu, picking, suivi).
 * Centralise l'ordre d'affichage, la couleur, le préfixe et la liste vivante de
 * chaque espèce, plus le mapping index↔agent et le modèle de lignes visibles du
 * panneau dépliable. Ajouter une 5e espèce = une ligne dans le constructeur.
 *
 * Logique pure (sans OpenGL) → unit-testable. Construit à partir de l'état vivant
 * du monde au moment de l'appel.
 */
public final class AgentRoster {

    /** Ordre = ordre d'affichage dans le panneau. L'ordinal sert d'index dans le
     *  tableau {@code expanded[]}. */
    public enum Species { LOUP, MOUTON, HUMAIN, OURS }

    /** Une espèce : nom affiché, couleur, préfixe d'étiquette, liste vivante. */
    public static final class Group {
        public final Species sp;
        public final String  name;
        public final float[] color;      // RGB de l'espèce (ne pas modifier)
        public final char    prefix;
        public final List<? extends Agent> agents;
        Group(Species sp, String name, float[] color, char prefix, List<? extends Agent> agents) {
            this.sp = sp; this.name = name; this.color = color; this.prefix = prefix; this.agents = agents;
        }
    }

    /** Une ligne visible du panneau : entête d'espèce, ou ligne d'agent. */
    public static final class Row {
        public final boolean header;     // true = entête ; false = ligne agent
        public final Species sp;
        public final int     localIndex; // index dans l'espèce (lignes agent) ; -1 pour une entête
        public final Agent   agent;      // null pour une entête
        Row(boolean header, Species sp, int localIndex, Agent agent) {
            this.header = header; this.sp = sp; this.localIndex = localIndex; this.agent = agent;
        }
    }

    private final List<Group> groups = new ArrayList<>();

    public AgentRoster(WorldOfCells w) {
        groups.add(new Group(Species.LOUP,   "LOUPS",   new float[]{1f,    0.30f, 0.30f}, 'L', w.loups));
        groups.add(new Group(Species.MOUTON, "MOUTONS", new float[]{0.95f, 0.95f, 0.95f}, 'M', w.moutons));
        groups.add(new Group(Species.HUMAIN, "HUMAINS", new float[]{0.40f, 0.60f, 1f},    'H', w.humains));
        groups.add(new Group(Species.OURS,   "OURS",    new float[]{0.60f, 0.42f, 0.22f}, 'O', w.ours));
    }

    public List<Group> groups() { return Collections.unmodifiableList(groups); }

    /** Liste plate ordonnée loups→moutons→humains→ours. */
    public List<Agent> flat() {
        List<Agent> f = new ArrayList<>();
        for (Group g : groups) f.addAll(g.agents);
        return f;
    }

    /** Index global dans {@link #flat()}, ou -1 si l'agent n'est pas présent. */
    public int indexOf(Agent a) {
        int base = 0;
        for (Group g : groups) {
            int i = g.agents.indexOf(a);
            if (i >= 0) return base + i;
            base += g.agents.size();
        }
        return -1;
    }

    /** Agent à l'index global, ou null si hors borne. */
    public Agent at(int globalIndex) {
        if (globalIndex < 0) return null;
        int base = 0;
        for (Group g : groups) {
            if (globalIndex < base + g.agents.size()) return g.agents.get(globalIndex - base);
            base += g.agents.size();
        }
        return null;
    }

    /**
     * Lignes visibles : pour chaque espèce une entête, suivie de ses agents si
     * {@code expanded[sp.ordinal()]}.
     *
     * @param expanded tableau de longueur Species.values().length (un flag de dépliage par espèce).
     */
    public List<Row> visibleRows(boolean[] expanded) {
        if (expanded.length < Species.values().length)
            throw new IllegalArgumentException(
                "expanded.length=" + expanded.length + " < " + Species.values().length);
        List<Row> rows = new ArrayList<>();
        for (Group g : groups) {
            rows.add(new Row(true, g.sp, -1, null));
            if (expanded[g.sp.ordinal()]) {
                for (int i = 0; i < g.agents.size(); i++) {
                    rows.add(new Row(false, g.sp, i, g.agents.get(i)));
                }
            }
        }
        return rows;
    }

    /** Espèce d'un agent, ou null si type inconnu. */
    public static Species speciesOf(Agent a) {
        if (a instanceof agents.Loup)   return Species.LOUP;
        if (a instanceof agents.Mouton) return Species.MOUTON;
        if (a instanceof agents.Humain) return Species.HUMAIN;
        if (a instanceof agents.Ours)   return Species.OURS;
        return null;
    }

    /** Énergie courante d'un agent (par espèce), 0 si type inconnu. */
    public static int energy(Agent a) {
        if (a instanceof agents.Loup)   return ((agents.Loup) a).getEnergie();
        if (a instanceof agents.Mouton) return (int) ((agents.Mouton) a).getEnergie(); // Mouton: energie en double
        if (a instanceof agents.Humain) return ((agents.Humain) a).getEnergie();
        if (a instanceof agents.Ours)   return ((agents.Ours) a).getEnergie();
        return 0;
    }

    /** Énergie max d'un agent (par espèce), 1 si type inconnu. */
    public static int maxEnergy(Agent a) {
        if (a instanceof agents.Loup)   return ((agents.Loup) a).getEnergieMax();
        if (a instanceof agents.Mouton) return (int) ((agents.Mouton) a).getEnergieMax(); // Mouton: energie en double
        if (a instanceof agents.Humain) return ((agents.Humain) a).getEnergieMax();
        if (a instanceof agents.Ours)   return ((agents.Ours) a).getEnergieMax();
        return 1;
    }
}
