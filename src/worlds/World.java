
package worlds;

import java.util.ArrayList;
import javax.media.opengl.GL2;

import applications.simpleworld.Agent;
import applications.simpleworld.Loup;
import applications.simpleworld.Mouton;
import applications.simpleworld.Humain;

import cellularautomata.*;

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
	int dureeJour=2000;// nombre d'iteration d'un jour et d'une nuit
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
    	initCellularAutomata(__dxCA,__dyCA,landscape);

    }
    
    
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
    abstract public int getNivSolCAValue(int x, int y);
    abstract public int getHerbeCAValue(int x, int y);
    abstract public int getForestCAValue(int x, int y); 
    abstract public int getLaveCAValue(int x, int y);
    abstract public int getStoneCAValue(int x, int y);
    
    abstract public void setForestCAValue(int x, int y, int state);
    abstract public void setHerbeCAValue(int x, int y, int state);
    abstract public void setLaveCAValue(int x, int y, int state);
    abstract public void setStoneCAValue(int x, int y, int state);
    abstract public void setNivSolCAValue(int x, int y,int state);
    
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
    	for ( int i = 0 ; i < uniqueObjects.size(); i++ )
    		uniqueObjects.get(i).displayUniqueObject(_myWorld,gl,offsetCA_x,offsetCA_y,offset,stepX,stepY,lenX,lenY,normalizeHeight);
    	for ( int i = 0 ; i < uniqueDynamicObjects.size(); i++ )
    		uniqueDynamicObjects.get(i).displayUniqueObject(_myWorld,gl,offsetCA_x,offsetCA_y,offset,stepX,stepY,lenX,lenY,normalizeHeight);
	}
    
	public int getWidth() { return dxCA; }
	public int getHeight() { return dyCA; }

	public double getMaxEverHeight() { return this.maxEverHeightValue; }
	public double getMinEverHeight() { return this.minEverHeightValue; }
	

}
