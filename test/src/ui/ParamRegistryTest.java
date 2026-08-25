package ui;

import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ParamRegistryTest {
    @Test
    void registreCouvreLesDeuxMenusEtClampe() {
        SimulationConfig config = new SimulationConfig();
        List<ParamRegistry.ParamDef> all = ParamRegistry.build(config);

        assertTrue(all.stream().anyMatch(d -> d.label.equals("Loups")
                && d.visibility == ParamRegistry.Visibility.LAUNCH_ONLY), "populations = launch only");
        // "HUD degats" n'existe que dans InGameMenu (PARAMS) — voir note dans le rapport :
        // "Force vent" de l'enonce apparait en realite dans LES DEUX menus, donc BOTH.
        assertTrue(all.stream().anyMatch(d -> d.label.equals("HUD degats")
                && d.visibility == ParamRegistry.Visibility.INGAME_ONLY), "HUD degats = ingame only");
        assertTrue(all.stream().anyMatch(d -> d.label.equals("Simulation Hz")
                && d.visibility == ParamRegistry.Visibility.BOTH), "hz = les deux");

        ParamRegistry.ParamDef hz = all.stream()
                .filter(d -> d.label.equals("Simulation Hz")).findFirst().get();
        config.simulationHz = 60;
        hz.inc.run();
        assertEquals(60, config.simulationHz, "inc au max = clamp");
        config.simulationHz = 10;
        hz.dec.run();
        assertEquals(10, config.simulationHz, "dec au min = clamp");
    }
}
