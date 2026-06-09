package agents;

import org.junit.jupiter.api.Test;
import worlds.WorldOfCells;
import static org.junit.jupiter.api.Assertions.*;

class BodyMassTest {
    private static WorldOfCells flat() {
        WorldOfCells w = AgentTestSupport.buildWorld();
        int W = w.getWidth(), H = w.getHeight();
        for (int x = 0; x < W; x++) for (int y = 0; y < H; y++) { w.setCellHeight(x, y, 0.5); w.setForestCAValue(x, y, 0); }
        return w;
    }

    @Test
    void bodyMassReflechitAgeTailleSante() {
        WorldOfCells w = flat();
        Loup adulte = new Loup(25, 25, w); adulte.isFounder = true; adulte.energie = adulte.energieD;
        Loup bebe   = new Loup(26, 25, w); bebe.isFounder = false; /* non-fondateur, age 0 => BABY */ bebe.energie = bebe.energieD;
        assertTrue(bebe.bodyMassKg() < adulte.bodyMassKg(), "un bebe pese moins qu'un adulte");

        // adulte sain de taille 1.0 => masse ≈ baseMassKg
        assertEquals(adulte.baseMassKg, adulte.bodyMassKg(), adulte.baseMassKg * 0.15,
                "adulte sain taille 1.0 ≈ baseMassKg");

        // affamé < sain (même agent)
        double sain = adulte.bodyMassKg();
        adulte.energie = 1;
        assertTrue(adulte.bodyMassKg() < sain, "un adulte affame pese moins qu'un sain");
    }

    @Test
    void resistanceVentInchangeePourAdulteSain() {
        WorldOfCells w = flat();
        Loup l = new Loup(25, 25, w); l.isFounder = true; l.energie = l.energieD;
        // baseMassKg/frontalAreaM2 d'un loup adulte sain => même résistance qu'avant (masse fixe).
        double r = l.windResistance();
        assertTrue(r > 0, "resistance vent definie");
        // la masse dynamique d'un adulte sain == baseMassKg (à la tolérance displaySize près)
        assertEquals(l.baseMassKg, l.bodyMassKg(), l.baseMassKg * 0.15);
    }
}
