package input;

import agents.Agent;
import agents.Loup;

/**
 * Actions assignables aux slots de la hotbar (mode pilotage). Phase 1 : seules
 * MANGER et HURLER sont implementees ; les autres sont des slots visibles mais
 * grises (reserves aux mecaniques futures). VIDE = slot libre.
 */
public enum HotbarAction {
    VIDE          ("",              true),
    FRAPPER       ("Frapper",       false),
    MANGER        ("Manger",        true),
    HURLER        ("Hurler",        true),
    SE_REPOSER    ("Se reposer",    false),
    BROUTER       ("Brouter",       false),
    BOIRE         ("Boire",         false),
    SAUTER        ("Sauter",        false),
    ORDRE         ("Donner ordre",  false),
    SE_SOUMETTRE  ("Se soumettre",  false),
    SE_REPRODUIRE ("Se reproduire", false);

    public final String label;       // ASCII (UI GLUT)
    private final boolean implemented;

    HotbarAction(String label, boolean implemented) { this.label = label; this.implemented = implemented; }

    public boolean isImplemented() { return this != VIDE && implemented; }

    /** L'action est-elle realisable MAINTENANT par cet agent (grisage UI + gate d'execution) ? */
    public boolean available(Agent a) {
        if (!isImplemented()) return false;
        switch (this) {
            case MANGER: return a.canEatCarcassNow();
            case HURLER: return (a instanceof Loup) && ((Loup) a).canHowlNow();
            default:     return false;
        }
    }

    /** Declenche l'action (pose un flag consomme par Agent.step au prochain tour pilote). */
    public void execute(Agent a) {
        if (!available(a)) return;
        switch (this) {
            case MANGER: a.playerWantsEat = true; break;
            case HURLER: ((Loup) a).playerWantsHowl = true; break;
            default: break;
        }
    }
}
