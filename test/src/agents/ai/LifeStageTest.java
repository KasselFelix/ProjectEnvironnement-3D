package agents.ai;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase B du système d'évolution (cf. docs/evolution.txt § 10.1) : le stade de
 * vie se déduit de l'âge rapporté à la durée de vie (fractions calées sur la
 * biologie du mouton : sevrage ~3 %, maturité ~6 %, sénescence ~80 %).
 */
class LifeStageTest {

    /** Un nouveau-né (âge 0) est un BÉBÉ. */
    @Test
    void nouveauNeEstBebe() {
        assertEquals(LifeStage.BABY, LifeStage.of(0.0, 100.0));
    }

    /** Les quatre stades en fonction de la fraction d'âge (vie de 100 jours :
     *  sevrage à 3, maturité à 6, sénescence à 80). */
    @Test
    void lesQuatreStadesSelonLAge() {
        assertEquals(LifeStage.BABY,     LifeStage.of(2.0,  100.0)); // < 3 % : sevrage
        assertEquals(LifeStage.JUVENILE, LifeStage.of(4.0,  100.0)); // 3–6 % : immature
        assertEquals(LifeStage.ADULT,    LifeStage.of(50.0, 100.0)); // 6–80 % : adulte
        assertEquals(LifeStage.OLD,      LifeStage.of(90.0, 100.0)); // > 80 % : sénescence
    }

    /** Âge négatif / nul aux bornes : 0 → BÉBÉ ; pile à la durée de vie → VIEUX. */
    @Test
    void bornesDAge() {
        assertEquals(LifeStage.BABY, LifeStage.of(0.0, 100.0));
        assertEquals(LifeStage.OLD,  LifeStage.of(100.0, 100.0));
    }

    /** Durée de vie ≤ 0 (immortel) : l'âge est rapporté à une durée de référence,
     *  donc les stades progressent quand même avec l'âge. */
    @Test
    void immortelUtiliseUneDureeDeReference() {
        // À âge 0, immortel ou non, c'est un bébé.
        assertEquals(LifeStage.BABY, LifeStage.of(0.0, -1.0));
        // À un âge avancé, un immortel doit avoir dépassé le stade bébé.
        assertNotEquals(LifeStage.BABY, LifeStage.of(1000.0, -1.0));
    }

    /** Courbe de croissance Gompertz (§ 10.2) : à la naissance la taille vaut la
     *  fraction de naissance (~0.40), elle atteint ~1.0 à la maturité, et elle
     *  est strictement croissante entre les deux. */
    @Test
    void courbeDeCroissanceGompertz() {
        double birth = LifeStage.gompertzGrowth(0.0, 100.0);
        assertEquals(0.40, birth, 0.02, "taille de naissance ≈ 40 % de l'adulte");

        double maturityAgeDays = LifeStage.MATURITY_FRAC * 100.0; // = 6 jours
        double atMaturity = LifeStage.gompertzGrowth(maturityAgeDays, 100.0);
        assertTrue(atMaturity > 0.95, "≈ pleine taille à la maturité, obtenu=" + atMaturity);

        assertTrue(LifeStage.gompertzGrowth(2.0, 100.0) < LifeStage.gompertzGrowth(4.0, 100.0),
                "croissance strictement croissante");
        assertTrue(LifeStage.gompertzGrowth(4.0, 100.0) < atMaturity);

        // Bornée : jamais sous la fraction de naissance, jamais au-dessus de 1.
        assertTrue(LifeStage.gompertzGrowth(0.0, 100.0) >= 0.40 - 1e-9);
        assertTrue(LifeStage.gompertzGrowth(1000.0, 100.0) <= 1.0 + 1e-9);
    }

    /** Facteur de fertilité par stade (§ 10.1) : l'ADULTE est pleinement fertile
     *  (1.0), le VIEUX l'est à taux réduit (0.5), bébé/jeune nuls (gate dur). */
    @Test
    void fertiliteReduiteChezLeVieux() {
        assertEquals(1.0, LifeStage.ADULT.fertilityFactor(), 1e-9);
        assertEquals(0.5, LifeStage.OLD.fertilityFactor(), 1e-9);
        assertEquals(0.0, LifeStage.BABY.fertilityFactor(), 1e-9);
        assertEquals(0.0, LifeStage.JUVENILE.fertilityFactor(), 1e-9);
    }
}
