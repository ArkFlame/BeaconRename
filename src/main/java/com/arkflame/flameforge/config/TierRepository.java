package com.arkflame.flameforge.config;

import com.arkflame.flameforge.model.TierDefinition;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.*;

public class TierRepository {
    private static final String TIER_PREFIX = "tier";
    private static final String TIER_EXTENSION = ".yml";

    private final JavaPlugin plugin;
    private final File tiersDirectory;
    private final File dataFolder;
    private boolean directoryExistedBeforeStartup;

    private final Map<String, TierDefinition> tiersById = new LinkedHashMap<>();
    private final NavigableMap<Integer, TierDefinition> tiersByLevel = new TreeMap<>();

    public TierRepository(JavaPlugin plugin) {
        this.plugin = plugin;
        this.dataFolder = plugin.getDataFolder();
        this.tiersDirectory = new File(dataFolder, "tiers");
    }

    public void bootstrapDefaultsIfDirectoryAbsent() {
        this.directoryExistedBeforeStartup = tiersDirectory.exists();

        if (directoryExistedBeforeStartup) {
            return;
        }

        if (!tiersDirectory.mkdirs()) {
            return;
        }

        for (int i = 1; i <= 7; i++) {
            String resourceName = TIER_PREFIX + i + TIER_EXTENSION;
            copyBundledTier(resourceName);
        }
    }

