// ### WORLD OF CELLS ### 
// created by nicolas.bredeche(at)upmc.fr
// date of creation: 2013-1-12

package objects;

import javax.media.opengl.GL2;

import worlds.World;

abstract public class CommonObject  // CommonObject are standard object with no particular properties (ie. no internal state)
{

    /**
     * Hauteur des blocks pierre et lave entièrement refroidie (unités monde).
     * Centralisée ici pour que :
     *  - StoneBlock, LavaBlock dessinent leurs cubes à la même hauteur.
     *  - les agents reposent au bon niveau sur la pierre (altitude += height).
     *  - les agents sur de la lave plongent à mi-hauteur (immersion réaliste).
     */
    public static final float STONE_BLOCK_HEIGHT = 3.0f;
    /** Fraction du block dans laquelle l'agent s'enfonce quand il marche sur de la lave. */
    public static final float AGENT_LAVA_DIVE_FRACTION = 0.5f;


    public static void displayObjectAt(World myWorld, GL2 gl, int cellState, float x, float y, double height, double heightBooster, float offset, float stepX, float stepY, float lenX, float lenY, float heightFactor, float smoothFactor[])
    {
    	System.out.println("CommonObject.displayObjectAt(...,x,y,...) called, but not implemented.");
    }

    public static void displayObjectAt(World myWorld, GL2 gl, float offset, float stepX, float stepY, float lenX, float lenY, float heightFactor )
    {
    	System.out.println("CommonObject.displayObjectAt(...) called, but not implemented.");
    }

    /**
     * Hash entier déterministe (x, y, salt) → [0, 1). Permet de produire du
     * « grain » de couleur stable d'une frame à l'autre, là où l'usage de
     * Math.random() ferait scintiller violemment la cellule (Math.random()
     * est appelé à CHAQUE frame de rendu).
     */
    public static double stableNoise(int x, int y, int salt) {
        int h = (x * 73856093) ^ (y * 19349663) ^ (salt * 83492791);
        h ^= h >>> 16;
        h *= 0x7feb352d;
        h ^= h >>> 15;
        return (h & 0x7FFFFFFF) / (double) Integer.MAX_VALUE;
    }

}
