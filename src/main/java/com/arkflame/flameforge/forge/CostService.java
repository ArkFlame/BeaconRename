package com.arkflame.flameforge.forge;

import com.arkflame.flameforge.hook.EconomyService;
import com.arkflame.flameforge.model.CostMode;
import com.arkflame.flameforge.model.TierCost;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

public final class CostService {

    private static final String BYPASS_PERMISSION = "flameforge.bypass.cost";
    private static final int XP_SCALE = 6;
    private static final RoundingMode XP_ROUNDING = RoundingMode.HALF_UP;
    private static final int MONEY_SCALE = 2;
    private static final RoundingMode MONEY_ROUNDING = RoundingMode.HALF_UP;

    private final JavaPlugin plugin;
    private final EconomyService economyService;

    public CostService(JavaPlugin plugin, EconomyService economyService) {
        this.plugin = Objects.requireNonNull(plugin);
        this.economyService = Objects.requireNonNull(economyService);
    }

    public CostQuote quote(Player player, TierCost tierCost) {
        Objects.requireNonNull(player);
        Objects.requireNonNull(tierCost);

        if (hasBypassPermission(player)) {
            return CostQuote.zero();
        }

        final CostMode mode = tierCost.getMode();
        final BigDecimal xpCost = normalizeXp(tierCost.getXpCost());
        final BigDecimal moneyCost = normalizeMoney(tierCost.getMoneyCost());

        final boolean xpAffordable = checkXpAffordable(player, xpCost);
        final boolean moneyAffordable = checkMoneyAffordable(player, moneyCost);

        return CostQuote.of(mode, xpCost, moneyCost, xpAffordable, moneyAffordable);
    }

    public ChargeReceipt charge(Player player, TierCost tierCost) {
        Objects.requireNonNull(player);
        Objects.requireNonNull(tierCost);

        if (hasBypassPermission(player)) {
            return ChargeReceipt.zero();
        }

        final CostMode mode = tierCost.getMode();

        switch (mode) {
            case XP_ONLY:
                return chargeXpOnly(player, tierCost);
            case MONEY_ONLY:
                return chargeMoneyOnly(player, tierCost);
            case XP_AND_MONEY:
                return chargeXpAndMoney(player, tierCost);
            case XP_OR_MONEY:
                return chargeXpOrMoney(player, tierCost);
            default:
                return ChargeReceipt.failure(mode, "Unknown cost mode");
        }
    }

    public ChargeReceipt refund(Player player, ChargeReceipt receipt) {
        Objects.requireNonNull(player);
        Objects.requireNonNull(receipt);

        if (!receipt.isSuccess()) {
            return receipt;
        }

        final BigDecimal xpToRefund = receipt.getXpCharged();
        final BigDecimal moneyToRefund = receipt.getMoneyCharged();

        if (xpToRefund.compareTo(BigDecimal.ZERO) > 0) {
            refundXp(player, xpToRefund);
        }

        if (moneyToRefund.compareTo(BigDecimal.ZERO) > 0) {
            economyService.deposit(player, moneyToRefund);
        }

        return receipt;
    }

    private boolean hasBypassPermission(Player player) {
        return player.hasPermission(BYPASS_PERMISSION);
    }

    private boolean checkXpAffordable(Player player, BigDecimal xpCost) {
        if (xpCost == null || xpCost.compareTo(BigDecimal.ZERO) <= 0) {
            return true;
        }
        final int playerLevel = player.getLevel();
        final BigDecimal playerXp = levelToXp(playerLevel);
        return playerXp.compareTo(xpCost) >= 0;
    }

    private boolean checkMoneyAffordable(Player player, BigDecimal moneyCost) {
        if (moneyCost == null || moneyCost.compareTo(BigDecimal.ZERO) <= 0) {
            return true;
        }
        if (!economyService.available()) {
            return false;
        }
        final BigDecimal balance = economyService.balance(player);
        return balance.compareTo(moneyCost) >= 0;
    }

    private ChargeReceipt chargeXpOnly(Player player, TierCost tierCost) {
        final BigDecimal xpCost = normalizeXp(tierCost.getXpCost());
        if (!checkXpAffordable(player, xpCost)) {
            return ChargeReceipt.failure(CostMode.XP_ONLY, "Insufficient XP");
        }
        final boolean deducted = deductXp(player, xpCost);
        if (!deducted) {
            return ChargeReceipt.failure(CostMode.XP_ONLY, "XP deduction failed");
        }
        return ChargeReceipt.success(CostMode.XP_ONLY, xpCost, BigDecimal.ZERO);
    }

    private ChargeReceipt chargeMoneyOnly(Player player, TierCost tierCost) {
        final BigDecimal moneyCost = normalizeMoney(tierCost.getMoneyCost());
        if (!checkMoneyAffordable(player, moneyCost)) {
            return ChargeReceipt.failure(CostMode.MONEY_ONLY, "Insufficient funds");
        }
        final boolean withdrawn = economyService.withdraw(player, moneyCost);
        if (!withdrawn) {
            return ChargeReceipt.failure(CostMode.MONEY_ONLY, "Money withdrawal failed");
        }
        return ChargeReceipt.success(CostMode.MONEY_ONLY, BigDecimal.ZERO, moneyCost);
    }

