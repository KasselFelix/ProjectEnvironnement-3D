package agents;


import javax.media.opengl.GL2;

import loader.GLBModel;

import worlds.World;
import agents.ai.*;
import static agents.ai.AgentState.opposite;

public class Mouton extends Agent {

	// ===== Modèle GLB chibi sheep (modddif.com, 10k faces, texture diffuse intégrée) =====
	//
	// Convention d'export Blender (à appliquer à TOUS les modèles d'agents) :
	//   - Z up (sabots Z bas, dos Z haut)
	//   - −Y = face / forward (museau pointe vers Y négatif)
	//   - Origine au centre des sabots (le mesh repose sur Z=0)
	//   - Bbox normalisé ~1 unité
	// Sous ces conditions, le code Java ci-dessous ne fait QUE translate +
	// rotate Z (selon la direction de marche) + scale. Aucun axis-swap.

	private static GLBModel moutonModel;

	public static void initModel(GL2 gl) {
		try {
			moutonModel = new GLBModel("models/Mouton_chibi.glb", false, gl, 0.32f);
		} catch (Exception e) {
			System.out.println("[Mouton] erreur chargement GLB: " + e.getMessage());
			e.printStackTrace();
		}
	}

	/**
	 * Rend un mouton via la display list GLB. À appeler HORS d'un glBegin.
	 *
	 * <p>Hypothèses sur le GLB (à garantir au moment de l'export Blender) :
	 * Z up, −Y forward, origine au centre des sabots, bbox normalisé ~1 unité.
	 * Tant que ce n'est pas encore le cas pour ce GLB précis, la rotation Z
	 * peut placer la face dans la mauvaise direction — c'est à corriger côté
	 * Blender, pas côté Java.
	 */
	public static void displayMoutonAt(Mouton m, World myWorld, GL2 gl,
			int offsetCA_x, int offsetCA_y,
			float offset, float stepX, float stepY,
			float lenX, float lenY, float normalizeHeight, float dayFactor) {
		if (moutonModel == null) return;
		if (!m._alive) return;
		if (m.hiddenFP) return;   // 1ère personne : agent piloté non dessiné

		int x2 = (m.x - (offsetCA_x % myWorld.getWidth()));
		if (x2 < 0) x2 += myWorld.getWidth();
		int y2 = (m.y - (offsetCA_y % myWorld.getHeight()));
		if (y2 < 0) y2 += myWorld.getHeight();

		float altitude = m.computeAgentAltitude(myWorld, m.x, m.y, normalizeHeight);
		float px = offset + x2 * stepX;
		float py = offset + y2 * stepY;
		float scale = Math.abs(lenX) * 1.5f;

		// Rotation Z pour aligner −Y modèle (forward) vers (udx, udy) monde.
		// Forward modèle = (0, −1), cible monde = (udx, udy).
		// angle = atan2(udx, −udy) — résolu pour la rotation autour de Z+.
		double[] u = m.getLastUnit();
		float angleZdeg = (float) Math.toDegrees(Math.atan2(u[0], -u[1]));

		gl.glPushMatrix();
		gl.glTranslatef(px, py, altitude);
		gl.glRotatef(angleZdeg, 0f, 0f, 1f);
		gl.glScalef(scale, scale, scale);
		moutonModel.opengldraw(gl, dayFactor);
		gl.glPopMatrix();
	}

	public boolean _alive;

	public double PreproD=0;//0.06  reproduction des moutons
	public double Prepro=PreproD;

	// Reproduction conditionnée à l'énergie (cf. SimulationConfig) : seuil de
	// santé requis + énergie investie dans l'agneau (fractions de energieMAX).
	// Proie prolifique → seuil + coût BAS (vs loup) pour se reproduire sur un
	// budget d'herbe modeste et surpasser le prédateur (Lotka-Volterra).
	public double reproEnergyThreshold = 0.40;
	public double reproOffspringRatio  = 0.25;

	public double energieMAX=400;//400 energie de base du mouton
	public double energie=energieMAX;//400 energie actuelle du mouton
	public int vision=10; // portee de vision du mouton

