package objects.dynamic;

import javax.media.opengl.GL2;

import objects.UniqueDynamicObject;
import worlds.World;

/**
 * Arbre projeté par une éruption ou poussé par une coulée de lave.
 *
 * Deux phases :
 *  1. VOL — trajectoire balistique (arc parabolique) entre la source et une
 *     cellule cible. L'arbre tournoie pendant la chute (rotation 0 → 90°
 *     autour de l'axe horizontal perpendiculaire au déplacement) pour finir
 *     couché à l'impact.
 *  2. AU_SOL — l'arbre reste couché, incliné selon la courbure du terrain
 *     (différence d'altitude entre cellule base et cellule tête). Son
 *     feuillage et son tronc changent de couleur progressivement
 *     (vert → orange → noir pour le feuillage, brun → carbonisé pour le tronc).
 *     Disparaît au bout de `GROUND_BURN_TICKS`.
 *
 * Rendu inline dans `glBegin(GL_QUADS)` ouvert par Landscape :
 *  - Tronc : cylindre 8 facettes (8 quads autour de l'axe vertical local)
 *  - Feuillage : cône 8 facettes (8 quads dégénérés = triangles : base→pointe)
 *
 * Vertices manuellement transformés (translation + rotation Rodrigues autour
 * d'un axe horizontal) sans glMatrix (interdit entre glBegin/glEnd).
 */
public class TreeProjectile extends UniqueDynamicObject {

    // ── Géométrie (unités world, alignées sur tree.obj) ──────────────────────
    // Hauteurs en unités world (Z natif, pas scalé — comme Tree.displayTreeAt qui
    // utilise glScalef(scaleXY, scaleXY, 1.0f)). Alignées sur ~10 unités du modèle OBJ.
    private static final float TRUNK_HEIGHT      = 4.0f;
    private static final float FOLIAGE_BASE_Z    = 2.5f;
    private static final float FOLIAGE_TOP_Z     = 10.0f;
    // Rayons en unités modèle (à multiplier par lenX au call time, comme Tree).
    private static final float TRUNK_RADIUS      = 0.20f;
    private static final float FOLIAGE_BASE_RADIUS = 1.00f;
    private static final int   CYLINDER_FACETS   = 8;
    private static final int   CONE_FACETS       = 8;

    // ── Phase au sol : durée combustion visible ──────────────────────────────
    private static final int   GROUND_BURN_TICKS = 60;

    // ── Couleurs d'évolution ────────────────────────────────────────────────
    private static final float[] LEAF_GREEN    = { 0.15f, 0.55f, 0.10f };
    private static final float[] LEAF_ORANGE   = { 0.85f, 0.40f, 0.05f };
    private static final float[] LEAF_BLACK    = { 0.10f, 0.06f, 0.02f };
    private static final float[] TRUNK_BROWN   = { 0.35f, 0.20f, 0.08f };
    private static final float[] TRUNK_CHARRED = { 0.10f, 0.06f, 0.02f };

    public float px, py, pz;
    private final float sx, sy, sz;
    private final float dxf, dyf, dz;
    public final int targetCellX, targetCellY;
    private final float apex;
    private final int flightTicks;
    /** Axe de rotation horizontal (cos, sin) : perpendiculaire à la direction du vol. */
    private final float axisCos, axisSin;
    public int ticksElapsed = 0;
    public boolean _alive = true;
    private boolean grounded = false;
    private int groundedTicks = 0;
    /** Angle de basculement : 0 = debout, π/2 = couché horizontal, π/2±terrain = incliné. */
    private float tiltAngle = 0f;

