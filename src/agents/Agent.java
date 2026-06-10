// ### WORLD OF CELLS ### 
// created by nicolas.bredeche(at)upmc.fr
// date of creation: 2013-1-12

package agents;

import javax.media.opengl.GL2;

import objects.CommonObject;
import objects.Layer;
import objects.Material;
import objects.UniqueDynamicObject;

import worlds.World;

public class Agent extends UniqueDynamicObject{

	World _world;

	/** Vivant ? Déclaré sur la base commune pour que le monde puisse tester
	 *  l'occupation des cases sans connaître le type concret de l'agent. */
	public boolean _alive = true;

	/**
	 * Cet agent bloque-t-il le déplacement de {@code mover} vers sa case ? Par
	 * défaut OUI : un agent est un obstacle pour tous les autres (pas de
	 * superposition). Surchargé par la proie pour laisser entrer son prédateur.
	 */
	public boolean blocksMovementOf(UniqueDynamicObject mover) {
		return true;
	}

	// ===== Évolution — socle commun (cf. docs/evolution.txt) =====
	// Factorisé depuis Mouton/Loup (consolidation C3). Chaque espèce câble sa
	// reproduction par-dessus ; les fondateurs ont un génome NEUTRE.

	/** Durée de vie en jours-jeu ; ≤ 0 = immortel par vieillesse (§ 10.1). */
	public double maxAgeDays = -1;
	/** Génome : axes à 3 états héritables (§ 4). NEUTRE pour un fondateur. */
	public agents.ai.Genome genome = new agents.ai.Genome();
	/** true = spawné (adulte d'emblée) ; false = né (démarre BÉBÉ, § 10.1). */
	public boolean isFounder = true;
	/** Facteur de taille individuel héritable (§ 10.2), clampé [0.8, 1.2]. */
	public double sizeFactor = 1.0;

	// ===== Physique du corps (modèle de traînée du vent) =====
	/** Masse de référence (kg) — adulte sain. Sert à la résistance au vent ET aux
	 *  carcasses. Défaut = mouton (~70 kg) ; chaque espèce la fixe dans son constructeur.
	 *  (ex-massKg, renommé pour distinguer de bodyMassKg() qui est dynamique.) */
	public double baseMassKg = 70.0;
	/** Surface frontale exposée au vent (m²). Grande voilure = plus poussé.
	 *  Défaut = mouton (~0.35 m²) ; chaque espèce la fixe dans son constructeur. */
	public double frontalAreaM2 = 0.35;
	/** Référence de normalisation = masse/surface du mouton (kg/m²) → résistance 1.0,
	 *  ce qui préserve le calibrage initial (WIND_DRAG_K) pour l'espèce de référence. */
	private static final double WIND_RESISTANCE_REF = 70.0 / 0.35;

	/** Stade de vie courant (§ 10.1) — un fondateur saute l'enfance. */
	public agents.ai.LifeStage currentStage() {
		agents.ai.LifeStage s = agents.ai.LifeStage.of(getAgeDays(), maxAgeDays);
		if (isFounder && (s == agents.ai.LifeStage.BABY || s == agents.ai.LifeStage.JUVENILE)) {
			return agents.ai.LifeStage.ADULT;
		}
		return s;
	}

	/** Échelle de croissance liée à l'âge (§ 10.2) : fondateur 1.0, né = Gompertz. */
	public double growthScale() {
		if (isFounder) return 1.0;
		return agents.ai.LifeStage.gompertzGrowth(getAgeDays(), maxAgeDays);
	}

	/** Taille de rendu relative (§ 10.2) : trait × croissance × Force du génome. */
	public double displaySize() {
		return sizeFactor * growthScale() * genome.strengthSizeFactor();
	}

	// ----- Héritage à la naissance (constantes + utilitaires partagés) -----
	/** Source d'aléa pour l'héritage du génome. */
	protected static final java.util.Random EVO_RNG = new java.util.Random();
	/** Mutation ±MUTATION_RATE des traits numériques hérités. */
	protected static final double MUTATION_RATE = 0.1;
	/** Proba de mutation d'un axe du génome à la naissance (§ 4.2). */
	protected static final double TYPE_MUTATION_RATE = 0.05;
	/** Proba d'hériter un axe d'un grand-parent (§ 4.4). */
	protected static final double GRANDPARENT_PROB = 0.1;
	/** Malus de longévité des enfants d'un parent INFERTILE (§ 4.3). */
	protected static final double INFERTILE_CHILD_LONGEVITY_MALUS = 0.5;
	private static final double SIZE_FACTOR_MIN = 0.8;
	private static final double SIZE_FACTOR_MAX = 1.2;

	protected static int mutateInt(int base) {
		double f = 1.0 + (Math.random() * 2 - 1) * MUTATION_RATE;
		return Math.max(1, (int) Math.round(base * f));
	}

	protected static double mutateDouble(double base) {
		double f = 1.0 + (Math.random() * 2 - 1) * MUTATION_RATE;
		return Math.max(0.1, base * f);
	}

	protected static double clampSize(double s) {
		return Math.max(SIZE_FACTOR_MIN, Math.min(SIZE_FACTOR_MAX, s));
	}

	/** true si l'agent porte l'axe Fertilité au pôle NÉGATIF (INFERTILE, § 4.3). */
	protected static boolean isInfertile(Agent a) {
		return a.genome.get(agents.ai.Axis.FERTILITY) == agents.ai.Pole.NEGATIVE;
	}

	// ===== Cognition commune (Mind / SemanticMemory / Character) — L1 =====
	// Hissée de Mouton vers Agent pour que Loup et Humain disposent du même
	// socle cognitif (cf. docs/evolution.txt § 5-7). Les espèces câblent
	// l'entraînement dans leur postTick via trainMindAndCharacter() et
	// surchargent isIsolated()/satisfaction() selon leur grégarité.

	/** Mémoire sémantique (§ 5) : zones connues (eau, chasse, danger, lieu sûr).
	 *  Capacité réglée par l'intelligence et l'âge via refreshMemoryCapacity(). */
	public final agents.ai.SemanticMemory memory = new agents.ai.SemanticMemory();

	/** Intelligence dynamique (§ 6) : score 0..1 démarrant depuis l'axe
	 *  Intelligence, entraîné par l'activité, dégénérant avec l'âge. */
	public agents.ai.Mind mind = new agents.ai.Mind(agents.ai.Mind.BASE_SCORE);

	/** Caractère social ÉMERGENT (§ 7) : développé par session selon le vécu.
	 *  Aucun à la naissance. */
	public final agents.ai.Character character = new agents.ai.Character();

	/** Durée d'une session de développement du caractère, en jours-jeu (§ 7.1). */
	protected static final double CHARACTER_SESSION_DAYS = 2.0;

	/** (Ré)initialise l'esprit depuis le génome courant (à appeler après avoir
	 *  fixé le génome — fondateur au spawn, petit à la naissance). */
	public void initMind() {
		mind = agents.ai.Mind.fromGenome(genome);
	}

	// ----- Économie métabolique / faim variable (L8) -----
	/** Reliquat fractionnaire de coût métabolique reporté d'un tick à l'autre
	 *  (l'énergie est entière, le coût est continu). */
	protected double metabolicDebt = 0.0;

	/** Facteur de dépense énergétique selon l'ACTIVITÉ du tick (L8) : se reposer
	 *  coûte moins (0.5), errer un peu moins (0.8), sprinter/fuir/chasser coûte
	 *  plus (1.3). Sert à moduler le coût métabolique de base. NB : l'ÉQUILIBRE
	 *  Lotka-Volterra qui en résulte se règle/observe EN SIMULATION (graphe), pas
	 *  en test unitaire — cf. plan L8. */
	public double activityEnergyFactor() {
		switch (currentState) {
			case REST:           return 0.5;
			case WANDER:         return 0.8;
			case HUNT:
			case FLEE_PREDATOR:
			case FLEE_LAVA:
			case ON_FIRE:        return 1.3;
			default:             return 1.0;
		}
	}

	/** Coût métabolique ENTIER à retrancher ce tick pour une dépense de base
	 *  {@code base}, modulé par l'activité (L8). Le reliquat fractionnaire est
	 *  reporté via {@link #metabolicDebt} pour conserver la dépense moyenne. */
	protected int metabolicCost(double base) {
		metabolicDebt += base * activityEnergyFactor();
		int whole = (int) Math.floor(metabolicDebt);
		metabolicDebt -= whole;
		return whole;
	}

	/** Niveau d'activité cognitive du tick (§ 6.2) : 1.0 pour les états de
	 *  survie/décision (qui entraînent l'esprit), 0.0 pour le repos et l'errance. */
	public double activityLevel() {
		switch (currentState) {
			case REST:
			case WANDER:
				return 0.0;
			default:
				return 1.0;
		}
	}

