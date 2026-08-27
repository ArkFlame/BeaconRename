package com.arkflame.flameforge.forge;

import com.arkflame.flameforge.compat.effect.ParticleBridge;
import com.arkflame.flameforge.compat.effect.particle.ParticleBatch;
import com.arkflame.flameforge.compat.effect.particle.pattern.ParticleNetworkRenderer;
import com.arkflame.flameforge.compat.effect.particle.style.ParticleStyle;
import com.arkflame.flameforge.compat.effect.particle.style.ParticleStyleId;
import com.arkflame.flameforge.compat.scheduler.SchedulerBridge;
import com.arkflame.flameforge.model.ForgePowerDefinition;
import com.arkflame.flameforge.testfakes.FakeSchedulerBridge;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
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

    @Test
    void chainDelaysChildrenAndRendersFiveFramesPerEdge() {
        SchedulerBridge scheduler = mock(SchedulerBridge.class);
        ParticleBridge particles = mock(ParticleBridge.class);
        MultiStrikeService service = new MultiStrikeService(scheduler, particles);
        World world = mock(World.class);
        Player viewer = player(new Location(world, -10, 0, 0));
        LivingEntity a = entity(UUID.randomUUID(), new Location(world, 0, 0, 0), 1);
        LivingEntity b = entity(UUID.randomUUID(), new Location(world, 4, 2, 0), 2);
        LivingEntity c = entity(UUID.randomUUID(), new Location(world, 8, 4, 1), 3);
        when(world.getNearbyEntities(new Location(world, 0, 0, 0), 5.0, 5.0, 5.0))
            .thenReturn(Arrays.<Entity>asList(b));
        when(world.getNearbyEntities(new Location(world, 4, 2, 0), 5.0, 5.0, 5.0))
            .thenReturn(Arrays.<Entity>asList(a, c));
        when(world.getNearbyEntities(new Location(world, 8, 4, 1), 5.0, 5.0, 5.0))
            .thenReturn(Arrays.<Entity>asList(b));
        ForgePowerDefinition power = chainDamagePower(3, 2, 5);

        Map<Entity, List<Runnable>> entityTasks = new LinkedHashMap<>();
        List<Object[]> laterTasks = new ArrayList<>();
        List<Object[]> batchCalls = new ArrayList<>();
        when(scheduler.runEntity(any(), any(), any())).thenAnswer(invocation -> {
            Entity entity = invocation.getArgument(0);
            List<Runnable> tasks = entityTasks.get(entity);
            if (tasks == null) {
                tasks = new ArrayList<>();
                entityTasks.put(entity, tasks);
            }
            tasks.add(invocation.getArgument(1));
            return null;
        });
        when(scheduler.runEntityLater(any(), any(), any(), anyLong())).thenAnswer(invocation -> {
            laterTasks.add(new Object[] {invocation.getArgument(0), invocation.getArgument(1),
                invocation.getArgument(3)});
            return null;
        });
        when(scheduler.runRegion(any(), any())).thenAnswer(invocation -> {
            Runnable task = invocation.getArgument(1);
            task.run();
            return null;
        });
        doAnswer(invocation -> {
            batchCalls.add(new Object[] {invocation.getArgument(0), invocation.getArgument(1),
                invocation.getArgument(2)});
            return true;
        }).when(particles).sendBatchLater(any(), any(ParticleBatch.class), anyLong());

        Map<UUID, Integer> struck = new HashMap<>();
        List<UUID> strikeOrder = new ArrayList<>();
        service.executeChain(viewer, a, power, false,
            target -> {
                strikeOrder.add(target.getUniqueId());
                struck.merge(target.getUniqueId(), 1, Integer::sum);
            });

        List<Runnable> aTasks = entityTasks.get(a);
        assertNotNull(aTasks);
        assertEquals(1, aTasks.size());
        aTasks.get(0).run();

        Object[] bTask = null;
        for (Object[] task : laterTasks) {
            if (task[0] == b) {
                bTask = task;
            }
        }
        assertNotNull(bTask, "first child must be scheduled through runEntityLater");
        assertEquals(2L, bTask[2]);
        ((Runnable) bTask[1]).run();

        Object[] cTask = null;
        for (Object[] task : laterTasks) {
            if (task[0] == c) {
                cTask = task;
            }
        }
        assertNotNull(cTask, "second child must be scheduled through runEntityLater");
        assertEquals(2L, cTask[2]);
        ((Runnable) cTask[1]).run();

        assertEquals(3, struck.size());
        assertEquals(1, struck.get(a.getUniqueId()).intValue());
        assertEquals(1, struck.get(b.getUniqueId()).intValue());
        assertEquals(1, struck.get(c.getUniqueId()).intValue());
        assertEquals(Arrays.asList(a.getUniqueId(), b.getUniqueId(), c.getUniqueId()), strikeOrder);

        for (Object[] task : laterTasks) {
            assertFalse(task[0] == a, "initial chain strike must never be delayed");
        }
        assertNull(entityTasks.get(b), "children must not run immediately");
        assertNull(entityTasks.get(c), "children must not run immediately");

        assertEquals(15, batchCalls.size(), "one batch call per frame across three rendered edges");
        List<Long> frameDelays = new ArrayList<>();
        for (Object[] call : batchCalls) {
            assertSame(viewer, call[0]);
            assertNotNull(call[1]);
            frameDelays.add((Long) call[2]);
        }
        assertEquals(Arrays.asList(0L, 2L, 4L, 6L, 8L,
            0L, 2L, 4L, 6L, 8L,
            0L, 2L, 4L, 6L, 8L), frameDelays);
        verify(particles, times(15)).sendBatchLater(eq(viewer), any(ParticleBatch.class), anyLong());
    }

    @Test
    void chainRendersAllFiveFramesForDistinctPlayerTarget() {
        SchedulerBridge scheduler = mock(SchedulerBridge.class);
        ParticleBridge particles = mock(ParticleBridge.class);
        MultiStrikeService service = new MultiStrikeService(scheduler, particles);
        World world = mock(World.class);
        Player viewer = player(new Location(world, -10, 0, 0));
        LivingEntity initial = entity(UUID.randomUUID(), new Location(world, 0, 0, 0), 1);
        Player target = player(new Location(world, 4, 0, 0));
        when(world.getNearbyEntities(new Location(world, 0, 0, 0), 5.0, 5.0, 5.0))
            .thenReturn(Arrays.<Entity>asList(target));
        ForgePowerDefinition power = chainDamagePower(2, 2, 5);

        Map<Entity, List<Runnable>> entityTasks = new LinkedHashMap<>();
        List<Object[]> laterTasks = new ArrayList<>();
        List<Object[]> batchCalls = new ArrayList<>();
        when(scheduler.runEntity(any(), any(), any())).thenAnswer(invocation -> {
            Entity entity = invocation.getArgument(0);
            List<Runnable> tasks = entityTasks.get(entity);
            if (tasks == null) {
                tasks = new ArrayList<>();
                entityTasks.put(entity, tasks);
            }
            tasks.add(invocation.getArgument(1));
            return null;
        });
        when(scheduler.runEntityLater(any(), any(), any(), anyLong())).thenAnswer(invocation -> {
            laterTasks.add(new Object[] {invocation.getArgument(0), invocation.getArgument(1),
                invocation.getArgument(3)});
            return null;
        });
        when(scheduler.runRegion(any(), any())).thenAnswer(invocation -> {
            Runnable task = invocation.getArgument(1);
            task.run();
            return null;
        });
        doAnswer(invocation -> {
            batchCalls.add(new Object[] {invocation.getArgument(0), invocation.getArgument(1),
                invocation.getArgument(2)});
            return true;
        }).when(particles).sendBatchLater(any(), any(ParticleBatch.class), anyLong());

        List<UUID> strikeOrder = new ArrayList<>();
        service.executeChain(viewer, initial, power, false,
            targetEntity -> strikeOrder.add(targetEntity.getUniqueId()));
        entityTasks.get(initial).get(0).run();
        assertEquals(1, laterTasks.size());
        assertSame(target, laterTasks.get(0)[0]);
        assertEquals(2L, laterTasks.get(0)[2]);
        for (Object[] task : laterTasks) {
            if (task[0] == target) {
                ((Runnable) task[1]).run();
                break;
            }
        }

        assertEquals(Arrays.asList(initial.getUniqueId(), target.getUniqueId()), strikeOrder);
        assertEquals(15, batchCalls.size(), "five batches for each viewer-edge pair");
        List<Object> batchOwners = new ArrayList<>();
        List<Long> frameDelays = new ArrayList<>();
        for (Object[] call : batchCalls) {
            batchOwners.add(call[0]);
            assertNotNull(call[1]);
            frameDelays.add((Long) call[2]);
        }
        assertEquals(Arrays.asList(viewer, viewer, viewer, viewer, viewer,
            viewer, viewer, viewer, viewer, viewer,
            target, target, target, target, target), batchOwners);
        assertEquals(Arrays.asList(0L, 2L, 4L, 6L, 8L,
            0L, 2L, 4L, 6L, 8L,
            0L, 2L, 4L, 6L, 8L), frameDelays);
        verify(particles, times(10)).sendBatchLater(eq(viewer), any(ParticleBatch.class), anyLong());
        verify(particles, times(5)).sendBatchLater(eq(target), any(ParticleBatch.class), anyLong());
    }

    @Test
    void chainDamageStyleUsesElectricNetwork() {
        FakeSchedulerBridge scheduler = new FakeSchedulerBridge();
        ParticleBridge particles = mock(ParticleBridge.class);
        List<ParticleStyle> styles = new ArrayList<>();
        List<List<String>> candidates = new ArrayList<>();
        ParticleNetworkRenderer renderer = new ParticleNetworkRenderer(scheduler,
            (player, points, style, preferredCandidates) -> {
                styles.add(style);
                candidates.add(preferredCandidates);
            });
        MultiStrikeService service = new MultiStrikeService(scheduler, particles, renderer);
        World world = mock(World.class);
        Player viewer = player(new Location(world, -10, 0, 0));
        LivingEntity a = entity(UUID.randomUUID(), new Location(world, 0, 0, 0), 1);
        LivingEntity b = entity(UUID.randomUUID(), new Location(world, 4, 0, 0), 2);
        when(world.getNearbyEntities(new Location(world, 0, 0, 0), 5.0, 5.0, 5.0))
            .thenReturn(Arrays.<Entity>asList(b));
        when(world.getNearbyEntities(new Location(world, 4, 0, 0), 5.0, 5.0, 5.0))
            .thenReturn(Arrays.<Entity>asList(a));
        ForgePowerDefinition power = chainDamagePower(3, 2, 5);

        service.executeChain(viewer, a, power, false, target -> { });

        assertFalse(styles.isEmpty());
        assertEquals(ParticleStyleId.ELECTRIC_NETWORK, styles.get(0).getId());
        assertEquals(styles.get(0).getCandidates(), candidates.get(0));
        assertTrue(candidates.get(0).contains("ELECTRIC_SPARK"));
    }

    @Test
    void chainPotionStyleUsesContagionNetwork() {
        FakeSchedulerBridge scheduler = new FakeSchedulerBridge();
        ParticleBridge particles = mock(ParticleBridge.class);
        List<ParticleStyle> styles = new ArrayList<>();
        ParticleNetworkRenderer renderer = new ParticleNetworkRenderer(scheduler,
            (player, points, style, preferredCandidates) -> styles.add(style));
        MultiStrikeService service = new MultiStrikeService(scheduler, particles, renderer);
        World world = mock(World.class);
        Player viewer = player(new Location(world, -10, 0, 0));
        LivingEntity a = entity(UUID.randomUUID(), new Location(world, 0, 0, 0), 1);
        LivingEntity b = entity(UUID.randomUUID(), new Location(world, 4, 0, 0), 2);
        when(world.getNearbyEntities(new Location(world, 0, 0, 0), 5.0, 5.0, 5.0))
            .thenReturn(Arrays.<Entity>asList(b));
        when(world.getNearbyEntities(new Location(world, 4, 0, 0), 5.0, 5.0, 5.0))
            .thenReturn(Arrays.<Entity>asList(a));
        ForgePowerDefinition power = chainPoisonPower(3, 2, 5);

        service.executeChain(viewer, a, power, false, target -> { });

        assertFalse(styles.isEmpty());
        assertEquals(ParticleStyleId.CONTAGION_NETWORK, styles.get(0).getId());
    }

    @Test
    void genericChainStylePassesAllConfiguredParticleCandidates() {
        FakeSchedulerBridge scheduler = new FakeSchedulerBridge();
        ParticleBridge particles = mock(ParticleBridge.class);
        List<List<String>> candidates = new ArrayList<>();
        ParticleNetworkRenderer renderer = new ParticleNetworkRenderer(scheduler,
            (player, points, style, preferredCandidates) -> candidates.add(preferredCandidates));
        MultiStrikeService service = new MultiStrikeService(scheduler, particles, renderer);
        World world = mock(World.class);
        Player viewer = player(new Location(world, -10, 0, 0));
        LivingEntity target = entity(UUID.randomUUID(), new Location(world, 0, 0, 0), 1);
        ForgePowerDefinition power = power(1, 0, 5);
        when(power.getParticleCandidates()).thenReturn(Arrays.asList("CUSTOM_A", "CUSTOM_B"));

        service.executeChain(viewer, target, power, false, ignored -> { });

        assertFalse(candidates.isEmpty());
        assertEquals(Arrays.asList("CUSTOM_A", "CUSTOM_B"), candidates.get(0));
    }

    private static ForgePowerDefinition chainDamagePower(int maxTargets, int delay, double radius) {
        ForgePowerDefinition power = mock(ForgePowerDefinition.class);
        when(power.getPowerType()).thenReturn(ForgePowerDefinition.PowerType.ON_HIT_CHAIN_DAMAGE);
        when(power.getMaxTargets()).thenReturn(maxTargets);
        when(power.getChainDelayTicks()).thenReturn(delay);
        when(power.getTrailPoints()).thenReturn(2);
        when(power.getRadius()).thenReturn(BigDecimal.valueOf(radius));
        when(power.getParticleCandidates()).thenReturn(Collections.singletonList("FLAME"));
        return power;
    }

    private static ForgePowerDefinition chainPoisonPower(int maxTargets, int delay, double radius) {
        ForgePowerDefinition power = mock(ForgePowerDefinition.class);
        when(power.getPowerType()).thenReturn(ForgePowerDefinition.PowerType.ON_HIT_CHAIN_POTION);
        when(power.getMaxTargets()).thenReturn(maxTargets);
        when(power.getChainDelayTicks()).thenReturn(delay);
        when(power.getTrailPoints()).thenReturn(2);
        when(power.getRadius()).thenReturn(BigDecimal.valueOf(radius));
        when(power.getEffectCandidates()).thenReturn(Collections.singletonList("POISON"));
        when(power.getParticleCandidates()).thenReturn(Collections.singletonList("HAPPY_VILLAGER"));
        return power;
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