    public TreeProjectile(World world,
                          float sx, float sy, float sz,
                          float dx, float dy, float dz,
                          int targetCellX, int targetCellY,
                          float apex, int flightTicks) {
        super((int) Math.round(sx), (int) Math.round(sy), world);
        this.sx = sx; this.sy = sy; this.sz = sz;
        this.dxf = dx; this.dyf = dy; this.dz = dz;
        this.targetCellX = targetCellX;
        this.targetCellY = targetCellY;
        this.apex = apex;
        this.flightTicks = Math.max(1, flightTicks);
        // Axe de rotation = perpendiculaire à la direction de vol (= chute "sur le côté")
        float dirX = dx - sx;
        float dirY = dy - sy;
        float dirLen = (float) Math.sqrt(dirX * dirX + dirY * dirY);
        if (dirLen < 1e-3f) { this.axisCos = 1f; this.axisSin = 0f; }
        else { this.axisCos = -dirY / dirLen; this.axisSin = dirX / dirLen; }
        this.px = sx; this.py = sy; this.pz = sz;
    }

    /** Factory : crée un projectile "static burn" sur (cellX, cellY) — arbre debout
     *  qui brûle visiblement sur place pendant GROUND_BURN_TICKS puis disparaît.
     *  Utilisé pour les NO-FLOW (arbre touché par lave sans flux voisin, ex: tephra
     *  LAVA atterri isolément). Pas de vol : grounded=true dès la création,
     *  tiltAngle=0 (debout, pas couché). */
    public static TreeProjectile createStatic(World world, int cellX, int cellY) {
        float sx = cellX + 0.5f;
        float sy = cellY + 0.5f;
        float sz = world.getCellTopAltitude(cellX, cellY);
        TreeProjectile p = new TreeProjectile(world, sx, sy, sz, sx, sy, sz, cellX, cellY, 0f, 1);
        p.grounded = true;
        p.tiltAngle = 0f;
        return p;
    }

    @Override
    public void step() {
        if (!_alive) return;
        if (!grounded) {
            ticksElapsed++;
            float ratio = Math.min(1f, (float) ticksElapsed / flightTicks);
            px = sx + (dxf - sx) * ratio;
            py = sy + (dyf - sy) * ratio;
            float zLin = sz + (dz - sz) * ratio;
            float zArc = apex * (float) Math.sin(Math.PI * ratio);
            pz = zLin + zArc;
            tiltAngle = (float) (Math.PI * 0.5 * ratio);  // 0 → π/2

            int w = world.getWidth();
            int h = world.getHeight();
            x = ((int) Math.round(px) % w + w) % w;
            y = ((int) Math.round(py) % h + h) % h;

            if (ticksElapsed >= flightTicks) {
                landWithTerrainTilt();
                grounded = true;
            }
        } else {
            groundedTicks++;
            // La coulée peut continuer à monter pendant que l'arbre brûle.
            // On suit le top de stack pour rester en surface — sans ça l'arbre
            // serait submergé et invisible sous la lave.
            pz = world.getCellTopAltitude(targetCellX, targetCellY);
            if (groundedTicks >= GROUND_BURN_TICKS) _alive = false;
        }
    }

    /** À l'atterrissage : cale la base au sommet de la stack cible et calcule
     *  l'inclinaison selon la différence d'altitude entre la base et la "tête"
     *  (cellule voisine dans la direction de la chute). */
    private void landWithTerrainTilt() {
        float fallDirX = dxf - sx;
        float fallDirY = dyf - sy;
        float fallLen = (float) Math.sqrt(fallDirX * fallDirX + fallDirY * fallDirY);
        // Direction normalisée puis arrondie au voisin cardinal le plus proche
        int stepX, stepY;
        if (fallLen < 1e-3f) { stepX = 0; stepY = 0; }
        else {
            float nx = fallDirX / fallLen;
            float ny = fallDirY / fallLen;
            stepX = (Math.abs(nx) > Math.abs(ny)) ? (nx > 0 ? 1 : -1) : 0;
            stepY = (Math.abs(ny) >= Math.abs(nx)) ? (ny > 0 ? 1 : -1) : 0;
            if (stepX == 0 && stepY == 0) stepX = 1;
        }
        int dxW = world.getWidth();
        int dyW = world.getHeight();
        int headCellX = ((targetCellX + stepX) % dxW + dxW) % dxW;
        int headCellY = ((targetCellY + stepY) % dyW + dyW) % dyW;
        float altBase = world.getCellTopAltitude(targetCellX, targetCellY);
        float altHead = world.getCellTopAltitude(headCellX, headCellY);
        float horizontalSpan = 1.0f;  // ~1 cellule d'écart entre base et tête en unités world
        float terrainTilt = (float) Math.atan2(altHead - altBase, horizontalSpan);
        // Clamp [-45°, +45°] : au-delà, l'arbre paraîtrait debout
        float maxTilt = (float) (Math.PI / 4);
        if (terrainTilt > maxTilt) terrainTilt = maxTilt;
        if (terrainTilt < -maxTilt) terrainTilt = -maxTilt;
        tiltAngle = (float) (Math.PI * 0.5) + terrainTilt;
        pz = altBase;
    }

