package infinode.org.example.bFFA01.listeners;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;

/**
 * A placed block plus the state it replaced. Removal restores that state
 * instead of forcing air onto a block the place never actually changed.
 */
public final class TrackedPlacement {

    private final Block block;
    private final BlockState replaced;

    public TrackedPlacement(Block block, BlockState replaced) {
        this.block = block;
        this.replaced = replaced;
    }

    public Block block() {
        return block;
    }

    public void restore() {
        if (replaced != null) {
            replaced.update(true, true);
            return;
        }
        block.setType(Material.AIR);
    }
}
