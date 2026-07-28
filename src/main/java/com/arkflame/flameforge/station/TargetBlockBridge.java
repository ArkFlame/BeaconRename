package com.arkflame.flameforge.station;

import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.lang.reflect.Method;
import java.util.Optional;

public final class TargetBlockBridge {

    private static final TargetBlockBridge INSTANCE = new TargetBlockBridge();

    private final Method getTargetBlockMethod;
    private final boolean modernMethodAvailable;

    private TargetBlockBridge() {
        Method modern = null;
        boolean modernAvailable = false;

        try {
            modern = Player.class.getMethod("getTargetBlock", int.class);
            modernAvailable = true;
        } catch (NoSuchMethodException ignored) {
        }

        this.getTargetBlockMethod = modern;
        this.modernMethodAvailable = modernAvailable;
    }

    public static TargetBlockBridge getInstance() {
        return INSTANCE;
    }

    public Optional<Block> findTargetBlock(Player player, int maxDistance) {
        if (player == null) {
            return Optional.empty();
        }
        if (maxDistance <= 0) {
            return Optional.empty();
        }

        if (modernMethodAvailable && getTargetBlockMethod != null) {
            return findTargetBlockModern(player, maxDistance);
        }

        return findTargetBlockLegacy(player, maxDistance);
    }

    private Optional<Block> findTargetBlockModern(Player player, int maxDistance) {
        try {
            Object result = getTargetBlockMethod.invoke(player, maxDistance);
            if (result instanceof Block) {
                return Optional.of((Block) result);
            }
        } catch (Exception ignored) {
        }
        return Optional.empty();
    }

    private Optional<Block> findTargetBlockLegacy(Player player, int maxDistance) {
        Location eyeLocation = player.getEyeLocation();
        Vector direction = eyeLocation.getDirection();

        for (int i = 0; i <= maxDistance; i++) {
            double dx = direction.getX() * i;
            double dy = direction.getY() * i;
            double dz = direction.getZ() * i;

            int blockX = (int) Math.floor(eyeLocation.getX() + dx);
            int blockY = (int) Math.floor(eyeLocation.getY() + dy);
            int blockZ = (int) Math.floor(eyeLocation.getZ() + dz);

            if (eyeLocation.getWorld().isChunkLoaded(blockX >> 4, blockZ >> 4)) {
                Block block = eyeLocation.getWorld().getBlockAt(blockX, blockY, blockZ);
                if (block.getType().isSolid()) {
                    return Optional.of(block);
                }
            }
        }

        return Optional.empty();
    }

    public Optional<Block> findTargetBeacon(Player player, int maxDistance) {
        return findTargetBlock(player, maxDistance)
                .filter(block -> block.getType() == org.bukkit.Material.BEACON);
    }
}
