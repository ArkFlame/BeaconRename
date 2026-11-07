package com.arkflame.flameforge.compat.effect;

import com.arkflame.flameforge.compat.effect.particle.ParticleBatch;
import com.arkflame.flameforge.compat.effect.particle.ParticleCapabilities;
import com.arkflame.flameforge.compat.effect.particle.ParticleCatalog;
import com.arkflame.flameforge.compat.effect.particle.ParticleProvider;
import com.arkflame.flameforge.compat.effect.particle.ParticleRequest;
import com.arkflame.flameforge.compat.scheduler.SchedulerBridge;
import com.arkflame.flameforge.compat.scheduler.TaskHandle;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class ParticleBridgeSchedulingTest {
    @Test
    void batchSchedulesOneEntityTaskAndEmitsAllRequestsOnlyWhenRun() {
        QueuedScheduler scheduler = new QueuedScheduler();
        AtomicInteger emitted = new AtomicInteger();
        ParticleProvider provider = provider(emitted);
        ParticleBridge bridge = new ParticleBridge(scheduler, provider, new ParticleCatalog());
        Player viewer = mock(Player.class);

        assertTrue(bridge.sendBatch(viewer, batch(100)));
        assertEquals(1, scheduler.entityCalls);
        assertEquals(0, scheduler.asyncCalls);
        assertEquals(0, emitted.get());

        scheduler.runEntityTask();

        assertEquals(100, emitted.get());
    }

    @Test
    void delayedBatchUsesOneDelayedEntityTaskAndNoAsyncTask() {
        QueuedScheduler scheduler = new QueuedScheduler();
        AtomicInteger emitted = new AtomicInteger();
        ParticleBridge bridge = new ParticleBridge(scheduler, provider(emitted), new ParticleCatalog());
        Player viewer = mock(Player.class);

        assertTrue(bridge.sendBatchLater(viewer, batch(3), 17L));
        assertEquals(0, scheduler.entityCalls);
        assertEquals(1, scheduler.delayedEntityCalls);
        assertEquals(17L, scheduler.delay);
        assertEquals(0, scheduler.asyncCalls);
        assertEquals(0, emitted.get());

        scheduler.runDelayedTask();

        assertEquals(3, emitted.get());
    }

    @Test
    void firstAvailableAtLocationsSchedulesOneCappedBatchOfValidSingleRequests() {
        QueuedScheduler scheduler = new QueuedScheduler();
        List<ParticleRequest> emittedRequests = new ArrayList<ParticleRequest>();
        ParticleBridge bridge = new ParticleBridge(scheduler, capturingProvider(emittedRequests), new ParticleCatalog());
        Player viewer = mock(Player.class);
        World world = mock(World.class);
        Location valid = new Location(world, 1, 2, 3);
        Location worldless = new Location(null, 4, 5, 6);

        bridge.sendFirstAvailableAtLocations(viewer,
            java.util.Arrays.asList(null, worldless, valid, new Location(world, 7, 8, 9)),
            java.util.Collections.singletonList("DUST"), 1);

        assertEquals(1, scheduler.entityCalls);
        assertEquals(0, emittedRequests.size());
        scheduler.runEntityTask();

        assertEquals(1, emittedRequests.size());
        ParticleRequest request = emittedRequests.get(0);
        assertEquals(1, request.getCount());
        assertEquals(0, request.getOffsetX());
        assertEquals(0, request.getOffsetY());
        assertEquals(0, request.getOffsetZ());
        assertEquals(0, request.getExtra());
        assertTrue(request.getPayload() instanceof ParticleRequest.None);
    }

    private static ParticleProvider provider(final AtomicInteger emitted) {
        return new ParticleProvider() {
            @Override public ParticleCapabilities getCapabilities() {
                return new ParticleCapabilities("test", false, java.util.Collections.<String>emptySet());
            }

            @Override public boolean emit(Player viewer, ParticleRequest request) {
                emitted.incrementAndGet();
                return true;
            }
        };
    }

    private static ParticleProvider capturingProvider(final List<ParticleRequest> emittedRequests) {
        return new ParticleProvider() {
            @Override public ParticleCapabilities getCapabilities() {
                return new ParticleCapabilities("test", false, java.util.Collections.<String>emptySet());
            }

            @Override public boolean emit(Player viewer, ParticleRequest request) {
                emittedRequests.add(request);
                return true;
            }
        };
    }

    private static ParticleBatch batch(int size) {
        World world = mock(World.class);
        ParticleRequest.ParticlePosition position = new ParticleRequest.ParticlePosition(world, 1, 2, 3);
        List<ParticleRequest> requests = new ArrayList<ParticleRequest>(size);
        for (int i = 0; i < size; i++) {
            requests.add(new ParticleRequest(position, java.util.Collections.singletonList("DUST"), 1,
                0, 0, 0, 0, new ParticleRequest.None()));
        }
        return new ParticleBatch(requests);
    }

    private static final class QueuedScheduler implements SchedulerBridge {
        private Runnable entityTask;
        private Runnable delayedTask;
        private int entityCalls;
        private int delayedEntityCalls;
        private int asyncCalls;
        private long delay;

        @Override public TaskHandle runGlobal(JavaPlugin plugin, Runnable task) { return TaskHandleStub.INSTANCE; }
        @Override public TaskHandle runGlobalLater(JavaPlugin plugin, Runnable task, long delay) {
            return TaskHandleStub.INSTANCE;
        }

        @Override public TaskHandle runEntity(Entity entity, Runnable task, Runnable retireCallback) {
            entityCalls++;
            entityTask = task;
            return TaskHandleStub.INSTANCE;
        }

        @Override public TaskHandle runEntityLater(Entity entity, Runnable task, Runnable retireCallback, long delay) {
            delayedEntityCalls++;
            delayedTask = task;
            this.delay = delay;
            return TaskHandleStub.INSTANCE;
        }

        @Override public TaskHandle runRegion(Location location, Runnable task) { return TaskHandleStub.INSTANCE; }
        @Override public TaskHandle runRegionLater(Location location, Runnable task, long delay) {
            return TaskHandleStub.INSTANCE;
        }

        @Override public TaskHandle runAsync(JavaPlugin plugin, Runnable task) {
            asyncCalls++;
            return TaskHandleStub.INSTANCE;
        }

        @Override public void cancelAll(JavaPlugin plugin) { }
        @Override public boolean isFolia() { return false; }

        void runEntityTask() {
            Runnable task = entityTask;
            entityTask = null;
            task.run();
        }

        void runDelayedTask() {
            Runnable task = delayedTask;
            delayedTask = null;
            task.run();
        }
    }

    private enum TaskHandleStub implements TaskHandle {
        INSTANCE;
        @Override public void cancel() { }
        @Override public boolean isCancelled() { return false; }
    }
}
