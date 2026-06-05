package agents;

import javax.media.opengl.GL2;

import loader.GLBModel;
import objects.UniqueDynamicObject;

import worlds.World;
import agents.ai.*;

public class Loup extends Agent {

	// ===== Modèle GLB loup (modddif.com, 1k faces, texture diffuse intégrée) =====
	//
	// Même convention d'export que le Mouton (cf. project-agent-model-convention) :
	// Z up, −Y = face/forward, origine au centre des pattes (pieds à Z=0), bbox
	// normalisé. Le code Java ne fait que translate + rotate Z (direction de
	// marche) + scale, aucun axis-swap.
	private static GLBModel loupModel;

	public static void initModel(GL2 gl) {
		try {
			loupModel = new GLBModel("models/Loup_chibi.glb", false, gl, 0.32f);
		} catch (Exception e) {
			System.out.println("[Loup] erreur chargement GLB: " + e.getMessage());
			e.printStackTrace();
		}
	}

	/**
	 * Rend un loup via la display list GLB. À appeler HORS d'un glBegin.
	 * Pattern identique à {@link Mouton#displayMoutonAt}. Scale ×2.0 (prédateur
	 * plus imposant que le mouton à ×1.5).
	 */
	public static void displayLoupAt(Loup l, World myWorld, GL2 gl,
			int offsetCA_x, int offsetCA_y,
			float offset, float stepX, float stepY,
			float lenX, float lenY, float normalizeHeight, float dayFactor) {
		if (loupModel == null) return;
		if (!l._alive) return;
		if (l.hiddenFP) return;   // 1ère personne : agent piloté non dessiné

		int x2 = (l.x - (offsetCA_x % myWorld.getWidth()));
		if (x2 < 0) x2 += myWorld.getWidth();
		int y2 = (l.y - (offsetCA_y % myWorld.getHeight()));
		if (y2 < 0) y2 += myWorld.getHeight();

		float altitude = l.computeAgentAltitude(myWorld, l.x, l.y, normalizeHeight);
		float px = offset + x2 * stepX;
		float py = offset + y2 * stepY;
		float scale = Math.abs(lenX) * 2.0f; // prédateur : plus grand que le mouton

		// Rotation Z : aligne le forward −Y du modèle vers la direction de marche.
		double[] u = l.getLastUnit();
		float angleZdeg = (float) Math.toDegrees(Math.atan2(u[0], -u[1]));

		gl.glPushMatrix();
		gl.glTranslatef(px, py, altitude);
		gl.glRotatef(angleZdeg, 0f, 0f, 1f);
		gl.glScalef(scale, scale, scale);
		loupModel.opengldraw(gl, dayFactor);
		gl.glPopMatrix();
	}

	public boolean _alive;

	public double PreproD = 0;//0.0009; reproduction des loups
	public double Prepro = PreproD;

	// Reproduction conditionnée à l'énergie (cf. SimulationConfig) : seuil de
	// santé requis + énergie investie dans le petit (fractions de energieD).
	public double reproEnergyThreshold = 0.60;
	public double reproOffspringRatio  = 0.45;

	public int energieD = 500;// 20 energie de base du loup
	public int energie = energieD; // energie actuelle du loup

	public int vision = 10; // portee de vision du loup

	// block/s MAX:28
	public double vcourse = 13.5; // vitesse de course du loup
	public double vtrot = 8; // vitesse de trot du loup
	public double vpas = 3; // vitesse de pas du loup
	public double vitesse = vpas;

	// Fraction de la vitesse conservée dans l'eau (le loup nage bien). Appliqué
	// uniformément dans postMove, quel que soit l'état (cf. SimulationConfig).
	public double swimFactor = 0.6;

	// Mort par vieillesse — surchargé par SimulationConfig au spawn et en live.
	// Valeur ≤ 0 = pas de mort par âge.
	public double maxAgeDays = -1;

