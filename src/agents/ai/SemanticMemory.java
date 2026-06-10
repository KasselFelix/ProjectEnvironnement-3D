package agents.ai;

import java.util.ArrayList;
import java.util.List;

/**
 * Mémoire sémantique d'un agent (cf. docs/evolution.txt § 5) : un ensemble de
 * lieux connus par catégorie ({@link MemoryKind}), chacun portant un score
 * d'utilisation. Capacité variable ; quand elle déborde, on oublie le souvenir
 * le MOINS utilisé (politique LFU actuelle — sera remplacée par priorité
 * composite en tâche 0.3).
 */
public final class SemanticMemory {

    /** Un lieu mémorisé : sa catégorie, sa position, son score d'utilisation. */
    static final class Entry {
        final MemoryKind kind;
        int x, y;                 // mutables : la fusion met a jour sur la derniere observation
        int usage;
        int lastSeen;             // iteration monde du dernier renforcement (recence)
        double value;             // charge utile (FOOD = masse de la carcasse ; 0 sinon)
        Entry(MemoryKind kind, int x, int y, int lastSeen, double value) {
            this.kind = kind; this.x = x; this.y = y; this.usage = 1;
            this.lastSeen = lastSeen; this.value = value;
        }
    }

    /** Capacité par défaut (nombre de souvenirs) avant que le calcul lié au
     *  génome/âge ne la fixe (§ 5.1). */
    public static final int DEFAULT_CAPACITY = 8;

    // Paramètres du calcul de capacité (§ 5.1).
    private static final int CAP_BASE = 8;
    private static final int CAP_INTELLIGENCE_BONUS = 3;  // par cran de l'axe Intelligence
    private static final int CAP_WISDOM_BONUS = 2;        // par cran de l'axe Sagesse
    private static final double CAP_AGE_PENALTY_MAX = 6.0; // perte max au grand âge
    private static final int CAP_MIN = 2;

    /**
     * Capacité mémoire d'un agent (§ 5.1) :
     * {@code BASE + intelligence + sagesse − pénalité d'âge}. La pénalité d'âge
     * croît avec la fraction d'âge et est atténuée par l'axe Longévité (un
     * agent à forte longévité dégénère plus lentement). Plancher à CAP_MIN.
     */
    public static int capacityFor(Genome genome, double ageDays, double lifespanDays) {
        double lifespan = lifespanDays > 0 ? lifespanDays : LifeStage.REFERENCE_LIFESPAN_DAYS;
        double ageFraction = Math.max(0.0, ageDays) / lifespan;
        double agePenalty = Math.min(1.0, ageFraction) * CAP_AGE_PENALTY_MAX / genome.longevityFactor();

        int cap = CAP_BASE
                + genome.get(Axis.INTELLIGENCE).sign * CAP_INTELLIGENCE_BONUS
                + genome.get(Axis.WISDOM).sign * CAP_WISDOM_BONUS
                - (int) Math.round(agePenalty);
        return Math.max(CAP_MIN, cap);
    }

    private final List<Entry> entries = new ArrayList<>();
    private int capacity = DEFAULT_CAPACITY;

    /** Fixe la capacité maximale ; si on déborde déjà, on oublie les moins
     *  utilisés jusqu'à rentrer dans la nouvelle capacité. */
    public void setCapacity(int capacity) {
        this.capacity = Math.max(1, capacity);
        while (entries.size() > this.capacity) forgetLowestPriority();
    }

    /**
     * Mémorise un lieu. Si déjà connu, renforce son score d'usage (pas de
     * doublon). Sinon l'ajoute, en évinçant le souvenir le moins utilisé si la
     * capacité est atteinte (oubli LFU, § 5.2).
     */
    public void remember(MemoryKind kind, int x, int y) {
        addOrMerge(kind, x, y, 0, 0.0);
    }

    /**
     * Ajoute ou fusionne un souvenir exact (même kind/x/y) en préservant
     * {@code value} et {@code lastSeen}. Usage interne partagé par
     * {@link #remember} et {@link #teach}.
     */
    private void addOrMerge(MemoryKind kind, int x, int y, int lastSeen, double value) {
        Entry existing = find(kind, x, y);
        if (existing != null) {
            existing.usage++;
            existing.value = Math.max(existing.value, value);
            existing.lastSeen = Math.max(existing.lastSeen, lastSeen);
            return;
        }
        if (entries.size() >= capacity) forgetLowestPriority();
        entries.add(new Entry(kind, x, y, lastSeen, value));
    }

