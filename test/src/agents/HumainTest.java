package agents;

import agents.ai.AgentState;
import agents.ai.Percept;
import agents.ai.Perception;
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

    /**
     * L3 — Variante CHASSEUR : un Humain en mode chasseur qui voit un loup le
     * pourchasse (HUNT) et s'oriente VERS lui (cap = direction du loup), au lieu
     * de garder le troupeau. Un berger classique, lui, ne chasse pas.
     */
    @Test
    void chasseurPourchasseLeLoup() {
        WorldOfCells world = AgentTestSupport.buildWorld();
        int cx = 25, cy = 25;
        AgentTestSupport.flattenLandArea(world, cx, cy, 12);
        world.setJour(1);   // jour : pas de retour au foyer

        Humain berger = new Humain(cx, cy, world);
        world.humains.add(berger);
        world.agents.add(berger);
        world.uniqueDynamicObjects.add(berger);

        // Loup plein EST, dans la vision.
        Loup loup = new Loup((cx + 5) % world.getWidth(), cy, world);
        world.loups.add(loup);
        world.agents.add(loup);
        world.uniqueDynamicObjects.add(loup);

        // Berger classique : ignore le loup (pas de HUNT).
        Percept p1 = Perception.sense(berger, world, world.loups, world.moutons);
        assertNotEquals(AgentState.HUNT, berger.decideState(p1),
                "un berger (chasseur=false) ne pourchasse pas le loup");

        // Mode chasseur : pourchasse le loup à l'EST (cap 1).
        berger.chasseur = true;
        Percept p2 = Perception.sense(berger, world, world.loups, world.moutons);
        assertEquals(AgentState.HUNT, berger.decideState(p2),
                "un chasseur qui voit un loup → HUNT");
        berger.applyState(AgentState.HUNT, p2);
        assertEquals(1, berger._orient,
                "le chasseur fonce VERS le loup à l'EST (cap 1), observé " + berger._orient);
    }

    /**
     * L3 — Le chasseur abat le loup qu'il rattrape (même cellule).
     */
    @Test
    void chasseurAbatLeLoupAuContact() {
        WorldOfCells world = AgentTestSupport.buildWorld();
        int cx = 25, cy = 25;
        AgentTestSupport.flattenLandArea(world, cx, cy, 4);

        Humain chasseur = new Humain(cx, cy, world);
        chasseur.chasseur = true;
        world.humains.add(chasseur);
        world.agents.add(chasseur);
        world.uniqueDynamicObjects.add(chasseur);

        Loup loup = new Loup(cx, cy, world);   // même cellule
        world.loups.add(loup);
        world.agents.add(loup);
        world.uniqueDynamicObjects.add(loup);

        Percept p = Perception.sense(chasseur, world, world.loups, world.moutons);
        chasseur.postMove(p);
        assertFalse(loup._alive, "le chasseur abat le loup présent sur sa cellule");
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
