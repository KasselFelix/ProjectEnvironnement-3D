package ui;

import javax.media.opengl.GL;
import javax.media.opengl.GL2;
import javax.media.opengl.glu.GLU;

import com.jogamp.opengl.util.gl2.GLUT;

/**
 * Aide au rendu d'overlay 2D au-dessus de la scène 3D, en OpenGL fixed-function.
 *
 * Usage type dans Landscape.display() :
 *
 * <pre>
 *   ui.begin2D(gl, width, height);
 *   ui.drawQuad(gl, 10, 10, 200, 50, 0f, 0f, 0f, 0.7f);
 *   ui.drawText(gl, 20, 30, "Hello", 1f, 1f, 1f);
 *   ui.end2D(gl);
 * </pre>
 *
 * Convention : coordonnées en pixels écran, origine en haut-gauche (Y vers le bas).
 */
public class UiRenderer {

    private final GLUT glut = new GLUT();
    private final GLU glu = new GLU();

    private boolean depthWasEnabled;
    private boolean lightingWasEnabled;
    private boolean blendWasEnabled;

    /** Bascule le pipeline en mode 2D ortho (0,w,h,0). Sauve l'état GL nécessaire. */
    public void begin2D(GL2 gl, int width, int height) {
        depthWasEnabled = gl.glIsEnabled(GL.GL_DEPTH_TEST);
        lightingWasEnabled = gl.glIsEnabled(GL2.GL_LIGHTING);
        blendWasEnabled = gl.glIsEnabled(GL.GL_BLEND);

        gl.glDisable(GL.GL_DEPTH_TEST);
        gl.glDisable(GL2.GL_LIGHTING);
        gl.glEnable(GL.GL_BLEND);
        gl.glBlendFunc(GL.GL_SRC_ALPHA, GL.GL_ONE_MINUS_SRC_ALPHA);

        gl.glMatrixMode(GL2.GL_PROJECTION);
        gl.glPushMatrix();
        gl.glLoadIdentity();
        glu.gluOrtho2D(0, width, height, 0);

        gl.glMatrixMode(GL2.GL_MODELVIEW);
        gl.glPushMatrix();
        gl.glLoadIdentity();
    }

    /** Restaure le pipeline 3D à l'identique de l'état avant begin2D(). */
    public void end2D(GL2 gl) {
        gl.glMatrixMode(GL2.GL_PROJECTION);
        gl.glPopMatrix();
        gl.glMatrixMode(GL2.GL_MODELVIEW);
        gl.glPopMatrix();

        if (depthWasEnabled) gl.glEnable(GL.GL_DEPTH_TEST);
        if (lightingWasEnabled) gl.glEnable(GL2.GL_LIGHTING);
        if (!blendWasEnabled) gl.glDisable(GL.GL_BLEND);
    }

    public void drawQuad(GL2 gl, float x, float y, float w, float h,
                         float r, float g, float b, float a) {
        gl.glColor4f(r, g, b, a);
        gl.glBegin(GL2.GL_QUADS);
        gl.glVertex2f(x,     y);
        gl.glVertex2f(x + w, y);
        gl.glVertex2f(x + w, y + h);
        gl.glVertex2f(x,     y + h);
        gl.glEnd();
    }

    public void drawLine(GL2 gl, float x0, float y0, float x1, float y1,
                         float r, float g, float b, float a) {
        gl.glColor4f(r, g, b, a);
        gl.glBegin(GL2.GL_LINES);
        gl.glVertex2f(x0, y0);
        gl.glVertex2f(x1, y1);
        gl.glEnd();
    }

    /** Cadre vide (bordure 1px) : 4 lignes formant un rectangle. */
    public void drawBorder(GL2 gl, float x, float y, float w, float h,
                           float r, float g, float b, float a) {
        drawLine(gl, x,     y,     x + w, y,     r, g, b, a);
        drawLine(gl, x + w, y,     x + w, y + h, r, g, b, a);
        drawLine(gl, x + w, y + h, x,     y + h, r, g, b, a);
        drawLine(gl, x,     y + h, x,     y,     r, g, b, a);
    }

    /**
     * Texte bitmap GLUT 12px Helvetica. Utilise glWindowPos qui contourne
     * complètement les matrices, donc on convertit notre Y top-down en Y bottom-up.
     */
    public void drawText(GL2 gl, int x, int y, int viewportHeight, String text,
                         float r, float g, float b) {
        gl.glColor3f(r, g, b);
        gl.glWindowPos2i(x, viewportHeight - y);
        glut.glutBitmapString(GLUT.BITMAP_HELVETICA_12, text);
    }

    /** Texte plus grand pour titres. */
    public void drawTitle(GL2 gl, int x, int y, int viewportHeight, String text,
                          float r, float g, float b) {
        gl.glColor3f(r, g, b);
        gl.glWindowPos2i(x, viewportHeight - y);
        glut.glutBitmapString(GLUT.BITMAP_HELVETICA_18, text);
    }

    /** Largeur en pixels d'une chaîne en police Helvetica 12. */
    public int textWidth(String text) {
        return glut.glutBitmapLength(GLUT.BITMAP_HELVETICA_12, text);
    }
}
