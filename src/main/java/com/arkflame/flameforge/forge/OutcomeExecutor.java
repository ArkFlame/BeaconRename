package com.arkflame.flameforge.forge;

import com.arkflame.flameforge.item.ItemIdentityService;
import com.arkflame.flameforge.item.ItemMutationService;
import com.arkflame.flameforge.model.AttributeSpec;
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
    private final AuditLogService auditLog;
    private final Map<String, Object> wardConfig;

    public OutcomeExecutor(ItemMutationService mutationService, AuditLogService auditLog,
                          Map<String, Object> wardConfig) {
        this.mutationService = Objects.requireNonNull(mutationService);
        this.auditLog = Objects.requireNonNull(auditLog);
        this.wardConfig = wardConfig;
    }

    public OutcomeExecutionResult execute(ForgePlan plan, ItemStack inputItem,
                                        Player player, UUID forgeId) {
        Objects.requireNonNull(plan);
        Set<String> executedIds = new HashSet<>();
        executedIds.add("forge_execution");

        boolean wardProtected = isWardProtected(plan);
        if (wardProtected && shouldConvertToUnchanged(plan)) {
            auditLog.logAsync("WARD_CONVERT", player != null ? player.getName() : "console",
                "forge_execution", "Protected outcome converted to RETURN_UNCHANGED");
            return OutcomeExecutionResult.wardConverted("forge_execution", executedIds);
        }

        ForgeOutcomeCategory category = plan.getSelectedVariant() != null ?
            ForgeOutcomeCategory.SUCCESS : rollCategory(plan);

        switch (category) {
            case SUCCESS:
                return executeSuccess(plan, inputItem, executedIds, player, forgeId);
            case BREAK:
                return executeBreak(plan, inputItem, executedIds, player, forgeId);
            case CURSE:
                return executeCurse(plan, inputItem, executedIds, player, forgeId);
            default:
                return OutcomeExecutionResult.error("forge_execution", "Unknown outcome category: " + category);
        }
    }

    private ForgeOutcomeCategory rollCategory(ForgePlan plan) {
        if (plan.getChances() == null) {
            return ForgeOutcomeCategory.BREAK;
        }
        double total = plan.getChances().getSuccessPercent() +
                       plan.getChances().getBreakPercent() +
                       plan.getChances().getCursePercent();
        if (total <= 0) {
            return ForgeOutcomeCategory.BREAK;
        }
        double roll = Math.random() * total;
        double successThreshold = plan.getChances().getSuccessPercent();
        double breakThreshold = successThreshold + plan.getChances().getBreakPercent();
        if (roll < successThreshold) {
            return ForgeOutcomeCategory.SUCCESS;
        } else if (roll < breakThreshold) {
            return ForgeOutcomeCategory.BREAK;
        } else {
            return ForgeOutcomeCategory.CURSE;
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
                                                  UUID forgeId) {
        if (inputItem == null) {
            return OutcomeExecutionResult.error("forge_execution", "Cannot success mutate: null input");
        }

        ItemIdentityService.IdentityData identity = readOrFreshIdentity(inputItem, forgeId);

        Map<org.bukkit.enchantments.Enchantment, Integer> baselineEnchants =
            readBaselineEnchants(inputItem);
        List<AttributeSpec> baselineAttributes = readBaselineAttributes(inputItem);
        List<String> baselinePowers = readBaselinePowers(inputItem);

        ForgeVariant variant = plan.getSelectedVariant();
        int targetTier = plan.getTargetTierLevel();

        ItemMutationService.MutationResult result = mutationService.mutateSuccess(
            inputItem, variant, baselineEnchants, baselineAttributes, baselinePowers,
            targetTier, identity);

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

        ItemIdentityService.IdentityData identity = readOrFreshIdentity(inputItem, forgeId);
        BreakPolicy policy = getBreakPolicy(plan);

        ItemMutationService.MutationResult result = mutationService.mutateBreak(
            inputItem, policy, identity);

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

        ItemIdentityService.IdentityData identity = readOrFreshIdentity(inputItem, forgeId);
        CurseDefinition curse = getCurseDefinition(plan);
        boolean currentlyCursed = isCurrentlyCursed(inputItem);

        ItemMutationService.MutationResult result = mutationService.mutateCurse(
            inputItem, curse, currentlyCursed, identity);

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

    private ItemIdentityService.IdentityData readOrFreshIdentity(ItemStack item, UUID forgeId) {
        if (item == null) {
            return ItemIdentityService.IdentityData.fresh();
        }
        return ItemIdentityService.getInstance().readIdentity(item)
            .map(id -> {
                if (forgeId != null && id.getForgeId() == null) {
                    return new ItemIdentityService.IdentityData(
                        id.getReforgeCount(), id.getHighestTier(),
                        id.getLastTier(), id.getLastOutcome(), forgeId);
                }
                return id;
            })
            .orElse(ItemIdentityService.IdentityData.fresh());
    }

    private Map<org.bukkit.enchantments.Enchantment, Integer> readBaselineEnchants(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return new java.util.HashMap<>();
        }
        try {
            return new java.util.HashMap<>(item.getItemMeta().getEnchants());
        } catch (Exception e) {
            return new java.util.HashMap<>();
        }
    }

    private List<AttributeSpec> readBaselineAttributes(ItemStack item) {
        return new java.util.ArrayList<>();
    }

    private List<String> readBaselinePowers(ItemStack item) {
        return new java.util.ArrayList<>();
    }

    private BreakPolicy getBreakPolicy(ForgePlan plan) {
        return BreakPolicy.none();
    }

    private CurseDefinition getCurseDefinition(ForgePlan plan) {
        return new CurseDefinition("", java.util.Collections.emptyList(),
            java.util.Collections.singletonList("VANISHING_CURSE"));
    }

    private boolean isCurrentlyCursed(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return false;
        }
        try {
            for (org.bukkit.enchantments.Enchantment enchant : item.getItemMeta().getEnchants().keySet()) {
                if (isEnchantmentCursed(enchant)) {
                    return true;
                }
            }
        } catch (Exception e) {
        }
        return false;
    }

    private boolean isEnchantmentCursed(org.bukkit.enchantments.Enchantment enchant) {
        if (enchant == null) {
            return false;
        }
        String name = enchant.getName();
        return "VANISHING_CURSE".equals(name) || "BINDING_CURSE".equals(name);
    }
}
