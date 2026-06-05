package cellularautomata.ecosystem;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import agents.Humain;
import agents.Loup;
import agents.Mouton;
import cellularautomata.CellularAutomataDouble;
import cellularautomata.CellularAutomataInteger;
import objects.CommonObject;
import objects.Layer;
import objects.LavaLineage;
import objects.Material;
import objects.dynamic.TephraProjectile;
import ui.SimulationConfig;
import worlds.World;

/**
 * Cellular Automaton de la lave volcanique — refonte 2026-05 (modèle physique de pression).
 *
 * Plus de cap d'épaisseur, plus de bErupt counter, plus de currentPower static.
 * À la place : liste `activeEruptions` (chaque éruption a sa propre `LavaSource`
 * avec signature couleur unique). Spam de `r` accumule plusieurs Eruptions
 * simultanées.
 *
 * Mécanique par tick :
 *  1. Décrément des éruptions actives + purge celles sans descendants vivants
 *  2. Injection au cratère (pour chaque éruption ouverte) : volume
 *     pressure × BASE_FLOW réparti sur les cellules à dist ≤ rCratere
 *  3. Débordement gravitaire : pour chaque cellule LAVA, transfert
 *     viscosity × (altitude_top - altitude_voisin_plus_bas) vers le voisin
 *     le plus bas
 *  4. Vieillissement + solidification (couches non-persistent → minéral
 *     selon altitude/distance, lineage conservé)
 *  5. Drainage résiduel post-éruption : la pile centrale descend vers
 *     pressure × RESIDUAL_FACTOR
 *  6. Fonte de pierre sur lave (mécanique inchangée)
 *
 * Backward compat : `setbErupt(1)` crée une nouvelle Eruption (touche `r`
 * dans Landscape). `getbErupt()` retourne 1 si au moins une éruption active.
 * `sourceX`/`sourceY`/`eruptionRadius` restent public static (lus par
 * StoneBlock, LavaBlock, Landscape).
 */
public class LavaCA extends CellularAutomataInteger {

	CellularAutomataDouble _cellsHeightValuesCA;
	World world;

	public double pErruption = 0; // proba d'éruption spontanée (désactivée par défaut)

	// Legacy publics (lus par tests et code externe) — interprétés via les nouveaux Sec config
	public static int solidifyEnd = 150;
	public static int solidifyStart = 50;

	int rCratere = 5;
	public static int eruptionRadiusRaw = 5;
	public static int eruptionRadius = 5;
	public int vLave = 100; // legacy — plus utilisé directement (remplacé par BASE_FLOW + SPREAD_FACTOR)
	int tmpNewErruption = 500; // legacy — durée d'éruption désormais via SimulationConfig.eruptionDurationSec

	float stepZ;

	private static final int R_VOLCAN_CAP = 25;

	// Tephra (inchangé)
	private static final float TEPHRA_COUNT_BASE = 8.0f;
	private static final float TEPHRA_THICKNESS_FACTOR = 0.3f;
	private static final float TEPHRA_RANGE_MULT = 1.5f;
	private static final int TEPHRA_FLIGHT_TICKS = 30;
	private static final float TEPHRA_APEX_FACTOR = 3.0f;

	public float craterHoleDepth = 3.0f;
	private boolean holeDug = false;
	public int subsidencePeriod = 6; // legacy field (remplacé par subsidenceIntervalSec)
	public float erruptionPowerMin = 0.4f;
	public float erruptionPowerMax = 2.0f;

	// Module 2 — constantes physiques (recalibrées après feedback utilisateur 2026-05-27)
	// BASE_FLOW = volume total injecté au cratère par tick à pressure=1.
	// Calibration : 200 → fill du cratère ~3.6 ticks (~0.18 sec @ 20 Hz), pic plus
	// haut, débordement plus important, lac résiduel plus épais. Historique :
	// 30 (trop lent) → 120 (OK) → 200 (puissance demandée par l'utilisateur).
	private static final float BASE_FLOW = 200.0f;
	/** Constante physique de l'équilibre hydrostatique : POIDS de colonne max
	 *  par unité de pression. Issue de l'équation P = ρ × g × h → max_weight = P / g
	 *  avec g normalisé à 1. Pression=1 → poids max colonne = 15 (= LAVA pure
	 *  jusqu'à thickness 15, ou BASALT jusqu'à thickness 15/1.7≈9, etc.).
	 *
	 *  Différence clé vs avant : on utilise le POIDS (somme densité × thickness)
	 *  et pas la thickness pure. Donc une colonne avec BASALT par-dessus pèse
	 *  plus → la pression de l'éruption pousse moins haut. Modélise correctement
	 *  l'effet "ça pèse plus avec de la pierre sur le dos". */
	private static final float PRESSURE_TO_MAX_COLUMN_WEIGHT = 15.0f;
	private static final float SPREAD_FACTOR = 8.0f;    // borne propagations/tick (× pressure × 100)
	private static final float RESIDUAL_FACTOR = 3.0f;  // hauteur lac résiduel = pressure × this

	// Drainage : 0.8 unités retirées par cycle de subsidence.
	// À subsidenceIntervalSec=0.1s × 20Hz = 2 ticks → 0.4 unités/tick → 8/sec.
	// Une pile de 18 → anchor 3 redescend en ~2 sec (avant : ~10 sec, perçu trop lent).
	private static final float SUBSIDENCE_SPLIT_DELTA = 0.8f;

	/** Fraction max d'un bloc complet (STONE_BLOCK_HEIGHT) transférée par tick.
	 *  3/10 = 0.3 → 0.9 unité/tick à 20 Hz → 18 unités/sec. Combiné au seuil 1 bloc,
	 *  force le remplissage progressif des voisins. (Choix utilisateur 2026-05-27.) */
	private static final float OVERFLOW_FRACTION_PER_TICK = 0.5f;
	/** Anchor min : la pile LAVA persistante ne descend jamais sous cette épaisseur. */
	private static final float SUBSIDENCE_ANCHOR = 3.0f;

