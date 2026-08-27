package com.arkflame.flameforge.effect;

import com.arkflame.flameforge.compat.effect.ParticleBridge;
import com.arkflame.flameforge.compat.effect.particle.pattern.ParticleNetworkRenderer;
import com.arkflame.flameforge.compat.effect.particle.ParticleBatch;
import com.arkflame.flameforge.compat.effect.particle.ParticleRequest;
import com.arkflame.flameforge.compat.effect.particle.ParticleColor;
import com.arkflame.flameforge.compat.effect.particle.pattern.ParticlePattern;
import com.arkflame.flameforge.compat.effect.particle.pattern.ParticlePatternBuilder;
import com.arkflame.flameforge.compat.effect.particle.pattern.ParticlePoint;
import com.arkflame.flameforge.compat.effect.particle.style.ParticleStyle;
import com.arkflame.flameforge.compat.effect.SoundResolver;
import com.arkflame.flameforge.compat.scheduler.SchedulerBridge;
import com.arkflame.flameforge.compat.scheduler.TaskHandle;
import com.arkflame.flameforge.model.AnimationStep;
import com.arkflame.flameforge.model.ForgeAnimationProfile;
import com.arkflame.flameforge.model.ForgeOutcomeCategory;
import com.arkflame.flameforge.model.ForgeVariant;
import com.arkflame.flameforge.text.TextBridge;
import com.arkflame.flameforge.text.TextRenderer;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.logging.Level;

public final class ForgeAnimationService {
    private static final int MAX_TRACKED_TRANSACTIONS = 1000;
    private static final double FORGING_CENTER_Y = 2.00;
    private static final double ITEM_PATH_BASE_Y = 1.15;
    private static final double ITEM_PATH_RISE = 0.85;
    private static final double ITEM_PATH_BOB_AMPLITUDE = 0.06;
    private static final double ITEM_PATH_RADIUS_START = 0.24;
    private static final double ITEM_PATH_RADIUS_END = 0.16;
    private static final double ITEM_PATH_ROTATIONS = 6.0;
    private static final double ITEM_PATH_YAW_DEGREES = 1080.0;
    private static final double SPIRAL_RADIUS = 0.42;
    private static final double SPIRAL_RADIUS_END = 0.28;
    private static final double SPIRAL_HALF_HEIGHT = 0.45;
    private static final int SPIRAL_SAMPLES_PER_STRAND = 6;
    private static final int ITEM_TRAIL_POINTS = 4;
    private static final int AURA_SAMPLES = 4;
    private static final double AURA_ORBIT_RADIUS = 0.35;
    private static final double AURA_VERTICAL_OFFSET = 0.12;
    private static final double CONNECTOR_FORGE_TOP_Y = 1.05;
    private static final int REVEAL_DURATION_TICKS = 10;
    private static final int REVEAL_FEEDBACK_TICK = 5;
    private static final int REVEAL_STAR_VERTICES = 10;
    private static final double REVEAL_STAR_OUTER_RADIUS = 0.75;
    private static final double REVEAL_STAR_INNER_RADIUS = 0.32;
    private static final double REVEAL_HALO_OUTER_RADIUS = 0.45;
    private static final int REVEAL_EDGE_SAMPLES = 3;
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
    private final ParticleNetworkRenderer networkRenderer;
    private final SoundResolver sounds;
    private final TextBridge text;
    private final TextRenderer textRenderer;
    private final ForgeItemVisualService itemVisuals;
    private final ForgeAnimationThemeResolver themeResolver;

    private final Map<String, AnimationHandle> handles = new ConcurrentHashMap<>();
    private final Map<String, TaskHandle> scheduledTasks = new ConcurrentHashMap<>();

