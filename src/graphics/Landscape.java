// ### WORLD OF CELLS ### 
// created by nicolas.bredeche(at)upmc.fr
// date of creation: 2013-1-12

package graphics;

import worlds.*;

import java.awt.*;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import javax.media.opengl.*;
import javax.media.opengl.fixedfunc.*;

import agents.Agent;
import cellularautomata.ecosystem.LavaCA;
import worlds.WorldOfCells;
import ui.AgentInfoPanel;
import ui.Hud;
import ui.InGameMenu;
import ui.LaunchMenu;
import ui.PopulationGraph;
import ui.SimulationConfig;
import ui.UiRenderer;

import com.jogamp.opengl.util.Animator;
import com.jogamp.opengl.util.awt.Screenshot;

// NEWT (fenêtrage natif JOGL) — remplace AWT GLCanvas/Frame pour permettre le
// masquage + verrouillage du curseur (setPointerVisible/confinePointer/warpPointer),
// impossibles via AWT sur WSLg/XWayland.
import com.jogamp.newt.opengl.GLWindow;
import com.jogamp.newt.event.KeyEvent;
import com.jogamp.newt.event.KeyListener;
import com.jogamp.newt.event.MouseEvent;
import com.jogamp.newt.event.MouseListener;
import com.jogamp.newt.event.WindowEvent;
import com.jogamp.opengl.util.texture.Texture;
import com.jogamp.opengl.util.texture.TextureIO;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;

import javax.media.opengl.GL;
import javax.media.opengl.GLAutoDrawable;
import javax.media.opengl.GLCapabilities;
import javax.media.opengl.GLEventListener;


import landscapegenerator.LoadFromFileLandscape;
import landscapegenerator.PerlinNoiseLandscapeGenerator;


/**
 * Self-contained code 
 * displaying a landscape generated with Perlin noise
 */
public class Landscape implements GLEventListener, KeyListener, MouseListener {
	
		private World _myWorld; 
	
		private static GLCapabilities caps;  // GO FAST ???
	
		static boolean MY_LIGHT_RENDERING = true; 
		static boolean MY_LIGHT_RENDERING_HIGHT = false; // true: nicer but slower

		// (Anciennement SMOOTH_AT_BORDER : feature désactivée et supprimée — le tore est maintenant
		// continu nativement grâce au bruit de Perlin tileable, donc plus besoin de masquer une jointure.)

		//final static double landscapeAltitudeRatio = 0.6; // 0.5: half mountain, half water ; 0.3: fewer water
		
		static boolean VIEW_FROM_ABOVE = true; // also deactivate altitudes
		
		static boolean DISPLAY_OBJECTS = true; // useful to deactivate if view_from_above
		
		final static boolean DISPLAY_FPS = true; // on-screen display
		
		/*
		 * benchmarking 
		 * 		Airbook (w/wo visible display) : 
		 * 			true:  frame per second  : frames per second  : 59 ; polygons per second: 966656 --- frames per second  : 82 ; polygons per second: 1343488
		 * 			false: frames per second  : 61 ; polygons per second: 999424 --- frames per second  : 254 ; polygons per second: 4161536 (!!!)
		 * 
		 * 
		 * Bonnes pratiques:
		 * - gl.Begin() ... gl.glEnd(); : faire un minimum d'appel, idealement un par iteration. (gain de 50% a 100% ici)
		 * - gl.glCullFace(GL.GL_FRONT); ... gl.glEnable(GL.GL_CULL_FACE); : si la scene le permet, reduit le nb de polyg a afficher.
		 * - TRIANGLE SPLIT permet de reduire le nombre d'appels a OpenGL (gl.begin et end)
		 * - each call to gl.glColor3f costs a lot (speed is down by two if no call!)
		 */
	
		static Animator animator;

		/** Fenêtre NEWT (fenêtrage natif JOGL), statique pour la bascule plein écran
		 *  ⇄ fenêtré (touche Échap) et le contrôle du pointeur. */
		static GLWindow glWindow;
		static boolean isFullscreen = false;
		/** FOV vertical (half-extent au plan near=1) de la caméra vue de dessus.
		 *  Réutilisé pour le cadrage auto de la fenêtre sur l'écran. */
		private static final float TOPDOWN_FH = 0.04f;
		/** Pas molette en vue de dessus : modifie la distance de vue (en cases). */
		private static final int   WHEEL_TOPDOWN_VIEW_STEP = 4;
		//  https://sites.google.com/site/justinscsstuff/jogl-tutorial-3
		//  if you use a regular Animator object instead of the FPSAnimator, your program will render as fast as possible. You can, however, limit the framerate of a regular Animator by asking the graphics driver to synchronize with the refresh rate of the display (v-sync). Because the target framerate is often the same as the refresh rate, which is often 60-75 or so, this method is a great choice as it lets the driver do the work of limiting frame changes. 
		//  However, some Intel GPUs may ignore this setting. In the init method, active v-sync as follows:
		//  add : drawable.getGL().setSwapInterval(1); in the init method
		//  then: in the main method, replace the FPSAnimator with a regular Animator.
		
		private float rotateX = 0.0f;       // yaw caméra en vue 3D libre
		private float cameraPitch = 0.0f;   // tilt vertical caméra en vue 3D libre (clic droit drag)

		// Orbit caméra autour de l'agent suivi (vue 3D + cameraFollow).
		// orbitYaw  : angle horizontal (clic gauche drag horizontal)
		// orbitPitch: angle vertical au-dessus de l'horizon (clic gauche drag vertical)
		// orbitRadius : distance caméra ↔ agent.
		// Position par défaut : derrière l'agent (yaw=0 ⇒ regarde « Nord »
		// conventionnel), assez haut (pitch=55° = vue d'au-dessus), distance
		// ~50 unités-monde.
		//
		// Le point visé est élevé au-dessus de l'agent par un offset
		// PROPORTIONNEL à orbitRadius (caméra 3e personne façon Minecraft) :
		// si l'offset était fixe, en zoomant (petit radius) il deviendrait
		// énorme relativement au champ et l'agent tomberait tout en bas de
		// l'écran. En le faisant croître/décroître avec le radius, la position
		// de l'agent à l'écran reste constante quel que soit le zoom, et la
		// caméra descend vers lui en s'approchant. focalZ = radius × ratio.
		private float orbitYaw    = 0.0f;
		private float orbitPitch  = 55.0f;
		private float orbitRadius = 50.0f;
		// Ratio calibré pour reproduire l'ancien cadrage à radius=50 (16/50≈0.32),
		// légèrement réduit pour recentrer un peu plus le sujet.
		private static final float ORBIT_FOCAL_Z_RATIO = 0.26f;
		// Plancher en unités-monde : on vise toujours au moins le haut du corps
		// (~5-6 u) même collé à l'agent, pour ne pas viser ses pieds.
		private static final float ORBIT_FOCAL_Z_MIN = 3.0f;

		// Bornes absolues du zoom (molette).
		private static final float ORBIT_RADIUS_MIN  = 1.0f;     // suivi : on peut coller l'agent pour bien voir la texture
		private static final float ORBIT_RADIUS_MAX  = 120.0f;   // suivi : dezoom max
		private static final float CAM_DIST_3D_MIN   = -300.0f;  // 3D libre : très loin
		private static final float CAM_DIST_3D_MAX   = -3.0f;    // 3D libre : très proche, ne traverse pas
		private static final float WHEEL_ORBIT_STEP  = 1.0f;     // pas fin pour cadrer l'agent de près
		private static final float WHEEL_3D_STEP     = 6.0f;

		/** Rayon de vue PROPRE à la vue de dessus (en cases), réglé à la molette.
		 *  Découplé de config.viewDistanceCells (qui pilote la 3D) → zoomer en vue
		 *  de dessus n'affecte pas la 3D. Défaut zoomé pour ne pas voir les bords. */
		private int topDownViewCells = 30;
		// Distance caméra↔point regardé en 3D libre (remplace le -130 hardcodé).
		// Modifié par la molette ; translateZ continue à gérer la hauteur.
		private float cameraDistance3D = -130.0f;

		// Sensibilités drag souris.
		private static final float PAN_SENSITIVITY   = 0.05f;
		private static final float ROT_SENSITIVITY   = 0.3f;
		private static final float ORBIT_SENSITIVITY = 0.4f;

		// State du drag souris.
		// Clic gauche : seuil DRAG_THRESHOLD_PX pour distinguer clic court
		// (= picking au release) d'un drag long (= rotation/orbit/pan).
		// Clic droit : drag immédiat pour le pan en 3D (style « agrippe le
		// sol »), sans logique de clic court.
		private boolean draggingLeft  = false;
		private boolean draggingRight = false;
		private int     lastDragX     = 0;
		private int     lastDragY     = 0;
		private int     pressX        = 0;
		private int     pressY        = 0;
		private boolean dragExceededThreshold = false;
		private static final int   DRAG_THRESHOLD_PX = 5;
		private static final float PAN_SENSITIVITY_3D = 0.08f;  // pan clic droit en 3D libre
		// Accumulateurs fractionnaires du pan « agrippe le sol » : movingX/Y sont
		// des entiers (cellules). Sans accumulateur, round(rdx * sens) = 0 pour les
		// petits deltas par event → un drag lent ne déplaçait RIEN. On accumule
		// donc la fraction de cellule et n'applique que la partie entière.
		private float panAccumX = 0f;
		private float panAccumY = 0f;

		/** Plan d'eau (technique des jeux) : quads à z=0 colorés par profondeur,
		 *  lighting OFF (couleur stable, pas de scintillement spéculaire selon
		 *  l'angle), assombris jour/nuit manuellement, semi-opaques, avec
		 *  polygon-offset pour ne pas z-fighter avec le fond marin peu profond.
		 *  Le terrain plonge en continu sous l'eau (fond marin) ; la ligne d'eau
		 *  est lissée per-pixel par le depth-buffer (terrain émergé qui occulte). */
		private static final float[] WATER_SHALLOW   = { 0.28f, 0.60f, 0.66f }; // bordure peu profonde (teal clair)
		private static final float[] WATER_DEEP      = { 0.03f, 0.12f, 0.26f }; // large profond (bleu nuit)
		private static final float   WATER_DEPTH_SAT = 0.55f;  // fraction de la profondeur max où on atteint WATER_DEEP
		// Opacité de l'eau dépendante de la profondeur (réaliste) : claire en faible
		// profondeur (on devine le fond), opaque au large (on ne voit plus rien).
		private static final float   WATER_ALPHA_SHALLOW = 0.42f; // bordure : on voit le fond
		private static final float   WATER_ALPHA_DEEP    = 0.97f; // large : quasi opaque

		/** Fin du brouillard de jour (= GL_FOG_END plein jour, cf. updateSunAndSky).
		 *  Sert à dériver la distance de vue par défaut. */
		private static final float FOG_END_DAY      = 380f;
		/** Marge (en cases) ajoutée au rayon de vue pour éviter tout « pop » au bord. */
		private static final int   VIEW_MARGIN_CELLS = 3;

		private float translateZ=-44.0f;//hauteur de la camera

		// État appui long des touches caméra-Z. AWT ne déclenche pas d'auto-
		// repeat sur les modificateurs (SHIFT), donc on convertit SPACE et
		// SHIFT en flags persistants appliqués par frame dans display() pour
		// une descente / montée symétriques.
		private boolean keySpaceHeld = false;
		private boolean keyShiftHeld = false;
		private static final float CAMERA_Z_SPEED = 0.4f;

		// ===== Pilotage manuel d'agent (touche 'c') =====
		// Flags de direction maintenus : tant qu'une touche est tenue, l'agent
		// avance. Le cap cardinal effectif (Agent.controlDir) est recalculé chaque
		// frame par updateManualControlHeading() selon la VUE : cardinal direct en
		// vue de dessus, relatif à la caméra (orbitYaw) en 3D façon Minecraft.
		private boolean ctrlFwd, ctrlBack, ctrlLeft, ctrlRight;
		// Rotation caméra au clavier en pilotage 3D (A = gauche, E = droite) — le
		// mouse-look est inutilisable sur WSLg (curseur non masquable/verrouillable).
		private boolean ctrlTurnLeft, ctrlTurnRight;
		private static final float KEY_TURN_DEG_PER_FRAME = 14.0f;
		// Agent actuellement piloté (au plus UN à la fois). Sert à relâcher le
		// précédent quand on en sélectionne / contrôle un autre.
		private agents.Agent controlledAgent = null;
		// Capture souris (mouse-look 1ère personne) : via NEWT on masque le curseur
		// (setPointerVisible(false)) + on le confine (confinePointer(true)) et on le
		// recentre (warpPointer) à chaque mouvement → verrou propre au centre, sans
		// dérive ni double curseur. Le yaw/pitch vient du delta au centre.
		private boolean cursorCaptured = false;
		// Suivi du dernier point souris pour le delta RELATIF du mouse-look (robuste
		// quel que soit le timing du warp NEWT). Invalidé après chaque recentrage.
		private int lastMouseX, lastMouseY;
		private boolean haveLastMouse = false;
		public static float nHeihtCommonObj;//test
		public static float nHeihtUniqueObj;//test

		// Demande de capture d'écran (F12). Écrit depuis l'AWT EDT (keyPressed),
		// lu par le thread GL (display). volatile pour la visibilité cross-thread.
		private volatile boolean screenshotRequested = false;

		// Texture de bruit grayscale tileable, modulée par la couleur de cellule
		// (sable/montagne/eau via getCellColorValue). Null si chargement échoué.
		private Texture groundTex;

		// Nombre de cellules par répétition de la texture (plus petit = motif plus
		// gros à l'écran). 1/8 = un carreau de bruit couvre 8 cellules.
		private static final float TEX_TILES_PER_CELL = 1f / 8f;

		// Normales par sommet (vertex shading sur le terrain) précalculées une fois
		// dans initLandscape() à partir de landscape[][]. Indexées comme landscape,
		// donc [dxView][dyView][3] avec wrap torique pour les voisins.
		private float[][][] normals;

		// Couleur du ciel/brouillard recalculée chaque frame par updateSunAndSky.
		private final float[] skyColor = new float[]{0.5f, 0.7f, 0.9f, 1f};

		// Facteur de nuit ∈ [0, 1] : 0 en plein jour, 1 quand le soleil est sous
		// l'horizon. Mis à jour par updateSunAndSky, utilisé pour les étoiles
		// et les lampes lunaires.
		private float currentNightFactor = 0f;

		// Phase courante du cycle jour/nuit ∈ [0, 1) : 0=aube, 0.25=midi,
		// 0.5=crépuscule, 0.75=minuit. Pour le HUD timer + rotation étoiles.
		private float currentDayPhase = 0f;

		// Étoiles dans le ciel — positions précalculées sur une demi-sphère
		// world-space (au-dessus de l'horizon). Visibles uniquement la nuit.
		private float[][] starPositions;
		private int starListId = 0; // display list compilée dans init(gl)
		private static final int N_STARS = 150;
		private static final float STAR_RADIUS = 320f; // inside fog/clip

		// Lampes lunaires fixes : 0=bergerie, 1=wolfHome, 2-5=aléatoires sur terre.
		// Stockées comme coordonnées de cellule CA (constantes, indépendantes de
		// movingX/movingY) ; la position world-space est recalculée chaque frame
		// dans applyMoonLights pour suivre le décalage du terrain (movingX/Y).
		private int[][] moonLightCells; // [N][2] : (cellX, cellY)
		private static final int N_MOON_LIGHTS = 6;

        int it = 0;
        int movingIt = 0;
        int dxView;
        int dyView;

        double[][] landscape;

        int lastFpsValue = 0;

        // Dimensions du viewport courant, mises à jour dans reshape(). Servent
        // au HUD/menu/overlay 2D pour positionner leurs widgets.
        private int viewportWidth  = 1024;
        private int viewportHeight = 768;

        // Couche UI overlay (Phase 5+).
        private final UiRenderer ui  = new UiRenderer();
        private final Hud        hud = new Hud();