	/** Recale la capacité de la mémoire sur le génome et l'âge courants (§ 5.1),
	 *  modulée par l'aptitude mentale dynamique (§ 6 : un esprit vif gère plus de
	 *  souvenirs, un esprit dégénéré en perd). */
	public void refreshMemoryCapacity() {
		int base = agents.ai.SemanticMemory.capacityFor(genome, getAgeDays(), maxAgeDays);
		memory.setCapacity((int) Math.round(base * mind.learningRate()));
	}

	/** true si aucun congénère n'est à portée (§ 7.3). Défaut : jamais isolé —
	 *  les espèces grégaires (Mouton, meute de Loups) surchargent. */
	public boolean isIsolated() { return false; }

	/** Satisfaction globale ∈ [0, 1] (§ 7.3). Défaut neutre fondé sur la survie
	 *  immédiate (feu = 0) ; les espèces affinent (faim, social…). */
	public double satisfaction() { return isOnFire() ? 0.0 : 1.0; }

	/** Entraîne l'esprit (activité ↑, âge ↓) et fait émerger le caractère par
	 *  session (§ 6-7). Appelé depuis le postTick des espèces qui ont activé la
	 *  cognition. Centralisé ici pour éviter la duplication Mouton/Loup. */
	protected void trainMindAndCharacter() {
		double lifespan = maxAgeDays > 0 ? maxAgeDays : agents.ai.LifeStage.REFERENCE_LIFESPAN_DAYS;
		// dt = 1 tick exprimé en jours-jeu (getAgeDays = age / (2*dureeJour)).
		// On intègre les taux PAR JOUR de Mind sur ce dt → l'évolution de
		// l'intelligence se mesure en jours, plus en ticks.
		double dtDays = 1.0 / (2.0 * Math.max(1, world.getDureeJour()));
		mind.train(activityLevel(), getAgeDays() / lifespan, genome.longevityFactor(), dtDays);
		character.observe(isIsolated(), satisfaction());
		int sessionTicks = Math.max(1, (int) (CHARACTER_SESSION_DAYS * 2 * world.getDureeJour()));
		if (world.getIteration() > 0 && world.getIteration() % sessionTicks == 0) {
			character.endSession();
		}
	}

	/** Lignes ASCII résumant les traits évolutifs pour la fiche d'agent (§ 11) :
	 *  stade & taille, traits génétiques, caractère, intelligence, mémoire.
	 *  Commun à toutes les espèces (enrichit aussi la fiche Loup/Humain — L1). */
	public java.util.List<String> evolutionSummary() {
		java.util.List<String> l = new java.util.ArrayList<>();
		l.add(String.format(java.util.Locale.US, "Stade    : %s (x%.2f)", stageLabel(), displaySize()));
		l.add("Traits   : " + genome.asciiTraits());
		l.add("Caractere: " + socialLabel());
		l.add(String.format(java.util.Locale.US, "Intel.   : %.2f", mind.score()));
		l.add("Memoire  : " + memory.size() + " lieux");
		return l;
	}

	/** Libellé ASCII du stade de vie courant (§ 10.1). */
	protected String stageLabel() {
		switch (currentStage()) {
			case BABY:     return "BEBE";
			case JUVENILE: return "JEUNE";
			case OLD:      return "VIEUX";
			default:       return "ADULTE";
		}
	}

	/** Libellé ASCII du caractère social (§ 7). */
	protected String socialLabel() {
		switch (character.social()) {
			case SOLITARY:   return "SOLITAIRE";
			case GREGARIOUS: return "GREGAIRE";
			default:         return "-";
		}
	}

	int 	_x;
	int 	_y;
	int		_z;
	public int 	_orient;
	// Direction du dernier déplacement effectif (entiers, peuvent être ±1 ou 0,
	// tore-aware). Initialisé à (0, 1) = Nord. Mis à jour dans
	// WorldOfCells.stepAgents() à chaque step où la position change. Utilisé
	// par le rendu pour orienter le triangle d'orientation au-dessus de
	// l'agent et (à terme) le modèle 3D quand on en importera.
	protected int _lastDx = 0;
	protected int _lastDy = 1;
	float 	_redValue;
	float 	_greenValue;
	float 	_blueValue;

	int _fireState=0;

	/** Mémoire comportementale partagée (errance en spirale, etc.). */
	protected agents.ai.BehaviorMemory mem = new agents.ai.BehaviorMemory();
	/** État FSM courant — par défaut WANDER. */
	protected agents.ai.AgentState currentState = agents.ai.AgentState.WANDER;

	/**
	 * Vrai si l'agent doit tenter de se déplacer ce tick. Remis à true au début
	 * de chaque tour ; un comportement peut le passer à false pour marquer une
	 * pause sur place (ex: flânerie du loup, ancien flag {@code imobil}).
	 */
	protected boolean wantsToMove = true;

	// ---- Mémoire spatiale (carte mentale anti-errance en boucle) ----
	private static final int MEM_SIZE = 8;
	private final int[] memVisitX = new int[MEM_SIZE];
	private final int[] memVisitY = new int[MEM_SIZE];
	private int memVisitCount = 0;
	private int memVisitHead = 0;

	/** Enregistre la cellule (vx,vy) dans la mémoire spatiale (buffer circulaire
	 *  des MEM_SIZE dernières positions). Appelé à chaque tour effectif. */
	protected void recordVisit(int vx, int vy) {
		memVisitX[memVisitHead] = vx;
		memVisitY[memVisitHead] = vy;
		memVisitHead = (memVisitHead + 1) % MEM_SIZE;
		if (memVisitCount < MEM_SIZE) memVisitCount++;
	}

	/** Vrai si (vx,vy) figure dans la mémoire spatiale récente. */
	public boolean hasVisitedRecently(int vx, int vy) {
		for (int k = 0; k < memVisitCount; k++) {
			if (memVisitX[k] == vx && memVisitY[k] == vy) return true;
		}
		return false;
	}

	/** Vrai si la cellule droit devant (selon _orient) a été visitée récemment
	 *  → sert à éviter de retourner sur ses pas pendant l'errance. */
	protected boolean aheadVisitedRecently() {
		int ax = (x + orientDx(_orient) + world.getWidth()) % world.getWidth();
		int ay = (y + orientDy(_orient) + world.getHeight()) % world.getHeight();
		return hasVisitedRecently(ax, ay);
	}

	/**
	 * Contrôle manuel par le joueur. Quand {@code playerControlled} est vrai,
	 * {@code step()} court-circuite decideState/applyState : l'agent prend pour
	 * cap {@code controlDir} (0=N/1=E/2=S/3=O, -1 = immobile) et avance d'une case
	 * dans ce sens. Le cap est calculé selon la vue (cardinal direct en vue de
	 * dessus, relatif à la caméra en 3D) par {@link graphics.Landscape}. Le reste
	 * (énergie, feu, manger, mort dans la lave) reste géré par postMove/postTick.
	 */
	public boolean playerControlled = false;
	/** Cap du corps à appliquer ce tour (0=N/1=E/2=S/3=O, -1 = ne pas tourner).
	 *  Découplé du regard caméra : Landscape ne le change que quand l'agent avance
	 *  ou quand l'angle regard/torse devient trop grand. */
	public int controlDir = -1;
	/** Pas de déplacement demandé ce tour (dx, dy ∈ {-1,0,1}), permettant les
	 *  diagonales (0,0 = immobile). Calculé par Landscape selon la vue/regard. */
	public int controlDx = 0, controlDy = 0;

	/**
	 * Masque le rendu de cet agent (modèle + flèche) — utilisé par Landscape en
	 * vue première personne pendant le pilotage, pour ne pas occulter la caméra
	 * placée à l'œil de l'agent.
	 */
	public boolean hiddenFP = false;

	/** Composante X (E/O) du vecteur unitaire d'une orientation cardinale. */
	protected static int orientDx(int o) { return (o == 1) ? 1 : (o == 3) ? -1 : 0; }
	/** Composante Y (N/S) du vecteur unitaire d'une orientation cardinale. */
	protected static int orientDy(int o) { return (o == 0) ? -1 : (o == 2) ? 1 : 0; }

	/** Facteur de traînée du vent pour CET agent, dans la direction où il se
	 *  déplace ({@code _orient}) et selon sa taille. >1 dos au vent, <1 face au
	 *  vent, 1 si vent nul/désactivé. Dans {@code postMove}, chaque espèce
	 *  l'applique DEUX fois : {@code vitesse *= windDragFactor()} (le vent change la
	 *  cadence/distance) ET {@code metabolicCost(1.0 / windF)} UNIQUEMENT si l'agent
	 *  a bougé ce tour (le vent fournit la propulsion → l'énergie/temps reste
	 *  invariante : dos au vent on va plus loin pour la même énergie, face au vent
	 *  moins loin pour la même énergie ; à l'arrêt le vent ne change pas l'effort).
	 *  NB : contrairement au vent, le froid ({@link World#coldSpeedFactor}) n'est PAS
	 *  découplé de l'énergie — c'est voulu : le froid est un engourdissement
	 *  INTRINSÈQUE (les muscles travaillent moins → moins d'énergie), alors que le
	 *  vent est une force EXTERNE (l'agent fournit le même effort). */
	protected double windDragFactor() {
		return world.windSpeedFactor(orientDx(_orient), orientDy(_orient), windResistance());
	}

