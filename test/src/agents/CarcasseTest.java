package agents;

import objects.Carcass;
import objects.Species;
import org.junit.jupiter.api.Test;
import worlds.WorldOfCells;
import static org.junit.jupiter.api.Assertions.*;

// Task 10 imports
// Note: Humain IS a wolf predator — Loup.predators() returns world.humains + world.ours.
// A Humain at dist 8 (25,25 -> 33,25) is within full wolf vision (10) but outside
// reduced vision (10 * 0.5 = 5). At dist 1 (26,25) it is seen even at reduced vision.

class CarcasseTest {
    private static WorldOfCells flat() {
        WorldOfCells w = AgentTestSupport.buildWorld();
        int W = w.getWidth(), H = w.getHeight();
        for (int x = 0; x < W; x++) for (int y = 0; y < H; y++) { w.setCellHeight(x, y, 0.5); w.setForestCAValue(x, y, 0); }
        return w;
    }

    @Test
    void spawnCreeUneCarcasse() {
        WorldOfCells w = flat();
        w.spawnCarcass(10, 10, 70.0, Species.MOUTON);
        assertEquals(1, w.carcasses.size());
        Carcass c = w.carcasses.get(0);
        assertEquals(70.0, c.mass, 1e-9);
        assertEquals(70.0, c.initialMass, 1e-9);
        assertEquals(Species.MOUTON, c.source);
    }

    @Test
    void manger_reduit_la_masse_et_borne_au_restant() {
        WorldOfCells w = flat();
        w.spawnCarcass(10, 10, 5.0, Species.MOUTON);
        Carcass c = w.carcasses.get(0);
        assertEquals(3.0, c.eat(3.0), 1e-9);   // retire 3 kg
        assertEquals(2.0, c.mass, 1e-9);
        assertEquals(2.0, c.eat(10.0), 1e-9);  // borne au restant
        assertTrue(c.isGone());
    }

    @Test
    void carcasseIgnoreePourritEtDisparait() {
        WorldOfCells w = flat();
        w.spawnCarcass(10, 10, 70.0, Species.MOUTON);
        // rotCarcasses(dt) appelé manuellement : dt = LIFETIME complet => masse à 0.
        w.rotCarcasses(Carcass.LIFETIME_SEC);
        assertTrue(w.carcasses.isEmpty(), "une carcasse non mangee pourrit et est retiree");
    }

    @Test
    void loupAffameMangeLaCarcasseEtSArreteRassasie() {
        WorldOfCells w = flat();
        Loup l = new Loup(25, 25, w); l.isFounder = true; w.loups.add(l);
        l.energie = 50;                                   // affamé
        w.spawnCarcass(25, 25, 200.0, objects.Species.MOUTON);   // sur la case du loup
        for (int t = 0; t < 2000; t++) {
            w.setIteration(t); l.step();
            if (l.energie >= (int) (l.energieD * Loup.HUNGER_RATIO)) break;
        }
        assertTrue(l.energie >= (int) (l.energieD * Loup.HUNGER_RATIO), "le loup a mange jusqu'a ne plus etre affame");
        assertTrue(w.carcasses.get(0).mass < 200.0, "la carcasse a diminue");
    }

    @Test
    void carcasseDisparaitQuandVidee() {
        WorldOfCells w = flat();
        Loup l = new Loup(25, 25, w); l.isFounder = true; w.loups.add(l);
        l.energie = 50;
        w.spawnCarcass(25, 25, 6.0, objects.Species.MOUTON);   // petite carcasse
        boolean aGagne = false;
        for (int t = 0; t < 3000; t++) {
            l.energie = 50;                                    // reste affamé => mange tout
            w.setIteration(t); l.step();
            if (l.energie > 50) aGagne = true;                // énergie montée => bouchée prise
            if (w.carcasses.isEmpty()) break;
        }
        assertTrue(w.carcasses.isEmpty(), "carcasse videe par les bouchees => disparue");
        assertTrue(aGagne, "le loup a bien gagne de l'energie en mangeant");
    }