        // Module 1 (refonte 2026-05) : fixed-timestep découplé du framerate.
        // Remplace l'ancien `_myWorld.step()` synchro frame par un loop accumulator.
        private final TimeKeeper timeKeeper = new TimeKeeper();

        // Menu de lancement (Phase 6 Pass B). Renseigné par le constructeur
        // Landscape(World, SimulationConfig). Si null, on saute toute la logique
        // de menu — les anciens constructeurs sans config restent compatibles.
        private SimulationConfig config = null;
        private LaunchMenu launchMenu = null;
        private InGameMenu inGameMenu = null;  // ouvert/fermé par la touche `m` (Phase 7)

        // Sélection d'agent (Phase 8). Mis à jour par le menu in-game (Enter sur
        // l'onglet AGENTS) ou par le picking 3D (clic souris).
        private Agent selectedAgent = null;
        private int   selectedAgentIndex = -1;
        private boolean cameraFollow = false;
        private final AgentInfoPanel agentInfoPanel = new AgentInfoPanel();
        private final PopulationGraph populationGraph = new PopulationGraph();
        private boolean showPopulationGraph = false;  // masqué au démarrage ; toggle par la touche `g`

        // Focus clavier du menu in-game. Découplé d'`isOpen()` : le menu peut
        // rester visible (parqué, plus transparent) tout en laissant les
        // raccourcis du jeu actifs. Cliquer dans le panneau ou appuyer sur `m`
        // re-focalise ; cliquer en dehors du panneau défocalise.
        private boolean menuFocused = true;

        // Picking 3D : la souris pose un drapeau ; le picking est résolu côté
        // display() où les matrices GL sont valides (camera appliquée).
        private boolean pickRequested = false;
        private int pickClickX = 0;
        private int pickClickY = 0;
        private final javax.media.opengl.glu.GLU pickGlu = new javax.media.opengl.glu.GLU();
        
        public static int lastItStamp = 0;
        public static long lastTimeStamp = 0;
        
        // visualization parameters
        
    	float heightFactor; //64.0f; // was: 32.0f;
        double heightBooster; // applied to landscape values. increase heights.
        // -- NOTE that this could also be achieved using heighFactor but is decomposed to enable further pre-calc of height values
        // heightFactor deals with visualization
        // heigBooster will impact landscape array content 
       
		float offset;
		float stepX;
		float stepY;
		float lenX;
		float lenY;
		
        float smoothFactor[];
        int smoothingDistanceThreshold;
        
        int movingX = 0; 
        int movingY = 0;
   
        
        /**
         * 
         */
        public Landscape (World __myWorld, int __dx, int __dy, double scaling, double landscapeAltitudeRatio)
        {
    		_myWorld = __myWorld;

    		landscape = PerlinNoiseLandscapeGenerator.generatePerlinNoiseLandscape(__dx,__dy,scaling,landscapeAltitudeRatio,10);
    		//landscape = RandomLandscapeGenerator.generateRandomLandscape ( __dx, __dy , scaling,landscapeAltitudeRatio);
    		
    		initLandscape();
        }

        /**
         *
         */
        public Landscape (World __myWorld, String __filename, double scaling, double landscapeAltitudeRatio)
        {
    		_myWorld = __myWorld;

    		landscape = LoadFromFileLandscape.load(__filename,scaling,landscapeAltitudeRatio);

    		initLandscape();
        }

        /**
         * Constructeur paramétré par un SimulationConfig (Phase 6).
         *
         * Choisit la source de paysage (Perlin vs PNG) et les paramètres associés
         * depuis la config, et propage la config au monde pour que les agents
         * soient construits avec les bons paramètres biologiques.
         */
        /** Sélection externe (depuis le menu in-game ou le picking). */
        public void setSelectedAgent(Agent a, int index) {
        	this.selectedAgent = a;
        	this.selectedAgentIndex = index;
        }
        public Agent getSelectedAgent() { return selectedAgent; }

        /** Vrai si un agent est sélectionné ET sous pilotage manuel du joueur :
         *  dans ce cas, les flèches/ZQSD dirigent l'agent au lieu de la caméra. */
        private boolean controllingAgent() {
        	return selectedAgent != null && selectedAgent.playerControlled;
        }

        /** Vrai si on pilote un agent EN VUE 3D → caméra première personne placée
         *  à l'œil de l'agent (déplacement relatif au regard, façon Minecraft). */
        private boolean firstPersonControl() {
        	return controllingAgent() && !VIEW_FROM_ABOVE;
        }

        /** Hauteur de l'œil (unités monde) au-dessus du sol de l'agent en 1ère personne. */
        private static final float FP_EYE_HEIGHT = 4.0f;

        /** Relâche le pilotage d'un agent : il reprend son comportement autonome.
         *  Garantit qu'au plus un agent est piloté à la fois. */
        private void releaseControl(agents.Agent a) {
        	if (a == null) return;
        	a.playerControlled = false;
        	a.controlDir = -1;
        	a.controlDx = 0;
        	a.controlDy = 0;
        	a.hiddenFP = false;
        	if (a == controlledAgent) controlledAgent = null;
        }

        /** Vérifie qu'un agent suivi est toujours vivant et dans le monde. */
        private boolean isAgentStillAlive(Agent a) {
        	if (a == null) return false;
        	if (!(_myWorld instanceof WorldOfCells)) return true; // pas de moyen de vérifier
        	WorldOfCells wc = (WorldOfCells) _myWorld;
        	return wc.loups.contains(a) || wc.moutons.contains(a) || wc.humains.contains(a);
        }

        /**
         * Picking : pour chaque agent vivant, projette sa position 3D en pixels
         * écran via gluProject, calcule la distance au clic, et sélectionne le
         * plus proche dans une tolérance de PICK_TOLERANCE_PX.
         *
         * Appelé en fin de display() avant le passage en mode 2D, pour que
         * GL_MODELVIEW_MATRIX et GL_PROJECTION_MATRIX reflètent encore le
         * pipeline 3D avec la caméra appliquée.
         */
        private static final double PICK_TOLERANCE_PX = 30.0;
        private void doPicking(GL2 gl) {
        	if (!(_myWorld instanceof WorldOfCells)) return;
        	WorldOfCells wc = (WorldOfCells) _myWorld;

        	double[] mv   = new double[16];
        	double[] proj = new double[16];
        	int[] view    = new int[] { 0, 0, viewportWidth, viewportHeight };
        	double[] win  = new double[3];

        	gl.glGetDoublev(GL2.GL_MODELVIEW_MATRIX, mv, 0);
        	gl.glGetDoublev(GL2.GL_PROJECTION_MATRIX, proj, 0);

        	Agent best = null;
        	int bestIndex = -1;
        	double bestDist = PICK_TOLERANCE_PX;

        	// Loups
        	for (int i = 0; i < wc.loups.size(); i++) {
        		Agent a = wc.loups.get(i);
        		double d = projectAndScreenDist(a, mv, proj, view, win);
        		if (d >= 0 && d < bestDist) { best = a; bestIndex = i; bestDist = d; }
        	}
        	// Moutons
        	for (int i = 0; i < wc.moutons.size(); i++) {
        		Agent a = wc.moutons.get(i);
        		double d = projectAndScreenDist(a, mv, proj, view, win);
        		if (d >= 0 && d < bestDist) { best = a; bestIndex = i; bestDist = d; }
        	}
        	// Humains
        	for (int i = 0; i < wc.humains.size(); i++) {
        		Agent a = wc.humains.get(i);
        		double d = projectAndScreenDist(a, mv, proj, view, win);
        		if (d >= 0 && d < bestDist) { best = a; bestIndex = i; bestDist = d; }
        	}

        	if (best != null) {
        		// Re-cliquer sur l'agent déjà sélectionné active le suivi
        		// caméra (raccourci pour éviter d'avoir à appuyer sur `f`).
        		if (best == selectedAgent) {
        			cameraFollow = true;
        		}
        		// Changer d'agent fait perdre le contrôle du précédent (un seul
        		// agent piloté à la fois) — il reprend son comportement autonome.
        		if (controlledAgent != null && controlledAgent != best) {
        			releaseControl(controlledAgent);
        		}
        		selectedAgent = best;
        		selectedAgentIndex = bestIndex;
        	}
        }

        /**
         * Retourne la distance pixel entre la projection de l'agent et le clic,
         * ou -1 si l'agent est hors frustum (derrière la caméra ou clippé).
         */
        private double projectAndScreenDist(Agent a, double[] mv, double[] proj, int[] view, double[] win) {
        	int w = _myWorld.getWidth();
        	int h = _myWorld.getHeight();
        	int x2 = ((a.x - (movingX % w)) % w + w) % w;
        	int y2 = ((a.y - (movingY % h)) % h + h) % h;
        	double worldX = offset + x2 * stepX;
        	double worldY = offset + y2 * stepY;
        	double cellH  = Math.max(0, _myWorld.getCellHeight(a.x, a.y));
        	// Centre approximatif de la boîte agent (h à h+5 dans Agent.displayUniqueObject).
        	double worldZ = cellH * nHeihtUniqueObj + 2.5;

        	boolean ok = pickGlu.gluProject(worldX, worldY, worldZ, mv, 0, proj, 0, view, 0, win, 0);
        	if (!ok) return -1;
        	if (win[2] < 0 || win[2] > 1) return -1; // hors frustum profondeur

        	double sx = win[0];
        	double sy = viewportHeight - win[1]; // Y OpenGL bottom-left → AWT top-left
        	double dx = sx - pickClickX;
        	double dy = sy - pickClickY;
        	return Math.sqrt(dx*dx + dy*dy);
        }

        /**
         * Test de visibilité écran d'un mouton (frustum culling) : projette le
         * centre du corps de l'agent avec les matrices courantes et renvoie false
         * s'il est derrière la caméra (profondeur hors [0,1]) ou hors viewport
         * élargi de {@code marginPx} (le corps du mouton peut déborder même quand
         * son centre est sorti, d'où la marge). {@code win} est un scratch buffer
         * réutilisé entre appels pour éviter d'allouer par mouton.
         */
        private boolean isAgentOnScreen(Agent a, double[] mv, double[] proj, int[] view, double[] win, double marginPx) {
        	int w = _myWorld.getWidth();
        	int h = _myWorld.getHeight();
        	int x2 = ((a.x - (movingX % w)) % w + w) % w;
        	int y2 = ((a.y - (movingY % h)) % h + h) % h;
        	double worldX = offset + x2 * stepX;
        	double worldY = offset + y2 * stepY;
        	double cellH  = Math.max(0, _myWorld.getCellHeight(a.x, a.y));
        	double worldZ = cellH * nHeihtUniqueObj + 2.5; // centre approx du corps

        	boolean ok = pickGlu.gluProject(worldX, worldY, worldZ, mv, 0, proj, 0, view, 0, win, 0);
        	if (!ok) return false;
        	if (win[2] < 0 || win[2] > 1) return false; // derrière la caméra / clippé en profondeur

        	double sx = win[0];
        	double sy = win[1];
        	return sx >= -marginPx && sx <= viewportWidth  + marginPx
        	    && sy >= -marginPx && sy <= viewportHeight + marginPx;
        }

        public Landscape (World __myWorld, SimulationConfig config)
        {
    		_myWorld = __myWorld;
    		this.config = config;
    		this.launchMenu = new LaunchMenu(config);
    		if (__myWorld instanceof WorldOfCells) {
    			((WorldOfCells) __myWorld).config = config;
    			this.inGameMenu = new InGameMenu(config, (WorldOfCells) __myWorld, this);
    		}

    		if (config.landscapeSource == SimulationConfig.LandscapeSource.PNG) {
    			landscape = LoadFromFileLandscape.load(
    					config.landscapePngPath,
    					config.landscapeScaling,
    					config.landscapeWaterRatio);
    		} else {
    			landscape = PerlinNoiseLandscapeGenerator.generatePerlinNoiseLandscape(
    					config.landscapeDx,
    					config.landscapeDy,
    					config.landscapeScaling,
    					config.landscapeWaterRatio,
    					config.landscapeOctaves);
    		}

    		initLandscape();
        }
        
        /**
         * 
         */
        private void initLandscape()
        {
       		dxView = landscape.length;
    		dyView = landscape[0].length;

    		System.out.println("Landscape contains " + dxView*dyView + " tiles. (" + dxView + "x" + dyView +")");
        	
    		_myWorld.init(dxView-1,dyView-1,landscape);

    		heightFactor = 50.0f; //64.0f; // was: 32.0f;
            heightBooster = 2.0; // default: 2.0 // 6.0 makes nice high mountains.

            // Système Layer : World a besoin de l'échelle verticale pour calculer
            // getCellTopAltitude (= cellHeight × scale + somme thickness) et le
            // worldCeiling. Doit être appelé APRÈS init() et APRÈS que
            // heightFactor/heightBooster sont fixés.
            _myWorld.setVerticalScale((float)(heightFactor * heightBooster));
           
    		offset =(float)(dxView*-2+10); // was: -40.//200
    		stepX = (-offset*2.0f) / dxView;
    		stepY = (-offset*2.0f) / dxView;
    		lenX = stepX / 2f;
    		lenY = stepY / 2f;

    		// Distance de vue par défaut, dérivée de la fin du brouillard de jour :
    		// au-delà, les cases sont déjà 100% brouillard (invisibles) → on peut
    		// ne pas les dessiner sans perte. Réglable ensuite via le slider PARAMS.
    		if (config != null && config.viewDistanceCells <= 0)
    			config.viewDistanceCells = (int)Math.ceil(FOG_END_DAY / stepX) + VIEW_MARGIN_CELLS;
    		
            smoothFactor = new float[4];
            for ( int i = 0 ; i < 4 ; i++ )
            	smoothFactor[i] = 1.0f;

            smoothingDistanceThreshold = 30; //30;

            precomputeNormals();
            initStars();
            initMoonLights();

            // Démarrer au milieu de la journée (et non à l'aube, qui donne 6s d'écran orange).
            it = _myWorld.getDureeJour() / 2;
        }

        /**
         * Précalcule les positions des étoiles sur une demi-sphère world-space.
         * Le seed est fixe pour avoir des constellations stables entre runs.
         */
        private void initStars() {
            java.util.Random rng = new java.util.Random(987654321L);
            starPositions = new float[N_STARS][3];
            for (int i = 0; i < N_STARS; i++) {
                double az = rng.nextDouble() * 2 * Math.PI;
                // Distribution slightly biased toward zenith via sqrt → moins d'étoiles près de l'horizon
                double elev = Math.acos(1.0 - rng.nextDouble() * 0.95); // [≈0, π/2)
                float sinE = (float) Math.sin(elev);
                float cosE = (float) Math.cos(elev);
                starPositions[i][0] = STAR_RADIUS * sinE * (float) Math.cos(az);
                starPositions[i][1] = STAR_RADIUS * sinE * (float) Math.sin(az);
                starPositions[i][2] = STAR_RADIUS * cosE;
            }
        }

        /**
         * Précalcule les CELLULES des lampes lunaires :
         *  - [0] : bergerie (cellule des Monolithes "humains" dans WorldOfCells)
         *  - [1] : wolfHome (cellule des Monolithes "loups")
         *  - [2..5] : 4 cellules aléatoires sur terre ferme (height > 0)
         * Les positions monde sont recalculées chaque frame dans applyMoonLights
         * en tenant compte du décalage movingX/Y du terrain.
         */
        private void initMoonLights() {
            moonLightCells = new int[N_MOON_LIGHTS][2];

            // _myWorld.init() a déjà tourné, donc bergerie/wolfHome sont définis.
            // WorldOfCells encode l'index comme y * dyCA + x avec dxCA = dxView - 1
            // (monde supposé carré).
            int dxCA = dxView - 1;
            int bergerie = _myWorld.getBergerie();
            int wolfHome = _myWorld.getWolfHome();
            moonLightCells[0][0] = bergerie % dxCA;
            moonLightCells[0][1] = bergerie / dxCA;
            moonLightCells[1][0] = wolfHome % dxCA;
            moonLightCells[1][1] = wolfHome / dxCA;

            // 4 lampes random sur terre ferme (landscape > 0).
            java.util.Random rng = new java.util.Random(123456789L);
            int idx = 2;
            for (int attempts = 0; attempts < 500 && idx < N_MOON_LIGHTS; attempts++) {
                int cx = rng.nextInt(dxCA);
                int cy = rng.nextInt(dxCA);
                if (landscape[cx][cy] > 0) {
                    moonLightCells[idx][0] = cx;
                    moonLightCells[idx][1] = cy;
                    idx++;
                }
            }
            // Fallback : si trop de tirages mer, place au centre.
            for (; idx < N_MOON_LIGHTS; idx++) {
                moonLightCells[idx][0] = dxCA / 2;
                moonLightCells[idx][1] = dxCA / 2;
            }
        }

