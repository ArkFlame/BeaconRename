package com.arkflame.flameforge.forge;

import com.arkflame.flameforge.compat.effect.ParticleBridge;
import com.arkflame.flameforge.compat.effect.particle.pattern.ParticleNetworkRenderer;
import com.arkflame.flameforge.compat.effect.particle.style.ParticleStyle;
import com.arkflame.flameforge.compat.effect.particle.style.ParticleStyleCatalog;
import com.arkflame.flameforge.compat.effect.particle.style.ParticleStyleId;
import com.arkflame.flameforge.compat.effect.PotionEffectResolver;
import com.arkflame.flameforge.compat.equipment.EquipmentBridge;
import com.arkflame.flameforge.compat.scheduler.SchedulerBridge;
import com.arkflame.flameforge.compat.scheduler.TaskHandle;
import com.arkflame.flameforge.config.TierRepository;
import com.arkflame.flameforge.item.ItemIdentityCodec;
import com.arkflame.flameforge.item.ItemIdentityService;
import com.arkflame.flameforge.model.ForgePowerDefinition;
import com.arkflame.flameforge.model.ForgeVariant;
import com.arkflame.flameforge.model.TierDefinition;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BooleanSupplier;

public final class ForgePowerService {

    private static final int DEFAULT_MAX_COOLDOWN_ENTRIES = 4096;
    private static final long TICK_MS = 50L;
    private static final int HEAL_PARTICLE_COUNT = 5;
    private static final float HEAL_PARTICLE_OFFSET_X = 0.35F;
    private static final float HEAL_PARTICLE_OFFSET_Y = 0.45F;
    private static final float HEAL_PARTICLE_OFFSET_Z = 0.35F;
    private static final float HEAL_PARTICLE_SPEED = 0F;
    private static final double HEAL_PARTICLE_Y_OFFSET = 1.0D;

    private final JavaPlugin plugin;
    private final SchedulerBridge schedulerBridge;
    private final ParticleBridge particleBridge;
    private final ParticleNetworkRenderer networkRenderer;
    private final MultiStrikeService multiStrikeService;
    private final PotionEffectResolver potionEffectResolver;
    private final EquipmentBridge equipmentBridge;
    private final ItemIdentityService identityService;
    private final TierRepository tierRepository;
    private final TimeSource timeSource;
    private final int maxCooldownEntries;
    private final BooleanSupplier powerTraceEnabled;

    private final Map<CooldownKey, Long> cooldowns = new ConcurrentHashMap<>();
    private final Map<HitCounterKey, AtomicLong> hitCounters = new ConcurrentHashMap<>();
    private final AtomicLong evictionCounter = new AtomicLong(0);
    private static final int PASSIVE_MAX_LEASE_TICKS = 40;
    private static final Runnable RETIRED_NOOP = () -> {};
    private final Map<UUID, Map<PassiveBindingKey, PassiveBinding>> passiveBindingsByPlayer = new ConcurrentHashMap<>();
    private final Map<UUID, Set<UUID>> inventoryForgeIdsByPlayer = new ConcurrentHashMap<>();

    public interface TimeSource {
        long monotonicMillis();
    }

    public static final class SystemTimeSource implements TimeSource {
        @Override
        public long monotonicMillis() {
            return TimeUnit.NANOSECONDS.toMillis(System.nanoTime());
        }
    }

    public ForgePowerService(JavaPlugin plugin, SchedulerBridge schedulerBridge,
                             PotionEffectResolver potionEffectResolver,
                             EquipmentBridge equipmentBridge,
                             ItemIdentityService identityService,
                             ParticleBridge particleBridge, MultiStrikeService multiStrikeService) {
        this(plugin, schedulerBridge, potionEffectResolver, equipmentBridge, identityService,
             particleBridge, multiStrikeService, new TierRepository(plugin),
             new SystemTimeSource(), DEFAULT_MAX_COOLDOWN_ENTRIES);
    }

    public ForgePowerService(JavaPlugin plugin, SchedulerBridge schedulerBridge,
                             ParticleBridge particleBridge,
                             PotionEffectResolver potionEffectResolver,
                             EquipmentBridge equipmentBridge,
                             ItemIdentityService identityService,
                             MultiStrikeService multiStrikeService) {
        this(plugin, schedulerBridge, particleBridge, potionEffectResolver, equipmentBridge,
             identityService, multiStrikeService, new TierRepository(plugin));
    }

    public ForgePowerService(JavaPlugin plugin, SchedulerBridge schedulerBridge,
                             ParticleBridge particleBridge,
                             PotionEffectResolver potionEffectResolver,
                             EquipmentBridge equipmentBridge,
                             ItemIdentityService identityService,
                             MultiStrikeService multiStrikeService,
                             TierRepository tierRepository) {
        this(plugin, schedulerBridge, potionEffectResolver, equipmentBridge, identityService,
             particleBridge, multiStrikeService, tierRepository,
             new SystemTimeSource(), DEFAULT_MAX_COOLDOWN_ENTRIES);
    }

    public ForgePowerService(JavaPlugin plugin, SchedulerBridge schedulerBridge,
                             ParticleBridge particleBridge,
                             PotionEffectResolver potionEffectResolver,
                             EquipmentBridge equipmentBridge,
                             ItemIdentityService identityService,
                             MultiStrikeService multiStrikeService,
                             TierRepository tierRepository,
                             BooleanSupplier powerTraceEnabled) {
        this(plugin, schedulerBridge, particleBridge, potionEffectResolver, equipmentBridge,
             identityService, multiStrikeService, tierRepository,
             new SystemTimeSource(), DEFAULT_MAX_COOLDOWN_ENTRIES, powerTraceEnabled);
    }

    public ForgePowerService(JavaPlugin plugin, SchedulerBridge schedulerBridge,
                             ParticleBridge particleBridge,
                             PotionEffectResolver potionEffectResolver,
                             EquipmentBridge equipmentBridge,
                             ItemIdentityService identityService,
                             MultiStrikeService multiStrikeService,
                             TierRepository tierRepository,
                             BooleanSupplier powerTraceEnabled,
                             ParticleNetworkRenderer networkRenderer) {
        this(plugin, schedulerBridge, particleBridge, potionEffectResolver, equipmentBridge,
             identityService, multiStrikeService, tierRepository,
             new SystemTimeSource(), DEFAULT_MAX_COOLDOWN_ENTRIES, powerTraceEnabled, networkRenderer);
    }