	/** Énergie max de référence (santé = energie/energieMax). Surchargé par espèce ;
	 *  défaut = énergie courante → healthFactor 1.0 (pas de modulation pour les agents
	 *  sans max défini, ex. Humain → masse/vent inchangés). */
	public double energieMaxValue() { return Math.max(1.0, getEnergieForMass()); }
	/** Énergie courante exposée pour le calcul de masse (les sous-classes ont des champs
	 *  {@code energie} de types différents). Défaut 1.0. Surchargé. */
	protected double getEnergieForMass() { return 1.0; }

	/** Masse vivante (kg) : référence × taille/âge (displaySize) × santé. Sert au vent
	 *  ET à la carcasse. Un adulte sain de taille 1.0 ≈ baseMassKg. */
	public double bodyMassKg() {
		double health = 0.7 + 0.3 * Math.min(1.0, getEnergieForMass() / energieMaxValue());
		return baseMassKg * displaySize() * health;
	}

	/** Résistance de l'agent au vent (sans dimension, 1.0 = mouton de référence).
	 *  Physique : masse / surface frontale (un corps lourd et peu exposé résiste
	 *  mieux), normalisée par la réf mouton. La dépendance à {@code sizeFactor} est
	 *  déjà incluse dans {@link #bodyMassKg()} via {@code displaySize()} : masse ∝
	 *  taille ⇒ un gros individu résiste davantage (∝ sizeFactor, linéaire). */
	protected double windResistance() {
		return (bodyMassKg() / frontalAreaM2) / WIND_RESISTANCE_REF;
	}

	// ===== Pilotage anti-obstacle (partagé Loup/Ours, recherche & errance) =====
	// Évite que l'agent s'entête à foncer dans un mur d'arbres, un congénère ou
	// l'eau : un comportement (spirale, errance…) propose un cap dans `_orient`,
	// ce pilotage le VALIDE et le détourne au besoin. Hissé ici pour être partagé.

	/**
	 * Avance l'état de la SPIRALE CARRÉE extensible (Loup / Ours) : bras de
	 * longueurs {@code L, L, 2L, 2L, 3L, 3L…} avec {@code L = 2·vision+1} (deux
	 * passes parallèles espacées du diamètre de vision → couverture sans trou ni
	 * recouvrement). Ne touche QUE {@code mem.spiralHeading} (le CAP VOULU) et les
	 * compteurs de bras — PAS {@code _orient}. Le pas réel (et les détours
	 * d'obstacle) sont décidés par {@link #followSpiralHeading}, qui conserve ce
	 * cap à travers les détours → l'agent ne dérive plus / ne tourne plus en rond.
	 */
	protected void spiralSearch(int vision) {
		final int L = 2 * Math.max(1, vision) + 1;
		if (mem.spiralHeading < 0) mem.spiralHeading = _orient;   // init = cap courant
		if (mem.spiralStepsLeft <= 0) {                 // bras terminé → on tourne le CAP
			mem.spiralHeading = (mem.spiralHeading + 1) % 4;
			int n = mem.spiralLegCount / 2 + 1;         // 1,1,2,2,3,3… (longueur ×L)
			mem.spiralStepsLeft = n * L;
			mem.spiralLegCount++;
		}
		mem.spiralStepsLeft--;
	}

	/**
	 * Biais mémoire de la recherche : si la spirale est FRAÎCHE (cap non initialisé,
	 * {@code spiralHeading < 0} — typiquement au sortir d'une chasse, cf. Loup HUNT
	 * qui marque {@code spiralHeading = -1}), oriente sa <b>première branche pleine</b>
	 * vers la dernière position connue de la proie ({@link #lastPreyX}/{@link #lastPreyY}),
	 * à défaut vers la zone de chasse mémorisée la plus proche. Le prédateur balaie
	 * ainsi d'abord le secteur le plus prometteur au lieu de repartir sur un cap
	 * périmé ; les branches suivantes grandissent normalement (ratissage systématique
	 * préservé). Mesuré : −35 % (terrain ouvert) à −60 % (forêt) de pas avant détection.
	 * No-op si la spirale est déjà en cours ou si aucune mémoire n'est disponible.
	 */
	protected void seedSpiralTowardMemory(int vision) {
		if (mem.spiralHeading >= 0) return;            // spirale déjà en cours → ne pas perturber
		int tx = lastPreyX, ty = lastPreyY;            // priorité : dernière proie aperçue
		if (tx < 0) {                                  // sinon : zone de chasse mémorisée la plus proche
			int[] zone = memory.nearest(agents.ai.MemoryKind.HUNTING, x, y,
					(a, b, c, d) -> world.distance(a, b, c, d));
			if (zone != null) { tx = zone[0]; ty = zone[1]; }
		}
		if (tx < 0) return;                            // aucune mémoire → spirale par défaut
		int dir = agents.ai.Perception.dirToCell(this, world, tx, ty);
		if (dir < 0) return;
		mem.spiralHeading   = dir;
		mem.spiralStepsLeft = 2 * Math.max(1, vision) + 1;   // 1re branche PLEINE vers la mémoire
		mem.spiralLegCount  = 0;                              // branches suivantes : croissance normale
	}

	/**
	 * Décide le pas de la recherche : on suit simplement le <b>CAP VOULU</b> de la
	 * spirale ({@code mem.spiralHeading}, avancé par {@link #spiralSearch}). Le
	 * contournement d'obstacle n'est PAS calculé ici : si la case devant est bloquée
	 * (arbre, lave, eau, congénère), c'est {@link agents.ai.Locomotion#move} qui fait
	 * un pas vers une case libre voisine (repli aléatoire borné). Cette approche —
	 * héritée du code d'origine — est volontairement simple et surtout <b>robuste</b> :
	 * l'agent ne se fige jamais (il bouge tant qu'une case voisine est libre) et ne
	 * tourne pas en rond indéfiniment (l'aléa du repli casse les cycles), là où les
	 * contournements « intelligents » (Pledge, plus-proche-cible) finissaient
	 * toujours par créer des blocages ou des boucles dans certaines configurations.
	 *
	 * <p>L'eau est traitée comme un obstacle pendant la spirale ({@code allowSwim}
	 * passé à {@code false} par l'appelant) : le loup longe les côtes au lieu d'y
	 * entrer (pas d'oscillation côte/eau). S'il se retrouve tout de même dans l'eau,
	 * {@code decideState} bascule en {@code SEEK_LAND}.</p>
	 */
	protected agents.ai.MoveConstraints followSpiralHeading(agents.ai.Percept p, int vision, boolean allowSwim) {
		_orient = mem.spiralHeading;
		return allowSwim ? agents.ai.MoveConstraints.amphibious()
		                 : agents.ai.MoveConstraints.landBound();
	}

	/**
	 * Valide / détourne {@code _orient} contre les obstacles et renvoie les
	 * contraintes du déplacement. Stratégie anti-entêtement :
	 * <ul>
	 *   <li>cap dégagé (et qui ne ramène pas sur ses pas) → on le garde ;</li>
	 *   <li>sinon on essaie, DANS L'ORDRE, tout droit → <b>décalage latéral</b>
	 *       (droite/gauche) → demi-tour, en <b>préférant une case non visitée
	 *       récemment</b> ; le demi-tour n'est choisi qu'en dernier recours ;</li>
	 *   <li>l'eau n'est franchie que si {@code allowSwim} ET une côte est en vue le
	 *       long du cap (sinon elle compte comme un obstacle).</li>
	 * </ul>
	 * Le décalage latéral + l'évitement des cases visitées suppriment les
	 * allers-retours entre deux arbres. Sur une impasse (seule issue = demi-tour),
	 * on rallonge le bras de spirale pour s'engager franchement dans la sortie
	 * sans rétrécir la spirale ({@code spiralLegCount} conservé).
	 *
	 * @param allowSwim autoriser la traversée d'un bras d'eau si une côte est en vue
	 *                  (true pour la chasse/recherche ; false pour l'errance repue).
	 * @param vision    portée de vision de l'agent (champ par espèce).
	 */
	protected agents.ai.MoveConstraints steerAroundObstacles(agents.ai.Percept p, boolean allowSwim, int vision) {
		final int desired = _orient;
		final int right = (desired + 1) % 4, left = (desired + 3) % 4, back = (desired + 2) % 4;
		// Ordre de préférence : tout droit, puis décalage latéral, puis demi-tour.
		int[] order = { desired, right, left, back };

		int chosen = -1, chosenKind = -1;
		int firstPassable = -1, firstPassableKind = -1;
		for (int d : order) {
			int k = headingKind(d, p, allowSwim, vision);
			if (k < 0) continue;                         // bloqué
			if (firstPassable < 0) { firstPassable = d; firstPassableKind = k; }
			if (!leadsToVisited(d)) { chosen = d; chosenKind = k; break; }  // 1re issue neuve
		}
		if (chosen < 0) { chosen = firstPassable; chosenKind = firstPassableKind; }  // tout visité
		if (chosen < 0) return agents.ai.MoveConstraints.landBound();                // cerné → fallback Locomotion

		if (chosen != desired) _orient = chosen;
		return (chosenKind == 1) ? agents.ai.MoveConstraints.amphibious()
		                         : agents.ai.MoveConstraints.landBound();
	}

