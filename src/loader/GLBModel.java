package loader;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.imageio.ImageIO;
import javax.media.opengl.GL2;
import javax.media.opengl.GLProfile;

import com.jogamp.opengl.util.texture.Texture;
import com.jogamp.opengl.util.texture.awt.AWTTextureIO;

/**
 * Loader glTF binaire (.glb) minimal — couvre tous les assets statiques du
 * projet : meshes texturés issus de modddif / Hunyuan3D (baseColorTexture PBR)
 * ET meshes à couleurs plates exportés depuis Blender (baseColorFactor, ex.
 * l'arbre tronc+feuillage). Gère plusieurs primitives par mesh (glTF crée une
 * primitive par matériau), positions/normales/UV (UV optionnelles) et les
 * index U8/U16/U32. Pas d'animation ni de skinning.
 *
 * <p>Pourquoi un loader maison plutôt que jglTF : pour rester sur les 8 jars
 * JOGL essentiels (cf. {@code CLAUDE.md} — jglTF ajouterait plusieurs MB de
 * jars hors-pile). On ne dépend que du JDK + {@link AWTTextureIO}.
 *
 * <p>API : précompile une display list dans le constructeur, expose
 * {@link #opengldraw(GL2)} pour rejouer, et publie les bornes du bbox global
 * pour le placement.
 */
public final class GLBModel {

    public float toppoint, bottompoint;
    public float leftpoint, rightpoint;
    public float farpoint, nearpoint;

    private int objectList = -1;
    /** Émission de base du matériau (0..1). Appliquée dynamiquement dans
     *  {@link #opengldraw(GL2, float)} et modulable par le facteur jour/nuit —
     *  PAS figée dans la display list, sinon l'agent brillerait la nuit. */
    private float emissionLevel = 0f;

    /** Données décodées d'une primitive (un matériau). */
    private static final class Prim {
        float[] positions;   // x0,y0,z0, x1,...
        float[] normals;     // peut être null
        float[] uvs;         // peut être null
        int[]   indices;
        float[] baseColor = { 1f, 1f, 1f, 1f }; // baseColorFactor (défaut blanc)
        Texture texture;     // baseColorTexture, ou null si couleur plate
    }

    public GLBModel(String glbPath, boolean centerit, GL2 gl) throws IOException {
        this(glbPath, centerit, gl, 0.0f);
    }

    /**
     * @param emissionLevel émission de base appliquée au matériau (0..1). Relève
     *   le plancher de luminosité pour les meshes clairs (laine du mouton) qui
     *   seraient sinon trop sombres sur les faces à l'ombre. Mettre 0 pour les
     *   meshes à couleur plate déjà bien lisibles (arbre) afin de ne pas les
     *   délaver.
     */
    public GLBModel(String glbPath, boolean centerit, GL2 gl, float emissionLevel) throws IOException {
        this.emissionLevel = emissionLevel;
        byte[] raw = readAll(glbPath);
        ByteBuffer buf = ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN);
        int magic = buf.getInt();
        if (magic != 0x46546C67) throw new IOException("GLB: magic invalide (0x" + Integer.toHexString(magic) + ")");
        buf.getInt(); // version
        buf.getInt(); // totalLen

        int jsonLen = buf.getInt();
        int jsonType = buf.getInt();
        if (jsonType != 0x4E4F534A) throw new IOException("GLB: chunk 0 != JSON");
        byte[] jsonBytes = new byte[jsonLen];
        buf.get(jsonBytes);

        int binLen = buf.getInt();
        int binType = buf.getInt();
        if (binType != 0x004E4942) throw new IOException("GLB: chunk 1 != BIN");
        int binStart = buf.position();
        // Les byteOffset glTF sont relatifs à la BIN chunk : on la copie dans un
        // tableau dédié pour indexer à partir de 0.
        byte[] bin = new byte[binLen];
        System.arraycopy(raw, binStart, bin, 0, binLen);

        @SuppressWarnings("unchecked")
        Map<String,Object> root = (Map<String,Object>) MiniJson.parse(new String(jsonBytes, "UTF-8"));

        List<Object> bufferViews = listOf(root, "bufferViews");
        List<Object> accessors   = listOf(root, "accessors");
        List<Object> meshes      = listOf(root, "meshes");
        List<Object> materials   = listOf(root, "materials");
        List<Object> textures    = listOf(root, "textures");
        List<Object> images      = listOf(root, "images");

        @SuppressWarnings("unchecked")
        Map<String,Object> mesh = (Map<String,Object>) meshes.get(0);
        @SuppressWarnings("unchecked")
        List<Object> primitives = (List<Object>) mesh.get("primitives");

