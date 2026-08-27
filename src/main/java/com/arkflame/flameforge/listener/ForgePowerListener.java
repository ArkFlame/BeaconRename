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
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
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
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public final class ForgePowerListener implements Listener {

    private final ForgePowerService powerService;
    private final EquipmentBridge equipmentBridge;
    private final ItemIdentityService identityService;
    private final TierRepository tierRepository;
    private final SchedulerBridge schedulerBridge;
    private final AttributeBridge attributeBridge;
    private final InteractionHandBridge handBridge;
    private final ConcurrentHashMap<UUID, TaskHandle> pendingPassiveRefresh = new ConcurrentHashMap<>();

    private static final Runnable RETIRED_NOOP = () -> {};

    public ForgePowerListener(ForgePowerService powerService,
                              EquipmentBridge equipmentBridge,
                              ItemIdentityService identityService,
                              TierRepository tierRepository,
                              SchedulerBridge schedulerBridge,
                              AttributeBridge attributeBridge,
                              InteractionHandBridge handBridge) {
        this.powerService = powerService;
        this.equipmentBridge = equipmentBridge;
        this.identityService = identityService;
        this.tierRepository = tierRepository;
        this.schedulerBridge = schedulerBridge;
        this.attributeBridge = attributeBridge;
        this.handBridge = handBridge;
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
        ItemStack item = getItemInHand(player, event);
        if (isPassiveRefreshRelevantItem(item)) {
            queuePassiveRefresh(player);
        }
        if (!player.isSneaking()) {
            return;
        }
        if (item == null || !item.hasItemMeta()) {
            return;
        }
        ItemIdentityService.ForgeIdentityRead identityRead = identityService.readForgeIdentity(item);
        if (identityRead.getStatus() != ItemIdentityService.ForgeIdentityStatus.VALID) {
            return;
        }
        ItemIdentityCodec.Identity identity = identityRead.getIdentity();
        if (identity.isCursed()) {
            return;
        }
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

    private boolean isPassiveRefreshRelevantItem(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return false;
        }
        ItemIdentityService.ForgeIdentityRead identityRead = identityService.readForgeIdentity(item);
        if (identityRead != null && identityRead.getStatus() == ItemIdentityService.ForgeIdentityStatus.VALID) {
            return true;
        }
        Optional<String> category = tierRepository.findEquipmentCategory(item.getType());
        if (category.isPresent()) {
            String categoryId = category.get();
            return "armor".equalsIgnoreCase(categoryId) || "shield".equalsIgnoreCase(categoryId);
        }
        return false;
    }

    private ItemStack getItemInHand(Player player, PlayerInteractEvent event) {
        InteractionHandBridge.Hand hand = handBridge.getHand(event);
        if (hand == InteractionHandBridge.Hand.OFF) {
            return equipmentBridge.getItem(player, EquipmentBridge.Slot.OFFHAND);
        }
        if (hand == InteractionHandBridge.Hand.MAIN) {
            return equipmentBridge.getItem(player, EquipmentBridge.Slot.MAINHAND);
        }
        return null;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageEvent event) {
        if (event == null || event instanceof EntityDamageByEntityEvent
            || !(event.getEntity() instanceof Player)) {
            return;
        }
        Player victim = (Player) event.getEntity();
        double reduction = computeDamageReduction(victim, event.getCause());
        if (reduction > 0) {
            event.setDamage(event.getDamage() * (1 - reduction));
            powerService.emitArmorReductionParticle(victim);
        }
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
                    if (!identity.isCursed() && !attributeBridge.isModernAttributesAvailable()) {
                        double attackBonus = sumAttackDamageBonus(identity);
                        if (attackBonus > 0) {
                            event.setDamage(event.getDamage() + attackBonus);
                        }
                    }
                }
            }
        }

        if (victim instanceof LivingEntity) {
            if (damager instanceof Player) {
                powerService.tracePowerEvent((Player) damager, "ON_HIT_EVENT", "damager=PLAYER");
            } else if (damager instanceof Projectile) {
                LivingEntity shooter = resolveLivingDamager(damager);
                if (shooter instanceof Player) {
                    powerService.tracePowerEvent((Player) shooter, "PROJECTILE_HIT_EVENT_IGNORED",
                        "projectile=" + damager.getClass().getSimpleName());
                }
            }
        }

        if (damager instanceof Player && victim instanceof LivingEntity) {
            Player attacker = (Player) damager;
            LivingEntity victimEntity = (LivingEntity) victim;
            boolean lethalHit = victimEntity.getHealth() > 0
                && event.getFinalDamage() >= victimEntity.getHealth();
            handleOnHit(attacker, victimEntity, lethalHit);
        }

        if (victim instanceof Player && ((Player) victim).isBlocking()) {
            LivingEntity attacker = resolveLivingDamager(damager);
            if (attacker != null) {
                handleBlock((Player) victim, attacker);
            }
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

    private double computeDamageReduction(Player player, EntityDamageEvent.DamageCause cause) {
        double genericMax = 0.0;
        double specificMax = 0.0;
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
            if (identity.isCursed()) {
                continue;
            }
            double generic = getMaxDamageReduction(identity,
                ForgeAttributeDefinition.AttributeType.DAMAGE_REDUCTION_PERCENT);
            if (generic > genericMax) {
                genericMax = generic;
            }
            double specific = getCauseSpecificDamageReduction(identity, cause);
            if (specific > specificMax) {
                specificMax = specific;
            }
        }
        return Math.min(genericMax + specificMax, 0.80);
    }

    private double getCauseSpecificDamageReduction(ItemIdentityCodec.Identity identity,
                                                   EntityDamageEvent.DamageCause cause) {
        ForgeAttributeDefinition.AttributeType causeType = causeAttributeType(cause);
        if (causeType == null) {
            return 0.0;
        }
        return getMaxDamageReduction(identity, causeType);
    }

    private double getMaxDamageReduction(ItemIdentityCodec.Identity identity,
                                         ForgeAttributeDefinition.AttributeType type) {
        String lastTierId = identity.getLastTierId();
        String lastVariantId = identity.getLastVariantId();
        List<String> activeAttributeIds = identity.getActiveAttributeIds();
        if (lastTierId == null || lastVariantId == null || activeAttributeIds == null) {
            return 0.0;
        }
        return tierRepository.findById(lastTierId)
            .map(tier -> tier.getVariants().stream()
                .filter(v -> lastVariantId.equals(v.getId()))
                .flatMap(v -> v.getAttributes().stream())
                .filter(attr -> activeAttributeIds.contains(attr.getId()))
                .filter(attr -> attr.getType() == type)
                .mapToDouble(ForgeAttributeDefinition::getMultiplier)
                .max()
                .orElse(0.0))
            .orElse(0.0);
    }

    private ForgeAttributeDefinition.AttributeType causeAttributeType(EntityDamageEvent.DamageCause cause) {
        if (cause == null) {
            return null;
        }
        if (cause == EntityDamageEvent.DamageCause.POISON) {
            return ForgeAttributeDefinition.AttributeType.POISON_DAMAGE_REDUCTION_PERCENT;
        }
        if (cause == EntityDamageEvent.DamageCause.MAGIC || "DRAGON_BREATH".equals(cause.name())) {
            return ForgeAttributeDefinition.AttributeType.MAGIC_DAMAGE_REDUCTION_PERCENT;
        }
        if (cause == EntityDamageEvent.DamageCause.FALL) {
            return ForgeAttributeDefinition.AttributeType.FALL_DAMAGE_REDUCTION_PERCENT;
        }
        return null;
    }

    private LivingEntity resolveLivingDamager(Entity damager) {
        if (damager instanceof Player) {
            return (Player) damager;
        }
        if (damager instanceof LivingEntity) {
            return (LivingEntity) damager;
        }
        if (damager instanceof Projectile) {
            Object shooter = ((Projectile) damager).getShooter();
            if (shooter instanceof LivingEntity) {
                return (LivingEntity) shooter;
            }
        }
        return null;
    }

    private void handleBlock(Player defender, LivingEntity attacker) {
        Set<String> processed = new HashSet<>();
        for (ItemStack item : getActivationItems(defender)) {
            if (item == null || !item.hasItemMeta()) {
                continue;
            }
            ItemIdentityService.ForgeIdentityRead read = identityService.readForgeIdentity(item);
            if (read.getStatus() != ItemIdentityService.ForgeIdentityStatus.VALID) {
                continue;
            }
            ItemIdentityCodec.Identity identity = read.getIdentity();
            if (identity.isCursed()) {
                continue;
            }
            List<ForgePowerDefinition> powers = getPowersForForge(identity.getLastTierId(),
                identity.getLastVariantId(), identity.getActivePowerIds());
            for (ForgePowerDefinition power : powers) {
                String key = identity.getForgeId() + ":" + power.getId();
                if (processed.add(key) && isItemInActivationSlot(defender, item, power.getActivationSlots())) {
                    powerService.triggerOnBlockPower(defender, attacker, power, identity.getForgeId());
                }
            }
        }
    }

    private void handleOnHit(Player attacker, LivingEntity victim, boolean lethalHit) {
        if (attacker == null || !attacker.isOnline()) {
            return;
        }
        ItemStack weapon = attacker.getItemInHand();
        if (weapon == null || !weapon.hasItemMeta()) {
            return;
        }
        ItemIdentityService.ForgeIdentityRead identityRead = identityService.readForgeIdentity(weapon);
        if (identityRead.getStatus() != ItemIdentityService.ForgeIdentityStatus.VALID) {
            powerService.tracePowerEvent(attacker, "ON_HIT_REJECT_IDENTITY",
                "status=" + identityRead.getStatus());
            return;
        }
        ItemIdentityCodec.Identity identity = identityRead.getIdentity();
        if (identity.isCursed()) {
            powerService.tracePowerEvent(attacker, "ON_HIT_REJECT_CURSED", "cursed=true");
            return;
        }
        String lastTierId = identity.getLastTierId();
        String lastVariantId = identity.getLastVariantId();
        if (lastTierId == null || lastVariantId == null) {
            powerService.tracePowerEvent(attacker, "ON_HIT_REJECT_VARIANT",
                "reason=missing-last-tier-or-variant");
            return;
        }
        UUID forgeId = identity.getForgeId();
        List<String> activePowerIds = identity.getActivePowerIds();
        List<ForgePowerDefinition> powers = getPowersForForge(lastTierId, lastVariantId, activePowerIds);
        powerService.tracePowerEvent(attacker, "ON_HIT_RESOLVED",
            "forge=" + forgeId + " tier=" + lastTierId + " variant=" + lastVariantId
                + " powers=" + powers.size());
        for (ForgePowerDefinition power : powers) {
            powerService.triggerOnHitPower(attacker, victim, power, forgeId, lethalHit);
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
        cancelPendingRefresh(player);
        powerService.clearCooldownsForPlayer(player);
        powerService.clearPassiveTasksForPlayer(player);
        powerService.clearInventoryCacheForPlayer(player);
        powerService.clearHitCountersForPlayer(player.getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        if (event != null) {
            queuePassiveRefresh(event.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (event != null) {
            queuePassiveRefresh(event.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerItemHeld(PlayerItemHeldEvent event) {
        if (event != null) {
            queuePassiveRefresh(event.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (event != null && event.getWhoClicked() instanceof Player) {
            queuePassiveRefresh((Player) event.getWhoClicked());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event != null && event.getWhoClicked() instanceof Player) {
            queuePassiveRefresh((Player) event.getWhoClicked());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryClose(InventoryCloseEvent event) {
        if (event != null && event.getPlayer() instanceof Player) {
            queuePassiveRefresh((Player) event.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerDropItem(PlayerDropItemEvent event) {
        if (event != null) {
            queuePassiveRefresh(event.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerPickupItem(PlayerPickupItemEvent event) {
        if (event != null) {
            queuePassiveRefresh(event.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerItemConsume(PlayerItemConsumeEvent event) {
        if (event != null) {
            queuePassiveRefresh(event.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerItemBreak(PlayerItemBreakEvent event) {
        if (event != null) {
            queuePassiveRefresh(event.getPlayer());
        }
    }

    public void queuePassiveRefresh(Player player) {
        if (player == null) {
            return;
        }
        UUID playerId = player.getUniqueId();
        if (playerId == null) {
            return;
        }
        TaskHandle prior = pendingPassiveRefresh.remove(playerId);
        if (prior != null) {
            prior.cancel();
        }
        TaskHandle handle = schedulerBridge.runEntityLater(player, () -> {
            pendingPassiveRefresh.remove(playerId);
            powerService.refreshPassivePowers(player);
        }, RETIRED_NOOP, 1L);
        if (handle != null) {
            pendingPassiveRefresh.put(playerId, handle);
        }
    }

    public void shutdown() {
        for (TaskHandle handle : pendingPassiveRefresh.values()) {
            if (handle != null) {
                handle.cancel();
            }
        }
        pendingPassiveRefresh.clear();
        powerService.clearAllPassiveTasks();
    }

    private void cancelPendingRefresh(Player player) {
        if (player == null) {
            return;
        }
        TaskHandle handle = pendingPassiveRefresh.remove(player.getUniqueId());
        if (handle != null) {
            handle.cancel();
        }
    }

    private boolean isItemInActivationSlot(Player player, ItemStack item, List<ForgePowerDefinition.ActivationSlot> slots) {
        if (slots == null || slots.isEmpty()) {
            return isItemInHand(player, item);
        }
        for (ForgePowerDefinition.ActivationSlot slot : slots) {
            if (slot == ForgePowerDefinition.ActivationSlot.INVENTORY) {
                ItemIdentityService.ForgeIdentityRead read = identityService.readForgeIdentity(item);
                return read.getStatus() == ItemIdentityService.ForgeIdentityStatus.VALID
                    && !read.getIdentity().isCursed()
                    && powerService.hasCachedInventoryForgeId(player, read.getIdentity().getForgeId());
            }
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
        ItemStack offhand = equipmentBridge.getItem(player, EquipmentBridge.Slot.OFFHAND);
        if (offhand != null && offhand.hasItemMeta() && item.hasItemMeta()) {
            return offhand.isSimilar(item);
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
            case INVENTORY:
                return EquipmentBridge.Slot.INVENTORY;
            default:
                return null;
        }
    }

    private List<ItemStack> getActivationItems(Player player) {
        List<ItemStack> items = new ArrayList<>();
        addItems(items, equipmentBridge.getInventoryContents(player));
        items.add(equipmentBridge.getItem(player, EquipmentBridge.Slot.MAINHAND));
        items.add(equipmentBridge.getItem(player, EquipmentBridge.Slot.OFFHAND));
        items.add(equipmentBridge.getItem(player, EquipmentBridge.Slot.HEAD));
        items.add(equipmentBridge.getItem(player, EquipmentBridge.Slot.CHEST));
        items.add(equipmentBridge.getItem(player, EquipmentBridge.Slot.LEGS));
        items.add(equipmentBridge.getItem(player, EquipmentBridge.Slot.FEET));
        return items;
    }

    private void addItems(List<ItemStack> target, ItemStack[] items) {
        if (items != null) {
            Collections.addAll(target, items);
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