	private static final float STONE_MELT_RATE = 0.05f;
	private static final float STONE_MELT_EPSILON = 0.05f;
	/** Épaisseur minimale d'une couche meltable pour résister à la fonte par contact direct avec LAVA.
	 *  En-dessous, la couche est considérée comme une croûte fine qui se fissure → fond toujours.
	 *  = 1 bloc complet (STONE_BLOCK_HEIGHT). */
	private static final float MIN_PROTECTIVE_THICKNESS = CommonObject.STONE_BLOCK_HEIGHT;
	/** Isolation thermique totale (somme thickness des couches non-LAVA au-dessus de LAVA)
	 *  en-dessous de laquelle la chaleur traverse et le top fond, même s'il n'est pas adjacent à LAVA.
	 *  = 2 blocs complets. */
	private static final float MIN_THERMAL_INSULATION = 2f * CommonObject.STONE_BLOCK_HEIGHT;

	// === Module physique du refroidissement (refonte 2026-05-27) ===
	/** Fraction du temps de solidification où la couleur commence à virer vers pierre. */
	private static final float SOLIDIFY_START_RATIO = 0.6f;
	/** Poids du drainage thermique par voisin froid (STONE/BASALT/GRANITE/OBSIDIAN/eau). */
	private static final float COOLING_COLD_NEIGHBOR_WEIGHT = 0.15f;
	/** Poids de la rétention thermique par voisin LAVA persistent (lac source). */
	private static final float COOLING_HOT_NEIGHBOR_WEIGHT = 0.20f;
	/** Bornes du coolingRate (clamp pour éviter cellules figées ou solidifiées en 1 tick). */
	private static final float COOLING_RATE_MIN = 0.1f;
	private static final float COOLING_RATE_MAX = 2.5f;

	/** Flag d'activité de flow par cellule, reset à chaque step. Une cellule active
	 *  (qui a reçu ou poussé un overflow ce tick) ne vieillit pas — convection. */
	private boolean[][] wasActiveThisTick;

	// Module 2 — état actif
	public static final List<Eruption> activeEruptions = new ArrayList<>();
	private static int nextEruptionId = 1;
	/** Compteur global de ticks CA depuis le démarrage du LavaCA. */
	private static int ticksSinceStart = 0;

	public static int sourceX;
	public static int sourceY;
	public static float sourceZ;

	public LavaCA(World __world, int __dx, int __dy, CellularAutomataDouble cellsHeightValuesCA) {
		super(__dx, __dy, false);
		_cellsHeightValuesCA = cellsHeightValuesCA;
		this.world = __world;
		stepZ = (float) ((world.getMaxEverHeight() - world.getMinEverHeight()) / 100);
		eruptionRadiusRaw = (int) (Math.sqrt(((_dx * _dy) / 2) / (2 * Math.PI)));
		eruptionRadius = Math.min(eruptionRadiusRaw, R_VOLCAN_CAP);
		wasActiveThisTick = new boolean[_dx][_dy];
		lastInstance = this; // pour triggerEruption static
	}

	public void init() {
		int s = 0;
		Collections.shuffle(world.list);
		for (int d = 0; d < world.list.size(); d++) {
			int x = world.list.get(d) % _dx;
			int y = world.list.get(d) / _dy;
			float height = (float) world.getCellHeight(x, y);
			if (height <= world.getMinEverHeight() + stepZ * 100
					&& height >= world.getMinEverHeight() + stepZ * 90 && s == 0) {
				sourceX = x;
				sourceY = y;
				sourceZ = height;
				s = 1;
			}
		}
		this.swapBuffer();
	}

	// ===== Backward compat : bErupt setter/getter =====

	/**
	 * Trigger d'éruption. Appelé par Landscape (touche `r`) ou par pErruption
	 * spontanée. `dummy` ignoré (signature préservée pour compat) : tout appel
	 * crée une nouvelle Eruption avec pressure tirée aléatoirement.
	 *
	 * Cas spécial : si dummy == 0, vide la liste (utilisé par tests pour reset).
	 */
	public static void setbErupt(int dummy) {
		if (dummy == 0) {
			activeEruptions.clear();
			return;
		}
		triggerEruption();
	}

	public int getbErupt() {
		return activeEruptions.isEmpty() ? 0 : 1;
	}

	private static void triggerEruption() {
		SimulationConfig config = currentConfig();
		int hz = (config != null) ? config.simulationHz : 20;
		float durationSec = (config != null) ? config.eruptionDurationSec : 5f;
		float pMin = (config != null) ? config.erruptionPowerMin : 0.4f;
		float pMax = (config != null) ? config.erruptionPowerMax : 2.0f;
		int ticks = Math.max(1, (int) (durationSec * hz));
		float pressure = pMin + (float) Math.random() * (pMax - pMin);
		Eruption e = new Eruption(nextEruptionId++, sourceX, sourceY, pressure, ticks, ticksSinceStart);
		activeEruptions.add(e);
		// Tephra : une seule salve au début de l'éruption, déclenchée ici.
		// Note : on ne peut spawn que si on a une instance vivante (world non-null).
		// Le mécanisme delegate à instance.ejectTephra via un singleton "dernier
		// LavaCA créé" — pattern simple compatible avec le test setup mono-instance.
		if (lastInstance != null) lastInstance.ejectTephra(e);
	}

	/** Dernier LavaCA construit (pour permettre triggerEruption static d'éjecter le tephra). */
	private static LavaCA lastInstance = null;

	/** True si (x, y) est dans le rayon d'au moins une éruption active (= cas "cratère").
	 *  Utilisé par ForestCA pour distinguer projection explosive (cratère) vs poussée
	 *  en aval (coulée hors cratère). Retourne false si aucune éruption active OU si
	 *  aucun LavaCA n'est encore instancié (test setup). */
	public static boolean isInActiveEruptionRadius(World world, int x, int y) {
		if (lastInstance == null) return false;
		int radius = lastInstance.rCratere;
		for (Eruption e : activeEruptions) {
			if (!e.isOpen()) continue;
			if (world.distance(x, y, e.sourceX, e.sourceY) <= radius) return true;
		}
		return false;
	}

