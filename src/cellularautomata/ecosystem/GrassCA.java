package cellularautomata.ecosystem;


import java.util.Collections;

import cellularautomata.CellularAutomataDouble;
import cellularautomata.CellularAutomataInteger;
import ui.SimulationConfig;
import worlds.World;

public class GrassCA extends CellularAutomataInteger {

	
	CellularAutomataDouble _cellsHeightValuesCA;

	World world;
	public double dherbe = 0.55; //0.55; // densite herbe
	public double pF=0.0000003;//probabilite de prendre feu pour les herbes
	public double pH=0.000006;// 0.00006// probabilite d'appHrition des herbes
	int tDispertion;// temps (ticks) avant dispersion des cendres — calculé hz-scalé au constructeur (1 s)
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
	/** Durée (secondes-jeu) de l'effet fertilisant de la cendre (→ ticks via hz). */
	private static final double ASH_SEC = 10.0;

	/** Tours restants d'état « herbe rase » après broutage (V4) : la cellule
	 *  reste tondue (pas de repousse) pendant ce délai → couplage agent→CA
	 *  visible (le rendu la dessine plus courte/brune). */
	private int[][] grazed;
	/** Durée (secondes-jeu) de l'état rase après broutage (→ ticks via hz). */
	private static final double GRAZED_SEC = 3.0;
	/** Durée en ticks de l'état rase, calculée depuis simulationHz (hz-invariant). */
	public int getGrazedDuration() { return secToTicks(GRAZED_SEC); }

	public GrassCA ( World __world, int __dx , int __dy, CellularAutomataDouble cellsHeightValuesCA )
	{
		// Asynchrone in-place (mono-buffer) DÉLIBÉRÉ ; le feu est borné à ~1 case/tick
		// via `ignitedThisTick` (Option 2), pas via un double-buffer. Cf. ForestCA.
		super(__dx,__dy,false );

		_cellsHeightValuesCA = cellsHeightValuesCA;

		this.world = __world;
		this.ashBoost = new int[_dx][_dy];
		this.grazed   = new int[_dx][_dy];
		this.ignitedThisTick = new boolean[_dx][_dy];
		this.tDispertion = secToTicks(1.0);   // 1 s de cycle cendre
	}

