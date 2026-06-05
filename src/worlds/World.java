
package worlds;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.media.opengl.GL2;

import agents.Agent;
import agents.Loup;
import agents.Mouton;
import agents.Humain;

import cellularautomata.*;
import cellularautomata.ecosystem.LavaSource;

import objects.*;

public abstract class World {
	
	protected int iteration = 0;

	protected ArrayList<UniqueObject> uniqueObjects = new ArrayList<UniqueObject>();
	public ArrayList<UniqueDynamicObject> uniqueDynamicObjects = new ArrayList<UniqueDynamicObject>();
	
	public ArrayList<Agent> agents = new ArrayList<Agent>();
	public ArrayList<Humain> humains = new ArrayList<Humain>();
	public ArrayList<Loup> loups = new ArrayList<Loup>();
	public ArrayList<Mouton> moutons = new ArrayList<Mouton>();
	
	public ArrayList<Integer> list=new ArrayList<Integer>();
	int jour=0;// 0:nuit / 1:jour
	int dureeJour=2000;// ticks d'un demi-cycle (jour OU nuit) — calculé depuis SimulationConfig.cycleTotalSec
	int transitionJour=500;// nombre d'iteration de la transition entre jour et nuit
	int before=0;//etat precedent jour/nuit 
    
	protected int dxCA;
	protected int dyCA;

	protected int indexCA;

	//protected CellularAutomataInteger cellularAutomata; // TO BE DEFINED IN CHILDREN CLASSES
    
	protected CellularAutomataDouble cellsHeightValuesCA;
	protected CellularAutomataDouble cellsHeightAmplitudeCA;
	
	public CellularAutomataColor cellsColorValues;

	private double maxEverHeightValue = Double.NEGATIVE_INFINITY;
	private double minEverHeightValue = Double.POSITIVE_INFINITY;

	/**
	 * Système Layer : stack de couches empilées par cellule (indexée par
	 * x * dyCA + y). Vide par défaut, alimentée par la solidification des
	 * coulées de lave (cf. LavaCA) et tout futur mécanisme de pose de
	 * matériau (constructions humaines, sédiments…). Le sol natif Perlin
	 * reste hors du stack (champ scalaire `cellsHeightValuesCA`).
	 */
	protected ArrayList<ArrayList<Layer>> cellStacks;

	/**
	 * Plafond monde au-dessus duquel aucune couche ne peut être posée. Calculé
	 * dans init() après génération du terrain : `maxEverHeight + DELTA` (DELTA
	 * = 100 unités-monde de référence). Sert de garde-fou technique pour
	 * éviter des piles absurdes ; jamais touché en jeu normal.
	 *
	 * Pour comparer à une altitude monde, on a besoin de connaître l'échelle
	 * verticale appliquée par le rendu (heightFactor × heightBooster) — cf.
	 * setVerticalScale().
	 */
	protected float worldCeiling = Float.MAX_VALUE;
	protected float verticalScale = 1.0f;
	/** Marge au-dessus du sol natif le plus haut (DELTA = 100 — quasi sans contrainte). */
	private static final float WORLD_CEILING_DELTA = 100.0f;

    public World( )
    {
    	// ... cf. init() for initialization
    }
    
