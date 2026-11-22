package graphics;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;

import javax.media.opengl.GL;
import javax.media.opengl.GL2;
import javax.media.opengl.fixedfunc.GLPointerFunc;

import com.jogamp.common.nio.Buffers;

/**
 * Maillage du terrain envoyé à OpenGL en un seul appel ({@code glDrawElements})
 * au lieu d'un {@code glVertex3f} par sommet.
 *
 * <p>Ce sont des <em>vertex arrays côté client</em> (OpenGL 1.1, pipeline fixe) :
 * pas de VBO, pas de shader. Les sommets forment une grille {@code vx × vy}
 * partagée par les cases voisines (un sommet sert à 4 quads), et la liste
 * d'indices décrit les quads dans le même ordre et le même sens de rotation
 * que l'ancien rendu immédiat : (x, y+1), (x+1, y+1), (x+1, y), (x, y).
 *
 * <p>Landscape remplit {@link #pos}, {@link #nrm} et {@link #tex} (3, 3 et 2
 * floats par sommet, sommet {@code vx * vyCount + vy}) ; les couleurs viennent
 * du cache de couleurs de sommet, qui a déjà cette disposition.
 */
final class TerrainMesh {

    float[] pos = new float[0];
    float[] nrm = new float[0];
    float[] tex = new float[0];

    private FloatBuffer posBuf, nrmBuf, texBuf, colBuf;
    private IntBuffer idxBuf;
    private int idxVx = -1, idxVy = -1, idxCount;

    /** Prépare les tableaux pour une grille de {@code vxCount × vyCount} sommets. */
    void ensureCapacity(int vxCount, int vyCount) {
        int n = vxCount * vyCount;
        if (pos.length < n * 3) {
            pos = new float[n * 3];
            nrm = new float[n * 3];
            tex = new float[n * 2];
            posBuf = Buffers.newDirectFloatBuffer(n * 3);
            nrmBuf = Buffers.newDirectFloatBuffer(n * 3);
            texBuf = Buffers.newDirectFloatBuffer(n * 2);
            colBuf = Buffers.newDirectFloatBuffer(n * 3);
        }
        if (vxCount != idxVx || vyCount != idxVy) buildIndices(vxCount, vyCount);
    }

    private void buildIndices(int vxCount, int vyCount) {
        idxVx = vxCount; idxVy = vyCount;
        idxCount = Math.max(0, (vxCount - 1) * (vyCount - 1) * 4);
        int[] idx = new int[idxCount];
        int k = 0;
        for (int x = 0; x < vxCount - 1; x++)
            for (int y = 0; y < vyCount - 1; y++) {
                idx[k++] = x * vyCount + (y + 1);
                idx[k++] = (x + 1) * vyCount + (y + 1);
                idx[k++] = (x + 1) * vyCount + y;
                idx[k++] = x * vyCount + y;
            }
        idxBuf = Buffers.newDirectIntBuffer(Math.max(1, idxCount));
        idxBuf.put(idx, 0, idxCount);
        idxBuf.rewind();
    }

    /**
     * Dessine les quads. {@code colors} : 3 floats par sommet, même indexation.
     * À la fin, la couleur et la normale courantes sont celles du dernier sommet
     * émis, comme après l'ancien {@code glBegin/glEnd} (les passes suivantes
     * peuvent en hériter).
     */
    void draw(GL2 gl, float[] colors, boolean withTex) {
        if (idxCount == 0) return;
        int n = idxVx * idxVy;
        fill(posBuf, pos, n * 3);
        fill(nrmBuf, nrm, n * 3);
        fill(colBuf, colors, n * 3);
        if (withTex) fill(texBuf, tex, n * 2);

        gl.glEnableClientState(GLPointerFunc.GL_VERTEX_ARRAY);
        gl.glEnableClientState(GLPointerFunc.GL_NORMAL_ARRAY);
        gl.glEnableClientState(GLPointerFunc.GL_COLOR_ARRAY);
        if (withTex) gl.glEnableClientState(GLPointerFunc.GL_TEXTURE_COORD_ARRAY);
        gl.glVertexPointer(3, GL.GL_FLOAT, 0, posBuf);
        gl.glNormalPointer(GL.GL_FLOAT, 0, nrmBuf);
        gl.glColorPointer(3, GL.GL_FLOAT, 0, colBuf);
        if (withTex) gl.glTexCoordPointer(2, GL.GL_FLOAT, 0, texBuf);

        gl.glDrawElements(GL2.GL_QUADS, idxCount, GL.GL_UNSIGNED_INT, idxBuf);

        if (withTex) gl.glDisableClientState(GLPointerFunc.GL_TEXTURE_COORD_ARRAY);
        gl.glDisableClientState(GLPointerFunc.GL_COLOR_ARRAY);
        gl.glDisableClientState(GLPointerFunc.GL_NORMAL_ARRAY);
        gl.glDisableClientState(GLPointerFunc.GL_VERTEX_ARRAY);

        // Le dernier sommet émis est le 4e coin du dernier quad : (vx-2, vy-2).
        int last = ((idxVx - 2) * idxVy + (idxVy - 2)) * 3;
        gl.glColor3f(colors[last], colors[last + 1], colors[last + 2]);
        gl.glNormal3f(nrm[last], nrm[last + 1], nrm[last + 2]);
    }

    private static void fill(FloatBuffer b, float[] src, int len) {
        b.clear();
        b.put(src, 0, len);
        b.rewind();
    }
}
