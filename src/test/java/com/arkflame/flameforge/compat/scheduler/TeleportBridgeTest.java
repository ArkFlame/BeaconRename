package com.arkflame.flameforge.compat.scheduler;

import com.arkflame.flameforge.compat.RuntimePlatform;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TeleportBridgeTest {

    @Test
    void teleportOutcomePropagatesSuccessFailureAndUnavailablePlayer() throws Exception {
        JavaPlugin plugin = mock(JavaPlugin.class);
        SchedulerBridge scheduler = new ImmediateSchedulerBridge();
        RuntimePlatform platform = mock(RuntimePlatform.class);
        when(platform.isTeleportAsyncAvailable()).thenReturn(false);
        TeleportBridge bridge = new TeleportBridge(plugin, scheduler, platform);
        Location destination = mock(Location.class);

        Player successfulPlayer = mock(Player.class);
        when(successfulPlayer.isOnline()).thenReturn(true);
        when(successfulPlayer.teleport(any(Location.class))).thenReturn(true);
        assertEquals(TeleportBridge.TeleportStatus.TELEPORTED,
            bridge.teleportAsync(successfulPlayer, destination).get().getStatus());

        Player rejectedPlayer = mock(Player.class);
        when(rejectedPlayer.isOnline()).thenReturn(true);
        when(rejectedPlayer.teleport(any(Location.class))).thenReturn(false);
        assertEquals(TeleportBridge.TeleportStatus.TELEPORT_REJECTED,
            bridge.teleportAsync(rejectedPlayer, destination).get().getStatus());

        Player offlinePlayer = mock(Player.class);
        when(offlinePlayer.isOnline()).thenReturn(false);
        CompletableFuture<TeleportBridge.TeleportOutcome> offline =
            bridge.teleportAsync(offlinePlayer, destination);
        assertEquals(TeleportBridge.TeleportStatus.PLAYER_OFFLINE, offline.get().getStatus());
        assertEquals(TeleportBridge.TeleportStatus.PLAYER_OFFLINE,
            bridge.teleportAsync(null, destination).get().getStatus());
    }

    private static final class ImmediateSchedulerBridge implements SchedulerBridge {
        @Override
        public TaskHandle runGlobal(JavaPlugin plugin, Runnable task) {
            task.run();
            return handle();
        }

        @Override
        public TaskHandle runGlobalLater(JavaPlugin plugin, Runnable task, long delay) {
            return runGlobal(plugin, task);
        }

        @Override
        public TaskHandle runEntity(org.bukkit.entity.Entity entity, Runnable task, Runnable retireCallback) {
            task.run();
            return handle();
        }

        @Override
        public TaskHandle runEntityLater(org.bukkit.entity.Entity entity, Runnable task,
                                         Runnable retireCallback, long delay) {
            return runEntity(entity, task, retireCallback);
        }

        @Override
        public TaskHandle runRegion(Location location, Runnable task) {
            task.run();
            return handle();
        }

        @Override
        public TaskHandle runRegionLater(Location location, Runnable task, long delay) {
            return runRegion(location, task);
        }

        @Override
        public TaskHandle runAsync(JavaPlugin plugin, Runnable task) {
            task.run();
            return handle();
        }

        @Override
        public void cancelAll(JavaPlugin plugin) {
        }

        @Override
        public boolean isFolia() {
            return false;
        }

        private TaskHandle handle() {
            return new TaskHandle() {
                private boolean cancelled;

                @Override
                public void cancel() {
                    cancelled = true;
                }

                @Override
                public boolean isCancelled() {
                    return cancelled;
                }
            };
        }
    }
}