	/**
	 * Évitement d'obstacles pour les états DIRIGÉS (fuite, ralliement…) : garde au
	 * mieux la direction voulue ({@code _orient}) mais la dévie autour des obstacles
	 * SOLIDES — essaie voulu → latéral droit → latéral gauche → demi-tour, et prend
	 * la première direction franchissable. L'eau est franchissable selon
	 * {@code waterPassable} (true pour un amphibie ou une fuite VERS l'eau ; false
	 * pour qui la craint, ex. mouton fuyant la lave). Empêche de rebondir
	 * indéfiniment sur un arbre pendant une fuite.
	 *
	 * @return les contraintes cohérentes ({@code amphibious} si l'eau est permise,
	 *         sinon {@code landBound}).
	 */
	protected agents.ai.MoveConstraints dodgeObstacles(boolean waterPassable) {
		final int desired = _orient;
		int[] order = { desired, (desired + 1) % 4, (desired + 3) % 4, (desired + 2) % 4 };
		for (int d : order) {
			if (passableForMove(d, waterPassable)) { _orient = d; break; }
		}
		return waterPassable ? agents.ai.MoveConstraints.amphibious()
		                     : agents.ai.MoveConstraints.landBound();
	}

	/** Case adjacente franchissable pour un déplacement dirigé : jamais forêt/lave
	 *  ni congénère ; l'eau dépend de {@code waterPassable}. */
	private boolean passableForMove(int dir, boolean waterPassable) {
		int w = world.getWidth(), h = world.getHeight();
		int tx = ((x + orientDx(dir)) % w + w) % w;
		int ty = ((y + orientDy(dir)) % h + h) % h;
		if (world.getForestCAValue(tx, ty) != 0) return false;
		if (world.getLavaCAValue(tx, ty)   != 0) return false;
		if (world.cellBlockedByAgent(tx, ty, this)) return false;
		if (!waterPassable && world.getCellHeight(tx, ty) < 0) return false;
		return true;
	}

	/** Vrai si la case adjacente dans la direction {@code orient} a été visitée
	 *  récemment (mémoire spatiale {@link #hasVisitedRecently}). */
	private boolean leadsToVisited(int orient) {
		int w = world.getWidth(), h = world.getHeight();
		int tx = ((x + orientDx(orient)) % w + w) % w;
		int ty = ((y + orientDy(orient)) % h + h) % h;
		return hasVisitedRecently(tx, ty);
	}

	/**
	 * Évalue le cap {@code orient} : {@code 0} = terre praticable (ni forêt/lave,
	 * ni agent, ni eau) ; {@code 1} = eau franchissable (côte en vue, si
	 * {@code allowSwim}) ; {@code -1} = bloqué.
	 */
	protected int headingKind(int orient, agents.ai.Percept p, boolean allowSwim, int vision) {
		if (!p.cardinalFree[orient]) return -1;                  // forêt ou lave devant
		int w = world.getWidth(), h = world.getHeight();
		int tx = ((x + orientDx(orient)) % w + w) % w;
		int ty = ((y + orientDy(orient)) % h + h) % h;
		if (world.cellBlockedByAgent(tx, ty, this)) return -1;   // congénère devant
		if (world.getCellHeight(tx, ty) >= 0) return 0;          // terre ferme
		return (allowSwim && waterCrossable(orient, vision)) ? 1 : -1;
	}

	/**
	 * Vrai si, tout droit selon {@code orient}, une terre ferme (côte) apparaît
	 * dans la portée de {@code vision} après l'eau. Borne la nage à un bras d'eau,
	 * jamais vers le large.
	 */
	protected boolean waterCrossable(int orient, int vision) {
		int w = world.getWidth(), h = world.getHeight();
		int dx = orientDx(orient), dy = orientDy(orient);
		for (int r = 1; r <= vision; r++) {
			int cx = ((x + dx * r) % w + w) % w;
			int cy = ((y + dy * r) % h + h) % h;
			if (world.getCellHeight(cx, cy) >= 0) return true;   // côte atteinte
		}
		return false;                                            // que de l'eau
	}

	// ===== Poursuite : persistance de piste + interception (partagé prédateurs) =====
	// La proie VISIBLE reste toujours prioritaire (opportunisme, cf. design) ; ces
	// outils ne servent que QUAND on l'a perdue de vue (persistance) ou pour
	// anticiper sa trajectoire (interception, désactivée pour l'instant).

	/** Interception (lead pursuit) : viser là où la proie VA, pas où elle est.
	 *  DÉSACTIVÉE par défaut — quasi sans gain en mouvement cardinal (re-quantifié),
	 *  gardée pour une future activation (diagonales) ou d'autres prédateurs.
	 *  Non-{@code final} volontairement : interrupteur (re)activable à l'exécution. */
	protected static boolean LEAD_PURSUIT = false;
	/** Anticipation (cases) appliquée à la vitesse estimée de la proie si activée. */
	protected static final int LEAD_FACTOR = 3;
	/** Persistance : nb de pas où le prédateur continue de foncer vers la dernière
	 *  position connue d'une proie sortie de vue, avant d'abandonner (→ recherche). */
	protected static final int PURSUIT_TRACK_TTL = 10;

	/** Dernière position connue de la proie poursuivie (-1 si aucune). */
	protected int lastPreyX = -1, lastPreyY = -1;
	/** Ticks de persistance restants (>0 ⇒ piste fraîche). */
	protected int pursuitTrackTtl = 0;

	/** Détection de blocage pour un déplacement DIRIGÉ vers une case (chasse vers une
	 *  proie OU fuite ON_FIRE vers l'eau) : meilleure distance à la cible atteinte
	 *  (record) et nb de ticks consécutifs sans battre ce record. Dans une poche en U
	 *  dont l'issue sort de la vision, le record plafonne → on bascule en évasion. */
	protected double huntBestDist = Double.MAX_VALUE;
	protected int huntStuck = 0;
	/** Dernière case visée (pour réinitialiser le record quand le BUT change : passer
	 *  d'une proie à une case d'eau, ou changer de proie). Une proie MOBILE ne bouge
	 *  que de ~1 case/tick (< seuil) → le suivi n'est pas réinitialisé à tort. */
	protected int lastAimX = -1, lastAimY = -1;
	/** Saut de cible (cases) au-delà duquel on considère un CHANGEMENT DE BUT → reset.
	 *  NB : suppose LEAD_PURSUIT=false. Si l'interception est réactivée, la cible
	 *  anticipée peut sauter de LEAD_FACTOR×2=6 cases quand la proie inverse sa
	 *  direction → reset intempestif en pleine poursuite (acceptable mais sous-optimal). */
	private static final double AIM_JUMP_RESET = 3.0;
	/** Seuil de ticks sans rapprochement au-delà duquel l'agent se considère PIÉGÉ et
	 *  replanifie sur un horizon élargi (sans lâcher sa cible). Calibré à 6 par
	 *  expérience : le plateau naturel d'un détour LÉGITIME autour d'un petit obstacle
	 *  mesure ~5 ticks, donc 6 est le plus court seuil qui réagit aux vrais pièges
	 *  concaves SANS déclencher la replanification sur un contournement ordinaire.
	 *  Sortie d'un U ≈ 96 ticks. Non-final : ajustable. */
	protected static int HUNT_STUCK_LIMIT = 6;
	/** Rayon de planification ÉLARGI pour s'extraire d'un piège (BFS de contournement
	 *  quand bloqué) ET portée de recherche d'eau en ON_FIRE. Borné (garde-fou CPU). */
	protected static final int HUNT_ESCAPE_VISION = 30;

	// ===== Mémoire FOOD : renforcement edge-triggered (Task 2.2) =====

	/** Distance tore-aware exposée à SemanticMemory (réutilisée par reinforce/bestFood). */
	protected final agents.ai.SemanticMemory.Distance memDistance =
		(x1, y1, x2, y2) -> world.distance(x1, y1, x2, y2);

	/** Exposé pour les tests (mémoire) : la distance tore-aware. */
	public agents.ai.SemanticMemory.Distance memDistanceForTest() { return memDistance; }

	/** Rayon de fusion des souvenirs (cellules). */
	protected static int MEMORY_MERGE_RADIUS = 3;

	/** Dernière cellule de carcasse renforcée par OBSERVATION (edge-trigger). */
	private int lastFoodSeenX = -1, lastFoodSeenY = -1;

