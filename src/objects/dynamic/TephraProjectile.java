package objects.dynamic;

import javax.media.opengl.GL2;

import objects.LavaLineage;
import objects.Material;
import objects.UniqueDynamicObject;
import worlds.World;

/**
 * Projectile balistique éjecté par une éruption volcanique.
 *
 * Trajectoire : interpolation linéaire en (x, y) entre la source et la cellule
 * d'atterrissage, plus un arc en z (sinus, hauteur d'apex `apex`). À
 * `ticksElapsed >= ticksTotal`, le projectile « atterrit » :
 *   1. Pose une couche BASALT (`world.pushLayer`) à `(targetCellX, targetCellY)`.
 *   2. Passe à `_alive = false` — sera retiré de `world.uniqueDynamicObjects`
 *      au prochain `stepAgents()` (cf. WorldOfCells.stepAgents).
 *
 * Le rendu utilise des coordonnées flottantes (`px, py, pz`) pour un
 * mouvement fluide à 60 FPS, indépendant de la cadence du CA.
 *
 * Le champ `x, y` hérité de UniqueDynamicObject est tenu synchronisé avec
 * `(round(px), round(py))` à des fins de debug et pour rester cohérent avec
 * le pattern des autres objets dynamiques (Loup, Mouton…) — mais aucun
 * système ne le lit pour cette classe.
 */
public class TephraProjectile extends UniqueDynamicObject {

    /** Position monde flottante mise à jour à chaque step. */
    public float px, py, pz;
    private final float sx, sy, sz;   // départ
    private final float dx, dy, dz;   // arrivée (centre de la cellule cible)
    public final int targetCellX, targetCellY;
    private final float thickness;    // épaisseur du matériau à poser à l'atterrissage
    private final float apex;         // hauteur additionnelle de l'arc parabolique
    private final int ticksTotal;
    /** Matériau posé à l'atterrissage. BASALT par défaut, LAVA pour les projections de lave. */
    private final Material landMaterial;
    /** Lineage transmis quand on pose une couche LAVA (null sinon). */
    private final LavaLineage lineage;
    public int ticksElapsed = 0;
    public boolean _alive = true;

    /** Constructeur historique : projectile BASALT (tephra solide). */
    public TephraProjectile(World world,
                            float sx, float sy, float sz,
                            float dx, float dy, float dz,
                            int targetCellX, int targetCellY,
                            float thickness, float apex, int ticksTotal) {
        this(world, sx, sy, sz, dx, dy, dz, targetCellX, targetCellY,
                thickness, apex, ticksTotal, Material.BASALT, null);
    }

    /** Constructeur étendu : matériau + lineage paramétrables (LAVA pour projections de lave fondue). */
    public TephraProjectile(World world,
                            float sx, float sy, float sz,
                            float dx, float dy, float dz,
                            int targetCellX, int targetCellY,
                            float thickness, float apex, int ticksTotal,
                            Material landMaterial, LavaLineage lineage) {
        super((int) Math.round(sx), (int) Math.round(sy), world);
        this.sx = sx; this.sy = sy; this.sz = sz;
        this.dx = dx; this.dy = dy; this.dz = dz;
        this.targetCellX = targetCellX;
        this.targetCellY = targetCellY;
        this.thickness = thickness;
        this.apex = apex;
        this.ticksTotal = Math.max(1, ticksTotal);
        this.landMaterial = landMaterial;
        this.lineage = lineage;
        this.px = sx; this.py = sy; this.pz = sz;
    }

    @Override
    public void step() {
        if (!_alive) return;
        ticksElapsed++;
        float ratio = Math.min(1f, (float) ticksElapsed / ticksTotal);
        px = sx + (dx - sx) * ratio;
        py = sy + (dy - sy) * ratio;
        float zLin = sz + (dz - sz) * ratio;
        float zArc = apex * (float) Math.sin(Math.PI * ratio);
        pz = zLin + zArc;
        int w = world.getWidth();
        int h = world.getHeight();
        x = ((int) Math.round(px) % w + w) % w;
        y = ((int) Math.round(py) % h + h) % h;

        if (ticksElapsed >= ticksTotal) {
            if (landMaterial == Material.LAVA && lineage != null) {
                // Projection de lave : pose LAVA fraîche (state=1) avec lineage hérité.
                world.pushLayer(targetCellX, targetCellY, Material.LAVA, thickness, 1, lineage);
            } else {
                world.pushLayer(targetCellX, targetCellY, landMaterial, thickness, 0);
            }
            // Impact sur les agents au sol :
            //  - BASALT (roche) → kill direct (écrasement physique).
            //  - LAVA (bombe fondue) → mise en feu (l'agent va chercher de l'eau).
            //    Humain n'a pas de comportement feu → kill direct (cf. setAgentsOnFireOnCell).
            if (landMaterial == Material.LAVA) {
                cellularautomata.ecosystem.LavaCA.setAgentsOnFireOnCell(world, targetCellX, targetCellY);
                // LAVA en bombe : met aussi le feu à un arbre vivant (cycle ForestCA normal ensuite).
                if (world.getForestCAValue(targetCellX, targetCellY) == 1) {
                    world.setForestCAValue(targetCellX, targetCellY, 2);
                }
            } else {
                cellularautomata.ecosystem.LavaCA.killAgentsOnCell(world, targetCellX, targetCellY);
                // BASALT en bombe : écrase la végétation (arbre + herbe) — pierre lourde
                // qui détruit sans brûler. Set à 0 quel que soit l'état précédent.
                world.setForestCAValue(targetCellX, targetCellY, 0);
                world.setGrassCAValue(targetCellX, targetCellY, 0);
            }
            _alive = false;
        }
    }

