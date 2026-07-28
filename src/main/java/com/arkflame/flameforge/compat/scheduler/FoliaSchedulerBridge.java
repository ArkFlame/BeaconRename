package com.arkflame.flameforge.compat.scheduler;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

class FoliaTaskHandle implements TaskHandle {
    private volatile boolean cancelled = false;
    private final Object task;

    FoliaTaskHandle(Object task) {
        this.task = task;
    }

    Object getTask() {
        return task;
    }

    @Override
    public void cancel() {
        if (!cancelled) {
            cancelled = true;
            try {
                Method cancelMethod = task.getClass().getMethod("cancel");
                cancelMethod.invoke(task);
            } catch (Exception e) {
                throw new RuntimeException("Failed to cancel Folia task", e);
            }
        }
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }
}

public class FoliaSchedulerBridge implements SchedulerBridge {
    private final Object globalScheduler;
    private final Object regionScheduler;
    private final Object entityScheduler;
    private final Object asyncScheduler;
    private final Method runMethod;
    private final Method runDelayedMethod;
    private final Method runEntitySchedulerMethod;
    private final Method runRegionSchedulerMethod;
    private final Method runAsyncMethod;

    public FoliaSchedulerBridge() {
        try {
            Class<?> schedulerClass = Class.forName("net.serveruller.components.IScheduler");
            Class<?> serverClass = Class.forName("net.serveruller.Serveruller");
            Object serveruller = schedulerClass.getField("INSTANCE").get(null);

            Field globalField = serverClass.getDeclaredField("globalScheduler");
            globalField.setAccessible(true);
            globalScheduler = globalField.get(serveruller);

            Field regionField = serverClass.getDeclaredField("regionScheduler");
            regionField.setAccessible(true);
            regionScheduler = regionField.get(serveruller);

            Field entityField = serverClass.getDeclaredField("entityScheduler");
            entityField.setAccessible(true);
            entityScheduler = entityField.get(serveruller);

            Field asyncField = serverClass.getDeclaredField("asyncScheduler");
            asyncField.setAccessible(true);
            asyncScheduler = asyncField.get(serveruller);

            runMethod = schedulerClass.getMethod("run", Runnable.class);
            runDelayedMethod = schedulerClass.getMethod("runDelayed", Runnable.class, long.class);
            runEntitySchedulerMethod = schedulerClass.getMethod("runEntityScheduler", Entity.class, Runnable.class, Runnable.class);
            runRegionSchedulerMethod = schedulerClass.getMethod("runRegionScheduler", Location.class, Runnable.class);
            runAsyncMethod = schedulerClass.getMethod("runAsync", Runnable.class);
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize Folia schedulers via reflection", e);
        }
    }

    private TaskHandle executeOnScheduler(Object scheduler, Method method, Object... args) {
        if (scheduler == null) {
            throw new IllegalStateException("Scheduler not available");
        }
        try {
            Object task = method.invoke(scheduler, args);
            return new FoliaTaskHandle(task);
        } catch (Exception e) {
            throw new RuntimeException("Failed to execute scheduler method", e);
        }
    }

    @Override
    public TaskHandle runGlobal(JavaPlugin plugin, Runnable task) {
        if (task == null) {
            throw new IllegalArgumentException("Task cannot be null");
        }
        return executeOnScheduler(globalScheduler, runMethod, task);
    }

    @Override
    public TaskHandle runGlobalLater(JavaPlugin plugin, Runnable task, long delay) {
        if (task == null) {
            throw new IllegalArgumentException("Task cannot be null");
        }
        if (delay < 0) {
            throw new IllegalArgumentException("Delay cannot be negative");
        }
        return executeOnScheduler(globalScheduler, runDelayedMethod, task, delay);
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
        return executeOnScheduler(entityScheduler, runEntitySchedulerMethod, entity, runnable, retireCallback);
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
        try {
            Method method = entityScheduler.getClass().getMethod("runDelayed", Runnable.class, long.class);
            return executeOnScheduler(entityScheduler, method, wrapEntityRunnable(entity, runnable, retireCallback), delay);
        } catch (Exception e) {
            throw new RuntimeException("Failed to run entity later", e);
        }
    }

    private Runnable wrapEntityRunnable(Entity entity, Runnable runnable, Runnable retireCallback) {
        return () -> {
            try {
                runnable.run();
            } finally {
                if (!Thread.currentThread().isInterrupted()) {
                    try {
                        retireCallback.run();
                    } catch (Exception ignored) {
                    }
                }
            }
        };
    }

    @Override
    public TaskHandle runRegion(Location location, Runnable task) {
        if (location == null) {
            throw new IllegalArgumentException("Location cannot be null");
        }
        if (task == null) {
            throw new IllegalArgumentException("Task cannot be null");
        }
        return executeOnScheduler(regionScheduler, runRegionSchedulerMethod, location, task);
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
        try {
            Method method = regionScheduler.getClass().getMethod("runDelayed", Runnable.class, long.class);
            return executeOnScheduler(regionScheduler, method, task, delay);
        } catch (Exception e) {
            throw new RuntimeException("Failed to run region later", e);
        }
    }

    @Override
    public TaskHandle runAsync(JavaPlugin plugin, Runnable task) {
        if (task == null) {
            throw new IllegalArgumentException("Task cannot be null");
        }
        return executeOnScheduler(asyncScheduler, runAsyncMethod, task);
    }

    @Override
    public void cancelAll(JavaPlugin plugin) {
        throw new UnsupportedOperationException("cancelAll not implemented for Folia - use TaskHandle.cancel() per task");
    }

    @Override
    public boolean isFolia() {
        return true;
    }
}
