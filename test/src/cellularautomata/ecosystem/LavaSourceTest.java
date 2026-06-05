package cellularautomata.ecosystem;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LavaSourceTest {

    @Test
    void forEruptionRandomiseHueShiftDansBornes() {
        for (int i = 0; i < 100; i++) {
            LavaSource s = LavaSource.forEruption(10, 10, 1, 0, 1.0f);
            assertTrue(s.colorHueShift >= -0.05f && s.colorHueShift <= 0.05f,
                    "hueShift hors bornes: " + s.colorHueShift);
            assertTrue(s.colorSatFactor >= 0.9f && s.colorSatFactor <= 1.1f,
                    "satFactor hors bornes: " + s.colorSatFactor);
        }
    }

    @Test
    void blendPondereParEpaisseur() {
        LavaSource a = new LavaSource(0, 0, 1, 0, 1.0f, 0.04f, 1.0f);
        LavaSource b = new LavaSource(0, 0, 2, 0, 2.0f, -0.02f, 1.1f);
        // a poids 3, b poids 1 : résultat ~ 3/4 a + 1/4 b
        LavaSource m = LavaSource.blend(a, b, 3f, 1f, 100);
        assertEquals(-1, m.eruptionId, "Mélange doit avoir eruptionId = -1");
        assertEquals(1.25f, m.pressure, 1e-4f, "pressure = (1.0*3 + 2.0*1) / 4 = 1.25");
        assertEquals(0.025f, m.colorHueShift, 1e-4f, "hue = (0.04*3 + (-0.02)*1) / 4 = 0.025");
        assertEquals(100, m.birthTick);
    }

    @Test
    void blendPoidsZeroFallback50_50() {
        LavaSource a = new LavaSource(0, 0, 1, 0, 1.0f, 0.04f, 1.0f);
        LavaSource b = new LavaSource(0, 0, 2, 0, 3.0f, 0.0f, 1.0f);
        LavaSource m = LavaSource.blend(a, b, 0f, 0f, 0);
        // Total = 0 → fallback à 50/50
        assertEquals(2.0f, m.pressure, 1e-4f, "pressure = (1.0 + 3.0) / 2 = 2.0");
    }
}
