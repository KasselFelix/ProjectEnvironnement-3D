package objects;

import javax.media.opengl.GL2;
import worlds.World;

/** Carcasse : objet statique à masse finie laissé par une mise à mort. Les carnivores
 *  la mangent (la masse décroît) ; elle pourrit dans le temps. Rendu : Task 10. */
public class Carcass extends UniqueObject {
	/** Énergie gagnée par kg de masse mangée. */
	public static double ENERGY_PER_KG = 10.0;
	/** Durée (sec réelles) avant pourriture complète d'une carcasse ignorée. */
	public static double LIFETIME_SEC = 90.0;

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

	public boolean isGone() { return mass <= 0.0; }

	@Override
	public void displayUniqueObject(World myWorld, GL2 gl, int offsetCA_x, int offsetCA_y,
			float offset, float stepX, float stepY, float lenX, float lenY, float normalizeHeight) {
		// Rendu implémenté en Task 10.
	}
}