	/**
	 * Lit la SimulationConfig courante. Pour découplage : si non disponible
	 * (tests qui n'instancient pas la config), retourne null et le caller doit
	 * utiliser ses défauts.
	 */
	private static SimulationConfig currentConfig() {
		// Hack léger : on essaie de récupérer la config via un singleton optionnel.
		// Si pas trouvé, retourne null (les callers ont des défauts).
		try {
			return SimulationConfig.getInstance();
		} catch (Throwable t) {
			return null;
		}
	}

	// ===== Boucle principale =====

	public void step() {
		ticksSinceStart++;

		// Reset du flag d'activité — chaque tick repart vierge (Module physique cooling).
		for (int x = 0; x < _dx; x++) {
			java.util.Arrays.fill(wasActiveThisTick[x], false);
		}

		SimulationConfig config = currentConfig();
		int hz = (config != null) ? config.simulationHz : 20;
		int solidifyEndTicks = (config != null) ? Math.max(1, (int) (config.solidifyEndSec * hz)) : solidifyEnd;
		float viscosity = (config != null) ? config.lavaViscosity : 0.3f;
		int subsidenceIntervalTicks = (config != null) ? Math.max(1, (int) (config.subsidenceIntervalSec * hz)) : subsidencePeriod;

		// Creusement différé du cratère (lecture du verticalScale calé par Landscape)
		if (!holeDug) {
			digHole();
			holeDug = true;
		}

		// Éruption spontanée (proba pErruption)
		if (Math.random() < pErruption && activeEruptions.isEmpty()) {
			triggerEruption();
		}

		// 1. Décrément + purge
		for (Eruption e : activeEruptions) {
			if (e.ticksRemaining > 0) e.ticksRemaining--;
		}
		// Note : on ne purge pas immédiatement après ticksRemaining==0 — on garde
		// l'Eruption tant qu'au moins un bloc LAVA pointe vers son source (lac
		// résiduel + coulées encore liquides). Vérification coûteuse (scan grille)
		// → faite seulement quand la pression vient de chuter (éruption fermée).
		activeEruptions.removeIf(e -> !e.isOpen() && !hasLivingDescendants(e));

		// 2. Injection au cratère (tephra déjà spawné dans triggerEruption)
		for (Eruption e : activeEruptions) {
			if (!e.isOpen()) continue;
			injectAtCrater(e);
		}

		// 3. Débordement gravitaire (Option B viscosité)
		applyGravitationalOverflow(viscosity);

		// 4. Vieillissement + solidification
		ageLavaAndSolidify(solidifyEndTicks);

		// 5. Drainage résiduel (post-éruption, hauteur = pressure × RESIDUAL_FACTOR).
		// Legacy override : si subsidencePeriod == 0 (field LavaCA), désactivé
		// même si config dit autrement (utilisé par les tests pour figer le lac).
		boolean drainEnabled = subsidencePeriod > 0 && subsidenceIntervalTicks > 0;
		if (drainEnabled && ticksSinceStart % subsidenceIntervalTicks == 0) {
			applyResidualDrainage();
		}

		// 6. Fonte de pierre sur lave (mécanique conservée)
		stepStoneMelt();

		// 7. Propagation feu : tout agent adjacent à une couche LAVA à sa hauteur
		//    (4 voisins cardinaux + même cellule) prend feu.
		igniteAgentsNearLava();

		this.swapBuffer();
	}

	// ===== Helpers physiques =====

	private void injectAtCrater(Eruption e) {
		float volume = e.pressure * BASE_FLOW;
		// Injection avec équilibre hydrostatique par POIDS (refonte 2026-05-27).
		//
		// PRINCIPE PHYSIQUE :
		// 1. Profil radial : pression max au centre du conduit, ~0 au bord (1-d/r)
		//    linéaire — donne une mare quasi-plate qui déborde naturellement, vs
		//    profil quadratique qui produisait un pic trop pointu au centre.
		// 2. Équilibre P = ρ × g × h utilisant les VRAIES densités : la pression
		//    de l'éruption pousse la magma vers le haut tant que le POIDS de la
		//    colonne au-dessus est inférieur. Une colonne avec BASALT (densité 1.7)
		//    par-dessus pèse plus qu'une colonne pure LAVA (densité 1.0).
		// 3. max_column_weight = pressure × PRESSURE_TO_MAX_COLUMN_WEIGHT × radial
		// 4. Pour injecter LAVA fraîche (densité 1.0), thickness ajoutable =
		//    (max_weight - current_weight) / LAVA.density
		//
		// Volume total tenté = BASE_FLOW × pressure (le surplus non absorbé par
		// les cellules à l'équilibre est perdu — physiquement la pression ne
		// peut pas pousser au-delà).
		float maxColumnWeight = e.pressure * PRESSURE_TO_MAX_COLUMN_WEIGHT;
		float[][] weights = new float[_dx][_dy];
		float totalWeight = 0f;
		for (int x = 0; x < _dx; x++) {
			for (int y = 0; y < _dy; y++) {
				double dist = world.distance(x, y, e.sourceX, e.sourceY);
				if (dist > rCratere) continue;
				double normDist = dist / rCratere;
				float radial = (float) (1.0 - normDist);
				if (radial <= 0f) continue;
				float maxWeightHere = maxColumnWeight * radial;
				float currentColumnWeight = computeColumnWeight(x, y);
				float remainingWeight = Math.max(0f, maxWeightHere - currentColumnWeight);
				if (remainingWeight <= 0f) continue;
				// Poids final = profil radial × ratio de poids restant
				float headroomRatio = remainingWeight / Math.max(0.01f, maxWeightHere);
				float w = radial * headroomRatio;
				weights[x][y] = w;
				totalWeight += w;
			}
		}
		if (totalWeight <= 0f) return;

		LavaLineage lineage = LavaLineage.forSource(e.source);
		for (int x = 0; x < _dx; x++) {
			for (int y = 0; y < _dy; y++) {
				float w = weights[x][y];
				if (w <= 0f) continue;
				float cellInjectionByVolume = volume * w / totalWeight;
				// HARD CAP physique par POIDS : on ne peut JAMAIS dépasser le
				// poids résiduel. La pression de l'éruption ne peut pas pousser
				// la magma au-delà de l'équilibre. Conversion poids → thickness
				// via la densité du matériau injecté (LAVA = 1.0).
				double dist = world.distance(x, y, e.sourceX, e.sourceY);
				double normDist = dist / rCratere;
				float radial = (float) (1.0 - normDist);
				float maxWeightHere = maxColumnWeight * radial;
				float currentColumnWeight = computeColumnWeight(x, y);
				float remainingWeight = Math.max(0f, maxWeightHere - currentColumnWeight);
				float maxLavaThickness = remainingWeight / Material.LAVA.density;
				float cellInjection = Math.min(cellInjectionByVolume, maxLavaThickness);
				if (cellInjection <= 0f) continue;

				world.pushLayer(x, y, Material.LAVA, cellInjection, 1, lineage);
				Layer top = world.topLayer(x, y);
				if (top != null) top.persistent = true;
				wasActiveThisTick[x][y] = true;
				killAgentsOnCell(x, y);
			}
		}
	}

