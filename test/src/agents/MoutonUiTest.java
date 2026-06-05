package agents;

import agents.ai.Axis;
import agents.ai.Pole;
import java.util.List;
import org.junit.jupiter.api.Test;
import worlds.WorldOfCells;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase H — données de la fiche d'agent (cf. docs/evolution.txt § 11) : la
 * logique « traits → texte » est testable (le rendu GL, lui, est validé par
 * capture d'écran).
 */
class MoutonUiTest {

    /** evolutionSummary() liste stade, traits, caractère, intelligence, mémoire,
     *  en ASCII pur (contrainte GLUT bitmap). */
    @Test
    void ficheResumeLesTraitsEvolutifs() {
        WorldOfCells world = AgentTestSupport.buildWorld();
        Mouton m = new Mouton(20, 20, world);
        m.genome.set(Axis.INTELLIGENCE, Pole.POSITIVE);

        List<String> lines = m.evolutionSummary();
        assertFalse(lines.isEmpty());
        String joined = String.join("\n", lines);

        assertTrue(joined.contains("ADULTE"), "un fondateur est adulte");
        assertTrue(joined.contains("INTELLIGENT"), "trait génétique listé");
        assertTrue(joined.contains("Caractere"), "ligne caractère présente");
        assertTrue(joined.contains("Intel."), "ligne intelligence présente");
        assertTrue(joined.contains("Memoire"), "ligne mémoire présente");
        assertTrue(joined.chars().allMatch(c -> c < 128), "ASCII pur (pas d'accents/Unicode)");
    }
}
