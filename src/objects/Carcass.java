package objects;

import javax.media.opengl.GL2;
import loader.GLBModel;
import agents.Mouton;
import agents.Loup;
import worlds.World;

/** Carcasse : objet statique à masse finie laissé par une mise à mort. Les carnivores
 *  la mangent (la masse décroît) ; elle pourrit dans le temps. */
public class Carcass extends UniqueObject {
	/** Énergie gagnée par kg de masse mangée. */
	public static double ENERGY_PER_KG = 10.0;
	/** Durée (sec réelles) de pleine fraîcheur avant que la pourriture ne commence. */
	public static double FRESH_SEC = 120.0;
	/** Durée (sec réelles) de pourriture après la fenêtre fraîche, jusqu'à disparition. */
	public static double ROT_SEC = 60.0;
	/** Fraîcheur minimale (∈[0,1]) pour qu'un loup repu hurle (défaut = fenêtre pleine). */
	public static double FRESH_HOWL_THRESHOLD = 1.0;

	/** Fraction minimale du modèle laissée visible (la tête) tant que la carcasse existe :
	 *  évite qu'il ne reste un copeau ridicule en fin de consommation. */
	private static final float MIN_VISIBLE_FRAC = 0.40f;

	/** Teinte chair fraîche (rougeâtre) -> teinte pourrie (verdâtre/grisâtre terne). */
	private static final float[] FRESH_RGB = { 0.55f, 0.32f, 0.28f };
	private static final float[] ROT_RGB   = { 0.32f, 0.34f, 0.22f };

	/** Temps écoulé depuis l'apparition (sec réelles) — axe pourriture, indépendant de la masse. */
	public double ageSeconds = 0.0;

	public double mass;
	public final double initialMass;
	public final Species source;

	public Carcass(int __x, int __y, World __world, double mass, Species source) {
		super(__x, __y, __world);
		this.mass = mass;
		this.initialMass = mass;
		this.source = source;
	}

	/** Retire jusqu'à kg de masse, renvoie la masse réellement retirée (bornée au restant). */
	public double eat(double kg) {
		double taken = Math.min(Math.max(0.0, kg), mass);
		mass -= taken;
		return taken;
	}

	/** Fraîcheur ∈ [0,1] : 1 pendant FRESH_SEC, puis décroît linéairement sur ROT_SEC. */
	public double freshness() {
		if (ageSeconds <= FRESH_SEC) return 1.0;
		return Math.max(0.0, 1.0 - (ageSeconds - FRESH_SEC) / ROT_SEC);
	}

	/** Assez fraîche pour déclencher un hurlement-nourriture. */
	public boolean isFresh() { return freshness() >= FRESH_HOWL_THRESHOLD; }

	/** true dès que la fenêtre fraîche est dépassée (phase de pourriture, jusqu'à disparition). */
	public boolean isRotten() { return ageSeconds >= FRESH_SEC; }

	/** Fraîcheur AFFICHÉE ∈ [0,1] pour la fiche/jauge : décroît linéairement sur toute
	 *  la fenêtre fraîche (1 à l'apparition → 0 quand la pourriture commence), puis 0.
	 *  (Distincte de {@link #freshness()}, qui pilote couleur + hurlement et reste à 1
	 *  pendant toute la fenêtre fraîche.) */
	public double displayFreshness() {
		if (FRESH_SEC <= 0) return isRotten() ? 0.0 : 1.0;
		return Math.max(0.0, Math.min(1.0, (FRESH_SEC - ageSeconds) / FRESH_SEC));
	}

	/** Valeur énergétique totale restante (pour la fiche UI). */
	public double energyValue() { return mass * ENERGY_PER_KG; }

	public boolean isGone() { return mass <= 0.0 || freshness() <= 0.0; }

