package agents.ai;

/**
 * États d'un agent dans la FSM de décision. L'ordre n'a pas de sémantique de
 * priorité (la priorité est portée par decideState). EAT = état réel retourné
 * par decideState pour un carnivore (Loup ou Ours) qui mange une carcasse présente sur
 * sa cellule ou une case adjacente. SEEK_FOOD = le mouton affamé se dirige
 * vers l'herbe la plus proche en vue (cf. Mouton).
 */
public enum AgentState {
    ON_FIRE, FLEE_LAVA, FLEE_PREDATOR, HUNT, HOWL, LOCALISATION, RECALL_FOOD, // affame, rien en vue -> rejoint une carcasse memorisee
    SCENT_TRACK,   // sous-projet C : le loup piste une proie a l'odeur (perdue de vue)
    WARY,          // sous-projet C : le mouton se mefie d'une trace de loup (sans le voir)
    CONFRONT,      // sous-projet D : berger temeraire confronte un loup pres du troupeau
    SEEK_MATE,     // sous-projet E : agent en rut qui piste l'odeur de seduction d'un partenaire
    SEARCH, EAT, SEEK_LAND, SEEK_WATER, SEEK_FOOD, FOLLOW_PARENT, HERD, HOME, REST, WANDER, CONTROLLED;

    /** Orientation opposée (0↔2, 1↔3). -1 reste -1 (pas de cible). */
    public static int opposite(int dir) {
        return dir < 0 ? -1 : (dir + 2) % 4;
    }
}
