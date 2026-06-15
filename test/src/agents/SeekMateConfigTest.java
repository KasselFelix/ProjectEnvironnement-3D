package agents;

import agents.ai.AgentState;
import org.junit.jupiter.api.Test;
import ui.ParamRegistry;
import ui.SimulationConfig;
import static org.junit.jupiter.api.Assertions.*;

/** Câblage E3 : état SEEK_MATE, réglage scentMateThreshold, exposition menu. */
class SeekMateConfigTest {

    @Test
    void etatSeekMateExiste() {
        assertNotNull(AgentState.valueOf("SEEK_MATE"));
    }

    @Test
    void reglageSeuilPartenaireExposeAuMenu() {
        boolean found = ParamRegistry.build(new SimulationConfig()).stream()
                .anyMatch(d -> d.label.contains("partenaire"));
        assertTrue(found, "le seuil de detection du partenaire doit apparaitre au menu ODEUR");
    }
}
