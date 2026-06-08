package agents;

import worlds.World;

public class Humain extends Agent {

	public int energieD=400;//20
	public int energie=energieD;

	public int vision=10;

	public double vitesse=7;// m/s MAX:28
	public double vcourse=13.5;
	public double vmarche=8;
	public double vpas=3;

	public int m=0;//1 si a manger ce tour

	/** L3 — mode CHASSEUR : au lieu de seulement garder le troupeau, l'Humain
	 *  pourchasse activement le Loup le plus proche en vue et le tue au contact.
	 *  false = berger classique (défaut). Réglable via SimulationConfig.humainChasseur. */
	public boolean chasseur = false;

	public Humain( int __x, int __y,World __world)
	{
		super(__x,__y,__world);
		_alive = true;

		_redValue = 0.f;
		_greenValue = 1.f;
		_blueValue = 0.f;

		massKg = 70.0; frontalAreaM2 = 0.65;   // debout, grande voilure → le plus sensible au vent
	}

	/** Accesseur public pour l'UI. */
	public int getEnergie() { return energie; }
	public int getEnergieMax() { return energieD; }
	public boolean isAlive() { return _alive; }

	@Override public String getTypeName() { return "Humain"; }

	@Override public String getCurrentBehavior() {
		if (playerControlled) return "Piloté";
		if (_fireState == 1)  return "En feu";
		if (currentState == agents.ai.AgentState.FLEE_LAVA) return "Fuit lave";
		if (currentState == agents.ai.AgentState.HUNT) return "Chasse loup";
		if (currentState == agents.ai.AgentState.HOME) return "Rentre au foyer";
		return currentState == agents.ai.AgentState.HERD ? "Garde le troupeau" : "Errance";
	}

	// ===== Hooks du Template Method (Agent.step) =====

	@Override
	protected boolean preStepAbort() { return !_alive; }

	@Override
	protected boolean isMyTurn() {
		// Math.max(1, …) : garde-fou anti division par zero (diviseur 0 si vitesse > 28).
		return world.getIteration() % Math.max(1, (int)((1.0 / vitesse) * 28)) == 0;
	}

	@Override
	protected void applyControlSpeed() { vitesse = vcourse; }

	/** Le berger « perçoit » le troupeau : les moutons alimentent preyDir/preyVisible
	 *  du Percept (cf. Agent.step → Perception.sense). */
	@Override
	protected java.util.List<? extends objects.UniqueDynamicObject> prey() {
		return world.moutons;
	}

	/** L3 — l'Humain localise les loups via le slot « predator » du Percept
	 *  (`predatorDir`). Il ne les FUIT pas (aucun FLEE_PREDATOR dans sa FSM) : en
	 *  mode chasseur il s'en sert pour les POURCHASSER. */
	@Override
	protected java.util.List<? extends objects.UniqueDynamicObject> predators() {
		return world.loups;
	}

	@Override
	protected agents.ai.AgentState decideState(agents.ai.Percept p) {
		if (isOnFire())          return agents.ai.AgentState.ON_FIRE;
		if (p.lavaVisible())     return agents.ai.AgentState.FLEE_LAVA;  // L2 — la lave tue au contact
		// L3 — chasseur : pourchasse le loup le plus proche, même la nuit (il
		// protège le troupeau des attaques nocturnes).
		if (chasseur && p.predatorVisible()) return agents.ai.AgentState.HUNT;
		if (world.getJour() == 0) return agents.ai.AgentState.HOME;  // la nuit, rentre au foyer
		if (p.preyVisible())     return agents.ai.AgentState.HERD;   // berger : rejoint le troupeau
		return agents.ai.AgentState.WANDER;
	}