    private void copyBundledTier(String resourceName) {
        InputStream stream = plugin.getResource("tiers/" + resourceName);
        if (stream == null) {
            return;
        }

        File destFile = new File(tiersDirectory, resourceName);
        if (destFile.exists()) {
            return;
        }

        try (InputStream in = stream) {
            Files.copy(in, destFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to copy bundled tier: " + resourceName + " - " + e.getMessage());
        }
    }

    public ValidationReport load() {
        ValidationReport report = new ValidationReport();
        Map<String, TierDefinition> loadedTiers = new LinkedHashMap<>();
        NavigableMap<Integer, TierDefinition> loadedByLevel = new TreeMap<>();

        if (!tiersDirectory.exists() || !tiersDirectory.isDirectory()) {
            tiersById.clear();
            tiersByLevel.clear();
            return report;
        }

        File[] files = tiersDirectory.listFiles((dir, name) ->
            name.endsWith(TIER_EXTENSION) && !name.startsWith("."));

        if (files == null) {
            tiersById.clear();
            tiersByLevel.clear();
            return report;
        }

        Set<String> seenIds = new HashSet<>();
        Set<Integer> seenLevels = new HashSet<>();

        for (File file : files) {
            ValidationReport fileReport = loadTierFile(file, seenIds, seenLevels, loadedTiers, loadedByLevel);
            report.merge(fileReport);
        }

        tiersById.clear();
        tiersByLevel.clear();
        tiersById.putAll(loadedTiers);
        tiersByLevel.putAll(loadedByLevel);
        return report;
    }

    public ValidationReport loadWithMigration(boolean replaceSchemaV1) {
        ValidationReport report = new ValidationReport();
        Map<String, TierDefinition> loadedTiers = new LinkedHashMap<>();
        NavigableMap<Integer, TierDefinition> loadedByLevel = new TreeMap<>();

        if (!tiersDirectory.exists() || !tiersDirectory.isDirectory()) {
            tiersById.clear();
            tiersByLevel.clear();
            return report;
        }

        File[] files = tiersDirectory.listFiles((dir, name) ->
            name.endsWith(TIER_EXTENSION) && !name.startsWith("."));

        if (files == null) {
            tiersById.clear();
            tiersByLevel.clear();
            return report;
        }

        Set<String> seenIds = new HashSet<>();
        Set<Integer> seenLevels = new HashSet<>();

        for (File file : files) {
            ValidationReport fileReport = loadTierFileWithMigration(file, seenIds, seenLevels,
                loadedTiers, loadedByLevel, replaceSchemaV1);
            report.merge(fileReport);
        }

        tiersById.clear();
        tiersByLevel.clear();
        tiersById.putAll(loadedTiers);
        tiersByLevel.putAll(loadedByLevel);
        return report;
    }

    private ValidationReport loadTierFile(File file, Set<String> seenIds, Set<Integer> seenLevels,
                                          Map<String, TierDefinition> loadedTiers,
                                          NavigableMap<Integer, TierDefinition> loadedByLevel) {
        return loadTierFileWithMigration(file, seenIds, seenLevels, loadedTiers, loadedByLevel, false);
    }

    private ValidationReport loadTierFileWithMigration(File file, Set<String> seenIds, Set<Integer> seenLevels,
                                                       Map<String, TierDefinition> loadedTiers,
                                                       NavigableMap<Integer, TierDefinition> loadedByLevel,
                                                       boolean replaceSchemaV1) {
        ValidationReport report = new ValidationReport();

        try {
            org.bukkit.configuration.file.YamlConfiguration yaml =
                org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(file);

            int schemaVersion = yaml.getInt("schema-version", 1);
            TierParser.MigrationContext migrationCtx = null;

            if (schemaVersion == 1 && replaceSchemaV1) {
                final File legacyFileRef = file;
                migrationCtx = TierParser.MigrationContext.forMigration(
                    tiersDirectory,
                    legacyFileRef,
                    true,
                    resourceName -> plugin.getResource("tiers/" + resourceName)
                );
            } else {
                migrationCtx = TierParser.MigrationContext.noOp();
            }

            YamlValues values = new YamlValues(yaml, report);
            TierDefinition tier = TierParser.parse(values, report, migrationCtx);

            if (tier == null) {
                return report;
            }

            String id = tier.getId();

            if (seenIds.contains(id)) {
                report.addError("", "id", "Duplicate tier id: " + id + " in file " + file.getName());
                return report;
            }

            int level = tier.getLevel();
            if (seenLevels.contains(level)) {
                report.addError("", "level", "Duplicate tier level: " + level + " in file " + file.getName());
                return report;
            }

            seenIds.add(id);
            seenLevels.add(level);
            loadedTiers.put(id, tier);
            loadedByLevel.put(level, tier);

        } catch (Exception e) {
            report.addError("", file.getName(), "Failed to parse tier file: " + e.getMessage());
        }

        return report;
    }

    public Optional<TierDefinition> findById(String id) {
        return Optional.ofNullable(tiersById.get(id));
    }

    public Optional<TierDefinition> find(String id) {
        return findById(id);
    }

    public <T> Optional<T> findExtra(String id) {
        return Optional.empty();
    }

    public Optional<TierDefinition> findByLevel(int level) {
        return Optional.ofNullable(tiersByLevel.get(level));
    }

    public Optional<TierDefinition> findNextLevel(int currentLevel) {
        Map.Entry<Integer, TierDefinition> next = tiersByLevel.higherEntry(currentLevel);
        return next != null ? Optional.of(next.getValue()) : Optional.empty();
    }

    public List<TierDefinition> allAscending() {
        return Collections.unmodifiableList(new ArrayList<>(tiersByLevel.values()));
    }

    public List<TierDefinition> all() {
        return Collections.unmodifiableList(new ArrayList<>(tiersById.values()));
    }

    public TierDefinition create(String id, int level) {
        TierDefinition tier = new TierDefinition(
            id,
            level,
            true,
            "",
            new TierDefinition.TierDisplay("", Collections.emptyList(), false, "AIR"),
            0L,
            Collections.singletonList("ANY"),
            Collections.emptyList(),
            null,
            null,
            null,
            null,
            null,
            Collections.emptyList()
        );
        tiersById.put(id, tier);
        tiersByLevel.put(level, tier);
        return tier;
    }

    public TierDefinition clone(String sourceId, String newId, int newLevel) {
        TierDefinition source = tiersById.get(sourceId);
        if (source == null) {
            return null;
        }

        TierDefinition clone = new TierDefinition(
            newId,
            newLevel,
            source.isEnabled(),
            source.getPermission(),
            source.getDisplay(),
            source.getCooldownSeconds(),
            new ArrayList<>(source.getAllowedGroups()),
            new ArrayList<>(source.getDeniedMaterials()),
            source.getRequirements(),
            source.getChances(),
            source.getBreakPolicy(),
            source.getCurseDefinition(),
            source.getAnimationProfile(),
            new ArrayList<>(source.getVariants())
        );

        tiersById.put(newId, clone);
        tiersByLevel.put(newLevel, clone);
        return clone;
    }

    public boolean delete(String id) {
        TierDefinition removed = tiersById.remove(id);
        if (removed == null) {
            return false;
        }
        tiersByLevel.remove(removed.getLevel());
        return true;
    }

    public boolean save(TierDefinition tier) {
        String id = tier.getId();
        if (!tiersById.containsKey(id)) {
            return false;
        }
        TierDefinition old = tiersById.get(id);
        tiersByLevel.remove(old.getLevel());
        tiersById.put(id, tier);
        tiersByLevel.put(tier.getLevel(), tier);
        return true;
    }

    public File getTiersDirectory() {
        return tiersDirectory;
    }

    public int size() {
        return tiersById.size();
    }

    public boolean isEmpty() {
        return tiersById.isEmpty();
    }

    public boolean didDirectoryExistBeforeStartup() {
        return directoryExistedBeforeStartup;
    }
}
