package com.arkflame.flameforge.hook;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.ServicesManager;

import java.util.Objects;

public final class EconomyServiceFactory {

    private final PluginConditionService pluginCondition;
    private final ServicesManager servicesManager;

    public EconomyServiceFactory(PluginConditionService pluginCondition, ServicesManager servicesManager) {
        this.pluginCondition = Objects.requireNonNull(pluginCondition);
        this.servicesManager = Objects.requireNonNull(servicesManager);
    }

    public EconomyService create() {
        if (!pluginCondition.isVaultEnabled()) {
            return new NoEconomyService();
        }
        final RegisteredServiceProvider<Economy> provider = servicesManager.getRegistration(Economy.class);
        if (provider == null) {
            return new NoEconomyService();
        }
        final Economy economy = provider.getProvider();
        if (economy == null) {
            return new NoEconomyService();
        }
        return new VaultEconomyService(economy, servicesManager);
    }
}
