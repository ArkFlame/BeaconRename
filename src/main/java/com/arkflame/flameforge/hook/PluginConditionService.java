package com.arkflame.flameforge.hook;

import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;

import java.util.Objects;

public final class PluginConditionService {

    private final PluginManager pluginManager;

    public PluginConditionService(PluginManager pluginManager) {
        this.pluginManager = Objects.requireNonNull(pluginManager);
    }

    public boolean isPluginEnabled(String pluginName) {
        if (pluginName == null || pluginName.isEmpty()) {
            return false;
        }
        final Plugin plugin = pluginManager.getPlugin(pluginName);
        return plugin != null && plugin.isEnabled();
    }

    public boolean isVaultEnabled() {
        return isPluginEnabled("Vault");
    }
}
