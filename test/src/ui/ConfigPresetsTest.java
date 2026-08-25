package ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Presets de configuration JSON (V7) : round-trip de sérialisation et
 * application partielle. Pas de dépendance OpenGL.
 */
class ConfigPresetsTest {

    @Test
    void roundTripPreserveLesChamps() {
        SimulationConfig a = new SimulationConfig();
        a.nbLoups = 7;
        a.nbOurs = 3;
        a.humainChasseur = 1;
        a.seasonLengthDays = 5;
        a.loupVision = 17;
        a.cycleTotalSec = 333f;
        a.moutonMaxAgeDays = 42.5;

        String json = ConfigPresets.toJson(a);

        SimulationConfig b = new SimulationConfig();
        ConfigPresets.applyJson(json, b);

        assertEquals(7, b.nbLoups);
        assertEquals(3, b.nbOurs);
        assertEquals(1, b.humainChasseur);
        assertEquals(5, b.seasonLengthDays);
        assertEquals(17, b.loupVision);
        assertEquals(333f, b.cycleTotalSec, 1e-4);
        assertEquals(42.5, b.moutonMaxAgeDays, 1e-9);
    }

    @Test
    void champsAbsentsGardentLeurValeur() {
        SimulationConfig b = new SimulationConfig();
        int defautMoutons = b.nbMoutons;
        ConfigPresets.applyJson("{ \"nbLoups\": 99 }", b);
        assertEquals(99, b.nbLoups, "le champ présent est appliqué");
        assertEquals(defautMoutons, b.nbMoutons, "un champ absent garde sa valeur par défaut");
    }

    @Test
    void jsonProduitEstReparsable() {
        SimulationConfig a = new SimulationConfig();
        Object parsed = loader.MiniJson.parse(ConfigPresets.toJson(a));
        assertTrue(parsed instanceof java.util.Map, "le JSON produit est un objet valide");
    }
}
