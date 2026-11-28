package graphics;

/**
 * Trajectoire rendue d'un agent : journal de ses derniers tours et courbe lissée
 * qui les relie, sans aucun appel OpenGL (testable sans contexte GL).
 *
 * <p><b>Journal.</b> À chaque tour, même immobile, l'agent ajoute
 * {@code (x, y, tick, tickDuTourSuivant)} par {@link #record}. Le journal garde les
 * {@link #SIZE} dernières entrées en coordonnées <em>déroulées</em> : un pas qui
 * franchit le bord du tore ajoute ±1 à la coordonnée précédente au lieu de sauter
 * d'un bord à l'autre ; la pose se replie dans [0, w) × [0, h) au rendu. Un saut de
 * plus d'une case (Chebyshev &gt; 1 : respawn, téléportation) réinitialise le journal
 * sur place, comme {@link #ensureCell} quand la case courante n'est pas la dernière
 * entrée. Écrit par la simulation, jamais lu par l'IA.
 *
 * <p><b>Courbe.</b> B-spline quadratique uniforme sur les milieux des cases,
 * {@code m_k = mid(c_{k-1}, c_k)}. Pendant le tour de la dernière entrée
 * {@code c_0}, la pose parcourt le segment de {@code mid(m_-2, m_-1)} à
 * {@code mid(m_-1, m_0)}, point de contrôle {@code m_-1}, avec
 * {@code u = (now - t_0) / (tSuivant_0 - t_0)} borné à [0, 1]. Une ligne droite passe
 * par les cases à vitesse constante, un escalier (pas alternés en X et en Y) devient
 * une diagonale droite au cap constant, un virage de 90° un arc au cap monotone ;
 * départ et arrêt s'étalent sur deux tours. Le rendu a de 1 à 2 cases de retard sur
 * la simulation. La pose est une fonction pure du journal et de {@code now}.
 *
 * <p><b>Agent piloté.</b> Tracé plus réactif : trajet droit de l'avant-dernière case à
 * la dernière pendant le tour (au plus un tour de retard, coude net aux virages), et
 * cap tourné vers la direction demandée ({@link #updateHeadingToward}). Au changement
 * de tracé, un fondu de {@link #MODE_BLEND_TICKS} ticks évite le saut ; c'est le seul
 * autre état hors journal.
 *
 * <p><b>Cap visuel.</b> Seul état hors journal : un angle qui suit la tangente de la
 * courbe tant qu'elle avance, sinon la direction de repli donnée par l'appelant
 * (le dernier déplacement), avec une rotation bornée en degrés par tick. Le temps ne
 * passant pas en pause, le cap s'y fige.
 *
 * <p>Usage par frame : {@link #ensureCell}, {@link #evaluate}, puis
 * {@link #updateHeading} ; lire {@link #poseX()}, {@link #poseY()}, {@link #headingX()},
 * {@link #headingY()}.
 */
public final class MotionTrack {

    /** Nombre d'entrées gardées : la courbe en lit quatre. */
    public static final int SIZE = 4;

    /** Rotation maximale du cap visuel par défaut, en degrés par tick de simulation. */
    public static final double DEFAULT_MAX_TURN_DEG_PER_TICK = 30.0;

    /** Rotation du cap d'un agent piloté : demi-tour en 3 ticks (0,15 s à 20 Hz). */
    public static final double PILOT_MAX_TURN_DEG_PER_TICK = 60.0;

    /** Durée du fondu entre courbe et trajet droit à la prise ou à la remise du pilotage. */
    public static final double MODE_BLEND_TICKS = 10.0;

    /** Sous cette vitesse (cases par tour), la courbe n'avance plus : cap de repli. */
    private static final double MOVING_EPS = 1e-6;

    // Journal circulaire ; head = indice de la dernière entrée.
    private final long[] xs = new long[SIZE];
    private final long[] ys = new long[SIZE];
    // Ticks en double : une entrée recalée (retimeLast) peut tomber entre deux ticks.
    private final double[] ticks = new double[SIZE];
    private final double[] nextTicks = new double[SIZE];
    private int head = -1;  // -1 = journal vide

    // Dernière pose évaluée (repliée sur le tore) et tangente (cases par tour).
    private double poseX, poseY, tanX, tanY;
    private double rawX, rawY;  // pose déroulée du tracé courant, avant fondu (evalRaw)

