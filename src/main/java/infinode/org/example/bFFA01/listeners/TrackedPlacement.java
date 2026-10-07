package infinode.org.example.bFFA01.listeners;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.event.block.BlockMultiPlaceEvent;
import org.bukkit.event.block.BlockPlaceEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * One tracked position. A second place on the same block (slab on slab, snow, candle)
 * keeps the oldest replaced state and restores it only when the last place expires.
 */
public final class TrackedPlacement {

    private final Block block;
    private final BlockState replaced;
    private Material expected;
    private int pending;
    private boolean warned;

    public TrackedPlacement(Block block, BlockState replaced) {
        this(block, replaced, null);
    }

    public TrackedPlacement(Block block, BlockState replaced, Material expected) {
        this.block = block;
        this.replaced = replaced;
        this.expected = expected;
        this.pending = 1;
    }

    public Block block() {
        return block;
    }

    /** Redstone warning belongs to the single live placement, not to an older stacked one. */
    public boolean shouldWarn() {
        return pending == 1;
    }

    public void markWarned() {
        warned = true;
    }

    /**
     * Another successful place on this same block. The original replaced state stays.
     */
    public void stack(Material placed) {
        pending++;
        expected = placed;
        warned = false;
    }

    /**
     * @return false while a newer place at this position is still pending
     */
    public boolean release() {
        if (pending > 1) {
            pending--;
            return false;
        }
        pending = 0;
        if (stillOurs()) {
            restore();
        }
        return true;
    }

    public boolean stillOurs() {
        if (block == null) {
            return false;
        }
        Material current = block.getType();
        if (expected != null && current == expected) {
            return true;
        }
        return warned && current == Material.REDSTONE_BLOCK;
    }

    public void restore() {
        if (replaced != null) {
            replaced.update(true, true);
            return;
        }
        if (block != null) {
            block.setType(Material.AIR);
        }
    }

    static TrackedPlacement track(List<TrackedPlacement> placedBlocks, Block block, BlockState replaced, Material expected) {
        for (int i = placedBlocks.size() - 1; i >= 0; i--) {
            TrackedPlacement existing = placedBlocks.get(i);
            if (existing.samePosition(block)) {
                existing.stack(expected);
                return existing;
            }
        }
        TrackedPlacement created = new TrackedPlacement(block, replaced, expected);
        placedBlocks.add(created);
        return created;
    }

    /**
     * Beds and doors publish one event with a replaced state per half. Each half is tracked
     * on its own so a later restore puts both halves back.
     */
    static List<TrackedPlacement> trackEvent(List<TrackedPlacement> placedBlocks, BlockPlaceEvent event) {
        List<TrackedPlacement> scheduled = new ArrayList<>();
        if (event instanceof BlockMultiPlaceEvent multi) {
            List<BlockState> states = multi.getReplacedBlockStates();
            if (states != null && !states.isEmpty()) {
                Material type = placedType(event);
                for (BlockState state : states) {
                    if (state == null || state.getBlock() == null) {
                        continue;
                    }
                    scheduled.add(track(placedBlocks, state.getBlock(), state, type));
                }
                if (!scheduled.isEmpty()) {
                    return scheduled;
                }
            }
        }
        scheduled.add(track(placedBlocks, event.getBlockPlaced(), event.getBlockReplacedState(), placedType(event)));
        return scheduled;
    }

    static void restoreAll(List<TrackedPlacement> placedBlocks) {
        for (int i = placedBlocks.size() - 1; i >= 0; i--) {
            TrackedPlacement placement = placedBlocks.get(i);
            if (placement.stillOurs()) {
                placement.restore();
            }
        }
        placedBlocks.clear();
    }

    private boolean samePosition(Block other) {
        return block != null && block.equals(other);
    }

    private static Material placedType(BlockPlaceEvent event) {
        Block placed = event.getBlockPlaced();
        if (placed == null || placed.getType() == null) {
            return Material.AIR;
        }
        return placed.getType();
    }
}
