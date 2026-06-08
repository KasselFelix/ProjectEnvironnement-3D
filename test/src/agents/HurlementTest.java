package agents;

import agents.ai.AgentState;
import agents.ai.Axis;
import agents.ai.Genome;
import agents.ai.MemoryKind;
import agents.ai.Percept;
import agents.ai.Perception;
import agents.ai.Pole;
import org.junit.jupiter.api.Test;
import worlds.WorldOfCells;

import static org.junit.jupiter.api.Assertions.*;

class HurlementTest {

    /** Monde plat 50×50 sans obstacle (déterminisme : la position des proies/loups
     *  ne dépend pas du terrain Perlin aléatoire). */
    private static WorldOfCells flatWorld() {
        WorldOfCells w = AgentTestSupport.buildWorld();
        int W = w.getWidth(), H = w.getHeight();
        for (int x = 0; x < W; x++)
            for (int y = 0; y < H; y++) { w.setCellHeight(x, y, 0.5); w.setForestCAValue(x, y, 0); }
        return w;
    }

    @Test
    void noisyLocationBonSensExactDesorienteFlou() {
        WorldOfCells w = flatWorld();
        Loup l = new Loup(25, 25, w);
        java.util.Random rng = new java.util.Random(123);

        // errProb = 0.0 (BON SENS) → position EXACTE, indépendamment du rng.
        int[] exact = l.noisyLocation(20, 20, 0.0, 16.0, rng);
        assertArrayEquals(new int[]{20, 20}, exact, "bon sens => position exacte");

        // errProb = 0.5 (DÉSORIENTÉ) → la position est décalée (offset > 0) et bornée.
        boolean someOffset = false;
        for (int i = 0; i < 50; i++) {
            int[] n = l.noisyLocation(20, 20, 0.5, 16.0, rng);
            double d = w.distance(20, 20, n[0], n[1]);
            assertTrue(d <= 0.5 * 16.0 + 1.5, "offset borné (" + d + ")");   // +1.5 = arrondi
            if (d > 0) someOffset = true;
        }
        assertTrue(someOffset, "desoriente => au moins un tirage decale la position");
    }

    /** Place une proie en vue et renvoie le percept du loup. */
    private static Percept sensePrey(WorldOfCells w, Loup l) {
        return Perception.sense(l, w, l.predators(), l.prey());
    }

    @Test
    void loupRepuHurleEtAffameChasse() {
        WorldOfCells w = flatWorld();
        Loup l = new Loup(25, 25, w); w.loups.add(l);
        Mouton prey = new Mouton(27, 25, w); w.moutons.add(prey);   // dist 2 <= vision 10
        l.attaqueNuit = 0;
        Percept p = sensePrey(w, l);
        assertTrue(p.preyVisible(), "la proie doit etre visible");

        l.energie = l.energieD;            // repu (>= HUNGER_RATIO*energieD), cooldown 0
        l.howlCooldown = 0;
        assertEquals(AgentState.HOWL, l.decideState(p), "repu + proie + pret => hurle");

        l.energie = 1;                     // affamé
        assertEquals(AgentState.HUNT, l.decideState(p), "affame + proie => chasse solo");
    }

    @Test
    void loupRepuEnCooldownNeHurlePas() {
        WorldOfCells w = flatWorld();
        Loup l = new Loup(25, 25, w); w.loups.add(l);
        Mouton prey = new Mouton(27, 25, w); w.moutons.add(prey);
        l.attaqueNuit = 0;
        l.energie = l.energieD;
        l.howlCooldown = 5;                // en cooldown
        AgentState s = l.decideState(sensePrey(w, l));
        assertNotEquals(AgentState.HOWL, s, "en cooldown : pas de hurlement");
    }

    @Test
    void loupAffameAvecBaliseVaEnLocalisation() {
        WorldOfCells w = flatWorld();
        Loup l = new Loup(25, 25, w); w.loups.add(l);
        l.energie = 1;                     // affamé
        l.howlTargetX = 40; l.howlTargetY = 25;
        Percept p = Perception.sense(l, w, l.predators(), l.prey());   // aucune proie en vue
        assertFalse(p.preyVisible());
        assertEquals(AgentState.LOCALISATION, l.decideState(p), "affame + balise => localisation");
    }

    @Test
    void hurlementMemoriseChezLesLoupsAPorteeSeulement() {
        WorldOfCells w = flatWorld();
        int saved = Loup.HOWL_RADIUS;
        try {
            Loup.HOWL_RADIUS = 8;                          // petit rayon pour distinguer dedans/dehors
            Loup howler = new Loup(25, 25, w); w.loups.add(howler);
            Loup near   = new Loup(30, 25, w); w.loups.add(near);  near.energie = 1;   // dist 5 <= 8, affamé
            Loup far    = new Loup(25, 40, w); w.loups.add(far);   far.energie = 1;    // dist 15 > 8
            near.genome.set(Axis.ORIENTATION, Pole.POSITIVE);            // BON SENS → exact

            howler.broadcastHowl();

            assertTrue(near.memory.contains(MemoryKind.HUNTING, 25, 25),
                    "loup a portee + bon sens => memorise la position EXACTE du hurleur");
            assertTrue(near.howlTargetX == 25 && near.howlTargetY == 25,
                    "loup affame a portee => adopte la balise");
            assertEquals(0, far.memory.size(), "loup hors rayon => rien");
            assertFalse(far.howlTargetX >= 0, "loup hors rayon => pas de balise");
        } finally {
            Loup.HOWL_RADIUS = saved;
        }
    }

