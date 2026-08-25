package scent;

/**
 * Classes d'odeur portees par un puff. Distinct de {@link objects.Species}
 * (qui n'a pas CARCASS et est partage par d'autres switches) : on ne touche pas
 * a l'enum existant. CARCASS permet de sentir une carcasse en C sans cout.
 */
public enum ScentKind {
    LOUP, MOUTON, HUMAIN, OURS, CARCASS;
}
