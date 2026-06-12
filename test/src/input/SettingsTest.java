package input;

import com.jogamp.newt.event.KeyEvent;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SettingsTest {

    @Test
    void roundTripBindings() throws Exception {
        Path f = Files.createTempFile("settings", ".properties");
        Settings s1 = new Settings(f);
        s1.bindings().rebindPrimary(GameAction.CAM_FWD, KeyEvent.VK_J);
        s1.save();

        Settings s2 = new Settings(f);
        assertEquals(GameAction.CAM_FWD, s2.bindings().actionFor(KeyEvent.VK_J, false));
        assertNull(s2.bindings().actionFor(KeyEvent.VK_Z, false));
    }

    @Test
    void fichierAbsentOuCorrompuDonneLesDefauts() throws Exception {
        Path absent = Files.createTempDirectory("nosettings").resolve("settings.properties");
        Settings s = new Settings(absent);
        assertEquals(GameAction.CAM_FWD, s.bindings().actionFor(KeyEvent.VK_Z, false));

        Path corrompu = Files.createTempFile("bad", ".properties");
        Files.write(corrompu, "key.ACTION_INCONNUE=42\nkey.CAM_FWD=abc\n".getBytes("UTF-8"));
        Settings s2 = new Settings(corrompu);
        assertNull(s2.bindings().actionFor(KeyEvent.VK_Z, false), "CAM_FWD present mais illisible => vide");
        assertEquals(GameAction.AGENT_FWD, s2.bindings().actionFor(KeyEvent.VK_Z, true), "le reste = defauts");
    }

    @Test
    void prefsUiPersistees() throws Exception {
        Path f = Files.createTempFile("settings", ".properties");
        Settings s1 = new Settings(f);
        s1.setUiPref("graph", "true");
        s1.save();
        assertEquals("true", new Settings(f).uiPref("graph", "false"));
        assertEquals("def",  new Settings(f).uiPref("inconnu", "def"));
    }

    @Test
    void nomsDeTouchesAscii() {
        assertEquals("Z",      KeyNames.name(KeyEvent.VK_Z));
        assertEquals("ESPACE", KeyNames.name(KeyEvent.VK_SPACE));
        assertEquals("MAJ",    KeyNames.name(KeyEvent.VK_SHIFT));
        assertEquals("F12",    KeyNames.name(KeyEvent.VK_F12));
        assertEquals("HAUT",   KeyNames.name(KeyEvent.VK_UP));
        assertTrue(KeyNames.name(0x1234).startsWith("KEY_"));
    }
}