    @Override
    public void displayUniqueObject(World myWorld, GL2 gl, int offsetCA_x, int offsetCA_y,
                                    float offset, float stepX, float stepY,
                                    float lenX, float lenY, float normalizeHeight) {
        float wx2 = px - (offsetCA_x % myWorld.getWidth());
        if (wx2 < 0) wx2 += myWorld.getWidth();
        float wy2 = py - (offsetCA_y % myWorld.getHeight());
        if (wy2 < 0) wy2 += myWorld.getHeight();

        float cx = offset + wx2 * stepX;
        float cy = offset + wy2 * stepY;
        // Scale X/Y des rayons par lenX (= demi-taille cellule), pour matcher la
        // taille des arbres normaux (Tree.displayTreeAt fait glScalef(scaleXY, scaleXY, 1)
        // avec scaleXY = abs(lenX)). Z reste natif.
        float scaleXY = Math.abs(lenX);

        // Couleurs (au sol : évoluent ; en vol : pleinement vivantes vert/brun)
        float groundedRatio = grounded ? Math.min(1f, (float) groundedTicks / GROUND_BURN_TICKS) : 0f;
        float[] leaf  = (groundedRatio < 0.5f)
                ? lerpRGB(LEAF_GREEN,  LEAF_ORANGE, groundedRatio * 2f)
                : lerpRGB(LEAF_ORANGE, LEAF_BLACK,  (groundedRatio - 0.5f) * 2f);
        float[] trunk = lerpRGB(TRUNK_BROWN, TRUNK_CHARRED, groundedRatio);

        float tiltCos = (float) Math.cos(tiltAngle);
        float tiltSin = (float) Math.sin(tiltAngle);

        drawCylinder(gl, cx, cy, 0f, TRUNK_HEIGHT, TRUNK_RADIUS * scaleXY, tiltCos, tiltSin, trunk);
        drawCone(gl, cx, cy, FOLIAGE_BASE_Z, FOLIAGE_TOP_Z, FOLIAGE_BASE_RADIUS * scaleXY, tiltCos, tiltSin, leaf);
    }

    /** Cylindre vertical (avant rotation), centré sur (0,0,z0..z1) avec rayon r,
     *  rendu en `facets` quads. Chaque vertex est rotaté autour de (axisCos, axisSin, 0)
     *  puis translaté à (cx, cy, pz). */
    private void drawCylinder(GL2 gl, float cx, float cy, float z0, float z1, float radius,
                              float tiltCos, float tiltSin, float[] rgb) {
        gl.glColor3f(rgb[0], rgb[1], rgb[2]);
        for (int f = 0; f < CYLINDER_FACETS; f++) {
            double a0 = 2 * Math.PI * f / CYLINDER_FACETS;
            double a1 = 2 * Math.PI * (f + 1) / CYLINDER_FACETS;
            float x0 = radius * (float) Math.cos(a0);
            float y0 = radius * (float) Math.sin(a0);
            float x1 = radius * (float) Math.cos(a1);
            float y1 = radius * (float) Math.sin(a1);
            // Quad : (x0,y0,z0) (x1,y1,z0) (x1,y1,z1) (x0,y0,z1)
            emitVertex(gl, x0, y0, z0, cx, cy, tiltCos, tiltSin);
            emitVertex(gl, x1, y1, z0, cx, cy, tiltCos, tiltSin);
            emitVertex(gl, x1, y1, z1, cx, cy, tiltCos, tiltSin);
            emitVertex(gl, x0, y0, z1, cx, cy, tiltCos, tiltSin);
        }
    }

