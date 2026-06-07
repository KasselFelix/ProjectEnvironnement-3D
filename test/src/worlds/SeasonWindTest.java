package worlds;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SeasonWindTest {
    @Test
    void ventPlusFortEnAutomneHiverQuEnEte() {
        assertTrue(Season.AUTUMN.windFactor > Season.SUMMER.windFactor, "automne plus venteux que l'été");
        assertTrue(Season.WINTER.windFactor > Season.SUMMER.windFactor, "hiver plus venteux que l'été");
        assertEquals(0.8, Season.SUMMER.windFactor, 1e-9, "été = saison la plus calme");
    }
}