    // Tracé (courbe ou trajet droit) de l'évaluation précédente et fondu en cours
    // au changement de tracé (écart en cases déroulées, résorbé en MODE_BLEND_TICKS).
    private boolean lastLinear, modeKnown = false, blending = false;
    private double blendOffX, blendOffY, blendT0;

    // Cap visuel (radians, 0 = +X, sens trigonométrique vers +Y).
    private double headingRad;
    private double headingNow;
    private boolean headingSet = false;

    /** Ajoute le tour d'un agent en (x, y), joué au tick {@code tick}, suivant au tick {@code nextTick}. */
    public void record(int x, int y, long tick, long nextTick, int w, int h) {
        if (head < 0) {
            resetAt(x, y, tick, nextTick);
            return;
        }
        long lx = xs[head], ly = ys[head];
        int dx = torusSignedDelta(floorMod(lx, w), x, w);
        int dy = torusSignedDelta(floorMod(ly, h), y, h);
        if (Math.max(Math.abs(dx), Math.abs(dy)) > 1) {
            resetAt(x, y, tick, nextTick);
            return;
        }
        head = (head + 1) % SIZE;
        xs[head] = lx + dx;
        ys[head] = ly + dy;
        ticks[head] = tick;
        nextTicks[head] = nextTick;
    }

    /**
     * Garantit que la dernière entrée est la case (x, y) de l'agent : sinon (journal
     * vide, agent déplacé hors de son tour), réinitialise le journal sur place.
     * @return true si le journal a été réinitialisé
     */
    public boolean ensureCell(int x, int y, int w, int h) {
        if (head >= 0 && floorMod(xs[head], w) == x && floorMod(ys[head], h) == y) return false;
        double t = head >= 0 ? ticks[head] : 0, nt = head >= 0 ? nextTicks[head] : 1;
        resetAt(x, y, t, nt);
        return true;
    }

    /** Remplit le journal de la seule case (x, y) : l'agent y est immobile, sans traversée. */
    public void resetAt(int x, int y, long tick, long nextTick) {
        resetAt(x, y, (double) tick, (double) nextTick);
    }

    private void resetAt(int x, int y, double tick, double nextTick) {
        for (int i = 0; i < SIZE; i++) {
            xs[i] = x;
            ys[i] = y;
            ticks[i] = tick;
            nextTicks[i] = nextTick;
        }
        head = SIZE - 1;
        blending = false;   // repère déroulé neuf : l'écart d'un fondu n'y a plus de sens
    }

    public boolean isEmpty() { return head < 0; }

    /**
     * Recale le tour en cours (dernière entrée) pour qu'il finisse au tick
     * {@code newNextTick} : l'avancement du segment au temps {@code at} est gardé, seul
     * le reste se parcourt à la nouvelle cadence, donc la pose ne saute pas. Sert au
     * changement d'allure d'un agent piloté au milieu de son pas. Sans effet si le
     * journal est vide, si le segment est déjà fini à {@code at}, ou si
     * {@code newNextTick} n'est pas après {@code at}.
     */
    public void retimeLast(double at, double newNextTick) {
        if (head < 0 || newNextTick <= at) return;
        double t0 = ticks[head], t1 = nextTicks[head];
        double u = segmentParam(at, t0, t1);
        if (u >= 1) return;
        // u = (at − t0') / (newNext − t0')  ⇒  t0' = (at − u·newNext) / (1 − u)
        ticks[head] = (at - u * newNextTick) / (1 - u);
        nextTicks[head] = newNextTick;
    }

    /**
     * Calcule la pose au temps {@code now} (en ticks, fraction comprise) et la replie
     * sur le tore. Sans effet si le journal est vide.
     * @return false si le journal est vide
     */
    public boolean evaluate(double now, int w, int h) {
        return evaluate(now, w, h, false);
    }