        /**
         * Normales par sommet à partir des hauteurs voisines (différences centrées,
         * wrap torique). Donne au terrain un vrai relief pour la lumière directionnelle.
         * Précalcul unique : O(dxView * dyView), une seule fois au boot.
         * En vue de dessus (terrain plat à z=0) les normales encodent quand même la
         * pente — ça produit un effet "relief shading" agréable.
         */
        private void precomputeNormals() {
            normals = new float[dxView][dyView][3];
            final double zScale = heightFactor * heightBooster;
            final double dxScale = 2.0 * Math.abs(stepX);
            final double dyScale = 2.0 * Math.abs(stepY);
            for (int x = 0; x < dxView; x++) {
                int xp = (x + 1) % dxView;
                int xm = (x - 1 + dxView) % dxView;
                for (int y = 0; y < dyView; y++) {
                    int yp = (y + 1) % dyView;
                    int ym = (y - 1 + dyView) % dyView;
                    double dzdx = (landscape[xp][y] - landscape[xm][y]) * zScale / dxScale;
                    double dzdy = (landscape[x][yp] - landscape[x][ym]) * zScale / dyScale;
                    float nx = (float) -dzdx;
                    float ny = (float) -dzdy;
                    float nz = 1f;
                    float invLen = 1f / (float) Math.sqrt(nx*nx + ny*ny + nz*nz);
                    normals[x][y][0] = nx * invLen;
                    normals[x][y][1] = ny * invLen;
                    normals[x][y][2] = nz * invLen;
                }
            }
        }
        
        /**
         * 
         */
        public static Landscape run(Landscape __landscape)
        {
    		caps = new GLCapabilities(null); //!n
    		caps.setDoubleBuffered(true);  //!n

    		final GLWindow win = GLWindow.create(caps);
    		glWindow = win;
    		win.setTitle("Game Of Life");

            win.addGLEventListener(__landscape);
            win.addMouseListener(__landscape);   // clics, drag, molette (NEWT MouseListener = tout-en-un)
            win.addKeyListener(__landscape);

            // Taille fenêtrée par défaut = 2/3 de l'écran. NEWT ne consomme pas Tab
            // (pas de focus traversal AWT à désactiver).
            Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
            win.setSize(Math.max(640, screen.width * 2 / 3), Math.max(480, screen.height * 2 / 3));

            win.addWindowListener(new com.jogamp.newt.event.WindowAdapter() {
                @Override public void windowDestroyNotify(WindowEvent e) {
                    if (animator != null) animator.stop();
                    // halt() au lieu de exit() : court-circuite les shutdown hooks
                    // JOGL/AWT qui plantent natif sur WSLg/Mesa au teardown du contexte.
                    Runtime.getRuntime().halt(0);
                }
            });

            animator = new Animator(win);
            win.setVisible(true);
            animator.start();
            win.requestFocus();

            // Démarrage en FENÊTRÉ (le plein écran NEWT sur WSLg n'est pas garanti) ;
            // Échap bascule plein écran ⇄ fenêtré. Fermer la fenêtre quitte.
            isFullscreen = false;

            return __landscape;
        }

        /** Bascule plein écran ⇄ fenêtré (touche Échap). */
        static void toggleFullscreen() {
            if (glWindow == null) return;
            isFullscreen = !isFullscreen;
            glWindow.setFullscreen(isFullscreen);
            glWindow.requestFocus();
        }
        
        

        /**
         * OpenGL Init method
         */
        //@Override
        public void init(GLAutoDrawable glDrawable) {
                GL2 gl = glDrawable.getGL().getGL2();

                // Enable front face culling (can speed up code, but is not always 
                // GO FAST ???
                
                //double buffer;
         
                gl.glEnable(GL2.GL_DOUBLEBUFFER);
                glDrawable.setAutoSwapBufferMode(true);
                
                // Enable VSync
                gl.setSwapInterval(1);
                // END of GO FAST ???


                gl.glShadeModel(GLLightingFunc.GL_SMOOTH);
                gl.glClearColor(0.0f,0.0f,0.0f,0.0f);// couleur de l'espace
                gl.glClearDepth(1.0f);
                gl.glEnable(GL.GL_DEPTH_TEST);
                gl.glDepthFunc(GL.GL_LEQUAL);
                gl.glHint(GL2ES1.GL_PERSPECTIVE_CORRECTION_HINT, GL.GL_NICEST);
                
                //Culling - display only triangles facing the screen
                gl.glCullFace(GL.GL_FRONT);
                gl.glEnable(GL.GL_CULL_FACE);

                // trucs d'alex
                gl.glEnable(GL.GL_DITHER);

                // Normalisation auto des normales (sécurité, nos normales sont déjà unitaires
                // mais les transformations de modelview peuvent les distordre).
                gl.glEnable(GL2.GL_NORMALIZE);

                // Brouillard linéaire — la couleur sera mise à jour chaque frame par
                // updateSunAndSky pour coller au ciel courant.
                gl.glFogi(GL2.GL_FOG_MODE, GL2.GL_LINEAR);
                gl.glFogf(GL2.GL_FOG_START, 180f);
                gl.glFogf(GL2.GL_FOG_END,   380f);
                gl.glHint(GL2.GL_FOG_HINT, GL.GL_NICEST);

                // Chargement de la texture de bruit du sol. Chemin relatif au CWD
                // (run.sh fait cd ProjectEnvironnement-3D/).
                try {
                        groundTex = TextureIO.newTexture(new File("textures/noise.png"), true);
                        groundTex.setTexParameteri(gl, GL.GL_TEXTURE_MIN_FILTER, GL.GL_LINEAR_MIPMAP_LINEAR);
                        groundTex.setTexParameteri(gl, GL.GL_TEXTURE_MAG_FILTER, GL.GL_LINEAR);
                        groundTex.setTexParameteri(gl, GL.GL_TEXTURE_WRAP_S, GL.GL_REPEAT);
                        groundTex.setTexParameteri(gl, GL.GL_TEXTURE_WRAP_T, GL.GL_REPEAT);
                        // Filtrage anisotrope si disponible : net aux angles rasants.
                        try {
                                float[] maxAniso = new float[1];
                                gl.glGetFloatv(0x84FF /* GL_MAX_TEXTURE_MAX_ANISOTROPY_EXT */, maxAniso, 0);
                                if (maxAniso[0] > 1f) {
                                        groundTex.setTexParameterf(gl, 0x84FE /* GL_TEXTURE_MAX_ANISOTROPY_EXT */, maxAniso[0]);
                                }
                        } catch (Exception ignore) { /* extension absente, on s'en passe */ }
                } catch (Exception e) {
                        System.err.println("Texture du sol introuvable, rendu en couleurs plates : " + e.getMessage());
                        groundTex = null;
                }

                // Charge le modèle OBJ du sapin (tronc cylindre + cône feuillage,
                // exporté depuis Blender) — utilisé dans la passe 2b.
                objects.vegetation.Tree.initModel(gl);

                // Charge le modèle GLB du mouton chibi (modddif.com, 10k faces) —
                // rendu dans la passe 2c après glEnd, comme l'arbre.
                agents.Mouton.initModel(gl);

                // Charge le modèle GLB du loup (modddif.com, 1k faces) — passe 2d.
                agents.Loup.initModel(gl);

                // Compile la display list des étoiles (positions précalculées
                // dans initLandscape). Évite 150 glVertex3f en immediate mode/frame.
                compileStarList(gl);
        }

        /**
         * Compile les positions d'étoiles en display list (glBegin GL_POINTS).
         * La couleur et la rotation sont posées DEHORS par l'appelant, donc le
         * fade jour/nuit + la dérive terrestre fonctionnent.
         */
        private void compileStarList(GL2 gl) {
            if (starPositions == null) return;
            if (starListId != 0) gl.glDeleteLists(starListId, 1);
            starListId = gl.glGenLists(1);
            gl.glNewList(starListId, GL2.GL_COMPILE);
            gl.glBegin(GL.GL_POINTS);
            for (int i = 0; i < starPositions.length; i++) {
                gl.glVertex3f(starPositions[i][0], starPositions[i][1], starPositions[i][2]);
            }
            gl.glEnd();
            gl.glEndList();
        }

        // ----- Cycle jour/nuit centralisé -----

        // Couleurs clé interpolées suivant l'altitude du soleil dans [-1, +1]
        // (-1 = nadir / 0 = horizon / +1 = zénith).
        private static final float[] SUN_NIGHT   = {0.04f, 0.05f, 0.12f}; // lumière nocturne (bleu profond)
        private static final float[] SUN_HORIZON = {1.00f, 0.55f, 0.30f}; // lever/coucher chaud
        private static final float[] SUN_NOON    = {1.00f, 0.97f, 0.88f}; // midi blanc-chaud
        private static final float[] SKY_NIGHT   = {0.02f, 0.03f, 0.10f};
        private static final float[] SKY_HORIZON = {0.95f, 0.55f, 0.40f};
        private static final float[] SKY_NOON    = {0.53f, 0.81f, 0.92f};

        // Direction du soleil dans le repère terrain (x=est, y=nord, z=haut).
        private final float[] sunDirGL = new float[]{0f, 0f, 1f, 0f}; // w=0 = lumière directionnelle
        private final float[] sunAmbient  = new float[]{0.2f, 0.2f, 0.2f, 1f};
        private final float[] sunDiffuse  = new float[]{0.8f, 0.8f, 0.8f, 1f};

        private static float lerp(float a, float b, float t) { return a + (b - a) * t; }

        private static float smoothstep(float e0, float e1, float x) {
            float t = Math.max(0f, Math.min(1f, (x - e0) / (e1 - e0)));
            return t * t * (3f - 2f * t);
        }

        private static void lerp3(float[] out, float[] a, float[] b, float t) {
            out[0] = lerp(a[0], b[0], t);
            out[1] = lerp(a[1], b[1], t);
            out[2] = lerp(a[2], b[2], t);
        }

        /**
         * Calcule le phase courante du cycle jour/nuit à partir de it, en déduit la
         * direction et la couleur du soleil + la couleur du ciel et du brouillard,
         * et applique glClearColor + glFogfv. Le glLightfv POSITION/COLOR doit être
         * fait par l'appelant APRÈS les transformations caméra (sinon la direction
         * est interprétée en eye-space et le soleil suit la caméra).
         */
        private void updateSunAndSky(GL2 gl) {
            int fullCycle = 2 * _myWorld.getDureeJour(); // un cycle complet jour+nuit
            float t = (it % fullCycle) / (float) fullCycle; // [0, 1)
            currentDayPhase = t; // exposé pour le HUD timer et la rotation des étoiles
            // Cycle asymétrique configurable via config.dayFractionRatio.
            // Avec l'horloge "6 + t × 24" : t=0 = aube (6h), t=dayFraction = crépuscule
            // (= 6h + dayFraction × 24h), t=1 = retour à 6h. Zénith au milieu visuel
            // du jour (légèrement décalé du midi astronomique pour garder un jour
            // symétrique autour de son zénith).
            float dayFraction = (config != null) ? config.dayFractionRatio
                                                 : SimulationConfig.getInstance().dayFractionRatio;
            if (dayFraction < 0.05f) dayFraction = 0.05f;  // garde-fou : pas de jour ⇒ div par 0
            if (dayFraction > 0.95f) dayFraction = 0.95f;  // garde-fou : pas de nuit ⇒ div par 0
            float sunAngle;
            if (t < dayFraction) {
                sunAngle = (float) (Math.PI * t / dayFraction);              // 0 → π sur la phase jour
            } else {
                sunAngle = (float) (Math.PI + Math.PI * (t - dayFraction) / (1f - dayFraction)); // π → 2π sur la phase nuit
            }
            float sunDirX = (float) Math.cos(sunAngle); // est (+1) à ouest (-1)
            float sunDirZ = (float) Math.sin(sunAngle); // -1 nadir, +1 zénith
            // un peu de tilt nord pour que les ombres ne soient pas purement E-O
            float sunDirY = 0.25f;
            float invLen = 1f / (float) Math.sqrt(sunDirX*sunDirX + sunDirY*sunDirY + sunDirZ*sunDirZ);
            sunDirX *= invLen; sunDirY *= invLen; sunDirZ *= invLen;

            sunDirGL[0] = sunDirX;
            sunDirGL[1] = sunDirY;
            sunDirGL[2] = sunDirZ;
            sunDirGL[3] = 0f; // directionnelle

            // sunAlt sert d'index pour les couleurs : <0 = nuit, ~0 = horizon, >0 = jour
            float sunAlt = sunDirZ;

            float[] sunColor = new float[3];
            float[] sky = new float[3];
            if (sunAlt <= 0f) {
                float w = smoothstep(-0.25f, 0.0f, sunAlt); // 0 nuit profonde → 1 horizon
                lerp3(sunColor, SUN_NIGHT, SUN_HORIZON, w);
                lerp3(sky,      SKY_NIGHT, SKY_HORIZON, w);
            } else {
                float w = smoothstep(0.0f, 0.30f, sunAlt); // 0 horizon → 1 midi
                lerp3(sunColor, SUN_HORIZON, SUN_NOON, w);
                lerp3(sky,      SKY_HORIZON, SKY_NOON, w);
            }

            // Intensité globale : 0.15 nuit profonde, 1.0 dès que le soleil dépasse l'horizon.
            float intensity = lerp(0.15f, 1f, smoothstep(-0.10f, 0.20f, sunAlt));

            // Facteur de nuit : 0 le jour, 1 quand le soleil est bien sous l'horizon.
            // Utilisé pour les étoiles et les lampes lunaires.
            currentNightFactor = 1f - smoothstep(-0.20f, 0.10f, sunAlt);

            // Synchronise le flag getJour() de l'agent layer avec la phase visuelle.
            // jour=1 quand le soleil est au-dessus de l'horizon, 0 sinon.
            // Cela remplace l'ancien toggle (it % dureeJour == 0) et permet au clic droit
            // de faire passer instantanément du jour à la nuit (en shiftant 'it').
            _myWorld.setJour(sunAlt > 0f ? 1 : 0);

            // Composantes de la lumière. Le budget total à midi est:
            //   global_ambient (0.2 par défaut) + sun_ambient + sun_diffuse*dot(N,L)
            // Avec ambient=0.25 et diffuse=0.55, la somme max = 0.2 + 0.25 + 0.55 = 1.0
            // pile au clamp OpenGL, donc plus de saturation des cellules à couleur de
            // base élevée (sable RGB ~ 1.0, 0.86, 0.72).
            sunAmbient[0] = sunColor[0] * intensity * 0.25f;
            sunAmbient[1] = sunColor[1] * intensity * 0.25f;
            sunAmbient[2] = sunColor[2] * intensity * 0.25f;
            sunDiffuse[0] = sunColor[0] * intensity * 0.55f;
            sunDiffuse[1] = sunColor[1] * intensity * 0.55f;
            sunDiffuse[2] = sunColor[2] * intensity * 0.55f;

            skyColor[0] = sky[0]; skyColor[1] = sky[1]; skyColor[2] = sky[2]; skyColor[3] = 1f;

            // Couleur du ciel et du brouillard pour cette frame.
            gl.glClearColor(skyColor[0], skyColor[1], skyColor[2], 1f);
            gl.glFogfv(GL2.GL_FOG_COLOR, skyColor, 0);

            // GLOBAL AMBIENT time-dependent : très sombre + bleuté la nuit, neutre le
            // jour. C'est ce qui "écrase" la nuit (sans ça, la valeur par défaut 0.2
            // partout maintient le terrain visible même en pleine nuit).
            //   nuit profonde : (0.03, 0.04, 0.08)  ← "lueur d'étoiles très faible"
            //   horizon       : (0.18, 0.12, 0.08)  ← teinte chaude assistée
            //   midi          : (0.20, 0.20, 0.20)  ← ambient neutre actuel
            float[] amb = new float[3];
            final float[] AMB_NIGHT   = {0.08f, 0.10f, 0.16f};
            final float[] AMB_HORIZON = {0.18f, 0.12f, 0.08f};
            final float[] AMB_DAY     = {0.20f, 0.20f, 0.20f};
            if (sunAlt <= 0f) {
                float w = smoothstep(-0.25f, 0.0f, sunAlt);
                lerp3(amb, AMB_NIGHT, AMB_HORIZON, w);
            } else {
                float w = smoothstep(0.0f, 0.30f, sunAlt);
                lerp3(amb, AMB_HORIZON, AMB_DAY, w);
            }
            float[] globalAmbient = {amb[0], amb[1], amb[2], 1f};
            gl.glLightModelfv(GL2.GL_LIGHT_MODEL_AMBIENT, globalAmbient, 0);

            // Brouillard plus proche la nuit pour réduire la portée visuelle (le jeu
            // utilise GL_FOG_LINEAR donc on bouge fogStart/fogEnd). Plein jour : 180/380
            // comme avant. Pleine nuit : 110/240. Interpolé continûment via nightFactor
            // pour suivre le coucher/lever de soleil.
            float nfFog = currentNightFactor;
            float fogStart = 180f * (1f - nfFog) + 110f * nfFog;
            float fogEnd   = 380f * (1f - nfFog) + 240f * nfFog;
            // Synchronisation avec la distance de vue : si la fenêtre de rendu est
            // plus COURTE que le brouillard, on rapproche le brouillard d'autant
            // (proportionnel) pour cacher le bord. Jamais au-delà du défaut.
            if (config != null && config.viewDistanceCells > 0 && stepX > 0f) {
                float viewWorld = config.viewDistanceCells * stepX;
                float fogScale  = Math.min(1f, viewWorld / FOG_END_DAY);
                fogStart *= fogScale;
                fogEnd   *= fogScale;
            }
            gl.glFogf(GL2.GL_FOG_START, fogStart);
            gl.glFogf(GL2.GL_FOG_END,   fogEnd);
        }

