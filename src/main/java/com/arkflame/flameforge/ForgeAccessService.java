package com.arkflame.flameforge;

import com.arkflame.flameforge.compat.effect.ParticleBridge;
import com.arkflame.flameforge.compat.scheduler.SchedulerBridge;
import com.arkflame.flameforge.config.ConfigService;
import com.arkflame.flameforge.forge.ForgeService;
import com.arkflame.flameforge.menu.ForgeMenuService;
import com.arkflame.flameforge.model.PlayerForgeState;
import com.arkflame.flameforge.persistence.PlayerStateRepository;
import com.arkflame.flameforge.persistence.StationRepository;
import com.arkflame.flameforge.session.ForgeSessionService;
import com.arkflame.flameforge.station.ForgeStationService;
import com.arkflame.flameforge.station.TargetBlockBridge;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Collections;
import java.util.logging.Level;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;

public final class ForgeAccessService {

    public enum OpenStatus {
        OPENED,
        PLAYER_OFFLINE,
        FORGE_NOT_FOUND,
        PROFILE_NOT_FOUND,
        PERMISSION_REQUIRED,
        NO_ALLOWED_TIER,
        SCHEDULER_REJECTED,
        MENU_OPEN_FAILED,
        PLAYER_RETIRED
    }

    public static final class OpenResult {
        private final OpenStatus status;
        private final String stationId;
        private final List<String> requiredPermissions;
        private final String reason;
        private final String reference;

        private OpenResult(OpenStatus status, String stationId, List<String> requiredPermissions, String reason, String reference) {
            this.status = status;
            this.stationId = stationId;
            this.requiredPermissions = requiredPermissions != null ? Collections.unmodifiableList(requiredPermissions) : Collections.emptyList();
            this.reason = reason;
            this.reference = reference;
        }

        public OpenStatus getStatus() { return status; }
        public String getStationId() { return stationId; }
        public List<String> getRequiredPermissions() { return requiredPermissions; }
        public String getReason() { return reason; }
        public String getReference() { return reference; }

        public static OpenResult opened(String stationId) {
            return new OpenResult(OpenStatus.OPENED, stationId, null, null, null);
        }

        public static OpenResult playerOffline() {
            return new OpenResult(OpenStatus.PLAYER_OFFLINE, null, null, null, null);
        }

        public static OpenResult forgeNotFound(String stationId) {
            return new OpenResult(OpenStatus.FORGE_NOT_FOUND, stationId, null, null, null);
        }

        public static OpenResult profileNotFound(String stationId) {
            return new OpenResult(OpenStatus.PROFILE_NOT_FOUND, stationId, null, null, null);
        }

        public static OpenResult permissionRequired(String stationId, List<String> requiredPermissions) {
            return new OpenResult(OpenStatus.PERMISSION_REQUIRED, stationId, requiredPermissions, null, null);
        }

        public static OpenResult noAllowedTier(String stationId) {
            return new OpenResult(OpenStatus.NO_ALLOWED_TIER, stationId, null, null, null);
        }

        public static OpenResult schedulerRejected(String stationId, String reason, String reference) {
            return new OpenResult(OpenStatus.SCHEDULER_REJECTED, stationId, null, reason, reference);
        }

        public static OpenResult menuOpenFailed(String stationId, String reason, String reference) {
            return new OpenResult(OpenStatus.MENU_OPEN_FAILED, stationId, null, reason, reference);
        }

        public static OpenResult playerRetired() {
            return new OpenResult(OpenStatus.PLAYER_RETIRED, null, null, null, null);
        }
    }

    private final JavaPlugin plugin;
    private final SchedulerBridge scheduler;
    private final ForgeStationService stationService;
    private final StationRepository stationRepository;
    private final ForgeSessionService sessionService;
    private final PlayerStateRepository playerStateRepository;
    private final ForgeMenuService menuService;
    private final ConfigService configService;
    private final ForgeService forgeService;
    private final ParticleBridge particleBridge;
    private final TargetBlockBridge targetBlockBridge;
    private final AtomicLong openFailureSequence = new AtomicLong();

