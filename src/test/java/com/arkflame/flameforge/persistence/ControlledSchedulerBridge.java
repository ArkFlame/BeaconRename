package com.arkflame.flameforge.persistence;

import com.arkflame.flameforge.compat.scheduler.SchedulerBridge;
import com.arkflame.flameforge.compat.scheduler.TaskHandle;
import com.arkflame.flameforge.testfakes.TaskHandleStub;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayDeque;
import java.util.Queue;

final class ControlledSchedulerBridge implements SchedulerBridge {
    private final Queue<Runnable> asyncTasks = new ArrayDeque<>();

    @Override
    public TaskHandle runGlobal(JavaPlugin plugin, Runnable task) {
        task.run();
        return TaskHandleStub.INSTANCE;
    }

    @Override
    public TaskHandle runGlobalLater(JavaPlugin plugin, Runnable task, long delay) {
        task.run();
        return TaskHandleStub.INSTANCE;
    }

    @Override
    public TaskHandle runEntity(Entity entity, Runnable runnable, Runnable retireCallback) {
        runnable.run();
        return TaskHandleStub.INSTANCE;
    }

    @Override
    public TaskHandle runEntityLater(Entity entity, Runnable runnable, Runnable retireCallback, long delay) {
        runnable.run();
        return TaskHandleStub.INSTANCE;
    }

    @Override
    public TaskHandle runRegion(Location location, Runnable task) {
        task.run();
        return TaskHandleStub.INSTANCE;
    }

    @Override
    public TaskHandle runRegionLater(Location location, Runnable task, long delay) {
        task.run();
        return TaskHandleStub.INSTANCE;
    }

    @Override
    public synchronized TaskHandle runAsync(JavaPlugin plugin, Runnable task) {
        asyncTasks.add(task);
        return TaskHandleStub.INSTANCE;
    }

    @Override
    public void cancelAll(JavaPlugin plugin) {
    }

    @Override
    public boolean isFolia() {
        return false;
    }

    synchronized void runNext() {
        Runnable task = asyncTasks.poll();
        if (task == null) {
            throw new IllegalStateException("No queued async task");
        }
        task.run();
    }

    synchronized Thread startNext() {
        Runnable task = asyncTasks.poll();
        if (task == null) {
            throw new IllegalStateException("No queued async task");
        }
        Thread thread = new Thread(task, "controlled-async-task");
        thread.start();
        return thread;
    }

    synchronized int queuedAsyncTasks() {
        return asyncTasks.size();
    }
}
