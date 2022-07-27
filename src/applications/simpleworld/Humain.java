package applications.simpleworld;

import worlds.World;

public class Humain extends Agent {
	
	boolean _alive;
	
	double PreproD=0.007;
	double Prepro=PreproD;
	
	int energieD=400;//20
	int energie=energieD;
	
	int vision=10;
	
	double vitesse=7;// m/s MAX:28
	double vcourse=13.5;
	double vmarche=8;
	double vpas=3;
	
	int m=0;//1 si a manger ce tour
	
	public Humain( int __x, int __y,World __world)
	{
		super(__x,__y,__world);
		_alive = true;
		
		_redValue = 0.f;
		_greenValue = 1.f;
		_blueValue = 0.f;
		
	}
}
