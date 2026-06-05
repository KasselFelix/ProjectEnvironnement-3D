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
        final int vision = visionOf(self);

        int predatorDir = -1; double predatorDist = vision + 1;
        int preyDir = -1;     double preyDist = vision + 1;
        int waterDir = -1;    double waterDist = vision + 1;
        int landDir = -1;     double landDist = vision + 1;
        int grassDir = -1;    double grassDist = vision + 1;

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

        return new Percept(predatorDir, predatorDist, preyDir, preyDist,
                waterDir, waterDist, landDir, landDist, grassDir, grassDist,
                fireAdj, lavaAdj, inWater, onLava, cardinalFree);
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

    private static int visionOf(objects.UniqueDynamicObject self) {
        if (self instanceof agents.Loup)   return ((agents.Loup) self).vision;
        if (self instanceof agents.Mouton) return ((agents.Mouton) self).vision;
        if (self instanceof agents.Humain) return ((agents.Humain) self).vision;
        return 10;
    }
}