	/**
	 * Rend la carcasse : même mesh que l'espèce source, couchée SUR LE FLANC
	 * (roulis 90° autour de Y = axe long du corps), désaturée brun-rougeâtre →
	 * verdâtre selon la fraîcheur, et DÉCOUPÉE de l'arrière-train vers la tête
	 * au fur et à mesure de la consommation (plan de coupe, plus de rétrécissement).
	 *
	 * <p>Convention : à appeler HORS d'un glBegin (la display list GLB ouvre
	 * son propre glBegin en interne — cf. GLBModel.opengldraw).
	 */
	@Override
	public void displayUniqueObject(World myWorld, GL2 gl, int offsetCA_x, int offsetCA_y,
			float offset, float stepX, float stepY, float lenX, float lenY, float normalizeHeight) {

		// --- Sélection du modèle GLB selon l'espèce source ---
		// OURS et HUMAIN n'ont pas de modèle dédié : on utilise le loup comme
		// fallback (silhouette plus proche d'un quadrupède que celle du mouton).
		GLBModel model;
		float baseScale;
		switch (source) {
			case MOUTON:
				model = Mouton.getModel();
				baseScale = 1.5f;   // identique à displayMoutonAt
				break;
			case LOUP:
			case OURS:
			case HUMAIN:
			default:
				model = Loup.getModel();
				baseScale = 2.0f;   // identique à displayLoupAt
				break;
		}
		if (model == null) return;  // modèle pas encore initialisé (démarrage)

		// --- Position monde (même formule que displayMoutonAt / displayLoupAt) ---
		int x2 = (x - (offsetCA_x % myWorld.getWidth()));
		if (x2 < 0) x2 += myWorld.getWidth();
		int y2 = (y - (offsetCA_y % myWorld.getHeight()));
		if (y2 < 0) y2 += myWorld.getHeight();

		// Altitude : sommet de la pile (terrain + layers), avec légère immersion si lave.
		float altitude = myWorld.getCellTopAltitude(x, y);
		Layer top = myWorld.topLayer(x, y);
		if (top != null && top.material == Material.LAVA) {
			altitude -= top.thickness * CommonObject.AGENT_LAVA_DIVE_FRACTION;
		}
		if (altitude < 0) altitude = -1;

		float px = offset + x2 * stepX - lenX;
		float py = offset + y2 * stepY - lenY;

		// --- Taille PLEINE (plus de rétrécissement) ---
		// La consommation n'est plus rendue en rétrécissant tout le corps mais en
		// DÉCOUPANT l'arrière-train mangé via un plan de coupe (cf. plus bas) — plus réaliste.
		float ratio = (float) Math.max(0.0, Math.min(1.0, mass / initialMass));
		// On ne découpe jamais en-dessous d'un minimum visible : sinon, en fin de
		// consommation, il ne reste quasiment rien à l'écran (vilain) alors que la
		// carcasse n'est retirée qu'à masse nulle. On garde donc au moins MIN_VISIBLE_FRAC
		// du modèle (la tête) jusqu'à la disparition complète.
		float visRatio = MIN_VISIBLE_FRAC + (1f - MIN_VISIBLE_FRAC) * ratio;
		float scale = Math.abs(lenX) * baseScale;

		// Demi-largeur du mesh (axe X = flanc) : sert à reposer le corps sur le sol
		// une fois couché sur le flanc, sinon il s'enfonce de sa demi-épaisseur.
		float halfWidth = Math.max(Math.abs(model.leftpoint), Math.abs(model.rightpoint));

		// --- Rendu ---
		// La scène cull globalement GL_FRONT ; GLBModel.opengldrawTinted force
		// GL_BACK en interne et restaure l'état — pas besoin de toucher glCullFace ici.
		gl.glPushMatrix();
		gl.glPushAttrib(GL2.GL_CURRENT_BIT | GL2.GL_LIGHTING_BIT | GL2.GL_ENABLE_BIT | GL2.GL_TRANSFORM_BIT);

		// Couché SUR LE FLANC : roulis +90° autour de l'axe long du corps (Y = nez→queue).
		// La tête reste à l'horizontale vers −Y (avant), les pattes pointent sur le côté.
		// (L'ancienne rotation autour de X plantait le museau dans le sol.)
		// Le +halfWidth×scale relève le corps de sa demi-épaisseur pour qu'il repose au sol.
		gl.glTranslatef(px, py, altitude + halfWidth * scale);
		gl.glRotatef(90f, 0f, 1f, 0f);
		gl.glScalef(scale, scale, scale);

		// Tint interpolé selon la fraîcheur : rougeâtre (fraîche) → verdâtre/grisâtre (pourrie).
		// À freshness=1 : FRESH_RGB = (0.55, 0.32, 0.28) — identique à l'ancien fixe.
		// opengldrawTinted est le chemin immédiat qui multiplie chaque couleur primitive
		// par ce facteur — contourne l'écrasement par la display list.
		// Émission 0 : corpse inerte, ne brille pas la nuit.
		float f = (float) freshness();                      // 1 = fraiche, 0 = pourrie
		float tr = ROT_RGB[0] + (FRESH_RGB[0] - ROT_RGB[0]) * f;
		float tg = ROT_RGB[1] + (FRESH_RGB[1] - ROT_RGB[1]) * f;
		float tb = ROT_RGB[2] + (FRESH_RGB[2] - ROT_RGB[2]) * f;

		// Disparition progressive : on découpe l'arrière-train (Y croissant = queue) au
		// fur et à mesure de la consommation ; la tête (−Y) reste visible en dernier.
		// Plan EN COORDS MODÈLE (émis après translate/rotate/scale) : garde y ≤ yCut.
		boolean clipped = visRatio < 0.999f;
		if (clipped) {
			double yCut = model.bottompoint + (model.toppoint - model.bottompoint) * visRatio;
			gl.glClipPlane(GL2.GL_CLIP_PLANE0, new double[]{ 0.0, -1.0, 0.0, yCut }, 0);
			gl.glEnable(GL2.GL_CLIP_PLANE0);
		}

		model.opengldrawTinted(gl, 0f, tr, tg, tb);

		if (clipped) gl.glDisable(GL2.GL_CLIP_PLANE0);

		gl.glPopAttrib();
		gl.glPopMatrix();
	}
}
