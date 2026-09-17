package infinode.org.example.bFFA01.game;

import infinode.org.example.bFFA01.BFFA01;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Tracks every block players place so the arena resets itself.
 *
 * <p>4.x kept the blocks in an {@code ArrayList} and scheduled two tasks per
 * block, then did an O(n) {@code contains} inside each of them. With a busy
 * arena that is thousands of scheduled tasks and a quadratic scan. This version
 * keeps one hash map and one sweep task.
 */
public final class BlockManager {

    /** How often the sweep runs. Quarter-second resolution is plenty. */
    private static final long SWEEP_INTERVAL_TICKS = 5L;

    private final BFFA01 plugin;
    private final Map<Block, Tracked> tracked = new LinkedHashMap<>();
    private BukkitTask sweepTask;

    public BlockManager(BFFA01 plugin) {
        this.plugin = plugin;
    }

    /** One tracked block and the two moments that matter for it. */
    private static final class Tracked {
        private final long warnAt;
        private final long removeAt;
        private final Material warnMaterial;
        private boolean warned;

        private Tracked(long warnAt, long removeAt, Material warnMaterial) {
            this.warnAt = warnAt;
            this.removeAt = removeAt;
            this.warnMaterial = warnMaterial;
        }
    }

    public void start() {
        stop();
        sweepTask = plugin.getServer().getScheduler()
                .runTaskTimer(plugin, this::sweep, SWEEP_INTERVAL_TICKS, SWEEP_INTERVAL_TICKS);
    }

    public void stop() {
        if (sweepTask != null) {
            sweepTask.cancel();
            sweepTask = null;
        }
    }

    /** Tracks a block a player placed, using the configured lifetime. */
    public void track(Block block) {
        long lifetimeMillis = plugin.settings().blockRemoveDelay() * 1000L;
        long warningMillis = plugin.settings().blockWarningSeconds() * 1000L;
        long now = System.currentTimeMillis();
        tracked.put(block, new Tracked(
                now + Math.max(0L, lifetimeMillis - warningMillis),
                now + lifetimeMillis,
                warningMillis > 0L ? plugin.settings().blockWarningMaterial() : null));
    }

    /**
     * Tracks a block placed by an item effect (rescue platform, web grenade).
     * These disappear without the warning colour change.
     */
    public void trackTemporary(Block block, int lifetimeSeconds) {
        long now = System.currentTimeMillis();
        long removeAt = now + Math.max(1, lifetimeSeconds) * 1000L;
        tracked.put(block, new Tracked(removeAt, removeAt, null));
    }

    public boolean isTracked(Block block) {
        return tracked.containsKey(block);
    }

    /** Stops tracking a block without changing the world. */
    public void untrack(Block block) {
        tracked.remove(block);
    }

    public int trackedCount() {
        return tracked.size();
    }

    /** Clears every tracked block, used before a map swap and on shutdown. */
    public void clearAll() {
        List<Block> blocks = new ArrayList<>(tracked.keySet());
        tracked.clear();
        for (Block block : blocks) {
            clearBlock(block);
        }
    }

    private void sweep() {
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<Block, Tracked>> iterator = tracked.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Block, Tracked> entry = iterator.next();
            Tracked state = entry.getValue();
            if (now >= state.removeAt) {
                iterator.remove();
                clearBlock(entry.getKey());
            } else if (!state.warned && state.warnMaterial != null && now >= state.warnAt) {
                state.warned = true;
                Block block = entry.getKey();
                if (!block.getType().isAir()) {
                    block.setType(state.warnMaterial, false);
                }
            }
        }
    }

    /** Only clears blocks that are still solid, never a player's own build. */
    private void clearBlock(Block block) {
        if (!block.getType().isAir()) {
            block.setType(Material.AIR, false);
        }
    }
}