    public void init( int __dxCA, int __dyCA, double[][] landscape )
    {
    	dxCA = __dxCA;
    	dyCA = __dyCA;
    	
    	iteration = 0;

    	this.cellsHeightValuesCA = new CellularAutomataDouble (__dxCA,__dyCA,false);
    	this.cellsHeightAmplitudeCA = new CellularAutomataDouble (__dxCA,__dyCA,false);

    	this.cellsColorValues = new CellularAutomataColor(__dxCA,__dyCA,false);

    	// Système Layer : une stack vide par cellule. Allouée d'avance pour
    	// éviter le null-check ; restent vides tant qu'aucune couche n'est
    	// poussée par la simulation.
    	this.cellStacks = new ArrayList<>(__dxCA * __dyCA);
    	for (int i = 0 ; i < __dxCA * __dyCA ; i++) {
    		this.cellStacks.add(new ArrayList<Layer>(0));
    	}

    	int cpt=0;
    	
    	// init altitude and color related information
    	for ( int x = 0 ; x != dxCA ; x++ )
    		for ( int y = 0 ; y != dyCA ; y++ )
    		{
    			// compute height values (and amplitude) from the landscape for this CA cell 
    			double minHeightValue = Math.min(Math.min(landscape[x][y],landscape[x+1][y]),Math.min(landscape[x][y+1],landscape[x+1][y+1]));
    			double maxHeightValue = Math.max(Math.max(landscape[x][y],landscape[x+1][y]),Math.max(landscape[x][y+1],landscape[x+1][y+1])); 
    			
    			if ( this.maxEverHeightValue < maxHeightValue )
    				this.maxEverHeightValue = maxHeightValue;
    			if ( this.minEverHeightValue > minHeightValue )
    				this.minEverHeightValue = minHeightValue;
    			
    			cellsHeightAmplitudeCA.setCellState(x,y,maxHeightValue-minHeightValue);
    			cellsHeightValuesCA.setCellState(x,y,(minHeightValue+maxHeightValue)/2.0);

    			list.add(cpt);
	    		cpt++;
    	}
    	// worldCeiling initial (sans échelle, sera multiplié par verticalScale
    	// quand Landscape appellera setVerticalScale après calcul de heightFactor
    	// × heightBooster). DELTA absolu en unités-monde de la stack.
    	this.worldCeiling = (float) this.maxEverHeightValue * this.verticalScale + WORLD_CEILING_DELTA;
    	initCellularAutomata(__dxCA,__dyCA,landscape);

    }

    /**
     * Configure l'échelle verticale du rendu (heightFactor × heightBooster
     * côté Landscape). Doit être appelé une fois après init() pour que les
     * calculs d'altitude monde et le plafond du stack soient corrects.
     */
    public void setVerticalScale(float scale) {
    	this.verticalScale = scale;
    	this.worldCeiling = (float) this.maxEverHeightValue * scale + WORLD_CEILING_DELTA;
    }

    public float getVerticalScale() { return verticalScale; }
    public float getWorldCeiling() { return worldCeiling; }
    
    
    public void step()
    {
    	stepCellularAutomata();
    	stepAgents();
    	before=jour;
    	iteration++;
    }
    
    public int getIteration()
    {
    	return this.iteration;
    }
    
    public int getJour() {
		return jour;
	}

	public void setJour(int jour) {
		this.jour = jour;
	}

	public int getDureeJour() {
		return dureeJour;
	}

	public void setDureeJour(int dureeJour) {
		this.dureeJour = dureeJour;
	}

	public int getTransitionJour() {
		return transitionJour;
	}

	public void setTransitionJour(int transitionJour) {
		this.transitionJour = transitionJour;
	}

	public int getBefore() {
		return before;
	}

	public void setBefore(int before) {
		this.before = before;
	}

	abstract protected void stepAgents();
    
    // ----

    protected abstract void initCellularAutomata(int __dxCA, int __dyCA, double[][] landscape);
    
    protected abstract void stepCellularAutomata();
    
    // ---
    // used by the visualization code to call specific object display.
    abstract public int getGrassCAValue(int x, int y);
    abstract public int getForestCAValue(int x, int y);
    abstract public int getLavaCAValue(int x, int y);
    abstract public int getStoneCAValue(int x, int y);

    abstract public void setForestCAValue(int x, int y, int state);
    abstract public void setGrassCAValue(int x, int y, int state);
    
    // ----
    abstract public int getNbhumains();
	abstract public void setNbhumains(int nbhumains);
    abstract public int getNbloups();
	abstract public void setNbloups(int nbloups);
	abstract public int getNbmoutons();
	abstract public void setNbmoutons(int nbmoutons);
	abstract public int getBergerie();
	abstract public void setBergerie(int bergerie);
	abstract public int getWolfHome();
	abstract public void setWolfHome(int wolfHome);
    
