package agents;

import agents.ai.AgentState;
import agents.ai.MemoryKind;
import agents.ai.Percept;
import agents.ai.Perception;
import org.junit.jupiter.api.Test;
import worlds.WorldOfCells;
import static org.junit.jupiter.api.Assertions.*;

class HurlementNourritureTest {
    private static WorldOfCells flat() {
        WorldOfCells w = AgentTestSupport.buildWorld();
        int W = w.getWidth(), H = w.getHeight();
        for (int x = 0; x < W; x++) for (int y = 0; y < H; y++) { w.setCellHeight(x, y, 0.5); w.setForestCAValue(x, y, 0); }
        return w;
    }

    @Test
    void loupRepuVoyantCarcasseFraicheHurle() {
        WorldOfCells w = flat();
        Loup l = new Loup(25, 25, w); l.isFounder = true; w.loups.add(l);
        l.energie = l.energieD;                       // repu (pas affame)
        w.spawnCarcass(28, 25, 70.0, objects.Species.MOUTON);   // fraiche, en vue
        Percept p = Perception.sense(l, w, l.predators(), l.prey());
        assertEquals(AgentState.HOWL, l.decideState(p), "repu + carcasse fraiche => hurle");
    }

    @Test
    void pasDeHurlementSurCarcassePourrie() {
        WorldOfCells w = flat();
        Loup l = new Loup(25, 25, w); l.isFounder = true; w.loups.add(l);
        l.energie = l.energieD;
        w.spawnCarcass(28, 25, 70.0, objects.Species.MOUTON);
        w.carcasses.get(0).ageSeconds = objects.Carcass.FRESH_SEC + 1.0;   // plus fraiche
        Percept p = Perception.sense(l, w, l.predators(), l.prey());
        assertNotEquals(AgentState.HOWL, l.decideState(p), "carcasse pourrie => pas de hurlement nourriture");
    }

    @Test
    void recepteursAffamesMemorisentFOOD() {
        WorldOfCells w = flat();
        Loup crieur = new Loup(25, 25, w); crieur.isFounder = true; crieur.energie = crieur.energieD; w.loups.add(crieur);
        Loup ecouteur = new Loup(30, 25, w); ecouteur.isFounder = true; ecouteur.energie = 50; w.loups.add(ecouteur);  // affame, a portee
        w.spawnCarcass(28, 25, 70.0, objects.Species.MOUTON);
        for (int t = 0; t < 10; t++) { w.setIteration(t); crieur.step(); }
        assertTrue(ecouteur.memory.usageOf(MemoryKind.FOOD, 28, 25) >= 1
                || ecouteur.howlTargetX >= 0,
                "l'ecouteur affame a recu l'info nourriture (memoire FOOD et/ou balise de ralliement)");
    }

    /**
     * Verifie que la balise carcasse du crieur (howlTargetX) reste vivante pendant tout
     * le hurlement FOOD. Sans le correctif de la garde de caducite, decideState efface
     * howlTargetX des le 2e tour de hurlement (s==HOWL !=LOCALISATION && !=SEEK_LAND),
     * rendant broadcastHowl aveugle (mass=0) pour tous les ticks restants.
     *
     * Ce test discrimine directement le bug : il echoue SANS le correctif car apres le
     * 2e tour actif (t=9, vpas=3 -> periode=9), crieur.howlTargetX == -1.
     * Avec le correctif, howlTargetX reste >= 0 jusqu'a la fin du hurlement.
     */
    @Test
    void baliseCarcasseResteVivantePendantToutLeHurlement() {
        WorldOfCells w = flat();
        Loup crieur = new Loup(25, 25, w); crieur.isFounder = true; crieur.energie = crieur.energieD; w.loups.add(crieur);
        w.spawnCarcass(28, 25, 70.0, objects.Species.MOUTON);

        // Tour 0 : le crieur voit la carcasse fraiche (repu + howlReady) -> demarre le hurlement FOOD,
        // pose howlTargetX = 28. isMyTurn() = (0 % 9 == 0) = true.
        w.setIteration(0); crieur.step();
        // Apres le 1er tour, le hurlement est en cours et la balise doit etre posee.
        assertTrue(crieur.howlTargetX >= 0,
                "apres le 1er tour de hurlement FOOD, la balise carcasse est posee (howlTargetX >= 0)");

        // Tour 9 : 2e tour actif (vpas=3 -> periode 9 ticks). Sans correctif : decideState
        // efface howlTargetX ici car s==HOWL n'est pas dans la liste d'exemptions.
        w.setIteration(9); crieur.step();
        assertTrue(crieur.howlTargetX >= 0,
                "au 2e tour actif de hurlement FOOD, la balise carcasse doit rester vivante (guard doit exempter HOWL)");
    }
}