    @Test
    void loupRepuMemoriseMaisNeRalliePas() {
        WorldOfCells w = flatWorld();
        int saved = Loup.HOWL_RADIUS;
        try {
            Loup.HOWL_RADIUS = 8;
            Loup howler = new Loup(25, 25, w); w.loups.add(howler);
            Loup repu   = new Loup(30, 25, w); w.loups.add(repu);
            repu.energie = repu.energieD;                  // repu → ne se déplace pas
            repu.genome.set(Axis.ORIENTATION, Pole.POSITIVE);
            howler.broadcastHowl();
            assertTrue(repu.memory.contains(MemoryKind.HUNTING, 25, 25), "repu memorise quand meme");
            assertFalse(repu.howlTargetX >= 0, "repu ne prend pas de balise (ne court pas)");
        } finally {
            Loup.HOWL_RADIUS = saved;
        }
    }

    @Test
    void baliseRebasculeVersLeHurleurLePlusProche() {
        WorldOfCells w = flatWorld();
        int savedR = Loup.HOWL_RADIUS, savedN = Loup.HOWL_NOISE_MAX;
        try {
            Loup.HOWL_RADIUS = 30; Loup.HOWL_NOISE_MAX = 0;   // pas de bruit → positions exactes
            Loup seeker = new Loup(25, 25, w); w.loups.add(seeker); seeker.energie = 1;  // affamé
            seeker.howlTargetX = 25; seeker.howlTargetY = 45;  // balise actuelle LOINTAINE (dist 20)
            Loup closeHowler = new Loup(25, 30, w); w.loups.add(closeHowler);  // dist 5 du seeker
            closeHowler.broadcastHowl();
            assertEquals(25, seeker.howlTargetX);
            assertEquals(30, seeker.howlTargetY, "rebascule vers le hurleur le plus proche (5 < 20)");
        } finally {
            Loup.HOWL_RADIUS = savedR; Loup.HOWL_NOISE_MAX = savedN;
        }
    }

    @Test
    void leHurleurResteSurPlaceDiffuseEtPasseEnCooldown() {
        WorldOfCells w = flatWorld();
        int savedR = Loup.HOWL_RADIUS, savedD = Loup.HOWL_DURATION;
        try {
            Loup.HOWL_RADIUS = 8; Loup.HOWL_DURATION = 3;
            Loup howler = new Loup(25, 25, w); w.loups.add(howler);
            Mouton prey = new Mouton(27, 25, w); w.moutons.add(prey);   // proie en vue → déclenche le hurlement
            Loup near = new Loup(30, 25, w); w.loups.add(near); near.energie = 1;  // affamé à portée

            int sx = howler.x, sy = howler.y;
            boolean howledThenStopped = false;
            for (int t = 0; t < 300; t++) {
                howler.energie = howler.energieD;   // repu et PLEIN : après le hurlement il REST (stationnaire), ne WANDER pas
                near.energie = 1;                   // reste affamé pour recevoir/garder la balise
                w.setIteration(t);
                howler.step();
                // le hurleur ne bouge JAMAIS (balise fixe pendant le hurlement, REST ensuite)
                assertEquals(sx, howler.x, "le hurleur reste sur place (x)");
                assertEquals(sy, howler.y, "le hurleur reste sur place (y)");
                // une fois le hurlement terminé, il n'est plus en HOWL (cooldown actif)
                if (near.howlTargetX >= 0 && howler.decideState(
                        Perception.sense(howler, w, howler.predators(), howler.prey())) != AgentState.HOWL) {
                    howledThenStopped = true;
                    break;
                }
            }
            assertTrue(near.howlTargetX >= 0, "un loup affame a portee a recu la balise");
            assertTrue(howledThenStopped, "apres HOWL_DURATION tours : plus en HOWL (cooldown), et toujours immobile");
        } finally {
            Loup.HOWL_RADIUS = savedR; Loup.HOWL_DURATION = savedD;
        }
    }