    // ---- 
	abstract public void colorInit(int x, int y, float color[]);
	// ----
	abstract public double distance( int ib,int jb,int ia,int ja);//calcul distance dans un monde torique
	
    // ----
	
    public double getCellHeight(int x, int y) // used by the visualization code to set correct height values
    {
    	return cellsHeightValuesCA.getCellState(x%dxCA,y%dyCA);
    }
    
    public void setCellHeight(int x,int y, double state){
    	cellsHeightValuesCA.setCellState(x%dxCA,y%dyCA,state);
    }
    
    public double getcellsHeightAmplitudeCA(int x, int y)
    {
    	return cellsHeightAmplitudeCA.getCellState(x%dxCA,y%dyCA);
    }
    
    public void setCellsHeightAmplitudeCA(int x, int y,double state)
    {
    	cellsHeightAmplitudeCA.setCellState( x%dxCA, y%dyCA,state);
    }
   
    // ----
    
    public float[] getCellColorValue(int x, int y) // used to display cell color
    {
    	float[] cellColor = this.cellsColorValues.getCellState( x%this.dxCA , y%this.dyCA );

    	float[] color  = {cellColor[0],cellColor[1],cellColor[2],1.0f};
        
        return color;
    }

	abstract public void displayObjectTree(World _myWorld, GL2 gl, int cellState, int x,
			int y, double height, float offset,
			float stepX, float stepY, float lenX, float lenY,
			float normalizeHeight,int movingX, int movingY);
	
	abstract public void displayObjectGrass(World _myWorld, GL2 gl, int cellState, int x,
			int y, double height, float offset,
			float stepX, float stepY, float lenX, float lenY,
			float normalizeHeight,int movingX, int movingY);
	
	abstract public void displayObjectLave(World _myWorld, GL2 gl, int cellState, int x,
			int y, double height, float offset,
			float stepX, float stepY, float lenX, float lenY,
			float normalizeHeight,int movingX, int movingY);
	
	abstract public void displayObjectStone(World _myWorld, GL2 gl, int cellState, int x,
			int y, double height, float offset,
			float stepX, float stepY, float lenX, float lenY,
			float normalizeHeight,int movingX, int movingY);

	public void displayUniqueObjects(World _myWorld, GL2 gl, int offsetCA_x, int offsetCA_y, float offset,
			float stepX, float stepY, float lenX, float lenY, float normalizeHeight)
	{
		displayStaticUniqueObjects(_myWorld, gl, offsetCA_x, offsetCA_y, offset, stepX, stepY, lenX, lenY, normalizeHeight);
		displayDynamicUniqueObjects(_myWorld, gl, offsetCA_x, offsetCA_y, offset, stepX, stepY, lenX, lenY, normalizeHeight);
	}

	/**
	 * Décor statique uniquement (arbres décoratifs, monolithes, ponts…).
	 * Toggle via la touche `o` côté Landscape.
	 */
	public void displayStaticUniqueObjects(World _myWorld, GL2 gl, int offsetCA_x, int offsetCA_y, float offset,
			float stepX, float stepY, float lenX, float lenY, float normalizeHeight)
	{
		for ( int i = 0 ; i < uniqueObjects.size(); i++ )
			uniqueObjects.get(i).displayUniqueObject(_myWorld,gl,offsetCA_x,offsetCA_y,offset,stepX,stepY,lenX,lenY,normalizeHeight);
	}

	/**
	 * Agents (UniqueDynamicObject). Toujours affichés — le toggle `o` ne les
	 * cache pas, sinon on perd la lisibilité de la simulation.
	 */
	public void displayDynamicUniqueObjects(World _myWorld, GL2 gl, int offsetCA_x, int offsetCA_y, float offset,
			float stepX, float stepY, float lenX, float lenY, float normalizeHeight)
	{
		for ( int i = 0 ; i < uniqueDynamicObjects.size(); i++ )
			uniqueDynamicObjects.get(i).displayUniqueObject(_myWorld,gl,offsetCA_x,offsetCA_y,offset,stepX,stepY,lenX,lenY,normalizeHeight);
	}
    
