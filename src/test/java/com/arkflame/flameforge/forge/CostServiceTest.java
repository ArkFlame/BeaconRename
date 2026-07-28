package com.arkflame.flameforge.forge;

import com.arkflame.flameforge.hook.EconomyService;
import com.arkflame.flameforge.model.CostMode;
import com.arkflame.flameforge.model.TierCost;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class CostServiceTest {

    private CostService costService;
    private FakeEconomyService economyService;
    private Player player;

    @BeforeEach
    void setUp() {
        economyService = new FakeEconomyService();
        JavaPlugin fakePlugin = UnsafeJavaPlugin.createFakePlugin(FakePlugin.class);
        costService = new CostService(fakePlugin, economyService);
        player = createFakePlayer();
    }

    @Test
    void quote_bypassPermission_returnsZeroQuote() {
        Player bypassPlayer = createFakePlayer(p -> p.hasBypass = true);
        TierCost tierCost = TierCost.xpOnly(new BigDecimal("100"));

        CostQuote quote = costService.quote(bypassPlayer, tierCost);

        assertEquals(BigDecimal.ZERO, quote.getXpCost());
        assertEquals(BigDecimal.ZERO, quote.getMoneyCost());
        assertTrue(quote.isXpAffordable());
        assertTrue(quote.isMoneyAffordable());
    }

    @Test
    void charge_bypassPermission_returnsZeroReceipt() {
        Player bypassPlayer = createFakePlayer(p -> p.hasBypass = true);
        TierCost tierCost = TierCost.xpOnly(new BigDecimal("100"));

        ChargeReceipt receipt = costService.charge(bypassPlayer, tierCost);

        assertTrue(receipt.isSuccess());
        assertEquals(BigDecimal.ZERO, receipt.getXpCharged());
        assertEquals(BigDecimal.ZERO, receipt.getMoneyCharged());
    }

    @Test
    void quote_xpOnly_playerHasEnoughXp_quoteAffordable() {
        Player richPlayer = createFakePlayer(p -> p.level = 50);
        TierCost tierCost = TierCost.xpOnly(new BigDecimal("100"));

        CostQuote quote = costService.quote(richPlayer, tierCost);

        assertTrue(quote.isXpAffordable());
        assertTrue(quote.isAffordable());
        assertEquals(CostMode.XP_ONLY, quote.getMode());
    }

    @Test
    void quote_xpOnly_playerHasInsufficientXp_quoteNotAffordable() {
        Player poorPlayer = createFakePlayer(p -> p.level = 1);
        TierCost tierCost = TierCost.xpOnly(new BigDecimal("100"));

        CostQuote quote = costService.quote(poorPlayer, tierCost);

        assertFalse(quote.isXpAffordable());
        assertFalse(quote.isAffordable());
    }

    @Test
    void quote_moneyOnly_playerHasEnoughMoney_quoteAffordable() {
        economyService.fakeBalance = new BigDecimal("500");
        TierCost tierCost = TierCost.moneyOnly(new BigDecimal("100"));

        CostQuote quote = costService.quote(player, tierCost);

        assertTrue(quote.isMoneyAffordable());
        assertTrue(quote.isAffordable());
        assertEquals(CostMode.MONEY_ONLY, quote.getMode());
    }

    @Test
    void quote_moneyOnly_playerHasInsufficientMoney_quoteNotAffordable() {
        economyService.fakeBalance = new BigDecimal("50");
        TierCost tierCost = TierCost.moneyOnly(new BigDecimal("100"));

        CostQuote quote = costService.quote(player, tierCost);

        assertFalse(quote.isMoneyAffordable());
        assertFalse(quote.isAffordable());
    }

    @Test
    void quote_moneyOnly_economyUnavailable_quoteNotAffordable() {
        economyService.available = false;
        TierCost tierCost = TierCost.moneyOnly(new BigDecimal("100"));

        CostQuote quote = costService.quote(player, tierCost);

        assertFalse(quote.isMoneyAffordable());
        assertFalse(quote.isAffordable());
    }

    @Test
    void charge_xpOnly_success_deductsXp() {
        Player richPlayer = createFakePlayer(p -> p.level = 50);
        TierCost tierCost = TierCost.xpOnly(new BigDecimal("100"));

        ChargeReceipt receipt = costService.charge(richPlayer, tierCost);

        assertTrue(receipt.isSuccess());
        assertEquals(CostMode.XP_ONLY, receipt.getMode());
        assertTrue(receipt.getXpCharged().compareTo(BigDecimal.ZERO) > 0);
        assertFalse(receipt.hasMoneyCharge());
    }

    @Test
    void charge_xpOnly_insufficientXp_fails() {
        Player poorPlayer = createFakePlayer(p -> p.level = 1);
        TierCost tierCost = TierCost.xpOnly(new BigDecimal("100"));

        ChargeReceipt receipt = costService.charge(poorPlayer, tierCost);

        assertFalse(receipt.isSuccess());
        assertEquals("Insufficient XP", receipt.getFailureReason());
    }

    @Test
    void charge_moneyOnly_success_withdrawsMoney() {
        economyService.fakeBalance = new BigDecimal("500");
        TierCost tierCost = TierCost.moneyOnly(new BigDecimal("100"));

        ChargeReceipt receipt = costService.charge(player, tierCost);

        assertTrue(receipt.isSuccess());
        assertEquals(CostMode.MONEY_ONLY, receipt.getMode());
        assertTrue(receipt.getMoneyCharged().compareTo(BigDecimal.ZERO) > 0);
        assertFalse(receipt.hasXpCharge());
    }

    @Test
    void charge_moneyOnly_insufficientFunds_fails() {
        economyService.fakeBalance = new BigDecimal("50");
        TierCost tierCost = TierCost.moneyOnly(new BigDecimal("100"));

        ChargeReceipt receipt = costService.charge(player, tierCost);

        assertFalse(receipt.isSuccess());
        assertEquals("Insufficient funds", receipt.getFailureReason());
    }

    @Test
    void charge_xpAndMoney_bothAffordable_success() {
        Player richPlayer = createFakePlayer(p -> p.level = 50);
        economyService.fakeBalance = new BigDecimal("500");
        TierCost tierCost = TierCost.xpAndMoney(new BigDecimal("100"), new BigDecimal("50"));

        ChargeReceipt receipt = costService.charge(richPlayer, tierCost);

        assertTrue(receipt.isSuccess());
        assertEquals(CostMode.XP_AND_MONEY, receipt.getMode());
        assertTrue(receipt.hasXpCharge());
        assertTrue(receipt.hasMoneyCharge());
    }

    @Test
    void charge_xpAndMoney_xpNotAffordable_fails() {
        Player poorPlayer = createFakePlayer(p -> p.level = 1);
        economyService.fakeBalance = new BigDecimal("500");
        TierCost tierCost = TierCost.xpAndMoney(new BigDecimal("100"), new BigDecimal("50"));

        ChargeReceipt receipt = costService.charge(poorPlayer, tierCost);

        assertFalse(receipt.isSuccess());
        assertEquals("Insufficient XP", receipt.getFailureReason());
    }

    @Test
    void charge_xpAndMoney_moneyNotAffordable_fails() {
        Player richPlayer = createFakePlayer(p -> p.level = 50);
        economyService.fakeBalance = new BigDecimal("10");
        TierCost tierCost = TierCost.xpAndMoney(new BigDecimal("100"), new BigDecimal("50"));

        ChargeReceipt receipt = costService.charge(richPlayer, tierCost);

        assertFalse(receipt.isSuccess());
        assertEquals("Insufficient funds", receipt.getFailureReason());
    }

    @Test
    void charge_xpAndMoney_moneyWithdrawFailsAfterXp_deductsRollsBack() {
        MutableInt mutableLevel = new MutableInt(50);
        Player richPlayer = createFakePlayerWithMutableLevel(mutableLevel);
        economyService.fakeBalance = new BigDecimal("500");
        economyService.nextWithdrawSucceeds = false;
        TierCost tierCost = TierCost.xpAndMoney(new BigDecimal("100"), new BigDecimal("50"));

        ChargeReceipt receipt = costService.charge(richPlayer, tierCost);

        assertFalse(receipt.isSuccess());
        assertEquals("Money withdrawal failed", receipt.getFailureReason());
        assertEquals(50, mutableLevel.value);
        assertEquals(new BigDecimal("500"), economyService.fakeBalance);
    }

    @Test
    void charge_xpOrMoney_bothAffordable_chargesXpFirst() {
        Player richPlayer = createFakePlayer(p -> p.level = 50);
        economyService.fakeBalance = new BigDecimal("500");
        TierCost tierCost = TierCost.of(CostMode.XP_OR_MONEY,
            new BigDecimal("100"), new BigDecimal("50"));

        ChargeReceipt receipt = costService.charge(richPlayer, tierCost);

        assertTrue(receipt.isSuccess());
        assertEquals(CostMode.XP_OR_MONEY, receipt.getMode());
        assertTrue(receipt.hasXpCharge());
    }

    @Test
    void charge_xpOrMoney_onlyXpAffordable_chargesXp() {
        Player xpOnlyPlayer = createFakePlayer(p -> p.level = 50);
        economyService.fakeBalance = new BigDecimal("10");
        TierCost tierCost = TierCost.of(CostMode.XP_OR_MONEY,
            new BigDecimal("100"), new BigDecimal("50"));

        ChargeReceipt receipt = costService.charge(xpOnlyPlayer, tierCost);

        assertTrue(receipt.isSuccess());
        assertEquals(CostMode.XP_ONLY, receipt.getMode());
        assertTrue(receipt.hasXpCharge());
        assertFalse(receipt.hasMoneyCharge());
    }

    @Test
    void charge_xpOrMoney_onlyMoneyAffordable_chargesMoney() {
        Player moneyOnlyPlayer = createFakePlayer(p -> p.level = 1);
        economyService.fakeBalance = new BigDecimal("500");
        TierCost tierCost = TierCost.of(CostMode.XP_OR_MONEY,
            new BigDecimal("100"), new BigDecimal("50"));

        ChargeReceipt receipt = costService.charge(moneyOnlyPlayer, tierCost);

        assertTrue(receipt.isSuccess());
        assertEquals(CostMode.MONEY_ONLY, receipt.getMode());
        assertFalse(receipt.hasXpCharge());
        assertTrue(receipt.hasMoneyCharge());
    }

    @Test
    void charge_xpOrMoney_neitherAffordable_fails() {
        Player poorPlayer = createFakePlayer(p -> p.level = 1);
        economyService.fakeBalance = new BigDecimal("10");
        TierCost tierCost = TierCost.of(CostMode.XP_OR_MONEY,
            new BigDecimal("100"), new BigDecimal("50"));

        ChargeReceipt receipt = costService.charge(poorPlayer, tierCost);

        assertFalse(receipt.isSuccess());
        assertEquals("Neither XP nor money affordable", receipt.getFailureReason());
    }

    @Test
    void refund_noSuccessReceipt_returnsUnchanged() {
        ChargeReceipt failedReceipt = ChargeReceipt.failure(CostMode.XP_ONLY, "test failure");

        ChargeReceipt result = costService.refund(player, failedReceipt);

        assertSame(failedReceipt, result);
    }

    @Test
    void refund_successReceipt_refundsBothXpAndMoney() {
        Player richPlayer = createFakePlayer(p -> p.level = 20);
        economyService.fakeBalance = new BigDecimal("100");
        ChargeReceipt successReceipt = ChargeReceipt.success(CostMode.XP_AND_MONEY,
            new BigDecimal("50"), new BigDecimal("30"));

        costService.refund(richPlayer, successReceipt);

        assertEquals(new BigDecimal("130"), economyService.fakeBalance);
    }

    @Test
    void refund_nullXpCharged_noOp() {
        economyService.fakeBalance = new BigDecimal("100");
        ChargeReceipt receipt = ChargeReceipt.success(CostMode.XP_ONLY, BigDecimal.ZERO, BigDecimal.ZERO);

        costService.refund(player, receipt);

        assertEquals(new BigDecimal("100"), economyService.fakeBalance);
    }

    @Test
    void quote_nullXpCost_normalizesToZero() {
        TierCost tierCost = TierCost.of(CostMode.XP_ONLY, null, null);
        Player noXpPlayer = createFakePlayer(p -> p.level = 0);

        CostQuote quote = costService.quote(noXpPlayer, tierCost);

        assertEquals(BigDecimal.ZERO, quote.getXpCost());
        assertTrue(quote.isXpAffordable());
    }

    private static Player createFakePlayer() {
        return createFakePlayer(p -> {});
    }

    private static Player createFakePlayer(PlayerMutator mutator) {
        PlayerState state = new PlayerState();
        mutator.mutate(state);

        InvocationHandler handler = (proxy, method, args) -> {
            String name = method.getName();
            switch (name) {
                case "getLevel": return state.level;
                case "setLevel": state.level = (Integer) args[0]; return null;
                case "hasPermission":
                    return state.hasBypass && "flameforge.bypass.cost".equals(args[0]);
                case "getName": return "FakePlayer";
                case "isOnline": return true;
                case "getUniqueId": return UUID.randomUUID();
                case "getServer": return null;
                case "getWorld": return null;
                case "getLocation": return null;
                case "getInventory": return null;
                case "isPermissionSet": return false;
                case "isPermissionSet_": return false;
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
                case "getEffectivePermissions": return Collections.emptySet();
                case "recalculatePermissions": return null;
                case "addAttachment": return null;
                case "removeAttachment": return null;
                case "isConversing": return false;
                case "acceptConversationInput": return null;
                case "abandonConversation":
                case "beginConversation":
                case "sendRawMessage":
                case "sendPluginMessage":
                case "getListeningPluginChannels":
                case "kickPlayer":
                case "chat":
                case "performCommand":
                case "isSneaking":
                case "setSneaking":
                case "isSprinting":
                case "setSprinting":
                case "saveData":
                case "loadData":
                case "setSleepingIgnored":
                case "isSleepingIgnored":
                case "playNote":
                case "playSound":
                case "playEffect":
                case "sendBlockChange":
                case "sendSignChange":
                case "sendMap":
                case "updateInventory":
                case "awardAchievement":
                case "removeAchievement":
                case "hasAchievement":
                case "getStatistic":
                case "setStatistic":
                case "incrementStatistic":
                case "decrementStatistic":
                case "setPlayerTime":
                case "getPlayerTime":
                case "getPlayerTimeOffset":
                case "isPlayerTimeRelative":
                case "resetPlayerTime":
                case "setPlayerWeather":
                case "getPlayerWeather":
                case "resetWeather":
                case "giveExp":
                case "giveExpLevels":
                case "getExp":
                case "setExp":
                case "getTotalExperience":
                case "setTotalExperience":
                case "getExhaustion":
                case "setExhaustion":
                case "getSaturation":
                case "setSaturation":
                case "getFoodLevel":
                case "setFoodLevel":
                case "getAllowFlight":
                case "setAllowFlight":
                case "hidePlayer":
                case "showPlayer":
                case "canSee":
                case "isOnGround":
                case "isFlying":
                case "setFlying":
                case "getFlySpeed":
                case "setFlySpeed":
                case "getWalkSpeed":
                case "setWalkSpeed":
                case "setTexturePack":
                case "setResourcePack":
                case "getScoreboard":
                case "setScoreboard":
                case "isHealthScaled":
                case "setHealthScaled":
                case "getHealthScale":
                case "setHealthScale":
                case "getHealth":
                case "setHealth":
                case "getMaxHealth":
                case "setMaxHealth":
                case "getVehicle":
                case "leaveVehicle":
                case "isInsideVehicle":
                case "getPassenger":
                case "setPassenger":
                case "isEmpty":
                case "eject":
                case "teleport":
                case "setVelocity":
                case "getVelocity":
                case "isDead":
                case "isValid":
                case "getEntityId":
                case "getFireTicks":
                case "setFireTicks":
                case "getRemainingAir":
                case "setRemainingAir":
                case "getMaximumAir":
                case "setMaximumAir":
                case "getMaxFireTicks":
                case "setMaxFireTicks":
                case "getLastDamage":
                case "setLastDamage":
                case "getNoDamageTicks":
                case "setNoDamageTicks":
                case "getMaxNoDamageTicks":
                case "setMaxNoDamageTicks":
                case "damage":
                case "knockBack":
                case "getCompassTarget":
                case "setCompassTarget":
                case "setDisplayName":
                case "setPlayerListName":
                case "setGameMode":
                case "getGameMode":
                case "getXPProgress":
                case "setXPProgress":
                case "getExperienceProgress":
                case "setExperienceProgress":
                case "getMaxExp":
                case "getSpectatorTarget":
                case "setSpectatorTarget":
                case "sendTitle":
                case "resetTitle":
                case "resetPlayerWeather":
                case "setCanPickupItems":
                case "getCanPickupItems":
                    return null;
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

    private interface PlayerMutator {
        void mutate(PlayerState state);
    }

    private static class PlayerState {
        int level = 0;
        boolean hasBypass = false;
    }

    private static class MutableInt {
        int value;
        MutableInt(int value) { this.value = value; }
    }

    private static Player createFakePlayerWithMutableLevel(MutableInt mutableLevel) {
        InvocationHandler handler = (proxy, method, args) -> {
            String name = method.getName();
            switch (name) {
                case "getLevel": return mutableLevel.value;
                case "setLevel": mutableLevel.value = (Integer) args[0]; return null;
                case "hasPermission":
                    return false;
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
                case "getEffectivePermissions": return Collections.emptySet();
                case "recalculatePermissions": return null;
                case "addAttachment": return null;
                case "removeAttachment": return null;
                case "isConversing": return false;
                case "acceptConversationInput": return null;
                case "abandonConversation": return null;
                case "beginConversation": return false;
                case "sendRawMessage": return null;
                case "sendPluginMessage": return null;
                case "getListeningPluginChannels": return Collections.emptySet();
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
                case "setMaxFireTicks": return null;
                case "getLastDamage": return 0;
                case "setLastDamage": return null;
                case "getNoDamageTicks": return 0;
                case "setNoDamageTicks": return null;
                case "getMaxNoDamageTicks": return 0;
                case "setMaxNoDamageTicks": return null;
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

    private static class FakePlugin extends JavaPlugin {
        @Override public void onEnable() {}
        @Override public void onDisable() {}
    }
}
