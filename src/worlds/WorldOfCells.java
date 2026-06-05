
package worlds;



import java.util.Collections;

import javax.media.opengl.GL2;

import agents.*;
import cellularautomata.ecosystem.*;
import objects.*;
import objects.blocks.*;
import objects.dynamic.*;
import objects.landmarks.*;
import objects.vegetation.*;
import ui.SimulationConfig;

public class WorldOfCells extends World {

	/**
	 * Config optionnelle (typiquement fournie par le menu de lancement, Phase 6).
	 * Si non null, ses valeurs sont appliquées au début de init() : populations,
	 * paramètres jour/nuit, et biologie des agents juste après leur création.
	 */
	public SimulationConfig config = null;

	public int nbloups = 10;//20
	public int nbmoutons =20;//45//20
	public int nbhumains=2;
	int bergerie;
	int wolfHome;
	float nivPlage;
	
	public ForestCA forestCA;
	public GrassCA grassCA;
	public LavaCA lavaCA;

    public void init ( int __dxCA, int __dyCA, double[][] landscape )
    {
    	super.init(__dxCA, __dyCA, landscape);

    	// Applique la config (Phase 6) avant tout spawn d'agents.
    	if (config != null) {
    		nbloups   = config.nbLoups;
    		nbmoutons = config.nbMoutons;
    		nbhumains = config.nbHumains;
    		// Module 1 (refonte 2026-05) : conversion sec → ticks via simulationHz
    		setDureeJour(Math.max(1, Math.round(config.cycleTotalSec * config.simulationHz / 2f)));
    		setTransitionJour(Math.max(1, Math.round(config.transitionJourSec * config.simulationHz)));
    	}

    	// add colors
    	
    	for ( int x = 0 ; x < __dxCA ; x++ )
    		for ( int y = 0 ; y < __dyCA ; y++ )
    		{
    			float color[] = new float[3];
	        	colorInit(x,y,color);
	        	this.cellsColorValues.setCellState(x, y, color);
    		}
    	
    	// add some objects
    	
    	Collections.shuffle(this.list);
    	int d=0;
    	bergerie=-1;
    	wolfHome=-1;
    	int testFRACTTREE=0;
    	while(d<this.list.size() ){
    		int x=this.list.get(d)%__dxCA;
			int y=this.list.get(d)/__dyCA;
			if(this.getCellHeight(x, y)==this.getMaxEverHeight() && testFRACTTREE==0){
				uniqueObjects.add(new FractalTree(x,y,this));
				testFRACTTREE=1;
			}
			if(bergerie==-1
				&& y>__dyCA/2
				&& x>__dxCA/2
				&&this.getCellHeight(x, y)>this.getMaxEverHeight()/10 
				&& this.getCellHeight(x, y)<this.getMaxEverHeight()*0.8){
					bergerie=this.list.get(d);
			    	uniqueObjects.add(new Monolith((x-5+__dxCA)%__dxCA,(y-5+__dyCA)%__dyCA,this));
			    	uniqueObjects.add(new Monolith((x-5+__dxCA)%__dxCA,(y+5+__dyCA)%__dyCA,this));
			    	uniqueObjects.add(new Monolith((x+5+__dxCA)%__dxCA,(y-5+__dyCA)%__dyCA,this));
			    	uniqueObjects.add(new Monolith((x+5+__dxCA)%__dxCA,(y+5+__dyCA)%__dyCA,this));
			}
			if(wolfHome==-1
				&& y<__dyCA/2
				&& x<__dxCA/2
				&&this.getCellHeight(x, y)>this.getMaxEverHeight()/10 
				&& this.getCellHeight(x, y)<this.getMaxEverHeight()*0.8){
					wolfHome=this.list.get(d);
					uniqueObjects.add(new Monolith(x,(y+3+__dyCA)%__dyCA,this));
					uniqueObjects.add(new Monolith((x-3+__dxCA)%__dxCA,(y-3+__dyCA)%__dyCA,this));
					uniqueObjects.add(new Monolith((x+3+__dxCA)%__dxCA,(y-3+__dyCA)%__dyCA,this));
			}
			d++;
    	}
    	if(bergerie==-1)bergerie=0;
		if(wolfHome==-1)wolfHome=(__dyCA*__dyCA)-1;
		
		
    	int px=0;
		int py=0;
		for ( int i = 0 ; i != nbhumains; i++ ){
			px=(int)(Math.random()*dxCA);
			py=(int)(Math.random()*dyCA);
			Humain humanA = new Humain(px,py,this);
			humains.add(humanA);
			agents.add(humanA);
			uniqueDynamicObjects.add(humanA);
		}
    	for ( int i = 0 ; i != nbloups; i++ ){
			px=(int)(Math.random()*dxCA);
			py=(int)(Math.random()*dyCA);
			Loup predA=new Loup(px,py,this);
			applyConfigTo(predA);
			loups.add(predA);
			agents.add(predA);
			uniqueDynamicObjects.add(predA);
		}
    	for ( int i = 0 ; i != nbmoutons; i++ ){
			px=(int)(Math.random()*dxCA);
			py=(int)(Math.random()*dyCA);
			Mouton preyA=new Mouton(px,py,this);
			applyConfigTo(preyA);
			moutons.add(preyA);
			agents.add(preyA);
			uniqueDynamicObjects.add(preyA);
		}

    }

