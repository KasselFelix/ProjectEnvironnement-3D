package agents;

import agents.ai.AgentState;
import agents.ai.Axis;
import agents.ai.Pole;
import org.junit.jupiter.api.Test;
import worlds.WorldOfCells;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase D — intelligence (Mind) du Mouton (cf. docs/evolution.txt § 6) : chaque
 * mouton porte un esprit initialisé depuis son génome, entraîné selon son
 * activité.
 */
class MoutonMindTest {

    /** Un mouton intelligent démarre avec un score d'esprit plus élevé (§ 6.1). */
    @Test
    void moutonIntelligentADuMindPlusEleve() {
        WorldOfCells world = AgentTestSupport.buildWorld();
        Mouton intelligent = new Mouton(20, 20, world);
        intelligent.genome.set(Axis.INTELLIGENCE, Pole.POSITIVE);
        intelligent.initMind();
        Mouton neutre = new Mouton(20, 20, world);

        assertTrue(intelligent.mind.score() > neutre.mind.score(),
                "l'esprit du mouton intelligent démarre plus haut");
    }

    // ===== Évolution de l'intelligence sur toute une vie (correctif cadence) =====
    //
    // mind.train() est appelé À CHAQUE tick (postTick). Avant le correctif, les
    // taux étaient PAR APPEL → ~4800 appels/jour faisaient s'effondrer le score en
    // ~5 s. Désormais les taux sont PAR JOUR, intégrés sur dt (1 tick en jours).
    // Ces tests simulent la vie d'un mouton avec le MÊME dt que le jeu.

    /** dt d'un tick exprimé en jours-jeu, comme dans Agent.trainMindAndCharacter. */
    private static int ticksPerDay(WorldOfCells w) { return 2 * Math.max(1, w.getDureeJour()); }

    /** Simule `lifespanDays` jours de vie : entraîne l'esprit `ticksPerDay` fois
     *  par jour, à activité constante, en faisant croître ageFraction de 0 à 1.
     *  Retourne le score d'intelligence échantillonné à la fin de chaque jour
     *  (index 0 = naissance). */
    private static double[] simulateLife(Mouton m, double activity, int lifespanDays, int ticksPerDay) {
        double dtDays = 1.0 / ticksPerDay;
        long total = (long) lifespanDays * ticksPerDay;
        double[] perDay = new double[lifespanDays + 1];
        perDay[0] = m.mind.score();
        long tick = 0;
        for (int day = 1; day <= lifespanDays; day++) {
            for (int t = 0; t < ticksPerDay; t++) {
                tick++;
                double ageFraction = (double) tick / total;
                m.mind.train(activity, ageFraction, m.genome.longevityFactor(), dtDays);
            }
            perDay[day] = m.mind.score();
        }
        return perDay;
    }

    /** RÉGRESSION du bug signalé : l'intelligence ne doit PAS s'effondrer en un
     *  jour. Un jeune mouton passif perd à peine sur 24 h (avant : tombait à 0). */
    @Test
    void intelligenceNeSEffondrePasEnUnJour() {
        WorldOfCells world = AgentTestSupport.buildWorld();
        Mouton m = new Mouton(20, 20, world);            // esprit neutre (0.5)
        double start = m.mind.score();

        int tpd = ticksPerDay(world);
        double dtDays = 1.0 / tpd;
        double ageFraction = 0.05;                        // jeune
        for (int t = 0; t < tpd; t++)                     // 1 jour, totalement PASSIF
            m.mind.train(0.0, ageFraction, m.genome.longevityFactor(), dtDays);

        double afterOneDay = m.mind.score();
        assertTrue(afterOneDay > start - 0.05,
                "en 1 jour passif un jeune mouton perd <0.05 d'intelligence (etait ~0). start="
                        + start + " apres=" + afterOneDay);
        assertTrue(afterOneDay > 0.4, "le score reste loin de 0 (=" + afterOneDay + ")");
    }