    public ForgePowerService(JavaPlugin plugin, SchedulerBridge schedulerBridge,
                              PotionEffectResolver potionEffectResolver,
                              EquipmentBridge equipmentBridge,
                              ItemIdentityService identityService,
                              ParticleBridge particleBridge, MultiStrikeService multiStrikeService,
                              TierRepository tierRepository,
                              TimeSource timeSource, int maxCooldownEntries) {
        this(plugin, schedulerBridge, particleBridge, potionEffectResolver, equipmentBridge,
            identityService, multiStrikeService, tierRepository, timeSource, maxCooldownEntries, () -> false);
    }

    public ForgePowerService(JavaPlugin plugin, SchedulerBridge schedulerBridge,
                              ParticleBridge particleBridge,
                              PotionEffectResolver potionEffectResolver,
                              EquipmentBridge equipmentBridge,
                              ItemIdentityService identityService,
                              MultiStrikeService multiStrikeService,
                              TierRepository tierRepository,
                              TimeSource timeSource, int maxCooldownEntries,
                              BooleanSupplier powerTraceEnabled) {
        this(plugin, schedulerBridge, particleBridge, potionEffectResolver, equipmentBridge,
            identityService, multiStrikeService, tierRepository, timeSource, maxCooldownEntries,
            powerTraceEnabled, null);
    }

    public ForgePowerService(JavaPlugin plugin, SchedulerBridge schedulerBridge,
                              ParticleBridge particleBridge,
                              PotionEffectResolver potionEffectResolver,
                              EquipmentBridge equipmentBridge,
                              ItemIdentityService identityService,
                              MultiStrikeService multiStrikeService,
                              TierRepository tierRepository,
                              TimeSource timeSource, int maxCooldownEntries,
                              BooleanSupplier powerTraceEnabled,
                              ParticleNetworkRenderer networkRenderer) {
        this.plugin = Objects.requireNonNull(plugin);
        this.schedulerBridge = Objects.requireNonNull(schedulerBridge);
        this.particleBridge = Objects.requireNonNull(particleBridge);
        this.networkRenderer = networkRenderer;
        this.multiStrikeService = Objects.requireNonNull(multiStrikeService);
        this.potionEffectResolver = Objects.requireNonNull(potionEffectResolver);
        this.equipmentBridge = Objects.requireNonNull(equipmentBridge);
        this.identityService = Objects.requireNonNull(identityService);
        this.tierRepository = Objects.requireNonNull(tierRepository);
        this.timeSource = Objects.requireNonNull(timeSource);
        this.maxCooldownEntries = maxCooldownEntries > 0 ? maxCooldownEntries : DEFAULT_MAX_COOLDOWN_ENTRIES;
        this.powerTraceEnabled = Objects.requireNonNull(powerTraceEnabled);
    }

    public boolean usePower(Player player, ForgePowerDefinition power, UUID forgeId) {
        CooldownKey key = new CooldownKey(player.getUniqueId(), forgeId, power.getId());
        int cooldownTicks = power.getCooldownTicks();
        if (cooldownTicks <= 0) {
            cooldowns.remove(key);
            tracePowerDecision(player, power, forgeId, "COOLDOWN_ACQUIRED", 0L, "cooldown=disabled");
            return true;
        }
        long cooldownMillis = ((long) cooldownTicks) * TICK_MS;
        for (;;) {
            long now = timeSource.monotonicMillis();
            Long lastUsed = cooldowns.get(key);
            if (lastUsed != null) {
                long elapsed = now - lastUsed;
                if (elapsed >= 0L && elapsed < cooldownMillis) {
                    tracePowerDecision(player, power, forgeId, "COOLDOWN_BLOCK",
                        cooldownMillis - elapsed, "cooldown=active");
                    return false;
                }
                if (cooldowns.replace(key, lastUsed, now)) {
                    tracePowerDecision(player, power, forgeId, "COOLDOWN_ACQUIRED", 0L,
                        elapsed < 0L ? "cooldown=regressed" : "cooldown=expired");
                    return true;
                }
                continue;
            }
            evictIfNeeded();
            if (cooldowns.putIfAbsent(key, now) == null) {
                tracePowerDecision(player, power, forgeId, "COOLDOWN_ACQUIRED", 0L, "cooldown=first-use");
                return true;
            }
        }
    }

    public boolean canUsePower(Player player, ForgePowerDefinition power, UUID forgeId) {
        CooldownKey key = new CooldownKey(player.getUniqueId(), forgeId, power.getId());
        if (power.getCooldownTicks() <= 0) {
            cooldowns.remove(key);
            return true;
        }
        Long lastUsed = cooldowns.get(key);
        if (lastUsed == null) {
            return true;
        }
        long cooldownMillis = ((long) power.getCooldownTicks()) * TICK_MS;
        long elapsed = timeSource.monotonicMillis() - lastUsed;
        if (elapsed < 0L || elapsed >= cooldownMillis) {
            cooldowns.remove(key, lastUsed);
            return true;
        }
        return false;
    }

    public void tracePowerEvent(Player player, String stage, String detail) {
        Objects.requireNonNull(player);
        Objects.requireNonNull(stage);
        Objects.requireNonNull(detail);
        if (!powerTraceEnabled.getAsBoolean()) {
            return;
        }
        plugin.getLogger().info("[PowerTrace] player=" + player.getUniqueId()
            + " stage=" + stage + " detail=" + sanitizeTraceDetail(detail));
    }

    private void tracePowerDecision(Player player, ForgePowerDefinition power, UUID forgeId,
                                    String stage, long remainingMillis, String detail) {
        StringBuilder context = new StringBuilder();
        context.append("forge=").append(forgeId);
        if (power != null) {
            context.append(" power=").append(power.getId());
            context.append(" type=").append(power.getPowerType());
            context.append(" cooldownTicks=").append(power.getCooldownTicks());
        }
        context.append(" remainingMs=").append(remainingMillis);
        context.append(" detail=").append(detail);
        tracePowerEvent(player, stage, context.toString());
    }

    private String sanitizeTraceDetail(String detail) {
        return detail.replace('\r', ' ').replace('\n', ' ');
    }

    private void evictIfNeeded() {
        if (cooldowns.size() < maxCooldownEntries) {
            return;
        }
        Iterator<Map.Entry<CooldownKey, Long>> iter = cooldowns.entrySet().iterator();
        if (iter.hasNext()) {
            iter.remove();
            evictionCounter.incrementAndGet();
        }
    }

    public void clearCooldownsForPlayer(Player player) {
        cooldowns.keySet().removeIf(key -> key.playerUuid.equals(player.getUniqueId()));
    }