	/** Renforce la mémoire FOOD au FRONT MONTANT de perception d'une carcasse
	 *  (évite l'inflation d'usage par vue continue). Porte la masse (payoff) + la date. */
	protected void reinforceFoodSighting(agents.ai.Percept p) {
		if (!p.carcassVisible()) { lastFoodSeenX = -1; lastFoodSeenY = -1; return; }
		if (p.carcassX == lastFoodSeenX && p.carcassY == lastFoodSeenY) return;   // déjà compté cette visite
		objects.Carcass c = world.carcassAt(p.carcassX, p.carcassY);
		double mass = (c != null) ? c.mass : 0.0;
		memory.reinforce(agents.ai.MemoryKind.FOOD, p.carcassX, p.carcassY, mass,
				world.getIteration(), MEMORY_MERGE_RADIUS, memDistance);
		lastFoodSeenX = p.carcassX; lastFoodSeenY = p.carcassY;
	}

	/** Rayon d'évasion effectif : borné pour que la fenêtre BFS (2·r+1) ne dépasse pas
	 *  la plus petite dimension du monde (sinon repli torique → double-comptage). */
	protected int escapeRadius() {
		return Math.min(HUNT_ESCAPE_VISION, (Math.min(world.getWidth(), world.getHeight()) - 1) / 2);
	}

	/** Bruite une POSITION (et non un cap) selon l'aptitude d'orientation : décalage
	 *  de magnitude aléatoire dans [0, errProb·maxOffset] cases, direction aléatoire,
	 *  tore-aware. errProb=0 (bon sens) → position EXACTE. Sert au hurlement de meute :
	 *  un bon orientateur localise mieux la source du cri (avantage évolutif). */
	protected int[] noisyLocation(int bx, int by, double errProb, double maxOffset, java.util.Random rng) {
		if (errProb <= 0.0) return new int[]{bx, by};
		double mag = rng.nextDouble() * errProb * maxOffset;
		double ang = rng.nextDouble() * 2.0 * Math.PI;
		int W = world.getWidth(), H = world.getHeight();
		int nx = (((int) Math.round(bx + Math.cos(ang) * mag)) % W + W) % W;
		int ny = (((int) Math.round(by + Math.sin(ang) * mag)) % H + H) % H;
		return new int[]{nx, ny};
	}

	/** Case d'eau (altitude &lt; 0) la plus proche dans un disque de rayon {@code radius}
	 *  autour de l'agent (tore), ou {@code {-1,-1}} si aucune. Sert de cible à la fuite
	 *  ON_FIRE (rejoindre l'eau qui éteint le feu). Scrute SEULEMENT quand l'agent brûle. */
	protected int[] nearestWaterCell(int radius) {
		int W = world.getWidth(), H = world.getHeight();
		int bx = -1, by = -1; double best = Double.MAX_VALUE;
		for (int dx = -radius; dx <= radius; dx++)
			for (int dy = -radius; dy <= radius; dy++) {
				int wx = ((x + dx) % W + W) % W, wy = ((y + dy) % H + H) % H;
				double d = world.distance(x, y, wx, wy);
				if (d > radius || d >= best) continue;
				if (world.getCellHeight(wx, wy) < 0) { best = d; bx = wx; by = wy; }
			}
		return new int[]{bx, by};
	}

	/** Proie EN VUE : met à jour la piste (dernière position + recharge la
	 *  persistance) et renvoie la CASE VISÉE — la case de la proie, ou une case
	 *  anticipée si {@link #LEAD_PURSUIT} (interception). */
	protected int[] pursuitSeen(agents.ai.Percept p) {
		int aimX = p.preyX, aimY = p.preyY;
		if (LEAD_PURSUIT && lastPreyX >= 0 && pursuitTrackTtl > 0) {
			int w = world.getWidth(), h = world.getHeight();
			int vx = torusSignedDelta(lastPreyX, p.preyX, w);   // vitesse estimée de la proie
			int vy = torusSignedDelta(lastPreyY, p.preyY, h);
			aimX = ((p.preyX + vx * LEAD_FACTOR) % w + w) % w;
			aimY = ((p.preyY + vy * LEAD_FACTOR) % h + h) % h;
		}
		lastPreyX = p.preyX; lastPreyY = p.preyY;
		pursuitTrackTtl = PURSUIT_TRACK_TTL;
		return new int[]{aimX, aimY};
	}

	/** Vrai si une proie a été vue assez récemment pour valoir la peine d'être
	 *  poursuivie « à l'aveugle » vers sa dernière position connue. */
	protected boolean hasFreshTrack() { return pursuitTrackTtl > 0 && lastPreyX >= 0; }

	/** Proie HORS DE VUE : consomme un pas de persistance et renvoie la dernière
	 *  position connue (case « fantôme » vers laquelle foncer). */
	protected int[] pursuitGhost() {
		if (pursuitTrackTtl > 0) pursuitTrackTtl--;
		return new int[]{lastPreyX, lastPreyY};
	}

	/** Oublie la piste (à appeler quand le prédateur cesse de chasser). */
	protected void resetPursuit() {
		pursuitTrackTtl = 0; lastPreyX = -1; lastPreyY = -1;
		huntBestDist = Double.MAX_VALUE; huntStuck = 0; lastAimX = -1; lastAimY = -1;
	}

	/**
	 * Un pas de déplacement DIRIGÉ vers la case {@code aim}, avec garantie de sortie
	 * des pièges concaves. Utilisé pour la TRAQUE (Loup/Ours vers une proie) ET la
	 * fuite ON_FIRE (tous les agents vers la case d'eau la plus proche). Trois niveaux :
	 * <ol>
	 *   <li>pas direct vers la cible si libre ;</li>
	 *   <li>sinon BFS de contournement borné à la vision (petits obstacles) ;</li>
	 *   <li>si la distance à la cible ne s'améliore pas depuis {@link #HUNT_STUCK_LIMIT}
	 *       ticks (piège concave/U dont l'issue sort de la vision), ÉVASION : on longe
	 *       l'obstacle ({@link #steerAroundObstacles}, anti-revisite) pour s'extraire de
	 *       la poche sans abandonner la traque.</li>
	 * </ol>
	 * Met à jour {@code _orient} ; renvoie les contraintes de déplacement (amphibie).
	 */
	protected agents.ai.MoveConstraints pursuitStep(agents.ai.Percept p, int[] aim, int vision) {
		// Changement de BUT (nouvelle proie, ou proie → case d'eau en ON_FIRE) : on
		// repart d'un record neuf. Une cible MOBILE bouge de ~1 case/tick (< seuil) →
		// pas de reset à tort sur une poursuite continue.
		if (aim[0] >= 0 && (lastAimX < 0 || world.distance(aim[0], aim[1], lastAimX, lastAimY) > AIM_JUMP_RESET)) {
			huntBestDist = Double.MAX_VALUE; huntStuck = 0;
		}
		if (aim[0] >= 0) { lastAimX = aim[0]; lastAimY = aim[1]; }

		// Suivi du record de rapprochement (gate « meilleur que jamais », robuste à
		// l'oscillation — cf. homingStepToward).
		double dAim = (aim[0] >= 0) ? world.distance(x, y, aim[0], aim[1]) : Double.MAX_VALUE;
		if (dAim < huntBestDist) { huntBestDist = dAim; huntStuck = 0; }
		else huntStuck++;

		// PIÉGÉ (poche concave dont l'issue sort de la vision) : le BFS borné à la
		// vision vise le fond de la poche. On replanifie sur un horizon ÉLARGI pour
		// trouver le VRAI chemin de contournement (le 1er pas peut s'éloigner de la
		// proie — sortir par l'ouverture). Coûteux mais calculé SEULEMENT quand bloqué.
		if (huntStuck >= HUNT_STUCK_LIMIT && aim[0] >= 0) {
			// Replanif sur un horizon élargi (borné par escapeRadius() pour éviter le
			// repli torique) → trouve le vrai chemin de contournement (1er pas pouvant
			// s'éloigner de la cible pour sortir par l'ouverture).
			int bfs = bfsStepToward(aim[0], aim[1], escapeRadius(), true);
			if (bfs >= 0) { _orient = bfs; return agents.ai.MoveConstraints.amphibious(); }
			// Cible vraiment inatteignable (murée) : longe-mur en dernier recours.
			int d = agents.ai.Perception.dirToCell(this, world, aim[0], aim[1]);
			if (d >= 0) _orient = d;
			return steerAroundObstacles(p, true, vision);
		}

		// Traque normale : pas direct, sinon BFS de contournement borné à la vision.
		int dir = agents.ai.Perception.dirToCell(this, world, aim[0], aim[1]);
		if (dir < 0) dir = (p.preyDir >= 0) ? p.preyDir : _orient;   // déjà sur la case / cas limite
		if (aim[0] >= 0 && headingKind(dir, p, true, vision) < 0) {
			int bfs = bfsStepToward(aim[0], aim[1], vision, true);
			if (bfs >= 0) dir = bfs;
		}
		_orient = dir;
		return agents.ai.MoveConstraints.amphibious();
	}

