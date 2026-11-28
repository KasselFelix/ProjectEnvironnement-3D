package objects.vegetation;

import javax.media.opengl.GL2;

import worlds.World;

public class Grass {

    /**
     * Hash déterministe (x, y, salt) → [0, 1). Permet de générer du « grain »
     * couleur stable d'une frame à l'autre — sinon Math.random() recalculé à
     * chaque frame fait scintiller l'herbe.
     */
    private static double stableNoise(int x, int y, int salt) {
        int h = (x * 73856093) ^ (y * 19349663) ^ (salt * 83492791);
        h ^= h >>> 16;
        h *= 0x7feb352d;
        h ^= h >>> 15;
        return (h & 0x7FFFFFFF) / (double) Integer.MAX_VALUE;
    }

    /** Phase 2 : échelle (hauteur/densité) d'une touffe selon ses brins.
     *  0 brin → SAPLING_MIN (rase), max → 1.0, linéaire entre les deux. */
    private static final float GRASS_SAPLING_MIN = 0.3f;
    public static float bladeScale(int brins, int maxBrins) {
        if (maxBrins <= 0) return GRASS_SAPLING_MIN;
        float f = Math.max(0f, Math.min(1f, brins / (float) maxBrins));
        return GRASS_SAPLING_MIN + (1f - GRASS_SAPLING_MIN) * f;
    }

    /**
     * Émet, dans le glBegin(GL_QUADS) de la passe 2a (culling coupé, cf.
     * Landscape), le « V » de brins de pied (bx,by,bz) et de pointes t1, t2 :
     * le triangle (pied, t1, t2).
     *
     * <p>Le sommet dupliqué est en positions 1-2 (voisines), si bien que les deux
     * découpages possibles du quad donnent le même triangle. L'ancien encodage
     * (pied, t1, pied, t2) dupliquait les sommets 1 et 3 : un pilote qui coupe le
     * quad selon la diagonale 1-3 (NVIDIA) n'obtenait que deux triangles plats et
     * l'herbe disparaissait ; selon la diagonale 2-4 (Intel), il donnait ce même
     * triangle dans les deux sens.
     */
    private static void emitV(GL2 gl, float bx, float by, float bz,
                              float t1x, float t1y, float t1z,
                              float t2x, float t2y, float t2z) {
        gl.glVertex3f(bx, by, bz);
        gl.glVertex3f(bx, by, bz);
        gl.glVertex3f(t1x, t1y, t1z);
        gl.glVertex3f(t2x, t2y, t2z);
    }

    public static void displayObjectAt(World myWorld, GL2 gl, int cellState, float x, float y, double height, float offset, float stepX, float stepY, float lenX, float lenY, float normalizeHeight,int movingX,int movingY, float bladeScale )
    {

        if ( cellState > 0 )
        {
    		// Altitude unifiée : sommet de la stack (sol natif + couches empilées).
    		float altitude = myWorld.getCellTopAltitude((int)x + movingX, (int)y + movingY);

    		// Planté sur le SOMMET de terrain (coin de cellule) dont l'altitude est lue,
    		// pas au centre du quad : sinon décalage d'une demi-cellule → le brin
    		// s'enfonce dans le sol en pente (même cause que les jeunes arbres, cf. Tree).
    		float px = offset + x * stepX - lenX;
    		float py = offset + y * stepY - lenY;

    		// Randoms déterministes par cellule (et par brin) — fixés pour
    		// la durée de vie de la cellule. Avant : Math.random() à chaque
    		// frame faisait scintiller violemment le sol.
    		int cellX = ((int) x + movingX);
    		int cellY = ((int) y + movingY);
    		double r1 = stableNoise(cellX, cellY, 1);
    		double r2 = stableNoise(cellX, cellY, 2);

    		switch ( cellState )
            {
            	case 1:
            		gl.glColor3f(0,(float)((220*0.6/255)+(220*0.4/255)*(myWorld.getCellHeight(cellX,cellY)/myWorld.getMaxEverHeight())-0.05*r2),0);
            		break;
            	case 2:
            		gl.glColor3f(1.f,127/255f-(float)(0.2*r2),0.f);
            		break;
            	case 3:
            		gl.glColor3f((float)(0.1*stableNoise(cellX,cellY,10)),
            		             (float)(0.1*stableNoise(cellX,cellY,11)),
            		             (float)(0.1*stableNoise(cellX,cellY,12)));
            		break;
            }
            emitV(gl, px, py, altitude,
                  px, py+lenY*0.4f*bladeScale, altitude+bladeScale,
                  px, py-lenY*0.4f*bladeScale, altitude+bladeScale);

            switch ( cellState )
            {
            	case 1:
            		gl.glColor3f(0,(float)((220*0.6/255)+(220*0.4/255)*(myWorld.getCellHeight(cellX,cellY)/myWorld.getMaxEverHeight())-0.05*r1),0);
            		break;
            	case 2:
            		gl.glColor3f(1.f,127/255f-(float)(0.2*r1),0.f);
            		break;
            	case 3:
            		gl.glColor3f((float)(0.2*stableNoise(cellX,cellY,20)),
            		             (float)(0.2*stableNoise(cellX,cellY,21)),
            		             (float)(0.2*stableNoise(cellX,cellY,22)));
            		break;
            }
            emitV(gl, px, py, altitude,
                  px-lenX*0.4f*bladeScale, py, altitude+bladeScale,
                  px+lenX*0.4f*bladeScale, py, altitude+bladeScale);
        }
    }
}
