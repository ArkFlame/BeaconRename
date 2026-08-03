package com.arkflame.flameforge.compat.scheduler;

import com.arkflame.flameforge.compat.RuntimePlatform;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Method;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class TeleportBridge {
    private final JavaPlugin plugin;
    private final SchedulerBridge scheduler;
    private final RuntimePlatform platform;
    private final Method teleportAsyncMethod;

    public TeleportBridge(JavaPlugin plugin, SchedulerBridge scheduler, RuntimePlatform platform) {
        this.plugin = plugin;
        this.scheduler = scheduler;
        this.platform = platform;
        this.teleportAsyncMethod = detectTeleportAsyncMethod();
    }

    private Method detectTeleportAsyncMethod() {
        try {
            return Player.class.getMethod("teleportAsync", Location.class);
        } catch (NoSuchMethodException e) {
            return null;
        }
    }

    public CompletableFuture<TeleportOutcome> teleportAsync(Player player, Location destination) {
        if (player == null || !player.isOnline()) {
            return CompletableFuture.completedFuture(TeleportOutcome.playerOffline());
        }

        if (teleportAsyncMethod != null) {
            try {
                CompletableFuture<Boolean> future = (CompletableFuture<Boolean>) teleportAsyncMethod.invoke(player, destination);
                return future.thenApply(result -> {
                    if (!result) {
                        return TeleportOutcome.teleportRejected();
                    }
                    if (!player.isOnline()) {
                        return TeleportOutcome.playerOffline();
                    }
                    return TeleportOutcome.teleported();
                }).exceptionally(ex -> {
                    String ref = UUID.randomUUID().toString();
                    return TeleportOutcome.teleportException(ex.getMessage(), ref);
                });
            } catch (Exception e) {
                String ref = UUID.randomUUID().toString();
                return CompletableFuture.completedFuture(TeleportOutcome.teleportException(e.getMessage(), ref));
            }
        }

        CompletableFuture<TeleportOutcome> future = new CompletableFuture<>();
        scheduler.runGlobal(plugin, () -> {
            if (!player.isOnline()) {
                future.complete(TeleportOutcome.playerOffline());
                return;
            }
            try {
                boolean result = player.teleport(destination);
                if (result) {
                    future.complete(TeleportOutcome.teleported());
                } else {
                    future.complete(TeleportOutcome.teleportRejected());
                }
            } catch (Exception e) {
                String ref = UUID.randomUUID().toString();
                future.complete(TeleportOutcome.teleportException(e.getMessage(), ref));
            }
        });
        return future;
    }

    public enum TeleportStatus {
        TELEPORTED,
        PLAYER_OFFLINE,
        WORLD_NOT_FOUND,
        WORLD_NOT_LOADED,
        TELEPORT_REJECTED,
        TELEPORT_EXCEPTION,
        PLAYER_RETIRED,
        SCHEDULER_REJECTED
    }

    public static final class TeleportOutcome {
        private final TeleportStatus status;
        private final String reason;
        private final String reference;

        private TeleportOutcome(TeleportStatus status, String reason, String reference) {
            this.status = status;
            this.reason = reason;
            this.reference = reference;
        }

        public TeleportStatus getStatus() {
            return status;
        }

        public String getReason() {
            return reason;
        }

        public String getReference() {
            return reference;
        }

        public static TeleportOutcome teleported() {
            return new TeleportOutcome(TeleportStatus.TELEPORTED, null, null);
        }

        public static TeleportOutcome playerOffline() {
            return new TeleportOutcome(TeleportStatus.PLAYER_OFFLINE, null, null);
        }

        public static TeleportOutcome worldNotFound() {
            return new TeleportOutcome(TeleportStatus.WORLD_NOT_FOUND, null, null);
        }

        public static TeleportOutcome worldNotLoaded() {
            return new TeleportOutcome(TeleportStatus.WORLD_NOT_LOADED, null, null);
        }

        public static TeleportOutcome teleportRejected() {
            return new TeleportOutcome(TeleportStatus.TELEPORT_REJECTED, null, null);
        }

        public static TeleportOutcome teleportException(String reason, String reference) {
            return new TeleportOutcome(TeleportStatus.TELEPORT_EXCEPTION, reason, reference);
        }

        public static TeleportOutcome playerRetired() {
            return new TeleportOutcome(TeleportStatus.PLAYER_RETIRED, null, null);
        }

        public static TeleportOutcome schedulerRejected() {
            return new TeleportOutcome(TeleportStatus.SCHEDULER_REJECTED, null, null);
        }
    }
}
