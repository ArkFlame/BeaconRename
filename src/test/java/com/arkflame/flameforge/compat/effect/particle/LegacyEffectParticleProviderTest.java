package com.arkflame.flameforge.compat.effect.particle;

import org.bukkit.Effect;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LegacyEffectParticleProviderTest {
    private static Location location() {
        return new Location(mock(World.class), 1, 2, 3);
    }

    private static ParticleRequest request(Location location, String... candidates) {
        return new ParticleRequest(new ParticleRequest.ParticlePosition(location.getWorld(), location.getX(),
            location.getY(), location.getZ()), Arrays.asList(candidates), 2, 0.1, 0.2, 0.3, 0.4,
            new ParticleRequest.None());
    }

    @Test
    void aliasesBlockAndItemPayloadsUseLegacyEffectData() {
        LegacyEffectParticleProvider provider = new LegacyEffectParticleProvider(new ParticleCatalog());
        Player viewer = mock(Player.class);
        Location location = location();

        assertTrue(provider.emit(viewer, request(location, "explode")));
        assertTrue(provider.emit(viewer, request(location, "firework")));
        assertTrue(provider.emit(viewer, request(location, "spell")));
        assertTrue(provider.emit(viewer, new ParticleRequest(new ParticleRequest.ParticlePosition(
            location.getWorld(), location.getX(), location.getY(), location.getZ()),
            Arrays.asList("block_crack"), 1, 0, 0, 0, 0,
            new ParticleRequest.Block(Material.STONE, (byte) 4))));
        assertTrue(provider.emit(viewer, new ParticleRequest(new ParticleRequest.ParticlePosition(
            location.getWorld(), location.getX(), location.getY(), location.getZ()),
            Arrays.asList("item_crack"), 1, 0, 0, 0, 0,
            new ParticleRequest.Item(new ItemStack(Material.STONE)))));

        verify(viewer).playEffect(any(Location.class), eq(Effect.EXPLOSION), isNull());
        verify(viewer).playEffect(any(Location.class), eq(Effect.FIREWORKS_SPARK), isNull());
        verify(viewer).playEffect(any(Location.class), eq(Effect.SPELL), isNull());
        verify(viewer).playEffect(any(Location.class), eq(Effect.STEP_SOUND), eq((Object) Material.STONE.getId()));
        verify(viewer).playEffect(any(Location.class), eq(Effect.ITEM_BREAK), eq((Object) Material.STONE.getId()));
    }

    @Test
    void unavailableFullSpigotSeamFallsBackToDirectEffect() {
        LegacyEffectParticleProvider provider = new LegacyEffectParticleProvider(new ParticleCatalog());
        Player viewer = mock(Player.class);
        Player.Spigot spigot = mock(Player.Spigot.class);
        when(viewer.spigot()).thenReturn(spigot);
        Location location = location();

        assertTrue(provider.emit(viewer, request(location, "heart")));

        verify(viewer).playEffect(any(Location.class), eq(Effect.HEART), isNull());
    }

    @Test
    void coloredParticleFailureFallsBackToSafeGenericEffect() {
        LegacyEffectParticleProvider provider = new LegacyEffectParticleProvider(new ParticleCatalog());
        Player viewer = mock(Player.class);
        Location location = location();
        doThrow(new IllegalStateException("dust unavailable"))
            .when(viewer).playEffect(any(Location.class), eq(Effect.COLOURED_DUST), isNull());

        ParticleRequest color = new ParticleRequest(new ParticleRequest.ParticlePosition(location.getWorld(),
            location.getX(), location.getY(), location.getZ()), Arrays.asList("DUST"), 1, 0, 0, 0, 0,
            new ParticleRequest.Color(new ParticleColor(255, 20, 10), 1f));
        assertDoesNotThrow(() -> provider.emit(viewer, color));

        verify(viewer).playEffect(any(Location.class), eq(Effect.CRIT), isNull());
    }

    @Test
    void unknownCandidateFallsThroughToLaterGenericCandidateWithoutFootstep() {
        LegacyEffectParticleProvider provider = new LegacyEffectParticleProvider(new ParticleCatalog());
        Player viewer = mock(Player.class);
        Location location = location();

        assertTrue(provider.emit(viewer, request(location, "future_particle", "heart")));

        verify(viewer).playEffect(any(Location.class), eq(Effect.HEART), isNull());
        verify(viewer, never()).playEffect(any(Location.class), eq(Effect.FOOTSTEP), any(Object.class));
    }
}
