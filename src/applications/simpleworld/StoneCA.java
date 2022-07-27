package applications.simpleworld;

import java.util.Collections;

import cellularautomata.CellularAutomataDouble;
import cellularautomata.CellularAutomataInteger;
import worlds.World;

public class StoneCA extends CellularAutomataInteger {

	
	CellularAutomataDouble _cellsHeightValuesCA;
	
	World world;
	
	public StoneCA ( World __world, int __dx , int __dy, CellularAutomataDouble cellsHeightValuesCA )
	{
		super(__dx,__dy,false ); // buffering false if laveCA is false
		
		_cellsHeightValuesCA = cellsHeightValuesCA;
		
		this.world = __world;
	}
	
	public void init()
	{
		
		for ( int x = 0 ; x != _dx ; x++ ){
    		for ( int y = 0 ; y != _dy ; y++ ){
	    		 this.setCellState(x, y, 0);
    		}
    	}
    	this.swapBuffer();
	}

	public void step()
	{
    	Collections.shuffle(world.list);
    	/*
    	for(int d=0;d<world.list.size();d++){
    		int x=world.list.get(d)%_dx;
			int y=world.list.get(d)/_dy;
			
		/**/
		for ( int x = 0 ; x != _dx ; x++ )
	    	for ( int y = 0 ; y != _dy ; y++ ){
				int cellState=this.getCellState(x, y);
				float height=(float)(world.getCellHeight(x,y));
				/*
				int xe=(x+1+_dx)%_dx;
				int xo=(x-1+_dx)%_dx;
				int yn=(y-1+_dy)%_dy;
				int ys=(y+1+_dy)%_dy;
				/**/
    			if(cellState > 0 )
    			{
    				
    				float color[] = new float[3];
	    			switch (cellState)
	    			{
	    				case 0:
	    					world.colorInit(x,y,color);
	    					break;
	    				case 1:
	    					color[0] = (float)(((172.f/255.f)*height)/world.getMaxEverHeight());
	    					color[1] = (float)(((177.f/255.f)*height)/world.getMaxEverHeight());
	    					color[2] = (float)(((181.f/255.f)*height)/world.getMaxEverHeight());
	    					break;
	    				default :
		    					color[0] = 0.5f;
		    					color[1] = 0.5f;
		    					color[2] = 0.5f;
		    					System.out.print("cannot interpret CA state: " + this.getCellState(x, y));
		    					System.out.println(" (at: " + x + "," + y + " -- height: " + this.world.getCellHeight(x,y) + " )");
	    			}
			    	this.world.cellsColorValues.setCellState(x, y, color);
    			}
    	}
    	this.swapBuffer();
	}

	
}
