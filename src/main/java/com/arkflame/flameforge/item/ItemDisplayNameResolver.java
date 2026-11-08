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
        final Material material = item != null ? item.getType() : null;
        final boolean preferConfigured = configService != null && material != null
                && preferConfiguredDisplayName();
        if (preferConfigured) {
            String configured = configuredDisplayName(material);
            if (configured != null) {
                return configured;
            }
        }

        if (identity != null) {
            String inherited = identity.getBaseDisplayName();
            if (inherited != null && !inherited.trim().isEmpty()) {
                return inherited;
            }
        }

        if (!preferConfigured) {
            String configured = configuredDisplayName(material);
            if (configured != null) {
                return configured;
            }
        }
        return identityService.defaultBaseDisplayName(material);
    }

    private boolean preferConfiguredDisplayName() {
        final ConfigSnapshot snapshot = configService.getCurrentSnapshot();
        return snapshot == null || snapshot.getRootBoolean("forge.prefer-configured-display-name", true);
    }

    private String configuredDisplayName(final Material material) {
        if (configService == null || material == null) {
            return null;
        }
        final ConfigSnapshot snapshot = configService.getCurrentSnapshot();
        if (snapshot == null) {
            return null;
        }
        String configured = snapshot.getRootString("item-display-names." + material.name(), null);
        return configured != null && !configured.trim().isEmpty() ? configured.trim() : null;
    }
}
