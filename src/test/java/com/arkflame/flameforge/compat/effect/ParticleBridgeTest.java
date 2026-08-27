package com.arkflame.flameforge.compat.effect;

import com.arkflame.flameforge.testfakes.FakeSchedulerBridge;
import org.bukkit.Effect;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ParticleBridgeTest {

    private static Location validLocation() {
        World world = mock(World.class);
        when(world.getName()).thenReturn("world");
        Location location = mock(Location.class);
        when(location.getWorld()).thenReturn(world);
        return location;
    }

    @Test
    void particleResolutionAndSendAreSafeAcrossKnownAndUnknownInputs() {
        ParticleBridge bridge = ParticleBridge.create(new FakeSchedulerBridge());
        Map<String, String> aliases = bridge.getAvailableParticles();
        assertFalse(aliases.isEmpty());

        Player player = mock(Player.class);
        Location location = validLocation();
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
    void legacySemanticLookupAvoidsOrdinalDrift() {
        ParticleBridge bridge = ParticleBridge.create(new FakeSchedulerBridge());
        Player player = mock(Player.class);
        Location location = validLocation();
        when(player.getLocation()).thenReturn(location);

        bridge.sendToPlayer(player, "explode");
        bridge.sendToPlayer(player, "dust");
        bridge.sendToPlayer(player, "crit");
        bridge.sendToPlayer(player, "heart");
        bridge.sendToPlayer(player, "enchant");

        verify(player).playEffect(any(Location.class), eq(Effect.EXPLOSION), isNull());
        verify(player).playEffect(any(Location.class), eq(Effect.COLOURED_DUST), isNull());
        verify(player).playEffect(any(Location.class), eq(Effect.CRIT), isNull());
        verify(player).playEffect(any(Location.class), eq(Effect.HEART), isNull());
        verify(player).playEffect(any(Location.class), eq(Effect.FLYING_GLYPH), isNull());
        verify(player, never()).playEffect(any(Location.class), eq(Effect.CLICK2), any());
        verify(player, never()).playEffect(any(Location.class), eq(Effect.EXPLOSION_HUGE), any());
    }

    @Test
    void unknownLegacyKeyDoesNotUseEffectOrdinal() {
        ParticleBridge bridge = ParticleBridge.create(new FakeSchedulerBridge());
        Player player = mock(Player.class);
        Location location = validLocation();
        when(player.getLocation()).thenReturn(location);

        bridge.sendToPlayer(player, "1");

        verify(player, never()).playEffect(any(Location.class), any(Effect.class), any());
    }

    @Test
    void unknownFamilyDoesNotEmitFootstep() {
        ParticleBridge bridge = ParticleBridge.create(new FakeSchedulerBridge());
        Player player = mock(Player.class);
        Location location = validLocation();
        doThrow(new IllegalStateException("playEffect unavailable"))
            .when(player).playEffect(any(Location.class), any(Effect.class), any());

        assertDoesNotThrow(() -> bridge.sendToPlayer(player, "bubble", location,
            0f, 0f, 0f, 0f, 1));

        verify(player, never()).playEffect(any(Location.class), eq(Effect.FOOTSTEP), any());
    }

    @Test
    void sendColoredDustFallsBackSafelyWhenDustOptionsUnavailable() {
        ParticleBridge bridge = ParticleBridge.create(new FakeSchedulerBridge());
        Player player = mock(Player.class);
        Location location = validLocation();

        assertDoesNotThrow(() -> bridge.sendColoredDust(player, location, 255, 80, 20, 1f, 2));

        verify(player).playEffect(any(Location.class), eq(Effect.COLOURED_DUST), isNull());
    }

    @Test
    void coloredDustUsesCritOnlyWhenLegacyDustInvocationFails() {
        ParticleBridge bridge = ParticleBridge.create(new FakeSchedulerBridge());
        Player player = mock(Player.class);
        Location location = validLocation();
        doThrow(new IllegalStateException("colored dust unavailable"))
            .when(player).playEffect(any(Location.class), eq(Effect.COLOURED_DUST), isNull());

        bridge.sendColoredDust(player, location, 255, 80, 20, 1f, 2);

        verify(player).playEffect(any(Location.class), eq(Effect.CRIT), isNull());
    }

    @Test
    void sendFirstAvailableDoesNotThrowWhenEveryCandidateUnavailable() {
        ParticleBridge bridge = ParticleBridge.create(new FakeSchedulerBridge());
        Player player = mock(Player.class);
        Location location = validLocation();

        assertDoesNotThrow(() -> bridge.sendFirstAvailable(player, location,
            Arrays.asList("no-such-particle-one", "no-such-particle-two"),
            0f, 0f, 0f, 0f, 1));
        assertDoesNotThrow(() -> bridge.sendFirstAvailable(player, location,
            null, 0f, 0f, 0f, 0f, 1));
        assertDoesNotThrow(() -> bridge.sendFirstAvailable(player, location,
            Collections.emptyList(), 0f, 0f, 0f, 0f, 1));

        verifyNoInteractions(player);
    }

    @Test
    void sendFirstAvailableSpawnsFirstUsableSemanticCandidateInOrder() {
        ParticleBridge bridge = ParticleBridge.create(new FakeSchedulerBridge());
        Player player = mock(Player.class);
        Location location = validLocation();

        assertDoesNotThrow(() -> bridge.sendFirstAvailable(player, location,
            Arrays.asList("explode", "smoke"), 0f, 0f, 0f, 0f, 1));

        verify(player).playEffect(any(Location.class), eq(Effect.EXPLOSION), isNull());
        verify(player, never()).playEffect(any(Location.class), eq(Effect.ZOMBIE_CHEW_WOODEN_DOOR), any());
    }

    @Test
    void candidateTwoWorksWhenCandidateOneIsUnavailableAndRepeatsFromCache() {
        ParticleBridge bridge = ParticleBridge.create(new FakeSchedulerBridge());
        Player player = mock(Player.class);
        Location location = validLocation();
        when(player.getLocation()).thenReturn(location);

        bridge.sendToPlayer(player, "candidate-one-unavailable", "heart");
        bridge.sendToPlayer(player, "candidate-one-unavailable", "heart");

        verify(player, times(2)).playEffect(any(Location.class), eq(Effect.HEART), isNull());
    }

    @Test
    void sendBlockBreakUsesLegacyStepSoundOnLegacyClasspath() {
        ParticleBridge bridge = ParticleBridge.create(new FakeSchedulerBridge());
        Player player = mock(Player.class);
        Location location = validLocation();

        assertDoesNotThrow(() -> bridge.sendBlockBreak(player, location, Material.STONE, 4));

        verify(player).playEffect(any(Location.class), eq(Effect.STEP_SOUND),
            eq((Object) Material.STONE.getId()));
    }

    @Test
    void sendBlockBreakRejectsNullArguments() {
        ParticleBridge bridge = ParticleBridge.create(new FakeSchedulerBridge());
        Player player = mock(Player.class);
        Location location = validLocation();

        assertDoesNotThrow(() -> bridge.sendBlockBreak(null, location, Material.STONE, 4));
        assertDoesNotThrow(() -> bridge.sendBlockBreak(player, null, Material.STONE, 4));
        assertDoesNotThrow(() -> bridge.sendBlockBreak(player, location, null, 4));

        verifyNoInteractions(player);
    }

    @Test
    void sendBlockBreakDoesNotThrowWhenFallbackFamilyCannotSend() {
        ParticleBridge bridge = ParticleBridge.create(new FakeSchedulerBridge());
        Player player = mock(Player.class);
        Location location = validLocation();
        doThrow(new IllegalStateException("playEffect unavailable"))
            .when(player).playEffect(any(Location.class), any(Effect.class), anyInt());
        doThrow(new IllegalStateException("playEffect unavailable"))
            .when(player).playEffect(any(Location.class), any(Effect.class), any());

        assertDoesNotThrow(() -> bridge.sendBlockBreak(player, location, Material.STONE, 4));
    }
}
