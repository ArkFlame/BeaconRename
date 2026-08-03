package com.arkflame.flameforge.station;

import com.arkflame.flameforge.compat.scheduler.SchedulerBridge;
import com.arkflame.flameforge.compat.scheduler.TaskHandle;
import com.arkflame.flameforge.testfakes.TaskHandleStub;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TargetBlockBridgeTest {

    private TargetBlockBridge bridge;
    private ControlledSchedulerBridge scheduler;
    private Player player;
    private World world;
    private ControlledWorld controlledWorld;

    @BeforeEach
    void setUp() {
        JavaPlugin fakePlugin = mock(JavaPlugin.class);
        scheduler = new ControlledSchedulerBridge();
        bridge = new TargetBlockBridge(fakePlugin, scheduler);
        player = mock(Player.class);
        controlledWorld = new ControlledWorld();
        world = controlledWorld.world;
    }

    @Test
    void captureRunsOnEntityOwnerBeforeAnyPlayerReadAndRetiredPlayerReturnsRetired() {
        preparePlayer(eye(0.5, 64.5, 0.5, 1, 0, 0));

        CompletableFuture<TargetBlockResult> future = bridge.findTargetBlock(player, 5);

        assertFalse(future.isDone());
        assertEquals(1, scheduler.entityTaskCount());
        verifyNoInteractions(player);

        scheduler.runEntityTask();
        verify(player).isOnline();
        assertEquals(1, scheduler.globalTaskCount());

        scheduler.retireEntityTask();
        assertEquals(TargetBlockResult.Status.PLAYER_RETIRED, future.getNow(null).status());
    }

    @Test
    void zeroOrNegativeDistanceReturnsNoTarget() {
        CompletableFuture<TargetBlockResult> zeroFuture = bridge.findTargetBlock(player, 0);
        assertEquals(TargetBlockResult.Status.NO_TARGET, zeroFuture.getNow(null).status());

        CompletableFuture<TargetBlockResult> negativeFuture = bridge.findTargetBlock(player, -1);
        assertEquals(TargetBlockResult.Status.NO_TARGET, negativeFuture.getNow(null).status());
    }

    @Test
    void ddaHandlesCardinalDiagonalThreeAxisAndZeroAxisDirections() {
        controlledWorld.put(1, 64, 0, Material.STONE);
        TargetBlockResult cardinalResult = runClassic(eye(0.5, 64.5, 0.5, 1, 0, 0), 5);
        assertFound(cardinalResult, 1, 64, 0, Material.STONE);

        controlledWorld.put(1, 64, 0, Material.AIR);
        controlledWorld.put(1, 64, 0, Material.STONE);
        controlledWorld.put(1, 64, 1, Material.BEACON);
        TargetBlockResult diagonalResult = runClassic(eye(0.5, 64.5, 0.5, 1, 0, 1), 5);
        assertFound(diagonalResult, 1, 64, 1, Material.BEACON);

        controlledWorld.put(0, 64, 0, Material.AIR);
        controlledWorld.put(0, 65, 0, Material.AIR);
        controlledWorld.put(1, 64, 0, Material.AIR);
        controlledWorld.put(0, 64, 1, Material.AIR);
        controlledWorld.put(1, 65, 0, Material.AIR);
        controlledWorld.put(1, 64, 1, Material.AIR);
        controlledWorld.put(0, 65, 1, Material.AIR);
        controlledWorld.put(1, 65, 1, Material.DIRT);
        TargetBlockResult threeAxisResult = runClassic(eye(0.5, 64.5, 0.5, 1, 1, 1), 5);
        assertFound(threeAxisResult, 1, 65, 1, Material.DIRT);

        controlledWorld.clear();
        controlledWorld.put(0, 64, 1, Material.STONE);
        TargetBlockResult zeroAxisResult = runClassic(eye(0.5, 64.5, 0.5, 0, 0, 1), 5);
        assertFound(zeroAxisResult, 0, 64, 1, Material.STONE);
    }

    @Test
    void ddaHandlesNegativeCoordinatesAndLocationDirectionUpDown() {
        controlledWorld.put(-2, 64, -1, Material.STONE);
        TargetBlockResult negCoordResult = runClassic(eye(-0.25, 64.5, -0.25, -1, 0, 0), 5);
        assertFound(negCoordResult, -2, 64, -1, Material.STONE);

        controlledWorld.clear();
        controlledWorld.put(0, 65, 0, Material.STONE);
        TargetBlockResult upResult = runClassic(eye(0.5, 64.5, 0.5, 0, 1, 0), 5);
        assertFound(upResult, 0, 65, 0, Material.STONE);

        controlledWorld.clear();
        controlledWorld.put(0, 63, 0, Material.STONE);
        TargetBlockResult downResult = runClassic(eye(0.5, 64.5, 0.5, 0, -1, 0), 5);
        assertFound(downResult, 0, 63, 0, Material.STONE);
    }

    @Test
    void maxDistanceIncludesExactBoundaryAndExcludesBeyond() {
        controlledWorld.put(1, 64, 0, Material.STONE);
        TargetBlockResult boundaryResult = runClassic(eye(0, 64.5, 0.5, 1, 0, 0), 1);
        assertFound(boundaryResult, 1, 64, 0, Material.STONE);

        controlledWorld.clear();
        controlledWorld.put(2, 64, 0, Material.STONE);
        TargetBlockResult beyondResult = runClassic(eye(0, 64.5, 0.5, 1, 0, 0), 1);
        assertEquals(TargetBlockResult.Status.NO_TARGET, beyondResult.status());
        assertEquals(java.util.Collections.singleton(new BlockKey(1, 64, 0)), controlledWorld.queried);
    }

    @Test
    void classicReturnsFirstNonAirMaterialIncludingStoneBeaconAndOrdinaryBlocks() {
        controlledWorld.put(1, 64, 0, Material.STONE);
        assertFound(runClassic(eye(0.5, 64.5, 0.5, 1, 0, 0), 5), 1, 64, 0, Material.STONE);

        controlledWorld.clear();
        controlledWorld.put(1, 64, 0, Material.BEACON);
        assertFound(runClassic(eye(0.5, 64.5, 0.5, 1, 0, 0), 5), 1, 64, 0, Material.BEACON);

        controlledWorld.clear();
        controlledWorld.put(1, 64, 0, Material.DIRT);
        assertFound(runClassic(eye(0.5, 64.5, 0.5, 1, 0, 0), 5), 1, 64, 0, Material.DIRT);
    }

    @Test
    void foliaAirContinuationSchedulesOneRegionReadPerCellAndCompletes() {
        scheduler.folia = true;
        controlledWorld.put(1, 64, 0, Material.AIR);
        controlledWorld.put(2, 64, 0, Material.STONE);
        preparePlayer(eye(0.5, 64.5, 0.5, 1, 0, 0));

        CompletableFuture<TargetBlockResult> future = bridge.findTargetBlock(player, 5);
        scheduler.runEntityTask();

        assertFalse(future.isDone());
        assertEquals(1, scheduler.regionTaskCount());
        assertEquals(new BlockKey(1, 64, 0), scheduler.regionLocations.get(0));

        scheduler.runRegionTask();
        assertFalse(future.isDone());
        assertEquals(1, scheduler.regionTaskCount());
        assertEquals(new BlockKey(2, 64, 0), scheduler.regionLocations.get(1));

        scheduler.runRegionTask();
        assertFound(future.getNow(null), 2, 64, 0, Material.STONE);
    }

    @Test
    void unloadedChunkReturnsUnavailableWithoutLoading() {
        controlledWorld.unloadChunk(0, 0);
        controlledWorld.put(1, 64, 0, Material.STONE);

        TargetBlockResult result = runClassic(eye(0.5, 64.5, 0.5, 1, 0, 0), 5);

        assertEquals(TargetBlockResult.Status.UNAVAILABLE, result.status());
        verify(world, never()).loadChunk(anyInt(), anyInt());
        verify(world, never()).loadChunk(anyInt(), anyInt(), anyBoolean());
    }

    @Test
    void resultStatusesDistinguishFoundNoTargetUnavailableAndRetired() {
        TargetBlockResult found = TargetBlockResult.found(new TargetBlockSnapshot(
            UUID.randomUUID().toString(), "world", 0, 64, 0, "STONE"));
        TargetBlockResult noTarget = TargetBlockResult.noTarget();
        TargetBlockResult unavailable = TargetBlockResult.unavailable();
        TargetBlockResult retired = TargetBlockResult.retired();

        assertEquals(TargetBlockResult.Status.FOUND, found.status());
        assertEquals(TargetBlockResult.Status.NO_TARGET, noTarget.status());
        assertEquals(TargetBlockResult.Status.UNAVAILABLE, unavailable.status());
        assertEquals(TargetBlockResult.Status.PLAYER_RETIRED, retired.status());
        assertTrue(found.isFound());
        assertFalse(noTarget.isFound());
        assertFalse(unavailable.isFound());
        assertFalse(retired.isFound());
    }

    @Test
    void nullPlayerIsRejected() {
        assertThrows(NullPointerException.class, () -> bridge.findTargetBlock(null, 5));
    }

    private TargetBlockResult runClassic(Location eye, int maxDistance) {
        preparePlayer(eye);
        CompletableFuture<TargetBlockResult> future = bridge.findTargetBlock(player, maxDistance);
        scheduler.runEntityTask();
        scheduler.runGlobalTask();
        return future.getNow(null);
    }

    private void preparePlayer(Location eye) {
        when(player.isOnline()).thenReturn(true);
        when(player.getEyeLocation()).thenReturn(eye);
        clearInvocations(player);
    }

    private Location eye(double x, double y, double z, double dx, double dy, double dz) {
        return new Location(world, x, y, z).setDirection(new Vector(dx, dy, dz));
    }

    private static void assertFound(TargetBlockResult result, int x, int y, int z, Material material) {
        assertEquals(TargetBlockResult.Status.FOUND, result.status());
        assertNotNull(result.snapshot());
        assertEquals(x, result.snapshot().getBlockX());
        assertEquals(y, result.snapshot().getBlockY());
        assertEquals(z, result.snapshot().getBlockZ());
        assertEquals(material.name(), result.snapshot().getMaterialName());
    }

    private static final class ControlledSchedulerBridge implements SchedulerBridge {
        private final Queue<Runnable> entityTasks = new ArrayDeque<>();
        private final Queue<Runnable> globalTasks = new ArrayDeque<>();
        private final Queue<Runnable> regionTasks = new ArrayDeque<>();
        private final List<BlockKey> regionLocations = new ArrayList<>();
        private Runnable retireCallback;
        private boolean folia;

        @Override
        public TaskHandle runGlobal(JavaPlugin plugin, Runnable task) {
            globalTasks.add(task);
            return TaskHandleStub.INSTANCE;
        }

        @Override
        public TaskHandle runGlobalLater(JavaPlugin plugin, Runnable task, long delay) {
            globalTasks.add(task);
            return TaskHandleStub.INSTANCE;
        }

        @Override
        public TaskHandle runEntity(org.bukkit.entity.Entity entity, Runnable runnable, Runnable retireCallback) {
            entityTasks.add(runnable);
            this.retireCallback = retireCallback;
            return TaskHandleStub.INSTANCE;
        }

        @Override
        public TaskHandle runEntityLater(org.bukkit.entity.Entity entity, Runnable runnable, Runnable retireCallback, long delay) {
            entityTasks.add(runnable);
            this.retireCallback = retireCallback;
            return TaskHandleStub.INSTANCE;
        }

        @Override
        public TaskHandle runRegion(Location location, Runnable task) {
            regionLocations.add(new BlockKey(location.getBlockX(), location.getBlockY(), location.getBlockZ()));
            regionTasks.add(task);
            return TaskHandleStub.INSTANCE;
        }

        @Override
        public TaskHandle runRegionLater(Location location, Runnable task, long delay) {
            return runRegion(location, task);
        }

        @Override
        public TaskHandle runAsync(JavaPlugin plugin, Runnable task) {
            task.run();
            return TaskHandleStub.INSTANCE;
        }

        @Override
        public void cancelAll(JavaPlugin plugin) {
        }

        @Override
        public boolean isFolia() {
            return folia;
        }

        private int entityTaskCount() {
            return entityTasks.size();
        }

        private int globalTaskCount() {
            return globalTasks.size();
        }

        private int regionTaskCount() {
            return regionTasks.size();
        }

        private void runEntityTask() {
            take(entityTasks).run();
        }

        private void runGlobalTask() {
            take(globalTasks).run();
        }

        private void runRegionTask() {
            take(regionTasks).run();
        }

        void retireEntityTask() {
            if (retireCallback == null) {
                throw new IllegalStateException("No entity retirement callback");
            }
            retireCallback.run();
        }

        private static Runnable take(Queue<Runnable> tasks) {
            Runnable task = tasks.poll();
            if (task == null) {
                throw new IllegalStateException("No queued task");
            }
            return task;
        }
    }

    private static final class ControlledWorld {
        private final World world = mock(World.class);
        private final Map<BlockKey, Material> blocks = new HashMap<>();
        private final Set<BlockKey> unloadedChunks = new HashSet<>();
        private final Set<BlockKey> queried = new HashSet<>();

        private ControlledWorld() {
            when(world.getUID()).thenReturn(UUID.randomUUID());
            when(world.getName()).thenReturn("controlled-world");
            when(world.isChunkLoaded(anyInt(), anyInt())).thenAnswer(invocation -> {
                int chunkX = (Integer) invocation.getArguments()[0];
                int chunkZ = (Integer) invocation.getArguments()[1];
                return !unloadedChunks.contains(new BlockKey(chunkX, 0, chunkZ));
            });
            when(world.getBlockAt(anyInt(), anyInt(), anyInt())).thenAnswer(invocation -> {
                int x = (Integer) invocation.getArguments()[0];
                int y = (Integer) invocation.getArguments()[1];
                int z = (Integer) invocation.getArguments()[2];
                BlockKey key = new BlockKey(x, y, z);
                queried.add(key);
                Block block = mock(Block.class);
                when(block.getType()).thenReturn(blocks.containsKey(key) ? blocks.get(key) : Material.AIR);
                return block;
            });
        }

        void put(int x, int y, int z, Material material) {
            blocks.put(new BlockKey(x, y, z), material);
        }

        void clear() {
            blocks.clear();
            queried.clear();
        }

        void unloadChunk(int chunkX, int chunkZ) {
            unloadedChunks.add(new BlockKey(chunkX, 0, chunkZ));
        }
    }

    private static final class BlockKey {
        private final int x;
        private final int y;
        private final int z;

        private BlockKey(int x, int y, int z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }

        @Override
        public boolean equals(Object other) {
            if (!(other instanceof BlockKey)) {
                return false;
            }
            BlockKey that = (BlockKey) other;
            return x == that.x && y == that.y && z == that.z;
        }

        @Override
        public int hashCode() {
            int result = x;
            result = 31 * result + y;
            return 31 * result + z;
        }
    }
}