    /**
     * Vide les listes d'agents et les recrée à partir de la config actuelle.
     * Utilisé par le menu de lancement (Phase 6) après que l'utilisateur ait
     * cliqué sur Start avec des paramètres modifiés. Le paysage et les CA ne
     * sont pas régénérés — seules les populations sont remises à plat.
     *
     * Les paramètres world-level live (durée jour/nuit) sont également
     * réappliqués.
     */
    public void respawnAgents() {
    	if (config != null) {
    		nbloups   = config.nbLoups;
    		nbmoutons = config.nbMoutons;
    		nbhumains = config.nbHumains;
    		// Module 1 (refonte 2026-05) : conversion sec → ticks via simulationHz
    		setDureeJour(Math.max(1, Math.round(config.cycleTotalSec * config.simulationHz / 2f)));
    		setTransitionJour(Math.max(1, Math.round(config.transitionJourSec * config.simulationHz)));
    	}
    	// Met à jour les paramètres CA modifiables à chaud (probabilités, vitesse
    	// lave). Les densités initiales darbre/dherbe n'ont plus d'effet à ce
    	// stade — la CA était déjà init'ée — mais on synchronise quand même les
    	// fields pour cohérence avec le menu.
    	applyConfigToCAs();

    	uniqueDynamicObjects.removeAll(loups);
    	uniqueDynamicObjects.removeAll(moutons);
    	uniqueDynamicObjects.removeAll(humains);
    	agents.removeAll(loups);
    	agents.removeAll(moutons);
    	agents.removeAll(humains);
    	loups.clear();
    	moutons.clear();
    	humains.clear();

    	int px, py;
    	for (int i = 0; i < nbhumains; i++) {
    		px = (int)(Math.random() * dxCA);
    		py = (int)(Math.random() * dyCA);
    		Humain h = new Humain(px, py, this);
    		humains.add(h);
    		agents.add(h);
    		uniqueDynamicObjects.add(h);
    	}
    	for (int i = 0; i < nbloups; i++) {
    		px = (int)(Math.random() * dxCA);
    		py = (int)(Math.random() * dyCA);
    		Loup l = new Loup(px, py, this);
    		applyConfigTo(l);
    		loups.add(l);
    		agents.add(l);
    		uniqueDynamicObjects.add(l);
    	}
    	for (int i = 0; i < nbmoutons; i++) {
    		px = (int)(Math.random() * dxCA);
    		py = (int)(Math.random() * dyCA);
    		Mouton m = new Mouton(px, py, this);
    		applyConfigTo(m);
    		moutons.add(m);
    		agents.add(m);
    		uniqueDynamicObjects.add(m);
    	}
    }

    /**
     * Applique la config à un loup. {@code resetDynamic=true} (utilisé au spawn)
     * remet aussi à zéro l'énergie courante et la vitesse ; {@code false}
     * (utilisé pour les modifs live via le menu in-game) préserve l'état
     * dynamique de l'agent et clampe juste l'énergie au nouveau plafond.
     */
    private void applyConfigTo(Loup l) { applyConfigTo(l, true); }

