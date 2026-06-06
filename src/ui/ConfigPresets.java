package ui;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import loader.MiniJson;

/**
 * Presets de configuration (V7) : sérialise/désérialise un {@link SimulationConfig}
 * en JSON pour sauvegarder et recharger un scénario. Sérialisation par RÉFLEXION
 * (tous les champs publics primitifs : int/long/double/float/boolean), parse via
 * {@link loader.MiniJson} (déjà embarqué, pas de dépendance externe).
 *
 * Quelques champs purement runtime sont exclus (cf. {@link #SKIP}). Aucune
 * dépendance OpenGL → testable en round-trip.
 */
public final class ConfigPresets {

    private ConfigPresets() {}

    /** Champs non persistés (état runtime, pas un paramètre de scénario). */
    private static final java.util.Set<String> SKIP = java.util.Set.of("awaitingStart");

    /** Sérialise les champs primitifs publics de {@code config} en JSON indenté. */
    public static String toJson(SimulationConfig config) {
        StringBuilder sb = new StringBuilder("{\n");
        boolean first = true;
        for (Field f : SimulationConfig.class.getFields()) {
            if (!isPersistable(f)) continue;
            try {
                Object v = f.get(config);
                if (!first) sb.append(",\n");
                first = false;
                sb.append("  \"").append(f.getName()).append("\": ");
                if (f.getType() == boolean.class) sb.append(v.toString());
                else sb.append(numberToString(v));
            } catch (IllegalAccessException ignored) {}
        }
        sb.append("\n}\n");
        return sb.toString();
    }

    /** Applique à {@code config} les champs présents dans le JSON (les autres
     *  gardent leur valeur). Coercion numérique selon le type du champ. */
    @SuppressWarnings("unchecked")
    public static void applyJson(String json, SimulationConfig config) {
        Object parsed = MiniJson.parse(json);
        if (!(parsed instanceof Map)) return;
        Map<String, Object> map = (Map<String, Object>) parsed;
        for (Map.Entry<String, Object> e : map.entrySet()) {
            Field f;
            try { f = SimulationConfig.class.getField(e.getKey()); }
            catch (NoSuchFieldException ex) { continue; }
            if (!isPersistable(f)) continue;
            Object v = e.getValue();
            try {
                if (f.getType() == boolean.class && v instanceof Boolean) f.setBoolean(config, (Boolean) v);
                else if (v instanceof Number) {
                    double d = ((Number) v).doubleValue();
                    if (f.getType() == int.class)        f.setInt(config, (int) Math.round(d));
                    else if (f.getType() == long.class)  f.setLong(config, Math.round(d));
                    else if (f.getType() == float.class) f.setFloat(config, (float) d);
                    else if (f.getType() == double.class)f.setDouble(config, d);
                }
            } catch (IllegalAccessException ignored) {}
        }
    }

    public static void save(SimulationConfig config, Path file) throws IOException {
        Files.writeString(file, toJson(config));
    }

    public static void load(Path file, SimulationConfig config) throws IOException {
        applyJson(Files.readString(file), config);
    }

    private static boolean isPersistable(Field f) {
        int m = f.getModifiers();
        if (!Modifier.isPublic(m) || Modifier.isStatic(m) || Modifier.isFinal(m)) return false;
        if (SKIP.contains(f.getName())) return false;
        Class<?> t = f.getType();
        return t == int.class || t == long.class || t == double.class
                || t == float.class || t == boolean.class;
    }

    private static String numberToString(Object v) {
        // Les entiers sans partie décimale s'écrivent sans « .0 » pour rester lisibles.
        double d = ((Number) v).doubleValue();
        if (v instanceof Integer || v instanceof Long) return v.toString();
        if (d == Math.rint(d) && !Double.isInfinite(d)) return Long.toString((long) d);
        return String.format(java.util.Locale.US, "%s", v.toString());
    }
}
