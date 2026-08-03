package com.arkflame.flameforge.compat.scheduler;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Method;
import java.lang.reflect.InvocationTargetException;
import java.util.function.Consumer;

class FoliaTaskHandle implements TaskHandle {
    private volatile boolean cancelled = false;
    private final Object task;
    private final Method isCancelledMethod;
    private final Method cancelMethod;

    FoliaTaskHandle(Object task, Method isCancelledMethod, Method cancelMethod) {
        this.task = task;
        this.isCancelledMethod = isCancelledMethod;
        this.cancelMethod = cancelMethod;
    }

    @Override
    public synchronized void cancel() {
        if (!cancelled) {
            if (task != null && cancelMethod != null) {
                try {
                    cancelMethod.invoke(task);
                } catch (Exception e) {
                    throw FoliaSchedulerBridge.schedulerFailure("Failed to cancel Folia task", e);
                }
            }
            cancelled = true;
        }
    }

    @Override
    public boolean isCancelled() {
        if (cancelled) {
            return true;
        }
        if (task == null) {
            return true;
        }
        if (isCancelledMethod != null) {
            try {
                return (Boolean) isCancelledMethod.invoke(task);
            } catch (Exception e) {
                throw FoliaSchedulerBridge.schedulerFailure("Failed to inspect Folia task cancellation", e);
            }
        }
        throw new IllegalStateException("Folia task cancellation method unavailable");
    }
}

public class FoliaSchedulerBridge implements SchedulerBridge {
    private final JavaPlugin plugin;
    private final Object globalScheduler;
    private final Object regionScheduler;
    private final Object asyncScheduler;
    private final Method globalRunMethod;
    private final Method globalRunDelayedMethod;
    private final Method regionRunMethod;
    private final Method regionRunDelayedMethod;
    private final Method asyncRunNowMethod;
    private final Method entityRunMethod;
    private final Method entityRunDelayedMethod;
    private final Method taskCancelMethod;
    private final Method taskIsCancelledMethod;

    public FoliaSchedulerBridge(JavaPlugin plugin) {
        if (plugin == null) {
            throw new IllegalArgumentException("Plugin cannot be null");
        }
        this.plugin = plugin;
        try {
            Class<?> globalSchedulerClass = Class.forName("io.papermc.paper.threadedregions.scheduler.GlobalRegionScheduler");
            Class<?> regionSchedulerClass = Class.forName("io.papermc.paper.threadedregions.scheduler.RegionScheduler");
            Class<?> asyncSchedulerClass = Class.forName("io.papermc.paper.threadedregions.scheduler.AsyncScheduler");
            Class<?> entitySchedulerClass = Class.forName("io.papermc.paper.threadedregions.scheduler.EntityScheduler");
            Class<?> scheduledTaskClass = Class.forName("io.papermc.paper.threadedregions.scheduler.ScheduledTask");

            taskCancelMethod = scheduledTaskClass.getMethod("cancel");
            taskIsCancelledMethod = scheduledTaskClass.getMethod("isCancelled");

            globalRunMethod = globalSchedulerClass.getMethod("run", Plugin.class, Consumer.class);
            globalRunDelayedMethod = globalSchedulerClass.getMethod("runDelayed", Plugin.class, Consumer.class, long.class);

            regionRunMethod = regionSchedulerClass.getMethod("run", Plugin.class, Location.class, Consumer.class);
            regionRunDelayedMethod = regionSchedulerClass.getMethod("runDelayed", Plugin.class, Location.class, Consumer.class, long.class);

            asyncRunNowMethod = asyncSchedulerClass.getMethod("runNow", Plugin.class, Consumer.class);

            entityRunMethod = entitySchedulerClass.getMethod("run", Plugin.class, Consumer.class, Runnable.class);
            entityRunDelayedMethod = entitySchedulerClass.getMethod("runDelayed", Plugin.class, Consumer.class, Runnable.class, long.class);

            Method getGlobalSchedulerMethod = Bukkit.class.getMethod("getGlobalRegionScheduler");
            globalScheduler = getGlobalSchedulerMethod.invoke(null);

            Method getRegionSchedulerMethod = Bukkit.class.getMethod("getRegionScheduler");
            regionScheduler = getRegionSchedulerMethod.invoke(null);

            Method getAsyncSchedulerMethod = Bukkit.class.getMethod("getAsyncScheduler");
            asyncScheduler = getAsyncSchedulerMethod.invoke(null);
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize Folia schedulers via reflection", e);
        }
    }