	/** Poids total d'une colonne = somme(thickness × densité) sur tous les Layer. */
	private float computeColumnWeight(int x, int y) {
		float w = 0f;
		for (Layer l : world.getStack(x, y)) {
			w += l.thickness * l.material.density;
		}
		return w;
	}

	/**
	 * Débordement gravitaire (Option B viscosité). Pour chaque cellule LAVA,
	 * trouve le voisin cardinal le plus bas. Si l'altitude top est strictement
	 * supérieure à celle du voisin, transfère `(excess / verticalScale) × viscosity`
	 * unités d'épaisseur LAVA au voisin (hérite du lineage avec generation+1).
	 *
	 * Borne le nombre de propagations par tick selon la pression totale des
	 * éruptions actives + un minimum pour le drainage post-éruption.
	 */
	private void applyGravitationalOverflow(float viscosity) {
		float pressureSum = 0f;
		for (Eruption e : activeEruptions) pressureSum += e.pressure;
		// Bornage du nombre de cellules pouvant déborder par tick. Min 80 pour
		// que le drainage post-éruption reste vivant ; le scaling × pressure × 100
		// donne ~800 par éruption power=1 (suffisant pour les flancs sans
		// emballement). Avant : min 200 trop permissif → écoulement trop rapide.
		int maxProps = Math.max(80, (int) (pressureSum * SPREAD_FACTOR * 50));

		// Solidify ticks pour calculer la viscosité dépendante du state.
		SimulationConfig cfg = currentConfig();
		int hz = (cfg != null) ? cfg.simulationHz : 20;
		int solidifyEndTicks = (cfg != null) ? Math.max(1, (int)(cfg.solidifyEndSec * hz)) : solidifyEnd;

		int props = 0;
		Collections.shuffle(world.list);
		for (int d = 0; d < world.list.size() && props < maxProps; d++) {
			int x = world.list.get(d) % _dx;
			int y = world.list.get(d) / _dy;
			Layer top = world.topLayer(x, y);
			if (top == null || top.material != Material.LAVA) continue;
			if (top.lineage == null) continue;

			// Règle "seuil 1 bloc" (choix utilisateur 2026-05-27) : une cellule
			// LAVA non-persistante ne peut PAS déborder si elle a moins d'un bloc
			// complet d'épaisseur. Simule la tension de surface du fluide visqueux.
			// La lave persistante (lac cratère) garde son comportement spécifique.
			if (!top.persistent && top.thickness < CommonObject.STONE_BLOCK_HEIGHT) continue;

			float topAlt = world.getCellTopAltitude(x, y);
			int xe = (x + 1 + _dx) % _dx;
			int xo = (x - 1 + _dx) % _dx;
			int yn = (y - 1 + _dy) % _dy;
			int ys = (y + 1 + _dy) % _dy;
			int bestNx = -1, bestNy = -1;
			float bestAlt = topAlt;
			int[][] nbrs = { { xe, y }, { xo, y }, { x, yn }, { x, ys } };
			for (int[] off : nbrs) {
				float nAlt = world.getCellTopAltitude(off[0], off[1]);
				if (nAlt < bestAlt) {
					bestAlt = nAlt;
					bestNx = off[0];
					bestNy = off[1];
				}
			}
			if (bestNx < 0) continue;

			// Fix unité : `thickness` et `altitude` sont dans la même échelle
			// (cf. getCellTopAltitude = cellH × vScale + Σthickness). Donc
			// excessAlt → excessThickness directement, sans division par vScale.
			float excessThickness = topAlt - bestAlt;

			// Pour une LAVA persistante DANS le cratère : le bowl contient un lac
			// permanent. Seul le SURPLUS au-dessus du rim peut déborder (le reste
			// reste piégé dans le creux). Sans cette règle, le lac drainait
			// complètement par overflow après l'éruption → cratère vide.
			// Pour une LAVA non-persistante : drainable = thickness - 1 bloc (la
			// cellule garde au moins un bloc complet, cohérent avec le seuil).
			float drainable;
			if (top.persistent && world.distance(x, y, sourceX, sourceY) <= rCratere) {
				float rimThickness = craterHoleDepth * CommonObject.STONE_BLOCK_HEIGHT;
				drainable = Math.max(0f, top.thickness - rimThickness);
				if (drainable <= 0.005f) continue;
			} else {
				// Garde 1 bloc complet (= STONE_BLOCK_HEIGHT) → puddle minimale.
				drainable = Math.max(0f, top.thickness - CommonObject.STONE_BLOCK_HEIGHT);
				if (drainable <= 0.005f) continue;
			}

			// Viscosité dépendante du state : la lave qui refroidit coule plus lentement.
			// Au state=1 (fraîche, dans le cratère) : 100% de viscosity → flow rapide
			//   → préserve la forme de l'éruption (équilibrage rapide du lac).
			// PLANCHER À 0.30 : même la lave très âgée garde 30% de viscosité, sinon
			// elle ne peut plus refluer vers la colonne qui draine post-éruption.
			float ageRatio = Math.min(1f, (float) top.state / solidifyEndTicks);
			float ageDamp = Math.max(0.30f, (1f - ageRatio) * (1f - ageRatio));
			float effectiveViscosity = viscosity * ageDamp;
			// Gravité interne au cratère = normale. La hauteur du pic est désormais
			// bornée par l'équilibre hydrostatique côté injection (cf. injectAtCrater)
			// — pas besoin de désactiver la gravité.

			// Le transfert remplit la "fente" entre top et voisin, atténué par
			// viscosity (effective). Quatre bornes :
			//  - 90% du drainable (la cellule ne se vide pas en un tick)
			//  - excessThickness × effectiveViscosity (résistance + state damping)
			//  - HALF excès (Option C) : le voisin ne dépasse jamais le source
			//  - FRACTION_PER_TICK × STONE_BLOCK_HEIGHT (choix utilisateur 2026-05-27) :
			//    le voisin se remplit par paliers de 1/10 de bloc max par tick
			//    → coulée naturellement lente et contrôlée.
			float transferThickness = Math.min(drainable * 0.9f, excessThickness * effectiveViscosity);
			float halfExcess = excessThickness * 0.5f;
			if (transferThickness > halfExcess) transferThickness = halfExcess;
			float fractionCap = CommonObject.STONE_BLOCK_HEIGHT * OVERFLOW_FRACTION_PER_TICK;
			if (transferThickness > fractionCap) transferThickness = fractionCap;
			// Seuil très bas : on accepte tout débordement même minuscule
			// (cohérent avec "déborder dès qu'on peut").
			if (transferThickness <= 0.005f) continue;

			top.thickness -= transferThickness;
			if (top.thickness <= 0.05f) {
				world.removeTopLayer(x, y);
			}
			LavaLineage derived = LavaLineage.derived(top.lineage.source, x, y, top.lineage.generation);
			world.pushLayer(bestNx, bestNy, Material.LAVA, transferThickness, top.state, derived);

			// Marquage flow actif (Module physique cooling) — source ET cible
			wasActiveThisTick[x][y] = true;
			wasActiveThisTick[bestNx][bestNy] = true;

			if (world.distance(bestNx, bestNy, sourceX, sourceY) <= rCratere) {
				Layer nTop = world.topLayer(bestNx, bestNy);
				if (nTop != null) nTop.persistent = true;
			}
			killAgentsOnCell(bestNx, bestNy);
			props++;
		}
	}

