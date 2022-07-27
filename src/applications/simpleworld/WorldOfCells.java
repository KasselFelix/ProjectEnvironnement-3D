
package applications.simpleworld;



import java.util.Collections;

import javax.media.opengl.GL2;

import objects.*;
import worlds.World;

public class WorldOfCells extends World {
	
	public int nbloups = 10;//20
	public int nbmoutons =10;//45//20
	public int nbhumains=2;
	int bergerie;
	int wolfHome;
	float nivPlage;
	
	protected NivSolCA nivSolCA;
	protected ForestCA forestCA;
	protected HerbeCA herbeCA;
	protected LaveCA laveCA; 
	protected StoneCA stoneCA;

    public void init ( int __dxCA, int __dyCA, double[][] landscape )
    {
    	super.init(__dxCA, __dyCA, landscape);
    	
    	// add colors
    	
    	for ( int x = 0 ; x < __dxCA ; x++ )
    		for ( int y = 0 ; y < __dyCA ; y++ )
    		{
    			float color[] = new float[3];
	        	colorInit(x,y,color);
	        	this.cellsColorValues.setCellState(x, y, color);
    		}
    	
    	// add some objects
    	
    	Collections.shuffle(this.list);
    	int d=0;
    	bergerie=-1;
    	wolfHome=-1;
    	int testFRACTTREE=0;
    	while(d<this.list.size() ){
    		int x=this.list.get(d)%__dxCA;
			int y=this.list.get(d)/__dyCA;
			if(this.getCellHeight(x, y)==this.getMaxEverHeight() && testFRACTTREE==0){
				uniqueObjects.add(new FractalTree(x,y,this));
				testFRACTTREE=1;
			}
			if(bergerie==-1
				&& y>__dyCA/2
				&& x>__dxCA/2
				&&this.getCellHeight(x, y)>this.getMaxEverHeight()/10 
				&& this.getCellHeight(x, y)<this.getMaxEverHeight()*0.8){
					bergerie=this.list.get(d);
			    	uniqueObjects.add(new Monolith((x-5+__dxCA)%__dxCA,(y-5+__dyCA)%__dyCA,this));
			    	uniqueObjects.add(new Monolith((x-5+__dxCA)%__dxCA,(y+5+__dyCA)%__dyCA,this));
			    	uniqueObjects.add(new Monolith((x+5+__dxCA)%__dxCA,(y-5+__dyCA)%__dyCA,this));
			    	uniqueObjects.add(new Monolith((x+5+__dxCA)%__dxCA,(y+5+__dyCA)%__dyCA,this));
			}
			if(wolfHome==-1
				&& y<__dyCA/2
				&& x<__dxCA/2
				&&this.getCellHeight(x, y)>this.getMaxEverHeight()/10 
				&& this.getCellHeight(x, y)<this.getMaxEverHeight()*0.8){
					wolfHome=this.list.get(d);
					uniqueObjects.add(new Monolith(x,(y+3+__dyCA)%__dyCA,this));
					uniqueObjects.add(new Monolith((x-3+__dxCA)%__dxCA,(y-3+__dyCA)%__dyCA,this));
					uniqueObjects.add(new Monolith((x+3+__dxCA)%__dxCA,(y-3+__dyCA)%__dyCA,this));
			}
			d++;
    	}
    	if(bergerie==-1)bergerie=0;
		if(wolfHome==-1)wolfHome=(__dyCA*__dyCA)-1;
		
		
    	int px=0;
		int py=0;
		for ( int i = 0 ; i != nbhumains; i++ ){
			px=(int)(Math.random()*dxCA);
			py=(int)(Math.random()*dyCA);
			Humain humanA = new Humain(px,py,this);
			humains.add(humanA);
			agents.add(humanA);
			uniqueDynamicObjects.add(humanA);
		}
    	for ( int i = 0 ; i != nbloups; i++ ){
			px=(int)(Math.random()*dxCA);
			py=(int)(Math.random()*dyCA);
			Loup predA=new Loup(px,py,this);
			loups.add(predA);
			agents.add(predA);
			uniqueDynamicObjects.add(predA);
		}
    	for ( int i = 0 ; i != nbmoutons; i++ ){
			px=(int)(Math.random()*dxCA);
			py=(int)(Math.random()*dyCA);
			Mouton preyA=new Mouton(px,py,this);
			moutons.add(preyA);
			agents.add(preyA);
			uniqueDynamicObjects.add(preyA);
		}
    	
    }
    
