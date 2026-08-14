package com.arkflame.flameforge.forge;

import com.arkflame.flameforge.compat.effect.ParticleBridge;
import com.arkflame.flameforge.model.ForgePowerDefinition;
import com.arkflame.flameforge.testfakes.FakeSchedulerBridge;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class MultiStrikeServiceTest {
    @Test
    void chainHitsEligibleTargetsOnceWithinLimit() {
        FakeSchedulerBridge scheduler = new FakeSchedulerBridge();
        ParticleBridge particles = mock(ParticleBridge.class);
        MultiStrikeService service = new MultiStrikeService(scheduler, particles);
        World world = mock(World.class);
        Location center = new Location(world, 0, 0, 0);
        Player viewer = player(center);
        LivingEntity initial = entity(UUID.randomUUID(), center, 1);
        LivingEntity first = entity(UUID.randomUUID(), new Location(world, 1, 0, 0), 2);
        LivingEntity second = entity(UUID.randomUUID(), new Location(world, 2, 0, 0), 3);
        when(world.getNearbyEntities(any(Location.class), anyDouble(), anyDouble(), anyDouble()))
            .thenReturn(Arrays.<Entity>asList(first, first, second));
        ForgePowerDefinition power = power(3, 2);
        Set<UUID> struck = new HashSet<>();
        AtomicInteger trails = new AtomicInteger();
        doAnswer(invocation -> {
            trails.incrementAndGet();
            return null;
        }).when(particles).sendToPlayer(any(), anyString(), any(), anyFloat(), anyFloat(), anyFloat(), anyFloat(), anyInt());

        service.execute(viewer, initial, power, false, target -> struck.add(target.getUniqueId()));

        assertEquals(3, struck.size());
        assertTrue(struck.contains(initial.getUniqueId()));
        assertTrue(struck.contains(first.getUniqueId()));
        assertTrue(struck.contains(second.getUniqueId()));
        assertTrue(trails.get() > 0);
    }

    @Test
    void playersOnlyChainFiltersMobsAndRendersTrail() {
        FakeSchedulerBridge scheduler = new FakeSchedulerBridge();
        ParticleBridge particles = mock(ParticleBridge.class);
        MultiStrikeService service = new MultiStrikeService(scheduler, particles);
        World world = mock(World.class);
        Location center = new Location(world, 0, 0, 0);
        Player viewer = player(center);
        Player initial = player(center);
        LivingEntity mob = entity(UUID.randomUUID(), new Location(world, 1, 0, 0), 2);
        Player secondary = player(new Location(world, 2, 0, 0));
        when(world.getNearbyEntities(any(Location.class), anyDouble(), anyDouble(), anyDouble()))
            .thenReturn(Arrays.<Entity>asList(mob, secondary));
        ForgePowerDefinition power = power(3, 2);
        Set<UUID> struck = new HashSet<>();

        service.execute(viewer, initial, power, true, target -> struck.add(target.getUniqueId()));

        assertEquals(2, struck.size());
        assertTrue(struck.contains(initial.getUniqueId()));
        assertTrue(struck.contains(secondary.getUniqueId()));
        assertFalse(struck.contains(mob.getUniqueId()));
        verify(particles, atLeastOnce()).sendToPlayer(any(), anyString(), any(), anyFloat(), anyFloat(), anyFloat(), anyFloat(), anyInt());
    }

    @Test
    void radialStrikesInitialAndNearbyOnceWithoutRecursion() {
        FakeSchedulerBridge scheduler = new FakeSchedulerBridge();
        ParticleBridge particles = mock(ParticleBridge.class);
        MultiStrikeService service = new MultiStrikeService(scheduler, particles);
        World world = mock(World.class);
        Player viewer = player(new Location(world, -10, 0, 0));
        LivingEntity initial = entity(UUID.randomUUID(), new Location(world, 0, 0, 0), 1);
        LivingEntity near = entity(UUID.randomUUID(), new Location(world, 2, 0, 0), 2);
        LivingEntity far = entity(UUID.randomUUID(), new Location(world, 6, 0, 0), 3);
        when(world.getNearbyEntities(new Location(world, 0, 0, 0), 5.0, 5.0, 5.0))
            .thenReturn(Arrays.<Entity>asList(near));
        when(world.getNearbyEntities(new Location(world, 2, 0, 0), 5.0, 5.0, 5.0))
            .thenReturn(Arrays.<Entity>asList(far));
        ForgePowerDefinition power = power(3, 2, 5);
        Map<UUID, Integer> strikes = new HashMap<>();

        service.executeRadial(viewer, initial, power, false,
            target -> strikes.merge(target.getUniqueId(), 1, Integer::sum));

        assertEquals(2, strikes.size());
        assertEquals(1, strikes.get(initial.getUniqueId()).intValue());
        assertEquals(1, strikes.get(near.getUniqueId()).intValue());
        assertFalse(strikes.containsKey(far.getUniqueId()));
    }

    @Test
    void chainPropagatesHopByHopAlongGeometry() {
        FakeSchedulerBridge scheduler = new FakeSchedulerBridge();
        ParticleBridge particles = mock(ParticleBridge.class);
        MultiStrikeService service = new MultiStrikeService(scheduler, particles);
        World world = mock(World.class);
        Player viewer = player(new Location(world, -10, 0, 0));
        LivingEntity a = entity(UUID.randomUUID(), new Location(world, 0, 0, 0), 1);
        LivingEntity b = entity(UUID.randomUUID(), new Location(world, 4, 0, 0), 2);
        LivingEntity c = entity(UUID.randomUUID(), new Location(world, 8, 0, 0), 3);
        when(world.getNearbyEntities(new Location(world, 0, 0, 0), 5.0, 5.0, 5.0))
            .thenReturn(Arrays.<Entity>asList(b));
        when(world.getNearbyEntities(new Location(world, 4, 0, 0), 5.0, 5.0, 5.0))
            .thenReturn(Arrays.<Entity>asList(a, c));
        when(world.getNearbyEntities(new Location(world, 8, 0, 0), 5.0, 5.0, 5.0))
            .thenReturn(Arrays.<Entity>asList(b));
        ForgePowerDefinition power = power(3, 2, 5);
        Map<UUID, Integer> strikes = new HashMap<>();

        service.executeChain(viewer, a, power, false,
            target -> strikes.merge(target.getUniqueId(), 1, Integer::sum));

        assertEquals(3, strikes.size());
        assertEquals(1, strikes.get(a.getUniqueId()).intValue());
        assertEquals(1, strikes.get(b.getUniqueId()).intValue());
        assertEquals(1, strikes.get(c.getUniqueId()).intValue());
    }

    @Test
    void chainDeduplicatesCyclicNearbyGraph() {
        FakeSchedulerBridge scheduler = new FakeSchedulerBridge();
        ParticleBridge particles = mock(ParticleBridge.class);
        MultiStrikeService service = new MultiStrikeService(scheduler, particles);
        World world = mock(World.class);
        Player viewer = player(new Location(world, -10, 0, 0));
        LivingEntity a = entity(UUID.randomUUID(), new Location(world, 0, 0, 0), 1);
        LivingEntity b = entity(UUID.randomUUID(), new Location(world, 4, 0, 0), 2);
        LivingEntity c = entity(UUID.randomUUID(), new Location(world, 2, 0, 0), 3);
        when(world.getNearbyEntities(new Location(world, 0, 0, 0), 5.0, 5.0, 5.0))
            .thenReturn(Arrays.<Entity>asList(b, c));
        when(world.getNearbyEntities(new Location(world, 4, 0, 0), 5.0, 5.0, 5.0))
            .thenReturn(Arrays.<Entity>asList(a, c));
        when(world.getNearbyEntities(new Location(world, 2, 0, 0), 5.0, 5.0, 5.0))
            .thenReturn(Arrays.<Entity>asList(a, b));
        ForgePowerDefinition power = power(3, 2, 5);
        Map<UUID, Integer> strikes = new HashMap<>();

        service.executeChain(viewer, a, power, false,
            target -> strikes.merge(target.getUniqueId(), 1, Integer::sum));

        assertEquals(3, strikes.size());
        assertEquals(1, strikes.get(a.getUniqueId()).intValue());
        assertEquals(1, strikes.get(b.getUniqueId()).intValue());
        assertEquals(1, strikes.get(c.getUniqueId()).intValue());
    }

    @Test
    void radialPlayersOnlyFiltersMobs() {
        FakeSchedulerBridge scheduler = new FakeSchedulerBridge();
        ParticleBridge particles = mock(ParticleBridge.class);
        MultiStrikeService service = new MultiStrikeService(scheduler, particles);
        World world = mock(World.class);
        Player viewer = player(new Location(world, -10, 0, 0));
        Player initial = player(new Location(world, 0, 0, 0));
        LivingEntity mob = entity(UUID.randomUUID(), new Location(world, 1, 0, 0), 2);
        Player secondary = player(new Location(world, 2, 0, 0));
        when(world.getNearbyEntities(new Location(world, 0, 0, 0), 5.0, 5.0, 5.0))
            .thenReturn(Arrays.<Entity>asList(mob, secondary));
        ForgePowerDefinition power = power(3, 2, 5);
        Set<UUID> struck = new HashSet<>();

        service.executeRadial(viewer, initial, power, true,
            target -> struck.add(target.getUniqueId()));

        assertEquals(2, struck.size());
        assertTrue(struck.contains(initial.getUniqueId()));
        assertTrue(struck.contains(secondary.getUniqueId()));
        assertFalse(struck.contains(mob.getUniqueId()));
    }

    @Test
    void chainPlayersOnlyFiltersMobsAtEveryHop() {
        FakeSchedulerBridge scheduler = new FakeSchedulerBridge();
        ParticleBridge particles = mock(ParticleBridge.class);
        MultiStrikeService service = new MultiStrikeService(scheduler, particles);
        World world = mock(World.class);
        Player viewer = player(new Location(world, -10, 0, 0));
        Player initial = player(new Location(world, 0, 0, 0));
        LivingEntity mob = entity(UUID.randomUUID(), new Location(world, 2, 0, 0), 2);
        Player secondary = player(new Location(world, 4, 0, 0));
        when(world.getNearbyEntities(new Location(world, 0, 0, 0), 5.0, 5.0, 5.0))
            .thenReturn(Arrays.<Entity>asList(mob, secondary));
        when(world.getNearbyEntities(new Location(world, 4, 0, 0), 5.0, 5.0, 5.0))
            .thenReturn(Arrays.<Entity>asList(mob));
        ForgePowerDefinition power = power(3, 2, 5);
        Set<UUID> struck = new HashSet<>();

        service.executeChain(viewer, initial, power, true,
            target -> struck.add(target.getUniqueId()));

        assertEquals(2, struck.size());
        assertTrue(struck.contains(initial.getUniqueId()));
        assertTrue(struck.contains(secondary.getUniqueId()));
        assertFalse(struck.contains(mob.getUniqueId()));
    }

    private static ForgePowerDefinition power(int maxTargets, int delay) {
        return power(maxTargets, delay, 10);
    }

    private static ForgePowerDefinition power(int maxTargets, int delay, double radius) {
        ForgePowerDefinition power = mock(ForgePowerDefinition.class);
        when(power.getMaxTargets()).thenReturn(maxTargets);
        when(power.getChainDelayTicks()).thenReturn(delay);
        when(power.getTrailPoints()).thenReturn(2);
        when(power.getRadius()).thenReturn(BigDecimal.valueOf(radius));
        when(power.getParticleCandidates()).thenReturn(Collections.singletonList("FLAME"));
        return power;
    }

    private static Player player(Location location) {
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(player.isDead()).thenReturn(false);
        when(player.getLocation()).thenReturn(location);
        return player;
    }

    private static LivingEntity entity(UUID id, Location location, int entityId) {
        LivingEntity entity = mock(LivingEntity.class);
        when(entity.getUniqueId()).thenReturn(id);
        when(entity.getEntityId()).thenReturn(entityId);
        when(entity.isDead()).thenReturn(false);
        when(entity.getLocation()).thenReturn(location);
        return entity;
    }
}
