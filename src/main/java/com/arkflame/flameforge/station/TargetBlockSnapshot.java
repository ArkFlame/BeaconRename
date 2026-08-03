package com.arkflame.flameforge.station;

import java.util.Objects;
import java.util.UUID;

public final class TargetBlockSnapshot {
    private final String worldUuid;
    private final String worldName;
    private final int blockX, blockY, blockZ;
    private final String materialName;

    public TargetBlockSnapshot(String worldUuid, String worldName, int blockX, int blockY, int blockZ, String materialName) {
        this.worldUuid = Objects.requireNonNull(worldUuid);
        this.worldName = Objects.requireNonNull(worldName);
        this.blockX = blockX;
        this.blockY = blockY;
        this.blockZ = blockZ;
        this.materialName = materialName;
    }

    public String getWorldUuid() {
        return worldUuid;
    }

    public String getWorldName() {
        return worldName;
    }

    public int getBlockX() {
        return blockX;
    }

    public int getBlockY() {
        return blockY;
    }

    public int getBlockZ() {
        return blockZ;
    }

    public String getMaterialName() {
        return materialName;
    }
}