	/** V4 — marque la cellule comme broutée : l'herbe disparaît (état 0) et reste
	 *  RASE pendant GRAZED_DURATION ticks (pas de repousse immédiate). Appelé par
	 *  le Mouton quand il broute. */
	public void markGrazed(int x, int y) {
		setCellState(x, y, 0);
		grazed[x % _dx][y % _dy] = getGrazedDuration();
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
	/** Hz de référence pour lequel pH/pF (taux par tick) ont été calibrés. */
	private static final int REF_HZ = 20;
	/** Échelle des taux PAR TICK → indépendants de simulationHz (1 à hz=REF_HZ). */
	private static double tickRateScale() {
		return (double) REF_HZ / SimulationConfig.getInstance().simulationHz;
	}

	/** Convertit une durée en secondes-jeu en ticks via simulationHz (≥ 1). */
	private static int secToTicks(double sec) {
		return Math.max(1, (int) Math.round(sec * SimulationConfig.getInstance().simulationHz));
	}

	/** Cellules ayant pris feu CE tick : exclues du voisinage enflammé → feu borné
	 *  à ~1 case/tick (Option 2). Reset en début de step(). */
	private boolean[][] ignitedThisTick;
	/** Proba d'ignition par voisin herbe en feu : cardinal > diagonal → front rond. */
	private static final double FIRE_P_CARD = 0.55, FIRE_P_DIAG = 0.22;
	/** Poids de propagation par direction. POINT D'ACCROCHE VENT (cf. ForestCA). */
	private static double dirSpreadWeight(int dx, int dy) {
		return (dx == 0 || dy == 0) ? FIRE_P_CARD : FIRE_P_DIAG;
	}

	public boolean canGrowGrass(int x, int y) {
		double maxH = world.getMaxEverHeight();
		double h = world.getCellHeight(x, y);
		return h >= maxH / 12 && h <= maxH * 0.7;
	}

	/** Plafond ABSOLU de brins par case (config Phase 2). */
	public int brinsMax = 5;

	/** Fertilité d'altitude CONTINUE ∈ [0,1] : 0 hors bande (canGrowGrass false),
	 *  sinon bosse lisse avec optimum au milieu de la bande [maxH/12, 0.7·maxH].
	 *  Donne le gradient 1-5 (≠ le booléen canGrowGrass). */
	public double grassFertility(int x, int y) {
		if (!canGrowGrass(x, y)) return 0.0;
		double maxH = world.getMaxEverHeight();
		double lo = maxH / 12.0, hi = maxH * 0.7;
		double hN = (world.getCellHeight(x, y) - lo) / (hi - lo);   // 0 au bas de bande, 1 au haut
		double bump = 1.0 - Math.pow((hN - 0.5) / 0.5, 2);          // optimum au milieu
		return Math.max(0.0, Math.min(1.0, bump));
	}

	/** Max de brins de la case CE tick : 0 hors bande, sinon
	 *  clamp(round(brinsMax × fertilité × sol × saison), 1, brinsMax).
	 *  La saison fait respirer la pâture (hiver → max plus bas). */
	public int maxBrinsAt(int x, int y) {
		double f = grassFertility(x, y);
		if (f <= 0.0) return 0;
		// Sol : malus pierre, bonus cendre post-incendie (cohérent avec grassGerminationProb).
		double soil = (world.getStoneCAValue(x, y) > 0) ? STONE_FERTILITY_MAX : 1.0;
		if (ashBoost[x % _dx][y % _dy] > 0) soil = Math.min(1.0, soil * ASH_BOOST_FACTOR);
		double v = brinsMax * f * soil * world.seasonalFertility();
		int m = (int) Math.round(v);
		return Math.max(1, Math.min(brinsMax, m));
	}

	/** Plafond de brins sur sol minéral (≠ STONE_GROWTH_FACTOR qui régit la PROBA
	 *  de germination ; ici c'est le plafond de brins, pas une probabilité). */
	private static final double STONE_FERTILITY_MAX = 0.3;

	/** Marque la cellule comme enrichie par la cendre : booste pH pendant
	 *  ASH_BOOST_DURATION ticks (succession après incendie). */
	public void markAsh(int x, int y) {
		ashBoost[x][y] = secToTicks(ASH_SEC);
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
    	for (boolean[] row : ignitedThisTick) java.util.Arrays.fill(row, false);  // Option 2 : borne le feu à 1 case/tick
    	for(int d=0;d<world.list.size();d++){
    		int c=world.list.get(d);                 // encodage World : c = x*_dy + y
			int i=c/_dy;                             // x
			int j=c%_dy;                             // y
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
    					if(Math.random() < grassGerminationProb(i, j) * tickRateScale() && canGrowGrass(i, j)){
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
		    				// Propagation — Option 2 : herbe→herbe 8-connexe probabiliste
		    				// bornée par tick (ignitedThisTick) ; sources de chaleur EXTERNES
		    				// (lave / arbre en feu adjacents) = ignition immédiate.
		    				boolean extHeat =
		    						world.getLavaCAValue( (i+_dx-1)%(_dx) , j ) != 0 ||
		    						world.getLavaCAValue( (i+_dx+1)%(_dx) , j ) != 0 ||
		    						world.getLavaCAValue( i , (j+_dy+1)%(_dy) ) != 0 ||
		    						world.getLavaCAValue( i , (j+_dy-1)%(_dy) ) != 0 ||
		    						ForestCA.isTreeOnFire(world.getForestCAValue( (i+_dx-1)%(_dx) , j )) ||
		    						ForestCA.isTreeOnFire(world.getForestCAValue( (i+_dx+1)%(_dx) , j )) ||
		    						ForestCA.isTreeOnFire(world.getForestCAValue( i , (j+_dy+1)%(_dy) )) ||
		    						ForestCA.isTreeOnFire(world.getForestCAValue( i , (j+_dy-1)%(_dy) ));
		    				double envFactor = world.fireSpreadFactor() * tickRateScale();
		    				double qNoCatch = 1.0;
		    				for (int wx = -1; wx <= 1; wx++)
		    					for (int wy = -1; wy <= 1; wy++) {
		    						if (wx == 0 && wy == 0) continue;
		    						int nx = (i+wx+_dx)%_dx, ny = (j+wy+_dy)%_dy;
		    						if (this.getCellState(nx, ny) == 2 && !ignitedThisTick[nx][ny])
		    							// -wx,-wy = direction fire travels (from nx,ny toward i,j). Math.max(0,..) :
		    							// le facteur-vent (≤4) peut faire dépasser 1 le terme dirSpread×wind×env ; on
		    							// borne chaque terme à [0,1] pour garder un produit de probabilités monotone.
		    							qNoCatch *= Math.max(0.0, 1.0 - dirSpreadWeight(wx, wy) * world.fireWindFactor(-wx, -wy) * envFactor);
		    					}
		    				if ( extHeat || Math.random() < 1.0 - qNoCatch )
		    				{
		    					this.setCellState(i,j,2);
		    					ignitedThisTick[i][j] = true;
		    					NbHerbe-=1;
		    				}
		    				else
		    					if ( Math.random() < pF * tickRateScale() ) // feu spontané
		    					{
		    						this.setCellState(i,j,2);
		    						ignitedThisTick[i][j] = true;
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
