package com.arkflame.flameforge;

import com.arkflame.flameforge.chance.ChanceTable;
import com.arkflame.flameforge.chance.OutcomeSelector;
import com.arkflame.flameforge.chance.RandomSource;
import com.arkflame.flameforge.chance.ThreadLocalRandomSource;
import com.arkflame.flameforge.command.FlameForgeCommand;
import com.arkflame.flameforge.compat.RuntimeCapabilities;
import com.arkflame.flameforge.compat.effect.ParticleBridge;
import com.arkflame.flameforge.compat.effect.SoundResolver;
import com.arkflame.flameforge.compat.material.MaterialResolver;
import com.arkflame.flameforge.compat.scheduler.SchedulerBridge;
import com.arkflame.flameforge.compat.scheduler.SchedulerBridgeFactory;
import com.arkflame.flameforge.config.ConfigService;
import com.arkflame.flameforge.config.TierRepository;
import com.arkflame.flameforge.effect.ForgeAnimationService;
import com.arkflame.flameforge.forge.ChargeReceipt;
import com.arkflame.flameforge.forge.CostQuote;
import com.arkflame.flameforge.forge.CostService;
import com.arkflame.flameforge.forge.DeliveryService;
import com.arkflame.flameforge.forge.OutcomeExecutor;
import com.arkflame.flameforge.forge.OutcomeExecutionResult;
import com.arkflame.flameforge.hook.EconomyService;
import com.arkflame.flameforge.hook.EconomyServiceFactory;
import com.arkflame.flameforge.hook.PluginConditionService;
import com.arkflame.flameforge.item.AttributeBridge;
import com.arkflame.flameforge.item.EnchantmentResolver;
import com.arkflame.flameforge.item.ItemFactory;
import com.arkflame.flameforge.item.ItemIdentityService;
import com.arkflame.flameforge.item.ItemMatcher;
import com.arkflame.flameforge.item.ItemMutationService;
import com.arkflame.flameforge.listener.ForgeInteractListener;
import com.arkflame.flameforge.listener.ForgeInventoryListener;
import com.arkflame.flameforge.listener.PlayerLifecycleListener;
import com.arkflame.flameforge.menu.ForgeMenuService;
import com.arkflame.flameforge.persistence.AuditLogService;
import com.arkflame.flameforge.persistence.PendingDeliveryRepository;
import com.arkflame.flameforge.persistence.PlayerStateRepository;
import com.arkflame.flameforge.persistence.StationRepository;
import com.arkflame.flameforge.session.ForgeSessionService;
import com.arkflame.flameforge.station.ForgeStationService;
import com.arkflame.flameforge.text.TextBridge;
import com.arkflame.flameforge.text.TextPlaceholders;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

public final class FlameForgePlugin extends JavaPlugin {

    private volatile boolean enabled;
    private volatile boolean degradedMode;

    private SchedulerBridge schedulerBridge;
    private RuntimeCapabilities runtimeCapabilities;
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

    private FlameForgeCommand command;
    private ForgeInteractListener forgeInteractListener;
    private ForgeInventoryListener forgeInventoryListener;
    private PlayerLifecycleListener playerLifecycleListener;

