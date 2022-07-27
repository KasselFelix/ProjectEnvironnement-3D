package objects;

import javax.media.opengl.GL2;

import applications.simpleworld.LaveCA;

import worlds.World;

public class LaveBlock extends CommonObject {

    public static void displayObjectAt(World myWorld, GL2 gl, int cellState, float x, float y, double height, float offset, float stepX, float stepY, float lenX, float lenY, float normalizeHeight,int movingX, int movingY )
    {
        
        
        if ( cellState > 0 )
        {
        	//float altitude=(float)(height * normalizeHeight +myWorld.getNivSolCAValue((int)x+movingX,(int)y+movingY)*3) ;
        	float altitude=(float)(height * normalizeHeight);
        	//au dessus de la pierre si pierre
        	if (myWorld.getStoneCAValue((int)x+movingX, (int)y+movingY)==1) altitude+=3;
        	//hauteur du block selon la position par rapport a la source
    		float h=(float)(3-(2.5*(myWorld.distance((int)(x+movingX),(int)(y+movingY),LaveCA.sourceX,LaveCA.sourceY)/LaveCA.rVolcan)));
    		//float h=3;
    		if(cellState<=LaveCA.debSoLave){
    			gl.glColor3f((float)(((55*height)/myWorld.getMaxEverHeight()+200)/255)+(float)(0.03*Math.random()),
    					(float)(((150*myWorld.distance((int)x+movingX,(int)y+movingY,LaveCA.sourceX,LaveCA.sourceY))/LaveCA.rVolcan)/255)+(float)(0.03*Math.random()),
    					0.0f+(float)(0.03*Math.random()));
    			
			}else{
				double vr=((55*height)/myWorld.getMaxEverHeight()+200);
				double vg=((150*myWorld.distance((int)(x+movingX),(int)(y+movingY),LaveCA.sourceX,LaveCA.sourceY))/LaveCA.rVolcan);
				double vb=0;
				double pr=((172*height)/myWorld.getMaxEverHeight());
				double pg=((177*height)/myWorld.getMaxEverHeight());
				double pb=((181*height)/myWorld.getMaxEverHeight());
				double r=vr+((pr-vr)*(cellState-LaveCA.debSoLave))/(LaveCA.tmpSoLave-LaveCA.debSoLave);
				double g=vg+((pg-vg)*(cellState-LaveCA.debSoLave))/(LaveCA.tmpSoLave-LaveCA.debSoLave);
				double b=vb+((pb-vb)*(cellState-LaveCA.debSoLave))/(LaveCA.tmpSoLave-LaveCA.debSoLave);
				gl.glColor3f((float)(r/255)+(float)(0.02*Math.random()),(float)(g/255) +(float)(0.02*Math.random()),(float)(b/255)+(float)(0.02*Math.random()));
			}
    		
	        
	        gl.glVertex3f( offset+x*stepX-lenX, offset+y*stepY-lenY, altitude);
	        gl.glVertex3f( offset+x*stepX-lenX, offset+y*stepY-lenY, altitude + h);
	        gl.glVertex3f( offset+x*stepX+lenX, offset+y*stepY-lenY, altitude + h);
	        gl.glVertex3f( offset+x*stepX+lenX, offset+y*stepY-lenY, altitude);
	
	        
	        gl.glVertex3f( offset+x*stepX+lenX, offset+y*stepY+lenY, altitude);
	        gl.glVertex3f( offset+x*stepX+lenX, offset+y*stepY+lenY, altitude +h);
	        gl.glVertex3f( offset+x*stepX-lenX, offset+y*stepY+lenY, altitude +h);
	        gl.glVertex3f( offset+x*stepX-lenX, offset+y*stepY+lenY, altitude);
	        
	       
	        gl.glVertex3f( offset+x*stepX+lenX, offset+y*stepY-lenY, altitude);
	        gl.glVertex3f( offset+x*stepX+lenX, offset+y*stepY-lenY, altitude + h);
	        gl.glVertex3f( offset+x*stepX+lenX, offset+y*stepY+lenY, altitude + h);
	        gl.glVertex3f( offset+x*stepX+lenX, offset+y*stepY+lenY, altitude);
	
	        
	        gl.glVertex3f( offset+x*stepX-lenX, offset+y*stepY+lenY, altitude);
	        gl.glVertex3f( offset+x*stepX-lenX, offset+y*stepY+lenY, altitude +h);
	        gl.glVertex3f( offset+x*stepX-lenX, offset+y*stepY-lenY, altitude +h);
	        gl.glVertex3f( offset+x*stepX-lenX, offset+y*stepY-lenY, altitude);
	
	        
	        gl.glVertex3f( offset+x*stepX-lenX, offset+y*stepY-lenY, altitude +h);
	        gl.glVertex3f( offset+x*stepX-lenX, offset+y*stepY+lenY, altitude +h);
	        gl.glVertex3f( offset+x*stepX+lenX, offset+y*stepY+lenY, altitude +h);
	        gl.glVertex3f( offset+x*stepX+lenX, offset+y*stepY-lenY, altitude +h);
	        
	        gl.glVertex3f( offset+x*stepX-lenX, offset+y*stepY-lenY, altitude);
	        gl.glVertex3f( offset+x*stepX-lenX, offset+y*stepY+lenY, altitude);
	        gl.glVertex3f( offset+x*stepX+lenX, offset+y*stepY+lenY, altitude);
	        gl.glVertex3f( offset+x*stepX+lenX, offset+y*stepY-lenY, altitude);
        }
    }
}