	public int m = 0;// 1 si a manger ce tour
	public int attaqueNuit = 0;

	/** Seuil de faim (fraction de energieD) : sous ce niveau le loup chasse ET
	 *  consomme. Au-dessus il est repu — la nuit il poursuit quand même pour
	 *  effrayer/disperser le troupeau, mais ne TUE pas (pas de surplus killing :
	 *  il en laisse pour la meute). Partagé par decideState et le bloc prédation. */
	public static final double HUNGER_RATIO = 0.7;

	public int lastX;
	public int lastY;

	public Loup(int __x, int __y, World __world) {
		super(__x, __y, __world);
		_alive = true;

		_redValue = 1.f;
		_greenValue = 0.f;
		_blueValue = 0.f;

	}

	/** Accesseur public pour l'UI (les champs restent package-private). */
	public int getEnergie() { return energie; }
	public int getEnergieMax() { return energieD; }
	public boolean isAlive() { return _alive; }

	@Override public String getTypeName() { return "Loup"; }

	@Override public String getCurrentBehavior() {
		if (_fireState == 1) return "Fuit feu";
		if (m == 1)          return "Mange";
		if (playerControlled) return "Piloté";
		switch (currentState) {
			case HUNT:      return "Chasse";
			case SEARCH:    return "Cherche proie";
			case SEEK_LAND: return "Cherche terre";
			default:        return attaqueNuit == 1 ? "Rode (nuit)" : "Errance";
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
	protected void updateDayNight() {
		if ((world.getIteration() % world.getDureeJour() >= world.getTransitionJour() && world.getJour() == 0)
				|| (world.getIteration() % world.getDureeJour() >= world.getTransitionJour()
				&& (world.getBefore() == 1 && world.getJour() == 0)))
					attaqueNuit = 1;

		if (world.getJour() == 1)attaqueNuit = 0;
	}

	@Override
	protected boolean isMyTurn() {
		return world.getIteration() % (int) ((1.0 / vitesse) * 28) == 0;
	}

	@Override
	protected void resetTickFlags() {
		m = 0;
		if(world.getCellHeight(x, y)<0){this._fireState=0;}

		lastX=x;
		lastY=y;
	}

	@Override
	protected java.util.List<? extends objects.UniqueDynamicObject> prey() {
		return world.moutons;
	}

	@Override
	protected boolean canMove() { return energie > 2; }

	@Override
	protected void applyControlSpeed() { vitesse = vcourse; }

	@Override
	protected void postMove(Percept p) {
		int xn=x;
		int yn=(y-1+this.world.getHeight())%this.world.getHeight();
		int xe=(x+1+this.world.getWidth())%this.world.getWidth();
		int ye=y;
		int xs=x;
		int ys=(y+1+this.world.getHeight())%this.world.getHeight();
		int xo=(x-1+this.world.getWidth())%this.world.getWidth();
		int yo=y;

		// mange
		// Consommation gâtée par la FAIM (pas par 90% comme avant) : un loup repu
		// (≥ HUNGER_RATIO) ne tue pas, même s'il rattrape un mouton la nuit — il
		// n'a fait que l'effrayer. Évite qu'un seul loup décime tout le troupeau.
		if (energie < energieD * HUNGER_RATIO) {
			for (Mouton ag : this.world.moutons) {
				UniqueDynamicObject pag = (UniqueDynamicObject) ag;
				if (pag.x == x && pag.y == y) {
					ag._alive = false;
					// C3 (analyse ALife) : gain énergétique plafonné à energieD/2 au lieu
					// d'une restauration totale à energieD. Force le loup à chasser
					// régulièrement plutôt que de "faire le plein" en un seul kill.
					energie = Math.min(energieD, energie + energieD / 2);
					m = 1;
					vitesse = vpas;
					if (attaqueNuit == 1)attaqueNuit = 0;
					// System.out.println("devore");
					break;
				}
			}
		}


		// si rencontre feu (sub-states 2, 3 ou 4)
		if (cellularautomata.ecosystem.ForestCA.isTreeOnFire(world.getForestCAValue(xn, yn))
				|| cellularautomata.ecosystem.ForestCA.isTreeOnFire(world.getForestCAValue(xe, ye))
				|| cellularautomata.ecosystem.ForestCA.isTreeOnFire(world.getForestCAValue(xs, ys))
				|| cellularautomata.ecosystem.ForestCA.isTreeOnFire(world.getForestCAValue(xo, yo))) {
			_fireState= 1;
		}

		// si renconre lave
		if (world.getLavaCAValue(xn, yn) >0
				|| world.getLavaCAValue(xe, ye) >0
				|| world.getLavaCAValue(xs, ys) >0
				|| world.getLavaCAValue(xo, yo) >0) {
			vitesse = vcourse;
		}

		// mise a jour energie
		if (energie <= 0) {
			_alive = false;
		} else {
			if (world.getCellHeight(x, y) < 0)energie-=2;
			if( world.getCellHeight(lastX, lastY) > world.getCellHeight(x, y)){
				energie--;
			}
			energie--;
		}
		if (energie < 10 && vitesse >= vcourse) {
			vitesse = vcourse / 2;
		}
		if (energie < 5) {
			vitesse = vpas;
		}

		//vitesse reduite en hauteur
		if(world.getCellHeight(x,y)>0)vitesse=vitesse-((vitesse*0.75)*world.getCellHeight(x,y));

		// vitesse réduite dans l'eau (nage) — pénalité uniforme, tous états
		// confondus. Le loup nage bien (swimFactor élevé) ⇒ il reste plus
		// rapide que le mouton dans l'eau comme sur terre.
		if(world.getCellHeight(x,y)<0)vitesse=vitesse*swimFactor;


		// si dans la lave
		if (_world.getLavaCAValue(x, y) > 0) {
			_alive = false;
		}


		// reproduction — conditionnée à l'énergie : le loup doit être en bonne
		// santé (≥ seuil) et INVESTIT une part de son énergie dans le petit
		// (énergie conservée, pas créée). Le petit naît à la position du parent.
		if (energie >= energieD * reproEnergyThreshold && Math.random() < Prepro) {
			double invest = energieD * reproOffspringRatio;
			Loup prea = new Loup(this.x, this.y, this._world);
			prea.energie = (int) invest;     // le petit hérite de l'énergie investie
			energie -= (int) invest;         // le parent paie ce coût → retombe sous le seuil
			this.world.uniqueDynamicObjects.add(prea);
			this.world.agents.add(prea);
			this.world.loups.add(prea);
			this.world.setNbloups(world.getNbloups() + 1);
		}


		// limtation reproduction
		if (this.world.getNbloups() < 10) {
			Prepro = PreproD * 2;
		} else if (this.world.getNbloups() > 20) {
			Prepro = 0;
		} else
			Prepro = PreproD;
	}

	@Override
	protected void postTick() {
		if ( world.getIteration() % 20 == 0 )if(_fireState==1)energie-=energieD/10;
	}

	public AgentState decideState(Percept p) {
		boolean enChasse = energie < energieD * HUNGER_RATIO || attaqueNuit == 1;
		if (isOnFire())                       return AgentState.ON_FIRE;
		if (enChasse && p.preyVisible())      return AgentState.HUNT;
		if (p.inWater)                        return AgentState.SEEK_LAND;
		if (enChasse)                         return AgentState.SEARCH;   // balayage spirale
		return AgentState.WANDER;                                          // flânerie économe
	}

	public MoveConstraints applyState(AgentState s, Percept p) {
		switch (s) {
			case ON_FIRE:
				// fuit vers l'eau si vue, sinon continue tout droit (pas de demi-tour)
				if (p.waterDir >= 0) _orient = p.waterDir;
				vitesse = vcourse;
				return MoveConstraints.amphibious();
			case HUNT:
				_orient = p.preyDir;
				// Affamé (< HUNGER_RATIO) → SPRINT (vcourse) pour rattraper et tuer.
				// Repu (chasse seulement parce que c'est la nuit, attaqueNuit) →
				// simple TROT (vtrot) : il effraie/disperse le troupeau sans se
				// vider en énergie. Comme vtrot(8) < vcourse mouton(9), le loup
				// repu ne rattrape pas — il ne fait qu'effrayer, ce qui est voulu.
				vitesse = (energie < energieD * HUNGER_RATIO) ? vcourse : vtrot;
				return MoveConstraints.amphibious();   // ralenti dans l'eau par swimFactor (postMove)
			case SEEK_LAND:
				// va vers la terre si vue, sinon continue tout droit jusqu'à la percevoir
				if (p.landDir >= 0) _orient = p.landDir;
				vitesse = vcourse;   // ralenti dans l'eau par swimFactor (postMove)
				return MoveConstraints.amphibious();
			case SEARCH:
				spiralSearch();
				return MoveConstraints.landBound();
			case WANDER:
			default:
				lazyWander();
				return MoveConstraints.landBound();
		}
	}

	/**
	 * Recherche active de proie : balayage en spirale (à {@code vtrot}), efficace
	 * pour couvrir du terrain. Déclenchée quand le loup est affamé ou chasse de
	 * nuit sans proie en vue. Mécanique de spirale portée par {@code mem}.
	 */
	private void spiralSearch() {
		if (mem.spiralStep == mem.spiralPeriod) {
			_orient = (_orient + 1) % 4;
			mem.spiralPeriod += vision / 2;
			mem.spiralStep = 0;
		} else {
			mem.spiralStep++;
		}
		vitesse = vtrot;
	}

	/**
	 * Flânerie économe (à {@code vpas}) quand le loup est repu : ~20% du temps il
	 * tourne légèrement et marque une pause (ne se déplace pas ce tick), sinon il
	 * continue tout droit. Préserve le comportement legacy « 2.2 » et limite la
	 * dépense d'énergie au repos. Réinitialise la spirale pour que la prochaine
	 * recherche reparte d'une spirale serrée.
	 */
	private void lazyWander() {
		mem.spiralStep = 0;
		mem.spiralPeriod = 1;
		if (Math.random() < 0.2) {
			_orient = (Math.random() > 0.5) ? (_orient + 1) % 4 : (_orient - 1 + 4) % 4;
			wantsToMove = false;   // pause (équivalent de l'ancien imobil=1)
			vitesse = 1;
		} else {
			vitesse = vpas;
		}
	}

	public void displayUniqueObject(World myWorld, GL2 gl, int offsetCA_x,
			int offsetCA_y, float offset, float stepX, float stepY, float lenX,
			float lenY, float normalizeHeight) {
		if (hiddenFP) return;   // 1ère personne : agent piloté non dessiné

		// gl.glColor3f(0.f+(float)(0.5*Math.random()),0.f+(float)(0.5*Math.random()),0.f+(float)(0.5*Math.random()));

		int x2 = (x - (offsetCA_x % myWorld.getWidth()));
		if (x2 < 0)
			x2 += myWorld.getWidth();
		int y2 = (y - (offsetCA_y % myWorld.getHeight()));
		if (y2 < 0)
			y2 += myWorld.getHeight();

		// Altitude unifiée (sol + stone + lave + clamp eau) → Agent.computeAgentAltitude.
		float altitude = computeAgentAltitude(myWorld, x, y, normalizeHeight);

		// Quand le modèle GLB est chargé, on saute le cube (rendu par displayLoupAt
		// dans la passe 2d, hors glBegin). On garde le triangle d'orientation.
		if (loupModel == null) {
		gl.glColor3f(0.f, 0.f, 0.f);
		gl.glVertex3f(offset + x2 * stepX - lenX, offset + y2 * stepY - lenY,altitude);
		gl.glVertex3f(offset + x2 * stepX - lenX, offset + y2 * stepY - lenY,altitude + 2.f);
		gl.glVertex3f(offset + x2 * stepX + lenX, offset + y2 * stepY - lenY,altitude + 2.f);
		gl.glVertex3f(offset + x2 * stepX + lenX, offset + y2 * stepY - lenY,altitude);

		gl.glColor3f(0.f, 0.f, 0.f);
		gl.glVertex3f(offset + x2 * stepX + lenX, offset + y2 * stepY + lenY,altitude);
		gl.glVertex3f(offset + x2 * stepX + lenX, offset + y2 * stepY + lenY,altitude + 2.f);
		gl.glVertex3f(offset + x2 * stepX - lenX, offset + y2 * stepY + lenY,altitude + 2.f);
		gl.glVertex3f(offset + x2 * stepX - lenX, offset + y2 * stepY + lenY,altitude);

		gl.glColor3f(0.3f, 0.3f, 0.3f);
		gl.glVertex3f(offset + x2 * stepX + lenX, offset + y2 * stepY - lenY,altitude);
		gl.glVertex3f(offset + x2 * stepX + lenX, offset + y2 * stepY - lenY,altitude + 2.f);
		gl.glVertex3f(offset + x2 * stepX + lenX, offset + y2 * stepY + lenY,altitude + 2.f);
		gl.glVertex3f(offset + x2 * stepX + lenX, offset + y2 * stepY + lenY,altitude);

		gl.glColor3f(0.3f, 0.3f, 0.3f);
		gl.glVertex3f(offset + x2 * stepX - lenX, offset + y2 * stepY + lenY,altitude);
		gl.glVertex3f(offset + x2 * stepX - lenX, offset + y2 * stepY + lenY,altitude + 2.f);
		gl.glVertex3f(offset + x2 * stepX - lenX, offset + y2 * stepY - lenY,altitude + 2.f);
		gl.glVertex3f(offset + x2 * stepX - lenX, offset + y2 * stepY - lenY,altitude);

		gl.glColor3f(0.3f, 0.3f, 0.3f);
		gl.glVertex3f(offset + x2 * stepX - lenX, offset + y2 * stepY - lenY,altitude + 2.f);
		gl.glVertex3f(offset + x2 * stepX - lenX, offset + y2 * stepY + lenY,altitude + 2.f);
		gl.glVertex3f(offset + x2 * stepX + lenX, offset + y2 * stepY + lenY,altitude + 2.f);
		gl.glVertex3f(offset + x2 * stepX + lenX, offset + y2 * stepY - lenY,altitude + 2.f);
		} // fin fallback cube (loupModel == null)

		if (_fireState == 1) {
			_redValue = 1.f;
			_greenValue = 1.f;
			_blueValue = 0.f;
		}else if (m != 1) {
			if (energie < 3) {
				_redValue = 1.f;
				_greenValue = 200.f / 255.f;
				_blueValue = 205.f / 255.f;
			} else {
				_redValue = 1.f;
				_greenValue = 0.f;
				_blueValue = 0.f;
			}
		} else {
			_redValue = 1.f;
			_greenValue = 0.f;
			_blueValue = 1.f;
		}

		// Triangle d'orientation : pointe dans la direction du dernier
		// déplacement effectif (cf. Agent.setLastMove). Rouge pour le loup ;
		// cyan vif quand l'agent est piloté manuellement (le jaune est réservé
		// au feu, cf. _fireState).
		if (playerControlled) gl.glColor3f(0.1f, 0.9f, 1f);
		else gl.glColor3f(_redValue, _greenValue, _blueValue);
		emitOrientationTriangle(gl,
				offset + x2 * stepX, offset + y2 * stepY,
				lenX, lenY,
				altitude + 5.f);
	}

}
