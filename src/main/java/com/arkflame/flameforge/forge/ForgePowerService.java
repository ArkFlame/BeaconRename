package com.arkflame.flameforge.forge;

import com.arkflame.flameforge.compat.effect.ParticleBridge;
import com.arkflame.flameforge.compat.effect.PotionEffectResolver;
import com.arkflame.flameforge.compat.equipment.EquipmentBridge;
import com.arkflame.flameforge.compat.scheduler.SchedulerBridge;
import com.arkflame.flameforge.compat.scheduler.TaskHandle;
import com.arkflame.flameforge.item.ItemIdentityService;
import com.arkflame.flameforge.model.ForgePowerDefinition;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import org.bukkit.Location;
import org.bukkit.util.Vector;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

public final class ForgePowerService {

    private static final int DEFAULT_MAX_COOLDOWN_ENTRIES = 4096;
    private static final long TICK_MS = 50L;

    private final JavaPlugin plugin;
    private final SchedulerBridge schedulerBridge;
    private final ParticleBridge particleBridge;
    private final MultiStrikeService multiStrikeService;
    private final PotionEffectResolver potionEffectResolver;
    private final EquipmentBridge equipmentBridge;
    private final ItemIdentityService identityService;
    private final TimeSource timeSource;
    private final int maxCooldownEntries;

    private final Map<CooldownKey, Long> cooldowns = new ConcurrentHashMap<>();
    private final Map<HitCounterKey, AtomicLong> hitCounters = new ConcurrentHashMap<>();
    private final AtomicLong evictionCounter = new AtomicLong(0);
    private static final int MAX_PASSIVE_TASKS_PER_PLAYER = 64;
    private final Map<UUID, List<TaskHandle>> passiveTasksByPlayer = new ConcurrentHashMap<>();

    public interface TimeSource {
        long monotonicMillis();
    }

    public static final class SystemTimeSource implements TimeSource {
        @Override
        public long monotonicMillis() {
            return System.currentTimeMillis();
        }
    }

    public ForgePowerService(JavaPlugin plugin, SchedulerBridge schedulerBridge,
                            PotionEffectResolver potionEffectResolver,
                            EquipmentBridge equipmentBridge,
                            ItemIdentityService identityService) {
        this(plugin, schedulerBridge, potionEffectResolver, equipmentBridge, identityService,
             ParticleBridge.getInstance(), new MultiStrikeService(schedulerBridge, ParticleBridge.getInstance()),
             new SystemTimeSource(), DEFAULT_MAX_COOLDOWN_ENTRIES);
    }

    public ForgePowerService(JavaPlugin plugin, SchedulerBridge schedulerBridge,
                             PotionEffectResolver potionEffectResolver,
                             EquipmentBridge equipmentBridge,
                             ItemIdentityService identityService,
                             ParticleBridge particleBridge, MultiStrikeService multiStrikeService) {
        this(plugin, schedulerBridge, potionEffectResolver, equipmentBridge, identityService,
             particleBridge, multiStrikeService, new SystemTimeSource(), DEFAULT_MAX_COOLDOWN_ENTRIES);
    }

    public ForgePowerService(JavaPlugin plugin, SchedulerBridge schedulerBridge,
                             ParticleBridge particleBridge,
                             PotionEffectResolver potionEffectResolver,
                             EquipmentBridge equipmentBridge,
                             ItemIdentityService identityService,
                             MultiStrikeService multiStrikeService) {
        this(plugin, schedulerBridge, potionEffectResolver, equipmentBridge, identityService,
             particleBridge, multiStrikeService, new SystemTimeSource(), DEFAULT_MAX_COOLDOWN_ENTRIES);
    }

    public ForgePowerService(JavaPlugin plugin, SchedulerBridge schedulerBridge,
                            PotionEffectResolver potionEffectResolver,
                            EquipmentBridge equipmentBridge,
                            ItemIdentityService identityService,
                            int maxCooldownEntries) {
        this(plugin, schedulerBridge, potionEffectResolver, equipmentBridge, identityService,
             ParticleBridge.getInstance(), new MultiStrikeService(schedulerBridge, ParticleBridge.getInstance()),
             new SystemTimeSource(), maxCooldownEntries > 0 ? maxCooldownEntries : DEFAULT_MAX_COOLDOWN_ENTRIES);
    }

