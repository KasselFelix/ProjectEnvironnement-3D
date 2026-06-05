package landscapegenerator;

/**
 * Génère une heightmap par bruit de Perlin <strong>périodique</strong> (tileable)
 * en X et en Y, adaptée à un monde torique.
 *
 * <p>Adapté de l'algorithme d'Hugo Elias
 * (<a href="http://freespace.virgin.net/hugo.elias/models/m_perlin.htm">référence d'origine</a>) :
 * hash entier + interpolation cosinus + somme multi-octaves. La périodicité a été
 * ajoutée pour que {@code landscape[0][y] == landscape[W-1][y]} (et idem en Y),
 * ce qui élimine la jointure visible quand le monde se replie sur lui-même.</p>
 *
 * <p><strong>Pipeline</strong> (du bas vers le haut) :</p>
 * <ol>
 *   <li>{@link #noise} : hash pseudo-aléatoire entier (déterministe) → [-1, 1]</li>
 *   <li>{@link #smoothNoise_1} : moyenne 3×3 pondérée (passe-bas léger)</li>
 *   <li>{@link #InterpolatedNoise_1} : interpolation cosinus entre 4 sommets entiers</li>
 *   <li>{@link #PerlinNoise_2D} : somme de plusieurs octaves (basses + hautes fréquences)</li>
 *   <li>{@link #generatePerlinNoiseLandscape} : pilote l'ensemble + normalisation + lissage des côtes</li>
 * </ol>
 *
 * <h2>Comment fonctionne la tileability</h2>
 *
 * <p>Le bruit de Perlin est, à la base, défini sur ℝ². On veut le rendre périodique
 * sur un rectangle W×H. La technique : à chaque appel de la fonction de hash
 * {@code noise(x, y)}, on remplace {@code (int)x} par
 * {@code Math.floorMod((int)x, periodX)} (et idem pour y). De cette manière
 * {@code noise(0, ...)} et {@code noise(periodX, ...)} produisent
 * <strong>exactement</strong> la même valeur. Cette propriété se propage à travers
 * les couches supérieures (smoothing, interpolation, octaves), donc la heightmap
 * finale est périodique.</p>
 *
 * <p>Subtilité importante des octaves : à l'octave {@code o}, le bruit est échantillonné
 * à une fréquence {@code 2^o}, donc les coordonnées en espace-bruit sont multipliées
 * par {@code 2^o}. La période en espace-bruit doit donc elle aussi être multipliée
 * par {@code 2^o} pour que la <em>période en espace-pixel</em> reste constante (= W).
 * C'est pourquoi {@link #PerlinNoise_2D} décale la période par {@code << o}.</p>
 *
 * <h2>Contraintes sur {@code fact}</h2>
 *
 * <p>Pour que {@code W * fact * 2^o} soit toujours un entier (sinon on aliase
 * aux jointures), {@link #generatePerlinNoiseLandscape} recalcule {@code fact}
 * dynamiquement à partir de {@code dxView}. Avec {@code fact = 8.0 / dxView},
 * les périodes sont {8, 16, 32, 64...} — toutes des puissances de 2.</p>
 */
public class PerlinNoiseLandscapeGenerator {

	/** Persistence : décroissance d'amplitude entre octaves (1.0 = pas de décroissance). */
	static double persistence = 1;

	/** Nombre d'octaves utilisées (2 à 4, choisi aléatoirement à l'init). */
	static double Number_Of_Octaves = (int)(Math.random()*3)+2;

	/**
	 * Facteur d'échelle de l'espace-bruit. Recalculé dans
	 * {@link #generatePerlinNoiseLandscape} pour que {@code dxView * fact} soit
	 * un entier (condition nécessaire à la tileability).
	 */
	static double fact = 0.0625; // valeur par défaut pour W = 128

	// Constantes du hash (randomisées à chaque lancement → terrain différent).
	static int rand  = (int)(Math.random()*30)+1;        // décalage
	static int rand2 = (int)(Math.random()*1001+10);     // multiplicateur Y
	static int r1    = (int)(Math.random()*10000)+1000;
	static int r2    = (int)(Math.random()*1000000)+100000;
	static int r3    = (int)(Math.random()*2000000000)+100000000;

