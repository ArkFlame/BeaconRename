package com.arkflame.flameforge.compat.effect;

import org.bukkit.Effect;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
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

    @Test
    void legacyLowercaseLookupSucceeds() {
        ParticleBridge bridge = ParticleBridge.getInstance();
        Player player = mock(Player.class);
        Location location = mock(Location.class);
        when(player.getLocation()).thenReturn(location);

        bridge.sendToPlayer(player, "explosion_fire");

        verify(player).playEffect(any(Location.class), any(Effect.class), isNull());
    }

    @Test
    void unknownFamilyDoesNotEmitFootstep() {
        ParticleBridge bridge = ParticleBridge.getInstance();
        Player player = mock(Player.class);
        Location location = mock(Location.class);
        doThrow(new IllegalStateException("playEffect unavailable"))
            .when(player).playEffect(any(Location.class), any(Effect.class), any());

        assertDoesNotThrow(() -> bridge.sendToPlayer(player, "bubble", location,
            0f, 0f, 0f, 0f, 1));

        verify(player, never()).playEffect(any(Location.class), eq(Effect.FOOTSTEP), any());
    }

    @Test
    void sendColoredDustFallsBackSafelyWhenDustOptionsUnavailable() {
        ParticleBridge bridge = ParticleBridge.getInstance();
        Player player = mock(Player.class);
        Location location = mock(Location.class);

        assertDoesNotThrow(() -> bridge.sendColoredDust(player, location, 255, 80, 20, 1f, 2));

        verify(player).playEffect(any(Location.class), eq(Effect.EXPLOSION_HUGE), isNull());
    }

    @Test
    void sendFirstAvailableReturnsFalseWhenEveryCandidateUnavailable() {
        ParticleBridge bridge = ParticleBridge.getInstance();
        Player player = mock(Player.class);
        Location location = mock(Location.class);

        assertFalse(bridge.sendFirstAvailable(player, location,
            Arrays.asList("no-such-particle-one", "no-such-particle-two"),
            0f, 0f, 0f, 0f, 1));
        assertFalse(bridge.sendFirstAvailable(player, location, null, 0f, 0f, 0f, 0f, 1));
        assertFalse(bridge.sendFirstAvailable(player, location,
            Collections.emptyList(), 0f, 0f, 0f, 0f, 1));
    }

    @Test
    void sendFirstAvailableSpawnsFirstUsableSemanticCandidateInOrder() {
        ParticleBridge bridge = ParticleBridge.getInstance();
        Player player = mock(Player.class);
        Location location = mock(Location.class);

        assertTrue(bridge.sendFirstAvailable(player, location,
            Arrays.asList("explode", "smoke"), 0f, 0f, 0f, 0f, 1));

        verify(player).playEffect(any(Location.class), eq(Effect.CLICK2), isNull());
        verify(player, never()).playEffect(any(Location.class), eq(Effect.ZOMBIE_CHEW_WOODEN_DOOR), any());
    }
}
