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
}
