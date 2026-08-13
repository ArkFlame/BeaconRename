package com.arkflame.flameforge.listener;

import com.arkflame.flameforge.compat.equipment.EquipmentBridge;
import com.arkflame.flameforge.compat.interaction.InteractionHandBridge;
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
import org.bukkit.entity.Projectile;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerPickupItemEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.Bukkit;
import org.bukkit.plugin.EventExecutor;
import org.bukkit.plugin.java.JavaPlugin;

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
    private final Set<UUID> refreshPending = Collections.newSetFromMap(new ConcurrentHashMap<UUID, Boolean>());

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
        this.handBridge = new InteractionHandBridge(null);
        registerSwapHandListener();
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
                    if (!attributeBridge.isModernAttributesAvailable()) {
                        double attackBonus = sumAttackDamageBonus(identity);
                        if (attackBonus > 0) {
                            event.setDamage(event.getDamage() + attackBonus);
                        }
                    }
                }
            }
        }

        if (damager instanceof Player && victim instanceof LivingEntity) {
            Player attacker = (Player) damager;
            LivingEntity victimEntity = (LivingEntity) victim;
            handleOnHit(attacker, victimEntity);
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
            double reduction = getDamageReductionFromIdentity(identity, cause);
            if (reduction > maxReduction) {
                maxReduction = reduction;
            }
        }
        return Math.min(maxReduction, 0.80);
    }

    private double getDamageReductionFromIdentity(ItemIdentityCodec.Identity identity, EntityDamageEvent.DamageCause cause) {
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
                .filter(attr -> attr.getType() == ForgeAttributeDefinition.AttributeType.DAMAGE_REDUCTION_PERCENT
                    || causeAttributeType(cause) == attr.getType())
                .mapToDouble(ForgeAttributeDefinition::getMultiplier)
                .max()
                .orElse(0.0))
            .orElse(0.0);
    }

    private ForgeAttributeDefinition.AttributeType causeAttributeType(EntityDamageEvent.DamageCause cause) {
        if (cause == EntityDamageEvent.DamageCause.POISON) {
            return ForgeAttributeDefinition.AttributeType.POISON_REDUCTION_PERCENT;
        }
        if (cause == EntityDamageEvent.DamageCause.MAGIC) {
            return ForgeAttributeDefinition.AttributeType.MAGIC_REDUCTION_PERCENT;
        }
        if (cause == EntityDamageEvent.DamageCause.FALL) {
            return ForgeAttributeDefinition.AttributeType.FALL_REDUCTION_PERCENT;
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
            powerService.triggerOnHitPower(attacker, victim, power, forgeId);
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
        powerService.clearInventoryCacheForPlayer(player);
        refreshPending.remove(player.getUniqueId());
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
        markDirty(player);
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
        markDirty(player);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (event != null && event.getWhoClicked() instanceof Player) {
            markDirty((Player) event.getWhoClicked());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event != null && event.getWhoClicked() instanceof Player) {
            markDirty((Player) event.getWhoClicked());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerDropItem(PlayerDropItemEvent event) {
        if (event != null) {
            markDirty(event.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerPickupItem(PlayerPickupItemEvent event) {
        if (event != null) {
            markDirty(event.getPlayer());
        }
    }

    private void registerSwapHandListener() {
        try {
            final Class<?> eventClass = Class.forName("org.bukkit.event.player.PlayerSwapHandItemsEvent");
            Bukkit.getPluginManager().registerEvent((Class) eventClass, this, EventPriority.MONITOR,
                new EventExecutor() {
                    @Override
                    public void execute(org.bukkit.event.Listener listener, Event event) {
                        try {
                            Object player = eventClass.getMethod("getPlayer").invoke(event);
                            if (player instanceof Player) {
                                markDirty((Player) player);
                            }
                        } catch (Exception ignored) {
                        }
                    }
                }, JavaPlugin.getProvidingPlugin(ForgePowerListener.class));
        } catch (ClassNotFoundException ignored) {
        } catch (RuntimeException ignored) {
        }
    }

    private void markDirty(final Player player) {
        if (player == null || !refreshPending.add(player.getUniqueId())) {
            return;
        }
        schedulerBridge.runEntityLater(player, () -> {
            refreshPending.remove(player.getUniqueId());
            refreshPassivePowers(player);
        }, RETIRED_NOOP, 1L);
    }

    private void refreshPassivePowers(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }
        powerService.clearPassiveTasksForPlayer(player);
        powerService.refreshInventoryCache(player);
        List<ItemStack> itemsToScan = new ArrayList<>();
        addItems(itemsToScan, equipmentBridge.getInventoryContents(player));
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
            if (slot == ForgePowerDefinition.ActivationSlot.INVENTORY) {
                ItemIdentityService.ForgeIdentityRead read = identityService.readForgeIdentity(item);
                return read.getStatus() == ItemIdentityService.ForgeIdentityStatus.VALID
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
