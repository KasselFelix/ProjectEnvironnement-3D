package input;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

/**
 * Reglages persistes (settings.properties a la racine du projet, gitigore) :
 * sections key.* (bindings), hotbar.* (layouts, Lot D), ui.* (preferences).
 * Sauvegarde immediate a chaque modification (pas de bouton Appliquer).
 * Coexiste avec presets/preset.json (F8/F10) qui ne couvre QUE SimulationConfig.
 */
public final class Settings {
    public static final Path DEFAULT_PATH = Paths.get("settings.properties");

    private final Path path;
    private final Properties props = new Properties();
    private final KeyBindings bindings = new KeyBindings();
    private final HotbarLayout hotbar = new HotbarLayout();

    public Settings(Path path) {
        this.path = path;
        if (Files.exists(path)) {
            try (InputStream in = Files.newInputStream(path)) { props.load(in); }
            catch (IOException e) { System.out.println("[settings] lecture impossible: " + e.getMessage()); }
        }
        bindings.readFrom(props);
        hotbar.readFrom(props);
    }

    public KeyBindings bindings() { return bindings; }
    public HotbarLayout hotbar() { return hotbar; }

    public String uiPref(String name, String def) { return props.getProperty("ui." + name, def); }
    public void setUiPref(String name, String value) { props.setProperty("ui." + name, value); }

    public Properties raw() { return props; }

    public void save() {
        bindings.writeTo(props);
        hotbar.writeTo(props);
        try {
            if (path.getParent() != null) Files.createDirectories(path.getParent());
            try (OutputStream out = Files.newOutputStream(path)) {
                props.store(out, "GameOfLife - reglages (bindings, hotbar, UI)");
            }
        } catch (IOException e) { System.out.println("[settings] sauvegarde impossible: " + e.getMessage()); }
    }
}
