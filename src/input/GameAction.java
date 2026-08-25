package input;

import com.jogamp.newt.event.KeyEvent;

/**
 * Actions nommées du jeu. Chaque action porte son contexte de résolution,
 * son type (impulsion / maintenue) et ses touches PAR DÉFAUT (multi-touches :
 * la 1re est la « principale » affichée/rebindée, les suivantes des alias).
 * L'id de persistance est name() — STABLE, ne pas renommer sans migration.
 */
public enum GameAction {
    // ----- GLOBAL -----
    TOGGLE_MENU      ("Ouvrir/fermer le menu", KeyContext.GLOBAL, ActionType.TAP, KeyEvent.VK_M),
    SCREENSHOT       ("Capture d'ecran",       KeyContext.GLOBAL, ActionType.TAP, KeyEvent.VK_F12),
    PAUSE_SIM        ("Pause simulation",      KeyContext.GLOBAL, ActionType.TAP, KeyEvent.VK_HOME, KeyEvent.VK_PAUSE),
    SPEED_UP         ("Avance rapide",         KeyContext.GLOBAL, ActionType.TAP, KeyEvent.VK_ADD, KeyEvent.VK_EQUALS, KeyEvent.VK_PAGE_UP),
    TOGGLE_GRAPH     ("Graphe populations",    KeyContext.GLOBAL, ActionType.TAP, KeyEvent.VK_G),
    CAMERA_FOLLOW    ("Suivi camera",          KeyContext.GLOBAL, ActionType.TAP, KeyEvent.VK_F),
    TOGGLE_CONTROL   ("Piloter l'agent",       KeyContext.GLOBAL, ActionType.TAP, KeyEvent.VK_C),
    TOGGLE_MINIMAP   ("Minimap",               KeyContext.GLOBAL, ActionType.TAP, KeyEvent.VK_F6),
    EXPORT_GRAPH_PNG ("Export graphe PNG",     KeyContext.GLOBAL, ActionType.TAP, KeyEvent.VK_F7),
    LOAD_PRESET      ("Charger preset",        KeyContext.GLOBAL, ActionType.TAP, KeyEvent.VK_F8),
    EXPORT_GRAPH_CSV ("Export graphe CSV",     KeyContext.GLOBAL, ActionType.TAP, KeyEvent.VK_F9),
    SAVE_PRESET      ("Sauver preset",         KeyContext.GLOBAL, ActionType.TAP, KeyEvent.VK_F10),
    DUMP_STACKS      ("Dump stacks (debug)",   KeyContext.GLOBAL, ActionType.TAP, KeyEvent.VK_F11),

    // ----- SIMULATION (hors pilotage) -----
    VIEW_ABOVE       ("Vue de dessus",         KeyContext.SIMULATION, ActionType.TAP, KeyEvent.VK_V),
    ERUPTION         ("Eruption volcanique",   KeyContext.SIMULATION, ActionType.TAP, KeyEvent.VK_R),
    LIGHTING         ("Eclairage",             KeyContext.SIMULATION, ActionType.TAP, KeyEvent.VK_L),
    HQ_LIGHTING      ("Eclairage HQ",          KeyContext.SIMULATION, ActionType.TAP, KeyEvent.VK_P),
    TOGGLE_OBJECTS   ("Afficher les objets",   KeyContext.SIMULATION, ActionType.TAP, KeyEvent.VK_O),
    DAY_NIGHT        ("Basculer jour/nuit",    KeyContext.SIMULATION, ActionType.TAP, KeyEvent.VK_N),
    HEIGHT_DOWN      ("Relief -",              KeyContext.SIMULATION, ActionType.TAP, KeyEvent.VK_1),
    HEIGHT_UP        ("Relief +",              KeyContext.SIMULATION, ActionType.TAP, KeyEvent.VK_2),
    HELP_CONSOLE     ("Aide (console)",        KeyContext.SIMULATION, ActionType.TAP, KeyEvent.VK_H),
    CAM_FWD          ("Camera avant",          KeyContext.SIMULATION, ActionType.TAP, KeyEvent.VK_Z, KeyEvent.VK_UP),
    CAM_BACK         ("Camera arriere",        KeyContext.SIMULATION, ActionType.TAP, KeyEvent.VK_S, KeyEvent.VK_DOWN),
    CAM_LEFT         ("Camera gauche",         KeyContext.SIMULATION, ActionType.TAP, KeyEvent.VK_Q, KeyEvent.VK_LEFT),
    CAM_RIGHT        ("Camera droite",         KeyContext.SIMULATION, ActionType.TAP, KeyEvent.VK_D, KeyEvent.VK_RIGHT),
    CAM_DOWN         ("Camera bas (maintenu)", KeyContext.SIMULATION, ActionType.HELD, KeyEvent.VK_SPACE),
    CAM_UP           ("Camera haut (maintenu)",KeyContext.SIMULATION, ActionType.HELD, KeyEvent.VK_SHIFT),

