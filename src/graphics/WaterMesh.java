package graphics;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;

import javax.media.opengl.GL;
import javax.media.opengl.GL2;
import javax.media.opengl.fixedfunc.GLPointerFunc;

import com.jogamp.common.nio.Buffers;

/**
 * Surface de l'eau (passe 1b) envoyée en un seul {@code glDrawElements(GL_TRIANGLES)}
 * sur des vertex arrays côté client (OpenGL 1.1, pipeline fixe), au lieu d'un
 * {@code glColor4f} + {@code glVertex3f} par sommet de triangle.
 *
 * <p>La couleur d'un sommet ne dépend que du sommet (profondeur cumulée des 4
 * cases qui le partagent) : elle est calculée une fois par sommet de la grille
 * au lieu d'une fois par triangle qui l'utilise. Landscape remplit {@link #pos}
 * (3 floats), {@link #col} (4 floats) et la liste d'indices via {@link #add}, le
 * sommet {@code (vx, vy)} ayant l'indice {@code vx * vyCount + vy} comme dans
 * {@link TerrainMesh}.
 */
final class WaterMesh {

    float[] pos = new float[0];
    float[] col = new float[0];
    /** Hauteurs des cases de la fenêtre élargie d'une case de chaque côté. */
    double[] cellH = new double[0];

    private int[] idx = new int[0];
    private int idxCount;
    private FloatBuffer posBuf, colBuf;
    private IntBuffer idxBuf;

    /** Prépare une grille de {@code vxCount × vyCount} sommets et vide les indices. */
    void begin(int vxCount, int vyCount) {
        int n = vxCount * vyCount;
        if (pos.length < n * 3) {
            pos = new float[n * 3];
            col = new float[n * 4];
            posBuf = Buffers.newDirectFloatBuffer(n * 3);
            colBuf = Buffers.newDirectFloatBuffer(n * 4);
        }
        int cells = (vxCount + 1) * (vyCount + 1);
        if (cellH.length < cells) cellH = new double[cells];
        // Au plus 2 triangles par case.
        int maxIdx = Math.max(1, (vxCount - 1) * (vyCount - 1) * 6);
        if (idx.length < maxIdx) {
            idx = new int[maxIdx];
            idxBuf = Buffers.newDirectIntBuffer(maxIdx);
        }
        idxCount = 0;
    }

    /** Ajoute un sommet (indice de grille) à la liste de triangles. */
    void add(int v) {
        idx[idxCount++] = v;
    }

    /**
     * Dessine les triangles ajoutés, avec pour seuls tableaux position et couleur
     * (l'éclairage est coupé pendant l'eau). La couleur courante finale est celle
     * du dernier sommet émis, comme après l'ancien {@code glBegin/glEnd}.
     */
    void draw(GL2 gl, int vertexCount) {
        if (idxCount == 0) return;
        posBuf.clear(); posBuf.put(pos, 0, vertexCount * 3); posBuf.rewind();
        colBuf.clear(); colBuf.put(col, 0, vertexCount * 4); colBuf.rewind();
        idxBuf.clear(); idxBuf.put(idx, 0, idxCount); idxBuf.rewind();

        gl.glEnableClientState(GLPointerFunc.GL_VERTEX_ARRAY);
        gl.glEnableClientState(GLPointerFunc.GL_COLOR_ARRAY);
        gl.glVertexPointer(3, GL.GL_FLOAT, 0, posBuf);
        gl.glColorPointer(4, GL.GL_FLOAT, 0, colBuf);

        gl.glDrawElements(GL.GL_TRIANGLES, idxCount, GL.GL_UNSIGNED_INT, idxBuf);

        gl.glDisableClientState(GLPointerFunc.GL_COLOR_ARRAY);
        gl.glDisableClientState(GLPointerFunc.GL_VERTEX_ARRAY);

        int last = idx[idxCount - 1] * 4;
        gl.glColor4f(col[last], col[last + 1], col[last + 2], col[last + 3]);
    }
}