        /**
         * Rend les étoiles comme des GL_POINTS additifs. Visibles uniquement la
         * nuit (modulées par currentNightFactor). En VIEW_FROM_ABOVE, on skippe
         * (on ne voit pas le ciel). Le fog est sauvé/restauré proprement pour
         * ne pas perturber le rendu suivant.
         */
        private void displayStars(GL2 gl) {
            if (VIEW_FROM_ABOVE) return; // pas de ciel visible
            if (currentNightFactor < 0.05f || starListId == 0) return;

            boolean fogWas = gl.glIsEnabled(GL2.GL_FOG);
            boolean lightingWas = gl.glIsEnabled(GL2.GL_LIGHTING);

            gl.glDisable(GL2.GL_LIGHTING);
            gl.glDisable(GL2.GL_FOG);
            gl.glDisable(GL.GL_TEXTURE_2D);
            gl.glEnable(GL.GL_BLEND);
            gl.glBlendFunc(GL.GL_ONE, GL.GL_ONE); // additif
            gl.glDepthMask(false);
            gl.glPointSize(2f);

            float b = currentNightFactor;
            gl.glColor3f(b, b, b);

            // Dérive lente du ciel : rotation 360° sur un cycle jour+nuit complet
            // (= rotation terrestre stylisée). L'observateur est au pôle = axe Z.
            gl.glPushMatrix();
            // Vitesse de dérive : ÷3 par rapport à un tour-par-cycle (120° au lieu de
            // 360°) → mouvement plus subtil, perceptible mais pas étourdissant.
            gl.glRotatef(120f * currentDayPhase, 0f, 0f, 1f);
            gl.glCallList(starListId);
            gl.glPopMatrix();

            // Restore exact previous state
            gl.glDepthMask(true);
            gl.glDisable(GL.GL_BLEND);
            if (fogWas) gl.glEnable(GL2.GL_FOG);
            if (lightingWas) gl.glEnable(GL2.GL_LIGHTING);
        }

        /**
         * Pose les 6 lampes lunaires (LIGHT2..LIGHT7) en world-space. Recalcul
         * de position à chaque frame : la cellule cible est fixe (moonLightCells)
         * mais sa position rendue dépend de movingX/Y, comme le terrain.
         * Couleur diffuse bleutée modulée par currentNightFactor.
         */
        private void applyMoonLights(GL2 gl) {
            if (moonLightCells == null) return;

            final int[] LIGHT_IDS = {
                GL2.GL_LIGHT2, GL2.GL_LIGHT3, GL2.GL_LIGHT4,
                GL2.GL_LIGHT5, GL2.GL_LIGHT6, GL2.GL_LIGHT7
            };
            float nf = currentNightFactor;
            int dxCA = dxView - 1;
            int dyCA = dyView - 1;

            for (int i = 0; i < N_MOON_LIGHTS; i++) {
                int lightId = LIGHT_IDS[i];
                if (nf < 0.05f) {
                    gl.glDisable(lightId);
                    continue;
                }
                int cellX = moonLightCells[i][0];
                int cellY = moonLightCells[i][1];

                // Viewport coords = position rendue à l'écran (compte tenu de movingX/Y)
                int vx = ((cellX - movingX) % dxCA + dxCA) % dxCA;
                int vy = ((cellY - movingY) % dyCA + dyCA) % dyCA;
                float wx = offset + vx * stepX;
                float wy = offset + vy * stepY;
                // Lampes encore plus hautes → cône d'éclairage très vertical, empreinte
                // au sol large mais douce, plus de cramage central.
                float wz = (float)(landscape[cellX % dxView][cellY % dyView] * heightBooster) * heightFactor + 24f;
                if (wz < 24f) wz = 24f;

                float[] pos = new float[]{wx, wy, wz, 1f};
                // Diffuse réduite (centre n'est plus brûlé, halo plus subtil).
                float[] diffuse = new float[]{
                    0.28f * nf,
                    0.34f * nf,
                    0.48f * nf,
                    1f
                };
                float[] noAmbient = new float[]{0f, 0f, 0f, 1f};

                gl.glLightfv(lightId, GL2.GL_POSITION, pos, 0);
                gl.glLightfv(lightId, GL2.GL_DIFFUSE, diffuse, 0);
                gl.glLightfv(lightId, GL2.GL_AMBIENT, noAmbient, 0);
                // Atténuation plus lâche → portée ~3× plus large qu'avant.
                gl.glLightf (lightId, GL2.GL_CONSTANT_ATTENUATION, 1.0f);
                gl.glLightf (lightId, GL2.GL_LINEAR_ATTENUATION,  0.04f);
                gl.glLightf (lightId, GL2.GL_QUADRATIC_ATTENUATION, 0.001f);
                gl.glEnable(lightId);
            }
        }

        /**
         * Émet un sommet du plan d'eau (coin {@code ci} ∈ {0:NO, 1:NE, 2:SE,
         * 3:SO} de la case (x,y)) à z=0, coloré selon la profondeur moyenne aux
         * 4 cases du coin (bordure claire → large bleu nuit), assombri jour/nuit.
         */
        private void emitWaterVertex(GL2 gl, int x, int y, int ci, int nx, int ny, double maxD, float dayF) {
        	int xIt   = (ci==1||ci==2) ? 1   : 0;
        	int yIt   = (ci==0||ci==1) ? 1   : 0;
        	float xSign = (ci==1||ci==2) ? 1f : -1f;
        	float ySign = (ci==0||ci==1) ? 1f : -1f;
        	int vx = x+xIt+movingX, vy = y+yIt+movingY;
        	double dsum = 0;
        	int[] dx = { -1, 0, -1, 0 }, dy = { -1, -1, 0, 0 };
        	for (int k = 0 ; k < 4 ; k++) {
        		double h = _myWorld.getCellHeight(((vx+dx[k])%nx+nx)%nx, ((vy+dy[k])%ny+ny)%ny);
        		if (h < 0) dsum += -h;
        	}
        	float t = (float)Math.min(1.0, (dsum/4.0) / (maxD*WATER_DEPTH_SAT));
        	gl.glColor4f(
        		(WATER_SHALLOW[0] + (WATER_DEEP[0]-WATER_SHALLOW[0])*t) * dayF,
        		(WATER_SHALLOW[1] + (WATER_DEEP[1]-WATER_SHALLOW[1])*t) * dayF,
        		(WATER_SHALLOW[2] + (WATER_DEEP[2]-WATER_SHALLOW[2])*t) * dayF,
        		WATER_ALPHA_SHALLOW + (WATER_ALPHA_DEEP - WATER_ALPHA_SHALLOW) * t);  // opacité ∝ profondeur
        	gl.glVertex3f( offset+x*stepX+xSign*lenX, offset+y*stepY+ySign*lenY, 0f );
        }

