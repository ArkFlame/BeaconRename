package com.arkflame.flameforge.listener;

import com.arkflame.flameforge.item.ItemIdentityCodec;
import com.arkflame.flameforge.item.ItemIdentityService;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

/** Shared check for items that a failed forge marked as unusable. */
public final class FailedItemGuard {
    private FailedItemGuard() {
    }

    public static boolean isFailed(ItemIdentityService identityService, ItemStack item) {
        if (item == null || item.getType() == org.bukkit.Material.AIR) {
            return false;
        }
        if (identityService != null) {
            ItemIdentityService.ForgeIdentityRead read = identityService.readForgeIdentity(item);
            if (read != null && read.getStatus() == ItemIdentityService.ForgeIdentityStatus.VALID) {
                ItemIdentityCodec.Identity identity = read.getIdentity();
                if (identity != null && (identity.isCursed() || identity.getFailureVariantId() != null)) {
                    return true;
                }
            }
        }
        // Fallback for legacy cursed items whose identity can no longer be decoded.
        if (item.hasItemMeta()) {
            ItemMeta meta = item.getItemMeta();
            List<String> lore = meta != null ? meta.getLore() : null;
            if (lore != null) {
                for (String line : lore) {
                    if (line != null && (line.contains("FLAMEFORGE_CURSED")
                            || line.contains("FLAMEFORGE_FAILED"))) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    public static boolean anyFailed(ItemIdentityService identityService, ItemStack[] items) {
        if (items == null) {
            return false;
        }
        for (ItemStack item : items) {
            if (isFailed(identityService, item)) {
                return true;
            }
        }
        return false;
    }
}
