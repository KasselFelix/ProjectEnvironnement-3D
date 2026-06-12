package input;

import com.jogamp.newt.event.KeyEvent;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Table de bindings touche -> action, par contexte. Résolution :
 * pilotage -> PILOTAGE puis SIMULATION puis GLOBAL (shadowing) ;
 * sinon -> SIMULATION puis GLOBAL. Conflits évalués au sein d'UN contexte.
 * ESC est réservée (plein écran, gérée en dur dans Landscape).
 */
public final class KeyBindings {

    private final Map<GameAction, List<Integer>> keys = new EnumMap<>(GameAction.class);

    public KeyBindings() { resetDefaults(); }

    public void resetDefaults() {
        keys.clear();
        for (GameAction a : GameAction.values()) {
            List<Integer> l = new ArrayList<>();
            for (int k : a.defaultKeys) l.add(k);
            keys.put(a, l);
        }
    }

    public List<Integer> keysOf(GameAction a) { return keys.get(a); }

    public boolean isReserved(int keyCode) { return keyCode == KeyEvent.VK_ESCAPE; }

    public GameAction actionFor(int keyCode, boolean controlling) {
        if (controlling) {
            GameAction a = lookup(keyCode, KeyContext.PILOTAGE);
            if (a != null) return a;
        }
        GameAction a = lookup(keyCode, KeyContext.SIMULATION);
        if (a != null) return a;
        return lookup(keyCode, KeyContext.GLOBAL);
    }

    public List<GameAction> actionsForKeyAllContexts(int keyCode) {
        List<GameAction> out = new ArrayList<>();
        for (Map.Entry<GameAction, List<Integer>> e : keys.entrySet())
            if (e.getValue().contains(keyCode)) out.add(e.getKey());
        return out;
    }

    public GameAction rebindPrimary(GameAction action, int keyCode) {
        if (isReserved(keyCode)) throw new IllegalArgumentException("touche reservee");
        GameAction evicted = null;
        for (Map.Entry<GameAction, List<Integer>> e : keys.entrySet()) {
            if (e.getKey() == action || e.getKey().context != action.context) continue;
            if (e.getValue().remove((Integer) keyCode)) evicted = e.getKey();
        }
        List<Integer> l = keys.get(action);
        l.remove((Integer) keyCode);
        if (l.isEmpty()) l.add(keyCode); else l.set(0, keyCode);
        return evicted;
    }

    public void writeTo(java.util.Properties p) {
        for (Map.Entry<GameAction, List<Integer>> e : keys.entrySet()) {
            StringBuilder sb = new StringBuilder();
            for (int k : e.getValue()) { if (sb.length() > 0) sb.append(','); sb.append(k); }
            p.setProperty("key." + e.getKey().name(), sb.toString());
        }
    }

    public void readFrom(java.util.Properties p) {
        resetDefaults();
        for (String name : p.stringPropertyNames()) {
            if (!name.startsWith("key.")) continue;
            GameAction a;
            try { a = GameAction.valueOf(name.substring(4)); }
            catch (IllegalArgumentException unknown) { continue; }
            List<Integer> l = new ArrayList<>();
            String v = p.getProperty(name).trim();
            if (!v.isEmpty())
                for (String s : v.split(",")) try { l.add(Integer.parseInt(s.trim())); } catch (NumberFormatException ignore) {}
            keys.put(a, l);
        }
    }

    private GameAction lookup(int keyCode, KeyContext ctx) {
        for (Map.Entry<GameAction, List<Integer>> e : keys.entrySet())
            if (e.getKey().context == ctx && e.getValue().contains(keyCode)) return e.getKey();
        return null;
    }
}