    private ChargeReceipt chargeXpAndMoney(Player player, TierCost tierCost) {
        final BigDecimal xpCost = normalizeXp(tierCost.getXpCost());
        final BigDecimal moneyCost = normalizeMoney(tierCost.getMoneyCost());

        if (!checkXpAffordable(player, xpCost)) {
            return ChargeReceipt.failure(CostMode.XP_AND_MONEY, "Insufficient XP");
        }
        if (!checkMoneyAffordable(player, moneyCost)) {
            return ChargeReceipt.failure(CostMode.XP_AND_MONEY, "Insufficient funds");
        }

        final boolean moneyWithdrawn = economyService.withdraw(player, moneyCost);
        if (!moneyWithdrawn) {
            return ChargeReceipt.failure(CostMode.XP_AND_MONEY, "Money withdrawal failed");
        }

        final boolean xpDeducted = deductXp(player, xpCost);
        if (!xpDeducted) {
            economyService.deposit(player, moneyCost);
            return ChargeReceipt.failure(CostMode.XP_AND_MONEY, "XP deduction failed, money refunded");
        }

        return ChargeReceipt.success(CostMode.XP_AND_MONEY, xpCost, moneyCost);
    }

    private ChargeReceipt chargeXpOrMoney(Player player, TierCost tierCost) {
        final BigDecimal xpCost = normalizeXp(tierCost.getXpCost());
        final BigDecimal moneyCost = normalizeMoney(tierCost.getMoneyCost());

        final boolean xpAffordable = checkXpAffordable(player, xpCost);
        final boolean moneyAffordable = checkMoneyAffordable(player, moneyCost);

        if (xpAffordable && moneyAffordable) {
            return chargeXpFirst(player, xpCost, moneyCost);
        } else if (xpAffordable) {
            return chargeXpOnly(player, tierCost);
        } else if (moneyAffordable) {
            return chargeMoneyOnly(player, tierCost);
        } else {
            return ChargeReceipt.failure(CostMode.XP_OR_MONEY, "Neither XP nor money affordable");
        }
    }

    private ChargeReceipt chargeXpFirst(Player player, BigDecimal xpCost, BigDecimal moneyCost) {
        final boolean xpDeducted = deductXp(player, xpCost);
        if (!xpDeducted) {
            return ChargeReceipt.failure(CostMode.XP_OR_MONEY, "XP deduction failed");
        }

        final boolean moneyWithdrawn = economyService.withdraw(player, moneyCost);
        if (!moneyWithdrawn) {
            refundXp(player, xpCost);
            return ChargeReceipt.failure(CostMode.XP_OR_MONEY, "Money withdrawal failed, XP refunded");
        }

        return ChargeReceipt.success(CostMode.XP_OR_MONEY, xpCost, moneyCost);
    }

    private boolean deductXp(Player player, BigDecimal xpCost) {
        if (xpCost == null || xpCost.compareTo(BigDecimal.ZERO) <= 0) {
            return true;
        }
        final int currentLevel = player.getLevel();
        final BigDecimal currentXp = levelToXp(currentLevel);
        final BigDecimal newXp = currentXp.subtract(xpCost);
        final int newLevel = xpToLevel(newXp);
        player.setLevel(newLevel);
        return true;
    }

    private void refundXp(Player player, BigDecimal xpAmount) {
        if (xpAmount == null || xpAmount.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        final int currentLevel = player.getLevel();
        final BigDecimal currentXp = levelToXp(currentLevel);
        final BigDecimal newXp = currentXp.add(xpAmount);
        final int newLevel = xpToLevel(newXp);
        player.setLevel(newLevel);
    }

    private BigDecimal levelToXp(int level) {
        if (level <= 16) {
            return BigDecimal.valueOf(level).multiply(BigDecimal.valueOf(level)).add(BigDecimal.valueOf(level * 6));
        } else if (level <= 31) {
            return BigDecimal.valueOf((level * level * 2.5) - (40.5 * level) + 360).setScale(XP_SCALE, XP_ROUNDING);
        } else {
            return BigDecimal.valueOf((level * level * 4.5) - (162.5 * level) + 2220).setScale(XP_SCALE, XP_ROUNDING);
        }
    }

    private int xpToLevel(BigDecimal xp) {
        if (xp.compareTo(BigDecimal.valueOf(1395)) <= 0) {
            double d = Math.sqrt(xp.doubleValue() + 9);
            return (int) Math.floor(-1 + d) / 2;
        } else if (xp.compareTo(BigDecimal.valueOf(4345)) <= 0) {
            double x = xp.doubleValue();
            return (int) Math.floor(9.5 + Math.sqrt(0.25 * x - 179.25));
        } else {
            double x = xp.doubleValue();
            return (int) Math.floor(29.5 + Math.sqrt(0.125 * x - 1006.375));
        }
    }

    private BigDecimal normalizeXp(BigDecimal xp) {
        if (xp == null) {
            return BigDecimal.ZERO;
        }
        return xp.setScale(XP_SCALE, XP_ROUNDING);
    }

    private BigDecimal normalizeMoney(BigDecimal money) {
        if (money == null) {
            return BigDecimal.ZERO;
        }
        return money.setScale(MONEY_SCALE, MONEY_ROUNDING);
    }
}
