package agents.ai;

/**
 * Les trois états d'un axe génétique (cf. docs/evolution.txt § 4.1) :
 * pôle NÉGATIF, NEUTRE, ou pôle POSITIF. Le {@code sign} ({-1, 0, +1}) sert au
 * calcul des modificateurs mécaniques.
 */
public enum Pole {
    NEGATIVE(-1),
    NEUTRAL(0),
    POSITIVE(1);

    public final int sign;

    Pole(int sign) {
        this.sign = sign;
    }
}
