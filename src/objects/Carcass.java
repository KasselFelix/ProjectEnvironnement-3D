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
	public static double FRESH_SEC = 30.0;
	/** Durée (sec réelles) de pourriture après la fenêtre fraîche, jusqu'à disparition. */
	public static double ROT_SEC = 60.0;
	/** Fraîcheur minimale (∈[0,1]) pour qu'un loup repu hurle (défaut = fenêtre pleine). */
	public static double FRESH_HOWL_THRESHOLD = 1.0;

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

	/** Valeur énergétique totale restante (pour la fiche UI). */
	public double energyValue() { return mass * ENERGY_PER_KG; }

	public boolean isGone() { return mass <= 0.0 || freshness() <= 0.0; }

	/**
	 * Rend la carcasse : même mesh que l'espèce source, couchée à plat
	 * (rotation 90° autour de X), désaturée brun-rougeâtre foncé, rétrécie
	 * au fur et à mesure de la consommation.
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

		// --- Facteur de rétrécissement : 1.0 quand pleine, 0.4 quand vide ---
		float shrink = 0.4f + 0.6f * (float) Math.max(0.0, mass / initialMass);
		float scale  = Math.abs(lenX) * baseScale * shrink;

		// --- Rendu ---
		// La scène cull globalement GL_FRONT ; GLBModel.opengldrawTinted force
		// GL_BACK en interne et restaure l'état — pas besoin de toucher glCullFace ici.
		gl.glPushMatrix();
		gl.glPushAttrib(GL2.GL_CURRENT_BIT | GL2.GL_LIGHTING_BIT);

		// Positionner, coucher à plat, mettre à l'échelle.
		gl.glTranslatef(px, py, altitude);
		// Rotation X +90° : bascule le mesh Z-up vers la position couchée
		// (le «dos» du modèle regarde vers le haut, les pattes à plat).
		gl.glRotatef(90f, 1f, 0f, 0f);
		gl.glScalef(scale, scale, scale);

		// Tint brun-rougeâtre sombre : appliqué en mode immédiat par
		// opengldrawTinted, qui multiplie chaque couleur primitive (glColor4f
		// de la display list) par ce facteur — contourne l'écrasement par la
		// display list. Émission 0 : corpse inerte, ne brille pas la nuit.
		model.opengldrawTinted(gl, 0f, 0.55f, 0.32f, 0.28f);

		gl.glPopAttrib();
		gl.glPopMatrix();
	}
}