    @Override
    public void onEnable() {
        enabled = true;
        degradedMode = false;

        saveDefaultConfig();

        schedulerBridge = SchedulerBridgeFactory.getBridge();

        runtimeCapabilities = RuntimeCapabilities.getInstance();
        runtimeCapabilities.initialize();

        textPlaceholders = TextPlaceholders.create();
        textBridge = TextBridge.create(this, textPlaceholders);
        SoundResolver.setLogger(getLogger());

        Path dataPath = getDataFolder().toPath();

        tierRepository = new TierRepository(this);
        configService = new ConfigService(this, schedulerBridge, tierRepository);
        configService.initialLoad();

        playerStateRepository = new PlayerStateRepository(this, schedulerBridge, dataPath);
        playerStateRepository.loadAllBlocking();

        stationRepository = new StationRepository(this, schedulerBridge, dataPath);
        stationRepository.load();

        pendingDeliveryRepository = new PendingDeliveryRepository(this, schedulerBridge, dataPath);
        pendingDeliveryRepository.load();

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
        OutcomeSelector outcomeSelector = new OutcomeSelector(randomSource);

        costService = new CostService(this, economyService);

        forgeStationService = new ForgeStationService(
            this,
            schedulerBridge,
            stationRepository,
            configService
        );

        forgeAnimationService = new ForgeAnimationService(
            this,
            schedulerBridge,
            particleBridge,
            soundResolver,
            textBridge
        );

        forgeMenuService = new ForgeMenuService(
            configService,
            costService,
            outcomeSelector
        );

        forgeSessionService = new ForgeSessionService();

        Map<String, Object> wardConfig = configService.getCurrentSnapshot().getWard("default");
        outcomeExecutor = new OutcomeExecutor(
            this,
            schedulerBridge,
            MaterialResolver.getInstance(),
            textBridge,
            auditLogService,
            deliveryService,
            wardConfig != null ? wardConfig : emptyMap
        );

        command = new FlameForgeCommand(
            this,
            schedulerBridge,
            textBridge,
            configService,
            forgeStationService,
            stationRepository,
            playerStateRepository,
            tierRepository
        );

        forgeInteractListener = new ForgeInteractListener(
            this,
            schedulerBridge,
            forgeStationService,
            forgeMenuService
        );

        forgeInventoryListener = new ForgeInventoryListener(
            this,
            forgeMenuService,
            schedulerBridge,
            playerStateRepository
        );

        playerLifecycleListener = new PlayerLifecycleListener(
            this,
            forgeStationService,
            playerStateRepository,
            deliveryService,
            schedulerBridge
        );

        getServer().getPluginCommand("flameforge").setExecutor(command);
        getServer().getPluginCommand("flameforge").setTabCompleter(command);

        getServer().getPluginManager().registerEvents(forgeInteractListener, this);
        getServer().getPluginManager().registerEvents(forgeInventoryListener, this);
        getServer().getPluginManager().registerEvents(playerLifecycleListener, this);

        deliveryService.processGlobalContext();

        if (configService.hasValidationErrors()) {
            degradedMode = true;
            getLogger().warning("Configuration has validation errors - running in DEGRADED mode");
            getLogger().warning("Some features may not work correctly. Please fix the configuration.");
        }

        int tierCount = tierRepository.size();
        int stationCount = stationRepository.getAllSnapshot().size();
        boolean isFolia = schedulerBridge.isFolia();
        boolean hasEconomy = economyService.available();
        boolean hasSMPWeapons = pluginConditionService.isPluginEnabled("SMPWeapons");

        Component readyMessage = Component.text()
            .append(Component.text("[FlameForge] ", NamedTextColor.GOLD))
            .append(Component.text("v" + getDescription().getVersion() + " ", NamedTextColor.WHITE))
            .append(Component.text("ready", NamedTextColor.GREEN))
            .build();

        Component detailsMessage = Component.text()
            .append(Component.text("  Tiers: ", NamedTextColor.GRAY))
            .append(Component.text(String.valueOf(tierCount), NamedTextColor.WHITE))
            .append(Component.text(" | Stations: ", NamedTextColor.GRAY))
            .append(Component.text(String.valueOf(stationCount), NamedTextColor.WHITE))
            .append(Component.text(" | Folia: ", NamedTextColor.GRAY))
            .append(Component.text(isFolia ? "yes" : "no", NamedTextColor.WHITE))
            .append(Component.text(" | Vault/Economy: ", NamedTextColor.GRAY))
            .append(Component.text(hasEconomy ? "available" : "n/a", NamedTextColor.WHITE))
            .append(Component.text(" | SMPWeapons: ", NamedTextColor.GRAY))
            .append(Component.text(hasSMPWeapons ? "detected" : "n/a", NamedTextColor.WHITE))
            .append(Component.text(" | Mode: ", NamedTextColor.GRAY))
            .append(Component.text(degradedMode ? "DEGRADED" : "normal", degradedMode ? NamedTextColor.YELLOW : NamedTextColor.GREEN))
            .build();

        textBridge.sendAll(readyMessage);
        textBridge.sendAll(detailsMessage);
    }

    @Override
    public void onDisable() {
        enabled = false;

        forgeSessionService.shutdown();

        stationRepository.saveAsync(null);
        pendingDeliveryRepository.saveAsync();

        if (auditLogService != null) {
            auditLogService.flush();
            auditLogService.close();
        }

        schedulerBridge.cancelAll(this);

        if (textBridge != null && !textBridge.isClosed()) {
            textBridge.close();
        }
    }

    public void reload() {
        configService.asyncReloadWithCallback(() -> {
            schedulerBridge.runGlobal(this, () -> {
                refreshRuntimeState();
            });
        });
    }

    private void refreshRuntimeState() {
        int tierCount = tierRepository.size();
        int stationCount = stationRepository.getAllSnapshot().size();
        boolean hasEconomy = economyService.available();
        boolean hasSMPWeapons = pluginConditionService.isPluginEnabled("SMPWeapons");

        Component message = Component.text()
            .append(Component.text("[FlameForge] ", NamedTextColor.GOLD))
            .append(Component.text("Reload complete. ", NamedTextColor.GREEN))
            .append(Component.text("Tiers: " + tierCount, NamedTextColor.WHITE))
            .append(Component.text(" | Stations: " + stationCount, NamedTextColor.WHITE))
            .append(Component.text(" | Economy: " + (hasEconomy ? "yes" : "no"), NamedTextColor.WHITE))
            .append(Component.text(" | SMPWeapons: " + (hasSMPWeapons ? "yes" : "no"), NamedTextColor.WHITE))
            .build();

        textBridge.sendAll(message);
    }
}
