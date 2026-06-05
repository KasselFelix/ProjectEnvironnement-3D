package agents;

import agents.ai.Axis;
import agents.ai.LifeStage;
import agents.ai.Pole;
import org.junit.jupiter.api.Test;
import worlds.WorldOfCells;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase I — généralisation du système d'évolution au Loup (cf.
 * docs/evolution.txt § 3, Phase I) : génome héritable, cycle de vie (louveteau →
 * adulte → vieux), taille variable. Miroir de ce qui a été validé sur le Mouton.
 */
class LoupEvolutionTest {

    /** Un loup fondateur a un génome NEUTRE et est un ADULTE d'emblée. */
    @Test
    void loupFondateurGenomeNeutreEtAdulte() {
        WorldOfCells world = AgentTestSupport.buildWorld();
        Loup l = new Loup(20, 20, world);
        l.maxAgeDays = 100.0;
        for (Axis a : Axis.values()) assertEquals(Pole.NEUTRAL, l.genome.get(a));
        assertEquals(LifeStage.ADULT, l.currentStage());
    }

    /** Un louveteau (né) démarre BÉBÉ et ne peut pas se reproduire. */
    @Test
    void louveteauBebeNeSeReproduitPas() {
        WorldOfCells world = AgentTestSupport.buildWorld();
        Loup pup = new Loup(20, 20, world);
        pup.isFounder = false;                  // né → bébé
        pup.maxAgeDays = 100.0;
        pup.energie = pup.energieD;
        pup.Prepro = 1.0;
        world.loups.add(pup); world.agents.add(pup); world.uniqueDynamicObjects.add(pup);
        Loup partner = new Loup(21, 20, world);
        partner.Prepro = 0;
        world.loups.add(partner); world.agents.add(partner); world.uniqueDynamicObjects.add(partner);
        world.setNbloups(2);

        assertEquals(LifeStage.BABY, pup.currentStage());
        pup.step();
        assertEquals(2, world.loups.size(), "un louveteau bébé ne se reproduit pas");
    }

    /** Un louveteau d'un parent INFERTILE naît avec une longévité réduite. */
    @Test
    void louveteauDeParentInfertileMalusLongevite() {
        WorldOfCells world = AgentTestSupport.buildWorld();
        Loup l = new Loup(20, 20, world);
        l.genome.set(Axis.FERTILITY, Pole.NEGATIVE);
        l.genome.set(Axis.LONGEVITY, Pole.POSITIVE);
        l.energie = l.energieD;
        l.maxAgeDays = 100.0;
        l.Prepro = 5.0;                          // 5 × 0.2 (infertile) = 1.0 → naissance certaine
        world.loups.add(l); world.agents.add(l); world.uniqueDynamicObjects.add(l);
        Loup partner = new Loup(21, 20, world);
        partner.genome.set(Axis.LONGEVITY, Pole.POSITIVE);
        partner.energie = partner.energieD;
        partner.maxAgeDays = 100.0;
        partner.Prepro = 0;
        world.loups.add(partner); world.agents.add(partner); world.uniqueDynamicObjects.add(partner);
        world.setNbloups(2);

        l.step();
        assertEquals(3, world.loups.size(), "un petit doit naître");
        Loup pup = world.loups.get(2);
        assertTrue(pup.maxAgeDays < 100.0, "malus infertile sur la longévité du petit");
    }

    /** À sizeFactor et stade égaux, un loup FORT est plus grand qu'un neutre. */
    @Test
    void loupFortEstPlusGrand() {
        WorldOfCells world = AgentTestSupport.buildWorld();
        Loup neutre = new Loup(20, 20, world);
        Loup fort = new Loup(20, 20, world);
        fort.genome.set(Axis.STRENGTH, Pole.POSITIVE);
        assertTrue(fort.displaySize() > neutre.displaySize());
    }
}
