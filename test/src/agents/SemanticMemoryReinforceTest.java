package agents;

import agents.ai.MemoryKind;
import agents.ai.SemanticMemory;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SemanticMemoryReinforceTest {
    // distance euclidienne simple (suffisant hors tore pour les tests)
    private static final SemanticMemory.Distance EUCLID =
        (x1, y1, x2, y2) -> Math.hypot(x1 - x2, y1 - y2);

    @Test
    void reinforceFusionneDansLeRayon() {
        SemanticMemory m = new SemanticMemory();
        m.reinforce(MemoryKind.FOOD, 10, 10, 70.0, 100, 3, EUCLID);
        m.reinforce(MemoryKind.FOOD, 12, 10, 50.0, 120, 3, EUCLID);   // dist 2 <= 3 => fusion
        assertEquals(1, m.size(), "un seul souvenir apres fusion");
        assertEquals(2, m.usageOf(MemoryKind.FOOD, 12, 10), "usage incremente");
        assertTrue(m.contains(MemoryKind.FOOD, 12, 10), "position mise a jour sur la derniere obs");
        assertFalse(m.contains(MemoryKind.FOOD, 10, 10), "ancienne position remplacee");
    }

    @Test
    void reinforceHorsRayonCreeDeuxEntrees() {
        SemanticMemory m = new SemanticMemory();
        m.reinforce(MemoryKind.FOOD, 10, 10, 70.0, 100, 3, EUCLID);
        m.reinforce(MemoryKind.FOOD, 20, 10, 70.0, 120, 3, EUCLID);   // dist 10 > 3
        assertEquals(2, m.size());
    }

    @Test
    void forgetRetireLeLieuExact() {
        SemanticMemory m = new SemanticMemory();
        m.reinforce(MemoryKind.FOOD, 10, 10, 70.0, 100, 3, EUCLID);
        m.forget(MemoryKind.FOOD, 10, 10);
        assertEquals(0, m.size());
    }

    @Test
    void foodConsolideAUsage2_dangerAUsage1() {
        SemanticMemory m = new SemanticMemory();
        m.reinforce(MemoryKind.FOOD, 10, 10, 70.0, 100, 3, EUCLID);    // usage 1
        assertFalse(m.isConsolidated(MemoryKind.FOOD, 10, 10), "FOOD seuil 2 : usage 1 = provisoire");
        m.reinforce(MemoryKind.FOOD, 10, 10, 70.0, 110, 3, EUCLID);    // usage 2
        assertTrue(m.isConsolidated(MemoryKind.FOOD, 10, 10), "FOOD usage 2 = consolide");

        m.reinforce(MemoryKind.DANGER, 5, 5, 0.0, 100, 3, EUCLID);     // usage 1
        assertTrue(m.isConsolidated(MemoryKind.DANGER, 5, 5), "DANGER consolide des usage 1");
    }

    @Test
    void teachPreserveValueEtLastSeen() {
        // Professeur qui connait une carcasse de masse 55.0 vue a l'iteration 200
        SemanticMemory teacher = new SemanticMemory();
        teacher.reinforce(MemoryKind.FOOD, 5, 7, 55.0, 200, 1, EUCLID);

        // Eleve vierge
        SemanticMemory student = new SemanticMemory();
        teacher.teach(student);

        assertTrue(student.contains(MemoryKind.FOOD, 5, 7),
                "l'eleve doit connaitre la position enseignee");
        assertEquals(55.0, student.valueOf(MemoryKind.FOOD, 5, 7), 1e-9,
                "teach doit preserver la valeur (masse carcasse)");
    }
}