	private void ageLavaAndSolidify(int solidifyEndTicks) {
		int solidifyStartTicks = Math.round(solidifyEndTicks * SOLIDIFY_START_RATIO);
		float rEffectiveAvg = currentEruptionRadius();
		for (int x = 0; x < _dx; x++) {
			for (int y = 0; y < _dy; y++) {
				Layer top = world.topLayer(x, y);
				if (top == null || top.material != Material.LAVA) continue;

				// === MODÈLE DE COOLING PHYSIQUE (Module A — refonte 2026-05-27) ===
				// Si la cellule a eu un flow ce tick (entrant ou sortant), convection
				// → reste hot → coolingRate = 0. Sinon, on calcule selon thickness × voisins.
				float coolingRate = 0f;
				if (!wasActiveThisTick[x][y]) {
					// Facteur épaisseur : 1 bloc = 1.0, 10 blocs = 0.1 (isolation thermique).
					float thicknessFactor = CommonObject.STONE_BLOCK_HEIGHT
							/ Math.max(CommonObject.STONE_BLOCK_HEIGHT, top.thickness);
					// Facteur voisins : froids accélèrent, lava persistent ralentit.
					int coldN = countNeighborsCold(x, y);
					int hotN = countNeighborsLavaPersistent(x, y);
					float neighborFactor = 1f
							+ COOLING_COLD_NEIGHBOR_WEIGHT * coldN
							- COOLING_HOT_NEIGHBOR_WEIGHT * hotN;
					coolingRate = thicknessFactor * neighborFactor;
					if (coolingRate < COOLING_RATE_MIN) coolingRate = COOLING_RATE_MIN;
					if (coolingRate > COOLING_RATE_MAX) coolingRate = COOLING_RATE_MAX;
				}
				// Bonus immersion (ajout direct, prio sur le flag mouvement).
				double height = world.getCellHeight(x, y);
				if (height < 0) coolingRate += 1f;

				top.state += Math.round(coolingRate);

				// === SOLIDIFICATION ===
				if (top.state >= solidifyEndTicks) {
					if (top.persistent) {
						// Clamp au milieu de Phase 1 → reste orange-jaune vive.
						top.state = Math.max(1, solidifyStartTicks / 2);
					} else {
						Material solidifiedAs = chooseSolidifiedMaterial(x, y, height, rEffectiveAvg);
						world.replaceTopLayer(x, y, solidifiedAs, 0);
						if (world.getForestCAValue(x, y) > 0) world.setForestCAValue(x, y, 0);
						if (world.getGrassCAValue(x, y) > 0) world.setGrassCAValue(x, y, 0);
					}
				}
			}
		}
	}

	// === Helpers cooling (Module physique 2026-05-27) ===

	private int countNeighborsCold(int x, int y) {
		int xe = (x + 1) % _dx, xo = (x - 1 + _dx) % _dx;
		int yn = (y - 1 + _dy) % _dy, ys = (y + 1) % _dy;
		int n = 0;
		if (isColdAt(xe, y)) n++;
		if (isColdAt(xo, y)) n++;
		if (isColdAt(x, yn)) n++;
		if (isColdAt(x, ys)) n++;
		return n;
	}

