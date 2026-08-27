package com.arkflame.flameforge.compat.effect.particle.pattern;

import com.arkflame.flameforge.compat.effect.ParticleBridge;
import com.arkflame.flameforge.compat.effect.particle.ParticleBatch;
import com.arkflame.flameforge.compat.effect.particle.ParticleColor;
import com.arkflame.flameforge.compat.effect.particle.ParticleRequest;
import com.arkflame.flameforge.compat.scheduler.SchedulerBridge;
import com.arkflame.flameforge.compat.effect.particle.style.ParticleStyle;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class ParticleNetworkRenderer {
    public static final int MAX_NODES = 64;
    public static final int MAX_FRAMES = 20;
    public static final int DEFAULT_FRAMES = 5;
    private static final Runnable RETIRED = new Runnable() {
        @Override
        public void run() {
        }
    };

    public interface FrameSender {
        void send(Player viewer, List<ParticlePoint> points, ParticleStyle style,
                  List<String> preferredCandidates);
    }

    private final SchedulerBridge scheduler;
    private final FrameSender sender;
    private final ParticleBridge bridge;
    private final ParticlePatternBuilder patternBuilder;

    public ParticleNetworkRenderer(SchedulerBridge scheduler, ParticleBridge bridge) {
        if (scheduler == null || bridge == null) {
            throw new IllegalArgumentException("Scheduler and particle bridge are required");
        }
        this.scheduler = scheduler;
        this.sender = null;
        this.bridge = bridge;
        this.patternBuilder = null;
    }

    public ParticleNetworkRenderer(SchedulerBridge scheduler, ParticleBridge bridge,
                                   ParticlePatternBuilder patternBuilder) {
        if (scheduler == null || bridge == null) {
            throw new IllegalArgumentException("Scheduler and particle bridge are required");
        }
        if (patternBuilder == null) {
            throw new IllegalArgumentException("Particle pattern builder is required");
        }
        this.scheduler = scheduler;
        this.sender = null;
        this.bridge = bridge;
        this.patternBuilder = patternBuilder;
    }

    public ParticleNetworkRenderer(SchedulerBridge scheduler, FrameSender sender) {
        if (scheduler == null || sender == null) {
            throw new IllegalArgumentException("Scheduler and frame sender are required");
        }
        this.scheduler = scheduler;
        this.sender = sender;
        this.bridge = null;
        this.patternBuilder = null;
    }

    public void render(final Player viewer, List<Location> locations, final ParticleStyle style,
                       List<String> preferredCandidates, int interiorPoints, int frames,
                       double spacing) {
        if (viewer == null || style == null || locations == null || locations.size() < 2
            || locations.size() > MAX_NODES || interiorPoints < 0 || frames < 1
            || frames > MAX_FRAMES || Double.isNaN(spacing) || Double.isInfinite(spacing)
            || spacing < 0.0) {
            throw new IllegalArgumentException("Invalid particle network arguments");
        }
        final List<Location> snapshots = snapshotLocations(locations);
        final List<ParticlePoint> geometry = compileGeometry(snapshots, interiorPoints, spacing);
        final List<String> candidates = preferredCandidates == null || preferredCandidates.isEmpty()
            ? style.getCandidates() : immutableCandidates(preferredCandidates);
        final List<ParticlePoint> frozenGeometry = geometry;
        final List<String> frozenCandidates = candidates;
        if (bridge != null) {
            World world = snapshots.get(0).getWorld();
            for (int frame = 0; frame < frames; frame++) {
                ParticleBatch batch = createBatch(world, frozenGeometry, style, frozenCandidates);
                bridge.sendBatchLater(viewer, batch, frame * 2L);
            }
            return;
        }
        final FrameSender frameSender = sender;
        for (int frame = 0; frame < frames; frame++) {
            final long delay = frame * 2L;
            Runnable task = new Runnable() {
                @Override
                public void run() {
                    frameSender.send(viewer, frozenGeometry, style, frozenCandidates);
                }
            };
            if (delay == 0L) {
                scheduler.runEntity(viewer, task, RETIRED);
            } else {
                scheduler.runEntityLater(viewer, task, RETIRED, delay);
            }
        }
    }

    public void render(final Player viewer, List<Location> locations, final ParticleStyle style,
                       int interiorPoints, double spacing) {
        render(viewer, locations, style, style == null ? null : style.getCandidates(),
            interiorPoints, DEFAULT_FRAMES, spacing);
    }

    private static List<Location> snapshotLocations(List<Location> locations) {
        List<Location> result = new ArrayList<Location>(locations.size());
        World world = null;
        for (Location location : locations) {
            if (location == null) {
                throw new IllegalArgumentException("Network locations require one non-null world");
            }
            World locationWorld = location.getWorld();
            if (locationWorld == null) {
                throw new IllegalArgumentException("Network locations require one non-null world");
            }
            if (world == null) {
                world = locationWorld;
            } else if (!world.equals(locationWorld)) {
                throw new IllegalArgumentException("Network locations must share one world");
            }
            result.add(location.clone());
        }
        return Collections.unmodifiableList(result);
    }

    private static List<ParticlePoint> compileGeometry(List<Location> locations, int interiorPoints,
                                                       double spacing) {
        List<ParticlePoint> result = new ArrayList<ParticlePoint>();
        for (int index = 0; index < locations.size() - 1; index++) {
            Location start = locations.get(index);
            Location end = locations.get(index + 1);
            result.add(new ParticlePoint(start.getX(), start.getY(), start.getZ()));
            for (int point = 1; point <= interiorPoints; point++) {
                double ratio = point / (double) (interiorPoints + 1);
                result.add(new ParticlePoint(start.getX() + (end.getX() - start.getX()) * ratio,
                    start.getY() + (end.getY() - start.getY()) * ratio,
                    start.getZ() + (end.getZ() - start.getZ()) * ratio));
                if (result.size() > ParticlePattern.MAX_POINTS) {
                    throw new IllegalArgumentException("Network geometry exceeds 2048 points");
                }
            }
            if (index == locations.size() - 2) {
                result.add(new ParticlePoint(end.getX(), end.getY(), end.getZ()));
                if (result.size() > ParticlePattern.MAX_POINTS) {
                    throw new IllegalArgumentException("Network geometry exceeds 2048 points");
                }
            }
        }
        return Collections.unmodifiableList(result);
    }

    private static List<String> immutableCandidates(List<String> candidates) {
        List<String> copy = new ArrayList<String>(candidates.size());
        for (String candidate : candidates) {
            if (candidate == null || candidate.trim().isEmpty()) {
                throw new IllegalArgumentException("Particle candidates must be non-empty");
            }
            copy.add(candidate);
        }
        return Collections.unmodifiableList(copy);
    }

    private static ParticleBatch createBatch(World world, List<ParticlePoint> points,
                                             ParticleStyle style, List<String> candidates) {
        List<ParticleRequest> requests = new ArrayList<ParticleRequest>(points.size());
        ParticleRequest.Payload payload = new ParticleRequest.Color(
            new ParticleColor(style.getRed(), style.getGreen(), style.getBlue()), 1F);
        for (ParticlePoint point : points) {
            ParticleRequest.ParticlePosition position = new ParticleRequest.ParticlePosition(
                world, point.x(), point.y(), point.z());
            requests.add(new ParticleRequest(position, candidates, 1, 0D, 0D, 0D, 0D, payload));
        }
        return new ParticleBatch(requests);
    }
}