	/**
	 * Point d'entrée : génère une heightmap périodique de taille
	 * {@code dxView × dyView} via Perlin.
	 *
	 * @param dxView                  largeur de la grille (vertices, pas cellules)
	 * @param dyView                  hauteur de la grille
	 * @param scaling                 amplitude finale (multiplie toutes les altitudes)
	 * @param landscapeAltitudeRatio  point pivot mer/montagne dans [0, 1] (0.4 → 40% sous l'eau)
	 * @param perlinLayerCount        non utilisé (compatibilité historique)
	 * @return heightmap périodique aux deux axes
	 */
	public static double[][] generatePerlinNoiseLandscape(int dxView, int dyView,
			double scaling, double landscapeAltitudeRatio, int perlinLayerCount) {

		// On calcule fact pour que la période de l'octave 0 vaille 8 unités de bruit
		// (donc 8 "blobs" sur toute la largeur du monde). C'est aussi ce qui garantit
		// que (dxView * fact) est un entier.
		fact = 8.0 / dxView;

		// Période en espace-bruit (entière) à l'octave 0 :
		//   periodX = dxView * fact = 8 si dxView = 128
		// À l'octave o, la période sera multipliée par 2^o.
		int periodX = (int)Math.round(dxView * fact);
		int periodY = (int)Math.round(dyView * fact);

		double landscape[][] = new double[dxView][dyView];
		for (int x = 0; x < dxView; x++) {
			for (int y = 0; y < dyView; y++) {
				landscape[x][y] = PerlinNoise_2D(
						(float)(x * fact),
						(float)(y * fact),
						periodX, periodY);
			}
		}

		System.out.println("Map : " + rand + "-" + rand2 + "-" + r1 + "-" + r2 + "-" + r3
				+ ", persistence : " + persistence
				+ ", octave : " + Number_Of_Octaves
				+ ", fact : " + fact
				+ ", periods : (" + periodX + "," + periodY + ")");

		// Mise à l'échelle (centre autour de 0, applique scaling et waterRatio)
		// puis lissage des côtes (les pixels sous l'eau adjacents à de la terre passent à 0).
		landscape = LandscapeToolbox.scaleAndCenter(landscape, scaling, landscapeAltitudeRatio);
		landscape = LandscapeToolbox.smoothLandscape(landscape);

		return landscape;
	}

	/**
	 * Hash entier pseudo-aléatoire : pour une paire (x, y) entière, retourne
	 * une valeur déterministe dans {@code [-1, 1]}. Aucun état interne, c'est
	 * une pure fonction.
	 *
	 * <p>C'est ici qu'on impose la périodicité : les coordonnées entières sont
	 * wrappées modulo (periodX, periodY) avant d'entrer dans la formule du hash.
	 * {@link Math#floorMod} gère correctement les valeurs négatives (contrairement
	 * à l'opérateur % de Java qui peut renvoyer un résultat négatif).</p>
	 */
	public static double noise(double x, double y, int periodX, int periodY) {
		int ix = Math.floorMod((int)Math.floor(x), periodX);
		int iy = Math.floorMod((int)Math.floor(y), periodY);

		int n = ix + iy * rand2;
		n = (n << rand) ^ n;
		return (1.0 - ((n * (n * n * r1 + r2) + r3) & 0x7FFFFFFF) / 1073741824.0);
	}

