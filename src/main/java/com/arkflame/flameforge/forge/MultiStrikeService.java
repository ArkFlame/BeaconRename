package com.arkflame.flameforge.forge;

import com.arkflame.flameforge.compat.effect.ParticleBridge;
import com.arkflame.flameforge.compat.scheduler.SchedulerBridge;
import com.arkflame.flameforge.model.ForgePowerDefinition;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

public final class MultiStrikeService {
    private static final Runnable RETIRED = new Runnable() {
        @Override
        public void run() {
        }
    };

    private final SchedulerBridge scheduler;
    private final ParticleBridge particles;

    public interface StrikeAction {
        void apply(LivingEntity target);
    }

    public MultiStrikeService(SchedulerBridge scheduler, ParticleBridge particles) {
        if (scheduler == null || particles == null) {
            throw new IllegalArgumentException("Scheduler and particles are required");
        }
        this.scheduler = scheduler;
        this.particles = particles;
    }

    public void execute(final Player viewer, final LivingEntity initialTarget,
                        final ForgePowerDefinition power, final boolean playersOnly,
                        final StrikeAction action) {
        if (viewer == null || initialTarget == null || power == null || action == null) {
            return;
        }
        final Set<UUID> visited = new HashSet<>();
        visited.add(viewer.getUniqueId());
        final int maxTargets = power.getMaxTargets();
        final int targetLimit = maxTargets < 1 ? 1 : maxTargets;
        visited.add(initialTarget.getUniqueId());
        scheduleStrike(viewer, initialTarget, power, playersOnly, action, visited,
            new AtomicInteger(1), targetLimit, false);
    }

    private void scheduleStrike(final Player viewer, final LivingEntity target,
                                 final ForgePowerDefinition power, final boolean playersOnly,
                                 final StrikeAction action, final Set<UUID> visited,
                                 final AtomicInteger targetCount, final int targetLimit, boolean delayed) {
        long delay = delayed ? Math.max(0, power.getChainDelayTicks()) : 0L;
        if (delay == 0L) {
            scheduler.runEntity(target, new Runnable() {
                @Override
                public void run() {
                    strike(viewer, target, power, playersOnly, action, visited, targetCount, targetLimit);
                }
            }, RETIRED);
        } else {
            scheduler.runEntityLater(target, new Runnable() {
                @Override
                public void run() {
                    strike(viewer, target, power, playersOnly, action, visited, targetCount, targetLimit);
                }
            }, RETIRED, delay);
        }
    }

    private void strike(final Player viewer, final LivingEntity target,
                        final ForgePowerDefinition power, final boolean playersOnly,
                        final StrikeAction action, final Set<UUID> visited,
                        final AtomicInteger targetCount, final int targetLimit) {
        if (target.isDead()) {
            return;
        }
        Location targetLocation = snapshot(target.getLocation());
        if (targetLocation == null || targetLocation.getWorld() == null) {
            return;
        }
        action.apply(target);
        renderTrail(viewer, target, targetLocation, power);
        if (targetCount.get() >= targetLimit) {
            return;
        }
        discover(viewer, targetLocation, power, playersOnly, action, visited, targetCount, targetLimit);
    }

    private void discover(final Player viewer, final Location center,
                           final ForgePowerDefinition power, final boolean playersOnly,
                           final StrikeAction action, final Set<UUID> visited,
                           final AtomicInteger targetCount, final int targetLimit) {
        final World world = center.getWorld();
        if (world == null) {
            return;
        }
        scheduler.runRegion(center, new Runnable() {
            @Override
            public void run() {
                List<Candidate> candidates = new ArrayList<>();
                double radius = power.getRadius().doubleValue();
                for (Entity entity : world.getNearbyEntities(center, radius, radius, radius)) {
                    if (!(entity instanceof LivingEntity) || entity.getUniqueId() == null
                        || entity.getUniqueId().equals(viewer.getUniqueId())) {
                        continue;
                    }
                    if (playersOnly && !(entity instanceof Player)) {
                        continue;
                    }
                    Location location = snapshot(entity.getLocation());
                    if (location == null || location.getWorld() == null
                        || !location.getWorld().equals(center.getWorld())) {
                        continue;
                    }
                    double distanceSquared = distanceSquared(center, location);
                    candidates.add(new Candidate((LivingEntity) entity, distanceSquared,
                        entity.getEntityId(), entity.getUniqueId()));
                }
                Collections.sort(candidates, new Comparator<Candidate>() {
                    @Override
                    public int compare(Candidate left, Candidate right) {
                        int distance = Double.compare(left.distanceSquared, right.distanceSquared);
                        if (distance != 0) {
                            return distance;
                        }
                        int entityId = Integer.compare(left.entityId, right.entityId);
                        if (entityId != 0) {
                            return entityId;
                        }
                        return left.uuid.toString().compareTo(right.uuid.toString());
                    }
                });
                for (Candidate candidate : candidates) {
                    synchronized (visited) {
                        if (targetCount.get() >= targetLimit || !visited.add(candidate.uuid)) {
                            continue;
                        }
                        targetCount.incrementAndGet();
                    }
                    scheduleStrike(viewer, candidate.entity, power, playersOnly, action,
                        visited, targetCount, targetLimit, true);
                }
            }
        });
    }

    private void renderTrail(final Player viewer, final LivingEntity target,
                             final Location targetLocation, final ForgePowerDefinition power) {
        final Location targetPoint = snapshot(targetLocation);
        scheduler.runEntity(viewer, new Runnable() {
            @Override
            public void run() {
                Location viewerLocation = snapshot(viewer.getLocation());
                render(viewer, viewerLocation, targetPoint, power);
            }
        }, RETIRED);
        if (target instanceof Player && !target.getUniqueId().equals(viewer.getUniqueId())) {
            scheduler.runEntity(target, new Runnable() {
                @Override
                public void run() {
                    Location targetOwnerLocation = snapshot(target.getLocation());
                    render((Player) target, targetOwnerLocation, targetPoint, power);
                }
            }, RETIRED);
        }
    }

    private void render(Player owner, Location start, Location end, ForgePowerDefinition power) {
        if (owner == null || start == null || end == null || start.getWorld() == null
            || !start.getWorld().equals(end.getWorld())) {
            return;
        }
        int points = power.getTrailPoints();
        String particle = power.getParticleCandidates().isEmpty()
            ? "FLAME" : power.getParticleCandidates().get(0);
        for (int i = 1; i <= points; i++) {
            double ratio = (double) i / (double) (points + 1);
            Location point = start.clone().add(
                (end.getX() - start.getX()) * ratio,
                (end.getY() - start.getY()) * ratio,
                (end.getZ() - start.getZ()) * ratio);
            particles.sendToPlayer(owner, particle, point, 0F, 0F, 0F, 0F, 1);
        }
    }

    private static Location snapshot(Location location) {
        if (location == null) {
            return null;
        }
        Location copy = location.clone();
        return copy == null ? location : copy;
    }

    private static double distanceSquared(Location left, Location right) {
        double x = left.getX() - right.getX();
        double y = left.getY() - right.getY();
        double z = left.getZ() - right.getZ();
        return x * x + y * y + z * z;
    }

    private static final class Candidate {
        private final LivingEntity entity;
        private final double distanceSquared;
        private final int entityId;
        private final UUID uuid;

        private Candidate(LivingEntity entity, double distanceSquared, int entityId, UUID uuid) {
            this.entity = entity;
            this.distanceSquared = distanceSquared;
            this.entityId = entityId;
            this.uuid = uuid;
        }
    }
}