    public void clearPassiveTasksForPlayer(Player player) {
        if (player == null) {
            return;
        }
        Map<PassiveBindingKey, PassiveBinding> bindings = passiveBindingsByPlayer.remove(player.getUniqueId());
        if (bindings != null) {
            for (PassiveBinding binding : bindings.values()) {
                binding.cancelRefresh();
            }
        }
    }

    public void clearCooldowns(UUID playerId) {
        cooldowns.keySet().removeIf(key -> key.playerUuid.equals(playerId));
    }

    public void clearCooldownsForPlayerAndForge(Player player, UUID forgeId) {
        cooldowns.keySet().removeIf(key -> key.playerUuid.equals(player.getUniqueId()) && key.forgeUuid.equals(forgeId));
    }

    public void clearHitCountersForPlayer(UUID playerId) {
        hitCounters.keySet().removeIf(key -> key.playerUuid.equals(playerId));
    }

    public void clearHitCountersForPlayerAndForge(UUID playerId, UUID forgeId) {
        hitCounters.keySet().removeIf(key -> key.playerUuid.equals(playerId) && key.forgeUuid.equals(forgeId));
    }

    public void clearAll() {
        cooldowns.clear();
        hitCounters.clear();
        inventoryForgeIdsByPlayer.clear();
        clearAllPassiveTasks();
    }

    public void clearAllPassiveTasks() {
        passiveBindingsByPlayer.values().forEach(bindings -> {
            for (PassiveBinding binding : bindings.values()) {
                binding.cancelRefresh();
            }
        });
        passiveBindingsByPlayer.clear();
    }

    public long getEvictionCount() {
        return evictionCounter.get();
    }

    public void refreshInventoryCache(Player player) {
        if (player == null) {
            return;
        }
        publishInventoryCache(player, captureItemSnapshot(player));
    }

    public Set<UUID> getCachedInventoryForgeIds(Player player) {
        if (player == null) {
            return Collections.emptySet();
        }
        Set<UUID> cached = inventoryForgeIdsByPlayer.get(player.getUniqueId());
        return cached == null ? Collections.<UUID>emptySet() : new HashSet<>(cached);
    }

    public boolean hasCachedInventoryForgeId(Player player, UUID forgeId) {
        if (player == null || forgeId == null) {
            return false;
        }
        Set<UUID> cached = inventoryForgeIdsByPlayer.get(player.getUniqueId());
        return cached != null && cached.contains(forgeId);
    }

    public void clearInventoryCacheForPlayer(Player player) {
        if (player != null) {
            inventoryForgeIdsByPlayer.remove(player.getUniqueId());
        }
    }

    public void emitArmorReductionParticle(Player player) {
        if (player == null) {
            return;
        }
        emitPowerParticles(player, player.getLocation(), ParticleStyleId.DEFENSIVE, 1);
    }

