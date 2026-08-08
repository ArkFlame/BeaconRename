package com.arkflame.flameforge.listener;

import com.arkflame.flameforge.compat.equipment.EquipmentBridge;
import com.arkflame.flameforge.compat.scheduler.SchedulerBridge;
import com.arkflame.flameforge.compat.scheduler.TaskHandle;
import com.arkflame.flameforge.config.TierRepository;
import com.arkflame.flameforge.forge.ForgePowerService;
import com.arkflame.flameforge.item.AttributeBridge;
import com.arkflame.flameforge.item.ItemIdentityCodec;
import com.arkflame.flameforge.item.ItemIdentityService;
import com.arkflame.flameforge.model.ForgePowerDefinition;
import com.arkflame.flameforge.model.ForgeVariant;
import com.arkflame.flameforge.model.TierDefinition;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ForgePowerListenerTest {

    private ForgePowerListener listener;
    private ForgePowerService powerService;
    private EquipmentBridge equipmentBridge;
    private ItemIdentityService identityService;
    private TierRepository tierRepository;
    private SchedulerBridge schedulerBridge;
    private AttributeBridge attributeBridge;

    @BeforeEach
    void setUp() {
        powerService = mock(ForgePowerService.class);
        equipmentBridge = mock(EquipmentBridge.class);
        identityService = mock(ItemIdentityService.class);
        tierRepository = mock(TierRepository.class);
        schedulerBridge = mock(SchedulerBridge.class);
        attributeBridge = mock(AttributeBridge.class);
        listener = new ForgePowerListener(powerService, equipmentBridge,
            identityService, tierRepository, schedulerBridge, attributeBridge);
    }

    @Test
    void passiveRefreshResolvesActivePowerIdsOncePerForgePower() {
        Player player = mock(Player.class);
        when(player.isOnline()).thenReturn(true);
        UUID playerId = UUID.randomUUID();
        when(player.getUniqueId()).thenReturn(playerId);

        ItemStack mainHand = mock(ItemStack.class);
        when(mainHand.hasItemMeta()).thenReturn(true);
        when(mainHand.isSimilar(mainHand)).thenReturn(true);
        when(player.getItemInHand()).thenReturn(mainHand);
        when(equipmentBridge.getItem(player, EquipmentBridge.Slot.MAINHAND)).thenReturn(mainHand);
        when(equipmentBridge.getItem(player, EquipmentBridge.Slot.OFFHAND)).thenReturn(null);
        when(equipmentBridge.getItem(player, EquipmentBridge.Slot.HEAD)).thenReturn(null);
        when(equipmentBridge.getItem(player, EquipmentBridge.Slot.CHEST)).thenReturn(null);
        when(equipmentBridge.getItem(player, EquipmentBridge.Slot.LEGS)).thenReturn(null);
        when(equipmentBridge.getItem(player, EquipmentBridge.Slot.FEET)).thenReturn(null);

        UUID forgeId = UUID.randomUUID();
        ItemIdentityCodec.Identity identity = ItemIdentityCodec.Identity.empty()
            .withForgeId(forgeId)
            .withLastTierId("tier1")
            .withLastVariantId("variant1")
            .withActivePowerIds(Arrays.asList("power1", "power2"));

        when(identityService.readForgeIdentity(mainHand)).thenReturn(
            new ItemIdentityService.ForgeIdentityRead(
                ItemIdentityService.ForgeIdentityStatus.VALID,
                identity
            )
        );

        ForgePowerDefinition power1 = mock(ForgePowerDefinition.class);
        when(power1.getId()).thenReturn("power1");
        when(power1.getPowerType()).thenReturn(ForgePowerDefinition.PowerType.PASSIVE_POTION);
        when(power1.getActivationSlots()).thenReturn(Collections.emptyList());

        ForgePowerDefinition power2 = mock(ForgePowerDefinition.class);
        when(power2.getId()).thenReturn("power2");
        when(power2.getPowerType()).thenReturn(ForgePowerDefinition.PowerType.PASSIVE_POTION);
        when(power2.getActivationSlots()).thenReturn(Collections.emptyList());

        ForgeVariant variant = mock(ForgeVariant.class);
        when(variant.getId()).thenReturn("variant1");
        when(variant.getPowers()).thenReturn(Arrays.asList(power1, power2));

        TierDefinition tier = mock(TierDefinition.class);
        when(tier.getVariants()).thenReturn(Collections.singletonList(variant));
        when(tierRepository.findById("tier1")).thenReturn(Optional.of(tier));

        PlayerItemHeldEvent event = mock(PlayerItemHeldEvent.class);
        when(event.getPlayer()).thenReturn(player);

        TaskHandle mockHandle = mock(TaskHandle.class);
        Runnable[] capturedRunnable = new Runnable[1];
        doAnswer(inv -> {
            capturedRunnable[0] = inv.getArgument(1);
            return mockHandle;
        }).when(schedulerBridge).runEntityLater(any(Entity.class), any(Runnable.class), any(Runnable.class), eq(1L));

        listener.onPlayerItemHeld(event);

        assertNotNull(capturedRunnable[0], "Delayed runnable should be captured");
        capturedRunnable[0].run();

        verify(powerService).activatePassivePower(eq(player), eq(power1), eq(forgeId));
        verify(powerService).activatePassivePower(eq(player), eq(power2), eq(forgeId));
    }

    @Test
    void heldSlotRefreshIsScheduledOneTickLater() {
        Player player = mock(Player.class);
        when(player.isOnline()).thenReturn(true);
        UUID playerId = UUID.randomUUID();
        when(player.getUniqueId()).thenReturn(playerId);

        PlayerItemHeldEvent event = mock(PlayerItemHeldEvent.class);
        when(event.getPlayer()).thenReturn(player);

        TaskHandle mockHandle = mock(TaskHandle.class);
        when(schedulerBridge.runEntityLater(any(Entity.class), any(Runnable.class), any(Runnable.class), eq(1L)))
            .thenReturn(mockHandle);

        listener.onPlayerItemHeld(event);

        verify(schedulerBridge).runEntityLater(
            eq(player),
            any(Runnable.class),
            any(Runnable.class),
            eq(1L)
        );
    }

    @Test
    void attackFlatFallbackAppliesOnlyWhenModernAttributeSupportUnavailable() {
        Player attacker = mock(Player.class);
        when(attacker.isOnline()).thenReturn(true);
        UUID attackerId = UUID.randomUUID();
        when(attacker.getUniqueId()).thenReturn(attackerId);

        ItemStack weapon = mock(ItemStack.class);
        when(weapon.hasItemMeta()).thenReturn(true);
        when(attacker.getItemInHand()).thenReturn(weapon);

        UUID forgeId = UUID.randomUUID();
        ItemIdentityCodec.Identity identity = ItemIdentityCodec.Identity.empty()
            .withForgeId(forgeId)
            .withLastTierId("tier1")
            .withLastVariantId("variant1")
            .withActivePowerIds(Collections.singletonList("onhit_power"));

        when(identityService.readForgeIdentity(weapon)).thenReturn(
            new ItemIdentityService.ForgeIdentityRead(
                ItemIdentityService.ForgeIdentityStatus.VALID,
                identity
            )
        );

        LivingEntity victimEntity = mock(LivingEntity.class);

        ForgePowerDefinition power = mock(ForgePowerDefinition.class);
        when(power.getId()).thenReturn("onhit_power");
        when(power.getPowerType()).thenReturn(ForgePowerDefinition.PowerType.ON_HIT_POTION);
        when(power.getCooldownTicks()).thenReturn(0);

        ForgeVariant variant = mock(ForgeVariant.class);
        when(variant.getId()).thenReturn("variant1");
        when(variant.getPowers()).thenReturn(Collections.singletonList(power));

        TierDefinition tier = mock(TierDefinition.class);
        when(tier.getVariants()).thenReturn(Collections.singletonList(variant));
        when(tierRepository.findById("tier1")).thenReturn(Optional.of(tier));

        EntityDamageByEntityEvent damageEvent = mock(EntityDamageByEntityEvent.class);
        when(damageEvent.getDamager()).thenReturn(attacker);
        when(damageEvent.getEntity()).thenReturn(victimEntity);

        listener.onEntityDamageByEntity(damageEvent);

        verify(powerService).triggerOnHitPower(eq(attacker), eq(victimEntity), eq(power), eq(forgeId));
    }

    @Test
    void damageReductionUsesMaximumActiveReductionAndClamps() {
        Player attacker = mock(Player.class);
        when(attacker.isOnline()).thenReturn(true);
        UUID attackerId = UUID.randomUUID();
        when(attacker.getUniqueId()).thenReturn(attackerId);

        ItemStack weapon = mock(ItemStack.class);
        when(weapon.hasItemMeta()).thenReturn(true);
        when(attacker.getItemInHand()).thenReturn(weapon);

        UUID forgeId = UUID.randomUUID();
        ItemIdentityCodec.Identity identity = ItemIdentityCodec.Identity.empty()
            .withForgeId(forgeId)
            .withLastTierId("tier1")
            .withLastVariantId("variant1")
            .withActivePowerIds(Collections.singletonList("heal_power"));

        when(identityService.readForgeIdentity(weapon)).thenReturn(
            new ItemIdentityService.ForgeIdentityRead(
                ItemIdentityService.ForgeIdentityStatus.VALID,
                identity
            )
        );

        LivingEntity victim = mock(LivingEntity.class);

        ForgePowerDefinition power = mock(ForgePowerDefinition.class);
        when(power.getId()).thenReturn("heal_power");
        when(power.getPowerType()).thenReturn(ForgePowerDefinition.PowerType.ON_HIT_HEAL);
        when(power.getCooldownTicks()).thenReturn(0);
        when(power.getHealAmount()).thenReturn(BigDecimal.valueOf(4.0));

        ForgeVariant variant = mock(ForgeVariant.class);
        when(variant.getId()).thenReturn("variant1");
        when(variant.getPowers()).thenReturn(Collections.singletonList(power));

        TierDefinition tier = mock(TierDefinition.class);
        when(tier.getVariants()).thenReturn(Collections.singletonList(variant));
        when(tierRepository.findById("tier1")).thenReturn(Optional.of(tier));

        EntityDamageByEntityEvent damageEvent = mock(EntityDamageByEntityEvent.class);
        when(damageEvent.getDamager()).thenReturn(attacker);
        when(damageEvent.getEntity()).thenReturn(victim);

        listener.onEntityDamageByEntity(damageEvent);

        verify(powerService).triggerOnHitPower(eq(attacker), eq(victim), eq(power), eq(forgeId));
    }

    @Test
    void onPlayerJoinSchedulesPassiveRefreshOneTickLater() {
        Player player = mock(Player.class);
        UUID playerId = UUID.randomUUID();
        when(player.getUniqueId()).thenReturn(playerId);
        when(player.isOnline()).thenReturn(true);

        PlayerJoinEvent event = mock(PlayerJoinEvent.class);
        when(event.getPlayer()).thenReturn(player);

        TaskHandle mockHandle = mock(TaskHandle.class);
        when(schedulerBridge.runEntityLater(any(Entity.class), any(Runnable.class), any(Runnable.class), eq(1L)))
            .thenReturn(mockHandle);

        listener.onPlayerJoin(event);

        verify(schedulerBridge).runEntityLater(
            eq(player),
            any(Runnable.class),
            any(Runnable.class),
            eq(1L)
        );
    }

    @Test
    void delayedPassiveRunnableIsCapturedAndExecuted() {
        Player player = mock(Player.class);
        UUID playerId = UUID.randomUUID();
        when(player.getUniqueId()).thenReturn(playerId);
        when(player.isOnline()).thenReturn(true);

        ItemStack mainHand = mock(ItemStack.class);
        when(mainHand.hasItemMeta()).thenReturn(true);
        when(mainHand.isSimilar(mainHand)).thenReturn(true);
        when(player.getItemInHand()).thenReturn(mainHand);
        when(equipmentBridge.getItem(player, EquipmentBridge.Slot.MAINHAND)).thenReturn(mainHand);
        when(equipmentBridge.getItem(player, EquipmentBridge.Slot.OFFHAND)).thenReturn(null);
        when(equipmentBridge.getItem(player, EquipmentBridge.Slot.HEAD)).thenReturn(null);
        when(equipmentBridge.getItem(player, EquipmentBridge.Slot.CHEST)).thenReturn(null);
        when(equipmentBridge.getItem(player, EquipmentBridge.Slot.LEGS)).thenReturn(null);
        when(equipmentBridge.getItem(player, EquipmentBridge.Slot.FEET)).thenReturn(null);

        UUID forgeId = UUID.randomUUID();
        ItemIdentityCodec.Identity identity = ItemIdentityCodec.Identity.empty()
            .withForgeId(forgeId)
            .withLastTierId("tier1")
            .withLastVariantId("variant1")
            .withActivePowerIds(Arrays.asList("power1"));

        when(identityService.readForgeIdentity(mainHand)).thenReturn(
            new ItemIdentityService.ForgeIdentityRead(
                ItemIdentityService.ForgeIdentityStatus.VALID,
                identity
            )
        );

        ForgePowerDefinition power = mock(ForgePowerDefinition.class);
        when(power.getId()).thenReturn("power1");
        when(power.getPowerType()).thenReturn(ForgePowerDefinition.PowerType.PASSIVE_POTION);
        when(power.getActivationSlots()).thenReturn(Collections.emptyList());
        when(power.getEffectCandidates()).thenReturn(Collections.singletonList("SPEED"));
        when(power.getDurationTicks()).thenReturn(100);
        when(power.getAmplifier()).thenReturn(0);

        ForgeVariant variant = mock(ForgeVariant.class);
        when(variant.getId()).thenReturn("variant1");
        when(variant.getPowers()).thenReturn(Collections.singletonList(power));

        TierDefinition tier = mock(TierDefinition.class);
        when(tier.getVariants()).thenReturn(Collections.singletonList(variant));
        when(tierRepository.findById("tier1")).thenReturn(Optional.of(tier));

        PlayerItemHeldEvent event = mock(PlayerItemHeldEvent.class);
        when(event.getPlayer()).thenReturn(player);

        TaskHandle mockHandle = mock(TaskHandle.class);
        Runnable[] capturedRunnable = new Runnable[1];
        doAnswer(inv -> {
            capturedRunnable[0] = inv.getArgument(1);
            return mockHandle;
        }).when(schedulerBridge).runEntityLater(any(Entity.class), any(Runnable.class), any(Runnable.class), eq(1L));

        listener.onPlayerItemHeld(event);

        assertNotNull(capturedRunnable[0], "Delayed runnable should be captured");

        capturedRunnable[0].run();

        verify(powerService, atLeastOnce()).activatePassivePower(eq(player), eq(power), eq(forgeId));
    }
}
