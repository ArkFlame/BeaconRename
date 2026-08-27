package com.arkflame.flameforge.compat.effect.particle;

import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class FallbackParticleProvider implements ParticleProvider {
    private final List<ParticleProvider> providers;
    private final ParticleCapabilities capabilities;
    private final Logger logger;
    private final Set<String> diagnostics = Collections.synchronizedSet(new LinkedHashSet<String>());
    private final AtomicInteger diagnosticCount = new AtomicInteger();

    public FallbackParticleProvider(List<ParticleProvider> providers, Logger logger) {
        this.providers = Collections.unmodifiableList(new ArrayList<ParticleProvider>(providers));
        this.logger = logger == null ? Logger.getLogger(FallbackParticleProvider.class.getName()) : logger;
        Set<String> names = new LinkedHashSet<String>();
        boolean reflective = false;
        for (ParticleProvider provider : providers) {
            names.addAll(provider.getCapabilities().getParticleNames());
            reflective = reflective || provider.getCapabilities().isReflective();
        }
        this.capabilities = new ParticleCapabilities("fallback", reflective, names);
    }

    @Override
    public ParticleCapabilities getCapabilities() { return capabilities; }

    @Override
    public boolean emit(Player viewer, ParticleRequest request) {
        for (ParticleProvider provider : providers) {
            try {
                if (provider.emit(viewer, request)) return true;
            } catch (RuntimeException e) {
                diagnose(provider, e);
            } catch (LinkageError e) {
                diagnose(provider, e);
            }
        }
        return false;
    }

    private void diagnose(ParticleProvider provider, Throwable failure) {
        String key = provider.getCapabilities().getProvider();
        if (diagnosticCount.get() >= 32 || !diagnostics.add(key)) return;
        diagnosticCount.incrementAndGet();
        logger.log(Level.FINE, "Particle provider unavailable: " + key, failure);
    }
}