    @Test
    void loupAffameRejointUneCarcasseEnVuePuisLaMange() {
        WorldOfCells w = flat();
        Loup l = new Loup(10, 25, w); l.isFounder = true; w.loups.add(l);
        l.energie = 50;
        w.spawnCarcass(20, 25, 300.0, objects.Species.MOUTON);   // à l'EST, dist 10 (= vision)
        double d0 = w.distance(l.x, l.y, 20, 25);
        boolean aMange = false;
        for (int t = 0; t < 2000; t++) {
            l.energie = Math.min(l.energie, 200);   // garde-le affamé tant qu'il n'a pas atteint
            w.setIteration(t); l.step();
            if (w.carcasses.get(0).mass < 300.0) { aMange = true; break; }
        }
        assertTrue(aMange, "le loup a rejoint la carcasse et commence a manger");
        assertTrue(w.distance(l.x, l.y, 20, 25) < d0, "il s'est rapproche");
    }

    @Test
    void loupAffameMemoriseLaCarcasseCommeFOOD() {
        WorldOfCells w = flat();
        Loup l = new Loup(10, 25, w); l.isFounder = true; w.loups.add(l);
        l.energie = 50;                                     // affamé
        w.spawnCarcass(15, 25, 70.0, objects.Species.MOUTON);   // en vue (dist 5 <= vision 10)
        for (int t = 0; t < 3; t++) { w.setIteration(t); l.step(); }
        assertTrue(l.memory.contains(agents.ai.MemoryKind.FOOD, 15, 25),
                "un loup affame voyant une carcasse memorise sa position comme FOOD");
    }

    @Test
    void miseAMortLaisseUneCarcasseSansGainInstantane() {
        WorldOfCells w = flat();
        Loup l = new Loup(25, 25, w); l.isFounder = true; w.loups.add(l);
        l.energie = 50;                          // affamé (<350) et > 2 (canMove)
        Mouton proie = new Mouton(25, 25, w); proie.isFounder = true; w.moutons.add(proie);  // même case
        int avant = l.energie;
        for (int t = 0; t < 5; t++) { w.setIteration(t); l.step(); if (!proie._alive) break; }
        assertFalse(proie._alive, "le mouton est tue");
        assertEquals(1, w.carcasses.size(), "une carcasse est creee a la cellule du kill");
        assertEquals(objects.Species.MOUTON, w.carcasses.get(0).source);
        // pas de bond instantane de +energieD/2 (le gain passe desormais par les bouchees, Task 5)
        assertTrue(l.energie <= avant + 5, "pas de gain instantane massif au kill");
    }

    @Test
    void deuxLoupsPartagentUneCarcasse() {
        WorldOfCells w = flat();
        Loup a = new Loup(24, 25, w); a.isFounder = true; w.loups.add(a);   // ouest de la carcasse
        Loup b = new Loup(26, 25, w); b.isFounder = true; w.loups.add(b);   // est de la carcasse
        w.spawnCarcass(25, 25, 400.0, objects.Species.MOUTON);
        objects.Carcass c = w.carcasses.get(0);
        double m0 = c.mass;
        boolean aMange = false, bMange = false;
        for (int t = 0; t < 2000; t++) {
            a.energie = 50; b.energie = 50;   // restent affamés => mangent en continu
            w.setIteration(t); a.step(); b.step();
            if (a.energie > 50) aMange = true;   // énergie montée => bouchée prise par a
            if (b.energie > 50) bMange = true;   // énergie montée => bouchée prise par b
            if (c.isGone()) break;               // carcasse vidée => fini
        }
        assertTrue(aMange, "le loup a (ouest) a mange de la carcasse");
        assertTrue(bMange, "le loup b (est) a mange de la carcasse");
        assertTrue(c.mass < m0 - 100.0 || w.carcasses.isEmpty(),
                "les deux loups ensemble vident la carcasse plus vite (festin partage)");
    }

