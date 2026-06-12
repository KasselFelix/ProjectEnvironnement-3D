package input;

import com.jogamp.newt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class InputHandlerTest {
    private final List<GameAction> taps = new ArrayList<>();
    private boolean controlling = false;

    private InputHandler make() {
        return new InputHandler(new KeyBindings(), () -> controlling, taps::add);
    }

    @Test
    void tapDeclencheLeListenerUneFoisParPress() {
        InputHandler h = make();
        h.onKeyPressed(KeyEvent.VK_V);
        assertEquals(java.util.Arrays.asList(GameAction.VIEW_ABOVE), taps);
        assertFalse(h.isHeld(GameAction.VIEW_ABOVE), "TAP ne va jamais dans le held-set");
    }

    @Test
    void heldSuitPressEtRelease() {
        controlling = true;
        InputHandler h = make();
        h.onKeyPressed(KeyEvent.VK_Z);
        assertTrue(h.isHeld(GameAction.AGENT_FWD));
        h.onKeyPressed(KeyEvent.VK_Z);
        assertTrue(h.isHeld(GameAction.AGENT_FWD));
        h.onKeyReleased(KeyEvent.VK_Z);
        assertFalse(h.isHeld(GameAction.AGENT_FWD));
        assertTrue(taps.isEmpty(), "une action HELD ne produit pas d'impulsion");
    }

    @Test
    void releaseLibereMemeApresChangementDeContexte() {
        controlling = true;
        InputHandler h = make();
        h.onKeyPressed(KeyEvent.VK_Z);
        controlling = false;
        h.onKeyReleased(KeyEvent.VK_Z);
        assertFalse(h.isHeld(GameAction.AGENT_FWD), "pas de touche fantome apres changement de mode");
    }

    @Test
    void clearHeldVideTout() {
        controlling = true;
        InputHandler h = make();
        h.onKeyPressed(KeyEvent.VK_Z);
        h.onKeyPressed(KeyEvent.VK_A);
        h.clearHeld();
        assertFalse(h.isHeld(GameAction.AGENT_FWD));
        assertFalse(h.isHeld(GameAction.TURN_CAM_LEFT));
    }

    @Test
    void toucheNonAssigneeEstIgnoree() {
        InputHandler h = make();
        h.onKeyPressed(KeyEvent.VK_J);
        assertTrue(taps.isEmpty());
    }
}
