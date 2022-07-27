package applications.simpleworld;

import java.util.Collections;

import cellularautomata.CellularAutomataDouble;
import cellularautomata.CellularAutomataInteger;
import worlds.World;

public class LaveCA extends CellularAutomataInteger {

	
	CellularAutomataDouble _cellsHeightValuesCA;
	
	World world;
	
	double pErruption=0.002;//probabilite d'une erruption//
	public static int tmpSoLave=150;//iteration avant solidification total de la lave
	public static int debSoLave=50;// iteration ou debute la solidification de la lave
	int rCratere=5;//rayon du cratere
	public static int rVolcan=5;//rayon delimite perimetre coulee de lave
	int vLave=100;//vitesse d'etalement lave
	int tmpNewErruption=500;// iterration avant prochaine eruption
	
	private static int bErupt=0;// 1 si erruption
	float stepZ;//[hateur min,hauteur max] /100
	int cptVitesse=0;
	
	public static int sourceX;
	public static int sourceY;
	public static float sourceZ;
	
	public LaveCA ( World __world, int __dx , int __dy, CellularAutomataDouble cellsHeightValuesCA )
	{
		super(__dx,__dy,false ); // buffering must be true.
		
		_cellsHeightValuesCA = cellsHeightValuesCA;
		
		this.world = __world;
		stepZ=(float)((world.getMaxEverHeight()-world.getMinEverHeight())/100);
		rVolcan=(int) (Math.sqrt(((_dx*_dy)/2)/(2*Math.PI)));
	}
	
	public void init()
	{
		int s=0;
		/*
		for ( int x = 0 ; x != _dx ; x++ )
    		for ( int y = 0 ; y != _dy ; y++ ){
    	/**/
		Collections.shuffle(world.list);
    	for(int d=0;d<world.list.size();d++){
    		int x=world.list.get(d)%_dx;
			int y=world.list.get(d)/_dy;
    	/**/		
    			float height = (float) world.getCellHeight(x, y);
    			if (height<=world.getMinEverHeight()+stepZ*100 
    				&& height>=world.getMinEverHeight()+stepZ*90 && s==0){
	    			sourceX=x;//50
	    			sourceY=y;//75
	    			sourceZ=height;
	    			s=1;
	    			
	    		}
    	}
    	this.swapBuffer();

	}

