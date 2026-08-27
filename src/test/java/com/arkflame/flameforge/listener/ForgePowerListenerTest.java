package com.arkflame.flameforge.listener;

import com.arkflame.flameforge.compat.equipment.EquipmentBridge;
import com.arkflame.flameforge.compat.interaction.InteractionHandBridge;
import com.arkflame.flameforge.compat.scheduler.SchedulerBridge;
import com.arkflame.flameforge.compat.scheduler.TaskHandle;
import com.arkflame.flameforge.config.TierRepository;
import com.arkflame.flameforge.forge.ForgePowerService;
import com.arkflame.flameforge.item.AttributeBridge;
import com.arkflame.flameforge.item.ItemIdentityCodec;
import com.arkflame.flameforge.item.ItemIdentityService;
import com.arkflame.flameforge.model.ForgeAttributeDefinition;
import com.arkflame.flameforge.model.ForgePowerDefinition;
import com.arkflame.flameforge.model.ForgeVariant;
import com.arkflame.flameforge.model.TierDefinition;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemBreakEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerPickupItemEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.Material;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ForgePowerListenerTest {

    private static final class Harness {
        final ForgePowerService powerService;
        final EquipmentBridge equipmentBridge;
        final ItemIdentityService identityService;
        final TierRepository tierRepository;
        final SchedulerBridge schedulerBridge;
        final AttributeBridge attributeBridge;
        final InteractionHandBridge handBridge;
        final ForgePowerListener listener;
        final List<Runnable> deferredRunnables = new ArrayList<>();
        final List<TaskHandle> deferredHandles = new ArrayList<>();

        Harness() {
            powerService = mock(ForgePowerService.class);
            equipmentBridge = mock(EquipmentBridge.class);
            identityService = mock(ItemIdentityService.class);
            tierRepository = mock(TierRepository.class);
            schedulerBridge = mock(SchedulerBridge.class);
            attributeBridge = mock(AttributeBridge.class);
            handBridge = mock(InteractionHandBridge.class);
            listener = new ForgePowerListener(powerService, equipmentBridge, identityService,
                tierRepository, schedulerBridge, attributeBridge, handBridge);
        }

        Harness withSynchronousScheduler() {
            doAnswer(invocation -> {
                ((Runnable) invocation.getArgument(1)).run();
                return mock(TaskHandle.class);
            }).when(schedulerBridge).runEntityLater(
                any(Entity.class), any(Runnable.class), any(Runnable.class), anyLong());
            return this;
        }

        Harness withDeferredScheduler() {
            doAnswer(invocation -> {
                deferredRunnables.add(invocation.getArgument(1));
                TaskHandle handle = mock(TaskHandle.class);
                deferredHandles.add(handle);
                return handle;
            }).when(schedulerBridge).runEntityLater(
                any(Entity.class), any(Runnable.class), any(Runnable.class), anyLong());
            return this;
        }
    }

    @Test
    void joinAndHeldEventsTriggerServicePassiveRefresh() {
        Harness harness = new Harness().withSynchronousScheduler();
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        PlayerJoinEvent join = mock(PlayerJoinEvent.class);
        when(join.getPlayer()).thenReturn(player);
        PlayerItemHeldEvent held = mock(PlayerItemHeldEvent.class);
        when(held.getPlayer()).thenReturn(player);

        harness.listener.onPlayerJoin(join);
        harness.listener.onPlayerItemHeld(held);

        verify(harness.powerService, atLeastOnce()).refreshPassivePowers(player);
    }

    @Test
    void sneakingRightClickActivatesDashFromMainHandItem() {
        Harness harness = new Harness().withSynchronousScheduler();
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(player.isOnline()).thenReturn(true);
        when(player.isSneaking()).thenReturn(true);
        PlayerInteractEvent interact = mock(PlayerInteractEvent.class);
        when(interact.getAction()).thenReturn(Action.RIGHT_CLICK_AIR);
        when(interact.getPlayer()).thenReturn(player);
        when(harness.handBridge.getHand(interact)).thenReturn(InteractionHandBridge.Hand.MAIN);
        UUID forgeId = UUID.randomUUID();
        ItemStack dashItem = forgedItemWithPower(harness, "dash-tier", "dash-variant", "dash",
            forgeId, ForgePowerDefinition.PowerType.SHIFT_RIGHT_CLICK_DASH);
        when(harness.equipmentBridge.getItem(player, EquipmentBridge.Slot.MAINHAND)).thenReturn(dashItem);

        harness.listener.onPlayerInteract(interact);

        verify(harness.powerService).activateDash(eq(player), any(ForgePowerDefinition.class), eq(forgeId));
        verify(harness.equipmentBridge, never()).getItem(player, EquipmentBridge.Slot.OFFHAND);
    }

    @Test
    void offHandSneakingRightClickActivatesOffhandItem() {
        Harness harness = new Harness().withSynchronousScheduler();
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(player.isOnline()).thenReturn(true);
        when(player.isSneaking()).thenReturn(true);
        PlayerInteractEvent interact = mock(PlayerInteractEvent.class);
        when(interact.getAction()).thenReturn(Action.RIGHT_CLICK_BLOCK);
        when(interact.getPlayer()).thenReturn(player);
        when(harness.handBridge.getHand(interact)).thenReturn(InteractionHandBridge.Hand.OFF);
        UUID forgeId = UUID.randomUUID();
        ItemStack offItem = forgedItemWithPower(harness, "off-tier", "off-variant", "heal",
            forgeId, ForgePowerDefinition.PowerType.SHIFT_RIGHT_CLICK_HEAL);
        when(harness.equipmentBridge.getItem(player, EquipmentBridge.Slot.OFFHAND)).thenReturn(offItem);

        harness.listener.onPlayerInteract(interact);

        verify(harness.powerService).activateHeal(eq(player), any(ForgePowerDefinition.class), eq(forgeId));
        verify(harness.equipmentBridge, never()).getItem(player, EquipmentBridge.Slot.MAINHAND);
    }

    @Test
    void cursedRightClickCannotActivateActivePower() {
        Harness harness = new Harness().withSynchronousScheduler();
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(player.isOnline()).thenReturn(true);
        when(player.isSneaking()).thenReturn(true);
        PlayerInteractEvent interact = mock(PlayerInteractEvent.class);
        when(interact.getAction()).thenReturn(Action.RIGHT_CLICK_AIR);
        when(interact.getPlayer()).thenReturn(player);
        when(harness.handBridge.getHand(interact)).thenReturn(InteractionHandBridge.Hand.MAIN);
        ItemStack item = forgedItemWithPower(harness, "cursed-tier", "cursed-variant", "dash",
            UUID.randomUUID(), ForgePowerDefinition.PowerType.SHIFT_RIGHT_CLICK_DASH, true);
        when(harness.equipmentBridge.getItem(player, EquipmentBridge.Slot.MAINHAND)).thenReturn(item);

        harness.listener.onPlayerInteract(interact);

        verify(harness.powerService, never()).activateDash(any(), any(), any());
        verify(harness.powerService, never()).activateHeal(any(), any(), any());
    }

    @Test
    void nonSneakingRightClickActivatesNothing() {
        Harness harness = new Harness().withSynchronousScheduler();
        Player player = mock(Player.class);
        when(player.isOnline()).thenReturn(true);
        when(player.isSneaking()).thenReturn(false);
        PlayerInteractEvent interact = mock(PlayerInteractEvent.class);
        when(interact.getAction()).thenReturn(Action.RIGHT_CLICK_AIR);
        when(interact.getPlayer()).thenReturn(player);

        harness.listener.onPlayerInteract(interact);

        verifyNoInteractions(harness.powerService);
    }

    @Test
    void rightClickArmorItemQueuesPassiveRefreshWithoutSneaking() {
        Harness harness = new Harness().withSynchronousScheduler();
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(player.isOnline()).thenReturn(true);
        when(player.isSneaking()).thenReturn(false);
        PlayerInteractEvent interact = mock(PlayerInteractEvent.class);
        when(interact.getAction()).thenReturn(Action.RIGHT_CLICK_AIR);
        when(interact.getPlayer()).thenReturn(player);
        when(harness.handBridge.getHand(interact)).thenReturn(InteractionHandBridge.Hand.MAIN);
        ItemStack armorItem = mock(ItemStack.class);
        when(armorItem.hasItemMeta()).thenReturn(true);
        when(armorItem.getType()).thenReturn(Material.DIAMOND_CHESTPLATE);
        when(harness.equipmentBridge.getItem(player, EquipmentBridge.Slot.MAINHAND)).thenReturn(armorItem);
        when(harness.identityService.readForgeIdentity(armorItem)).thenReturn(
            new ItemIdentityService.ForgeIdentityRead(
                ItemIdentityService.ForgeIdentityStatus.INVALID, ItemIdentityCodec.Identity.empty()));
        when(harness.tierRepository.findEquipmentCategory(Material.DIAMOND_CHESTPLATE))
            .thenReturn(Optional.of("armor"));

        harness.listener.onPlayerInteract(interact);

        verify(harness.powerService).refreshPassivePowers(player);
        verify(harness.powerService, never()).activateDash(any(), any(), any());
        verify(harness.powerService, never()).activateHeal(any(), any(), any());
    }

    @Test
    void rightClickDashStillActivatesAndCoalescesPassiveRefresh() {
        Harness harness = new Harness().withDeferredScheduler();
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(player.isOnline()).thenReturn(true);
        when(player.isSneaking()).thenReturn(true);
        PlayerInteractEvent interact = mock(PlayerInteractEvent.class);
        when(interact.getAction()).thenReturn(Action.RIGHT_CLICK_AIR);
        when(interact.getPlayer()).thenReturn(player);
        when(harness.handBridge.getHand(interact)).thenReturn(InteractionHandBridge.Hand.MAIN);
        UUID forgeId = UUID.randomUUID();
        ItemStack dashItem = forgedItemWithPower(harness, "dash-tier", "dash-variant", "dash",
            forgeId, ForgePowerDefinition.PowerType.SHIFT_RIGHT_CLICK_DASH);
        when(harness.equipmentBridge.getItem(player, EquipmentBridge.Slot.MAINHAND)).thenReturn(dashItem);

        harness.listener.onPlayerInteract(interact);

        verify(harness.powerService).activateDash(eq(player), any(ForgePowerDefinition.class), eq(forgeId));
        verify(harness.powerService, never()).refreshPassivePowers(player);
        harness.deferredRunnables.get(0).run();
        verify(harness.powerService, times(1)).refreshPassivePowers(player);
    }

    @Test
    void burstMutationEventsCoalesceToOneFinalRefresh() {
        Harness harness = new Harness().withDeferredScheduler();
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());

        PlayerItemHeldEvent held = mock(PlayerItemHeldEvent.class);
        when(held.getPlayer()).thenReturn(player);
        InventoryClickEvent click = mock(InventoryClickEvent.class);
        when(click.getWhoClicked()).thenReturn(player);
        PlayerDropItemEvent drop = mock(PlayerDropItemEvent.class);
        when(drop.getPlayer()).thenReturn(player);

        harness.listener.onPlayerItemHeld(held);
        harness.listener.onInventoryClick(click);
        harness.listener.onPlayerDropItem(drop);

        assertEquals(3, harness.deferredRunnables.size());
        harness.deferredRunnables.get(2).run();
        verify(harness.powerService, times(1)).refreshPassivePowers(player);
    }

    @Test
    void wrongActionActivatesNothing() {
        Harness harness = new Harness().withSynchronousScheduler();
        PlayerInteractEvent interact = mock(PlayerInteractEvent.class);
        when(interact.getAction()).thenReturn(Action.LEFT_CLICK_AIR);

        harness.listener.onPlayerInteract(interact);

        verifyNoInteractions(harness.powerService);
    }

    @Test
    void playerHitDelegatesOnHitPower() {
        Harness harness = new Harness().withSynchronousScheduler();
        Player attacker = mock(Player.class);
        when(attacker.isOnline()).thenReturn(true);
        UUID forgeId = UUID.randomUUID();
        ItemStack weapon = forgedItemWithPower(harness, "weapon_tier1", "draining", "draining_heal",
            forgeId, ForgePowerDefinition.PowerType.ON_HIT_HEAL);
        when(attacker.getItemInHand()).thenReturn(weapon);
        LivingEntity victim = mock(LivingEntity.class);
        EntityDamageByEntityEvent combat = mock(EntityDamageByEntityEvent.class);
        when(combat.getDamager()).thenReturn(attacker);
        when(combat.getEntity()).thenReturn(victim);

        harness.listener.onEntityDamageByEntity(combat);

        verify(harness.powerService).tracePowerEvent(attacker, "ON_HIT_EVENT", "damager=PLAYER");
        verify(harness.powerService).tracePowerEvent(attacker, "ON_HIT_RESOLVED",
            "forge=" + forgeId + " tier=weapon_tier1 variant=draining powers=1");
        verify(harness.powerService).triggerOnHitPower(eq(attacker), eq(victim),
            any(ForgePowerDefinition.class), eq(forgeId), anyBoolean());
    }

    @Test
    void projectileHitFromPlayerIsTracedAndDoesNotTriggerOnHitPower() {
        Harness harness = new Harness().withSynchronousScheduler();
        Player shooter = mock(Player.class);
        Projectile projectile = mock(Projectile.class);
        when(projectile.getShooter()).thenReturn(shooter);
        LivingEntity victim = mock(LivingEntity.class);
        EntityDamageByEntityEvent combat = mock(EntityDamageByEntityEvent.class);
        when(combat.getDamager()).thenReturn(projectile);
        when(combat.getEntity()).thenReturn(victim);

        harness.listener.onEntityDamageByEntity(combat);

        verify(harness.powerService).tracePowerEvent(shooter, "PROJECTILE_HIT_EVENT_IGNORED",
            "projectile=" + projectile.getClass().getSimpleName());
        verify(harness.powerService, never()).triggerOnHitPower(any(), any(), any(), any(), anyBoolean());
    }

    @Test
    void invalidIdentityRejectsOnHitAndTracesStatus() {
        Harness harness = new Harness().withSynchronousScheduler();
        Player attacker = mock(Player.class);
        when(attacker.isOnline()).thenReturn(true);
        ItemStack weapon = mock(ItemStack.class);
        when(weapon.hasItemMeta()).thenReturn(true);
        when(attacker.getItemInHand()).thenReturn(weapon);
        when(harness.identityService.readForgeIdentity(weapon)).thenReturn(
            new ItemIdentityService.ForgeIdentityRead(
                ItemIdentityService.ForgeIdentityStatus.INVALID, ItemIdentityCodec.Identity.empty()));
        LivingEntity victim = mock(LivingEntity.class);
        EntityDamageByEntityEvent combat = mock(EntityDamageByEntityEvent.class);
        when(combat.getDamager()).thenReturn(attacker);
        when(combat.getEntity()).thenReturn(victim);

        harness.listener.onEntityDamageByEntity(combat);

        verify(harness.powerService).tracePowerEvent(attacker, "ON_HIT_EVENT", "damager=PLAYER");
        verify(harness.powerService).tracePowerEvent(attacker, "ON_HIT_REJECT_IDENTITY", "status=INVALID");
        verify(harness.powerService, never()).triggerOnHitPower(any(), any(), any(), any(), anyBoolean());
    }

    @Test
    void cursedPlayerHitCannotTriggerOnHitPower() {
        Harness harness = new Harness().withSynchronousScheduler();
        Player attacker = mock(Player.class);
        when(attacker.isOnline()).thenReturn(true);
        ItemStack weapon = forgedItemWithPower(harness, "cursed-hit-tier", "cursed-hit-variant", "hit",
            UUID.randomUUID(), ForgePowerDefinition.PowerType.ON_HIT_POTION, true);
        when(attacker.getItemInHand()).thenReturn(weapon);
        LivingEntity victim = mock(LivingEntity.class);
        EntityDamageByEntityEvent combat = mock(EntityDamageByEntityEvent.class);
        when(combat.getDamager()).thenReturn(attacker);
        when(combat.getEntity()).thenReturn(victim);

        harness.listener.onEntityDamageByEntity(combat);

        verify(harness.powerService).tracePowerEvent(attacker, "ON_HIT_EVENT", "damager=PLAYER");
        verify(harness.powerService).tracePowerEvent(attacker, "ON_HIT_REJECT_CURSED", "cursed=true");
        verify(harness.powerService, never()).triggerOnHitPower(any(), any(), any(), any(), anyBoolean());
    }

    @Test
    void blockingDefenderTriggersOnBlockPower() {
        Harness harness = new Harness().withSynchronousScheduler();
        Player defender = mock(Player.class);
        when(defender.getUniqueId()).thenReturn(UUID.randomUUID());
        when(defender.isBlocking()).thenReturn(true);
        UUID forgeId = UUID.randomUUID();
        ItemStack shield = forgedItemWithPower(harness, "block-tier", "block-variant", "block-power",
            forgeId, ForgePowerDefinition.PowerType.ON_BLOCK_POTION);
        when(defender.getItemInHand()).thenReturn(shield);
        when(harness.equipmentBridge.getItem(defender, EquipmentBridge.Slot.MAINHAND)).thenReturn(shield);
        when(harness.equipmentBridge.getInventoryContents(defender)).thenReturn(new ItemStack[0]);
        LivingEntity attacker = mock(LivingEntity.class);
        EntityDamageByEntityEvent combat = mock(EntityDamageByEntityEvent.class);
        when(combat.getDamager()).thenReturn(attacker);
        when(combat.getEntity()).thenReturn(defender);

        harness.listener.onEntityDamageByEntity(combat);

        verify(harness.powerService).triggerOnBlockPower(eq(defender), eq(attacker),
            any(ForgePowerDefinition.class), eq(forgeId));
    }

    @Test
    void cursedBlockingItemCannotTriggerOnBlockPower() {
        Harness harness = new Harness().withSynchronousScheduler();
        Player defender = mock(Player.class);
        when(defender.getUniqueId()).thenReturn(UUID.randomUUID());
        when(defender.isBlocking()).thenReturn(true);
        ItemStack shield = forgedItemWithPower(harness, "cursed-block-tier", "cursed-block-variant", "block",
            UUID.randomUUID(), ForgePowerDefinition.PowerType.ON_BLOCK_POTION, true);
        when(defender.getItemInHand()).thenReturn(shield);
        when(harness.equipmentBridge.getItem(defender, EquipmentBridge.Slot.MAINHAND)).thenReturn(shield);
        when(harness.equipmentBridge.getInventoryContents(defender)).thenReturn(new ItemStack[0]);
        LivingEntity attacker = mock(LivingEntity.class);
        EntityDamageByEntityEvent combat = mock(EntityDamageByEntityEvent.class);
        when(combat.getDamager()).thenReturn(attacker);
        when(combat.getEntity()).thenReturn(defender);

        harness.listener.onEntityDamageByEntity(combat);

        verify(harness.powerService, never()).triggerOnBlockPower(any(), any(), any(), any());
    }

    @Test
    void queuePassiveRefreshMergesPendingRefreshes() {
        Harness harness = new Harness().withDeferredScheduler();
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());

        harness.listener.queuePassiveRefresh(player);
        harness.listener.queuePassiveRefresh(player);

        assertEquals(2, harness.deferredRunnables.size());
        verify(harness.deferredHandles.get(0)).cancel();
        harness.deferredRunnables.get(1).run();
        verify(harness.powerService, times(1)).refreshPassivePowers(player);
    }

    @Test
    void queuePassiveRefreshWithNullPlayerUuidDoesNotThrowAndDoesNotSchedule() {
        Harness harness = new Harness().withDeferredScheduler();
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(null);

        harness.listener.queuePassiveRefresh(player);

        assertEquals(0, harness.deferredRunnables.size());
        verify(harness.schedulerBridge, never()).runEntityLater(
            any(Entity.class), any(Runnable.class), any(Runnable.class), anyLong());
        verify(harness.powerService, never()).refreshPassivePowers(player);
    }

    @Test
    void shutdownCancelsPendingRefreshAndServicePassives() {
        Harness harness = new Harness().withDeferredScheduler();
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        harness.listener.queuePassiveRefresh(player);

        harness.listener.shutdown();

        verify(harness.deferredHandles.get(0)).cancel();
        verify(harness.powerService).clearAllPassiveTasks();
    }

    @Test
    void allDirtyEventsQueuePassiveRefresh() {
        Harness harness = new Harness().withSynchronousScheduler();
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());

        PlayerJoinEvent join = mock(PlayerJoinEvent.class);
        when(join.getPlayer()).thenReturn(player);
        PlayerRespawnEvent respawn = mock(PlayerRespawnEvent.class);
        when(respawn.getPlayer()).thenReturn(player);
        PlayerItemHeldEvent held = mock(PlayerItemHeldEvent.class);
        when(held.getPlayer()).thenReturn(player);
        InventoryClickEvent click = mock(InventoryClickEvent.class);
        when(click.getWhoClicked()).thenReturn(player);
        InventoryDragEvent drag = mock(InventoryDragEvent.class);
        when(drag.getWhoClicked()).thenReturn(player);
        InventoryCloseEvent close = mock(InventoryCloseEvent.class);
        when(close.getPlayer()).thenReturn(player);
        PlayerDropItemEvent drop = mock(PlayerDropItemEvent.class);
        when(drop.getPlayer()).thenReturn(player);
        PlayerPickupItemEvent pickup = mock(PlayerPickupItemEvent.class);
        when(pickup.getPlayer()).thenReturn(player);
        PlayerItemConsumeEvent consume = mock(PlayerItemConsumeEvent.class);
        when(consume.getPlayer()).thenReturn(player);
        PlayerItemBreakEvent breakEvent = mock(PlayerItemBreakEvent.class);
        when(breakEvent.getPlayer()).thenReturn(player);

        harness.listener.onPlayerJoin(join);
        harness.listener.onPlayerRespawn(respawn);
        harness.listener.onPlayerItemHeld(held);
        harness.listener.onInventoryClick(click);
        harness.listener.onInventoryDrag(drag);
        harness.listener.onInventoryClose(close);
        harness.listener.onPlayerDropItem(drop);
        harness.listener.onPlayerPickupItem(pickup);
        harness.listener.onPlayerItemConsume(consume);
        harness.listener.onPlayerItemBreak(breakEvent);

        verify(harness.powerService, times(10)).refreshPassivePowers(player);
    }

    @Test
    void armorReductionCombinesGenericAndCauseSpecificCappedAt80Percent() {
        Harness harness = new Harness().withSynchronousScheduler();
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());

        ForgeAttributeDefinition generic = attribute(
            ForgeAttributeDefinition.AttributeType.DAMAGE_REDUCTION_PERCENT, "g-attr", 0.6);
        ForgeAttributeDefinition poison = attribute(
            ForgeAttributeDefinition.AttributeType.POISON_DAMAGE_REDUCTION_PERCENT, "p-attr", 0.3);
        wireTierWithVariants(harness, "tier", variant("v1", generic), variant("v2", poison));
        ItemStack helmet = attributeItem(harness, "tier", "v1", "g-attr");
        ItemStack boots = attributeItem(harness, "tier", "v2", "p-attr");
        when(harness.equipmentBridge.getItem(player, EquipmentBridge.Slot.HEAD)).thenReturn(helmet);
        when(harness.equipmentBridge.getItem(player, EquipmentBridge.Slot.FEET)).thenReturn(boots);

        EntityDamageEvent damage = mock(EntityDamageEvent.class);
        when(damage.getEntity()).thenReturn(player);
        when(damage.getCause()).thenReturn(EntityDamageEvent.DamageCause.POISON);
        when(damage.getDamage()).thenReturn(100.0);

        harness.listener.onEntityDamage(damage);

        ArgumentCaptor<Double> damageCaptor = ArgumentCaptor.forClass(Double.class);
        verify(damage).setDamage(damageCaptor.capture());
        assertEquals(20.0, damageCaptor.getValue(), 0.0001);
        verify(harness.powerService).emitArmorReductionParticle(player);
    }

    @Test
    void armorReductionAppliesOnlyForMatchingCause() {
        Harness harness = new Harness().withSynchronousScheduler();
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());

        ForgeAttributeDefinition poison = attribute(
            ForgeAttributeDefinition.AttributeType.POISON_DAMAGE_REDUCTION_PERCENT, "p-attr", 0.3);
        wireTierWithVariants(harness, "tier", variant("v1", poison));
        ItemStack boots = attributeItem(harness, "tier", "v1", "p-attr");
        when(harness.equipmentBridge.getItem(player, EquipmentBridge.Slot.FEET)).thenReturn(boots);

        EntityDamageEvent fall = mock(EntityDamageEvent.class);
        when(fall.getEntity()).thenReturn(player);
        when(fall.getCause()).thenReturn(EntityDamageEvent.DamageCause.FALL);
        when(fall.getDamage()).thenReturn(50.0);

        harness.listener.onEntityDamage(fall);

        verify(fall, never()).setDamage(anyDouble());
        verify(harness.powerService, never()).emitArmorReductionParticle(player);
    }

    @Test
    void cursedIdentityCannotProvideLegacyAttackBonusOrDamageReduction() {
        Harness harness = new Harness().withSynchronousScheduler();
        when(harness.attributeBridge.isModernAttributesAvailable()).thenReturn(false);
        Player attacker = mock(Player.class);
        ForgeAttributeDefinition attack = attribute(
            ForgeAttributeDefinition.AttributeType.ATTACK_DAMAGE_FLAT, "attack", 5.0);
        wireTierWithVariants(harness, "attack-tier", variant("attack-variant", attack));
        ItemStack weapon = attributeItem(harness, "attack-tier", "attack-variant", "attack", true);
        when(attacker.getItemInHand()).thenReturn(weapon);
        LivingEntity victim = mock(LivingEntity.class);
        EntityDamageByEntityEvent combat = mock(EntityDamageByEntityEvent.class);
        when(combat.getDamager()).thenReturn(attacker);
        when(combat.getEntity()).thenReturn(victim);
        when(combat.getDamage()).thenReturn(2.0);
        harness.listener.onEntityDamageByEntity(combat);
        verify(combat, never()).setDamage(anyDouble());

        Player protectedPlayer = mock(Player.class);
        ForgeAttributeDefinition reduction = attribute(
            ForgeAttributeDefinition.AttributeType.DAMAGE_REDUCTION_PERCENT, "reduction", 0.5);
        wireTierWithVariants(harness, "reduction-tier", variant("reduction-variant", reduction));
        ItemStack cursedArmor = attributeItem(harness, "reduction-tier", "reduction-variant", "reduction", true);
        when(harness.equipmentBridge.getItem(protectedPlayer, EquipmentBridge.Slot.HEAD)).thenReturn(cursedArmor);
        EntityDamageEvent damage = mock(EntityDamageEvent.class);
        when(damage.getEntity()).thenReturn(protectedPlayer);
        when(damage.getCause()).thenReturn(EntityDamageEvent.DamageCause.FALL);
        when(damage.getDamage()).thenReturn(10.0);
        harness.listener.onEntityDamage(damage);

        verify(damage, never()).setDamage(anyDouble());
        verify(harness.powerService, never()).emitArmorReductionParticle(protectedPlayer);
    }

    @Test
    void lethalDamagePassesLethalFlagAfterAttackBonus() {
        Harness harness = new Harness().withSynchronousScheduler();
        Player attacker = mock(Player.class);
        when(attacker.isOnline()).thenReturn(true);
        UUID forgeId = UUID.randomUUID();
        ItemStack weapon = forgedItemWithPower(harness, "lethal-tier", "lethal-variant", "fire",
            forgeId, ForgePowerDefinition.PowerType.ON_HIT_FIRE);
        when(attacker.getItemInHand()).thenReturn(weapon);
        LivingEntity victim = mock(LivingEntity.class);
        when(victim.getHealth()).thenReturn(10.0);
        EntityDamageByEntityEvent combat = mock(EntityDamageByEntityEvent.class);
        when(combat.getDamager()).thenReturn(attacker);
        when(combat.getEntity()).thenReturn(victim);
        when(combat.getFinalDamage()).thenReturn(10.0);

        harness.listener.onEntityDamageByEntity(combat);

        verify(harness.powerService).triggerOnHitPower(eq(attacker), eq(victim),
            any(ForgePowerDefinition.class), eq(forgeId), eq(true));
    }

    @Test
    void byEntityDamageIsNotDoubleReduced() {
        Harness harness = new Harness().withSynchronousScheduler();
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        EntityDamageByEntityEvent damage = mock(EntityDamageByEntityEvent.class);
        when(damage.getEntity()).thenReturn(player);
        when(damage.getCause()).thenReturn(EntityDamageEvent.DamageCause.FALL);
        when(damage.getDamage()).thenReturn(50.0);

        harness.listener.onEntityDamage(damage);

        verify(damage, never()).setDamage(anyDouble());
        verify(harness.powerService, never()).emitArmorReductionParticle(player);
        verifyNoInteractions(harness.equipmentBridge, harness.identityService, harness.tierRepository);
    }

    @Test
    void offhandSwapRegistrationIsLegacySafeWhenClassAbsent() {
        EquipmentBridge bridge = new EquipmentBridge();
        JavaPlugin plugin = mock(JavaPlugin.class);
        Consumer<Player> callback = mock(Consumer.class);

        boolean registered = bridge.registerOffhandSwapListener(plugin, callback);

        assertFalse(registered);
    }

    private ItemStack forgedItemWithPower(Harness harness, String tierId, String variantId,
                                          String powerId, UUID forgeId,
                                          ForgePowerDefinition.PowerType powerType) {
        return forgedItemWithPower(harness, tierId, variantId, powerId, forgeId, powerType, false);
    }

    private ItemStack forgedItemWithPower(Harness harness, String tierId, String variantId,
                                          String powerId, UUID forgeId,
                                          ForgePowerDefinition.PowerType powerType, boolean cursed) {
        ItemStack item = mock(ItemStack.class);
        when(item.hasItemMeta()).thenReturn(true);
        when(item.isSimilar(item)).thenReturn(true);
        ItemIdentityCodec.Identity identity = ItemIdentityCodec.Identity.empty()
            .withForgeId(forgeId)
            .withLastTierId(tierId)
            .withLastVariantId(variantId)
            .withActivePowerIds(Collections.singletonList(powerId))
            .withCursed(cursed);
        when(harness.identityService.readForgeIdentity(item)).thenReturn(
            new ItemIdentityService.ForgeIdentityRead(
                ItemIdentityService.ForgeIdentityStatus.VALID, identity));
        ForgePowerDefinition power = mock(ForgePowerDefinition.class);
        when(power.getId()).thenReturn(powerId);
        when(power.getPowerType()).thenReturn(powerType);
        when(power.getActivationSlots()).thenReturn(Collections.emptyList());
        ForgeVariant variant = mock(ForgeVariant.class);
        when(variant.getId()).thenReturn(variantId);
        when(variant.getPowers()).thenReturn(Collections.singletonList(power));
        when(variant.getAttributes()).thenReturn(Collections.emptyList());
        TierDefinition tier = mock(TierDefinition.class);
        when(tier.getVariants()).thenReturn(Collections.singletonList(variant));
        when(harness.tierRepository.findById(tierId)).thenReturn(Optional.of(tier));
        return item;
    }

    private ForgeAttributeDefinition attribute(ForgeAttributeDefinition.AttributeType type,
                                               String id, double multiplier) {
        ForgeAttributeDefinition attr = mock(ForgeAttributeDefinition.class);
        when(attr.getId()).thenReturn(id);
        when(attr.getType()).thenReturn(type);
        when(attr.getMultiplier()).thenReturn(multiplier);
        return attr;
    }

    private ForgeVariant variant(String id, ForgeAttributeDefinition... attributes) {
        ForgeVariant variant = mock(ForgeVariant.class);
        when(variant.getId()).thenReturn(id);
        when(variant.getPowers()).thenReturn(Collections.emptyList());
        when(variant.getAttributes()).thenReturn(Arrays.asList(attributes));
        return variant;
    }

    private void wireTierWithVariants(Harness harness, String tierId, ForgeVariant... variants) {
        TierDefinition tier = mock(TierDefinition.class);
        when(tier.getVariants()).thenReturn(Arrays.asList(variants));
        when(harness.tierRepository.findById(tierId)).thenReturn(Optional.of(tier));
    }

    private ItemStack attributeItem(Harness harness, String tierId, String variantId, String attrId) {
        return attributeItem(harness, tierId, variantId, attrId, false);
    }

    private ItemStack attributeItem(Harness harness, String tierId, String variantId,
                                    String attrId, boolean cursed) {
        ItemStack item = mock(ItemStack.class);
        when(item.hasItemMeta()).thenReturn(true);
        ItemIdentityCodec.Identity identity = ItemIdentityCodec.Identity.empty()
            .withForgeId(UUID.randomUUID())
            .withLastTierId(tierId)
            .withLastVariantId(variantId)
            .withActiveAttributeIds(Collections.singletonList(attrId))
            .withCursed(cursed);
        when(harness.identityService.readForgeIdentity(item)).thenReturn(
            new ItemIdentityService.ForgeIdentityRead(
                ItemIdentityService.ForgeIdentityStatus.VALID, identity));
        return item;
    }
}