	// block/s MAX:28
	public double vcourse=9;//14 test algo de fuite
	public double vmarche=2;// vitesse de marche du mouton
	public double vitesse=vmarche;// vitesse actuelle du mouton

	// Fraction de la vitesse conservée dans l'eau. Le mouton nage très mal
	// (laine gorgée, panique) ⇒ swimFactor bas. Appliqué dans postMove, tous
	// états confondus (cf. SimulationConfig).
	public double swimFactor = 0.25;

	// Mort par vieillesse — surchargé par SimulationConfig au spawn et en live.
	// Valeur ≤ 0 = pas de mort par âge.
	public double maxAgeDays = -1;

	public int earthSearch=0;// 1 si est dans l'eau et recherche la terre ferme

	public int fuite=0;
	public int m=0;
	public int lastX;
	public int lastY;

	public Mouton( int __x, int __y, World __World )
	{
		super(__x,__y,__World);
		_alive = true;

		_redValue = 0.f;
		_greenValue = 0.f;
		_blueValue = 1.f;

	}

	/** Accesseur public pour l'UI (les champs restent package-private). */
	public double getEnergie() { return energie; }
	public double getEnergieMax() { return energieMAX; }
	public boolean isAlive() { return _alive; }

	@Override public String getTypeName() { return "Mouton"; }

	@Override public String getCurrentBehavior() {
		if (playerControlled) return "Piloté";
		if (_fireState == 1) return "Fuit feu";
		if (m == 1)          return "Broute";
		switch (currentState) {
			case FLEE_PREDATOR: return "Fuit loup";
			case SEEK_LAND:     return "Cherche terre";
			case SEEK_FOOD:     return "Cherche herbe";
			default:            return "Errance";
		}
	}

	// ===== Hooks du Template Method (Agent.step) =====

	@Override
	protected boolean preStepAbort() {
		// Mort par vieillesse — check à chaque tick, indépendamment du gating
		// `vitesse`. Si maxAgeDays ≤ 0, l'agent est immortel par vieillesse.
		if (maxAgeDays > 0 && getAgeDays() > maxAgeDays) { _alive = false; return true; }
		return !_alive;
	}

	@Override
	protected boolean isMyTurn() {
		return world.getIteration() % (int)((1.0/vitesse)*28) == 0;
	}

	@Override
	protected void resetTickFlags() {
		fuite=0;
		m=0;
		if(world.getCellHeight(x, y)>=0)earthSearch=0;
		if(world.getCellHeight(x, y)<0){this._fireState=0;}

		lastX=x;
		lastY=y;
	}

	@Override
	protected java.util.List<? extends objects.UniqueDynamicObject> predators() {
		return world.loups;
	}

	@Override
	protected boolean canMove() { return energie > 2; }

	@Override
	protected void applyControlSpeed() { vitesse = vcourse; }