    public ForgePowerService(JavaPlugin plugin, SchedulerBridge schedulerBridge,
                            PotionEffectResolver potionEffectResolver,
                            EquipmentBridge equipmentBridge,
                            ItemIdentityService identityService,
                             TimeSource timeSource,
                             int maxCooldownEntries) {
        this(plugin, schedulerBridge, potionEffectResolver, equipmentBridge, identityService,
             ParticleBridge.getInstance(), new MultiStrikeService(schedulerBridge, ParticleBridge.getInstance()),
             timeSource, maxCooldownEntries);
    }

    public ForgePowerService(JavaPlugin plugin, SchedulerBridge schedulerBridge,
                              PotionEffectResolver potionEffectResolver,
                              EquipmentBridge equipmentBridge,
                              ItemIdentityService identityService,
                              ParticleBridge particleBridge, MultiStrikeService multiStrikeService,
                              TimeSource timeSource, int maxCooldownEntries) {
        this.plugin = Objects.requireNonNull(plugin);
        this.schedulerBridge = Objects.requireNonNull(schedulerBridge);
        this.particleBridge = Objects.requireNonNull(particleBridge);
        this.multiStrikeService = Objects.requireNonNull(multiStrikeService);
        this.potionEffectResolver = Objects.requireNonNull(potionEffectResolver);
        this.equipmentBridge = Objects.requireNonNull(equipmentBridge);
        this.identityService = Objects.requireNonNull(identityService);
        this.timeSource = Objects.requireNonNull(timeSource);
        this.maxCooldownEntries = maxCooldownEntries > 0 ? maxCooldownEntries : DEFAULT_MAX_COOLDOWN_ENTRIES;
    }

    public boolean usePower(Player player, ForgePowerDefinition power, UUID forgeId) {
        if (!canUsePower(player, power, forgeId)) {
            return false;
        }
        setCooldown(player, power, forgeId);
        return true;
    }

    public boolean canUsePower(Player player, ForgePowerDefinition power, UUID forgeId) {
        CooldownKey key = new CooldownKey(player.getUniqueId(), forgeId, power.getId());
        Long lastUsed = cooldowns.get(key);
        if (lastUsed == null) {
            return true;
        }
        long cooldownEnd = lastUsed + (power.getCooldownTicks() * TICK_MS);
        return timeSource.monotonicMillis() >= cooldownEnd;
    }