        // Décodage de toutes les primitives.
        List<Prim> prims = new ArrayList<Prim>();
        for (Object po : primitives) {
            @SuppressWarnings("unchecked")
            Map<String,Object> prim = (Map<String,Object>) po;
            @SuppressWarnings("unchecked")
            Map<String,Object> attrs = (Map<String,Object>) prim.get("attributes");

            Prim p = new Prim();
            int posAcc = ((Number) attrs.get("POSITION")).intValue();
            int nrmAcc = attrs.containsKey("NORMAL")     ? ((Number) attrs.get("NORMAL")).intValue()     : -1;
            int uvAcc  = attrs.containsKey("TEXCOORD_0") ? ((Number) attrs.get("TEXCOORD_0")).intValue() : -1;
            int idxAcc = ((Number) prim.get("indices")).intValue();
            int matIdx = prim.containsKey("material") ? ((Number) prim.get("material")).intValue() : -1;

            p.positions = readVec3(accessors, bufferViews, bin, posAcc);
            p.normals   = (nrmAcc >= 0) ? readVec3(accessors, bufferViews, bin, nrmAcc) : null;
            p.uvs       = (uvAcc >= 0)  ? readVec2(accessors, bufferViews, bin, uvAcc)  : null;
            p.indices   = readIndices(accessors, bufferViews, bin, idxAcc);

            // Matériau : baseColorTexture (prioritaire) sinon baseColorFactor.
            if (matIdx >= 0 && materials != null) {
                @SuppressWarnings("unchecked")
                Map<String,Object> mat = (Map<String,Object>) materials.get(matIdx);
                @SuppressWarnings("unchecked")
                Map<String,Object> pbr = (Map<String,Object>) mat.get("pbrMetallicRoughness");
                if (pbr != null) {
                    if (pbr.containsKey("baseColorFactor")) {
                        @SuppressWarnings("unchecked")
                        List<Object> bc = (List<Object>) pbr.get("baseColorFactor");
                        for (int k = 0; k < 4 && k < bc.size(); k++)
                            p.baseColor[k] = ((Number) bc.get(k)).floatValue();
                    }
                    if (pbr.containsKey("baseColorTexture")) {
                        p.texture = loadTexture(pbr, textures, images, bufferViews, raw, binStart, gl);
                    }
                }
            }
            prims.add(p);
        }

        // bbox global sur toutes les primitives.
        boolean first = true;
        for (Prim p : prims) {
            for (int i = 0; i < p.positions.length; i += 3) {
                float x = p.positions[i], y = p.positions[i+1], z = p.positions[i+2];
                if (first) { leftpoint=rightpoint=x; bottompoint=toppoint=y; farpoint=nearpoint=z; first=false; }
                if (x < leftpoint) leftpoint = x; if (x > rightpoint) rightpoint = x;
                if (y < bottompoint) bottompoint = y; if (y > toppoint) toppoint = y;
                if (z < farpoint) farpoint = z; if (z > nearpoint) nearpoint = z;
            }
        }
        if (centerit) {
            float cx = (leftpoint + rightpoint) * 0.5f;
            float cy = (bottompoint + toppoint) * 0.5f;
            float cz = (farpoint + nearpoint) * 0.5f;
            for (Prim p : prims)
                for (int i = 0; i < p.positions.length; i += 3) {
                    p.positions[i] -= cx; p.positions[i+1] -= cy; p.positions[i+2] -= cz;
                }
            leftpoint -= cx; rightpoint -= cx;
            bottompoint -= cy; toppoint -= cy;
            farpoint -= cz; nearpoint -= cz;
        }

        // === Display list ===
        objectList = gl.glGenLists(1);
        gl.glNewList(objectList, GL2.GL_COMPILE);

        // CRITIQUE : la scène globale fait glCullFace(GL_FRONT) (adapté au winding
        // du terrain). Nos GLB ont un winding standard glTF (faces avant = CCW =
        // GL_FRONT) — avec le culling FRONT global on verrait l'INTÉRIEUR de la
        // coquille. On bascule en cull BACK le temps du modèle, puis on restaure.
        gl.glCullFace(GL2.GL_BACK);
        // Two-sided : robustesse aux normales localement incohérentes des meshes
        // image-to-3D (faces à NdotL<0 sinon noires).
        gl.glLightModeli(GL2.GL_LIGHT_MODEL_TWO_SIDE, 1);

        // NB : l'émission n'est PAS posée ici (display list) mais dans opengldraw,
        // pour pouvoir la moduler par le facteur jour/nuit à chaque frame.
        gl.glEnable(GL2.GL_COLOR_MATERIAL);
        gl.glTexEnvi(GL2.GL_TEXTURE_ENV, GL2.GL_TEXTURE_ENV_MODE, GL2.GL_MODULATE);