	@Override
	protected void postMove(Percept p) {
		//Broute
		if(energie<(energieMAX*0.75) &&fuite==0) {
			if(world.getGrassCAValue( x, y)==1){
				world.setGrassCAValue( x, y, 0);
				energie+=energieMAX/100;
				m=1;
				//System.out.println("broute");
			}
		}


		int xn=x;
		int yn=(y-1+this.world.getHeight())%this.world.getHeight();
		int xe=(x+1+this.world.getWidth())%this.world.getWidth();
		int ye=y;
		int xs=x;
		int ys=(y+1+this.world.getHeight())%this.world.getHeight();
		int xo=(x-1+this.world.getWidth())%this.world.getWidth();
		int yo=y;

		// si rencontre feu
		if(cellularautomata.ecosystem.ForestCA.isTreeOnFire(world.getForestCAValue(xn,yn))
				|| cellularautomata.ecosystem.ForestCA.isTreeOnFire(world.getForestCAValue(xe,ye))
				|| cellularautomata.ecosystem.ForestCA.isTreeOnFire(world.getForestCAValue(xs,ys))
				|| cellularautomata.ecosystem.ForestCA.isTreeOnFire(world.getForestCAValue(xo,yo))){
			_fireState=1;
		}

		// Contagion du feu entre agents adjacents (préservé du legacy
		// INTERACTION INTERAGENTS) : un mouton prend feu au contact d'un
		// agent voisin déjà en feu. Distinct du feu de forêt ci-dessus.
		for (Agent a : this.world.agents) {
			if ((xn == a.x && yn == a.y && a._fireState == 1)
			 || (xe == a.x && ye == a.y && a._fireState == 1)
			 || (xs == a.x && ys == a.y && a._fireState == 1)
			 || (xo == a.x && yo == a.y && a._fireState == 1)) {
				this._fireState = 1;
				break;
			}
		}

		//si renconre lave
		if( world.getLavaCAValue(xn,yn)>0
				|| world.getLavaCAValue(xe,ye)>0
				|| world.getLavaCAValue(xs,ys)>0
				|| world.getLavaCAValue(xo,yo)>0){
			vitesse=vcourse;
		}


		//mise a jour energie
		if(energie<=0){
			_alive = false;
		}else{
			if(world.getCellHeight(x, y)<0)energie-=2;
			if( world.getCellHeight(lastX, lastY) > world.getCellHeight(x, y)){
				energie--;
			}
			energie--;
		}
		if(energie<10 && vitesse>=vcourse){
			vitesse=vcourse/2;
		}
		if(energie<3){
			vitesse=vmarche;
		}

		//vitesse reduite en hauteur
		if(world.getCellHeight(x,y)>0)vitesse=vitesse-((vitesse*0.75)*world.getCellHeight(x,y));

		// vitesse réduite dans l'eau (nage) — pénalité uniforme, tous états
		// confondus. Le mouton nage mal (swimFactor bas) ⇒ fuir dans l'eau
		// n'est plus une échappatoire : le loup l'y rattrape.
		if(world.getCellHeight(x,y)<0)vitesse=vitesse*swimFactor;

		//si dans la lave
		if(_world.getLavaCAValue(x,y)>0) {
			_alive=false;
		}

		//reproduction — conditionnée à l'énergie : le mouton doit être en bonne
		// santé (≥ seuil) et INVESTIT une part de son énergie dans l'agneau
		// (énergie conservée, pas créée). L'agneau naît à la position du parent.
		if(energie >= energieMAX * reproEnergyThreshold && Math.random()<Prepro) {
			double invest = energieMAX * reproOffspringRatio;
			Mouton prea=new Mouton(this.x, this.y, this._world);
			prea.energie = invest;       // l'agneau hérite de l'énergie investie
			energie -= invest;           // le parent paie ce coût → retombe sous le seuil
			this.world.uniqueDynamicObjects.add(prea);
			this.world.agents.add(prea);
			this.world.moutons.add(prea);
			this.world.setNbmoutons(world.getNbmoutons()+1);
		}

		// limitation reproduction
		if(_world.getNbmoutons()<10){Prepro=PreproD*4;}
		else if(_world.getNbmoutons()> 20){Prepro=0;}
		else Prepro = PreproD;
	}

	@Override
	protected void postTick() {
		if ( world.getIteration() % 20 == 0 )if(_fireState==1)energie-=energieMAX/10;
	}


	/** Décision pure (priorité = ordre des gardes). La recherche de nourriture
	 *  vient APRÈS la survie (feu, fuite, sortie de l'eau) : un mouton affamé
	 *  ne cherche de l'herbe que s'il n'est ni en feu, ni poursuivi, ni dans
	 *  l'eau. Affamé = énergie sous 50% du max ET de l'herbe en vue. */
	public AgentState decideState(Percept p) {
		if (isOnFire())          return AgentState.ON_FIRE;
		if (p.predatorVisible()) return AgentState.FLEE_PREDATOR;
		if (p.inWater)           return AgentState.SEEK_LAND;
		if (energie < energieMAX * 0.5 && p.grassVisible()) return AgentState.SEEK_FOOD;
		return AgentState.WANDER;
	}

