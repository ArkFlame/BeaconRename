package com.arkflame.flameforge.compat.effect.particle;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

public final class ParticleProviderFactory {
    private static final AtomicBoolean CAPABILITY_LOGGED = new AtomicBoolean(false);

    private ParticleProviderFactory() { }

    public static ParticleProvider create(ParticleCatalog catalog) {
        Logger logger = Logger.getLogger(ParticleProviderFactory.class.getName());
        List<ParticleProvider> providers = new ArrayList<ParticleProvider>();
        try {
            Class.forName("org.bukkit.Particle");
            providers.add(new ReflectiveBukkitParticleProvider(catalog, logger));
        } catch (RuntimeException e) {
            logger.fine("Reflective particle provider unavailable");
        } catch (LinkageError e) {
            logger.fine("Reflective particle provider unavailable");
        } catch (ClassNotFoundException e) {
            logger.fine("Reflective particle provider unavailable");
        }
        providers.add(new LegacyEffectParticleProvider(catalog, logger));
        ParticleProvider provider = new FallbackParticleProvider(providers, logger);
        if (CAPABILITY_LOGGED.compareAndSet(false, true)) {
            logger.info("Particle capabilities: provider=" + provider.getCapabilities().getProvider()
                + ", reflective=" + provider.getCapabilities().isReflective()
                + ", particles=" + provider.getCapabilities().getParticleNames().size());
        }
        return provider;
    }
}
