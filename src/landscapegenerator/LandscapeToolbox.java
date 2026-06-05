// ### WORLD OF CELLS ### 
// nicolas.bredeche(at)upmc.fr

package landscapegenerator;

public class LandscapeToolbox {

	/**
	 * Normalise puis recentre la heightmap autour de 0.
	 *
	 * <p>Pipeline en deux passes :</p>
	 * <ol>
	 *   <li>Cherche min et max du tableau pour calculer le facteur de normalisation</li>
	 *   <li>Pour chaque case : translation → normalisation dans [0,1] → décalage par
	 *       {@code landscapeAltitudeRatio} (place le pivot mer/montagne) →
	 *       multiplication par {@code __scaling} (amplitude finale) →
	 *       quantification à 2 décimales</li>
	 * </ol>
	 *
	 * <p><strong>Mutation en place</strong> : la heightmap passée en argument est
	 * modifiée directement. La valeur de retour est la même référence (renvoyée
	 * pour le chaînage). Cohérent avec {@link #smoothLandscape}.</p>
	 *
	 * <p>(Le code historique utilisait {@code landscape.clone()} pour stocker une
	 * "copie" — mais c'était un shallow clone sur {@code double[][]}, donc les
	 * sous-tableaux restaient partagés et la copie n'isolait rien. Suppression
	 * du faux clone pour clarifier l'intention.)</p>
	 */
	public static double[][] scaleAndCenter(double[][] landscape, double __scaling, double landscapeAltitudeRatio)
	{
		// Pass 1 : trouver min et max pour la normalisation
		double minValue = landscape[0][0];
		double maxValue = landscape[0][0];

		for (int x = 0; x < landscape.length; x++)
			for (int y = 0; y < landscape[0].length; y++)
			{
				if (landscape[x][y] < minValue)
					minValue = landscape[x][y];
				else if (landscape[x][y] > maxValue)
					maxValue = landscape[x][y];
			}

		double normalizeFactor = 1.0 / (maxValue - minValue);

		// Pass 2 : transformer chaque valeur
		for (int x = 0; x < landscape.length; x++)
			for (int y = 0; y < landscape[0].length; y++)
			{
				landscape[x][y] = (landscape[x][y] - minValue) * normalizeFactor; // [0; 1]
				landscape[x][y] = (landscape[x][y] - landscapeAltitudeRatio) * __scaling;
				// Quantification à 2 décimales (réduit l'aliasing visuel à certains seuils)
				landscape[x][y] = ((int)(landscape[x][y] * 100)) / 100.0;
			}

		return landscape;
	}