	/** Delta torique signé minimal de a vers b sur un axe de taille n. */
	private int torusSignedDelta(int a, int b, int n) {
		int fwd = (b - a + n) % n, bwd = (a - b + n) % n;
		return (fwd <= bwd) ? fwd : -bwd;
	}

	/**
	 * Pas de RALLIEMENT À TERRE vers ({@code tx},{@code ty}) avec garde-fou
	 * anti-oscillation — logique PARTAGÉE par {@code Loup.huntHoming} (zone de
	 * chasse) et {@code Mouton} HOME (lieu sûr). Renvoie une direction (0-3)
	 * UNIQUEMENT si le pas fait <b>strictement mieux</b> que la meilleure distance
	 * déjà atteinte vers cette cible (suivi dans {@code mem}) ; <b>-1 sinon</b>.
	 *
	 * <p>C'est le cœur du correctif du gel en recherche (2026-06-07) et l'expression
	 * du principe « une décision de navigation qui ne progresse pas doit CÉDER la
	 * priorité, pas s'entêter » : au pied d'un obstacle qui mure la cible (eau OU
	 * forêt), aucun pas ne bat le record → on renvoie -1, et l'appelant retombe sur
	 * son comportement de repli (spirale pour le loup, errance pour le mouton) au
	 * lieu d'osciller indéfiniment. Le « record » (et non « mieux que la case
	 * courante ») est indispensable : sinon le repli repousse l'agent hors de la
	 * rangée de distance minimale et le ralliement l'y ré-aspire → oscillation.</p>
	 *
	 * <p>L'eau est un obstacle ({@code allowSwim=false}) : on ne nage pas vers un
	 * souvenir, ce qui évite aussi la bascule SEARCH↔SEEK_LAND au littoral.</p>
	 */
	protected int homingStepToward(int tx, int ty, int vision, agents.ai.Percept p) {
		double dHere = world.distance(x, y, tx, ty);
		// Nouvelle cible (ou première fois) → on initialise le record à la distance
		// courante. resetHoming() (appelé hors état de ralliement) évite un record
		// périmé qui interdirait tout ralliement futur.
		if (tx != mem.homingZoneX || ty != mem.homingZoneY) {
			mem.homingZoneX = tx;
			mem.homingZoneY = ty;
			mem.homingBestDist = dHere;
		}
		int dir = agents.ai.Perception.dirToCell(this, world, tx, ty);
		// Pas direct bloqué (eau / arbre / congénère) → contournement BFS À TERRE.
		if (dir < 0 || headingKind(dir, p, false, vision) < 0) {
			dir = bfsStepToward(tx, ty, vision, false);
		}
		if (dir < 0) return -1;                       // aucun pas terrestre → repli
		int w = world.getWidth(), h = world.getHeight();
		int nx = ((x + orientDx(dir)) % w + w) % w;
		int ny = ((y + orientDy(dir)) % h + h) % h;
		double dNext = world.distance(nx, ny, tx, ty);
		if (dNext >= mem.homingBestDist) return -1;   // pas de NOUVEAU progrès → repli
		mem.homingBestDist = dNext;
		return dir;
	}

	/**
	 * Direction de RALLIEMENT vers la zone de chasse mémorisée la plus proche
	 * ({@code MemoryKind.HUNTING}), avec le garde-fou de progrès de
	 * {@link #homingStepToward} : renvoie un cap (0-3) tant que l'agent peut se
	 * rapprocher d'une zone à terre, sinon -1 (aucune zone, déjà dessus, ou murée →
	 * l'appelant retombe sur la spirale). Partagé Loup/Ours ; l'appelant fixe
	 * l'orientation et la vitesse (trot). */
	protected int huntHomingDir(agents.ai.Percept p, int vision) {
		int[] zone = memory.nearest(agents.ai.MemoryKind.HUNTING, x, y,
				(a, b, c, d) -> world.distance(a, b, c, d));
		if (zone == null) return -1;
		if (zone[0] == x && zone[1] == y) return -1;   // déjà sur la zone → balayer
		return homingStepToward(zone[0], zone[1], vision, p);
	}

	// ===== Pathfinding local (BFS borné à la vision) pour la TRAQUE =====
	// Sert à HUNT et huntHoming : quand le pas direct vers la cible est bloqué par
	// un obstacle, on contourne. BFS sur la fenêtre (2·vision+1)² centrée sur
	// l'agent → ≤ ~441 cases, calculé SEULEMENT en cas de blocage → coût
	// négligeable. La cible peut être hors fenêtre (zone mémorisée lointaine) : on
	// vise alors la case ATTEIGNABLE la plus proche de la cible (frontière).

	/** Case franchissable pour le BFS (statique : forêt/lave/eau). On ignore les
	 *  autres agents (mobiles, transitoires) — la collision est gérée au pas. */
	private boolean bfsPassable(int wx, int wy, boolean allowSwim) {
		if (world.getForestCAValue(wx, wy) != 0) return false;
		if (world.getLavaCAValue(wx, wy)   != 0) return false;
		if (!allowSwim && world.getCellHeight(wx, wy) < 0) return false;
		return true;
	}

	/**
	 * Première direction (0=N/1=E/2=S/3=O) du plus court chemin (BFS, 4-connexe)
	 * vers ({@code targetX},{@code targetY}), borné à la fenêtre de vision. Si la
	 * cible est hors fenêtre ou inatteignable, vise la case atteignable la plus
	 * proche de la cible. Renvoie -1 si l'agent est complètement cerné.
	 *
	 * @param allowSwim l'eau est-elle franchissable dans le calcul (true en chasse).
	 */
	protected int bfsStepToward(int targetX, int targetY, int vision, boolean allowSwim) {
		final int v = Math.max(1, vision);
		final int size = 2 * v + 1;
		final int W = world.getWidth(), H = world.getHeight();
		boolean[] visited = new boolean[size * size];
		int[] firstDir = new int[size * size];
		int[] queue = new int[size * size];
		int qh = 0, qt = 0;

		final int originIdx = v * size + v;          // l'agent est au centre
		visited[originIdx] = true;
		firstDir[originIdx] = -1;
		queue[qt++] = originIdx;

		int bestIdx = -1;
		double bestDist = Double.MAX_VALUE;

		while (qh < qt) {
			int idx = queue[qh++];
			int lx = idx % size, ly = idx / size;
			int wx = ((x - v + lx) % W + W) % W;
			int wy = ((y - v + ly) % H + H) % H;
			if (idx != originIdx) {                  // candidat « le plus proche de la cible »
				double d = world.distance(wx, wy, targetX, targetY);
				if (d < bestDist) { bestDist = d; bestIdx = idx; }
				if (d == 0) break;                   // cible atteinte → chemin optimal trouvé
			}
			for (int dir = 0; dir < 4; dir++) {
				int nlx = lx + orientDx(dir), nly = ly + orientDy(dir);
				if (nlx < 0 || nlx >= size || nly < 0 || nly >= size) continue;  // hors fenêtre
				int nIdx = nly * size + nlx;
				if (visited[nIdx]) continue;
				int nwx = ((x - v + nlx) % W + W) % W;
				int nwy = ((y - v + nly) % H + H) % H;
				// Clip au DISQUE de vision (euclidien, comme Perception) : l'agent ne
				// planifie que sur le terrain qu'il perçoit réellement — pas les coins
				// du carré qui dépasseraient ~vision·√2.
				if (world.distance(x, y, nwx, nwy) > v) continue;
				if (!bfsPassable(nwx, nwy, allowSwim)) continue;
				visited[nIdx] = true;
				firstDir[nIdx] = (idx == originIdx) ? dir : firstDir[idx];
				queue[qt++] = nIdx;
			}
		}
		return bestIdx < 0 ? -1 : firstDir[bestIdx];
	}

	/**
	 * Itération du monde au moment de la naissance. Sert à calculer l'âge à
	 * la volée sans avoir à incrémenter un compteur dans chaque step() des
	 * sous-classes (Loup/Mouton/Humain).
	 */
	protected final int birthIteration;

	public Agent ( int __x , int __y, World __world )
	{
		super(__x,__y,__world);
		_x = __x;
		_y = __y;
		_world = __world;
		_orient = 0;
		birthIteration = __world.getIteration();

		_redValue =1.f;
		_greenValue = 1.f;
		_blueValue = 0.f;
	}

	/** Âge de l'agent en itérations (ticks simulation depuis sa naissance). */
	public int getAge() {
		return _world.getIteration() - birthIteration;
	}

	/**
	 * Âge en « jours » de jeu — un jour = un cycle jour+nuit complet, soit
	 * {@code 2 * dureeJour} itérations. Approximatif si {@code dureeJour} a
	 * changé en cours de vie de l'agent.
	 */
	public double getAgeDays() {
		int fullDay = 2 * _world.getDureeJour();
		if (fullDay <= 0) return 0;
		return getAge() / (double) fullDay;
	}

	/** Étiquette d'espèce pour l'UI. Les sous-classes l'overrident. */
	public String getTypeName() { return "Agent"; }