    private Object getEntityScheduler(Entity entity) {
        try {
            Method method = Entity.class.getMethod("getScheduler");
            return method.invoke(entity);
        } catch (Exception e) {
            throw new RuntimeException("Failed to get entity scheduler", e);
        }
    }

    static RuntimeException schedulerFailure(String message, Exception exception) {
        if (exception instanceof InvocationTargetException) {
            Throwable cause = ((InvocationTargetException) exception).getCause();
            if (cause instanceof RuntimeException) {
                return (RuntimeException) cause;
            }
            if (cause instanceof Error) {
                throw (Error) cause;
            }
            return new RuntimeException(message, cause);
        }
        return new RuntimeException(message, exception);
    }

    private TaskHandle executeOnScheduler(Object scheduler, Method method, Object... args) {
        if (scheduler == null) {
            throw new IllegalStateException("Scheduler not available");
        }
        try {
            Object task = method.invoke(scheduler, args);
            if (task == null) {
                return null;
            }
            return new FoliaTaskHandle(task, taskIsCancelledMethod, taskCancelMethod);
        } catch (Exception e) {
            throw schedulerFailure("Failed to execute scheduler method", e);
        }
    }

    @Override
    public TaskHandle runGlobal(JavaPlugin plugin, Runnable task) {
        if (plugin == null) {
            throw new IllegalArgumentException("Plugin cannot be null");
        }
        if (task == null) {
            throw new IllegalArgumentException("Task cannot be null");
        }
        return executeOnScheduler(globalScheduler, globalRunMethod, plugin, wrappedTask(task));
    }

    @Override
    public TaskHandle runGlobalLater(JavaPlugin plugin, Runnable task, long delay) {
        if (plugin == null) {
            throw new IllegalArgumentException("Plugin cannot be null");
        }
        if (task == null) {
            throw new IllegalArgumentException("Task cannot be null");
        }
        if (delay < 0) {
            throw new IllegalArgumentException("Delay cannot be negative");
        }
        return executeOnScheduler(globalScheduler, globalRunDelayedMethod, plugin, wrappedTask(task), delay);
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
        Object scheduler = getEntityScheduler(entity);
        return executeOnScheduler(scheduler, entityRunMethod, plugin, wrappedEntityRunnable(entity, runnable), retireCallback);
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
        Object scheduler = getEntityScheduler(entity);
        return executeOnScheduler(scheduler, entityRunDelayedMethod, plugin,
                wrappedEntityRunnable(entity, runnable), retireCallback, delay);
    }

    private Consumer<Object> wrappedTask(Runnable task) {
        return ignored -> task.run();
    }

    private Consumer<Object> wrappedEntityRunnable(Entity entity, Runnable runnable) {
        return ignored -> {
            if (!entity.isValid()) {
                return;
            }
            runnable.run();
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
        return executeOnScheduler(regionScheduler, regionRunMethod, plugin, location, wrappedTask(task));
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
        return executeOnScheduler(regionScheduler, regionRunDelayedMethod, plugin, location, wrappedTask(task), delay);
    }

    @Override
    public TaskHandle runAsync(JavaPlugin plugin, Runnable task) {
        if (plugin == null) {
            throw new IllegalArgumentException("Plugin cannot be null");
        }
        if (task == null) {
            throw new IllegalArgumentException("Task cannot be null");
        }
        return executeOnScheduler(asyncScheduler, asyncRunNowMethod, plugin, wrappedTask(task));
    }

    @Override
    public void cancelAll(JavaPlugin plugin) {
        if (plugin == null) {
            throw new IllegalArgumentException("Plugin cannot be null");
        }
        try {
            Method cancelAllMethod = asyncScheduler.getClass().getMethod("cancelTasks", Plugin.class);
            cancelAllMethod.invoke(asyncScheduler, plugin);
            Method cancelAllGlobalMethod = globalScheduler.getClass().getMethod("cancelTasks", Plugin.class);
            cancelAllGlobalMethod.invoke(globalScheduler, plugin);
        } catch (Exception e) {
            throw schedulerFailure("Failed to cancel Folia tasks", e);
        }
    }

    @Override
    public boolean isFolia() {
        return true;
    }
}