    /**
     * Comme {@link #evaluate(double, int, int)}, avec le choix du tracé.
     * {@code linear} vrai (agent piloté) : trajet droit de l'avant-dernière case à la
     * dernière pendant le tour de celle-ci ; au plus un tour de retard, coude net aux
     * virages. Faux : la courbe lissée (1 à 2 tours de retard). Au changement de tracé,
     * l'écart entre les deux poses se résorbe en {@link #MODE_BLEND_TICKS} ticks : la
     * prise et la remise du pilotage ne font pas sauter le modèle.
     */
    public boolean evaluate(double now, int w, int h, boolean linear) {
        if (head < 0) return false;
        if (modeKnown && linear != lastLinear) {
            // Pose affichée avec l'ancien tracé (reste de fondu compris), même instant.
            double k = blendWeight(now);
            evalRaw(now, lastLinear);
            double oldX = rawX + blendOffX * k, oldY = rawY + blendOffY * k;
            evalRaw(now, linear);
            blendOffX = oldX - rawX;
            blendOffY = oldY - rawY;
            blendT0 = now;
            blending = true;
        } else {
            evalRaw(now, linear);
        }
        lastLinear = linear;
        modeKnown = true;
        double x = rawX, y = rawY;
        double k = blendWeight(now);
        if (k > 0) {
            x += blendOffX * k;
            y += blendOffY * k;
        } else {
            blending = false;
        }
        poseX = floorMod(x, w);
        poseY = floorMod(y, h);
        return true;
    }

    /** Pose déroulée (rawX/rawY) et tangente du tracé choisi, sans fondu ni repli. */
    private void evalRaw(double now, boolean linear) {
        int i0 = head, i1 = (head + SIZE - 1) % SIZE, i2 = (head + SIZE - 2) % SIZE, i3 = (head + SIZE - 3) % SIZE;
        double u = segmentParam(now, ticks[i0], nextTicks[i0]);
        if (linear) {
            tanX = xs[i0] - xs[i1];
            tanY = ys[i0] - ys[i1];
            rawX = xs[i1] + u * tanX;
            rawY = ys[i1] + u * tanY;
            return;
        }
        // Milieux m_-2, m_-1, m_0 des 4 dernières cases c_-3..c_0.
        double m2x = 0.5 * (xs[i3] + xs[i2]), m2y = 0.5 * (ys[i3] + ys[i2]);
        double m1x = 0.5 * (xs[i2] + xs[i1]), m1y = 0.5 * (ys[i2] + ys[i1]);
        double m0x = 0.5 * (xs[i1] + xs[i0]), m0y = 0.5 * (ys[i1] + ys[i0]);
        double p0x = 0.5 * (m2x + m1x), p0y = 0.5 * (m2y + m1y);
        double p1x = 0.5 * (m1x + m0x), p1y = 0.5 * (m1y + m0y);
        double a = (1 - u) * (1 - u), b = 2 * u * (1 - u), c = u * u;
        rawX = a * p0x + b * m1x + c * p1x;
        rawY = a * p0y + b * m1y + c * p1y;
        tanX = 2 * ((1 - u) * (m1x - p0x) + u * (p1x - m1x));
        tanY = 2 * ((1 - u) * (m1y - p0y) + u * (p1y - m1y));
    }

    /** Poids restant du fondu de changement de tracé, de 1 (début) à 0 (fini). */
    private double blendWeight(double now) {
        if (!blending) return 0;
        double k = 1 - (now - blendT0) / MODE_BLEND_TICKS;
        return k < 0 ? 0 : (k > 1 ? 1 : k);
    }

    /**
     * Fait tourner le cap visuel vers la tangente de la dernière pose évaluée (ou vers
     * ({@code fallbackX}, {@code fallbackY}) si la courbe n'avance pas), d'au plus
     * {@code maxDegPerTick} par tick écoulé depuis l'appel précédent. Le premier appel
     * pose le cap d'un coup ; un {@code now} égal ou antérieur ne le change pas.
     */
    public void updateHeading(double now, double fallbackX, double fallbackY, double maxDegPerTick) {
        double target;
        if (Math.hypot(tanX, tanY) > MOVING_EPS) target = Math.atan2(tanY, tanX);
        else if (fallbackX != 0 || fallbackY != 0) target = Math.atan2(fallbackY, fallbackX);
        else target = headingSet ? headingRad : Math.PI / 2;
        rotateHeading(now, target, maxDegPerTick);
    }

    /**
     * Fait tourner le cap visuel vers ({@code targetX}, {@code targetY}) sans regarder
     * la courbe (agent piloté : le cap suit la direction demandée tout de suite), même
     * borne de rotation que {@link #updateHeading}. Cible nulle : cap inchangé.
     */
    public void updateHeadingToward(double now, double targetX, double targetY, double maxDegPerTick) {
        double target = (targetX != 0 || targetY != 0) ? Math.atan2(targetY, targetX)
                : (headingSet ? headingRad : Math.PI / 2);
        rotateHeading(now, target, maxDegPerTick);
    }

