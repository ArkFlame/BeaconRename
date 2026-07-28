package com.arkflame.flameforge.effect;

import com.arkflame.flameforge.compat.effect.ParticleBridge;
import com.arkflame.flameforge.compat.effect.SoundResolver;
import com.arkflame.flameforge.compat.scheduler.SchedulerBridge;
import com.arkflame.flameforge.compat.scheduler.TaskHandle;
import com.arkflame.flameforge.model.AnimationStep;
import com.arkflame.flameforge.text.TextBridge;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public final class ForgeAnimationService {
    private static final int MAX_TRACKED_TRANSACTIONS = 1000;
    private static final String STEP_TYPE_PARTICLE = "particle";
    private static final String STEP_TYPE_SOUND = "sound";
    private static final String STEP_TYPE_TEXT = "text";
    private static final String STEP_TYPE_TITLE = "title";
    private static final String STEP_TYPE_ACTIONBAR = "actionbar";

    private final JavaPlugin plugin;
    private final SchedulerBridge scheduler;
    private final ParticleBridge particles;
    private final SoundResolver sounds;
    private final TextBridge text;

    private final Map<String, AnimationHandle> handles = new ConcurrentHashMap<>();
    private final Map<String, TaskHandle> scheduledTasks = new ConcurrentHashMap<>();

    public ForgeAnimationService(JavaPlugin plugin, SchedulerBridge scheduler,
                                 ParticleBridge particles, SoundResolver sounds, TextBridge text) {
        this.plugin = plugin;
        this.scheduler = scheduler;
        this.particles = particles;
        this.sounds = sounds;
        this.text = text;
    }

    public AnimationHandle playAnimation(String transactionId, Player owner, Location stationLocation,
                                        List<AnimationStep> steps, int totalDuration,
                                        Consumer<String> completionCallback) {
        if (transactionId == null || owner == null || steps == null || steps.isEmpty()) {
            throw new IllegalArgumentException("transactionId, owner, and steps must not be null or empty");
        }

        AnimationHandle handle = new AnimationHandle(transactionId);
        AnimationHandle existing = handles.put(transactionId, handle);
        if (existing != null) {
            existing.cancel();
        }

        boundEviction();

        List<TaskHandle> stepTasks = new ArrayList<>();
        int[] currentTick = {0};

        Iterator<AnimationStep> iterator = steps.iterator();
        while (iterator.hasNext()) {
            AnimationStep step = iterator.next();
            int stepTick = step.getDelay();

            if (stepTick < 0 || stepTick > totalDuration) {
                continue;
            }

            final int tickSnapshot = stepTick;
            final AnimationStep stepSnapshot = step;
            final boolean isLast = !iterator.hasNext();

            final TaskHandle[] taskHandleHolder = new TaskHandle[1];
            if (stepSnapshot.getType().equals(STEP_TYPE_PARTICLE)) {
                taskHandleHolder[0] = scheduler.runRegionLater(stationLocation, () -> {
                    if (!handle.isTerminal()) {
                        executeParticleStep(owner, stationLocation, stepSnapshot);
                    }
                }, tickSnapshot);
            } else if (stepSnapshot.getType().equals(STEP_TYPE_SOUND) ||
                       stepSnapshot.getType().equals(STEP_TYPE_TEXT) ||
                       stepSnapshot.getType().equals(STEP_TYPE_TITLE) ||
                       stepSnapshot.getType().equals(STEP_TYPE_ACTIONBAR)) {
                Entity entity = owner;
                taskHandleHolder[0] = scheduler.runEntityLater(entity, () -> {
                    if (!handle.isTerminal()) {
                        executePlayerFeedbackStep(owner, stepSnapshot);
                    }
                }, () -> {
                    if (!handle.isTerminal()) {
                        cleanupTask(transactionId, taskHandleHolder[0]);
                    }
                }, tickSnapshot);
            } else {
                continue;
            }

            stepTasks.add(taskHandleHolder[0]);
            scheduledTasks.put(transactionId + "_" + tickSnapshot, taskHandleHolder[0]);
            currentTick[0] = tickSnapshot;
        }

        final TaskHandle[] completionTaskHolder = new TaskHandle[1];
        completionTaskHolder[0] = scheduler.runEntityLater(owner, () -> {
            if (!handle.isTerminal()) {
                handle.complete();
                cleanupAfterCompletion(transactionId, stepTasks);
                safeInvokeCallback(completionCallback, transactionId);
            }
        }, () -> {
            cleanupTask(transactionId, completionTaskHolder[0]);
        }, totalDuration);

        scheduledTasks.put(transactionId + "_completion", completionTaskHolder[0]);
        stepTasks.add(completionTaskHolder[0]);

        return handle;
    }

    public void cancelAnimation(String transactionId, boolean completeNow,
                               Consumer<String> completionCallback) {
        AnimationHandle handle = handles.get(transactionId);
        if (handle == null) {
            return;
        }

        if (handle.cancel()) {
            cancelAllTasks(transactionId);
            if (completeNow) {
                cleanupAfterCompletion(transactionId, null);
                safeInvokeCallback(completionCallback, transactionId);
            }
        }
    }

    public AnimationHandle getHandle(String transactionId) {
        return handles.get(transactionId);
    }

    public boolean isRunning(String transactionId) {
        AnimationHandle handle = handles.get(transactionId);
        return handle != null && !handle.isTerminal();
    }

    private void executeParticleStep(Player owner, Location stationLocation, AnimationStep step) {
        String data = step.getData();
        if (data == null || data.isEmpty()) {
            particles.sendToPlayer(owner, "flame", stationLocation, 0.1f, 0.5f, 0.1f, 0.01f, 5);
        } else {
            String[] parts = data.split(",");
            String particleKey = parts[0];
            float offsetX = parts.length > 1 ? Float.parseFloat(parts[1].trim()) : 0.1f;
            float offsetY = parts.length > 2 ? Float.parseFloat(parts[2].trim()) : 0.5f;
            float offsetZ = parts.length > 3 ? Float.parseFloat(parts[3].trim()) : 0.1f;
            float speed = parts.length > 4 ? Float.parseFloat(parts[4].trim()) : 0.01f;
            int count = parts.length > 5 ? Integer.parseInt(parts[5].trim()) : 5;
            particles.sendToPlayer(owner, particleKey, stationLocation, offsetX, offsetY, offsetZ, speed, count);
        }
    }

    private void executePlayerFeedbackStep(Player owner, AnimationStep step) {
        String stepType = step.getType();
        String data = step.getData();

        switch (stepType) {
            case STEP_TYPE_SOUND:
                sounds.playCosmetic(owner, data != null ? data : "ui.button.click");
                break;
            case STEP_TYPE_TEXT:
                if (data != null) {
                    text.send(owner, text.parse(data));
                }
                break;
            case STEP_TYPE_TITLE:
                if (data != null) {
                    text.sendTitle(owner, data, "");
                }
                break;
            case STEP_TYPE_ACTIONBAR:
                if (data != null) {
                    text.sendActionBar(owner, data);
                }
                break;
        }
    }

    private void safeInvokeCallback(Consumer<String> callback, String transactionId) {
        if (callback != null) {
            try {
                callback.accept(transactionId);
            } catch (Exception e) {
                // no-op: callback failure must not propagate
            }
        }
    }

    private void cancelAllTasks(String transactionId) {
        scheduledTasks.entrySet().removeIf(entry -> {
            if (entry.getKey().startsWith(transactionId)) {
                entry.getValue().cancel();
                return true;
            }
            return false;
        });
    }

    private void cleanupTask(String transactionId, TaskHandle task) {
        scheduledTasks.remove(transactionId + "_" + System.identityHashCode(task));
    }

    private void cleanupAfterCompletion(String transactionId, List<TaskHandle> stepTasks) {
        handles.remove(transactionId);
        if (stepTasks != null) {
            for (TaskHandle task : stepTasks) {
                task.cancel();
            }
        }
        cancelAllTasks(transactionId);
    }

    private void cancelFeedbackStep(String transactionId, TaskHandle task) {
        if (!Thread.currentThread().isInterrupted()) {
            cleanupTask(transactionId, task);
        }
    }

    private void cancelCompletionStep(String transactionId, TaskHandle task) {
        if (!Thread.currentThread().isInterrupted()) {
            cleanupTask(transactionId, task);
        }
    }

    private void boundEviction() {
        if (handles.size() > MAX_TRACKED_TRANSACTIONS) {
            Iterator<String> iterator = handles.keySet().iterator();
            int toRemove = handles.size() - MAX_TRACKED_TRANSACTIONS;
            int removed = 0;
            while (iterator.hasNext() && removed < toRemove) {
                String key = iterator.next();
                AnimationHandle handle = handles.get(key);
                if (handle != null && handle.isTerminal()) {
                    iterator.remove();
                    removed++;
                }
            }
        }
    }
}
