package com.arkflame.flameforge;

import com.arkflame.flameforge.chance.RandomSource;
import com.arkflame.flameforge.chance.ThreadLocalRandomSource;
import com.arkflame.flameforge.command.CommandSuggestionIndex;
import com.arkflame.flameforge.command.FlameForgeCommand;
import com.arkflame.flameforge.command.ReadyServices;
import com.arkflame.flameforge.command.StartupFailure;
import com.arkflame.flameforge.compat.RuntimeCapabilities;
import com.arkflame.flameforge.compat.RuntimePlatform;
import com.arkflame.flameforge.compat.effect.ParticleBridge;
import com.arkflame.flameforge.compat.effect.PotionEffectResolver;
import com.arkflame.flameforge.compat.effect.SoundResolver;
import com.arkflame.flameforge.compat.equipment.EquipmentBridge;
import com.arkflame.flameforge.compat.interaction.InteractionHandBridge;
import com.arkflame.flameforge.compat.material.MaterialResolver;
import com.arkflame.flameforge.compat.scheduler.SchedulerBridge;
import com.arkflame.flameforge.compat.scheduler.SchedulerBridgeFactory;
import com.arkflame.flameforge.compat.scheduler.TeleportBridge;
import com.arkflame.flameforge.config.ConfigService;
import com.arkflame.flameforge.config.TierRepository;
import com.arkflame.flameforge.effect.ForgeAnimationService;
import com.arkflame.flameforge.forge.ChargeReceipt;
import com.arkflame.flameforge.forge.CostQuote;
import com.arkflame.flameforge.forge.CostService;
import com.arkflame.flameforge.forge.DeliveryService;
import com.arkflame.flameforge.forge.ForgeItemPolicy;
import com.arkflame.flameforge.forge.ForgePowerService;
import com.arkflame.flameforge.forge.ForgeService;
import com.arkflame.flameforge.forge.OutcomeExecutor;
import com.arkflame.flameforge.forge.OutcomeExecutionResult;
import com.arkflame.flameforge.hook.EconomyService;
import com.arkflame.flameforge.hook.EconomyServiceFactory;
import com.arkflame.flameforge.hook.PluginConditionService;
import com.arkflame.flameforge.item.AttributeBridge;
import com.arkflame.flameforge.item.EnchantmentResolver;
import com.arkflame.flameforge.item.ItemFactory;
import com.arkflame.flameforge.item.ItemIdentityCodec;
import com.arkflame.flameforge.item.ItemIdentityService;
import com.arkflame.flameforge.item.ItemMatcher;
import com.arkflame.flameforge.item.ItemMutationService;
import com.arkflame.flameforge.listener.ForgeInteractListener;
import com.arkflame.flameforge.listener.ForgeInventoryListener;
import com.arkflame.flameforge.listener.ForgePowerListener;
import com.arkflame.flameforge.listener.PlayerLifecycleListener;
import com.arkflame.flameforge.menu.ForgeMenuService;
import com.arkflame.flameforge.menu.InventoryFactory;
import com.arkflame.flameforge.menu.MenuInputReturnService;
import com.arkflame.flameforge.menu.SimpleInventoryFactory;
import com.arkflame.flameforge.menu.MenuItemFactory;
import com.arkflame.flameforge.persistence.AuditLogService;
import com.arkflame.flameforge.persistence.PendingDeliveryRepository;
import com.arkflame.flameforge.persistence.PlayerStateRepository;
import com.arkflame.flameforge.persistence.StationRepository;
import com.arkflame.flameforge.session.ForgeSessionService;
import com.arkflame.flameforge.station.ForgeStationService;
import com.arkflame.flameforge.text.MessageService;
import com.arkflame.flameforge.text.TextBridge;
import com.arkflame.flameforge.text.TextPlaceholders;
import com.arkflame.flameforge.text.TextRenderer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;

public final class FlameForgePlugin extends JavaPlugin {

    public enum StartupRetryResult {
        STARTED,
        ALREADY_LOADING,
        ALREADY_READY,
        RESTART_REQUIRED,
        UNAVAILABLE
    }

    private static class StartupPhaseException extends RuntimeException {
        private final StartupFailure.Component component;

        StartupPhaseException(StartupFailure.Component component, Throwable cause) {
            super(cause);
            this.component = component;
        }

        StartupFailure.Component getComponent() {
            return component;
        }
    }

