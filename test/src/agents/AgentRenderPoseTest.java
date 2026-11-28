package agents;

import org.junit.jupiter.api.Test;
import worlds.WorldOfCells;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pose rendue d'un agent (Agent.updateRenderPose) : rendu par cases quand l'agent
 * est immobile ou que le lissage est coupé, position et altitude interpolées entre
 * deux cases sinon.
 */
class AgentRenderPoseTest {

    private static final float NH = 100f;   // normalizeHeight (non lu par computeAgentAltitude)

    /** Agent immobile = rendu d'aujourd'hui : case, altitude de la case, dernier déplacement. */
    @Test
    void agentImmobileEgaleRenduParCases() {
        WorldOfCells w = AgentTestSupport.buildWorld();
        int[] c = AgentTestSupport.findLandCell(w, 20, 20);
        Loup l = new Loup(c[0], c[1], w);
        l.setLastMove(1, 0);
        double[] u = l.getLastUnit();
        float alt = l.computeAgentAltitude(w, c[0], c[1], NH);

        for (boolean smooth : new boolean[] { true, false }) {
            for (double now : new double[] { -1.0, 0.0, 3.7, 50.25 }) {
                l.updateRenderPose(w, now, smooth, NH, 30.0);
                assertEquals(c[0], l.renderX(), 0.0, "x, smooth=" + smooth + " now=" + now);
                assertEquals(c[1], l.renderY(), 0.0, "y");
                assertEquals(alt, l.renderAltitude(w, NH), 0.0f, "altitude");
                assertEquals(u[0], l.renderHeadX(), 1e-12, "cap x");
                assertEquals(u[1], l.renderHeadY(), 1e-12, "cap y");
            }
        }
    }

    /** Ligne droite : à mi-tour, la pose est entre deux cases et l'altitude est
     *  l'interpolation des deux ; sans lissage, la case et son altitude. */
    @Test
    void ligneDroiteInterpoleePositionEtAltitude() {
        WorldOfCells w = AgentTestSupport.buildWorld();
        int W = w.getWidth(), H = w.getHeight();
        int y = 7;
        Loup l = new Loup(13, y, w);
        for (int k = 0; k < 4; k++) l.motion.record(10 + k, y, 3L * k, 3L * k + 3, W, H);
        l.setLastMove(1, 0);

        l.updateRenderPose(w, 10.5, true, NH, 30.0);   // u = 0,5 sur le dernier tour
        assertEquals(11.5, l.renderX(), 1e-9, "1 à 2 cases de retard, entre deux cases");
        assertEquals(y, l.renderY(), 1e-9);
        float a11 = l.computeAgentAltitude(w, 11, y, NH), a12 = l.computeAgentAltitude(w, 12, y, NH);
        assertEquals(0.5f * (a11 + a12), l.renderAltitude(w, NH), 1e-3f, "altitude bilinéaire");
        assertEquals(1.0, l.renderHeadX(), 1e-9, "cap le long de la ligne");

        l.updateRenderPose(w, 10.5, false, NH, 30.0);
        assertEquals(13, l.renderX(), 0.0, "interrupteur : case de la simulation");
        assertEquals(l.computeAgentAltitude(w, 13, y, NH), l.renderAltitude(w, NH), 0.0f);
    }

    /** Fenêtre décalée : même repli que l'ancien x2 entier, fraction comprise. */
    @Test
    void renderX2ReplieCommeLAncienX2() {
        WorldOfCells w = AgentTestSupport.buildWorld();
        int W = w.getWidth();
        Loup l = new Loup(3, 4, w);
        l.updateRenderPose(w, 0.0, false, NH, 30.0);
        for (int off : new int[] { 0, 2, 3, 10, W + 5, 3 * W - 1 }) {
            int old = 3 - (off % W);
            if (old < 0) old += W;
            assertEquals(old, l.renderX2(w, off), 0.0f, "offset " + off);
        }
    }
}
