package graphics;

/**
 * Calcul des bornes d'une fenêtre de rendu centrée sur le milieu d'une
 * dimension de la grille. Le rendu (Landscape) parcourt aujourd'hui toute la
 * carte ; on le restreint à [lo, hi) autour du centre (= centre caméra) pour ne
 * dessiner que les cases proches. Unité pure (aucune dépendance OpenGL) → testable.
 */
public final class ViewWindow {

    private ViewWindow() {}

    /**
     * Bornes de boucle pour une dimension donnée.
     *
     * @param dim    taille de la dimension de grille (dxView ou dyView)
     * @param radius rayon de la fenêtre en cases (≥ 0)
     * @return {@code int[]{lo, hi}} avec {@code lo} inclus, {@code hi} exclu,
     *         clampés à {@code [0, dim-1]}. La boucle d'origine va de 0 à
     *         {@code dim-1} (exclu) ; ici on centre sur {@code dim/2} et on
     *         borne à {@code radius} cases de part et d'autre. Jamais vide.
     */
    public static int[] of(int dim, int radius) {
        int center = dim / 2;
        int lo = Math.max(0, center - radius);
        int hi = Math.min(dim - 1, center + radius + 1);
        if (hi <= lo) hi = Math.min(dim - 1, lo + 1); // garde-fou : jamais vide
        return new int[] { lo, hi };
    }
}
