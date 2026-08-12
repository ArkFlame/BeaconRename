package com.arkflame.flameforge.compat;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RuntimePlatformTest {

    @Test
    void capabilityDetectionProducesStableSnapshot() {
        RuntimePlatform first = RuntimePlatform.detect();
        RuntimePlatform second = RuntimePlatform.detect();

        assertEquals(first.isFolia(), second.isFolia());
        assertEquals(first.isTeleportAsyncAvailable(), second.isTeleportAsyncAvailable());
        assertEquals(first.isModernParticleApiAvailable(), second.isModernParticleApiAvailable());
        assertEquals(first.isLegacySpigotEffectAvailable(), second.isLegacySpigotEffectAvailable());
    }
}
