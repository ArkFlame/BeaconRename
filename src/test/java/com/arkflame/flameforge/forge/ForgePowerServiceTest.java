package com.arkflame.flameforge.forge;

import com.arkflame.flameforge.compat.effect.PotionEffectResolver;
import com.arkflame.flameforge.compat.equipment.EquipmentBridge;
import com.arkflame.flameforge.compat.scheduler.SchedulerBridge;
import com.arkflame.flameforge.compat.scheduler.TaskHandle;
import com.arkflame.flameforge.item.ItemIdentityCodec;
import com.arkflame.flameforge.item.ItemIdentityService;
import com.arkflame.flameforge.model.ForgePowerDefinition;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

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

        ItemIdentityCodec.Identity identityData = ItemIdentityCodec.Identity.empty()
            .withForgeId(forgeId);
        when(identityService.readForgeIdentity(mainHand)).thenReturn(
            new ItemIdentityService.ForgeIdentityRead(
                ItemIdentityService.ForgeIdentityStatus.VALID,
                identityData
            )
        );

        TaskHandle taskHandle = mock(TaskHandle.class);
        when(schedulerBridge.runEntityLater(any(LivingEntity.class), any(Runnable.class), any(Runnable.class), anyLong()))
            .thenReturn(taskHandle);

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

    @Test
    void lightningTriggersOnlyOnConfiguredNthHit() {
        JavaPlugin plugin = mock(JavaPlugin.class);
        SchedulerBridge schedulerBridge = mock(SchedulerBridge.class);
        PotionEffectResolver potionEffectResolver = mock(PotionEffectResolver.class);
        EquipmentBridge equipmentBridge = mock(EquipmentBridge.class);
        ItemIdentityService identityService = mock(ItemIdentityService.class);
        UUID forgeId = UUID.randomUUID();

        ForgePowerService service = new ForgePowerService(plugin, schedulerBridge,
            potionEffectResolver, equipmentBridge, identityService);
        ForgePowerDefinition power = mock(ForgePowerDefinition.class);
        when(power.getPowerType()).thenReturn(ForgePowerDefinition.PowerType.EVERY_N_HIT_LIGHTNING);
        when(power.getId()).thenReturn("lightning_power");
        when(power.getCooldownTicks()).thenReturn(0);
        when(power.getHitInterval()).thenReturn(3);
        when(power.getChance()).thenReturn(BigDecimal.ONE);

        Player player = mock(Player.class);
        UUID playerId = UUID.randomUUID();
        when(player.getUniqueId()).thenReturn(playerId);

        LivingEntity victim = mock(LivingEntity.class);
        Location victimLoc = mock(Location.class);
        when(victim.getLocation()).thenReturn(victimLoc);
        when(victimLoc.getWorld()).thenReturn(mock(org.bukkit.World.class));

        when(schedulerBridge.runRegion(any(Location.class), any(Runnable.class))).thenReturn(mock(TaskHandle.class));

        boolean firstHit = service.triggerOnHitPower(player, victim, power, forgeId);
        assertFalse(firstHit, "First hit should not trigger lightning (interval=3)");

        boolean secondHit = service.triggerOnHitPower(player, victim, power, forgeId);
        assertFalse(secondHit, "Second hit should not trigger lightning (interval=3)");

        boolean thirdHit = service.triggerOnHitPower(player, victim, power, forgeId);
        assertTrue(thirdHit, "Third hit should trigger lightning (interval=3)");
    }

    @Test
    void knockbackTriggersOnlyOnConfiguredNthHit() {
        JavaPlugin plugin = mock(JavaPlugin.class);
        SchedulerBridge schedulerBridge = mock(SchedulerBridge.class);
        PotionEffectResolver potionEffectResolver = mock(PotionEffectResolver.class);
        EquipmentBridge equipmentBridge = mock(EquipmentBridge.class);
        ItemIdentityService identityService = mock(ItemIdentityService.class);
        UUID forgeId = UUID.randomUUID();

        ForgePowerService service = new ForgePowerService(plugin, schedulerBridge,
            potionEffectResolver, equipmentBridge, identityService);
        ForgePowerDefinition power = mock(ForgePowerDefinition.class);
        when(power.getPowerType()).thenReturn(ForgePowerDefinition.PowerType.EVERY_N_HIT_KNOCKBACK);
        when(power.getId()).thenReturn("knockback_power");
        when(power.getCooldownTicks()).thenReturn(0);
        when(power.getHitInterval()).thenReturn(5);
        when(power.getChance()).thenReturn(BigDecimal.ONE);
        when(power.getHorizontalStrength()).thenReturn(BigDecimal.ONE);
        when(power.getVerticalStrength()).thenReturn(BigDecimal.ZERO);

        Player player = mock(Player.class);
        UUID playerId = UUID.randomUUID();
        when(player.getUniqueId()).thenReturn(playerId);
        Location playerLoc = mock(Location.class);
        when(playerLoc.getDirection()).thenReturn(new org.bukkit.util.Vector(0, 0, 1));
        when(player.getLocation()).thenReturn(playerLoc);

        LivingEntity victim = mock(LivingEntity.class);
        Location victimLoc = mock(Location.class);
        when(victim.getLocation()).thenReturn(victimLoc);
        when(victimLoc.clone()).thenReturn(victimLoc);
        when(victimLoc.getX()).thenReturn(10.0);
        when(victimLoc.getZ()).thenReturn(10.0);
        when(victimLoc.getDirection()).thenReturn(new org.bukkit.util.Vector(1, 0, 0));

        when(schedulerBridge.runEntity(any(Entity.class), any(Runnable.class), any(Runnable.class))).thenReturn(mock(TaskHandle.class));

        for (int i = 1; i < 5; i++) {
            boolean result = service.triggerOnHitPower(player, victim, power, forgeId);
            assertFalse(result, "Hit " + i + " should not trigger knockback (interval=5)");
        }

        boolean fifthHit = service.triggerOnHitPower(player, victim, power, forgeId);
        assertTrue(fifthHit, "Fifth hit should trigger knockback (interval=5)");
    }

    @Test
    void counterResetsAfterProc() {
        JavaPlugin plugin = mock(JavaPlugin.class);
        SchedulerBridge schedulerBridge = mock(SchedulerBridge.class);
        PotionEffectResolver potionEffectResolver = mock(PotionEffectResolver.class);
        EquipmentBridge equipmentBridge = mock(EquipmentBridge.class);
        ItemIdentityService identityService = mock(ItemIdentityService.class);
        UUID forgeId = UUID.randomUUID();

        ForgePowerService service = new ForgePowerService(plugin, schedulerBridge,
            potionEffectResolver, equipmentBridge, identityService);
        ForgePowerDefinition power = mock(ForgePowerDefinition.class);
        when(power.getPowerType()).thenReturn(ForgePowerDefinition.PowerType.EVERY_N_HIT_LIGHTNING);
        when(power.getId()).thenReturn("lightning_power");
        when(power.getCooldownTicks()).thenReturn(0);
        when(power.getHitInterval()).thenReturn(2);
        when(power.getChance()).thenReturn(BigDecimal.ONE);

        Player player = mock(Player.class);
        UUID playerId = UUID.randomUUID();
        when(player.getUniqueId()).thenReturn(playerId);

        LivingEntity victim = mock(LivingEntity.class);
        Location victimLoc = mock(Location.class);
        when(victim.getLocation()).thenReturn(victimLoc);
        when(victimLoc.getWorld()).thenReturn(mock(org.bukkit.World.class));

        when(schedulerBridge.runRegion(any(Location.class), any(Runnable.class))).thenReturn(mock(TaskHandle.class));

        service.triggerOnHitPower(player, victim, power, forgeId);
        service.triggerOnHitPower(player, victim, power, forgeId);

        service.clearHitCountersForPlayer(playerId);

        boolean afterClearFirstHit = service.triggerOnHitPower(player, victim, power, forgeId);
        assertFalse(afterClearFirstHit, "After counter clear, first hit should not trigger");
    }

    @Test
    void clearPlayerForgeRemovesCounters() {
        JavaPlugin plugin = mock(JavaPlugin.class);
        SchedulerBridge schedulerBridge = mock(SchedulerBridge.class);
        PotionEffectResolver potionEffectResolver = mock(PotionEffectResolver.class);
        EquipmentBridge equipmentBridge = mock(EquipmentBridge.class);
        ItemIdentityService identityService = mock(ItemIdentityService.class);
        UUID forgeId = UUID.randomUUID();

        ForgePowerService service = new ForgePowerService(plugin, schedulerBridge,
            potionEffectResolver, equipmentBridge, identityService);
        ForgePowerDefinition power = mock(ForgePowerDefinition.class);
        when(power.getPowerType()).thenReturn(ForgePowerDefinition.PowerType.EVERY_N_HIT_LIGHTNING);
        when(power.getId()).thenReturn("lightning_power");
        when(power.getCooldownTicks()).thenReturn(0);
        when(power.getHitInterval()).thenReturn(2);
        when(power.getChance()).thenReturn(BigDecimal.ONE);

        Player player = mock(Player.class);
        UUID playerId = UUID.randomUUID();
        when(player.getUniqueId()).thenReturn(playerId);

        LivingEntity victim = mock(LivingEntity.class);
        Location victimLoc = mock(Location.class);
        when(victim.getLocation()).thenReturn(victimLoc);
        when(victimLoc.getWorld()).thenReturn(mock(org.bukkit.World.class));

        when(schedulerBridge.runRegion(any(Location.class), any(Runnable.class))).thenReturn(mock(TaskHandle.class));

        service.triggerOnHitPower(player, victim, power, forgeId);
        service.triggerOnHitPower(player, victim, power, forgeId);

        service.clearHitCountersForPlayerAndForge(playerId, forgeId);

        boolean afterClearFirstHit = service.triggerOnHitPower(player, victim, power, forgeId);
        assertFalse(afterClearFirstHit, "After player+forge counter clear, first hit should not trigger");
    }

    @Test
    void clearAllRemovesCounters() {
        JavaPlugin plugin = mock(JavaPlugin.class);
        SchedulerBridge schedulerBridge = mock(SchedulerBridge.class);
        PotionEffectResolver potionEffectResolver = mock(PotionEffectResolver.class);
        EquipmentBridge equipmentBridge = mock(EquipmentBridge.class);
        ItemIdentityService identityService = mock(ItemIdentityService.class);
        UUID forgeId = UUID.randomUUID();

        ForgePowerService service = new ForgePowerService(plugin, schedulerBridge,
            potionEffectResolver, equipmentBridge, identityService);
        ForgePowerDefinition power = mock(ForgePowerDefinition.class);
        when(power.getPowerType()).thenReturn(ForgePowerDefinition.PowerType.EVERY_N_HIT_LIGHTNING);
        when(power.getId()).thenReturn("lightning_power");
        when(power.getCooldownTicks()).thenReturn(0);
        when(power.getHitInterval()).thenReturn(2);
        when(power.getChance()).thenReturn(BigDecimal.ONE);

        Player player = mock(Player.class);
        UUID playerId = UUID.randomUUID();
        when(player.getUniqueId()).thenReturn(playerId);

        LivingEntity victim = mock(LivingEntity.class);
        Location victimLoc = mock(Location.class);
        when(victim.getLocation()).thenReturn(victimLoc);
        when(victimLoc.getWorld()).thenReturn(mock(org.bukkit.World.class));

        when(schedulerBridge.runRegion(any(Location.class), any(Runnable.class))).thenReturn(mock(TaskHandle.class));

        service.triggerOnHitPower(player, victim, power, forgeId);
        service.triggerOnHitPower(player, victim, power, forgeId);

        service.clearAll();

        boolean afterClearFirstHit = service.triggerOnHitPower(player, victim, power, forgeId);
        assertFalse(afterClearFirstHit, "After clearAll, first hit should not trigger");
    }

    @Test
    void clearAllCancelsPassiveTasks() {
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
        when(potionEffectResolver.resolve(anyList())).thenReturn(Optional.of(PotionEffectType.SPEED));

        Player player = mock(Player.class);
        UUID playerId = UUID.randomUUID();
        when(player.getUniqueId()).thenReturn(playerId);
        when(player.isOnline()).thenReturn(true);
        when(player.getInventory()).thenReturn(mock(org.bukkit.inventory.PlayerInventory.class));

        ItemStack mainHand = mock(ItemStack.class);
        when(mainHand.hasItemMeta()).thenReturn(true);
        when(mainHand.getType()).thenReturn(Material.DIAMOND_SWORD);
        when(equipmentBridge.getItem(player, EquipmentBridge.Slot.MAINHAND)).thenReturn(mainHand);
        when(equipmentBridge.getItem(player, EquipmentBridge.Slot.OFFHAND)).thenReturn(null);

        ItemIdentityCodec.Identity identityData = ItemIdentityCodec.Identity.empty()
            .withForgeId(forgeId)
            .withBaseMaterial("DIAMOND_SWORD")
            .withBaseDisplayName("Diamond Sword");
        when(identityService.readForgeIdentity(mainHand)).thenReturn(
            new ItemIdentityService.ForgeIdentityRead(
                ItemIdentityService.ForgeIdentityStatus.VALID,
                identityData
            )
        );
        when(identityService.defaultBaseDisplayName(any())).thenReturn("Diamond Sword");

        TaskHandle taskHandle = mock(TaskHandle.class);
        when(schedulerBridge.runEntityLater(any(), any(), any(), anyLong())).thenReturn(taskHandle);

        boolean activated = service.activatePassivePower(player, power, forgeId);

        if (activated) {
            service.clearAllPassiveTasks();
            verify(taskHandle).cancel();
        }
    }
}
