package input;

import java.util.EnumSet;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * Traduit les événements clavier (codes NEWT bruts, injectés par Landscape)
 * en actions : les TAP sont dispatchées immédiatement au listener (même thread,
 * comme le switch historique — AUCUN appel GL ici, règle du projet) ; les HELD
 * alimentent un set consulté chaque frame par display().
 */
public final class InputHandler {
    private final KeyBindings bindings;
    private final BooleanSupplier controlling;
    private final Consumer<GameAction> tapListener;
    private final Set<GameAction> held = EnumSet.noneOf(GameAction.class);

    public InputHandler(KeyBindings bindings, BooleanSupplier controlling, Consumer<GameAction> tapListener) {
        this.bindings = bindings; this.controlling = controlling; this.tapListener = tapListener;
    }

    public void onKeyPressed(int keyCode) {
        GameAction a = bindings.actionFor(keyCode, controlling.getAsBoolean());
        if (a == null) return;
        if (a.type == ActionType.HELD) held.add(a);
        else tapListener.accept(a);
    }

    /** Libère TOUTES les actions HELD liées à la touche, tous contextes confondus. */
    public void onKeyReleased(int keyCode) {
        for (GameAction a : bindings.actionsForKeyAllContexts(keyCode))
            if (a.type == ActionType.HELD) held.remove(a);
    }

    public boolean isHeld(GameAction a) { return held.contains(a); }

    /** Reset (bascule de pilotage, perte de focus...). */
    public void clearHeld() { held.clear(); }
}