        /**
         *
         */
        //@Override
        public void display(GLAutoDrawable gLDrawable) {
           
        		// ** compute FPS
        		
        		if ( System.currentTimeMillis() - lastTimeStamp >= 1000 )
        		{
    				int fps = ( it - lastItStamp ) / 1;   // FPS pour le HUD (log console retiré)
        			lastItStamp = it;
        			lastTimeStamp = System.currentTimeMillis();

        			lastFpsValue = fps;
        		}
        		
        		// ** clean screen

        		final GL2 gl = gLDrawable.getGL().getGL2();

        		// Pilotage manuel : (dé)capture du curseur (mouse-look 3D) et
        		// recalcul du cap de l'agent selon la vue, à chaque frame.
        		updateCursorCapture();
        		applyKeyboardTurn();          // A/E : rotation caméra en pilotage 3D
        		updateManualControlHeading();
        		// Masque le modèle de l'agent piloté en 1ère personne (sinon il
        		// occulte la caméra placée à son œil).
        		if (selectedAgent != null) selectedAgent.hiddenFP = firstPersonControl();

        		// Caméra-follow (Phase 8) : recentre movingX/movingY sur l'agent
        		// sélectionné si la touche `f` a basculé le mode.
        		if (cameraFollow && selectedAgent != null) {
        			int w = _myWorld.getWidth();
        			int h = _myWorld.getHeight();
        			movingX = ((selectedAgent.x - dxView / 2) % w + w) % w;
        			movingY = ((selectedAgent.y - dyView / 2) % h + h) % h;
        		}

        		// Cycle jour/nuit : calcule direction/couleurs du soleil et applique
        		// glClearColor + glFog COLOR. La POSITION/AMBIENT/DIFFUSE de la lumière
        		// sont appliqués plus bas, APRÈS les transforms caméra (cf. note dans
        		// updateSunAndSky).
        		updateSunAndSky(gl);

                gl.glClear(GL.GL_COLOR_BUFFER_BIT);
                gl.glClear(GL.GL_DEPTH_BUFFER_BIT);

                // Vue de dessus : passer en perspective « téléobjectif »
                // (FOV étroit + grande distance). Garde un peu de parallaxe
                // (les objets en altitude se décalent légèrement) sans
                // l'énorme effet d'une perspective standard à courte distance.
                // Le rapport FOV/distance fait que le décalage est ~5× plus
                // petit qu'en frustum « normal ». Push/pop autour du rendu 3D
                // pour ne pas casser overlays 2D ni picking.
                boolean topDownOrthoPushed = false;
                if (VIEW_FROM_ABOVE) {
                    gl.glMatrixMode(GLMatrixFunc.GL_PROJECTION);
                    gl.glPushMatrix();
                    gl.glLoadIdentity();
                    float aspect = (float) viewportWidth / (float) viewportHeight;
                    float telephotoFh = TOPDOWN_FH;  // FOV vertical ~4.6° (très téléobjectif → parallax très faible)
                    float telephotoFw = telephotoFh * aspect;
                    gl.glFrustumf(-telephotoFw, telephotoFw, -telephotoFh, telephotoFh,
                            1.0f, 60000.0f);  // far agrandi : la distance de cadrage auto peut être grande
                    gl.glMatrixMode(GLMatrixFunc.GL_MODELVIEW);
                    topDownOrthoPushed = true;
                }
                gl.glLoadIdentity();

                // L'ancien bloc d'affichage FPS / heure était dessiné ici via
                // glWindowPos2d. Il est remplacé par le HUD overlay 2D, dessiné en
                // fin de frame pour rester par-dessus la scène 3D (cf. plus bas).

                // ** render all : active la lumière, les couleurs seront posées après
                // les transformations caméra (pour que la direction soleil soit en world-space).

                if ( MY_LIGHT_RENDERING ){
	                gl.glEnable(GL2.GL_LIGHT1);
	                gl.glEnable(GL2.GL_LIGHTING);
	                gl.glEnable(GL2.GL_COLOR_MATERIAL);
                }
                else{
                    gl.glDisable(GL2.GL_LIGHT1);
                    gl.glDisable(GL2.GL_LIGHTING);
                    gl.glDisable(GL2.GL_COLOR_MATERIAL);
                }
                
                // ***
                
                //gl.glTranslatef(0.0f, 0.0f, -100.0f); // 0,0,-5
 
                //rotate on the three axis
                //gl.glRotatef(rotateT, 1.0f, 0.0f, 0.0f);
                //gl.glRotatef(rotateT, 0.0f, 1.0f, 0.0f);
                //gl.glRotatef(rotateT, 0.0f, 0.0f, 1.0f);
                /**/
                //gl.glPushMatrix(); gl.glPopMatrix();
                /**/
                /* DEBUG: as seen from above 
                gl.glTranslatef(0.0f, 0.0f, 500.0f); // 0,0,-5
                //gl.glRotatef(rotateT, 0.0f, 0.0f, 1.0f);
                // DEBUG
                /**/

                /*
                // DEBUG
                gl.glTranslatef(0.0f, 0.0f, -500.0f); // 0,0,-5
                //gl.glRotatef(rotateT, 0.0f, 0.0f, 1.0f);
                //gl.glRotatef(-90.f, 1.0f, 0.0f, 0.0f);
                gl.glRotatef(-90.f, 0.0f, 0.0f, 1.0f);
                // DEBUG
                /**/

                if ( VIEW_FROM_ABOVE == true )
                {
                	// Vue de dessus : pas de brouillard (le terrain est loin de la caméra).
                	// Zoom AUTO : on cadre exactement la fenêtre de rendu
                	// (viewDistanceCells) pour qu'elle REMPLISSE l'écran — fini les
                	// bandes noires, et les agents hors fenêtre tombent hors écran
                	// (cullés par isAgentOnScreen). La taille de la map est inchangée.
                	// max(1,aspect) → on couvre le grand côté de l'écran. La molette
                	// modifie viewDistanceCells (cf. mouseWheelMoved) pour (dé)zoomer.
                	float tdAspect = (float) viewportWidth / (float) viewportHeight;
                	int   tdR      = Math.min(topDownViewCells, Math.max(8, Math.min(dxView, dyView)/2 - 5));
                	float tdFit    = (tdR * stepX) / (TOPDOWN_FH * Math.max(1f, tdAspect));
                	gl.glTranslatef(0.0f, 0.0f, -tdFit);
                	gl.glDisable(GL2.GL_FOG);
                }
                else if ( firstPersonControl() )
                {
                	// Vue PREMIÈRE PERSONNE (pilotage 3D) : caméra placée à l'œil
                	// de l'agent (pas de recul orbital), orientée par le mouse-look
                	// (rotateX = yaw, cameraPitch = tangage). Le centrage XY sur
                	// l'agent est déjà fait via movingX/movingY (cameraFollow forcé).
                	int ax = ((selectedAgent.x % dxView) + dxView) % dxView;
                	int ay = ((selectedAgent.y % dyView) + dyView) % dyView;
                	float agentAltitudeWorld =
                			(float)(_myWorld.getCellHeight(ax, ay) * heightBooster) * heightFactor;
                	gl.glRotatef(cameraPitch, 1.0f, 0.0f, 0.0f);
                	gl.glRotatef(rotateX,     0.0f, 1.0f, 0.0f);
                	gl.glRotatef(-90.f,       1.0f, 0.0f, 0.0f);
                	gl.glTranslatef(0.0f, 0.0f, -agentAltitudeWorld - FP_EYE_HEIGHT);
                	gl.glEnable(GL2.GL_FOG);
                }
                else if ( cameraFollow && selectedAgent != null )
                {
                	// Orbit caméra 3e personne autour de l'agent suivi (clic
                	// gauche-drag modifie orbitYaw / orbitPitch). Le centrage
                	// XY sur l'agent est géré via movingX/movingY plus haut.
                	// Centrage Z : on translate la scène de -agentAltitudeWorld
                	// pour que l'altitude du sol sous l'agent soit ramenée à 0,
                	// sinon la caméra reste au niveau de la mer et passe sous
                	// les montagnes quand l'agent grimpe.
                	int ax = ((selectedAgent.x % dxView) + dxView) % dxView;
                	int ay = ((selectedAgent.y % dyView) + dyView) % dyView;
                	float agentAltitudeWorld =
                			(float)(_myWorld.getCellHeight(ax, ay) * heightBooster) * heightFactor;
                	gl.glTranslatef(0.0f, 0.0f, -orbitRadius);
                	gl.glRotatef(orbitPitch, 1.0f, 0.0f, 0.0f);
                	gl.glRotatef(orbitYaw,   0.0f, 1.0f, 0.0f);
                	gl.glRotatef(-90.f, 1.0f, 0.0f, 0.0f);
                	// Focal point : altitude du sol sous l'agent + offset vertical
                	// PROPORTIONNEL au radius (cf. ORBIT_FOCAL_Z_RATIO) pour garder
                	// le sujet centré à tous les niveaux de zoom (style Minecraft).
                	float focalZ = Math.max(ORBIT_FOCAL_Z_MIN, orbitRadius * ORBIT_FOCAL_Z_RATIO);
                	gl.glTranslatef(0.0f, 0.0f, -agentAltitudeWorld - focalZ);
                	gl.glEnable(GL2.GL_FOG);
                }
                else
                {
                	// 3D libre : rotation FPS yaw + pitch, translateZ pour la
                	// hauteur, cameraDistance3D pour la distance au point
                	// regardé (modulée à la molette).
                	gl.glTranslatef(0.0f, translateZ, cameraDistance3D);
                	gl.glRotatef(cameraPitch, 1.0f, 0.0f, 0.0f);
                	gl.glRotatef(rotateX,     0.0f, 1.0f, 0.0f);
                	gl.glRotatef(-90.f, 1.0f, 0.0f, 0.0f);
                	gl.glEnable(GL2.GL_FOG);
                }

                // Soleil : direction et couleur appliquées MAINTENANT, dans le repère
                // world-space après les rotations caméra. Une lumière directionnelle
                // (w=0) est transformée par la 3x3 rotation du modelview, ce qui
                // garantit que le soleil reste fixe dans le ciel pendant que la
                // caméra tourne (rotateX).
                if ( MY_LIGHT_RENDERING ) {
                	gl.glLightfv(GL2.GL_LIGHT1, GL2.GL_POSITION, sunDirGL, 0);
                	gl.glLightfv(GL2.GL_LIGHT1, GL2.GL_AMBIENT,  sunAmbient, 0);
                	gl.glLightfv(GL2.GL_LIGHT1, GL2.GL_DIFFUSE,  sunDiffuse, 0);

                	// Lampes lunaires bleutées (bergerie + wolfHome + 4 random),
                	// éteintes le jour, intensité scaled par currentNightFactor.
                	applyMoonLights(gl);
                }

                // Étoiles dans le ciel : rendues APRÈS transforms caméra et AVANT le
                // terrain (depth write désactivé, donc terrain devant). Visibles
                // uniquement la nuit.
                displayStars(gl);

                // getJour()/setJour() est maintenant synchronisé par updateSunAndSky()
                // selon l'altitude du soleil. Plus de toggle à it%dureeJour ici —
                // le clic droit shift directement 'it' pour basculer.

                it++;
                
                /*
                if ( it % 30 == 0 )//&& it != 0)
                	movingIt++;
                movingIt=0;
                movingIt=dxView+1;
                */
                
        		// ** update Cellular Automata — gelé tant que le menu de lancement
        		//    n'a pas été validé (Phase 6).
        		// Module 1 (refonte 2026-05) : on exécute 0..MAX_STEPS_PER_FRAME
        		// steps selon le temps réel écoulé (fixed-timestep accumulator).
            	if (config == null || !config.awaitingStart) {
            		int hz = (config != null) ? config.simulationHz : 20;
            		int stepsThisFrame = timeKeeper.stepsToRun(hz);
            		for (int s = 0; s < stepsThisFrame; s++) {
            			_myWorld.step();
            			// Échantillonnage pour le graphe de populations (Phase 9).
            			if (_myWorld instanceof WorldOfCells) {
            				populationGraph.sample((WorldOfCells) _myWorld);
            			}
            		}
            	}

        		// ** draw everything
    		//
    		// PASSE 1a : terrain texturé pour les cellules de TERRE (texture de bruit
    		//            modulée par glColor pour préserver le cycle jour/nuit).
    		// PASSE 1b : terrain en couleur plate pour les cellules d'EAU (l'eau ne
    		//            doit pas avoir de grain visible).
    		// PASSE 2  : objets (Tree/Grass/Stone/Lave + UniqueObjects), texture
    		//            désactivée car leurs glVertex3f n'émettent pas de glTexCoord2f.

                // Fenêtre de rendu : on ne dessine que les cases proches de la
                // caméra (centrée sur dxView/2, dyView/2). R vient du slider
                // « Distance de vue » (défaut dérivé du brouillard). Purement
                // visuel — la simulation (world.step) tourne sur toute la carte.
                // Rayon de la fenêtre : la vue de dessus a son PROPRE zoom
                // (topDownViewCells), découplé de la distance de vue 3D
                // (config.viewDistanceCells) → l'un n'affecte pas l'autre.
                int viewR;
                if (VIEW_FROM_ABOVE) {
                    // -5 cases de marge : au dézoom max on ne voit pas le bord du terrain.
                    viewR = Math.min(topDownViewCells, Math.max(8, Math.min(dxView, dyView)/2 - 5));
                } else {
                    viewR = (config != null && config.viewDistanceCells > 0) ? config.viewDistanceCells : dxView;
                }
                int[] xWin = ViewWindow.of(dxView, viewR);
                int[] yWin = ViewWindow.of(dyView, viewR);

                final boolean useTex = (groundTex != null);
                if (useTex) {
                        gl.glEnable(GL.GL_TEXTURE_2D);
                        // GL_ADD : la texture (mostly black + highlights clairs) S'AJOUTE
                        // à la couleur de cellule au lieu de la multiplier. Donne des
                        // éclats brillants sur le sable au lieu de taches sombres.
                        gl.glTexEnvi(GL2.GL_TEXTURE_ENV, GL2.GL_TEXTURE_ENV_MODE, GL2.GL_ADD);
                        groundTex.bind(gl);
                }

            	gl.glBegin(GL2.GL_QUADS);

                for ( int x = xWin[0] ; x < xWin[1] ; x++ ){
                	for ( int y = yWin[0] ; y < yWin[1] ; y++ )
                	{
           			 	// Technique des jeux : on dessine TOUTES les cases, y compris
           			 	// sous l'eau (le terrain plonge en continu → fond marin).
           			 	// L'eau est ensuite un plan translucide unique (passe 1b),
           			 	// la ligne d'eau étant lissée per-pixel par le depth-buffer.
	                	if ( MY_LIGHT_RENDERING_HIGHT && MY_LIGHT_RENDERING && VIEW_FROM_ABOVE != true)
                        {
	                		float[] lightColorSpecular = {0.8f, 0.8f, 0.3f, 1f};
	                        gl.glMaterialfv( GL.GL_FRONT_AND_BACK, GL2.GL_SPECULAR, lightColorSpecular, 0 );
	                        gl.glMateriali( GL.GL_FRONT_AND_BACK, GL2.GL_SHININESS, 5 );
                        }

                        for ( int i = 0 ; i < 4 ; i++ )
                        {
                        	int xIt = i==1||i==2?1:0;
                        	int yIt = i==0||i==1?1:0;
                        	float xSign = i==1||i==2?1.f:-1.f;
                        	float ySign = i==0||i==1?1.f:-1.f;

                            float zValue = 0.f;

                        	if ( VIEW_FROM_ABOVE == false )
	                        {
	                        	// Plus de clamp à 0 : les sommets sous l'eau gardent leur
	                        	// altitude réelle (négative) → le fond marin est continu.
	                        	double altitude = _myWorld.getCellHeight((x+xIt+movingX) % (dxView-1), (y+yIt+movingY) % (dyView-1)) * heightBooster;
	                        	zValue = heightFactor*(float)altitude * smoothFactor[i];
	                        }

	                        // UV = position monde * scale, continue à travers les cellules.
	                        // GL_REPEAT s'occupe du wrap, donc seamless naturellement.
	                        float u = (x + xIt + movingX) * TEX_TILES_PER_CELL;
	                        float v = (y + yIt + movingY) * TEX_TILES_PER_CELL;

	                        // Normale précalculée du sommet — donne du relief sous
	                        // la lumière directionnelle (face éclairée / face ombragée).
	                        float[] n = normals[(x+xIt+movingX) % dxView][(y+yIt+movingY) % dyView];
	                        gl.glNormal3f(n[0], n[1], n[2]);
	                        gl.glTexCoord2f(u, v);
	                        float[] vcol = ((worlds.WorldOfCells)_myWorld).getVertexTerrainColor(x+xIt+movingX, y+yIt+movingY);
	                        // Marqueur de forêt : on TEINTE le sol directement (suit le
	                        // relief, toujours visible) au lieu d'un disque flottant.
	                        ((worlds.WorldOfCells)_myWorld).applyForestTint(x+xIt+movingX, y+yIt+movingY, vcol);
	                        gl.glColor3f(vcol[0], vcol[1], vcol[2]);
	                        gl.glVertex3f( offset+x*stepX+xSign*lenX, offset+y*stepY+ySign*lenY, zValue);
                        }
                	}
        		}

	            gl.glEnd();

	            if (useTex) gl.glDisable(GL.GL_TEXTURE_2D);

	            // ** PASSE 1b : EAU — quads à z=0 colorés PAR PROFONDEUR (bordure claire
	            //    -> large bleu nuit), lighting OFF (couleur stable, pas de scintillement
	            //    spéculaire selon l'angle), assombris jour/nuit, semi-opaques. Dessinés
	            //    sur les cases immergées + 1 case de rivage (couvre la plage qui plonge
	            //    sous 0). polygon-offset -> l'eau gagne proprement le depth-test sur le
	            //    fond marin peu profond (anti z-fighting). En vue de dessus : sauté (sol
	            //    plat à z=0, déjà coloré par sommet) pour ne pas recouvrir la carte.
	            if ( VIEW_FROM_ABOVE == false )
	            {
	            	gl.glPushAttrib(GL2.GL_ENABLE_BIT | GL2.GL_DEPTH_BUFFER_BIT | GL2.GL_POLYGON_BIT);
	            	gl.glDisable(GL2.GL_LIGHTING);   // couleur stable (indépendante de l'angle)
	            	gl.glDisable(GL.GL_TEXTURE_2D);
	            	gl.glEnable(GL.GL_BLEND);
	            	gl.glBlendFunc(GL.GL_SRC_ALPHA, GL.GL_ONE_MINUS_SRC_ALPHA);
	            	gl.glDisable(GL.GL_CULL_FACE);
	            	// La surface d'eau ÉCRIT la profondeur : les objets sous l'eau
	            	// (obsidienne sur le fond, etc.) dessinés APRÈS sont alors masqués
	            	// par la surface au lieu d'apparaître par-dessus. Effet bonus : un
	            	// agent à z=-1 voit le HAUT de son corps dépasser (z>0, visible) et
	            	// le bas masqué par la surface → « à moitié immergé » automatique.
	            	gl.glDepthMask(true);
	            	gl.glEnable(GL2.GL_POLYGON_OFFSET_FILL);
	            	gl.glPolygonOffset(-1.0f, -1.0f); // anti z-fighting avec le fond marin peu profond


	            	float dayF = 0.25f + 0.75f * (1f - currentNightFactor);
	            	int nx = dxView-1, ny = dyView-1;
	            	double maxD = -_myWorld.getMinEverHeight(); if (maxD <= 0) maxD = 1;

	            	// MÉTHODE COIN DIAGONAL : l'eau (h<0) = quad plein (2 triangles) ;
	            	// une case de TERRE de rivage avec EXACTEMENT 2 voisins eau
	            	// ADJACENTS reçoit un triangle d'eau qui coupe son coin → la côte
	            	// passe d'un angle droit (90°) à une diagonale (45°).
	            	gl.glBegin(GL2.GL_TRIANGLES);
	            	for ( int x = xWin[0] ; x < xWin[1] ; x++ ) {
	            		for ( int y = yWin[0] ; y < yWin[1] ; y++ ) {
	            			boolean self = _myWorld.getCellHeight(((x+movingX)%nx+nx)%nx, ((y+movingY)%ny+ny)%ny) < 0;
	            			if (self) {
	            				// case eau : quad plein = 2 triangles (0,1,2) + (0,2,3)
	            				emitWaterVertex(gl, x, y, 0, nx, ny, maxD, dayF);
	            				emitWaterVertex(gl, x, y, 1, nx, ny, maxD, dayF);
	            				emitWaterVertex(gl, x, y, 2, nx, ny, maxD, dayF);
	            				emitWaterVertex(gl, x, y, 0, nx, ny, maxD, dayF);
	            				emitWaterVertex(gl, x, y, 2, nx, ny, maxD, dayF);
	            				emitWaterVertex(gl, x, y, 3, nx, ny, maxD, dayF);
	            			} else {
	            				// case terre : coupe diagonale si 2 voisins eau adjacents
	            				boolean wN = _myWorld.getCellHeight(((x+movingX)%nx+nx)%nx, ((y+movingY+1)%ny+ny)%ny) < 0;
	            				boolean wS = _myWorld.getCellHeight(((x+movingX)%nx+nx)%nx, ((y+movingY-1)%ny+ny)%ny) < 0;
	            				boolean wE = _myWorld.getCellHeight(((x+movingX+1)%nx+nx)%nx, ((y+movingY)%ny+ny)%ny) < 0;
	            				boolean wW = _myWorld.getCellHeight(((x+movingX-1)%nx+nx)%nx, ((y+movingY)%ny+ny)%ny) < 0;
	            				int omit = -1;                 // coin de TERRE exclu du triangle d'eau
	            				if      (wN && wE) omit = 3;   // eau N+E → terre = SO (i3)
	            				else if (wE && wS) omit = 0;   // eau E+S → terre = NO (i0)
	            				else if (wS && wW) omit = 1;   // eau S+O → terre = NE (i1)
	            				else if (wW && wN) omit = 2;   // eau O+N → terre = SE (i2)
	            				if (omit >= 0)
	            					for (int ci = 0 ; ci < 4 ; ci++)
	            						if (ci != omit) emitWaterVertex(gl, x, y, ci, nx, ny, maxD, dayF);
	            			}
	            		}
	            	}
	            	gl.glEnd();
	            	gl.glPopAttrib();
	            }
	            
	            // ** PASSE 2 : objets non-texturés

	            gl.glBegin(GL2.GL_QUADS);
	            // Une normale unique pour tous les objets — ils n'ont pas leurs propres
	            // glNormal3f, donc sans ça ils hériteraient de la dernière normale de
	            // l'eau (ou du sol). Ça les met tous sous le même éclairage diffus.
	            gl.glNormal3f(0f, 0f, 1f);

                if ( DISPLAY_OBJECTS == true)
                {
	                for ( int x = xWin[0] ; x < xWin[1] ; x++ ) {
	                	for ( int y = yWin[0] ; y < yWin[1] ; y++ )
	                	{
		                	double height = _myWorld.getCellHeight(x+movingX,y+movingY);
	           			 	int forestState = _myWorld.getForestCAValue(x+movingX,y+movingY);
	           			 	int herbeState = _myWorld.getGrassCAValue(x+movingX,y+movingY);

	                    	float normalizeHeight = ( smoothFactor[0] + smoothFactor[1] + smoothFactor[2] + smoothFactor[3] ) / 4.f * (float)heightBooster * heightFactor;
	                    	nHeihtCommonObj=normalizeHeight;

	                    	// Rendu de la stack de couches (système Layer) :
	                    	// itère du bas vers le haut, en faisant glisser zCursor.
	                    	// Remplace les anciens appels displayObjectStone/Lave
	                    	// (devenus no-op après le refactor).
	                    	float zCursor = (float)(height * normalizeHeight);
	                    	for (objects.Layer __layer : _myWorld.getStack(x + movingX, y + movingY)) {
	                    		switch (__layer.material) {
	                    			case STONE:
	                    			case OBSIDIAN:
	                    			case BASALT:
	                    			case GRANITE:
	                    				// Mêmes 6 faces que la pierre standard, la
	                    				// couleur est dispatché dans StoneBlock
	                    				// selon le material. Module 4 : passe le
	                    				// lineage pour teinter selon l'éruption d'origine.
	                    				objects.blocks.StoneBlock.drawAt(_myWorld, gl, __layer.material,
	                    						x, y, zCursor, __layer.thickness, __layer.lineage,
	                    						offset, stepX, stepY, lenX, lenY,
	                    						movingX, movingY);
	                    				break;
	                    			case LAVA:
	                    				// Module physique cooling (2026-05-27) : seuils dérivés
	                    				// pour le rendu couleur par phase. Utilise SimulationConfig
	                    				// (la variable `config` du Landscape peut être null en mode
	                    				// debug — fallback sur l'instance singleton).
	                    				SimulationConfig _cfgRender = (config != null) ? config : SimulationConfig.getInstance();
	                    				int _solidifyEndTicksRender = Math.max(1,
	                    						(int)(_cfgRender.solidifyEndSec * _cfgRender.simulationHz));
	                    				int _solidifyStartTicksRender = Math.round(_solidifyEndTicksRender * 0.6f);
	                    				objects.blocks.LavaBlock.drawAt(_myWorld, gl,
	                    						x, y, zCursor, __layer.thickness, __layer.state,
	                    						__layer.persistent, __layer.lineage,
	                    						_solidifyStartTicksRender, _solidifyEndTicksRender,
	                    						offset, stepX, stepY, lenX, lenY,
	                    						movingX, movingY);
	                    				break;
	                    		}
	                    		zCursor += __layer.thickness;
	                    	}

	                    	// Végétation : Tree / Grass utilisent leur propre
	                    	// altitude via World.getCellTopAltitude (cf. patch).
	                    	_myWorld.displayObjectTree(_myWorld,gl,forestState, x, y, height, offset, stepX, stepY, lenX, lenY, normalizeHeight,movingX,movingY);
	                    	_myWorld.displayObjectGrass(_myWorld,gl,herbeState, x, y, height, offset, stepX, stepY, lenX, lenY, normalizeHeight,movingX,movingY);
	                	}
	                }

	            	float normalizeHeight = (float)heightBooster * heightFactor;
	            	nHeihtUniqueObj=normalizeHeight;
	            	// Décor statique : suit le toggle `o` (DISPLAY_OBJECTS).
	            	_myWorld.displayStaticUniqueObjects(_myWorld,gl,movingX,movingY,offset,stepX,stepY,lenX,lenY,normalizeHeight);
	            }

	            // Agents : toujours dessinés (même quand `o` est OFF) sinon on
	            // perd la lisibilité de la simulation.
	            {
	            	float normalizeHeightAgents = (float)heightBooster * heightFactor;
	            	_myWorld.displayDynamicUniqueObjects(_myWorld,gl,movingX,movingY,offset,stepX,stepY,lenX,lenY,normalizeHeightAgents);
	            }

	            gl.glEnd();

	            // ** PASSE 2b : arbres complets (tronc cylindre + feuillage cône via display lists).
	            // Hors d'un glBegin — display lists ouvrent leur propre glBegin en interne.
	            if ( DISPLAY_OBJECTS == true )
	            {
	            	float normalizeHeightTrees = ( smoothFactor[0] + smoothFactor[1] + smoothFactor[2] + smoothFactor[3] ) / 4.f * (float)heightBooster * heightFactor;
	            	for ( int x = xWin[0] ; x < xWin[1] ; x++ ) {
	            		for ( int y = yWin[0] ; y < yWin[1] ; y++ ) {
	            			int forestState = _myWorld.getForestCAValue(x+movingX, y+movingY);
	            			if (forestState < 1 || forestState > 5) continue;
	            			double height = _myWorld.getCellHeight(x+movingX, y+movingY);
	            			objects.vegetation.Tree.displayTreeAt(_myWorld, gl, forestState, x, y, height, offset, stepX, stepY, lenX, lenY, normalizeHeightTrees, movingX, movingY);
	            		}
	            	}
	            }


	            // Passe 2c : moutons via modèle GLB Blender (modddif.com, 10k faces).
	            // Hors glBegin comme les arbres, parce que opengldraw fait un
	            // glCallList qui ouvre son propre glBegin en interne.
	            //
	            // FRUSTUM CULLING : chaque mouton est un mesh lourd (10k tris) et le
	            // rendu est logiciel (Mesa). On projette le centre de l'agent à
	            // l'écran et on saute ceux qui sont derrière la caméra ou bien
	            // au-delà des bords (avec marge pour ne pas faire « pop » un mouton
	            // dont le corps déborde encore à l'écran alors que son centre est
	            // sorti). Gain ×3-5 selon la vue, zéro perte de qualité.
	            {
	            	WorldOfCells wcMoutons = (WorldOfCells) _myWorld;
	            	float normalizeHeightMoutons = (float)heightBooster * heightFactor;
	            	// Émission auto des agents modulée par le jour : pleine de jour,
	            	// quasi nulle la nuit (sinon ils brillent dans le noir).
	            	float dayFactor = 1f - currentNightFactor;

	            	// Matrices capturées une seule fois pour toute la passe.
	            	double[] mv   = new double[16];
	            	double[] proj = new double[16];
	            	int[]    view = new int[4];
	            	double[] win  = new double[3];
	            	gl.glGetDoublev(GL2.GL_MODELVIEW_MATRIX,  mv,   0);
	            	gl.glGetDoublev(GL2.GL_PROJECTION_MATRIX, proj, 0);
	            	gl.glGetIntegerv(GL2.GL_VIEWPORT,         view, 0);
	            	final double marginPx = 120.0; // tolérance bord (corps du mouton)

	            	for (int i = 0; i < wcMoutons.moutons.size(); i++) {
	            		agents.Mouton mt = wcMoutons.moutons.get(i);
	            		if (!isAgentOnScreen(mt, mv, proj, view, win, marginPx)) continue;
	            		agents.Mouton.displayMoutonAt(
	            			mt, _myWorld, gl,
	            			movingX, movingY, offset, stepX, stepY,
	            			lenX, lenY, normalizeHeightMoutons, dayFactor);
	            	}

	            	// Passe 2d : loups (GLB 1k faces), même frustum culling.
	            	for (int i = 0; i < wcMoutons.loups.size(); i++) {
	            		agents.Loup lp = wcMoutons.loups.get(i);
	            		if (!isAgentOnScreen(lp, mv, proj, view, win, marginPx)) continue;
	            		agents.Loup.displayLoupAt(
	            			lp, _myWorld, gl,
	            			movingX, movingY, offset, stepX, stepY,
	            			lenX, lenY, normalizeHeightMoutons, dayFactor);
	            	}
	            }
                // Appui long SPACE/SHIFT : translateZ est modifié à chaque frame
            // pour une montée/descente symétriques (AWT n'auto-repeat pas
            // SHIFT, donc le simple key-press ne donnait pas un mouvement
            // continu équivalent à SPACE).
            if (keySpaceHeld) translateZ -= CAMERA_Z_SPEED;
            if (keyShiftHeld) translateZ += CAMERA_Z_SPEED;

	            // === Overlays 2D : menu de lancement (Phase 6) puis HUD (Phase 5) ====
	            // Dessinés en fin de frame, par-dessus la scène 3D, en mode ortho
	            // pour avoir des coordonnées pixel-écran simples (origine haut-gauche).
	            // Désélectionne l'agent suivi s'il est mort.
	            if (selectedAgent != null && !isAgentStillAlive(selectedAgent)) {
	            	if (selectedAgent == controlledAgent) controlledAgent = null;
	            	selectedAgent = null;
	            	selectedAgentIndex = -1;
	            }

	            // Picking 3D (Phase 8) — résolu ici car ModelView a la camera appliquée
	            // et le mode ortho 2D n'est pas encore enclenché.
	            if (pickRequested) {
	            	doPicking(gl);
	            	pickRequested = false;
	            }

	            // Restaurer la projection perspective si on était passé en ortho
	            // pour la vue de dessus. Doit être fait AVANT begin2D car
	            // celui-ci push à son tour la projection en mode ortho 2D.
	            if (topDownOrthoPushed) {
	            	gl.glMatrixMode(GLMatrixFunc.GL_PROJECTION);
	            	gl.glPopMatrix();
	            	gl.glMatrixMode(GLMatrixFunc.GL_MODELVIEW);
	            }

	            ui.begin2D(gl, viewportWidth, viewportHeight);
	            if (config != null && config.awaitingStart && launchMenu != null) {
	            	// Menu de lancement opaque : couvre la scène 3D derrière.
	            	launchMenu.draw(gl, ui, viewportWidth, viewportHeight);
	            } else {
	            	if (DISPLAY_FPS) {
	            		double gameHour24 = (6.0 + currentDayPhase * 24.0) % 24.0;
	            		int hr = (int) gameHour24;
	            		int mn = (int) ((gameHour24 - hr) * 60.0);
	            		String dayLabel = (currentNightFactor > 0.5f) ? "Nuit" : "Jour";
	            		String gameTime = String.format("%02d:%02d", hr, mn);

	            		hud.draw(gl, ui, viewportWidth, viewportHeight,
	            		         _myWorld, dayLabel, gameTime, lastFpsValue);
	            	}
	            	// Fiche détaillée de l'agent suivi (Phase 8), si présent.
	            	if (selectedAgent != null) {
	            		agentInfoPanel.draw(gl, ui, viewportWidth, viewportHeight,
	            		                    selectedAgent, selectedAgentIndex, cameraFollow);
	            	}
	            	// Graphe populations (Phase 9) — coin haut-GAUCHE, donc il ne
	            	// chevauche plus le menu in-game (panneau droit) : on l'affiche
	            	// même menu ouvert. Seule la touche `g` le masque.
	            	if (showPopulationGraph) {
	            		populationGraph.draw(gl, ui, viewportWidth, viewportHeight);
	            	}
	            	// Menu in-game (Phase 7) : panneau latéral semi-transparent
	            	// au-dessus du HUD. L'alpha varie selon `menuFocused` —
	            	// indication visuelle que le clavier est rendu au jeu.
	            	if (inGameMenu != null && inGameMenu.isOpen()) {
	            		inGameMenu.draw(gl, ui, viewportWidth, viewportHeight, menuFocused);
	            	}
	            	// Réticule en croix au centre pendant le pilotage 1ère personne :
	            	// point de visée (la rotation se fait au clavier A/E).
	            	if (firstPersonControl()) {
	            		float ccx = viewportWidth / 2f, ccy = viewportHeight / 2f;
	            		float rr = 10f;
	            		ui.drawLine(gl, ccx - rr, ccy, ccx + rr, ccy, 1f, 1f, 1f, 0.9f);
	            		ui.drawLine(gl, ccx, ccy - rr, ccx, ccy + rr, 1f, 1f, 1f, 0.9f);
	            	}
	            }
	            ui.end2D(gl);
	            // ==================================================================

                gl.glFlush(); // GO FAST ???//was desable
            	//gLDrawable.swapBuffers(); // GO FAST ???  // should be done at the end (http://stackoverflow.com/questions/1540928/jogl-double-buffering)

                if (screenshotRequested) {
                    screenshotRequested = false;
                    try {
                        File dir = new File("screenshots");
                        if (!dir.exists()) dir.mkdirs();
                        String name = "screenshot-"
                            + new SimpleDateFormat("yyyyMMdd-HHmmss").format(new Date())
                            + ".png";
                        File file = new File(dir, name);
                        Screenshot.writeToFile(file, gLDrawable.getWidth(), gLDrawable.getHeight());
                        System.out.println("Screenshot saved: " + file.getAbsolutePath());
                    } catch (Exception e) {
                        System.err.println("Screenshot failed: " + e);
                    }
                }

        }
        

