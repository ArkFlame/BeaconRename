package com.arkflame.flameforge.forge;

import com.arkflame.flameforge.hook.EconomyService;
import com.arkflame.flameforge.model.TierRequirements;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CostServiceTest {
    private FakeEconomy economy;
    private CostService service;

    @BeforeEach
    void setUp() {
        economy = new FakeEconomy();
        service = new CostService(mock(JavaPlugin.class), economy);
    }

    @Test
    void quoteAndChargeCoverXpMoneyAndCombinedModes() {
        Player xpPlayer = player(10);
        TierRequirements xp = requirements(TierRequirements.Combine.ALL, true, 4, false, 0);
        assertTrue(service.quote(xpPlayer, xp).isAffordable());
        ChargeReceipt xpReceipt = service.charge(xpPlayer, xp, Collections.emptyList());
        assertTrue(xpReceipt.isSuccess());
        assertEquals(4, xpReceipt.getXpCharged());

        Player moneyPlayer = player(0);
        economy.balance = new BigDecimal("12.00");
        TierRequirements money = requirements(TierRequirements.Combine.ALL, false, 0, true, 5);
        assertTrue(service.quote(moneyPlayer, money).isAffordable());
        ChargeReceipt moneyReceipt = service.charge(moneyPlayer, money, Collections.emptyList());
        assertTrue(moneyReceipt.isSuccess());
        assertEquals(new BigDecimal("5.00"), moneyReceipt.getMoneyCharged());

        Player combinedPlayer = player(10);
        economy.balance = new BigDecimal("12.00");
        TierRequirements combined = requirements(TierRequirements.Combine.ALL, true, 4, true, 5);
        assertTrue(service.quote(combinedPlayer, combined).isAffordable());
        ChargeReceipt combinedReceipt = service.charge(combinedPlayer, combined, Collections.emptyList());
        assertTrue(combinedReceipt.isSuccess());
        assertEquals(4, combinedReceipt.getXpCharged());
        assertEquals(new BigDecimal("5.00"), combinedReceipt.getMoneyCharged());

        Player anyPlayer = player(0);
        economy.balance = new BigDecimal("12.00");
        TierRequirements any = requirements(TierRequirements.Combine.ANY, true, 4, true, 5);
        assertTrue(service.quote(anyPlayer, any).isAffordable());
        ChargeReceipt anyReceipt = service.charge(anyPlayer, any, Collections.emptyList());
        assertTrue(anyReceipt.isSuccess());
        assertEquals(0, anyReceipt.getXpCharged());
        assertEquals(new BigDecimal("5.00"), anyReceipt.getMoneyCharged());
    }

    @Test
    void failedCombinedChargeRollsBackPartialMutation() {
        Player player = player(10);
        economy.balance = new BigDecimal("20.00");
        economy.withdrawSucceeds = false;

        ChargeReceipt receipt = service.charge(player,
            requirements(TierRequirements.Combine.ALL, true, 4, true, 5), Collections.emptyList());

        assertFalse(receipt.isSuccess());
        assertEquals(10, player.getLevel());
        assertEquals(new BigDecimal("20.00"), economy.balance);
        assertEquals(0, economy.depositCalls);
    }

    @Test
    void refundRestoresOnlyActuallyChargedResources() {
        Player player = player(0);
        economy.balance = new BigDecimal("10.00");
        TierRequirements requirements = requirements(TierRequirements.Combine.ANY, true, 4, true, 3);

        ChargeReceipt receipt = service.charge(player, requirements, Collections.emptyList());
        assertTrue(receipt.isSuccess());
        assertEquals(0, receipt.getXpCharged());
        assertEquals(new BigDecimal("3.00"), receipt.getMoneyCharged());

        service.refund(player, receipt);

        assertEquals(0, player.getLevel());
        assertEquals(new BigDecimal("10.00"), economy.balance);
        assertEquals(1, economy.depositCalls);
        assertTrue(receipt.isRefunded());
    }

    private static TierRequirements requirements(TierRequirements.Combine combine,
                                                 boolean xpEnabled, int xp,
                                                 boolean moneyEnabled, int money) {
        return new TierRequirements(combine,
            new TierRequirements.XpRequirement(xpEnabled, xp),
            new TierRequirements.MoneyRequirement(moneyEnabled, BigDecimal.valueOf(money)),
            new TierRequirements.ItemsRequirement(false, Collections.emptyList()));
    }

    private static Player player(int level) {
        AtomicInteger currentLevel = new AtomicInteger(level);
        PlayerInventory inventory = mock(PlayerInventory.class);
        when(inventory.getContents()).thenReturn(new org.bukkit.inventory.ItemStack[0]);
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(player.hasPermission(anyString())).thenReturn(false);
        when(player.getLevel()).thenAnswer(invocation -> currentLevel.get());
        doAnswer(invocation -> {
            currentLevel.set(invocation.getArgument(0, Integer.class));
            return null;
        }).when(player).setLevel(anyInt());
        when(player.getInventory()).thenReturn(inventory);
        return player;
    }

    private static final class FakeEconomy implements EconomyService {
        private BigDecimal balance = BigDecimal.ZERO;
        private boolean withdrawSucceeds = true;
        private int depositCalls;

        @Override public boolean available() { return true; }
        @Override public BigDecimal balance(OfflinePlayer player) { return balance; }
        @Override public boolean withdraw(OfflinePlayer player, BigDecimal amount) {
            if (!withdrawSucceeds || balance.compareTo(amount) < 0) return false;
            balance = balance.subtract(amount);
            return true;
        }
        @Override public boolean deposit(OfflinePlayer player, BigDecimal amount) {
            depositCalls++;
            balance = balance.add(amount);
            return true;
        }
        @Override public String format(BigDecimal amount) { return amount.toPlainString(); }
    }
}
