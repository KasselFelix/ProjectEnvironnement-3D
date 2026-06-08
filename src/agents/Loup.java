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
		// Planté sur le SOMMET de terrain (coin de cellule) dont l'altitude est lue,
		// pas au centre du quad — sinon décalage d'une demi-cellule → l'agent
		// s'enfonce dans le sol en pente (même cause que les jeunes arbres, cf. Tree).
		float px = offset + x2 * stepX - lenX;
		float py = offset + y2 * stepY - lenY;
		// prédateur : plus grand que le mouton, × taille individuelle (§ 10.2).
		float scale = Math.abs(lenX) * 2.0f * (float) l.displaySize();

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

	public int m = 0;// 1 si a manger ce tour
	public int attaqueNuit = 0;

	/** Seuil de faim (fraction de energieD) : sous ce niveau le loup chasse ET
	 *  consomme. Au-dessus il est repu — la nuit il poursuit quand même pour
	 *  effrayer/disperser le troupeau, mais ne TUE pas (pas de surplus killing :
	 *  il en laisse pour la meute). Partagé par decideState et le bloc prédation. */
	public static final double HUNGER_RATIO = 0.7;

	public int lastX;
	public int lastY;

	// Génome, cycle de vie (isFounder/currentStage/growthScale/displaySize) et
	// taille héritable : factorisés dans Agent (consolidation C3).

	public Loup(int __x, int __y, World __world) {
		super(__x, __y, __world);
		_alive = true;

		_redValue = 1.f;
		_greenValue = 0.f;
		_blueValue = 0.f;

		massKg = 45.0; frontalAreaM2 = 0.30;   // quadrupède profilé → résistance vent intermédiaire
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
			case FLEE_LAVA: return "Fuit lave";
			case FLEE_PREDATOR: return "Fuit berger";
			case REST:      return "Repos";
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
		// Math.max(1, …) : garde-fou anti division par zero. La vitesse evolue sans
		// borne haute (cf. but de la simulation) → le diviseur peut tomber a 0
		// (vitesse > 28). Au-dela de 28 l'agent agit simplement a chaque tick.
		return world.getIteration() % Math.max(1, (int) ((1.0 / vitesse) * 28)) == 0;
	}

	@Override
	protected void resetTickFlags() {
		m = 0;
		if(world.getCellHeight(x, y)<0){this._fireState=0;}

		lastX=x;
		lastY=y;

		refreshMemoryCapacity();   // L1 — la capacité mémoire suit l'âge/intelligence (§ 5.1)
	}

	@Override
	protected java.util.List<? extends objects.UniqueDynamicObject> prey() {
		return world.moutons;
	}

	/** Le Loup craint l'Humain (berger) ET l'Ours (super-prédateur, L4) : les deux
	 *  alimentent predatorDir du Percept → le loup fuit. Un troupeau gardé est plus
	 *  sûr ; un ours dans les parages fait fuir la meute. */
	@Override
	protected java.util.List<? extends objects.UniqueDynamicObject> predators() {
		if (world.ours.isEmpty()) return world.humains;   // cas courant : pas d'ours
		java.util.List<objects.UniqueDynamicObject> threats =
				new java.util.ArrayList<>(world.humains.size() + world.ours.size());
		threats.addAll(world.humains);
		threats.addAll(world.ours);
		return threats;
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
					// L1 — mémoire de chasse : la cellule où la prédation a réussi
					// est mémorisée comme zone fertile (MemoryKind.HUNTING). Affamé
					// et sans proie en vue, le loup y reviendra (huntHoming).
					memory.remember(MemoryKind.HUNTING, x, y);
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

		// Traînée du vent : calculée une fois, appliquée à la cadence (plus bas) ET
		// au coût métabolique (le vent propulse → énergie/temps invariante au vent).
		double windF = windDragFactor();

		// mise a jour energie
		if (energie <= 0) {
			_alive = false;
		} else {
			if (world.getCellHeight(x, y) < 0)energie-=2;
			if( world.getCellHeight(lastX, lastY) > world.getCellHeight(x, y)){
				energie--;
			}
			// ÷windF SEULEMENT si l'agent a bougé ce tour (sinon le vent ne fournit pas
			// de propulsion : à l'arrêt l'effort ne dépend pas du vent).
			boolean moved = (x != lastX || y != lastY);
			energie -= metabolicCost(moved ? 1.0 / windF : 1.0);   // L8 — coût métabolique modulé par l'activité
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

		// L6 — grand froid (nuits d'hiver) : l'agent s'engourdit (no-op si > 5°C).
		vitesse *= world.coldSpeedFactor();
		vitesse *= windF;   // traînée vent (même facteur que le décompte d'énergie ci-dessus)


		// si dans la lave
		if (_world.getLavaCAValue(x, y) > 0) {
			_alive = false;
		}


		// reproduction — conditionnée à l'énergie : le loup doit être en bonne
		// santé (≥ seuil) et INVESTIT une part de son énergie dans le petit
		// (énergie conservée, pas créée). Le petit naît à la position du parent.
		Loup mate = findReproPartner();
		// Gating par STADE (§ 10.1 : bébé/jeune ne se reproduisent pas, vieux à
		// taux réduit) + proba modulée par la fertilité du génome (§ 4.1/§ 4.3).
		if (currentStage().canReproduce() && energie >= energieD * reproEnergyThreshold && mate != null
				&& Math.random() < Prepro * genome.reproProbaFactor() * currentStage().fertilityFactor()) {
			double invest = energieD * reproOffspringRatio;
			Loup prea = new Loup(this.x, this.y, this._world);
			prea.isFounder = false;          // né → démarre BÉBÉ (§ 10.1)
			prea.energie = (int) invest;     // le petit hérite de l'énergie investie
			// Héritage évolutif : traits moyennés des deux parents + mutation ±10%.
			prea.vision  = mutateInt((this.vision + mate.vision) / 2);
			prea.vcourse = mutateDouble((this.vcourse + mate.vcourse) / 2.0);
			prea.vtrot   = mutateDouble((this.vtrot + mate.vtrot) / 2.0);
			// Héritage du génome (§ 4.4) + taille héritable (§ 10.2).
			prea.genome = agents.ai.Genome.inherit(this.genome, mate.genome,
					EVO_RNG, TYPE_MUTATION_RATE, GRANDPARENT_PROB);
			prea.initMind();   // L1 — esprit démarré depuis le génome hérité (§ 6)
			prea.sizeFactor = clampSize(mutateDouble((this.sizeFactor + mate.sizeFactor) / 2.0));
			// Longévité : base parentale × facteur Longévité du petit, × malus
			// si un parent est INFERTILE (§ 4.3 : enfants à courte vie).
			double childMaxAge = this.maxAgeDays;
			if (childMaxAge > 0) {
				childMaxAge *= prea.genome.longevityFactor();
				if (isInfertile(this) || isInfertile(mate)) childMaxAge *= INFERTILE_CHILD_LONGEVITY_MALUS;
			}
			prea.maxAgeDays = childMaxAge;
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
		if ( world.getIteration() % ticksPerGameSecond() == 0 )if(_fireState==1)energie-=energieD/10;

		// L1 — entraînement cérébral (§ 6.2) + émergence du caractère de meute (§ 7).
		trainMindAndCharacter();
	}

	/** Rayon (cases) en deçà duquel un congénère « rompt » l'isolement de meute (§ 7.3). */
	private static final int PACK_NEAR_RADIUS = 8;

	/** L1 — true si aucun autre loup vivant n'est à portée de meute. Donne au
	 *  Character de quoi faire émerger un trait GREGAIRE (loup de meute) ou
	 *  SOLITAIRE (loup solitaire) selon la vie sociale réelle de l'agent. */
	@Override
	public boolean isIsolated() {
		for (Loup o : world.loups) {
			if (o == this || !o._alive) continue;
			if (world.distance(o.x, o.y, x, y) <= PACK_NEAR_RADIUS) return false;
		}
		return true;
	}

	/** L1 — satisfaction ∈ [0,1] du loup (§ 7.3) : faim + sécurité + besoin
	 *  social MODULÉ par le caractère (un solitaire est comblé seul, un loup de
	 *  meute en groupe). Alimente l'émergence du caractère via trainMindAndCharacter. */
	@Override
	public double satisfaction() {
		double hunger = Math.max(0.0, Math.min(1.0, energie / (double) energieD));
		double safety = (isOnFire() || currentState == AgentState.FLEE_PREDATOR) ? 0.0 : 1.0;
		boolean iso = isIsolated();
		double socialNeed;
		switch (character.social()) {
			case SOLITARY:   socialNeed = iso ? 1.0 : 0.0; break;
			case GREGARIOUS: socialNeed = iso ? 0.0 : 1.0; break;
			default:         socialNeed = iso ? 0.4 : 0.6; break;   // léger biais meute
		}
		return (hunger + safety + socialNeed) / 3.0;
	}

	/** Rayon dans lequel un congénère vivant compte comme partenaire de repro. */
	private static final int REPRO_RADIUS = 3;
	// Mutation/taille + héritage du génome : factorisés dans Agent (C3).

	/** Partenaire de reproduction le plus proche (Loup vivant dans REPRO_RADIUS),
	 *  ou null. Exigé par la reproduction sexuée ; fournit la moitié des gènes. */
	private Loup findReproPartner() {
		Loup best = null;
		double bestD = Double.MAX_VALUE;
		for (Loup other : world.loups) {
			if (other == this || !other._alive) continue;
			double d = world.distance(other.x, other.y, x, y);
			if (d <= REPRO_RADIUS && d < bestD) { bestD = d; best = other; }
		}
		return best;
	}

	public AgentState decideState(Percept p) {
		boolean enChasse = energie < energieD * HUNGER_RATIO || attaqueNuit == 1;
		if (!enChasse) resetPursuit();        // plus en chasse → on oublie la piste
		if (isOnFire())                       return AgentState.ON_FIRE;
		if (p.lavaVisible())                  return AgentState.FLEE_LAVA;       // L2 — la lave tue au contact
		if (p.predatorVisible())              return AgentState.FLEE_PREDATOR;  // fuit l'Humain (prime sur la faim)
		if (enChasse && p.preyVisible())      return AgentState.HUNT;            // proie en vue (prioritaire)
		if (enChasse && hasFreshTrack())      return AgentState.HUNT;            // proie perdue de vue mais piste fraîche → persistance
		if (p.inWater)                        return AgentState.SEEK_LAND;
		if (enChasse)                         return AgentState.SEARCH;   // balayage spirale
		if (energie >= energieD)              return AgentState.REST;     // repu plein → repos
		return AgentState.WANDER;                                          // flânerie économe
	}

	public MoveConstraints applyState(AgentState s, Percept p) {
		switch (s) {
			case ON_FIRE:
				// fuit vers l'eau si vue, sinon continue tout droit. steerAroundObstacles
				// (anti-revisite) → atteint l'eau sans dithérer dans une concavité.
				if (p.waterDir >= 0) _orient = p.waterDir;
				vitesse = vcourse;
				return steerAroundObstacles(p, true, vision);   // contourne (anti-revisite) vers l'eau
			case FLEE_LAVA:
				// L2 — fuit à l'opposé de la lave, au sprint. Le loup nage bien
				// (amphibie) : il peut couper par l'eau pour s'éloigner.
				if (p.lavaDir >= 0) _orient = AgentState.opposite(p.lavaDir);
				vitesse = vcourse;
				return steerAroundObstacles(p, true, vision);   // contourne arbres/lave (anti-revisite) en fuyant
			case FLEE_PREDATOR:
				// Fuit à l'opposé de l'Humain. Le loup nage bien (swimFactor élevé)
				// → amphibie, pas besoin d'éviter l'eau comme le mouton.
				_orient = AgentState.opposite(p.predatorDir);
				vitesse = vcourse;
				return dodgeObstacles(true);   // contourne les obstacles en fuyant
			case HUNT: {
				// Affamé (< HUNGER_RATIO) → SPRINT (vcourse) pour rattraper et tuer.
				// Repu (chasse seulement parce que c'est la nuit, attaqueNuit) →
				// simple TROT (vtrot) : il effraie/disperse le troupeau sans se
				// vider en énergie. Comme vtrot(8) < vcourse mouton(9), le loup
				// repu ne rattrape pas — il ne fait qu'effrayer, ce qui est voulu.
				vitesse = (energie < energieD * HUNGER_RATIO) ? vcourse : vtrot;
				// Case visée : proie EN VUE → sa case (met à jour la piste) ; HORS DE
				// VUE → persistance vers la dernière position connue (case « fantôme »).
				int[] aim = p.preyVisible() ? pursuitSeen(p) : pursuitGhost();
				// Marque la spirale « à ré-amorcer » : au sortir de cette chasse, la
				// recherche repartira vers la dernière position connue de la proie
				// (seedSpiralTowardMemory) au lieu d'un cap périmé d'avant-chasse.
				mem.spiralHeading = -1;
				// Traque (pas direct → BFS contournement → évasion longe-mur si piégé
				// dans une poche en U) : logique partagée avec l'Ours (Agent.pursuitStep).
				return pursuitStep(p, aim, vision);   // ralenti dans l'eau par swimFactor (postMove)
			}
			case SEEK_LAND:
				// COMMIT : si une côte est en vue DROIT DEVANT (cap actuel), on TERMINE
				// la traversée au lieu de rebrousser vers la terre la plus proche (souvent
				// la rive qu'on vient de quitter) → anti-oscillation au littoral pendant
				// une traversée délibérée. Sinon (pas de côte devant : chute accidentelle
				// en eau profonde), on vise la terre la plus proche.
				if (!waterCrossable(_orient, vision)) {
					if (p.landDir >= 0) _orient = p.landDir;
				}
				vitesse = vcourse;   // ralenti dans l'eau par swimFactor (postMove)
				return dodgeObstacles(true);   // contourne les arbres vers la terre
			case REST:
				// Repu (énergie pleine) : repos sur place (économie d'énergie).
				wantsToMove = false;
				return MoveConstraints.landBound();
			case SEARCH:
				// L1 — d'abord rejoindre une zone de chasse mémorisée (homing À TERRE :
				// il ne rallie que s'il PROGRESSE vers elle, sinon il rend la main à la
				// spirale — cf. huntHoming, cause racine du gel littoral/forêt). Sinon,
				// balayage en SPIRALE : spiralSearch avance le CAP VOULU, followSpiralHeading
				// le suit, le repli de Locomotion gère les obstacles. Tout est landBound :
				// l'eau est un obstacle → aucune entrée dans l'eau → pas de bascule SEEK_LAND.
				if (huntHoming(p)) return MoveConstraints.landBound();
				seedSpiralTowardMemory(vision);   // biais mémoire : 1re branche vers la dernière proie / zone connue
				spiralSearch(vision);
				vitesse = vtrot;
				_orient = mem.spiralHeading;       // cap voulu de la spirale
				// Contournement intelligent : longe les obstacles (eau « vers le large »
				// ET murs d'arbres), mais TRAVERSE un bras d'eau si la côte est en vue
				// droit devant (allowSwim=true → headingKind/waterCrossable). Le cap de
				// spirale (mem.spiralHeading) est conservé : la déviation n'est que locale.
				return steerAroundObstacles(p, true, vision);
			case WANDER:
			default:
				lazyWander();
				// Anti-obstacle aussi en errance (sans nager : un loup repu ne
				// traverse pas l'eau pour flâner → allowSwim=false).
				return steerAroundObstacles(p, false, vision);
		}
	}

	/**
	 * Homing vers la zone de chasse mémorisée la plus proche (§ 5, L1). Renvoie
	 * true (et oriente le loup vers elle, au trot) UNIQUEMENT s'il existe un pas
	 * <b>à terre</b> qui RAPPROCHE réellement de la zone ; false sinon (→ retombe
	 * sur la spirale, qui ratisse les environs au cas où la proie a bougé).
	 *
	 * <p><b>Cause racine du gel en SEARCH (corrigé 2026-06-07).</b> L'ancienne
	 * version gardait le cap vers la zone à chaque tick et renvoyait toujours
	 * {@code true}, même quand la zone était INATTEIGNABLE (derrière un bras d'eau
	 * ou un mur d'arbres hors d'atteinte en vision). Conséquences observées :
	 * <ul>
	 *   <li>à terre : le loup battait le pied de l'obstacle (BFS « case atteignable
	 *       la plus proche » oscillant entre deux cases) — la spirale ne tournait
	 *       jamais ;</li>
	 *   <li>au littoral : le BFS était amphibie → pas DANS l'eau, puis {@code
	 *       decideState} renvoyait {@code SEEK_LAND} (sort de l'eau), puis re-homing
	 *       (re-rentre) → oscillation avance/recule au bord de l'eau.</li>
	 * </ul>
	 * Le correctif : (1) BFS et franchissement <b>à terre</b> (l'eau est un
	 * obstacle, comme la spirale) ; (2) on ne rallie QUE si le pas choisi diminue
	 * strictement la distance tore à la zone — sinon on renonce et on rend la main
	 * à la spirale. Le loup longe donc l'obstacle vers la zone tant qu'il progresse,
	 * et ratisse dès qu'il est bloqué, au lieu de rester figé.</p>
	 */
	private boolean huntHoming(Percept p) {
		// Ralliement à terre AVEC garde-fou de progrès (Agent.huntHomingDir →
		// homingStepToward) : -1 si la zone est murée/inatteignable → on rend la main
		// à la spirale au lieu d'osciller au pied de l'obstacle (cause racine du gel).
		int dir = huntHomingDir(p, vision);
		if (dir < 0) return false;
		_orient = dir;
		vitesse = vtrot;
		return true;
	}

	/**
	 * Cohésion de meute (§ 7, L1) : un loup au caractère NON solitaire qui voit
	 * des congénères s'oriente (au pas) vers leur barycentre. Renvoie true s'il a
	 * trouvé une meute à rallier ; false sinon (→ flânerie aléatoire). Un loup
	 * SOLITAIRE ignore la meute.
	 */
	private boolean packCohesion() {
		if (character.social() == SocialTrait.SOLITARY) return false;
		int dir = Perception.dirToFlockCentroid(this, world, world.loups);
		if (dir < 0) return false;
		_orient = dir;
		vitesse = vpas;
		return true;
	}

	/**
	 * Flânerie économe (à {@code vpas}) quand le loup est repu : ~20% du temps il
	 * tourne légèrement et marque une pause (ne se déplace pas ce tick), sinon il
	 * continue tout droit. Préserve le comportement legacy « 2.2 » et limite la
	 * dépense d'énergie au repos. Réinitialise la spirale pour que la prochaine
	 * recherche reparte du centre (spirale fraîche).
	 */
	private void lazyWander() {
		mem.resetSpiral();
		// L1 — cohésion de meute : un loup repu NON solitaire dérive vers le
		// barycentre des congénères visibles (la meute reste groupée hors chasse).
		if (packCohesion()) return;
		if (aheadVisitedRecently()) {
			// Mémoire spatiale : la case devant a déjà été visitée → on tourne
			// pour ne pas flâner en boucle.
			_orient = (_orient + 1) % 4;
			vitesse = vpas;
			return;
		}
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
