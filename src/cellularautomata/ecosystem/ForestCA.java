
package cellularautomata.ecosystem;

import java.util.Collections;

import cellularautomata.CellularAutomataDouble;
import cellularautomata.CellularAutomataInteger;
import objects.Layer;
import objects.Material;
import objects.dynamic.TreeProjectile;
import ui.SimulationConfig;
import worlds.World;

public class ForestCA extends CellularAutomataInteger {

	
	CellularAutomataDouble _cellsHeightValuesCA;

	World world;
	public double darbre = 0.1;// densite arbre// 0.6 test fire
	public double pF=0.00003;//probabilite de prendre feu pour les arbres
	public double pA=0.000006;// 0.00006// probabilite d'apparition des arbres
	int tDispertion=60;// temps avant dispertion des cendres — augmenté pour que la combustion soit visible plus longtemps (3 sec à 20 Hz)

	// ── Système de states (refonte 2026-05-28) ──────────────────────────────
	// 0       = vide
	// 1       = arbre vivant
	// 2, 3, 4 = arbre en feu (3 sub-states pour étendre la durée visible)
	// 5       = arbre brûlé
	// 6..5+tDispertion = cendres en dispersion
	public static final int FIRE_FIRST = 2;
	public static final int FIRE_LAST  = 4;
	public static final int BURNT      = 5;
	/** Durée moyenne (en secondes wall-clock) d'un sub-state visible (feu ou brûlé).
	 *  Convertie en probabilité via simulationHz au runtime. À 2 sec × 20 Hz = ~40 ticks moyens. */
	private static final float TREE_STATE_DURATION_SEC = 2.0f;

	/** Probabilité d'avancer d'un sub-state à chaque tick. Calculée dynamiquement
	 *  depuis simulationHz pour que les durées en secondes restent constantes
	 *  quelle que soit la fréquence de simulation. */
	private static double stateAdvanceProbability() {
		int hz = SimulationConfig.getInstance().simulationHz;
		return 1.0 / (TREE_STATE_DURATION_SEC * hz);
	}

	/** True si le state correspond à un arbre en feu (n'importe quel sub-state). */
	public static boolean isTreeOnFire(int state) {
		return state >= FIRE_FIRST && state <= FIRE_LAST;
	}

	/** Accesseur pour permettre à LavaCA d'accélérer la dispersion (arbre dans coulée). */
	public int getTDispertion() { return tDispertion; }

	/** Lecture de la progression de croissance d'une cellule. */
	public double getGrowth(int x, int y) { return growth[x][y]; }

	/** Écriture directe (init + tests). */
	public void setGrowth(int x, int y, double v) { growth[x][y] = v; }

	/** True si la cellule porte un marqueur de forêt à afficher (toute case non vide). */
	public boolean hasMarker(int x, int y) {
		return getCellState(x, y) >= 1;
	}

	/**
	 * Couleur du marqueur selon l'état de la cellule (reprise de l'ancienne
	 * coloration du sol) : vert (vivant, modulé par l'altitude), orange/jaune
	 * (feu), noir (brûlé), estompé vers le terrain (cendres). Écrit dans out[0..2].
	 */
	public void getStateColor(int x, int y, float[] out) {
		int s = getCellState(x, y);
		if (s == 1) {                       // arbre vivant : tache de terre / ombre
			// brun terreux sombre — lisible aussi bien sur l'herbe que sur le
			// sable (le vert se confondait avec le gazon). Évoque le sol au pied
			// de l'arbre / l'ombre de la canopée plutôt qu'un disque coloré.
			out[0] = 60/255f; out[1] = 42/255f; out[2] = 26/255f;
		} else if (s >= FIRE_FIRST && s <= FIRE_LAST) {   // en feu
			if (Math.random() < 0.5) { out[0] = 1f; out[1] = 40/255f;  out[2] = 0f; }
			else                     { out[0] = 1f; out[1] = 206/255f; out[2] = 0f; }
		} else if (s == BURNT) {            // brûlé
			out[0] = 0f; out[1] = 0f; out[2] = 0f;
		} else {                            // cendres (BURNT+1 .. BURNT+tDispertion) : fondu vers le terrain
			float[] cinit = new float[3];
			world.colorInit(x, y, cinit);
			out[0] = (cinit[0]*s)/(BURNT+tDispertion);
			out[1] = (cinit[1]*s)/(BURNT+tDispertion);
			out[2] = (cinit[2]*s)/(BURNT+tDispertion);
		}
	}