		/**
         * 
         */
        //@Override
        public void reshape(GLAutoDrawable gLDrawable, int x, int y, int width, int height) {
        		if ( this.it == 0 )
		        	System.out.println( "Game Of Life - FELIX Wycherley-Kassel & GABOUR Smail , 2020\n");
        		this.viewportWidth  = width;
        		this.viewportHeight = height;
        		GL2 gl = gLDrawable.getGL().getGL2();
                final float aspect = (float) width / (float) height;
                gl.glMatrixMode(GLMatrixFunc.GL_PROJECTION);
                gl.glLoadIdentity();
                final float fh = 0.5f;
                final float fw = fh * aspect;
                gl.glFrustumf(-fw, fw, -fh, fh, 1.0f, 1000.0f);
                gl.glMatrixMode(GLMatrixFunc.GL_MODELVIEW);
                gl.glLoadIdentity();
        }
 
        
        /**
         * 
         */
        //@Override
        public void dispose(GLAutoDrawable gLDrawable) {
        }
 

      
        
        /**
         * 
         * @param args
         */
        /*
		public static void main(String[] args) {

        	initLandscape(200,200, new WorldOfTrees());

        }
        */


		@Override
		public void mouseClicked(MouseEvent arg0) {
		}


		@Override
		public void mouseEntered(MouseEvent arg0) {
		}


		@Override
		public void mouseExited(MouseEvent arg0) {
		}


