package com.arkflame.flameforge.compat.effect;

import org.bukkit.Sound;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SoundResolverTest {

    @Test
    void soundResolutionUsesKnownCandidateAndSafeFallback() {
        SoundResolver resolver = SoundResolver.getInstance();
        resolver.clearCache();

        Sound known = Sound.values()[0];
        assertEquals(known, resolver.resolveOrThrow(known.name()));
        assertEquals(known, resolver.resolveOrDefault("unknown-sound", known));
        assertEquals(known, resolver.resolveOrDefault(null, known));
    }
}
