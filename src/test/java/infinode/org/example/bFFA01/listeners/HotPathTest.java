package infinode.org.example.bFFA01.listeners;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HotPathTest {

    @Test
    void mapHeightsAreReadOnceUntilTheMapOrReloadChanges() {
        MapThresholds thresholds = new MapThresholds();
        int[] reads = {0};
        assertEquals(40, thresholds.deathY("arena", 1, () -> {
            reads[0]++;
            return 40;
        }, () -> 90));
        assertEquals(90, thresholds.safeY("arena", 1, () -> {
            reads[0]++;
            return 1;
        }, () -> 1));
        assertEquals(1, reads[0]);
        assertEquals(12, thresholds.deathY("arena", 2, () -> 12, () -> 80));
        assertEquals(80, thresholds.safeY("arena", 2, () -> 12, () -> 80));
    }

    @Test
    void quietCombatSkipsNametagRescans() {
        CombatClock clock = new CombatClock();
        UUID id = UUID.randomUUID();
        CombatClock.Mark first = clock.note(id, false);
        assertTrue(first.changed());
        assertTrue(CombatClock.rescan(0, first.generation()));
        CombatClock.Mark again = clock.note(id, false);
        assertEquals(false, again.changed());
        assertEquals(first.generation(), again.generation());
        assertEquals(false, CombatClock.rescan(again.generation(), again.generation()));
        CombatClock.Mark entered = clock.note(id, true);
        assertTrue(entered.changed());
        assertTrue(CombatClock.rescan(again.generation(), entered.generation()));
    }

    @Test
    void benchCombatScansAndMapLookups() {
        int players = 40;
        int ticks = 200;
        int sink = 0;
        long naiveOps = 0;
        long naiveStart = System.nanoTime();
        for (int tick = 0; tick < ticks; tick++) {
            for (int viewer = 0; viewer < players; viewer++) {
                Set<String> combat = new HashSet<>();
                Set<String> safe = new HashSet<>();
                for (int target = 0; target < players; target++) {
                    String name = "player-" + target;
                    if (target % 5 == 0) {
                        safe.remove(name);
                        if (combat.add(name)) {
                            sink++;
                        }
                    } else {
                        combat.remove(name);
                        if (safe.add(name)) {
                            sink++;
                        }
                    }
                    naiveOps++;
                }
            }
        }
        long naiveNs = System.nanoTime() - naiveStart;

        UUID[] ids = new UUID[players];
        boolean[] combat = new boolean[players];
        int[] seen = new int[players];
        for (int i = 0; i < players; i++) {
            ids[i] = new UUID(0L, i + 1L);
        }
        CombatClock clock = new CombatClock();
        long smartOps = 0;
        long smartStart = System.nanoTime();
        for (int tick = 0; tick < ticks; tick++) {
            if (tick == 20 || tick == 60 || tick == 100 || tick == 140) {
                int who = tick % players;
                combat[who] = !combat[who];
            }
            int generation = 0;
            for (int viewer = 0; viewer < players; viewer++) {
                generation = clock.note(ids[viewer], combat[viewer]).generation();
            }
            for (int viewer = 0; viewer < players; viewer++) {
                if (!CombatClock.rescan(seen[viewer], generation)) {
                    continue;
                }
                Set<String> red = new HashSet<>();
                Set<String> safe = new HashSet<>();
                for (int target = 0; target < players; target++) {
                    String name = "player-" + target;
                    if (combat[target]) {
                        safe.remove(name);
                        if (red.add(name)) {
                            sink++;
                        }
                    } else {
                        red.remove(name);
                        if (safe.add(name)) {
                            sink++;
                        }
                    }
                    smartOps++;
                }
                seen[viewer] = generation;
            }
        }
        long smartNs = System.nanoTime() - smartStart;

        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("maps.arena.death-y-level", 40);
        yaml.set("maps.arena.safezone-y-level", 90);
        yaml.set("default-death-y-level", 50);
        yaml.set("default-safezone-y-level", 90);
        int rounds = 100_000;
        long lookupStart = System.nanoTime();
        for (int i = 0; i < rounds; i++) {
            String map = "arena";
            if (yaml.contains("maps." + map + ".death-y-level")) {
                sink += yaml.getInt("maps." + map + ".death-y-level", 50);
            } else {
                sink += yaml.getInt("default-death-y-level", 50);
            }
        }
        long lookupNs = System.nanoTime() - lookupStart;
        MapThresholds thresholds = new MapThresholds();
        long cacheStart = System.nanoTime();
        for (int i = 0; i < rounds; i++) {
            sink += thresholds.deathY("arena", 3,
                    () -> yaml.getInt("maps.arena.death-y-level", 50),
                    () -> yaml.getInt("maps.arena.safezone-y-level", 90));
        }
        long cacheNs = System.nanoTime() - cacheStart;
        if (sink == Integer.MIN_VALUE) {
            System.out.println("sink");
        }
        System.out.println("BENCH combat nametag ops " + players + " players x " + ticks
                + " ticks: full " + naiveOps + " in " + (naiveNs / 1000) + " us; changed-only "
                + smartOps + " in " + (smartNs / 1000) + " us");
        System.out.println("BENCH map height x" + rounds + ": config key " + (lookupNs / 1000)
                + " us; cached " + (cacheNs / 1000) + " us");
        assertTrue(smartOps < naiveOps / 10);
        assertTrue(cacheNs < lookupNs);
    }
}
