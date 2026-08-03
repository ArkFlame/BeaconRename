package com.arkflame.flameforge.forge;

import com.arkflame.flameforge.config.TierRepository;
import com.arkflame.flameforge.item.AttributeBridge;
import com.arkflame.flameforge.item.ItemIdentityCodec;
import com.arkflame.flameforge.item.ItemIdentityService;
import com.arkflame.flameforge.model.PlayerForgeState;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public final class ForgeItemPolicy {

    public static final class PolicyResult {
        private final boolean allowed;
        private final String reason;

        private PolicyResult(boolean allowed, String reason) {
            this.allowed = allowed;
            this.reason = reason;
        }

        public static PolicyResult allow() {
            return new PolicyResult(true, null);
        }

        public static PolicyResult deny(String reason) {
            return new PolicyResult(false, reason);
        }

        public boolean isAllowed() {
            return allowed;
        }

        public String getReason() {
            return reason;
        }
    }

    private final ItemIdentityService identityService;
    private final AttributeBridge attributeBridge;
    private final TierRepository tierRepository;
    private final ForgeItemInspection inspection;

    public ForgeItemPolicy(ItemIdentityService identityService, AttributeBridge attributeBridge,
                          TierRepository tierRepository) {
        this.identityService = identityService;
        this.attributeBridge = attributeBridge;
        this.tierRepository = tierRepository;
        ItemIdentityCodec codec = new ItemIdentityCodec();
        this.inspection = new ForgeItemInspection(codec, identityService, attributeBridge, tierRepository);
    }

    public PolicyResult checkItem(Player player, PlayerForgeState session, ItemStack item) {
        ForgeItemInspection.InspectionResult result = inspection.inspect(player, session, item);
        return toPolicyResult(result);
    }

    public boolean isReady(Player player, PlayerForgeState session, ItemStack item) {
        return checkItem(player, session, item).isAllowed();
    }

    private PolicyResult toPolicyResult(ForgeItemInspection.InspectionResult result) {
        if (result.isReady()) {
            return PolicyResult.allow();
        }
        ForgeItemInspection.Status status = result.getStatus();
        String reason = status.name();
        return PolicyResult.deny(reason);
    }
}