    public ForgeAccessService(JavaPlugin plugin, SchedulerBridge scheduler,
                            ForgeStationService stationService, StationRepository stationRepository,
                            ForgeSessionService sessionService, PlayerStateRepository playerStateRepository,
                            ForgeMenuService menuService, ConfigService configService,
                            ForgeService forgeService) {
        this.plugin = Objects.requireNonNull(plugin);
        this.scheduler = Objects.requireNonNull(scheduler);
        this.stationService = Objects.requireNonNull(stationService);
        this.stationRepository = Objects.requireNonNull(stationRepository);
        this.sessionService = Objects.requireNonNull(sessionService);
        this.playerStateRepository = Objects.requireNonNull(playerStateRepository);
        this.menuService = Objects.requireNonNull(menuService);
        this.configService = Objects.requireNonNull(configService);
        this.forgeService = Objects.requireNonNull(forgeService);
        this.particleBridge = ParticleBridge.getInstance();
        this.targetBlockBridge = new TargetBlockBridge(plugin, scheduler);
    }

    public ForgeStationService getStationService() {
        return stationService;
    }

    public ForgeService getForgeService() {
        return forgeService;
    }

    public ParticleBridge getParticleBridge() {
        return particleBridge;
    }

    public TargetBlockBridge getTargetBlockBridge() {
        return targetBlockBridge;
    }

    public CompletableFuture<OpenResult> openForgeFromBlock(Player player, Block clickedBlock) {
        Objects.requireNonNull(player, "player cannot be null");
        Objects.requireNonNull(clickedBlock, "clickedBlock cannot be null");

        if (!player.isOnline()) {
            return CompletableFuture.completedFuture(OpenResult.playerOffline());
        }

        Location loc = clickedBlock.getLocation();
        Optional<StationRepository.StationData> stationOpt = stationService.resolveStationAt(
            loc.getWorld().getName(), loc.getBlockX(), loc.getBlockY(), loc.getBlockZ()
        );

        if (!stationOpt.isPresent()) {
            return CompletableFuture.completedFuture(OpenResult.forgeNotFound(null));
        }

        StationRepository.StationData station = stationOpt.get();
        return openForgeForStation(player, station.id);
    }

    public CompletableFuture<OpenResult> openForgeFromId(Player player, String forgeId) {
        Objects.requireNonNull(player, "player cannot be null");
        if (forgeId == null || forgeId.isEmpty()) {
            return CompletableFuture.completedFuture(OpenResult.forgeNotFound(null));
        }

        if (!player.isOnline()) {
            return CompletableFuture.completedFuture(OpenResult.playerOffline());
        }

        Optional<StationRepository.RegisteredForge> forgeOpt = stationRepository.findById(forgeId);
        if (!forgeOpt.isPresent()) {
            return CompletableFuture.completedFuture(OpenResult.forgeNotFound(forgeId));
        }

        return openForgeForStation(player, forgeId);
    }

    private CompletableFuture<OpenResult> openForgeForStation(Player player, String stationId) {
        if (!player.isOnline()) {
            return CompletableFuture.completedFuture(OpenResult.playerOffline());
        }

        Optional<StationRepository.StationData> stationDataOpt = stationService.getStationById(stationId);
        if (!stationDataOpt.isPresent()) {
            return CompletableFuture.completedFuture(OpenResult.forgeNotFound(stationId));
        }

        StationRepository.StationData stationData = stationDataOpt.get();

        Optional<com.arkflame.flameforge.model.StationProfile> profileOpt =
            stationService.resolveProfile(stationData);
        if (!profileOpt.isPresent()) {
            return CompletableFuture.completedFuture(OpenResult.profileNotFound(stationId));
        }

        com.arkflame.flameforge.model.StationProfile profile = profileOpt.get();

        List<String> requiredPermissions = stationService.getRequiredPermissions(stationData, profile);
        if (!stationService.hasPermission(player, stationData, profile)) {
            return CompletableFuture.completedFuture(OpenResult.permissionRequired(stationId, requiredPermissions));
        }

        int firstEnabledTier = stationService.getFirstAllowedTier(profile);
        if (!stationService.isTierAllowed(profile, firstEnabledTier)) {
            return CompletableFuture.completedFuture(OpenResult.noAllowedTier(stationId));
        }

        String playerId = player.getUniqueId().toString();
        sessionService.openSession(playerId);

        PlayerForgeState playerState = loadOrCreatePlayerState(player);
        PlayerForgeState stateWithStation = playerState.withActiveStation(stationId, firstEnabledTier);

        persistPlayerStateMerge(player.getUniqueId(), stateWithStation);

        return openMenuOnEntityScheduler(player, stateWithStation, stationId);
    }

