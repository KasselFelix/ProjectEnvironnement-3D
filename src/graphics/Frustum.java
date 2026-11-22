package graphics;

import javax.media.opengl.GL2;

/**
 * Pyramide de vue de la caméra courante, pour ne pas envoyer à OpenGL des
 * objets entièrement hors de l'écran (frustum culling).
 *
 * <p>Les 6 plans sont extraits de {@code PROJECTION × MODELVIEW} (méthode de
 * Gribb &amp; Hartmann), dans le repère où l'objet est dessiné : il faut donc
 * appeler {@link #update(GL2)} avec la même matrice MODELVIEW que celle qui
 * précède les {@code glTranslatef} des objets testés. Marche aussi pour la
 * projection orthographique de la vue de dessus.
 *
 * <p>Le test est conservatif : un objet n'est rejeté que si sa sphère
 * englobante est entièrement derrière un plan. Aucun objet visible n'est perdu.
 */
public final class Frustum {

    /** 6 plans × (a, b, c, d), normalisés : distance signée = a·x + b·y + c·z + d. */
    private final double[] planes = new double[24];
    private final double[] mv = new double[16];
    private final double[] proj = new double[16];
    private final double[] m = new double[16];

    /** Relit les matrices courantes de {@code gl} et recalcule les plans. */
    public void update(GL2 gl) {
        gl.glGetDoublev(GL2.GL_MODELVIEW_MATRIX, mv, 0);
        gl.glGetDoublev(GL2.GL_PROJECTION_MATRIX, proj, 0);
        setFromMatrices(proj, mv);
    }

    /** Calcule les plans depuis des matrices OpenGL (colonne-majeure). */
    void setFromMatrices(double[] p, double[] v) {
        // m = p × v (colonne-majeure : m[col*4 + row])
        for (int c = 0; c < 4; c++)
            for (int r = 0; r < 4; r++) {
                double s = 0;
                for (int k = 0; k < 4; k++) s += p[k * 4 + r] * v[c * 4 + k];
                m[c * 4 + r] = s;
            }
        // Ligne r de m : (m[r], m[4+r], m[8+r], m[12+r]).
        // gauche = L3 + L0, droite = L3 − L0, bas = L3 + L1, haut = L3 − L1,
        // proche = L3 + L2, loin = L3 − L2.
        int i = 0;
        for (int row = 0; row < 3; row++) {
            for (int sign = 1; sign >= -1; sign -= 2) {
                double a = m[3]  + sign * m[row];
                double b = m[7]  + sign * m[4 + row];
                double c = m[11] + sign * m[8 + row];
                double d = m[15] + sign * m[12 + row];
                double n = Math.sqrt(a * a + b * b + c * c);
                if (n == 0) n = 1;
                planes[i++] = a / n; planes[i++] = b / n; planes[i++] = c / n; planes[i++] = d / n;
            }
        }
    }

    /** Vrai si la sphère (centre, rayon) touche au moins en partie la pyramide de vue. */
    public boolean sphereVisible(double x, double y, double z, double radius) {
        for (int i = 0; i < 24; i += 4) {
            if (planes[i] * x + planes[i + 1] * y + planes[i + 2] * z + planes[i + 3] < -radius)
                return false;
        }
        return true;
    }
}
