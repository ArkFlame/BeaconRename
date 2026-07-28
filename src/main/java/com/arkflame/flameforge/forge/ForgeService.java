package com.arkflame.flameforge.forge;

import com.arkflame.flameforge.chance.ChanceEntry;
import com.arkflame.flameforge.chance.ChanceTable;
import com.arkflame.flameforge.chance.OutcomeSelector;
import com.arkflame.flameforge.chance.RandomSource;
import com.arkflame.flameforge.chance.ThreadLocalRandomSource;
import com.arkflame.flameforge.compat.scheduler.SchedulerBridge;
import com.arkflame.flameforge.config.ConfigSnapshot;
import com.arkflame.flameforge.config.ConfigService;
import com.arkflame.flameforge.effect.AnimationHandle;
import com.arkflame.flameforge.effect.ForgeAnimationService;
import com.arkflame.flameforge.forge.ForgeTransaction.Builder;
import com.arkflame.flameforge.model.AnimationStep;
import com.arkflame.flameforge.model.ForgeHistory;
import com.arkflame.flameforge.model.OutcomeDefinition;
import com.arkflame.flameforge.model.OutcomeType;
import com.arkflame.flameforge.model.PlayerForgeState;
import com.arkflame.flameforge.model.StationProfile;
import com.arkflame.flameforge.model.TierDefinition;
import com.arkflame.flameforge.persistence.AuditLogService;
import com.arkflame.flameforge.persistence.PendingDeliveryRepository;
import com.arkflame.flameforge.persistence.PlayerStateRepository;
import com.arkflame.flameforge.session.ForgeSession;
import com.arkflame.flameforge.session.ForgeSessionService;
import com.arkflame.flameforge.persistence.StationRepository.StationData;
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
                returnCustodyAndRefund(session, player);
                session.transitionToClosed();
            } else if (session.isProcessing()) {
                session.transitionToSettling();
                queuePendingDeliveryForSession(session, player);
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
                        queuePendingDeliveryForSession(session, player);
                    }
                }
            }
        });
        deliveryService.processGlobalContext();
    }

    public ForgeContext buildContext(Player player, ItemStack[] inputSlots,
            ItemStack[] catalystSlots, ItemStack[] wardSlots, int tierLevel) {
        if (player == null) {
            throw new IllegalArgumentException("Player cannot be null");
        }
        ConfigSnapshot config = configService.getCurrentSnapshot();
        String playerId = player.getUniqueId().toString();
        PlayerForgeState playerState = loadOrCreatePlayerState(player);

        StationData stationData = stationService.resolveStationFromClick(player).orElse(null);
        String stationId = stationData != null ? stationData.id : null;
        StationProfile profile = stationData != null ?
            stationService.resolveProfile(stationData).orElse(null) : null;
        Location stationLocation = null;
        if (stationData != null && player.getWorld() != null) {
            World world = Bukkit.getWorld(stationData.world);
            if (world != null) {
                stationLocation = stationData.toLocation(world);
            }
        }

        return ForgeContext.builder()
            .transactionId(UUID.randomUUID())
            .playerId(playerId)
            .inputSlots(cloneItems(inputSlots))
            .catalystSlots(cloneItems(catalystSlots))
            .wardSlots(cloneItems(wardSlots))
            .tierLevel(tierLevel)
            .stationId(stationId)
            .stationProfile(profile)
            .playerState(playerState)
            .configSnapshot(config)
            .stationLocation(stationLocation)
            .build();
    }

    public ValidationResult validateOpen(ForgeContext context, Player player) {
        if (disabled) {
            return ValidationResult.failure("Forge is disabled");
        }
        if (context == null) {
            return ValidationResult.failure("Context is null");
        }
        if (player == null || !player.isOnline()) {
            return ValidationResult.failure("Player is not online");
        }
        PlayerForgeState state = context.getPlayerState();
        if (state != null && state.isOnCooldown(context.getStationId())) {
            return ValidationResult.failure("Player is on cooldown");
        }
        StationProfile profile = context.getStationProfile();
        if (profile != null) {
            if (!stationService.hasPermission(player, null, profile)) {
                return ValidationResult.failure("Missing station permission");
            }
            if (!stationService.isTierAllowed(profile, context.getTierLevel())) {
                return ValidationResult.failure("Tier not allowed at this station");
            }
        }
        if (!context.hasInputItems()) {
            return ValidationResult.failure("No input items provided");
        }
        return ValidationResult.success();
    }

    public PreviewResult preview(ForgeContext context, Player player) {
        ConfigSnapshot config = context.getConfigSnapshot();
        TierDefinition tier = findTier(config, context.getTierLevel());
        if (tier == null) {
            return PreviewResult.failure("Tier not found");
        }
        CostQuote quote = costService.quote(player, tier.getCost());
        if (!quote.isAffordable()) {
            return PreviewResult.failure("Cannot afford cost");
        }
        List<AnimationStep> animSteps = loadAnimationSteps(config, context.getStationId(), "preview");
        ChanceTable table = buildChanceTable(tier);
        return PreviewResult.of(quote, tier, table, animSteps);
    }

    public ForgeResolution confirmAndExecute(ForgeContext context, Player player,
            ItemStack[] inputSlots, ItemStack[] catalystSlots, ItemStack[] wardSlots,
            Consumer<ForgeResolution> completionCallback) {
        if (Thread.holdsLock(getClass())) {
            throw new IllegalStateException("Cannot call while holding lock");
        }
        String playerId = context.getPlayerId();
        ForgeSession session = sessionService.openSession(playerId);

        synchronized (session) {
            if (session.isClosed()) {
                return ForgeResolution.failure(context.getTransactionId(), "Session is closed", false);
            }
            if (!session.isOpen()) {
                return ForgeResolution.failure(context.getTransactionId(), "Session is not open", false);
            }

            ConfigSnapshot configAtConfirm = configService.getCurrentSnapshot();
            PlayerForgeState reReadPlayerState = loadOrCreatePlayerState(player);

            TierDefinition tier = findTier(configAtConfirm, context.getTierLevel());
            if (tier == null) {
                return finalizeWithFailure(session, context, player, "Tier not found", true);
            }

            ForgeTransaction.Builder txBuilder = ForgeTransaction.builder()
                .transactionId(context.getTransactionId())
                .context(context)
                .historyBefore(ForgeHistory.of(playerId, Instant.now(), context.getStationId(),
                    context.getTierLevel(), null, null, Collections.emptyList()));

            CostQuote quote = costService.quote(player, tier.getCost());
            if (!quote.isAffordable()) {
                return finalizeWithFailure(session, context, player, "Cannot afford cost", true);
            }
            txBuilder.quote(quote);

            List<ItemStack> custodySnapshot = collectCustody(inputSlots, catalystSlots, wardSlots);
            txBuilder.custodySnapshot(custodySnapshot);

            ChargeReceipt chargeReceipt = costService.charge(player, tier.getCost());
            if (!chargeReceipt.isSuccess()) {
                costService.refund(player, chargeReceipt);
                return finalizeWithFailure(session, context, player,
                    chargeReceipt.getFailureReason(), true);
            }
            txBuilder.chargeReceipt(chargeReceipt);

            removeInputCustody(inputSlots, catalystSlots, wardSlots);

            ChanceTable chanceTable = buildChanceTable(tier);
            txBuilder.chanceTable(chanceTable);

            long randomValue = randomSource.nextLong(chanceTable.getTotalMicroWeight());
            ChanceEntry selectedEntry = chanceTable.select(randomValue);

            OutcomeDefinition selectedOutcome = findOutcomeById(tier, selectedEntry.getOutcomeId());
            if (selectedOutcome == null) {
                rollbackCustodyAndCharges(context, player, custodySnapshot, chargeReceipt);
                return finalizeWithFailure(session, context, player, "Selected outcome not found", true);
            }
            txBuilder.selectedEntry(selectedEntry);
            txBuilder.selectedOutcome(selectedOutcome);

            ForgeTransaction transaction = txBuilder.build();
            if (!session.atomicOpenToProcessing(context, transaction)) {
                rollbackCustodyAndCharges(context, player, custodySnapshot, chargeReceipt);
                return finalizeWithFailure(session, context, player, "State transition failed", true);
            }

            updateCooldownAndPityOnce(context, reReadPlayerState, player);

            List<AnimationStep> animSteps = loadAnimationSteps(configAtConfirm,
                context.getStationId(), selectedOutcome.getType() == OutcomeType.BREAK ? "fail" : "success");
            int animDuration = selectedOutcome.getType() == OutcomeType.BREAK ?
                tier.getFailAnimationDuration() : tier.getSuccessAnimationDuration();

            AnimationHandle animHandle = animationService.playAnimation(
                context.getTransactionId().toString(), player, context.getStationLocation(),
                animSteps, animDuration, (txId) -> {
                    scheduler.runGlobal(plugin, () -> {
                        synchronized (session) {
                            if (session.isSettling()) {
                                executeOutcomeAndDeliver(session, context, player, selectedOutcome,
                                    chargeReceipt, completionCallback);
                            }
                        }
                    });
                });

            if (animHandle == null) {
                executeOutcomeAndDeliver(session, context, player, selectedOutcome,
                    chargeReceipt, completionCallback);
            }

            return null;
        }
    }

    private ForgeResolution finalizeWithFailure(ForgeSession session, ForgeContext context,
            Player player, String errorMessage, boolean preRollFailure) {
        ForgeResolution resolution = ForgeResolution.failure(
            context.getTransactionId(), errorMessage, preRollFailure);
        session.setTerminalResolution(resolution);
        session.transitionToClosed();
        return resolution;
    }

    private void rollbackCustodyAndCharges(ForgeContext context,
            Player player, List<ItemStack> custodySnapshot, ChargeReceipt chargeReceipt) {
        returnCustodyToPlayer(player, custodySnapshot);
        if (chargeReceipt != null && chargeReceipt.isSuccess()) {
            costService.refund(player, chargeReceipt);
        }
    }

    private void returnCustodyAndRefund(ForgeSession session, Player player) {
        ForgeTransaction tx = session.getCurrentTransaction();
        if (tx == null) {
            return;
        }
        List<ItemStack> custody = tx.getCustodySnapshot();
        if (custody != null && !custody.isEmpty()) {
            returnCustodyToPlayer(player, custody);
        }
        ChargeReceipt receipt = tx.getChargeReceipt();
        if (receipt != null && receipt.isSuccess()) {
            costService.refund(player, receipt);
        }
    }

    private void queuePendingDeliveryForSession(ForgeSession session, Player player) {
        ForgeTransaction tx = session.getCurrentTransaction();
        if (tx == null || !tx.hasSelectedOutcome()) {
            session.transitionToClosed();
            return;
        }
        ForgeContext ctx = session.getCurrentContext();
        if (ctx == null) {
            session.transitionToClosed();
            return;
        }
        OutcomeDefinition outcome = tx.getSelectedOutcome();
        OutcomeExecutionResult execResult = outcomeExecutor.execute(outcome,
            ctx.getInputSlots().length > 0 ? ctx.getInputSlots()[0] : null, player,
            ctx.getStationLocation());

        session.setTerminalResolution(ForgeResolution.success(
            ctx.getTransactionId(), tx.getChanceTable(), tx.getSelectedEntry(),
            outcome, execResult.getItemOutput(), null, tx.getChargeReceipt(),
            tx.getCustodySnapshot(), execResult.isWardConverted()));
        session.transitionToClosed();
    }

    private void executeOutcomeAndDeliver(ForgeSession session, ForgeContext context,
            Player player, OutcomeDefinition outcome, ChargeReceipt chargeReceipt,
            Consumer<ForgeResolution> completionCallback) {
        if (session.isClosed()) {
            return;
        }

        ForgeTransaction tx = session.getCurrentTransaction();
        ItemStack resultItem = null;
        boolean wardConverted = false;

        ItemStack inputItem = context.getInputSlots().length > 0 ? context.getInputSlots()[0] : null;
        OutcomeExecutionResult execResult = outcomeExecutor.execute(outcome, inputItem, player,
            context.getStationLocation());

        wardConverted = execResult.isWardConverted();

        if (execResult.hasItemOutput()) {
            resultItem = execResult.getItemOutput();
        } else if (outcome.getType() == OutcomeType.BREAK) {
            resultItem = null;
        } else if (outcome.getType() == OutcomeType.RETURN_UNCHANGED && inputItem != null) {
            resultItem = inputItem.clone();
            String deliveryId = deliveryService.generateDeliveryId(player, outcome.getId());
            deliveryService.deliverItem(resultItem, player, context.getStationLocation(), deliveryId);
        }

        ForgeHistory history = ForgeHistory.of(
            context.getPlayerId(), Instant.now(), context.getStationId(),
            context.getTierLevel(), outcome.getId(), outcome.getType(),
            Collections.emptyList());

        ForgeResolution resolution = ForgeResolution.success(
            context.getTransactionId(), tx.getChanceTable(), tx.getSelectedEntry(),
            outcome, resultItem, history, chargeReceipt, tx.getCustodySnapshot(), wardConverted);

        session.setTerminalResolution(resolution);
        session.setPlayerStateSnapshot(context.getPlayerState());
        session.transitionToClosed();

        auditLog.logAsync("FORGE_COMPLETE", player.getName(), context.getTransactionId().toString(),
            "Outcome: " + outcome.getId() + ", Type: " + outcome.getType());

        if (completionCallback != null) {
            completionCallback.accept(resolution);
        }
    }

    private ItemStack[] cloneItems(ItemStack[] original) {
        if (original == null) return null;
        ItemStack[] copy = new ItemStack[original.length];
        for (int i = 0; i < original.length; i++) {
            if (original[i] != null) {
                copy[i] = original[i].clone();
            }
        }
        return copy;
    }

    private List<ItemStack> collectCustody(ItemStack[] input, ItemStack[] catalyst, ItemStack[] ward) {
        List<ItemStack> custody = new ArrayList<>();
        if (input != null) {
            for (ItemStack item : input) {
                if (item != null && item.getType() != org.bukkit.Material.AIR) {
                    custody.add(item.clone());
                }
            }
        }
        if (catalyst != null) {
            for (ItemStack item : catalyst) {
                if (item != null && item.getType() != org.bukkit.Material.AIR) {
                    custody.add(item.clone());
                }
            }
        }
        if (ward != null) {
            for (ItemStack item : ward) {
                if (item != null && item.getType() != org.bukkit.Material.AIR) {
                    custody.add(item.clone());
                }
            }
        }
        return custody;
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

    private void removeInputCustody(ItemStack[] input, ItemStack[] catalyst, ItemStack[] ward) {
        if (input != null) {
            for (int i = 0; i < input.length; i++) {
                if (input[i] != null && input[i].getType() != org.bukkit.Material.AIR) {
                    input[i] = null;
                }
            }
        }
        if (catalyst != null) {
            for (int i = 0; i < catalyst.length; i++) {
                if (catalyst[i] != null && catalyst[i].getType() != org.bukkit.Material.AIR) {
                    catalyst[i] = null;
                }
            }
        }
        if (ward != null) {
            for (int i = 0; i < ward.length; i++) {
                if (ward[i] != null && ward[i].getType() != org.bukkit.Material.AIR) {
                    ward[i] = null;
                }
            }
        }
    }

    private void updateCooldownAndPityOnce(ForgeContext context,
            PlayerForgeState currentState, Player player) {
        String stationId = context.getStationId();
        if (stationId == null || player == null) {
            return;
        }

        ConfigSnapshot config = context.getConfigSnapshot();
        long cooldownDuration = getCooldownDuration(config, stationId);

        if (cooldownDuration <= 0) {
            return;
        }

        playerStateRepository.updateAndSave(player.getUniqueId(), existing -> {
            PlayerStateRepository.PlayerState state = existing != null ? existing : new PlayerStateRepository.PlayerState(player.getUniqueId(), 0, 0L);
            long newCooldown = System.currentTimeMillis() + cooldownDuration;
            return new PlayerStateRepository.PlayerState(state.uuid, state.tier, newCooldown);
        });
    }

    private TierDefinition findTier(ConfigSnapshot config, int tierLevel) {
        if (config == null) return null;
        List<TierDefinition> tiers = config.getTiers();
        if (tiers == null) return null;
        for (TierDefinition tier : tiers) {
            if (tier.getTierLevel() == tierLevel) {
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

    private List<AnimationStep> loadAnimationSteps(ConfigSnapshot config, String stationId, String profile) {
        return Collections.emptyList();
    }

    private long getCooldownDuration(ConfigSnapshot config, String stationId) {
        return 0L;
    }

    private PlayerForgeState loadOrCreatePlayerState(Player player) {
        if (player == null) return null;
        PlayerStateRepository.PlayerState state = playerStateRepository.getOrLoad(player.getUniqueId());
        return PlayerForgeState.of(
            player.getUniqueId().toString(),
            com.arkflame.flameforge.model.ForgeSessionState.OPEN,
            null,
            state != null ? state.tier : 0,
            Collections.emptyMap(),
            Collections.emptyMap()
        );
    }

    public static final class ValidationResult {
        private final boolean success;
        private final String errorMessage;

        private ValidationResult(boolean success, String errorMessage) {
            this.success = success;
            this.errorMessage = errorMessage;
        }

        public static ValidationResult success() {
            return new ValidationResult(true, null);
        }

        public static ValidationResult failure(String errorMessage) {
            return new ValidationResult(false, errorMessage);
        }

        public boolean isSuccess() { return success; }
        public String getErrorMessage() { return errorMessage; }
    }

    public static final class PreviewResult {
        private final boolean success;
        private final String errorMessage;
        private final CostQuote quote;
        private final TierDefinition tier;
        private final ChanceTable chanceTable;
        private final List<AnimationStep> animationSteps;

        private PreviewResult(boolean success, String errorMessage, CostQuote quote,
                TierDefinition tier, ChanceTable chanceTable, List<AnimationStep> animationSteps) {
            this.success = success;
            this.errorMessage = errorMessage;
            this.quote = quote;
            this.tier = tier;
            this.chanceTable = chanceTable;
            this.animationSteps = animationSteps;
        }

        public static PreviewResult of(CostQuote quote, TierDefinition tier,
                ChanceTable chanceTable, List<AnimationStep> animationSteps) {
            return new PreviewResult(true, null, quote, tier, chanceTable, animationSteps);
        }

        public static PreviewResult failure(String errorMessage) {
            return new PreviewResult(false, errorMessage, null, null, null, null);
        }

        public boolean isSuccess() { return success; }
        public String getErrorMessage() { return errorMessage; }
        public CostQuote getQuote() { return quote; }
        public TierDefinition getTier() { return tier; }
        public ChanceTable getChanceTable() { return chanceTable; }
        public List<AnimationStep> getAnimationSteps() { return animationSteps; }
    }
}