    private void applyConfigTo(Loup l, boolean resetDynamic) {
    	if (config == null) return;
    	l.vision     = config.loupVision;
    	l.energieD   = config.loupEnergieMax;
    	if (resetDynamic) l.energie = l.energieD;
    	else if (l.energie > l.energieD) l.energie = l.energieD;
    	l.PreproD    = config.loupPrepro;
    	l.Prepro     = l.PreproD;
    	l.reproEnergyThreshold = config.loupReproEnergyThreshold;
    	l.reproOffspringRatio  = config.loupReproOffspringRatio;
    	l.vpas       = config.loupVpas;
    	l.vtrot      = config.loupVtrot;
    	l.vcourse    = config.loupVcourse;
    	l.swimFactor = config.loupSwimFactor;
    	l.maxAgeDays = config.loupMaxAgeDays;
    	if (resetDynamic) l.vitesse = l.vpas;
    }

    /** Variante mouton. */
    private void applyConfigTo(Mouton m) { applyConfigTo(m, true); }

    private void applyConfigTo(Mouton m, boolean resetDynamic) {
    	if (config == null) return;
    	m.vision      = config.moutonVision;
    	m.energieMAX  = config.moutonEnergieMax;
    	if (resetDynamic) m.energie = m.energieMAX;
    	else if (m.energie > m.energieMAX) m.energie = m.energieMAX;
    	m.PreproD     = config.moutonPrepro;
    	m.Prepro      = m.PreproD;
    	m.reproEnergyThreshold = config.moutonReproEnergyThreshold;
    	m.reproOffspringRatio  = config.moutonReproOffspringRatio;
    	m.vmarche     = config.moutonVmarche;
    	m.vcourse     = config.moutonVcourse;
    	m.swimFactor  = config.moutonSwimFactor;
    	m.maxAgeDays  = config.moutonMaxAgeDays;
    	if (resetDynamic) m.vitesse = m.vmarche;
    }

    /**
     * Met à jour à chaud tous les agents et CAs avec les valeurs courantes de
     * config. Préserve l'état dynamique des agents (énergie, vitesse instantanée).
     * Utilisé par le menu in-game (Phase 7) quand l'utilisateur ajuste un slider.
     *
     * N'agit pas sur : populations (les agents ne sont pas respawn), paysage,
     * densités initiales de CA.
     */
    public void applyLiveConfig() {
    	if (config != null) {
    		// Module 1 (refonte 2026-05) : conversion sec → ticks via simulationHz
    		setDureeJour(Math.max(1, Math.round(config.cycleTotalSec * config.simulationHz / 2f)));
    		setTransitionJour(Math.max(1, Math.round(config.transitionJourSec * config.simulationHz)));
    	}
    	applyConfigToCAs();
    	for (Loup l : loups) applyConfigTo(l, false);
    	for (Mouton m : moutons) applyConfigTo(m, false);
    }
    
    public void colorInit(int x, int y, float color[]){

    	float height = (float) this.getCellHeight(x, y);
    	nivPlage=(float)(this.getMaxEverHeight()/10);
    	if ( height >= 0 && height <nivPlage){
    		//sand 
    		color[0] = 254/255f- (20/255f) * height / nivPlage ;
			color[1] = 219/255f- (20/255f) * height / nivPlage ;
			color[2] = 183/255f- (20/255f) * height / nivPlage ;
    	}
    	else if ( height >= nivPlage )
        {
        	// snowy mountains
        	/*
        	color[0] = height / (float)this.getMaxEverHeight();
			color[1] = height / (float)this.getMaxEverHeight();
			color[2] = height / (float)this.getMaxEverHeight();
			/**/
        	
			// green mountains : vert plus mat / olive (l'ancienne formule
			// donnait G ≥ 0.9 → trop lumineux et désaturé sous la lumière
			// soleil). On compose maintenant un vert avec un peu de terre
			// (R) et de bleu (B) qui s'éclairent doucement avec l'altitude.
        	/**/
        	float ratio = height / ( (float)this.getMaxEverHeight() );
        	color[0] = 0.15f + 0.30f * ratio;
			color[1] = 0.55f + 0.20f * ratio;
			color[2] = 0.10f + 0.20f * ratio;
			/**/
        }
        else
        {
        	// water
        	/**/
			color[0] = (52/255f)-((26/255f)*height)/(float)this.getMinEverHeight();
			color[1] = (168/255f)-((84/255f)*height)/(float)this.getMinEverHeight();
			color[2] = (180/255f)-((90/255f)*height)/(float)this.getMinEverHeight();
        	/*
        	color[0] = (52/255f)-((13/255f)*height)/(float)this.getMinEverHeight();
			color[1] = (84/255f)-((42/255f)*height)/(float)this.getMinEverHeight();
			color[2] = (90/255f)-((45/255f)*height)/(float)this.getMinEverHeight();
			/**/
        }
    	
	}
    
