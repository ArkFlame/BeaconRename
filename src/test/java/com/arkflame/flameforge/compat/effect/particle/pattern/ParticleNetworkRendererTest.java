package com.arkflame.flameforge.compat.effect.particle.pattern;

import com.arkflame.flameforge.compat.scheduler.SchedulerBridge;
import com.arkflame.flameforge.compat.scheduler.TaskHandle;
import com.arkflame.flameforge.compat.effect.particle.style.ParticleStyle;
import com.arkflame.flameforge.compat.effect.particle.style.ParticleStyleCatalog;
import com.arkflame.flameforge.compat.effect.particle.style.ParticleStyleId;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ParticleNetworkRendererTest {
    @Test
    void compilesOrderedSegmentsOnceAndSendsOneFrameThroughSeam() {
        SchedulerBridge scheduler = mock(SchedulerBridge.class);
        Player viewer = mock(Player.class);
        World world = mock(World.class);
        when(viewer.getWorld()).thenReturn(world);
        AtomicInteger sends = new AtomicInteger();
        List<Long> delays = new java.util.ArrayList<Long>();
        ParticleNetworkRenderer.FrameSender sender = (player, points, style, candidates) -> {
            sends.incrementAndGet();
            assertEquals(Arrays.asList("ELECTRIC_SPARK", "END_ROD", "ENCHANT", "NOTE", "CRIT"),
                candidates);
            assertEquals(new ParticlePoint(0, 0, 0), points.get(0));
            assertEquals(new ParticlePoint(1, 0, 0), points.get(2));
        };
        doAnswer(invocation -> {
            delays.add(0L);
            invocation.<Runnable>getArgument(1).run();
            return null;
        }).when(scheduler).runEntity(any(Entity.class), any(Runnable.class), any(Runnable.class));
        doAnswer(invocation -> {
            delays.add(invocation.getArgument(3));
            invocation.<Runnable>getArgument(1).run();
            return null;
        }).when(scheduler).runEntityLater(any(Entity.class), any(Runnable.class), any(Runnable.class), anyLong());

        ParticleNetworkRenderer renderer = new ParticleNetworkRenderer(scheduler, sender);
        renderer.render(viewer, Arrays.asList(new Location(world, 0, 0, 0),
                new Location(world, 1, 0, 0), new Location(world, 2, 0, 0)),
            ParticleStyleCatalog.get(ParticleStyleId.ELECTRIC_NETWORK), null, 1, 5, 0.5);

        assertEquals(5, sends.get());
        assertEquals(Arrays.asList(0L, 2L, 4L, 6L, 8L), delays);
    }

    @Test
    void rejectsMixedWorldsAndOversizedNetworks() {
        SchedulerBridge scheduler = mock(SchedulerBridge.class);
        ParticleNetworkRenderer renderer = new ParticleNetworkRenderer(scheduler,
            (viewer, points, style, candidates) -> { });
        Player viewer = mock(Player.class);
        World first = mock(World.class);
        World second = mock(World.class);
        ParticleStyle style = ParticleStyleCatalog.get(ParticleStyleId.GENERIC_MAGIC);

        assertThrows(IllegalArgumentException.class, () -> renderer.render(viewer,
            Arrays.asList(new Location(first, 0, 0, 0), new Location(second, 1, 0, 0)),
            style, Collections.<String>emptyList(), 1, 5, 1));
        assertThrows(IllegalArgumentException.class, () -> renderer.render(viewer,
            Arrays.asList(new Location(first, 0, 0, 0), new Location(first, 1, 0, 0)),
            style, style.getCandidates(), 2048, 5, 1));
    }
}
