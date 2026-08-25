package agents.ai;

import java.util.Random;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase A du système d'évolution (cf. docs/evolution.txt § 4) : le Genome
 * porte un état {NÉGATIF, NEUTRE, POSITIF} par axe, héritable et mutable.
 */
class GenomeTest {

    /** Profil par défaut (§ 4.5) : un genome neuf est NEUTRE sur TOUS les axes. */
    @Test
    void genomeParDefautToutNeutre() {
        Genome g = new Genome();
        for (Axis axis : Axis.values()) {
            assertEquals(Pole.NEUTRAL, g.get(axis),
                    "axe " + axis + " devrait être NEUTRE par défaut");
        }
    }

    /** Héritage (§ 4.4) : quand les deux parents ont le MÊME pôle sur un axe,
     *  l'enfant l'hérite à coup sûr (mutation et grand-parent désactivés). */
    @Test
    void heritageDeuxParentsMemePoleDonneCePole() {
        Genome p1 = new Genome();
        Genome p2 = new Genome();
        p1.set(Axis.STRENGTH, Pole.POSITIVE);
        p2.set(Axis.STRENGTH, Pole.POSITIVE);

        Genome child = Genome.inherit(p1, p2, new Random(1), 0.0, 0.0);

        assertEquals(Pole.POSITIVE, child.get(Axis.STRENGTH));
    }

    /** Conflit d'axe (§ 4.4) : un parent POSITIF, l'autre NÉGATIF → le plus
     *  souvent NEUTRE (les pôles opposés s'annulent), mais chaque pôle reste
     *  possible. Jamais le cas « POSITIF ET NÉGATIF » (exclusion mutuelle). */
    @Test
    void heritageConflitDonneNeutreLePlusSouvent() {
        Genome p1 = new Genome();
        Genome p2 = new Genome();
        p1.set(Axis.STRENGTH, Pole.POSITIVE);
        p2.set(Axis.STRENGTH, Pole.NEGATIVE);

        int neutral = 0, positive = 0, negative = 0;
        Random rng = new Random(42);
        int n = 2000;
        for (int i = 0; i < n; i++) {
            Pole pole = Genome.inherit(p1, p2, rng, 0.0, 0.0).get(Axis.STRENGTH);
            switch (pole) {
                case NEUTRAL:  neutral++;  break;
                case POSITIVE: positive++; break;
                case NEGATIVE: negative++; break;
            }
        }
        assertTrue(neutral > positive, "NEUTRE devrait dominer POSITIF");
        assertTrue(neutral > negative, "NEUTRE devrait dominer NÉGATIF");
        assertTrue(positive > 0 && negative > 0, "les deux pôles restent possibles");
        assertEquals(n, neutral + positive + negative);
    }

    /** Mutation de type (§ 4.2) : avec un taux forcé à 1.0, un axe NEUTRE hérité
     *  bascule forcément vers un pôle (POSITIF ou NÉGATIF). */
    @Test
    void mutationTypeBasculeUnAxeNeutre() {
        Genome p1 = new Genome();   // tout NEUTRE
        Genome p2 = new Genome();   // tout NEUTRE

        Genome child = Genome.inherit(p1, p2, new Random(7), 1.0, 0.0);

        for (Axis axis : Axis.values()) {
            assertNotEquals(Pole.NEUTRAL, child.get(axis),
                    "axe " + axis + " devrait avoir muté hors de NEUTRE");
        }
    }

    /** Saut de génération (§ 4.4) : les deux parents sont NEUTRES sur un axe mais
     *  un GRAND-PARENT y était POSITIF. Sans saut (prob 0) l'enfant reste NEUTRE ;
     *  avec saut (prob 1) le trait du grand-parent peut resurgir. */
    @Test
    void sautDeGenerationFaitResurgirUnTraitDeGrandParent() {
        Genome gp1 = new Genome(); gp1.set(Axis.WISDOM, Pole.POSITIVE);
        Genome gp2 = new Genome(); gp2.set(Axis.WISDOM, Pole.POSITIVE);
        Genome p1 = new Genome();          // WISDOM NEUTRE...
        p1.rememberParents(gp1, gp2);      // ...mais ses parents étaient POSITIFS
        Genome p2 = new Genome();          // fondateur, sans grands-parents

        // Contrôle : sans saut de génération, l'enfant est toujours NEUTRE.
        Random rng = new Random(3);
        for (int i = 0; i < 500; i++) {
            assertEquals(Pole.NEUTRAL,
                    Genome.inherit(p1, p2, rng, 0.0, 0.0).get(Axis.WISDOM));
        }

        // Avec saut de génération forcé, le trait POSITIF du grand-parent resurgit.
        int positive = 0;
        for (int i = 0; i < 2000; i++) {
            if (Genome.inherit(p1, p2, rng, 0.0, 1.0).get(Axis.WISDOM) == Pole.POSITIVE)
                positive++;
        }
        assertTrue(positive > 0, "le trait du grand-parent devrait resurgir");
    }

