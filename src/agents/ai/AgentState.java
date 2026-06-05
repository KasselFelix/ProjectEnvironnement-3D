package agents.ai;

/**
 * États d'un agent dans la FSM de décision. L'ordre n'a pas de sémantique de
 * priorité (la priorité est portée par decideState). EAT n'est pas choisi par
 * decideState : c'est un libellé positionné en post-mouvement. SEEK_FOOD = le
 * mouton affamé se dirige vers l'herbe la plus proche en vue (cf. Mouton).
 */
public enum AgentState {
    ON_FIRE, FLEE_PREDATOR, HUNT, SEARCH, EAT, SEEK_LAND, SEEK_WATER, SEEK_FOOD, WANDER, CONTROLLED;

    /** Orientation opposée (0↔2, 1↔3). -1 reste -1 (pas de cible). */
    public static int opposite(int dir) {
        return dir < 0 ? -1 : (dir + 2) % 4;
    }
}
