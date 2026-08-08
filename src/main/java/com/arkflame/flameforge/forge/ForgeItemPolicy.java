package com.arkflame.flameforge.forge;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public final class ForgeItemPolicy {

    public static final class PolicyResult {
        private final boolean allowed;
        private final String messageKey;

        private PolicyResult(boolean allowed, String messageKey) {
            this.allowed = allowed;
            this.messageKey = messageKey;
        }

        public static PolicyResult allow() {
            return new PolicyResult(true, null);
        }

        public static PolicyResult deny(String messageKey) {
            return new PolicyResult(false, messageKey);
        }

        public boolean isAllowed() {
            return allowed;
        }

        public String getMessageKey() {
            return messageKey;
        }
    }

    private final ForgeItemInspection inspection;

    public ForgeItemPolicy(ForgeItemInspection inspection) {
        this.inspection = inspection;
    }

    public PolicyResult checkItem(Player player, com.arkflame.flameforge.model.PlayerForgeState session, ItemStack item) {
        ForgeItemInspection.InspectionResult result = inspection.inspect(player, session, item);
        return toPolicyResult(result);
    }

    public boolean isReady(Player player, com.arkflame.flameforge.model.PlayerForgeState session, ItemStack item) {
        return checkItem(player, session, item).isAllowed();
    }

    private PolicyResult toPolicyResult(ForgeItemInspection.InspectionResult result) {
        if (result.isReady()) {
            return PolicyResult.allow();
        }
        ForgeItemInspection.Status status = result.getStatus();
        String messageKey = mapStatusToMessageKey(status);
        return PolicyResult.deny(messageKey);
    }

    private String mapStatusToMessageKey(ForgeItemInspection.Status status) {
        switch (status) {
            case MAX_TIER:
            case NEXT_TIER_MISSING:
            case NEXT_TIER_DISABLED:
            case NO_ELIGIBLE_VARIANTS:
                return "menu.item-denied.no-tier";
            case INVALID_IDENTITY:
                return "menu.item-denied.invalid-identity";
            case DENIED_MATERIAL:
            case DENIED_GROUP:
                return "menu.item-denied.unsupported";
            case CUSTOM_NAME:
            case CUSTOM_LORE:
            case CUSTOM_MODEL_DATA:
            case FOREIGN_PERSISTENT_DATA:
                return "menu.item-denied.customized";
            case CURSED:
                return "menu.item-denied.cursed";
            case STATION_TIER_BLOCKED:
                return "menu.item-denied.station";
            case TIER_PERMISSION_REQUIRED:
                return "menu.item-denied.permission";
            case EMPTY:
            case AIR:
                return "menu.item-denied.empty";
            default:
                return "menu.item-denied.unsupported";
        }
    }
}