    /** Modificateur de fertilité (§ 4.1, § 4.3) : FERTILE ×1.5, NEUTRE ×1.0,
     *  INFERTILE ×0.2 (cul-de-sac mou : rare mais non nul). */
    @Test
    void modificateurDeFertilite() {
        Genome g = new Genome();
        assertEquals(1.0, g.reproProbaFactor(), 1e-9);
        g.set(Axis.FERTILITY, Pole.POSITIVE);
        assertEquals(1.5, g.reproProbaFactor(), 1e-9);
        g.set(Axis.FERTILITY, Pole.NEGATIVE);
        assertEquals(0.2, g.reproProbaFactor(), 1e-9);
    }

    /** Modificateur de longévité (§ 4.1) : LONGÉVITÉ ×1.4, NEUTRE ×1.0,
     *  DÉGÉNÉRESCENCE ×0.6. */
    @Test
    void modificateurDeLongevite() {
        Genome g = new Genome();
        assertEquals(1.0, g.longevityFactor(), 1e-9);
        g.set(Axis.LONGEVITY, Pole.POSITIVE);
        assertEquals(1.4, g.longevityFactor(), 1e-9);
        g.set(Axis.LONGEVITY, Pole.NEGATIVE);
        assertEquals(0.6, g.longevityFactor(), 1e-9);
    }

    /** Modificateur de taille lié à la Force (§ 4.1, § 10.2) : FORT ×1.1,
     *  NEUTRE ×1.0, FAIBLE ×0.9. Effet « léger » sur la taille de rendu. */
    @Test
    void modificateurDeTailleSelonLaForce() {
        Genome g = new Genome();
        assertEquals(1.0, g.strengthSizeFactor(), 1e-9);
        g.set(Axis.STRENGTH, Pole.POSITIVE);
        assertEquals(1.1, g.strengthSizeFactor(), 1e-9);
        g.set(Axis.STRENGTH, Pole.NEGATIVE);
        assertEquals(0.9, g.strengthSizeFactor(), 1e-9);
    }

    /** Probabilité d'erreur de cap en navigation longue distance (§ 4.1, § 9) :
     *  BON_SENS 0.0 (cap exact), NEUTRE faible, DÉSORIENTÉ élevée. */
    @Test
    void probabiliteDErreurDOrientation() {
        Genome g = new Genome();
        double neutre = g.orientationErrorProb();
        g.set(Axis.ORIENTATION, Pole.POSITIVE);
        double bonSens = g.orientationErrorProb();
        g.set(Axis.ORIENTATION, Pole.NEGATIVE);
        double desoriente = g.orientationErrorProb();

        assertEquals(0.0, bonSens, 1e-9, "BON_SENS → cap exact, aucune erreur");
        assertTrue(neutre > bonSens, "le neutre dévie parfois");
        assertTrue(desoriente > neutre, "le désorienté dévie souvent");
    }

    /** Libellés ASCII des axes non-neutres pour la fiche UI (§ 10/§ 11) :
     *  les axes NEUTRES ne sont pas listés ; vide → "-". */
    @Test
    void libellesAsciiDesTraits() {
        Genome g = new Genome();
        assertEquals("-", g.asciiTraits(), "génome neutre → aucun trait affiché");

        g.set(Axis.INTELLIGENCE, Pole.POSITIVE);
        g.set(Axis.STRENGTH, Pole.NEGATIVE);
        String s = g.asciiTraits();
        assertTrue(s.contains("INTELLIGENT"), "axe Intelligence POSITIF → INTELLIGENT");
        assertTrue(s.contains("FAIBLE"), "axe Force NEGATIF → FAIBLE");
        // ASCII only : pas de caractère accentué / Unicode.
        assertTrue(s.chars().allMatch(c -> c < 128), "libellés en ASCII pur");
    }

    /** Diversité initiale des fondateurs (§ 4.5) : avec proba 0 le génome reste
     *  NEUTRE ; avec proba 1 chaque axe bascule sur un pôle. */
    @Test
    void seedDiversiteDesFondateurs() {
        Genome neutre = new Genome();
        neutre.seedDiversity(new Random(1), 0.0);
        for (Axis a : Axis.values())
            assertEquals(Pole.NEUTRAL, neutre.get(a), "proba 0 → reste neutre");

        Genome divers = new Genome();
        divers.seedDiversity(new Random(2), 1.0);
        for (Axis a : Axis.values())
            assertNotEquals(Pole.NEUTRAL, divers.get(a), "proba 1 → chaque axe non-neutre");
    }
}