    private volatile boolean enabled;
    private final AtomicLong lifecycleEpoch = new AtomicLong();
    private final AtomicBoolean startupFailureLogged = new AtomicBoolean();
    private volatile CompletableFuture<Void> startupFuture;
    private volatile CompletableFuture<?>[] startupComponentFutures = new CompletableFuture<?>[0];

    private SchedulerBridge schedulerBridge;
    private RuntimeCapabilities runtimeCapabilities;
    private RuntimePlatform runtimePlatform;
    private TextRenderer textRenderer;
    private TextBridge textBridge;
    private TextPlaceholders textPlaceholders;

    private ConfigService configService;
    private TierRepository tierRepository;

    private PlayerStateRepository playerStateRepository;
    private StationRepository stationRepository;
    private PendingDeliveryRepository pendingDeliveryRepository;
    private AuditLogService auditLogService;

    private EconomyService economyService;
    private PluginConditionService pluginConditionService;

    private ForgeStationService forgeStationService;
    private ForgeAnimationService forgeAnimationService;
    private ForgeMenuService forgeMenuService;
    private ForgeSessionService forgeSessionService;
    private CostService costService;
    private DeliveryService deliveryService;
    private OutcomeExecutor outcomeExecutor;
    private ForgeService forgeService;
    private ForgeAccessService forgeAccessService;
    private TeleportBridge teleportBridge;

    private ItemIdentityCodec itemIdentityCodec;
    private ItemIdentityService itemIdentityService;
    private ForgeItemPolicy forgeItemPolicy;
    private PotionEffectResolver potionEffectResolver;
    private EquipmentBridge equipmentBridge;
    private ForgePowerService forgePowerService;
    private MenuInputReturnService menuInputReturnService;

    private FlameForgeCommand command;
    private ForgeInteractListener forgeInteractListener;
    private ForgeInventoryListener forgeInventoryListener;
    private ForgePowerListener forgePowerListener;
    private PlayerLifecycleListener playerLifecycleListener;

    private CommandSuggestionIndex suggestionIndex;
    private ReadyServices readyServices;
    private MessageService messageService;

    @Override
    public void onEnable() {
        enabled = true;
        final long epoch = lifecycleEpoch.incrementAndGet();
        startupFailureLogged.set(false);

        saveDefaultConfig();

        runtimeCapabilities = RuntimeCapabilities.getInstance();
        runtimeCapabilities.initialize();

        runtimePlatform = RuntimePlatform.detect();
        schedulerBridge = SchedulerBridgeFactory.create(this, runtimePlatform);

        textRenderer = new TextRenderer();
        textPlaceholders = new TextPlaceholders();
        textBridge = TextBridge.create(this, textRenderer);
        SoundResolver.setLogger(getLogger());

        Path dataPath = getDataFolder().toPath();

        tierRepository = new TierRepository(this);
        configService = new ConfigService(this, schedulerBridge, tierRepository);
        playerStateRepository = new PlayerStateRepository(this, schedulerBridge, dataPath);
        stationRepository = new StationRepository(this, schedulerBridge, dataPath);
        pendingDeliveryRepository = new PendingDeliveryRepository(this, schedulerBridge, dataPath);
        messageService = MessageService.create(this, configService, textRenderer, textBridge, textPlaceholders,
            getLogger());
        suggestionIndex = new CommandSuggestionIndex(tierRepository);

        command = new FlameForgeCommand(
            this,
            schedulerBridge,
            messageService,
            configService,
            tierRepository,
            suggestionIndex
        );
        command.markLoading();

        getServer().getPluginCommand("flameforge").setExecutor(command);
        getServer().getPluginCommand("flameforge").setTabCompleter(command);

        beginStartupLoads(epoch);
    }

    private void beginStartupLoads(long epoch) {
        CompletableFuture<Void> configLoad = tagStartupFuture(
            configService.initialLoadAsync(), StartupFailure.Component.CONFIGURATION, epoch);
        CompletableFuture<Void> playerLoad = tagStartupFuture(
            playerStateRepository.loadAllAsync(), StartupFailure.Component.PLAYER_DATA, epoch);
        CompletableFuture<Void> stationLoad = tagStartupFuture(
            stationRepository.loadAsync(), StartupFailure.Component.STATION_DATA, epoch);
        CompletableFuture<Void> pendingLoad = tagStartupFuture(
            pendingDeliveryRepository.loadAsync(), StartupFailure.Component.PENDING_DELIVERIES, epoch);
        startupComponentFutures = new CompletableFuture<?>[] {
            configLoad, playerLoad, stationLoad, pendingLoad
        };
        startupFuture = CompletableFuture.allOf(startupComponentFutures);
        startupFuture.whenComplete((ignored, failure) -> {
            if (failure instanceof StartupPhaseException) {
                scheduleStartupFinalization(epoch, (StartupPhaseException) failure);
            } else {
                scheduleStartupFinalization(epoch, failure);
            }
        });
    }

