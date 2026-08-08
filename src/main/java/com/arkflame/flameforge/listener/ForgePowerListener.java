package com.arkflame.flameforge.listener;

import com.arkflame.flameforge.compat.equipment.EquipmentBridge;
import com.arkflame.flameforge.item.AttributeBridge;
import com.arkflame.flameforge.compat.scheduler.SchedulerBridge;
import com.arkflame.flameforge.config.TierRepository;
import com.arkflame.flameforge.forge.ForgePowerService;
import com.arkflame.flameforge.item.ItemIdentityCodec;
import com.arkflame.flameforge.item.ItemIdentityService;
import com.arkflame.flameforge.model.ForgeAttributeDefinition;
import com.arkflame.flameforge.model.ForgePowerDefinition;
import com.arkflame.flameforge.model.ForgeVariant;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public final class ForgePowerListener implements Listener {

    private final ForgePowerService powerService;
    private final EquipmentBridge equipmentBridge;
    private final ItemIdentityService identityService;
    private final TierRepository tierRepository;
    private final SchedulerBridge schedulerBridge;
    private final AttributeBridge attributeBridge;

    private static final Runnable RETIRED_NOOP = () -> {};

    public ForgePowerListener(ForgePowerService powerService,
                              EquipmentBridge equipmentBridge,
                              ItemIdentityService identityService,
                              TierRepository tierRepository,
                              SchedulerBridge schedulerBridge,
                              AttributeBridge attributeBridge) {
        this.powerService = powerService;
        this.equipmentBridge = equipmentBridge;
        this.identityService = identityService;
        this.tierRepository = tierRepository;
        this.schedulerBridge = schedulerBridge;
        this.attributeBridge = attributeBridge;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = false)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event == null) {
            return;
        }
        if (event.getAction().equals(org.bukkit.event.block.Action.RIGHT_CLICK_AIR)
            || event.getAction().equals(org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK)) {
            handleRightClick(event);
        }
    }

    private void handleRightClick(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (player == null || !player.isOnline()) {
            return;
        }
        if (!player.isSneaking()) {
            return;
        }
        ItemStack item = getItemInHand(player, event);
        if (item == null || !item.hasItemMeta()) {
            return;
        }
        ItemIdentityService.ForgeIdentityRead identityRead = identityService.readForgeIdentity(item);
        if (identityRead.getStatus() != ItemIdentityService.ForgeIdentityStatus.VALID) {
            return;
        }
        ItemIdentityCodec.Identity identity = identityRead.getIdentity();
        String lastTierId = identity.getLastTierId();
        String lastVariantId = identity.getLastVariantId();
        if (lastTierId == null || lastVariantId == null) {
            return;
        }
        UUID forgeId = identity.getForgeId();
        List<String> activePowerIds = identity.getActivePowerIds();
        List<ForgePowerDefinition> powers = getPowersForForge(lastTierId, lastVariantId, activePowerIds);
        for (ForgePowerDefinition power : powers) {
            ForgePowerDefinition.PowerType type = power.getPowerType();
            if (type == ForgePowerDefinition.PowerType.SHIFT_RIGHT_CLICK_DASH) {
                powerService.activateDash(player, power, forgeId);
            } else if (type == ForgePowerDefinition.PowerType.SHIFT_RIGHT_CLICK_HEAL) {
                powerService.activateHeal(player, power, forgeId);
            }
        }
    }

    private ItemStack getItemInHand(Player player, PlayerInteractEvent event) {
        org.bukkit.event.block.Action action = event.getAction();
        if (action == org.bukkit.event.block.Action.RIGHT_CLICK_AIR) {
            return player.getItemInHand();
        }
        if (action == org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK) {
            return player.getItemInHand();
        }
        return null;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (event == null) {
            return;
        }
        Entity damager = event.getDamager();
        Entity victim = event.getEntity();

        if (damager instanceof Player) {
            Player attacker = (Player) damager;
            ItemStack weapon = attacker.getItemInHand();
            if (weapon != null && weapon.hasItemMeta()) {
                ItemIdentityService.ForgeIdentityRead identityRead = identityService.readForgeIdentity(weapon);
                if (identityRead.getStatus() == ItemIdentityService.ForgeIdentityStatus.VALID) {
                    ItemIdentityCodec.Identity identity = identityRead.getIdentity();
                    if (!attributeBridge.isModernAttributesAvailable()) {
                        double attackBonus = sumAttackDamageBonus(identity);
                        if (attackBonus > 0) {
                            event.setDamage(event.getDamage() + attackBonus);
                        }
                    }
                }
            }
        }

        if (victim instanceof Player) {
            Player victimPlayer = (Player) victim;
            double reduction = computeDamageReduction(victimPlayer);
            if (reduction > 0) {
                double currentDamage = event.getDamage();
                event.setDamage(currentDamage * (1 - reduction));
            }
        }

        if (damager instanceof Player && victim instanceof LivingEntity) {
            Player attacker = (Player) damager;
            LivingEntity victimEntity = (LivingEntity) victim;
            handleOnHit(attacker, victimEntity);
        }
    }

    private double sumAttackDamageBonus(ItemIdentityCodec.Identity identity) {
        String lastTierId = identity.getLastTierId();
        String lastVariantId = identity.getLastVariantId();
        List<String> activeAttributeIds = identity.getActiveAttributeIds();
        if (lastTierId == null || lastVariantId == null || activeAttributeIds == null) {
            return 0;
        }
        return tierRepository.findById(lastTierId)
            .map(tier -> tier.getVariants().stream()
                .filter(v -> lastVariantId.equals(v.getId()))
                .flatMap(v -> v.getAttributes().stream())
                .filter(attr -> activeAttributeIds.contains(attr.getId()))
                .filter(attr -> attr.getType() == ForgeAttributeDefinition.AttributeType.ATTACK_DAMAGE_FLAT)
                .mapToDouble(ForgeAttributeDefinition::getMultiplier)
                .sum())
            .orElse(0.0);
    }

    private double computeDamageReduction(Player player) {
        double maxReduction = 0.0;
        EquipmentBridge.Slot[] slots = {
            EquipmentBridge.Slot.MAINHAND,
            EquipmentBridge.Slot.OFFHAND,
            EquipmentBridge.Slot.HEAD,
            EquipmentBridge.Slot.CHEST,
            EquipmentBridge.Slot.LEGS,
            EquipmentBridge.Slot.FEET
        };
        for (EquipmentBridge.Slot slot : slots) {
            ItemStack item = equipmentBridge.getItem(player, slot);
            if (item == null || !item.hasItemMeta()) {
                continue;
            }
            ItemIdentityService.ForgeIdentityRead identityRead = identityService.readForgeIdentity(item);
            if (identityRead.getStatus() != ItemIdentityService.ForgeIdentityStatus.VALID) {
                continue;
            }
            ItemIdentityCodec.Identity identity = identityRead.getIdentity();
            double reduction = getDamageReductionFromIdentity(identity);
            if (reduction > maxReduction) {
                maxReduction = reduction;
            }
        }
        return Math.min(maxReduction, 0.80);
    }

    private double getDamageReductionFromIdentity(ItemIdentityCodec.Identity identity) {
        String lastTierId = identity.getLastTierId();
        String lastVariantId = identity.getLastVariantId();
        List<String> activeAttributeIds = identity.getActiveAttributeIds();
        if (lastTierId == null || lastVariantId == null || activeAttributeIds == null) {
            return 0;
        }
        return tierRepository.findById(lastTierId)
            .map(tier -> tier.getVariants().stream()
                .filter(v -> lastVariantId.equals(v.getId()))
                .flatMap(v -> v.getAttributes().stream())
                .filter(attr -> activeAttributeIds.contains(attr.getId()))
                .filter(attr -> attr.getType() == ForgeAttributeDefinition.AttributeType.DAMAGE_REDUCTION_PERCENT)
                .mapToDouble(ForgeAttributeDefinition::getMultiplier)
                .max()
                .orElse(0.0))
            .orElse(0.0);
    }

    private void handleOnHit(Player attacker, LivingEntity victim) {
        if (attacker == null || !attacker.isOnline()) {
            return;
        }
        ItemStack weapon = attacker.getItemInHand();
        if (weapon == null || !weapon.hasItemMeta()) {
            return;
        }
        ItemIdentityService.ForgeIdentityRead identityRead = identityService.readForgeIdentity(weapon);
        if (identityRead.getStatus() != ItemIdentityService.ForgeIdentityStatus.VALID) {
            return;
        }
        ItemIdentityCodec.Identity identity = identityRead.getIdentity();
        String lastTierId = identity.getLastTierId();
        String lastVariantId = identity.getLastVariantId();
        if (lastTierId == null || lastVariantId == null) {
            return;
        }
        UUID forgeId = identity.getForgeId();
        List<String> activePowerIds = identity.getActivePowerIds();
        List<ForgePowerDefinition> powers = getPowersForForge(lastTierId, lastVariantId, activePowerIds);
        for (ForgePowerDefinition power : powers) {
            ForgePowerDefinition.PowerType type = power.getPowerType();
            if (type == ForgePowerDefinition.PowerType.ON_HIT_POTION
                || type == ForgePowerDefinition.PowerType.ON_HIT_FIRE
                || type == ForgePowerDefinition.PowerType.ON_HIT_HEAL
                || type == ForgePowerDefinition.PowerType.EVERY_N_HIT_LIGHTNING
                || type == ForgePowerDefinition.PowerType.EVERY_N_HIT_KNOCKBACK) {
                powerService.triggerOnHitPower(attacker, victim, power, forgeId);
            }
        }
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onPlayerQuit(PlayerQuitEvent event) {
        if (event == null) {
            return;
        }
        Player player = event.getPlayer();
        if (player == null) {
            return;
        }
        powerService.clearCooldownsForPlayer(player);
        powerService.clearPassiveTasksForPlayer(player);
        powerService.clearHitCountersForPlayer(player.getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (event == null) {
            return;
        }
        Player player = event.getPlayer();
        if (player == null) {
            return;
        }
        schedulerBridge.runEntityLater(player, () -> refreshPassivePowers(player), RETIRED_NOOP, 1L);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerItemHeld(PlayerItemHeldEvent event) {
        if (event == null) {
            return;
        }
        Player player = event.getPlayer();
        if (player == null) {
            return;
        }
        schedulerBridge.runEntityLater(player, () -> refreshPassivePowers(player), RETIRED_NOOP, 1L);
    }

    private void refreshPassivePowers(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }
        powerService.clearPassiveTasksForPlayer(player);
        List<ItemStack> itemsToScan = new ArrayList<>();
        ItemStack mainhand = equipmentBridge.getItem(player, EquipmentBridge.Slot.MAINHAND);
        if (mainhand != null && mainhand.hasItemMeta()) {
            itemsToScan.add(mainhand);
        }
        ItemStack offhand = equipmentBridge.getItem(player, EquipmentBridge.Slot.OFFHAND);
        if (offhand != null && offhand.hasItemMeta()) {
            itemsToScan.add(offhand);
        }
        ItemStack helmet = equipmentBridge.getItem(player, EquipmentBridge.Slot.HEAD);
        if (helmet != null && helmet.hasItemMeta()) {
            itemsToScan.add(helmet);
        }
        ItemStack chest = equipmentBridge.getItem(player, EquipmentBridge.Slot.CHEST);
        if (chest != null && chest.hasItemMeta()) {
            itemsToScan.add(chest);
        }
        ItemStack legs = equipmentBridge.getItem(player, EquipmentBridge.Slot.LEGS);
        if (legs != null && legs.hasItemMeta()) {
            itemsToScan.add(legs);
        }
        ItemStack boots = equipmentBridge.getItem(player, EquipmentBridge.Slot.FEET);
        if (boots != null && boots.hasItemMeta()) {
            itemsToScan.add(boots);
        }
        Set<String> processed = new HashSet<>();
        for (ItemStack item : itemsToScan) {
            ItemIdentityService.ForgeIdentityRead identityRead = identityService.readForgeIdentity(item);
            if (identityRead.getStatus() != ItemIdentityService.ForgeIdentityStatus.VALID) {
                continue;
            }
            ItemIdentityCodec.Identity identity = identityRead.getIdentity();
            String lastTierId = identity.getLastTierId();
            String lastVariantId = identity.getLastVariantId();
            if (lastTierId == null || lastVariantId == null) {
                continue;
            }
            UUID forgeId = identity.getForgeId();
            List<String> activePowerIds = identity.getActivePowerIds();
            List<ForgePowerDefinition> powers = getPowersForForge(lastTierId, lastVariantId, activePowerIds);
            for (ForgePowerDefinition power : powers) {
                if (power.getPowerType() != ForgePowerDefinition.PowerType.PASSIVE_POTION) {
                    continue;
                }
                String uniqueKey = forgeId.toString() + ":" + power.getId();
                if (processed.contains(uniqueKey)) {
                    continue;
                }
                processed.add(uniqueKey);
                List<ForgePowerDefinition.ActivationSlot> slots = power.getActivationSlots();
                if (isItemInActivationSlot(player, item, slots)) {
                    powerService.activatePassivePower(player, power, forgeId);
                }
            }
        }
    }

    private boolean isItemInActivationSlot(Player player, ItemStack item, List<ForgePowerDefinition.ActivationSlot> slots) {
        if (slots == null || slots.isEmpty()) {
            return isItemInHand(player, item);
        }
        for (ForgePowerDefinition.ActivationSlot slot : slots) {
            if (isItemInSlot(player, item, slot)) {
                return true;
            }
        }
        return false;
    }

    private boolean isItemInHand(Player player, ItemStack item) {
        ItemStack mainhand = player.getItemInHand();
        if (mainhand != null && mainhand.hasItemMeta() && item.hasItemMeta()) {
            if (mainhand.isSimilar(item)) {
                return true;
            }
        }
        return false;
    }

    private boolean isItemInSlot(Player player, ItemStack item, ForgePowerDefinition.ActivationSlot slot) {
        if (slot == null) {
            return false;
        }
        EquipmentBridge.Slot bridgeSlot = convertSlot(slot);
        if (bridgeSlot == null) {
            return false;
        }
        ItemStack equipped = equipmentBridge.getItem(player, bridgeSlot);
        if (equipped != null && equipped.hasItemMeta() && item.hasItemMeta()) {
            return equipped.isSimilar(item);
        }
        return false;
    }

    private EquipmentBridge.Slot convertSlot(ForgePowerDefinition.ActivationSlot slot) {
        if (slot == null) {
            return null;
        }
        switch (slot) {
            case MAINHAND:
                return EquipmentBridge.Slot.MAINHAND;
            case OFFHAND:
                return EquipmentBridge.Slot.OFFHAND;
            case HEAD:
                return EquipmentBridge.Slot.HEAD;
            case CHEST:
                return EquipmentBridge.Slot.CHEST;
            case LEGS:
                return EquipmentBridge.Slot.LEGS;
            case FEET:
                return EquipmentBridge.Slot.FEET;
            default:
                return null;
        }
    }

    private List<ForgePowerDefinition> getPowersForForge(String lastTierId, String lastVariantId, List<String> activePowerIds) {
        if (lastTierId == null || lastVariantId == null) {
            return new ArrayList<>();
        }
        if (activePowerIds == null || activePowerIds.isEmpty()) {
            return new ArrayList<>();
        }
        Set<String> activeSet = new HashSet<>(activePowerIds);
        return tierRepository.findById(lastTierId)
                .map(tier -> tier.getVariants().stream()
                        .filter(v -> lastVariantId.equals(v.getId()))
                        .map(ForgeVariant::getPowers)
                        .flatMap(List::stream)
                        .filter(power -> activeSet.contains(power.getId()))
                        .collect(Collectors.toList()))
                .orElse(new ArrayList<>());
    }
}