		@Override
		  public void mousePressed(MouseEvent mouse)
		  {
			if (config != null && config.awaitingStart) return; // pas pendant le menu de lancement

			// Robustesse WSLg/NEWT : le bouton droit peut remonter une valeur
			// inattendue. Gauche = BUTTON1 ; tout autre bouton (droit, et milieu
			// inutilisé) = pan « agrippe le sol ».
			boolean isLeft  = mouse.getButton() == MouseEvent.BUTTON1;
			boolean isRight = !isLeft;

			// Si le menu in-game est ouvert et que le clic tombe dans son
			// panneau : interactions menu (clic onglet, double-clic agent).
			if (inGameMenu != null && inGameMenu.containsScreenPoint(
					viewportWidth, viewportHeight, mouse.getX(), mouse.getY())) {
				if (isLeft) {
					// 1. Clic sur un onglet → switche + focus (même si pas focus).
					ui.InGameMenu.Tab clickedTab =
							inGameMenu.tabAt(viewportWidth, viewportHeight, mouse.getX(), mouse.getY());
					if (clickedTab != null) {
						inGameMenu.setActiveTab(clickedTab);
						menuFocused = true;
						return;
					}
					// 2. Double-clic sur une ligne agent → suivre + défocaliser.
					if (mouse.getClickCount() == 2) {
						int agentIdx = inGameMenu.agentRowAt(
								viewportWidth, viewportHeight, mouse.getX(), mouse.getY());
						if (agentIdx >= 0) {
							if (inGameMenu.selectAgentByGlobalIndex(agentIdx)) {
								cameraFollow = true;
								menuFocused = false;
							}
							return;
						}
					}
				}
				// 3. Tout autre clic dans le panneau → focus seulement.
				menuFocused = true;
				return;
			}
			// Clic en dehors du panneau : rend le clavier au jeu.
			if (inGameMenu != null && inGameMenu.isOpen()) {
				menuFocused = false;
			}

			// Clic gauche : amorce de drag potentiel sans déclencher de picking
			// immédiat. Le picking sera déclenché dans mouseReleased si on n'a
			// pas bougé au-delà du seuil.
			// Clic droit : amorce de pan en 3D (libre ou en sortant du suivi).
			if (isLeft) {
				draggingLeft = true;
				dragExceededThreshold = false;
				pressX = mouse.getX();
				pressY = mouse.getY();
				lastDragX = pressX;
				lastDragY = pressY;
			} else if (isRight) {
				// RMB drag autorisé partout SAUF en 3D + suivi (où il serait
				// no-op à cause du recadrage chaque frame — autant ne pas
				// amorcer le drag).
				boolean follow3D = !VIEW_FROM_ABOVE && cameraFollow && selectedAgent != null;
				if (!follow3D) {
					draggingRight = true;
					lastDragX = mouse.getX();
					lastDragY = mouse.getY();
				}
			}
		  }

		@Override
		public void mouseDragged(MouseEvent mouse) {
			if (config != null && config.awaitingStart) return;

			// Drag « agrippe le sol » (style Google Maps) au bouton DROIT/milieu.
			// Détection robuste SANS isButtonDown : sur WSLg/NEWT le masque bouton
			// est peu fiable (il retombe à false en cours de drag → le pan coupait
			// « au bout de quelques pixels », voire ne démarrait pas). On se base
			// donc sur le fait qu'un drag GAUCHE en cours arme draggingLeft : tout
			// mouseDragged reçu SANS draggingLeft est forcément un autre bouton.
			if (!draggingLeft) {
				// En pilotage manuel, le drag gauche tourne déjà la caméra ; on
				// laisse le bouton droit tranquille (pas de pan qui casserait le
				// recadrage). controllingAgent() couvre ce cas.
				if (controllingAgent()) { draggingRight = false; return; }
				// 3D + suivi : le drag droit CASSE le suivi puis pan (intention
				// documentée), au lieu d'être un no-op.
				boolean follow3D = !VIEW_FROM_ABOVE && cameraFollow && selectedAgent != null;
				if (follow3D) cameraFollow = false;
				if (!draggingRight) {        // début du drag droit : arme + référence
					draggingRight = true;
					lastDragX = mouse.getX();
					lastDragY = mouse.getY();
					panAccumX = panAccumY = 0f;   // repart d'une fraction nulle
					return;
				}
				int rdx = mouse.getX() - lastDragX;
				int rdy = mouse.getY() - lastDragY;
				lastDragX = mouse.getX();
				lastDragY = mouse.getY();
				int modX = dxView - 1;
				int modY = dyView - 1;

				// Déplacement caméra désiré (en cellules, fractionnaire) façon
				// « agrippe le sol » (Google Maps) :
				//   souris vers le BAS → caméra AVANCE ;  HAUT → recule.
				//   souris vers la GAUCHE → caméra va à DROITE ;  inverse à droite.
				float moveX, moveY;
				if (VIEW_FROM_ABOVE) {
					// Vue de dessus (nord = +Y vers le haut écran, est = +X à droite).
					moveX = -rdx * PAN_SENSITIVITY;   // souris gauche → +X (droite)
					moveY =  rdy * PAN_SENSITIVITY;   // souris bas    → +Y (avance)
				} else {
					// 3D libre : base relative au yaw caméra. Avant = (sin, cos),
					// droite = (cos, -sin) — même base que le pilotage d'agent.
					double th = Math.toRadians(rotateX);
					double s = Math.sin(th), c = Math.cos(th);
					float fwd   =  rdy * PAN_SENSITIVITY_3D;   // bas → avance
					float right = -rdx * PAN_SENSITIVITY_3D;   // gauche → droite
					moveX = (float) (fwd * s + right * c);
					moveY = (float) (fwd * c - right * s);
				}

				// Accumulation fractionnaire : n'applique que la partie entière,
				// conserve le reste pour le prochain event (drag lent fluide).
				panAccumX += moveX;
				panAccumY += moveY;
				int idx = (int) panAccumX;  panAccumX -= idx;
				int idy = (int) panAccumY;  panAccumY -= idy;
				if (modX > 0 && idx != 0) movingX = ((movingX + idx) % modX + modX) % modX;
				if (modY > 0 && idy != 0) movingY = ((movingY + idy) % modY + modY) % modY;
				return;
			}

			if (!draggingLeft) return;

			// Drag dans le panneau menu (focalisé) : ignore — le panneau n'est
			// pas une zone de manipulation caméra.
			if (inGameMenu != null && inGameMenu.containsScreenPoint(
					viewportWidth, viewportHeight, mouse.getX(), mouse.getY())
					&& menuFocused) {
				return;
			}

			// Mise à jour du seuil : si le mouvement total depuis l'appui
			// dépasse DRAG_THRESHOLD_PX, on bascule en mode drag (et plus de
			// picking au release).
			int totalDx = mouse.getX() - pressX;
			int totalDy = mouse.getY() - pressY;
			if (!dragExceededThreshold
					&& (Math.abs(totalDx) > DRAG_THRESHOLD_PX || Math.abs(totalDy) > DRAG_THRESHOLD_PX)) {
				dragExceededThreshold = true;
				// Référence fraîche (pas de saut initial de ~5px) + accumulateur de
				// pan « agrippe le sol » remis à zéro, comme à l'armement du drag
				// droit. Cet event n'applique encore aucun déplacement.
				lastDragX = mouse.getX();
				lastDragY = mouse.getY();
				panAccumX = panAccumY = 0f;
				return;
			}
			if (!dragExceededThreshold) return;

			int dx = mouse.getX() - lastDragX;
			int dy = mouse.getY() - lastDragY;
			lastDragX = mouse.getX();
			lastDragY = mouse.getY();

			boolean follow3D = !VIEW_FROM_ABOVE && cameraFollow && selectedAgent != null;

			if (firstPersonControl()) {
				// Pilotage 1ère personne : le clic gauche prolongé tourne la
				// caméra (yaw = rotateX, tangage = cameraPitch) — mêmes champs
				// que la caméra FP. Sans ce cas, follow3D capturait le drag et
				// modifiait orbitYaw/orbitPitch (ignorés en FP) → drag sans effet.
				rotateX    += dx * ROT_SENSITIVITY;
				cameraPitch = Math.max(-80f, Math.min(80f, cameraPitch + dy * ROT_SENSITIVITY));
			} else if (follow3D) {
				// Orbit caméra autour de l'agent suivi (style Blender).
				orbitYaw   += dx * ORBIT_SENSITIVITY;
				orbitPitch  = Math.max(5f, Math.min(85f, orbitPitch + dy * ORBIT_SENSITIVITY));
			} else if (VIEW_FROM_ABOVE) {
				// Pan map « agrippe le sol » — IDENTIQUE au drag droit : accumulation
				// fractionnaire pour que les petits déplacements lents comptent
				// (movingX/Y sont des cellules entières ; sans accumulateur, un drag
				// lent arrondirait à 0 et ne bougerait pas). Souris gauche → +X
				// (droite), souris bas → +Y (avance).
				int modX = dxView - 1;
				int modY = dyView - 1;
				panAccumX += -dx * PAN_SENSITIVITY;
				panAccumY +=  dy * PAN_SENSITIVITY;
				int idx = (int) panAccumX;  panAccumX -= idx;
				int idy = (int) panAccumY;  panAccumY -= idy;
				if (modX > 0 && idx != 0) movingX = ((movingX + idx) % modX + modX) % modX;
				if (modY > 0 && idy != 0) movingY = ((movingY + idy) % modY + modY) % modY;
			} else {
				// 3D libre : rotation FPS yaw + pitch.
				rotateX    += dx * ROT_SENSITIVITY;
				cameraPitch = Math.max(-80f, Math.min(80f, cameraPitch + dy * ROT_SENSITIVITY));
			}
		}

		@Override
		public void mouseMoved(MouseEvent mouse) {
			// Mouse-look 1ère personne (NEWT). Le pointeur est masqué + confiné dans
			// la fenêtre. On utilise le delta RELATIF au dernier point (correct quel
			// que soit le timing du warp), et on recentre seulement PRÈS DES BORDS
			// pour ne pas buter — en invalidant le dernier point pour éviter tout saut.
			if (!cursorCaptured || glWindow == null) { haveLastMouse = false; return; }
			int mx = mouse.getX(), my = mouse.getY();
			if (!haveLastMouse) { lastMouseX = mx; lastMouseY = my; haveLastMouse = true; return; }
			int dx = mx - lastMouseX, dy = my - lastMouseY;
			lastMouseX = mx; lastMouseY = my;
			if (dx != 0 || dy != 0) {
				float sens = (config != null) ? config.mouseLookSensitivity : 0.06f;
				rotateX += dx * sens;
				cameraPitch = Math.max(-80f, Math.min(80f, cameraPitch + dy * sens));
			}
			final int margin = 100;
			if (mx < margin || mx > viewportWidth - margin
					|| my < margin || my > viewportHeight - margin) {
				glWindow.warpPointer(viewportWidth / 2, viewportHeight / 2);
				haveLastMouse = false;   // prochain événement = nouvelle référence (pas de saut)
			}
		}

		/**
		 * Recalcule le cap cardinal de l'agent piloté selon la VUE. Appelé chaque
		 * frame depuis display(). Vue de dessus : cardinal direct (Z/S = ±Y calé
		 * sur le pan caméra, Q/D = ±X). Vue 3D suivi : relatif à orbitYaw façon
		 * Minecraft (Z = vers où regarde la caméra, Q/D = strafe), arrondi au
		 * cardinal le plus proche (monde en grille).
		 */
		/** Rotation caméra au clavier (A = gauche, E = droite) en pilotage 3D —
		 *  remplace le mouse-look (curseur non verrouillable sur WSLg). Tourne le
		 *  yaw (rotateX) tant qu'une touche est tenue. */
		private void applyKeyboardTurn() {
			if (!firstPersonControl()) return;
			int dir = (ctrlTurnRight ? 1 : 0) - (ctrlTurnLeft ? 1 : 0);
			if (dir != 0) rotateX += dir * KEY_TURN_DEG_PER_FRAME;
		}

		private void updateManualControlHeading() {
			if (!controllingAgent()) return;
			agents.Agent a = selectedAgent;
			int fwd    = (ctrlFwd ? 1 : 0)   - (ctrlBack ? 1 : 0);
			int strafe = (ctrlRight ? 1 : 0) - (ctrlLeft ? 1 : 0);

			// Vecteur monde désiré (x = Est+, y = Sud+). Vue de dessus : avancer = +Y,
			// droite = +X. Vue 3D : relatif au regard (rotateX, formule caméra libre).
			double wx, wy;
			if (VIEW_FROM_ABOVE) {
				wx = strafe;
				wy = fwd;
			} else {
				double th = Math.toRadians(rotateX);
				double s = Math.sin(th), c = Math.cos(th);
				wx = fwd * s + strafe * c;
				wy = fwd * c - strafe * s;
			}
			// Pas par axe arrondi à {-1,0,1} → DIAGONALES possibles (ex: Z+D).
			int sx = step1(wx), sy = step1(wy);
			a.controlDx = sx;
			a.controlDy = sy;

			if (sx != 0 || sy != 0) {
				// le corps fait face à la direction de déplacement (cardinal dominant)
				a.controlDir = cardinalFromDelta(sx, sy);
			} else if (!VIEW_FROM_ABOVE) {
				// Immobile en 3D : le corps ne pivote que si le regard s'écarte trop
				// du torse (sinon on regarde librement sans tourner l'agent).
				double diff = angularDiffDeg(rotateX, orientYaw(a._orient));
				a.controlDir = (diff > BODY_TURN_THRESHOLD_DEG)
						? cardinalFromDelta(Math.sin(Math.toRadians(rotateX)),
						                    Math.cos(Math.toRadians(rotateX)))
						: -1;
			} else {
				a.controlDir = -1;
			}
		}

		/** Arrondit une composante à un pas dans {-1, 0, 1}. */
		private static int step1(double v) {
			int r = (int) Math.round(v);
			return r < -1 ? -1 : (r > 1 ? 1 : r);
		}

		/** Yaw (deg) d'une orientation cardinale, cohérent avec cardinalFromDelta(sinθ,cosθ) :
		 *  Sud(2)→0, Est(1)→90, Nord(0)→180, Ouest(3)→270. */
		private static float orientYaw(int o) {
			switch (o) { case 2: return 0f; case 1: return 90f; case 0: return 180f; default: return 270f; }
		}

		/** Écart angulaire minimal (deg, 0-180) entre deux angles. */
		private static float angularDiffDeg(float a, float b) {
			float d = Math.abs((a - b) % 360f);
			return d > 180f ? 360f - d : d;
		}

		/** Au-delà de cet écart regard/torse (deg), le corps de l'agent pivote pour
		 *  rattraper le regard (sinon le joueur regarde librement sans tourner l'agent). */
		private static final float BODY_TURN_THRESHOLD_DEG = 100f;

		/** Vecteur monde → orientation cardinale dominante (0=N/1=E/2=S/3=O), -1 si nul.
		 *  Convention : +X = Est, +Y = Sud. */
		private static int cardinalFromDelta(double wx, double wy) {
			int rx = (int) Math.round(wx);
			int ry = (int) Math.round(wy);
			if (rx == 0 && ry == 0) return -1;
			if (Math.abs(rx) >= Math.abs(ry)) return rx > 0 ? 1 : 3;
			return ry > 0 ? 2 : 0;
		}

		/**
		 * (Dé)active la capture du pointeur pour le mouse-look (NEWT) : actif quand
		 * on pilote un agent en vue 3D, menu non focalisé. Masque + confine le
		 * pointeur et le recentre (warpPointer) ; le restaure sinon. Idempotent.
		 */
		private void updateCursorCapture() {
			// Mouse-look désactivé : sur WSLg le curseur n'est ni masquable ni
			// verrouillable (compositeur Windows). La rotation caméra en pilotage 3D
			// se fait au clavier (A/E). On garde donc le curseur visible et libre.
			boolean want = false;
			if (want == cursorCaptured) return;
			cursorCaptured = want;
			if (glWindow == null) return;
			glWindow.confinePointer(false);
			glWindow.setPointerVisible(true);
		}

