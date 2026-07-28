package com.arkflame.flameforge.station;

import com.arkflame.flameforge.compat.scheduler.SchedulerBridge;
import com.arkflame.flameforge.compat.scheduler.TaskHandle;
import com.arkflame.flameforge.config.ConfigService;
import com.arkflame.flameforge.config.ConfigSnapshot;
import com.arkflame.flameforge.model.StationProfile;
import com.arkflame.flameforge.persistence.StationRepository;
import com.arkflame.flameforge.persistence.StationRepository.StationData;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public final class ForgeStationService {

    public enum StationMode {
        ANY_BEACON,
        REGISTERED_ONLY
    }

    public static final String DEFAULT_PROFILE_ID = "default";
    public static final int SETUP_MAX_DISTANCE = 6;

    private final JavaPlugin plugin;
    private final SchedulerBridge scheduler;
    private final StationRepository stationRepository;
    private final ConfigService configService;
    private final TargetBlockBridge targetBlockBridge;

    private volatile StationMode stationMode;

    public ForgeStationService(JavaPlugin plugin, SchedulerBridge scheduler,
                               StationRepository stationRepository, ConfigService configService) {
        this.plugin = Objects.requireNonNull(plugin);
        this.scheduler = Objects.requireNonNull(scheduler);
        this.stationRepository = Objects.requireNonNull(stationRepository);
        this.configService = Objects.requireNonNull(configService);
        this.targetBlockBridge = TargetBlockBridge.getInstance();
        this.stationMode = StationMode.REGISTERED_ONLY;
    }

    public void setStationMode(StationMode mode) {
        this.stationMode = mode != null ? mode : StationMode.REGISTERED_ONLY;
    }

    public StationMode getStationMode() {
        return stationMode;
    }

    public Optional<StationData> resolveStationFromClick(Player player) {
        if (player == null) {
            return Optional.empty();
        }

        Optional<Block> beaconBlock = targetBlockBridge.findTargetBeacon(player, SETUP_MAX_DISTANCE);
        if (beaconBlock.isEmpty()) {
            return Optional.empty();
        }

        Block block = beaconBlock.get();
        return resolveStationAt(block);
    }

    public Optional<StationData> resolveStationAt(Block block) {
        if (block == null || block.getType() != org.bukkit.Material.BEACON) {
            return Optional.empty();
        }

        Location loc = block.getLocation();
        String world = loc.getWorld().getName();
        double x = loc.getX();
        double y = loc.getY();
        double z = loc.getZ();

        String key = world + "_" + x + "_" + y + "_" + z;
        StationData station = stationRepository.getByKey(key);

        if (station != null) {
            return Optional.of(station);
        }

        if (stationMode == StationMode.ANY_BEACON) {
            return Optional.of(createUnregisteredStationData(world, x, y, z));
        }

        return Optional.empty();
    }

    private StationData createUnregisteredStationData(String world, double x, double y, double z) {
        String key = world + "_" + x + "_" + y + "_" + z;
        return new StationData("unregistered", world, x, y, z, DEFAULT_PROFILE_ID);
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

    public boolean addStation(String id, Block block, String profile) {
        if (id == null || id.isEmpty() || block == null) {
            return false;
        }

        if (block.getType() != org.bukkit.Material.BEACON) {
            return false;
        }

        Location loc = block.getLocation();
        String world = loc.getWorld().getName();
        double x = loc.getX();
        double y = loc.getY();
        double z = loc.getZ();

        String effectiveProfile = profile != null && !profile.isEmpty() ? profile : DEFAULT_PROFILE_ID;

        boolean added = stationRepository.addStation(id, world, x, y, z, effectiveProfile);
        if (added) {
            stationRepository.saveAsync(null);
        }
        return added;
    }

    public boolean removeStation(String id) {
        if (id == null || id.isEmpty()) {
            return false;
        }

        Map<String, StationData> all = stationRepository.getAllSnapshot();
        for (Map.Entry<String, StationData> entry : all.entrySet()) {
            if (entry.getValue().id.equals(id)) {
                stationRepository.remove(entry.getKey());
                stationRepository.saveAsync(null);
                return true;
            }
        }
        return false;
    }

    public boolean removeStationAt(Block block) {
        if (block == null) {
            return false;
        }

        Location loc = block.getLocation();
        String key = loc.getWorld().getName() + "_" + loc.getX() + "_" + loc.getY() + "_" + loc.getZ();

        StationData existing = stationRepository.getByKey(key);
        if (existing == null) {
            return false;
        }

        stationRepository.remove(key);
        stationRepository.saveAsync(null);
        return true;
    }

    public List<StationData> listStations() {
        return new ArrayList<>(stationRepository.getAllSnapshot().values());
    }

    public Optional<StationData> getStationById(String id) {
        if (id == null || id.isEmpty()) {
            return Optional.empty();
        }

        Map<String, StationData> all = stationRepository.getAllSnapshot();
        for (StationData data : all.values()) {
            if (id.equals(data.id)) {
                return Optional.of(data);
            }
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

    public TeleportResult teleportToStation(Player player, StationData station) {
        if (player == null || station == null) {
            return TeleportResult.FAILURE;
        }

        World world = Bukkit.getWorld(station.world);
        if (world == null) {
            return TeleportResult.WORLD_NOT_FOUND;
        }

        Location location = station.toLocation(world);
        if (!world.equals(player.getWorld())) {
            return TeleportResult.WORLD_NOT_LOADED;
        }

        return teleportPlayerToLocation(player, location);
    }

    private TeleportResult teleportPlayerToLocation(Player player, Location location) {
        try {
            if (scheduler.isFolia()) {
                TaskHandle handle = scheduler.runEntity(player, () -> {
                    player.teleport(location);
                }, () -> {});
                if (handle != null) {
                    return TeleportResult.SUCCESS_ASYNC;
                }
            }

            CompletableFuture<Boolean> future = new CompletableFuture<>();
            scheduler.runGlobal(plugin, () -> {
                player.teleport(location);
                future.complete(true);
            });

            return TeleportResult.SUCCESS_SYNC;

        } catch (Exception e) {
            plugin.getLogger().warning("Teleport failed: " + e.getMessage());
            return TeleportResult.FAILURE;
        }
    }

    public boolean validateBeaconSetup(Player player, Block block) {
        if (player == null || block == null) {
            return false;
        }

        if (block.getType() != org.bukkit.Material.BEACON) {
            return false;
        }

        Location beaconLoc = block.getLocation();
        Location playerLoc = player.getLocation();

        if (!beaconLoc.getWorld().equals(playerLoc.getWorld())) {
            return false;
        }

        double distance = playerLoc.distance(beaconLoc);
        return distance <= SETUP_MAX_DISTANCE;
    }

    public boolean isDuplicateId(String id) {
        if (id == null || id.isEmpty()) {
            return true;
        }

        Map<String, StationData> all = stationRepository.getAllSnapshot();
        for (StationData data : all.values()) {
            if (id.equals(data.id)) {
                return true;
            }
        }
        return false;
    }

    public boolean isDuplicateCoordinate(String world, double x, double y, double z) {
        if (world == null) {
            return false;
        }

        Map<String, StationData> all = stationRepository.getAllSnapshot();
        for (StationData data : all.values()) {
            if (data.world.equals(world) && data.x == x && data.y == y && data.z == z) {
                return true;
            }
        }
        return false;
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

    public enum TeleportResult {
        SUCCESS_SYNC,
        SUCCESS_ASYNC,
        WORLD_NOT_FOUND,
        WORLD_NOT_LOADED,
        FAILURE
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

        public double getX() {
            return stationData != null ? stationData.x : 0;
        }

        public double getY() {
            return stationData != null ? stationData.y : 0;
        }

        public double getZ() {
            return stationData != null ? stationData.z : 0;
        }

        public String getProfileId() {
            return stationData != null ? stationData.profile : null;
        }
    }
}
