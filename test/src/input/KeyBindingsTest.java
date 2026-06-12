package input;

import com.jogamp.newt.event.KeyEvent;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class KeyBindingsTest {

    @Test
    void defautsCompletsEtResolutionParContexte() {
        KeyBindings b = new KeyBindings();
        for (GameAction a : GameAction.values()) {
            assertFalse(b.keysOf(a).isEmpty(), a + " doit avoir au moins une touche par defaut");
            assertEquals(a.defaultKeys[0], (int) b.keysOf(a).get(0), a + " touche principale");
        }
        assertEquals(GameAction.CAM_FWD,   b.actionFor(KeyEvent.VK_Z, false));
        assertEquals(GameAction.AGENT_FWD, b.actionFor(KeyEvent.VK_Z, true));
        assertEquals(GameAction.VIEW_ABOVE, b.actionFor(KeyEvent.VK_V, true));
        assertEquals(GameAction.TOGGLE_MENU, b.actionFor(KeyEvent.VK_M, false));
        assertEquals(GameAction.TOGGLE_MENU, b.actionFor(KeyEvent.VK_M, true));
        assertNull(b.actionFor(KeyEvent.VK_J, false));
    }

    @Test
    void rebindRemplaceLaPrincipaleEtGardeLesAlias() {
        KeyBindings b = new KeyBindings();
        GameAction evicted = b.rebindPrimary(GameAction.CAM_FWD, KeyEvent.VK_J);
        assertNull(evicted, "pas de conflit attendu");
        assertEquals(GameAction.CAM_FWD, b.actionFor(KeyEvent.VK_J, false));
        assertNull(b.actionFor(KeyEvent.VK_Z, false), "l'ancienne principale Z est liberee");
        assertEquals(GameAction.CAM_FWD, b.actionFor(KeyEvent.VK_UP, false), "l'alias fleche est conserve");
    }

    @Test
    void conflitMemeContexteDesassigneLAncienne() {
        KeyBindings b = new KeyBindings();
        GameAction evicted = b.rebindPrimary(GameAction.ERUPTION, KeyEvent.VK_V);
        assertEquals(GameAction.VIEW_ABOVE, evicted);
        assertEquals(GameAction.ERUPTION, b.actionFor(KeyEvent.VK_V, false));
        assertTrue(b.keysOf(GameAction.VIEW_ABOVE).isEmpty(), "VIEW_ABOVE n'a plus de touche");
    }

    @Test
    void shadowingInterContexteSansConflit() {
        KeyBindings b = new KeyBindings();
        GameAction evicted = b.rebindPrimary(GameAction.TURN_CAM_LEFT, KeyEvent.VK_V);
        assertNull(evicted);
        assertEquals(GameAction.TURN_CAM_LEFT, b.actionFor(KeyEvent.VK_V, true),  "pilotage : shadowe");
        assertEquals(GameAction.VIEW_ABOVE,    b.actionFor(KeyEvent.VK_V, false), "simulation : intacte");
    }

    @Test
    void toucheReserveeRefusee() {
        KeyBindings b = new KeyBindings();
        assertTrue(b.isReserved(KeyEvent.VK_ESCAPE));
        assertThrows(IllegalArgumentException.class,
                () -> b.rebindPrimary(GameAction.ERUPTION, KeyEvent.VK_ESCAPE));
    }

    @Test
    void resetRetablitLesDefauts() {
        KeyBindings b = new KeyBindings();
        b.rebindPrimary(GameAction.CAM_FWD, KeyEvent.VK_J);
        b.resetDefaults();
        assertEquals(GameAction.CAM_FWD, b.actionFor(KeyEvent.VK_Z, false));
        assertNull(b.actionFor(KeyEvent.VK_J, false));
    }
}
