package com.arkflame.flameforge.station;

import com.arkflame.flameforge.compat.scheduler.SchedulerBridge;
import com.arkflame.flameforge.compat.scheduler.TaskHandle;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class TargetBlockBridge {

    private static final double EPSILON = 1e-6;

    private final JavaPlugin plugin;
    private final SchedulerBridge scheduler;

    public TargetBlockBridge(JavaPlugin plugin, SchedulerBridge scheduler) {
        this.plugin = Objects.requireNonNull(plugin);
        this.scheduler = Objects.requireNonNull(scheduler);
    }

    public CompletableFuture<TargetBlockResult> findTargetBlock(Player player, int maxDistance) {
        Objects.requireNonNull(player);

        if (maxDistance <= 0) {
            return CompletableFuture.completedFuture(TargetBlockResult.noTarget());
        }

        final CompletableFuture<TargetBlockResult> future = new CompletableFuture<>();
        TaskHandle handle = scheduler.runEntity(player, () -> {
            if (!player.isOnline()) {
                future.complete(TargetBlockResult.retired());
                return;
            }

            Location eye = player.getEyeLocation();
            if (eye == null || eye.getWorld() == null) {
                future.complete(TargetBlockResult.unavailable());
                return;
            }

            World world = eye.getWorld();
            Vector direction = eye.getDirection();
            if (!isFiniteNonZero(direction)) {
                future.complete(TargetBlockResult.noTarget());
                return;
            }
            direction.normalize();
            if (!isFiniteNonZero(direction)) {
                future.complete(TargetBlockResult.noTarget());
                return;
            }

            TargetCapture capture = new TargetCapture(world, eye.getX(), eye.getY(), eye.getZ(),
                    direction.getX(), direction.getY(), direction.getZ());
            DdaCursor cursor = new DdaCursor(capture.originX, capture.originY, capture.originZ,
                    capture.directionX, capture.directionY, capture.directionZ, maxDistance);

            if (scheduler.isFolia()) {
                scheduleFoliaCell(future, capture, cursor);
            } else {
                TaskHandle globalHandle = scheduler.runGlobal(plugin, () ->
                        future.complete(scanClassic(capture, cursor)));
                if (globalHandle == null) {
                    future.complete(TargetBlockResult.noTarget());
                }
            }
        }, () -> {
            if (!future.isDone()) {
                future.complete(TargetBlockResult.retired());
            }
        });

        if (handle == null && !future.isDone()) {
            future.complete(scheduler.isFolia() ? TargetBlockResult.retired() : TargetBlockResult.noTarget());
        }
        return future;
    }

    private static boolean isFiniteNonZero(Vector direction) {
        if (direction == null) {
            return false;
        }
        double x = direction.getX();
        double y = direction.getY();
        double z = direction.getZ();
        double length = Math.hypot(Math.hypot(x, y), z);
        return Double.isFinite(x) && Double.isFinite(y) && Double.isFinite(z)
                && Double.isFinite(length) && length > 0.0;
    }

    private TargetBlockResult scanClassic(TargetCapture capture, DdaCursor cursor) {
        TargetCell cell;
        while ((cell = cursor.next()) != null) {
            if (!capture.world.isChunkLoaded(cell.x >> 4, cell.z >> 4)) {
                return TargetBlockResult.unavailable();
            }

            org.bukkit.block.Block block = capture.world.getBlockAt(cell.x, cell.y, cell.z);
            if (block.getType() != Material.AIR) {
                return TargetBlockResult.found(snapshot(capture, cell, block.getType().name()));
            }
        }
        return TargetBlockResult.noTarget();
    }

    private void scheduleFoliaCell(CompletableFuture<TargetBlockResult> future,
                                   TargetCapture capture, DdaCursor cursor) {
        if (future.isDone()) {
            return;
        }

        TargetCell cell = cursor.next();
        if (cell == null) {
            future.complete(TargetBlockResult.noTarget());
            return;
        }

        Location blockLocation = new Location(capture.world, cell.x, cell.y, cell.z);
        TaskHandle regionHandle = scheduler.runRegion(blockLocation, () -> {
            if (future.isDone()) {
                return;
            }
            if (!capture.world.isChunkLoaded(cell.x >> 4, cell.z >> 4)) {
                future.complete(TargetBlockResult.unavailable());
                return;
            }

            org.bukkit.block.Block block = capture.world.getBlockAt(cell.x, cell.y, cell.z);
            if (block.getType() != Material.AIR) {
                future.complete(TargetBlockResult.found(snapshot(capture, cell, block.getType().name())));
                return;
            }

            scheduleFoliaCell(future, capture, cursor);
        });

        if (regionHandle == null) {
            future.complete(TargetBlockResult.unavailable());
        }
    }

    private static TargetBlockSnapshot snapshot(TargetCapture capture, TargetCell cell, String materialName) {
        return new TargetBlockSnapshot(capture.worldUuid.toString(), capture.worldName,
                cell.x, cell.y, cell.z, materialName);
    }

    private static final class TargetCapture {
        private final World world;
        private final UUID worldUuid;
        private final String worldName;
        private final double originX;
        private final double originY;
        private final double originZ;
        private final double directionX;
        private final double directionY;
        private final double directionZ;

        private TargetCapture(World world, double originX, double originY, double originZ,
                              double directionX, double directionY, double directionZ) {
            this.world = world;
            this.worldUuid = world.getUID();
            this.worldName = world.getName();
            this.originX = originX;
            this.originY = originY;
            this.originZ = originZ;
            this.directionX = directionX;
            this.directionY = directionY;
            this.directionZ = directionZ;
        }
    }

    static final class DdaCursor {
        private final double maxDistance;
        private final double tDeltaX;
        private final double tDeltaY;
        private final double tDeltaZ;
        private final int stepX;
        private final int stepY;
        private final int stepZ;
        private final int eyeX;
        private final int eyeY;
        private final int eyeZ;
        private int cellX;
        private int cellY;
        private int cellZ;
        private double tMaxX;
        private double tMaxY;
        private double tMaxZ;
        private int lastX;
        private int lastY;
        private int lastZ;
        private boolean returnedCell;

        DdaCursor(double originX, double originY, double originZ,
                  double directionX, double directionY, double directionZ, int maxDistance) {
            this.maxDistance = maxDistance;
            this.cellX = (int) Math.floor(originX);
            this.cellY = (int) Math.floor(originY);
            this.cellZ = (int) Math.floor(originZ);
            this.eyeX = cellX;
            this.eyeY = cellY;
            this.eyeZ = cellZ;
            this.stepX = (int) Math.signum(directionX);
            this.stepY = (int) Math.signum(directionY);
            this.stepZ = (int) Math.signum(directionZ);
            this.tDeltaX = directionX == 0.0 ? Double.POSITIVE_INFINITY : Math.abs(1.0 / directionX);
            this.tDeltaY = directionY == 0.0 ? Double.POSITIVE_INFINITY : Math.abs(1.0 / directionY);
            this.tDeltaZ = directionZ == 0.0 ? Double.POSITIVE_INFINITY : Math.abs(1.0 / directionZ);
            this.tMaxX = firstBoundary(originX, cellX, directionX, tDeltaX);
            this.tMaxY = firstBoundary(originY, cellY, directionY, tDeltaY);
            this.tMaxZ = firstBoundary(originZ, cellZ, directionZ, tDeltaZ);
        }

        TargetCell next() {
            while (true) {
                double nextT = Math.min(tMaxX, Math.min(tMaxY, tMaxZ));
                if (nextT > maxDistance + EPSILON || Double.isNaN(nextT)) {
                    return null;
                }

                if (tMaxX <= nextT + EPSILON) {
                    tMaxX += tDeltaX;
                    cellX += stepX;
                }
                if (tMaxY <= nextT + EPSILON) {
                    tMaxY += tDeltaY;
                    cellY += stepY;
                }
                if (tMaxZ <= nextT + EPSILON) {
                    tMaxZ += tDeltaZ;
                    cellZ += stepZ;
                }

                if ((cellX == eyeX && cellY == eyeY && cellZ == eyeZ)
                        || (returnedCell && cellX == lastX && cellY == lastY && cellZ == lastZ)) {
                    continue;
                }

                returnedCell = true;
                lastX = cellX;
                lastY = cellY;
                lastZ = cellZ;
                return new TargetCell(cellX, cellY, cellZ, nextT);
            }
        }

        private static double firstBoundary(double origin, int cell, double direction, double tDelta) {
            if (direction == 0.0) {
                return Double.POSITIVE_INFINITY;
            }
            return direction < 0.0 ? (origin - cell) * tDelta : (cell + 1.0 - origin) * tDelta;
        }
    }

    static final class TargetCell {
        final int x;
        final int y;
        final int z;
        final double distance;

        TargetCell(int x, int y, int z, double distance) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.distance = distance;
        }
    }
}
