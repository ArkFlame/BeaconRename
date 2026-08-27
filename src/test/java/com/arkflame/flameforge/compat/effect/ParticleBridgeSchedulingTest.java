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
