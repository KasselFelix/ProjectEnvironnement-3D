package agents;

import landscapegenerator.PerlinNoiseLandscapeGenerator;
import worlds.WorldOfCells;

/** Construit un WorldOfCells sans spawn aléatoire d'agents, pour les tests. */
public final class AgentTestSupport {
    public static final int DX_VIEW = 51;
    public static final int DY_VIEW = 51;
    public static final int DX = DX_VIEW - 1;
    public static final int DY = DY_VIEW - 1;

    private AgentTestSupport() {}

    public static WorldOfCells buildWorld() {
        WorldOfCells world = new WorldOfCells();
        world.nbloups = 0;
        world.nbmoutons = 0;
        world.nbhumains = 0;
        double[][] landscape = PerlinNoiseLandscapeGenerator
                .generatePerlinNoiseLandscape(DX_VIEW, DY_VIEW, 0.7, 0.4, 4);
        world.init(DX, DY, landscape);
        return world;
    }

    /**
     * Cherche une cellule de TERRE FERME (altitude ≥ 0) en spirale autour de
     * ({@code sx}, {@code sy}) — le terrain Perlin n'étant pas déterministe, les
     * tests qui exigent une cellule hors de l'eau passent par ici. Renvoie
     * {x, y}. Lève une AssertionError si tout est immergé (improbable).
     */
    public static int[] findLandCell(WorldOfCells world, int sx, int sy) {
        int w = world.getWidth(), h = world.getHeight();
        for (int r = 0; r < Math.max(w, h); r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dy = -r; dy <= r; dy++) {
                    int x = ((sx + dx) % w + w) % w;
                    int y = ((sy + dy) % h + h) % h;
                    if (world.getCellHeight(x, y) >= 0) return new int[]{x, y};
                }
            }
        }
        throw new AssertionError("aucune cellule de terre ferme trouvée");
    }
}