    @Test
    void moutonMemoriseUneCarcasseDeMoutonCommeDanger() {
        WorldOfCells w = flat();
        Mouton m = new Mouton(25, 25, w); w.moutons.add(m);
        w.spawnCarcass(27, 25, 70.0, objects.Species.MOUTON);   // carcasse de mouton en vue
        for (int t = 0; t < 3; t++) { w.setIteration(t); m.step(); }
        assertTrue(m.memory.contains(agents.ai.MemoryKind.DANGER, 27, 25),
                "un mouton voyant une carcasse de mouton la memorise comme DANGER");

        // une carcasse de LOUP ne declenche pas la peur du mouton
        WorldOfCells w2 = flat();
        Mouton m2 = new Mouton(25, 25, w2); w2.moutons.add(m2);
        w2.spawnCarcass(27, 25, 45.0, objects.Species.LOUP);
        for (int t = 0; t < 3; t++) { w2.setIteration(t); m2.step(); }
        assertFalse(m2.memory.contains(agents.ai.MemoryKind.DANGER, 27, 25),
                "une carcasse de loup n'effraie pas le mouton");
    }

    @Test
    void oursPrendUnePlusGrosseBoucheeQuLeLoup() {
        WorldOfCells w = flat();
        Loup l  = new Loup(10, 10, w);  l.isFounder = true;  l.energie = 50;  w.loups.add(l);
        Ours o  = new Ours(30, 30, w);  o.isFounder = true;  o.energie = 50;  w.ours.add(o);
        w.spawnCarcass(10, 10, 500.0, objects.Species.MOUTON);   // sous le loup
        w.spawnCarcass(30, 30, 500.0, objects.Species.LOUP);     // sous l'ours
        objects.Carcass cl = w.carcassAt(10, 10);
        objects.Carcass co = w.carcassAt(30, 30);
        double ml0 = cl.mass, mo0 = co.mass;
        double loupMange = 0, oursMange = 0;
        for (int t = 0; t < 200; t++) {
            l.energie = 50; o.energie = 50; w.setIteration(t); l.step(); o.step();
            loupMange = ml0 - cl.mass; oursMange = mo0 - co.mass;
            if (loupMange > 0 && oursMange > 0) break;
        }
        assertTrue(oursMange > loupMange, "l'ours retire plus de masse par bouchee que le loup");
    }

    // ===== Task 10 : vision réduite en mangeant =====

    @Test
    void visionReduiteEnMangeant() {
        WorldOfCells w = flat();
        Loup l = new Loup(25, 25, w); l.isFounder = true; w.loups.add(l);
        l.energie = 50;
        w.spawnCarcass(25, 25, 200.0, objects.Species.MOUTON);   // il va manger ici
        // amène-le en état EAT
        for (int t = 0; t < 50; t++) { w.setIteration(t); l.step(); if (l.isFeeding()) break; }
        assertTrue(l.isFeeding(), "le loup mange");
        // Un Humain (chasseur=true) à dist 8 : dans world.humains => dans l.predators().
        // Vision pleine = 10 : dist 8 <= 10 => vu. Vision réduite = 5 : dist 8 > 5 => non vu.
        Humain h = new Humain(33, 25, w); h.chasseur = true; w.humains.add(h);   // dist 8 < vision 10
        agents.ai.Percept p = agents.ai.Perception.sense(l, w, l.predators(), l.prey());
        assertFalse(p.predatorVisible(), "tete baissee : menace a 8 cases non percue (vision reduite)");
    }

    @Test
    void dangerPreempteLeRepasUneFoisPercu() {
        WorldOfCells w = flat();
        Loup l = new Loup(25, 25, w); l.isFounder = true; w.loups.add(l);
        l.energie = 50;
        w.spawnCarcass(25, 25, 200.0, objects.Species.MOUTON);
        for (int t = 0; t < 50; t++) { w.setIteration(t); l.step(); if (l.isFeeding()) break; }
        // prédateur COLLÉ (dist 1) : perçu même en vision réduite => fuite préempte le repas.
        Humain h = new Humain(26, 25, w); h.chasseur = true; w.humains.add(h);
        agents.ai.Percept p = agents.ai.Perception.sense(l, w, l.predators(), l.prey());
        assertTrue(p.predatorVisible(), "menace collee : percue malgre la vision reduite");
        assertEquals(agents.ai.AgentState.FLEE_PREDATOR, l.decideState(p), "le danger preempte le repas");
    }
}