    // ----- PILOTAGE (agent contrôlé) -----
    AGENT_FWD        ("Avancer",               KeyContext.PILOTAGE, ActionType.HELD, KeyEvent.VK_Z, KeyEvent.VK_UP),
    AGENT_BACK       ("Reculer",               KeyContext.PILOTAGE, ActionType.HELD, KeyEvent.VK_S, KeyEvent.VK_DOWN),
    AGENT_LEFT       ("Strafe gauche",         KeyContext.PILOTAGE, ActionType.HELD, KeyEvent.VK_Q, KeyEvent.VK_LEFT),
    AGENT_RIGHT      ("Strafe droite",         KeyContext.PILOTAGE, ActionType.HELD, KeyEvent.VK_D, KeyEvent.VK_RIGHT),
    TURN_CAM_LEFT    ("Tourner camera gauche", KeyContext.PILOTAGE, ActionType.HELD, KeyEvent.VK_A),
    TURN_CAM_RIGHT   ("Tourner camera droite", KeyContext.PILOTAGE, ActionType.HELD, KeyEvent.VK_E),
    SPRINT     ("Sprinter (maintenu)", KeyContext.PILOTAGE, ActionType.HELD, KeyEvent.VK_SHIFT),
    WALK       ("Marcher (maintenu)",  KeyContext.PILOTAGE, ActionType.HELD, KeyEvent.VK_W),
    JUMP       ("Sauter (reserve)",    KeyContext.PILOTAGE, ActionType.TAP,  KeyEvent.VK_SPACE),
    // HOTBAR_1..HOTBAR_9 : DOIVENT rester consecutives (Landscape mappe via ordinal()).
    HOTBAR_1   ("Hotbar 1", KeyContext.PILOTAGE, ActionType.TAP, KeyEvent.VK_1),
    HOTBAR_2   ("Hotbar 2", KeyContext.PILOTAGE, ActionType.TAP, KeyEvent.VK_2),
    HOTBAR_3   ("Hotbar 3", KeyContext.PILOTAGE, ActionType.TAP, KeyEvent.VK_3),
    HOTBAR_4   ("Hotbar 4", KeyContext.PILOTAGE, ActionType.TAP, KeyEvent.VK_4),
    HOTBAR_5   ("Hotbar 5", KeyContext.PILOTAGE, ActionType.TAP, KeyEvent.VK_5),
    HOTBAR_6   ("Hotbar 6", KeyContext.PILOTAGE, ActionType.TAP, KeyEvent.VK_6),
    HOTBAR_7   ("Hotbar 7", KeyContext.PILOTAGE, ActionType.TAP, KeyEvent.VK_7),
    HOTBAR_8   ("Hotbar 8", KeyContext.PILOTAGE, ActionType.TAP, KeyEvent.VK_8),
    HOTBAR_9   ("Hotbar 9", KeyContext.PILOTAGE, ActionType.TAP, KeyEvent.VK_9);

    public final String label;
    public final KeyContext context;
    public final ActionType type;
    public final int[] defaultKeys;

    GameAction(String label, KeyContext context, ActionType type, int... defaultKeys) {
        this.label = label; this.context = context; this.type = type; this.defaultKeys = defaultKeys;
    }
}
