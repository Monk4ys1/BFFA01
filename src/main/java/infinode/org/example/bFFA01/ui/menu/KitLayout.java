package infinode.org.example.bFFA01.ui.menu;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Hotbar order shared by the kit editor. Saved slots win when that material is still in the kit.
 */
public final class KitLayout {

    private KitLayout() {
    }

    public static List<String> resolve(List<String> defaults, Map<Integer, String> saved) {
        List<String> remaining = new ArrayList<>();
        if (defaults != null) {
            for (String material : defaults) {
                String name = normalize(material);
                if (!name.isEmpty()) {
                    remaining.add(name);
                }
            }
        }
        List<String> slots = new ArrayList<>(Collections.nCopies(9, "AIR"));
        if (saved != null) {
            for (int slot = 0; slot < 9; slot++) {
                String wanted = normalize(saved.get(slot));
                int index = remaining.indexOf(wanted);
                if (index >= 0) {
                    slots.set(slot, remaining.remove(index));
                }
            }
        }
        for (int slot = 0; slot < 9 && !remaining.isEmpty(); slot++) {
            if ("AIR".equals(slots.get(slot))) {
                slots.set(slot, remaining.remove(0));
            }
        }
        return List.copyOf(slots);
    }

    public static List<String> swap(List<String> slots, int from, int to) {
        List<String> next = new ArrayList<>(slots == null ? List.of() : slots);
        while (next.size() < 9) {
            next.add("AIR");
        }
        if (next.size() > 9) {
            next = new ArrayList<>(next.subList(0, 9));
        }
        if (from < 0 || to < 0 || from > 8 || to > 8 || from == to) {
            return List.copyOf(next);
        }
        Collections.swap(next, from, to);
        return List.copyOf(next);
    }

    private static String normalize(String material) {
        if (material == null || material.isBlank()) {
            return "";
        }
        return material.trim().toUpperCase(Locale.ROOT);
    }
}
