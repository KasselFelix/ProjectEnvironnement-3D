package agents.ai;

import objects.UniqueDynamicObject;
import worlds.World;

public final class Locomotion {

    private Locomotion() {}

    /** Tente d'avancer d'une case selon orient ; renvoie true si l'agent a bougé. */
    public static boolean move(UniqueDynamicObject a, World world,
                               int orient, MoveConstraints c) {
        final int w = world.getWidth(), h = world.getHeight();
        int tx = a.x, ty = a.y;
        switch (orient) {
            case 0: ty = (a.y - 1 + h) % h; break; // Nord
            case 1: tx = (a.x + 1) % w;     break; // Est
            case 2: ty = (a.y + 1) % h;     break; // Sud
            case 3: tx = (a.x - 1 + w) % w; break; // Ouest
        }
        if (passable(world, tx, ty, c)) {
            a.x = tx; a.y = ty;
            return true;
        }
        // Fallback : évitement aléatoire (20 essais), identique au code legacy.
        int i = 0, j = 0, rx, ry, cpt = 20;
        do {
            i = (int) (Math.random() * 3) - 1;
            j = (int) (Math.random() * 3) - 1;
            rx = (a.x + i + w) % w;
            ry = (a.y + j + h) % h;
            cpt--;
        } while (!passable(world, rx, ry, c) && cpt != 0);
        if (cpt != 0) {
            a.x = (a.x + i + w) % w;
            a.y = (a.y + j + h) % h;
            return (i != 0 || j != 0);
        }
        return false;
    }

    /**
     * Déplace l'agent d'un pas (dx, dy ∈ {-1,0,1}), diagonales comprises. Si la
     * case diagonale est bloquée, tente de glisser le long d'un axe (dx,0) puis
     * (0,dy). Renvoie true si l'agent a bougé. Utilisé par le pilotage manuel.
     */
    public static boolean moveBy(UniqueDynamicObject a, World world,
                                 int dx, int dy, MoveConstraints c) {
        if (dx == 0 && dy == 0) return false;
        final int w = world.getWidth(), h = world.getHeight();
        int tx = ((a.x + dx) % w + w) % w;
        int ty = ((a.y + dy) % h + h) % h;
        if (passable(world, tx, ty, c)) { a.x = tx; a.y = ty; return true; }
        // Diagonale bloquée : glisse le long d'un mur (un seul axe).
        if (dx != 0) {
            int sx = ((a.x + dx) % w + w) % w;
            if (passable(world, sx, a.y, c)) { a.x = sx; return true; }
        }
        if (dy != 0) {
            int sy = ((a.y + dy) % h + h) % h;
            if (passable(world, a.x, sy, c)) { a.y = sy; return true; }
        }
        return false;
    }

    private static boolean passable(World world, int x, int y, MoveConstraints c) {
        if (c.avoidForest && world.getForestCAValue(x, y) != 0) return false;
        if (c.avoidLava   && world.getLavaCAValue(x, y)   != 0) return false;
        if (!c.allowWater && world.getCellHeight(x, y) < 0)     return false;
        return true;
    }
}
