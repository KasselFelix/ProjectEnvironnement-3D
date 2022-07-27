package objects;

import javax.media.opengl.GL2;

import worlds.World;

public class StoneBlock extends CommonObject {

    public static void displayObjectAt(World myWorld, GL2 gl, int cellState, float x, float y, double height, float offset, float stepX, float stepY, float lenX, float lenY, float normalizeHeight,int movingX, int movingY )
    {
        
        
        if ( cellState > 0 )
        {
    		float altitude = (float)(height * normalizeHeight) ;
    		double pr=((172*height)/myWorld.getMaxEverHeight());
			double pg=((177*height)/myWorld.getMaxEverHeight());
			double pb=((181*height)/myWorld.getMaxEverHeight());
			gl.glColor3f((float)(pr/255)+(float)(0.02*Math.random()),
					(float)(pg/255) +(float)(0.02*Math.random()),
					(float)(pb/255)+(float)(0.02*Math.random()));
    		
	        
	        gl.glVertex3f( offset+x*stepX-lenX, offset+y*stepY-lenY, altitude);
	        gl.glVertex3f( offset+x*stepX-lenX, offset+y*stepY-lenY, altitude + 3);
	        gl.glVertex3f( offset+x*stepX+lenX, offset+y*stepY-lenY, altitude + 3);
	        gl.glVertex3f( offset+x*stepX+lenX, offset+y*stepY-lenY, altitude);
	
	        
	        gl.glVertex3f( offset+x*stepX+lenX, offset+y*stepY+lenY, altitude);
	        gl.glVertex3f( offset+x*stepX+lenX, offset+y*stepY+lenY, altitude +3);
	        gl.glVertex3f( offset+x*stepX-lenX, offset+y*stepY+lenY, altitude +3);
	        gl.glVertex3f( offset+x*stepX-lenX, offset+y*stepY+lenY, altitude);
	        
	       
	        gl.glVertex3f( offset+x*stepX+lenX, offset+y*stepY-lenY, altitude);
	        gl.glVertex3f( offset+x*stepX+lenX, offset+y*stepY-lenY, altitude + 3);
	        gl.glVertex3f( offset+x*stepX+lenX, offset+y*stepY+lenY, altitude + 3);
	        gl.glVertex3f( offset+x*stepX+lenX, offset+y*stepY+lenY, altitude);
	
	        
	        gl.glVertex3f( offset+x*stepX-lenX, offset+y*stepY+lenY, altitude);
	        gl.glVertex3f( offset+x*stepX-lenX, offset+y*stepY+lenY, altitude +3);
	        gl.glVertex3f( offset+x*stepX-lenX, offset+y*stepY-lenY, altitude +3);
	        gl.glVertex3f( offset+x*stepX-lenX, offset+y*stepY-lenY, altitude);
	
	        
	        gl.glVertex3f( offset+x*stepX-lenX, offset+y*stepY-lenY, altitude +3);
	        gl.glVertex3f( offset+x*stepX-lenX, offset+y*stepY+lenY, altitude +3);
	        gl.glVertex3f( offset+x*stepX+lenX, offset+y*stepY+lenY, altitude +3);
	        gl.glVertex3f( offset+x*stepX+lenX, offset+y*stepY-lenY, altitude +3);
	        
	        gl.glVertex3f( offset+x*stepX-lenX, offset+y*stepY-lenY, altitude);
	        gl.glVertex3f( offset+x*stepX-lenX, offset+y*stepY+lenY, altitude);
	        gl.glVertex3f( offset+x*stepX+lenX, offset+y*stepY+lenY, altitude);
	        gl.glVertex3f( offset+x*stepX+lenX, offset+y*stepY-lenY, altitude);
        }
    }
}