    public ForgeAnimationService(JavaPlugin plugin, SchedulerBridge scheduler,
                                  ParticleBridge particles, SoundResolver sounds, TextBridge text,
                                  TextRenderer textRenderer, ForgeItemVisualService itemVisuals,
                                  ForgeAnimationThemeResolver themeResolver) {
        this(plugin, scheduler, particles, sounds, text, textRenderer, itemVisuals,
            themeResolver, null);
    }

    public ForgeAnimationService(JavaPlugin plugin, SchedulerBridge scheduler,
                                 ParticleBridge particles, SoundResolver sounds, TextBridge text,
                                 TextRenderer textRenderer, ForgeItemVisualService itemVisuals,
                                 ForgeAnimationThemeResolver themeResolver,
                                 ParticleNetworkRenderer networkRenderer) {
        this.plugin = plugin;
        this.scheduler = scheduler;
        this.particles = particles;
        this.networkRenderer = networkRenderer;
        this.sounds = sounds;
        this.text = text;
        this.textRenderer = textRenderer;
        this.itemVisuals = itemVisuals;
        this.themeResolver = Objects.requireNonNull(themeResolver, "themeResolver");
    }

    public ForgeAnimationService(JavaPlugin plugin, SchedulerBridge scheduler,
                                 ParticleBridge particles, SoundResolver sounds, TextBridge text,
                                 TextRenderer textRenderer, ForgeItemVisualService itemVisuals) {
        this(plugin, scheduler, particles, sounds, text, textRenderer, itemVisuals,
            new ForgeAnimationThemeResolver(), null);
    }

    public AnimationHandle playAnimation(String transactionId, Player owner, Location stationLocation,
                                         ItemStack visualItem, ForgeAnimationProfile profile,
                                         Consumer<String> completionCallback,
                                         Consumer<String> failureCallback) {
        ForgeVariant legacyVariant = new ForgeVariant("legacy-animation", "", Collections.emptyList(),
            0.0, null, Collections.emptyList(), Collections.emptyList(), Collections.emptyList(), Collections.emptyList());
        return playAnimation(transactionId, owner, stationLocation, visualItem, profile,
            ForgeOutcomeCategory.SUCCESS, legacyVariant, completionCallback, failureCallback);
    }

    public AnimationHandle playAnimation(String transactionId, Player owner, Location stationLocation,
                                         ItemStack visualItem, ForgeAnimationProfile profile,
                                         ForgeOutcomeCategory outcomeCategory, ForgeVariant usedVariant,
                                         Consumer<String> completionCallback,
                                         Consumer<String> failureCallback) {
        if (transactionId == null || owner == null || stationLocation == null || profile == null
            || visualItem == null || outcomeCategory == null) {
            throw new IllegalArgumentException("transactionId, owner, stationLocation, visualItem, profile, and outcomeCategory must not be null");
        }
        if (outcomeCategory == ForgeOutcomeCategory.SUCCESS && usedVariant == null) {
            throw new IllegalArgumentException("usedVariant must not be null for SUCCESS");
        }

        ForgeAnimationTheme theme = themeResolver.resolve(outcomeCategory, usedVariant);

        ItemStack visualItemClone = visualItem.clone();

        AnimationHandle handle = new AnimationHandle(transactionId);
        AnimationHandle existing = handles.put(transactionId, handle);
        if (existing != null) {
            existing.cancel();
            if (itemVisuals != null) {
                itemVisuals.destroy(transactionId);
            }
        }

        boundEviction();

        int durationTicks = profile.getDurationTicks();
        int intervalTicks = profile.getIntervalTicks();
        if (durationTicks <= 0 || intervalTicks <= 0) {
            handle.fail();
            handles.remove(transactionId);
            if (itemVisuals != null) {
                itemVisuals.destroy(transactionId);
            }
            safeInvokeCallback(failureCallback, transactionId);
            return handle;
        }

        List<Integer> tickSequence = buildTickSequence(durationTicks, intervalTicks);
        if (tickSequence.isEmpty()) {
            handle.fail();
            handles.remove(transactionId);
            if (itemVisuals != null) {
                itemVisuals.destroy(transactionId);
            }
            safeInvokeCallback(failureCallback, transactionId);
            return handle;
        }

        List<TaskHandle> admittedTasks = new ArrayList<>();
        boolean admissionFailed = false;

        for (int tick : tickSequence) {
            final int tickSnapshot = tick;
            TaskHandle taskHandle = scheduleTick(tickSnapshot, owner, stationLocation, visualItemClone, profile,
                theme, handle, transactionId);
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
            if (itemVisuals != null) {
                itemVisuals.destroy(transactionId);
            }
            safeInvokeCallback(failureCallback, transactionId);
            return handle;
        }

        for (int revealTick = 1; revealTick <= REVEAL_DURATION_TICKS; revealTick++) {
            TaskHandle revealTask = scheduleRevealTick(revealTick, owner, stationLocation, profile, theme,
                outcomeCategory, handle, transactionId);
            if (revealTask == null) {
                admissionFailed = true;
                break;
            }
            admittedTasks.add(revealTask);
        }
        if (admissionFailed) {
            for (TaskHandle task : admittedTasks) {
                task.cancel();
            }
            handle.fail();
            handles.remove(transactionId);
            cancelAllTasks(transactionId);
            if (itemVisuals != null) {
                itemVisuals.destroy(transactionId);
            }
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
        }, durationTicks + REVEAL_DURATION_TICKS);

