package graphics;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Pyramide de vue (frustum culling des arbres). Calcul pur sur des matrices
 * OpenGL colonne-majeure → testable sans contexte GL.
 */
class FrustumTest {

    private static final double[] IDENTITY = {
        1, 0, 0, 0,
        0, 1, 0, 0,
        0, 0, 1, 0,
        0, 0, 0, 1 };

    /** gluPerspective(fovy, aspect, near, far) en colonne-majeure. */
    private static double[] perspective(double fovyDeg, double aspect, double near, double far) {
        double f = 1.0 / Math.tan(Math.toRadians(fovyDeg) / 2);
        return new double[] {
            f / aspect, 0, 0, 0,
            0, f, 0, 0,
            0, 0, (far + near) / (near - far), -1,
            0, 0, 2 * far * near / (near - far), 0 };
    }

    @Test
    void identiteDonneLeCubeUnite() {
        Frustum f = new Frustum();
        f.setFromMatrices(IDENTITY, IDENTITY);
        assertTrue(f.sphereVisible(0, 0, 0, 0.1));
        assertTrue(f.sphereVisible(1.5, 0, 0, 1.0), "sphère à cheval sur le plan droit");
        assertFalse(f.sphereVisible(5, 0, 0, 1.0), "entièrement à droite");
        assertFalse(f.sphereVisible(0, -5, 0, 1.0), "entièrement en dessous");
    }

    @Test
    void perspectiveRejetteCeQuiEstDerriereLaCamera() {
        Frustum f = new Frustum();
        f.setFromMatrices(perspective(60, 1.0, 1, 1000), IDENTITY);
        assertTrue(f.sphereVisible(0, 0, -50, 1), "devant la caméra (−Z)");
        assertFalse(f.sphereVisible(0, 0, 50, 1), "derrière la caméra");
        assertFalse(f.sphereVisible(200, 0, -50, 1), "loin sur le côté");
        assertTrue(f.sphereVisible(200, 0, -50, 200), "grosse sphère qui déborde dans le champ");
    }

    @Test
    void tientCompteDeLaModelview() {
        // Caméra translatée de −100 en X : un point en X=+100 se retrouve au centre.
        double[] mv = IDENTITY.clone();
        mv[12] = -100;
        Frustum f = new Frustum();
        f.setFromMatrices(perspective(60, 1.0, 1, 1000), mv);
        assertTrue(f.sphereVisible(100, 0, -50, 1));
        assertFalse(f.sphereVisible(-100, 0, -50, 1));
    }
}