    private PlayerForgeState loadOrCreatePlayerState(Player player) {
        PlayerStateRepository.PlayerState repoState = playerStateRepository.getOrLoad(player.getUniqueId());
        return PlayerForgeState.of(
            player.getUniqueId().toString(),
            com.arkflame.flameforge.model.ForgeSessionState.OPEN,
            null,
            repoState != null ? repoState.tier : 0,
            Collections.emptyMap(),
            Collections.emptyMap()
        );
    }

    private void persistPlayerStateMerge(java.util.UUID uuid, PlayerForgeState state) {
        playerStateRepository.updateAndSave(uuid, existing -> {
            int tier = existing != null ? existing.tier : 0;
            long pityCooldown = existing != null ? existing.pityCooldown : 0L;
            return new PlayerStateRepository.PlayerState(uuid, tier, pityCooldown);
        });
    }

    private CompletableFuture<OpenResult> openMenuOnEntityScheduler(Player player, PlayerForgeState session, String stationId) {
        CompletableFuture<OpenResult> future = new CompletableFuture<>();
        String menuFailureRef = "FF-MENU-OPEN-" + openFailureSequence.incrementAndGet();

        try {
            scheduler.runEntity(player, () -> {
                try {
                    if (!player.isOnline()) {
                        future.complete(OpenResult.playerOffline());
                        return;
                    }
                    ForgeMenuService.MenuResult menuResult = menuService.open(player, session);
                    if (!menuResult.isOpened()) {
                        String reason = menuResult.getReason() != null ? menuResult.getReason() : "render failed";
                        String ref = menuResult.getReference() != null ? menuResult.getReference() : menuFailureRef;
                        plugin.getLogger().warning("Forge menu open failed for player " + player.getUniqueId() + ": " + reason);
                        future.complete(OpenResult.menuOpenFailed(stationId, reason, ref));
                        return;
                    }
                    future.complete(OpenResult.opened(stationId));
                } catch (Exception e) {
                    String reason = e.getClass().getSimpleName() + (e.getMessage() != null ? ": " + e.getMessage() : "");
                    plugin.getLogger().severe("Forge menu open failed:\nplayer=" + player.getUniqueId() + "\nstation=" + stationId + "\nreference=" + menuFailureRef);
                    plugin.getLogger().log(Level.SEVERE, "Forge menu open failed", e);
                    future.complete(OpenResult.menuOpenFailed(stationId, reason, menuFailureRef));
                }
            }, () -> {
                future.complete(OpenResult.playerRetired());
            });
        } catch (Exception e) {
            String schedulerFailureRef = "FF-MENU-SCHEDULER-" + openFailureSequence.incrementAndGet();
            String reason = e.getClass().getSimpleName() + (e.getMessage() != null ? ": " + e.getMessage() : "");
            plugin.getLogger().severe("Forge menu scheduler rejected:\nplayer=" + player.getUniqueId() + "\nstation=" + stationId + "\nreference=" + schedulerFailureRef);
            plugin.getLogger().log(Level.SEVERE, "Forge menu scheduler rejected", e);
            future.complete(OpenResult.schedulerRejected(stationId, reason, schedulerFailureRef));
        }

        return future;
    }
}
