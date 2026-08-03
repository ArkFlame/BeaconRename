package com.arkflame.flameforge.forge;

import com.arkflame.flameforge.chance.ChanceEntry;
import com.arkflame.flameforge.chance.ChanceTable;
import com.arkflame.flameforge.chance.OutcomeSelector;
import com.arkflame.flameforge.chance.RandomSource;
import com.arkflame.flameforge.chance.ThreadLocalRandomSource;
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
import com.arkflame.flameforge.model.OutcomeDefinition;
import com.arkflame.flameforge.model.OutcomeType;
import com.arkflame.flameforge.model.PlayerForgeState;
import com.arkflame.flameforge.model.StationProfile;
import com.arkflame.flameforge.model.TierChances;
import com.arkflame.flameforge.model.TierDefinition;
import com.arkflame.flameforge.model.TierRequirements;
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
    private final RandomSource randomSource;
    private volatile boolean disabled;

    public ForgeService(JavaPlugin plugin, SchedulerBridge scheduler, ConfigService configService,
                       ForgeSessionService sessionService, ForgeStationService stationService,
                       CostService costService, ForgeAnimationService animationService,
                       OutcomeExecutor outcomeExecutor, DeliveryService deliveryService,
                       PlayerStateRepository playerStateRepository,
                       PendingDeliveryRepository pendingDeliveryRepository,
                       AuditLogService auditLog) {
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
        this.randomSource = ThreadLocalRandomSource.getInstance();
    }

    public void setDisabled(boolean disabled) {
        this.disabled = disabled;
    }

    public ForgePlan createPlan(Player player, PlayerForgeState session, ItemStack input) {
        if (player == null || session == null || input == null) {
            return null;
        }

        ConfigSnapshot config = configService.getCurrentSnapshot();
        int currentTier = session.getActiveTierLevel();
        int targetTier = currentTier + 1;

        TierDefinition tier = findTier(config, targetTier);
        if (tier == null) {
            return null;
        }

        if (!tier.isEnabled()) {
            return null;
        }

        TierRequirements requirements = tier.getRequirements();
        TierChances chances = tier.getChances();
        ForgeVariant selectedVariant = selectVariant(tier, session);
        CostQuote costQuote = costService.quote(player, tier.getCost());

        return ForgePlan.create(player, session, input, tier, requirements, chances,
                               selectedVariant, costQuote);
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

            ForgeContext context = ForgeContext.builder()
                .transactionId(transactionId)
                .playerId(playerId)
                .plan(plan)
                .playerState(sessionState)
                .configSnapshot(configService.getCurrentSnapshot())
                .stationProfile(null)
                .stationLocation(player.getLocation())
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

        ChargeReceipt chargeReceipt = costService.charge(player, plan.getTargetTier().getCost(), Collections.singletonList(input));
        if (!chargeReceipt.isSuccess()) {
            rollbackAndFail(context, session, player, ForgeTransaction.RollbackReason.PRE_TERMINAL_FAILURE,
                chargeReceipt.getFailureReason(), completionCallback);
            return null;
        }

        List<ItemStack> custodySnapshot = collectCustody(input);
        removeInputCustody(input);

        ChanceTable chanceTable = buildChanceTable(plan.getTargetTier());
        long randomValue = randomSource.nextLong(chanceTable.getTotalMicroWeight());
        ChanceEntry selectedEntry = chanceTable.select(randomValue);

        String selectedOutcomeId = selectedEntry.getOutcomeId();
        ForgeOutcomeCategory category = mapToCategory(selectedOutcomeId,
            plan.getTargetTier());
        OutcomeDefinition selectedOutcome = findOutcomeById(plan.getTargetTier(),
            selectedOutcomeId);

        if (selectedOutcome == null) {
            atomicRollback(context, player, custodySnapshot, chargeReceipt);
            rollbackAndFail(context, session, player, ForgeTransaction.RollbackReason.PRE_TERMINAL_FAILURE,
                "Selected outcome not found", completionCallback);
            return null;
        }

        ForgeTransaction.Builder txBuilder = ForgeTransaction.builder()
            .transactionId(context.getTransactionId())
            .context(context)
            .plan(plan)
            .quote(quote)
            .chargeReceipt(chargeReceipt)
            .chanceTable(chanceTable)
            .selectedEntry(selectedEntry)
            .outcomeCategory(category)
            .selectedOutcomeId(selectedOutcomeId)
            .custodySnapshot(custodySnapshot)
            .usedVariant(plan.getSelectedVariant());

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

        int animDuration = getAnimationDuration(plan.getTargetTier(), category);

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
        String selectedOutcomeId = transaction.getSelectedOutcomeId();
        OutcomeDefinition selectedOutcomeDef = findOutcomeById(plan.getTargetTier(), selectedOutcomeId);

        OutcomeExecutionResult execResult = outcomeExecutor.execute(plan, inputItem, player,
            UUID.randomUUID());

        if (execResult.hasItemOutput()) {
            resultItem = execResult.getItemOutput();
            mutatedItems.add(resultItem);
        }

        if (resultItem != null) {
            String deliveryId = deliveryService.generateDeliveryId(player, selectedOutcomeId != null ? selectedOutcomeId : "unknown");
            deliveryService.deliverItem(resultItem, player, context.getStationLocation(), deliveryId);
        }

        ForgeHistory history = ForgeHistory.of(
            context.getPlayerId(), Instant.now(),
            context.getPlan().getTargetTier().getId(),
            context.getTargetTierLevel(), selectedOutcomeId != null ? selectedOutcomeId : "unknown",
            selectedOutcomeDef != null ? selectedOutcomeDef.getType() : OutcomeType.BREAK,
            Collections.emptyList());

        ForgeOutcomeCategory category = transaction.getOutcomeCategory();

        ForgeResolution resolution = ForgeResolution.success(
            context.getTransactionId(), category,
            transaction.getChanceTable(), transaction.getSelectedEntry(),
            transaction.getUsedVariant(), selectedOutcomeId, resultItem, mutatedItems,
            history, transaction.getChargeReceipt(),
            transaction.getCustodySnapshot());

        session.setTerminalResolution(resolution);
        session.setPlayerStateSnapshot(context.getPlayerState());
        session.transitionToClosed();

        auditLog.logAsync("FORGE_COMPLETE", player.getName(), context.getTransactionId().toString(),
            "Category: " + category + ", Outcome: " + selectedOutcomeId);

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

    private ForgeOutcomeCategory mapToCategory(String outcomeId, TierDefinition tier) {
        if (tier == null || outcomeId == null) {
            return ForgeOutcomeCategory.BREAK;
        }
        OutcomeDefinition outcome = findOutcomeById(tier, outcomeId);
        if (outcome == null) {
            return ForgeOutcomeCategory.BREAK;
        }
        switch (outcome.getType()) {
            case MODIFY_INPUT:
            case CREATE_ITEM:
                return ForgeOutcomeCategory.SUCCESS;
            case BREAK:
                return ForgeOutcomeCategory.BREAK;
            case RETURN_UNCHANGED:
            case COMMANDS:
            default:
                return ForgeOutcomeCategory.BREAK;
        }
    }

    private int getAnimationDuration(TierDefinition tier, ForgeOutcomeCategory category) {
        if (tier == null) {
            return 1000;
        }
        switch (category) {
            case SUCCESS:
                return tier.getSuccessAnimationDuration();
            case BREAK:
            case CURSE:
            default:
                return tier.getFailAnimationDuration();
        }
    }

    private void invokeCallback(Consumer<ForgeResolution> callback, ForgeResolution resolution) {
        if (callback != null) {
            callback.accept(resolution);
        }
    }

    private ForgeVariant selectVariant(TierDefinition tier, PlayerForgeState session) {
        if (tier == null || tier.getVariants() == null || tier.getVariants().isEmpty()) {
            return null;
        }
        List<ForgeVariant> variants = tier.getVariants();
        double totalWeight = variants.stream().mapToDouble(ForgeVariant::getWeight).sum();
        if (totalWeight <= 0) {
            return variants.get(0);
        }
        long randomValue = randomSource.nextLong((long) totalWeight * 10000) / 10000;
        double cumulative = 0;
        for (ForgeVariant variant : variants) {
            cumulative += variant.getWeight();
            if (randomValue < cumulative) {
                return variant;
            }
        }
        return variants.get(variants.size() - 1);
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

    private TierDefinition findTier(ConfigSnapshot config, int tierLevel) {
        if (config == null) return null;
        List<TierDefinition> tiers = config.getTiers();
        if (tiers == null) return null;
        for (TierDefinition tier : tiers) {
            if (tier.getLevel() == tierLevel) {
                return tier;
            }
        }
        return null;
    }

    private OutcomeDefinition findOutcomeById(TierDefinition tier, String outcomeId) {
        if (tier == null || outcomeId == null) return null;
        for (OutcomeDefinition outcome : tier.getOutcomes()) {
            if (outcome.getId().equals(outcomeId)) {
                return outcome;
            }
        }
        return null;
    }

    private ChanceTable buildChanceTable(TierDefinition tier) {
        if (tier == null) {
            throw new IllegalStateException("Tier is null");
        }
        List<OutcomeDefinition> outcomes = tier.getOutcomes();
        if (outcomes == null || outcomes.isEmpty()) {
            throw new IllegalStateException("No outcomes defined for tier");
        }

        int n = outcomes.size();
        java.math.BigDecimal[] weights = new java.math.BigDecimal[n];
        String[] ids = new String[n];
        int[] orders = new int[n];

        for (int i = 0; i < n; i++) {
            OutcomeDefinition outcome = outcomes.get(i);
            weights[i] = outcome.getWeight();
            ids[i] = outcome.getId();
            orders[i] = outcome.getDisplayOrder();
        }

        return ChanceTable.from(weights, ids, orders);
    }

    private List<AnimationStep> loadAnimationSteps(ConfigSnapshot config, Location location,
                                                  ForgeOutcomeCategory category) {
        return Collections.emptyList();
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
