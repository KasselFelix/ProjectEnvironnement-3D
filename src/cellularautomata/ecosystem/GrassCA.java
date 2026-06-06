package cellularautomata.ecosystem;


import java.util.Collections;

import cellularautomata.CellularAutomataDouble;
import cellularautomata.CellularAutomataInteger;
import worlds.World;

public class GrassCA extends CellularAutomataInteger {

	
	CellularAutomataDouble _cellsHeightValuesCA;

	World world;
	public double dherbe = 0.55; //0.55; // densite herbe
	public double pF=0.0000003;//probabilite de prendre feu pour les herbes
	public double pH=0.000006;// 0.00006// probabilite d'appHrition des herbes
	int tDispertion=20;// temps avant dispertion des cendres
	int NbHerbe=0;

	/**
	 * Facteur multiplicatif appliqué à la proba de germination quand la
	 * cellule a une pile de pierre. Identique à ForestCA.STONE_GROWTH_FACTOR
	 * pour cohérence (la végétation reconquiert la pierre lentement).
	 */
	private static final double STONE_GROWTH_FACTOR = 0.1;
	/** Tours restants d'enrichissement du sol par la cendre (post-incendie) ; booste pH. */
	private int[][] ashBoost;
	/** Facteur multiplicatif de germination de l'herbe sur sol cendré récent. */
	private static final double ASH_BOOST_FACTOR = 3.0;
	/** Durée (ticks) de l'effet fertilisant de la cendre. */
	private static final int ASH_BOOST_DURATION = 200;

	/** Tours restants d'état « herbe rase » après broutage (V4) : la cellule
	 *  reste tondue (pas de repousse) pendant ce délai → couplage agent→CA
	 *  visible (le rendu la dessine plus courte/brune). */
	private int[][] grazed;
	/** Durée (ticks) de l'état rase après broutage avant que la repousse reparte. */
	public static final int GRAZED_DURATION = 60;

	public GrassCA ( World __world, int __dx , int __dy, CellularAutomataDouble cellsHeightValuesCA )
	{
		super(__dx,__dy,false ); // buffering must be true.

		_cellsHeightValuesCA = cellsHeightValuesCA;

		this.world = __world;
		this.ashBoost = new int[_dx][_dy];
		this.grazed   = new int[_dx][_dy];
	}

	/** V4 — marque la cellule comme broutée : l'herbe disparaît (état 0) et reste
	 *  RASE pendant GRAZED_DURATION ticks (pas de repousse immédiate). Appelé par
	 *  le Mouton quand il broute. */
	public void markGrazed(int x, int y) {
		setCellState(x, y, 0);
		grazed[x % _dx][y % _dy] = GRAZED_DURATION;
	}

	/** Tours restants d'état rase pour la cellule (V4) ; 0 = herbe normale.
	 *  Lu par le rendu pour brunir/raccourcir l'herbe broutée. */
	public int getGrazed(int x, int y) { return grazed[x % _dx][y % _dy]; }
	
	/**
	 * Invariant d'altitude de l'herbe : elle ne pousse que dans une bande
	 * d'altitude [maxH/12, maxH×0.7] — ni dans l'eau / le littoral très bas
	 * (h < maxH/12), ni sur les hauts sommets (h > maxH×0.7). La borne basse
	 * (positive) implique aussi « terre ferme ». Prédicat pur, partagé par
	 * `init()` et `step()`.
	 */
	public boolean canGrowGrass(int x, int y) {
		double maxH = world.getMaxEverHeight();
		double h = world.getCellHeight(x, y);
		return h >= maxH / 12 && h <= maxH * 0.7;
	}

	/** Marque la cellule comme enrichie par la cendre : booste pH pendant
	 *  ASH_BOOST_DURATION ticks (succession après incendie). */
	public void markAsh(int x, int y) {
		ashBoost[x][y] = ASH_BOOST_DURATION;
	}

	/** Probabilité de germination de l'herbe sur la case (x,y) : pH de base,
	 *  réduit sur pierre, augmenté sur sol cendré récent. Fonction pure. */
	public double grassGerminationProb(int x, int y) {
		double p = pH;
		if (world.getStoneCAValue(x, y) > 0) p *= STONE_GROWTH_FACTOR;
		if (ashBoost[x][y] > 0) p *= ASH_BOOST_FACTOR;   // sol cendré récent → repousse accélérée
		p *= world.seasonalFertility();                  // L5 — l'herbe pousse au rythme des saisons
		return p;
	}

	public void init()
	{
		for ( int x = 0 ; x != _dx ; x++ )
    		for ( int y = 0 ; y != _dy ; y++ )
    		{
    			if ( _cellsHeightValuesCA.getCellState(x,y) >= 0 )
    			{
    				if ( Math.random() < dherbe && canGrowGrass(x, y)){
    					this.setCellState(x, y, 1); // grass
    					NbHerbe+=1;
    				}
    				else
    					this.setCellState(x, y, 0); // empty
    			}
    			else
    			{
    				this.setCellState(x, y, -1); // water (ignore)
    			}
    		}
    	this.swapBuffer();

	}

