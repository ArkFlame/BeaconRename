package com.arkflame.flameforge.station;

import com.arkflame.flameforge.compat.scheduler.SchedulerBridge;
import com.arkflame.flameforge.compat.scheduler.TeleportBridge;
import com.arkflame.flameforge.config.ConfigService;
import com.arkflame.flameforge.config.ConfigSnapshot;
import com.arkflame.flameforge.hologram.ForgeStationHologramService;
import com.arkflame.flameforge.hologram.HologramProvider;
import com.arkflame.flameforge.hologram.HologramProviderFactory;
import com.arkflame.flameforge.hologram.HologramProviderSelector;
import com.arkflame.flameforge.hologram.HologramSettings;
import com.arkflame.flameforge.model.StationProfile;
import com.arkflame.flameforge.persistence.StationRepository;
import com.arkflame.flameforge.persistence.StationRepository.RegisteredForge;
import com.arkflame.flameforge.persistence.StationRepository.StationData;
import com.arkflame.flameforge.text.TextRenderer;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class ForgeStationService {

    public static final String DEFAULT_PROFILE_ID = "default";
    public static final int SETUP_MAX_DISTANCE = 6;
    private static final int MAX_ID_GENERATION_ATTEMPTS = 8;

    private final JavaPlugin plugin;
    private final SchedulerBridge scheduler;
    private final StationRepository stationRepository;
    private final ConfigService configService;
    private final TargetBlockBridge targetBlockBridge;
    private final ForgeStationHologramService hologramService;
    private final TextRenderer textRenderer;
    private final TeleportBridge teleportBridge;

    public ForgeStationService(JavaPlugin plugin, SchedulerBridge scheduler,
                               StationRepository stationRepository, ConfigService configService,
                               TextRenderer textRenderer, TeleportBridge teleportBridge) {
        this.plugin = Objects.requireNonNull(plugin);
        this.scheduler = Objects.requireNonNull(scheduler);
        this.stationRepository = Objects.requireNonNull(stationRepository);
        this.configService = Objects.requireNonNull(configService);
        this.textRenderer = Objects.requireNonNull(textRenderer);
        this.targetBlockBridge = new TargetBlockBridge(plugin, scheduler);
        this.teleportBridge = Objects.requireNonNull(teleportBridge);

        ConfigSnapshot snapshot = configService.getCurrentSnapshot();
        HologramSettings hologramSettings = HologramSettings.fromSnapshot(snapshot);
        HologramProviderSelector providerSelector = new HologramProviderSelector(
            plugin, plugin.getServer().getPluginManager(), new HologramProviderFactory.Default(), plugin.getLogger());
        HologramProvider hologramProvider = providerSelector.select(hologramSettings);

        this.hologramService = new ForgeStationHologramService(
            plugin, scheduler, stationRepository, configService, textRenderer,
            providerSelector, hologramProvider, hologramSettings);
    }

    ForgeStationService(JavaPlugin plugin, SchedulerBridge scheduler,
                        StationRepository stationRepository, ConfigService configService,
                        ForgeStationHologramService hologramService, TeleportBridge teleportBridge) {
        this.plugin = Objects.requireNonNull(plugin);
        this.scheduler = Objects.requireNonNull(scheduler);
        this.stationRepository = Objects.requireNonNull(stationRepository);
        this.configService = Objects.requireNonNull(configService);
        this.targetBlockBridge = new TargetBlockBridge(plugin, scheduler);
        this.hologramService = Objects.requireNonNull(hologramService);
        this.textRenderer = null;
        this.teleportBridge = Objects.requireNonNull(teleportBridge);
    }

    public ForgeStationHologramService getHologramService() {
        return hologramService;
    }

    public StationRepository getStationRepository() {
        return stationRepository;
    }

    public CompletableFuture<Optional<StationData>> resolveRegisteredForgeFromTarget(Player player) {
        if (player == null) {
            return CompletableFuture.completedFuture(Optional.empty());
        }

        return targetBlockBridge.findTargetBlock(player, SETUP_MAX_DISTANCE)
            .thenApply(result -> {
                if (!result.isFound()) {
                    return Optional.<StationData>empty();
                }
                TargetBlockSnapshot snapshot = result.snapshot();
                return resolveStationAt(snapshot.getWorldName(),
                        snapshot.getBlockX(), snapshot.getBlockY(), snapshot.getBlockZ());
            });
    }

    public Optional<StationData> resolveStationAt(String worldName, int x, int y, int z) {
        if (worldName == null) {
            return Optional.empty();
        }

        Optional<RegisteredForge> forge = stationRepository.findByKey(worldName, x, y, z);
        if (forge.isPresent()) {
            RegisteredForge f = forge.get();
            return Optional.of(new StationData(f.getId(), f.getWorldName(), f.getX(), f.getY(), f.getZ(), f.getProfileId()));
        }
        return Optional.empty();
    }

    public Optional<StationData> resolveStationAt(Block block) {
        if (block == null || block.getType() == org.bukkit.Material.AIR) {
            return Optional.empty();
        }

        Location loc = block.getLocation();
        return resolveStationAt(loc.getWorld().getName(), (int) Math.floor(loc.getX()), (int) Math.floor(loc.getY()), (int) Math.floor(loc.getZ()));
    }

    public CompletableFuture<AddForgeOutcome> addTargetedForge(Player player, Optional<String> requestedId, String profileId) {
        Objects.requireNonNull(player, "player cannot be null");

        if (!player.isOnline()) {
            return CompletableFuture.completedFuture(AddForgeOutcome.playerRetired());
        }

        return targetBlockBridge.findTargetBlock(player, SETUP_MAX_DISTANCE)
            .thenCompose(result -> {
                if (result.status() == TargetBlockResult.Status.UNAVAILABLE) {
                    return CompletableFuture.completedFuture(AddForgeOutcome.targetUnavailable(null));
                }
                if (result.status() == TargetBlockResult.Status.NO_TARGET) {
                    return CompletableFuture.completedFuture(AddForgeOutcome.noTarget(null));
                }
                if (result.status() == TargetBlockResult.Status.PLAYER_RETIRED) {
                    return CompletableFuture.completedFuture(AddForgeOutcome.playerRetired());
                }

                String effectiveProfile = (profileId != null && !profileId.isEmpty()) ? profileId : DEFAULT_PROFILE_ID;
                Optional<StationProfile> profileOpt = resolveProfileById(effectiveProfile);
                if (!profileOpt.isPresent()) {
                    return CompletableFuture.completedFuture(AddForgeOutcome.unknownProfile(null));
                }

                String explicitId = null;
                if (requestedId.isPresent() && !StationIdPolicy.isAutoToken(requestedId.get())) {
                    explicitId = StationIdPolicy.normalize(requestedId.get());
                    if (!StationIdPolicy.isValidExplicit(explicitId)) {
                        return CompletableFuture.completedFuture(AddForgeOutcome.invalidId(null));
                    }
                }

                TargetBlockSnapshot snapshot = result.snapshot();
                UUID worldUuid = UUID.fromString(snapshot.getWorldUuid());
                String worldName = snapshot.getWorldName();
                int x = snapshot.getBlockX();
                int y = snapshot.getBlockY();
                int z = snapshot.getBlockZ();

                Optional<RegisteredForge> existing = stationRepository.findByKey(worldName, x, y, z);
                if (existing.isPresent()) {
                    return CompletableFuture.completedFuture(AddForgeOutcome.duplicateLocation(explicitId));
                }

                if (requestedId.isPresent()) {
                    String rawId = requestedId.get();
                    if (StationIdPolicy.isAutoToken(rawId)) {
                        return persistGeneratedForge(snapshot, effectiveProfile, 0);
                    } else {
                        RegisteredForge forge = new RegisteredForge(explicitId, worldUuid, worldName, x, y, z,
                                effectiveProfile);
                        return persistExplicitForge(forge, explicitId);
                    }
                } else {
                    return persistGeneratedForge(snapshot, effectiveProfile, 0);
                }
            });
    }

    private CompletableFuture<AddForgeOutcome> persistExplicitForge(RegisteredForge forge, String id) {
        return stationRepository.addAndSave(forge).thenApply(outcome -> {
            switch (outcome.getResult()) {
                case ADDED:
                    RegisteredForge addedForge = outcome.getAddedForge();
                    hologramService.onStationAdded(addedForge);
                    return AddForgeOutcome.added(addedForge.getId(), addedForge);
                case DUPLICATE_ID:
                    return AddForgeOutcome.duplicateId(id);
                case DUPLICATE_LOCATION:
                    return AddForgeOutcome.duplicateLocation(id);
                case PERSISTENCE_FAILED:
                default:
                    return AddForgeOutcome.persistenceFailed(id);
            }
        });
    }

    private CompletableFuture<AddForgeOutcome> persistGeneratedForge(TargetBlockSnapshot target,
                                                                       String profile,
                                                                       int attempt) {
        if (attempt >= MAX_ID_GENERATION_ATTEMPTS) {
            return CompletableFuture.completedFuture(AddForgeOutcome.idGenerationExhausted(null));
        }

        String candidate = StationIdPolicy.generateCandidate();
        RegisteredForge forge = new RegisteredForge(candidate,
                UUID.fromString(target.getWorldUuid()), target.getWorldName(),
                target.getBlockX(), target.getBlockY(), target.getBlockZ(), profile);

        return stationRepository.addAndSave(forge).thenCompose(outcome -> {
            switch (outcome.getResult()) {
                case ADDED:
                    RegisteredForge addedForge = outcome.getAddedForge();
                    hologramService.onStationAdded(addedForge);
                    return CompletableFuture.completedFuture(
                            AddForgeOutcome.added(addedForge.getId(), addedForge));
                case DUPLICATE_ID:
                    return persistGeneratedForge(target, profile, attempt + 1);
                case DUPLICATE_LOCATION:
                    return CompletableFuture.completedFuture(AddForgeOutcome.duplicateLocation(candidate));
                case PERSISTENCE_FAILED:
                default:
                    return CompletableFuture.completedFuture(AddForgeOutcome.persistenceFailed(candidate));
            }
        });
    }

    private String generateUniqueId() {
        return generateUniqueIdWithRetry(0);
    }

    String generateUniqueIdForTest(int attempt) {
        return generateUniqueIdWithRetry(attempt);
    }

    private String generateUniqueIdWithRetry(int attempt) {
        for (int i = attempt; i < MAX_ID_GENERATION_ATTEMPTS; i++) {
            String candidate = StationIdPolicy.generateCandidate();
            if (!stationRepository.findById(candidate).isPresent()) {
                return candidate;
            }
        }
        return null;
    }

    public Optional<StationProfile> resolveProfile(StationData station) {
        if (station == null) {
            return Optional.empty();
        }
        return resolveProfileById(station.profile);
    }

    public Optional<StationProfile> resolveProfileById(String profileId) {
        if (profileId == null) {
            profileId = DEFAULT_PROFILE_ID;
        }

        ConfigSnapshot snapshot = configService.getCurrentSnapshot();
        Map<String, Object> profileData = snapshot.getStationProfile(profileId);

        if (profileData == null) {
            if (DEFAULT_PROFILE_ID.equals(profileId)) {
                return Optional.of(StationProfile.of(DEFAULT_PROFILE_ID, "", -1, Collections.emptyList()));
            }
            return Optional.empty();
        }

        return Optional.of(buildStationProfile(profileId, profileData));
    }

    private StationProfile buildStationProfile(String profileId, Map<String, Object> data) {
        String stationId = getString(data, "station-id", profileId);
        int maxTier = getInt(data, "max-tier", -1);

        @SuppressWarnings("unchecked")
        List<String> permissions = (List<String>) data.getOrDefault("permissions", Collections.emptyList());

        return StationProfile.of(profileId, stationId, maxTier, permissions);
    }

    public List<String> getRequiredPermissions(StationData station, StationProfile profile) {
        List<String> permissions = new ArrayList<>();
        permissions.add("flameforge.station.use." + station.id);
        if (profile != null) {
            permissions.addAll(profile.getRequiredPermissions());
        }
        return permissions;
    }

    public boolean hasPermission(Player player, StationData station, StationProfile profile) {
        if (player == null || station == null) {
            return false;
        }

        List<String> requiredPermissions = profile != null ?
                profile.getRequiredPermissions() : Collections.emptyList();

        if (requiredPermissions.isEmpty()) {
            return true;
        }

        for (String permission : requiredPermissions) {
            if (!player.hasPermission(permission)) {
                return false;
            }
        }
        return true;
    }

    public boolean isTierAllowed(StationProfile profile, int tierLevel) {
        if (profile == null) {
            return true;
        }

        int maxTier = profile.getMaxTierUnlocked();
        if (maxTier < 0) {
            return true;
        }

        return tierLevel <= maxTier;
    }

    public int getFirstAllowedTier(StationProfile profile) {
        if (profile == null) {
            ConfigSnapshot snapshot = configService.getCurrentSnapshot();
            List<com.arkflame.flameforge.model.TierDefinition> tiers = snapshot.getTiers();
            if (tiers == null || tiers.isEmpty()) {
                return 1;
            }
            List<com.arkflame.flameforge.model.TierDefinition> sorted = new ArrayList<>(tiers);
            Collections.sort(sorted, (a, b) -> Integer.compare(a.getTierLevel(), b.getTierLevel()));
            for (com.arkflame.flameforge.model.TierDefinition tier : sorted) {
                if (isTierAllowed(profile, tier.getTierLevel())) {
                    return tier.getTierLevel();
                }
            }
            return sorted.get(0).getTierLevel();
        }

        ConfigSnapshot snapshot = configService.getCurrentSnapshot();
        List<com.arkflame.flameforge.model.TierDefinition> tiers = snapshot.getTiers();
        if (tiers == null || tiers.isEmpty()) {
            return 1;
        }
        List<com.arkflame.flameforge.model.TierDefinition> sorted = new ArrayList<>(tiers);
        Collections.sort(sorted, (a, b) -> Integer.compare(a.getTierLevel(), b.getTierLevel()));
        for (com.arkflame.flameforge.model.TierDefinition tier : sorted) {
            if (isTierAllowed(profile, tier.getTierLevel())) {
                return tier.getTierLevel();
            }
        }
        return 1;
    }

    public CompletableFuture<StationRepository.RemoveOutcome> removeStation(String id) {
        if (id == null || id.isEmpty()) {
            return CompletableFuture.completedFuture(StationRepository.RemoveOutcome.notFound(null));
        }

        Optional<RegisteredForge> existing = stationRepository.findById(id);
        if (!existing.isPresent()) {
            return CompletableFuture.completedFuture(StationRepository.RemoveOutcome.notFound(null));
        }

        RegisteredForge forge = existing.get();
        String normalizedId = StationIdPolicy.normalize(id);
        return stationRepository.removeAndSave(normalizedId)
            .thenApply(outcome -> {
                if (outcome.getResult() == StationRepository.Result.REMOVED) {
                    hologramService.onStationRemoved(forge);
                }
                return outcome;
            });
    }

    public List<StationData> listStations() {
        List<RegisteredForge> snapshot = stationRepository.snapshotSortedById();
        List<StationData> result = new ArrayList<>();
        for (RegisteredForge f : snapshot) {
            result.add(new StationData(f.getId(), f.getWorldName(), f.getX(), f.getY(), f.getZ(), f.getProfileId()));
        }
        return result;
    }

    public Optional<StationData> getStationById(String id) {
        if (id == null || id.isEmpty()) {
            return Optional.empty();
        }

        Optional<RegisteredForge> forge = stationRepository.findById(id);
        if (forge.isPresent()) {
            RegisteredForge f = forge.get();
            return Optional.of(new StationData(f.getId(), f.getWorldName(), f.getX(), f.getY(), f.getZ(), f.getProfileId()));
        }
        return Optional.empty();
    }

    public Optional<StationInfo> getStationInfo(String id) {
        return getStationById(id).map(this::buildStationInfo);
    }

    private StationInfo buildStationInfo(StationData data) {
        Optional<StationProfile> profile = resolveProfileById(data.profile);
        return new StationInfo(data, profile.orElse(null));
    }

    public int getAnnouncementRadius(StationProfile profile) {
        if (profile == null) {
            return 0;
        }

        ConfigSnapshot snapshot = configService.getCurrentSnapshot();
        Map<String, Object> profileData = snapshot.getStationProfile(profile.getId());

        if (profileData == null) {
            return 0;
        }

        return getInt(profileData, "announcement-radius", 0);
    }

    public Optional<String> getMenuProfile(StationProfile profile) {
        if (profile == null) {
            return Optional.empty();
        }

        ConfigSnapshot snapshot = configService.getCurrentSnapshot();
        Map<String, Object> profileData = snapshot.getStationProfile(profile.getId());

        if (profileData == null) {
            return Optional.empty();
        }

        String menuProfile = getString(profileData, "menu", null);
        return Optional.ofNullable(menuProfile);
    }

    public Optional<String> getAnimationProfile(StationProfile profile) {
        if (profile == null) {
            return Optional.empty();
        }

        ConfigSnapshot snapshot = configService.getCurrentSnapshot();
        Map<String, Object> profileData = snapshot.getStationProfile(profile.getId());

        if (profileData == null) {
            return Optional.empty();
        }

        String animProfile = getString(profileData, "animation", null);
        return Optional.ofNullable(animProfile);
    }

    public boolean isDuplicateId(String id) {
        if (id == null || id.isEmpty()) {
            return true;
        }
        return stationRepository.findById(id).isPresent();
    }

    public boolean isDuplicateCoordinate(String world, double x, double y, double z) {
        if (world == null) {
            return false;
        }
        return stationRepository.findByKey(world, (int) Math.floor(x), (int) Math.floor(y), (int) Math.floor(z)).isPresent();
    }

    private String getString(Map<String, Object> data, String key, String def) {
        Object value = data.get(key);
        return value instanceof String ? (String) value : def;
    }

    private int getInt(Map<String, Object> data, String key, int def) {
        Object value = data.get(key);
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        return def;
    }

    public void reconcileHolograms() {
        hologramService.reconcileStartup();
    }

    public void reloadHolograms() {
        hologramService.reload();
    }

    public void cleanupHolograms() {
        hologramService.disableCleanup();
    }

    public enum Result {
        ADDED,
        INVALID_ID,
        UNKNOWN_PROFILE,
        NO_TARGET,
        TARGET_UNAVAILABLE,
        DUPLICATE_ID,
        DUPLICATE_LOCATION,
        PERSISTENCE_FAILED,
        ID_GENERATION_EXHAUSTED,
        PLAYER_RETIRED
    }

    public static final class AddForgeOutcome {
        private final Result result;
        private final String finalId;
        private final RegisteredForge forge;

        private AddForgeOutcome(Result result, String finalId, RegisteredForge forge) {
            this.result = result;
            this.finalId = finalId;
            this.forge = forge;
        }

        public static AddForgeOutcome added(String finalId, RegisteredForge forge) {
            return new AddForgeOutcome(Result.ADDED, finalId, forge);
        }

        public static AddForgeOutcome invalidId(String finalId) {
            return new AddForgeOutcome(Result.INVALID_ID, finalId, null);
        }

        public static AddForgeOutcome unknownProfile(String finalId) {
            return new AddForgeOutcome(Result.UNKNOWN_PROFILE, finalId, null);
        }

        public static AddForgeOutcome noTarget(String finalId) {
            return new AddForgeOutcome(Result.NO_TARGET, finalId, null);
        }

        public static AddForgeOutcome targetUnavailable(String finalId) {
            return new AddForgeOutcome(Result.TARGET_UNAVAILABLE, finalId, null);
        }

        public static AddForgeOutcome duplicateId(String finalId) {
            return new AddForgeOutcome(Result.DUPLICATE_ID, finalId, null);
        }

        public static AddForgeOutcome duplicateLocation(String finalId) {
            return new AddForgeOutcome(Result.DUPLICATE_LOCATION, finalId, null);
        }

        public static AddForgeOutcome persistenceFailed(String finalId) {
            return new AddForgeOutcome(Result.PERSISTENCE_FAILED, finalId, null);
        }

        public static AddForgeOutcome idGenerationExhausted(String finalId) {
            return new AddForgeOutcome(Result.ID_GENERATION_EXHAUSTED, finalId, null);
        }

        public static AddForgeOutcome playerRetired() {
            return new AddForgeOutcome(Result.PLAYER_RETIRED, null, null);
        }

        public Result result() {
            return result;
        }

        public String finalId() {
            return finalId;
        }

        public RegisteredForge forge() {
            return forge;
        }
    }

    public static final class StationInfo {
        private final StationData stationData;
        private final StationProfile profile;

        public StationInfo(StationData stationData, StationProfile profile) {
            this.stationData = stationData;
            this.profile = profile;
        }

        public StationData getStationData() {
            return stationData;
        }

        public StationProfile getProfile() {
            return profile;
        }

        public String getId() {
            return stationData != null ? stationData.id : null;
        }

        public String getWorld() {
            return stationData != null ? stationData.world : null;
        }

        public int getX() {
            return stationData != null ? stationData.x : 0;
        }

        public int getY() {
            return stationData != null ? stationData.y : 0;
        }

        public int getZ() {
            return stationData != null ? stationData.z : 0;
        }

        public String getProfileId() {
            return stationData != null ? stationData.profile : null;
        }
    }

    public CompletableFuture<TeleportBridge.TeleportOutcome> teleportToStation(Player player, StationData station) {
        if (player == null || station == null) {
            return CompletableFuture.completedFuture(
                TeleportBridge.TeleportOutcome.teleportException("player or station is null", null));
        }

        org.bukkit.World world = plugin.getServer().getWorld(station.world);
        if (world == null) {
            return CompletableFuture.completedFuture(TeleportBridge.TeleportOutcome.worldNotFound());
        }

        if (!world.isChunkLoaded(station.x >> 4, station.z >> 4)) {
            return CompletableFuture.completedFuture(TeleportBridge.TeleportOutcome.worldNotLoaded());
        }

        Location location = new Location(world, station.x + 0.5, station.y, station.z + 0.5);
        return teleportBridge.teleportAsync(player, location);
    }
}
