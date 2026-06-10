package agents;

import agents.ai.Axis;
import agents.ai.Genome;
import agents.ai.Pole;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RecallFoodTest {
    @Test
    void audaceFourragementSuitEndurance() {
        Genome neutre = new Genome();
        assertEquals(1.0, neutre.foragingBoldnessFactor(), 1e-9);
        Genome endurant = new Genome();
        endurant.set(Axis.ENDURANCE, Pole.POSITIVE);
        assertTrue(endurant.foragingBoldnessFactor() > 1.0, "endurant => va plus loin");
        Genome fragile = new Genome();
        fragile.set(Axis.ENDURANCE, Pole.NEGATIVE);
        assertTrue(fragile.foragingBoldnessFactor() < 1.0);
    }
}
