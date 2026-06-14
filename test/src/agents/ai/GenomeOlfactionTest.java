package agents.ai;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GenomeOlfactionTest {

    @Test
    void olfactionFactorParPole() {
        Genome g = new Genome();
        assertEquals(1.0, g.olfactionFactor(), 1e-9, "NEUTRE = x1.0");
        g.set(Axis.OLFACTION, Pole.POSITIVE);
        assertEquals(1.4, g.olfactionFactor(), 1e-9, "NEZ FIN = x1.4");
        g.set(Axis.OLFACTION, Pole.NEGATIVE);
        assertEquals(0.6, g.olfactionFactor(), 1e-9, "ANOSMIE = x0.6");
    }

    @Test
    void libelleAsciiNezFin() {
        Genome g = new Genome();
        g.set(Axis.OLFACTION, Pole.POSITIVE);
        assertTrue(g.asciiTraits().contains("NEZ FIN"), "le trait doit s'afficher");
        g.set(Axis.OLFACTION, Pole.NEGATIVE);
        assertTrue(g.asciiTraits().contains("ANOSMIE"), "l'anosmie doit s'afficher");
    }
}
