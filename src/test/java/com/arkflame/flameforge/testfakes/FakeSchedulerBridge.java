package com.arkflame.flameforge.testfakes;

import com.arkflame.flameforge.compat.scheduler.SchedulerBridge;
import com.arkflame.flameforge.compat.scheduler.TaskHandle;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.concurrent.atomic.AtomicInteger;

public final class FakeSchedulerBridge implements SchedulerBridge {
    private final AtomicInteger asyncCallCount = new AtomicInteger(0);

    @Override
    public TaskHandle runGlobal(JavaPlugin plugin, Runnable task) {
        if (task == null) {
            throw new IllegalArgumentException("Task cannot be null");
        }
        if (plugin == null) {
            throw new IllegalArgumentException("Plugin cannot be null");
        }
        task.run();
        return TaskHandleStub.INSTANCE;
    }

    @Override
    public TaskHandle runGlobalLater(JavaPlugin plugin, Runnable task, long delay) {
        if (task == null) {
            throw new IllegalArgumentException("Task cannot be null");
        }
        if (plugin == null) {
            throw new IllegalArgumentException("Plugin cannot be null");
        }
        if (delay < 0) {
            throw new IllegalArgumentException("Delay cannot be negative");
        }
        task.run();
        return TaskHandleStub.INSTANCE;
    }

    @Override
    public TaskHandle runEntity(Entity entity, Runnable runnable, Runnable retireCallback) {
        if (entity == null) {
            throw new IllegalArgumentException("Entity cannot be null");
        }
        if (runnable == null) {
            throw new IllegalArgumentException("Runnable cannot be null");
        }
        if (retireCallback == null) {
            throw new IllegalArgumentException("Retire callback cannot be null");
        }
        runnable.run();
        return TaskHandleStub.INSTANCE;
    }

    @Override
    public TaskHandle runEntityLater(Entity entity, Runnable runnable, Runnable retireCallback, long delay) {
        if (entity == null) {
            throw new IllegalArgumentException("Entity cannot be null");
        }
        if (runnable == null) {
            throw new IllegalArgumentException("Runnable cannot be null");
        }
        if (retireCallback == null) {
            throw new IllegalArgumentException("Retire callback cannot be null");
        }
        if (delay < 0) {
            throw new IllegalArgumentException("Delay cannot be negative");
        }
        runnable.run();
        return TaskHandleStub.INSTANCE;
    }

    @Override
    public TaskHandle runRegion(Location location, Runnable task) {
        if (location == null) {
            throw new IllegalArgumentException("Location cannot be null");
        }
        if (task == null) {
            throw new IllegalArgumentException("Task cannot be null");
        }
        task.run();
        return TaskHandleStub.INSTANCE;
    }

    @Override
    public TaskHandle runRegionLater(Location location, Runnable task, long delay) {
        if (location == null) {
            throw new IllegalArgumentException("Location cannot be null");
        }
        if (task == null) {
            throw new IllegalArgumentException("Task cannot be null");
        }
        if (delay < 0) {
            throw new IllegalArgumentException("Delay cannot be negative");
        }
        task.run();
        return TaskHandleStub.INSTANCE;
    }

    @Override
    public TaskHandle runAsync(JavaPlugin plugin, Runnable task) {
        asyncCallCount.incrementAndGet();
        task.run();
        return TaskHandleStub.INSTANCE;
    }

    @Override
    public void cancelAll(JavaPlugin plugin) {
    }

    @Override
    public boolean isFolia() {
        return false;
    }

    public int getAsyncCallCount() {
        return asyncCallCount.get();
    }

    public void resetAsyncCount() {
        asyncCallCount.set(0);
    }
}
