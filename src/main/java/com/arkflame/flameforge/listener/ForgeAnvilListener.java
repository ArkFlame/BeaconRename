package com.arkflame.flameforge.listener;

import com.arkflame.flameforge.item.ItemIdentityService;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.inventory.AnvilInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.Repairable;

/**
 * A pure anvil rename (nothing in the second slot) of a forged item must only change the
 * display name. The result is rebuilt from the original stack so identity data, enchantments
 * (including unsafe ones the vanilla anvil may clamp), attributes and lore always survive.
 */
public final class ForgeAnvilListener implements Listener {
    private final ItemIdentityService identityService;

    public ForgeAnvilListener(final ItemIdentityService identityService) {
        this.identityService = identityService;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPrepareAnvil(final PrepareAnvilEvent event) {
        final AnvilInventory inv = event.getInventory();
        final ItemStack left = inv.getItem(0);
        final ItemStack right = inv.getItem(1);
        final ItemStack vanilla = event.getResult();
        if (left == null || vanilla == null || (right != null && right.getType() != org.bukkit.Material.AIR)) return;
        if (!left.hasItemMeta() || !identityService.hasForgeIdentityMarker(left)) return;

        final ItemStack result = left.clone();
        final ItemMeta meta = result.getItemMeta();
        final ItemMeta vanillaMeta = vanilla.getItemMeta();
        if (meta == null || vanillaMeta == null) return;
        meta.setDisplayName(vanillaMeta.hasDisplayName() ? vanillaMeta.getDisplayName() : null);
        if (meta instanceof Repairable && vanillaMeta instanceof Repairable) {
            ((Repairable) meta).setRepairCost(((Repairable) vanillaMeta).getRepairCost());
        }
        result.setItemMeta(meta);
        event.setResult(result);
    }
}
