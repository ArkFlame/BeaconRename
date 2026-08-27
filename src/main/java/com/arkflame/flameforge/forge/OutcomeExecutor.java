package com.arkflame.flameforge.forge;

import com.arkflame.flameforge.item.ItemIdentityCodec;
import com.arkflame.flameforge.item.ItemIdentityService;
import com.arkflame.flameforge.item.ItemMutationService;
import com.arkflame.flameforge.model.BreakPolicy;
import com.arkflame.flameforge.model.CurseDefinition;
import com.arkflame.flameforge.model.ForgeOutcomeCategory;
import com.arkflame.flameforge.model.ForgeVariant;
import com.arkflame.flameforge.persistence.AuditLogService;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public final class OutcomeExecutor {
    private final ItemMutationService mutationService;
    private final ItemIdentityService identityService;
    private final AuditLogService auditLog;
    private final Map<String, Object> wardConfig;

    public OutcomeExecutor(ItemMutationService mutationService, ItemIdentityService identityService,
                          AuditLogService auditLog, Map<String, Object> wardConfig) {
        this.mutationService = Objects.requireNonNull(mutationService);
        this.identityService = Objects.requireNonNull(identityService);
        this.auditLog = Objects.requireNonNull(auditLog);
        this.wardConfig = wardConfig;
    }

    public OutcomeExecutionResult execute(ForgePlan plan, ItemStack inputItem,
                                        Player player, UUID forgeId,
                                        ForgeOutcomeCategory category,
                                        ForgeVariant selectedVariant) {
        Objects.requireNonNull(plan);
        Set<String> executedIds = new HashSet<>();
        executedIds.add("forge_execution");

        boolean wardProtected = isWardProtected(plan);
        if (wardProtected && shouldConvertToUnchanged(plan)) {
            auditLog.logAsync("WARD_CONVERT", player != null ? player.getName() : "console",
                "forge_execution", "Protected outcome converted to RETURN_UNCHANGED");
            return OutcomeExecutionResult.wardConverted("forge_execution", executedIds);
        }

        switch (category) {
            case SUCCESS:
                return executeSuccess(plan, inputItem, executedIds, player, forgeId, selectedVariant);
            case BREAK:
                return executeBreak(plan, inputItem, executedIds, player, forgeId);
            case CURSE:
                return executeCurse(plan, inputItem, executedIds, player, forgeId);
            default:
                return OutcomeExecutionResult.error("forge_execution", "Unknown outcome category: " + category);
        }
    }

    private boolean isWardProtected(ForgePlan plan) {
        if (wardConfig == null) {
            return false;
        }
        return Boolean.TRUE.equals(wardConfig.get("protect_all"));
    }

    private boolean shouldConvertToUnchanged(ForgePlan plan) {
        if (wardConfig == null) {
            return false;
        }
        Object convertTypes = wardConfig.get("convert_to_unchanged");
        if (convertTypes instanceof List) {
            @SuppressWarnings("unchecked")
            List<String> typeList = (List<String>) convertTypes;
            return typeList.contains("SUCCESS") || typeList.contains("BREAK") ||
                   typeList.contains("CURSE");
        }
        return false;
    }

    private OutcomeExecutionResult executeSuccess(ForgePlan plan, ItemStack inputItem,
                                                  Set<String> executedIds, Player player,
                                                  UUID forgeId, ForgeVariant selectedVariant) {
        if (inputItem == null) {
            return OutcomeExecutionResult.error("forge_execution", "Cannot success mutate: null input");
        }
        if (selectedVariant == null) {
            return OutcomeExecutionResult.error("forge_execution", "Cannot success mutate: null variant");
        }

        ItemIdentityCodec.Identity identity = readRichIdentity(inputItem, forgeId);

        ItemMutationService.MutationResult result = mutationService.mutateSuccess(
            inputItem, plan.getTargetTier(), selectedVariant, identity, forgeId);

        if (!result.isSuccess()) {
            StringBuilder sb = new StringBuilder();
            java.util.List<String> warnings = result.getWarnings();
            for (int i = 0; i < warnings.size(); i++) {
                if (i > 0) sb.append(", ");
                sb.append(warnings.get(i));
            }
            return OutcomeExecutionResult.error("forge_execution",
                "Success mutation failed: " + sb.toString());
        }

        return OutcomeExecutionResult.successWithItem("forge_execution", executedIds, result.getResult());
    }

    private OutcomeExecutionResult executeBreak(ForgePlan plan, ItemStack inputItem,
                                                 Set<String> executedIds, Player player,
                                                 UUID forgeId) {
        if (inputItem == null) {
            return OutcomeExecutionResult.error("forge_execution", "Cannot break mutate: null input");
        }

        ItemIdentityCodec.Identity identity = readRichIdentity(inputItem, forgeId);
        BreakPolicy policy = getBreakPolicy(plan);

        ItemMutationService.MutationResult result = mutationService.mutateBreak(
            inputItem, policy, identity, forgeId);

        if (!result.isSuccess()) {
            StringBuilder sb = new StringBuilder();
            java.util.List<String> warnings = result.getWarnings();
            for (int i = 0; i < warnings.size(); i++) {
                if (i > 0) sb.append(", ");
                sb.append(warnings.get(i));
            }
            return OutcomeExecutionResult.error("forge_execution",
                "Break mutation failed: " + sb.toString());
        }

        if (result.isDestroyed()) {
            auditLog.logAsync("ITEM_DESTROYED", player != null ? player.getName() : "console",
                "forge_execution", "Item destroyed on break");
            return OutcomeExecutionResult.breakResult("forge_execution", executedIds, null);
        }

        return OutcomeExecutionResult.breakResult("forge_execution", executedIds, result.getResult());
    }

    private OutcomeExecutionResult executeCurse(ForgePlan plan, ItemStack inputItem,
                                                 Set<String> executedIds, Player player,
                                                 UUID forgeId) {
        if (inputItem == null) {
            return OutcomeExecutionResult.error("forge_execution", "Cannot curse mutate: null input");
        }

        ItemIdentityCodec.Identity identity = readRichIdentity(inputItem, forgeId);
        CurseDefinition curse = getCurseDefinition(plan);
        boolean currentlyCursed = identity.isCursed();

        ItemMutationService.MutationResult result = mutationService.mutateCurse(
            inputItem, curse, currentlyCursed, identity, forgeId);

        if (!result.isSuccess()) {
            StringBuilder sb = new StringBuilder();
            java.util.List<String> warnings = result.getWarnings();
            for (int i = 0; i < warnings.size(); i++) {
                if (i > 0) sb.append(", ");
                sb.append(warnings.get(i));
            }
            return OutcomeExecutionResult.error("forge_execution",
                "Curse mutation failed: " + sb.toString());
        }

        return OutcomeExecutionResult.curseResult("forge_execution", executedIds, result.getResult());
    }

    private ItemIdentityCodec.Identity readRichIdentity(ItemStack item, UUID forgeId) {
        if (item == null) {
            return ItemIdentityCodec.Identity.empty().withForgeId(forgeId != null ? forgeId : UUID.randomUUID());
        }

        ItemIdentityService.ForgeIdentityRead read = identityService.readForgeIdentity(item);
        ItemIdentityService.ForgeIdentityStatus status = read.getStatus();

        switch (status) {
            case VALID: {
                ItemIdentityCodec.Identity identity = read.getIdentity();
                if (forgeId != null && identity.getForgeId() == null) {
                    identity = identity.withForgeId(forgeId);
                }
                return identity;
            }
            case INVALID:
                return ItemIdentityCodec.Identity.empty().withForgeId(forgeId != null ? forgeId : UUID.randomUUID());
            case NONE:
            default: {
                UUID actualForgeId = forgeId != null ? forgeId : UUID.randomUUID();
                String baseMaterial = item.getType().name();
                String baseDisplayName = identityService.defaultBaseDisplayName(item.getType());
                return ItemIdentityCodec.Identity.empty()
                        .withForgeId(actualForgeId)
                        .withBaseMaterial(baseMaterial)
                        .withBaseDisplayName(baseDisplayName);
            }
        }
    }

    private BreakPolicy getBreakPolicy(ForgePlan plan) {
        if (plan == null || plan.getTargetTier() == null) {
            return BreakPolicy.none();
        }
        return plan.getTargetTier().getBreakPolicy();
    }

    private CurseDefinition getCurseDefinition(ForgePlan plan) {
        if (plan == null || plan.getTargetTier() == null) {
            return null;
        }
        return plan.getTargetTier().getCurseDefinition();
    }

}