    public void refreshPassivePowers(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }
        Map<UUID, PassiveItemSnapshot> snapshot = captureItemSnapshot(player);
        publishInventoryCache(player, snapshot);
        reconcilePassiveBindings(player, snapshot);
    }

    private Map<UUID, PassiveItemSnapshot> captureItemSnapshot(Player player) {
        Map<UUID, PassiveItemSnapshot> snapshots = new LinkedHashMap<>();
        ItemStack[] storage = equipmentBridge.getStorageContents(player);
        if (storage != null) {
            for (ItemStack item : storage) {
                markSnapshotItem(snapshots, item, EquipmentBridge.Slot.INVENTORY);
            }
        }
        markSnapshotItem(snapshots, equipmentBridge.getItem(player, EquipmentBridge.Slot.MAINHAND),
            EquipmentBridge.Slot.MAINHAND);
        markSnapshotItem(snapshots, equipmentBridge.getItem(player, EquipmentBridge.Slot.OFFHAND),
            EquipmentBridge.Slot.OFFHAND);
        markSnapshotItem(snapshots, equipmentBridge.getItem(player, EquipmentBridge.Slot.HEAD),
            EquipmentBridge.Slot.HEAD);
        markSnapshotItem(snapshots, equipmentBridge.getItem(player, EquipmentBridge.Slot.CHEST),
            EquipmentBridge.Slot.CHEST);
        markSnapshotItem(snapshots, equipmentBridge.getItem(player, EquipmentBridge.Slot.LEGS),
            EquipmentBridge.Slot.LEGS);
        markSnapshotItem(snapshots, equipmentBridge.getItem(player, EquipmentBridge.Slot.FEET),
            EquipmentBridge.Slot.FEET);
        return snapshots;
    }

    private void markSnapshotItem(Map<UUID, PassiveItemSnapshot> snapshots, ItemStack item,
                                  EquipmentBridge.Slot slot) {
        if (item == null || !item.hasItemMeta()) {
            return;
        }
        ItemIdentityService.ForgeIdentityRead read = identityService.readForgeIdentity(item);
        if (read.getStatus() != ItemIdentityService.ForgeIdentityStatus.VALID) {
            return;
        }
        ItemIdentityCodec.Identity identity = read.getIdentity();
        if (identity.isCursed()) {
            return;
        }
        PassiveItemSnapshot existing = snapshots.get(identity.getForgeId());
        if (existing == null) {
            snapshots.put(identity.getForgeId(), new PassiveItemSnapshot(identity, slot));
        } else {
            existing.mergeSlot(slot);
        }
    }

    private void publishInventoryCache(Player player, Map<UUID, PassiveItemSnapshot> snapshot) {
        Set<UUID> forgeIds = new HashSet<>(snapshot.keySet());
        inventoryForgeIdsByPlayer.put(player.getUniqueId(), Collections.unmodifiableSet(forgeIds));
    }

    private List<ForgePowerDefinition> resolveActivePowers(String lastTierId, String lastVariantId,
                                                           List<String> activePowerIds) {
        if (lastTierId == null || lastVariantId == null
            || activePowerIds == null || activePowerIds.isEmpty()) {
            return Collections.emptyList();
        }
        Optional<TierDefinition> tier = tierRepository.findById(lastTierId);
        if (!tier.isPresent()) {
            return Collections.emptyList();
        }
        Set<String> activeSet = new HashSet<>(activePowerIds);
        List<ForgePowerDefinition> powers = new ArrayList<>();
        for (ForgeVariant variant : tier.get().getVariants()) {
            if (lastVariantId.equals(variant.getId())) {
                for (ForgePowerDefinition power : variant.getPowers()) {
                    if (activeSet.contains(power.getId())) {
                        powers.add(power);
                    }
                }
            }
        }
        return powers;
    }

    public boolean activatePassivePower(Player player, ForgePowerDefinition power, UUID forgeId) {
        if (power.getPowerType() != ForgePowerDefinition.PowerType.PASSIVE_POTION) {
            return false;
        }
        refreshPassivePowers(player);
        Map<PassiveBindingKey, PassiveBinding> current = passiveBindingsByPlayer.get(player.getUniqueId());
        return current != null && current.containsKey(new PassiveBindingKey(forgeId, power.getId()));
    }

    private void reconcilePassiveBindings(Player player, Map<UUID, PassiveItemSnapshot> snapshot) {
        UUID playerId = player.getUniqueId();
        Map<PassiveBindingKey, PassiveBinding> current = passiveBindingsByPlayer.get(playerId);
        if (current == null) {
            current = Collections.emptyMap();
        }
        Map<PassiveBindingKey, PassiveBinding> next = new HashMap<>();
        for (Map.Entry<UUID, PassiveItemSnapshot> entry : snapshot.entrySet()) {
            PassiveItemSnapshot itemSnapshot = entry.getValue();
            ItemIdentityCodec.Identity identity = itemSnapshot.getIdentity();
            List<ForgePowerDefinition> powers = resolveActivePowers(identity.getLastTierId(),
                identity.getLastVariantId(), identity.getActivePowerIds());
            for (ForgePowerDefinition power : powers) {
                if (power.getPowerType() != ForgePowerDefinition.PowerType.PASSIVE_POTION) {
                    continue;
                }
                if (!isPassiveActiveInSnapshot(power, itemSnapshot)) {
                    continue;
                }
                Optional<PotionEffectType> effectType = potionEffectResolver.resolve(power.getEffectCandidates());
                if (!effectType.isPresent()) {
                    continue;
                }
                int leaseTicks = effectiveLease(power.getDurationTicks());
                int amplifier = power.getAmplifier();
                PassiveBindingKey key = new PassiveBindingKey(entry.getKey(), power.getId());
                PassiveBinding existing = current.get(key);
                if (existing != null && existing.matches(effectType.get(), amplifier, leaseTicks)) {
                    next.put(key, existing);
                } else {
                    if (existing != null) {
                        existing.cancelRefresh();
                    }
                    PassiveBinding binding = new PassiveBinding(key, power, effectType.get(),
                        amplifier, leaseTicks, null);
                    player.addPotionEffect(new PotionEffect(binding.getEffectType(),
                        binding.getLeaseTicks(), binding.getAmplifier(), false, false));
                    emitPassiveActivationParticles(player, power);
                    scheduleBindingRefresh(player, binding);
                    next.put(key, binding);
                }
            }
        }
        for (Map.Entry<PassiveBindingKey, PassiveBinding> entry : current.entrySet()) {
            if (!next.containsKey(entry.getKey())) {
                entry.getValue().cancelRefresh();
            }
        }
        if (next.isEmpty()) {
            passiveBindingsByPlayer.remove(playerId);
        } else {
            passiveBindingsByPlayer.put(playerId, next);
        }
    }

    private boolean isPassiveActiveInSnapshot(ForgePowerDefinition power, PassiveItemSnapshot snapshot) {
        List<ForgePowerDefinition.ActivationSlot> slots = power.getActivationSlots();
        if (slots.isEmpty()) {
            return snapshot.getSlots().contains(EquipmentBridge.Slot.MAINHAND)
                || snapshot.getSlots().contains(EquipmentBridge.Slot.OFFHAND);
        }
        for (ForgePowerDefinition.ActivationSlot slot : slots) {
            EquipmentBridge.Slot bridgeSlot = convertSlot(slot);
            if (bridgeSlot != null && snapshot.getSlots().contains(bridgeSlot)) {
                return true;
            }
        }
        return false;
    }

    private EquipmentBridge.Slot convertSlot(ForgePowerDefinition.ActivationSlot slot) {
        switch (slot) {
            case MAINHAND: return EquipmentBridge.Slot.MAINHAND;
            case OFFHAND: return EquipmentBridge.Slot.OFFHAND;
            case HEAD: return EquipmentBridge.Slot.HEAD;
            case CHEST: return EquipmentBridge.Slot.CHEST;
            case LEGS: return EquipmentBridge.Slot.LEGS;
            case FEET: return EquipmentBridge.Slot.FEET;
            case INVENTORY: return EquipmentBridge.Slot.INVENTORY;
            default: return null;
        }
    }

    private int effectiveLease(int durationTicks) {
        return Math.max(2, Math.min(durationTicks, PASSIVE_MAX_LEASE_TICKS));
    }

    private int passiveRefreshDelay(int leaseTicks) {
        return Math.max(1, Math.min(20, leaseTicks / 2));
    }

    private void scheduleBindingRefresh(Player player, PassiveBinding binding) {
        if (!player.isOnline()) {
            return;
        }
        final UUID playerId = player.getUniqueId();
        final PassiveBindingKey key = binding.getKey();
        final LivingEntity entity = player;
        final TaskHandle[] handleRef = new TaskHandle[1];
        handleRef[0] = schedulerBridge.runEntityLater(entity, new Runnable() {
            @Override
            public void run() {
                if (!player.isOnline()) {
                    return;
                }
                Map<PassiveBindingKey, PassiveBinding> bindings = passiveBindingsByPlayer.get(playerId);
                if (bindings == null || bindings.get(key) != binding) {
                    return;
                }
                player.addPotionEffect(new PotionEffect(binding.getEffectType(),
                    binding.getLeaseTicks(), binding.getAmplifier(), false, false));
                scheduleBindingRefresh(player, binding);
            }
        }, new Runnable() {
            @Override
            public void run() {
                Map<PassiveBindingKey, PassiveBinding> bindings = passiveBindingsByPlayer.get(playerId);
                if (bindings != null && bindings.get(key) == binding && binding.getRefreshHandle() == handleRef[0]) {
                    binding.clearRefreshHandle();
                }
            }
        }, passiveRefreshDelay(binding.getLeaseTicks()));
        if (handleRef[0] != null) {
            binding.setRefreshHandle(handleRef[0]);
        }
    }

    private void emitPowerParticles(Player viewer, Location location, ParticleStyleId styleId, int count) {
        if (viewer == null || location == null || location.getWorld() == null) {
            return;
        }
        ParticleStyle style = ParticleStyleCatalog.get(styleId);
        particleBridge.sendFirstAvailable(viewer, location, style.getCandidates(),
            0F, 0F, 0F, 0F, count);
        particleBridge.sendColoredDust(viewer, location, style.getRed(), style.getGreen(),
            style.getBlue(), 1F, count);
    }

    private List<String> powerParticleCandidates(ForgePowerDefinition power, ParticleStyle style) {
        List<String> configuredCandidates = power.getParticleCandidates();
        List<String> styleCandidates = style.getCandidates();
        Set<String> candidates = new LinkedHashSet<>();
        if (configuredCandidates != null) {
            candidates.addAll(configuredCandidates);
        }
        if (styleCandidates != null) {
            candidates.addAll(styleCandidates);
        }
        return new ArrayList<>(candidates);
    }

    private void emitPowerParticles(Player viewer, Location location, ForgePowerDefinition power,
                                    ParticleStyleId styleId, int count) {
        emitPowerParticles(viewer, location, power, styleId, count, 0F, 0F, 0F, 0F);
    }

    private void emitPowerParticles(Player viewer, Location location, ForgePowerDefinition power,
                                    ParticleStyleId styleId, int count, float offsetX,
                                    float offsetY, float offsetZ, float speed) {
        if (viewer == null || location == null || location.getWorld() == null) {
            return;
        }
        ParticleStyle style = ParticleStyleCatalog.get(styleId);
        particleBridge.sendFirstAvailable(viewer, location, powerParticleCandidates(power, style),
            offsetX, offsetY, offsetZ, speed, count);
        particleBridge.sendColoredDust(viewer, location, style.getRed(), style.getGreen(),
            style.getBlue(), 1F, count);
    }

    private void emitPotionParticles(Player viewer, Location location, ForgePowerDefinition power,
                                     PotionEffectType effectType) {
        ParticleStyleId styleId = PotionEffectType.POISON.equals(effectType)
            ? ParticleStyleId.POISON
            : PotionEffectType.WITHER.equals(effectType)
                ? ParticleStyleId.WITHER
                : PotionEffectType.SPEED.equals(effectType)
                    ? ParticleStyleId.SWIFT
                    : PotionEffectType.DAMAGE_RESISTANCE.equals(effectType)
                        ? ParticleStyleId.DEFENSIVE
                        : PotionEffectType.FAST_DIGGING.equals(effectType)
                            ? ParticleStyleId.HASTE
                            : PotionEffectType.REGENERATION.equals(effectType)
                                ? ParticleStyleId.HEAL : ParticleStyleId.GENERIC_MAGIC;
        emitPowerParticles(viewer, location, power, styleId, 1);
    }

    private void emitHealParticles(Player player, ForgePowerDefinition power) {
        if (player == null || power == null) {
            return;
        }
        Location location = player.getLocation().clone().add(0, HEAL_PARTICLE_Y_OFFSET, 0);
        emitPowerParticles(player, location, power, ParticleStyleId.HEAL, HEAL_PARTICLE_COUNT,
            HEAL_PARTICLE_OFFSET_X, HEAL_PARTICLE_OFFSET_Y, HEAL_PARTICLE_OFFSET_Z,
            HEAL_PARTICLE_SPEED);
    }

    private void emitPassiveActivationParticles(Player player, ForgePowerDefinition power) {
        if (player == null || power == null) {
            return;
        }
        Set<ParticleStyleId> styles = new LinkedHashSet<>();
        for (String effectCandidate : power.getEffectCandidates()) {
            if (effectCandidate == null) {
                continue;
            }
            ParticleStyleId styleId = passiveStyleId(effectCandidate);
            if (styleId != null) {
                styles.add(styleId);
            }
        }
        for (ParticleStyleId styleId : styles) {
            emitPowerParticles(player, player.getLocation(), power, styleId, 1);
        }
    }

    private ParticleStyleId passiveStyleId(String effectCandidate) {
        switch (effectCandidate.toUpperCase()) {
            case "REGENERATION":
                return ParticleStyleId.HEAL;
            case "SPEED":
                return ParticleStyleId.SWIFT;
            case "FAST_DIGGING":
            case "HASTE":
                return ParticleStyleId.HASTE;
            case "DAMAGE_RESISTANCE":
            case "RESISTANCE":
                return ParticleStyleId.DEFENSIVE;
            default:
                return null;
        }
    }

    public boolean triggerOnHitPower(Player attacker, LivingEntity victim, ForgePowerDefinition power, UUID forgeId) {
        return triggerOnHitPower(attacker, victim, power, forgeId, false);
    }

    public boolean triggerOnHitPower(Player attacker, LivingEntity victim, ForgePowerDefinition power,
                                     UUID forgeId, boolean lethalHit) {
        ForgePowerDefinition.PowerType type = power.getPowerType();
        if (type == ForgePowerDefinition.PowerType.EVERY_N_HIT_LIGHTNING
            || type == ForgePowerDefinition.PowerType.EVERY_N_HIT_KNOCKBACK) {
            return triggerEveryNHitPower(attacker, victim, power, forgeId);
        }
        if (type != ForgePowerDefinition.PowerType.ON_HIT_POTION
            && type != ForgePowerDefinition.PowerType.ON_HIT_FIRE
            && type != ForgePowerDefinition.PowerType.ON_HIT_HEAL
            && type != ForgePowerDefinition.PowerType.ON_HIT_AOE_FIRE
            && type != ForgePowerDefinition.PowerType.ON_HIT_BLEED
            && type != ForgePowerDefinition.PowerType.ON_HIT_EXPLOSIVE
            && type != ForgePowerDefinition.PowerType.ON_HIT_CHAIN_POTION
            && type != ForgePowerDefinition.PowerType.ON_HIT_CHAIN_DAMAGE) {
            return false;
        }
        tracePowerDecision(attacker, power, forgeId, "POWER_ENTRY", 0L, "entry");
        boolean lethalPrimaryFireApplied = false;
        if (lethalHit && (type == ForgePowerDefinition.PowerType.ON_HIT_FIRE
            || type == ForgePowerDefinition.PowerType.ON_HIT_AOE_FIRE)
            && power.getFireTicks() > 0) {
            victim.setFireTicks(Math.max(victim.getFireTicks(), power.getFireTicks()));
            lethalPrimaryFireApplied = true;
        }
        BigDecimal chance = power.getChance();
        if (!rollChance(chance)) {
            tracePowerDecision(attacker, power, forgeId, "CHANCE_MISS", 0L,
                "chance=" + chance);
            return false;
        }
        if (!usePower(attacker, power, forgeId)) {
            return false;
        }
        switch (type) {
            case ON_HIT_POTION:
                applyOnHitPotion(attacker, victim, power);
                break;
            case ON_HIT_FIRE:
                applyOnHitFire(attacker, victim, power, lethalPrimaryFireApplied);
                break;
            case ON_HIT_HEAL:
                applyOnHitHeal(attacker, victim, power);
                break;
            case ON_HIT_AOE_FIRE:
                applyAoeFire(attacker, victim, power, lethalPrimaryFireApplied);
                break;
            case ON_HIT_BLEED:
                applyBleed(attacker, victim, power);
                break;
            case ON_HIT_EXPLOSIVE:
                applyExplosive(attacker, victim, power);
                break;
            case ON_HIT_CHAIN_POTION:
                applyChainPotion(attacker, victim, power);
                break;
            case ON_HIT_CHAIN_DAMAGE:
                applyChainDamage(attacker, victim, power);
                break;
            default:
                break;
        }
        tracePowerDecision(attacker, power, forgeId, "POWER_APPLIED", 0L, "applied");
        return true;
    }

    public boolean triggerOnBlockPower(Player defender, LivingEntity attacker,
                                       ForgePowerDefinition power, UUID forgeId) {
        ForgePowerDefinition.PowerType type = power.getPowerType();
        if (type != ForgePowerDefinition.PowerType.ON_BLOCK_POTION
            && type != ForgePowerDefinition.PowerType.ON_BLOCK_KNOCKBACK
            && type != ForgePowerDefinition.PowerType.ON_BLOCK_HEAL) {
            return false;
        }
        if (!rollChance(power.getChance()) || !usePower(defender, power, forgeId)) {
            return false;
        }
        if (type == ForgePowerDefinition.PowerType.ON_BLOCK_POTION) {
            applyOnHitPotion(defender, attacker, power);
        } else if (type == ForgePowerDefinition.PowerType.ON_BLOCK_KNOCKBACK) {
            applyKnockback(defender, attacker, power);
        } else {
            applySelfHeal(defender, power);
        }
        return true;
    }

    private boolean triggerEveryNHitPower(Player attacker, LivingEntity victim, ForgePowerDefinition power, UUID forgeId) {
        ForgePowerDefinition.PowerType type = power.getPowerType();
        HitCounterKey counterKey = new HitCounterKey(attacker.getUniqueId(), forgeId, power.getId());
        AtomicLong counter = hitCounters.computeIfAbsent(counterKey, k -> new AtomicLong(0));
        long hits = counter.incrementAndGet();
        int hitInterval = power.getHitInterval();
        if (hitInterval <= 0) {
            return false;
        }
        if (hits < hitInterval) {
            return false;
        }
        counter.set(0);
        if (!rollChance(power.getChance())) {
            return false;
        }
        if (!usePower(attacker, power, forgeId)) {
            return false;
        }
        switch (type) {
            case EVERY_N_HIT_LIGHTNING:
                applyLightning(attacker, victim.getLocation().clone(), power);
                break;
            case EVERY_N_HIT_KNOCKBACK:
                applyKnockback(attacker, victim, power);
                break;
            default:
                break;
        }
        return true;
    }

    private void applyLightning(Player attacker, Location location, ForgePowerDefinition power) {
        if (location == null || location.getWorld() == null) {
            return;
        }
        emitPowerParticles(attacker, location, power, ParticleStyleId.ELECTRIC, 1);
        schedulerBridge.runRegion(location, () -> location.getWorld().strikeLightning(location));
    }

    private void applyKnockback(Player attacker, LivingEntity victim, ForgePowerDefinition power) {
        BigDecimal horizontal = power.getHorizontalStrength();
        BigDecimal vertical = power.getVerticalStrength();
        double horizontalStrength = horizontal != null ? horizontal.doubleValue() : 1.0;
        double verticalStrength = vertical != null ? vertical.doubleValue() : 0.3;
        double victimX = victim.getLocation().getX();
        double victimZ = victim.getLocation().getZ();
        double attackerX = attacker.getLocation().getX();
        double attackerZ = attacker.getLocation().getZ();
        double dirX = victimX - attackerX;
        double dirZ = victimZ - attackerZ;
        double len = Math.sqrt(dirX * dirX + dirZ * dirZ);
        if (len < 0.001) {
            org.bukkit.util.Vector facing = attacker.getLocation().getDirection();
            dirX = facing.getX();
            dirZ = facing.getZ();
            len = Math.sqrt(dirX * dirX + dirZ * dirZ);
        }
        if (len < 0.001) {
            dirX = 0;
            dirZ = 1;
            len = 1;
        }
        double normX = dirX / len;
        double normZ = dirZ / len;
        double knockX = normX * horizontalStrength;
        double knockZ = normZ * horizontalStrength;
        Vector vector = new Vector(knockX, verticalStrength, knockZ);
        emitPowerParticles(attacker, victim.getLocation(), power, ParticleStyleId.DEFENSIVE, 1);
        schedulerBridge.runEntity(victim, () -> victim.setVelocity(vector), () -> {});
    }

    private boolean rollChance(BigDecimal chance) {
        if (chance == null || chance.compareTo(BigDecimal.ONE) >= 0) {
            return true;
        }
        if (chance.compareTo(BigDecimal.ZERO) <= 0) {
            return false;
        }
        double roll = Math.random();
        return chance.doubleValue() >= roll;
    }

    private void applyOnHitPotion(Player attacker, LivingEntity victim, ForgePowerDefinition power) {
        List<String> candidates = power.getEffectCandidates();
        if (candidates.isEmpty()) {
            return;
        }
        Optional<PotionEffectType> effectType = potionEffectResolver.resolve(candidates);
        if (!effectType.isPresent()) {
            return;
        }
        int duration = power.getDurationTicks();
        int amplifier = power.getAmplifier();
        LivingEntity entity = victim;
        entity.addPotionEffect(new PotionEffect(effectType.get(), duration, amplifier, false, false));
        emitPotionParticles(attacker, victim.getLocation(), power, effectType.get());
    }

    private void applyOnHitFire(Player attacker, LivingEntity victim, ForgePowerDefinition power,
                                boolean primaryAlreadyApplied) {
        int fireTicks = power.getFireTicks();
        if (fireTicks <= 0) {
            return;
        }
        LivingEntity entity = victim;
        if (!primaryAlreadyApplied) {
            entity.setFireTicks(fireTicks);
        }
        emitPowerParticles(attacker, victim.getLocation(), power, ParticleStyleId.FIRE, 1);
    }

    private void applyOnHitHeal(Player attacker, LivingEntity victim, ForgePowerDefinition power) {
        BigDecimal healAmount = power.getHealAmount();
        if (healAmount == null || healAmount.signum() <= 0) {
            return;
        }
        double heal = healAmount.doubleValue();
        double maxHealth = attacker.getMaxHealth();
        double newHealth = Math.min(attacker.getHealth() + heal, maxHealth);
        attacker.setHealth(newHealth);
        emitHealParticles(attacker, power);
    }

    private void applyAoeFire(final Player attacker, final LivingEntity victim,
                              final ForgePowerDefinition power, final boolean primaryAlreadyApplied) {
        final int fireTicks = power.getFireTicks();
        if (fireTicks <= 0) {
            return;
        }
        emitPowerParticles(attacker, victim.getLocation(), power, ParticleStyleId.FIRE, 1);
        multiStrikeService.executeRadial(attacker, victim, power, false, new MultiStrikeService.StrikeAction() {
            @Override
            public void apply(LivingEntity target) {
                if (target != victim || !primaryAlreadyApplied) {
                    target.setFireTicks(fireTicks);
                }
                if (target != victim) {
                    emitPowerParticles(attacker, target.getLocation(), power, ParticleStyleId.FIRE, 1);
                }
            }
        });
    }

    private void applyBleed(final Player attacker, final LivingEntity victim, final ForgePowerDefinition power) {
        emitBleedVisuals(attacker, victim, power);
        scheduleBleedPulse(attacker, victim, power, power.getPulseCount());
    }

    private void emitBleedVisuals(Player attacker, LivingEntity victim, ForgePowerDefinition power) {
        if (attacker == null || victim == null) {
            return;
        }
        Location location = victim.getLocation();
        if (location == null || location.getWorld() == null) {
            return;
        }
        Location center = location.clone().add(0, 0.90, 0);
        Location left = location.clone().add(0.18, 1.12, -0.12);
        Location right = location.clone().add(-0.16, 1.28, 0.14);
        ParticleStyle bleedStyle = ParticleStyleCatalog.get(ParticleStyleId.BLEED);
        particleBridge.sendBlockBreak(attacker, center, Material.REDSTONE_BLOCK, 4);
        particleBridge.sendBlockBreak(attacker, left, Material.REDSTONE_BLOCK, 4);
        particleBridge.sendBlockBreak(attacker, right, Material.REDSTONE_BLOCK, 4);
        particleBridge.sendColoredDust(attacker, center, bleedStyle.getRed(), bleedStyle.getGreen(),
            bleedStyle.getBlue(), 1F, 2);
        particleBridge.sendFirstAvailable(attacker, center,
            powerParticleCandidates(power, bleedStyle),
            0F, 0F, 0F, 0F, 2);
    }

    private void scheduleBleedPulse(final Player attacker, final LivingEntity victim,
                                    final ForgePowerDefinition power, final int remaining) {
        if (remaining <= 0) {
            return;
        }
        schedulerBridge.runEntityLater(victim, new Runnable() {
            @Override
            public void run() {
                if (victim.isDead()) {
                    return;
                }
                victim.damage(power.getDamageAmount().doubleValue());
                emitBleedVisuals(attacker, victim, power);
                scheduleBleedPulse(attacker, victim, power, remaining - 1);
            }
        }, RETIRED_NOOP, power.getPulseIntervalTicks());
    }

    private void applyExplosive(final Player attacker, final LivingEntity victim,
                                final ForgePowerDefinition power) {
        emitPowerParticles(attacker, victim.getLocation(), power, ParticleStyleId.EXPLOSIVE, 1);
        final AtomicBoolean primary = new AtomicBoolean(true);
        multiStrikeService.executeRadial(attacker, victim, power, false, new MultiStrikeService.StrikeAction() {
            @Override
            public void apply(LivingEntity target) {
                double multiplier = primary.getAndSet(false) ? 1.0
                    : power.getSecondaryDamageMultiplier().doubleValue();
                double damage = power.getDamageAmount().doubleValue() * multiplier;
                if (damage > 0) {
                    target.damage(damage);
                }
                if (target != victim) {
                    emitPowerParticles(attacker, target.getLocation(), power, ParticleStyleId.EXPLOSIVE, 1);
                }
                if (power.getPrimaryKnockbackMultiplier().signum() > 0) {
                    schedulerBridge.runEntityLater(target, new Runnable() {
                        @Override
                        public void run() {
                            if (!target.isDead()) {
                                target.setVelocity(new Vector(0,
                                    power.getPrimaryKnockbackMultiplier().doubleValue(), 0));
                            }
                        }
                    }, () -> {}, 1L);
                }
            }
        });
    }

    private void applyChainPotion(Player attacker, LivingEntity victim, ForgePowerDefinition power) {
        Optional<PotionEffectType> effectType = potionEffectResolver.resolve(power.getEffectCandidates());
        if (!effectType.isPresent()) {
            return;
        }
        final PotionEffect effect = new PotionEffect(effectType.get(), power.getDurationTicks(),
            power.getAmplifier(), false, false);
        emitPotionParticles(attacker, victim.getLocation(), power, effectType.get());
        multiStrikeService.executeChain(attacker, victim, power, false, new MultiStrikeService.StrikeAction() {
            @Override
            public void apply(LivingEntity target) {
                target.addPotionEffect(effect);
                if (target != victim) {
                    emitPotionParticles(attacker, target.getLocation(), power, effectType.get());
                }
            }
        });
    }

    private void applyChainDamage(Player attacker, LivingEntity victim, final ForgePowerDefinition power) {
        emitPowerParticles(attacker, victim.getLocation(), power, ParticleStyleId.ELECTRIC, 4);
        multiStrikeService.executeChain(attacker, victim, power, false, new MultiStrikeService.StrikeAction() {
            @Override
            public void apply(LivingEntity target) {
                double damage = power.getDamageAmount().doubleValue();
                if (damage > 0) {
                    target.damage(damage);
                }
                if (target != victim) {
                    emitPowerParticles(attacker, target.getLocation(), power, ParticleStyleId.ELECTRIC, 4);
                }
            }
        });
    }

    public boolean activateDash(Player player, ForgePowerDefinition power, UUID forgeId) {
        if (power.getPowerType() != ForgePowerDefinition.PowerType.SHIFT_RIGHT_CLICK_DASH) {
            return false;
        }
        if (!usePower(player, power, forgeId)) {
            return false;
        }
        applyDash(player, power);
        return true;
    }

    private void applyDash(Player player, ForgePowerDefinition power) {
        BigDecimal horizontal = power.getHorizontalStrength();
        BigDecimal vertical = power.getVerticalStrength();
        double horiz = horizontal != null ? horizontal.doubleValue() : 1.0;
        double vert = vertical != null ? vertical.doubleValue() : 0.0;
        applyVelocity(player, power, horiz, vert);
    }

    private void applyVelocity(Player player, ForgePowerDefinition power,
                               double horizontalStrength, double verticalStrength) {
        if (player == null) {
            return;
        }
        final Vector direction = player.getLocation().getDirection();
        double horizX = direction.getX();
        double horizZ = direction.getZ();
        double horizontalLength = Math.sqrt(horizX * horizX + horizZ * horizZ);
        if (horizontalLength < 0.001) {
            horizX = 0;
            horizZ = 1;
            horizontalLength = 1;
        }
        final double velocityX = horizX / horizontalLength * horizontalStrength;
        final double velocityZ = horizZ / horizontalLength * horizontalStrength;
        final double velocityY = verticalStrength > 0
            ? verticalStrength : direction.getY() * horizontalStrength;
        schedulerBridge.runEntity(player, new Runnable() {
            @Override
            public void run() {
                player.setVelocity(new Vector(velocityX, velocityY, velocityZ));
                emitPowerParticles(player, player.getLocation(), power, ParticleStyleId.SWIFT, 1);
            }
        }, RETIRED_NOOP);
    }

    public boolean activateHeal(Player player, ForgePowerDefinition power, UUID forgeId) {
        if (power.getPowerType() != ForgePowerDefinition.PowerType.SHIFT_RIGHT_CLICK_HEAL) {
            return false;
        }
        if (!usePower(player, power, forgeId)) {
            return false;
        }
        applySelfHeal(player, power);
        return true;
    }

    private void applySelfHeal(Player player, ForgePowerDefinition power) {
        BigDecimal healAmount = power.getHealAmount();
        if (healAmount == null || healAmount.signum() <= 0) {
            return;
        }
        double heal = healAmount.doubleValue();
        double maxHealth = player.getMaxHealth();
        double newHealth = Math.min(player.getHealth() + heal, maxHealth);
        player.setHealth(newHealth);
        emitHealParticles(player, power);
    }

    public boolean isCooldownExpired(Player player, ForgePowerDefinition power, UUID forgeId) {
        return canUsePower(player, power, forgeId);
    }

    static final class PassiveBindingKey {
        private final UUID forgeId;
        private final String powerId;

        PassiveBindingKey(UUID forgeId, String powerId) {
            this.forgeId = Objects.requireNonNull(forgeId);
            this.powerId = Objects.requireNonNull(powerId);
        }

        UUID getForgeId() {
            return forgeId;
        }

        String getPowerId() {
            return powerId;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof PassiveBindingKey)) return false;
            PassiveBindingKey that = (PassiveBindingKey) o;
            return forgeId.equals(that.forgeId) && powerId.equals(that.powerId);
        }

        @Override
        public int hashCode() {
            return 31 * forgeId.hashCode() + powerId.hashCode();
        }

        @Override
        public String toString() {
            return "PassiveBindingKey{forgeId=" + forgeId + ", powerId=" + powerId + "}";
        }
    }

    static final class PassiveBinding {
        private final PassiveBindingKey key;
        private final ForgePowerDefinition power;
        private final PotionEffectType effectType;
        private final int amplifier;
        private final int leaseTicks;
        private TaskHandle refreshHandle;

        PassiveBinding(PassiveBindingKey key, ForgePowerDefinition power, PotionEffectType effectType,
                       int amplifier, int leaseTicks, TaskHandle refreshHandle) {
            this.key = Objects.requireNonNull(key);
            this.power = Objects.requireNonNull(power);
            this.effectType = Objects.requireNonNull(effectType);
            this.amplifier = amplifier;
            this.leaseTicks = leaseTicks;
            this.refreshHandle = refreshHandle;
        }

        PassiveBindingKey getKey() {
            return key;
        }

        ForgePowerDefinition getPower() {
            return power;
        }

        PotionEffectType getEffectType() {
            return effectType;
        }

        int getAmplifier() {
            return amplifier;
        }

        int getLeaseTicks() {
            return leaseTicks;
        }

        TaskHandle getRefreshHandle() {
            return refreshHandle;
        }

        void setRefreshHandle(TaskHandle refreshHandle) {
            this.refreshHandle = refreshHandle;
        }

        void clearRefreshHandle() {
            this.refreshHandle = null;
        }

        boolean matches(PotionEffectType effectType, int amplifier, int leaseTicks) {
            return this.effectType.equals(effectType)
                && this.amplifier == amplifier && this.leaseTicks == leaseTicks;
        }

        void cancelRefresh() {
            if (refreshHandle != null) {
                refreshHandle.cancel();
                refreshHandle = null;
            }
        }
    }

    static final class PassiveItemSnapshot {
        private final ItemIdentityCodec.Identity identity;
        private final EnumSet<EquipmentBridge.Slot> slots;

        PassiveItemSnapshot(ItemIdentityCodec.Identity identity, EquipmentBridge.Slot slot) {
            this.identity = Objects.requireNonNull(identity);
            this.slots = EnumSet.of(slot);
        }

        ItemIdentityCodec.Identity getIdentity() {
            return identity;
        }

        Set<EquipmentBridge.Slot> getSlots() {
            return Collections.unmodifiableSet(EnumSet.copyOf(slots));
        }

        void mergeSlot(EquipmentBridge.Slot slot) {
            slots.add(slot);
        }
    }

    private static final class CooldownKey {
        private final UUID playerUuid;
        private final UUID forgeUuid;
        private final String powerId;

        CooldownKey(UUID playerUuid, UUID forgeUuid, String powerId) {
            this.playerUuid = playerUuid;
            this.forgeUuid = forgeUuid;
            this.powerId = powerId;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof CooldownKey)) return false;
            CooldownKey that = (CooldownKey) o;
            return playerUuid.equals(that.playerUuid) && forgeUuid.equals(that.forgeUuid) && powerId.equals(that.powerId);
        }

        @Override
        public int hashCode() {
            return 31 * playerUuid.hashCode() + 17 * forgeUuid.hashCode() + powerId.hashCode();
        }
    }

    private static final class HitCounterKey {
        private final UUID playerUuid;
        private final UUID forgeUuid;
        private final String powerId;

        HitCounterKey(UUID playerUuid, UUID forgeUuid, String powerId) {
            this.playerUuid = playerUuid;
            this.forgeUuid = forgeUuid;
            this.powerId = powerId;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof HitCounterKey)) return false;
            HitCounterKey that = (HitCounterKey) o;
            return playerUuid.equals(that.playerUuid) && forgeUuid.equals(that.forgeUuid) && powerId.equals(that.powerId);
        }

        @Override
        public int hashCode() {
            return 31 * playerUuid.hashCode() + 17 * forgeUuid.hashCode() + powerId.hashCode();
        }
    }
}
