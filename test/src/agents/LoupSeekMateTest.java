package agents;

import agents.ai.AgentState;
import agents.ai.Percept;
import agents.ai.Perception;
import landscapegenerator.PerlinNoiseLandscapeGenerator;
import org.junit.jupiter.api.Test;
import scent.ScentKind;
import worlds.WorldOfCells;
import static org.junit.jupiter.api.Assertions.*;

/** Le loup repu en rut piste l'odeur d'un partenaire (sous-projet E). */
class LoupSeekMateTest {

    private WorldOfCells flatWorld() {
        WorldOfCells w = new WorldOfCells();
        w.nbloups = 0; w.nbmoutons = 0; w.nbhumains = 0; w.nbours = 0;
        double[][] ls = PerlinNoiseLandscapeGenerator.generatePerlinNoiseLandscape(51, 51, 0.7, 0.4, 4);
        w.init(50, 50, ls);
        for (int x = 0; x < w.getWidth(); x++)
            for (int y = 0; y < w.getHeight(); y++) w.setCellHeight(x, y, 5.0);
        w.setWindEnabled(true); w.setWindVector(0.0, 0.0); w.setRaining(false);
        return w;
    }

    private void setSeason(WorldOfCells w, int idx) {
        w.setDureeJour(1); w.setSeasonLengthDays(1); w.setIteration(idx * 2);
    }

    /** Pose une odeur de séduction LOUP sur la sonde Sud du loup (acuité 1.0 → 4 cases). */
    private Percept senseWithMateScent(Loup self, WorldOfCells w) {
        w.getScentField().emit(4242, ScentKind.LOUP, -1, self.x, self.y + 4, w.getIteration(), 1.3f, true);
        return Perception.sense(self, w, w.humains, w.moutons);
    }

    @Test
    void loupReepuEnHiverChercheUnPartenaire() {
        WorldOfCells w = flatWorld();
        setSeason(w, 3);                          // WINTER
        Loup l = new Loup(10, 10, w); l.energie = l.energieD;   // repu → !affamé, en rut
        w.loups.add(l); w.agents.add(l); w.uniqueDynamicObjects.add(l);
        assertEquals(AgentState.SEEK_MATE, l.decideState(senseWithMateScent(l, w)),
                "loup repu en rut + odeur partenaire → SEEK_MATE");
    }

    @Test
    void loupHorsSaisonNeCherchePas() {
        WorldOfCells w = flatWorld();
        setSeason(w, 1);                          // SUMMER (hors saison)
        Loup l = new Loup(10, 10, w); l.energie = l.energieD;
        w.loups.add(l); w.agents.add(l); w.uniqueDynamicObjects.add(l);
        assertNotEquals(AgentState.SEEK_MATE, l.decideState(senseWithMateScent(l, w)),
                "hors saison → pas de SEEK_MATE");
    }

    @Test
    void loupAffameChasseAvantDeCourtiser() {
        WorldOfCells w = flatWorld();
        setSeason(w, 3);                          // WINTER
        Loup l = new Loup(10, 10, w); l.energie = (int) (l.energieD * 0.3);   // affamé
        w.loups.add(l); w.agents.add(l); w.uniqueDynamicObjects.add(l);
        assertNotEquals(AgentState.SEEK_MATE, l.decideState(senseWithMateScent(l, w)),
                "un loup affamé ne courtise pas (la faim prime)");
    }

    @Test
    void seekMateEngageUnCapVersLePartenaire() {
        WorldOfCells w = flatWorld();
        setSeason(w, 3);
        Loup l = new Loup(10, 10, w); l.energie = l.energieD;
        w.loups.add(l); w.agents.add(l); w.uniqueDynamicObjects.add(l);
        Percept p = senseWithMateScent(l, w);
        l.applyState(AgentState.SEEK_MATE, p);
        assertTrue(l.mateCommitLeft > 0, "un cap partenaire est engagé");
        assertTrue(l.mateWpY > l.y, "le waypoint partenaire est au Sud (vers l'odeur)");
    }

    /** M1 : decideState (qui appelle resetPursuit chaque tick hors-chasse) ne doit
     *  PAS effacer l'engagement de cap partenaire — sinon le loup ré-projette son
     *  waypoint à chaque tick (anti-oscillation défait, contrairement au mouton). */
    @Test
    void decideStateNeReinitialisePasLEngagementPartenaire() {
        WorldOfCells w = flatWorld();
        setSeason(w, 3);                          // WINTER
        Loup l = new Loup(10, 10, w); l.energie = l.energieD;
        w.loups.add(l); w.agents.add(l); w.uniqueDynamicObjects.add(l);
        Percept p = senseWithMateScent(l, w);
        assertEquals(AgentState.SEEK_MATE, l.decideState(p));
        l.applyState(AgentState.SEEK_MATE, p);   // engage un cap stable
        int commit = l.mateCommitLeft, wpX = l.mateWpX, wpY = l.mateWpY;
        assertTrue(commit > 0, "cap engagé");
        // Tick suivant : decideState seul (comme la boucle réelle) ne doit rien effacer.
        l.decideState(senseWithMateScent(l, w));
        assertEquals(commit, l.mateCommitLeft, "decideState ne réinitialise pas mateCommitLeft");
        assertEquals(wpX, l.mateWpX, "waypoint partenaire conservé (X)");
        assertEquals(wpY, l.mateWpY, "waypoint partenaire conservé (Y)");
    }

    /** M1 : le détecteur de blocage BFS du pistage partenaire est DÉDIÉ (mateStuck)
     *  et survit au resetPursuit() per-tick — il s'accumule quand la distance au
     *  waypoint ne s'améliore pas (ici l'agent ne bouge pas faute de world.step). */
    @Test
    void seekMateAccumuleLeBlocageMalgreResetPursuit() {
        WorldOfCells w = flatWorld();
        setSeason(w, 3);
        Loup l = new Loup(10, 10, w); l.energie = l.energieD;
        w.loups.add(l); w.agents.add(l); w.uniqueDynamicObjects.add(l);
        for (int t = 0; t < 5; t++) {            // boucle réelle : decideState puis applyState
            Percept p = senseWithMateScent(l, w);
            l.decideState(p);                    // appelle resetPursuit (hors-chasse)
            l.applyState(AgentState.SEEK_MATE, p);
        }
        assertTrue(l.mateStuck > 0, "le blocage partenaire s'accumule (isolé de resetPursuit)");
    }
}