	public int getWidth() { return dxCA; }
	public int getHeight() { return dyCA; }

	public double getMaxEverHeight() { return this.maxEverHeightValue; }
	public double getMinEverHeight() { return this.minEverHeightValue; }

	// ============================================================
	// Système Layer (stack par cellule) — cf. plan de refactor.
	// ============================================================

	private int stackIndex(int x, int y) {
		int xm = ((x % dxCA) + dxCA) % dxCA;
		int ym = ((y % dyCA) + dyCA) % dyCA;
		return xm * dyCA + ym;
	}

	/** Stack live (modifiable) à la cellule (x, y). Tore-aware. Jamais null. */
	public List<Layer> getStack(int x, int y) {
		return cellStacks.get(stackIndex(x, y));
	}

	/** Stack en lecture seule pour les consommateurs externes (rendu, agents). */
	public List<Layer> getStackView(int x, int y) {
		return Collections.unmodifiableList(cellStacks.get(stackIndex(x, y)));
	}

	/** Couche du sommet, ou null si la cellule n'a aucune couche empilée. */
	public Layer topLayer(int x, int y) {
		List<Layer> s = cellStacks.get(stackIndex(x, y));
		return s.isEmpty() ? null : s.get(s.size() - 1);
	}

	/** Somme des épaisseurs de la stack (hauteur cumulée au-dessus du sol natif). */
	public float getStackHeight(int x, int y) {
		float h = 0f;
		for (Layer l : cellStacks.get(stackIndex(x, y))) h += l.thickness;
		return h;
	}

	/**
	 * Altitude monde du sommet de la pile : sol natif × verticalScale + somme
	 * des thickness. C'est le « haut » de la cellule (où marchent les agents,
	 * où sont plantés les arbres).
	 */
	public float getCellTopAltitude(int x, int y) {
		return (float) (getCellHeight(x, y) * verticalScale) + getStackHeight(x, y);
	}

	/**
	 * Pousse une couche au sommet de la stack à (x, y).
	 *  - Auto-merge : si la couche du sommet a le même matériau, on additionne
	 *    les épaisseurs (la pile reste compacte).
	 *  - Troncage : si le sommet de la pile dépasse worldCeiling, l'épaisseur
	 *    ajoutée est réduite pour atteindre exactement worldCeiling. Si déjà
	 *    au plafond, la couche n'est pas poussée (épaisseur effective = 0).
	 *  - State : appliqué tel quel à la couche poussée (ou à la couche
	 *    fusionnée). Utile pour rajeunir une couche LAVA qui se fait écraser
	 *    par une nouvelle coulée fraîche.
	 *
	 * Retourne l'épaisseur effectivement ajoutée (0 si tronquée à plein).
	 */
	/**
	 * Version legacy (sans lineage) — délègue à la version étendue avec null.
	 * Conservée pour les appels non-LAVA (STONE, BASALT, etc.).
	 */
	public float pushLayer(int x, int y, Material material, float thickness, int state) {
		return pushLayer(x, y, material, thickness, state, null);
	}