    /**
     * Renforce un souvenir par FUSION DE PROXIMITE : s'il existe deja un souvenir de
     * meme categorie a distance <= {@code mergeRadius}, on incremente son usage, on
     * met a jour sa position (derniere observation), sa date ({@code now}) et on
     * retient le plus gros butin connu. Sinon on cree un nouveau souvenir (eviction
     * LFU/priorite si plein). {@code dist} = distance tore-aware injectee.
     */
    public void reinforce(MemoryKind kind, int x, int y, double value, int now,
                          int mergeRadius, Distance dist) {
        Entry near = null;
        double bestD = Double.MAX_VALUE;
        for (Entry e : entries) {
            if (e.kind != kind) continue;
            double d = dist.between(e.x, e.y, x, y);
            if (d <= mergeRadius && d < bestD) { bestD = d; near = e; }
        }
        if (near != null) {
            near.usage++;
            near.x = x; near.y = y;
            near.lastSeen = now;
            near.value = Math.max(near.value, value);
            return;
        }
        if (entries.size() >= capacity) forgetLowestPriority();
        entries.add(new Entry(kind, x, y, now, value));
    }

    /** Seuil d'usage a partir duquel un souvenir est ACTIONNABLE (consolide / LTM).
     *  Survie = immediat (1) ; nourriture = corroboration requise (2). */
    public static int consolidationThreshold(MemoryKind kind) {
        return kind == MemoryKind.FOOD ? 2 : 1;
    }

    /** true si le souvenir existe ET est consolide (usage >= seuil de sa categorie). */
    public boolean isConsolidated(MemoryKind kind, int x, int y) {
        Entry e = find(kind, x, y);
        return e != null && e.usage >= consolidationThreshold(kind);
    }

    /** Oublie le souvenir exact (lose-shift : carcasse trouvee absente a l'arrivee). */
    public void forget(MemoryKind kind, int x, int y) {
        Entry e = find(kind, x, y);
        if (e != null) entries.remove(e);
    }

    /** Charge utile memorisee d'un lieu (FOOD = masse de la carcasse ; 0 si absent). */
    public double valueOf(MemoryKind kind, int x, int y) {
        Entry e = find(kind, x, y);
        return e == null ? 0.0 : e.value;
    }

    /** Nombre total de souvenirs. */
    public int size() {
        return entries.size();
    }

    /** true si ce lieu exact est mémorisé dans cette catégorie. */
    public boolean contains(MemoryKind kind, int x, int y) {
        return find(kind, x, y) != null;
    }

    /** Score d'usage d'un lieu (0 s'il n'est pas mémorisé). */
    public int usageOf(MemoryKind kind, int x, int y) {
        Entry e = find(kind, x, y);
        return e == null ? 0 : e.usage;
    }

    /** Fonction de distance injectée (tore-aware côté agent via World.distance). */
    public interface Distance {
        double between(int x1, int y1, int x2, int y2);
    }

    /**
     * Lieu mémorisé le PLUS PROCHE de ({@code fromX}, {@code fromY}) dans la
     * catégorie donnée, ou null si aucun. Distance fournie par l'appelant (pour
     * rester tore-aware sans dépendre de World).
     */
    public int[] nearest(MemoryKind kind, int fromX, int fromY, Distance dist) {
        Entry best = null;
        double bestD = Double.MAX_VALUE;
        for (Entry e : entries) {
            if (e.kind != kind) continue;
            double d = dist.between(fromX, fromY, e.x, e.y);
            if (d < bestD) { bestD = d; best = e; }
        }
        return best == null ? null : new int[]{best.x, best.y};
    }

    /** Transmet TOUS les souvenirs de cette mémoire à {@code student} (§ 8 :
     *  apprentissage social / éducation). L'élève les ajoute (sous contrainte de
     *  sa propre capacité, oubli LFU) en préservant {@code value} et
     *  {@code lastSeen} du professeur. */
    public void teach(SemanticMemory student) {
        for (Entry e : entries) {
            student.addOrMerge(e.kind, e.x, e.y, e.lastSeen, e.value);
        }
    }

    private Entry find(MemoryKind kind, int x, int y) {
        for (Entry e : entries) {
            if (e.kind == kind && e.x == x && e.y == y) return e;
        }
        return null;
    }

    /**
     * Oublie le souvenir au plus faible score d'usage (politique LFU actuelle).
     * Le nom anticipe la tâche 0.3 qui remplacera ce critère par un score de
     * priorité composite (usage, recence, valeur) — le corps sera mis à jour
     * à ce moment-là.
     */
    private void forgetLowestPriority() {
        if (entries.isEmpty()) return;
        Entry least = entries.get(0);
        for (Entry e : entries) {
            if (e.usage < least.usage) least = e;
        }
        entries.remove(least);
    }
}
