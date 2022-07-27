package applications.simpleworld;


import cellularautomata.CellularAutomataDouble;
import cellularautomata.CellularAutomataZ;
import worlds.World;

public class LaveCAZ extends CellularAutomataZ {

	
	CellularAutomataDouble _cellsHeightValuesCA;
	
	World world;
	
	double perruption=0.02;//probabilite d'une erruption
	int vLave=0;//vitesse d'etalement lave
	public static int tmpSoLave=200;//iteration avant solidification de la lave
	int bErupt=0;// 1 si erruption
	float stepZ;//[hateur min,hauteur max] /100 
	public static int rVolcan;//rayon delimite perimetre coulee de lave
	
	public static int sourceX;
	public static int sourceY;
	public static float sourceZ;
	
	public LaveCAZ ( World __world, int __dx , int __dy, int __dz, CellularAutomataDouble cellsHeightValuesCA )
	{
		super(__dx,__dy,__dz,false ); // buffering must be true.
		
		_cellsHeightValuesCA = cellsHeightValuesCA;
		
		this.world = __world;
	}
	
	public void init()
	{	/*
		int s=0;
		stepZ=(float)((world.getMaxEverHeight()-world.getMinEverHeight())/100);
		
		//rVolcan=(int) (Math.sqrt(((_dx*_dy)/2)/(2*Math.PI)));
		rVolcan=80;
		for ( int x = 0 ; x != _dx ; x++ ){
    		for ( int y = 0 ; y != _dy ; y++ )
    		{
    			float height = (float) world.getCellHeight(x, y);
    			if (height<=world.getMinEverHeight()+stepZ*85 
    				&& height>=world.getMinEverHeight()+stepZ*75 && s==0){
	    			sourceX=x;//75
	    			sourceY=y;//40
	    			sourceZ=height;
	    			s=1;
	    			
	    			for ( int i =(sourceX-11+_dx)%_dx  ;((sourceX-11+_dx)%_dx <= i && i <=(sourceX+11+_dx)%_dx) || (_dx-12<=i || i <=13); i=(i+1+_dx)%_dx ){
	    		    	for ( int j =(sourceY-11+_dy)%_dy ;(( sourceY-11+_dy)%_dy <= j && j <=(sourceY+11+_dy)%_dy) || (_dy-12<= j || j <=13);j=(j+1+_dy)%_dy){
	    		    		//pose colonne de pierre
	    		    		if((int)(world.distance(i,j,sourceX,sourceY))<=10){
	    		    			if (height<=world.getMinEverHeight()+stepZ*80 && height>=world.getMinEverHeight()+stepZ*75 ){
		    		    			//if(tabD[i][j][z]!=0){
		    		    				//tabD[i][j][z]=0;
		    		    			//}
		    		    			world.setStoneCAValue(x,y,1);
		    		    			world.setNivSolCAValue(x,y,world.getNivSolCAValue(x,y)+1);
	    		    				//solid[i][j][z]=1;
	    		    				//if(z==height+1)setNivSOL(i,j,1);
	    		    			}
	    		    		}
	    		    		//creuse un cratere
	    		    		if(world.distance(i,j,sourceX,sourceY)<10 
	    		    				&& height<=world.getMinEverHeight()+stepZ*95 
	    		    				&& height>=world.getMinEverHeight()+stepZ*75 ){//&& stone[i][j][sourceZ]==0
				    			//tabD[i][j][height]=0;
				    			//solid[i][j][height]=0;
	    		    			//world.setStoneCAValue(i,j,0);
	    		    			this.setCellState(i, j,world.getNivSolCAValue(x,y), 1);
				    			//setNivSOL(i,j,-1);
	    		    		}
	    		    	}
	    			}
	    		}
    		}
    	}
    	this.swapBuffer();
    	*/
	}
	/**
	public void step()
	{
		//MISE a jour asynchrone randomiser
		//
		if(bErupt==500)bErupt=0;
		if(Math.random()<perruption && bErupt==0)bErupt=1;
		//
    	Collections.shuffle(world.list);
    	for(int d=0;d<world.list.size();d++){
    		int x=world.list.get(d)%_dx;
			int y=world.list.get(d)/_dy;
			
				int cellState=this.getCellState(x, y,world.getNivSolCAValue(x,y));
				double height=world.getCellHeight(x,y);
				
				int xe=(x+1+_dx)%_dx;
				int xo=(x-1+_dx)%_dx;
				int yn=(y-1+_dy)%_dy;
				int ys=(y+1+_dy)%_dy;
				//
				if(world.distance(x,y,sourceX,sourceY)<=10 
						&& height<=world.getMinEverHeight()+stepZ*80
						&& height>=world.getMinEverHeight()+stepZ*75
						//&& solid[x][y][sourceZ]==0 
						 ){
					this.setCellState(x,y,world.getNivSolCAValue(x,y),1);//source lave
	    		}
				if(bErupt==1 && (int)(world.distance(x,y,sourceX,sourceY))==10 ){
	    			//if(tabD[x][y][sourceZ+1]==0)
					this.setCellState(x,y,world.getNivSolCAValue(x,y),1);//fait deborder la source
	    		}//
    			
    			if(cellState!=0 )
    			{	//solidification
    				
    				if(height < 0)this.setCellState(x,y,this.getCellState(x, y,world.getNivSolCAValue(x,y)),+2);
    				else this.setCellState(x,y,this.getCellState(x, y,world.getNivSolCAValue(x,y)),+1);
   
    				if(cellState>=tmpSoLave)
    				{
    					this.setCellState(x,y,world.getNivSolCAValue(x,y),0);
    					if(height < 0){
    						//tabE [x][y][height+1]=0;
    						world.setStoneCAValue(x,y,1);
    					}
    					else world.setStoneCAValue(x,y,1);
    					//solid[x][y][height+1]=1;
    					world.setNivSolCAValue(x,y,world.getNivSolCAValue(x,y)+1);
    				}else{//parametre coulee de lave : vitesse/limite temp/limite distance
    					if(vLave<10 && cellState<=100 && world.distance(x,y,sourceX,sourceY)<rVolcan)
    					{
    					//if(vLave<100 && x<(sourceX+rVolcan)-(y-sourceY) && x>(sourceX-rVolcan)+(sourceY-y)  &&  y>(sourceY-rVolcan)-(sourceX-x) && y<(sourceY+rVolcan)+(x-sourceX) ){
    								
    						int dir=(int)(Math.random()*4);
    						switch (dir){
    							case 0:
    								if( world.getCellHeight(x,yn)*Landscape.nHeihtCommonObj+world.getNivSolCAValue(x,yn)<= height*Landscape.nHeihtCommonObj+world.getNivSolCAValue(x,y)
    									//&& tabD[x][yn][world.getCellHeight(x,yn)+1]==0
    									&& world.getStoneCAValue(x,yn) ==0
    									&& this.getCellState(x,yn,world.getNivSolCAValue(x,yn))==0)
    									{
    										this.setCellState(x,yn,world.getNivSolCAValue(x,yn),cellState);
    										vLave++;
    										break;
    									}
    							case 1:
    								if(world.getCellHeight(x,ys) <= height
    									//&& tabD[x][ys][world.getCellHeight(x,ys)+1]==0
    									&& world.getStoneCAValue(x,ys) ==0
    									&& this.getCellState(x,ys,world.getNivSolCAValue(x,ys))==0)
    									{
    										this.setCellState(x,ys,world.getNivSolCAValue(x,ys),cellState);
    										vLave++;
    										break;
    									}
    							case 2:
    								if(world.getCellHeight(xe,y) <= height
    									//&& tabD[xe][y][world.getCellHeight(xe,y)+1]==0
    									&& world.getStoneCAValue(xe,y) ==0
    									&& this.getCellState(xe,y,world.getNivSolCAValue(xe,y))==0)
    									{
    										this.setCellState(xe,y,world.getNivSolCAValue(xe,y),cellState);
    										vLave++;
    										break;
    									}
    							case 3:
    								if( world.getCellHeight(xo,y) <= height
    									//&& tabD[xo][y][world.getCellHeight(xo,y)+1]==0
    									&& world.getStoneCAValue(xo,y) ==0
    									&& this.getCellState(xo,y,world.getNivSolCAValue(xo,y))==0)
    									{
    										this.setCellState(xo,y,world.getNivSolCAValue(xo,y),cellState);
    										vLave++;
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
	    				default :
		    				if(cellState<=100){
		    					color[0] = (float)(((55*height)/world.getMaxEverHeight()+200)/255);
		    					color[1] = (float)((150*world.distance((int)x,(int)y,sourceX,sourceY)/rVolcan)/255);
		    					color[2] = 0.0f;
		    				}
		    				else if(cellState>100){
		    					double vr=((55*height)/world.getMaxEverHeight()+200);
		    					double vg=((150*world.distance((int)x,(int)y,sourceX,sourceY))/rVolcan);
		    					//double vg=((62*world.distance((int)x,(int)y,sourceX,sourceY))/rVolcan);
		    					double vb=0;
		    					double pr=((172*height)/world.getMaxEverHeight());
		    					double pg=((177*height)/world.getMaxEverHeight());
		    					double pb=((181*height)/world.getMaxEverHeight());
		    					double r=vr+((pr-vr)*(cellState-100))/(tmpSoLave-100);
		    					double g=vg+((pg-vg)*(cellState-100))/(tmpSoLave-100);
		    					double b=vb+((pb-vb)*(cellState-100))/(tmpSoLave-100);
		    					color[0] = (float)(r/255);
		    					color[1] = (float)(g/255);
		    					color[2] = (float)(b/255);  
		    				}
		    				else{
		    					color[0] = 0.5f;
		    					color[1] = 0.5f;
		    					color[2] = 0.5f;
		    					System.out.print("cannot interpret CA state: " + this.getCellState(x, y,world.getNivSolCAValue(x,y)));
		    					System.out.println(" (at: " + x + "," + y + " -- height: " + this.world.getCellHeight(x,y) + " )");
		    				}
	    				}
			    	this.world.cellsColorValues.setCellState(x, y, color);
    			}
    		}
    	
    	if(bErupt!=0)bErupt++;
		vLave=0;
		
    	this.swapBuffer();
	}
	/**/
	
}
