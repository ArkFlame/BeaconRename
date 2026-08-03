package com.arkflame.flameforge.forge;

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

import java.math.BigDecimal;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public final class ForgePowerService {

    private static final int MAX_COOLDOWN_ENTRIES = 4096;
    private static final long TICK_MS = 50L;

    private final JavaPlugin plugin;
    private final SchedulerBridge schedulerBridge;
    private final PotionEffectResolver potionEffectResolver;
    private final EquipmentBridge equipmentBridge;
    private final ItemIdentityService identityService;
    private final TimeSource timeSource;

    private final Map<CooldownKey, Long> cooldowns = new ConcurrentHashMap<>();
    private final AtomicLong evictionCounter = new AtomicLong(0);

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
        this(plugin, schedulerBridge, potionEffectResolver, equipmentBridge, identityService, new SystemTimeSource());
    }

    public ForgePowerService(JavaPlugin plugin, SchedulerBridge schedulerBridge,
                            PotionEffectResolver potionEffectResolver,
                            EquipmentBridge equipmentBridge,
                            ItemIdentityService identityService,
                            TimeSource timeSource) {
        this.plugin = Objects.requireNonNull(plugin);
        this.schedulerBridge = Objects.requireNonNull(schedulerBridge);
        this.potionEffectResolver = Objects.requireNonNull(potionEffectResolver);
        this.equipmentBridge = Objects.requireNonNull(equipmentBridge);
        this.identityService = Objects.requireNonNull(identityService);
        this.timeSource = Objects.requireNonNull(timeSource);
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
        if (cooldowns.size() < MAX_COOLDOWN_ENTRIES) {
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

    public void clearCooldowns(UUID playerId) {
        cooldowns.keySet().removeIf(key -> key.playerUuid.equals(playerId));
    }

    public void clearCooldownsForPlayerAndForge(Player player, UUID forgeId) {
        cooldowns.keySet().removeIf(key -> key.playerUuid.equals(player.getUniqueId()) && key.forgeUuid.equals(forgeId));
    }

    public void clearAll() {
        cooldowns.clear();
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
        Optional<ItemIdentityService.IdentityData> identity = identityService.readIdentity(item);
        return identity.map(data -> forgeId.equals(data.getForgeId())).orElse(false);
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
        schedulerBridge.runEntityLater(entity, () -> {
            if (!player.isOnline()) {
                return;
            }
            if (!isForgeItemEquipped(player, forgeId, power)) {
                return;
            }
            applyPassivePotionEffect(player, power, forgeId);
        }, () -> {}, delayTicks);
    }

    public boolean triggerOnHitPower(Player attacker, LivingEntity victim, ForgePowerDefinition power, UUID forgeId) {
        ForgePowerDefinition.PowerType type = power.getPowerType();
        if (type != ForgePowerDefinition.PowerType.ON_HIT_POTION
            && type != ForgePowerDefinition.PowerType.ON_HIT_FIRE
            && type != ForgePowerDefinition.PowerType.ON_HIT_HEAL) {
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
            default:
                break;
        }
        return true;
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
        double maxHealth = victim.getMaxHealth();
        double newHealth = Math.min(victim.getHealth() + heal, maxHealth);
        victim.setHealth(newHealth);
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
}
