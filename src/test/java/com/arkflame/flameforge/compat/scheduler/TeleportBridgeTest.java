package com.arkflame.flameforge.compat.scheduler;

import com.arkflame.flameforge.compat.RuntimePlatform;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TeleportBridgeTest {

    private TeleportBridge bridge;
    private FakeTestSchedulerBridge scheduler;
    private JavaPlugin fakePlugin;
    private RuntimePlatform mockPlatform;

    @BeforeEach
    void setUp() throws Exception {
        fakePlugin = mock(JavaPlugin.class);
        scheduler = new FakeTestSchedulerBridge();
        mockPlatform = mock(RuntimePlatform.class);
        when(mockPlatform.isTeleportAsyncAvailable()).thenReturn(false);
        bridge = new TeleportBridge(fakePlugin, scheduler, mockPlatform);
    }

    @Test
    void nullOfflineOrRetiredPlayerCompletesFalse() throws Exception {
        CompletableFuture<TeleportBridge.TeleportOutcome> nullFuture = bridge.teleportAsync(null, new Location(null, 0, 64, 0));
        assertEquals(TeleportBridge.TeleportStatus.PLAYER_OFFLINE, nullFuture.get().getStatus(), "null player should complete with PLAYER_OFFLINE");

        Player offlinePlayer = mock(Player.class);
        when(offlinePlayer.isOnline()).thenReturn(false);
        CompletableFuture<TeleportBridge.TeleportOutcome> offlineFuture = bridge.teleportAsync(offlinePlayer, new Location(null, 0, 64, 0));
        assertEquals(TeleportBridge.TeleportStatus.PLAYER_OFFLINE, offlineFuture.get().getStatus(), "offline player should complete with PLAYER_OFFLINE");
    }

    @Test
    void successfulSameAndCrossWorldTeleportPropagatesTrue() throws Exception {
        World world = mock(World.class);
        Location sameWorldDest = new Location(world, 100.5, 64.0, 200.5);

        Player sameWorldPlayer = mock(Player.class);
        when(sameWorldPlayer.isOnline()).thenReturn(true);
        when(sameWorldPlayer.teleport(any(Location.class))).thenReturn(true);
        when(sameWorldPlayer.getLocation()).thenReturn(new Location(world, 0, 64, 0));
        CompletableFuture<TeleportBridge.TeleportOutcome> sameWorldFuture = bridge.teleportAsync(sameWorldPlayer, sameWorldDest);
        assertEquals(TeleportBridge.TeleportStatus.TELEPORTED, sameWorldFuture.get().getStatus(), "same world teleport success should propagate TELEPORTED");

        UUID crossWorldUid = UUID.randomUUID();
        World crossWorld = mock(World.class);
        when(crossWorld.getUID()).thenReturn(crossWorldUid);
        Location crossWorldDest = new Location(crossWorld, 100.5, 64.0, 200.5);

        Player crossWorldPlayer = mock(Player.class);
        when(crossWorldPlayer.isOnline()).thenReturn(true);
        when(crossWorldPlayer.teleport(any(Location.class))).thenReturn(true);
        when(crossWorldPlayer.getLocation()).thenReturn(new Location(mock(World.class), 0, 64, 0));
        CompletableFuture<TeleportBridge.TeleportOutcome> crossWorldFuture = bridge.teleportAsync(crossWorldPlayer, crossWorldDest);
        assertEquals(TeleportBridge.TeleportStatus.TELEPORTED, crossWorldFuture.get().getStatus(), "cross world teleport success should propagate TELEPORTED");
    }

    @Test
    void falseTeleportResultPropagatesFalse() throws Exception {
        Player player = mock(Player.class);
        when(player.isOnline()).thenReturn(true);
        when(player.teleport(any(Location.class))).thenReturn(false);
        World world = mock(World.class);
        Location dest = new Location(world, 0, 64, 0);

        CompletableFuture<TeleportBridge.TeleportOutcome> future = bridge.teleportAsync(player, dest);
        assertEquals(TeleportBridge.TeleportStatus.TELEPORT_REJECTED, future.get().getStatus(), "teleport returning false should propagate TELEPORT_REJECTED");
    }

    @Test
    void teleportExceptionUsesCurrentFailureContract() throws Exception {
        Player player = mock(Player.class);
        when(player.isOnline()).thenReturn(true);
        when(player.teleport(any(Location.class))).thenThrow(new RuntimeException("Teleport failed"));
        World world = mock(World.class);
        Location dest = new Location(world, 0, 64, 0);

        CompletableFuture<TeleportBridge.TeleportOutcome> future = bridge.teleportAsync(player, dest);
        assertEquals(TeleportBridge.TeleportStatus.TELEPORT_EXCEPTION, future.get().getStatus(), "teleport exception should result in TELEPORT_EXCEPTION");
    }

    @Test
    void returnedFutureCompletesAfterTeleport() throws Exception {
        Player player = mock(Player.class);
        when(player.isOnline()).thenReturn(true);
        when(player.teleport(any(Location.class))).thenReturn(true);
        when(player.getLocation()).thenReturn(new Location(mock(World.class), 0, 64, 0));
        World world = mock(World.class);
        Location dest = new Location(world, 100, 64, 100);

        CompletableFuture<TeleportBridge.TeleportOutcome> future = bridge.teleportAsync(player, dest);

        assertTrue(future.isDone(), "Future should be done after teleport completes");
        assertEquals(TeleportBridge.TeleportStatus.TELEPORTED, future.get(5, TimeUnit.SECONDS).getStatus(), "Future should complete with TELEPORTED after teleport");
    }

    private static class FakeTestSchedulerBridge implements SchedulerBridge {
        @Override
        public TaskHandle runGlobal(JavaPlugin plugin, Runnable task) {
            task.run();
            return new TaskHandle() {
                private volatile boolean cancelled = false;
                @Override public void cancel() { cancelled = true; }
                @Override public boolean isCancelled() { return cancelled; }
            };
        }

        @Override
        public TaskHandle runGlobalLater(JavaPlugin plugin, Runnable task, long delay) {
            task.run();
            return new TaskHandle() {
                private volatile boolean cancelled = false;
                @Override public void cancel() { cancelled = true; }
                @Override public boolean isCancelled() { return cancelled; }
            };
        }

        @Override
        public TaskHandle runEntity(Entity entity, Runnable runnable, Runnable retireCallback) {
            runnable.run();
            return new TaskHandle() {
                private volatile boolean cancelled = false;
                @Override public void cancel() { cancelled = true; }
                @Override public boolean isCancelled() { return cancelled; }
            };
        }

        @Override
        public TaskHandle runEntityLater(Entity entity, Runnable runnable, Runnable retireCallback, long delay) {
            runnable.run();
            return new TaskHandle() {
                private volatile boolean cancelled = false;
                @Override public void cancel() { cancelled = true; }
                @Override public boolean isCancelled() { return cancelled; }
            };
        }

        @Override
        public TaskHandle runRegion(Location location, Runnable task) {
            task.run();
            return new TaskHandle() {
                private volatile boolean cancelled = false;
                @Override public void cancel() { cancelled = true; }
                @Override public boolean isCancelled() { return cancelled; }
            };
        }

        @Override
        public TaskHandle runRegionLater(Location location, Runnable task, long delay) {
            task.run();
            return new TaskHandle() {
                private volatile boolean cancelled = false;
                @Override public void cancel() { cancelled = true; }
                @Override public boolean isCancelled() { return cancelled; }
            };
        }

        @Override
        public TaskHandle runAsync(JavaPlugin plugin, Runnable task) {
            task.run();
            return new TaskHandle() {
                private volatile boolean cancelled = false;
                @Override public void cancel() { cancelled = true; }
                @Override public boolean isCancelled() { return cancelled; }
            };
        }

        @Override
        public void cancelAll(JavaPlugin plugin) {
        }

        @Override
        public boolean isFolia() {
            return false;
        }
    }
}
