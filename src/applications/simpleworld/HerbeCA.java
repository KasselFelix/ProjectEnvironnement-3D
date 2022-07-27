package applications.simpleworld;


import java.util.Collections;

import cellularautomata.CellularAutomataDouble;
import cellularautomata.CellularAutomataInteger;
import worlds.World;

public class HerbeCA extends CellularAutomataInteger {

	
	CellularAutomataDouble _cellsHeightValuesCA;
	
	World world;
	double dherbe = 0.55; //0.55; // densite herbe
	double pF=0.00003;//probabilite de prendre feu pour les herbes
	double pH=0.000006;// 0.00006// probabilite d'appHrition des herbes
	int tDispertion=20;// temps avant dispertion des cendres
	int NbHerbe=0;
	
	public HerbeCA ( World __world, int __dx , int __dy, CellularAutomataDouble cellsHeightValuesCA )
	{
		super(__dx,__dy,false ); // buffering must be true.
		
		_cellsHeightValuesCA = cellsHeightValuesCA;
		
		this.world = __world;
	}
	
	public void init()
	{
		for ( int x = 0 ; x != _dx ; x++ )
    		for ( int y = 0 ; y != _dy ; y++ )
    		{
    			if ( _cellsHeightValuesCA.getCellState(x,y) >= 0 )
    			{
    				if ( Math.random() < dherbe 
    						&& world.getCellHeight(x,y)>=world.getMaxEverHeight()/12 
    						&& world.getCellHeight(x,y)<=world.getMaxEverHeight()*0.7){
    					this.setCellState(x, y, 1); // grass
    					NbHerbe+=1;
    				}
    				else
    					this.setCellState(x, y, 0); // empty
    			}
    			else
    			{
    				this.setCellState(x, y, -1); // water (ignore)
    			}
    		}
    	this.swapBuffer();

	}

	public void step()
	{
		//MISE a jour asynchrone randomiser
    	Collections.shuffle(world.list);
    	for(int d=0;d<world.list.size();d++){
    		int i=world.list.get(d)%_dx;
			int j=world.list.get(d)/_dy;
    			if (this.getCellState(i, j)>=0 &&  this.getCellState(i,j)<= 3+tDispertion)
    			{	
    				// Pour une case sans herbe
    				if ( this.getCellState(i,j) == 0
    						&& world.getLaveCAValue(i, j)==0){ 
    					if(Math.random()<pH 
    							&& world.getCellHeight(i,j)>=world.getMaxEverHeight()/12
    							&& world.getCellHeight(i,j)<=world.getMaxEverHeight()*0.7 ){
    						this.setCellState(i,j,1);
    						/*solid[x][y][]=1;*/
    						NbHerbe+=1;
    					}
	    			}
    				//pour un herbe en cendre
    				else if ( this.getCellState(i,j) == 3+tDispertion){
	    				this.setCellState(i,j,0); //dispertion
	    			}
    				// Pour une case avec herbe
	    			else if ( this.getCellState(i,j) == 1 ) // grass
	    			{
	    				if(world.getLaveCAValue(i, j)!=0){
	    					this.setCellState(i,j,2);
						}else{
		    				// check if neighbors are burning
		    				if ( 
		    						this.getCellState( (i+_dx-1)%(_dx) , j ) == 2 ||
		    						this.getCellState( (i+_dx+1)%(_dx) , j ) == 2 ||
		    						this.getCellState( i , (j+_dy+1)%(_dy) ) == 2 ||
		    						this.getCellState( i , (j+_dy-1)%(_dy) ) == 2 ||
		    						world.getLaveCAValue( (i+_dx-1)%(_dx) , j ) != 0 ||
		    						world.getLaveCAValue( (i+_dx+1)%(_dx) , j ) != 0 ||
		    						world.getLaveCAValue( i , (j+_dy+1)%(_dy) ) != 0 ||
		    						world.getLaveCAValue( i , (j+_dy-1)%(_dy) ) != 0 ||
		    						world.getForestCAValue( (i+_dx-1)%(_dx) , j ) == 2 ||
		    						world.getForestCAValue( (i+_dx+1)%(_dx) , j ) == 2 ||
		    						world.getForestCAValue( i , (j+_dy+1)%(_dy) ) == 2 ||
		    						world.getForestCAValue( i , (j+_dy-1)%(_dy) ) == 2
		    					)
		    				{
		    					this.setCellState(i,j,2);
		    					NbHerbe-=1;
		    				}
		    				else
		    					if ( Math.random() < pF ) // spontaneously take fire ?
		    					{
		    						this.setCellState(i,j,2);
		    						NbHerbe-=1;
		    					}
		    					else
		    					{
		    						this.setCellState(i,j,1); // copied unchanged
		    					}
						}
	    			}
    				// Pour une case avec herbe en feu
	    			else if ( this.getCellState(i,j) == 2)
	    			{
	        				if ( this.getCellState( i , j ) == 2 ) // burning?
	        				{
	        					this.setCellState(i,j,3); // burnt
	        				}
	        				else
	        				{
	        					this.setCellState(i,j, this.getCellState(i,j) ); // copied unchanged
	        				}
	    			}
	    			else{
	    				if ( this.getCellState(i,j)>=3 && this.getCellState(i,j) < 3+tDispertion)
	    					this.setCellState(i,j,this.getCellState(i,j)+1);
	    			}
	    			
    				/**
	    			float color[] = new float[3];
	    			switch ( this.getCellState(i, j) )
	    			{
	    				case 0:
	    					world.colorInit(i,j,color);
	    					break;
	    				case 1:
	    					color[0] = 0.f;
	    					color[1] = (float)((220*0.6/255)+(220*0.4/255)*(this.world.getCellHeight(i,j)/this.world.getMaxEverHeight()));
	    					color[2] = 0.f;
	    					break;
	    				case 2: // burning grass
	    					if(Math.random()<0.5){
		    					color[0] = 255/255f;
		    					color[1] = 40/255f;
		    					color[2] = 0f;
	    					}else{
	    						color[0] = 255/255f;
		    					color[1] = 206/255f;
		    					color[2] = 0f;
	    					}
	    					break;
	    				case 3: // burnt grass
	    					color[0] = 0.f;
	    					color[1] = 0.f;
	    					color[2] = 0.f;
	    					break;
	    				default:
	    					if (this.getCellState(i, j)<0 &&  this.getCellState(i,j)> 3+tDispertion){
	    					color[0] = 0.5f;
	    					color[1] = 0.5f;
	    					color[2] = 0.5f;
	    					System.out.print("cannot interpret CA state: " + this.getCellState(i, j));
	    					System.out.println(" (at: " + i + "," + j + " -- height: " + this.world.getCellHeight(i,j) + " )");
	    					}else{// burnt grass
	    						float cinit[]=new float[3];
	    						world.colorInit(i,j,cinit);
	    						color[0] = (cinit[0]*getCellState(i, j))/(3+tDispertion);
		    					color[1] = (cinit[1]*getCellState(i, j))/(3+tDispertion);
		    					color[2] = (cinit[2]*getCellState(i, j))/(3+tDispertion);
	    					}
	    			}	   
	    			this.world.cellsColorValues.setCellState(i, j, color);
	    			/**/
    			}
    		}
    	this.swapBuffer();
	}
}