	@Override
	protected agents.ai.MoveConstraints applyState(agents.ai.AgentState s, agents.ai.Percept p) {
		// Vitesse re-dérivée CHAQUE tick selon l'état (comme Loup/Mouton/Ours) : sprint
		// en panique/chasse, marche en garde/retour, pas en errance. Indispensable pour
		// que la modulation par le vent (postMove) ne se cumule pas tick après tick.
		vitesse = vpas;
		if (s == agents.ai.AgentState.ON_FIRE) {
			vitesse = vcourse;              // panique : sprint
			// Rejoint la case d'EAU la plus proche (éteint le feu) avec sortie garantie
			// des pièges concaves (pursuitStep). Fallback steer si aucune eau à portée.
			int[] water = nearestWaterCell(escapeRadius());
			if (water[0] >= 0) return pursuitStep(p, water, vision);
			if (p.waterDir >= 0) _orient = p.waterDir;
			return steerAroundObstacles(p, true, vision);
		}
		if (s == agents.ai.AgentState.FLEE_LAVA) {
			// L2 — fuit à l'opposé de la lave la plus proche, à terre (eau interdite).
			vitesse = vcourse;              // panique : sprint
			if (p.lavaDir >= 0) _orient = agents.ai.AgentState.opposite(p.lavaDir);
			return steerAroundObstacles(p, false, vision);  // contourne arbres/lave (anti-revisite), reste à terre
		}
		if (s == agents.ai.AgentState.HUNT) {
			// L3 — fonce VERS le loup le plus proche (cap = predatorDir, pas son
			// opposé), au sprint. Le loup le fuyant, l'Humain doit courir.
			if (p.predatorDir >= 0) _orient = p.predatorDir;
			vitesse = vcourse;
			return agents.ai.MoveConstraints.landBound();
		}
		if (s == agents.ai.AgentState.HOME) {
			// La nuit, l'Humain rallie le foyer (bergerie) d'un pas soutenu.
			vitesse = vmarche;
			worlds.WorldOfCells wc = (worlds.WorldOfCells) world;
			int dir = agents.ai.Perception.dirToCell(this, world, wc.getBergerieX(), wc.getBergerieY());
			if (dir >= 0) _orient = dir;
			return agents.ai.MoveConstraints.landBound();
		}
		if (s == agents.ai.AgentState.HERD) {
			// Suit le CENTRE DE MASSE du troupeau visible (pas la bête la plus
			// proche) — un berger conduit le gros du troupeau, pas une traînarde.
			vitesse = vmarche;
			int dir = agents.ai.Perception.dirToFlockCentroid(this, world, world.moutons);
			if (dir >= 0) _orient = dir;
			return agents.ai.MoveConstraints.landBound();
		}
		// Errance : pas tranquille (vitesse = vpas, déjà fixée plus haut).
		if (Math.random() < 0.25) _orient = (int)(Math.random() * 4);
		return agents.ai.MoveConstraints.landBound();
	}

	@Override
	protected void postMove(agents.ai.Percept p) {
		// Traînée du vent : module la vitesse (donc la cadence/distance) selon la
		// direction de marche et la résistance au vent de l'Humain (le plus sensible
		// des agents). vitesse est re-dérivée chaque tick (applyState/applyControlSpeed)
		// → le facteur ne se cumule pas. Pas de coût métabolique pour l'Humain (il n'en
		// a pas encore) → rien à découpler côté énergie.
		vitesse *= windDragFactor();

		// L3 — chasseur : tout loup sur la case de l'Humain est abattu (le berger
		// chasse le prédateur de son troupeau). Le loup mort est purgé par
		// WorldOfCells.stepAgents.
		if (!chasseur) return;
		for (Loup l : world.loups) {
			if (l._alive && l.x == x && l.y == y) {
				l._alive = false;
				m = 1;   // libellé « capture » ce tour
			}
		}
	}

	@Override
	protected void postTick() {
		// Mécanique feu : extinction au contact de l'eau + perte d'énergie + mort.
		if (_fireState == 1) {
			if (world.getCellHeight(x, y) < 0) _fireState = 0;
			if (world.getIteration() % ticksPerGameSecond() == 0) energie -= energieD / 10;
			if (energie <= 0) _alive = false;
		}

		// L1 — socle cognitif commun : entraînement de l'esprit + émergence du
		// caractère de berger (GREGAIRE quand il garde bien son troupeau).
		trainMindAndCharacter();
	}

	/** Rayon (cases) dans lequel un mouton compte comme « troupeau gardé » (L1). */
	private static final int FLOCK_GUARD_RADIUS = 8;

	/** L1 — pour le berger, « être isolé » = ne pas avoir de troupeau à portée.
	 *  Sa vie sociale est tournée vers ses moutons, pas vers d'autres humains :
	 *  un berger qui garde son troupeau développe un caractère GREGAIRE. */
	@Override
	public boolean isIsolated() {
		for (Mouton mt : world.moutons) {
			if (!mt._alive) continue;
			if (world.distance(mt.x, mt.y, x, y) <= FLOCK_GUARD_RADIUS) return false;
		}
		return true;
	}

	/** L1 — satisfaction ∈ [0,1] du berger : sécurité (pas en feu) + devoir
	 *  accompli (troupeau à portée). Alimente l'émergence du caractère. */
	@Override
	public double satisfaction() {
		double safety = isOnFire() ? 0.0 : 1.0;
		double duty   = isIsolated() ? 0.0 : 1.0;
		return (safety + duty) / 2.0;
	}
}
