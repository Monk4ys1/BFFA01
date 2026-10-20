package infinode.org.example.bFFA01.combat;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Recent hits on each victim. The window decides assists and the death breakdown.
 * Callers pass the clock so tests do not sleep.
 */
public final class DamageBook {

    private final Map<UUID, List<Hit>> hits = new HashMap<>();

    public void record(UUID victim, UUID attacker, String name, long hundredths, String weapon, long atMillis, boolean crit, boolean sweep) {
        if (victim == null || attacker == null || hundredths <= 0 || atMillis < 0) {
            return;
        }
        synchronized (hits) {
            List<Hit> rows = hits.computeIfAbsent(victim, ignored -> new ArrayList<>());
            rows.add(new Hit(attacker, clean(name), hundredths, clean(weapon), atMillis, crit, sweep));
            long cutoff = atMillis - 120_000L;
            rows.removeIf(hit -> hit.at < cutoff);
        }
    }

    public long combo(UUID victim, UUID attacker, long now, long windowMillis) {
        if (victim == null || attacker == null) {
            return 0L;
        }
        long window = Math.max(0L, windowMillis);
        synchronized (hits) {
            List<Hit> rows = hits.get(victim);
            if (rows == null) {
                return 0L;
            }
            long total = 0L;
            for (Hit hit : rows) {
                if (attacker.equals(hit.attacker) && now - hit.at <= window) {
                    total += hit.hundredths;
                }
            }
            return total;
        }
    }

    public List<DamageShare> settle(UUID victim, long now, long windowMillis) {
        if (victim == null) {
            return List.of();
        }
        List<Hit> rows;
        synchronized (hits) {
            rows = hits.remove(victim);
        }
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }
        long window = Math.max(0L, windowMillis);
        Map<UUID, Acc> merged = new HashMap<>();
        for (Hit hit : rows) {
            if (now - hit.at > window) {
                continue;
            }
            Acc acc = merged.computeIfAbsent(hit.attacker, ignored -> new Acc(hit.name));
            acc.hundredths += hit.hundredths;
            if (hit.hundredths >= acc.best) {
                acc.best = hit.hundredths;
                acc.weapon = hit.weapon;
            }
            acc.crit = acc.crit || hit.crit;
            acc.sweep = acc.sweep || hit.sweep;
            if (acc.name.isBlank() && !hit.name.isBlank()) {
                acc.name = hit.name;
            }
        }
        List<DamageShare> shares = new ArrayList<>();
        for (Map.Entry<UUID, Acc> entry : merged.entrySet()) {
            Acc acc = entry.getValue();
            if (acc.hundredths <= 0) {
                continue;
            }
            shares.add(new DamageShare(entry.getKey(), acc.name, acc.hundredths, acc.weapon, acc.crit, acc.sweep));
        }
        shares.sort(Comparator.comparingLong(DamageShare::hundredths).reversed()
                .thenComparing(share -> share.name() == null ? "" : share.name(), String.CASE_INSENSITIVE_ORDER));
        return List.copyOf(shares);
    }

    public void forget(UUID victim) {
        if (victim == null) {
            return;
        }
        synchronized (hits) {
            hits.remove(victim);
        }
    }

    private static String clean(String value) {
        if (value == null) {
            return "";
        }
        String trimmed = value.trim();
        return trimmed.length() > 32 ? trimmed.substring(0, 32) : trimmed;
    }

    private record Hit(UUID attacker, String name, long hundredths, String weapon, long at, boolean crit, boolean sweep) {
    }

    private static final class Acc {
        private String name;
        private long hundredths;
        private long best;
        private String weapon = "";
        private boolean crit;
        private boolean sweep;

        private Acc(String name) {
            this.name = name == null ? "" : name;
        }
    }
}