    /** Cône vertical centré sur (0,0,zBase..zTop) avec rayon de base baseRadius.
     *  Rendu en `facets` quads dégénérés (= triangles : base→pointe). */
    private void drawCone(GL2 gl, float cx, float cy, float zBase, float zTop, float baseRadius,
                          float tiltCos, float tiltSin, float[] rgb) {
        gl.glColor3f(rgb[0], rgb[1], rgb[2]);
        for (int f = 0; f < CONE_FACETS; f++) {
            double a0 = 2 * Math.PI * f / CONE_FACETS;
            double a1 = 2 * Math.PI * (f + 1) / CONE_FACETS;
            float x0 = baseRadius * (float) Math.cos(a0);
            float y0 = baseRadius * (float) Math.sin(a0);
            float x1 = baseRadius * (float) Math.cos(a1);
            float y1 = baseRadius * (float) Math.sin(a1);
            // Quad dégénéré (apex dupliqué) : (x0,y0,zBase) (x1,y1,zBase) (0,0,zTop) (0,0,zTop)
            emitVertex(gl, x0, y0, zBase, cx, cy, tiltCos, tiltSin);
            emitVertex(gl, x1, y1, zBase, cx, cy, tiltCos, tiltSin);
            emitVertex(gl, 0f, 0f, zTop,  cx, cy, tiltCos, tiltSin);
            emitVertex(gl, 0f, 0f, zTop,  cx, cy, tiltCos, tiltSin);
        }
    }

    /** Émet un vertex en appliquant rotation autour de (axisCos, axisSin, 0) puis translation. */
    private void emitVertex(GL2 gl, float x0, float y0, float z0,
                            float cx, float cy, float tiltCos, float tiltSin) {
        // Décompose en composante alignée sur l'axe et composante perpendiculaire
        float axisDot = x0 * axisCos + y0 * axisSin;
        float perpX = x0 - axisDot * axisCos;
        float perpY = y0 - axisDot * axisSin;
        // Direction perp dans le plan XY (axisSin, -axisCos)
        float perpLen = (float) Math.sqrt(perpX * perpX + perpY * perpY);
        float rotPerpX = axisSin;
        float rotPerpY = -axisCos;
        float signPerp = (perpX * rotPerpX + perpY * rotPerpY) >= 0 ? 1f : -1f;
        float signedPerp = perpLen * signPerp;
        // Rotation θ : perp et z deviennent (perp·cos + z·sin, -perp·sin + z·cos)
        float newPerp = signedPerp * tiltCos + z0 * tiltSin;
        float newZ    = -signedPerp * tiltSin + z0 * tiltCos;
        float worldX = axisDot * axisCos + newPerp * rotPerpX;
        float worldY = axisDot * axisSin + newPerp * rotPerpY;
        gl.glVertex3f(cx + worldX, cy + worldY, pz + newZ);
    }

    private static float[] lerpRGB(float[] a, float[] b, float t) {
        if (t < 0) t = 0; if (t > 1) t = 1;
        return new float[] {
                a[0] + (b[0] - a[0]) * t,
                a[1] + (b[1] - a[1]) * t,
                a[2] + (b[2] - a[2]) * t,
        };
    }
}
