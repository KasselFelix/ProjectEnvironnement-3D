package objects;

import javax.media.opengl.GL2;

import worlds.World;

public class FractalTree extends UniqueObject{
	
	public FractalTree ( int __x , int __y , World __world )
	{
		super(__x,__y,__world);
	}
	
	static void makeTree(int length, int angle, float px, float py, float pz,
			World myWorld, GL2 gl ,  float x, float y, float offset, float lenX, float lenY){
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
					myWorld, gl, x, y,offset, lenX, lenY);
			makeTree(length-1,angle-15,px+move, py+move, pz+zmove,
					myWorld, gl, x, y,offset, lenX, lenY);
		}
	}
	
   	public void displayUniqueObject(World myWorld, GL2 gl, int offsetCA_x, int offsetCA_y, float offset, float stepX, float stepY, float lenX, float lenY, float normalizeHeight )
    {
   		
   		int x2 = (x-(offsetCA_x%myWorld.getWidth()));
    	if ( x2 < 0) x2+=myWorld.getWidth();
    	int y2 = (y-(offsetCA_y%myWorld.getHeight()));
    	if ( y2 < 0) y2+=myWorld.getHeight();
    	
    	float altitude=(float)(myWorld.getCellHeight(x2+offsetCA_x,y2+offsetCA_y)*normalizeHeight);
		double r1=Math.random();
		
        
		gl.glColor3f((float)((133*0.6/255)+(133*0.4/255)*(myWorld.getCellHeight((int)x2,(int)y2)/myWorld.getMaxEverHeight())-0.05*r1),
            	(float)((94*0.75/255)+(95*0.25/255)*(myWorld.getCellHeight((int)x2,(int)y2)/myWorld.getMaxEverHeight())-0.05*r1),
            	(float)((66*0.75/255)+(66*0.25/255)*(myWorld.getCellHeight((int)x2,(int)y2)/myWorld.getMaxEverHeight())-0.05*r1));
		makeTree(5, 0, offset+x2*stepX, offset+y2*stepY,altitude,myWorld, gl, offset+x2*stepX,  offset+y2*stepY, offset, lenX, lenY);
		
   		
    }
}
