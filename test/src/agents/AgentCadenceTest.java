package agents;

import org.junit.jupiter.api.Test;
import ui.SimulationConfig;
import worlds.WorldOfCells;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Cadence des agents (correctifs d'audit 2026-06-07) :
 *  - isMyTurn() ne doit jamais diviser par zero, meme si la vitesse depasse 28
 *    (la vitesse evolue sans borne haute — c'est voulu — donc le diviseur
 *    (int)((1/vitesse)*28) peut tomber a 0) ;
 *  - les drains « horaires » (feu) suivent simulationHz via ticksPerGameSecond()
 *    au lieu d'un % 20 code en dur.
 */
class AgentCadenceTest {

    /** RÉGRESSION : une vitesse > 28 ne provoque plus d'ArithmeticException (/ 0)
     *  dans isMyTurn() — pour les quatre especes. */
    @Test
    void vitesseElevoeeNeCrashePasIsMyTurn() {
        WorldOfCells w = AgentTestSupport.buildWorld();

        Loup l = new Loup(10, 10, w);   l.vitesse   = 30;   // > 28
        Mouton m = new Mouton(11, 11, w); m.vitesse = 40;
        Ours o = new Ours(12, 12, w);   o.vitesse   = 99;
        Humain h = new Humain(13, 13, w); h.vitesse = 28.5;

        // Avant le correctif : (int)((1/vitesse)*28) == 0 → world.getIteration() % 0
        // → ArithmeticException. Apres : diviseur borne a 1 → agit chaque tick.
        assertDoesNotThrow(l::isMyTurn, "Loup rapide ne crashe pas");
        assertDoesNotThrow(m::isMyTurn, "Mouton rapide ne crashe pas");
        assertDoesNotThrow(o::isMyTurn, "Ours rapide ne crashe pas");
        assertDoesNotThrow(h::isMyTurn, "Humain rapide ne crashe pas");

        // A l'iteration 0 et avec un diviseur de 1, l'agent agit (0 % 1 == 0).
        assertTrue(l.isMyTurn() && m.isMyTurn() && o.isMyTurn() && h.isMyTurn(),
                "au-dela de 28 l'agent agit a chaque tick");
    }

    /** Formule de cadence d'avant turnPeriodTicks() (copie figée, référence des tests). */
    private static int anciennePeriode(double vitesse) {
        return Math.max(1, (int) ((1.0 / vitesse) * 28));
    }

    /** Vitesses de test : valeurs usuelles, bornes de troncature de 28/v, au-delà de 28. */
    private static final double[] VITESSES = {
        0.3, 0.5, 1.0, 2.0, 2.8, 3.0, 3.1, 3.5, 4.0, 7.0, 9.33, 14.0, 27.9, 28.0,
        28.0001, 30.0, 99.0, 1000.0
    };

    /** {@code vitesse} est déclaré dans chaque espèce, pas sur Agent. */
    private static void setVitesse(Agent a, double v) {
        if (a instanceof Loup) ((Loup) a).vitesse = v;
        else if (a instanceof Mouton) ((Mouton) a).vitesse = v;
        else if (a instanceof Ours) ((Ours) a).vitesse = v;
        else if (a instanceof Humain) ((Humain) a).vitesse = v;
        else fail("espèce inconnue : " + a.getClass());
    }

    private static Agent[] quatreEspeces(WorldOfCells w) {
        return new Agent[] { new Loup(10, 10, w), new Mouton(11, 11, w),
                             new Ours(12, 12, w), new Humain(13, 13, w) };
    }

    /** turnPeriodTicks() reprend exactement l'ancienne formule des 4 espèces,
     *  y compris aux bornes de troncature et sur des vitesses tirées au hasard. */
    @Test
    void periodeEgaleAncienneFormule() {
        WorldOfCells w = AgentTestSupport.buildWorld();
        java.util.Random rnd = new java.util.Random(42);
        for (Agent a : quatreEspeces(w)) {
            for (double v : VITESSES) {
                setVitesse(a, v);
                assertEquals(anciennePeriode(v), a.turnPeriodTicks(),
                        a.getClass().getSimpleName() + " vitesse=" + v);
            }
            for (int k = 0; k < 500; k++) {
                double v = 0.1 + rnd.nextDouble() * 40;
                setVitesse(a, v);
                assertEquals(anciennePeriode(v), a.turnPeriodTicks(),
                        a.getClass().getSimpleName() + " vitesse=" + v);
            }
        }
    }

    /** Garde au-delà de 28 : la période ne descend jamais sous 1 (pas de % 0),
     *  et vaut 1 dès que 28/vitesse tronqué tombe à 0. */
    @Test
    void periodeGardeeAuDelaDe28() {
        WorldOfCells w = AgentTestSupport.buildWorld();
        for (Agent a : quatreEspeces(w)) {
            for (double v : new double[] { 28.0001, 28.5, 30, 56, 99, 1e6 }) {
                setVitesse(a, v);
                assertEquals(1, a.turnPeriodTicks(), a.getClass().getSimpleName() + " vitesse=" + v);
            }
            setVitesse(a, 28.0);
            assertEquals(1, a.turnPeriodTicks(), "28/28 = 1");
            setVitesse(a, 14.0);
            assertEquals(2, a.turnPeriodTicks(), "28/14 = 2");
        }
    }

    /** isMyTurn() donne les mêmes tours qu'avant : itération multiple de l'ancienne période. */
    @Test
    void isMyTurnMemesToursQuAvant() {
        WorldOfCells w = AgentTestSupport.buildWorld();
        for (Agent a : quatreEspeces(w)) {
            for (double v : VITESSES) {
                setVitesse(a, v);
                int p = anciennePeriode(v);
                for (int it = 0; it < 300; it++) {
                    w.setIteration(it);
                    assertEquals(it % p == 0, a.isMyTurn(),
                            a.getClass().getSimpleName() + " vitesse=" + v + " it=" + it);
                }
            }
        }
    }

    /** Journal de tours du rendu : un tour immobile ajoute quand même une entrée
     *  (case inchangée, tick du tour, tick du tour suivant) ; un tick hors tour n'ajoute rien. */
    @Test
    void tourImmobileAjouteUneEntree() {
        WorldOfCells w = AgentTestSupport.buildWorld();
        int[] c = AgentTestSupport.findLandCell(w, 20, 20);
        AgentTestSupport.flattenLandArea(w, c[0], c[1], 3);
        Loup l = new Loup(c[0], c[1], w);
        l.energie = l.energieD;
        l.playerControlled = true;     // piloté sans touche : le tour se joue sur place
        l.controlDx = 0; l.controlDy = 0;
        assertTrue(l.motion.isEmpty(), "journal vide avant le premier tour");

        w.setIteration(0);
        l.step();
        assertFalse(l.motion.isEmpty(), "le premier tour, immobile, ajoute une entrée");
        assertEquals(0, l.motion.lastTick());
        assertEquals(c[0], l.motion.lastX());
        assertEquals(c[1], l.motion.lastY());
        long next = l.motion.lastNextTick();
        assertEquals(l.turnPeriodTicks(), next, "tour suivant = prochain multiple de la période");

        for (int it = 1; it < next; it++) {   // ticks hors tour : rien n'est ajouté
            w.setIteration(it);
            l.step();
            assertEquals(0, l.motion.lastTick(), "pas d'entrée hors tour (it=" + it + ")");
        }
        w.setIteration((int) next);
        l.step();
        assertEquals(next, l.motion.lastTick(), "deuxième tour immobile : nouvelle entrée");
        assertEquals(c[0], l.motion.lastX());
        assertEquals(c[1], l.motion.lastY());
        assertEquals(c[0], l.x, "l'agent n'a pas bougé");
    }

    /** Prédiction des tours sur un long run (4 espèces, relief Perlin, chasses,
     *  changements d'état et de vitesse) : le tour suivant noté par chaque entrée
     *  est exactement le tick de l'entrée suivante, et aucun tour n'est sauté. */
    @Test
    void tourSuivantPreditTombeAuTickDeLEntreeSuivante() {
        WorldOfCells w = AgentTestSupport.buildWorld();
        java.util.Random rnd = new java.util.Random(7);
        int W = w.getWidth(), H = w.getHeight();
        for (int k = 0; k < 12; k++) {
            int[] c = AgentTestSupport.findLandCell(w, rnd.nextInt(W), rnd.nextInt(H));
            Agent a;
            switch (k % 4) {
                case 0:  { Loup l = new Loup(c[0], c[1], w); w.loups.add(l); a = l; break; }
                case 1:  { Mouton m = new Mouton(c[0], c[1], w); w.moutons.add(m); a = m; break; }
                case 2:  { Mouton m = new Mouton(c[0], c[1], w); w.moutons.add(m); a = m; break; }
                default: { Ours o = new Ours(c[0], c[1], w); w.ours.add(o); a = o; break; }
            }
            w.agents.add(a);
            w.uniqueDynamicObjects.add(a);
        }

        java.util.Map<Agent, long[]> prev = new java.util.IdentityHashMap<>();   // {tick, nextTick}
        java.util.Map<Agent, java.util.Set<Long>> periods = new java.util.IdentityHashMap<>();
        int verified = 0;
        for (int step = 0; step < 3000; step++) {
            w.step();
            long played = w.getIteration() - 1;   // tick que les agents viennent de jouer
            for (Agent a : new java.util.ArrayList<>(w.agents)) {
                if (!a._alive || a.motion.isEmpty()) continue;
                long t = a.motion.lastTick(), nt = a.motion.lastNextTick();
                periods.computeIfAbsent(a, k -> new java.util.HashSet<>()).add(nt - t);
                long[] p = prev.get(a);
                if (p != null && t != p[0]) {
                    assertEquals(p[1], t, a.getClass().getSimpleName()
                            + " : tour prédit au tick " + p[1] + ", joué au tick " + t);
                    verified++;
                } else if (p != null) {
                    assertTrue(played < p[1], a.getClass().getSimpleName()
                            + " : tour prédit au tick " + p[1] + " non joué (tick " + played + ")");
                }
                prev.put(a, new long[] { t, nt });
            }
        }
        assertTrue(verified > 500, "assez de tours vérifiés : " + verified);
        long changing = periods.values().stream().filter(s -> s.size() >= 2).count();
        assertTrue(changing >= 1, "au moins un agent change de période en cours de run");
    }

    /** Le pas « 1 seconde-jeu » suit simulationHz (fallback 20 sans config) au lieu
     *  d'un 20 code en dur — sinon la letalite du feu varierait avec le Hz. */
    @Test
    void ticksParSecondeSuitSimulationHz() {
        WorldOfCells w = AgentTestSupport.buildWorld();
        Loup l = new Loup(10, 10, w);

        w.config = null;
        assertEquals(20, l.ticksPerGameSecond(), "fallback 20 sans config");

        SimulationConfig c = new SimulationConfig();
        c.simulationHz = 60;
        w.config = c;
        assertEquals(60, l.ticksPerGameSecond(), "suit simulationHz=60");

        c.simulationHz = 10;
        assertEquals(10, l.ticksPerGameSecond(), "suit simulationHz=10");
    }
}
