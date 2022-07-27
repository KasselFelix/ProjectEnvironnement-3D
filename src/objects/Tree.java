package objects;

import javax.media.opengl.GL2;

import worlds.World;


public class Tree extends CommonObject {
	
	static void makeTree(int length, int angle, float px, float py, float pz,
			World myWorld, GL2 gl, int cellState,  float x, float y, float offset, float lenX, float lenY){
		float move = (int)(Math.cos(Math.toRadians(angle+90))*length);
		float zmove = -((int)(Math.sin(Math.toRadians(angle-90))*length));
		/**/
		gl.glVertex3f( px-lenX*0.05f, y, pz);
		gl.glVertex3f( px-lenX*0.05f+move, y, pz+zmove);
		gl.glVertex3f( px+lenX*0.05f+move, y, pz+zmove);
		gl.glVertex3f( px+lenX*0.05f, y, pz );


		gl.glVertex3f( px-lenX*0.05f, y, pz );
		gl.glVertex3f( px+lenX*0.05f, y, pz );
		gl.glVertex3f( px+lenX*0.05f+move, y, pz+zmove);
		gl.glVertex3f( px-lenX*0.05f+move, y, pz+zmove);


		gl.glVertex3f( x,py-lenY*0.05f, pz);
		gl.glVertex3f( x,py-lenY*0.05f+move, pz+zmove);
		gl.glVertex3f( x,py+lenY*0.05f+move, pz+zmove);
		gl.glVertex3f( x,py+lenY*0.05f, pz );


		gl.glVertex3f( x,py-lenY*0.05f, pz );
		gl.glVertex3f( x,py+lenY*0.05f, pz );
		gl.glVertex3f( x,py+lenY*0.05f+move, pz+zmove);
		gl.glVertex3f( x,py-lenY*0.05f+move, pz+zmove);
		/**/
		/*
		gl.glVertex3f( px-lenX*0.05f, py-lenY*0.05f, pz);
        gl.glVertex3f( px-lenX*0.05f+move, py-lenY*0.05f+move, pz+zmove);
        gl.glVertex3f( px-lenX*0.05f+move, py+lenY*0.05f+move, pz+zmove);
        gl.glVertex3f( px-lenX*0.05f,py+lenY*0.05f, pz );


        gl.glVertex3f( px+lenX*0.05f, py-lenY*0.05f, pz);
        gl.glVertex3f( px+lenX*0.05f+move, py-lenY*0.05f+move, pz+zmove);
        gl.glVertex3f( px+lenX*0.05f+move, py+lenY*0.05f+move, pz+zmove);
        gl.glVertex3f( px+lenX*0.05f,py+lenY*0.05f, pz );

        gl.glVertex3f( px-lenX*0.05f, py-lenY*0.05f, pz);
        gl.glVertex3f( px-lenX*0.05f+move, py-lenY*0.05f+move, pz+zmove);
        gl.glVertex3f( px+lenX*0.05f+move, py-lenY*0.05f+move, pz+zmove);
        gl.glVertex3f( px+lenX*0.05f,py-lenY*0.05f, pz );

        gl.glVertex3f( px-lenX*0.05f, py+lenY*0.05f, pz);
        gl.glVertex3f( px-lenX*0.05f+move, py+lenY*0.05f+move, pz+zmove);
        gl.glVertex3f( px+lenX*0.05f+move, py+lenY*0.05f+move, pz+zmove);
        gl.glVertex3f( px+lenX*0.05f,py+lenY*0.05f, pz );

        gl.glVertex3f( px-lenX*0.05f+move, py-lenY*0.05f+move, pz+zmove);
        gl.glVertex3f( px+lenX*0.05f+move, py-lenY*0.05f+move, pz+zmove);
        gl.glVertex3f( px+lenX*0.05f+move, py+lenY*0.05f+move, pz+zmove);
        gl.glVertex3f( px-lenX*0.05f+move, py+lenY*0.05f+move, pz+zmove );

        gl.glVertex3f( px-lenX*0.05f, py-lenY*0.05f, pz);
        gl.glVertex3f( px+lenX*0.05f, py-lenY*0.05f, pz);
        gl.glVertex3f( px+lenX*0.05f, py+lenY*0.05f, pz);
        gl.glVertex3f( px-lenX*0.05f, py+lenY*0.05f, pz);
        /**/

        
		if (length>=1){
			makeTree(length-1,angle+15,px+move, py+move, pz+zmove,
					myWorld, gl,cellState, x, y,offset, lenX, lenY);
			makeTree(length-1,angle-15,px+move, py+move, pz+zmove,
					myWorld, gl,cellState, x, y,offset, lenX, lenY);
		}
	}

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
            		gl.glColor3f(0,(float)((128*0.6/255)+(128*0.4/255)*(myWorld.getCellHeight((int)x,(int)y)/myWorld.getMaxEverHeight())-0.05*r2),0);
            		break;
            	case 2:
            		gl.glColor3f(1.f,127/255f-(float)(0.2*r2),0.f);
            		break;
            	case 3:
            		gl.glColor3f(0.f+(float)(0.1*Math.random()),0.f+(float)(0.1*Math.random()),0.f+(float)(0.1*Math.random()));
            		break;
            }
    		/**
    		if(height<=0.1*myWorld.getMaxEverHeight()){
    		makeTree(5, 0, offset+x*stepX, offset+y*stepY, altitude,myWorld, gl, cellState, offset+x*stepX,  offset+y*stepY, offset, lenX, lenY);
    		}
    		else{
    		/**/
    		gl.glVertex3f( offset+x*stepX, offset+y*stepY, altitude+10.f );
            gl.glVertex3f( offset+x*stepX, offset+y*stepY+lenY*0.8f, altitude+2.f );
            gl.glVertex3f( offset+x*stepX, offset+y*stepY, altitude+10.f );
            gl.glVertex3f( offset+x*stepX, offset+y*stepY-lenY*0.8f, altitude+2.f);
            
            switch ( cellState )
            {
            	case 1:
            		gl.glColor3f(0,(float)((128*0.6/255)+(128*0.4/255)*(myWorld.getCellHeight((int)x,(int)y)/myWorld.getMaxEverHeight())-0.05*r1),0);
            		break;
            	case 2:
            		gl.glColor3f(1.f,127/255f-(float)(0.2*r1),0.f);
            		break;
            	case 3:
            		gl.glColor3f(0.f+(float)(0.2*Math.random()),0.f+(float)(0.2*Math.random()),0.f+(float)(0.2*Math.random()));
            		break;
            }
            gl.glVertex3f( offset+x*stepX, offset+y*stepY, altitude + 10.f );
            gl.glVertex3f( offset+x*stepX-lenX*0.8f, offset+y*stepY, altitude+2.f);
    		gl.glVertex3f( offset+x*stepX, offset+y*stepY, altitude + 10.f );
            gl.glVertex3f( offset+x*stepX+lenX*0.8f, offset+y*stepY, altitude+2.f);
            
            
            gl.glColor3f((float)((133*0.6/255)+(133*0.4/255)*(myWorld.getCellHeight((int)x,(int)y)/myWorld.getMaxEverHeight())-0.05*r1),
            	(float)((94*0.75/255)+(95*0.25/255)*(myWorld.getCellHeight((int)x,(int)y)/myWorld.getMaxEverHeight())-0.05*r1),
            	(float)((66*0.75/255)+(66*0.25/255)*(myWorld.getCellHeight((int)x,(int)y)/myWorld.getMaxEverHeight())-0.05*r1));
            gl.glVertex3f( offset+x*stepX-lenX*0.2f, offset+y*stepY, altitude);
            gl.glVertex3f( offset+x*stepX-lenX*0.2f, offset+y*stepY, altitude+2.f );
            gl.glVertex3f( offset+x*stepX+lenX*0.2f, offset+y*stepY, altitude+2.f );
            gl.glVertex3f( offset+x*stepX+lenX*0.2f, offset+y*stepY, altitude );
            
           
            gl.glVertex3f( offset+x*stepX-lenX*0.2f, offset+y*stepY, altitude );
            gl.glVertex3f( offset+x*stepX+lenX*0.2f, offset+y*stepY, altitude );
            gl.glVertex3f( offset+x*stepX+lenX*0.2f, offset+y*stepY, altitude+2.f );
            gl.glVertex3f( offset+x*stepX-lenX*0.2f, offset+y*stepY, altitude+2.f );
            
            
            gl.glColor3f((float)((133*0.6/255)+(133*0.4/255)*(myWorld.getCellHeight((int)x,(int)y)/myWorld.getMaxEverHeight())-0.05*r2),
                	(float)((94*0.75/255)+(95*0.25/255)*(myWorld.getCellHeight((int)x,(int)y)/myWorld.getMaxEverHeight())-0.05*r2),
                	(float)((66*0.75/255)+(66*0.25/255)*(myWorld.getCellHeight((int)x,(int)y)/myWorld.getMaxEverHeight())-0.05*r2));
            gl.glVertex3f( offset+x*stepX, offset+y*stepY-lenY*0.2f, altitude);
    		gl.glVertex3f( offset+x*stepX, offset+y*stepY-lenY*0.2f, altitude+2.f );
    		gl.glVertex3f( offset+x*stepX, offset+y*stepY+lenY*0.2f, altitude+2.f );
    		gl.glVertex3f( offset+x*stepX, offset+y*stepY+lenY*0.2f, altitude );
            
    		
    		gl.glVertex3f( offset+x*stepX, offset+y*stepY-lenY*0.2f, altitude);
    		gl.glVertex3f( offset+x*stepX, offset+y*stepY+lenY*0.2f, altitude );
    		gl.glVertex3f( offset+x*stepX, offset+y*stepY+lenY*0.2f, altitude+2.f );
    		gl.glVertex3f( offset+x*stepX, offset+y*stepY-lenY*0.2f, altitude+2.f );
    		//}
    		/**/
       }
    }

}
