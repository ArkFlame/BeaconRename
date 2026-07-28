package com.arkflame.flameforge.hook;

import org.bukkit.OfflinePlayer;

import java.math.BigDecimal;

public final class NoEconomyService implements EconomyService {

    @Override
    public boolean available() {
        return false;
    }

    @Override
    public BigDecimal balance(OfflinePlayer player) {
        return BigDecimal.ZERO;
    }

    @Override
    public boolean withdraw(OfflinePlayer player, BigDecimal amount) {
        return false;
    }

    @Override
    public boolean deposit(OfflinePlayer player, BigDecimal amount) {
        return false;
    }

    @Override
    public String format(BigDecimal amount) {
        return amount.toPlainString();
    }
}