    private void rotateHeading(double now, double target, double maxDegPerTick) {
        if (!headingSet) {
            headingRad = target;
            headingNow = now;
            headingSet = true;
            return;
        }
        double dt = now - headingNow;
        if (dt <= 0) return;
        headingNow = now;
        double diff = wrapAngle(target - headingRad);
        double max = Math.toRadians(maxDegPerTick) * dt;
        headingRad = (Math.abs(diff) <= max) ? target : wrapAngle(headingRad + Math.copySign(max, diff));
    }

    public double poseX() { return poseX; }
    public double poseY() { return poseY; }
    /** Tangente de la courbe à la dernière pose, en cases par tour (0 si immobile). */
    public double tangentX() { return tanX; }
    public double tangentY() { return tanY; }
    /** Cap visuel, vecteur unitaire (même repère que {@code Agent.getLastUnit()}). */
    public double headingX() { return Math.cos(headingRad); }
    public double headingY() { return Math.sin(headingRad); }
    public double headingRad() { return headingRad; }

    /** Dernière entrée (coordonnées déroulées), en lecture seule pour les tests et le
     *  débogage ; journal vide interdit ({@link #isEmpty}). */
    public long lastX() { return xs[head]; }
    public long lastY() { return ys[head]; }
    /** Tick de la dernière entrée (arrondi à l'entier inférieur si elle a été recalée). */
    public long lastTick() { return (long) Math.floor(ticks[head]); }
    /** Tick du tour suivant de la dernière entrée (entier : celui où il se joue). */
    public long lastNextTick() { return (long) Math.ceil(nextTicks[head]); }

    // ------------------------------------------------------------------ helpers purs

    /** Premier tick strictement après {@code tick} où un agent de période {@code period} joue. */
    public static long nextTurnTick(long tick, int period) {
        int p = Math.max(1, period);
        return (Math.floorDiv(tick, p) + 1) * p;
    }

    /** Lecture d'une grille par case ; l'appelant gère le repli sur le tore. */
    @FunctionalInterface
    public interface CellSampler {
        float at(int x, int y);
    }

    /**
     * Interpolation bilinéaire de {@code s} en (fx, fy). Sur une case entière, rend
     * exactement {@code s.at(x, y)} sans lire les voisines ; sur une arête, ne lit que
     * les deux cases de l'arête.
     */
    public static float bilinear(double fx, double fy, CellSampler s) {
        int x0 = (int) Math.floor(fx), y0 = (int) Math.floor(fy);
        double tx = fx - x0, ty = fy - y0;
        float v00 = s.at(x0, y0);
        if (tx == 0 && ty == 0) return v00;
        if (ty == 0) return (float) (v00 + (s.at(x0 + 1, y0) - v00) * tx);
        float v01 = s.at(x0, y0 + 1);
        if (tx == 0) return (float) (v00 + (v01 - v00) * ty);
        float v10 = s.at(x0 + 1, y0), v11 = s.at(x0 + 1, y0 + 1);
        double bottom = v00 + (v10 - v00) * tx;
        double top = v01 + (v11 - v01) * tx;
        return (float) (bottom + (top - bottom) * ty);
    }

    /** Delta torique signé minimal de a vers b sur un axe de taille n (a, b dans [0, n)). */
    static int torusSignedDelta(int a, int b, int n) {
        int fwd = Math.floorMod(b - a, n), bwd = Math.floorMod(a - b, n);
        return (fwd <= bwd) ? fwd : -bwd;
    }

    private static double segmentParam(double now, double t0, double t1) {
        if (t1 <= t0) return 1.0;
        double u = (now - t0) / (t1 - t0);
        return u < 0 ? 0 : (u > 1 ? 1 : u);
    }

    private static int floorMod(long v, int n) { return (int) Math.floorMod(v, (long) n); }

    private static double floorMod(double v, int n) {
        double r = v % n;
        if (r < 0) r += n;
        return (r >= n) ? 0 : r;  // -1e-17 % n + n peut arrondir à n
    }

    private static double wrapAngle(double a) {
        a = a % (2 * Math.PI);
        if (a > Math.PI) a -= 2 * Math.PI;
        else if (a <= -Math.PI) a += 2 * Math.PI;
        return a;
    }
}
