package com.arkflame.flameforge.hook;

import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.ServicesManager;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

public final class VaultEconomyService implements EconomyService {

    private static final int MONEY_SCALE = 2;
    private static final RoundingMode MONEY_ROUNDING = RoundingMode.HALF_UP;

    private final Economy economy;
    private final ServicesManager servicesManager;

    public VaultEconomyService(Economy economy, ServicesManager servicesManager) {
        this.economy = Objects.requireNonNull(economy);
        this.servicesManager = Objects.requireNonNull(servicesManager);
    }

    @Override
    public boolean available() {
        return economy != null && economy.isEnabled();
    }

    @Override
    public BigDecimal balance(OfflinePlayer player) {
        if (!available() || player == null) {
            return BigDecimal.ZERO;
        }
        final double rawBalance = economy.getBalance(player);
        return BigDecimal.valueOf(rawBalance).setScale(MONEY_SCALE, MONEY_ROUNDING);
    }

    @Override
    public boolean withdraw(OfflinePlayer player, BigDecimal amount) {
        if (!available() || player == null || amount == null) {
            return false;
        }
        final double rawAmount = toVaultDouble(amount);
        final EconomyResponse response = economy.withdrawPlayer(player, rawAmount);
        return response.transactionSuccess();
    }

    @Override
    public boolean deposit(OfflinePlayer player, BigDecimal amount) {
        if (!available() || player == null || amount == null) {
            return false;
        }
        final double rawAmount = toVaultDouble(amount);
        final EconomyResponse response = economy.depositPlayer(player, rawAmount);
        return response.transactionSuccess();
    }

    @Override
    public String format(BigDecimal amount) {
        if (!available() || amount == null) {
            return "0.00";
        }
        return economy.format(toVaultDouble(amount));
    }

    private double toVaultDouble(BigDecimal amount) {
        return amount.setScale(MONEY_SCALE, MONEY_ROUNDING).doubleValue();
    }

    public void unregister() {
        if (servicesManager != null && economy != null) {
            servicesManager.unregister(Economy.class, economy);
        }
    }
}