    @Override
    public void displayUniqueObject(World myWorld, GL2 gl, int offsetCA_x, int offsetCA_y,
                                    float offset, float stepX, float stepY,
                                    float lenX, float lenY, float normalizeHeight) {
        // Wrap caméra (tore) — pareil que Loup.displayUniqueObject.
        float wx2 = px - (offsetCA_x % myWorld.getWidth());
        if (wx2 < 0) wx2 += myWorld.getWidth();
        float wy2 = py - (offsetCA_y % myWorld.getHeight());
        if (wy2 < 0) wy2 += myWorld.getHeight();

        float cx = offset + wx2 * stepX;
        float cy = offset + wy2 * stepY;
        float halfX = lenX * 0.6f;
        float halfY = lenY * 0.6f;
        float z0 = pz;
        float z1 = pz + thickness;

        float ratio = Math.min(1f, (float) ticksElapsed / ticksTotal);
        float r, g, b;
        if (landMaterial == Material.LAVA) {
            // Projection de lave : orange vif constant (la lave reste fondue
            // pendant le vol, légère atténuation à l'approche du sol).
            r = 1.0f;
            g = lerp(0.45f, 0.30f, ratio);
            b = 0.0f;
        } else {
            // Tephra BASALT : dégradé thermique orange (chaud) → gris-bleu (refroidi).
            r = lerp(0.78f, 0.22f, ratio);
            g = lerp(0.32f, 0.22f, ratio);
            b = lerp(0.10f, 0.25f, ratio);
        }
        gl.glColor3f(r, g, b);

        // 6 faces du cube en GL_QUADS (un glBegin est déjà ouvert par le caller).
        // Nord
        gl.glVertex3f(cx - halfX, cy - halfY, z0);
        gl.glVertex3f(cx + halfX, cy - halfY, z0);
        gl.glVertex3f(cx + halfX, cy - halfY, z1);
        gl.glVertex3f(cx - halfX, cy - halfY, z1);
        // Sud
        gl.glVertex3f(cx - halfX, cy + halfY, z0);
        gl.glVertex3f(cx - halfX, cy + halfY, z1);
        gl.glVertex3f(cx + halfX, cy + halfY, z1);
        gl.glVertex3f(cx + halfX, cy + halfY, z0);
        // Est
        gl.glVertex3f(cx + halfX, cy - halfY, z0);
        gl.glVertex3f(cx + halfX, cy + halfY, z0);
        gl.glVertex3f(cx + halfX, cy + halfY, z1);
        gl.glVertex3f(cx + halfX, cy - halfY, z1);
        // Ouest
        gl.glVertex3f(cx - halfX, cy - halfY, z0);
        gl.glVertex3f(cx - halfX, cy - halfY, z1);
        gl.glVertex3f(cx - halfX, cy + halfY, z1);
        gl.glVertex3f(cx - halfX, cy + halfY, z0);
        // Top
        gl.glVertex3f(cx - halfX, cy - halfY, z1);
        gl.glVertex3f(cx + halfX, cy - halfY, z1);
        gl.glVertex3f(cx + halfX, cy + halfY, z1);
        gl.glVertex3f(cx - halfX, cy + halfY, z1);
        // Bottom
        gl.glVertex3f(cx - halfX, cy - halfY, z0);
        gl.glVertex3f(cx - halfX, cy + halfY, z0);
        gl.glVertex3f(cx + halfX, cy + halfY, z0);
        gl.glVertex3f(cx + halfX, cy - halfY, z0);
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }
}