	/**
	 * Fertilité de la cellule ∈ [0,1] = produit de trois facteurs :
	 *  - altitude : bosse lisse, optimum à mi-hauteur, 0 aux extrêmes ;
	 *  - sol : 1.0 sur terre, STONE_FERTILITY sur pile minérale ;
	 *  - compétition : décroît avec le nombre de voisins-arbres (8-connexe).
	 * Pilote la vitesse de croissance ET la taille adulte max.
	 */
	public double fertility(int x, int y) {
		double maxH = world.getMaxEverHeight();
		double hN = (maxH > 0) ? world.getCellHeight(x, y) / maxH : 0.0;
		double d = (hN - 0.5) / 0.5;
		double fAlt = 1.0 - d * d;          // pic à hN=0.5, 0 à hN=0 ou 1
		if (fAlt < 0) fAlt = 0;
		double fSoil = (world.getStoneCAValue(x, y) > 0) ? STONE_FERTILITY : 1.0;
		int n = 0;
		for (int dx = -1; dx <= 1; dx++)
			for (int dy = -1; dy <= 1; dy++) {
				if (dx == 0 && dy == 0) continue;
				if (this.getCellState((x + dx + _dx) % _dx, (y + dy + _dy) % _dy) == 1) n++;
			}
		double fComp = 1.0 - COMP_K * (n / 8.0);
		if (fComp < 0) fComp = 0;
		// L5 — modulation saisonnière : la forêt pousse vite au printemps, à
		// l'arrêt l'hiver. Multiplie germination ET croissance (fertility est
		// lue par les deux).
		return fAlt * fSoil * fComp * waterProximityFactor(x, y) * world.seasonalFertility();
	}

	/**
	 * Facteur d'humidité ∈ [WATER_DRY_MIN, 1] : modélise la sécheresse loin de
	 * l'eau (berges plus fertiles). 1.0 au contact de l'eau, décroît avec la
	 * distance, plancher WATER_DRY_MIN au-delà de WATER_REACH cases. Reste dans
	 * [0,1] → préserve l'invariant fertilité ∈ [0,1] (taux de croissance, seed,
	 * taille de rendu). Scan en anneaux avec sortie anticipée à la 1re eau
	 * trouvée : bon marché là où il y a de l'eau proche (cas courant), coûteux
	 * seulement au cœur des terres sèches.
	 */
	private double waterProximityFactor(int x, int y) {
		for (int r = 1; r <= WATER_REACH; r++) {
			for (int dx = -r; dx <= r; dx++)
				for (int dy = -r; dy <= r; dy++) {
					if (Math.max(Math.abs(dx), Math.abs(dy)) != r) continue; // anneau
					if (world.getCellHeight((x + dx + _dx) % _dx, (y + dy + _dy) % _dy) < 0) {
						double prox = 1.0 - (double) (r - 1) / WATER_REACH; // r=1→1, r=REACH→1/REACH
						return WATER_DRY_MIN + (1.0 - WATER_DRY_MIN) * prox;
					}
				}
		}
		return WATER_DRY_MIN;
	}

	/** Nombre de ticks pour atteindre g=1 en fertilité maximale (≥ 1). */
	private double ticksToAdult() {
		SimulationConfig c = SimulationConfig.getInstance();
		double ticksPerGameDay = (double) c.cycleTotalSec * c.simulationHz;
		return Math.max(1.0, treeGrowthDays * ticksPerGameDay);
	}

