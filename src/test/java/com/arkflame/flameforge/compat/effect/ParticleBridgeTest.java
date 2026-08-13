package com.arkflame.flameforge.compat.effect;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ParticleBridgeTest {

    @Test
    void particleResolutionAndSendAreSafeAcrossKnownAndUnknownInputs() {
        ParticleBridge bridge = ParticleBridge.getInstance();
        Map<String, String> aliases = bridge.getAvailableParticles();
        assertFalse(aliases.isEmpty());

        Player player = mock(Player.class);
        Location location = mock(Location.class);
        when(player.getLocation()).thenReturn(location);
        String known = aliases.keySet().iterator().next();

        assertDoesNotThrow(() -> bridge.sendToPlayer(player, known, location,
            0f, 0f, 0f, 0f, 1));
        assertDoesNotThrow(() -> bridge.sendToPlayer(player, "unknown-particle", location,
            0f, 0f, 0f, 0f, 1));
        assertDoesNotThrow(() -> bridge.sendToPlayer(player, null, location,
            0f, 0f, 0f, 0f, 1));
        assertDoesNotThrow(() -> bridge.sendToPlayer(null, known, location,
            0f, 0f, 0f, 0f, 1));
        assertDoesNotThrow(() -> bridge.sendToPlayer(player, known, null,
            0f, 0f, 0f, 0f, 1));
        assertDoesNotThrow(() -> bridge.sendColoredDust(player, location, 255, 80, 20, 1f, 2));
    }
}
