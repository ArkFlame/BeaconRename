package com.arkflame.flameforge.hologram;

import org.bukkit.plugin.Plugin;
import java.util.logging.Logger;

public interface HologramProviderFactory {
    HologramProvider create(Plugin providerPlugin, Logger logger);

    final class Default implements HologramProviderFactory {
        @Override
        public HologramProvider create(Plugin providerPlugin, Logger logger) {
            String name = providerPlugin.getName();
            if ("FancyHolograms".equals(name)) {
                return new FancyHologramsProvider(providerPlugin, logger);
            }
            if ("DecentHolograms".equals(name)) {
                return new DecentHologramsProvider(providerPlugin, logger);
            }
            return new NoOpHologramProvider("Unknown provider: " + name);
        }
    }
}
