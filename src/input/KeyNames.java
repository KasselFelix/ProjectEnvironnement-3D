package input;

import com.jogamp.newt.event.KeyEvent;
import java.util.HashMap;
import java.util.Map;

/** Noms ASCII affichables des codes NEWT (UI GLUT = pas d'accents, pas de fleches unicode). */
public final class KeyNames {
    private static final Map<Integer, String> SPECIAL = new HashMap<>();
    static {
        SPECIAL.put((int) KeyEvent.VK_SPACE,     "ESPACE");
        SPECIAL.put((int) KeyEvent.VK_SHIFT,     "MAJ");
        SPECIAL.put((int) KeyEvent.VK_CONTROL,   "CTRL");
        SPECIAL.put((int) KeyEvent.VK_ALT,       "ALT");
        SPECIAL.put((int) KeyEvent.VK_UP,        "HAUT");
        SPECIAL.put((int) KeyEvent.VK_DOWN,      "BAS");
        SPECIAL.put((int) KeyEvent.VK_LEFT,      "GAUCHE");
        SPECIAL.put((int) KeyEvent.VK_RIGHT,     "DROITE");
        SPECIAL.put((int) KeyEvent.VK_ENTER,     "ENTREE");
        SPECIAL.put((int) KeyEvent.VK_TAB,       "TAB");
        SPECIAL.put((int) KeyEvent.VK_HOME,      "DEBUT");
        SPECIAL.put((int) KeyEvent.VK_PAUSE,     "PAUSE");
        SPECIAL.put((int) KeyEvent.VK_PAGE_UP,   "PG.HAUT");
        SPECIAL.put((int) KeyEvent.VK_PAGE_DOWN, "PG.BAS");
        SPECIAL.put((int) KeyEvent.VK_ADD,       "PAVE +");
        SPECIAL.put((int) KeyEvent.VK_EQUALS,    "=");
    }

    private KeyNames() {}

    public static String name(int keyCode) {
        String s = SPECIAL.get(keyCode);
        if (s != null) return s;
        // VK_F1=112 .. VK_F12=123 are contiguous (verified via javac/KeyEvent inspection)
        if (keyCode >= KeyEvent.VK_F1 && keyCode <= KeyEvent.VK_F12)
            return "F" + (keyCode - KeyEvent.VK_F1 + 1);
        if (keyCode >= 'A' && keyCode <= 'Z') return String.valueOf((char) keyCode);
        if (keyCode >= '0' && keyCode <= '9') return String.valueOf((char) keyCode);
        return "KEY_" + keyCode;
    }
}