        if (completionTaskHolder[0] == null) {
            for (TaskHandle task : admittedTasks) {
                task.cancel();
            }
            handle.fail();
            handles.remove(transactionId);
            cancelAllTasks(transactionId);
            if (itemVisuals != null) {
                itemVisuals.destroy(transactionId);
            }
            safeInvokeCallback(failureCallback, transactionId);
            return handle;
        }

        if (!handle.isTerminal()) {
            scheduledTasks.put(transactionId + "_completion", completionTaskHolder[0]);
        } else {
            completionTaskHolder[0].cancel();
        }

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
                                    ItemStack visualItemClone, ForgeAnimationProfile profile,
                                    ForgeAnimationTheme theme, AnimationHandle handle, String transactionId) {
        ForgeAnimationProfile.ChargeSound chargeSound = profile.getChargeSound();
        ForgeAnimationProfile.ChargeParticle chargeParticle = profile.getChargeParticle();

        int durationTicks = profile.getDurationTicks();
        int intervalTicks = profile.getIntervalTicks();
        List<Integer> tickSequence = buildTickSequence(durationTicks, intervalTicks);
        int stepIndex = tickSequence.indexOf(tick);
        int totalSteps = tickSequence.size();
        float progress = totalSteps > 1 ? (float) stepIndex / (totalSteps - 1) : 0f;

        TaskHandle visualTask = scheduler.runEntityLater(owner, () -> {
            if (handle.isTerminal()) {
                return;
            }
            Location itemLoc = computeForgingItemLocation(stationLocation, progress);
            executeForgingParticleStep(owner, stationLocation, chargeParticle, progress, itemLoc, theme);
            if (tick == durationTicks) {
                executeFinalBurst(owner, stationLocation);
            }
            executeFakeItemStep(owner, itemLoc, visualItemClone, tick, transactionId);
        }, () -> {
            scheduledTasks.remove(transactionId + "_visual_" + tick);
        }, tick);
        if (visualTask == null) return null;
        scheduledTasks.put(transactionId + "_visual_" + tick, visualTask);

        if (chargeSound != null) {
            float interpolatedPitch = interpolatePitch(chargeSound, progress);
            TaskHandle task = scheduler.runEntityLater(owner, () -> {
                if (!handle.isTerminal()) {
                    executeChargeSoundStep(owner, chargeSound, interpolatedPitch);
                }
            }, () -> {
                scheduledTasks.remove(transactionId + "_sound_" + tick);
            }, tick);
            if (task == null) return null;
            scheduledTasks.put(transactionId + "_sound_" + tick, task);
        }

        return new BasicTaskHandle();
    }

    private TaskHandle scheduleRevealTick(int revealTick, Player owner, Location stationLocation,
                                          ForgeAnimationProfile profile, ForgeAnimationTheme theme,
                                          ForgeOutcomeCategory outcomeCategory, AnimationHandle handle,
                                          String transactionId) {
        final int delay = profile.getDurationTicks() + revealTick;
        TaskHandle task = scheduler.runEntityLater(owner, () -> {
            if (handle.isTerminal()) {
                return;
            }
            if (revealTick == 1) {
                holdFakeItemAtFinalPosition(owner, stationLocation, transactionId);
            }
            executeRevealStep(owner, stationLocation, profile, theme, outcomeCategory, revealTick);
        }, () -> scheduledTasks.remove(transactionId + "_reveal_" + revealTick), delay);
        if (task == null) {
            return null;
        }
        scheduledTasks.put(transactionId + "_reveal_" + revealTick, task);
        return new BasicTaskHandle();
    }

    private float interpolatePitch(ForgeAnimationProfile.ChargeSound chargeSound, float progress) {
        float startPitch = chargeSound.getStartPitch().floatValue();
        float endPitch = chargeSound.getEndPitch().floatValue();
        return startPitch + (endPitch - startPitch) * progress;
    }

    private void executeForgingParticleStep(Player owner, Location stationLocation,
                                             ForgeAnimationProfile.ChargeParticle chargeParticle,
                                             float progress, Location itemLoc, ForgeAnimationTheme theme) {
        String particleKey;
        if (chargeParticle != null && chargeParticle.getCandidates() != null && !chargeParticle.getCandidates().isEmpty()) {
            particleKey = chargeParticle.getCandidates().get(0);
        } else {
            particleKey = "flame";
        }
        List<ParticleRequest> requests = new ArrayList<>();
        World world = stationLocation.getWorld();
        addPatternRequests(requests, particleKey, buildSpiralPattern(stationLocation, progress), 2, world);
        addPatternRequests(requests, particleKey, buildTrailPattern(stationLocation, progress), 1, world);
        addPatternRequests(requests, particleKey, buildConnectorPattern(stationLocation, itemLoc), 1, world);
        addPatternRequests(requests, Collections.singletonList("DUST"),
            buildAuraPattern(stationLocation, itemLoc), 1,
            new ParticleRequest.Color(new ParticleColor(theme.getAuraRed(), theme.getAuraGreen(),
                theme.getAuraBlue()), 1.0f), world);
        addPointRequest(requests, itemLoc, theme.getStyle().getCandidates(), 1, new ParticleRequest.None());
        sendBatchSafe(owner, requests);
    }

    private void executeRevealStep(Player owner, Location stationLocation, ForgeAnimationProfile profile,
                                   ForgeAnimationTheme theme, ForgeOutcomeCategory category, int revealTick) {
        ParticleStyle style = theme.getStyle();
        List<ParticleRequest> requests = new ArrayList<>();
        ParticlePattern star = ParticlePatternBuilder.star(point(stationLocation, FORGING_CENTER_Y),
            REVEAL_STAR_OUTER_RADIUS, REVEAL_STAR_INNER_RADIUS, REVEAL_STAR_VERTICES);
        World world = stationLocation.getWorld();
        addPatternRequests(requests, Collections.singletonList("DUST"), star, 1,
            new ParticleRequest.Color(new ParticleColor(theme.getStarRed(), theme.getStarGreen(),
                theme.getStarBlue()), 1.0f), world);
        addPatternRequests(requests, style.getCandidates(), star, 1, world);
        if (revealTick == REVEAL_FEEDBACK_TICK) {
            ParticlePattern halo = ParticlePatternBuilder.circle(point(stationLocation, FORGING_CENTER_Y),
                REVEAL_HALO_OUTER_RADIUS, REVEAL_STAR_VERTICES * REVEAL_EDGE_SAMPLES);
            addPatternRequests(requests, Collections.singletonList("DUST"), halo, 1,
                new ParticleRequest.Color(new ParticleColor(theme.getStarRed(), theme.getStarGreen(),
                    theme.getStarBlue()), 1.0f), world);
        }
        sendBatchSafe(owner, requests);
        if (revealTick == REVEAL_FEEDBACK_TICK) {
            executeOutcomeFeedbackStep(owner, feedbackFor(profile, category));
        }
    }

    private void holdFakeItemAtFinalPosition(Player owner, Location stationLocation, String transactionId) {
        if (itemVisuals == null) {
            return;
        }
        try {
            itemVisuals.move(transactionId, computeForgingItemLocation(stationLocation, 1.0f));
        } catch (RuntimeException | LinkageError e) {
            plugin.getLogger().log(Level.WARNING,
                "Forge item hold failed for transaction " + transactionId, e);
        }
    }

    private ForgeAnimationProfile.OutcomeFeedback feedbackFor(ForgeAnimationProfile profile,
                                                               ForgeOutcomeCategory category) {
        switch (category) {
            case BREAK:
                return profile.getBreakFeedback();
            case CURSE:
                return profile.getCurseFeedback();
            case SUCCESS:
            default:
                return profile.getSuccessFeedback();
        }
    }

    private void executeFinalBurst(Player owner, Location stationLocation) {
        Location center = stationLocation.clone().add(0, FORGING_CENTER_Y, 0);
        List<ParticleRequest> requests = new ArrayList<>();
        addPointRequest(requests, center, Collections.singletonList("crit"), 4, new ParticleRequest.None());
        addPointRequest(requests, center, Collections.singletonList("flame"), 8, new ParticleRequest.None());
        sendBatchSafe(owner, requests);
    }

    private void sendBatchSafe(Player owner, List<ParticleRequest> requests) {
        if (particles == null || requests.isEmpty()) {
            return;
        }
        try {
            particles.sendBatch(owner, new ParticleBatch(requests));
        } catch (RuntimeException | LinkageError e) {
            plugin.getLogger().log(Level.WARNING,
                "Forge particle batch failed for owner " + owner.getUniqueId(), e);
        }
    }

    private void addPatternRequests(List<ParticleRequest> requests, String particleKey,
                                    ParticlePattern pattern, int count, World world) {
        addPatternRequests(requests, Collections.singletonList(particleKey), pattern, count,
            new ParticleRequest.None(), world);
    }

    private void addPatternRequests(List<ParticleRequest> requests, List<String> candidates,
                                    ParticlePattern pattern, int count, World world) {
        addPatternRequests(requests, candidates, pattern, count, new ParticleRequest.None(), world);
    }

    private void addPatternRequests(List<ParticleRequest> requests, List<String> candidates,
                                    ParticlePattern pattern, int count, ParticleRequest.Payload payload,
                                    World world) {
        for (ParticlePoint point : pattern.getPoints()) {
            addPointRequest(requests, point, candidates, count, payload, world);
        }
    }

    private void addPointRequest(List<ParticleRequest> requests, Location location,
                                  List<String> candidates, int count, ParticleRequest.Payload payload) {
        addPointRequest(requests, point(location), candidates, count, payload,
            location.getWorld());
    }

    private void addPointRequest(List<ParticleRequest> requests, ParticlePoint point,
                                  List<String> candidates, int count, ParticleRequest.Payload payload,
                                  World world) {
        requests.add(new ParticleRequest(new ParticleRequest.ParticlePosition(
            world, point.x(), point.y(), point.z()), candidates, count,
            0, 0, 0, 0, payload));
    }

    private ParticlePoint point(Location location) {
        return new ParticlePoint(location.getX(), location.getY(), location.getZ());
    }

    private ParticlePoint point(Location location, double yOffset) {
        return new ParticlePoint(location.getX(), location.getY() + yOffset, location.getZ());
    }

    private ParticlePattern buildSpiralPattern(Location station, float progress) {
        Location item = computeForgingItemLocation(station, progress);
        double p = clamp01(progress);
        double radius = SPIRAL_RADIUS + (SPIRAL_RADIUS_END - SPIRAL_RADIUS) * p;
        ParticlePattern helix = ParticlePatternBuilder.helix(
            new ParticlePoint(item.getX(), item.getY() - SPIRAL_HALF_HEIGHT, item.getZ()),
            radius, 2 * SPIRAL_HALF_HEIGHT, 1, SPIRAL_SAMPLES_PER_STRAND);
        double animationAngle = ITEM_PATH_ROTATIONS * Math.PI * p;
        List<ParticlePoint> rotated = new ArrayList<>(helix.size());
        for (int index = 0; index < helix.size(); index++) {
            ParticlePoint point = helix.getPoints().get(index);
            double x = point.x() - item.getX();
            double z = point.z() - item.getZ();
            int sample = index % SPIRAL_SAMPLES_PER_STRAND;
            double sampleProgress = sample / (double) (SPIRAL_SAMPLES_PER_STRAND - 1);
            rotated.add(new ParticlePoint(item.getX() + x * Math.cos(animationAngle) - z * Math.sin(animationAngle),
                item.getY() - SPIRAL_HALF_HEIGHT + 2 * SPIRAL_HALF_HEIGHT
                    * (sampleProgress * sampleProgress * (3.0 - 2.0 * sampleProgress)),
                item.getZ() + x * Math.sin(animationAngle) + z * Math.cos(animationAngle)));
        }
        return new ParticlePattern(rotated);
    }

    private ParticlePattern buildTrailPattern(Location station, float progress) {
        Location item = computeForgingItemLocation(station, progress);
        double angle = ITEM_PATH_ROTATIONS * Math.PI * clamp01(progress);
        double endDistance = 0.08 * ITEM_TRAIL_POINTS;
        double endDescent = endDistance * 0.25;
        double endHorizontal = Math.sqrt(endDistance * endDistance - endDescent * endDescent);
        return ParticlePatternBuilder.line(
            new ParticlePoint(item.getX() + Math.sin(angle) * 0.08, item.getY() - 0.02,
                item.getZ() - Math.cos(angle) * Math.sqrt(0.08 * 0.08 - 0.02 * 0.02)),
            new ParticlePoint(item.getX() + Math.sin(angle) * endHorizontal, item.getY() - endDescent,
                item.getZ() - Math.cos(angle) * endHorizontal), ITEM_TRAIL_POINTS);
    }

    private ParticlePattern buildConnectorPattern(Location station, Location item) {
        ParticlePattern line = ParticlePatternBuilder.line(point(station.clone().add(0, CONNECTOR_FORGE_TOP_Y, 0)),
            point(item), 5);
        return new ParticlePattern(line.getPoints().subList(1, 4));
    }

    private ParticlePattern buildAuraPattern(Location station, Location item) {
        ParticlePattern circle = ParticlePatternBuilder.circle(point(item), AURA_ORBIT_RADIUS, AURA_SAMPLES);
        List<ParticlePoint> points = new ArrayList<>(circle.size());
        for (int index = 0; index < circle.size(); index++) {
            ParticlePoint source = circle.getPoints().get(index);
            points.add(new ParticlePoint(source.x(), source.y()
                + (index % 2 == 0 ? -AURA_VERTICAL_OFFSET : AURA_VERTICAL_OFFSET), source.z()));
        }
        return new ParticlePattern(points);
    }

    private void executeFakeItemStep(Player owner, Location itemLoc, ItemStack item,
                                     int tick, String transactionId) {
        if (itemVisuals == null) {
            return;
        }
        try {
            if (tick == 0) {
                if (itemVisuals.spawn(transactionId, owner, item, itemLoc)) {
                    scheduleMetadataRefresh(owner, transactionId);
                }
            } else {
                itemVisuals.move(transactionId, itemLoc);
            }
        } catch (RuntimeException | LinkageError e) {
            plugin.getLogger().log(Level.WARNING,
                "Forge item visual failed for transaction " + transactionId, e);
            try {
                itemVisuals.destroy(transactionId);
            } catch (RuntimeException | LinkageError destroyFailure) {
                plugin.getLogger().log(Level.WARNING,
                    "Forge item visual cleanup failed for transaction " + transactionId, destroyFailure);
            }
        }
    }

    private void scheduleMetadataRefresh(Player owner, String transactionId) {
        final String taskKey = transactionId + "_metadata";
        final AtomicBoolean callbackRan = new AtomicBoolean(false);
        try {
            TaskHandle task = scheduler.runEntityLater(owner, () -> {
                try {
                    itemVisuals.refreshMetadata(transactionId);
                } catch (RuntimeException | LinkageError e) {
                    plugin.getLogger().log(Level.WARNING,
                        "Forge item metadata refresh failed for transaction " + transactionId, e);
                    try {
                        itemVisuals.destroy(transactionId);
                    } catch (RuntimeException | LinkageError destroyFailure) {
                        plugin.getLogger().log(Level.WARNING,
                            "Forge item metadata cleanup failed for transaction " + transactionId, destroyFailure);
                    }
                } finally {
                    callbackRan.set(true);
                    scheduledTasks.remove(taskKey);
                }
            }, () -> {
                callbackRan.set(true);
                scheduledTasks.remove(taskKey);
            }, 1);
            if (task != null && !callbackRan.get()) {
                scheduledTasks.put(taskKey, task);
            }
        } catch (RuntimeException | LinkageError e) {
            plugin.getLogger().log(Level.WARNING,
                "Forge item metadata refresh scheduling failed for transaction " + transactionId, e);
        }
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
            if (itemVisuals != null) {
                itemVisuals.destroy(transactionId);
            }
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
                plugin.getLogger().log(Level.SEVERE,
                    "Forge animation callback failed for transaction " + transactionId, e);
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
        if (itemVisuals != null) {
            itemVisuals.destroy(transactionId);
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

    static Location computeForgingItemLocation(Location station, float progress) {
        double baseX = station.getX();
        double baseY = station.getY();
        double baseZ = station.getZ();
        double p = clamp01(progress);
        double smooth = p * p * (3.0 - 2.0 * p);
        double angle = ITEM_PATH_ROTATIONS * Math.PI * p;
        double radius = ITEM_PATH_RADIUS_START + (ITEM_PATH_RADIUS_END - ITEM_PATH_RADIUS_START) * p;
        return new Location(station.getWorld(),
            baseX + Math.cos(angle) * radius,
            baseY + ITEM_PATH_BASE_Y + ITEM_PATH_RISE * smooth + ITEM_PATH_BOB_AMPLITUDE * Math.sin(angle),
            baseZ + Math.sin(angle) * radius,
            (float) (ITEM_PATH_YAW_DEGREES * p), 0f);
    }

    private static double clamp01(double progress) {
        return Math.max(0.0, Math.min(1.0, progress));
    }

    public void shutdown() {
        List<Map.Entry<String, AnimationHandle>> handleSnapshot = new ArrayList<>(handles.entrySet());
        for (Map.Entry<String, AnimationHandle> entry : handleSnapshot) {
            AnimationHandle handle = entry.getValue();
            if (handle != null && !handle.isTerminal()) {
                handle.cancel();
            }
        }
        for (TaskHandle task : scheduledTasks.values()) {
            if (task != null) {
                task.cancel();
            }
        }
        scheduledTasks.clear();
        handles.clear();
        if (itemVisuals != null) {
            itemVisuals.destroyAll();
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