	private boolean isColdAt(int x, int y) {
		if (world.getCellHeight(x, y) < 0) return true; // eau drain
		Layer t = world.topLayer(x, y);
		if (t == null) return true; // air / sol nu
		return t.material == Material.STONE || t.material == Material.BASALT
				|| t.material == Material.GRANITE || t.material == Material.OBSIDIAN;
	}

	private int countNeighborsLavaPersistent(int x, int y) {
		int xe = (x + 1) % _dx, xo = (x - 1 + _dx) % _dx;
		int yn = (y - 1 + _dy) % _dy, ys = (y + 1) % _dy;
		int n = 0;
		if (isLavaPersistentAt(xe, y)) n++;
		if (isLavaPersistentAt(xo, y)) n++;
		if (isLavaPersistentAt(x, yn)) n++;
		if (isLavaPersistentAt(x, ys)) n++;
		return n;
	}

	private boolean isLavaPersistentAt(int x, int y) {
		Layer t = world.topLayer(x, y);
		return t != null && t.material == Material.LAVA && t.persistent;
	}

	private float currentEruptionRadius() {
		if (activeEruptions.isEmpty()) return eruptionRadius;
		float sum = 0f;
		for (Eruption e : activeEruptions) sum += e.pressure;
		return eruptionRadius * (sum / activeEruptions.size());
	}

	/**
	 * Drainage résiduel post-éruption : toute couche LAVA persistante (lac de
	 * cratère ou pile artificielle créée par un test) descend vers la hauteur
	 * résiduelle visée. Pour les cellules du cratère : target = max des
	 * pressure × RESIDUAL_FACTOR ; pour les autres cellules persistent (cas
	 * de test), target = SUBSIDENCE_FLOOR. Désactivé pendant éruption ouverte.
	 */
	private void applyResidualDrainage() {
		for (Eruption e : activeEruptions) {
			if (e.isOpen()) return; // pas de drainage pendant injection
		}

		// Calcule target pour le cratère
		float craterTarget = 0f;
		for (int x = 0; x < _dx; x++) {
			for (int y = 0; y < _dy; y++) {
				if (world.distance(x, y, sourceX, sourceY) > rCratere) continue;
				Layer top = world.topLayer(x, y);
				if (top == null || top.material != Material.LAVA || !top.persistent) continue;
				if (top.lineage != null && top.lineage.source != null) {
					float r = top.lineage.source.pressure * RESIDUAL_FACTOR;
					if (r > craterTarget) craterTarget = r;
				}
			}
		}
		if (craterTarget < SUBSIDENCE_ANCHOR) craterTarget = SUBSIDENCE_ANCHOR;

		for (int x = 0; x < _dx; x++) {
			for (int y = 0; y < _dy; y++) {
				Layer top = world.topLayer(x, y);
				if (top == null || top.material != Material.LAVA || !top.persistent) continue;
				boolean inCrater = world.distance(x, y, sourceX, sourceY) <= rCratere;
				float target = inCrater ? craterTarget : SUBSIDENCE_ANCHOR;
				if (top.thickness > target + 0.01f) {
					top.thickness -= SUBSIDENCE_SPLIT_DELTA;
					if (top.thickness < target) top.thickness = target;
					wasActiveThisTick[x][y] = true; // Module physique cooling : drainage = mouvement
				}
			}
		}
	}

	private boolean hasLivingDescendants(Eruption e) {
		for (int x = 0; x < _dx; x++) {
			for (int y = 0; y < _dy; y++) {
				List<Layer> stack = world.getStack(x, y);
				for (Layer l : stack) {
					if (l.material == Material.LAVA && l.lineage != null
							&& l.lineage.source == e.source) {
						return true;
					}
				}
			}
		}
		return false;
	}

	// ===== Fonte (inchangé) =====

	private void stepStoneMelt() {
		for (int x = 0; x < _dx; x++) {
			for (int y = 0; y < _dy; y++) {
				List<Layer> stack = world.getStack(x, y);
				if (stack.size() < 2) continue;
				Layer top = stack.get(stack.size() - 1);
				if (!isMeltable(top.material)) continue;

				// Trouve la couche LAVA la plus haute (s'il y en a) en dessous du top.
				int lavaIdx = -1;
				for (int i = stack.size() - 2; i >= 0; i--) {
					if (stack.get(i).material == Material.LAVA) { lavaIdx = i; break; }
				}
				if (lavaIdx < 0) continue;

				boolean adjacentToLava = (lavaIdx == stack.size() - 2);
				Layer lavaLayer = stack.get(lavaIdx);

				// Calcul de l'isolation thermique = somme des thicknesses des couches
				// non-LAVA entre la LAVA et le top (inclus).
				float insulation = 0f;
				for (int i = lavaIdx + 1; i < stack.size(); i++) {
					insulation += stack.get(i).thickness;
				}

				boolean shouldMelt;
				if (adjacentToLava && top.thickness < MIN_PROTECTIVE_THICKNESS) {
					// Règle 1 : croûte fine en contact direct avec LAVA → fissure, fond toujours.
					shouldMelt = true;
				} else if (!adjacentToLava && insulation < MIN_THERMAL_INSULATION) {
					// Règle 3 : isolation totale insuffisante → chaleur traverse, top fond.
					shouldMelt = true;
				} else if (adjacentToLava) {
					// Cas classique : top adjacent à LAVA et épais (≥ 1 bloc).
					// LAVA persistante (lac cratère) → fond toujours. Sinon check du pont :
					// au moins 2 voisins solides ÉPAIS protègent (forme un pont stable).
					shouldMelt = lavaLayer.persistent || countNeighborsSolidThick(x, y) < 2;
				} else {
					// Non adjacent + isolation ≥ 2 blocs → couche bien isolée, ne fond pas.
					shouldMelt = false;
				}

				if (shouldMelt) {
					top.thickness *= (1f - STONE_MELT_RATE);
					if (top.thickness < STONE_MELT_EPSILON) {
						world.removeTopLayer(x, y);
					}
				}
			}
		}
	}