	/**
	 * Étiquette texte du comportement actuel, dérivée des champs d'état de la
	 * sous-classe. Renvoie « Errance » par défaut. Override chez Loup/Mouton.
	 */
	public String getCurrentBehavior() {
		if (_fireState == 1) return "En feu";
		return "Errance";
	}

	/** Libellé cardinal de l'orientation courante. */
	/** Libellé cardinal ORIENTÉ ÉCRAN. Les entiers _orient sont une énumération
	 *  interne (cf. orientDx/orientDy) : orient 0 = grille −Y, orient 2 = grille +Y.
	 *  Or au rendu (vue de dessus, sans rotation) grille +Y = HAUT = Nord et −Y =
	 *  BAS = Sud. Donc orient 0 (−Y) s'affiche « Sud » et orient 2 (+Y) « Nord ». */
	public String getOrientLabel() {
		switch (_orient) {
			case 0: return "Sud";    // grille −Y → bas de l'écran
			case 1: return "Est";    // grille +X → droite
			case 2: return "Nord";   // grille +Y → haut de l'écran
			case 3: return "Ouest";  // grille −X → gauche
			default: return "?";
		}
	}

	/**
	 * Enregistre le delta du dernier déplacement effectif (tore-aware).
	 * Appelée par WorldOfCells.stepAgents() après le step() de l'agent.
	 * (0, 0) → on ne touche pas à _lastDx/_lastDy pour garder la dernière
	 * direction de marche connue quand l'agent est immobile.
	 */
	/**
	 * Altitude effective à laquelle dessiner l'agent à la position monde
	 * (cellX, cellY) :
	 *  - base = hauteur du terrain × normalizeHeight
	 *  - +STONE_BLOCK_HEIGHT si la cellule a un block pierre (l'agent marche
	 *    par-dessus)
	 *  - +STONE_BLOCK_HEIGHT * AGENT_LAVA_DIVE_FRACTION si lave (l'agent
	 *    s'enfonce de moitié dans le bloc — réaliste, l'agent mourra au
	 *    prochain step de toute façon)
	 *  - clampé à -1 si l'altitude résultante est négative (= eau : l'agent
	 *    plonge légèrement sous la surface, effet voulu)
	 *
	 * Centralisé ici pour que Loup / Mouton / Humain partagent exactement la
	 * même règle de positionnement vertical.
	 */
	protected float computeAgentAltitude(World myWorld, int cellX, int cellY, float normalizeHeight) {
		// Altitude unifiée via le système Layer (cf. plan de refactor). Le
		// sommet de la pile donne l'altitude marchable ; si la couche du haut
		// est de la lave, l'agent s'enfonce à mi-hauteur dedans (il mourra
		// au prochain tick, comportement réaliste).
		float altitude = myWorld.getCellTopAltitude(cellX, cellY);
		Layer top = myWorld.topLayer(cellX, cellY);
		if (top != null && top.material == Material.LAVA) {
			altitude -= top.thickness * CommonObject.AGENT_LAVA_DIVE_FRACTION;
		}
		if (altitude < 0) altitude = -1;  // plongée légère sous l'eau (effet voulu)
		return altitude;
	}

	public void setLastMove(int dx, int dy) {
		// Sous pilotage manuel, le cap (et donc la flèche) est dicté par _orient
		// dans step(), pas par le delta de position — on ignore l'appel du monde.
		if (playerControlled) return;
		if (dx == 0 && dy == 0) return;
		_lastDx = dx;
		_lastDy = dy;
	}

	public int getLastDx() { return _lastDx; }
	public int getLastDy() { return _lastDy; }

	/** Met l'agent en feu. Le comportement de fuite vers l'eau et la perte
	 *  d'énergie sont gérés par les sous-classes (Loup, Mouton). Humain n'a
	 *  pas de logique feu — pour Humain, préférer un kill direct. */
	public void setOnFire() { _fireState = 1; }

	public boolean isOnFire() { return _fireState == 1; }

	/** true si l'agent est en train de manger (tête baissée → vigilance réduite). Surchargé. */
	public boolean isFeeding() { return false; }

	/** Période (en ticks) équivalant à ~1 seconde-jeu, pour les drains « horaires »
	 *  comme le feu. Suit simulationHz pour rester cohérente quel que soit le Hz de
	 *  simulation (sinon un `% 20` codé en dur change la létalité du feu quand on
	 *  règle le Hz). Fallback 20 si la config n'est pas disponible (tests). */
	protected int ticksPerGameSecond() {
		if (world instanceof worlds.WorldOfCells) {
			ui.SimulationConfig c = ((worlds.WorldOfCells) world).config;
			if (c != null) return Math.max(1, c.simulationHz);
		}
		return 20;
	}

	/** Hz de simulation (pour convertir sec → ticks). Délègue à ticksPerGameSecond()
	 *  (même accès config.simulationHz, même fallback 20). */
	protected double simulationHz() { return ticksPerGameSecond(); }

	// ===== Festin (EAT) : bouchées cadencées sur une carcasse (Task 5) =====
	/** Délai (sec réelles) entre deux bouchées. Hz-invariant via simulationHz(). */
	protected static final double EAT_BITE_SEC = 0.5;
	/** Masse (kg) d'une bouchée de base (0 = non-carnivore). Surchargé au ctor de l'espèce carnivore. */
	public double biteBaseKg = 0.0;
	/** Ticks restants avant la prochaine bouchée (0 = prêt). */
	protected int eatBiteCooldown = 0;

	/** Hook d'écriture d'énergie depuis eatStep. Surchargé par les espèces carnivores
	 *  (Loup, Ours) pour faire {@code energie += delta}. No-op par défaut (Mouton, etc.). */
	protected void gainEnergie(int delta) {}

	/** Fixe la vitesse à "pas" (vpas) avant de manger, pour stabiliser isMyTurn()
	 *  pendant le festin (empêche vitesse de dériver sous les facteurs terrain).
	 *  Surchargé par les espèces carnivores (Loup, Ours) pour faire {@code vitesse = vpas}. */
	protected void applyEatSpeed() {}

	/** Une étape de festin : si une carcasse est sur la cellule courante ou une
	 *  case adjacente (9-voisinage) ET que le cooldown est écoulé, prend une bouchée.
	 *  Reste sur place (wantsToMove=false). Renvoie les contraintes de déplacement. */
	protected agents.ai.MoveConstraints eatStep(agents.ai.Percept p) {
		applyEatSpeed();   // stabilise vitesse avant postMove (empêche la dérive vers 0)
		wantsToMove = false;
		if (!p.carcassVisible()) return agents.ai.MoveConstraints.landBound();
		objects.Carcass c = world.carcassAt(p.carcassX, p.carcassY);
		if (c == null) return agents.ai.MoveConstraints.landBound();
		// S'orienter vers la carcasse si on n'y est pas déjà dessus.
		int dir = agents.ai.Perception.dirToCell(this, world, c.getX(), c.getY());
		if (dir >= 0 && _orient != dir) { _orient = dir; return agents.ai.MoveConstraints.landBound(); }
		// Prend une bouchée quand le cooldown est écoulé.
		if (eatBiteCooldown == 0) {
			double bite = biteBaseKg * Math.max(0.2, displaySize()); // plancher 0.2 : garde une bouchée non nulle même si la taille devient minuscule
			double taken = c.eat(bite);
			gainEnergie((int) Math.round(taken * objects.Carcass.ENERGY_PER_KG));
			eatBiteCooldown = Math.max(1, (int) Math.round(EAT_BITE_SEC * simulationHz()));
			if (c.isGone()) world.carcasses.remove(c);
		}
		return agents.ai.MoveConstraints.landBound();
	}

	/** Se dirige vers la carcasse perçue la plus proche (pursuitStep, fiable contre les
	 *  pièges concaves). Renvoie la contrainte de mouvement, ou null si aucune carcasse. */
	protected agents.ai.MoveConstraints seekCarcassStep(agents.ai.Percept p, int visionRange) {
		if (!p.carcassVisible()) return null;
		return pursuitStep(p, new int[]{p.carcassX, p.carcassY}, visionRange);
	}

	/** Carcasse à portée de bouchée : sur l'une des 9 cases (cellule + 8 voisines →
	 *  distance euclidienne torique ≤ 1.5, diagonale = √2 ≈ 1.41). Partagé Loup/Ours. */
	protected boolean carcassAdjacente(agents.ai.Percept p) {
		return p.carcassVisible() && world.distance(x, y, p.carcassX, p.carcassY) <= 1.5;
	}

	/**
	 * Vecteur unitaire (udx, udy) de la dernière direction de déplacement.
	 * Renvoie (0, 1) = Nord si l'agent n'a pas encore bougé (lastDx/Dy
	 * jamais set).
	 */
	public double[] getLastUnit() {
		double mag = Math.sqrt(_lastDx * _lastDx + _lastDy * _lastDy);
		if (mag == 0) return new double[] { 0.0, 1.0 };
		return new double[] { _lastDx / mag, _lastDy / mag };
	}

