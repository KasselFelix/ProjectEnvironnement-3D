package agents.ai;

import java.util.List;
import objects.UniqueDynamicObject;
import worlds.World;
import cellularautomata.ecosystem.ForestCA;

public final class Perception {

    private Perception() {}

    /**
     * Balaye le champ de vision de l'agent UNE fois et renvoie un Percept.
     * @param predators agents fuis (peut être null)
     * @param prey      agents chassés (peut être null)
     */
    public static Percept sense(objects.UniqueDynamicObject self, World world,
                                List<? extends UniqueDynamicObject> predators,
                                List<? extends UniqueDynamicObject> prey) {
        final int ax = self.x, ay = self.y;
        final int w = world.getWidth(), h = world.getHeight();
        // L7 — fumée : un agent entouré de feu/lave voit moins loin (la fumée
        // brouille la vue). La portée effective est réduite tant qu'il est dans
        // la fumée.
        int vision = visionOf(self);
        if (smokyAround(self, world)) {
            vision = Math.max(1, (int) Math.round(vision * SMOKE_VISION_FACTOR));
        }
        // Task 10 — tête baissée : un agent qui mange (loup/ours sur carcasse,
        // mouton qui broute) voit deux fois moins loin → il réagit plus tard aux prédateurs.
        if (self instanceof agents.Agent && ((agents.Agent) self).isFeeding()) {
            vision = Math.max(1, (int) Math.round(vision * EATING_VISION_FACTOR));
        }

        int predatorDir = -1; double predatorDist = vision + 1;
        int preyDir = -1;     double preyDist = vision + 1;
        int preyX = -1, preyY = -1;   // cellule de la proie la plus proche (pour le pathfinding)
        int carcassDir = -1;  double carcassDist = vision + 1;
        int carcassX = -1, carcassY = -1;
        int waterDir = -1;    double waterDist = vision + 1;
        int landDir = -1;     double landDist = vision + 1;
        int grassDir = -1;    double grassDist = vision + 1;
        int lavaDir = -1;     double lavaDist = vision + 1;   // L2

        // Cibles mobiles : direction depuis le delta torique réel (correctif C1).
        if (predators != null) {
            for (UniqueDynamicObject a : predators) {
                if (a == self) continue;
                double d = torusDist(ax, ay, a.x, a.y, w, h);
                if (d <= vision && d < predatorDist) {
                    predatorDist = d;
                    predatorDir = dominantDir(ax, ay, a.x, a.y, w, h);
                }
            }
        }
        if (prey != null) {
            for (UniqueDynamicObject a : prey) {
                if (a == self) continue;
                double d = torusDist(ax, ay, a.x, a.y, w, h);
                if (d <= vision && d < preyDist) {
                    preyDist = d;
                    preyDir = dominantDir(ax, ay, a.x, a.y, w, h);
                    preyX = a.x; preyY = a.y;
                }
            }
        }

        // Carcasses comestibles : canal parallele aux proies mobiles.
        for (objects.Carcass c : world.carcasses) {
            if (c.isGone()) continue;
            double d = torusDist(ax, ay, c.getX(), c.getY(), w, h);
            if (d <= vision && d < carcassDist) {
                carcassDist = d;
                carcassDir = dominantDir(ax, ay, c.getX(), c.getY(), w, h);
                carcassX = c.getX(); carcassY = c.getY();
            }
        }

        // Terrain : balayage en anneaux pour eau/terre la plus proche.
        for (int r = 1; r <= vision; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dy = -r; dy <= r; dy++) {
                    if (Math.max(Math.abs(dx), Math.abs(dy)) != r) continue; // anneau
                    int i = ((ax + dx) % w + w) % w;
                    int j = ((ay + dy) % h + h) % h;
                    double d = torusDist(ax, ay, i, j, w, h);
                    boolean land = world.getCellHeight(i, j) >= 0;
                    if (land && d < landDist)  { landDist = d;  landDir = dominantDir(ax, ay, i, j, w, h); }
                    if (!land && d < waterDist){ waterDist = d; waterDir = dominantDir(ax, ay, i, j, w, h); }
                    // Nourriture (herbe) la plus proche en vue : alimente SEEK_FOOD
                    // du mouton. Même boucle d'anneaux → aucun coût supplémentaire.
                    if (world.getGrassCAValue(i, j) == 1 && d < grassDist) {
                        grassDist = d; grassDir = dominantDir(ax, ay, i, j, w, h);
                    }
                    // L2 — lave la plus proche en vue : alimente FLEE_LAVA (fuite
                    // active à l'opposé). Même balayage d'anneaux → coût nul.
                    if (world.getLavaCAValue(i, j) > 0 && d < lavaDist) {
                        lavaDist = d; lavaDir = dominantDir(ax, ay, i, j, w, h);
                    }
                }
            }
        }

        // Voisinage immédiat.
        int[] nx = { ax, (ax + 1) % w, ax, (ax - 1 + w) % w };
        int[] ny = { (ay - 1 + h) % h, ay, (ay + 1) % h, ay };
        boolean fireAdj = false, lavaAdj = false;
        boolean[] cardinalFree = new boolean[4];
        for (int d = 0; d < 4; d++) {
            int fCA = world.getForestCAValue(nx[d], ny[d]);
            int lCA = world.getLavaCAValue(nx[d], ny[d]);
            if (ForestCA.isTreeOnFire(fCA)) fireAdj = true;
            if (lCA > 0) lavaAdj = true;
            cardinalFree[d] = fCA == 0 && lCA == 0;
        }
        boolean inWater = world.getCellHeight(ax, ay) < 0;
        boolean onLava  = world.getLavaCAValue(ax, ay) > 0;

        // ── Olfaction (sous-projet B) : gate biologique + sondes cardinales ──
        int scentPreyDir = -1, scentDangerDir = -1, scentCarcassDir = -1, scentMateDir = -1;
        double scentPreyI = 0, scentDangerI = 0, scentCarcassI = 0, scentMateI = 0;
        double acuity = olfactionAcuity(self);
        ui.SimulationConfig cfg = ui.SimulationConfig.getInstance();
        if (acuity >= cfg.olfactionGate && self instanceof agents.Agent) {
            agents.Agent ag = (agents.Agent) self;
            scent.ScentField fld = world.getScentField();
            int now = world.getIteration();
            double thr = cfg.olfactionDetectBase / acuity;
            int probe = (int) Math.round(acuity * cfg.olfactionProbeK);
            if (probe < 1) probe = 1;
            if (probe > OLFACTION_PROBE_MAX) probe = OLFACTION_PROBE_MAX;
            scent.ScentKind[] preyK = ag.scentPreyKinds();
            scent.ScentKind[] dangerK = ag.scentDangerKinds();

            scent.ScentReading r0 = fld.sampleAt(ax, ay, now);
            double preyHere = sumKinds(r0, preyK);
            double dangerHere = sumKinds(r0, dangerK);
            double carcHere = r0.of(scent.ScentKind.CARCASS);

            double preyBest = 0, dangerBest = 0, carcBest = 0;
            int preyBestDir = -1, dangerBestDir = -1, carcBestDir = -1;
            int[] pdx = { 0, probe, 0, -probe };   // 0=N,1=E,2=S,3=O
            int[] pdy = { -probe, 0, probe, 0 };
            for (int d = 0; d < 4; d++) {
                int px = ((ax + pdx[d]) % w + w) % w;
                int py = ((ay + pdy[d]) % h + h) % h;
                scent.ScentReading r = fld.sampleAt(px, py, now);
                double pv = sumKinds(r, preyK);
                if (pv > preyBest)   { preyBest = pv;   preyBestDir = d; }
                double dv = sumKinds(r, dangerK);
                if (dv > dangerBest) { dangerBest = dv; dangerBestDir = d; }
                double cv = r.of(scent.ScentKind.CARCASS);
                if (cv > carcBest)   { carcBest = cv;   carcBestDir = d; }
            }
            double preyMax = Math.max(preyHere, preyBest);
            if (preyMax > thr) { scentPreyI = preyMax; scentPreyDir = (preyBest > thr) ? preyBestDir : -1; }
            double dangerMax = Math.max(dangerHere, dangerBest);
            if (dangerMax > thr) { scentDangerI = dangerMax; scentDangerDir = (dangerBest > thr) ? dangerBestDir : -1; }
            double carcMax = Math.max(carcHere, carcBest);
            if (carcMax > thr) { scentCarcassI = carcMax; scentCarcassDir = (carcBest > thr) ? carcBestDir : -1; }
            // Sous-projet E : canal de SÉDUCTION = odeur mating de la PROPRE espèce,
            // hors soi (emitterId). Mêmes sondes cardinales, même seuil de détection.
            scent.ScentKind ownKind = ag.scentKind();
            double mateHere = fld.matingIntensityAt(ax, ay, now, ownKind, ag.agentId);
            double mateBest = 0; int mateBestDir = -1;
            for (int d = 0; d < 4; d++) {
                int px = ((ax + pdx[d]) % w + w) % w;
                int py = ((ay + pdy[d]) % h + h) % h;
                double mv = fld.matingIntensityAt(px, py, now, ownKind, ag.agentId);
                if (mv > mateBest) { mateBest = mv; mateBestDir = d; }
            }
            double mateMax = Math.max(mateHere, mateBest);
            if (mateMax > thr) { scentMateI = mateMax; scentMateDir = (mateBest > thr) ? mateBestDir : -1; }
        }

        return new Percept(predatorDir, predatorDist, preyDir, preyDist, preyX, preyY,
                carcassDir, carcassDist, carcassX, carcassY,
                waterDir, waterDist, landDir, landDist, grassDir, grassDist,
                lavaDir, lavaDist, fireAdj, lavaAdj, inWater, onLava, cardinalFree,
                scentPreyDir, scentPreyI, scentDangerDir, scentDangerI,
                scentCarcassDir, scentCarcassI, scentMateDir, scentMateI, acuity);
    }

    /**
     * Direction dominante (0=N/1=E/2=S/3=O) vers le CENTRE DE MASSE des membres
     * de {@code flock} situés dans le rayon de vision de {@code self}, tore-aware.
     * Renvoie -1 si aucun membre n'est visible. Utilisé par le berger (Humain)
     * pour suivre le barycentre du troupeau plutôt que la bête la plus proche.
     */
    public static int dirToFlockCentroid(objects.UniqueDynamicObject self, World world,
                                         List<? extends UniqueDynamicObject> flock) {
        if (flock == null) return -1;
        final int ax = self.x, ay = self.y;
        final int w = world.getWidth(), h = world.getHeight();
        final int vision = visionOf(self);
        long sumDx = 0, sumDy = 0;
        int n = 0;
        for (UniqueDynamicObject a : flock) {
            if (a == self) continue;
            if (torusDist(ax, ay, a.x, a.y, w, h) > vision) continue;
            sumDx += signedDelta(ax, a.x, w);   // >0 → barycentre à l'Est
            sumDy += signedDelta(ay, a.y, h);   // >0 → barycentre au Sud
            n++;
        }
        if (n == 0) return -1;
        double mdx = sumDx / (double) n, mdy = sumDy / (double) n;
        if (Math.abs(mdx) >= Math.abs(mdy)) return mdx >= 0 ? 1 : 3;
        return mdy >= 0 ? 2 : 0;
    }

    /**
     * Direction dominante (0=N/1=E/2=S/3=O) de l'agent {@code self} vers la
     * cellule cible (tx,ty), tore-aware. -1 si l'agent est déjà sur la cible.
     * Utilisé pour rallier un point fixe (ex : l'Humain qui rentre au foyer).
     */
    public static int dirToCell(objects.UniqueDynamicObject self, World world, int tx, int ty) {
        final int w = world.getWidth(), h = world.getHeight();
        if (self.x == tx && self.y == ty) return -1;
        return dominantDir(self.x, self.y, tx, ty, w, h);
    }

    /**
     * Cap de navigation LONGUE DISTANCE vers ({@code tx}, {@code ty}) : le cap
     * exact ({@link #dirToCell}) éventuellement dévié d'un cran (±1) avec la
     * probabilité {@code errorProb} (gouvernée par l'axe Orientation, § 9).
     * Renvoie -1 si la cible est la position courante.
     */
    public static int navigate(objects.UniqueDynamicObject self, World world,
                               int tx, int ty, double errorProb, java.util.Random rng) {
        int base = dirToCell(self, world, tx, ty);
        if (base < 0) return -1;
        if (rng.nextDouble() < errorProb) {
            return rng.nextBoolean() ? (base + 1) % 4 : (base + 3) % 4;
        }
        return base;
    }

    /** Distance torique min entre deux cellules. */
    static double torusDist(int ax, int ay, int bx, int by, int w, int h) {
        double dx = Math.min((ax - bx + w) % w, (bx - ax + w) % w);
        double dy = Math.min((ay - by + h) % h, (by - ay + h) % h);
        return Math.sqrt(dx * dx + dy * dy);
    }

    /** Orientation dominante (0=N/1=E/2=S/3=O) de (a) vers (b), tore-aware. */
    static int dominantDir(int ax, int ay, int bx, int by, int w, int h) {
        int sdx = signedDelta(ax, bx, w); // >0 → cible à l'Est
        int sdy = signedDelta(ay, by, h); // >0 → cible au Sud (y croît vers le bas)
        if (Math.abs(sdx) >= Math.abs(sdy)) return sdx >= 0 ? 1 : 3;
        return sdy >= 0 ? 2 : 0;
    }

    /** Delta torique signé minimal de a vers b sur un axe de taille n. */
    static int signedDelta(int a, int b, int n) {
        int fwd = (b - a + n) % n;          // pas vers l'avant
        int bwd = (a - b + n) % n;          // pas vers l'arrière
        return (fwd <= bwd) ? fwd : -bwd;
    }

    /** Fraction de la vision conservée quand l'agent est dans la fumée (L7). */
    static final double SMOKE_VISION_FACTOR = 0.5;
    /** Fraction de la vision conservée quand l'agent mange (tête baissée — Task 10). */
    private static final double EATING_VISION_FACTOR = 0.5;
    /** Rayon (cases) de détection de fumée autour de l'agent (feu ou lave). */
    static final int SMOKE_RADIUS = 3;

    /** true si du feu (arbre en combustion) ou de la lave se trouve à
     *  {@link #SMOKE_RADIUS} cases de l'agent → il est dans la fumée (L7). */
    static boolean smokyAround(objects.UniqueDynamicObject self, World world) {
        final int w = world.getWidth(), h = world.getHeight();
        for (int dx = -SMOKE_RADIUS; dx <= SMOKE_RADIUS; dx++)
            for (int dy = -SMOKE_RADIUS; dy <= SMOKE_RADIUS; dy++) {
                int i = ((self.x + dx) % w + w) % w;
                int j = ((self.y + dy) % h + h) % h;
                if (ForestCA.isTreeOnFire(world.getForestCAValue(i, j))) return true;
                if (world.getLavaCAValue(i, j) > 0) return true;
            }
        return false;
    }

    /** Plafond de distance d'une sonde olfactive cardinale (cases). */
    static final int OLFACTION_PROBE_MAX = 8;

    /**
     * Acuité olfactive EFFECTIVE d'un agent = base d'espèce × facteur génome
     * (sous-projet B). 0 pour un non-agent ou une espèce sans odorat.
     */
    public static double olfactionAcuity(objects.UniqueDynamicObject self) {
        double base = olfactionBaseOf(self);
        if (base <= 0) return 0;
        if (self instanceof agents.Agent) base *= ((agents.Agent) self).genome.olfactionFactor();
        return base;
    }

    /** Base d'acuité par espèce (réglages SimulationConfig). */
    private static double olfactionBaseOf(objects.UniqueDynamicObject self) {
        ui.SimulationConfig c = ui.SimulationConfig.getInstance();
        if (self instanceof agents.Loup)   return c.olfactionBaseLoup;
        if (self instanceof agents.Ours)   return c.olfactionBaseOurs;
        if (self instanceof agents.Mouton) return c.olfactionBaseMouton;
        if (self instanceof agents.Humain) return c.olfactionBaseHumain;
        return 0;
    }

    /** Somme des intensités des classes d'odeur demandées dans une lecture. */
    private static double sumKinds(scent.ScentReading r, scent.ScentKind[] kinds) {
        double s = 0;
        for (scent.ScentKind k : kinds) s += r.of(k);
        return s;
    }

    private static int visionOf(objects.UniqueDynamicObject self) {
        if (self instanceof agents.Loup)   return ((agents.Loup) self).vision;
        if (self instanceof agents.Mouton) return ((agents.Mouton) self).vision;
        if (self instanceof agents.Humain) return ((agents.Humain) self).vision;
        if (self instanceof agents.Ours)   return ((agents.Ours) self).vision;
        return 10;
    }
}
