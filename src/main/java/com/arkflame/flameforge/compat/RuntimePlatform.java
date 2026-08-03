package com.arkflame.flameforge.compat;

import org.bukkit.Location;
import org.bukkit.entity.Entity;

public final class RuntimePlatform {
    private final boolean folia;
    private final boolean teleportAsyncAvailable;
    private final boolean modernParticleApiAvailable;
    private final boolean legacySpigotEffectAvailable;

    private RuntimePlatform(boolean folia, boolean teleportAsyncAvailable,
                            boolean modernParticleApiAvailable, boolean legacySpigotEffectAvailable) {
        this.folia = folia;
        this.teleportAsyncAvailable = teleportAsyncAvailable;
        this.modernParticleApiAvailable = modernParticleApiAvailable;
        this.legacySpigotEffectAvailable = legacySpigotEffectAvailable;
    }

    public static RuntimePlatform detect() {
        boolean folia = detectFolia();
        boolean teleportAsync = detectTeleportAsync();
        boolean modernParticle = detectModernParticleApi();
        boolean legacyEffect = detectLegacySpigotEffect();
        return new RuntimePlatform(folia, teleportAsync, modernParticle, legacyEffect);
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

    private static boolean detectModernParticleApi() {
        try {
            Class.forName("org.bukkit.Particle");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    private static boolean detectLegacySpigotEffect() {
        try {
            org.bukkit.Effect.class.getMethod("playEffect", Location.class);
            return true;
        } catch (NoSuchMethodException e) {
            return false;
        }
    }

    public boolean isFolia() { return folia; }
    public boolean isTeleportAsyncAvailable() { return teleportAsyncAvailable; }
    public boolean isModernParticleApiAvailable() { return modernParticleApiAvailable; }
    public boolean isLegacySpigotEffectAvailable() { return legacySpigotEffectAvailable; }
}
