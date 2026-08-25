package input;

import java.util.EnumMap;
import java.util.Map;
import java.util.Properties;
import objects.Species;

/** 9 slots de hotbar PAR ESPECE, persistes dans settings.properties (hotbar.<ESPECE>.<slot>). */
public final class HotbarLayout {
    public static final int SLOTS = 9;

    private final Map<Species, HotbarAction[]> bySpecies = new EnumMap<>(Species.class);

    public HotbarLayout() { resetDefaults(); }

    public void resetDefaults() {
        bySpecies.clear();
        put(Species.LOUP,   HotbarAction.FRAPPER, HotbarAction.MANGER, HotbarAction.HURLER, HotbarAction.SE_REPOSER);
        put(Species.OURS,   HotbarAction.FRAPPER, HotbarAction.MANGER, HotbarAction.SE_REPOSER);
        put(Species.MOUTON, HotbarAction.BROUTER, HotbarAction.SE_REPOSER);
        put(Species.HUMAIN, HotbarAction.FRAPPER);
    }

    public HotbarAction slot(Species s, int index) {
        HotbarAction[] arr = bySpecies.get(s);
        return (arr == null || index < 0 || index >= SLOTS) ? HotbarAction.VIDE : arr[index];
    }

    public void assign(Species s, int index, HotbarAction a) {
        bySpecies.computeIfAbsent(s, k -> filled())[index] = a;
    }

    public void writeTo(Properties p) {
        for (Map.Entry<Species, HotbarAction[]> e : bySpecies.entrySet())
            for (int i = 0; i < SLOTS; i++)
                p.setProperty("hotbar." + e.getKey().name() + "." + i, e.getValue()[i].name());
    }

    public void readFrom(Properties p) {
        resetDefaults();
        for (String name : p.stringPropertyNames()) {
            if (!name.startsWith("hotbar.")) continue;
            String[] parts = name.split("\\.");
            if (parts.length != 3) continue;
            try {
                Species s = Species.valueOf(parts[1]);
                int idx = Integer.parseInt(parts[2]);
                if (idx >= 0 && idx < SLOTS) assign(s, idx, HotbarAction.valueOf(p.getProperty(name)));
            } catch (IllegalArgumentException ignore) {}   // espece/action/index inconnus -> defauts
        }
    }

    private void put(Species s, HotbarAction... actions) {
        HotbarAction[] arr = filled();
        System.arraycopy(actions, 0, arr, 0, actions.length);
        bySpecies.put(s, arr);
    }

    private static HotbarAction[] filled() {
        HotbarAction[] arr = new HotbarAction[SLOTS];
        java.util.Arrays.fill(arr, HotbarAction.VIDE);
        return arr;
    }
}
