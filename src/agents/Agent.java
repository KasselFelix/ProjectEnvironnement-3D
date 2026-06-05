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
	public String getOrientLabel() {
		switch (_orient) {
			case 0: return "Nord";
			case 1: return "Est";
			case 2: return "Sud";
			case 3: return "Ouest";
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