        for (Prim p : prims) {
            if (p.texture != null) {
                gl.glEnable(GL2.GL_TEXTURE_2D);
                p.texture.bind(gl);
                gl.glColor4f(1f, 1f, 1f, 1f); // texture non teintée, modulée par l'éclairage
            } else {
                gl.glDisable(GL2.GL_TEXTURE_2D);
                gl.glColor4f(p.baseColor[0], p.baseColor[1], p.baseColor[2], p.baseColor[3]);
            }
            gl.glBegin(GL2.GL_TRIANGLES);
            for (int t = 0; t < p.indices.length; t++) {
                int v = p.indices[t];
                if (p.normals != null) gl.glNormal3f(p.normals[v*3], p.normals[v*3+1], p.normals[v*3+2]);
                // AWTTextureIO uploade ligne 0 en haut → t=0 = haut de l'image, comme
                // la convention glTF (V=0 en haut). On émet V sans flip.
                if (p.uvs != null)     gl.glTexCoord2f(p.uvs[v*2], p.uvs[v*2+1]);
                gl.glVertex3f(p.positions[v*3], p.positions[v*3+1], p.positions[v*3+2]);
            }
            gl.glEnd();
        }

        // Reset des états globaux pour ne pas contaminer le terrain rendu ensuite.
        // (l'émission est gérée hors display list, dans opengldraw.)
        gl.glDisable(GL2.GL_TEXTURE_2D);
        gl.glLightModeli(GL2.GL_LIGHT_MODEL_TWO_SIDE, 0);
        gl.glCullFace(GL2.GL_FRONT);
        gl.glEndList();

