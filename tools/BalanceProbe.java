// Sonde de balance écosystème — HEADLESS (aucun OpenGL).
// Fait tourner le vrai WorldOfCells sur plusieurs années-jeu et trace les
// populations à chaque jour-jeu + à chaque transition de saison, pour caler
// l'équilibre (longévité / énergie / populations). Paramètres via -Dxxx=...
//
// Compile : javac -cp "bin:JOGL/jar/*" -d tools/out tools/BalanceProbe.java
// Run     : java -cp "bin:tools/out:JOGL/jar/*" -Dyears=4 -DnbMoutons=80 ... BalanceProbe
//
// 1 jour-jeu = cycleTotalSec(240) × hz(20) = 4800 ticks = 1 "mois".
// 1 saison = seasonLengthDays jours ; 1 an = 4 saisons.

import landscapegenerator.PerlinNoiseLandscapeGenerator;
import ui.SimulationConfig;
import worlds.Season;
import worlds.WorldOfCells;

public class BalanceProbe {

    static double dbl(String k, double d) { String v = System.getProperty(k); return v == null ? d : Double.parseDouble(v); }
    static int   intp(String k, int d)    { String v = System.getProperty(k); return v == null ? d : Integer.parseInt(v); }

    public static void main(String[] args) {
        int dim = intp("dim", 200);

        SimulationConfig c = new SimulationConfig();
        c.landscapeDx = dim; c.landscapeDy = dim;
        c.landscapeWaterRatio = dbl("waterRatio", c.landscapeWaterRatio);
        c.seasonLengthDays = intp("seasonLengthDays", c.seasonLengthDays);
        c.nbLoups   = intp("nbLoups",   c.nbLoups);
        c.nbMoutons = intp("nbMoutons", c.nbMoutons);
        c.nbHumains = intp("nbHumains", c.nbHumains);
        c.nbOurs    = intp("nbOurs",    c.nbOurs);
        c.loupEnergieMax    = intp("loupEnergieMax",    c.loupEnergieMax);
        c.moutonEnergieMax  = dbl("moutonEnergieMax",   c.moutonEnergieMax);
        c.loupMaxAgeDays    = dbl("loupMaxAgeDays",     c.loupMaxAgeDays);
        c.moutonMaxAgeDays  = dbl("moutonMaxAgeDays",   c.moutonMaxAgeDays);
        c.loupReproEnergyThreshold   = dbl("loupReproEnergyThreshold",   c.loupReproEnergyThreshold);
        c.moutonReproEnergyThreshold = dbl("moutonReproEnergyThreshold", c.moutonReproEnergyThreshold);
        c.loupPrepro   = dbl("loupPrepro",   c.loupPrepro);
        c.moutonPrepro = dbl("moutonPrepro", c.moutonPrepro);
        c.herbeProbApparition = dbl("herbeProb", c.herbeProbApparition);
        c.herbeProbFeu = dbl("herbeProbFeu", c.herbeProbFeu);
        c.reproRadius = intp("reproRadius", c.reproRadius);
        c.loupMetabolicFactor = dbl("loupMetabolicFactor", c.loupMetabolicFactor);
        c.oursMetabolicFactor = dbl("oursMetabolicFactor", c.oursMetabolicFactor);
        c.loupHungerRatio = dbl("loupHungerRatio", c.loupHungerRatio);
        c.oursHungerRatio = dbl("oursHungerRatio", c.oursHungerRatio);
        c.loupReproOffspringRatio = dbl("loupReproOffspringRatio", c.loupReproOffspringRatio);
        c.moutonReproEnergyThreshold = dbl("moutonReproEnergyThreshold", c.moutonReproEnergyThreshold);
        // ── Herbe en brins (Phase 2) ──
        c.brinsMax            = intp("brinsMax",            c.brinsMax);
        c.energyPerBrin       = dbl("energyPerBrin",       c.energyPerBrin);
        c.grazeCooldownSec    = dbl("grazeCooldownSec",    c.grazeCooldownSec);
        c.brinsRegrowthPerSec = dbl("brinsRegrowthPerSec", c.brinsRegrowthPerSec);
        c.brinsInitialFill    = dbl("brinsInitialFill",    c.brinsInitialFill);
        // Énergie tirée de la viande (carcasse) — lève la prédation hors déficit (carnivores seuls).
        objects.Carcass.ENERGY_PER_KG = dbl("energyPerKg", objects.Carcass.ENERGY_PER_KG);
        c.awaitingStart = false;
        SimulationConfig.setInstance(c);

        WorldOfCells w = new WorldOfCells();
        w.config = c;
        double[][] ls = PerlinNoiseLandscapeGenerator.generatePerlinNoiseLandscape(
                dim, dim, c.landscapeScaling, c.landscapeWaterRatio, c.landscapeOctaves);
        w.init(dim - 1, dim - 1, ls);

        int hz = c.simulationHz;
        int ticksPerDay = (int) Math.round(c.cycleTotalSec * hz);   // 4800
        int ticksPerSeason = ticksPerDay * c.seasonLengthDays;
        double years = dbl("years", 4.0);
        long totalTicks = (long) (years * 4 * ticksPerSeason);

        boolean timing = Boolean.getBoolean("timing");
        if (timing) {
            long t0 = System.nanoTime();
            int n = intp("timingTicks", 5000);
            for (int i = 0; i < n; i++) w.step();
            double ms = (System.nanoTime() - t0) / 1e6;
            System.out.printf("TIMING dim=%d : %d ticks en %.0f ms = %.3f ms/tick → 1 an(%d ticks)=%.1f s%n",
                    dim, n, ms, ms / n, 4 * ticksPerSeason, (ms / n) * 4 * ticksPerSeason / 1000.0);
            return;
        }

        System.out.printf("# dim=%d saison=%dj an=%d ticks | loups=%d moutons=%d ours=%d humains=%d%n",
                dim, c.seasonLengthDays, 4 * ticksPerSeason, c.nbLoups, c.nbMoutons, c.nbOurs, c.nbHumains);
        System.out.printf("# loupAge=%.0f moutonAge=%.0f loupE=%d moutonE=%.0f%n",
                c.loupMaxAgeDays, c.moutonMaxAgeDays, c.loupEnergieMax, c.moutonEnergieMax);
        System.out.println("day\tseason\tloups\tmoutons\tours\thumains");

        int minLoups = Integer.MAX_VALUE, minMoutons = Integer.MAX_VALUE;
        Season prev = null;
        boolean loupExtinct = false, moutonExtinct = false;
        // Compteurs au DÉBUT de chaque saison de rut (loup=WINTER, mouton=AUTUMN)
        StringBuilder rutLog = new StringBuilder();

        for (long t = 0; t <= totalTicks; t++) {
            Season s = w.currentSeason();
            if (t % ticksPerDay == 0 || s != prev) {
                int day = (int) (t / ticksPerDay);
                int nl = w.loups.size(), nm = w.moutons.size(), no = w.ours.size(), nh = w.humains.size();
                if (s != prev) {
                    if (s == Season.WINTER) rutLog.append(String.format("  [rut LOUP]   j%d : %d loups%n", day, nl));
                    if (s == Season.AUTUMN) rutLog.append(String.format("  [rut MOUTON] j%d : %d moutons%n", day, nm));
                    prev = s;
                }
                if (t % ticksPerDay == 0) {
                    // Diagnostic : énergie moyenne des moutons + énergie moyenne loups
                    double meM = 0; for (agents.Mouton mo : w.moutons) meM += mo.energie;
                    if (nm > 0) meM /= nm;
                    double meL = 0; for (agents.Loup lo : w.loups) meL += lo.energie;
                    if (nl > 0) meL /= nl;
                    int grass = 0, land = 0; long brinsTot = 0;
                    for (int gx = 0; gx < w.getWidth(); gx++)
                        for (int gy = 0; gy < w.getHeight(); gy++) {
                            if (w.getCellHeight(gx, gy) >= 0) land++;
                            if (w.getGrassCAValue(gx, gy) == 1) { grass++; brinsTot += w.getGrassBrins(gx, gy); }
                        }
                    double grassPct = land > 0 ? 100.0 * grass / land : 0;
                    double brinsMoy = grass > 0 ? (double) brinsTot / grass : 0;   // brins moyens par case d'herbe
                    int onGrass = 0; for (agents.Mouton mo : w.moutons) if (w.getGrassCAValue(mo.x, mo.y) == 1) onGrass++;
                    double onGrassPct = nm > 0 ? 100.0 * onGrass / nm : 0;
                    java.util.Map<String,Integer> beh = new java.util.TreeMap<>();
                    for (agents.Mouton mo : w.moutons) { String b = mo.getCurrentBehavior(); beh.merge(b, 1, Integer::sum); }
                    System.out.printf("%d\t%s\t%d\t%d\t%d\t%d\tEm=%.0f\tEl=%.0f\therbe=%.0f%%\tbrinsMoy=%.2f\tmoutonSurHerbe=%.0f%%\t%s%n",
                            day, s, nl, nm, no, nh, meM, meL, grassPct, brinsMoy, onGrassPct, beh);
                    minLoups = Math.min(minLoups, nl);
                    minMoutons = Math.min(minMoutons, nm);
                    if (nl == 0) loupExtinct = true;
                    if (nm == 0) moutonExtinct = true;
                    boolean hadAgents = (c.nbLoups + c.nbMoutons + c.nbOurs) > 0;
                    if (hadAgents && nl == 0 && nm == 0) { System.out.println("# EXTINCTION TOTALE jour " + day); t = totalTicks + 1; }
                }
            }
            if (t < totalTicks) w.step();
        }

        System.out.println("# --- BILAN ---");
        System.out.println("# loups   min=" + minLoups + (loupExtinct ? "  EXTINCT à un moment" : "  jamais éteint"));
        System.out.println("# moutons min=" + minMoutons + (moutonExtinct ? "  EXTINCT à un moment" : "  jamais éteint"));
        System.out.print(rutLog);
    }
}