    public void colorInit(int x, int y, float color[]){

    	float height = (float) this.getCellHeight(x, y);
    	nivPlage=(float)(this.getMaxEverHeight()/10);
    	if ( height >= 0 && height <nivPlage){
    		//sand 
    		color[0] = 254/255f- (20/255f) * height / nivPlage ;
			color[1] = 219/255f- (20/255f) * height / nivPlage ;
			color[2] = 183/255f- (20/255f) * height / nivPlage ;
    	}
    	else if ( height >= nivPlage )
        {
        	// snowy mountains
        	/*
        	color[0] = height / (float)this.getMaxEverHeight();
			color[1] = height / (float)this.getMaxEverHeight();
			color[2] = height / (float)this.getMaxEverHeight();
			/**/
        	
			// green mountains
        	/**/
        	color[0] = height / ( (float)this.getMaxEverHeight() );
			color[1] = 0.9f + 0.1f * height / ( (float)this.getMaxEverHeight() );
			color[2] = height / ( (float)this.getMaxEverHeight() );
			/**/
        }
        else
        {
        	// water
        	/**/
			color[0] = (52/255f)-((26/255f)*height)/(float)this.getMinEverHeight();
			color[1] = (168/255f)-((84/255f)*height)/(float)this.getMinEverHeight();
			color[2] = (180/255f)-((90/255f)*height)/(float)this.getMinEverHeight();
        	/*
        	color[0] = (52/255f)-((13/255f)*height)/(float)this.getMinEverHeight();
			color[1] = (84/255f)-((42/255f)*height)/(float)this.getMinEverHeight();
			color[2] = (90/255f)-((45/255f)*height)/(float)this.getMinEverHeight();
			/**/
        }
    	
	}
    
    protected void initCellularAutomata(int __dxCA, int __dyCA, double[][] landscape)
    {
    	nivSolCA= new NivSolCA(__dxCA,__dyCA,false);
    	stoneCA = new StoneCA(this,__dxCA,__dyCA,cellsHeightValuesCA);// stone in first
    	stoneCA.init();
    	forestCA = new ForestCA(this,__dxCA,__dyCA,cellsHeightValuesCA);
    	forestCA.init();
    	herbeCA = new HerbeCA(this,__dxCA,__dyCA,cellsHeightValuesCA);
    	herbeCA.init();
    	laveCA = new LaveCA(this,__dxCA,__dyCA,cellsHeightValuesCA);
    	laveCA.init();
    }
    
    protected void stepCellularAutomata()
    {
    	if ( iteration%10 == 0 ){
    		forestCA.step();
    		herbeCA.step();
    		laveCA.step();
    		stoneCA.step();
    	}
    }
    
    protected void stepAgents()
    {
    	for ( int i = 0 ; i < humains.size() ; i++ ){
			if(humains.get(i)._alive == false) {
				this.uniqueDynamicObjects.remove((UniqueDynamicObject)this.humains.get(i));
				this.humains.remove(this.humains.get(i));
				this.humains.remove(i);
				nbhumains--;
			}
    	}
    	for ( int i = 0 ; i < loups.size() ; i++ ){
			if(loups.get(i)._alive == false) {
				this.uniqueDynamicObjects.remove((UniqueDynamicObject)this.loups.get(i));
				this.agents.remove(this.loups.get(i));
				this.loups.remove(i);
				nbloups--;
			}
    	}
    	for ( int i = 0 ; i < moutons.size() ; i++ ){
			if(moutons.get(i)._alive == false) {
				this.uniqueDynamicObjects.remove((UniqueDynamicObject)this.moutons.get(i));
				this.agents.remove(this.moutons.get(i));
				this.moutons.remove(i);
				nbmoutons--;
			}
    	}
    	for ( int i = 0 ; i < this.uniqueDynamicObjects.size() ; i++ )
    	{
    		this.uniqueDynamicObjects.get(i).step();
    	}
    	
    }
    
    
	public double distance( int ib,int jb,int ia,int ja){
		/*
		int ib1=ib-_dx,jb1=jb-_dy;int ib2=ib,jb2=jb-_dy;int ib3=ib+_dx,jb3=jb-_dy;
		int ib4=ib-_dx,jb4=jb;							int ib6=ib+_dx,jb6=jb;
		int ib7=ib-_dx,jb7=jb+_dy;int ib8=ib,jb8=jb+_dy;int ib9=ib+_dx,jb6=jb+_dy;
		*/
		double tmp=Double.MAX_VALUE;
		for(int i=-1;i<2;i++){
			ib=ib+i*dxCA;
			for(int j=-1;j<2;j++){
				jb=jb+j*dyCA;
				tmp=Math.min(tmp,Math.abs(Math.sqrt((jb-ja)*(jb-ja)+(ib-ia)*(ib-ia))));
				jb=jb-j*dyCA;
			}
			ib=ib-i*dxCA;
		}
		return tmp;
	}
	
	
	/*
	public boolean stepZ100(int x,int y,double haut, double bas){
		float stepZ=(float)((this.getMaxEverHeight()-this.getMinEverHeight())/100);
		return this.getCellHeight(x, y)<=this.getMinEverHeight()+stepZ*haut && this.getCellHeight(x, y)>=this.getMinEverHeight()+stepZ*bas;
	}*/
	
