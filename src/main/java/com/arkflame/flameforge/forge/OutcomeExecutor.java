package com.arkflame.flameforge.forge;

import com.arkflame.flameforge.config.BreakSystemConfig;
import com.arkflame.flameforge.config.ConfigService;
import com.arkflame.flameforge.item.ItemIdentityCodec;
import com.arkflame.flameforge.item.ItemIdentityService;
import com.arkflame.flameforge.item.ItemMutationService;
import com.arkflame.flameforge.model.BreakPolicy;
import com.arkflame.flameforge.model.CurseDefinition;
import com.arkflame.flameforge.model.FailureVariant;
import com.arkflame.flameforge.model.ForgeOutcomeCategory;
import com.arkflame.flameforge.model.ForgeVariant;
import com.arkflame.flameforge.persistence.AuditLogService;
import com.arkflame.flameforge.text.MessageArguments;
import com.arkflame.flameforge.text.MessageService;
import com.arkflame.flameforge.text.TextRenderer;
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
    private final ConfigService configService;
    private final MessageService messageService;
    private final TextRenderer textRenderer;

    public OutcomeExecutor(ItemMutationService mutationService, ItemIdentityService identityService,
                          AuditLogService auditLog, Map<String, Object> wardConfig) {
        this.mutationService = Objects.requireNonNull(mutationService);
        this.identityService = Objects.requireNonNull(identityService);
        this.auditLog = Objects.requireNonNull(auditLog);
        this.wardConfig = wardConfig;
        this.configService = null;
        this.messageService = null;
        this.textRenderer = null;
    }

    public OutcomeExecutor(ItemMutationService mutationService, ItemIdentityService identityService,
                          AuditLogService auditLog, Map<String, Object> wardConfig,
                          ConfigService configService, MessageService messageService,
                          TextRenderer textRenderer) {
        this.mutationService = Objects.requireNonNull(mutationService);
        this.identityService = Objects.requireNonNull(identityService);
        this.auditLog = Objects.requireNonNull(auditLog);
        this.wardConfig = wardConfig;
        this.configService = configService;
        this.messageService = messageService;
        this.textRenderer = textRenderer;
    }

    public OutcomeExecutionResult execute(ForgePlan plan, ItemStack inputItem,
                                        Player player, UUID forgeId,
                                        ForgeOutcomeCategory category,
                                        ForgeVariant selectedVariant) {
        return execute(plan, inputItem, player, forgeId, category, selectedVariant, null);
    }

    public OutcomeExecutionResult execute(ForgePlan plan, ItemStack inputItem,
                                        Player player, UUID forgeId,
                                        ForgeOutcomeCategory category,
                                        ForgeVariant selectedVariant,
                                        String failureVariantId) {
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
            case CURSE: {
                BreakSystemConfig breakSystem = breakSystem();
                String variantId = failureVariantId != null ? failureVariantId : category.name().toLowerCase();
                if (breakSystem.isEnabled()) {
                    FailureVariant variant = breakSystem.resolve(variantId, plan.getTargetTier());
                    if (variant != null && variant.isEnabled()) {
                        return executeFailure(plan, inputItem, executedIds, player, forgeId,
                            category, variant);
                    }
                }
                if (category == ForgeOutcomeCategory.BREAK) {
                    return executeBreak(plan, inputItem, executedIds, player, forgeId);
                }
                return executeCurse(plan, inputItem, executedIds, player, forgeId);
            }
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

    private BreakSystemConfig breakSystem() {
        if (configService == null) {
            return BreakSystemConfig.from(null);
        }
        return BreakSystemConfig.from(configService.getCurrentSnapshot());
    }

    private OutcomeExecutionResult executeFailure(ForgePlan plan, ItemStack inputItem,
                                                  Set<String> executedIds, Player player,
                                                  UUID forgeId, ForgeOutcomeCategory category,
                                                  FailureVariant variant) {
        if (inputItem == null) {
            return OutcomeExecutionResult.error("forge_execution", "Cannot apply failure: null input");
        }
        ItemIdentityCodec.Identity identity = readRichIdentity(inputItem, forgeId);
        ItemMutationService.MutationResult result = mutationService.mutateFailure(
            inputItem, variant, identity, forgeId);
        if (!result.isSuccess()) {
            StringBuilder sb = new StringBuilder();
            List<String> warnings = result.getWarnings();
            for (int i = 0; i < warnings.size(); i++) {
                if (i > 0) sb.append(", ");
                sb.append(warnings.get(i));
            }
            return OutcomeExecutionResult.error("forge_execution",
                "Failure mutation failed: " + sb.toString());
        }
        if (result.isDestroyed()) {
            auditLog.logAsync("ITEM_DESTROYED", player != null ? player.getName() : "console",
                "forge_execution", "Item destroyed by failure variant " + variant.getId());
            return OutcomeExecutionResult.failure("forge_execution", executedIds, null,
                category, variant.getId(), variant.getMessage());
        }
        sendFailureMessage(player, inputItem, variant);
        return OutcomeExecutionResult.failure("forge_execution", executedIds, result.getResult(),
            category, variant.getId(), variant.getMessage());
    }

    private void sendFailureMessage(Player player, ItemStack input, FailureVariant variant) {
        if (player == null || messageService == null || textRenderer == null
                || variant.getMessage().isEmpty()) {
            return;
        }
        MessageArguments arguments = MessageArguments.create()
            .string("base_name", identityService.defaultBaseDisplayName(input.getType()))
            .string("player_name", player.getName());
        messageService.sendComponent(player, textRenderer.renderComponent(variant.getMessage(),
            arguments, "break-system.failure-variants." + variant.getId() + ".message"));
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
