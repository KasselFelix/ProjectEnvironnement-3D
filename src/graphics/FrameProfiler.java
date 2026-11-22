package graphics;

import java.util.LinkedHashMap;
import java.util.Map;

import javax.media.opengl.GL2;

/**
 * Chronométrage par passe de la boucle de rendu (diagnostic de performance).
 *
 * <p>Inactif par défaut : il ne coûte rien tant que {@code -Dprofile=true}
 * n'est pas passé à la JVM. Actif, il appelle {@code glFinish()} à chaque
 * jalon pour que le temps GPU d'une passe lui soit attribué (sinon le pilote
 * met les commandes en file et tout le coût retombe sur le swap). Ce
 * {@code glFinish} ralentit un peu la frame : les valeurs servent à comparer
 * les passes entre elles, pas à prédire le FPS exact.
 *
 * <p>Toutes les {@link #REPORT_MS} ms, imprime la moyenne par frame de chaque
 * passe dans la console.
 */
public final class FrameProfiler {

    public static final boolean ENABLED = Boolean.getBoolean("profile");
    private static final long REPORT_MS = 3000;

    private final Map<String, long[]> totals = new LinkedHashMap<>(); // nom → {nanos cumulés}
    private long lastMark;
    private int frames;
    private long windowStartMs;

    /** Début de frame : remet le chronomètre à zéro. */
    public void begin(GL2 gl) {
        if (!ENABLED) return;
        gl.glFinish();
        lastMark = System.nanoTime();
        if (windowStartMs == 0L) windowStartMs = System.currentTimeMillis();
    }

    /** Attribue le temps écoulé depuis le jalon précédent à {@code name}. */
    public void mark(GL2 gl, String name) {
        if (!ENABLED) return;
        gl.glFinish();
        long now = System.nanoTime();
        totals.computeIfAbsent(name, k -> new long[1])[0] += now - lastMark;
        lastMark = now;
    }

    private long steps;

    /** Compte les pas de simulation exécutés pendant la frame. */
    public void addSteps(int n) {
        if (ENABLED) steps += n;
    }

    /** Fin de frame : imprime le rapport quand la fenêtre de mesure est écoulée. */
    public void end() {
        if (!ENABLED) return;
        frames++;
        long nowMs = System.currentTimeMillis();
        if (nowMs - windowStartMs < REPORT_MS) return;
        StringBuilder sb = new StringBuilder("[profile] ");
        double sum = 0;
        for (long[] v : totals.values()) sum += v[0];
        sb.append(String.format("%d frames, %.1f ms/frame :", frames, sum / 1e6 / frames));
        for (Map.Entry<String, long[]> e : totals.entrySet()) {
            sb.append(String.format(" %s=%.1f", e.getKey(), e.getValue()[0] / 1e6 / frames));
        }
        long[] sim = totals.get("simulation");
        if (sim != null && steps > 0) {
            sb.append(String.format(" | %.1f pas/frame, %.1f ms/pas",
                    (double) steps / frames, sim[0] / 1e6 / steps));
        }
        System.out.println(sb);
        totals.clear();
        steps = 0;
        frames = 0;
        windowStartMs = nowMs;
    }
}
