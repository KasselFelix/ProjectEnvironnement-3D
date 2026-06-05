package landscapegenerator;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import javax.imageio.ImageIO;

/**
 * Charge une heightmap depuis un fichier image (PNG, JPG, etc.).
 *
 * <p>La luminance du canal rouge de chaque pixel est interprétée comme altitude
 * (rouge = 255 → haut, rouge = 0 → bas). Les canaux vert et bleu sont ignorés.</p>
 *
 * <p><strong>Tileability</strong> : la plupart des PNG ne sont pas conçus pour
 * tiler (les bords gauche/droite et haut/bas ne se rejoignent pas en altitude).
 * Sans précaution, on verrait une jointure brutale au franchissement du tore.
 * {@link #load} applique automatiquement {@link LandscapeToolbox#enforceTileability}
 * pour blender progressivement les bords. Si l'image source est déjà tileable
 * (par exemple traitée avec Gimp → Filtres → Carte → Rendre raccordable), ce
 * traitement n'affecte quasiment pas le rendu.</p>
 */
public class LoadFromFileLandscape {

	/**
	 * Charge un fichier image et produit une heightmap normalisée et tileable.
	 *
	 * @param __filename                  chemin du fichier image (relatif à CWD)
	 * @param __scaling                   amplitude finale des altitudes
	 * @param __landscapeAltitudeRatio    pivot mer/montagne dans [0, 1]
	 * @return heightmap de taille {@code (image.width × image.height)} prête
	 *         à être passée à {@link worlds.World#init}.
	 */
	public static double[][] load(String __filename, double __scaling, double __landscapeAltitudeRatio)
	{
		double landscape[][] = null;

		try {
			BufferedImage bi = ImageIO.read(new File(__filename));

			landscape = new double[bi.getWidth()][bi.getHeight()];

			// Lecture pixel par pixel : on ne garde que le canal rouge.
			// L'axe Y est inversé (l'origine d'une image est en haut à gauche,
			// alors qu'on veut un repère mathématique en bas à gauche).
			for (int x = 0; x != bi.getWidth(); x++)
				for (int y = 0; y != bi.getHeight(); y++)
				{
					int rawvalue = bi.getRGB(x, y);
					int[] rgb = new int[3];
					rgb[0] = (rawvalue & 0x00FF0000) / (int) Math.pow(256, 2); // red
					rgb[1] = (rawvalue & 0x0000FF00) / 256;                    // green
					rgb[2] = (rawvalue & 0x000000FF);                          // blue
					landscape[x][bi.getHeight() - 1 - y] = ((double) rgb[0]) / 255.0;
				}
		}
		catch (IOException e)
		{
			System.err.println("[error] image \"" + __filename + "\" could not be loaded.");
			System.exit(-1);
		}

		// Continuité torique : on blend les bords pour que la heightmap se
		// referme proprement gauche/droite et haut/bas. Indispensable pour
		// les PNG non conçus tileables. Si l'image est déjà tileable, l'effet
		// est imperceptible.
		landscape = LandscapeToolbox.enforceTileability(landscape);

		// Pipeline standard : normalisation autour de 0 puis lissage des côtes.
		landscape = LandscapeToolbox.scaleAndCenter(landscape, __scaling, __landscapeAltitudeRatio);
		landscape = LandscapeToolbox.smoothLandscape(landscape);

		return landscape;
	}
}
