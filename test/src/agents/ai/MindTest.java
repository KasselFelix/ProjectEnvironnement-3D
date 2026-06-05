package agents.ai;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase D du système d'évolution (cf. docs/evolution.txt § 6) : l'intelligence
 * est un SCORE (0..1) — poids sur la capacité d'apprendre — qui démarre selon
 * l'axe Intelligence, dégénère avec l'âge et est entretenu par l'activité.
 */
class MindTest {

    /** Score de départ (§ 6.1) : INTELLIGENT > NEUTRE > STUPIDE. */
    @Test
    void scoreDeDepartSelonLAxeIntelligence() {
        Genome neutre = new Genome();
        Genome intelligent = new Genome();
        intelligent.set(Axis.INTELLIGENCE, Pole.POSITIVE);
        Genome stupide = new Genome();
        stupide.set(Axis.INTELLIGENCE, Pole.NEGATIVE);

        double sNeutre = Mind.fromGenome(neutre).score();
        double sIntelligent = Mind.fromGenome(intelligent).score();
        double sStupide = Mind.fromGenome(stupide).score();

        assertTrue(sIntelligent > sNeutre, "l'intelligent démarre plus haut");
        assertTrue(sNeutre > sStupide, "le stupide démarre plus bas");
        assertEquals(0.5, sNeutre, 1e-9, "le neutre démarre à 0.5");
    }

    /** Entraînement vs dégénérescence (§ 6.2) : à âge égal, un esprit ACTIF
     *  garde un meilleur score qu'un esprit INACTIF (l'activité ralentit la
     *  dégénérescence liée à l'âge). */
    @Test
    void espritActifDegenereeMoinsQueInactif() {
        Mind actif = new Mind(0.5);
        Mind inactif = new Mind(0.5);
        double ageFraction = 0.9;            // vieux : forte dégénérescence
        for (int i = 0; i < 20; i++) {
            actif.train(1.0, ageFraction, 1.0);   // activité maximale
            inactif.train(0.0, ageFraction, 1.0); // aucune activité
        }
        assertTrue(actif.score() > inactif.score(),
                "l'esprit actif dégénère moins vite que l'inactif");
    }

    /** La longévité atténue la dégénérescence (§ 6.2) : à âge et activité égaux,
     *  une forte longévité conserve un meilleur score. */
    @Test
    void forteLongeviteRalentitLaDegenerescence() {
        Mind longeve = new Mind(0.8);
        Mind degenere = new Mind(0.8);
        for (int i = 0; i < 20; i++) {
            longeve.train(0.0, 0.9, 1.4);    // longevityFactor élevé
            degenere.train(0.0, 0.9, 0.6);   // longevityFactor faible
        }
        assertTrue(longeve.score() > degenere.score(),
                "une forte longévité ralentit la dégénérescence cognitive");
    }

    /** learningRate (§ 6.1) : croît avec le score ; ≈ 1.0 pour un esprit neutre. */
    @Test
    void tauxDApprentissageCroitAvecLeScore() {
        assertTrue(new Mind(0.8).learningRate() > new Mind(0.2).learningRate());
        assertEquals(1.0, new Mind(0.5).learningRate(), 1e-9, "neutre → poids 1.0");
    }

    /** Le score reste borné dans [0, 1] quelles que soient les sollicitations. */
    @Test
    void scoreBorneEntreZeroEtUn() {
        Mind m = new Mind(0.99);
        for (int i = 0; i < 100; i++) m.train(1.0, 0.0, 1.4);   // que de l'entraînement
        assertTrue(m.score() <= 1.0 + 1e-9 && m.score() >= 0.0);

        Mind d = new Mind(0.01);
        for (int i = 0; i < 100; i++) d.train(0.0, 1.0, 0.6);   // que de la dégénérescence
        assertTrue(d.score() >= 0.0 && d.score() <= 1.0);
    }
}
