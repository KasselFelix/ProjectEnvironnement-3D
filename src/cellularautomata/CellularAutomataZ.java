package cellularautomata;

public abstract class CellularAutomataZ {
	protected int _dx;
	protected int _dy;
	protected int _dz;
	
	boolean buffering;
	
	int activeIndex;
	
	protected int Buffer0[][][];
	protected int Buffer1[][][];
	
	public CellularAutomataZ ( int __dx , int __dy,int __dz, boolean __buffering )
	{
		_dx = __dx;
		_dy = __dy;
		_dz = __dz;
		buffering = __buffering;
		
		activeIndex = 0;
		
		Buffer0 = new int[_dx][_dy][_dz];
		Buffer1 = new int[_dx][_dy][_dz];
		
	    for ( int x = 0 ; x != _dx ; x++ )
	    	for ( int y = 0 ; y != _dy ; y++ )
	    	{
	    		for ( int z = 0 ; z != _dz ; z++ )
		    	{
    			Buffer0[x][y][z]=0;
    			Buffer0[x][y][z]=0;
    			Buffer0[x][y][z]=0;
    			Buffer1[x][y][z]=0;
    			Buffer1[x][y][z]=0;
    			Buffer1[x][y][z]=0;
		    	}
	    	}
	}
	
	public void checkBounds( int __x , int __y )
	{
		if ( __x < 0 || __x > _dx || __y < 0 || __y > _dy )
		{
			System.err.println("[error] out of bounds ("+__x+","+__y+")");
			System.exit(-1);
		}
	}
	
	public int getWidth()
	{
		return _dx;
	}
	
	public int getHeight()
	{
		return _dy;
	}
	
	
	public int getHauteur()
	{
		return _dz;
	}
	
	public void step() 
	{ 
		if ( buffering )
			swapBuffer();
	}
	
	public void swapBuffer() // should be used carefully (except for initial step)
	{
		activeIndex = ( activeIndex+1 ) % 2;
	}
	
	public int getCellState ( int __x, int __y, int __z )
	{
		checkBounds (__x,__y);
		
		int value;

		if ( buffering == false )
		{
			value = Buffer0[__x][__y][__z];
		}
		else
		{
			if ( activeIndex == 1 ) // read old buffer
			{
				value = Buffer0[__x][__y][__z];
			}
			else
			{
				value = Buffer1[__x][__y][__z];
			}
		}
		
		return value;
	}
	
	public void setCellState ( int __x, int __y, int __z, int __value )
	{
		checkBounds (__x,__y);
		
		if ( buffering == false )
		{
			Buffer0[__x][__y][__z] = __value;
		}
		else
		{
			if ( activeIndex == 0 ) // write new buffer
			{
				Buffer0[__x][__y][__z] = __value;
			}
			else
			{
				Buffer1[__x][__y][__z] = __value;
			}
		}
	}
	
	public int[][][] getCurrentBuffer()
	{
		if ( activeIndex == 0 || buffering == false ) 
			return Buffer0;
		else
			return Buffer1;		
	}
	
}
