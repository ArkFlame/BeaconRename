package com.arkflame.flameforge.config;

import com.arkflame.flameforge.model.TierDefinition;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
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

    public static final class LoadCandidate {
        private final Map<String, TierDefinition> byId;
        private final NavigableMap<Integer, TierDefinition> byLevel;
        private final ValidationReport validationReport;

        private LoadCandidate(Map<String, TierDefinition> byId, NavigableMap<Integer, TierDefinition> byLevel, ValidationReport validationReport) {
            this.byId = byId;
            this.byLevel = byLevel;
            this.validationReport = validationReport;
        }

        public Map<String, TierDefinition> getById() {
            return byId;
        }

        public NavigableMap<Integer, TierDefinition> getByLevel() {
            return byLevel;
        }

        public ValidationReport getValidationReport() {
            return validationReport;
        }
    }

    public LoadCandidate loadCandidate() {
        ValidationReport report = new ValidationReport();
        Map<String, TierDefinition> loadedTiers = new LinkedHashMap<>();
        NavigableMap<Integer, TierDefinition> loadedByLevel = new TreeMap<>();

        Set<String> seenIds = new HashSet<>();
        Set<Integer> seenLevels = new HashSet<>();

        for (int i = 1; i <= 7; i++) {
            String bundledResourceName = TIER_PREFIX + i + TIER_EXTENSION;
            try (InputStream bundledStream = plugin.getResource("tiers/" + bundledResourceName)) {
                if (bundledStream == null) {
                    report.addError("bundled tiers/" + bundledResourceName, "resource",
                        "Required bundled tier resource not found in plugin JAR");
                    continue;
                }

                ValidationReport bundledReport = new ValidationReport();
                TierDefinition tier = TierParser.parseBundled(bundledStream, bundledReport, bundledResourceName);

                if (bundledReport.hasErrors()) {
                    for (ValidationIssue issue : bundledReport.getErrors()) {
                        report.addError("bundled tiers/" + bundledResourceName, issue.getField(), issue.getMessage());
                    }
                    continue;
                }

                if (tier == null) {
                    report.addError("bundled tiers/" + bundledResourceName, "parse",
                        "Failed to parse bundled tier");
                    continue;
                }

                String id = tier.getId();
                int level = tier.getLevel();

                if (seenIds.contains(id)) {
                    report.addError("bundled tiers/" + bundledResourceName, "id",
                        "Duplicate tier id: " + id);
                    continue;
                }

                if (seenLevels.contains(level)) {
                    report.addError("bundled tiers/" + bundledResourceName, "level",
                        "Duplicate tier level: " + level);
                    continue;
                }

                seenIds.add(id);
                seenLevels.add(level);
                loadedTiers.put(id, tier);
                loadedByLevel.put(level, tier);
            } catch (Exception e) {
                report.addError("bundled tiers/" + bundledResourceName, "parse",
                    "Failed to read bundled tier: " + e.getMessage());
            }
        }

        if (!report.hasErrors() && tiersDirectory.exists() && tiersDirectory.isDirectory()) {
            File[] files = tiersDirectory.listFiles((dir, name) ->
                name.endsWith(TIER_EXTENSION) && !name.startsWith("."));

            if (files != null) {
                Arrays.sort(files, Comparator.comparing(File::getName));

                for (File file : files) {
                    ValidationReport fileReport = loadOperatorTierFile(file, loadedTiers, loadedByLevel);
                    report.merge(fileReport);
                }
            }
        }

        return new LoadCandidate(loadedTiers, loadedByLevel, report);
    }

    public synchronized void publish(LoadCandidate candidate) {
        if (candidate == null || candidate.validationReport.hasErrors()) {
            return;
        }

        tiersById.clear();
        tiersByLevel.clear();
        tiersById.putAll(candidate.byId);
        tiersByLevel.putAll(candidate.byLevel);
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
            Files.copy(in, destFile.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to copy bundled tier: " + resourceName + " - " + e.getMessage());
        }
    }

    public ValidationReport load() {
        LoadCandidate candidate = loadCandidate();
        if (!candidate.validationReport.hasErrors()) {
            publish(candidate);
        }
        return candidate.validationReport;
    }

    private ValidationReport loadOperatorTierFile(File file, Map<String, TierDefinition> loadedTiers,
                                                  NavigableMap<Integer, TierDefinition> loadedByLevel) {
        ValidationReport report = new ValidationReport();
        String fileName = file.getName();

        ValidationReport isolatedReport = new ValidationReport();

        try (InputStreamReader reader = new InputStreamReader(
                java.nio.file.Files.newInputStream(file.toPath()), StandardCharsets.UTF_8)) {
            org.bukkit.configuration.file.YamlConfiguration yaml =
                org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(reader);

            int schemaVersion = yaml.getInt("schema-version", -1);
            if (schemaVersion != 2) {
                report.addWarning("tiers/" + fileName, "schema-version",
                    "Unsupported schema version " + schemaVersion + "; only schema version 2 is accepted. The file was not loaded.");
                return report;
            }

            YamlValues values = new YamlValues(yaml, isolatedReport);
            TierDefinition tier = TierParser.parse(values, isolatedReport);

            if (isolatedReport.hasErrors()) {
                for (ValidationIssue issue : isolatedReport.getErrors()) {
                    report.addWarning("tiers/" + fileName, issue.getField(), issue.getMessage());
                }
                return report;
            }

            if (tier == null) {
                return report;
            }

            String id = tier.getId();
            int level = tier.getLevel();

            TierDefinition existingById = loadedTiers.get(id);
            TierDefinition existingByLevel = loadedByLevel.get(level);

            if (existingById == null && existingByLevel == null) {
                loadedTiers.put(id, tier);
                loadedByLevel.put(level, tier);
            } else if (existingById != null && existingByLevel != null && existingById == existingByLevel) {
                loadedTiers.put(id, tier);
                loadedByLevel.put(level, tier);
            } else {
                report.addWarning("tiers/" + fileName, "id/level",
                    "ID/level conflict with existing tier; skipping file");
            }

        } catch (Exception e) {
            report.addWarning("tiers/" + fileName, "parse",
                "Failed to parse tier file: " + e.getMessage());
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

    public Optional<TierDefinition> findExactNext(int currentLevel) {
        return Optional.ofNullable(tiersByLevel.get(currentLevel + 1));
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