	/**
	 * Probabilité qu'un arbre germe sur la case vide (x,y) ce tick. Combine :
	 *  - la proba de base `pA` ;
	 *  - la pénalité de sol minéral (× STONE_GROWTH_FACTOR sur pierre) ;
	 *  - la DISPERSION DE GRAINES : les arbres ADULTES voisins (8-connexe,
	 *    pondérés par leur maturité `growth ∈ [0,1]`) augmentent la proba via
	 *    `× (1 + SEED_DISPERSAL_K × pression)`. La forêt se propage donc depuis
	 *    les peuplements existants plutôt que par germination uniforme.
	 * Fonction pure (lecture seule). Bornée par construction à pA × (1 + K).
	 */
	public double germinationProb(int x, int y) {
		double p = pA;
		if (world.getStoneCAValue(x, y) > 0) p *= STONE_GROWTH_FACTOR;
		if (ashBoost[x][y] > 0) p *= ASH_BOOST_FACTOR;   // sol cendré récent → reforestation accélérée
		double seed = 0.0;
		for (int dx = -1; dx <= 1; dx++)
			for (int dy = -1; dy <= 1; dy++) {
				if (dx == 0 && dy == 0) continue;
				int nx = (x + dx + _dx) % _dx, ny = (y + dy + _dy) % _dy;
				if (this.getCellState(nx, ny) == 1) seed += growth[nx][ny]; // maturité ∈ [0,1]
			}
		seed /= 8.0;                                    // pression de graines ∈ [0,1]
		return p * (1.0 + SEED_DISPERSAL_K * seed);
	}

	/** Marque la cellule comme enrichie par la cendre (post-incendie) : le sol
	 *  fertile booste la germination pendant ASH_BOOST_DURATION ticks. Appelé à
	 *  la dispersion des cendres d'un arbre brûlé. */
	public void markAsh(int x, int y) {
		ashBoost[x][y] = ASH_BOOST_DURATION;
	}

	/** Valeur de croissance de la cellule après un tick, bornée à 1. Fonction pure. */
	public double grownValue(int x, int y) {
		double rate = (1.0 / ticksToAdult()) * fertility(x, y);
		return Math.min(1.0, growth[x][y] + rate);
	}

	/** Hash déterministe ∈ [0,1) pour le jitter de taille initiale. */
	private static double hash01(int x, int y, int salt) {
		double v = Math.sin(x * 12.9898 + y * 78.233 + salt * 37.719) * 43758.5453;
		return v - Math.floor(v);
	}

	int NbArbreSaint=0;

	/**
	 * Facteur multiplicatif appliqué à la proba de germination quand la
	 * cellule a une pile de pierre (volcan refroidi). 0.1 = 10× moins de
	 * chance qu'un arbre apparaisse sur un sol minéral comparé à un sol
	 * de terre native. La forêt finit par reconquérir la pierre mais c'est
	 * long, ce qui est réaliste.
	 */
	private static final double STONE_GROWTH_FACTOR = 0.1;

	/** Durée (jours-jeu) pour atteindre l'âge adulte (conditions normales). Réglé depuis SimulationConfig. */
	public double treeGrowthDays = 10.0;

	/** Fertilité du sol minéral (pile STONE/volcan refroidi) vs terre native (=1.0). */
	private static final double STONE_FERTILITY = 0.3;
	/** Pénalité de compétition : 8 voisins-arbres ⇒ fertilité × (1 - COMP_K). */
	private static final double COMP_K = 0.6;
	/** Portée (en cases) de l'effet fertilisant de l'eau au-delà duquel le sol est « sec ». */
	private static final int WATER_REACH = 4;
	/** Plancher de fertilité en sol sec (loin de l'eau) ; 1.0 au contact de l'eau. */
	private static final double WATER_DRY_MIN = 0.55;
	/** Dispersion de graines : 8 voisins adultes (maturité 1) ⇒ proba germination × (1 + K). */
	private static final double SEED_DISPERSAL_K = 4.0;

	/** Progression de croissance par cellule, g ∈ [0,1] (0 = pousse, 1 = adulte). */
	private final double[][] growth;

	/** Tours restants d'enrichissement du sol par la cendre (post-incendie) par
	 *  cellule ; 0 = sol normal. Booste la germination (succession écologique). */
	private final int[][] ashBoost;
	/** Facteur multiplicatif de germination sur sol cendré récent. */
	private static final double ASH_BOOST_FACTOR = 3.0;
	/** Durée (ticks) de l'effet fertilisant de la cendre après dispersion. */
	private static final int ASH_BOOST_DURATION = 200;

	public ForestCA ( World __world, int __dx , int __dy, CellularAutomataDouble cellsHeightValuesCA )
	{
		super(__dx,__dy,false ); // buffering must be true.
		
		_cellsHeightValuesCA = cellsHeightValuesCA;// reference to height CA

		this.world = __world;
		this.growth = new double[_dx][_dy];
		this.ashBoost = new int[_dx][_dy];
	}
	
