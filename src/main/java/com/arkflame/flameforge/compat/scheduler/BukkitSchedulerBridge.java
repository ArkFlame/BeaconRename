package com.arkflame.flameforge.compat.scheduler;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

class BukkitTaskHandle implements TaskHandle {
    private volatile boolean cancelled = false;
    private final BukkitTask task;

    BukkitTaskHandle(BukkitTask task) {
        this.task = task;
    }

    @Override
    public void cancel() {
        if (!cancelled) {
            cancelled = true;
            task.cancel();
        }
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }
}

public class BukkitSchedulerBridge implements SchedulerBridge {
    @Override
    public TaskHandle runGlobal(JavaPlugin plugin, Runnable task) {
        if (task == null) {
            throw new IllegalArgumentException("Task cannot be null");
        }
        if (plugin == null) {
            throw new IllegalArgumentException("Plugin cannot be null");
        }
        BukkitTask bukkitTask = Bukkit.getScheduler().runTask(plugin, task);
        return new BukkitTaskHandle(bukkitTask);
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
        BukkitTask bukkitTask = Bukkit.getScheduler().runTaskLater(plugin, task, delay);
        return new BukkitTaskHandle(bukkitTask);
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
        BukkitTask bukkitTask = Bukkit.getScheduler().runTask(
                JavaPlugin.getProvidingPlugin(BukkitSchedulerBridge.class), runnable);
        return new BukkitTaskHandle(bukkitTask);
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
        BukkitTask bukkitTask = Bukkit.getScheduler().runTaskLater(
                JavaPlugin.getProvidingPlugin(BukkitSchedulerBridge.class), runnable, delay);
        return new BukkitTaskHandle(bukkitTask);
    }

    @Override
    public TaskHandle runRegion(Location location, Runnable task) {
        if (location == null) {
            throw new IllegalArgumentException("Location cannot be null");
        }
        if (task == null) {
            throw new IllegalArgumentException("Task cannot be null");
        }
        BukkitTask bukkitTask = Bukkit.getScheduler().runTask(
                JavaPlugin.getProvidingPlugin(BukkitSchedulerBridge.class), task);
        return new BukkitTaskHandle(bukkitTask);
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
        BukkitTask bukkitTask = Bukkit.getScheduler().runTaskLater(
                JavaPlugin.getProvidingPlugin(BukkitSchedulerBridge.class), task, delay);
        return new BukkitTaskHandle(bukkitTask);
    }

    @Override
    public TaskHandle runAsync(JavaPlugin plugin, Runnable task) {
        if (task == null) {
            throw new IllegalArgumentException("Task cannot be null");
        }
        if (plugin == null) {
            throw new IllegalArgumentException("Plugin cannot be null");
        }
        BukkitTask bukkitTask = Bukkit.getScheduler().runTaskAsynchronously(plugin, task);
        return new BukkitTaskHandle(bukkitTask);
    }

    @Override
    public void cancelAll(JavaPlugin plugin) {
        if (plugin == null) {
            throw new IllegalArgumentException("Plugin cannot be null");
        }
        Bukkit.getScheduler().cancelTasks(plugin);
    }

    @Override
    public boolean isFolia() {
        return false;
    }
}