        int totalTris = 0, totalVerts = 0;
        for (Prim p : prims) { totalTris += p.indices.length/3; totalVerts += p.positions.length/3; }
        System.out.println("[GLBModel] " + glbPath + " : " + prims.size() + " prim(s), "
                + totalTris + " triangles, " + totalVerts + " vertices, bbox X[" + leftpoint + ","
                + rightpoint + "] Y[" + bottompoint + "," + toppoint + "] Z[" + farpoint + "," + nearpoint + "]");
    }

    public void opengldraw(GL2 gl) {
        opengldraw(gl, 1f);
    }

    /**
     * @param emissionScale multiplie l'émission de base (0..1). Passer
     *   {@code 1 - nightFactor} depuis le rendu pour que l'agent ne brille plus
     *   la nuit (sinon il reste auto-éclairé alors que la scène est sombre).
     */
    public void opengldraw(GL2 gl, float emissionScale) {
        float e = emissionLevel * emissionScale;
        if (e > 0f) {
            float[] em = { e, e * 0.97f, e * 0.9f, 1f };
            gl.glMaterialfv(GL2.GL_FRONT_AND_BACK, GL2.GL_EMISSION, em, 0);
        }
        gl.glCallList(objectList);
        if (e > 0f) {
            float[] noEm = { 0f, 0f, 0f, 1f };
            gl.glMaterialfv(GL2.GL_FRONT_AND_BACK, GL2.GL_EMISSION, noEm, 0);
        }
        gl.glDisable(GL2.GL_TEXTURE_2D);
        gl.glDisable(GL2.GL_COLOR_MATERIAL);
    }

    public float getXWidth()  { return rightpoint - leftpoint; }
    public float getYHeight() { return toppoint - bottompoint; }
    public float getZDepth()  { return nearpoint - farpoint; }

    // === Helpers internes ===

    /** Charge la baseColorTexture d'un bloc pbrMetallicRoughness en Texture JOGL. */
    private static Texture loadTexture(Map<String,Object> pbr, List<Object> textures, List<Object> images,
            List<Object> bufferViews, byte[] raw, int binStart, GL2 gl) {
        try {
            @SuppressWarnings("unchecked")
            Map<String,Object> btex = (Map<String,Object>) pbr.get("baseColorTexture");
            int texIdx = ((Number) btex.get("index")).intValue();
            @SuppressWarnings("unchecked")
            Map<String,Object> tex = (Map<String,Object>) textures.get(texIdx);
            int imgIdx = ((Number) tex.get("source")).intValue();
            @SuppressWarnings("unchecked")
            Map<String,Object> img = (Map<String,Object>) images.get(imgIdx);
            int imgBV = ((Number) img.get("bufferView")).intValue();
            @SuppressWarnings("unchecked")
            Map<String,Object> bv = (Map<String,Object>) bufferViews.get(imgBV);
            int bvOffset = bv.containsKey("byteOffset") ? ((Number) bv.get("byteOffset")).intValue() : 0;
            int bvLength = ((Number) bv.get("byteLength")).intValue();
            byte[] png = new byte[bvLength];
            System.arraycopy(raw, binStart + bvOffset, png, 0, bvLength);
            try (InputStream is = new ByteArrayInputStream(png)) {
                BufferedImage bi = ImageIO.read(is);
                if (bi == null) return null;
                Texture t = AWTTextureIO.newTexture(GLProfile.getDefault(), bi, true);
                t.setTexParameteri(gl, GL2.GL_TEXTURE_MIN_FILTER, GL2.GL_LINEAR_MIPMAP_LINEAR);
                t.setTexParameteri(gl, GL2.GL_TEXTURE_MAG_FILTER, GL2.GL_LINEAR);
                t.setTexParameteri(gl, GL2.GL_TEXTURE_WRAP_S, GL2.GL_REPEAT);
                t.setTexParameteri(gl, GL2.GL_TEXTURE_WRAP_T, GL2.GL_REPEAT);
                System.out.println("[GLBModel] texture diffuse OK (id=" + t.getTextureObject(gl)
                        + ", " + bi.getWidth() + "x" + bi.getHeight() + ")");
                return t;
            }
        } catch (Exception ex) {
            System.out.println("[GLBModel] échec chargement texture: " + ex.getMessage());
            return null;
        }
    }

    private static byte[] readAll(String path) throws IOException {
        File f = new File(path);
        byte[] out = new byte[(int) f.length()];
        try (FileInputStream in = new FileInputStream(f)) {
            int off = 0, n;
            while (off < out.length && (n = in.read(out, off, out.length - off)) > 0) off += n;
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    private static List<Object> listOf(Map<String,Object> root, String key) {
        Object v = root.get(key);
        return (v == null) ? null : (List<Object>) v;
    }

    /** Offset absolu dans le tableau BIN pour un accessor. */
    private static int baseOf(List<Object> accessors, List<Object> bvs, int accIdx) {
        @SuppressWarnings("unchecked")
        Map<String,Object> acc = (Map<String,Object>) accessors.get(accIdx);
        int bvIdx = ((Number) acc.get("bufferView")).intValue();
        int accOffset = acc.containsKey("byteOffset") ? ((Number) acc.get("byteOffset")).intValue() : 0;
        @SuppressWarnings("unchecked")
        Map<String,Object> bv = (Map<String,Object>) bvs.get(bvIdx);
        int bvOffset = bv.containsKey("byteOffset") ? ((Number) bv.get("byteOffset")).intValue() : 0;
        return bvOffset + accOffset;
    }

    private static int countOf(List<Object> accessors, int accIdx) {
        @SuppressWarnings("unchecked")
        Map<String,Object> acc = (Map<String,Object>) accessors.get(accIdx);
        return ((Number) acc.get("count")).intValue();
    }

    private static float[] readVec3(List<Object> accessors, List<Object> bvs, byte[] bin, int accIdx) {
        int count = countOf(accessors, accIdx);
        int base = baseOf(accessors, bvs, accIdx);
        ByteBuffer view = ByteBuffer.wrap(bin, base, count * 12).order(ByteOrder.LITTLE_ENDIAN);
        float[] out = new float[count * 3];
        for (int i = 0; i < out.length; i++) out[i] = view.getFloat();
        return out;
    }

    private static float[] readVec2(List<Object> accessors, List<Object> bvs, byte[] bin, int accIdx) {
        int count = countOf(accessors, accIdx);
        int base = baseOf(accessors, bvs, accIdx);
        ByteBuffer view = ByteBuffer.wrap(bin, base, count * 8).order(ByteOrder.LITTLE_ENDIAN);
        float[] out = new float[count * 2];
        for (int i = 0; i < out.length; i++) out[i] = view.getFloat();
        return out;
    }

    /** Index UNSIGNED_BYTE/SHORT/INT → int[]. */
    private static int[] readIndices(List<Object> accessors, List<Object> bvs, byte[] bin, int accIdx) {
        @SuppressWarnings("unchecked")
        Map<String,Object> acc = (Map<String,Object>) accessors.get(accIdx);
        int comp = ((Number) acc.get("componentType")).intValue();
        int count = countOf(accessors, accIdx);
        int base = baseOf(accessors, bvs, accIdx);
        int[] out = new int[count];
        if (comp == 5121) {
            for (int i = 0; i < count; i++) out[i] = bin[base + i] & 0xFF;
        } else if (comp == 5123) {
            ByteBuffer v = ByteBuffer.wrap(bin, base, count * 2).order(ByteOrder.LITTLE_ENDIAN);
            for (int i = 0; i < count; i++) out[i] = v.getShort() & 0xFFFF;
        } else if (comp == 5125) {
            ByteBuffer v = ByteBuffer.wrap(bin, base, count * 4).order(ByteOrder.LITTLE_ENDIAN);
            for (int i = 0; i < count; i++) out[i] = v.getInt();
        } else {
            throw new IllegalStateException("GLB: componentType d'indices non géré: " + comp);
        }
        return out;
    }
}
