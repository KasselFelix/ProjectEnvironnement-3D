package agents.ai;

/**
 * Catégories de connaissances stockées en mémoire sémantique (cf.
 * docs/evolution.txt § 5).
 */
public enum MemoryKind {
    /** Cases d'eau découvertes (à boire / à éviter selon l'espèce). */
    WATER,
    /** Zones où la chasse a réussi (Loup / Humain). */
    HUNTING,
    /** Cases où un prédateur / un danger a été rencontré. */
    DANGER,
    /** Lieux sûrs (p. ex. la bergerie pour un mouton). */
    SAFE_PLACE;
}