	public void step()
	{
		//MISE a jour asynchrone randomiser
    	Collections.shuffle(world.list);
    	for(int d=0;d<world.list.size();d++){
    		int i=world.list.get(d)%_dx;
			int j=world.list.get(d)/_dy;
			if (ashBoost[i][j] > 0) ashBoost[i][j]--;   // l'enrichissement cendre s'épuise (1×/cellule/tick)
			if (grazed[i][j] > 0) grazed[i][j]--;       // V4 — l'état rase s'estompe (repousse ensuite)
    			if (this.getCellState(i, j)>=0 &&  this.getCellState(i,j)<= 3+tDispertion)
    			{
    				// Pour une case sans herbe. Pas de germination sur la lave
    				// en refroidissement (top = LAVA tant que state < solidifyEnd).
    				// Sur la pierre (volcan refroidi), germination autorisée
    				// mais avec proba réduite (sol minéral peu fertile).
    				// V4 : une cellule encore RASE (récemment broutée) ne repousse pas.
    				if ( this.getCellState(i,j) == 0
    						&& world.getLavaCAValue(i, j)==0 && grazed[i][j]==0){
    					if(Math.random() < grassGerminationProb(i, j) && canGrowGrass(i, j)){
    						this.setCellState(i,j,1);
    						/*solid[x][y][]=1;*/
    						NbHerbe+=1;
    					}
	    			}
    				//pour un herbe en cendre
    				else if ( this.getCellState(i,j) == 3+tDispertion){
	    				this.setCellState(i,j,0); //dispertion
	    				markAsh(i, j);            // la cendre de l'herbe brûlée enrichit le sol
	    			}
    				// Pour une case avec herbe
	    			else if ( this.getCellState(i,j) == 1 ) // grass
	    			{
	    				if(world.getLavaCAValue(i, j)!=0){
	    					// Lave directement sur la cellule : l'herbe (petite) est
	    					// consumée instantanément, pas de phase visible de combustion.
	    					this.setCellState(i,j,0);
						}else{
		    				// check if neighbors are burning
		    				if ( 
		    						this.getCellState( (i+_dx-1)%(_dx) , j ) == 2 ||
		    						this.getCellState( (i+_dx+1)%(_dx) , j ) == 2 ||
		    						this.getCellState( i , (j+_dy+1)%(_dy) ) == 2 ||
		    						this.getCellState( i , (j+_dy-1)%(_dy) ) == 2 ||
		    						world.getLavaCAValue( (i+_dx-1)%(_dx) , j ) != 0 ||
		    						world.getLavaCAValue( (i+_dx+1)%(_dx) , j ) != 0 ||
		    						world.getLavaCAValue( i , (j+_dy+1)%(_dy) ) != 0 ||
		    						world.getLavaCAValue( i , (j+_dy-1)%(_dy) ) != 0 ||
		    						ForestCA.isTreeOnFire(world.getForestCAValue( (i+_dx-1)%(_dx) , j )) ||
		    						ForestCA.isTreeOnFire(world.getForestCAValue( (i+_dx+1)%(_dx) , j )) ||
		    						ForestCA.isTreeOnFire(world.getForestCAValue( i , (j+_dy+1)%(_dy) )) ||
		    						ForestCA.isTreeOnFire(world.getForestCAValue( i , (j+_dy-1)%(_dy) ))
		    					)
		    				{
		    					this.setCellState(i,j,2);
		    					NbHerbe-=1;
		    				}
		    				else
		    					if ( Math.random() < pF ) // spontaneously take fire ?
		    					{
		    						this.setCellState(i,j,2);
		    						NbHerbe-=1;
		    					}
		    					else
		    					{
		    						this.setCellState(i,j,1); // copied unchanged
		    					}
						}
	    			}
    				// Pour une case avec herbe en feu
	    			else if ( this.getCellState(i,j) == 2)
	    			{
	        				if ( this.getCellState( i , j ) == 2 ) // burning?
	        				{
	        					this.setCellState(i,j,3); // burnt
	        				}
	        				else
	        				{
	        					this.setCellState(i,j, this.getCellState(i,j) ); // copied unchanged
	        				}
	    			}
	    			else{
	    				if ( this.getCellState(i,j)>=3 && this.getCellState(i,j) < 3+tDispertion)
	    					this.setCellState(i,j,this.getCellState(i,j)+1);
	    			}
	    			
    				/**
	    			float color[] = new float[3];
	    			switch ( this.getCellState(i, j) )
	    			{
	    				case 0:
	    					world.colorInit(i,j,color);
	    					break;
	    				case 1:
	    					color[0] = 0.f;
	    					color[1] = (float)((220*0.6/255)+(220*0.4/255)*(this.world.getCellHeight(i,j)/this.world.getMaxEverHeight()));
	    					color[2] = 0.f;
	    					break;
	    				case 2: // burning grass
	    					if(Math.random()<0.5){
		    					color[0] = 255/255f;
		    					color[1] = 40/255f;
		    					color[2] = 0f;
	    					}else{
	    						color[0] = 255/255f;
		    					color[1] = 206/255f;
		    					color[2] = 0f;
	    					}
	    					break;
	    				case 3: // burnt grass
	    					color[0] = 0.f;
	    					color[1] = 0.f;
	    					color[2] = 0.f;
	    					break;
	    				default:
	    					if (this.getCellState(i, j)<0 &&  this.getCellState(i,j)> 3+tDispertion){
	    					color[0] = 0.5f;
	    					color[1] = 0.5f;
	    					color[2] = 0.5f;
	    					System.out.print("cannot interpret CA state: " + this.getCellState(i, j));
	    					System.out.println(" (at: " + i + "," + j + " -- height: " + this.world.getCellHeight(i,j) + " )");
	    					}else{// burnt grass
	    						float cinit[]=new float[3];
	    						world.colorInit(i,j,cinit);
	    						color[0] = (cinit[0]*getCellState(i, j))/(3+tDispertion);
		    					color[1] = (cinit[1]*getCellState(i, j))/(3+tDispertion);
		    					color[2] = (cinit[2]*getCellState(i, j))/(3+tDispertion);
	    					}
	    			}	   
	    			this.world.cellsColorValues.setCellState(i, j, color);
	    			/**/
    			}
    		}
    	this.swapBuffer();
	}
}
