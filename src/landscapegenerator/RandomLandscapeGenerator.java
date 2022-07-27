//code maked with http://staffwww.itn.liu.se/~stegu/simplexnoise/simplexnoise.pdf

package landscapegenerator;

public class RandomLandscapeGenerator {
	
	private static double grad3[][] = {{1,1,0},{-1,1,0},{1,-1,0},{-1,-1,0},
		{1,0,1},{-1,0,1},{1,0,-1},{-1,0,-1},
		{0,1,1},{0,-1,1},{0,1,-1},{0,-1,-1}};
	
	private static int p[];
	private static int perm[];
	static int dx;
	static int dy;
	
    public static double[][] generateRandomLandscape ( int dxView, int dyView, double scaling, double landscapeAltitudeRatio )
    {
    	dx=dxView;
    	dy=dyView;
    	
    	int nb=dxView*dyView;
    	p=new int[nb];
    	for ( int cpt = 0 ; cpt != nb ; cpt++ ){
				p[cpt]=(int)(Math.random()*256);
    	}
    	// To remove the need for index wrapping, double the permutation table length
    	perm = new int[nb];
    	for(int i=0; i<nb; i++)perm[i]=p[i & 255];
    	
    	double[][] landscape = new double[dxView][dyView];
    	
    	double freq=0.0159;
    	double r;
    	
		for ( int i = 0 ; i != dxView ; i++ ){
			for ( int j = 0 ; j != dyView ; j++ )
			{   
				r=noise(i*freq,j*freq);
				//r=pnoise2(i*freq,j*freq,1,1);
				landscape[i][j]=r;
    		}
		}
		
		landscape = LandscapeToolbox.scaleAndCenter(landscape, scaling, landscapeAltitudeRatio);
		landscape = LandscapeToolbox.smoothLandscape(landscape);
		
		return landscape;
    }
    
    
    // This method is a *lot* faster than using (int)Math.floor(x)
    private static int fastfloor(double x) {
    	return x>0 ? (int)x : (int)x-1;
    }
    
    private static double dot(double g[], double x, double y) {
    	return g[0]*x + g[1]*y ;
    }
    
    /**
    //2D Perlin noise
    private static double mix(double a, double b, double t) {
    	return (1-t)*a + t*b;
    }
    
    private static double fade(double t) {
    	return t*t*t*(t*(t*6-15)+10);
    }
    
    public static double noise(double x, double y) {
    	 // Find unit grid cell containing point
    	 int X = fastfloor(x);
    	 int Y = fastfloor(y);

    	 // Get relative xy coordinates of point within that cell
    	 x = x - X;
    	 y = y - Y;

    	 // Wrap the integer cells at 255 (smaller integer period can be introduced here)
    	 X = X & 255;
    	 Y = Y & 255;

    	 // Calculate a set of eight hashed gradient indices
    	 int gi000 = perm[X+perm[Y]] % 12;
    	 int gi001 = perm[X+perm[Y]] % 12;
    	 int gi010 = perm[X+perm[Y+1]] % 12;
    	 int gi011 = perm[X+perm[Y+1]] % 12;
    	 int gi100 = perm[X+1+perm[Y]] % 12;
    	 int gi101 = perm[X+1+perm[Y]] % 12;
    	 int gi110 = perm[X+1+perm[Y+1]] % 12;
    	 int gi111 = perm[X+1+perm[Y+1]] % 12;

    	 // The gradients of each corner are now:
    	 // g000 = grad3[gi000];
    	 // g001 = grad3[gi001];
    	 // g010 = grad3[gi010];
    	 // g011 = grad3[gi011];
    	 // g100 = grad3[gi100];
    	 // g101 = grad3[gi101];
    	 // g110 = grad3[gi110];
    	 // g111 = grad3[gi111];
    	 // Calculate noise contributions from each of the eight corners
    	 double n000= dot(grad3[gi000], x, y);
    	 double n100= dot(grad3[gi100], x-1, y);
    	 double n010= dot(grad3[gi010], x, y-1);
    	 double n110= dot(grad3[gi110], x-1, y-1);
    	 double n001= dot(grad3[gi001], x, y);
    	 double n101= dot(grad3[gi101], x-1, y);
    	 double n011= dot(grad3[gi011], x, y-1);
    	 double n111= dot(grad3[gi111], x-1, y-1);
    	 // Compute the fade curve value for each of x, y
    	 double u = fade(x);
    	 double v = fade(y);
    	 // Interpolate along x the contributions from each of the corners
    	 double nx00 = mix(n000, n100, u);
    	 double nx01 = mix(n001, n101, u);
    	 double nx10 = mix(n010, n110, u);
    	 double nx11 = mix(n011, n111, u);
    	 // Interpolate the four results along y
    	 double nxy0 = mix(nx00, nx10, v);
    	 double nxy1 = mix(nx01, nx11, v);
    	 // Interpolate the two last results
    	 double nxy = mix(nxy0, nxy1, 1);

    	 return nxy;
    	 }
	/**/
    
