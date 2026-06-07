package agents;

import worlds.World;
import agents.ai.*;

/**
 * Ours — super-prédateur (L4). Sommet de la chaîne alimentaire : il chasse le
 * LOUP (qui chasse le Mouton, qui broute l'herbe). Apex : il ne fuit aucun
 * agent (seulement le feu et la lave). Plus lent mais bien plus endurant que le
 * loup ; gros gabarit à l'écran.
 *
 * Calqué sur {@link Loup} (mêmes hooks du Template Method) mais sans modèle GLB
 * (rendu par le cube de {@link Agent}). Hérite du socle cognitif d'Agent (L1).
 */
public class Ours extends Agent {

    public double PreproD = 0.0008;
    public double Prepro  = PreproD;
    public double reproEnergyThreshold = 0.60;
    public double reproOffspringRatio  = 0.45;

    public int energieD = 3000;   // très endurant
    public int energie  = energieD;

    public int vision = 11;

    public double vcourse = 11.0;  // plus lent que le loup (13.5) mais inexorable
    public double vtrot   = 6.0;
    public double vpas    = 2.5;
    public double vitesse = vpas;

    public double swimFactor = 0.5;

    /** Sous ce seuil de faim (fraction de energieD) l'ours chasse et dévore. */
    public static final double HUNGER_RATIO = 0.7;

    public int m = 0;        // 1 si a mangé ce tour
    public int lastX, lastY;

    public Ours(int x, int y, World w) {
        super(x, y, w);
        _alive = true;
        _redValue = 0.45f; _greenValue = 0.27f; _blueValue = 0.10f;   // brun
    }

    public int getEnergie() { return energie; }
    public int getEnergieMax() { return energieD; }
    public boolean isAlive() { return _alive; }

    @Override public String getTypeName() { return "Ours"; }

    @Override public String getCurrentBehavior() {
        if (_fireState == 1) return "Fuit feu";
        if (m == 1)          return "Devore loup";
        if (playerControlled) return "Pilote";
        switch (currentState) {
            case FLEE_LAVA: return "Fuit lave";
            case REST:      return "Repos";
            case HUNT:      return "Chasse loup";
            case SEARCH:    return "Cherche proie";
            case SEEK_LAND: return "Cherche terre";
            default:        return "Errance";
        }
    }

    // ===== Hooks du Template Method =====

    @Override
    protected boolean preStepAbort() {
        if (maxAgeDays > 0 && getAgeDays() > maxAgeDays) { _alive = false; return true; }
        return !_alive;
    }

    @Override
    protected boolean isMyTurn() {
        // Math.max(1, …) : garde-fou anti division par zero (vitesse evolutive non
        // bornee → diviseur 0 si vitesse > 28 ; au-dela l'ours agit chaque tick).
        return world.getIteration() % Math.max(1, (int) ((1.0 / vitesse) * 28)) == 0;
    }

    @Override
    protected void resetTickFlags() {
        m = 0;
        if (world.getCellHeight(x, y) < 0) _fireState = 0;
        lastX = x; lastY = y;
        refreshMemoryCapacity();   // L1
    }

    /** L4 — l'ours chasse le LOUP. */
    @Override
    protected java.util.List<? extends objects.UniqueDynamicObject> prey() {
        return world.loups;
    }

    @Override
    protected boolean canMove() { return energie > 2; }

    @Override
    protected void applyControlSpeed() { vitesse = vcourse; }

    @Override
    public AgentState decideState(Percept p) {
        boolean affame = energie < energieD * HUNGER_RATIO;
        if (!affame) resetPursuit();                       // plus en chasse → oublie la piste
        if (isOnFire())                  return AgentState.ON_FIRE;
        if (p.lavaVisible())             return AgentState.FLEE_LAVA;   // L2
        if (affame && p.preyVisible())   return AgentState.HUNT;        // proie en vue (prioritaire)
        if (affame && hasFreshTrack())   return AgentState.HUNT;        // proie perdue de vue mais piste fraîche → persistance
        if (p.inWater)                   return AgentState.SEEK_LAND;
        if (affame)                      return AgentState.SEARCH;
        if (energie >= energieD)         return AgentState.REST;
        return AgentState.WANDER;
    }

    @Override
    public MoveConstraints applyState(AgentState s, Percept p) {
        switch (s) {
            case ON_FIRE:
                if (p.waterDir >= 0) _orient = p.waterDir;
                vitesse = vcourse;
                return dodgeObstacles(true);   // contourne les arbres ; l'eau est l'objectif
            case FLEE_LAVA:
                if (p.lavaDir >= 0) _orient = AgentState.opposite(p.lavaDir);
                vitesse = vcourse;
                return dodgeObstacles(true);   // contourne arbres/lave en fuyant
            case HUNT: {
                vitesse = vcourse;
                // Proie EN VUE → sa case (met à jour la piste) ; HORS DE VUE →
                // persistance vers la dernière position connue (case « fantôme »).
                int[] aim = p.preyVisible() ? pursuitSeen(p) : pursuitGhost();
                // Au sortir de la chasse, la recherche se ré-amorce vers la mémoire.
                mem.spiralHeading = -1;
                int dir = Perception.dirToCell(this, world, aim[0], aim[1]);
                if (dir < 0) dir = (p.preyDir >= 0) ? p.preyDir : _orient;
                // Contourne l'obstacle vers la cible si le pas direct est bloqué (BFS vision).
                if (aim[0] >= 0 && headingKind(dir, p, true, vision) < 0) {
                    int bfs = bfsStepToward(aim[0], aim[1], vision, true);
                    if (bfs >= 0) dir = bfs;
                }
                _orient = dir;
                return MoveConstraints.amphibious();
            }
            case SEEK_LAND:
                // COMMIT : côte en vue droit devant → terminer la traversée
                // (anti-oscillation au littoral) ; sinon, terre la plus proche.
                if (!waterCrossable(_orient, vision)) {
                    if (p.landDir >= 0) _orient = p.landDir;
                }
                vitesse = vcourse;
                return dodgeObstacles(true);   // contourne les arbres vers la terre
            case REST:
                wantsToMove = false;
                return MoveConstraints.landBound();
            case SEARCH: {
                // D'abord rallier une zone de chasse mémorisée (à terre, garde-fou de
                // progrès) ; sinon spirale biaisée vers la mémoire + contournement
                // intelligent (longe / traverse un bras d'eau si la côte est en vue).
                // Strictement la même logique que le Loup (helpers partagés d'Agent).
                int hdir = huntHomingDir(p, vision);
                if (hdir >= 0) { _orient = hdir; vitesse = vtrot; return MoveConstraints.landBound(); }
                seedSpiralTowardMemory(vision);
                spiralSearch(vision);
                vitesse = vtrot;
                _orient = mem.spiralHeading;
                return steerAroundObstacles(p, true, vision);
            }
            case WANDER:
            default:
                lazyWander();
                // Anti-obstacle en errance, sans nager (ours repu).
                return steerAroundObstacles(p, false, vision);
        }
    }