    private CompletableFuture<Void> tagStartupFuture(CompletableFuture<?> future, StartupFailure.Component component, long epoch) {
        return future.exceptionally(ex -> {
            throw new StartupPhaseException(component, ex);
        }).thenApply(v -> null);
    }

    private void scheduleStartupFinalization(final long epoch, final Throwable loadFailure) {
        final SchedulerBridge scheduler = schedulerBridge;
        if (!isCurrentEpoch(epoch) || scheduler == null) {
            return;
        }

        final StartupFailure.Component failureComponent;
        if (loadFailure instanceof StartupPhaseException) {
            failureComponent = ((StartupPhaseException) loadFailure).getComponent();
        } else {
            failureComponent = null;
        }

        try {
            scheduler.runGlobal(this, () -> {
                if (!isCurrentEpoch(epoch)) {
                    return;
                }
                if (loadFailure != null) {
                    markStartupFailed(epoch, failureComponent, loadFailure);
                    return;
                }
                try {
                    initializeReadyServices();
                    if (!isCurrentEpoch(epoch)) {
                        return;
                    }
                    registerReadyListeners(epoch);
                    if (!isCurrentEpoch(epoch)) {
                        return;
                    }
                    command.markReady(readyServices);
                    logReadySummary();
                } catch (Throwable failure) {
                    markStartupFailed(epoch, null, failure);
                }
            });
        } catch (Throwable failure) {
            markStartupFailed(epoch, null, failure);
        }
    }

    private void initializeReadyServices() {
        Path dataPath = getDataFolder().toPath();
        int auditQueueCapacity = configService.getCurrentSnapshot().getRootInt("audit-queue-capacity", 1024);
        auditLogService = new AuditLogService(this, schedulerBridge, dataPath, auditQueueCapacity);

        pluginConditionService = new PluginConditionService(getServer().getPluginManager());
        EconomyServiceFactory economyFactory = new EconomyServiceFactory(
            pluginConditionService,
            getServer().getServicesManager()
        );
        economyService = economyFactory.create();

        ParticleBridge particleBridge = ParticleBridge.getInstance();
        SoundResolver soundResolver = SoundResolver.getInstance();
        Map<String, Object> emptyMap = new HashMap<>();
        deliveryService = new DeliveryService(
            this,
            schedulerBridge,
            pendingDeliveryRepository,
            textBridge,
            auditLogService,
            emptyMap
        );

        RandomSource randomSource = ThreadLocalRandomSource.getInstance();
        com.arkflame.flameforge.chance.OutcomeSelector outcomeSelector =
            new com.arkflame.flameforge.chance.OutcomeSelector(randomSource);
        costService = new CostService(this, economyService);
        teleportBridge = new TeleportBridge(this, schedulerBridge, runtimePlatform);
        forgeStationService = new ForgeStationService(this, schedulerBridge, stationRepository, configService, textRenderer, teleportBridge);
        forgeAnimationService = new ForgeAnimationService(
            this, schedulerBridge, particleBridge, soundResolver, textBridge, textRenderer
        );
        forgeSessionService = new ForgeSessionService();

        itemIdentityCodec = new ItemIdentityCodec();
        itemIdentityService = ItemIdentityService.getInstance();
        AttributeBridge attributeBridge = AttributeBridge.getInstance();
        forgeItemPolicy = new ForgeItemPolicy(itemIdentityService, attributeBridge, tierRepository);
        potionEffectResolver = new PotionEffectResolver();
        equipmentBridge = new EquipmentBridge();
        forgePowerService = new ForgePowerService(
            this,
            schedulerBridge,
            potionEffectResolver,
            equipmentBridge,
            itemIdentityService
        );
        menuInputReturnService = new MenuInputReturnService(deliveryService);

        InventoryFactory inventoryFactory = new SimpleInventoryFactory();
        MenuItemFactory menuItemFactory = new MenuItemFactory(MaterialResolver.getInstance(), textRenderer);
        forgeMenuService = new ForgeMenuService(
            inventoryFactory,
            menuInputReturnService,
            configService,
            costService,
            textRenderer
        );

        Map<String, Object> wardConfig = configService.getCurrentSnapshot().getWard("default");
        outcomeExecutor = new OutcomeExecutor(
            ItemMutationService.getInstance(),
            auditLogService,
            wardConfig != null ? wardConfig : emptyMap
        );
        forgeService = new ForgeService(
            this,
            schedulerBridge,
            configService,
            forgeSessionService,
            forgeStationService,
            costService,
            forgeAnimationService,
            outcomeExecutor,
            deliveryService,
            playerStateRepository,
            pendingDeliveryRepository,
            auditLogService
        );
        forgeAccessService = new ForgeAccessService(
            this,
            schedulerBridge,
            forgeStationService,
            stationRepository,
            forgeSessionService,
            playerStateRepository,
            forgeMenuService,
            configService,
            forgeService
        );
        readyServices = new ReadyServices(
            configService,
            tierRepository,
            forgeStationService,
            stationRepository,
            playerStateRepository,
            forgeService,
            forgeAccessService,
            textBridge,
            messageService,
            MaterialResolver.getInstance(),
            null,
            itemIdentityCodec,
            itemIdentityService,
            forgeItemPolicy,
            potionEffectResolver,
            equipmentBridge,
            forgePowerService,
            menuInputReturnService
        );

        updateSuggestionIndex();
        InteractionHandBridge handBridge = new InteractionHandBridge(getLogger());
        forgeInteractListener = new ForgeInteractListener(this, forgeAccessService, forgeStationService, handBridge);
        forgeInventoryListener = new ForgeInventoryListener(this, forgeMenuService, schedulerBridge);
        forgePowerListener = new ForgePowerListener(this, forgePowerService, equipmentBridge, itemIdentityService, tierRepository);
        playerLifecycleListener = new PlayerLifecycleListener(
            this,
            forgeStationService,
            playerStateRepository,
            deliveryService,
            forgePowerService,
            forgeMenuService,
            schedulerBridge,
            readyServices,
            suggestionIndex
        );
        deliveryService.processGlobalContext();

        if (configService.hasValidationErrors()) {
            throw new RuntimeException("Configuration failed: " + configService.getValidationReport().getErrors().size() + " validation error(s)");
        }
    }