    private void setCooldown(Player player, ForgePowerDefinition power, UUID forgeId) {
        evictIfNeeded();
        CooldownKey key = new CooldownKey(player.getUniqueId(), forgeId, power.getId());
        cooldowns.put(key, timeSource.monotonicMillis());
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
        List<TaskHandle> tasks = passiveTasksByPlayer.remove(player.getUniqueId());
        if (tasks != null) {
            for (TaskHandle task : tasks) {
                if (task != null) {
                    task.cancel();
                }
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
        clearAllPassiveTasks();
    }

    public void clearAllPassiveTasks() {
        passiveTasksByPlayer.values().forEach(list -> {
            if (list != null) {
                for (TaskHandle task : list) {
                    if (task != null) {
                        task.cancel();
                    }
                }
            }
        });
        passiveTasksByPlayer.clear();
    }

    public long getEvictionCount() {
        return evictionCounter.get();
    }

    public boolean activatePassivePower(Player player, ForgePowerDefinition power, UUID forgeId) {
        if (power.getPowerType() != ForgePowerDefinition.PowerType.PASSIVE_POTION) {
            return false;
        }
        if (!isForgeItemEquipped(player, forgeId, power)) {
            return false;
        }
        if (!usePower(player, power, forgeId)) {
            return false;
        }
        applyPassivePotionEffect(player, power, forgeId);
        return true;
    }

    private boolean isForgeItemEquipped(Player player, UUID forgeId, ForgePowerDefinition power) {
        List<ForgePowerDefinition.ActivationSlot> slots = power.getActivationSlots();
        if (slots.isEmpty()) {
            return isForgeItemInHands(player, forgeId);
        }
        for (ForgePowerDefinition.ActivationSlot slot : slots) {
            EquipmentBridge.Slot bridgeSlot = convertSlot(slot);
            if (bridgeSlot == null) {
                continue;
            }
            if (isForgeItemInSlot(player, forgeId, bridgeSlot)) {
                return true;
            }
        }
        return false;
    }

    private boolean isForgeItemInHands(Player player, UUID forgeId) {
        return isForgeItemInSlot(player, forgeId, EquipmentBridge.Slot.MAINHAND)
            || isForgeItemInSlot(player, forgeId, EquipmentBridge.Slot.OFFHAND);
    }

    private boolean isForgeItemInSlot(Player player, UUID forgeId, EquipmentBridge.Slot slot) {
        ItemStack item = equipmentBridge.getItem(player, slot);
        if (item == null || !item.hasItemMeta()) {
            return false;
        }
        ItemIdentityService.ForgeIdentityRead identityRead = identityService.readForgeIdentity(item);
        if (identityRead.getStatus() != ItemIdentityService.ForgeIdentityStatus.VALID) {
            return false;
        }
        UUID richForgeId = identityRead.getIdentity().getForgeId();
        return forgeId.equals(richForgeId);
    }

    private EquipmentBridge.Slot convertSlot(ForgePowerDefinition.ActivationSlot slot) {
        switch (slot) {
            case MAINHAND: return EquipmentBridge.Slot.MAINHAND;
            case OFFHAND: return EquipmentBridge.Slot.OFFHAND;
            case HEAD: return EquipmentBridge.Slot.HEAD;
            case CHEST: return EquipmentBridge.Slot.CHEST;
            case LEGS: return EquipmentBridge.Slot.LEGS;
            case FEET: return EquipmentBridge.Slot.FEET;
            default: return null;
        }
    }

    private void applyPassivePotionEffect(Player player, ForgePowerDefinition power, UUID forgeId) {
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
        schedulePassiveRefresh(player, power, forgeId, effectType.get(), duration, amplifier);
    }

    private void schedulePassiveRefresh(Player player, ForgePowerDefinition power, UUID forgeId,
                                        PotionEffectType effectType, int duration, int amplifier) {
        if (!player.isOnline()) {
            return;
        }
        if (!isForgeItemEquipped(player, forgeId, power)) {
            return;
        }
        LivingEntity entity = player;
        entity.addPotionEffect(new PotionEffect(effectType, duration, amplifier, false, false));
        long delayTicks = duration;
        UUID playerId = player.getUniqueId();
        TaskHandle handle = schedulerBridge.runEntityLater(entity, () -> {
            if (!player.isOnline()) {
                return;
            }
            if (!isForgeItemEquipped(player, forgeId, power)) {
                return;
            }
            passiveTasksByPlayer.computeIfAbsent(playerId, k -> new ArrayList<>()).removeIf(t -> t == null || t.isCancelled());
            applyPassivePotionEffect(player, power, forgeId);
        }, () -> {
            passiveTasksByPlayer.computeIfPresent(playerId, (k, list) -> {
                list.removeIf(t -> t == null || t.isCancelled());
                return list.isEmpty() ? null : list;
            });
        }, delayTicks);
        if (handle != null) {
            passiveTasksByPlayer.compute(playerId, (k, list) -> {
                if (list == null) {
                    list = new ArrayList<>();
                } else {
                    list.removeIf(t -> t == null || t.isCancelled());
                }
                if (list.size() < MAX_PASSIVE_TASKS_PER_PLAYER) {
                    list.add(handle);
                } else {
                    handle.cancel();
                }
                return list;
            });
        }
    }

    public boolean triggerOnHitPower(Player attacker, LivingEntity victim, ForgePowerDefinition power, UUID forgeId) {
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
        if (!rollChance(power.getChance())) {
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
                applyOnHitFire(attacker, victim, power);
                break;
            case ON_HIT_HEAL:
                applyOnHitHeal(attacker, victim, power);
                break;
            case ON_HIT_AOE_FIRE:
                applyAoeFire(attacker, victim, power);
                break;
            case ON_HIT_BLEED:
                applyBleed(victim, power);
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
                applyLightning(victim.getLocation().clone());
                break;
            case EVERY_N_HIT_KNOCKBACK:
                applyKnockback(attacker, victim, power);
                break;
            default:
                break;
        }
        return true;
    }

    private void applyLightning(Location location) {
        if (location == null || location.getWorld() == null) {
            return;
        }
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
    }

    private void applyOnHitFire(Player attacker, LivingEntity victim, ForgePowerDefinition power) {
        int fireTicks = power.getFireTicks();
        if (fireTicks <= 0) {
            return;
        }
        LivingEntity entity = victim;
        entity.setFireTicks(fireTicks);
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
    }

    private void applyAoeFire(Player attacker, LivingEntity victim, ForgePowerDefinition power) {
        final int fireTicks = power.getFireTicks();
        if (fireTicks <= 0) {
            return;
        }
        multiStrikeService.execute(attacker, victim, power, true, new MultiStrikeService.StrikeAction() {
            @Override
            public void apply(LivingEntity target) {
                target.setFireTicks(fireTicks);
            }
        });
    }

    private void applyBleed(final LivingEntity victim, final ForgePowerDefinition power) {
        scheduleBleedPulse(victim, power, power.getPulseCount());
    }

    private void scheduleBleedPulse(final LivingEntity victim, final ForgePowerDefinition power, final int remaining) {
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
                scheduleBleedPulse(victim, power, remaining - 1);
            }
        }, () -> {}, power.getPulseIntervalTicks());
    }

    private void applyExplosive(final Player attacker, final LivingEntity victim,
                                final ForgePowerDefinition power) {
        final AtomicBoolean primary = new AtomicBoolean(true);
        multiStrikeService.execute(attacker, victim, power, false, new MultiStrikeService.StrikeAction() {
            @Override
            public void apply(LivingEntity target) {
                double multiplier = primary.getAndSet(false) ? 1.0
                    : power.getSecondaryDamageMultiplier().doubleValue();
                double damage = power.getDamageAmount().doubleValue() * multiplier;
                if (damage > 0) {
                    target.damage(damage);
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
        multiStrikeService.execute(attacker, victim, power, true, new MultiStrikeService.StrikeAction() {
            @Override
            public void apply(LivingEntity target) {
                target.addPotionEffect(effect);
            }
        });
    }

    private void applyChainDamage(Player attacker, LivingEntity victim, final ForgePowerDefinition power) {
        multiStrikeService.execute(attacker, victim, power, false, new MultiStrikeService.StrikeAction() {
            @Override
            public void apply(LivingEntity target) {
                double damage = power.getDamageAmount().doubleValue();
                if (damage > 0) {
                    target.damage(damage);
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
        applyVelocity(player, horiz, vert);
    }

    private void applyVelocity(Player player, double horizontalStrength, double verticalStrength) {
        org.bukkit.util.Vector direction = player.getLocation().getDirection();
        double horizFactor = horizontalStrength * 0.5;
        double vertFactor = verticalStrength * 0.5;
        org.bukkit.util.Vector velocity = new org.bukkit.util.Vector(
            direction.getX() * horizFactor,
            verticalStrength > 0 ? Math.max(verticalStrength * 0.5, 0.3) : direction.getY() * vertFactor,
            direction.getZ() * horizFactor
        );
        player.setVelocity(velocity);
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
    }

    public boolean isCooldownExpired(Player player, ForgePowerDefinition power, UUID forgeId) {
        return canUsePower(player, power, forgeId);
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
