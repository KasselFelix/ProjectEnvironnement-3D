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
    BROUTER       ("Brouter",       true),
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
            case MANGER:  return a.canEatCarcassNow();
            case HURLER:  return (a instanceof Loup) && ((Loup) a).canHowlNow();
            case BROUTER: return a.canGrazeNow();
            default:      return false;
        }
    }

    /** Declenche l'action (pose un flag consomme par Agent.step au prochain tour pilote). */
    public void execute(Agent a) {
        if (!available(a)) return;
        switch (this) {
            case MANGER:  a.playerWantsEat = true; break;
            case HURLER:  ((Loup) a).playerWantsHowl = true; break;
            case BROUTER: a.playerWantsGraze = true; break;
            default: break;
        }
    }

    /** Fraction [0,1] du cooldown restant pour cet agent (0 = pret / sans objet) — balayage radial UI. */
    public double cooldownFraction(Agent a) {
        switch (this) {
            case MANGER:  return a.eatBiteCooldownFraction();
            case HURLER:  return (a instanceof Loup) ? ((Loup) a).howlCooldownFraction() : 0.0;
            case BROUTER: return a.eatBiteCooldownFraction();
            default:      return 0.0;
        }
    }

    /** Secondes reelles restantes du cooldown (compte a rebours UI). */
    public double cooldownSeconds(Agent a) {
        switch (this) {
            case MANGER:  return a.eatBiteCooldownSeconds();
            case HURLER:  return (a instanceof Loup) ? ((Loup) a).howlCooldownSeconds() : 0.0;
            case BROUTER: return a.eatBiteCooldownSeconds();
            default:      return 0.0;
        }
    }

    /** Action en cours d'execution prolongee (ex: loup en train de hurler) — halo "actif" UI. */
    public boolean isActive(Agent a) {
        return this == HURLER && (a instanceof Loup) && ((Loup) a).isHowlingControlled();
    }
}
