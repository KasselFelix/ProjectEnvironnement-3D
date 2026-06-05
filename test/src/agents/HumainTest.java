package agents;

import org.junit.jupiter.api.Test;
import worlds.WorldOfCells;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests du socle cognitif généralisé à l'Humain (L1). Le berger partage le
 * Genome / Mind / SemanticMemory / Character d'Agent ; sa vie sociale est
 * tournée vers son troupeau (isIsolated = pas de mouton à portée).
 */
class HumainTest {

    @Test
    void bergerIsoleSansTroupeau() {
        WorldOfCells world = AgentTestSupport.buildWorld();
        Humain berger = new Humain(20, 20, world);
        world.humains.add(berger);
        world.agents.add(berger);
        world.uniqueDynamicObjects.add(berger);

        assertTrue(berger.isIsolated(), "sans mouton à portée, le berger est isolé");
        assertEquals(0.5, berger.satisfaction(), 1e-9,
                "isolé mais pas en feu → satisfaction = (1 sécurité + 0 devoir)/2");
    }

    @Test
    void bergerNonIsoleAvecTroupeau() {
        WorldOfCells world = AgentTestSupport.buildWorld();
        Humain berger = new Humain(20, 20, world);
        world.humains.add(berger);
        world.agents.add(berger);
        world.uniqueDynamicObjects.add(berger);

        Mouton mt = new Mouton(22, 21, world);   // dans FLOCK_GUARD_RADIUS
        world.moutons.add(mt);
        world.agents.add(mt);
        world.uniqueDynamicObjects.add(mt);

        assertFalse(berger.isIsolated(), "un mouton proche → le berger garde son troupeau");
        assertEquals(1.0, berger.satisfaction(), 1e-9,
                "troupeau gardé, pas en feu → satisfaction pleine");
    }

    @Test
    void bergerExposeUnResumeEvolutif() {
        WorldOfCells world = AgentTestSupport.buildWorld();
        Humain berger = new Humain(20, 20, world);
        berger.initMind();
        // Le socle hissé dans Agent (L1) doit fournir une fiche évolutive non vide,
        // exploitable par AgentInfoPanel pour toutes les espèces.
        assertFalse(berger.evolutionSummary().isEmpty(),
                "le berger expose le même résumé évolutif que les autres espèces");
    }
}
