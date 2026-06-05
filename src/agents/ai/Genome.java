package agents.ai;

import java.util.EnumMap;
import java.util.Random;

/**
 * Génome d'un agent : un {@link Pole} par {@link Axis} (cf. docs/evolution.txt
 * § 4). Un génome neuf est NEUTRE sur tous les axes (profil par défaut § 4.5).
 */
public final class Genome {

    private final EnumMap<Axis, Pole> axes = new EnumMap<>(Axis.class);

    // Snapshots des axes des DEUX parents (= les grands-parents des enfants à
    // venir), capturés à la naissance. null pour un fondateur (§ 4.4, saut de
    // génération). On ne garde qu'UNE génération de recul — pas de chaîne.
    private EnumMap<Axis, Pole> parentA;
    private EnumMap<Axis, Pole> parentB;

    /** Construit un génome NEUTRE sur tous les axes. */
    public Genome() {
        for (Axis axis : Axis.values()) {
            axes.put(axis, Pole.NEUTRAL);
        }
    }

    /** Le pôle de l'agent sur cet axe. */
    public Pole get(Axis axis) {
        return axes.get(axis);
    }

    /** Fixe le pôle sur cet axe (usage interne héritage/mutation + tests). */
    public void set(Axis axis, Pole pole) {
        axes.put(axis, pole);
    }

    /** Mémorise les génomes des deux parents (snapshots) → ils deviennent les
     *  grands-parents disponibles pour le saut de génération des enfants. */
    public void rememberParents(Genome a, Genome b) {
        this.parentA = new EnumMap<>(a.axes);
        this.parentB = new EnumMap<>(b.axes);
    }

    // ===== Modificateurs mécaniques (§ 4.1) =====
    // Chaque axe module multiplicativement le trait numérique correspondant.

    /** Facteur sur la proba de reproduction (axe Fertilité, § 4.1/§ 4.3) :
     *  FERTILE ×1.5, NEUTRE ×1.0, INFERTILE ×0.2 (cul-de-sac mou). */
    public double reproProbaFactor() {
        switch (get(Axis.FERTILITY)) {
            case POSITIVE: return 1.5;
            case NEGATIVE: return 0.2;
            default:       return 1.0;
        }
    }

    /** Facteur sur la durée de vie (axe Longévité, § 4.1) :
     *  LONGÉVITÉ ×1.4, NEUTRE ×1.0, DÉGÉNÉRESCENCE ×0.6. */
    public double longevityFactor() {
        switch (get(Axis.LONGEVITY)) {
            case POSITIVE: return 1.4;
            case NEGATIVE: return 0.6;
            default:       return 1.0;
        }
    }

    /**
     * Construit le génome d'un enfant à partir des deux parents (§ 4.4). Pour
     * chaque axe, l'enfant prend le pôle d'un des deux parents (tirage 50/50).
     *
     * @param rng source d'aléa (injectée pour la testabilité déterministe)
     * @param typeMutationRate proba de mutation d'un axe (§ 4.2)
     * @param grandparentProb proba d'hériter d'un grand-parent (§ 4.4)
     */
    public static Genome inherit(Genome p1, Genome p2, Random rng,
                                 double typeMutationRate, double grandparentProb) {
        Genome child = new Genome();
        for (Axis axis : Axis.values()) {
            Pole pole;
            Pole gp = rng.nextDouble() < grandparentProb ? grandparentPole(p1, p2, axis, rng) : null;
            if (gp != null) {
                pole = gp;                                  // saut de génération
            } else {
                pole = inheritAxis(p1.get(axis), p2.get(axis), rng);
            }
            if (rng.nextDouble() < typeMutationRate) pole = mutate(pole, rng);
            child.set(axis, pole);
        }
        child.rememberParents(p1, p2);
        return child;
    }

    /** Pôle d'un grand-parent tiré au hasard parmi ceux disponibles sur cet axe
     *  (les snapshots parentaux des deux parents), ou null si aucun grand-parent
     *  connu (parents fondateurs). */
    private static Pole grandparentPole(Genome p1, Genome p2, Axis axis, Random rng) {
        Pole[] gp = {
            p1.parentA == null ? null : p1.parentA.get(axis),
            p1.parentB == null ? null : p1.parentB.get(axis),
            p2.parentA == null ? null : p2.parentA.get(axis),
            p2.parentB == null ? null : p2.parentB.get(axis),
        };
        int count = 0;
        for (Pole p : gp) if (p != null) count++;
        if (count == 0) return null;
        int pick = rng.nextInt(count);
        for (Pole p : gp) {
            if (p != null && pick-- == 0) return p;
        }
        return null; // inatteignable
    }

    /** Mutation d'un axe d'UN cran (§ 4.2) : depuis NEUTRE → un pôle au hasard ;
     *  depuis un pôle → retour au NEUTRE. */
    private static Pole mutate(Pole pole, Random rng) {
        if (pole == Pole.NEUTRAL) {
            return rng.nextBoolean() ? Pole.POSITIVE : Pole.NEGATIVE;
        }
        return Pole.NEUTRAL;
    }

    /** Probabilité de retomber au NEUTRE quand les deux parents sont à des pôles
     *  OPPOSÉS sur le même axe (§ 4.4) — les pôles « s'annulent » génétiquement. */
    private static final double CONFLICT_NEUTRAL_PROB = 0.6;

    /** Résout l'héritage d'UN axe à partir des pôles des deux parents. */
    private static Pole inheritAxis(Pole a, Pole b, Random rng) {
        if (a == b) return a;
        boolean opposite = a != Pole.NEUTRAL && b != Pole.NEUTRAL; // l'un +, l'autre −
        if (opposite && rng.nextDouble() < CONFLICT_NEUTRAL_PROB) {
            return Pole.NEUTRAL;
        }
        return rng.nextBoolean() ? a : b;
    }
}
