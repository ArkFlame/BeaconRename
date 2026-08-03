package com.arkflame.flameforge.compat;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RuntimePlatformTest {

    @Test
    void detectFlagsMatchActualClassAndMethodCapabilities() {
        RuntimePlatform platform = RuntimePlatform.detect();

        boolean expectedFolia = checkForRegionizedServerClass();
        assertEquals(expectedFolia, platform.isFolia(),
            "folia flag should match RegionizedServer class presence");

        boolean expectedTeleportAsync = checkForTeleportAsyncMethod();
        assertEquals(expectedTeleportAsync, platform.isTeleportAsyncAvailable(),
            "teleportAsync flag should match Entity.teleportAsync method presence");

        boolean expectedModernParticle = checkForParticleClass();
        assertEquals(expectedModernParticle, platform.isModernParticleApiAvailable(),
            "modernParticle flag should match Particle class presence");

        boolean expectedLegacyEffect = checkForPlayEffectMethod();
        assertEquals(expectedLegacyEffect, platform.isLegacySpigotEffectAvailable(),
            "legacyEffect flag should match Effect.playEffect method presence");
    }

    @Test
    void detectReturnsStableImmutableCapabilitySnapshot() {
        RuntimePlatform platform1 = RuntimePlatform.detect();
        RuntimePlatform platform2 = RuntimePlatform.detect();

        assertEquals(platform1.isFolia(), platform2.isFolia());
        assertEquals(platform1.isTeleportAsyncAvailable(), platform2.isTeleportAsyncAvailable());
        assertEquals(platform1.isModernParticleApiAvailable(), platform2.isModernParticleApiAvailable());
        assertEquals(platform1.isLegacySpigotEffectAvailable(), platform2.isLegacySpigotEffectAvailable());
    }

    private boolean checkForRegionizedServerClass() {
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    private boolean checkForTeleportAsyncMethod() {
        try {
            org.bukkit.entity.Entity.class.getMethod("teleportAsync", org.bukkit.Location.class);
            return true;
        } catch (NoSuchMethodException e) {
            return false;
        }
    }

    private boolean checkForParticleClass() {
        try {
            Class.forName("org.bukkit.Particle");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    private boolean checkForPlayEffectMethod() {
        try {
            org.bukkit.Effect.class.getMethod("playEffect", org.bukkit.Location.class);
            return true;
        } catch (NoSuchMethodException e) {
            return false;
        }
    }
}