    // 2D simplex noise
    /**/
    public static double noise(double xin, double yin) {
	    double n0, n1, n2; // Noise contributions from the three corners
	    // Skew the input space to determine which simplex cell we're in
	    final double F2 = 0.5*(Math.sqrt(3.0)-1.0);
	    double s = (xin+yin)*F2; // Hairy factor for 2D
	    int i = fastfloor(xin+s);
	    int j = fastfloor(yin+s);
	    final double G2 = (3.0-Math.sqrt(3.0))/6.0;
	    double t = (i+j)*G2;
	    double X0 = i-t; // Unskew the cell origin back to (x,y) space
	    double Y0 = j-t;
	   
	    double x0 = xin-X0; // The x,y distances from the cell origin
	    double y0 = yin-Y0;
	    
	    // For the 2D case, the simplex shape is an equilateral triangle.
	    // Determine which simplex we are in.
	    int i1, j1; // Offsets for second (middle) corner of simplex in (i,j) coords
	    if(x0>y0) {i1=1; j1=0;} // lower triangle, XY order: (0,0)->(1,0)->(1,1)
	    else {i1=0; j1=1;} // upper triangle, YX order: (0,0)->(0,1)->(1,1)
	    // A step of (1,0) in (i,j) means a step of (1-c,-c) in (x,y), and
	    // a step of (0,1) in (i,j) means a step of (-c,1-c) in (x,y), where
	    // c = (3-sqrt(3))/6
	   
	    double x1 = x0 - i1 + G2; // Offsets for middle corner in (x,y) unskewed coords
	    double y1 = y0 - j1 + G2;
	    double x2 = x0 - 1.0 + 2.0 * G2; // Offsets for last corner in (x,y) unskewed coords
	    double y2 = y0 - 1.0 + 2.0 * G2;
	    // Work out the hashed gradient indices of the three simplex corners
	    int ii = i & 255;
	    int jj = j & 255;
	    int gi0 = perm[ii+perm[jj]] % 12;
	    int gi1 = perm[ii+i1+perm[jj+j1]] % 12;
	    int gi2 = perm[ii+1+perm[jj+1]] % 12;
	    // Calculate the contribution from the three corners
	    double t0 = 0.5 - x0*x0-y0*y0;
	    if(t0<0) n0 = 0.0;
	    else {
	    t0 *= t0;
	    n0 = t0 * t0 * dot(grad3[gi0], x0, y0); // (x,y) of grad3 used for 2D gradient
	    }
	    double t1 = 0.5 - x1*x1-y1*y1;
	    if(t1<0) n1 = 0.0;
	    else {
	    t1 *= t1;
	    n1 = t1 * t1 * dot(grad3[gi1], x1, y1);
	    }
	    double t2 = 0.5 - x2*x2-y2*y2;
	    if(t2<0) n2 = 0.0;
	    else {
	    t2 *= t2;
	    n2 = t2 * t2 * dot(grad3[gi2], x2, y2);
	    }
	    // Add contributions from each corner to get the final noise value.
	    // The result is scaled to return values in the interval [-1,1].
	    return 70.0 * (n0 + n1 + n2);
	  }
	  /**/
    
    
    /**/
    //2D float Perlin periodic noise. https://github.com/stegu/perlin-noise
    // This is the new and improved, C(2) continuous interpolant
    static double FADE(double t){ return ( t * t * t * ( t * ( t * 6 - 15 ) + 10 ) );}
    static double  FASTFLOOR(double x){ return ( ((int)(x)<(x)) ? ((int)x) : ((int)x-1 ) );}
    static double LERP(double t, double a,double b){return  ((a) + (t)*((b)-(a)));}

    static float grad2( int hash, float x, float y ) {
        int h = hash & 7;      // Convert low 3 bits of hash code
        float u = h<4 ? x : y;  // into 8 simple gradient directions,
        float v = h<4 ? y : x;  // and compute the dot product with (x,y).
        //return (float)((h & 1))? -u : u) + ((h&2)? -2.0*v : 2.0*v));
        return (float)(((h==(h & 1))? -u : u) + ((h==(h&2))? -2.0*v : 2.0*v));
    }
    
    static float pnoise2( double x, double y, int px, int py )
    {
        int ix0, iy0, ix1, iy1;
        float fx0, fy0, fx1, fy1;
        float s, t, nx0, nx1, n0, n1;

        ix0 = (int) FASTFLOOR( x ); // Integer part of x
        iy0 = (int) FASTFLOOR( y ); // Integer part of y
        fx0 = (float) (x - ix0);        // Fractional part of x
        fy0 = (float) (y - iy0);        // Fractional part of y
        fx1 = fx0 - 1.0f;
        fy1 = fy0 - 1.0f;
        ix1 = (( ix0 + 1 ) % px) & 0xff;  // Wrap to 0..px-1 and wrap to 0..255
        iy1 = (( iy0 + 1 ) % py) & 0xff;  // Wrap to 0..py-1 and wrap to 0..255
        ix0 = ( ix0 % px ) & 0xff;
        iy0 = ( iy0 % py ) & 0xff;
        
        t = (float) FADE( fy0 );
        s = (float) FADE( fx0 );

        nx0 = grad2(perm[ix0 + perm[iy0]], fx0, fy0);
        nx1 = grad2(perm[ix0 + perm[iy1]], fx0, fy1);
        n0 = (float) LERP( t, nx0, nx1 );

        nx0 = grad2(perm[ix1 + perm[iy0]], fx1, fy0);
        nx1 = grad2(perm[ix1 + perm[iy1]], fx1, fy1);
        n1 = (float) LERP(t, nx0, nx1);

        return (float) (0.507f * ( LERP( s, n0, n1 ) ));
    }
    /**/
    
}