	public void step()
	{
		//MISE a jour asynchrone randomiser
		/**/
		if(getbErupt()==tmpNewErruption)setbErupt(0);
		if(Math.random()<pErruption && getbErupt()==0)setbErupt(1);
		/**/
    	Collections.shuffle(world.list);
    	for(int d=0;d<world.list.size();d++){
    		int x=world.list.get(d)%_dx;
			int y=world.list.get(d)/_dy;
			
				int cellState=this.getCellState(x, y);
				double height=world.getCellHeight(x,y);
				
				int xe=(x+1+_dx)%_dx;
				int xo=(x-1+_dx)%_dx;
				int yn=(y-1+_dy)%_dy;
				int ys=(y+1+_dy)%_dy;
				/*
				if((int)world.distance(x,y,sourceX,sourceY)<=rCratere
						&& height<=world.getMinEverHeight()+stepZ*100
						&& height>=world.getMinEverHeight()+stepZ*90
						//&& solid[x][y][sourceZ]==0 
						 ){
					this.setCellState(x,y,1);//source lave
	    		}
				/**/
				if(getbErupt()==1 && (int)(world.distance(x,y,sourceX,sourceY))<=rCratere ){
	    			
					this.setCellState(x,y,1);//fait deborder la source
	    		}
    			
    			if(cellState > 0 )
    			{	//solidification
    				
    				if(height < 0)this.setCellState(x,y,this.getCellState(x, y)+2);
    				else this.setCellState(x,y,this.getCellState(x, y)+1);
   
    				if(cellState>=tmpSoLave)
    				{
    					this.setCellState(x,y,0);
    					world.setStoneCAValue(x,y,1);
    				}else{//parametre coulee de lave : vitesse/limite temp/limite distance
    					
    					if(cptVitesse<vLave && cellState<=debSoLave && world.distance(x,y,sourceX,sourceY)<rVolcan)
    					{
    					//if(cptVitesse<vLave && cellState<=debSoLaves && x<(sourceX+rVolcan)-(y-sourceY) && x>(sourceX-rVolcan)+(sourceY-y)  &&  y>(sourceY-rVolcan)-(sourceX-x) && y<(sourceY+rVolcan)+(x-sourceX) ){
    								
    						int dir=(int)(Math.random()*4);
    						switch (dir){
    							case 0:
    								if( world.getCellHeight(x,yn)<= height
    									//&& world.getStoneCAValue(x,yn) ==0
    									&& world.getCellHeight(x, yn)>=0
    									&& this.getCellState(x,yn)==0)
    									{
    										this.setCellState(x,yn,cellState);
    										cptVitesse++;
    										break;
    									}
    							case 1:
    								if(world.getCellHeight(x,ys)<= height
    									//&& world.getStoneCAValue(x,ys) ==0
    									&& world.getCellHeight(x, ys)>=0
    									&& this.getCellState(x,ys)==0)
    									{
    										this.setCellState(x,ys,cellState);
    										cptVitesse++;
    										break;
    									}
    							case 2:
    								if(world.getCellHeight(xe,y) <= height
    									//&& world.getStoneCAValue(xe,y) ==0
    									&& world.getCellHeight(xe, y)>=0
    									&& this.getCellState(xe,y)==0)
    									{
    										this.setCellState(xe,y,cellState);
    										cptVitesse++;
    										break;
    									}
    							case 3:
    								if( world.getCellHeight(xo,y) <= height
    									//&& world.getStoneCAValue(xo,y) ==0
    									&& world.getCellHeight(xo, y)>=0
    									&& this.getCellState(xo,y)==0)
    									{
    										this.setCellState(xo,y,cellState);
    										cptVitesse++;
    									}
    						}
    					}
    				}
    				
    				float color[] = new float[3];
	    			switch ( this.getCellState(x, y) )
	    			{
	    				case 0:
	    					world.colorInit(x,y,color);
	    					break;
	    				default :
		    				if(cellState<=debSoLave){
		    					color[0] = (float)(((55*height)/world.getMaxEverHeight()+200)/255);
		    					color[1] = (float)((150*world.distance((int)x,(int)y,sourceX,sourceY)/rVolcan)/255);
		    					color[2] = 0.0f;
		    				}
		    				else if(cellState>debSoLave){
		    					double vr=((55*height)/world.getMaxEverHeight()+200);
		    					double vg=((150*world.distance((int)x,(int)y,sourceX,sourceY))/rVolcan);
		    					double vb=0;
		    					double pr=((172*height)/world.getMaxEverHeight());
		    					double pg=((177*height)/world.getMaxEverHeight());
		    					double pb=((181*height)/world.getMaxEverHeight());
		    					double r=vr+((pr-vr)*(cellState-debSoLave))/(tmpSoLave-debSoLave);
		    					double g=vg+((pg-vg)*(cellState-debSoLave))/(tmpSoLave-debSoLave);
		    					double b=vb+((pb-vb)*(cellState-debSoLave))/(tmpSoLave-debSoLave);
		    					color[0] = (float)(r/255);
		    					color[1] = (float)(g/255);
		    					color[2] = (float)(b/255);  
		    				}
		    				else{
		    					color[0] = 0.5f;
		    					color[1] = 0.5f;
		    					color[2] = 0.5f;
		    					System.out.print("cannot interpret CA state: " + this.getCellState(x, y));
		    					System.out.println(" (at: " + x + "," + y + " -- height: " + this.world.getCellHeight(x,y) + " )");
		    				}
	    				}
			    	this.world.cellsColorValues.setCellState(x, y, color);
    			}
    		}
    	
    	if(getbErupt()!=0)setbErupt(getbErupt() + 1);
		cptVitesse=0;
		
    	this.swapBuffer();
	}

	public static void setbErupt(int bErupt) {
		LaveCA.bErupt = bErupt;
	}

	public int getbErupt() {
		return bErupt;
	}

	
}