    private void registerReadyListeners(long epoch) {
        if (!isCurrentEpoch(epoch)) {
            return;
        }
        getServer().getPluginManager().registerEvents(forgeInteractListener, this);
        getServer().getPluginManager().registerEvents(forgeInventoryListener, this);
        getServer().getPluginManager().registerEvents(forgePowerListener, this);
        getServer().getPluginManager().registerEvents(playerLifecycleListener, this);
    }

    private void logReadySummary() {
        int tierCount = tierRepository.size();
        int stationCount = stationRepository.getAllSnapshot().size();
        boolean hasEconomy = economyService.available();
        getLogger().info("FlameForge 1.0.1 ready: tiers=" + tierCount + ", stations=" + stationCount
            + ", folia=" + schedulerBridge.isFolia() + ", economy=" + (hasEconomy ? "available" : "unavailable"));
    }

    private void markStartupFailed(long epoch, StartupFailure.Component component, Throwable failure) {
        if (!isCurrentEpoch(epoch) || !startupFailureLogged.compareAndSet(false, true)) {
            return;
        }
        Throwable root = rootCause(failure);
        StartupFailure startupFailure = StartupFailure.create(component, root, epoch);
        command.markFailed(startupFailure);
        getLogger().log(Level.SEVERE, "Startup failed: " + startupFailure.getReason(), root);
    }

    private void markStartupFailed(long epoch, Throwable failure) {
        markStartupFailed(epoch, null, failure);
    }

    public StartupRetryResult retryStartup() {
        if (!enabled) {
            return StartupRetryResult.UNAVAILABLE;
        }
        if (command.isReady()) {
            return StartupRetryResult.ALREADY_READY;
        }
        if (command.isLoading()) {
            return StartupRetryResult.ALREADY_LOADING;
        }
        StartupFailure failure = command.getStartupFailure();
        if (failure == null || !failure.isRetryable()) {
            return StartupRetryResult.RESTART_REQUIRED;
        }
        final long newEpoch = lifecycleEpoch.incrementAndGet();
        startupFailureLogged.set(false);
        CompletableFuture<?>[] pendingComponents = startupComponentFutures;
        for (CompletableFuture<?> pendingComponent : pendingComponents) {
            if (pendingComponent != null) {
                pendingComponent.cancel(false);
            }
        }
        command.markLoading();
        beginStartupLoads(newEpoch);
        return StartupRetryResult.STARTED;
    }

