package applications.simpleworld;


import cellularautomata.CellularAutomataDouble;
import cellularautomata.CellularAutomataZ;
import worlds.World;

public class EauCAZ extends CellularAutomataZ {

	
	CellularAutomataDouble _cellsHeightValuesCA;
	
	World world;
	
	int nivE=0;
	int vEau=100;//vitesse
	int cptVitesse=0;
	
	float stepZ;//[hateur min,hauteur max] /100
	public static int sourceX;
	public static int sourceY;
	public static float sourceZ;
	
	public EauCAZ ( World __world, int __dx , int __dy, int __dz, CellularAutomataDouble cellsHeightValuesCA )
	{
		super(__dx,__dy,__dz,false ); // buffering must be true.
		
		_cellsHeightValuesCA = cellsHeightValuesCA;
		
		this.world = __world;
	}
	
	public void init()
	{
		for ( int x = 0 ; x != _dx ; x++ ){
	    	for ( int y = 0 ; y != _dy ; y++ )
	    	{
	    		for ( double z =world.getCellHeight(x, y); z < nivE; z+=0.01){
	    			this.setCellState(x, y,(int)((z-world.getCellHeight(x, y))*100), 1);
	    		}
	    	}
    	}
    	this.swapBuffer();

	}
	
	/**
	public void step()
	{
		//MISE a jour asynchrone randomiser
		
		int cptVitesse=0;
    	Collections.shuffle(world.list);
    	for(int d=0;d<world.list.size();d++){
    		int x=world.list.get(d)%_dx;
			int y=world.list.get(d)/_dy;
			for (double z =world.getCellHeight(x, y); z <= world.getMaxEverHeight(); z+=0.01){
				
				int cellState=this.getCellState(x, y,world.getNivSolCAValue(x,y));
				double height=world.getCellHeight(x,y);
				
				int xe=(x+1+_dx)%_dx;
				int xo=(x-1+_dx)%_dx;
				int yn=(y-1+_dy)%_dy;
				int ys=(y+1+_dy)%_dy;
				
    			if(cellState!=0 )
    			{
    				if(this.getCellState(x,ys,(int)((z-height)*100-1))==1){
    				}
    				else if(z>height){
    					this.setCellState(x,ys,(int)((z-height)*100-1),1);
    				}
    				else{
    					if(cptVitesse<vEau){
		    				int dir=(int)(Math.random()*4);
		    				switch (dir){
			    				case 0:
			    					if( world.getCellHeight(x,yn)*Landscape.nHeihtCommonObj+world.getNivSolCAValue(x,yn)<= height*Landscape.nHeihtCommonObj+world.getNivSolCAValue(x,y)
				    					&& (world.getStoneCAValue(x,yn) ==0 && world.getCellHeight(x, yn)==height)
				    					&& this.getCellState(x,yn,(int)((z-world.getCellHeight(x, yn))*100))==0)
				    					{
				    						this.setCellState(x,yn,world.getNivSolCAValue(x,yn),cellState);
				    						vEau++;
				    						break;
				    					}
			    				case 1:
			    					if(world.getCellHeight(x,ys) <= height
			    						&& world.getStoneCAValue(x,ys) ==0
			    						&& this.getCellState(x,ys,world.getNivSolCAValue(x,ys))==0)
			    						{
			    							this.setCellState(x,ys,world.getNivSolCAValue(x,ys),cellState);
			    							vEau++;
			    							break;
			    						}
			    				case 2:
			    					if(world.getCellHeight(xe,y) <= height
			    							//&& tabD[xe][y][world.getCellHeight(xe,y)+1]==0
			    							&& world.getStoneCAValue(xe,y) ==0
			    							&& this.getCellState(xe,y,world.getNivSolCAValue(xe,y))==0)
			    						{
			    							this.setCellState(xe,y,world.getNivSolCAValue(xe,y),cellState);
			    							vEau++;
			    							break;
			    						}
			    				case 3:
			    					if( world.getCellHeight(xo,y) <= height
			    						//&& tabD[xo][y][world.getCellHeight(xo,y)+1]==0
			    						&& world.getStoneCAValue(xo,y) ==0
			    						&& this.getCellState(xo,y,world.getNivSolCAValue(xo,y))==0)
			    						{
			    							this.setCellState(xo,y,world.getNivSolCAValue(xo,y),cellState);
			    							vEau++;
			    						}
		    				}
    					}
    				}
    				
    				float color[] = new float[3];
	    			switch ( this.getCellState(x, y ,world.getNivSolCAValue(x,y)) )
	    			{
	    				case 0:
	    					world.colorInit(x,y,color);
	    					break;
	    				case 1:
	    					color[0] = (float)((52.f/255.f)-((26.f/255.f)*height)/world.getMinEverHeight());
	    					color[1] = (float)((168.f/255.f)-((84.f/255.f)*height)/world.getMinEverHeight());
	    					color[2] = (float)((180.f/255.f)-((90.f/255.f)*height)/world.getMinEverHeight());
	    				default :
		    					color[0] = 0.5f;
		    					color[1] = 0.5f;
		    					color[2] = 0.5f;
		    					System.out.print("cannot interpret CA state: " + this.getCellState(x, y,world.getNivSolCAValue(x,y)));
		    					System.out.println(" (at: " + x + "," + y + " -- height: " + this.world.getCellHeight(x,y) + " )");
	    				}
			    	this.world.cellsColorValues.setCellState(x, y, color);
    			}
    		}
    	}
    	this.swapBuffer();
	}

	/**/
}

