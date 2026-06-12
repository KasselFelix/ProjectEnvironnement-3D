package agents;

import agents.ai.MemoryKind;
import org.junit.jupiter.api.Test;
import worlds.WorldOfCells;
import static org.junit.jupiter.api.Assertions.*;

class ControlledActionsTest {
    private static WorldOfCells flat() {
        WorldOfCells w = AgentTestSupport.buildWorld();
        int W = w.getWidth(), H = w.getHeight();
        for (int x = 0; x < W; x++) for (int y = 0; y < H; y++) { w.setCellHeight(x, y, 0.5); w.setForestCAValue(x, y, 0); }
        return w;
    }

    @Test
    void loupPiloteMangeLaCarcasseAdjacente() {
        WorldOfCells w = flat();
        Loup l = new Loup(10, 10, w); l.isFounder = true; w.loups.add(l);
        l.playerControlled = true;
        l.energie = 50;
        w.spawnCarcass(11, 10, 70.0, objects.Species.MOUTON);
        objects.Carcass c = w.carcasses.get(0);
        double m0 = c.mass; int e0 = l.energie;
        for (int t = 1; t < 80; t++) {
            l.playerWantsEat = true;
            w.setIteration(t); l.step();
        }
        assertTrue(c.mass < m0, "la carcasse a ete entamee par le loup pilote");
        assertTrue(l.energie > e0, "le loup pilote a gagne de l'energie en mangeant");
    }

    @Test
    void flagMangerSansCarcasseEstSansEffetEtRetombe() {
        WorldOfCells w = flat();
        Loup l = new Loup(10, 10, w); l.isFounder = true; w.loups.add(l);
        l.playerControlled = true; l.energie = 50;
        l.playerWantsEat = true;
        for (int t = 1; t < 30; t++) { w.setIteration(t); l.step(); }
        assertFalse(l.playerWantsEat, "flag consomme meme si rien a manger (pas de latch infini)");
    }

    @Test
    void loupPiloteQuiHurleRallieLaMeute() {
        WorldOfCells w = flat();
        Loup crieur = new Loup(25, 25, w); crieur.isFounder = true; crieur.energie = crieur.energieD; w.loups.add(crieur);
        crieur.playerControlled = true;
        Loup ecouteur = new Loup(30, 25, w); ecouteur.isFounder = true; ecouteur.energie = 50; w.loups.add(ecouteur);
        crieur.playerWantsHowl = true;
        for (int t = 1; t < 120; t++) { w.setIteration(t); crieur.step(); }
        assertTrue(ecouteur.memory.usageOf(MemoryKind.HUNTING, 25, 25) >= 1
                || ecouteur.howlTargetX >= 0,
                "le hurlement pilote a touche l'ecouteur (memoire et/ou balise)");
    }

    @Test
    void alluresDePilotage() {
        WorldOfCells w = flat();
        Loup l = new Loup(10, 10, w); l.isFounder = true; w.loups.add(l);
        l.playerControlled = true; l.energie = 500;

        l.controlGait = Agent.ControlGait.TROT;   l.applyControlSpeed(); double vTrot   = l.vitesse;
        l.controlGait = Agent.ControlGait.SPRINT; l.applyControlSpeed(); double vSprint = l.vitesse;
        l.controlGait = Agent.ControlGait.WALK;   l.applyControlSpeed(); double vWalk   = l.vitesse;

        assertTrue(vSprint > vTrot, "sprint plus rapide que trot");
        assertTrue(vTrot > vWalk,   "trot plus rapide que marche");
    }

    @Test
    void cooldownAccessorsReposEtDispatch() {
        WorldOfCells w = flat();
        Loup l = new Loup(10, 10, w); l.isFounder = true; w.loups.add(l);
        // Au repos : aucun cooldown, aucune action active.
        assertEquals(0.0, input.HotbarAction.HURLER.cooldownFraction(l), 1e-9);
        assertEquals(0.0, input.HotbarAction.MANGER.cooldownFraction(l), 1e-9);
        assertFalse(input.HotbarAction.HURLER.isActive(l));
        // HURLER sur un non-loup : 0 et inactif, sans ClassCastException.
        Mouton m = new Mouton(5, 5, w);
        assertEquals(0.0, input.HotbarAction.HURLER.cooldownFraction(m), 1e-9);
        assertFalse(input.HotbarAction.HURLER.isActive(m));
    }

    @Test
    void hurlementPiloteEngageLeCooldownAffichable() {
        WorldOfCells w = flat();
        Loup l = new Loup(10, 10, w); l.isFounder = true; l.energie = l.energieD; w.loups.add(l);
        l.playerControlled = true;
        l.playerWantsHowl = true;
        // Pendant le hurlement, l'action est "active" a un moment ; apres, le cooldown s'engage
        // puis se resorbe (postTick decremente meme en pilotage).
        boolean vuActif = false, vuCooldown = false;
        for (int t = 1; t < 60; t++) {
            w.setIteration(t); l.step();
            if (input.HotbarAction.HURLER.isActive(l)) vuActif = true;
            if (input.HotbarAction.HURLER.cooldownFraction(l) > 0.0) vuCooldown = true;
        }
        assertTrue(vuActif,    "le slot Hurler a ete vu actif pendant le hurlement");
        assertTrue(vuCooldown, "le cooldown de Hurler est devenu affichable apres le hurlement");
    }
}
