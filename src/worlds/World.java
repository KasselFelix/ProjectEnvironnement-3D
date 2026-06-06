
package worlds;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.media.opengl.GL2;

import agents.Agent;
import agents.Loup;
import agents.Mouton;
import agents.Humain;
import agents.Ours;

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
	/** Super-prédateurs (L4) : chassent les loups. */
	public ArrayList<Ours> ours = new ArrayList<Ours>();

	/** Journal d'événements (V6) : compteurs de dommages + notifications. */
	public final EventLog events = new EventLog();

	/** Ticks restants de FLASH de foudre (V8) ; lu par le rendu pour un éclair
	 *  blanc bref. Posé par un impact de foudre, décrémenté côté rendu. */
	public int lightningFlash = 0;

	/**
	 * La case ({@code x}, {@code y}) est-elle bloquée pour {@code mover} par un
	 * autre agent ? Les agents sont des obstacles les uns pour les autres (pas de
	 * superposition) ; la proie laisse toutefois passer son prédateur
	 * ({@link Agent#blocksMovementOf}). Les cadavres et soi-même ne bloquent pas.
	 */
	public boolean cellBlockedByAgent(int x, int y, UniqueDynamicObject mover) {
		for (Agent a : agents) {
			if (a == mover || !a._alive) continue;
			if (a.x == x && a.y == y && a.blocksMovementOf(mover)) return true;
		}
		return false;
	}
	
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

    	// Grille de neige (L7) : surface-state des sommets, vide au départ.
    	this.snowDepth = new double[__dxCA][__dyCA];
    	// Grille d'eau de ruissellement (L7) : vide au départ, alimentée par la pluie.
    	this.waterDepth = new double[__dxCA][__dyCA];

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
    	updateWeather();        // L6 — météo du jour (pluie selon la saison)
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

	// ===== Saisons (L5) =====
	/** Durée d'une saison en jours-jeu ; ≤ 0 = saisons désactivées (été perpétuel). */
	int seasonLengthDays = 3;

	public int getSeasonLengthDays() { return seasonLengthDays; }
	public void setSeasonLengthDays(int d) { this.seasonLengthDays = d; }

	/** Indice du jour-jeu courant (0-based). Un jour = un cycle jour+nuit complet
	 *  = {@code 2 * dureeJour} itérations. */
	public int getCurrentDay() {
		int fullDay = 2 * dureeJour;
		return fullDay <= 0 ? 0 : iteration / fullDay;
	}

	/** Saison courante (L5). Avance d'un cran tous les {@code seasonLengthDays}
	 *  jours-jeu, en boucle PRINTEMPS→ÉTÉ→AUTOMNE→HIVER. Été perpétuel si la durée
	 *  de saison est ≤ 0. */
	public Season currentSeason() {
		if (seasonLengthDays <= 0) return Season.SUMMER;
		int idx = (getCurrentDay() / seasonLengthDays) % Season.values().length;
		return Season.values()[idx];
	}

	/** Multiplicateur de fertilité saisonnier lu par les CA de végétation (L5). */
	public double seasonalFertility() {
		return currentSeason().fertilityFactor;
	}

	// ===== Météo & température (L6) =====
	/** Amortissement de la propagation du feu sous la pluie (0.2 = 5× plus lent). */
	private static final double RAIN_FIRE_DAMP = 0.2;
	/** Écart de température jour↔nuit en °C (±). */
	private static final double DAY_NIGHT_TEMP_SWING = 4.0;
	/** Refroidissement supplémentaire quand il pleut, en °C. */
	private static final double RAIN_TEMP_DROP = 3.0;

	private boolean raining = false;
	private int lastWeatherDay = -1;
	private final java.util.Random weatherRng = new java.util.Random();

	public boolean isRaining() { return raining; }
	public void setRaining(boolean r) { this.raining = r; }

	/** Tire la météo du jour (une fois par jour-jeu) selon la probabilité de
	 *  pluie de la saison. Appelé au début de {@link #step()}. */
	void updateWeather() {
		int day = getCurrentDay();
		if (day != lastWeatherDay) {
			lastWeatherDay = day;
			raining = weatherRng.nextDouble() < currentSeason().rainProbability;
		}
	}

	/** Température approximative du monde en °C (L6) : base saisonnière modulée
	 *  par le jour/nuit (jour plus chaud) et la pluie (plus frais). */
	public double getTemperature() {
		double t = currentSeason().baseTemperatureC;
		t += (jour == 1) ? DAY_NIGHT_TEMP_SWING : -DAY_NIGHT_TEMP_SWING;
		if (raining) t -= RAIN_TEMP_DROP;
		return t;
	}

	/** Facteur multiplicatif de propagation/ignition du feu (L6) : 1.0 par temps
	 *  sec, {@link #RAIN_FIRE_DAMP} sous la pluie. Lu par ForestCA. */
	public double fireSpreadFactor() {
		return raining ? RAIN_FIRE_DAMP : 1.0;
	}

	/** Facteur de refroidissement de la lave lié à la température ambiante (L6) :
	 *  référence 1.0 à 20°C ; par grand froid la lave fige plus vite (jusqu'à 1.6
	 *  vers -10°C), par forte chaleur elle reste fondue plus longtemps (jusqu'à
	 *  0.6 vers 40°C). Borné [0.6, 1.6]. Multiplie le coolingRate de LavaCA. */
	public double lavaCoolingFactor() {
		double f = 1.0 + (20.0 - getTemperature()) * 0.02;
		return Math.max(0.6, Math.min(1.6, f));
	}

	/** Facteur de vitesse lié au froid (L6) : 1.0 au-dessus de 5°C, descend
	 *  linéairement jusqu'à 0.7 à -10°C (les agents s'engourdissent par grand
	 *  froid). Borné [0.7, 1.0]. */
	public double coldSpeedFactor() {
		double t = getTemperature();
		if (t >= 5.0) return 1.0;
		double f = 1.0 - (5.0 - t) / 15.0 * 0.3;   // -10°C → 0.7
		return Math.max(0.7, Math.min(1.0, f));
	}

	// ===== Neige sur les sommets (L7) =====
	/** Épaisseur de neige par cellule (0 = sol nu). Surface-state distinct du
	 *  système Layer (la neige ne s'empile pas avec la pierre/lave) ; lu par le
	 *  rendu pour blanchir les sommets enneigés. */
	protected double[][] snowDepth;

	/** Fraction de {@code maxEverHeight} au-dessus de laquelle la neige peut tenir. */
	private static final double SNOW_LINE_FRAC = 0.55;
	/** Température (°C) sous laquelle la neige s'accumule. */
	private static final double SNOW_FREEZE_C = 0.0;
	/** Température (°C) au-dessus de laquelle la neige fond. */
	private static final double SNOW_MELT_C = 2.0;
	/** Épaisseur de neige max (unités arbitraires de rendu). */
	public static final double SNOW_MAX = 1.0;
	private static final double SNOW_ACCUM_RATE = 0.05;
	private static final double SNOW_MELT_RATE = 0.04;

	public double getSnowDepth(int x, int y) {
		return snowDepth == null ? 0.0 : snowDepth[x % dxCA][y % dyCA];
	}
	public void setSnowDepth(int x, int y, double d) {
		if (snowDepth != null) snowDepth[x % dxCA][y % dyCA] = Math.max(0.0, Math.min(SNOW_MAX, d));
	}

	/**
	 * Loi d'évolution PURE de la neige d'une cellule (L7), testable sans état.
	 * La neige s'accumule sur les hauts sommets quand il gèle, fond quand il fait
	 * doux, et ne tient jamais sous la ligne de neige (altitude normalisée &lt;
	 * {@code snowLineFrac}).
	 *
	 * @param current     épaisseur actuelle
	 * @param tempC       température du monde en °C
	 * @param altitudeNorm altitude normalisée de la cellule ∈ [0,1] (h / maxH)
	 */
	public static double nextSnowDepth(double current, double tempC,
			double altitudeNorm, double snowLineFrac) {
		if (altitudeNorm < snowLineFrac) {
			return Math.max(0.0, current - SNOW_MELT_RATE);   // trop bas : fond/pas de neige
		}
		if (tempC <= SNOW_FREEZE_C) return Math.min(SNOW_MAX, current + SNOW_ACCUM_RATE);
		if (tempC >= SNOW_MELT_C)   return Math.max(0.0, current - SNOW_MELT_RATE);
		return current;   // zone tampon [0, 2]°C : stable
	}

	/** Met à jour toute la grille de neige selon la température et l'altitude (L7).
	 *  Appelée à cadence réduite par {@link worlds.WorldOfCells} (la neige varie
	 *  lentement). No-op si la grille n'est pas allouée. */
	public void stepSnow() {
		if (snowDepth == null) return;
		double maxH = getMaxEverHeight();
		if (maxH <= 0) return;
		double t = getTemperature();
		for (int x = 0; x < dxCA; x++)
			for (int y = 0; y < dyCA; y++) {
				double hN = getCellHeight(x, y) / maxH;
				snowDepth[x][y] = nextSnowDepth(snowDepth[x][y], t, hN, SNOW_LINE_FRAC);
			}
	}

	// ===== Eau qui s'écoule (L7) : ruissellement / rivières =====
	/** Hauteur d'eau de surface par cellule (en UNITÉS DE HAUTEUR de cellule,
	 *  comme getCellHeight) qui ruisselle vers les voisins plus bas. Distincte de
	 *  l'océan (cellHeight < 0) : c'est l'eau MOBILE posée sur la terre. */
	protected double[][] waterDepth;
	private double[][] waterScratch;

	/** Fraction de la dénivelée transférée par tick vers le voisin le plus bas. */
	private static final double WATER_FLOW_RATE = 0.5;
	/** Évaporation de l'eau de surface sur terre par tick (lente). */
	private static final double WATER_EVAP = 0.0008;
	/** Eau ajoutée par tick de pluie sur chaque cellule de terre (source L6→L7). */
	private static final double RAIN_WATER_PER_TICK = 0.02;

	public double getWaterDepth(int x, int y) {
		return waterDepth == null ? 0.0 : waterDepth[x % dxCA][y % dyCA];
	}
	public void setWaterDepth(int x, int y, double d) {
		if (waterDepth != null) waterDepth[x % dxCA][y % dyCA] = Math.max(0.0, d);
	}
	public void addWater(int x, int y, double d) {
		if (waterDepth != null) {
			int i = x % dxCA, j = y % dyCA;
			waterDepth[i][j] = Math.max(0.0, waterDepth[i][j] + d);
		}
	}

	/**
	 * Un pas d'écoulement de l'eau de surface (L7). Pour chaque cellule portant de
	 * l'eau, on transfère une fraction du surplus d'altitude (terrain + eau) vers
	 * le voisin cardinal le plus BAS — l'eau coule donc des crêtes vers les
	 * cuvettes, formant ruisseaux et flaques. Calcul en deux temps (deltas dans un
	 * scratch, puis application) pour être indépendant de l'ordre de balayage.
	 * L'eau qui atteint l'océan (cellHeight &lt; 0) est absorbée ; l'eau sur terre
	 * s'évapore lentement. Couplé à la pluie (L6) comme source quand il pleut.
	 */
	public void stepWater() {
		if (waterDepth == null) return;
		if (waterScratch == null) waterScratch = new double[dxCA][dyCA];

		// Source : la pluie alimente le ruissellement sur la terre ferme.
		if (isRaining()) {
			for (int x = 0; x < dxCA; x++)
				for (int y = 0; y < dyCA; y++)
					if (getCellHeight(x, y) >= 0) waterDepth[x][y] += RAIN_WATER_PER_TICK;
		}

		for (int x = 0; x < dxCA; x++)
			for (int y = 0; y < dyCA; y++) waterScratch[x][y] = 0.0;

		final int[] dxs = { 0, 0, 1, -1 };
		final int[] dys = { -1, 1, 0, 0 };
		for (int x = 0; x < dxCA; x++)
			for (int y = 0; y < dyCA; y++) {
				double water = waterDepth[x][y];
				if (water <= 0.0) continue;
				double totalHere = getCellHeight(x, y) + water;
				// voisin cardinal le plus bas (terrain + eau), tore-aware.
				int bestNx = -1, bestNy = -1;
				double bestTotal = totalHere;
				for (int k = 0; k < 4; k++) {
					int nx = ((x + dxs[k]) % dxCA + dxCA) % dxCA;
					int ny = ((y + dys[k]) % dyCA + dyCA) % dyCA;
					double tn = getCellHeight(nx, ny) + waterDepth[nx][ny];
					if (tn < bestTotal) { bestTotal = tn; bestNx = nx; bestNy = ny; }
				}
				if (bestNx < 0) continue;   // cuvette locale : l'eau reste (flaque)
				// Transfert : moitié de la dénivelée, borné à l'eau disponible.
				double move = Math.min(water, (totalHere - bestTotal) * 0.5) * WATER_FLOW_RATE;
				if (move <= 0) continue;
				waterScratch[x][y]       -= move;
				waterScratch[bestNx][bestNy] += move;
			}

		// Application + drainage océan + évaporation terre.
		for (int x = 0; x < dxCA; x++)
			for (int y = 0; y < dyCA; y++) {
				double d = waterDepth[x][y] + waterScratch[x][y];
				if (getCellHeight(x, y) < 0) d = 0.0;          // absorbé par l'océan
				else d = Math.max(0.0, d - WATER_EVAP);        // évaporation lente
				waterDepth[x][y] = d;
			}
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
	abstract public int getNbours();
	abstract public void setNbours(int nbours);
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