	/**
	 * Pousse une couche au sommet de la stack avec parenté optionnelle (Module 3,
	 * refonte 2026-05).
	 *
	 * Auto-merge :
	 *  - Si top.material != material : push une nouvelle couche.
	 *  - Si top.material == material non-LAVA : addition d'épaisseur, state remplacé.
	 *  - Si top.material == material == LAVA avec même source : conserve lineage,
	 *    state rajeuni (= min(top.state, newState) en pratique : state du push).
	 *  - Si top.material == material == LAVA avec sources DIFFÉRENTES (Option B) :
	 *    crée une LavaSource virtuelle (eruptionId=-1) avec pondération par
	 *    épaisseur (hueShift, satFactor, pressure tous moyennés).
	 *    generation devient le min des deux.
	 *
	 * @param lineage parenté du bloc poussé. Doit être non-null si material == LAVA,
	 *                null pour les autres matériaux.
	 * @return épaisseur effectivement ajoutée (0 si plafond atteint).
	 */
	public float pushLayer(int x, int y, Material material, float thickness, int state,
	                       LavaLineage lineage) {
		if (thickness <= 0f) return 0f;
		List<Layer> stack = cellStacks.get(stackIndex(x, y));

		// Troncage au plafond monde.
		float currentAltitude = getCellTopAltitude(x, y);
		float ceilingHeadroom = worldCeiling - currentAltitude;
		if (ceilingHeadroom <= 0f) return 0f;
		float effective = Math.min(thickness, ceilingHeadroom);

		// Auto-merge si même matériau au sommet.
		if (!stack.isEmpty()) {
			Layer top = stack.get(stack.size() - 1);
			if (top.material == material) {
				if (material == Material.LAVA && top.lineage != null && lineage != null) {
					if (top.lineage.source != lineage.source) {
						// Sources différentes → mélange pondéré (Option B du Module 3)
						LavaSource blended = LavaSource.blend(
								top.lineage.source, lineage.source,
								top.thickness, effective, iteration);
						top.lineage.source = blended;
					}
					top.lineage.generation = Math.min(top.lineage.generation, lineage.generation);
				}
				if (material == Material.LAVA) {
					// Règle "Option C état" (choix utilisateur 2026-05-27) : moyenne
					// pondérée par épaisseur — physiquement plus réaliste qu'un
					// remplacement ou un min. Une grosse pile chaude reçoit un petit
					// reflux froid → la pile garde une température proche de l'origine.
					// Inversement, un petit volume jeune sur une grosse pile aged →
					// state = pile aged (le mélange a la température dominante).
					// Calcul AVANT addition d'épaisseur (poids = thicknesses actuelles).
					float total = top.thickness + effective;
					if (total > 0f) {
						top.state = Math.round((top.state * top.thickness + state * effective) / total);
					}
				} else {
					top.state = state;
				}
				top.thickness += effective;
				return effective;
			}
		}
		stack.add(new Layer(material, effective, state, lineage));
		return effective;
	}

	/**
	 * Remplace le matériau et le state de la couche du sommet, en conservant
	 * son épaisseur. Utilisé typiquement pour convertir LAVA → STONE à la
	 * solidification. Auto-merge avec la couche en-dessous si elle a le
	 * nouveau matériau (compaction post-conversion).
	 *
	 * No-op si la stack est vide.
	 */
	public void replaceTopLayer(int x, int y, Material material, int state) {
		List<Layer> stack = cellStacks.get(stackIndex(x, y));
		if (stack.isEmpty()) return;
		Layer top = stack.get(stack.size() - 1);
		// Module 3 : le lineage est CONSERVÉ même quand LAVA → STONE.
		// Module 4 (LavaColorModel) lit la signature couleur de la source
		// pour teinter le minéral.
		top.material = material;
		top.state = state;
		// Compaction : si la couche d'en-dessous a maintenant le même matériau
		// ET un lineage compatible (= même source, ou les deux null),
		// on fusionne. Si les sources diffèrent, on garde 2 couches pour
		// préserver la signature couleur par éruption.
		if (stack.size() >= 2) {
			Layer below = stack.get(stack.size() - 2);
			boolean sameMaterial = below.material == material;
			boolean compatibleLineage =
					(top.lineage == null && below.lineage == null)
					|| (top.lineage != null && below.lineage != null
						&& top.lineage.source == below.lineage.source);
			if (sameMaterial && compatibleLineage) {
				below.thickness += top.thickness;
				below.state = state;
				stack.remove(stack.size() - 1);
			}
		}
	}

	/**
	 * Retire et retourne la couche du sommet. Retourne null si la stack est
	 * vide. Symétrique de pushLayer — utilisé par les processus de destruction
	 * (fonte de pierre dans la lave, érosion future, démolition par agent).
	 */
	public Layer removeTopLayer(int x, int y) {
		List<Layer> stack = cellStacks.get(stackIndex(x, y));
		if (stack.isEmpty()) return null;
		return stack.remove(stack.size() - 1);
	}

}