    protected void initCellularAutomata(int __dxCA, int __dyCA, double[][] landscape)
    {
    	// stoneCA / nivSolCA supprimés : la pierre vit dans le système Layer
    	// (World.cellStacks), nivSol n'était jamais utilisé.
    	forestCA = new ForestCA(this,__dxCA,__dyCA,cellsHeightValuesCA);
    	// Important : appliquer la config AVANT init() — `darbre` est lu dans
    	// ForestCA.init() pour fixer la densité initiale d'arbres.
    	applyConfigToCAs();
    	forestCA.init();
    	grassCA = new GrassCA(this,__dxCA,__dyCA,cellsHeightValuesCA);
    	applyConfigToCAs();
    	grassCA.init();
    	lavaCA = new LavaCA(this,__dxCA,__dyCA,cellsHeightValuesCA);
    	applyConfigToCAs();
    	lavaCA.init();
    }

    /**
     * Copie les valeurs CA du config vers les instances déjà créées. Sûr à
     * appeler plusieurs fois (chaque CA test sa propre nullité avant assign).
     * Les fields des CAs sont package-private, accessibles directement depuis ici.
     */
    // subsidencePeriod (déprécié) reste lu comme kill-switch legacy du drainage
    // (== 0 désactive). Géré explicitement → on assume la dépréciation ici.
    @SuppressWarnings("deprecation")
    private void applyConfigToCAs() {
    	if (config == null) return;
    	if (forestCA != null) {
    		forestCA.darbre = config.forestDensite;
    		forestCA.pA     = config.forestProbApparition;
    		forestCA.pF     = config.forestProbFeu;
    		forestCA.treeGrowthDays = config.treeGrowthDays;
    	}
    	if (grassCA != null) {
    		grassCA.dherbe = config.herbeDensite;
    		grassCA.pH     = config.herbeProbApparition;
    		grassCA.pF     = config.herbeProbFeu;
    	}
    	if (lavaCA != null) {
    		lavaCA.pErruption      = config.laveProbErruption;
    		lavaCA.craterHoleDepth = config.craterHoleDepth;
    		lavaCA.subsidencePeriod = config.subsidencePeriod;
    		lavaCA.erruptionPowerMin = config.erruptionPowerMin;
    		lavaCA.erruptionPowerMax = config.erruptionPowerMax;
    	}
    }
    
    protected void stepCellularAutomata()
    {
    	// Module 1 (refonte 2026-05) : plus de filtre iteration%10.
    	// TimeKeeper (Landscape) gère la fréquence via simulationHz.
    	// Les CAs tournent à chaque step (= simulationHz fois par seconde réelle).
    	forestCA.step();
    	grassCA.step();
    	lavaCA.step();
    }
    