	// used by the visualization code to call specific object display.
	public int getNivSolCAValue(int x, int y) 
    {
    	return nivSolCA.getCellState(x%dxCA,y%dyCA);
    }
	
    public int getForestCAValue(int x, int y)
    {
    	return forestCA.getCellState(x%dxCA,y%dyCA);
    }
    
    public int getHerbeCAValue(int x, int y)
    {
    	return herbeCA.getCellState(x%dxCA,y%dyCA);
    }
    
    public int getLaveCAValue(int x, int y) 
    {
    	return laveCA.getCellState(x%dxCA,y%dyCA);
    }
    
    public int getStoneCAValue(int x, int y) 
    {
    	return stoneCA.getCellState(x%dxCA,y%dyCA);
    }
    
    // used by the visualization code to call specific object display.
    public void setNivSolCAValue(int x, int y, int state) 
    {
    	nivSolCA.setCellState(x%dxCA,y%dyCA,state);
    }

    public void setForestCAValue(int x, int y, int state)
    {
    	forestCA.setCellState( x%dxCA, y%dyCA, state);
    }
    
    public void setHerbeCAValue(int x, int y, int state)
    {
    	herbeCA.setCellState( x%dxCA, y%dyCA, state);
    }
    
    public void setLaveCAValue(int x, int y, int state)
    {
    	laveCA.setCellState( x%dxCA, y%dyCA, state);
    }
    
    public void setStoneCAValue(int x, int y, int state) 
    {
    	stoneCA.setCellState(x%dxCA,y%dyCA, state);
    }
    
    public int getNbhumains() {
		return nbhumains;
	}

	public void setNbhumains(int nbhumains) {
		this.nbhumains = nbhumains;
	}
    
    public int getNbloups() {
		return nbloups;
	}

	public void setNbloups(int nbloups) {
		this.nbloups = nbloups;
	}

	public int getNbmoutons() {
		return nbmoutons;
	}

	public void setNbmoutons(int nbmoutons) {
		this.nbmoutons = nbmoutons;
	}

	public int getBergerie() {
		return bergerie;
	}

	public void setBergerie(int bergerie) {
		this.bergerie = bergerie;
	}

	public int getWolfHome() {
		return wolfHome;
	}

	public void setWolfHome(int wolfHome) {
		this.wolfHome = wolfHome;
	}

	public void displayObjectTree(World _myWorld, GL2 gl, int cellState, int x,
			int y, double height, float offset,
			float stepX, float stepY, float lenX, float lenY,
			float normalizeHeight, int movingX, int movingY) 
	{
		switch ( cellState )
		{
		case 1: // trees: green, fire, burnt
		case 2:
		case 3:
		case 4:
			Tree.displayObjectAt(_myWorld,gl,cellState, x, y, height, offset, stepX, stepY, lenX, lenY, normalizeHeight,movingX,movingY);
		default:
			// nothing to display at this location.
		}
	}
	
	public void displayObjectGrass(World _myWorld, GL2 gl, int cellState, int x,
			int y, double height, float offset,
			float stepX, float stepY, float lenX, float lenY,
			float normalizeHeight, int movingX, int movingY) 
	{
		switch ( cellState )
		{
		case 1: // grass: green, fire, burnt
		case 2:
		case 3:
		case 4:
			Grass.displayObjectAt(_myWorld,gl,cellState, x, y, height, offset, stepX, stepY, lenX, lenY, normalizeHeight,movingX,movingY);
		default:
			// nothing to display at this location.
		}
	}
	
	public void displayObjectLave(World _myWorld, GL2 gl, int cellState, int x,
			int y, double height, float offset,
			float stepX, float stepY, float lenX, float lenY,
			float normalizeHeight,int movingX, int movingY) 
	{
		if(cellState > 0){
			LaveBlock.displayObjectAt(_myWorld,gl,cellState, x, y, height, offset, stepX, stepY, lenX, lenY, normalizeHeight,movingX,movingY);
		}
	}
	
	public void displayObjectStone(World _myWorld, GL2 gl, int cellState, int x,
			int y, double height, float offset,
			float stepX, float stepY, float lenX, float lenY,
			float normalizeHeight,int movingX, int movingY) 
	{
		if(cellState > 0){
			StoneBlock.displayObjectAt(_myWorld,gl,cellState, x, y, height, offset, stepX, stepY, lenX, lenY, normalizeHeight,movingX,movingY);
		}
	}

	//public void displayObject(World _myWorld, GL2 gl, float offset,float stepX, float stepY, float lenX, float lenY, float heightFactor, double heightBooster) { ... } 
    
   
}