	/** Pour le check de pont fonte : un voisin compte uniquement si son top est une vraie
	 *  couche solide d'au moins 1 bloc complet (sinon = pellicule fine, ne forme pas un pont). */
	private boolean neighborTopIsSolidThick(int x, int y) {
		Layer t = world.topLayer(x, y);
		return t != null && isSolidStructure(t.material) && t.thickness >= MIN_PROTECTIVE_THICKNESS;
	}

	private int countNeighborsSolidThick(int x, int y) {
		int xe = (x + 1 + _dx) % _dx;
		int xo = (x - 1 + _dx) % _dx;
		int yn = (y - 1 + _dy) % _dy;
		int ys = (y + 1 + _dy) % _dy;
		int n = 0;
		if (neighborTopIsSolidThick(xe, y)) n++;
		if (neighborTopIsSolidThick(xo, y)) n++;
		if (neighborTopIsSolidThick(x, yn)) n++;
		if (neighborTopIsSolidThick(x, ys)) n++;
		return n;
	}

	private static boolean isMeltable(Material m) {
		return m == Material.STONE || m == Material.BASALT || m == Material.GRANITE;
	}

	private static boolean isSolidStructure(Material m) {
		return m == Material.STONE || m == Material.BASALT
				|| m == Material.GRANITE || m == Material.OBSIDIAN;
	}

	// ===== Cratère physique =====

	private void digHole() {
		float vScale = world.getVerticalScale();
		if (vScale <= 0) vScale = 1.0f;
		double depthInCellUnits = (craterHoleDepth * CommonObject.STONE_BLOCK_HEIGHT) / vScale;
		double waterFloor = 0.0 + 1e-3;
		for (int x = 0; x < _dx; x++) {
			for (int y = 0; y < _dy; y++) {
				if (world.distance(x, y, sourceX, sourceY) > rCratere) continue;
				double current = world.getCellHeight(x, y);
				double newH = Math.max(waterFloor, current - depthInCellUnits);
				world.setCellHeight(x, y, newH);
			}
		}
	}

	// ===== Solidification — choix du matériau (probabiliste, conservé) =====

	Material chooseSolidifiedMaterial(int x, int y, double height, float rEffective) {
		if (height < 0) return Material.OBSIDIAN;
		double maxH = world.getMaxEverHeight();
		double pGranite;
		if (height >= maxH * 0.6) pGranite = 1.0;
		else if (height <= maxH * 0.3) pGranite = 0.0;
		else pGranite = (height - maxH * 0.3) / (maxH * 0.3);
		Material altitudeMat = (Math.random() < pGranite) ? Material.GRANITE : Material.STONE;

		double dist = world.distance(x, y, sourceX, sourceY);
		double pBasalt;
		if (dist <= rEffective * 0.3) pBasalt = 1.0;
		else if (dist >= rEffective * 0.6) pBasalt = 0.0;
		else pBasalt = 1.0 - (dist - rEffective * 0.3) / (rEffective * 0.3);
		if (Math.random() < pBasalt) return Material.BASALT;
		return altitudeMat;
	}

	// ===== Agents =====

	private void killAgentsOnCell(int x, int y) {
		killAgentsOnCell(world, x, y);
	}

	/** Tue tous les agents (Loup, Mouton, Humain) présents sur la cellule (x, y).
	 *  Helper static pour pouvoir être appelé depuis TephraProjectile à l'atterrissage. */
	public static void killAgentsOnCell(World w, int x, int y) {
		for (int i = 0; i < w.uniqueDynamicObjects.size(); i++) {
			objects.UniqueDynamicObject obj = w.uniqueDynamicObjects.get(i);
			if (obj.x != x || obj.y != y) continue;
			if (obj instanceof Loup) ((Loup) obj)._alive = false;
			else if (obj instanceof Mouton) ((Mouton) obj)._alive = false;
			else if (obj instanceof Humain) ((Humain) obj)._alive = false;
		}
	}

	/** Met le feu à tous les agents sur la cellule (x, y). Loup/Mouton/Humain
	 *  entrent en _fireState=1. La perte d'énergie + (éventuelle) fuite vers
	 *  l'eau sont gérées par leur step() respectif. */
	public static void setAgentsOnFireOnCell(World w, int x, int y) {
		for (int i = 0; i < w.uniqueDynamicObjects.size(); i++) {
			objects.UniqueDynamicObject obj = w.uniqueDynamicObjects.get(i);
			if (obj.x != x || obj.y != y) continue;
			if (obj instanceof agents.Agent) ((agents.Agent) obj).setOnFire();
		}
	}

	/** Tick de propagation feu. Pour chaque agent vivant non encore en feu :
	 *  vérifie sa cellule + ses 4 voisins cardinaux ; s'il existe une couche
	 *  LAVA dont l'intervalle vertical contient l'altitude de l'agent (pas de
	 *  vide ni de bloc entre), l'agent prend feu. */
	private void igniteAgentsNearLava() {
		if (world.uniqueDynamicObjects.isEmpty()) return;
		float vScale = world.getVerticalScale();
		int[][] dirs = {{0, 0}, {-1, 0}, {1, 0}, {0, -1}, {0, 1}};
		for (int i = 0; i < world.uniqueDynamicObjects.size(); i++) {
			objects.UniqueDynamicObject obj = world.uniqueDynamicObjects.get(i);
			if (!(obj instanceof agents.Agent)) continue;
			agents.Agent agent = (agents.Agent) obj;
			if (agent.isOnFire()) continue;
			float agentAlt = world.getCellTopAltitude(agent.x, agent.y);
			for (int[] d : dirs) {
				int nx = ((agent.x + d[0]) % _dx + _dx) % _dx;
				int ny = ((agent.y + d[1]) % _dy + _dy) % _dy;
				if (hasLavaTouchingAltitude(nx, ny, agentAlt, vScale)) {
					agent.setOnFire();
					break;
				}
			}
		}
	}