	/**
	 * Émet (dans un glBegin(GL_QUADS) déjà ouvert) un triangle isocèle pointant
	 * dans la direction du dernier déplacement, encodé comme un quad dégénéré
	 * (4 vertices dont deux confondus). Cela permet aux agents de garder un
	 * indicateur visuel d'orientation tout en restant batchés dans le big
	 * glBegin du Landscape.
	 *
	 * cx, cy : centre de la cellule de l'agent en coords monde.
	 * lenX, lenY : demi-pas de cellule (lenX == lenY en pratique).
	 * z : altitude monde du sommet.
	 */
	public void emitOrientationTriangle(GL2 gl, float cx, float cy,
			float lenX, float lenY, float z) {
		double[] u = getLastUnit();
		double udx = u[0];
		double udy = u[1];
		float apexX = (float) (cx + udx * lenX);
		float apexY = (float) (cy + udy * lenY);
		float baseX = (float) (cx - udx * lenX);
		float baseY = (float) (cy - udy * lenY);
		// perp_right de (udx, udy) = (udy, -udx). On reporte une demi-largeur
		// de chaque côté → triangle isocèle large d'une cellule à la base.
		float blX = (float) (baseX - udy * lenX);
		float blY = (float) (baseY + udx * lenY);
		float brX = (float) (baseX + udy * lenX);
		float brY = (float) (baseY - udx * lenY);
		gl.glVertex3f(apexX, apexY, z);
		gl.glVertex3f(blX,   blY,   z);
		gl.glVertex3f(apexX, apexY, z);
		gl.glVertex3f(brX,   brY,   z);
	}

	//abstract public void step( );

	/**
	 * Template Method : flux canonique d'un step d'agent. Les différences
	 * d'espèce sont déléguées aux hooks ci-dessous (overridés par
	 * Loup/Mouton/Humain). Ne pas overrider step() dans les sous-classes —
	 * overrider les hooks.
	 */
	@Override
	public void step() {
		if (preStepAbort()) return;     // mort (âge / plus en vie) → on n'avance pas
		updateDayNight();               // ex: loup calcule attaqueNuit (no-op par défaut)
		if (isMyTurn()) {               // gating vitesse par espèce
			resetTickFlags();           // reset flags + lastX/lastY
			wantsToMove = true;         // défaut : on bouge (un comportement peut l'annuler)
			agents.ai.Percept p = agents.ai.Perception.sense(this, world, predators(), prey());
			if (playerControlled) {
				// Pilotage joueur : on remplace la décision ET le choix de
				// déplacement par l'input clavier (cap calculé selon la vue par
				// Landscape). L'environnement (postMove/postTick) s'applique quand
				// même — l'agent peut mourir dans la lave, prendre feu, manger.
				currentState = agents.ai.AgentState.CONTROLLED;
				applyControlSpeed();                 // cadence réactive (vcourse)
				if (controlDir >= 0) _orient = controlDir;        // tourne le corps (visuel)
				if ((controlDx != 0 || controlDy != 0) && canMove())  // déplacement (diagonales OK)
					agents.ai.Locomotion.moveBy(this, world, controlDx, controlDy,
							agents.ai.MoveConstraints.playerControlled());
				// La flèche d'orientation suit le cap, même à l'arrêt.
				_lastDx = orientDx(_orient);
				_lastDy = orientDy(_orient);
			} else {
				currentState = decideState(p);
				agents.ai.MoveConstraints c = applyState(currentState, p);
				if (canMove() && wantsToMove) agents.ai.Locomotion.move(this, world, _orient, c);
			}
			postMove(p);                // blocs post-mouvement spécifiques (manger, feu, énergie, repro…)
			recordVisit(x, y);          // mémoire spatiale : trace la cellule occupée ce tour
		}
		postTick();                     // tourne CHAQUE tick (ex: drain de feu)
	}

	// ===== Hooks du Template Method (overridés par les sous-classes) =====
	protected boolean preStepAbort() { return false; }
	protected void updateDayNight() {}
	protected boolean isMyTurn() { return true; }
	protected void resetTickFlags() {}
	protected java.util.List<? extends objects.UniqueDynamicObject> predators() { return null; }
	protected java.util.List<? extends objects.UniqueDynamicObject> prey() { return null; }
	protected agents.ai.AgentState decideState(agents.ai.Percept p) { return agents.ai.AgentState.WANDER; }
	protected agents.ai.MoveConstraints applyState(agents.ai.AgentState s, agents.ai.Percept p) { return agents.ai.MoveConstraints.landBound(); }
	protected boolean canMove() { return true; }
	protected void postMove(agents.ai.Percept p) {}
	protected void postTick() {}
	/** Fixe la vitesse de l'agent quand il est piloté manuellement, pour une
	 *  cadence de déplacement réactive. Overridé par les espèces (→ vcourse). */
	protected void applyControlSpeed() {}

    public void displayUniqueObject(World myWorld, GL2 gl, int offsetCA_x, int offsetCA_y, float offset, float stepX, float stepY, float lenX, float lenY, float normalizeHeight)
    {
        if (hiddenFP) return;   // 1ère personne : ne pas dessiner l'agent piloté

        // display a monolith
        
        //gl.glColor3f(0.f+(float)(0.5*Math.random()),0.f+(float)(0.5*Math.random()),0.f+(float)(0.5*Math.random()));
        
    	int x2 = (x-(offsetCA_x%myWorld.getWidth()));
    	if ( x2 < 0) x2+=myWorld.getWidth();
    	int y2 = (y-(offsetCA_y%myWorld.getHeight()));
    	if ( y2 < 0) y2+=myWorld.getHeight();

    	// Altitude unifiée (sol + stone + lave + clamp eau) — même règle que
    	// Loup/Mouton via Agent.computeAgentAltitude.
    	float altitude = computeAgentAltitude(myWorld, x, y, normalizeHeight);

        gl.glColor3f(1.f,1.f,1.f);
        gl.glVertex3f( offset+x2*stepX-lenX, offset+y2*stepY-lenY, altitude);
        gl.glVertex3f( offset+x2*stepX-lenX, offset+y2*stepY-lenY, altitude + 4.f);
        gl.glVertex3f( offset+x2*stepX+lenX, offset+y2*stepY-lenY, altitude + 4.f);
        gl.glVertex3f( offset+x2*stepX+lenX, offset+y2*stepY-lenY, altitude);

        gl.glColor3f(1.f,1.f,1.f);
        gl.glVertex3f( offset+x2*stepX+lenX, offset+y2*stepY+lenY, altitude);
        gl.glVertex3f( offset+x2*stepX+lenX, offset+y2*stepY+lenY, altitude + 4.f);
        gl.glVertex3f( offset+x2*stepX-lenX, offset+y2*stepY+lenY, altitude + 4.f);
        gl.glVertex3f( offset+x2*stepX-lenX, offset+y2*stepY+lenY, altitude);

        gl.glColor3f(1.f,1.f,1.f);
        gl.glVertex3f( offset+x2*stepX+lenX, offset+y2*stepY-lenY, altitude);
        gl.glVertex3f( offset+x2*stepX+lenX, offset+y2*stepY-lenY, altitude + 4.f);
        gl.glVertex3f( offset+x2*stepX+lenX, offset+y2*stepY+lenY, altitude + 4.f);
        gl.glVertex3f( offset+x2*stepX+lenX, offset+y2*stepY+lenY, altitude);

        gl.glColor3f(1.f,1.f,1.f);
        gl.glVertex3f( offset+x2*stepX-lenX, offset+y2*stepY+lenY, altitude);
        gl.glVertex3f( offset+x2*stepX-lenX, offset+y2*stepY+lenY, altitude + 4.f);
        gl.glVertex3f( offset+x2*stepX-lenX, offset+y2*stepY-lenY, altitude + 4.f);
        gl.glVertex3f( offset+x2*stepX-lenX, offset+y2*stepY-lenY, altitude);

        gl.glColor3f(0.5f,0.5f,0.5f);
        gl.glVertex3f( offset+x2*stepX-lenX, offset+y2*stepY-lenY, altitude + 4.f);
        gl.glVertex3f( offset+x2*stepX-lenX, offset+y2*stepY+lenY, altitude + 4.f);
        gl.glVertex3f( offset+x2*stepX+lenX, offset+y2*stepY+lenY, altitude + 4.f);
        gl.glVertex3f( offset+x2*stepX+lenX, offset+y2*stepY-lenY, altitude + 4.f);

        // Triangle d'orientation au sommet : pointe vers la direction du
        // dernier déplacement. Couleur héritée de la sous-classe (humain =
        // vert) ; cyan vif quand l'agent est piloté manuellement (le jaune
        // est réservé au feu, cf. _fireState).
        if (playerControlled) gl.glColor3f(0.1f, 0.9f, 1f);
        else gl.glColor3f(_redValue,_greenValue,_blueValue);
        emitOrientationTriangle(gl,
                offset + x2 * stepX, offset + y2 * stepY,
                lenX, lenY,
                altitude + 5.f);
    }
}