    protected void stepAgents()
    {
    	// Cleanup des projectiles atterris (alive=false posé après le cycle complet).
    	// Itération inversée pour supprimer en place sans casser les indices.
    	for (int i = uniqueDynamicObjects.size() - 1; i >= 0; i--) {
    		UniqueDynamicObject obj = uniqueDynamicObjects.get(i);
    		if (obj instanceof TephraProjectile && !((TephraProjectile) obj)._alive) {
    			uniqueDynamicObjects.remove(i);
    		} else if (obj instanceof TreeProjectile && !((TreeProjectile) obj)._alive) {
    			uniqueDynamicObjects.remove(i);
    		}
    	}
    	for ( int i = 0 ; i < humains.size() ; i++ ){
			if(humains.get(i)._alive == false) {
				this.uniqueDynamicObjects.remove((UniqueDynamicObject)this.humains.get(i));
				this.humains.remove(this.humains.get(i));
				this.humains.remove(i);
				nbhumains--;
			}
    	}
    	for ( int i = 0 ; i < loups.size() ; i++ ){
			if(loups.get(i)._alive == false) {
				this.uniqueDynamicObjects.remove((UniqueDynamicObject)this.loups.get(i));
				this.agents.remove(this.loups.get(i));
				this.loups.remove(i);
				nbloups--;
			}
    	}
    	for ( int i = 0 ; i < moutons.size() ; i++ ){
			if(moutons.get(i)._alive == false) {
				this.uniqueDynamicObjects.remove((UniqueDynamicObject)this.moutons.get(i));
				this.agents.remove(this.moutons.get(i));
				this.moutons.remove(i);
				nbmoutons--;
			}
    	}
    	int w = getWidth();
    	int h = getHeight();
    	for ( int i = 0 ; i < this.uniqueDynamicObjects.size() ; i++ )
    	{
    		UniqueDynamicObject obj = this.uniqueDynamicObjects.get(i);
    		int oldX = obj.x, oldY = obj.y;
    		obj.step();
    		// Tracking du dernier déplacement (tore-aware) pour orienter le
    		// triangle d'orientation au-dessus de l'agent. On wrap les deltas
    		// > moitié-monde pour gérer le passage de bord de tore.
    		if (obj instanceof Agent) {
    			int dx = obj.x - oldX;
    			int dy = obj.y - oldY;
    			if (dx >  w/2) dx -= w;
    			if (dx < -w/2) dx += w;
    			if (dy >  h/2) dy -= h;
    			if (dy < -h/2) dy += h;
    			((Agent) obj).setLastMove(dx, dy);
    		}
    	}

    }
    
    
	public double distance( int ib,int jb,int ia,int ja){
		/*
		int ib1=ib-_dx,jb1=jb-_dy;int ib2=ib,jb2=jb-_dy;int ib3=ib+_dx,jb3=jb-_dy;
		int ib4=ib-_dx,jb4=jb;							int ib6=ib+_dx,jb6=jb;
		int ib7=ib-_dx,jb7=jb+_dy;int ib8=ib,jb8=jb+_dy;int ib9=ib+_dx,jb6=jb+_dy;
		*/
		double tmp=Double.MAX_VALUE;
		for(int i=-1;i<2;i++){
			ib=ib+i*dxCA;
			for(int j=-1;j<2;j++){
				jb=jb+j*dyCA;
				tmp=Math.min(tmp,Math.abs(Math.sqrt((jb-ja)*(jb-ja)+(ib-ia)*(ib-ia))));
				jb=jb-j*dyCA;
			}
			ib=ib-i*dxCA;
		}
		return tmp;
	}
	
	
	/*
	public boolean stepZ100(int x,int y,double haut, double bas){
		float stepZ=(float)((this.getMaxEverHeight()-this.getMinEverHeight())/100);
		return this.getCellHeight(x, y)<=this.getMinEverHeight()+stepZ*haut && this.getCellHeight(x, y)>=this.getMinEverHeight()+stepZ*bas;
	}*/
	
	// used by the visualization code to call specific object display.
    public int getForestCAValue(int x, int y)
    {
    	return forestCA.getCellState(x%dxCA,y%dyCA);
    }

    /** Progression de croissance (0..1) de l'arbre en (x,y), pour le rendu. */
    public double getTreeGrowth(int x, int y)
    {
    	return forestCA.getGrowth(x%dxCA, y%dyCA);
    }

    /** Le marqueur de forêt doit-il être dessiné en (x,y) ? (case non vide) */
    public boolean forestHasMarker(int x, int y)
    {
    	return forestCA.hasMarker(x%dxCA, y%dyCA);
    }

    /** Couleur du marqueur de forêt selon l'état de la cellule, dans out[0..2]. */
    public void getForestStateColor(int x, int y, float[] out)
    {
    	forestCA.getStateColor(x%dxCA, y%dyCA, out);
    }

    /** Fertilité (0..1) de la cellule (x,y), pour dériver la taille adulte au rendu. */
    public double getTreeFertility(int x, int y)
    {
    	return forestCA.fertility(x%dxCA, y%dyCA);
    }

