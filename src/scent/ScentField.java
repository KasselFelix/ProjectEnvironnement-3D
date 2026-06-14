package scent;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import ui.SimulationConfig;
import worlds.World;

/**
 * Champ d'odeur lagrangien (sous-projet A). Une liste eparse de puffs
 * gaussiens, echantillonnee a la demande ; pas de grille par cellule, pas
 * d'index spatial (scan complet sous budget {@link #MAX_PUFFS}). Le vent
 * advecte les centres (anisotropie de panache), la pluie/l'eau accelerent la
 * decroissance. Possede par {@link World}.
 */
public final class ScentField {

    // ── Constantes physiques (calibrage) ────────────────────────────────
    static final float SIGMA0     = 0.8f;    // rayon initial (cellules)
    static final float K_BASE     = 0.015f;  // etalement de base / sec
    static final float K_WIND     = 0.02f;   // etalement aval supplementaire par (m/s) / sec
    static final float DIL_K      = 0.05f;   // dilution par le vent (par m/s)
    static final float RAIN_K     = 1.0f;    // dilution pluie
    static final float EPSILON    = 0.01f;   // seuil de purge / detection
    static final float WATER_FADE = 4.0f;    // sur-vieillissement au-dessus de l'eau (x par sec)
    static final int   MAX_PUFFS  = 4000;    // budget dur

    // ── Emission (lues aussi par Agent) ─────────────────────────────────
    public static final int   WET_SUPPRESSION_TICKS = 40;   // ~2 s a 20 Hz
    public static final float WET_EMIT_FACTOR        = 0.2f;

    private final World world;
    private final List<ScentPuff> puffs = new ArrayList<>();

    public ScentField(World world) { this.world = world; }

    private int hz() {
        int hz = SimulationConfig.getInstance().simulationHz;
        return hz > 0 ? hz : 20;
    }

    /** Nombre de puffs vivants (tests + debug). */
    public int size() { return puffs.size(); }

    /** Ajoute un puff. Ignore les intensites nulles/negatives. */
    public void emit(int emitterId, ScentKind kind, int familyId,
                     int x, int y, int birthTick, float baseIntensity) {
        if (baseIntensity <= 0f) return;
        puffs.add(new ScentPuff(emitterId, kind, familyId, x, y, birthTick, baseIntensity));
    }

    /**
     * Echantillonne le champ en (px,py) au temps {@code now}. Somme l'intensite
     * de chaque puff via le noyau gaussien anisotrope, retient l'emetteur du
     * puff le plus intense au point lu.
     */
    public ScentReading sampleAt(int px, int py, int now) {
        final int hz = hz();
        final double lifeSec = SimulationConfig.getInstance().scentLifetimeSec;
        final double windX = world.getWindX(), windY = world.getWindY();
        final double windMag = Math.hypot(windX, windY);
        final boolean raining = world.isRaining();
        final int w = world.getWidth(), h = world.getHeight();

        float[] perKind = new float[ScentKind.values().length];
        int domEmitter = 0, domFamily = -1;
        ScentKind domKind = null;
        float domI = 0f;

        for (ScentPuff p : puffs) {
            float i = intensityAt(p, px, py, now, hz, lifeSec, windX, windY, windMag, raining, w, h);
            if (i <= 0f) continue;
            perKind[p.kind.ordinal()] += i;
            if (i > domI) { domI = i; domEmitter = p.emitterId; domFamily = p.familyId; domKind = p.kind; }
        }
        return new ScentReading(perKind, domEmitter, domFamily, domKind, domI);
    }

    /** Constante de temps effective (sec) : dilution par le vent + pluie. */
    static double tauEff(double lifeSec, double windMag, boolean raining) {
        return lifeSec / (1.0 + DIL_K * windMag + (raining ? RAIN_K : 0.0));
    }

    /** Intensite courante au CENTRE d'un puff (sans le terme gaussien spatial). */
    static double peakIntensity(ScentPuff p, int now, int hz, double tauEff) {
        double age = (now - p.birthTick) / (double) hz + p.extraAgeSec;
        return p.baseIntensity * Math.exp(-age / tauEff);
    }

    /**
     * Snapshot debug (overlay) : {cx, cy, peak, kindOrdinal} par puff vivant.
     * O(n), pour un rendu direct des particules (evite un scan O(cellules x
     * puffs)). Le 4e champ porte {@link ScentKind#ordinal()} pour colorer chaque
     * puff par espece. Note : tauEff est recalcule depuis la config courante au
     * moment du rendu ; le snapshot peut donc differer legerement du dernier
     * step() si un slider a bouge dans la meme frame (sans incidence — debug).
     */
    public float[][] debugPuffSnapshot(int now) {
        final int hz = hz();
        final double te = tauEff(SimulationConfig.getInstance().scentLifetimeSec,
                                 Math.hypot(world.getWindX(), world.getWindY()), world.isRaining());
        float[][] out = new float[puffs.size()][4];
        for (int i = 0; i < puffs.size(); i++) {
            ScentPuff p = puffs.get(i);
            out[i][0] = p.cx; out[i][1] = p.cy;
            out[i][2] = (float) peakIntensity(p, now, hz, te);
            out[i][3] = p.kind.ordinal();
        }
        return out;
    }

