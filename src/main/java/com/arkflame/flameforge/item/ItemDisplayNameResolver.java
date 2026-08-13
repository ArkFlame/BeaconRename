package com.arkflame.flameforge.item;

import com.arkflame.flameforge.config.ConfigService;
import com.arkflame.flameforge.config.ConfigSnapshot;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

public final class ItemDisplayNameResolver {
    private final ItemIdentityService identityService;
    private final ConfigService configService;

    public ItemDisplayNameResolver(final ItemIdentityService identityService,
                                   final ConfigService configService) {
        if (identityService == null) {
            throw new IllegalArgumentException("identityService");
        }
        this.identityService = identityService;
        this.configService = configService;
    }

    public String resolve(final ItemStack item, final ItemIdentityCodec.Identity identity) {
        if (identity != null) {
            String inherited = identity.getBaseDisplayName();
            if (inherited != null && !inherited.trim().isEmpty()) {
                return inherited;
            }
        }

        Material material = item != null ? item.getType() : null;
        if (configService != null && material != null) {
            ConfigSnapshot snapshot = configService.getCurrentSnapshot();
            if (snapshot != null) {
                String configured = snapshot.getRootString(
                        "item-display-names." + material.name(), null);
                if (configured != null && !configured.trim().isEmpty()) {
                    return configured.trim();
                }
            }
        }
        return identityService.defaultBaseDisplayName(material);
    }
}
