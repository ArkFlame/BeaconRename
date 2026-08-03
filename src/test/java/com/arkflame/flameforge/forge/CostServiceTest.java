package com.arkflame.flameforge.forge;

import com.arkflame.flameforge.hook.EconomyService;
import com.arkflame.flameforge.model.TierRequirements;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CostServiceTest {

    private CostService costService;
    private FakeEconomyService economyService;
    private Player player;
    private JavaPlugin fakePlugin;

    @BeforeEach
    void setUp() {
        economyService = new FakeEconomyService();
        fakePlugin = mock(JavaPlugin.class);
        costService = new CostService(fakePlugin, economyService);
        player = createFakePlayer();
    }

    @Test
    void bypassProducesZeroQuoteAndZeroReceipt() {
        Player bypassPlayer = createFakePlayerWithBypass(true);
        TierRequirements requirements = new TierRequirements(
            TierRequirements.Combine.ALL,
            new TierRequirements.XpRequirement(true, 100),
            new TierRequirements.MoneyRequirement(false, BigDecimal.ZERO),
            new TierRequirements.ItemsRequirement(false, Collections.emptyList())
        );

        CostQuote quote = costService.quote(bypassPlayer, requirements);
        assertEquals(0, quote.getXpRequired());
        assertEquals(BigDecimal.ZERO, quote.getMoneyRequired());
        assertTrue(quote.isAffordable());

        ChargeReceipt receipt = costService.charge(bypassPlayer, requirements, Collections.singletonList(new org.bukkit.inventory.ItemStack(Material.AIR)));
        assertTrue(receipt.isSuccess());
        assertEquals(0, receipt.getXpCharged());
        assertEquals(BigDecimal.ZERO, receipt.getMoneyCharged());
    }

    @Test
    void quoteEvaluatesXpMoneyAndUnavailableEconomyAcrossAllModes() {
        Player poorPlayer = createFakePlayerWithLevel(1);
        Player richPlayer = createFakePlayerWithLevel(100);
        economyService.fakeBalance = new BigDecimal("10");
        economyService.available = false;

        TierRequirements xpOnlyReq = new TierRequirements(
            TierRequirements.Combine.ALL,
            new TierRequirements.XpRequirement(true, 100),
            new TierRequirements.MoneyRequirement(false, BigDecimal.ZERO),
            new TierRequirements.ItemsRequirement(false, Collections.emptyList())
        );
        TierRequirements moneyOnlyReq = new TierRequirements(
            TierRequirements.Combine.ALL,
            new TierRequirements.XpRequirement(false, 0),
            new TierRequirements.MoneyRequirement(true, new BigDecimal("100")),
            new TierRequirements.ItemsRequirement(false, Collections.emptyList())
        );
        TierRequirements xpAndMoneyReq = new TierRequirements(
            TierRequirements.Combine.ALL,
            new TierRequirements.XpRequirement(true, 100),
            new TierRequirements.MoneyRequirement(true, new BigDecimal("100")),
            new TierRequirements.ItemsRequirement(false, Collections.emptyList())
        );
        TierRequirements xpOrMoneyReq = new TierRequirements(
            TierRequirements.Combine.ANY,
            new TierRequirements.XpRequirement(true, 100),
            new TierRequirements.MoneyRequirement(true, new BigDecimal("100")),
            new TierRequirements.ItemsRequirement(false, Collections.emptyList())
        );

        CostQuote xpOnlyQuote = costService.quote(poorPlayer, xpOnlyReq);
        assertFalse(xpOnlyQuote.isAffordable());

        CostQuote moneyOnlyQuote = costService.quote(poorPlayer, moneyOnlyReq);
        assertFalse(moneyOnlyQuote.isAffordable());

        CostQuote xpAndMoneyQuote = costService.quote(richPlayer, xpAndMoneyReq);
        assertFalse(xpAndMoneyQuote.isAffordable());

        CostQuote xpOrMoneyQuote = costService.quote(poorPlayer, xpOrMoneyReq);
        assertFalse(xpOrMoneyQuote.isAffordable());
    }

    @Test
    void chargeXpOnlyAndMoneyOnlyDeductExactlyOnce() {
        Player richPlayer = createFakePlayerWithLevel(100);
        economyService.fakeBalance = new BigDecimal("500");

        TierRequirements xpCostReq = new TierRequirements(
            TierRequirements.Combine.ALL,
            new TierRequirements.XpRequirement(true, 100),
            new TierRequirements.MoneyRequirement(false, BigDecimal.ZERO),
            new TierRequirements.ItemsRequirement(false, Collections.emptyList())
        );
        ChargeReceipt xpReceipt = costService.charge(richPlayer, xpCostReq, Collections.singletonList(new org.bukkit.inventory.ItemStack(Material.AIR)));
        assertTrue(xpReceipt.isSuccess());
        assertTrue(xpReceipt.getXpCharged() > 0);
        assertEquals(BigDecimal.ZERO, xpReceipt.getMoneyCharged());

        TierRequirements moneyCostReq = new TierRequirements(
            TierRequirements.Combine.ALL,
            new TierRequirements.XpRequirement(false, 0),
            new TierRequirements.MoneyRequirement(true, new BigDecimal("100")),
            new TierRequirements.ItemsRequirement(false, Collections.emptyList())
        );
        ChargeReceipt moneyReceipt = costService.charge(richPlayer, moneyCostReq, Collections.singletonList(new org.bukkit.inventory.ItemStack(Material.AIR)));
        assertTrue(moneyReceipt.isSuccess());
        assertEquals(0, moneyReceipt.getXpCharged());
        assertTrue(moneyReceipt.getMoneyCharged().compareTo(BigDecimal.ZERO) > 0);
    }

    @Test
    void chargeXpAndMoneyRequiresBothAndRollsBackXpWhenWithdrawalFails() {
        MutableInt mutableLevel = new MutableInt(50);
        Player richPlayer = createFakePlayerWithMutableLevel(mutableLevel);
        economyService.fakeBalance = new BigDecimal("500");
        economyService.nextWithdrawSucceeds = false;

        TierRequirements tierCost = new TierRequirements(
            TierRequirements.Combine.ALL,
            new TierRequirements.XpRequirement(true, 100),
            new TierRequirements.MoneyRequirement(true, new BigDecimal("50")),
            new TierRequirements.ItemsRequirement(false, Collections.emptyList())
        );
        ChargeReceipt receipt = costService.charge(richPlayer, tierCost, Collections.singletonList(new org.bukkit.inventory.ItemStack(Material.AIR)));

        assertFalse(receipt.isSuccess());
        assertEquals(50, mutableLevel.value);
        assertEquals(new BigDecimal("500"), economyService.fakeBalance);
    }

    @Test
    void chargeXpOrMoneyPrefersXpThenFallsBackToMoney() {
        Player xpRichPlayer = createFakePlayerWithLevel(100);
        economyService.fakeBalance = new BigDecimal("500");
        TierRequirements xpOrMoney = new TierRequirements(
            TierRequirements.Combine.ANY,
            new TierRequirements.XpRequirement(true, 100),
            new TierRequirements.MoneyRequirement(true, new BigDecimal("50")),
            new TierRequirements.ItemsRequirement(false, Collections.emptyList())
        );
        ChargeReceipt receipt1 = costService.charge(xpRichPlayer, xpOrMoney, Collections.singletonList(new org.bukkit.inventory.ItemStack(Material.AIR)));
        assertTrue(receipt1.isSuccess());
        assertTrue(receipt1.getXpCharged() > 0);

        economyService.fakeBalance = new BigDecimal("500");
        Player xpPoorPlayer = createFakePlayerWithLevel(1);
        ChargeReceipt receipt2 = costService.charge(xpPoorPlayer, xpOrMoney, Collections.singletonList(new org.bukkit.inventory.ItemStack(Material.AIR)));
        assertTrue(receipt2.isSuccess());
        assertEquals(0, receipt2.getXpCharged());
        assertTrue(receipt2.getMoneyCharged().compareTo(BigDecimal.ZERO) > 0);
    }

    @Test
    void failedChargeDoesNotMutatePlayerOrEconomy() {
        MutableInt mutableLevel = new MutableInt(5);
        Player poorPlayer = createFakePlayerWithMutableLevel(mutableLevel);
        economyService.fakeBalance = new BigDecimal("5");

        TierRequirements expensiveCost = new TierRequirements(
            TierRequirements.Combine.ALL,
            new TierRequirements.XpRequirement(true, 1000),
            new TierRequirements.MoneyRequirement(true, new BigDecimal("1000")),
            new TierRequirements.ItemsRequirement(false, Collections.emptyList())
        );
        int levelBefore = mutableLevel.value;
        BigDecimal balanceBefore = economyService.fakeBalance;

        ChargeReceipt failed = costService.charge(poorPlayer, expensiveCost, Collections.singletonList(new org.bukkit.inventory.ItemStack(Material.AIR)));
        assertFalse(failed.isSuccess());
        assertEquals(levelBefore, mutableLevel.value);
        assertEquals(balanceBefore, economyService.fakeBalance);
    }

    @Test
    void successfulRefundRestoresExactlyChargedXpAndMoney() {
        MutableInt mutableLevel = new MutableInt(100);
        Player richPlayer = createFakePlayerWithMutableLevel(mutableLevel);
        economyService.fakeBalance = new BigDecimal("500");

        TierRequirements tierCost = new TierRequirements(
            TierRequirements.Combine.ALL,
            new TierRequirements.XpRequirement(true, 100),
            new TierRequirements.MoneyRequirement(true, new BigDecimal("50")),
            new TierRequirements.ItemsRequirement(false, Collections.emptyList())
        );
        ChargeReceipt receipt = costService.charge(richPlayer, tierCost, Collections.singletonList(new org.bukkit.inventory.ItemStack(Material.AIR)));
        assertTrue(receipt.isSuccess());

        BigDecimal balanceAfterCharge = economyService.fakeBalance;

        costService.refund(richPlayer, receipt);

        assertEquals(balanceAfterCharge.add(receipt.getMoneyCharged()), economyService.fakeBalance);
    }

    @Test
    void unsuccessfulOrEmptyReceiptRefundIsNoOp() {
        ChargeReceipt failedReceipt = ChargeReceipt.failure("test failure");
        ChargeReceipt zeroReceipt = ChargeReceipt.success(0, BigDecimal.ZERO, Collections.emptyList());

        ChargeReceipt result1 = costService.refund(player, failedReceipt);
        assertSame(failedReceipt, result1);

        ChargeReceipt result2 = costService.refund(player, zeroReceipt);
        assertSame(zeroReceipt, result2);

        TierRequirements nullXpReq = new TierRequirements(
            TierRequirements.Combine.ALL,
            new TierRequirements.XpRequirement(true, 0),
            new TierRequirements.MoneyRequirement(false, BigDecimal.ZERO),
            new TierRequirements.ItemsRequirement(false, Collections.emptyList())
        );
        CostQuote quote = costService.quote(player, nullXpReq);
        assertEquals(0, quote.getXpRequired());
        assertTrue(quote.isAffordable());
    }

    @Test
    void quoteWithZeroRequirementsReturnsZeroCost() {
        TierRequirements zeroReq = new TierRequirements(
            TierRequirements.Combine.ALL,
            new TierRequirements.XpRequirement(false, 0),
            new TierRequirements.MoneyRequirement(false, BigDecimal.ZERO),
            new TierRequirements.ItemsRequirement(false, Collections.emptyList())
        );
        CostQuote quote = costService.quote(player, zeroReq);
        assertEquals(0, quote.getXpRequired());
        assertEquals(BigDecimal.ZERO, quote.getMoneyRequired());
    }

    @Test
    void chargeWithEmptyItemListSucceeds() {
        Player richPlayer = createFakePlayerWithLevel(100);
        TierRequirements xpReq = new TierRequirements(
            TierRequirements.Combine.ALL,
            new TierRequirements.XpRequirement(true, 10),
            new TierRequirements.MoneyRequirement(false, BigDecimal.ZERO),
            new TierRequirements.ItemsRequirement(false, Collections.emptyList())
        );
        ChargeReceipt receipt = costService.charge(richPlayer, xpReq, Collections.emptyList());
        assertTrue(receipt.isSuccess());
    }

    private static Player createFakePlayer() {
        return createFakePlayerWithLevel(0);
    }

    private static Player createFakePlayerWithBypass(boolean hasBypass) {
        return createFakePlayerWithState(0, hasBypass);
    }

    private static Player createFakePlayerWithLevel(int level) {
        return createFakePlayerWithState(level, false);
    }

    private static Player createFakePlayerWithState(int level, boolean hasBypass) {
        MutableInt mutableLevel = new MutableInt(level);
        InvocationHandler handler = (proxy, method, args) -> {
            String name = method.getName();
            switch (name) {
                case "getLevel": return mutableLevel.value;
                case "setLevel": mutableLevel.value = (Integer) args[0]; return null;
                case "getTotalExperience": return mutableLevel.value;
                case "setTotalExperience": mutableLevel.value = (Integer) args[0]; return null;
                case "hasPermission":
                    return hasBypass && "flameforge.bypass.cost".equals(args[0]);
                case "getName": return "FakePlayer";
                case "isOnline": return true;
                case "getUniqueId": return UUID.randomUUID();
                case "getServer": return null;
                case "getWorld": return null;
                case "getLocation": return null;
                case "getInventory": return null;
                case "isPermissionSet": return false;
                case "isOp": return false;
                case "setOp": return null;
                case "sendMessage": return null;
                case "getDisplayName": return "FakePlayer";
                case "getPlayerListName": return "FakePlayer";
                case "getPlayer": return proxy;
                case "getAddress": return null;
                case "isBanned": return false;
                case "isWhitelisted": return false;
                case "hasPlayedBefore": return true;
                case "getFirstPlayed": return 0L;
                case "getLastPlayed": return 0L;
                case "getBedSpawnLocation": return null;
                case "setBedSpawnLocation": return null;
                case "getEnderChest": return null;
                case "getEffectivePermissions": return java.util.Collections.emptySet();
                case "recalculatePermissions": return null;
                case "addAttachment": return null;
                case "removeAttachment": return null;
                case "isConversing": return false;
                case "acceptConversationInput": return null;
                case "abandonConversation": return null;
                case "beginConversation": return false;
                case "sendRawMessage": return null;
                case "sendPluginMessage": return null;
                case "getListeningPluginChannels": return java.util.Collections.emptySet();
                case "kickPlayer": return null;
                case "chat": return null;
                case "performCommand": return true;
                case "isSneaking": return false;
                case "setSneaking": return null;
                case "isSprinting": return false;
                case "setSprinting": return null;
                case "saveData": return null;
                case "loadData": return null;
                case "setSleepingIgnored": return null;
                case "isSleepingIgnored": return false;
                case "playNote": return null;
                case "playSound": return null;
                case "playEffect": return null;
                case "sendBlockChange": return null;
                case "sendSignChange": return null;
                case "sendMap": return null;
                case "updateInventory": return null;
                case "awardAchievement": return null;
                case "removeAchievement": return null;
                case "hasAchievement": return false;
                case "getStatistic": return 0;
                case "setStatistic": return null;
                case "incrementStatistic": return null;
                case "decrementStatistic": return null;
                case "setPlayerTime": return null;
                case "getPlayerTime": return 0L;
                case "getPlayerTimeOffset": return 0L;
                case "isPlayerTimeRelative": return false;
                case "resetPlayerTime": return null;
                case "setPlayerWeather": return null;
                case "getPlayerWeather": return null;
                case "resetWeather": return null;
                case "giveExp": return null;
                case "giveExpLevels": return null;
                case "getExp": return 0f;
                case "setExp": return null;
                case "getExhaustion": return 0f;
                case "setExhaustion": return null;
                case "getSaturation": return 0f;
                case "setSaturation": return null;
                case "getFoodLevel": return 20;
                case "setFoodLevel": return null;
                case "getAllowFlight": return false;
                case "setAllowFlight": return null;
                case "hidePlayer": return null;
                case "showPlayer": return null;
                case "canSee": return false;
                case "isOnGround": return false;
                case "isFlying": return false;
                case "setFlying": return null;
                case "getFlySpeed": return 0.1f;
                case "setFlySpeed": return null;
                case "getWalkSpeed": return 0.2f;
                case "setWalkSpeed": return null;
                case "setTexturePack": return null;
                case "setResourcePack": return null;
                case "getScoreboard": return null;
                case "setScoreboard": return null;
                case "isHealthScaled": return false;
                case "setHealthScaled": return null;
                case "getHealthScale": return 20.0;
                case "setHealthScale": return null;
                case "getHealth": return 20.0;
                case "setHealth": return null;
                case "getMaxHealth": return 20.0;
                case "setMaxHealth": return null;
                case "getVehicle": return null;
                case "leaveVehicle": return false;
                case "isInsideVehicle": return false;
                case "getPassenger": return null;
                case "setPassenger": return false;
                case "isEmpty": return false;
                case "eject": return false;
                case "teleport": return false;
                case "setVelocity": return null;
                case "getVelocity": return null;
                case "isDead": return false;
                case "isValid": return false;
                case "getEntityId": return 0;
                case "getFireTicks": return 0;
                case "setFireTicks": return null;
                case "getRemainingAir": return 300;
                case "setRemainingAir": return null;
                case "getMaximumAir": return 300;
                case "setMaximumAir": return null;
                case "getMaxFireTicks": return 0;
                case "getLastDamage": return 0;
                case "setLastDamage": return null;
                case "getNoDamageTicks": return 0;
                case "setNoDamageTicks": return null;
                case "getMaxNoDamageTicks": return 0;
                case "damage": return null;
                case "knockBack": return null;
                case "getCompassTarget": return null;
                case "setCompassTarget": return null;
                case "setDisplayName": return null;
                case "setPlayerListName": return null;
                case "setGameMode": return null;
                case "getGameMode": return null;
                case "getXPProgress": return 0f;
                case "setXPProgress": return null;
                case "getExperienceProgress": return 0f;
                case "setExperienceProgress": return null;
                case "getMaxExp": return 0;
                case "getSpectatorTarget": return null;
                case "setSpectatorTarget": return null;
                case "sendTitle": return null;
                case "resetTitle": return null;
                case "resetPlayerWeather": return null;
                case "setCanPickupItems": return null;
                case "getCanPickupItems": return false;
                case "equals": return proxy == args[0];
                case "hashCode": return System.identityHashCode(proxy);
                case "toString": return "FakePlayer{}";
                case "spigot": return null;
                default:
                    if (name.startsWith("get")) return null;
                    if (name.startsWith("is")) return false;
                    if (name.startsWith("set")) return null;
                    return false;
            }
        };

        return (Player) Proxy.newProxyInstance(
            Player.class.getClassLoader(),
            new Class<?>[] { Player.class },
            handler
        );
    }

    private static Player createFakePlayerWithMutableLevel(MutableInt mutableLevel) {
        InvocationHandler handler = (proxy, method, args) -> {
            String name = method.getName();
            switch (name) {
                case "getLevel": return mutableLevel.value;
                case "setLevel": mutableLevel.value = (Integer) args[0]; return null;
                case "hasPermission": return false;
                case "getName": return "FakePlayer";
                case "isOnline": return true;
                case "getUniqueId": return UUID.randomUUID();
                case "getServer": return null;
                case "getWorld": return null;
                case "getLocation": return null;
                case "getInventory": return null;
                case "isPermissionSet": return false;
                case "isOp": return false;
                case "setOp": return null;
                case "sendMessage": return null;
                case "getDisplayName": return "FakePlayer";
                case "getPlayerListName": return "FakePlayer";
                case "getPlayer": return proxy;
                case "getAddress": return null;
                case "isBanned": return false;
                case "isWhitelisted": return false;
                case "hasPlayedBefore": return true;
                case "getFirstPlayed": return 0L;
                case "getLastPlayed": return 0L;
                case "getBedSpawnLocation": return null;
                case "setBedSpawnLocation": return null;
                case "getEnderChest": return null;
                case "getEffectivePermissions": return java.util.Collections.emptySet();
                case "recalculatePermissions": return null;
                case "addAttachment": return null;
                case "removeAttachment": return null;
                case "isConversing": return false;
                case "acceptConversationInput": return null;
                case "abandonConversation": return null;
                case "beginConversation": return false;
                case "sendRawMessage": return null;
                case "sendPluginMessage": return null;
                case "getListeningPluginChannels": return java.util.Collections.emptySet();
                case "kickPlayer": return null;
                case "chat": return null;
                case "performCommand": return true;
                case "isSneaking": return false;
                case "setSneaking": return null;
                case "isSprinting": return false;
                case "setSprinting": return null;
                case "saveData": return null;
                case "loadData": return null;
                case "setSleepingIgnored": return null;
                case "isSleepingIgnored": return false;
                case "playNote": return null;
                case "playSound": return null;
                case "playEffect": return null;
                case "sendBlockChange": return null;
                case "sendSignChange": return null;
                case "sendMap": return null;
                case "updateInventory": return null;
                case "awardAchievement": return null;
                case "removeAchievement": return null;
                case "hasAchievement": return false;
                case "getStatistic": return 0;
                case "setStatistic": return null;
                case "incrementStatistic": return null;
                case "decrementStatistic": return null;
                case "setPlayerTime": return null;
                case "getPlayerTime": return 0L;
                case "getPlayerTimeOffset": return 0L;
                case "isPlayerTimeRelative": return false;
                case "resetPlayerTime": return null;
                case "setPlayerWeather": return null;
                case "getPlayerWeather": return null;
                case "resetWeather": return null;
                case "giveExp": return null;
                case "giveExpLevels": return null;
                case "getExp": return 0f;
                case "setExp": return null;
                case "getTotalExperience": return mutableLevel.value;
                case "setTotalExperience": return null;
                case "getExhaustion": return 0f;
                case "setExhaustion": return null;
                case "getSaturation": return 0f;
                case "setSaturation": return null;
                case "getFoodLevel": return 20;
                case "setFoodLevel": return null;
                case "getAllowFlight": return false;
                case "setAllowFlight": return null;
                case "hidePlayer": return null;
                case "showPlayer": return null;
                case "canSee": return false;
                case "isOnGround": return false;
                case "isFlying": return false;
                case "setFlying": return null;
                case "getFlySpeed": return 0.1f;
                case "setFlySpeed": return null;
                case "getWalkSpeed": return 0.2f;
                case "setWalkSpeed": return null;
                case "setTexturePack": return null;
                case "setResourcePack": return null;
                case "getScoreboard": return null;
                case "setScoreboard": return null;
                case "isHealthScaled": return false;
                case "setHealthScaled": return null;
                case "getHealthScale": return 20.0;
                case "setHealthScale": return null;
                case "getHealth": return 20.0;
                case "setHealth": return null;
                case "getMaxHealth": return 20.0;
                case "setMaxHealth": return null;
                case "getVehicle": return null;
                case "leaveVehicle": return false;
                case "isInsideVehicle": return false;
                case "getPassenger": return null;
                case "setPassenger": return false;
                case "isEmpty": return false;
                case "eject": return false;
                case "teleport": return false;
                case "setVelocity": return null;
                case "getVelocity": return null;
                case "isDead": return false;
                case "isValid": return false;
                case "getEntityId": return 0;
                case "getFireTicks": return 0;
                case "setFireTicks": return null;
                case "getRemainingAir": return 300;
                case "setRemainingAir": return null;
                case "getMaximumAir": return 300;
                case "setMaximumAir": return null;
                case "getMaxFireTicks": return 0;
                case "getLastDamage": return 0;
                case "setLastDamage": return null;
                case "getNoDamageTicks": return 0;
                case "setNoDamageTicks": return null;
                case "getMaxNoDamageTicks": return 0;
                case "damage": return null;
                case "knockBack": return null;
                case "getCompassTarget": return null;
                case "setCompassTarget": return null;
                case "setDisplayName": return null;
                case "setPlayerListName": return null;
                case "setGameMode": return null;
                case "getGameMode": return null;
                case "getXPProgress": return 0f;
                case "setXPProgress": return null;
                case "getExperienceProgress": return 0f;
                case "setExperienceProgress": return null;
                case "getMaxExp": return 0;
                case "getSpectatorTarget": return null;
                case "setSpectatorTarget": return null;
                case "sendTitle": return null;
                case "resetTitle": return null;
                case "resetPlayerWeather": return null;
                case "setCanPickupItems": return null;
                case "getCanPickupItems": return false;
                case "equals": return proxy == args[0];
                case "hashCode": return System.identityHashCode(proxy);
                case "toString": return "FakePlayer{}";
                case "spigot": return null;
                default:
                    if (name.startsWith("get") && !name.equals("getTotalExperience") && !name.equals("getLevel")) return null;
                    if (name.startsWith("is")) return false;
                    if (name.startsWith("set")) return null;
                    return false;
            }
        };

        return (Player) Proxy.newProxyInstance(
            Player.class.getClassLoader(),
            new Class<?>[] { Player.class },
            handler
        );
    }

    private static class MutableInt {
        int value;
        MutableInt(int value) { this.value = value; }
    }

    private static class FakeEconomyService implements EconomyService {
        BigDecimal fakeBalance = BigDecimal.ZERO;
        boolean available = true;
        boolean nextWithdrawSucceeds = true;

        @Override
        public boolean available() { return available; }

        @Override
        public BigDecimal balance(OfflinePlayer player) { return fakeBalance; }

        @Override
        public boolean withdraw(OfflinePlayer player, BigDecimal amount) {
            if (!nextWithdrawSucceeds) return false;
            if (fakeBalance.compareTo(amount) >= 0) {
                fakeBalance = fakeBalance.subtract(amount);
                return true;
            }
            return false;
        }

        @Override
        public boolean deposit(OfflinePlayer player, BigDecimal amount) {
            fakeBalance = fakeBalance.add(amount);
            return true;
        }

        @Override
        public String format(BigDecimal amount) { return amount.toPlainString(); }
    }
}
