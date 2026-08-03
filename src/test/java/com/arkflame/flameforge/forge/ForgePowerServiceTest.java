package com.arkflame.flameforge.forge;

import com.arkflame.flameforge.compat.effect.PotionEffectResolver;
import com.arkflame.flameforge.compat.equipment.EquipmentBridge;
import com.arkflame.flameforge.compat.scheduler.SchedulerBridge;
import com.arkflame.flameforge.item.ItemIdentityService;
import com.arkflame.flameforge.model.ForgePowerDefinition;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ForgePowerServiceTest {

    @Test
    void testPowerCooldownEnforcement() {
        JavaPlugin plugin = mock(JavaPlugin.class);
        SchedulerBridge schedulerBridge = mock(SchedulerBridge.class);
        PotionEffectResolver potionEffectResolver = mock(PotionEffectResolver.class);
        EquipmentBridge equipmentBridge = mock(EquipmentBridge.class);
        ItemIdentityService identityService = mock(ItemIdentityService.class);
        UUID forgeId = UUID.randomUUID();

        ForgePowerService service = new ForgePowerService(plugin, schedulerBridge,
            potionEffectResolver, equipmentBridge, identityService);
        ForgePowerDefinition power = mock(ForgePowerDefinition.class);
        when(power.getId()).thenReturn("test_power");
        when(power.getCooldownTicks()).thenReturn(100);

        Player player = mock(Player.class);
        UUID playerId = UUID.randomUUID();
        when(player.getUniqueId()).thenReturn(playerId);

        boolean firstUse = service.canUsePower(player, power, forgeId);
        assertTrue(firstUse, "First use should be allowed");

        service.usePower(player, power, forgeId);

        boolean secondUse = service.canUsePower(player, power, forgeId);
        assertFalse(secondUse, "Second use within cooldown should be denied");
    }

    @Test
    void testPowerCooldownExpired() {
        JavaPlugin plugin = mock(JavaPlugin.class);
        SchedulerBridge schedulerBridge = mock(SchedulerBridge.class);
        PotionEffectResolver potionEffectResolver = mock(PotionEffectResolver.class);
        EquipmentBridge equipmentBridge = mock(EquipmentBridge.class);
        ItemIdentityService identityService = mock(ItemIdentityService.class);
        UUID forgeId = UUID.randomUUID();

        ForgePowerService service = new ForgePowerService(plugin, schedulerBridge,
            potionEffectResolver, equipmentBridge, identityService);
        ForgePowerDefinition power = mock(ForgePowerDefinition.class);
        when(power.getId()).thenReturn("expired_power");
        when(power.getCooldownTicks()).thenReturn(0);

        Player player = mock(Player.class);
        UUID playerId = UUID.randomUUID();
        when(player.getUniqueId()).thenReturn(playerId);

        boolean canUse = service.canUsePower(player, power, forgeId);
        assertTrue(canUse, "Power with 0 cooldown should always be available");
    }

    @Test
    void testPassivePotionActivation() {
        JavaPlugin plugin = mock(JavaPlugin.class);
        SchedulerBridge schedulerBridge = mock(SchedulerBridge.class);
        PotionEffectResolver potionEffectResolver = mock(PotionEffectResolver.class);
        EquipmentBridge equipmentBridge = mock(EquipmentBridge.class);
        ItemIdentityService identityService = mock(ItemIdentityService.class);
        UUID forgeId = UUID.randomUUID();

        ForgePowerService service = new ForgePowerService(plugin, schedulerBridge,
            potionEffectResolver, equipmentBridge, identityService);
        ForgePowerDefinition power = mock(ForgePowerDefinition.class);
        when(power.getPowerType()).thenReturn(ForgePowerDefinition.PowerType.PASSIVE_POTION);
        when(power.getId()).thenReturn("passive_power");
        when(power.getCooldownTicks()).thenReturn(0);
        when(power.getActivationSlots()).thenReturn(Collections.emptyList());
        when(power.getEffectCandidates()).thenReturn(Collections.singletonList("SPEED"));
        when(power.getDurationTicks()).thenReturn(100);
        when(power.getAmplifier()).thenReturn(0);

        Player player = mock(Player.class);
        UUID playerId = UUID.randomUUID();
        when(player.getUniqueId()).thenReturn(playerId);
        when(player.isOnline()).thenReturn(true);
        when(player.getInventory()).thenReturn(mock(org.bukkit.inventory.PlayerInventory.class));

        ItemStack mainHand = mock(ItemStack.class);
        when(mainHand.hasItemMeta()).thenReturn(true);
        when(equipmentBridge.getItem(player, EquipmentBridge.Slot.MAINHAND)).thenReturn(mainHand);
        when(equipmentBridge.getItem(player, EquipmentBridge.Slot.OFFHAND)).thenReturn(null);

        ItemIdentityService.IdentityData identityData = mock(ItemIdentityService.IdentityData.class);
        when(identityData.getForgeId()).thenReturn(forgeId);
        when(identityService.readIdentity(mainHand)).thenReturn(Optional.of(identityData));

        boolean result = service.activatePassivePower(player, power, forgeId);
        assertTrue(result, "PASSIVE_POTION power should activate");
    }

    @Test
    void testOnHitPotionTrigger() {
        JavaPlugin plugin = mock(JavaPlugin.class);
        SchedulerBridge schedulerBridge = mock(SchedulerBridge.class);
        PotionEffectResolver potionEffectResolver = mock(PotionEffectResolver.class);
        EquipmentBridge equipmentBridge = mock(EquipmentBridge.class);
        ItemIdentityService identityService = mock(ItemIdentityService.class);
        UUID forgeId = UUID.randomUUID();

        ForgePowerService service = new ForgePowerService(plugin, schedulerBridge,
            potionEffectResolver, equipmentBridge, identityService);
        Player victim = mock(Player.class);
        ForgePowerDefinition power = mock(ForgePowerDefinition.class);
        when(power.getPowerType()).thenReturn(ForgePowerDefinition.PowerType.ON_HIT_POTION);
        when(power.getId()).thenReturn("onhit_power");
        when(power.getCooldownTicks()).thenReturn(0);

        Player player = mock(Player.class);
        UUID playerId = UUID.randomUUID();
        when(player.getUniqueId()).thenReturn(playerId);

        boolean result = service.triggerOnHitPower(player, victim, power, forgeId);
        assertTrue(result, "ON_HIT_POTION power should trigger");
    }

    @Test
    void testShiftRightClickDash() {
        JavaPlugin plugin = mock(JavaPlugin.class);
        SchedulerBridge schedulerBridge = mock(SchedulerBridge.class);
        PotionEffectResolver potionEffectResolver = mock(PotionEffectResolver.class);
        EquipmentBridge equipmentBridge = mock(EquipmentBridge.class);
        ItemIdentityService identityService = mock(ItemIdentityService.class);
        UUID forgeId = UUID.randomUUID();

        ForgePowerService service = new ForgePowerService(plugin, schedulerBridge,
            potionEffectResolver, equipmentBridge, identityService);
        ForgePowerDefinition power = mock(ForgePowerDefinition.class);
        when(power.getPowerType()).thenReturn(ForgePowerDefinition.PowerType.SHIFT_RIGHT_CLICK_DASH);
        when(power.getId()).thenReturn("dash_power");
        when(power.getCooldownTicks()).thenReturn(0);
        when(power.getHorizontalStrength()).thenReturn(java.math.BigDecimal.ONE);
        when(power.getVerticalStrength()).thenReturn(java.math.BigDecimal.ZERO);

        Player player = mock(Player.class);
        UUID playerId = UUID.randomUUID();
        when(player.getUniqueId()).thenReturn(playerId);
        Location location = mock(Location.class);
        when(location.getDirection()).thenReturn(new org.bukkit.util.Vector(1, 0, 0));
        when(player.getLocation()).thenReturn(location);

        boolean result = service.activateDash(player, power, forgeId);
        assertTrue(result, "SHIFT_RIGHT_CLICK_DASH power should activate");
    }

    @Test
    void testShiftRightClickHeal() {
        JavaPlugin plugin = mock(JavaPlugin.class);
        SchedulerBridge schedulerBridge = mock(SchedulerBridge.class);
        PotionEffectResolver potionEffectResolver = mock(PotionEffectResolver.class);
        EquipmentBridge equipmentBridge = mock(EquipmentBridge.class);
        ItemIdentityService identityService = mock(ItemIdentityService.class);
        UUID forgeId = UUID.randomUUID();

        ForgePowerService service = new ForgePowerService(plugin, schedulerBridge,
            potionEffectResolver, equipmentBridge, identityService);
        ForgePowerDefinition power = mock(ForgePowerDefinition.class);
        when(power.getPowerType()).thenReturn(ForgePowerDefinition.PowerType.SHIFT_RIGHT_CLICK_HEAL);
        when(power.getId()).thenReturn("heal_power");
        when(power.getCooldownTicks()).thenReturn(0);

        Player player = mock(Player.class);
        UUID playerId = UUID.randomUUID();
        when(player.getUniqueId()).thenReturn(playerId);

        boolean result = service.activateHeal(player, power, forgeId);
        assertTrue(result, "SHIFT_RIGHT_CLICK_HEAL power should activate");
    }
}