    private boolean isCurrentEpoch(long epoch) {
        return enabled && lifecycleEpoch.get() == epoch;
    }

    private static Throwable rootCause(Throwable failure) {
        Throwable root = failure;
        while ((root instanceof java.util.concurrent.CompletionException
                || root instanceof java.util.concurrent.ExecutionException)
                && root.getCause() != null) {
            root = root.getCause();
        }
        return root;
    }

    @Override
    public void onDisable() {
        enabled = false;
        final long shutdownEpoch = lifecycleEpoch.incrementAndGet();
        CompletableFuture<?>[] pendingComponents = startupComponentFutures;
        for (CompletableFuture<?> pendingComponent : pendingComponents) {
            if (pendingComponent != null) {
                pendingComponent.cancel(false);
            }
        }
        startupComponentFutures = new CompletableFuture<?>[0];
        CompletableFuture<Void> pendingStartup = startupFuture;
        if (pendingStartup != null) {
            pendingStartup.cancel(false);
        }

        if (command != null) {
            command.markUnavailable();
        }
        if (forgeMenuService != null) {
            forgeMenuService.closeAll();
        }
        if (forgePowerService != null) {
            forgePowerService.clearAll();
        }
        if (forgeService != null) {
            forgeService.onDisable();
        }
        if (forgeSessionService != null) {
            forgeSessionService.shutdown();
        }
        if (forgeStationService != null) {
            forgeStationService.cleanupHolograms();
        }
        if (pendingDeliveryRepository != null) {
            pendingDeliveryRepository.saveAsync();
        }
        if (stationRepository != null) {
            stationRepository.flush();
        }

        final SchedulerBridge shutdownScheduler = schedulerBridge;
        if (auditLogService != null) {
            auditLogService.flushAsync();
            auditLogService.closeAsync().whenComplete((ignored, failure) -> {
                if (shutdownScheduler != null && (!enabled || lifecycleEpoch.get() == shutdownEpoch)) {
                    shutdownScheduler.cancelAll(this);
                }
            });
        } else if (shutdownScheduler != null) {
            shutdownScheduler.cancelAll(this);
        }

        if (textBridge != null) {
            textBridge.close();
        }
    }

    public void reload() {
        final long epoch = lifecycleEpoch.get();
        final ConfigService config = configService;
        final SchedulerBridge scheduler = schedulerBridge;
        if (!isCurrentEpoch(epoch) || config == null || scheduler == null) {
            return;
        }
        config.asyncReloadWithCallback(() -> {
            if (!isCurrentEpoch(epoch)) {
                return;
            }
            scheduler.runGlobal(this, () -> {
                if (isCurrentEpoch(epoch)) {
                    refreshRuntimeState();
                }
            });
        });
    }

    private void updateSuggestionIndex() {
        suggestionIndex.updateOnlinePlayers(
            Bukkit.getOnlinePlayers().stream()
                .map(p -> p.getName())
                .collect(java.util.stream.Collectors.toSet())
        );
        suggestionIndex.updateProfileIds(readyServices.getProfileIds());
        suggestionIndex.updateStationIds(
            forgeStationService.listStations().stream()
                .map(s -> s.id)
                .collect(java.util.stream.Collectors.toList())
        );
    }

    private void refreshRuntimeState() {
        forgeStationService.reloadHolograms();
        updateSuggestionIndex();

        int tierCount = tierRepository.size();
        int stationCount = stationRepository.getAllSnapshot().size();
        boolean hasEconomy = economyService.available();
        String hologramStatus = forgeStationService.getHologramService().getProviderStatus();

        Component message = Component.text()
            .append(Component.text("[FlameForge] ", NamedTextColor.GOLD))
            .append(Component.text("Reload complete. ", NamedTextColor.GREEN))
            .append(Component.text("Tiers: " + tierCount, NamedTextColor.WHITE))
            .append(Component.text(" | Stations: " + stationCount, NamedTextColor.WHITE))
            .append(Component.text(" | Economy: " + (hasEconomy ? "yes" : "no"), NamedTextColor.WHITE))
            .append(Component.text(" | Holograms: " + hologramStatus, NamedTextColor.WHITE))
            .build();

        textBridge.sendAll(message);
    }
}