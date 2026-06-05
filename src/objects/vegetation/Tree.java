package objects.vegetation;

import javax.media.opengl.GL2;

import loader.GLBModel;
import objects.CommonObject;
import worlds.World;
import worlds.WorldOfCells;


public class Tree extends CommonObject {
	
	static void makeTree(int length, int angle, float px, float py, float pz,
			World myWorld, GL2 gl, int cellState,  float x, float y, float offset, float lenX, float lenY){
		float move = (int)(Math.cos(Math.toRadians(angle+90))*length);
		float zmove = -((int)(Math.sin(Math.toRadians(angle-90))*length));
		/**/
		gl.glVertex3f( px-lenX*0.05f, y, pz);
		gl.glVertex3f( px-lenX*0.05f+move, y, pz+zmove);
		gl.glVertex3f( px+lenX*0.05f+move, y, pz+zmove);
		gl.glVertex3f( px+lenX*0.05f, y, pz );


		gl.glVertex3f( px-lenX*0.05f, y, pz );
		gl.glVertex3f( px+lenX*0.05f, y, pz );
		gl.glVertex3f( px+lenX*0.05f+move, y, pz+zmove);
		gl.glVertex3f( px-lenX*0.05f+move, y, pz+zmove);


		gl.glVertex3f( x,py-lenY*0.05f, pz);
		gl.glVertex3f( x,py-lenY*0.05f+move, pz+zmove);
		gl.glVertex3f( x,py+lenY*0.05f+move, pz+zmove);
		gl.glVertex3f( x,py+lenY*0.05f, pz );


		gl.glVertex3f( x,py-lenY*0.05f, pz );
		gl.glVertex3f( x,py+lenY*0.05f, pz );
		gl.glVertex3f( x,py+lenY*0.05f+move, pz+zmove);
		gl.glVertex3f( x,py-lenY*0.05f+move, pz+zmove);
		/**/
		/*
		gl.glVertex3f( px-lenX*0.05f, py-lenY*0.05f, pz);
        gl.glVertex3f( px-lenX*0.05f+move, py-lenY*0.05f+move, pz+zmove);
        gl.glVertex3f( px-lenX*0.05f+move, py+lenY*0.05f+move, pz+zmove);
        gl.glVertex3f( px-lenX*0.05f,py+lenY*0.05f, pz );


        gl.glVertex3f( px+lenX*0.05f, py-lenY*0.05f, pz);
        gl.glVertex3f( px+lenX*0.05f+move, py-lenY*0.05f+move, pz+zmove);
        gl.glVertex3f( px+lenX*0.05f+move, py+lenY*0.05f+move, pz+zmove);
        gl.glVertex3f( px+lenX*0.05f,py+lenY*0.05f, pz );

        gl.glVertex3f( px-lenX*0.05f, py-lenY*0.05f, pz);
        gl.glVertex3f( px-lenX*0.05f+move, py-lenY*0.05f+move, pz+zmove);
        gl.glVertex3f( px+lenX*0.05f+move, py-lenY*0.05f+move, pz+zmove);
        gl.glVertex3f( px+lenX*0.05f,py-lenY*0.05f, pz );

        gl.glVertex3f( px-lenX*0.05f, py+lenY*0.05f, pz);
        gl.glVertex3f( px-lenX*0.05f+move, py+lenY*0.05f+move, pz+zmove);
        gl.glVertex3f( px+lenX*0.05f+move, py+lenY*0.05f+move, pz+zmove);
        gl.glVertex3f( px+lenX*0.05f,py+lenY*0.05f, pz );

        gl.glVertex3f( px-lenX*0.05f+move, py-lenY*0.05f+move, pz+zmove);
        gl.glVertex3f( px+lenX*0.05f+move, py-lenY*0.05f+move, pz+zmove);
        gl.glVertex3f( px+lenX*0.05f+move, py+lenY*0.05f+move, pz+zmove);
        gl.glVertex3f( px-lenX*0.05f+move, py+lenY*0.05f+move, pz+zmove );

        gl.glVertex3f( px-lenX*0.05f, py-lenY*0.05f, pz);
        gl.glVertex3f( px+lenX*0.05f, py-lenY*0.05f, pz);
        gl.glVertex3f( px+lenX*0.05f, py+lenY*0.05f, pz);
        gl.glVertex3f( px-lenX*0.05f, py+lenY*0.05f, pz);
        /**/

        
		if (length>=1){
			makeTree(length-1,angle+15,px+move, py+move, pz+zmove,
					myWorld, gl,cellState, x, y,offset, lenX, lenY);
			makeTree(length-1,angle-15,px+move, py+move, pz+zmove,
					myWorld, gl,cellState, x, y,offset, lenX, lenY);
		}
	}

    /**
     * Hash pseudo-aléatoire stable basé sur (x, y, salt) → fractional part de
     * sin(...) × constante. Donne une valeur dans [0, 1) déterministe pour
     * une position donnée. Sert à figer la teinte/forme d'un arbre afin
     * d'éviter le scintillement (auparavant Math.random() était appelé chaque
     * frame, chaque arbre changeait de couleur en permanence).
     */
    private static double stableHash(float x, float y, float salt) {
        double v = Math.sin(x * 12.9898 + y * 78.233 + salt * 37.719) * 43758.5453;
        v = v - Math.floor(v);
        return v;
    }