    private void lazyWander() {
        mem.resetSpiral();
        if (aheadVisitedRecently()) { _orient = (_orient + 1) % 4; vitesse = vpas; return; }
        if (Math.random() < 0.2) {
            _orient = (Math.random() > 0.5) ? (_orient + 1) % 4 : (_orient - 1 + 4) % 4;
            wantsToMove = false; vitesse = 1;
        } else {
            vitesse = vpas;
        }
    }

    @Override
    protected void postMove(Percept p) {
        // L4 — dévore tout loup sur la case (gain plafonné à energieD/2).
        if (energie < energieD * HUNGER_RATIO) {
            for (Loup l : world.loups) {
                if (l._alive && l.x == x && l.y == y) {
                    l._alive = false;
                    energie = Math.min(energieD, energie + energieD / 2);
                    m = 1;
                    vitesse = vpas;
                    memory.remember(MemoryKind.HUNTING, x, y);   // L1
                    break;
                }
            }
        }

        // Traînée du vent : une fois, pour la cadence ET le coût métabolique
        // (le vent propulse → énergie/temps invariante au vent).
        double windF = windDragFactor();

        // Énergie.
        if (energie <= 0) { _alive = false; }
        else {
            if (world.getCellHeight(x, y) < 0) energie -= 2;
            energie -= metabolicCost(1.0 / windF);   // L8 — coût métabolique modulé par l'activité ; ÷windF = effort propre constant
        }
        if (world.getCellHeight(x, y) < 0) vitesse *= swimFactor;
        vitesse *= world.coldSpeedFactor();   // L6
        vitesse *= windF;   // traînée vent (même facteur que le décompte d'énergie ci-dessus)

        // Mort dans la lave.
        if (world.getLavaCAValue(x, y) > 0) _alive = false;

        // Reproduction sexuée (mirror Loup).
        Ours mate = findReproPartner();
        if (currentStage().canReproduce() && energie >= energieD * reproEnergyThreshold && mate != null
                && Math.random() < Prepro * genome.reproProbaFactor() * currentStage().fertilityFactor()) {
            double invest = energieD * reproOffspringRatio;
            Ours cub = new Ours(x, y, _world);
            cub.isFounder = false;
            cub.energie = (int) invest;
            cub.vision  = mutateInt((this.vision + mate.vision) / 2);
            cub.vcourse = mutateDouble((this.vcourse + mate.vcourse) / 2.0);
            cub.genome  = Genome.inherit(this.genome, mate.genome, EVO_RNG, TYPE_MUTATION_RATE, GRANDPARENT_PROB);
            cub.initMind();
            cub.sizeFactor = clampSize(mutateDouble((this.sizeFactor + mate.sizeFactor) / 2.0));
            double childMaxAge = this.maxAgeDays;
            if (childMaxAge > 0) {
                childMaxAge *= cub.genome.longevityFactor();
                if (isInfertile(this) || isInfertile(mate)) childMaxAge *= INFERTILE_CHILD_LONGEVITY_MALUS;
            }
            cub.maxAgeDays = childMaxAge;
            energie -= (int) invest;
            world.uniqueDynamicObjects.add(cub);
            world.agents.add(cub);
            ((worlds.WorldOfCells) world).ours.add(cub);
            ((worlds.WorldOfCells) world).setNbours(((worlds.WorldOfCells) world).getNbours() + 1);
        }
    }

    @Override
    protected void postTick() {
        // Mecanique feu (parite Loup/Mouton/Humain) : perte d'energie + mort.
        // L'extinction au contact de l'eau est geree dans resetTickFlags().
        if (_fireState == 1) {
            if (world.getIteration() % ticksPerGameSecond() == 0) energie -= energieD / 10;
            if (energie <= 0) _alive = false;
        }
        trainMindAndCharacter();   // L1
    }

    private static final int REPRO_RADIUS = 4;

    private Ours findReproPartner() {
        Ours best = null; double bestD = Double.MAX_VALUE;
        for (Ours o : ((worlds.WorldOfCells) world).ours) {
            if (o == this || !o._alive) continue;
            double d = world.distance(o.x, o.y, x, y);
            if (d <= REPRO_RADIUS && d < bestD) { bestD = d; best = o; }
        }
        return best;
    }
}