	public void init()
	{
		for ( int x = 0 ; x != _dx ; x++ )
    		for ( int y = 0 ; y != _dy ; y++ )
    		{
    			if ( _cellsHeightValuesCA.getCellState(x,y) >= 0 )
    			{
    				if ( Math.random() < darbre){ // was: 0.71
    					this.setCellState(x, y, 1); // tree
    					NbArbreSaint+=1;
    				}
    				else
    					this.setCellState(x, y, 0); // empty
    			}
    		}
    	// Seed de la croissance : forêt établie → tailles variées mais homogènes
    	// par zone (même fertilité), avec un léger jitter individuel.
    	for ( int x = 0 ; x != _dx ; x++ )
    		for ( int y = 0 ; y != _dy ; y++ )
    		{
    			if ( this.getCellState(x, y) == 1 ) {
    				double F = fertility(x, y);
    				double jitter = (hash01(x, y, 7) - 0.5) * 0.30; // ±0.15
    				double g = 0.5 + 0.5 * F + jitter;
    				if (g < 0) g = 0; if (g > 1) g = 1;
    				growth[x][y] = g;
    			} else {
    				growth[x][y] = 0.0;
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
    			if (this.getCellState(i, j)>=0 &&  this.getCellState(i,j)<= BURNT+tDispertion)
    			{	
    				// Pour une case sans arbre. Conditions de germination :
    				//   - pas de lave (couche LAVA au sommet) : interdit (la lave
    				//     en cours de refroidissement reste classifiée LAVA tant
    				//     que cellState < solidifyEnd). Une fois totalement refroidie,
    				//     la couche bascule en STONE et la germination redevient
    				//     possible — avec proba réduite (sol moins fertile).
    				//   - sol au-dessus du niveau de la mer (height >= 0).
    				if ( this.getCellState(i,j) == 0
    						&& world.getLavaCAValue(i, j)==0){
    					if(Math.random() < germinationProb(i, j) && world.getCellHeight(i,j) >= 0){
    						this.setCellState(i,j,1);
    						NbArbreSaint+=1;
    					}
	    			}
    				//pour un arbre en cendre
    				else if ( this.getCellState(i,j) == BURNT + tDispertion){
	    				this.setCellState(i,j,0); //dispertion
	    				markAsh(i, j);            // la cendre dispersée enrichit le sol
	    			}
    				// Pour une case avec arbre
	    			else if ( this.getCellState(i,j) == 1 ) // tree?
	    			{
	    				if(world.getLavaCAValue(i, j)!=0){
	    					// Lave directement sur la cellule. projectBurningTree décide :
	    					//  - dans cratère → projection explosive en arc (vrai)
	    					//  - hors cratère + voisin LAVA → poussée en aval (vrai)
	    					//  - hors cratère sans flux → brûle sur place (faux)
	    					if (projectBurningTree(i, j)) {
	    						this.setCellState(i,j,0);
	    						NbArbreSaint-=1;
	    					} else {
	    						this.setCellState(i,j, FIRE_FIRST);
	    						NbArbreSaint-=1;
	    					}
						}else{
		    				// check if neighbors are burning (any fire sub-state)
		    				// L6 — la pluie ralentit la propagation : un voisin en feu
		    				// n'embrase l'arbre qu'avec proba fireSpreadFactor (1.0 à sec).
		    				// La lave adjacente, elle, embrase toujours (chaleur directe).
		    				boolean lavaAdj =
		    						world.getLavaCAValue( (i+_dx-1)%(_dx) , j ) != 0 ||
		    						world.getLavaCAValue( (i+_dx+1)%(_dx) , j ) != 0 ||
		    						world.getLavaCAValue( i , (j+_dy+1)%(_dy) ) != 0 ||
		    						world.getLavaCAValue( i , (j+_dy-1)%(_dy) ) != 0;
		    				boolean fireAdj =
		    						isTreeOnFire(this.getCellState( (i+_dx-1)%(_dx) , j )) ||
		    						isTreeOnFire(this.getCellState( (i+_dx+1)%(_dx) , j )) ||
		    						isTreeOnFire(this.getCellState( i , (j+_dy+1)%(_dy) )) ||
		    						isTreeOnFire(this.getCellState( i , (j+_dy-1)%(_dy) ));
		    				if ( lavaAdj || (fireAdj && Math.random() < world.fireSpreadFactor()) )
		    				{
		    					this.setCellState(i,j, FIRE_FIRST);
		    					NbArbreSaint-=1;
		    				}
		    				else
		    					if ( Math.random() < pF * world.fireSpreadFactor() ) // spontaneously take fire ?
		    					{
		    						this.setCellState(i,j, FIRE_FIRST);
		    						NbArbreSaint-=1;
		    					}
		    					else
		    					{
		    						this.setCellState(i,j,1); // copied unchanged
		    					}
						}
	    			}
    				// Pour une case avec arbre en feu (sub-states FIRE_FIRST..FIRE_LAST)
	    			else if ( isTreeOnFire(this.getCellState(i,j)) )
	    			{
	    				int curState = this.getCellState(i, j);
	    				// Si la lave atteint l'arbre en feu (n'importe quel sub-state), il est
	    				// emporté/projeté par la coulée. projectBurningTree gère la direction.
	    				if (world.getLavaCAValue(i, j) != 0) {
	    					if (projectBurningTree(i, j)) {
	    						this.setCellState(i,j,0); // arbre parti en vol
	    					} else {
	    						this.setCellState(i,j, BURNT); // pas de flux, devient brûlé
	    					}
	    				}
	    				else if (curState < FIRE_LAST) {
	    					// Progresse via proba 1/40 → chaque sub-state dure ~2 sec à 20 Hz
	    					if (Math.random() < stateAdvanceProbability()) {
	    						this.setCellState(i,j, curState + 1);
	    					} else {
	    						this.setCellState(i,j, curState); // reste en feu
	    					}
	    				}
	    				else {
	    					// Dernier sub-state feu → devient brûlé (toujours avec proba)
	    					if (Math.random() < stateAdvanceProbability()) {
	    						this.setCellState(i,j, BURNT);
	    					} else {
	    						this.setCellState(i,j, curState);
	    					}
	    				}
	    			}
	    			else{
	    				int s = this.getCellState(i,j);
	    				if (s == BURNT) {
	    					// Brûlé visible (noir) : reste ~2 sec via proba 1/40
	    					if (Math.random() < stateAdvanceProbability()) {
	    						this.setCellState(i,j, s + 1);
	    					} else {
	    						this.setCellState(i,j, s);
	    					}
	    				} else if (s > BURNT && s < BURNT+tDispertion) {
	    					// Cycle cendres invisibles : avance rapide (1 tick par +1)
	    					this.setCellState(i,j, s + 1);
	    				}
	    			}
	    			
	    			// Croissance : un arbre vivant grandit (taux ∝ fertilité) ;
	    			// tout autre état (feu, brûlé, vide, germination ce tick)
	    			// remet la croissance à 0 → une repousse repart en pousse.
	    			if ( this.getCellState(i, j) == 1 )
	    				growth[i][j] = grownValue(i, j);
	    			else
	    				growth[i][j] = 0.0;
    			}
    		}
    	this.swapBuffer();
	}

	/**
	 * Décide du comportement d'un arbre touché par la lave :
	 *  - Dans le rayon d'une éruption active → projection explosive en arc (vol long)
	 *  - Hors cratère, avec voisin LAVA détecté → poussée en aval (direction opposée
	 *    au voisin LAVA le plus haut, suivant le flux gravitaire)
	 *  - Hors cratère, sans voisin LAVA (cas pathologique : tephra LAVA atterri direct)
	 *    → brûle sur place via cycle ForestCA normal (return false → caller met state=2)
	 *
	 * Retourne true si un TreeProjectile a été créé (caller libère la cellule),
	 * false si l'arbre doit brûler sur place (caller passe state à 2).
	 */
	private boolean projectBurningTree(int i, int j) {
		if (cellularautomata.ecosystem.LavaCA.isInActiveEruptionRadius(world, i, j)) {
			// Cratère : explosion → projection en arc dans une direction aléatoire.
			double angle = Math.random() * 2 * Math.PI;
			double r = 8.0 + Math.random() * 12.0;  // 8-20 cellules
			spawnTreeProjectile(i, j, angle, r, 25f, 25);
			return true;
		}

		// Hors cratère : cherche le delta vers le voisin LAVA le plus haut (= source du flux)
		int[] upstreamDelta = findHighestLavaDelta(i, j);
		if (upstreamDelta == null) {
			// Pas de flux détecté (tephra LAVA atterri isolément, ou voisins totalement
			// solidifiés). On crée un projectile "static burn" : arbre debout qui brûle
			// visiblement sur place pendant ~3 sec, change de couleur, puis disparaît.
			// La cellule source est libérée (le projectile gère son propre rendu).
			world.uniqueDynamicObjects.add(objects.dynamic.TreeProjectile.createStatic(world, i, j));
			return true;
		}

		// Aval = direction opposée à l'upstream (delta inversé, évite bug wrap-around toroïdal)
		double angle = Math.atan2(-upstreamDelta[1], -upstreamDelta[0]);
		double r = 3.0 + Math.random() * 3.0;  // 3-6 cellules
		spawnTreeProjectile(i, j, angle, r, 15f, 25);
		return true;
	}

	/** Cherche parmi les 4 voisins cardinaux celui dont la couche LAVA la plus
	 *  haute (n'importe où dans la stack, pas seulement au top) est la plus haute,
	 *  et retourne le DELTA (dx, dy) ∈ {-1,0,1}². Null si AUCUN voisin n'a de
	 *  LAVA dans sa stack.
	 *
	 *  Pourquoi chercher dans la stack et pas juste au top : une coulée laisse
	 *  derrière elle une croûte STONE/BASALT superficielle mais la LAVA continue
	 *  de couler dessous (modèle Layer + observation physique des tunnels de lave).
	 *  Le top peut être STONE alors que la LAVA est juste en dessous, encore active.
	 *
	 *  Représente la direction d'où vient le flux gravitaire entrant. */
	private int[] findHighestLavaDelta(int i, int j) {
		int[][] dirs = { { -1, 0 }, { 1, 0 }, { 0, -1 }, { 0, 1 } };
		int[] bestDelta = null;
		float bestLavaTop = -Float.MAX_VALUE;
		for (int[] d : dirs) {
			int nx = (i + d[0] + _dx) % _dx;
			int ny = (j + d[1] + _dy) % _dy;
			float lavaTop = findHighestLavaTopInStack(nx, ny);
			if (Float.isNaN(lavaTop)) continue;  // aucune LAVA dans la stack
			if (lavaTop > bestLavaTop) { bestLavaTop = lavaTop; bestDelta = d; }
		}
		return bestDelta;
	}

	/** Retourne l'altitude du top de la couche LAVA la plus haute dans la stack
	 *  de (x, y), ou NaN si aucune couche LAVA n'existe. */
	private float findHighestLavaTopInStack(int x, int y) {
		java.util.List<Layer> stack = world.getStack(x, y);
		if (stack == null || stack.isEmpty()) return Float.NaN;
		float vScale = world.getVerticalScale();
		float cursorZ = (float)(world.getCellHeight(x, y) * vScale);
		float bestLavaTop = Float.NaN;
		for (Layer l : stack) {
			cursorZ += l.thickness;
			if (l.material == Material.LAVA) {
				bestLavaTop = cursorZ;  // top de cette couche LAVA ; on garde la plus haute (= dernière itérée)
			}
		}
		return bestLavaTop;
	}

	private void spawnTreeProjectile(int i, int j, double angle, double r, float apex, int ticks) {
		// Offsets signed (peuvent être négatifs ou > _dx) — utilisés pour le vol.
		int dxRaw = (int) Math.round(Math.cos(angle) * r);
		int dyRaw = (int) Math.round(Math.sin(angle) * r);
		// Coords cibles normalisées (pour l'atterrissage et la lecture d'altitude).
		int tx = ((i + dxRaw) % _dx + _dx) % _dx;
		int ty = ((j + dyRaw) % _dy + _dy) % _dy;

		float sx = i + 0.5f;
		float sy = j + 0.5f;
		float sz = world.getCellTopAltitude(i, j);
		// Pour le vol : utiliser coords absolues NON normalisées (sx + dxRaw)
		// pour éviter que le projectile traverse tout le monde via wrap-around
		// toroïdal au lieu de faire le chemin court. Le wrap caméra dans
		// displayUniqueObject gère l'affichage à travers le bord.
		float dxAbs = sx + dxRaw;
		float dyAbs = sy + dyRaw;
		float dz = world.getCellTopAltitude(tx, ty);
		TreeProjectile p = new TreeProjectile(world, sx, sy, sz, dxAbs, dyAbs, dz, tx, ty, apex, ticks);
		world.uniqueDynamicObjects.add(p);
	}
}
