package infinode.org.example.bFFA01.listeners;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.BlockData;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.BlockMultiPlaceEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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

    @Test
    void stackedPlaceKeepsTheOldestStateUntilTheLastExpires() {
        Block block = mock(Block.class);
        when(block.getType()).thenReturn(Material.OAK_PLANKS);
        BlockState air = mock(BlockState.class);
        BlockState singleSlab = mock(BlockState.class);
        List<TrackedPlacement> placed = new ArrayList<>();

        TrackedPlacement first = TrackedPlacement.track(placed, block, air, Material.OAK_SLAB);
        TrackedPlacement second = TrackedPlacement.track(placed, block, singleSlab, Material.OAK_PLANKS);

        assertSame(first, second);
        assertEquals(1, placed.size());
        assertFalse(first.shouldWarn());
        assertFalse(first.release());
        verify(air, never()).update(true, true);
        verify(singleSlab, never()).update(true, true);

        assertTrue(first.shouldWarn());
        assertTrue(first.release());
        verify(air).update(true, true);
        verify(singleSlab, never()).update(true, true);
    }

    @Test
    void clearAllRestoresOnlyOurBlocksAndWalksBackwards() {
        Block older = mock(Block.class);
        Block newer = mock(Block.class);
        Block foreign = mock(Block.class);
        when(older.getType()).thenReturn(Material.SLIME_BLOCK);
        when(newer.getType()).thenReturn(Material.COBWEB);
        when(foreign.getType()).thenReturn(Material.STONE);
        BlockState olderState = mock(BlockState.class);
        BlockState newerState = mock(BlockState.class);
        BlockState foreignState = mock(BlockState.class);
        List<TrackedPlacement> placed = new ArrayList<>();
        TrackedPlacement.track(placed, older, olderState, Material.SLIME_BLOCK);
        TrackedPlacement.track(placed, newer, newerState, Material.COBWEB);
        TrackedPlacement.track(placed, foreign, foreignState, Material.OAK_SLAB);

        TrackedPlacement.restoreAll(placed);

        InOrder order = inOrder(newerState, olderState);
        order.verify(newerState).update(true, true);
        order.verify(olderState).update(true, true);
        verify(foreignState, never()).update(true, true);
        assertTrue(placed.isEmpty());
    }

    @Test
    void clearAllOfAStackRestoresTheOldestStateOnce() {
        Block block = mock(Block.class);
        when(block.getType()).thenReturn(Material.OAK_PLANKS);
        BlockState air = mock(BlockState.class);
        BlockState singleSlab = mock(BlockState.class);
        List<TrackedPlacement> placed = new ArrayList<>();
        TrackedPlacement.track(placed, block, air, Material.OAK_SLAB);
        TrackedPlacement.track(placed, block, singleSlab, Material.OAK_PLANKS);

        TrackedPlacement.restoreAll(placed);

        verify(air).update(true, true);
        verify(singleSlab, never()).update(true, true);
        assertTrue(placed.isEmpty());
    }

    @Test
    void redstoneWarningStillCountsAsOurBlock() {
        Block block = mock(Block.class);
        when(block.getType()).thenReturn(Material.REDSTONE_BLOCK);
        BlockState air = mock(BlockState.class);
        TrackedPlacement placement = new TrackedPlacement(block, air, Material.OAK_SLAB);
        placement.markWarned();

        assertTrue(placement.stillOurs());
        assertTrue(placement.release());
        verify(air).update(true, true);
    }

    @Test
    void bedsDoorsAndSunflowersRestoreBothHalvesWithoutPhysics() {
        Block lowerBlock = mock(Block.class);
        Block upperBlock = mock(Block.class);
        when(lowerBlock.getType()).thenReturn(Material.SUNFLOWER);
        when(upperBlock.getType()).thenReturn(Material.SUNFLOWER);
        BlockData lowerData = mock(BlockData.class);
        BlockData upperData = mock(BlockData.class);
        BlockState lower = mock(BlockState.class);
        BlockState upper = mock(BlockState.class);
        when(lower.getBlock()).thenReturn(lowerBlock);
        when(upper.getBlock()).thenReturn(upperBlock);
        when(lower.getBlockData()).thenReturn(lowerData);
        when(upper.getBlockData()).thenReturn(upperData);
        Block placed = mock(Block.class);
        when(placed.getType()).thenReturn(Material.SUNFLOWER);
        BlockMultiPlaceEvent event = mock(BlockMultiPlaceEvent.class);
        when(event.getReplacedBlockStates()).thenReturn(List.of(lower, upper));
        when(event.getBlockPlaced()).thenReturn(placed);
        doAnswer(invocation -> {
            when(lowerBlock.getType()).thenReturn(Material.AIR);
            return null;
        }).when(upperBlock).setBlockData(upperData, false);

        List<TrackedPlacement> tracked = new ArrayList<>();
        List<TrackedPlacement> scheduled = TrackedPlacement.trackEvent(tracked, event);

        assertEquals(1, scheduled.size());
        assertEquals(2, tracked.size());
        assertEquals(2, scheduled.get(0).members().size());
        TrackedPlacement.restoreAll(tracked);

        InOrder order = inOrder(upperBlock, lowerBlock);
        order.verify(upperBlock).setBlockData(upperData, false);
        order.verify(lowerBlock).setBlockData(lowerData, false);
        verify(lower, never()).update(anyBoolean(), anyBoolean());
        verify(upper, never()).update(anyBoolean(), anyBoolean());
        verify(lowerBlock, never()).setType(any(Material.class), eq(true));
        verify(upperBlock, never()).setType(any(Material.class), eq(true));
        assertTrue(tracked.isEmpty());
    }

    @Test
    void releasingATwoBlockGroupRestoresBothHalvesOnce() {
        Block lowerBlock = mock(Block.class);
        Block upperBlock = mock(Block.class);
        when(lowerBlock.getType()).thenReturn(Material.OAK_DOOR);
        when(upperBlock.getType()).thenReturn(Material.OAK_DOOR);
        BlockData lowerData = mock(BlockData.class);
        BlockData upperData = mock(BlockData.class);
        BlockState lower = mock(BlockState.class);
        BlockState upper = mock(BlockState.class);
        when(lower.getBlock()).thenReturn(lowerBlock);
        when(upper.getBlock()).thenReturn(upperBlock);
        when(lower.getBlockData()).thenReturn(lowerData);
        when(upper.getBlockData()).thenReturn(upperData);
        Block placed = mock(Block.class);
        when(placed.getType()).thenReturn(Material.OAK_DOOR);
        BlockMultiPlaceEvent event = mock(BlockMultiPlaceEvent.class);
        when(event.getReplacedBlockStates()).thenReturn(List.of(lower, upper));
        when(event.getBlockPlaced()).thenReturn(placed);

        List<TrackedPlacement> tracked = new ArrayList<>();
        TrackedPlacement leader = TrackedPlacement.trackEvent(tracked, event).get(0);

        assertTrue(leader.release());
        leader.release();

        verify(upperBlock).setBlockData(upperData, false);
        verify(lowerBlock).setBlockData(lowerData, false);
        verify(lower, never()).update(anyBoolean(), anyBoolean());
        verify(upper, never()).update(anyBoolean(), anyBoolean());
    }
}
