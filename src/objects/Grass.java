package objects;

import javax.media.opengl.GL2;

import worlds.World;

public class Grass {
	public static void displayObjectAt(World myWorld, GL2 gl, int cellState, float x, float y, double height, float offset, float stepX, float stepY, float lenX, float lenY, float normalizeHeight,int movingX,int movingY )
    {
        
        if ( cellState > 0 )
        {
    		float altitude = (float)height * normalizeHeight ;
    		if (myWorld.getStoneCAValue((int)x+movingX, (int)y+movingY)==1) altitude+=3;
    		double r1=Math.random();
    		double r2=Math.random();
    		
    		switch ( cellState )
            {
            	case 1:
            		gl.glColor3f(0,(float)((220*0.6/255)+(220*0.4/255)*(myWorld.getCellHeight((int)x,(int)y)/myWorld.getMaxEverHeight())-0.05*r2),0);
            		break;
            	case 2:
            		gl.glColor3f(1.f,127/255f-(float)(0.2*r2),0.f);
            		break;
            	case 3:
            		gl.glColor3f(0.f+(float)(0.1*Math.random()),0.f+(float)(0.1*Math.random()),0.f+(float)(0.1*Math.random()));
            		break;
            }
    		gl.glVertex3f( offset+x*stepX, offset+y*stepY, altitude );
            gl.glVertex3f( offset+x*stepX, offset+y*stepY+lenY*0.4f, altitude+1.f);
            gl.glVertex3f( offset+x*stepX, offset+y*stepY, altitude );
            gl.glVertex3f( offset+x*stepX, offset+y*stepY-lenY*0.4f, altitude+1.f);
            
            switch ( cellState )
            {
            	case 1:
            		gl.glColor3f(0,(float)((220*0.6/255)+(220*0.4/255)*(myWorld.getCellHeight((int)x,(int)y)/myWorld.getMaxEverHeight())-0.05*r1),0);
            		break;
            	case 2:
            		gl.glColor3f(1.f,127/255f-(float)(0.2*r1),0.f);
            		break;
            	case 3:
            		gl.glColor3f(0.f+(float)(0.2*Math.random()),0.f+(float)(0.2*Math.random()),0.f+(float)(0.2*Math.random()));
            		break;
            }
            gl.glVertex3f( offset+x*stepX, offset+y*stepY, altitude );
            gl.glVertex3f( offset+x*stepX-lenX*0.4f, offset+y*stepY, altitude+1.f);
    		gl.glVertex3f( offset+x*stepX, offset+y*stepY, altitude );
            gl.glVertex3f( offset+x*stepX+lenX*0.4f, offset+y*stepY, altitude+1.f);
        }
    }
}
