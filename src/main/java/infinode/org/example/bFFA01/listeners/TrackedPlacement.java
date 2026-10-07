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
    private Group group;

    /** Halves of one bed, door, or two-high flower. Restored together, without physics. */
    private static final class Group {
        private final List<TrackedPlacement> members;
        private boolean restored;

        private Group(List<TrackedPlacement> members) {
            this.members = members;
        }
    }

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

    List<TrackedPlacement> members() {
        if (group == null) {
            return List.of(this);
        }
        return group.members;
    }

    /**
     * Single blocks keep the redstone flash. A two-block object is marked on every half
     * that is still ours, without physics, so the partner is not broken and nothing drops.
     */
    void applyWarning() {
        if (group == null || group.members.size() < 2) {
            markWarned();
            if (block != null) {
                block.setType(Material.REDSTONE_BLOCK);
            }
            return;
        }
        for (TrackedPlacement half : group.members) {
            if (!half.stillOurs() || half.block == null) {
                continue;
            }
            half.markWarned();
            half.block.setType(Material.REDSTONE_BLOCK, false);
        }
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
        if (group != null && group.members.size() > 1) {
            restoreGroup();
        } else if (stillOurs()) {
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

    /**
     * Puts the previous block back without a physics update. Physics on one half of a
     * bed, door, or sunflower breaks the other half and drops the item.
     */
    private void restoreWithoutPhysics() {
        if (block == null) {
            return;
        }
        if (replaced != null) {
            block.setBlockData(replaced.getBlockData(), false);
            return;
        }
        block.setType(Material.AIR, false);
    }

    private void restoreGroup() {
        if (group == null || group.restored) {
            return;
        }
        group.restored = true;
        List<TrackedPlacement> ours = new ArrayList<>();
        for (int i = group.members.size() - 1; i >= 0; i--) {
            TrackedPlacement half = group.members.get(i);
            if (half.stillOurs()) {
                ours.add(half);
            }
        }
        for (TrackedPlacement half : ours) {
            half.restoreWithoutPhysics();
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
     * Beds, doors, and two-high flowers publish one event with a replaced state per half.
     * The halves share one timer and are restored together.
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
                if (scheduled.size() >= 2) {
                    Group group = new Group(List.copyOf(scheduled));
                    for (TrackedPlacement half : scheduled) {
                        half.group = group;
                    }
                    return List.of(scheduled.get(0));
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
            if (placement.group != null && placement.group.members.size() > 1) {
                placement.restoreGroup();
                continue;
            }
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