	/** True si la stack en (x, y) contient une couche LAVA dont l'intervalle
	 *  vertical [base, top] inclut targetAlt (avec tolérance de 1 unité pour
	 *  la hauteur du modèle agent). Renvoie false si la cellule n'a pas de
	 *  couche à cette altitude (= vide), ou si la couche présente n'est pas
	 *  LAVA (= bloc entre l'agent et la lave). */
	private boolean hasLavaTouchingAltitude(int x, int y, float targetAlt, float vScale) {
		float base = (float) (world.getCellHeight(x, y) * vScale);
		final float tolerance = 1.0f;
		for (Layer l : world.getStack(x, y)) {
			float top = base + l.thickness;
			if (targetAlt >= base - tolerance && targetAlt <= top + tolerance) {
				return l.material == Material.LAVA;
			}
			base = top;
		}
		return false;
	}

	// ===== Tephra (conservé, adapté pour prendre l'Eruption en paramètre) =====

	/**
	 * Salve de projectiles BASALT au début d'une éruption. Calque le système
	 * de Module 2 : utilise `e.pressure` au lieu de l'ancien `currentPower`
	 * static. Re-roll si une cible tombe dans la zone persistante (cratère).
	 */
	private void ejectTephra(Eruption e) {
		int n = Math.max(1, Math.round(TEPHRA_COUNT_BASE * e.pressure));
		// Portée balistique ∝ √pressure (sous-linéaire) plutôt que linéaire :
		// à pressure=5 les tephra vont 2.24× plus loin et non 5×. Plus physique
		// (énergie cinétique ½mv² → distance ∝ v, et v ∝ √pressure).
		float rEffective = Math.max(1.0f, eruptionRadius * (float) Math.sqrt(e.pressure));
		float rMin = rCratere;
		float rMax = rEffective * TEPHRA_RANGE_MULT;
		if (rMax < rMin + 1f) rMax = rMin + 1f;
		float thickness = CommonObject.STONE_BLOCK_HEIGHT * e.pressure * TEPHRA_THICKNESS_FACTOR;
		float apex = CommonObject.STONE_BLOCK_HEIGHT * e.pressure * TEPHRA_APEX_FACTOR;
		float sx = sourceX + 0.5f;
		float sy = sourceY + 0.5f;
		float sz = world.getCellTopAltitude(sourceX, sourceY);
		for (int i = 0; i < n; i++) {
			int tx = 0, ty = 0;
			boolean valid = false;
			for (int attempt = 0; attempt < 10; attempt++) {
				double angle = Math.random() * 2 * Math.PI;
				double r = rMin + Math.random() * (rMax - rMin);
				tx = (int) Math.round(sourceX + Math.cos(angle) * r);
				ty = (int) Math.round(sourceY + Math.sin(angle) * r);
				tx = ((tx % _dx) + _dx) % _dx;
				ty = ((ty % _dy) + _dy) % _dy;
				if (world.distance(tx, ty, sourceX, sourceY) > rCratere) {
					valid = true;
					break;
				}
			}
			if (!valid) continue;
			float tz = world.getCellTopAltitude(tx, ty);
			TephraProjectile p = new TephraProjectile(world,
					sx, sy, sz,
					tx + 0.5f, ty + 0.5f, tz,
					tx, ty,
					thickness, apex, TEPHRA_FLIGHT_TICKS);
			world.uniqueDynamicObjects.add(p);
		}

		// === Projections de LAVA (refonte 2026-05-27, feedback utilisateur) ===
		// En plus du tephra BASALT solidifié, on spawn quelques "bombes de lave"
		// liquides qui atterrissent en posant une petite couche LAVA fraîche.
		// Plus petites en thickness, portée plus courte (gouttes fondues lourdes).
		int nLava = Math.max(2, Math.round(TEPHRA_COUNT_BASE * e.pressure * 1.5f));
		float lavaThickness = CommonObject.STONE_BLOCK_HEIGHT * e.pressure * 0.4f;  // un peu plus épais que BASALT
		float lavaApex = apex * 0.7f;  // arc plus bas (gouttes plus lourdes)
		float lavaRMax = rEffective * 1.0f;  // portée plus courte que BASALT
		if (lavaRMax < rMin + 1f) lavaRMax = rMin + 1f;
		LavaLineage projectileLineage = LavaLineage.forSource(e.source);
		for (int i = 0; i < nLava; i++) {
			int tx = 0, ty = 0;
			boolean valid = false;
			for (int attempt = 0; attempt < 10; attempt++) {
				double angle = Math.random() * 2 * Math.PI;
				double r = rMin + Math.random() * (lavaRMax - rMin);
				tx = (int) Math.round(sourceX + Math.cos(angle) * r);
				ty = (int) Math.round(sourceY + Math.sin(angle) * r);
				tx = ((tx % _dx) + _dx) % _dx;
				ty = ((ty % _dy) + _dy) % _dy;
				if (world.distance(tx, ty, sourceX, sourceY) > rCratere) {
					valid = true;
					break;
				}
			}
			if (!valid) continue;
			float tz = world.getCellTopAltitude(tx, ty);
			TephraProjectile p = new TephraProjectile(world,
					sx, sy, sz,
					tx + 0.5f, ty + 0.5f, tz,
					tx, ty,
					lavaThickness, lavaApex, TEPHRA_FLIGHT_TICKS,
					Material.LAVA, projectileLineage);
			world.uniqueDynamicObjects.add(p);
		}
	}

	// ===== Backward-compat stub (tests qui appellent maxLavaThicknessAt) =====

	/** @deprecated supprimé en Module 2 — plus de cap. Retourne MAX_VALUE pour la rétrocompat. */
	@Deprecated
	public float maxLavaThicknessAt(int x, int y) {
		return Float.MAX_VALUE;
	}
}