    /** RÉALISME : sur une vie engagée, l'intelligence monte pendant la jeunesse/
     *  l'âge adulte puis décline au grand âge (courbe en cloche), de façon
     *  GRADUELLE (en jours, pas en ticks). Affiche la trajectoire jour par jour. */
    @Test
    void intelligenceMonteAdulteEtDeclineVieux() {
        WorldOfCells world = AgentTestSupport.buildWorld();
        Mouton m = new Mouton(20, 20, world);            // neutre (0.5)
        int lifespanDays = 12;
        double activity = 0.6;                            // mouton « engagé » (mix décision/repos)
        double[] s = simulateLife(m, activity, lifespanDays, ticksPerDay(world));

        StringBuilder sb = new StringBuilder("\n[MoutonMind] intelligence sur la vie (L="
                + lifespanDays + "j, activite=" + activity + ") :\n");
        for (int d = 0; d <= lifespanDays; d++)
            sb.append(String.format(java.util.Locale.US,
                    "  j%-2d  age=%4.0f%%  Intel=%.3f%n", d, 100.0 * d / lifespanDays, s[d]));
        System.out.print(sb);

        // Pic strictement à l'intérieur de la vie (ni naissance ni mort).
        int peakDay = 0;
        for (int d = 1; d <= lifespanDays; d++) if (s[d] > s[peakDay]) peakDay = d;
        assertTrue(peakDay >= 2 && peakDay <= lifespanDays - 2,
                "le pic d'intelligence tombe en milieu de vie (j" + peakDay + ")");
        assertTrue(s[peakDay] > s[0] + 0.05, "l'esprit progresse durant la jeunesse");
        assertTrue(s[lifespanDays] < s[peakDay] - 0.03,
                "l'esprit decline au grand age (fin=" + s[lifespanDays] + " < pic=" + s[peakDay] + ")");

        // Graduel : aucun saut brutal d'un jour à l'autre (≠ effondrement par tick).
        for (int d = 1; d <= lifespanDays; d++)
            assertTrue(Math.abs(s[d] - s[d - 1]) < 0.2,
                    "variation journaliere douce a j" + d + " (" + (s[d] - s[d - 1]) + ")");
    }

    /** RÉALISME : à âge ABSOLU égal, un mouton à plus longue espérance de vie
     *  conserve une intelligence plus haute (le déclin dépend bien de la durée de
     *  vie via ageFraction). */
    @Test
    void uneVieLonguePreserveMieuxLIntelligence() {
        WorldOfCells world = AgentTestSupport.buildWorld();
        int tpd = ticksPerDay(world);
        double activity = 0.6;
        int absoluteAgeDays = 8;

        Mouton courteVie = new Mouton(20, 20, world);    // vit 8 jours → a 8j = très vieux
        double[] sCourt = simulateLife(courteVie, activity, absoluteAgeDays, tpd);
        Mouton longueVie = new Mouton(20, 20, world);    // vit 16 jours → a 8j = mi-vie
        double[] sLong = simulateLifeOverLongerLifespan(longueVie, activity, absoluteAgeDays, 16, tpd);

        assertTrue(sLong[absoluteAgeDays] > sCourt[absoluteAgeDays] + 0.1,
                "a " + absoluteAgeDays + "j, le mouton longevif est plus intelligent ("
                        + sLong[absoluteAgeDays] + " vs " + sCourt[absoluteAgeDays] + ")");
    }

    /** Variante de simulateLife où ageFraction est rapporté à un `lifespanDays`
     *  différent du nombre de jours simulés (pour comparer à âge absolu égal). */
    private static double[] simulateLifeOverLongerLifespan(Mouton m, double activity,
            int simulatedDays, int lifespanDays, int ticksPerDay) {
        double dtDays = 1.0 / ticksPerDay;
        long lifeTotal = (long) lifespanDays * ticksPerDay;
        double[] perDay = new double[simulatedDays + 1];
        perDay[0] = m.mind.score();
        long tick = 0;
        for (int day = 1; day <= simulatedDays; day++) {
            for (int t = 0; t < ticksPerDay; t++) {
                tick++;
                double ageFraction = (double) tick / lifeTotal;
                m.mind.train(activity, ageFraction, m.genome.longevityFactor(), dtDays);
            }
            perDay[day] = m.mind.score();
        }
        return perDay;
    }

    /** Le niveau d'activité cognitive (§ 6.2) dépend de l'état : les états de
     *  survie/décision sont actifs (1.0), le repos et l'errance passifs (0.0). */
    @Test
    void niveauDActiviteSelonLEtat() {
        WorldOfCells world = AgentTestSupport.buildWorld();
        Mouton m = new Mouton(20, 20, world);

        m.currentState = AgentState.FLEE_PREDATOR;
        assertEquals(1.0, m.activityLevel(), 1e-9, "fuir est une activité cognitive");
        m.currentState = AgentState.SEEK_FOOD;
        assertEquals(1.0, m.activityLevel(), 1e-9, "chercher de la nourriture aussi");
        m.currentState = AgentState.REST;
        assertEquals(0.0, m.activityLevel(), 1e-9, "le repos n'entraîne pas l'esprit");
        m.currentState = AgentState.WANDER;
        assertEquals(0.0, m.activityLevel(), 1e-9, "l'errance non plus");
    }
}
