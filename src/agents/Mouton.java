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
			moutonModel = new GLBModel("models/Mouton_chibi.glb", false, gl, 0.32f, true); // true = retenir prims pour carcasse tintee
		} catch (Exception e) {
			System.out.println("[Mouton] erreur chargement GLB: " + e.getMessage());
			e.printStackTrace();
		}
	}

	/** Accesseur statique pour la carcasse : renvoie le modèle GLB déjà chargé (ou null). */
	public static GLBModel getModel() { return moutonModel; }

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
		// Planté sur le SOMMET de terrain (coin de cellule) dont l'altitude est lue,
		// pas au centre du quad — sinon décalage d'une demi-cellule → l'agent
		// s'enfonce dans le sol en pente (même cause que les jeunes arbres, cf. Tree).
		float px = offset + x2 * stepX - lenX;
		float py = offset + y2 * stepY - lenY;
		// Taille individuelle (§ 10.2) : trait héritable × croissance (âge) × Force.
		float scale = Math.abs(lenX) * 1.5f * (float) m.displaySize();

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

	public int earthSearch=0;// 1 si est dans l'eau et recherche la terre ferme

	public int fuite=0;
	/** Alarme du troupeau : tours restants où ce mouton fuit car ALERTÉ par un
	 *  congénère qui a vu un loup (même sans le voir lui-même). 0 = pas alerté. */
	public int alertTtl=0;
	/** Direction de fuite imposée par l'alarme (cap du troupeau). -1 = aucune. */
	public int alertDir=-1;
	public int m=0;
	public int lastX;
	public int lastY;

	/** Parent à suivre tant qu'on est bébé/jeune (§ 10.3). null pour un fondateur
	 *  ou un orphelin. Le suivi cesse quand le parent meurt ou qu'on devient adulte. */
	public Mouton parent;

	/** Cap de navigation longue distance courant (§ 9), distinct de _orient (le
	 *  regard pas-à-pas). -1 = pas de cap longue distance actif. */
	public int directionSens = -1;

	// memory / mind / character + CHARACTER_SESSION_DAYS : hissés dans Agent (L1) ;
	// hérités tels quels. Le Mouton ne garde que ce qui lui est propre.
	/** Rayon (cases) en deçà duquel un congénère « rompt » l'isolement (§ 7.3). */
	private static final int FLOCK_NEAR_RADIUS = 6;

	/** true si aucun congénère vivant n'est à portée de troupeau (§ 7.3). */
	@Override
	public boolean isIsolated() {
		for (Mouton o : world.moutons) {
			if (o == this || !o._alive) continue;
			if (world.distance(o.x, o.y, x, y) <= FLOCK_NEAR_RADIUS) return false;
		}
		return true;
	}

	/** Satisfaction globale ∈ [0, 1] (§ 7.3) : moyenne des besoins primaires
	 *  (faim, sécurité, social). Le besoin SOCIAL est MODULÉ par le caractère :
	 *  un solitaire est satisfait seul, un grégaire en groupe. */
	@Override
	public double satisfaction() {
		double hunger = Math.max(0.0, Math.min(1.0, energie / energieMAX));
		double safety = (isOnFire() || currentState == agents.ai.AgentState.FLEE_PREDATOR) ? 0.0 : 1.0;
		boolean iso = isIsolated();
		double social;
		switch (character.social()) {
			case SOLITARY:   social = iso ? 1.0 : 0.0; break;
			case GREGARIOUS: social = iso ? 0.0 : 1.0; break;
			default:         social = iso ? 0.4 : 0.6; break;   // léger biais grégaire naturel
		}
		return (hunger + safety + social) / 3.0;
	}

	// initMind / evolutionSummary / stageLabel / socialLabel / activityLevel /
	// refreshMemoryCapacity : hissés dans Agent (L1), hérités tels quels.

	/** true si la carcasse perçue ({@code p.carcassX/Y}) est une carcasse de mouton. */
	private boolean isMoutonCarcass(agents.ai.Percept p) {
		objects.Carcass c = world.carcassAt(p.carcassX, p.carcassY);
		return c != null && c.source == objects.Species.MOUTON;
	}

	/** Mémorise comme DANGER la position du loup visible le plus proche (§ 5). */
	private void recordNearestPredatorAsDanger() {
		Loup nearest = null;
		double bestD = Double.MAX_VALUE;
		for (Loup l : world.loups) {
			if (!l._alive) continue;
			double d = world.distance(l.x, l.y, x, y);
			if (d <= vision && d < bestD) { bestD = d; nearest = l; }
		}
		if (nearest != null) memory.remember(agents.ai.MemoryKind.DANGER, nearest.x, nearest.y);
	}

	/** Rayon (cases) d'apprentissage d'un point de repère (bergerie → lieu sûr). */
	private static final int LEARN_LANDMARK_RADIUS = 3;

	/** Rayon (cases) d'apprentissage social auprès d'un congénère (§ 8). */
	private static final int SOCIAL_LEARN_RADIUS = 6;

	/**
	 * Apprentissage social & éducation (§ 8) : le mouton recopie les
	 * connaissances sémantiques de ses congénères vivants à portée (le parent en
	 * fait partie quand l'agneau le suit → éducation). Un mouton isolé n'apprend
	 * rien. La capacité mémoire (liée à l'intelligence) borne ce qu'il retient.
	 */
	public void learnFromNeighbours() {
		for (Mouton other : world.moutons) {
			if (other == this || !other._alive) continue;
			if (world.distance(other.x, other.y, x, y) <= SOCIAL_LEARN_RADIUS) {
				other.memory.teach(this.memory);
			}
		}
	}

	/** Mémorise les repères proches comme lieux sûrs (§ 5 / § 9) : si le mouton
	 *  est près de la bergerie, il l'enregistre comme SAFE_PLACE. */
	public void learnNearbyLandmarks() {
		worlds.WorldOfCells wc = (worlds.WorldOfCells) world;
		int bx = wc.getBergerieX(), by = wc.getBergerieY();
		if (world.distance(x, y, bx, by) <= LEARN_LANDMARK_RADIUS) {
			memory.remember(agents.ai.MemoryKind.SAFE_PLACE, bx, by);
		}
	}

	/** Lieu sûr mémorisé le plus proche (tore-aware), ou null. */
	private int[] nearestSafePlace() {
		return memory.nearest(agents.ai.MemoryKind.SAFE_PLACE, x, y,
				(a, b, c, d) -> world.distance(a, b, c, d));
	}

	/** true si la case droit devant (cap _orient) est une zone de danger mémorisée. */
	private boolean dangerAhead() {
		int ax = ((x + orientDx(_orient)) % world.getWidth() + world.getWidth()) % world.getWidth();
		int ay = ((y + orientDy(_orient)) % world.getHeight() + world.getHeight()) % world.getHeight();
		return memory.contains(agents.ai.MemoryKind.DANGER, ax, ay);
	}

	/** Rayon (cases) dans lequel un agneau suit son parent (§ 10.3). */
	private static final int FOLLOW_RADIUS = 8;

	/** true si l'agneau a un parent vivant à portée à suivre (bébé/jeune only). */
	private boolean shouldFollowParent() {
		agents.ai.LifeStage s = currentStage();
		if (s != agents.ai.LifeStage.BABY && s != agents.ai.LifeStage.JUVENILE) return false;
		return parent != null && parent._alive
				&& world.distance(parent.x, parent.y, x, y) <= FOLLOW_RADIUS;
	}

	public Mouton( int __x, int __y, World __World )
	{
		super(__x,__y,__World);
		_alive = true;

		_redValue = 0.f;
		_greenValue = 0.f;
		_blueValue = 1.f;

		baseMassKg = 70.0; frontalAreaM2 = 0.35;   // espèce de référence → résistance vent 1.0
	}

	/** Accesseur public pour l'UI (les champs restent package-private). */
	public double getEnergie() { return energie; }
	public double getEnergieMax() { return energieMAX; }
	public boolean isAlive() { return _alive; }

	@Override protected double getEnergieForMass() { return energie; }
	@Override public double energieMaxValue() { return energieMAX; }

	@Override public String getTypeName() { return "Mouton"; }

	/** Conditions de broutage : pas en fuite, faim modérée, sur de l'herbe. Partagé par
	 *  postMove (effet) et isFeeding (vigilance réduite) pour rester synchrones. */
	private boolean isGrazingNow() {
		return fuite == 0 && energie < (energieMAX * 0.75) && world.getGrassCAValue(x, y) == 1;
	}

	@Override public boolean isFeeding() {
		return isGrazingNow();
	}

	// Sous-projet D : risque = rester près d'une odeur de loup sans fuir.
	@Override protected boolean riskSituation(agents.ai.Percept p) {
		return p != null && p.scentDangerDetected();
	}
	@Override protected boolean tookRisk(agents.ai.Percept p) {
		// Pas de dépendance à p : currentState reflète la décision de ce tick.
		return currentState != agents.ai.AgentState.FLEE_PREDATOR;
	}

	/** La proie ne bloque pas son prédateur : un Loup peut entrer sur sa case
	 *  pour la dévorer (chevauchement transitoire). Bloque tous les autres. */
	@Override
	public boolean blocksMovementOf(objects.UniqueDynamicObject mover) {
		return !(mover instanceof Loup);
	}

	@Override public String getCurrentBehavior() {
		if (playerControlled) return "Piloté";
		if (_fireState == 1) return "Fuit feu";
		if (m == 1)          return "Broute";
		switch (currentState) {
			case FLEE_LAVA:     return "Fuit lave";
			case FLEE_PREDATOR: return "Fuit loup";
			case SEEK_LAND:     return "Cherche terre";
			case SEEK_FOOD:     return "Cherche herbe";
			case HERD:          return "Regroupement";
			case REST:          return "Repos";
			case WARY:          return "Méfiance";
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
		// Math.max(1, …) : garde-fou anti division par zero (vitesse evolutive non
		// bornee → diviseur 0 si vitesse > 28 ; au-dela l'agent agit chaque tick).
		return world.getIteration() % Math.max(1, (int)((1.0/vitesse)*28)) == 0;
	}

	@Override
	protected void resetTickFlags() {
		fuite=0;
		m=0;
		refreshMemoryCapacity();             // la capacité mémoire suit l'âge (§ 5.1)
		learnNearbyLandmarks();              // apprend la bergerie comme lieu sûr (§ 9)
		learnFromNeighbours();               // apprend des congénères proches (§ 8)
		if (alertTtl > 0) alertTtl--;        // l'alarme s'estompe (en TOURS, pas en ticks)
		if(world.getCellHeight(x, y)>=0)earthSearch=0;
		if(world.getCellHeight(x, y)<0){this._fireState=0;}

		lastX=x;
		lastY=y;
	}

	/** Rayon (cases) de propagation de l'alarme à un congénère. */
	private static final int ALERT_RADIUS = 6;
	/** Durée de l'alarme transmise, en TOURS de l'agent (gating vitesse). */
	private static final int ALERT_DURATION = 4;

	/**
	 * Donne l'alarme : tout Mouton à portée ALERT_RADIUS (tore-aware) est marqué
	 * alerté pour ALERT_DURATION tours et reçoit le cap de fuite du troupeau.
	 * Seuls les moutons qui VOIENT réellement le loup propagent → pas de cascade
	 * infinie (un mouton seulement alerté ne ré-alerte personne).
	 */
	private void alertNeighbours() {
		for (Mouton other : world.moutons) {
			if (other == this) continue;
			if (world.distance(other.x, other.y, x, y) <= ALERT_RADIUS) {
				other.alertTtl = ALERT_DURATION;
				other.alertDir = _orient;
			}
		}
	}

	@Override
	protected java.util.List<? extends objects.UniqueDynamicObject> predators() {
		return world.loups;
	}

	// Sous-projet C (berger) : le mouton ne craint PAS l'humain (sinon il fuirait
	// son berger). Aligné sur predators() = world.loups. Futur : par-instance pour
	// distinguer mouton sauvage vs apprivoisé.
	private static final scent.ScentKind[] SCENT_DANGER_KINDS = { scent.ScentKind.LOUP };
	@Override public scent.ScentKind[] scentDangerKinds() { return SCENT_DANGER_KINDS; }

	@Override
	protected boolean canMove() { return energie > 2; }

	@Override
	protected void applyControlSpeed() {
		// 2 allures seulement : SPRINT=vcourse ; WALK et TROT partagent vmarche.
		switch (controlGait) {
			case SPRINT: vitesse = vcourse;  break;
			default:     vitesse = vmarche;  break;
		}
	}

	@Override
	protected void postMove(Percept p) {
		//Broute
		if (isGrazingNow()) {
			// V4 — broutage : la cellule passe en herbe RASE (markGrazed) au lieu
			// de disparaître net → repousse différée + rendu « tondu » visible.
			((worlds.WorldOfCells) world).grassCA.markGrazed(x, y);
			energie+=energieMAX/100;
			m=1;
			//System.out.println("broute");
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


		// Traînée du vent : calculée une fois, appliquée à la cadence (plus bas) ET
		// au coût métabolique (le vent propulse → énergie/temps invariante au vent).
		double windF = windDragFactor();

		//mise a jour energie
		if(energie<=0){
			_alive = false;
		}else{
			if(world.getCellHeight(x, y)<0)energie-=2;
			if( world.getCellHeight(lastX, lastY) > world.getCellHeight(x, y)){
				energie--;
			}
			// ÷windF SEULEMENT si l'agent a bougé ce tour (sinon le vent ne fournit pas
			// de propulsion : à l'arrêt l'effort ne dépend pas du vent).
			boolean moved = (x != lastX || y != lastY);
			energie -= metabolicCost(moved ? 1.0 / windF : 1.0);   // L8 — coût métabolique modulé par l'activité
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

		// L6 — grand froid (nuits d'hiver) : le mouton s'engourdit (no-op si > 5°C).
		vitesse *= world.coldSpeedFactor();
		vitesse *= windF;   // traînée vent (même facteur que le décompte d'énergie ci-dessus)

		//si dans la lave
		if(_world.getLavaCAValue(x,y)>0) {
			_alive=false;
		}

		//reproduction — conditionnée à l'énergie : le mouton doit être en bonne
		// santé (≥ seuil) et INVESTIT une part de son énergie dans l'agneau
		// (énergie conservée, pas créée). L'agneau naît à la position du parent.
		Mouton mate = findReproPartner();
		// Reproduction gardée par le STADE (§ 10.1) : bébé et jeune ne se
		// reproduisent pas. La proba est modulée par la fertilité du génome
		// (§ 4.1/§ 4.3) : FERTILE ×1.5, INFERTILE ×0.2 (cul-de-sac mou).
		if(currentStage().canReproduce() && energie >= energieMAX * reproEnergyThreshold && mate != null
				&& Math.random() < Prepro * genome.reproProbaFactor() * currentStage().fertilityFactor()) {
			double invest = energieMAX * reproOffspringRatio;
			Mouton prea=new Mouton(this.x, this.y, this._world);
			prea.isFounder = false;      // né par reproduction → démarre BÉBÉ (§ 10.1)
			prea.parent = this;          // suit son parent tant qu'il est jeune (§ 10.3)
			prea.energie = invest;       // l'agneau hérite de l'énergie investie
			// Héritage évolutif : l'agneau hérite des traits MOYENNÉS des deux
			// parents, avec une légère mutation → variation individuelle et
			// sélection naturelle (les mieux adaptés laissent plus de descendants).
			prea.vision  = mutateInt((this.vision + mate.vision) / 2);
			prea.vcourse = mutateDouble((this.vcourse + mate.vcourse) / 2.0);
			prea.vmarche = mutateDouble((this.vmarche + mate.vmarche) / 2.0);
			// Taille héritable (§ 10.2) : moyenne des parents + mutation, clampée.
			prea.sizeFactor = clampSize(mutateDouble((this.sizeFactor + mate.sizeFactor) / 2.0));
			// Héritage du GÉNOME : axes hérités des deux parents (+ conflit→NEUTRE,
			// mutation de type, saut de génération possible — cf. Genome.inherit § 4.4).
			prea.genome = agents.ai.Genome.inherit(this.genome, mate.genome,
					EVO_RNG, TYPE_MUTATION_RATE, GRANDPARENT_PROB);
			prea.initMind();             // esprit démarré depuis le génome hérité (§ 6)
			// Longévité de l'agneau : base parentale × facteur de son propre axe
			// Longévité (§ 4.1), puis malus si un parent est INFERTILE (§ 4.3 :
			// enfants à courte espérance de vie).
			double childMaxAge = this.maxAgeDays;
			if (childMaxAge > 0) {
				childMaxAge *= prea.genome.longevityFactor();
				if (isInfertile(this) || isInfertile(mate)) {
					childMaxAge *= INFERTILE_CHILD_LONGEVITY_MALUS;
				}
			}
			prea.maxAgeDays = childMaxAge;
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
		if ( world.getIteration() % ticksPerGameSecond() == 0 )if(_fireState==1)energie-=energieMAX/10;

		// Entraînement cérébral (§ 6.2) + émergence du caractère (§ 7) :
		// centralisés dans Agent.trainMindAndCharacter() (L1).
		trainMindAndCharacter();
	}

	/** Rayon (cases) dans lequel un congénère vivant compte comme partenaire de
	 *  reproduction. La reproduction sexuée exige un tel partenaire à portée. */
	private static final int REPRO_RADIUS = 3;
	// Héritage du génome + helpers de mutation/taille : factorisés dans Agent (C3).

	/** Partenaire de reproduction le plus proche (Mouton vivant dans REPRO_RADIUS),
	 *  ou null si aucun. La reproduction sexuée l'exige ; il fournit aussi la
	 *  moitié des traits hérités par l'agneau. Le regroupement nocturne maintient
	 *  le troupeau assez serré pour éviter l'extinction par dispersion. */
	private Mouton findReproPartner() {
		Mouton best = null;
		double bestD = Double.MAX_VALUE;
		for (Mouton other : world.moutons) {
			if (other == this || !other._alive) continue;
			double d = world.distance(other.x, other.y, x, y);
			if (d <= REPRO_RADIUS && d < bestD) { bestD = d; best = other; }
		}
		return best;
	}


	/** Décision pure (priorité = ordre des gardes). La recherche de nourriture
	 *  vient APRÈS la survie (feu, fuite, sortie de l'eau) : un mouton affamé
	 *  ne cherche de l'herbe que s'il n'est ni en feu, ni poursuivi, ni dans
	 *  l'eau. Affamé = énergie sous 50% du max ET de l'herbe en vue. */
	public AgentState decideState(Percept p) {
		// Une carcasse de congénère = signal de prédateur récent → zone à éviter.
		if (p.carcassVisible() && isMoutonCarcass(p)
				&& !memory.contains(agents.ai.MemoryKind.DANGER, p.carcassX, p.carcassY))
			memory.remember(agents.ai.MemoryKind.DANGER, p.carcassX, p.carcassY);

		if (isOnFire())          return AgentState.ON_FIRE;
		if (p.lavaVisible())     return AgentState.FLEE_LAVA;   // L2 — la lave tue au contact : priorité absolue
		if (p.predatorVisible()) return AgentState.FLEE_PREDATOR;
		if (alertTtl > 0)        return AgentState.FLEE_PREDATOR;   // alerté par le troupeau
		// Sous-projet C : méfiance olfactive graduée, SOUS la fuite visuelle.
		// Trace de loup forte/fraîche → fuite ; faible → méfiance. scentDangerKinds
		// = {LOUP} (Task 1) → l'odeur humaine n'arrive jamais ici (berger).
		ui.SimulationConfig cfgScent = ui.SimulationConfig.getInstance();
		// Sous-projet D : le seuil de fuite est modulé par le trait — TÉMÉRAIRE le
		// relève (reste WARY plus longtemps), PRUDENT l'abaisse (fuit plus tôt),
		// NEUTRE → facteur 1.0 (identique à avant D).
		double scentFleeThreshold = cfgScent.scentFleeIntensity
				* character.boldnessFactor(cfgScent.fleeBoldnessDelta);
		if (p.scentDangerDetected() && p.scentDangerIntensity >= scentFleeThreshold)
			return AgentState.FLEE_PREDATOR;
		if (p.scentDangerDetected() || scentCommitLeft > 0)
			return AgentState.WARY;
		if (p.inWater)           return AgentState.SEEK_LAND;
		// Suivi du parent (§ 10.3) : un agneau colle à son parent, priorité sur
		// l'herbe / le troupeau / l'errance (mais après la survie ci-dessus).
		if (shouldFollowParent()) return AgentState.FOLLOW_PARENT;
		// La nuit : le troupeau se regroupe (sécurité du nombre) s'il voit des
		// congénères — SAUF un SOLITAIRE qui évite le troupeau (§ 7.2). Sinon, un
		// mouton ISOLÉ qui connaît un lieu sûr y rentre (§ 9).
		if (world.getJour() == 0) {
			if (character.social() != agents.ai.SocialTrait.SOLITARY
					&& agents.ai.Perception.dirToFlockCentroid(this, world, world.moutons) >= 0)
				return AgentState.HERD;
			if (nearestSafePlace() != null)
				return AgentState.HOME;
		}
		if (energie < energieMAX * 0.5 && p.grassVisible()) return AgentState.SEEK_FOOD;
		if (energie >= energieMAX)                          return AgentState.REST;   // repu → rumination
		return AgentState.WANDER;
	}

	/** Traduit l'état en intention (orientation + vitesse), renvoie les contraintes,
	 *  et met à jour les flags legacy lus par les blocs conservés (Broute, énergie). */
	public MoveConstraints applyState(AgentState s, Percept p) {
		// par défaut, on remet les flags legacy à 0 ; chaque état réactive ce qu'il faut
		fuite = 0; earthSearch = 0;
		// Hors de l'état de ralliement (HOME), on purge le record de distance du
		// homing : sinon un record périmé empêcherait un futur retour au bercail.
		if (s != AgentState.HOME) mem.resetHoming();
		switch (s) {
			case FOLLOW_PARENT:
				// L'agneau marche vers son parent (§ 10.3) — la proximité déclenche
				// aussi l'éducation (Phase F).
				if (parent != null) {
					int pdir = agents.ai.Perception.dirToCell(this, world, parent.x, parent.y);
					if (pdir >= 0) _orient = pdir;
				}
				vitesse = vmarche;
				return MoveConstraints.landBound();
			case HOME:
				// Rentre vers le lieu sûr connu le plus proche (§ 9).
				int[] safe = nearestSafePlace();
				if (safe != null) {
					boolean inSight = world.distance(x, y, safe[0], safe[1]) <= vision;
					if (inSight) {
						// En vue : ralliement à terre AVEC garde-fou de progrès
						// (Agent.homingStepToward, partagé avec le loup). Si le lieu
						// sûr est muré (eau/forêt) et qu'aucun pas ne rapproche, le
						// homing CÈDE LA PRIORITÉ à l'errance au lieu de figer le
						// mouton au pied de l'obstacle (même cause racine que le loup).
						int dir = homingStepToward(safe[0], safe[1], vision, p);
						if (dir >= 0) { _orient = dir; }
						else { return wanderMove(); }
					} else {
						// Hors vue : navigation à l'estime, perturbée par l'axe
						// Orientation (directionSens, feature § 9).
						directionSens = agents.ai.Perception.navigate(this, world,
								safe[0], safe[1], genome.orientationErrorProb(), EVO_RNG);
						if (directionSens >= 0) _orient = directionSens;
					}
				}
				vitesse = vmarche;
				return MoveConstraints.landBound();
			case HERD:
				// Regroupement nocturne : marche vers le centre de masse du troupeau.
				int gdir = agents.ai.Perception.dirToFlockCentroid(this, world, world.moutons);
				if (gdir >= 0) _orient = gdir;
				vitesse = vmarche;
				return MoveConstraints.landBound();
			case REST:
				// Repu : rumination sur place. On annule le déplacement de ce tick
				// (le coût d'énergie de postMove s'applique quand même — l'économie
				// métabolique réelle est laissée à un futur modèle d'activité).
				wantsToMove = false;
				vitesse = vmarche;
				return MoveConstraints.landBound();
			case ON_FIRE: {
				// Rejoint la case d'EAU la plus proche (l'eau éteint le feu) avec sortie
				// GARANTIE des pièges concaves : pursuitStep (direct → BFS vision →
				// replanif élargie si bloqué dans un U). Fallback steer si aucune eau à portée.
				vitesse = vcourse;
				int[] water = nearestWaterCell(escapeRadius());
				if (water[0] >= 0) return pursuitStep(p, water, vision);
				if (p.waterDir >= 0) _orient = p.waterDir;
				return steerAroundObstacles(p, true, vision);
			}
			case FLEE_LAVA:
				// L2 — fuit à l'opposé de la lave la plus proche, au sprint. Le
				// mouton craint l'eau : il reste à terre (allowSwim=false). Anti-revisite
				// → il longe proprement la lave/les arbres sans se piéger dans une poche.
				if (p.lavaDir >= 0) _orient = AgentState.opposite(p.lavaDir);
				fuite = 1;                       // supprime le Broute pendant la fuite
				vitesse = vcourse;
				return steerAroundObstacles(p, false, vision);  // contourne arbres/lave (anti-revisite), eau interdite
			case FLEE_PREDATOR:
				if (p.predatorVisible()) {
					// Voit le loup : fuit à l'opposé (en évitant l'eau si possible),
					// donne l'alarme au troupeau proche ET mémorise la zone de danger.
					_orient = chooseFleeOrient(p);
					alertNeighbours();
					recordNearestPredatorAsDanger();
				} else if (alertDir >= 0) {
					// Alerté sans voir le loup : fuit dans le cap du troupeau.
					_orient = alertDir;
				} else {
					// Sous-projet C : fuite déclenchée par l'ODEUR (ni vue, ni alerte).
					// Engage un cap opposé à la trace qq ticks (anti-oscillation) ;
					// le timer décroît dans step().
					if (scentCommitLeft <= 0 && p.scentDangerDir >= 0) {
						scentFleeDir = AgentState.opposite(p.scentDangerDir);
						scentCommitLeft = ui.SimulationConfig.getInstance().scentCommitTicks;
					}
					if (scentFleeDir >= 0) _orient = scentFleeDir;
				}
				fuite = 1;                       // supprime le Broute pendant la fuite
				vitesse = vcourse;               // correctif : toujours vcourse à la fuite
				return MoveConstraints.amphibious();
			case SEEK_LAND:
				// Par défaut : terre la plus proche. MAIS s'il a fui (danger mémorisé),
				// il ne doit pas regagner la rive du PRÉDATEUR : on vise alors une rive
				// EN VUE la plus éloignée du danger (aucun prédateur n'est visible ici,
				// sinon on serait en FLEE_PREDATOR). Anti « demi-tour vers le loup » en
				// pleine traversée de rivière.
				int seekDir = p.landDir;
				int[] danger = memory.nearest(agents.ai.MemoryKind.DANGER, x, y,
						(a, b, c, d) -> world.distance(a, b, c, d));
				if (danger != null) {
					int safeBank = bankDirAwayFromDanger(danger[0], danger[1]);
					if (safeBank >= 0) seekDir = safeBank;
				}
				if (seekDir >= 0) _orient = seekDir;
				earthSearch = 1;
				vitesse = vcourse;   // ralenti dans l'eau par swimFactor (postMove)
				return dodgeObstacles(true);   // contourne les arbres vers la terre
			case SEEK_FOOD:
				// affamé : se dirige vers l'herbe la plus proche en vue (plus
				// d'errance aléatoire). L'herbe est sur terre → landBound. Le
				// broutage effectif reste géré dans postMove quand il l'atteint.
				if (p.grassDir >= 0) _orient = p.grassDir;
				vitesse = vmarche;
				return MoveConstraints.landBound();
			case WARY: {
				// Méfiance : s'écarte de la trace au PAS, sans brouter, en gardant un
				// cap engagé (anti-oscillation ; décru en step()). steerAroundObstacles
				// est anti-revisite → pas d'aller-retour.
				if (scentCommitLeft <= 0 && p.scentDangerDir >= 0) {
					scentFleeDir = AgentState.opposite(p.scentDangerDir);
					scentCommitLeft = ui.SimulationConfig.getInstance().scentCommitTicks;
				}
				if (scentFleeDir >= 0) _orient = scentFleeDir;
				vitesse = vmarche;
				// Ne broute pas : isGrazingNow() est gardé par `fuite == 0`, donc on
				// pose `fuite = 1` (même suppresseur que la fuite) ; postMove ne broutera
				// pas. m = 0 efface tout drapeau de broutage résiduel.
				fuite = 1;
				m = 0;
				return steerAroundObstacles(p, false, vision);
			}
			case WANDER:
			default:
				return wanderMove();
		}
	}

	/**
	 * Mouvement d'errance (état WANDER, et repli de HOME quand le lieu sûr est
	 * inatteignable) : évite une zone de danger connue / une case déjà visitée
	 * (mémoires sémantique § 5.3 et spatiale), sinon tourne un peu au hasard. Le
	 * pas réel (et le contournement d'obstacle) est laissé au repli de Locomotion.
	 */
	private MoveConstraints wanderMove() {
		if (dangerAhead()) {
			_orient = (_orient + 1) % 4;
		} else if (aheadVisitedRecently()) {
			_orient = (_orient + 1) % 4;
		} else if (Math.random() < 0.2) {
			_orient = (Math.random() > 0.5) ? (_orient + 1) % 4 : (_orient - 1 + 4) % 4;
		}
		vitesse = vmarche;
		return MoveConstraints.landBound();
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

	/**
	 * Direction cardinale d'une RIVE EN VUE (terre ferme atteignable tout droit dans
	 * la portée de vision) la PLUS ÉLOIGNÉE de ({@code dgX},{@code dgY}) — pour qu'un
	 * mouton sortant de l'eau après une fuite ne regagne pas la rive du prédateur.
	 * Renvoie -1 si aucune rive n'est en vue (→ l'appelant garde la terre la plus proche).
	 */
	private int bankDirAwayFromDanger(int dgX, int dgY) {
		int w = world.getWidth(), h = world.getHeight();
		int best = -1; double bestD = -1;
		for (int d = 0; d < 4; d++) {
			if (!waterCrossable(d, vision)) continue;          // pas de rive en vue par là
			int lx = x, ly = y;                                // cellule de débarquement = 1re terre le long de d
			for (int r = 1; r <= vision; r++) {
				int cx = ((x + orientDx(d) * r) % w + w) % w;
				int cy = ((y + orientDy(d) * r) % h + h) % h;
				if (world.getCellHeight(cx, cy) >= 0) { lx = cx; ly = cy; break; }
			}
			double dist = world.distance(lx, ly, dgX, dgY);
			if (dist > bestD) { bestD = dist; best = d; }
		}
		return best;
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