    /**
     * Intensite d'UN puff perçue en (px,py) au temps {@code now}. Package-visible
     * pour les tests. age = (now-naissance)/hz + sur-vieillissement.
     */
    static float intensityAt(ScentPuff p, int px, int py, int now,
                             int hz, double lifeSec, double windX, double windY,
                             double windMag, boolean raining, int w, int h) {
        double age = (now - p.birthTick) / (double) hz + p.extraAgeSec;
        if (age < 0) return 0f;
        double tauEff = tauEff(lifeSec, windMag, raining);
        double peak = p.baseIntensity * Math.exp(-age / tauEff);
        if (peak < EPSILON) return 0f;

        double dx = signedDelta(p.cx, px, w);
        double dy = signedDelta(p.cy, py, h);

        if (windMag > 1e-6) {
            double ux = windX / windMag, uy = windY / windMag;
            double dPar  = dx * ux + dy * uy;          // le long du vent
            double dPerp = dx * (-uy) + dy * ux;       // en travers
            double sigPar  = SIGMA0 + (K_BASE + K_WIND * windMag) * age;
            double sigPerp = SIGMA0 + K_BASE * age;
            double e = (dPar * dPar) / (2 * sigPar * sigPar)
                     + (dPerp * dPerp) / (2 * sigPerp * sigPerp);
            return (float) (peak * Math.exp(-e));
        } else {
            double sig = SIGMA0 + K_BASE * age;
            double e = (dx * dx + dy * dy) / (2 * sig * sig);
            return (float) (peak * Math.exp(-e));
        }
    }

    /** Delta torique signe minimal de a vers b sur un axe de taille n. */
    static double signedDelta(double a, double b, int n) {
        double d = b - a;
        if (d >  n / 2.0) d -= n;
        if (d < -n / 2.0) d += n;
        return d;
    }

    static double wrap(double v, int n) {
        v %= n;
        if (v < 0) v += n;
        return v;
    }

    /**
     * Avance le champ d'un tick (dt en secondes) : advection des centres par le
     * vent courant, sur-vieillissement au-dessus de l'eau, purge des puffs
     * eteints, application du budget dur.
     */
    public void step(double dtSec) {
        final double windX = world.getWindX(), windY = world.getWindY();
        final double driftK = SimulationConfig.getInstance().scentWindDrift;
        final int now = world.getIteration();
        final int hz = hz();
        final double lifeSec = SimulationConfig.getInstance().scentLifetimeSec;
        final double windMag = Math.hypot(windX, windY);
        final boolean raining = world.isRaining();
        final int w = world.getWidth(), h = world.getHeight();

        Iterator<ScentPuff> it = puffs.iterator();
        while (it.hasNext()) {
            ScentPuff p = it.next();
            // advection (integre le vent courant -> correct sous vent variable)
            p.cx = (float) wrap(p.cx + windX * dtSec * driftK, w);
            p.cy = (float) wrap(p.cy + windY * dtSec * driftK, h);
            // estompage eau : centre derive au-dessus d'une cellule d'eau
            if (world.getCellHeight(Math.round(p.cx), Math.round(p.cy)) < 0)
                p.extraAgeSec += (float) (dtSec * WATER_FADE);
            // purge si pic eteint
            double te = tauEff(lifeSec, windMag, raining);
            if (peakIntensity(p, now, hz, te) < EPSILON) it.remove();
        }
        enforceBudget(now, hz, lifeSec, windMag, raining);
    }

    /** Au-dela du budget, retire les puffs de plus faible pic courant (un seul passage). */
    private void enforceBudget(int now, int hz, double lifeSec, double windMag, boolean raining) {
        int excess = puffs.size() - MAX_PUFFS;
        if (excess <= 0) return;
        double te = tauEff(lifeSec, windMag, raining);
        final double[] peak = new double[puffs.size()];
        for (int i = 0; i < peak.length; i++) {
            peak[i] = peakIntensity(puffs.get(i), now, hz, te);
        }
        Integer[] idx = new Integer[peak.length];
        for (int i = 0; i < idx.length; i++) idx[i] = i;
        java.util.Arrays.sort(idx, (a, b) -> Double.compare(peak[a], peak[b]));   // pic croissant
        int[] toRemove = new int[excess];
        for (int i = 0; i < excess; i++) toRemove[i] = idx[i];                    // les `excess` plus faibles
        java.util.Arrays.sort(toRemove);                                          // index croissant
        for (int i = excess - 1; i >= 0; i--) puffs.remove(toRemove[i]);          // retrait par index decroissant
    }

    /**
     * Direction cardinale (0=N/1=E/2=S/3=O, -1 si rien) de plus forte odeur de
     * la classe {@code kind} parmi les 4 voisins cardinaux. Primitive de
     * pistage (B/C) et, gradient inverse, d'evitement.
     */
    public int gradientToward(int px, int py, int now, ScentKind kind) {
        final int w = world.getWidth(), h = world.getHeight();
        int[] nx = { px, (px + 1) % w, px, (px - 1 + w) % w };
        int[] ny = { (py - 1 + h) % h, py, (py + 1) % h, py };
        int best = -1;
        float bestI = EPSILON;
        for (int d = 0; d < 4; d++) {
            float i = sampleAt(nx[d], ny[d], now).of(kind);
            if (i > bestI) { bestI = i; best = d; }
        }
        return best;
    }

    /** Intensite totale toutes classes confondues (overlay debug). */
    public float debugIntensityAt(int px, int py, int now) {
        float[] pk = sampleAt(px, py, now).perKind();
        float s = 0f;
        for (float v : pk) s += v;
        return s;
    }
}
