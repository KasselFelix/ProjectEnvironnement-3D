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

        int predatorDir = -1; double predatorDist = vision + 1;
        int preyDir = -1;     double preyDist = vision + 1;
        int preyX = -1, preyY = -1;   // cellule de la proie la plus proche (pour le pathfinding)
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

        return new Percept(predatorDir, predatorDist, preyDir, preyDist, preyX, preyY,
                waterDir, waterDist, landDir, landDist, grassDir, grassDist,
                lavaDir, lavaDist, fireAdj, lavaAdj, inWater, onLava, cardinalFree);
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

    private static int visionOf(objects.UniqueDynamicObject self) {
        if (self instanceof agents.Loup)   return ((agents.Loup) self).vision;
        if (self instanceof agents.Mouton) return ((agents.Mouton) self).vision;
        if (self instanceof agents.Humain) return ((agents.Humain) self).vision;
        return 10;
    }
}