		@Override
		public void mouseWheelMoved(MouseEvent e) {
			if (config != null && config.awaitingStart) return;

			// NEWT inverse le signe de la molette par rapport à AWT → on renégocie
			// pour garder : notch > 0 = vers le bas → dezoom, notch < 0 = zoom in.
			int notch = -e.getWheelRotation();
			if (notch == 0) return;

			// Si curseur dans le panneau menu ET menu focalisé : scroll les
			// lignes du menu (équivalent à plusieurs ↑/↓).
			if (inGameMenu != null
					&& inGameMenu.containsScreenPoint(viewportWidth, viewportHeight, e.getX(), e.getY())
					&& menuFocused) {
				inGameMenu.scroll(notch);
				return;
			}

			boolean follow3D = !VIEW_FROM_ABOVE && cameraFollow && selectedAgent != null;
			if (VIEW_FROM_ABOVE) {
				// Vue de dessus : la molette change SON PROPRE zoom (topDownViewCells),
				// SANS toucher à la distance de vue 3D (config.viewDistanceCells).
				// notch>0 (molette vers soi) → plus de cases → dézoom.
				topDownViewCells += notch * WHEEL_TOPDOWN_VIEW_STEP;
				topDownViewCells = Math.max(8, Math.min(200, topDownViewCells));
			} else if (follow3D) {
				orbitRadius += notch * WHEEL_ORBIT_STEP;
				orbitRadius = Math.max(ORBIT_RADIUS_MIN, Math.min(ORBIT_RADIUS_MAX, orbitRadius));
			} else {
				// 3D libre : cameraDistance3D est négatif (-130 par défaut).
				// notch>0 (vers le bas) → s'éloigner = plus négatif.
				cameraDistance3D -= notch * WHEEL_3D_STEP;
				cameraDistance3D = Math.max(CAM_DIST_3D_MIN, Math.min(CAM_DIST_3D_MAX, cameraDistance3D));
			}
		}

		/**
		 * Déplacement caméra par touche, relatif au mode d'affichage.
		 *
		 *  - Vue de dessus : mouvement absolu N/S/E/O sur la grille
		 *    (forward = +Y, strafe = +X). Convention identique à l'historique.
		 *  - 3D libre : décomposition selon le yaw `rotateX`. À yaw=0,
		 *    forward+ → +Y et strafe+ → +X (cohérent avec le mode précédent
		 *    où les flèches modifiaient directement movingX/Y).
		 *  - 3D avec suivi caméra : no-op (la caméra suit l'agent, l'utilisateur
		 *    ne contrôle pas movingX/Y).
		 *
		 * forwardSign : +1 = avant, -1 = arrière, 0 = pas de translation longitudinale.
		 * strafeSign  : +1 = droite, -1 = gauche, 0 = pas de strafe.
		 */
		private void cameraRelativeMove(int forwardSign, int strafeSign) {
			if (dxView <= 1 || dyView <= 1) return;
			int modX = dxView - 1;
			int modY = dyView - 1;

			if (VIEW_FROM_ABOVE) {
				movingX = ((movingX + strafeSign)  % modX + modX) % modX;
				movingY = ((movingY + forwardSign) % modY + modY) % modY;
				return;
			}
			if (cameraFollow && selectedAgent != null) {
				return;
			}
			double yawRad = Math.toRadians(rotateX);
			double s = Math.sin(yawRad);
			double c = Math.cos(yawRad);
			// forward = (+sin, +cos) ; strafe right = (+cos, -sin).
			// Conventions choisies pour matcher l'ancien comportement à yaw=0 :
			// VK_UP (forward) → movingY += 1, VK_RIGHT (strafe) → movingX += 1.
			double dx = forwardSign * s + strafeSign * c;
			double dy = forwardSign * c - strafeSign * s;
			int idx = (int) Math.round(dx);
			int idy = (int) Math.round(dy);
			if (idx != 0) movingX = ((movingX + idx) % modX + modX) % modX;
			if (idy != 0) movingY = ((movingY + idy) % modY + modY) % modY;
		}

		@Override
		public void mouseReleased(MouseEvent mouse) {
			// Fin du pan droit dès qu'un bouton remonte. Sur WSLg/NEWT le code de
			// bouton du release est peu fiable : on désarme draggingRight quoi
			// qu'il arrive (un pan « collé » serait gênant). S'il n'y avait pas de
			// drag gauche en cours, rien d'autre à faire.
			draggingRight = false;
			if (!draggingLeft) return;
			draggingLeft = false;

			// Clic court (sans drag significatif) ET en dehors du panneau menu
			// → picking d'agent (résolu dans display() quand les matrices GL
			// sont valides). Si on a dragé, on a déjà manipulé la caméra :
			// pas de picking.
			boolean inMenu = inGameMenu != null && inGameMenu.containsScreenPoint(
					viewportWidth, viewportHeight, mouse.getX(), mouse.getY());
			if (!dragExceededThreshold && !inMenu) {
				pickRequested = true;
				pickClickX = mouse.getX();
				pickClickY = mouse.getY();
			}
		}


		/**
		 * Debug : dump à la touche F11 de la stack de toutes les cellules dans
		 * un rayon 2×rCratere autour de l'épicentre du volcan. Écrit un fichier
		 * texte horodaté dans `dumps/` avec, pour chaque cellule :
		 *  - dist au cratère, cellHeight (live World) vs landscape[][] (copie)
		 *  - getCellTopAltitude (sol + stack)
		 *  - chaque couche : material, thickness, state, persistent
		 *
		 * But : visualiser ce que la simulation a vraiment posé sans dépendre
		 * du rendu 3D. Utile pour débugger les bugs d'empilement (tour de lave,
		 * crust qui ne descend pas, etc.). Ne dépend d'aucun thread GL — peut
		 * être appelée directement depuis l'EDT keyPressed.
		 */
		private void dumpStacksAroundCrater() {
			int sx = cellularautomata.ecosystem.LavaCA.sourceX;
			int sy = cellularautomata.ecosystem.LavaCA.sourceY;
			int rCratere = 5; // valeur défaut de LavaCA.rCratere (champ package-private)
			int radius = rCratere * 2;
			String ts = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").format(LocalDateTime.now());
			Path out = Paths.get("dumps", "dump-stacks-" + ts + ".txt");
			try {
				Files.createDirectories(out.getParent());
				try (PrintWriter pw = new PrintWriter(Files.newBufferedWriter(out))) {
					pw.printf("=== Dump stacks autour du cratere — %s ===%n", ts);
					pw.printf("Source : (%d, %d)%n", sx, sy);
					pw.printf("rCratere : %d   rayon dump : %d%n", rCratere, radius);
					pw.printf("heightBooster : %.2f   heightFactor : %.2f%n", heightBooster, heightFactor);
					cellularautomata.ecosystem.LavaCA lca = (_myWorld instanceof WorldOfCells)
						? ((WorldOfCells) _myWorld).lavaCA : null;
				pw.printf("bErupt : %s%n", lca != null ? Integer.toString(lca.getbErupt()) : "n/a");
				if (lca != null) {
					pw.printf("craterHoleDepth : %.2f   cap au cratere : %.2f%n",
							lca.craterHoleDepth, lca.craterHoleDepth * objects.CommonObject.STONE_BLOCK_HEIGHT);
				}
					pw.println();
					pw.printf("%-10s %-7s %-10s %-10s %-10s %s%n",
							"cell", "dist", "cellH-live", "cellH-fig", "topAlt", "stack (bottom→top)");
					for (int dx = -radius; dx <= radius; dx++) {
						for (int dy = -radius; dy <= radius; dy++) {
							int x = ((sx + dx) % dxView + dxView) % dxView;
							int y = ((sy + dy) % dyView + dyView) % dyView;
							int d = (int) _myWorld.distance(x, y, sx, sy);
							if (d > radius) continue;
							double hLive = _myWorld.getCellHeight(x, y);
							double hFig  = landscape[x % (dxView - 1)][y % (dyView - 1)];
							double topAlt = _myWorld.getCellTopAltitude(x, y);
							StringBuilder stack = new StringBuilder();
							java.util.List<objects.Layer> layers = _myWorld.getStack(x, y);
							if (layers.isEmpty()) {
								stack.append("(empty)");
							} else {
								for (int i = 0; i < layers.size(); i++) {
									objects.Layer L = layers.get(i);
									if (i > 0) stack.append(" | ");
									stack.append(String.format("%s t=%.2f s=%d%s",
											L.material, L.thickness, L.state,
											L.persistent ? " P" : ""));
								}
							}
							pw.printf("(%2d,%2d) %-7d %-10.3f %-10.3f %-10.3f %s%n",
									x, y, d, hLive, hFig, topAlt, stack);
						}
					}
				}
				System.out.println("[DUMP] " + out.toAbsolutePath());
			} catch (IOException e) {
				System.err.println("[DUMP] échec écriture : " + e.getMessage());
			}
		}

		@Override
		public void keyPressed(KeyEvent key) {
			// Menu de lancement actif : on lui passe la touche et on s'arrête là.
			// VK_ESCAPE n'est pas consommé par le menu → bascule le plein écran (switch).
			if (config != null && config.awaitingStart && launchMenu != null
					&& key.getKeyCode() != KeyEvent.VK_ESCAPE) {
				boolean menuClosed = launchMenu.handleKey(key.getKeyCode());
				if (menuClosed && _myWorld instanceof WorldOfCells) {
					// Re-spawn des agents avec les nouvelles valeurs du config.
					((WorldOfCells) _myWorld).respawnAgents();
					// Ouvre le menu in-game sur l'onglet RACCOURCIS pour présenter
					// les commandes au joueur dès le début de la simulation.
					if (inGameMenu != null) {
						inGameMenu.openOnTab(InGameMenu.Tab.AIDE);
						menuFocused = true;
					}
				}
				return;
			}

			// Menu in-game ouvert ET focalisé : intercepte ses touches de
			// navigation/édition. Si défocalisé (clic en dehors), le clavier
			// est rendu au jeu — le menu reste visible mais inerte.
			// `m` reste toujours géré pour fermer/ouvrir.
			if (inGameMenu != null && inGameMenu.isOpen() && menuFocused
					&& key.getKeyCode() != KeyEvent.VK_ESCAPE) {
				if (inGameMenu.handleKey(key.getKeyCode())) return;
			}

			switch (key.getKeyCode()) {
			case KeyEvent.VK_ESCAPE:
				// Échap bascule plein écran ⇄ fenêtré (ne quitte plus).
				// Quitter : croix de la fenêtre, visible en mode fenêtré.
				toggleFullscreen();
				break;
			case KeyEvent.VK_V:
				VIEW_FROM_ABOVE = !VIEW_FROM_ABOVE ;
				break;
			case KeyEvent.VK_R:
				LavaCA.setbErupt(1);
				break;
			case KeyEvent.VK_L:
				MY_LIGHT_RENDERING = !MY_LIGHT_RENDERING;
				break;
			case KeyEvent.VK_P:
				MY_LIGHT_RENDERING_HIGHT = !MY_LIGHT_RENDERING_HIGHT;
				break;
			case KeyEvent.VK_O:
				DISPLAY_OBJECTS = !DISPLAY_OBJECTS;
				break;
			case KeyEvent.VK_M:
				// Logique « m » à 3 états :
				//  - menu fermé                → ouvre + focus
				//  - menu ouvert + pas focus   → re-focalise (ne ferme pas)
				//  - menu ouvert + focus       → ferme
				if (inGameMenu != null) {
					if (!inGameMenu.isOpen()) {
						inGameMenu.toggle();
						menuFocused = true;
					} else if (!menuFocused) {
						menuFocused = true;
					} else {
						inGameMenu.toggle();  // ferme
					}
				}
				break;
			case KeyEvent.VK_N:
				// Bascule jour/nuit instantanée — shift l'itération d'un demi-cycle.
				// Anciennement déclenchée par le clic souris (réservé au picking P8).
				it += _myWorld.getDureeJour();
				break;
			case KeyEvent.VK_F:
				// Bascule la caméra-follow sur l'agent sélectionné (Phase 8).
				cameraFollow = !cameraFollow;
				break;
			case KeyEvent.VK_C:
				// Bascule le PILOTAGE MANUEL de l'agent sélectionné. En contrôle,
				// les flèches/ZQSD dirigent l'agent (cap maintenu) ; la caméra-follow
				// est forcée et le clavier est rendu à la scène (menu défocalisé).
				if (selectedAgent != null && isAgentStillAlive(selectedAgent)) {
					ctrlFwd = ctrlBack = ctrlLeft = ctrlRight = false;
					if (selectedAgent.playerControlled) {
						releaseControl(selectedAgent);          // contrôle OFF
					} else {
						// Un seul agent piloté à la fois : on relâche le précédent.
						if (controlledAgent != null && controlledAgent != selectedAgent)
							releaseControl(controlledAgent);
						selectedAgent.playerControlled = true;  // contrôle ON
						controlledAgent = selectedAgent;
						cameraFollow = true;
						cameraPitch = 0.0f;   // vue de départ à l'horizon (clic gauche pour incliner)
						menuFocused = false;
					}
					updateCursorCapture();             // (dé)masque le curseur en 3D
				}
				break;
			case KeyEvent.VK_G:
				// Affiche / masque le graphe de populations.
				showPopulationGraph = !showPopulationGraph;
				break;
			case KeyEvent.VK_SHIFT:
				keyShiftHeld = true;
				break;
			case KeyEvent.VK_SPACE:
				keySpaceHeld = true;
				break;
			case KeyEvent.VK_2:
				heightBooster++;
				break;
			case KeyEvent.VK_1:
				if ( heightBooster > 0 )
					heightBooster--;
				break;
			case KeyEvent.VK_UP:
			case KeyEvent.VK_Z:
				if (controllingAgent()) ctrlFwd = true;     // avancer (selon la vue)
				else cameraRelativeMove(+1, 0);
				break;
			case KeyEvent.VK_DOWN:
			case KeyEvent.VK_S:
				if (controllingAgent()) ctrlBack = true;    // reculer
				else cameraRelativeMove(-1, 0);
				break;
			case KeyEvent.VK_RIGHT:
			case KeyEvent.VK_D:
				if (controllingAgent()) ctrlRight = true;   // droite
				else cameraRelativeMove(0, +1);
				break;
			case KeyEvent.VK_LEFT:
			case KeyEvent.VK_Q:
				if (controllingAgent()) ctrlLeft = true;    // strafe gauche
				else cameraRelativeMove(0, -1);
				break;
			case KeyEvent.VK_A:
				if (controllingAgent()) ctrlTurnLeft = true;   // pilotage 3D : tourner la caméra à gauche
				break;
			case KeyEvent.VK_E:
				if (controllingAgent()) ctrlTurnRight = true;  // pilotage 3D : tourner la caméra à droite
				break;
			case KeyEvent.VK_F12:
				screenshotRequested = true;
				break;
			case KeyEvent.VK_F11:
				dumpStacksAroundCrater();
				break;
			case KeyEvent.VK_H:
				System.out.println(
						"Help:\n" +
						"           [v] change view\n" +
						"           [o] objects display on/off\n" +
						"           [1] decrease altitude booster\n" +
						"           [2] increase altitude booster\n" +
						" [cursor keys] navigate in the landscape\n" +
						"       [space] navigate in the landscape\n" +
						"       [shift] navigate in the landscape\n" +
						"         [q/d] rotation wrt landscape\n" +
						"           [r] volcanic eruption\n"+
						"           [f] toggle camera-follow on selected agent\n"+
						"           [c] toggle manual control of selected agent\n"+
						"           [l] light\n"+
						"         [F11] dump stacks around crater to dumps/\n"+
						"         [F12] save screenshot to screenshots/\n"
						);
				break;
			default:
				break;
			}
		}


		@Override
		public void keyReleased(KeyEvent key) {
			// SHIFT et SPACE en appui long : reset des flags au relâchement.
			switch (key.getKeyCode()) {
				case KeyEvent.VK_SHIFT: keyShiftHeld = false; break;
				case KeyEvent.VK_SPACE: keySpaceHeld = false; break;
				// Pilotage manuel : relâcher une direction lève son flag maintenu.
				case KeyEvent.VK_UP:    case KeyEvent.VK_Z:    ctrlFwd = false;   break;
				case KeyEvent.VK_DOWN:  case KeyEvent.VK_S:    ctrlBack = false;  break;
				case KeyEvent.VK_RIGHT: case KeyEvent.VK_D:    ctrlRight = false; break;
				case KeyEvent.VK_LEFT:  case KeyEvent.VK_Q:    ctrlLeft = false;  break;
				case KeyEvent.VK_A:     ctrlTurnLeft = false;  break;
				case KeyEvent.VK_E:     ctrlTurnRight = false; break;
				default: break;
			}
		}


		@Override
		public void keyTyped(KeyEvent arg0) {
		}
}