    /**
     * Couleur de terrain d'un SOMMET de la grille (coin partagé par jusqu'à 4
     * cases) = moyenne pondérée des couleurs de terrain des cases adjacentes.
     *
     * PAS de fondue entre l'eau et le sable (irréaliste — du bleu bavait sur la
     * plage) : si le sommet touche au moins une case TERRE (h ≥ 0), il n'est
     * coloré que par les cases terre (sable légèrement dominant pour que la
     * plage « monte » un peu sur l'herbe). Un sommet entièrement sous l'eau
     * prend la couleur de fond marin. La transition eau↔terre est donc nette
     * (le rendu de l'eau est géré séparément par le plan d'eau translucide) ;
     * la fondue ne joue qu'entre sable et vert/montagne. Renvoie float[3] RGB.
     */
    public float[] getVertexTerrainColor(int vx, int vy)
    {
    	int[][] cells = { {vx-1,vy-1}, {vx,vy-1}, {vx-1,vy}, {vx,vy} };
    	double niv = getMaxEverHeight() / 10.0; // = nivPlage de colorInit
    	float r = 0f, g = 0f, b = 0f, wsum = 0f;
    	float rw = 0f, gw = 0f, bw = 0f, wsumW = 0f; // accumulateur eau (fallback)
    	for (int[] c : cells) {
    		int cxm = ((c[0] % dxCA) + dxCA) % dxCA;
    		int cym = ((c[1] % dyCA) + dyCA) % dyCA;
    		double h = getCellHeight(cxm, cym);
    		float[] col = getCellColorValue(cxm, cym); // RGBA terrain (forêt ne peint plus)
    		if (h >= 0) {                              // TERRE : sable (1.3) > vert (1.0)
    			float w = (h < niv) ? 1.3f : 1.0f;
    			r += col[0]*w; g += col[1]*w; b += col[2]*w; wsum += w;
    		} else {                                   // EAU : accumulée à part
    			rw += col[0]; gw += col[1]; bw += col[2]; wsumW += 1f;
    		}
    	}
    	if (wsum > 0f) return new float[] { r/wsum, g/wsum, b/wsum };      // touche de la terre → terre pure
    	return new float[] { rw/wsumW, gw/wsumW, bw/wsumW };               // tout eau → fond marin
    }

    /**
     * Teinte le SOL directement (couleur de sommet du terrain) selon la forêt,
     * au lieu d'un disque flottant (qui s'enterrait dans le relief en pente).
     * Pour chaque case-forêt adjacente au sommet, mélange sa couleur d'état
     * (brun-terre vivant / orange feu / noir brûlé / cendres) dans {@code col}.
     * La force suit (a) la couverture (nb de cases-forêt parmi les 4 → bord
     * doux, plus rond qu'un carré) et (b) la croissance pour un arbre vivant
     * (jeune pousse → teinte faible). Épouse le relief, toujours visible, et ne
     * coûte aucune géométrie. Modifie {@code col} en place.
     */
    public void applyForestTint(int vx, int vy, float[] col)
    {
    	final float GROUND_TINT_MAX = 0.82f; // force max (couverture pleine)
    	final float SAPLING_TINT    = 0.20f; // teinte minimale d'une jeune pousse
    	int[][] cells = { {vx-1,vy-1}, {vx,vy-1}, {vx-1,vy}, {vx,vy} };
    	float tr = 0f, tg = 0f, tb = 0f, cover = 0f;
    	float[] sc = new float[3];
    	for (int[] c : cells) {
    		int cxm = ((c[0] % dxCA) + dxCA) % dxCA;
    		int cym = ((c[1] % dyCA) + dyCA) % dyCA;
    		if (!forestCA.hasMarker(cxm, cym)) continue;
    		forestCA.getStateColor(cxm, cym, sc);
    		int st = forestCA.getCellState(cxm, cym);
    		float wgt = (st == 1)
    			? (float)(SAPLING_TINT + (1.0 - SAPLING_TINT) * forestCA.getGrowth(cxm, cym))
    			: 1f;
    		tr += sc[0]*wgt; tg += sc[1]*wgt; tb += sc[2]*wgt; cover += wgt;
    	}
    	if (cover <= 0f) return;
    	float ar = tr/cover, ag = tg/cover, ab = tb/cover;        // couleur d'état moyenne
    	float strength = Math.min(1f, cover/4f) * GROUND_TINT_MAX; // couverture 0..1 × force max
    	col[0] = col[0]*(1f-strength) + ar*strength;
    	col[1] = col[1]*(1f-strength) + ag*strength;
    	col[2] = col[2]*(1f-strength) + ab*strength;
    }

    public int getGrassCAValue(int x, int y)
    {
    	return grassCA.getCellState(x%dxCA,y%dyCA);
    }
    
    public int getLavaCAValue(int x, int y)
    {
    	// Système Layer : si la couche du sommet est LAVA, on retourne son
    	// state (= cellState 1..solidifyEnd). Sinon 0. Lu par ForestCA/GrassCA
    	// pour détecter la présence de lave et propager / éteindre le feu.
    	objects.Layer top = topLayer(x, y);
    	return (top != null && top.material == objects.Material.LAVA) ? top.state : 0;
    }
    