    /**
     * No-op : l'arbre entier (tronc cylindre + feuillage cône) est maintenant rendu
     * par {@link #displayTreeAt} dans la passe 2b, hors du glBegin(GL_QUADS) de
     * la passe 2 (impossible d'appeler une display list dans un glBegin).
     * On garde cette signature pour ne pas casser WorldOfCells.displayObjectTree.
     */
    public static void displayObjectAt(World myWorld, GL2 gl, int cellState, float x, float y, double height, float offset, float stepX, float stepY, float lenX, float lenY, float normalizeHeight,int movingX,int movingY )
    {
        // intentionnellement vide
    }

    // ===== Modèle OBJ Blender (tronc + feuillage) =====

    /** Modèle compilé une fois par initModel(). Null tant qu'init n'a pas tourné. */
    private static GLBModel treeModel;

    /**
     * Charge `models/tree.glb` (converti depuis l'ancien tree.obj). Le modèle
     * est Z-up, base du tronc à z=0, apex cône à z=10, deux matériaux couleur
     * plate (FoliageGreen / TrunkBrown via baseColorFactor). centerit=false :
     * on garde la base à z=0. Émission 0 : les couleurs plates sont déjà
     * lisibles, pas besoin de relever les ombres comme pour la laine du mouton.
     */
    public static void initModel(GL2 gl) {
        try {
            treeModel = new GLBModel("models/arbre.glb", false, gl, 0.0f);
        } catch (Exception e) {
            System.out.println("[Tree] erreur chargement GLB: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Facteur d'échelle de l'arbre relatif à la taille d'une cellule (lenX).
     * Le modèle arbre.glb est normalisé à ~1 unité de haut, tronc à Z=0. Avec
     * lenX ≈ 1.95 (map 200²) → hauteur monde ≈ 11 unités, soit ~4× un mouton.
     * Ajuster ici si les arbres paraissent trop grands/petits.
     */
    private static final float TREE_SCALE_FACTOR = 5.5f;
    /** Fraction de la taille adulte qu'a une jeune pousse (g=0). */
    private static final float SAPLING_FRAC = 0.25f;
    /** Multiplicateur de taille adulte selon la fertilité : sol pauvre → MULT_MIN, sol riche → MULT_MAX. */
    private static final float MULT_MIN = 0.6f;
    private static final float MULT_MAX = 1.0f;
    /** Sel du hash pour le yaw aléatoire déterministe. */
    private static final float SALT_YAW = 17.0f;

    /**
     * Rend un arbre via le modèle GLB (sapin low-poly texturé, modddif). Le
     * modèle est Z-up, tronc à Z=0, normalisé ~1 unité. Scale UNIFORME (lenX ×
     * TREE_SCALE_FACTOR) pour garder les proportions naturelles du sapin.
     *
     * À appeler HORS d'un glBegin (opengldraw fait un glCallList).
     */
    public static void displayTreeAt(World myWorld, GL2 gl, int cellState, float x, float y,
                                      double height, float offset, float stepX, float stepY,
                                      float lenX, float lenY, float normalizeHeight,
                                      int movingX, int movingY) {
        if (treeModel == null) return;
        if (cellState < 1 || cellState > 5) return; // état 1=vivant, 2-4=en feu, 5=brûlé

        // Altitude unifiée : sommet de la stack (sol natif + couches empilées).
        float altitude = myWorld.getCellTopAltitude((int)x + movingX, (int)y + movingY);

        float px = offset + x * stepX;
        float py = offset + y * stepY;

        // Taille = croissance (jeune→adulte) × plafond dépendant de la fertilité.
        double g = 1.0, F = 1.0;
        if (myWorld instanceof WorldOfCells) {
            WorldOfCells wc = (WorldOfCells) myWorld;
            g = wc.getTreeGrowth((int) x + movingX, (int) y + movingY);
            F = wc.getTreeFertility((int) x + movingX, (int) y + movingY);
        }
        float maxMult     = (float) (MULT_MIN + (MULT_MAX - MULT_MIN) * F);
        float currentMult = (float) ((SAPLING_FRAC + (1.0 - SAPLING_FRAC) * g) * maxMult);
        float scale       = Math.abs(lenX) * TREE_SCALE_FACTOR * currentMult;

        // Yaw aléatoire déterministe en coordonnées MONDE (x+movingX, y+movingY) :
        // stable entre frames ET quand le viewport panne — l'arbre ne tourne pas.
        float yawDeg = (float) (360.0 * stableHash(x + movingX, y + movingY, SALT_YAW));

        gl.glPushMatrix();
        gl.glTranslatef(px, py, altitude);
        gl.glRotatef(yawDeg, 0f, 0f, 1f);
        gl.glScalef(scale, scale, scale);
        treeModel.opengldraw(gl);
        gl.glPopMatrix();
    }

}
