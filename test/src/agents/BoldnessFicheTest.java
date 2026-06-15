package agents;

import agents.ai.BoldnessTrait;
import landscapegenerator.PerlinNoiseLandscapeGenerator;
import org.junit.jupiter.api.Test;
import worlds.WorldOfCells;
import static org.junit.jupiter.api.Assertions.*;

/** La fiche agent montre l'axe boldness (sous-projet D). */
class BoldnessFicheTest {

    private WorldOfCells flatWorld() {
        WorldOfCells world = new WorldOfCells();
        world.nbloups = 0; world.nbmoutons = 0; world.nbhumains = 0; world.nbours = 0;
        double[][] ls = PerlinNoiseLandscapeGenerator.generatePerlinNoiseLandscape(51, 51, 0.7, 0.4, 4);
        world.init(50, 50, ls);
        for (int x = 0; x < world.getWidth(); x++)
            for (int y = 0; y < world.getHeight(); y++) world.setCellHeight(x, y, 5.0);
        world.setWindEnabled(true); world.setWindVector(0.0, 0.0); world.setRaining(false);
        return world;
    }

    @Test
    void ficheAfficheTemeraire() {
        WorldOfCells w = flatWorld();
        Mouton m = new Mouton(10, 10, w);
        m.character.setBoldness(BoldnessTrait.BOLD);
        assertEquals("TEMERAIRE", m.boldnessLabel());
        boolean trouve = false;
        for (String line : m.evolutionSummary())
            if (line.startsWith("Caractere") && line.contains("TEMERAIRE")) trouve = true;
        assertTrue(trouve, "la ligne Caractere doit montrer le trait boldness");
    }

    @Test
    void ficheNoneTiret() {
        WorldOfCells w = flatWorld();
        Mouton m = new Mouton(10, 10, w);
        assertEquals("-", m.boldnessLabel());
    }
}
