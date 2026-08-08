package com.arkflame.flameforge.forge;

import com.arkflame.flameforge.chance.OutcomeSelector;
import com.arkflame.flameforge.compat.scheduler.SchedulerBridge;
import com.arkflame.flameforge.compat.scheduler.TaskHandle;
import com.arkflame.flameforge.config.ConfigSnapshot;
import com.arkflame.flameforge.config.ConfigService;
import com.arkflame.flameforge.effect.AnimationHandle;
import com.arkflame.flameforge.effect.ForgeAnimationService;
import com.arkflame.flameforge.model.AnimationStep;
import com.arkflame.flameforge.model.ForgeHistory;
import com.arkflame.flameforge.model.ForgeOutcomeCategory;
import com.arkflame.flameforge.model.ForgeVariant;
import com.arkflame.flameforge.model.OutcomeType;
import com.arkflame.flameforge.model.PlayerForgeState;
import com.arkflame.flameforge.model.StationProfile;
import com.arkflame.flameforge.model.TierChances;
import com.arkflame.flameforge.model.TierDefinition;
import com.arkflame.flameforge.model.TierRequirements;
import com.arkflame.flameforge.item.ItemIdentityService;
import com.arkflame.flameforge.persistence.AuditLogService;
import com.arkflame.flameforge.persistence.PendingDeliveryRepository;
import com.arkflame.flameforge.persistence.PlayerStateRepository;
import com.arkflame.flameforge.session.ForgeSession;
import com.arkflame.flameforge.session.ForgeSessionService;
import com.arkflame.flameforge.station.ForgeStationService;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public final class ForgeService {
    private final JavaPlugin plugin;
    private final SchedulerBridge scheduler;
    private final ConfigService configService;
    private final ForgeSessionService sessionService;
    private final ForgeStationService stationService;
    private final CostService costService;
    private final ForgeAnimationService animationService;
    private final OutcomeExecutor outcomeExecutor;
    private final DeliveryService deliveryService;
    private final PlayerStateRepository playerStateRepository;
    private final AuditLogService auditLog;
    private final OutcomeSelector outcomeSelector;
    private final ForgeVariantEligibility variantEligibility;
    private final ItemIdentityService identityService;
    private volatile boolean disabled;

    public ForgeService(JavaPlugin plugin, SchedulerBridge scheduler, ConfigService configService,
                       ForgeSessionService sessionService, ForgeStationService stationService,
                       CostService costService, ForgeAnimationService animationService,
                       OutcomeExecutor outcomeExecutor, DeliveryService deliveryService,
                       PlayerStateRepository playerStateRepository,
                       PendingDeliveryRepository pendingDeliveryRepository,
                       AuditLogService auditLog,
                       OutcomeSelector outcomeSelector,
                       ForgeVariantEligibility variantEligibility,
                       ItemIdentityService identityService) {
        this.plugin = Objects.requireNonNull(plugin);
        this.scheduler = Objects.requireNonNull(scheduler);
        this.configService = Objects.requireNonNull(configService);
        this.sessionService = Objects.requireNonNull(sessionService);
        this.stationService = Objects.requireNonNull(stationService);
        this.costService = Objects.requireNonNull(costService);
        this.animationService = Objects.requireNonNull(animationService);
        this.outcomeExecutor = Objects.requireNonNull(outcomeExecutor);
        this.deliveryService = Objects.requireNonNull(deliveryService);
        this.playerStateRepository = Objects.requireNonNull(playerStateRepository);
        this.auditLog = Objects.requireNonNull(auditLog);
        this.outcomeSelector = Objects.requireNonNull(outcomeSelector);
        this.variantEligibility = Objects.requireNonNull(variantEligibility);
        this.identityService = Objects.requireNonNull(identityService);
    }

    public void setDisabled(boolean disabled) {
        this.disabled = disabled;
    }

    public ForgePlanResult createPlan(Player player, PlayerForgeState session, ItemStack input) {
        if (player == null || session == null || input == null) {
            return ForgePlanResult.noInput();
        }

        ItemIdentityService.ForgeIdentityRead identityRead = identityService.readForgeIdentity(input);
        int currentTierLevel = 0;
        if (identityRead.getStatus() == ItemIdentityService.ForgeIdentityStatus.VALID) {
            currentTierLevel = identityRead.getIdentity().getCurrentTier();
        }

        ConfigSnapshot config = configService.getCurrentSnapshot();
        TierDefinition tier = findExactNextTier(config, currentTierLevel);

        if (tier == null) {
            return ForgePlanResult.nextTierMissing();
        }

        if (!tier.isEnabled()) {
            return ForgePlanResult.nextTierDisabled();
        }

        String stationId = session.getActiveStationId();
        String stationProfileId = null;
        java.util.UUID stationWorldUuid = null;
        String stationWorldName = null;
        int stationBlockX = 0;
        int stationBlockY = 0;
        int stationBlockZ = 0;
        if (stationId != null) {
            java.util.Optional<com.arkflame.flameforge.persistence.StationRepository.StationData> stationData =
                stationService.getStationById(stationId);
            if (stationData.isPresent()) {
                com.arkflame.flameforge.persistence.StationRepository.StationData data = stationData.get();
                stationWorldName = data.world;
                stationProfileId = data.profile;
                stationBlockX = data.x;
                stationBlockY = data.y;
                stationBlockZ = data.z;
                java.util.Optional<com.arkflame.flameforge.persistence.StationRepository.RegisteredForge> registered =
                    stationService.getStationRepository().findById(stationId);
                if (registered.isPresent()) {
                    stationWorldUuid = registered.get().getWorldUuid();
                }
            }
        }

        TierRequirements requirements = tier.getRequirements();
        TierChances chances = tier.getChances();
        CostQuote costQuote = costService.quote(player, tier.getCost());

        ForgePlan plan = ForgePlan.createWithTier(input, currentTierLevel, tier, requirements, chances,
                               costQuote, stationId, stationProfileId,
                               stationWorldUuid, stationWorldName, stationBlockX, stationBlockY, stationBlockZ);

        return ForgePlanResult.ready(plan);
    }

    public void confirmAndExecute(Player player, PlayerForgeState sessionState,
                                 ItemStack input, ForgePlan plan,
                                 Consumer<ForgeResolution> completionCallback) {
        if (Thread.holdsLock(getClass())) {
            throw new IllegalStateException("Cannot call while holding lock");
        }
        if (player == null || plan == null) {
            invokeCallback(completionCallback, ForgeResolution.failure(
                plan != null ? UUID.randomUUID() : null,
                ForgeOutcomeCategory.BREAK, "Invalid parameters", true));
            return;
        }

        String playerId = player.getUniqueId().toString();
        ForgeSession session = sessionService.openSession(playerId);

        synchronized (session) {
            if (session.isClosed()) {
                invokeCallback(completionCallback, ForgeResolution.failure(
                    plan.getTargetTier() != null ? UUID.randomUUID() : null,
                    ForgeOutcomeCategory.BREAK, "Session is closed", false));
                return;
            }
            if (!session.isOpen()) {
                invokeCallback(completionCallback, ForgeResolution.failure(
                    plan.getTargetTier() != null ? UUID.randomUUID() : null,
                    ForgeOutcomeCategory.BREAK, "Session is not open", false));
                return;
            }

            UUID transactionId = UUID.randomUUID();

            Location stationLocation = resolveStationLocation(plan);

            ForgeContext context = ForgeContext.builder()
                .transactionId(transactionId)
                .playerId(playerId)
                .plan(plan)
                .playerState(sessionState)
                .configSnapshot(configService.getCurrentSnapshot())
                .stationProfile(null)
                .stationLocation(stationLocation)
                .build();

            ForgeTransaction tx = executeOrchestration(session, context, player, input,
                                                       completionCallback);
            if (tx != null) {
                context.setCurrentTransaction(tx);
            }
        }
    }

    private ForgeTransaction executeOrchestration(ForgeSession session, ForgeContext context,
                                                  Player player, ItemStack input,
                                                  Consumer<ForgeResolution> completionCallback) {
        ForgePlan plan = context.getPlan();

        if (!revalidatePlan(context, player, plan)) {
            rollbackAndFail(context, session, player, ForgeTransaction.RollbackReason.PRE_TERMINAL_FAILURE,
                "Plan revalidation failed", completionCallback);
            return null;
        }

        CostQuote quote = plan.getCostQuote();
        if (quote == null || !quote.isAffordable()) {
            rollbackAndFail(context, session, player, ForgeTransaction.RollbackReason.PRE_TERMINAL_FAILURE,
                "Cannot afford cost", completionCallback);
            return null;
        }

        List<ForgeVariant> eligibleVariants = variantEligibility.eligibleVariants(input, plan.getTargetTier().getVariants());
        if (eligibleVariants.isEmpty()) {
            rollbackAndFail(context, session, player, ForgeTransaction.RollbackReason.PRE_TERMINAL_FAILURE,
                "No eligible variants for this item", completionCallback);
            return null;
        }

        ForgeVariant usedVariant = outcomeSelector.selectVariant(eligibleVariants);
        ForgeOutcomeCategory category = outcomeSelector.rollCategory(plan.getTargetTier().getChances());

        ChargeReceipt chargeReceipt = costService.charge(player, plan.getTargetTier().getCost(), Collections.singletonList(input));
        if (!chargeReceipt.isSuccess()) {
            rollbackAndFail(context, session, player, ForgeTransaction.RollbackReason.PRE_TERMINAL_FAILURE,
                chargeReceipt.getFailureReason(), completionCallback);
            return null;
        }

        List<ItemStack> custodySnapshot = collectCustody(input);
        removeInputCustody(input);

        ForgeTransaction.Builder txBuilder = ForgeTransaction.builder()
            .transactionId(context.getTransactionId())
            .context(context)
            .plan(plan)
            .quote(quote)
            .chargeReceipt(chargeReceipt)
            .outcomeCategory(category)
            .custodySnapshot(custodySnapshot)
            .usedVariant(usedVariant);

        ForgeTransaction transaction = txBuilder.build();

        if (!session.atomicOpenToProcessing(context, transaction)) {
            atomicRollback(context, player, custodySnapshot, chargeReceipt);
            rollbackAndFail(context, session, player, ForgeTransaction.RollbackReason.PRE_TERMINAL_FAILURE,
                "State transition failed", completionCallback);
            return null;
        }

        if (!context.tryMarkCompleted()) {
            return transaction;
        }

        AnimationHandle animHandle = animationService.playAnimation(
            context.getTransactionId().toString(), player, context.getStationLocation(),
            plan.getTargetTier().getAnimationProfile(), (txId) -> {
                scheduler.runGlobal(plugin, () -> {
                    synchronized (session) {
                        if (session.isSettling()) {
                            mutateAndDeliver(session, context,
                                player, plan, transaction, completionCallback);
                        }
                    }
                });
            }, (txId) -> {
                scheduler.runGlobal(plugin, () -> {
                    rollbackAndFail(context, session, player, ForgeTransaction.RollbackReason.ANIMATION_FAILURE,
                        "Animation failed", completionCallback);
                });
            });

        if (animHandle == null) {
            mutateAndDeliver(session, context, player, plan, transaction,
                completionCallback);
        }

        return transaction;
    }

    private boolean revalidatePlan(ForgeContext context, Player player, ForgePlan plan) {
        if (disabled) {
            return false;
        }
        if (context == null || plan == null) {
            return false;
        }
        if (player == null || !player.isOnline()) {
            return false;
        }
        PlayerForgeState state = context.getPlayerState();
        if (state != null && state.isOnCooldown(context.getPlan().getTargetTier().getId())) {
            return false;
        }
        return true;
    }

    private List<ItemStack> mutateAndDeliver(ForgeSession session, ForgeContext context,
                                             Player player, ForgePlan plan,
                                             ForgeTransaction transaction,
                                             Consumer<ForgeResolution> completionCallback) {
        List<ItemStack> mutatedItems = new ArrayList<>();
        ItemStack inputItem = context.getInputItem();
        ItemStack resultItem = null;
        ForgeOutcomeCategory category = transaction.getOutcomeCategory();
        String outcomeId = category.name();

        OutcomeExecutionResult execResult = outcomeExecutor.execute(plan, inputItem, player,
            context.getTransactionId(), category, transaction.getUsedVariant());

        if (execResult.hasItemOutput()) {
            resultItem = execResult.getItemOutput();
            mutatedItems.add(resultItem);
        }

        if (resultItem != null) {
            String deliveryId = deliveryService.generateDeliveryId(player, outcomeId);
            deliveryService.deliverItem(resultItem, player, context.getStationLocation(), deliveryId);
        }

        ForgeHistory history = ForgeHistory.of(
            context.getPlayerId(), Instant.now(),
            context.getPlan().getTargetTier().getId(),
            context.getTargetTierLevel(), outcomeId,
            category == ForgeOutcomeCategory.SUCCESS ? OutcomeType.CREATE_ITEM : OutcomeType.BREAK,
            Collections.emptyList());

        ForgeResolution resolution = ForgeResolution.success(
            context.getTransactionId(), category,
            transaction.getUsedVariant(), outcomeId, resultItem, mutatedItems,
            history, transaction.getChargeReceipt(),
            transaction.getCustodySnapshot());

        session.setTerminalResolution(resolution);
        session.setPlayerStateSnapshot(context.getPlayerState());
        session.transitionToClosed();

        auditLog.logAsync("FORGE_COMPLETE", player.getName(), context.getTransactionId().toString(),
            "Category: " + category);

        invokeCallback(completionCallback, resolution);

        return mutatedItems;
    }

    private void rollbackAndFail(ForgeContext context, ForgeSession session, Player player,
                                 ForgeTransaction.RollbackReason reason, String errorMessage,
                                 Consumer<ForgeResolution> completionCallback) {
        ForgeTransaction tx = context.getCurrentTransaction();
        if (tx != null) {
            tx.rollback(reason, player,
                cust -> returnCustodyToPlayer(player, cust),
                receipt -> costService.refund(player, receipt));
        }

        ForgeResolution resolution = ForgeResolution.failure(
            context.getTransactionId(),
            ForgeOutcomeCategory.BREAK,
            errorMessage,
            reason == ForgeTransaction.RollbackReason.PRE_TERMINAL_FAILURE);

        session.setTerminalResolution(resolution);
        session.transitionToClosed();

        invokeCallback(completionCallback, resolution);
    }

    private void atomicRollback(ForgeContext context, Player player,
                               List<ItemStack> custodySnapshot, ChargeReceipt chargeReceipt) {
        returnCustodyToPlayer(player, custodySnapshot);
        if (chargeReceipt != null && chargeReceipt.isSuccess()) {
            costService.refund(player, chargeReceipt);
        }
    }

    private void returnCustodyToPlayer(Player player, List<ItemStack> custody) {
        if (custody == null || custody.isEmpty() || player == null || !player.isOnline()) {
            return;
        }
        for (ItemStack item : custody) {
            if (item != null && item.getType() != org.bukkit.Material.AIR) {
                deliveryService.deliverItem(item, player, player.getLocation(), null);
            }
        }
    }

    private void removeInputCustody(ItemStack input) {
        if (input != null && input.getType() != org.bukkit.Material.AIR) {
            input.setType(org.bukkit.Material.AIR);
            input.setAmount(0);
        }
    }

    private List<ItemStack> collectCustody(ItemStack input) {
        List<ItemStack> custody = new ArrayList<>();
        if (input != null && input.getType() != org.bukkit.Material.AIR) {
            custody.add(input.clone());
        }
        return custody;
    }

    private TierDefinition findExactNextTier(ConfigSnapshot config, int currentTierLevel) {
        if (config == null) return null;
        List<TierDefinition> tiers = config.getTiers();
        if (tiers == null) return null;
        for (TierDefinition tier : tiers) {
            if (tier.getLevel() == currentTierLevel + 1) {
                return tier;
            }
        }
        return null;
    }

    private Location resolveStationLocation(ForgePlan plan) {
        if (plan == null) {
            return null;
        }
        String worldName = plan.getStationWorldName();
        if (worldName == null) {
            return null;
        }
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            return null;
        }
        return new Location(world, plan.getStationBlockX() + 0.5, plan.getStationBlockY(), plan.getStationBlockZ() + 0.5);
    }

    private void invokeCallback(Consumer<ForgeResolution> callback, ForgeResolution resolution) {
        if (callback != null) {
            callback.accept(resolution);
        }
    }

    public void onPlayerQuit(Player player) {
        if (player == null) return;
        String playerId = player.getUniqueId().toString();
        ForgeSession session = sessionService.getSession(playerId);
        if (session == null) {
            return;
        }
        synchronized (session) {
            if (session.isOpen()) {
                session.transitionToSettling();
                ForgeTransaction tx = session.getCurrentTransaction();
                if (tx != null) {
                    tx.rollback(ForgeTransaction.RollbackReason.PLAYER_QUIT, player,
                        cust -> returnCustodyToPlayer(player, cust),
                        receipt -> costService.refund(player, receipt));
                }
                session.transitionToClosed();
            } else if (session.isProcessing()) {
                session.transitionToSettling();
            }
        }
    }

    public void onPlayerJoin(Player player) {
        if (player != null) {
            deliveryService.processPlayerJoin(player);
        }
    }

    public void onDisable() {
        disabled = true;
        sessionService.closeAllSessions(session -> {
            String playerId = session.getPlayerId();
            Player player = Bukkit.getPlayer(playerId);
            if (player != null && player.isOnline()) {
                synchronized (session) {
                    if (session.isProcessing()) {
                        session.transitionToSettling();
                    }
                }
            }
        });
        deliveryService.processGlobalContext();
    }
}
