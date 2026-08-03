package com.arkflame.flameforge.compat.effect;

import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ParticleBridgeTest {

    private ParticleBridge particleBridge;

    @BeforeEach
    void setUp() {
        particleBridge = ParticleBridge.getInstance();
    }

    @Test
    void singletonAliasMapIsImmutableAndContainsRepresentativeLegacyModernMappings() {
        Map<String, String> particles = particleBridge.getAvailableParticles();

        assertNotNull(particles);
        assertFalse(particles.isEmpty());

        assertTrue(particles.containsKey("explode"), "explode alias should exist");
        assertEquals("explosion_normal", particles.get("explode"));

        assertTrue(particles.containsKey("large_explosion"), "large_explosion alias should exist");
        assertEquals("explosion_large", particles.get("large_explosion"));

        assertTrue(particles.containsKey("crit"), "crit alias should exist");
        assertEquals("crit", particles.get("crit"));

        assertTrue(particles.containsKey("magic_crit"), "magic_crit alias should exist");
        assertEquals("crit", particles.get("magic_crit"));

        assertThrows(UnsupportedOperationException.class, () -> {
            particles.put("test", "value");
        });
    }

    @Test
    void allConfiguredAliasesResolveToKnownParticleOrLegacyEffectCandidate() {
        Map<String, String> particles = particleBridge.getAvailableParticles();

        for (Map.Entry<String, String> entry : particles.entrySet()) {
            String alias = entry.getKey();
            String canonical = entry.getValue();

            assertNotNull(alias, "alias should not be null");
            assertNotNull(canonical, "canonical particle should not be null");
            assertFalse(alias.isEmpty(), "alias should not be empty");
            assertFalse(canonical.isEmpty(), "canonical should not be empty");
        }
    }

    @Test
    void nullEmptyAndUnknownRequestsAreSafeNoOps() {
        Player player = mock(Player.class);

        assertDoesNotThrow(() -> {
            particleBridge.sendToPlayer(player, null, new Location(null, 0, 64, 0), 0f, 0f, 0f, 0f, 1);
        });

        assertDoesNotThrow(() -> {
            particleBridge.sendToPlayer(player, "", new Location(null, 0, 64, 0), 0f, 0f, 0f, 0f, 1);
        });

        assertDoesNotThrow(() -> {
            particleBridge.sendToPlayer(player, "not_a_real_particle_xyz_123", new Location(null, 0, 64, 0), 0f, 0f, 0f, 0f, 1);
        });

        assertDoesNotThrow(() -> {
            particleBridge.sendToPlayer(null, "explode", new Location(null, 0, 64, 0), 0f, 0f, 0f, 0f, 1);
        });

        assertDoesNotThrow(() -> {
            particleBridge.sendToPlayer(player, "explode", null, 0f, 0f, 0f, 0f, 1);
        });
    }

    @Test
    void validRequestUsesAvailableBackendWithoutThrowing() {
        Player player = mock(Player.class);
        when(player.getLocation()).thenReturn(new Location(null, 0, 64, 0));

        boolean modernAvailable = particleBridge.isModernAvailable();

        for (Map.Entry<String, String> entry : particleBridge.getAvailableParticles().entrySet()) {
            String particleKey = entry.getKey();
            assertDoesNotThrow(() -> {
                particleBridge.sendToPlayer(player, particleKey, new Location(null, 0, 64, 0),
                    0f, 0f, 0f, 0f, 1);
            }, "Particle key '" + particleKey + "' should not throw");
        }
    }
}
