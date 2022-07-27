 //code maked with http://freespace.virgin.net/hugo.elias/models/m_perlin.htm 

package landscapegenerator;

public class PerlinNoiseLandscapeGenerator {
	
	static double persistence=1;
	static double Number_Of_Octaves =(int)(Math.random()*3)+2;//3
	static double fact =0.05;//0.05
	
	static int rand=(int)(Math.random()*30)+1;//13
	static int rand2=(int)(Math.random()*1001+10);//57
	static int r1=(int)(Math.random()*10000)+1000;//15731
	static int r2=(int)(Math.random()*1000000)+100000;//789221
	static int r3=(int)(Math.random()*2000000000)+100000000;//1376312589
	
    public static double[][] generatePerlinNoiseLandscape ( int dxView, int dyView, double scaling, double landscapeAltitudeRatio, int perlinLayerCount )
    {
    	double landscape[][] = new double[dxView][dyView];
    	double r;
    	for ( int x = 0 ; x < dxView ; x++ ){
    		for ( int y = 0 ; y < dyView ; y++ ){
    			int sx=x,sy=y;
    			/**
    			//MAP MIRROIR
    			if(x>dxView/2)sx=dxView-x;
    			if(y>dyView/2)sy=dyView-y;
    			/**/
    			r=PerlinNoise_2D((float)(sx*fact),(float)(sy*fact));
    			landscape[x][y] = r;
    		}
    	}

    	System.out.println("Map : "+rand+"-"+rand2+"-"+r1+"-"+r2+"-"+r3+", persistence : "+persistence+", octave : "+Number_Of_Octaves+", fact : "+fact);
    	
    	// scaling and polishing
    	landscape = LandscapeToolbox.scaleAndCenter(landscape, scaling, landscapeAltitudeRatio);
    	landscape = LandscapeToolbox.smoothLandscape(landscape);
    	
		return landscape;
    }
    
    public static double noise(double x, double y){
    	int n =(int)( x + y * rand2);
    	n = (n<<rand) ^ n;
    	return ( 1.0 - ( (n * (n * n * r1 + r2) + r3) & 0x7FFFFFFF) / 1073741824.0);
    }

    public static double smoothNoise_1(float x, float y){
    	double corners = ( noise(x-1, y-1)+noise(x+1, y-1)+noise(x-1, y+1)+noise(x+1, y+1) ) / 16;
    	double sides   = ( noise(x-1, y)  +noise(x+1, y)  +noise(x, y-1)  +noise(x, y+1) ) /  8;
    	/**/
    	double center  =  noise(x, y) / 4;
    	return corners + sides + center;
    }
    
    public static double interpolate(double a,double b,double x){
    	double ft = x * 3.1415927;
    	double f = (1 - Math.cos(ft)) * .5;

    	return  a*(1-f) + b*f;
    }

    public static double InterpolatedNoise_1(float x, float y){
    	
    	int integer_X    = (int)(x);
    	double fractional_X = x - integer_X;

    	int integer_Y    = (int)(y);
    	double fractional_Y = y - integer_Y;
    	
    	double v1 = smoothNoise_1(integer_X,     integer_Y);
    	double v2 = smoothNoise_1(integer_X + 1, integer_Y);
    	double v3 = smoothNoise_1(integer_X,     integer_Y + 1);
    	double v4 = smoothNoise_1(integer_X + 1, integer_Y + 1);

    	double i1 = interpolate(v1 , v2 , fractional_X);
    	double i2 = interpolate(v3 , v4 , fractional_X);

    	return interpolate(i1 , i2 , fractional_Y);

    }


    public static double PerlinNoise_2D(float x, float y){

    	double total = 0;
    	double p = persistence;
    	double n = Number_Of_Octaves - 1;

    	for(double i=0;i!=n;i++){

    		double frequency = Math.pow(2,i);
    		double amplitude = Math.pow(p,i);
    		
    		total = total + InterpolatedNoise_1((float)(x * frequency), (float)(y * frequency)) * amplitude;
    	}

    	return total;
    }


}
