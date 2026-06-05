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
	
	public Humain( int __x, int __y,World __world)
	{
		super(__x,__y,__world);
		_alive = true;

		_redValue = 0.f;
		_greenValue = 1.f;
		_blueValue = 0.f;

	}

	/** Accesseur public pour l'UI. */
	public int getEnergie() { return energie; }
	public int getEnergieMax() { return energieD; }
	public boolean isAlive() { return _alive; }

	@Override public String getTypeName() { return "Humain"; }

	@Override public String getCurrentBehavior() {
		if (playerControlled) return "Piloté";
		if (_fireState == 1)  return "En feu";
		if (currentState == agents.ai.AgentState.HOME) return "Rentre au foyer";
		return currentState == agents.ai.AgentState.HERD ? "Garde le troupeau" : "Errance";
	}

	// ===== Hooks du Template Method (Agent.step) =====

	@Override
	protected boolean preStepAbort() { return !_alive; }

	@Override
	protected boolean isMyTurn() {
		return world.getIteration() % (int)((1.0 / vitesse) * 28) == 0;
	}

	@Override
	protected void applyControlSpeed() { vitesse = vcourse; }

	/** Le berger « perçoit » le troupeau : les moutons alimentent preyDir/preyVisible
	 *  du Percept (cf. Agent.step → Perception.sense). */
	@Override
	protected java.util.List<? extends objects.UniqueDynamicObject> prey() {
		return world.moutons;
	}

	@Override
	protected agents.ai.AgentState decideState(agents.ai.Percept p) {
		if (isOnFire())          return agents.ai.AgentState.ON_FIRE;
		if (world.getJour() == 0) return agents.ai.AgentState.HOME;  // la nuit, rentre au foyer
		if (p.preyVisible())     return agents.ai.AgentState.HERD;   // berger : rejoint le troupeau
		return agents.ai.AgentState.WANDER;
	}

	@Override
	protected agents.ai.MoveConstraints applyState(agents.ai.AgentState s, agents.ai.Percept p) {
		if (s == agents.ai.AgentState.ON_FIRE) {
			if (p.waterDir >= 0) _orient = p.waterDir;
			return agents.ai.MoveConstraints.amphibious();
		}
		if (s == agents.ai.AgentState.HOME) {
			// La nuit, l'Humain rallie le foyer (bergerie).
			worlds.WorldOfCells wc = (worlds.WorldOfCells) world;
			int dir = agents.ai.Perception.dirToCell(this, world, wc.getBergerieX(), wc.getBergerieY());
			if (dir >= 0) _orient = dir;
			return agents.ai.MoveConstraints.landBound();
		}
		if (s == agents.ai.AgentState.HERD) {
			// Suit le CENTRE DE MASSE du troupeau visible (pas la bête la plus
			// proche) — un berger conduit le gros du troupeau, pas une traînarde.
			int dir = agents.ai.Perception.dirToFlockCentroid(this, world, world.moutons);
			if (dir >= 0) _orient = dir;
			return agents.ai.MoveConstraints.landBound();
		}
		if (Math.random() < 0.25) _orient = (int)(Math.random() * 4);
		return agents.ai.MoveConstraints.landBound();
	}

	@Override
	protected void postTick() {
		// Mécanique feu : extinction au contact de l'eau + perte d'énergie + mort.
		if (_fireState == 1) {
			if (world.getCellHeight(x, y) < 0) _fireState = 0;
			if (world.getIteration() % 20 == 0) energie -= energieD / 10;
			if (energie <= 0) _alive = false;
		}
	}
}
