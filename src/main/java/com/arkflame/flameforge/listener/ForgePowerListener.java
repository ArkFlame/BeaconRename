package com.arkflame.flameforge.listener;

import com.arkflame.flameforge.compat.equipment.EquipmentBridge;
import com.arkflame.flameforge.config.TierRepository;
import com.arkflame.flameforge.forge.ForgePowerService;
import com.arkflame.flameforge.item.ItemIdentityService;
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
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

public final class ForgePowerListener implements Listener {

    private final JavaPlugin plugin;
    private final ForgePowerService powerService;
    private final EquipmentBridge equipmentBridge;
    private final ItemIdentityService identityService;
    private final TierRepository tierRepository;

    public ForgePowerListener(JavaPlugin plugin, ForgePowerService powerService,
                              EquipmentBridge equipmentBridge, ItemIdentityService identityService,
                              TierRepository tierRepository) {
        this.plugin = plugin;
        this.powerService = powerService;
        this.equipmentBridge = equipmentBridge;
        this.identityService = identityService;
        this.tierRepository = tierRepository;
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
        Optional<ItemIdentityService.IdentityData> identityOpt = identityService.readIdentity(item);
        if (!identityOpt.isPresent()) {
            return;
        }
        UUID forgeId = identityOpt.get().getForgeId();
        int highestTier = identityOpt.get().getHighestTier();
        List<ForgePowerDefinition> powers = getPowersForForge(highestTier);
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
        if (!(damager instanceof Player)) {
            return;
        }
        Player attacker = (Player) damager;
        Entity victim = event.getEntity();
        if (!(victim instanceof LivingEntity)) {
            return;
        }
        LivingEntity victimEntity = (LivingEntity) victim;
        handleOnHit(attacker, victimEntity, event);
    }

    private void handleOnHit(Player attacker, LivingEntity victim, EntityDamageByEntityEvent event) {
        if (attacker == null || !attacker.isOnline()) {
            return;
        }
        ItemStack weapon = attacker.getItemInHand();
        if (weapon == null || !weapon.hasItemMeta()) {
            return;
        }
        Optional<ItemIdentityService.IdentityData> identityOpt = identityService.readIdentity(weapon);
        if (!identityOpt.isPresent()) {
            return;
        }
        UUID forgeId = identityOpt.get().getForgeId();
        int highestTier = identityOpt.get().getHighestTier();
        List<ForgePowerDefinition> powers = getPowersForForge(highestTier);
        for (ForgePowerDefinition power : powers) {
            ForgePowerDefinition.PowerType type = power.getPowerType();
            if (type == ForgePowerDefinition.PowerType.ON_HIT_POTION
                || type == ForgePowerDefinition.PowerType.ON_HIT_FIRE
                || type == ForgePowerDefinition.PowerType.ON_HIT_HEAL) {
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
    }

    private List<ForgePowerDefinition> getPowersForForge(int highestTier) {
        return tierRepository.findByLevel(highestTier)
                .map(tier -> tier.getVariants().stream()
                        .map(ForgeVariant::getPowers)
                        .flatMap(List::stream)
                        .collect(Collectors.toList()))
                .orElse(new ArrayList<>());
    }
}
