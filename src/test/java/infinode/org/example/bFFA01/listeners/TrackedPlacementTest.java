package infinode.org.example.bFFA01.listeners;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.BlockPlaceEvent;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class TrackedPlacementTest {

    @Test
    void restorePutsTheReplacedStateBack() {
        Block block = mock(Block.class);
        BlockState replaced = mock(BlockState.class);

        new TrackedPlacement(block, replaced).restore();

        verify(replaced).update(true, true);
        verify(block, never()).setType(Material.AIR);
    }

    @Test
    void restoreWithoutASnapshotClearsToAir() {
        Block block = mock(Block.class);

        new TrackedPlacement(block, null).restore();

        verify(block).setType(Material.AIR);
    }

    @Test
    void placementTrackingWatchesOnlyFinalUncancelledPlaces() throws Exception {
        Method track = GameListener.class.getDeclaredMethod("onBlockPlaceMonitor", BlockPlaceEvent.class);
        EventHandler handler = track.getAnnotation(EventHandler.class);

        assertEquals(EventPriority.MONITOR, handler.priority());
        assertTrue(handler.ignoreCancelled());
    }

    @Test
    void placementRulesCanStillCancel() throws Exception {
        Method rules = GameListener.class.getDeclaredMethod("onBlockPlace", BlockPlaceEvent.class);
        EventHandler handler = rules.getAnnotation(EventHandler.class);

        assertEquals(EventPriority.NORMAL, handler.priority());
        assertTrue(handler.ignoreCancelled());
    }
}