	/**
	 * Force la tileability (continuité torique) d'une heightmap en blendant
	 * progressivement ses bords. Utile pour les PNG dessinés sans souci de
	 * raccord torique : sans ce traitement, on voit une jointure brutale au
	 * franchissement du bord (cf. docs/generation-terrain.txt §5).
	 *
	 * <p><strong>Stratégie :</strong> pour chaque ligne y, on calcule la moyenne
	 * {@code avgX = (orig[0][y] + orig[W-1][y]) / 2} ; on remplace alors les
	 * {@code feather} premières et dernières colonnes par une interpolation
	 * linéaire entre {@code avgX} (au bord, poids 1) et la valeur originale
	 * (au-delà de feather, poids 0). Idem en Y pour les lignes haut/bas.</p>
	 *
	 * <p>Résultat : {@code landscape[0][y] == landscape[W-1][y] = avgX[y]} (et
	 * idem en Y), donc le tore se referme parfaitement aux deux axes. Aux 4
	 * coins, la valeur finale est la moyenne des 4 coins d'origine (obtenu
	 * automatiquement par l'enchaînement passe-X puis passe-Y).</p>
	 *
	 * <p><strong>Mutation en place</strong> : modifie l'argument et le renvoie
	 * (pattern cohérent avec {@link #scaleAndCenter} et {@link #smoothLandscape}).</p>
	 *
	 * @param landscape  heightmap à modifier
	 * @param feather    largeur de la zone de transition aux bords, en pixels.
	 *                   {@code feather = 0} → no-op ; {@code feather = W/2} →
	 *                   blending sur toute la moitié de la carte (très visible).
	 *                   Valeur recommandée : {@code W/16} (= 8 px pour W=128).
	 * @return la même heightmap, désormais tileable
	 */
	public static double[][] enforceTileability(double[][] landscape, int feather)
	{
		int W = landscape.length;
		int H = landscape[0].length;

		// Validations basiques : feather hors-bornes → no-op silencieux.
		if (feather <= 0 || feather > W / 2 || feather > H / 2)
			return landscape;

		// Snapshot des valeurs d'origine. On lira depuis le snapshot et on
		// écrira dans landscape, pour éviter de baser les calculs sur des
		// valeurs déjà modifiées au sein de la même passe.
		double[][] orig = new double[W][H];
		for (int x = 0; x < W; x++)
			System.arraycopy(landscape[x], 0, orig[x], 0, H);

		// === Passe X : continuité gauche/droite ===
		// Pour chaque ligne y, on calcule la cible (moyenne des deux extrémités)
		// et on interpole entre cette cible et l'original sur les bandes de
		// feather pixels à gauche et à droite.
		for (int y = 0; y < H; y++)
		{
			double avgX = (orig[0][y] + orig[W - 1][y]) / 2.0;

			// Bande de gauche : x dans [0, feather)
			for (int x = 0; x < feather; x++)
			{
				double t = (double) x / feather;   // 0 au bord, 1 en intérieur
				landscape[x][y] = (1 - t) * avgX + t * orig[x][y];
			}
			// Bande de droite : x dans [W-feather, W)
			for (int x = W - feather; x < W; x++)
			{
				double t = (double) (W - 1 - x) / feather;   // 0 au bord, 1 en intérieur
				landscape[x][y] = (1 - t) * avgX + t * orig[x][y];
			}
		}

		// === Passe Y : continuité haut/bas ===
		// On part des valeurs APRÈS la passe X (snapshot afterX) pour que les
		// coins reçoivent une moyenne cohérente des 4 coins d'origine.
		double[][] afterX = new double[W][H];
		for (int x = 0; x < W; x++)
			System.arraycopy(landscape[x], 0, afterX[x], 0, H);

		for (int x = 0; x < W; x++)
		{
			double avgY = (afterX[x][0] + afterX[x][H - 1]) / 2.0;

			// Bande du haut : y dans [0, feather)
			for (int y = 0; y < feather; y++)
			{
				double t = (double) y / feather;
				landscape[x][y] = (1 - t) * avgY + t * afterX[x][y];
			}
			// Bande du bas : y dans [H-feather, H)
			for (int y = H - feather; y < H; y++)
			{
				double t = (double) (H - 1 - y) / feather;
				landscape[x][y] = (1 - t) * avgY + t * afterX[x][y];
			}
		}

		return landscape;
	}

	/**
	 * Version par défaut de {@link #enforceTileability(double[][], int)} avec
	 * une largeur de feather = max(W, H) / 16. Sur une carte 128×128, ça fait
	 * 8 pixels — un compromis raisonnable entre raccord propre et zone modifiée
	 * peu visible à l'œil.
	 */
	public static double[][] enforceTileability(double[][] landscape)
	{
		int featherSize = Math.max(landscape.length, landscape[0].length) / 16;
		return enforceTileability(landscape, featherSize);
	}


	public static double[][] smoothLandscape ( double[][] landscape )
	{
		int dxView = landscape.length;
		int dyView = landscape[0].length;
		
    	// smoothing coasts (coast tiles will have a zero)
		for ( int x = 0 ; x != dxView ; x++ )
			for ( int y = 0 ; y != dyView ; y++ )
			{
				if ( landscape[x][y] < 0 )//met la cote au niveau 0
				{
					if ( // one neighbor above ground is enough.
							landscape[(x-1+dxView)%dxView][(y-1+dyView)%dyView]>0 || landscape[x][(y-1+dyView)%dyView]>0 || landscape[(x+1)%dxView][(y-1+dyView)%dyView]>0 ||
							landscape[(x-1+dxView)%dxView][y]>0                   										 || landscape[(x+1)%dxView][y]>0      ||
							landscape[(x-1+dxView)%dxView][(y+1+dyView)%dyView]>0 || landscape[x][(y+1+dyView)%dyView]>0 || landscape[(x+1)%dxView][(y+1+dyView)%dyView]>0 ) 
						landscape[x][y] = 0.0;
				}
			}
		
		return landscape;
	}
	
}
