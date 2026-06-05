package agents;

import landscapegenerator.PerlinNoiseLandscapeGenerator;
import org.junit.jupiter.api.Test;
import worlds.WorldOfCells;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests comportementaux du Loup.
 *
 * Stratégie : on instancie un vrai WorldOfCells avec nbloups/moutons/humains=0
 * pour éviter le spawn aléatoire d'agents, puis on ajoute manuellement les
 * agents de test à des positions contrôlées.
 *
 * Les agents.step() ne touchent pas à OpenGL (seul Landscape.display() le fait),
 * donc on peut exécuter step() sans contexte GL.
 */
class LoupTest {

    // Convention du projet (cf. graphics/Landscape.java:192,220) :
    // on génère un paysage (dxView, dyView) puis init le World avec (dxView-1, dyView-1)
    // car World.init lit landscape[x+1][y+1].
    private static final int DX_VIEW = 51;
    private static final int DY_VIEW = 51;
    private static final int DX = DX_VIEW - 1;
    private static final int DY = DY_VIEW - 1;

    private WorldOfCells buildWorld() {
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
     * C3 — Rééquilibrage du gain énergétique à la prédation.
     *
     * Attendu après correctif : un loup à énergie 100 qui mange un mouton
     * doit voir son énergie passer à 100 + energieD/2 = 600, et non à
     * energieD = 1000 (restauration totale, code original).
     *
     * Robustesse : on place un mouton à la position du loup ET sur les 4
     * cases cardinales adjacentes, donc quel que soit le mouvement du loup
     * dans son step(), il finira sur une case contenant un mouton et le mangera.
     */
    @Test
    void loupGainEnergieBornéEnMangeantMouton() {
        WorldOfCells world = buildWorld();
        int cx = 10, cy = 10;

        Loup loup = new Loup(cx, cy, world);
        loup.energie = 100;
        world.loups.add(loup);
        world.agents.add(loup);
        world.uniqueDynamicObjects.add(loup);

        int[][] positions = {
                {cx,     cy    },
                {cx,     cy - 1},
                {cx,     cy + 1},
                {cx - 1, cy    },
                {cx + 1, cy    }
        };
        Mouton[] moutons = new Mouton[positions.length];
        for (int i = 0; i < positions.length; i++) {
            moutons[i] = new Mouton(positions[i][0], positions[i][1], world);
            world.moutons.add(moutons[i]);
            world.agents.add(moutons[i]);
            world.uniqueDynamicObjects.add(moutons[i]);
        }

        loup.step();

        int dead = 0;
        for (Mouton m : moutons) if (!m._alive) dead++;
        assertEquals(1, dead,
                "Exactement un mouton doit être mangé par step (boucle break dans Loup.step).");

        // Après prédation, le bloc "mise à jour énergie" de Loup.step() décrémente
        // energie de 1 à 4 unités (base + eau + descente). On vérifie donc la borne :
        // C3 impose un gain plafonné à energieD/2, donc energie ≤ 100 + energieD/2,
        // alors que le code original ramène energie à energieD = 1000.
        int gainMaxAttendu = 100 + loup.energieD / 2;
        assertTrue(loup.energie > 100,
                "Le loup a dû gagner de l'énergie en mangeant le mouton (energie > 100 initial).");
        assertTrue(loup.energie <= gainMaxAttendu,
                "C3 : gain plafonné à energieD/2. Attendu energie ≤ "
                + gainMaxAttendu + ", observé " + loup.energie
                + " (probable restauration totale à energieD du code original).");
    }

    /**
     * Mort par vieillesse — le loup doit être marqué `_alive=false` quand son âge
     * dépasse `maxAgeDays`. Configuré très court (0.05 jour ≈ 200 itérations) pour
     * que le check d'âge déclenche bien AVANT toute autre cause possible de mort
     * (famine — l'énergie est aussi gonflée par sécurité).
     */
    @Test
    void loupMeurtParVieillesse() {
        WorldOfCells world = buildWorld();

        Loup loup = new Loup(10, 10, world);
        loup.maxAgeDays = 0.05;          // ≈ 200 ticks avec dureeJour=2000
        loup.energieD   = 1_000_000;     // empêche toute mort par famine
        loup.energie    = loup.energieD;
        world.loups.add(loup);
        world.agents.add(loup);
        world.uniqueDynamicObjects.add(loup);

        assertTrue(loup._alive, "Au démarrage le loup est vivant.");

        int targetIter = 500;            // ~2× plus que l'âge max pour absorber le jitter
        while (world.getIteration() < targetIter && loup._alive) {
            world.step();
        }

        assertFalse(loup._alive,
                "Après " + world.getIteration() + " ticks (age " + loup.getAgeDays()
                + " jours, max " + loup.maxAgeDays + "), le loup doit être mort.");
        assertTrue(loup.getAgeDays() > loup.maxAgeDays,
                "L'âge final " + loup.getAgeDays() + " ne dépasse pas maxAgeDays "
                + loup.maxAgeDays + " — le loup est mort d'autre chose.");
    }
}
