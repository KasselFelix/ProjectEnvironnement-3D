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
}
