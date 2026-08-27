package com.arkflame.flameforge.compat.effect;

import com.arkflame.flameforge.compat.effect.particle.ParticleBatch;
import com.arkflame.flameforge.compat.effect.particle.ParticleCatalog;
import com.arkflame.flameforge.compat.effect.particle.ParticleCapabilities;
import com.arkflame.flameforge.compat.effect.particle.ParticleColor;
import com.arkflame.flameforge.compat.effect.particle.ParticleProvider;
import com.arkflame.flameforge.compat.effect.particle.ParticleProviderFactory;
import com.arkflame.flameforge.compat.effect.particle.ParticleRequest;
import com.arkflame.flameforge.compat.scheduler.SchedulerBridge;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public final class ParticleBridge {
    private final SchedulerBridge scheduler;
    private final ParticleProvider provider;
    private final ParticleCatalog catalog;

    public static ParticleBridge create(SchedulerBridge scheduler) {
        ParticleCatalog catalog = new ParticleCatalog();
        return new ParticleBridge(scheduler, ParticleProviderFactory.create(catalog), catalog);
    }

    public ParticleBridge(SchedulerBridge scheduler, ParticleProvider provider, ParticleCatalog catalog) {
        if (scheduler == null || provider == null || catalog == null) {
            throw new IllegalArgumentException("Particle bridge dependencies cannot be null");
        }
        this.scheduler = scheduler;
        this.provider = provider;
        this.catalog = catalog;
    }

    public ParticleCapabilities getCapabilities() { return provider.getCapabilities(); }
    public boolean isModernAvailable() { return provider.getCapabilities().isReflective(); }

    public boolean sendBatch(final Player viewer, final ParticleBatch batch) {
        if (viewer == null || batch == null) return false;
        try {
            scheduler.runEntity(viewer, new Runnable() {
                @Override public void run() { emitSafely(viewer, batch); }
            }, new Runnable() { @Override public void run() { } });
            return true;
        } catch (RuntimeException | LinkageError ignored) {
            return false;
        }
    }

    public boolean sendBatchLater(final Player viewer, final ParticleBatch batch, long delay) {
        if (viewer == null || batch == null || delay < 0) return false;
        try {
            scheduler.runEntityLater(viewer, new Runnable() {
                @Override public void run() { emitSafely(viewer, batch); }
            }, new Runnable() { @Override public void run() { } }, delay);
            return true;
        } catch (RuntimeException | LinkageError ignored) {
            return false;
        }
    }

    public void sendToPlayer(Player player, String particleKey, Location location,
                             float offsetX, float offsetY, float offsetZ, float speed, int count) {
        if (player == null || particleKey == null || location == null) return;
        ParticleRequest request = request(location, Collections.singletonList(particleKey), count,
            offsetX, offsetY, offsetZ, speed, new ParticleRequest.None());
        sendBatch(player, ParticleBatch.single(request));
    }

    public void sendToPlayer(Player player, String particleKey, String... candidates) {
        if (player == null || particleKey == null) return;
        Location location = player.getLocation();
        if (location == null) return;
        List<String> values = candidates == null || candidates.length == 0
            ? Collections.singletonList(particleKey)
            : Arrays.asList(candidates);
        java.util.ArrayList<String> ordered = new java.util.ArrayList<String>(values.size() + 1);
        ordered.add(particleKey);
        ordered.addAll(values);
        sendBatch(player, ParticleBatch.single(request(location, ordered, 1, 0, 0, 0, 0,
            new ParticleRequest.None())));
    }

    public void sendColoredDust(Player player, Location location, int red, int green, int blue,
                                float size, int count) {
        if (player == null || location == null) return;
        ParticleRequest.Color payload = new ParticleRequest.Color(new ParticleColor(red, green, blue), size);
        sendBatch(player, ParticleBatch.single(request(location, Collections.singletonList("DUST"), count,
            0, 0, 0, 0, payload)));
    }

    public void sendBlockBreak(Player player, Location location, Material material, int count) {
        if (player == null || location == null || material == null) return;
        ParticleRequest.Block payload = new ParticleRequest.Block(material, (byte) 0);
        sendBatch(player, ParticleBatch.single(request(location,
            Arrays.asList("BLOCK", "BLOCK_CRACK", "BLOCK_DUST"), count, 0, 0, 0, 0, payload)));
    }

    public void sendFirstAvailable(Player player, Location location, List<String> candidates,
                                   float offsetX, float offsetY, float offsetZ,
                                   float speed, int count) {
        if (player == null || location == null || candidates == null || candidates.isEmpty()) return;
        sendBatch(player, ParticleBatch.single(request(location, candidates, count,
            offsetX, offsetY, offsetZ, speed, new ParticleRequest.None())));
    }

    public Map<String, String> getAvailableParticles() { return catalog.getAliases(); }

    private void emitSafely(Player viewer, ParticleBatch batch) {
        try {
            provider.emit(viewer, batch);
        } catch (RuntimeException ignored) {
            // Particle effects are cosmetic; one provider failure must not kill scheduled work.
        } catch (LinkageError ignored) {
            // Keep optional Bukkit APIs isolated from the scheduler callback.
        }
    }

    private ParticleRequest request(Location location, List<String> candidates, int count,
                                    double offsetX, double offsetY, double offsetZ, double extra,
                                    ParticleRequest.Payload payload) {
        if (candidates.size() > 32) throw new IllegalArgumentException("Too many particle candidates");
        if (location.getWorld() == null) throw new IllegalArgumentException("Particle world cannot be null");
        World world = location.getWorld();
        ParticleRequest.ParticlePosition position = new ParticleRequest.ParticlePosition(
            world, location.getX(), location.getY(), location.getZ());
        return new ParticleRequest(position, candidates, count, offsetX, offsetY, offsetZ, extra, payload);
    }
}
