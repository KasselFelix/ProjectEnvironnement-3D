package agents;

import agents.ai.Axis;
import agents.ai.Pole;
import org.junit.jupiter.api.Test;
import worlds.WorldOfCells;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase A — câblage du Genome dans le Mouton (cf. docs/evolution.txt § 4) :
 * un mouton porte un génome, l'agneau l'hérite des deux parents, et un parent
 * INFERTILE impose un malus de longévité à sa descendance (§ 4.3).
 */
class MoutonGenomeTest {

    /** Un mouton fondateur (spawné, pas né) a un génome NEUTRE sur tous les axes
     *  (profil par défaut § 4.5). */
    @Test
    void moutonFondateurAUnGenomeNeutre() {
        WorldOfCells world = AgentTestSupport.buildWorld();
        Mouton m = new Mouton(20, 20, world);
        assertNotNull(m.genome);
        for (Axis axis : Axis.values()) {
            assertEquals(Pole.NEUTRAL, m.genome.get(axis));
        }
    }

    /** Un agneau dont un parent est INFERTILE naît avec une longévité RÉDUITE
     *  (§ 4.3, cul-de-sac mou). Montage : les deux parents sont LONGÉVITÉ POSITIF
     *  (facteur ×1.4, qui POUSSE la longévité au-dessus de celle du parent), donc
     *  si l'agneau finit SOUS la longévité parentale, c'est forcément le malus
     *  infertile (×0.5) qui l'a fait basculer. */
    @Test
    void agneauDeParentInfertileADuMalusLongevite() {
        WorldOfCells world = AgentTestSupport.buildWorld();
        world.setDureeJour(1); world.setSeasonLengthDays(1); world.setIteration(28); // AUTUMN (28%14==0)

        Mouton m = new Mouton(20, 20, world);
        m.genome.set(Axis.FERTILITY, Pole.NEGATIVE);   // parent infertile
        m.genome.set(Axis.LONGEVITY, Pole.POSITIVE);
        m.energie = m.energieMAX;
        m.maxAgeDays = 100.0;
        m.Prepro = 5.0;                                 // 5.0 × 0.2 (infertile) = 1.0 → naissance certaine
        world.moutons.add(m); world.agents.add(m);
        world.uniqueDynamicObjects.add(m);

        Mouton partner = new Mouton(21, 20, world);
        partner.genome.set(Axis.LONGEVITY, Pole.POSITIVE);
        partner.energie = partner.energieMAX;
        partner.maxAgeDays = 100.0;
        partner.Prepro = 0;
        world.moutons.add(partner); world.agents.add(partner);
        world.uniqueDynamicObjects.add(partner);
        world.setNbmoutons(2);

        m.step();

        assertEquals(3, world.moutons.size(), "un agneau doit naître (Prepro×facteur = 1.0)");
        Mouton lamb = world.moutons.get(2);
        assertNotNull(lamb.genome, "l'agneau doit porter un génome");
        assertTrue(lamb.maxAgeDays < 100.0,
                "malus infertile : l'agneau (longévité POSITIVE ×1.4) finit SOUS 100 → ×0.5 appliqué. Obtenu="
                        + lamb.maxAgeDays);
    }
}
