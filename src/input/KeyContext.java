package input;

/** Contexte de résolution d'une action clavier. */
public enum KeyContext {
    /** Toujours actif (menu, screenshot, pause, exports...). */
    GLOBAL,
    /** Hors pilotage : caméra libre, outils de simulation. */
    SIMULATION,
    /** Agent piloté (touche c). Un binding PILOTAGE shadowe le binding SIMULATION. */
    PILOTAGE
}
