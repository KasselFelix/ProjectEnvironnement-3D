package agents.ai;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Émergence de l'axe prudence/témérité (sous-projet D). Logique pure, sans monde. */
class CharacterBoldnessTest {

    /** n ticks : chacun une opportunité (took = takesRisk), satisfaction sat. */
    private void session(Character c, int n, boolean takesRisk, double sat) {
        for (int i = 0; i < n; i++) {
            c.observe(false, sat);              // axe social neutre
            c.observeRisk(true, takesRisk);     // une opportunité de risque
        }
        c.endSession();
    }

    @Test
    void defautNone() {
        assertEquals(BoldnessTrait.NONE, new Character().boldness());
    }

    @Test
    void risquesPrisEtSatisfait_devientTemeraire() {
        Character c = new Character();
        session(c, 12, true, 0.8);              // ≥ MIN_OPPORTUNITIES, frac=1, sat haute
        assertEquals(BoldnessTrait.BOLD, c.boldness());
    }

    @Test
    void risquesEsquivesEtSatisfait_devientPrudent() {
        Character c = new Character();
        session(c, 12, false, 0.8);             // opportunités mais jamais prises
        assertEquals(BoldnessTrait.CAUTIOUS, c.boldness());
    }

    @Test
    void tropPeuDOpportunites_resteNoneEtReporte() {
        Character c = new Character();
        session(c, 4, true, 0.8);               // < MIN_OPPORTUNITIES → pas de décision
        assertEquals(BoldnessTrait.NONE, c.boldness());
        session(c, 4, true, 0.8);               // 4+4 = 8 cumulés (report) → décide
        assertEquals(BoldnessTrait.BOLD, c.boldness());
    }

    @Test
    void satisfactionEffondree_perdLeTrait() {
        Character c = new Character();
        session(c, 12, true, 0.8);
        assertEquals(BoldnessTrait.BOLD, c.boldness());
        session(c, 12, true, 0.2);              // risques pris mais sat ≤ S_LOSE
        assertEquals(BoldnessTrait.NONE, c.boldness());
    }

    /** Hystérésis CÔTÉ PRUDENT (asymétrique du côté BOLD) : un prudent qui se met
     *  à prendre les risques (boldFrac > 1 − PROFILE_KEEP_FLOOR = 0.7) reperd le trait. */
    @Test
    void prudentPerdLeTrait_silSeMetAPrendreLesRisques() {
        Character c = new Character();
        session(c, 12, false, 0.8);             // jamais de risque → devient PRUDENT
        assertEquals(BoldnessTrait.CAUTIOUS, c.boldness());
        session(c, 12, true, 0.8);              // boldFrac=1.0 > 0.7 → reperd CAUTIOUS
        assertEquals(BoldnessTrait.NONE, c.boldness());
    }
}