	/**
	 * Lisse la valeur de bruit en (x, y) par une moyenne pondérée 3×3 :
	 * 1/4 sur le centre, 1/8 sur les côtés, 1/16 sur les coins. C'est un
	 * filtre passe-bas qui adoucit légèrement les hautes fréquences du hash.
	 *
	 * <p>Tous les appels à {@link #noise} reçoivent les mêmes périodes, donc
	 * la périodicité est conservée.</p>
	 */
	public static double smoothNoise_1(float x, float y, int periodX, int periodY) {
		double corners = (noise(x-1, y-1, periodX, periodY) + noise(x+1, y-1, periodX, periodY)
				+ noise(x-1, y+1, periodX, periodY) + noise(x+1, y+1, periodX, periodY)) / 16;
		double sides   = (noise(x-1, y,   periodX, periodY) + noise(x+1, y,   periodX, periodY)
				+ noise(x,   y-1, periodX, periodY) + noise(x,   y+1, periodX, periodY)) / 8;
		double center  = noise(x, y, periodX, periodY) / 4;
		return corners + sides + center;
	}

	/**
	 * Interpolation cosinus : transition douce entre {@code a} (à x=0) et
	 * {@code b} (à x=1). Plus jolie qu'une lerp linéaire (pas de discontinuité
	 * de dérivée), moins coûteuse qu'une cubique.
	 */
	public static double interpolate(double a, double b, double x) {
		double ft = x * Math.PI;
		double f = (1 - Math.cos(ft)) * 0.5;
		return a * (1 - f) + b * f;
	}

	/**
	 * Échantillonne le bruit à une coordonnée flottante (x, y) en interpolant
	 * entre les 4 sommets entiers qui l'entourent. C'est cette étape qui transforme
	 * le hash discret en signal continu.
	 *
	 * <p>Les 4 sommets sont {@code (ix, iy)}, {@code (ix+1, iy)}, {@code (ix, iy+1)},
	 * {@code (ix+1, iy+1)} ; la périodicité dans {@code noise()} assure qu'à la
	 * jointure (ix + 1 == periodX), le sommet "suivant" rebascule sur {@code 0}
	 * et donne la même valeur que de l'autre côté du monde.</p>
	 */
	public static double InterpolatedNoise_1(float x, float y, int periodX, int periodY) {
		int integer_X = (int)Math.floor(x);
		double fractional_X = x - integer_X;

		int integer_Y = (int)Math.floor(y);
		double fractional_Y = y - integer_Y;

		double v1 = smoothNoise_1(integer_X,     integer_Y,     periodX, periodY);
		double v2 = smoothNoise_1(integer_X + 1, integer_Y,     periodX, periodY);
		double v3 = smoothNoise_1(integer_X,     integer_Y + 1, periodX, periodY);
		double v4 = smoothNoise_1(integer_X + 1, integer_Y + 1, periodX, periodY);

		double i1 = interpolate(v1, v2, fractional_X);
		double i2 = interpolate(v3, v4, fractional_X);

		return interpolate(i1, i2, fractional_Y);
	}

	/**
	 * Somme multi-octaves : combine plusieurs couches de bruit à des fréquences
	 * doublées (2, 4, 8...) avec des amplitudes décroissantes. Donne la signature
	 * caractéristique de Perlin : grandes formes générales + détail à plusieurs
	 * échelles.
	 *
	 * <p>À chaque octave {@code o}, on double la fréquence (les coords sont
	 * multipliées par {@code 2^o}) <em>et</em> la période en espace-bruit. La
	 * période en espace-pixel reste donc constante = {@code dxView}, ce qui
	 * préserve la tileability pour TOUTES les octaves.</p>
	 *
	 * @param basePeriodX  période en espace-bruit à l'octave 0 (= dxView * fact)
	 * @param basePeriodY  idem en Y
	 */
	public static double PerlinNoise_2D(float x, float y, int basePeriodX, int basePeriodY) {
		double total = 0;
		double p = persistence;
		double n = Number_Of_Octaves - 1;

		for (double i = 0; i != n; i++) {
			double frequency = Math.pow(2, i);
			double amplitude = Math.pow(p, i);

			// La période grandit avec la fréquence : (basePeriod << i) = basePeriod * 2^i
			int periodX_o = basePeriodX << (int)i;
			int periodY_o = basePeriodY << (int)i;

			total += InterpolatedNoise_1(
					(float)(x * frequency),
					(float)(y * frequency),
					periodX_o, periodY_o) * amplitude;
		}

		return total;
	}
}
