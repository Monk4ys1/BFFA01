package infinode.org.example.bFFA01.ui.vote;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * One vote per player. A tie uses the supplied seed so the choice is testable.
 */
public final class MapVotes {

    private final Map<UUID, String> votes = new ConcurrentHashMap<>();

    public boolean vote(UUID player, String map, Set<String> allowed) {
        if (player == null || map == null || allowed == null || !allowed.contains(map)) {
            return false;
        }
        votes.put(player, map);
        return true;
    }

    public String selection(UUID player) {
        return player == null ? null : votes.get(player);
    }

    public int count(String map) {
        if (map == null) {
            return 0;
        }
        int total = 0;
        for (String vote : votes.values()) {
            if (map.equals(vote)) {
                total++;
            }
        }
        return total;
    }

    public String winner(Collection<String> candidates, long seed) {
        if (candidates == null || candidates.isEmpty()) {
            return null;
        }
        int best = 0;
        List<String> tied = new ArrayList<>();
        for (String map : candidates) {
            int count = count(map);
            if (count <= 0) {
                continue;
            }
            if (count > best) {
                best = count;
                tied.clear();
                tied.add(map);
            } else if (count == best) {
                tied.add(map);
            }
        }
        if (tied.isEmpty()) {
            return null;
        }
        if (tied.size() == 1) {
            return tied.get(0);
        }
        return tied.get(Math.floorMod(seed, tied.size()));
    }

    public void remove(UUID player) {
        if (player != null) {
            votes.remove(player);
        }
    }

    public void clear() {
        votes.clear();
    }
}
