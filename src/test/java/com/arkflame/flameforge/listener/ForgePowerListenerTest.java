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
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ForgePowerListenerTest {

    @Test
    void playerLifecycleRefreshesActiveForgePowers() {
        ForgePowerService powerService = mock(ForgePowerService.class);
        EquipmentBridge equipmentBridge = mock(EquipmentBridge.class);
        ItemIdentityService identityService = mock(ItemIdentityService.class);
        TierRepository tierRepository = mock(TierRepository.class);
        SchedulerBridge schedulerBridge = mock(SchedulerBridge.class);
        AttributeBridge attributeBridge = mock(AttributeBridge.class);
        ForgePowerListener listener = new ForgePowerListener(
            powerService, equipmentBridge, identityService, tierRepository,
            schedulerBridge, attributeBridge
        );

        Player player = mock(Player.class);
        when(player.isOnline()).thenReturn(true);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        ItemStack item = mock(ItemStack.class);
        when(item.hasItemMeta()).thenReturn(true);
        when(item.isSimilar(item)).thenReturn(true);
        when(player.getItemInHand()).thenReturn(item);
        when(equipmentBridge.getItem(player, EquipmentBridge.Slot.MAINHAND)).thenReturn(item);

        UUID forgeId = UUID.randomUUID();
        ItemIdentityCodec.Identity identity = ItemIdentityCodec.Identity.empty()
            .withForgeId(forgeId)
            .withLastTierId("tier")
            .withLastVariantId("variant")
            .withActivePowerIds(Collections.singletonList("passive"));
        when(identityService.readForgeIdentity(item)).thenReturn(
            new ItemIdentityService.ForgeIdentityRead(
                ItemIdentityService.ForgeIdentityStatus.VALID, identity
            )
        );

        ForgePowerDefinition power = mock(ForgePowerDefinition.class);
        when(power.getId()).thenReturn("passive");
        when(power.getPowerType()).thenReturn(ForgePowerDefinition.PowerType.PASSIVE_POTION);
        when(power.getActivationSlots()).thenReturn(Collections.emptyList());
        ForgeVariant variant = mock(ForgeVariant.class);
        when(variant.getId()).thenReturn("variant");
        when(variant.getPowers()).thenReturn(Collections.singletonList(power));
        TierDefinition tier = mock(TierDefinition.class);
        when(tier.getVariants()).thenReturn(Collections.singletonList(variant));
        when(tierRepository.findById("tier")).thenReturn(Optional.of(tier));

        doAnswer(invocation -> {
            ((Runnable) invocation.getArgument(1)).run();
            return mock(TaskHandle.class);
        }).when(schedulerBridge).runEntityLater(
            any(Entity.class), any(Runnable.class), any(Runnable.class), anyLong()
        );

        PlayerJoinEvent join = mock(PlayerJoinEvent.class);
        when(join.getPlayer()).thenReturn(player);
        PlayerItemHeldEvent held = mock(PlayerItemHeldEvent.class);
        when(held.getPlayer()).thenReturn(player);

        listener.onPlayerJoin(join);
        listener.onPlayerItemHeld(held);

        verify(powerService, atLeastOnce()).activatePassivePower(player, power, forgeId);
    }

    @Test
    void validCombatOrInteractEventDelegatesPowerAndInvalidTriggerDoesNothing() {
        ForgePowerService powerService = mock(ForgePowerService.class);
        EquipmentBridge equipmentBridge = mock(EquipmentBridge.class);
        ItemIdentityService identityService = mock(ItemIdentityService.class);
        TierRepository tierRepository = mock(TierRepository.class);
        SchedulerBridge schedulerBridge = mock(SchedulerBridge.class);
        AttributeBridge attributeBridge = mock(AttributeBridge.class);
        ForgePowerListener listener = new ForgePowerListener(
            powerService, equipmentBridge, identityService, tierRepository,
            schedulerBridge, attributeBridge
        );

        Player interactingPlayer = mock(Player.class);
        when(interactingPlayer.isOnline()).thenReturn(true);
        when(interactingPlayer.isSneaking()).thenReturn(true);
        ItemStack dashItem = mock(ItemStack.class);
        when(dashItem.hasItemMeta()).thenReturn(true);
        when(interactingPlayer.getItemInHand()).thenReturn(dashItem);
        when(equipmentBridge.getItem(interactingPlayer, EquipmentBridge.Slot.MAINHAND)).thenReturn(dashItem);
        UUID dashForgeId = UUID.randomUUID();
        ItemIdentityCodec.Identity dashIdentity = ItemIdentityCodec.Identity.empty()
            .withForgeId(dashForgeId)
            .withLastTierId("dash-tier")
            .withLastVariantId("dash-variant")
            .withActivePowerIds(Collections.singletonList("dash"));
        when(identityService.readForgeIdentity(dashItem)).thenReturn(
            new ItemIdentityService.ForgeIdentityRead(
                ItemIdentityService.ForgeIdentityStatus.VALID, dashIdentity
            )
        );
        ForgePowerDefinition dash = mock(ForgePowerDefinition.class);
        when(dash.getId()).thenReturn("dash");
        when(dash.getPowerType()).thenReturn(ForgePowerDefinition.PowerType.SHIFT_RIGHT_CLICK_DASH);
        ForgeVariant dashVariant = mock(ForgeVariant.class);
        when(dashVariant.getId()).thenReturn("dash-variant");
        when(dashVariant.getPowers()).thenReturn(Collections.singletonList(dash));
        TierDefinition dashTier = mock(TierDefinition.class);
        when(dashTier.getVariants()).thenReturn(Collections.singletonList(dashVariant));
        when(tierRepository.findById("dash-tier")).thenReturn(Optional.of(dashTier));

        PlayerInteractEvent interact = mock(PlayerInteractEvent.class);
        when(interact.getAction()).thenReturn(Action.RIGHT_CLICK_AIR);
        when(interact.getPlayer()).thenReturn(interactingPlayer);
        listener.onPlayerInteract(interact);

        Player attacker = mock(Player.class);
        when(attacker.isOnline()).thenReturn(true);
        ItemStack weapon = mock(ItemStack.class);
        when(weapon.hasItemMeta()).thenReturn(true);
        when(attacker.getItemInHand()).thenReturn(weapon);
        LivingEntity victim = mock(LivingEntity.class);
        UUID hitForgeId = UUID.randomUUID();
        ItemIdentityCodec.Identity hitIdentity = ItemIdentityCodec.Identity.empty()
            .withForgeId(hitForgeId)
            .withLastTierId("hit-tier")
            .withLastVariantId("hit-variant")
            .withActivePowerIds(Collections.singletonList("on-hit"));
        when(identityService.readForgeIdentity(weapon)).thenReturn(
            new ItemIdentityService.ForgeIdentityRead(
                ItemIdentityService.ForgeIdentityStatus.VALID, hitIdentity
            )
        );
        ForgePowerDefinition onHit = mock(ForgePowerDefinition.class);
        when(onHit.getId()).thenReturn("on-hit");
        when(onHit.getPowerType()).thenReturn(ForgePowerDefinition.PowerType.ON_HIT_POTION);
        ForgeVariant hitVariant = mock(ForgeVariant.class);
        when(hitVariant.getId()).thenReturn("hit-variant");
        when(hitVariant.getPowers()).thenReturn(Collections.singletonList(onHit));
        when(hitVariant.getAttributes()).thenReturn(Collections.emptyList());
        TierDefinition hitTier = mock(TierDefinition.class);
        when(hitTier.getVariants()).thenReturn(Collections.singletonList(hitVariant));
        when(tierRepository.findById("hit-tier")).thenReturn(Optional.of(hitTier));

        EntityDamageByEntityEvent combat = mock(EntityDamageByEntityEvent.class);
        when(combat.getDamager()).thenReturn(attacker);
        when(combat.getEntity()).thenReturn(victim);
        listener.onEntityDamageByEntity(combat);

        verify(powerService).activateDash(interactingPlayer, dash, dashForgeId);
        verify(powerService).triggerOnHitPower(attacker, victim, onHit, hitForgeId);

        ForgePowerService invalidPowerService = mock(ForgePowerService.class);
        ForgePowerListener invalidListener = new ForgePowerListener(
            invalidPowerService, equipmentBridge, identityService, tierRepository,
            schedulerBridge, attributeBridge
        );
        PlayerInteractEvent invalidTrigger = mock(PlayerInteractEvent.class);
        when(invalidTrigger.getAction()).thenReturn(Action.LEFT_CLICK_AIR);
        invalidListener.onPlayerInteract(invalidTrigger);
        verifyNoInteractions(invalidPowerService);
    }

    @Test
    void inventoryDirtyEventUsesEntityOwnedDebounce() {
        ForgePowerService powerService = mock(ForgePowerService.class);
        EquipmentBridge equipmentBridge = mock(EquipmentBridge.class);
        ItemIdentityService identityService = mock(ItemIdentityService.class);
        TierRepository tierRepository = mock(TierRepository.class);
        SchedulerBridge schedulerBridge = mock(SchedulerBridge.class);
        AttributeBridge attributeBridge = mock(AttributeBridge.class);
        ForgePowerListener listener = new ForgePowerListener(powerService, equipmentBridge,
            identityService, tierRepository, schedulerBridge, attributeBridge);
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        InventoryClickEvent event = mock(InventoryClickEvent.class);
        when(event.getWhoClicked()).thenReturn(player);

        listener.onInventoryClick(event);

        verify(schedulerBridge).runEntityLater(any(Entity.class), any(Runnable.class), any(Runnable.class), anyLong());
    }
}