	/** Traduit l'état en intention (orientation + vitesse), renvoie les contraintes,
	 *  et met à jour les flags legacy lus par les blocs conservés (Broute, énergie). */
	public MoveConstraints applyState(AgentState s, Percept p) {
		// par défaut, on remet les flags legacy à 0 ; chaque état réactive ce qu'il faut
		fuite = 0; earthSearch = 0;
		switch (s) {
			case ON_FIRE:
				// fuit vers l'eau si vue, sinon continue tout droit (pas de demi-tour)
				if (p.waterDir >= 0) _orient = p.waterDir;
				vitesse = vcourse;
				return MoveConstraints.amphibious();
			case FLEE_PREDATOR:
				// Fuit à l'opposé du prédateur MAIS évite de plonger dans l'eau
				// (mortelle pour le mouton) si une issue terrestre existe.
				_orient = chooseFleeOrient(p);
				fuite = 1;                       // supprime le Broute pendant la fuite
				vitesse = vcourse;               // correctif : toujours vcourse à la fuite
				return MoveConstraints.amphibious();
			case SEEK_LAND:
				// va vers la terre si vue, sinon continue tout droit jusqu'à la percevoir
				if (p.landDir >= 0) _orient = p.landDir;
				earthSearch = 1;
				vitesse = vcourse;   // ralenti dans l'eau par swimFactor (postMove)
				return MoveConstraints.amphibious();
			case SEEK_FOOD:
				// affamé : se dirige vers l'herbe la plus proche en vue (plus
				// d'errance aléatoire). L'herbe est sur terre → landBound. Le
				// broutage effectif reste géré dans postMove quand il l'atteint.
				if (p.grassDir >= 0) _orient = p.grassDir;
				vitesse = vmarche;
				return MoveConstraints.landBound();
			case WANDER:
			default:
				if (Math.random() < 0.2) {
					_orient = (Math.random() > 0.5) ? (_orient + 1) % 4 : (_orient - 1 + 4) % 4;
				}
				vitesse = vmarche;
				return MoveConstraints.landBound();
		}
	}

	/**
	 * Choisit la direction de fuite : idéalement à l'opposé du prédateur, mais
	 * on refuse de plonger dans l'eau (mortelle pour le mouton) tant qu'une
	 * issue terrestre praticable existe. Ordre de préférence : fuir tout droit
	 * (opposé au prédateur), puis les deux directions latérales (perpendiculaires
	 * à l'axe du prédateur). En dernier recours — cerné par l'eau — on fuit
	 * quand même à l'opposé (pas le choix).
	 */
	private int chooseFleeOrient(Percept p) {
		int away = opposite(p.predatorDir);
		if (away < 0) return _orient;                 // pas de prédateur (cas limite)
		int[] candidates = { away, (away + 1) % 4, (away + 3) % 4 };
		for (int o : candidates) {
			// p.cardinalFree[o] = ni forêt ni lave ; + on exige de la terre ferme.
			if (p.cardinalFree[o] && isLandInDir(o)) return o;
		}
		return away;                                  // tout est eau → fuite forcée
	}

	/** true si la case cardinale dans la direction {@code orient} est de la
	 *  terre ferme (altitude ≥ 0), tore-aware. */
	private boolean isLandInDir(int orient) {
		int w = world.getWidth(), h = world.getHeight();
		int tx = ((x + orientDx(orient)) % w + w) % w;
		int ty = ((y + orientDy(orient)) % h + h) % h;
		return world.getCellHeight(tx, ty) >= 0;
	}