    @Test
    void loupAffameRallieLaBaliseAuCourse() {
        WorldOfCells w = flatWorld();
        Loup l = new Loup(10, 25, w); w.loups.add(l);
        l.energie = 5;                       // affamé (reste affamé : refill ; > 2 pour canMove)
        l.howlTargetX = 40; l.howlTargetY = 25;   // balise à l'EST
        double d0 = w.distance(l.x, l.y, 40, 25);
        for (int t = 0; t < 600; t++) {
            l.energie = 5;                   // isole la navigation (affamé + peut bouger)
            w.setIteration(t); l.step();
            // applyState(LOCALISATION) efface la balise quand la distance est <= 1.0 AU DEBUT
            // d'un tick actif (isMyTurn). La balise est relâchée exactement au tick où
            // l'agent se trouve à portée ET c'est son tour d'agir.
            if (l.howlTargetX < 0 || w.distance(l.x, l.y, 40, 25) <= 0.1) break;
        }
        assertTrue(w.distance(l.x, l.y, 40, 25) < d0, "le loup s'est rapproche de la balise");
        if (w.distance(l.x, l.y, 40, 25) <= 1.0)
            assertTrue(l.howlTargetX < 0, "balise relachee a l'arrivee");
    }

    @Test
    void localisationVersHuntRelacheLaBaliseEtResetLaPoursuite() {
        WorldOfCells w = flatWorld();
        Loup l = new Loup(25, 25, w); w.loups.add(l);
        l.energie = 5;                       // affamé (5 << 350) et > 2 (canMove)
        l.howlTargetX = 45; l.howlTargetY = 25;   // une balise (lointaine)
        l.huntStuck = 50;                    // simule un blocage accumulé en ralliant une balise murée
        Mouton prey = new Mouton(27, 25, w); w.moutons.add(prey);   // proie en vue (dist 2)
        Percept p = Perception.sense(l, w, l.predators(), l.prey());
        assertTrue(p.preyVisible());

        AgentState s = l.decideState(p);
        assertEquals(AgentState.HUNT, s, "proie en vue => HUNT");
        assertTrue(l.howlTargetX < 0, "spec §4 : la balise est relachee en entrant en HUNT");
        assertEquals(0, l.huntStuck, "la poursuite repart propre (huntStuck reset) au lieu d'heriter du blocage de la balise");
    }

    @Test
    void localisationAbandonneSiBaliseMuree() {
        WorldOfCells w = flatWorld();
        int saved = Loup.LOCALISATION_GIVEUP;
        try {
            Loup.LOCALISATION_GIVEUP = 20;   // abandon rapide pour le test
            int cx = 25, cy = 25;
            // mur d'arbres fermé (anneau) autour de la balise → inatteignable
            for (int x = cx - 1; x <= cx + 1; x++) for (int y = cy - 1; y <= cy + 1; y++) w.setForestCAValue(x, y, 1);
            w.setForestCAValue(cx, cy, 0);   // la balise est au centre, mais cernée d'arbres
            Loup l = new Loup(cx - 6, cy, w); w.loups.add(l);
            l.energie = 5; l.howlTargetX = cx; l.howlTargetY = cy;
            boolean abandoned = false;
            for (int t = 0; t < 600; t++) {
                l.energie = 5; w.setIteration(t); l.step();
                if (l.howlTargetX < 0) { abandoned = true; break; }
            }
            assertTrue(abandoned, "balise muree => le loup abandonne (clear) et repart en recherche");
        } finally {
            Loup.LOCALISATION_GIVEUP = saved;
        }
    }

    @Test
    void localisationBasculeEnHuntSiProieEnVue() {
        WorldOfCells w = flatWorld();
        Loup l = new Loup(25, 25, w); w.loups.add(l);
        l.energie = 1;                       // affamé
        l.howlTargetX = 45; l.howlTargetY = 25;   // a une balise
        Mouton prey = new Mouton(27, 25, w); w.moutons.add(prey);   // proie en vue (dist 2)
        Percept p = Perception.sense(l, w, l.predators(), l.prey());
        assertTrue(p.preyVisible());
        // proie en vue prime sur le ralliement : HUNT, pas LOCALISATION.
        assertEquals(AgentState.HUNT, l.decideState(p), "proie en vue => HUNT (prime sur localisation)");
    }

    @Test
    void boucleHurlementRalliement() {
        WorldOfCells w = flatWorld();
        Loup howler = new Loup(25, 25, w); w.loups.add(howler);
        Mouton prey = new Mouton(26, 25, w); w.moutons.add(prey);   // proie en vue du hurleur
        howler.energie = howler.energieD;     // repu → va hurler
        Loup seeker = new Loup(25, 5, w); w.loups.add(seeker);      // affamé, à portée (dist 20 <= 30)
        seeker.energie = 5;

        double d0 = w.distance(seeker.x, seeker.y, howler.x, howler.y);
        for (int t = 0; t < 300; t++) {
            howler.energie = howler.energieD;   // reste repu (continue de pouvoir hurler)
            seeker.energie = 5;                 // reste affamé (5 << 350) et > 2 (canMove)
            w.setIteration(t);
            howler.step();
            seeker.step();
            if (seeker.howlTargetX >= 0 && w.distance(seeker.x, seeker.y, howler.x, howler.y) < d0 - 3) break;
        }
        assertTrue(seeker.howlTargetX >= 0 || w.distance(seeker.x, seeker.y, howler.x, howler.y) < d0,
                "le chercheur a entendu le hurlement et s'est rapproche");
    }
}
