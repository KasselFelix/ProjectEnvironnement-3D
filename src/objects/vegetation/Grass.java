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

    public static void displayObjectAt(World myWorld, GL2 gl, int cellState, float x, float y, double height, float offset, float stepX, float stepY, float lenX, float lenY, float normalizeHeight,int movingX,int movingY )
    {

        if ( cellState > 0 )
        {
    		// Altitude unifiée : sommet de la stack (sol natif + couches empilées).
    		float altitude = myWorld.getCellTopAltitude((int)x + movingX, (int)y + movingY);

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
            		gl.glColor3f(0,(float)((220*0.6/255)+(220*0.4/255)*(myWorld.getCellHeight((int)x,(int)y)/myWorld.getMaxEverHeight())-0.05*r2),0);
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
    		gl.glVertex3f( offset+x*stepX, offset+y*stepY, altitude );
            gl.glVertex3f( offset+x*stepX, offset+y*stepY+lenY*0.4f, altitude+1.f);
            gl.glVertex3f( offset+x*stepX, offset+y*stepY, altitude );
            gl.glVertex3f( offset+x*stepX, offset+y*stepY-lenY*0.4f, altitude+1.f);

            switch ( cellState )
            {
            	case 1:
            		gl.glColor3f(0,(float)((220*0.6/255)+(220*0.4/255)*(myWorld.getCellHeight((int)x,(int)y)/myWorld.getMaxEverHeight())-0.05*r1),0);
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
            gl.glVertex3f( offset+x*stepX, offset+y*stepY, altitude );
            gl.glVertex3f( offset+x*stepX-lenX*0.4f, offset+y*stepY, altitude+1.f);
    		gl.glVertex3f( offset+x*stepX, offset+y*stepY, altitude );
            gl.glVertex3f( offset+x*stepX+lenX*0.4f, offset+y*stepY, altitude+1.f);
        }
    }
}