	public void displayUniqueObject(World myWorld, GL2 gl, int offsetCA_x, int offsetCA_y, float offset, float stepX, float stepY, float lenX, float lenY, float normalizeHeight)
	{
		if (hiddenFP) return;   // 1ère personne : agent piloté non dessiné

		//gl.glColor3f(0.f+(float)(0.5*Math.random()),0.f+(float)(0.5*Math.random()),0.f+(float)(0.5*Math.random()));

		int x2 = (x-(offsetCA_x%myWorld.getWidth()));
		if ( x2 < 0) x2+=myWorld.getWidth();
		int y2 = (y-(offsetCA_y%myWorld.getHeight()));
		if ( y2 < 0) y2+=myWorld.getHeight();

		// Altitude unifiée (sol + stone + lave + clamp eau) → Agent.computeAgentAltitude.
		float altitude = computeAgentAltitude(myWorld, x, y, normalizeHeight);

		// Quand le modèle OBJ est chargé, on saute le cube (rendu par
		// displayMoutonAt dans la passe 2b, hors glBegin). On garde quand
		// même le triangle d'orientation ci-dessous pour la lisibilité.
		if (moutonModel == null) {
			gl.glColor3f(1.f,1.f,1.f);
			gl.glVertex3f( offset+x2*stepX-lenX, offset+y2*stepY-lenY, altitude);
			gl.glVertex3f( offset+x2*stepX-lenX, offset+y2*stepY-lenY, altitude + 2.f);
			gl.glVertex3f( offset+x2*stepX+lenX, offset+y2*stepY-lenY, altitude + 2.f);
			gl.glVertex3f( offset+x2*stepX+lenX, offset+y2*stepY-lenY, altitude);

			gl.glColor3f(1.f,1.f,1.f);
			gl.glVertex3f( offset+x2*stepX+lenX+1, offset+y2*stepY+lenY, altitude);
			gl.glVertex3f( offset+x2*stepX+lenX+1, offset+y2*stepY+lenY, altitude + 2.f);
			gl.glVertex3f( offset+x2*stepX-lenX-1, offset+y2*stepY+lenY, altitude + 2.f);
			gl.glVertex3f( offset+x2*stepX-lenX-1, offset+y2*stepY+lenY, altitude);

			gl.glColor3f(0.8f,0.8f,0.8f);
			gl.glVertex3f( offset+x2*stepX+lenX, offset+y2*stepY-lenY, altitude);
			gl.glVertex3f( offset+x2*stepX+lenX, offset+y2*stepY-lenY, altitude + 2.f);
			gl.glVertex3f( offset+x2*stepX+lenX, offset+y2*stepY+lenY, altitude + 2.f);
			gl.glVertex3f( offset+x2*stepX+lenX, offset+y2*stepY+lenY, altitude);

			gl.glColor3f(0.8f,0.8f,0.8f);
			gl.glVertex3f( offset+x2*stepX-lenX, offset+y2*stepY+lenY, altitude);
			gl.glVertex3f( offset+x2*stepX-lenX, offset+y2*stepY+lenY, altitude + 2.f);
			gl.glVertex3f( offset+x2*stepX-lenX, offset+y2*stepY-lenY, altitude + 2.f);
			gl.glVertex3f( offset+x2*stepX-lenX, offset+y2*stepY-lenY, altitude);

			gl.glColor3f(0.5f,0.5f,0.5f);
			gl.glVertex3f( offset+x2*stepX-lenX, offset+y2*stepY-lenY, altitude + 2.f);
			gl.glVertex3f( offset+x2*stepX-lenX, offset+y2*stepY+lenY, altitude + 2.f);
			gl.glVertex3f( offset+x2*stepX+lenX, offset+y2*stepY+lenY, altitude + 2.f);
			gl.glVertex3f( offset+x2*stepX+lenX, offset+y2*stepY-lenY, altitude + 2.f);
		}
		
		if (_fireState== 1) {
			_redValue = 1.f;
			_greenValue = 1.f;
			_blueValue = 0.f;
		} else {
			_redValue = 0.f;
			_greenValue = 0.f;
			_blueValue = 1.f;
		}


		// Triangle d'orientation : pointe dans la direction du dernier
		// déplacement effectif (cf. Agent.setLastMove). Bleu pour le mouton ;
		// cyan vif quand l'agent est piloté manuellement (le jaune est réservé
		// au feu ; le cyan reste distinct du bleu mouton via sa composante verte).
		if (playerControlled) gl.glColor3f(0.1f, 0.9f, 1f);
		else gl.glColor3f(_redValue,_greenValue,_blueValue);
		emitOrientationTriangle(gl,
				offset + x2 * stepX, offset + y2 * stepY,
				lenX, lenY,
				altitude + 4.f);
	}
}
