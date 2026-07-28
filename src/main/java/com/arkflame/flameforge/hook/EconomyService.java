package com.arkflame.flameforge.hook;

import org.bukkit.OfflinePlayer;

import java.math.BigDecimal;

public interface EconomyService {

    boolean available();

    BigDecimal balance(OfflinePlayer player);

    boolean withdraw(OfflinePlayer player, BigDecimal amount);

    boolean deposit(OfflinePlayer player, BigDecimal amount);

    String format(BigDecimal amount);
}
