package com.arkflame.flameforge.hologram;

import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

public final class HologramProviderSelector {
    private final Plugin hostPlugin;
    private final PluginManager pluginManager;
    private final HologramProviderFactory providerFactory;
    private final Logger logger;

    public HologramProviderSelector(Plugin hostPlugin, PluginManager pluginManager,
                                     HologramProviderFactory providerFactory, Logger logger) {
        this.hostPlugin = hostPlugin;
        this.pluginManager = pluginManager;
        this.providerFactory = providerFactory;
        this.logger = logger;
    }

    public HologramProvider select(HologramSettings settings) {
        if (!settings.isEnabled()) {
            String reason = "disabled by configuration";
            logger.info("Hologram provider: " + reason);
            return new NoOpHologramProvider(reason);
        }

        List<String> combinedReasons = new ArrayList<>();

        for (String providerName : settings.getProviderOrder()) {
            String trimmed = providerName.trim();
            if (!"FancyHolograms".equals(trimmed) && !"DecentHolograms".equals(trimmed)) {
                continue;
            }

            Plugin plugin = pluginManager.getPlugin(trimmed);
            if (plugin == null) {
                combinedReasons.add(trimmed + " not found");
                continue;
            }

            if (!plugin.isEnabled()) {
                combinedReasons.add(trimmed + " disabled (version " + plugin.getDescription().getVersion() + ")");
                continue;
            }

            try {
                HologramProvider provider = providerFactory.create(plugin, logger);
                if (provider.isAvailable()) {
                    logger.info("Hologram provider: " + provider.getName() + " v" + provider.getVersion());
                    return provider;
                } else {
                    combinedReasons.add(trimmed + " unavailable: " + provider.getUnavailableReason());
                }
            } catch (RuntimeException e) {
                combinedReasons.add(trimmed + ": " + e.getClass().getSimpleName() + ": " + e.getMessage());
            } catch (LinkageError e) {
                combinedReasons.add(trimmed + ": " + e.getClass().getSimpleName() + ": " + e.getMessage());
            }
        }

        StringBuilder reasonBuilder = new StringBuilder();
        for (int i = 0; i < combinedReasons.size(); i++) {
            if (i > 0) reasonBuilder.append("; ");
            reasonBuilder.append(combinedReasons.get(i));
        }
        String combinedReason = reasonBuilder.toString();
        logger.info("Hologram provider: no supported provider");
        return new NoOpHologramProvider(combinedReason.isEmpty() ? "no supported provider available" : combinedReason);
    }
}