    public int getStoneCAValue(int x, int y)
    {
    	// Système Layer : retourne le nombre de couches « minérales solides »
    	// dans la stack — STONE, OBSIDIAN, BASALT, GRANITE. Toutes sont
    	// issues de lave solidifiée et partagent les mêmes implications
    	// gameplay : pas de germination directe dessus, agents marchent au
    	// sommet, etc. Si demain on ajoute un matériau qui devrait être
    	// exclu (ex. SAND meuble), ajouter une exception ici.
    	int count = 0;
    	for (objects.Layer layer : getStack(x, y)) {
    		objects.Material m = layer.material;
    		if (m == objects.Material.STONE
    				|| m == objects.Material.OBSIDIAN
    				|| m == objects.Material.BASALT
    				|| m == objects.Material.GRANITE) count++;
    	}
    	return count;
    }
    
    // used by the visualization code to call specific object display.
    public void setForestCAValue(int x, int y, int state)
    {
    	forestCA.setCellState( x%dxCA, y%dyCA, state);
    }
    
    public void setGrassCAValue(int x, int y, int state)
    {
    	grassCA.setCellState( x%dxCA, y%dyCA, state);
    }
    
    public int getNbhumains() {
		return nbhumains;
	}

	public void setNbhumains(int nbhumains) {
		this.nbhumains = nbhumains;
	}
    
    public int getNbloups() {
		return nbloups;
	}

	public void setNbloups(int nbloups) {
		this.nbloups = nbloups;
	}

	public int getNbmoutons() {
		return nbmoutons;
	}

	public void setNbmoutons(int nbmoutons) {
		this.nbmoutons = nbmoutons;
	}

	public int getBergerie() {
		return bergerie;
	}

	public void setBergerie(int bergerie) {
		this.bergerie = bergerie;
	}

	public int getWolfHome() {
		return wolfHome;
	}

	public void setWolfHome(int wolfHome) {
		this.wolfHome = wolfHome;
	}

	public void displayObjectTree(World _myWorld, GL2 gl, int cellState, int x,
			int y, double height, float offset,
			float stepX, float stepY, float lenX, float lenY,
			float normalizeHeight, int movingX, int movingY) 
	{
		switch ( cellState )
		{
		case 1: // trees: green, fire (2,3,4), burnt (5)
		case 2:
		case 3:
		case 4:
		case 5:
			Tree.displayObjectAt(_myWorld,gl,cellState, x, y, height, offset, stepX, stepY, lenX, lenY, normalizeHeight,movingX,movingY);
		default:
			// nothing to display at this location.
		}
	}
	
	public void displayObjectGrass(World _myWorld, GL2 gl, int cellState, int x,
			int y, double height, float offset,
			float stepX, float stepY, float lenX, float lenY,
			float normalizeHeight, int movingX, int movingY) 
	{
		switch ( cellState )
		{
		case 1: // grass: green, fire, burnt
		case 2:
		case 3:
		case 4:
			Grass.displayObjectAt(_myWorld,gl,cellState, x, y, height, offset, stepX, stepY, lenX, lenY, normalizeHeight,movingX,movingY);
		default:
			// nothing to display at this location.
		}
	}
	
	public void displayObjectLave(World _myWorld, GL2 gl, int cellState, int x,
			int y, double height, float offset,
			float stepX, float stepY, float lenX, float lenY,
			float normalizeHeight,int movingX, int movingY) 
	{
		if(cellState > 0){
			LavaBlock.displayObjectAt(_myWorld,gl,cellState, x, y, height, offset, stepX, stepY, lenX, lenY, normalizeHeight,movingX,movingY);
		}
	}
	
	public void displayObjectStone(World _myWorld, GL2 gl, int cellState, int x,
			int y, double height, float offset,
			float stepX, float stepY, float lenX, float lenY,
			float normalizeHeight,int movingX, int movingY) 
	{
		if(cellState > 0){
			StoneBlock.displayObjectAt(_myWorld,gl,cellState, x, y, height, offset, stepX, stepY, lenX, lenY, normalizeHeight,movingX,movingY);
		}
	}

	//public void displayObject(World _myWorld, GL2 gl, float offset,float stepX, float stepY, float lenX, float lenY, float heightFactor, double heightBooster) { ... } 
    
   
}
