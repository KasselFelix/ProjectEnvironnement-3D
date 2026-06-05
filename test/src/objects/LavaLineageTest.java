package objects;

import cellularautomata.ecosystem.LavaSource;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LavaLineageTest {

    @Test
    void forSourceCreeUnBlocSource() {
        LavaSource s = LavaSource.forEruption(5, 5, 1, 0, 1.0f);
        LavaLineage l = LavaLineage.forSource(s);
        assertEquals(0, l.generation);
        assertEquals(-1, l.parentCellX);
        assertEquals(-1, l.parentCellY);
        assertTrue(l.isSource());
    }

    @Test
    void derivedIncrementeLaGeneration() {
        LavaSource s = LavaSource.forEruption(5, 5, 1, 0, 1.0f);
        LavaLineage l = LavaLineage.derived(s, 3, 4, 2);
        assertEquals(3, l.generation, "generation = parentGen + 1");
        assertEquals(3, l.parentCellX);
        assertEquals(4, l.parentCellY);
        assertFalse(l.isSource());
    }
}
