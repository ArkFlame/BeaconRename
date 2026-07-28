package com.arkflame.flameforge.compat.scheduler;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.java.JavaPlugin;

public interface SchedulerBridge {
    TaskHandle runGlobal(JavaPlugin plugin, Runnable task);
    TaskHandle runGlobalLater(JavaPlugin plugin, Runnable task, long delay);
    TaskHandle runEntity(Entity entity, Runnable runnable, Runnable retireCallback);
    TaskHandle runEntityLater(Entity entity, Runnable runnable, Runnable retireCallback, long delay);
    TaskHandle runRegion(Location location, Runnable task);
    TaskHandle runRegionLater(Location location, Runnable task, long delay);
    TaskHandle runAsync(JavaPlugin plugin, Runnable task);
    void cancelAll(JavaPlugin plugin);
    boolean isFolia();
}
