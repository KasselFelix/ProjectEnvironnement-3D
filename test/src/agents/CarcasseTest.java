package agents;

import objects.Carcass;
import objects.Species;
import org.junit.jupiter.api.Test;
import worlds.WorldOfCells;
import static org.junit.jupiter.api.Assertions.*;

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
}
