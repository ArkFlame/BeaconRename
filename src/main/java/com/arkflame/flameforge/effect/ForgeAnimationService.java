package com.arkflame.flameforge.effect;

import com.arkflame.flameforge.compat.effect.ParticleBridge;
import com.arkflame.flameforge.compat.effect.SoundResolver;
import com.arkflame.flameforge.compat.scheduler.SchedulerBridge;
import com.arkflame.flameforge.compat.scheduler.TaskHandle;
import com.arkflame.flameforge.model.AnimationStep;
import com.arkflame.flameforge.model.ForgeAnimationProfile;
import com.arkflame.flameforge.text.TextBridge;
import com.arkflame.flameforge.text.TextRenderer;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.Collections;
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
    private static final String STEP_TYPE_POTION = "potion";
    private static final String STEP_TYPE_VELOCITY = "velocity";

    private final JavaPlugin plugin;
    private final SchedulerBridge scheduler;
    private final ParticleBridge particles;
    private final SoundResolver sounds;
    private final TextBridge text;
    private final TextRenderer textRenderer;

    private final Map<String, AnimationHandle> handles = new ConcurrentHashMap<>();
    private final Map<String, TaskHandle> scheduledTasks = new ConcurrentHashMap<>();

    public ForgeAnimationService(JavaPlugin plugin, SchedulerBridge scheduler,
                                 ParticleBridge particles, SoundResolver sounds, TextBridge text,
                                 TextRenderer textRenderer) {
        this.plugin = plugin;
        this.scheduler = scheduler;
        this.particles = particles;
        this.sounds = sounds;
        this.text = text;
        this.textRenderer = textRenderer;
    }

    public AnimationHandle playAnimation(String transactionId, Player owner, Location stationLocation,
                                        ForgeAnimationProfile profile,
                                        Consumer<String> completionCallback,
                                        Consumer<String> failureCallback) {
        if (transactionId == null || owner == null || profile == null) {
            throw new IllegalArgumentException("transactionId, owner, and profile must not be null");
        }

        AnimationHandle handle = new AnimationHandle(transactionId);
        AnimationHandle existing = handles.put(transactionId, handle);
        if (existing != null) {
            existing.cancel();
        }

        boundEviction();

        int durationTicks = profile.getDurationTicks();
        int intervalTicks = profile.getIntervalTicks();
        if (durationTicks <= 0 || intervalTicks <= 0) {
            handle.fail();
            handles.remove(transactionId);
            safeInvokeCallback(failureCallback, transactionId);
            return handle;
        }

        List<Integer> tickSequence = buildTickSequence(durationTicks, intervalTicks);
        if (tickSequence.isEmpty()) {
            handle.fail();
            handles.remove(transactionId);
            safeInvokeCallback(failureCallback, transactionId);
            return handle;
        }

        List<TaskHandle> admittedTasks = new ArrayList<>();
        boolean admissionFailed = false;

        for (int tick : tickSequence) {
            final int tickSnapshot = tick;
            TaskHandle taskHandle = scheduleTick(tickSnapshot, owner, stationLocation, profile, handle, transactionId);
            if (taskHandle == null) {
                admissionFailed = true;
                break;
            }
            admittedTasks.add(taskHandle);
        }

        if (admissionFailed) {
            for (TaskHandle task : admittedTasks) {
                task.cancel();
            }
            handle.fail();
            handles.remove(transactionId);
            scheduledTasks.entrySet().removeIf(entry -> {
                if (entry.getKey().startsWith(transactionId)) {
                    entry.getValue().cancel();
                    return true;
                }
                return false;
            });
            safeInvokeCallback(failureCallback, transactionId);
            return handle;
        }

        final TaskHandle[] completionTaskHolder = new TaskHandle[1];
        completionTaskHolder[0] = scheduler.runEntityLater(owner, () -> {
            if (handle.complete()) {
                cleanupAfterCompletion(transactionId, admittedTasks);
                safeInvokeCallback(completionCallback, transactionId);
            }
        }, () -> {
            cleanupTask(transactionId, completionTaskHolder[0]);
        }, durationTicks);

        if (completionTaskHolder[0] == null) {
            for (TaskHandle task : admittedTasks) {
                task.cancel();
            }
            handle.fail();
            handles.remove(transactionId);
            safeInvokeCallback(failureCallback, transactionId);
            return handle;
        }

        scheduledTasks.put(transactionId + "_completion", completionTaskHolder[0]);

        return handle;
    }

    private List<Integer> buildTickSequence(int durationTicks, int intervalTicks) {
        List<Integer> sequence = new ArrayList<>();
        for (int tick = 0; tick <= durationTicks; tick += intervalTicks) {
            sequence.add(tick);
        }
        if (!sequence.contains(durationTicks)) {
            sequence.add(durationTicks);
        }
        return sequence;
    }

    private TaskHandle scheduleTick(int tick, Player owner, Location stationLocation,
                                    ForgeAnimationProfile profile, AnimationHandle handle,
                                    String transactionId) {
        ForgeAnimationProfile.ChargeSound chargeSound = profile.getChargeSound();
        ForgeAnimationProfile.ChargeParticle chargeParticle = profile.getChargeParticle();
        ForgeAnimationProfile.ImpactParticle impactParticle = profile.getImpactParticle();

        int durationTicks = profile.getDurationTicks();
        int intervalTicks = profile.getIntervalTicks();
        List<Integer> tickSequence = buildTickSequence(durationTicks, intervalTicks);
        int stepIndex = tickSequence.indexOf(tick);
        int totalSteps = tickSequence.size();
        float progress = totalSteps > 1 ? (float) stepIndex / (totalSteps - 1) : 0f;

        if (chargeParticle != null && tick < durationTicks) {
            TaskHandle task = scheduler.runRegionLater(stationLocation, () -> {
                if (!handle.isTerminal()) {
                    executeChargeParticleStep(owner, stationLocation, chargeParticle, progress);
                }
            }, tick);
            if (task == null) return null;
            scheduledTasks.put(transactionId + "_particle_" + tick, task);
        }

        if (impactParticle != null && tick == durationTicks) {
            TaskHandle task = scheduler.runRegionLater(stationLocation, () -> {
                if (!handle.isTerminal()) {
                    executeImpactParticleStep(owner, stationLocation, impactParticle);
                }
            }, tick);
            if (task == null) return null;
            scheduledTasks.put(transactionId + "_impact_" + tick, task);
        }

        if (chargeSound != null) {
            float interpolatedPitch = interpolatePitch(chargeSound, progress);
            TaskHandle task = scheduler.runRegionLater(stationLocation, () -> {
                if (!handle.isTerminal()) {
                    executeChargeSoundStep(owner, chargeSound, interpolatedPitch);
                }
            }, tick);
            if (task == null) return null;
            scheduledTasks.put(transactionId + "_sound_" + tick, task);
        }

        return new BasicTaskHandle();
    }

    private float interpolatePitch(ForgeAnimationProfile.ChargeSound chargeSound, float progress) {
        float startPitch = chargeSound.getStartPitch().floatValue();
        float endPitch = chargeSound.getEndPitch().floatValue();
        return startPitch + (endPitch - startPitch) * progress;
    }

    private void executeChargeParticleStep(Player owner, Location stationLocation,
                                           ForgeAnimationProfile.ChargeParticle chargeParticle,
                                           float progress) {
        List<String> candidates = chargeParticle.getCandidates();
        if (candidates == null || candidates.isEmpty()) {
            particles.sendToPlayer(owner, "flame", stationLocation, 0.1f, 0.5f, 0.1f, 0.01f, 5);
            return;
        }
        String particleKey = candidates.get(0);
        float radius = chargeParticle.getRadius().floatValue();
        int count = chargeParticle.getCount();
        float yOffset = progress * radius;
        Location particleLoc = stationLocation.clone().add(0, yOffset, 0);
        particles.sendToPlayer(owner, particleKey, particleLoc, 0.1f, 0.1f, 0.1f, 0.01f, count);
    }

    private void executeImpactParticleStep(Player owner, Location stationLocation,
                                            ForgeAnimationProfile.ImpactParticle impactParticle) {
        List<String> candidates = impactParticle.getCandidates();
        if (candidates == null || candidates.isEmpty()) {
            particles.sendToPlayer(owner, "flame", stationLocation, 0.3f, 0.3f, 0.3f, 0.05f, 20);
            return;
        }
        String particleKey = candidates.get(0);
        int count = impactParticle.getCount();
        particles.sendToPlayer(owner, particleKey, stationLocation, 0.3f, 0.3f, 0.3f, 0.05f, count);
    }

    private void executeChargeSoundStep(Player owner, ForgeAnimationProfile.ChargeSound chargeSound,
                                         float interpolatedPitch) {
        List<String> candidates = chargeSound.getCandidates();
        String soundKey = (candidates != null && !candidates.isEmpty()) ?
            candidates.get(0) : "ui.button.click";
        float volume = chargeSound.getVolume().floatValue();
        sounds.playCosmetic(owner, soundKey, volume, interpolatedPitch);
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
                    Component component = textRenderer.renderToComponent(data);
                    text.send(owner, component);
                }
                break;
            case STEP_TYPE_TITLE:
                if (data != null) {
                    Component component = textRenderer.renderToComponent(data);
                    text.sendTitle(owner, component, Component.empty(), 10, 70, 20);
                }
                break;
            case STEP_TYPE_ACTIONBAR:
                if (data != null) {
                    Component component = textRenderer.renderToComponent(data);
                    text.sendActionBar(owner, component);
                }
                break;
        }
    }

    private void executeOutcomeFeedbackStep(Player owner, ForgeAnimationProfile.OutcomeFeedback feedback) {
        if (feedback == null) return;
        if (feedback.getSound() != null) {
            sounds.playCosmetic(owner, feedback.getSound());
        }
        if (feedback.getTitle() != null) {
            Component component = textRenderer.renderToComponent(feedback.getTitle());
            text.sendTitle(owner, component, Component.empty(), 10, 70, 20);
        }
        if (feedback.getActionBar() != null) {
            Component component = textRenderer.renderToComponent(feedback.getActionBar());
            text.sendActionBar(owner, component);
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

    private static final class BasicTaskHandle implements TaskHandle {
        private volatile boolean cancelled = false;

        @Override
        public void cancel() {
            cancelled = true;
        }

        @Override
        public boolean isCancelled() {
            return cancelled;
        }
    }
}
