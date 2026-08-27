package com.arkflame.flameforge.compat;

import org.bukkit.Location;
import org.bukkit.entity.Entity;

public final class RuntimePlatform {
    private final boolean folia;
    private final boolean teleportAsyncAvailable;

    private RuntimePlatform(boolean folia, boolean teleportAsyncAvailable) {
        this.folia = folia;
        this.teleportAsyncAvailable = teleportAsyncAvailable;
    }

    public static RuntimePlatform detect() {
        boolean folia = detectFolia();
        boolean teleportAsync = detectTeleportAsync();
        return new RuntimePlatform(folia, teleportAsync);
    }

    private static boolean detectFolia() {
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    private static boolean detectTeleportAsync() {
        try {
            Entity.class.getMethod("teleportAsync", Location.class);
            return true;
        } catch (NoSuchMethodException e) {
            return false;
        }
    }

    public boolean isFolia() { return folia; }
    public boolean isTeleportAsyncAvailable() { return teleportAsyncAvailable; }
}
