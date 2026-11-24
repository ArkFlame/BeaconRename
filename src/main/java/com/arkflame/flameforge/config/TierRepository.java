package com.arkflame.flameforge.config;

import com.arkflame.flameforge.model.BreakPolicy;
import com.arkflame.flameforge.model.ForgeVariant;
import com.arkflame.flameforge.model.TierDefinition;
import org.bukkit.Material;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public class TierRepository {
    private static final String TIER_PREFIX = "tier";
    private static final String TIER_EXTENSION = ".yml";
    /** Number of legacy bundled tier files (tier1.yml .. tier7.yml) copied to the data folder. */
    private static final int BUNDLED_LEGACY_TIER_COUNT = 7;
    private static final java.util.regex.Pattern TIER_ID_PATTERN =
        java.util.regex.Pattern.compile("[A-Za-z0-9_-]+");

    private final JavaPlugin plugin;
    private final File tiersDirectory;
    private final File dataFolder;
    private final File equipmentFile;
    private boolean directoryExistedBeforeStartup;

    private volatile Map<String, TierDefinition> tiersById = new LinkedHashMap<>();
    private volatile List<TierDefinition> tiersAscending = new ArrayList<>();
    private volatile EquipmentCatalog equipmentCatalog = EquipmentCatalog.empty();

    public TierRepository(JavaPlugin plugin) {
        this.plugin = plugin;
        this.dataFolder = plugin.getDataFolder();
        this.tiersDirectory = new File(dataFolder, "tiers");
        this.equipmentFile = new File(dataFolder, "equipment.yml");
    }

    public static final class LoadCandidate {
        private final Map<String, TierDefinition> byId;
        private final List<TierDefinition> allAscending;
        private final EquipmentCatalog catalog;
        private final ValidationReport validationReport;

        private LoadCandidate(Map<String, TierDefinition> byId, List<TierDefinition> allAscending,
                              EquipmentCatalog catalog, ValidationReport validationReport) {
            this.byId = byId;
            this.allAscending = allAscending;
            this.catalog = catalog;
            this.validationReport = validationReport;
        }

        public Map<String, TierDefinition> getById() {
            return byId;
        }

        public List<TierDefinition> allAscending() {
            return Collections.unmodifiableList(new ArrayList<>(allAscending));
        }

        public EquipmentCatalog getCatalog() {
            return catalog;
        }

        public ValidationReport getValidationReport() {
            return validationReport;
        }
    }

    public LoadCandidate loadCandidate() {
        return loadCandidate(BreakPolicy.DefaultText.ENGLISH);
    }

    public LoadCandidate loadCandidate(BreakPolicy.DefaultText breakDefaults) {
        ValidationReport report = new ValidationReport();
        Map<String, TierDefinition> loadedTiers = new LinkedHashMap<>();

        loadBundledLegacyTiers(loadedTiers, report, breakDefaults);

        EquipmentCatalog bundledCatalog = loadBundledCatalog(report);
        loadBundledCategoryTiers(bundledCatalog, loadedTiers, report, breakDefaults);

        if (tiersDirectory.exists() && tiersDirectory.isDirectory()) {
            File[] files = tiersDirectory.listFiles((dir, name) ->
                name.endsWith(TIER_EXTENSION) && !name.startsWith("."));

            if (files != null) {
                Arrays.sort(files, Comparator.comparing(File::getName));

                for (File file : files) {
                    report.merge(loadOperatorTierFile(file, loadedTiers, breakDefaults));
                }
            }
        }

        EquipmentCatalog effectiveCatalog = loadEquipmentCatalog(report);
        validateCatalog(effectiveCatalog, loadedTiers, report);

        return new LoadCandidate(loadedTiers, buildAscending(loadedTiers), effectiveCatalog, report);
    }

    private void loadBundledLegacyTiers(Map<String, TierDefinition> loadedTiers, ValidationReport report,
                                        BreakPolicy.DefaultText breakDefaults) {
        for (int i = 1; i <= BUNDLED_LEGACY_TIER_COUNT; i++) {
            String bundledResourceName = TIER_PREFIX + i + TIER_EXTENSION;
            try (InputStream bundledStream = plugin.getResource("tiers/" + bundledResourceName)) {
                if (bundledStream == null) {
                    report.addError("bundled tiers/" + bundledResourceName, "resource",
                        "Required bundled tier resource not found in plugin JAR");
                    continue;
                }

                ValidationReport bundledReport = new ValidationReport();
                TierDefinition tier = TierParser.parseBundled(bundledStream, bundledReport, bundledResourceName, breakDefaults);

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

                if (loadedTiers.containsKey(id)) {
                    report.addError("bundled tiers/" + bundledResourceName, "id",
                        "Duplicate tier id: " + id);
                    continue;
                }

                loadedTiers.put(id, tier);
            } catch (Exception e) {
                report.addError("bundled tiers/" + bundledResourceName, "parse",
                    "Failed to read bundled tier: " + e.getMessage());
            }
        }
    }

    private EquipmentCatalog loadBundledCatalog(ValidationReport report) {
        InputStream bundled = plugin.getResource("equipment.yml");
        if (bundled == null) {
            report.addWarning("equipment.yml", "categories",
                "Bundled equipment.yml resource not found; bundled category tiers are not loaded");
            return EquipmentCatalog.empty();
        }
        return EquipmentCatalogParser.parse(bundled, report);
    }

    private void loadBundledCategoryTiers(EquipmentCatalog bundledCatalog, Map<String, TierDefinition> loadedTiers,
                                          ValidationReport report, BreakPolicy.DefaultText breakDefaults) {
        for (EquipmentCatalog.Category category : bundledCatalog.all()) {
            for (String progressionId : category.getProgression()) {
                if (loadedTiers.containsKey(progressionId)) {
                    continue;
                }

                String bundledResourceName = progressionId + TIER_EXTENSION;
                try (InputStream bundledStream = plugin.getResource("tiers/" + bundledResourceName)) {
                    if (bundledStream == null) {
                        report.addError("bundled tiers/" + bundledResourceName, "resource",
                            "Required bundled category tier resource not found in plugin JAR");
                        continue;
                    }

                    ValidationReport bundledReport = new ValidationReport();
                    TierDefinition tier = TierParser.parseBundled(bundledStream, bundledReport, bundledResourceName, breakDefaults);

                    if (bundledReport.hasErrors()) {
                        for (ValidationIssue issue : bundledReport.getErrors()) {
                            report.addError("bundled tiers/" + bundledResourceName, issue.getField(), issue.getMessage());
                        }
                        continue;
                    }

                    if (tier == null) {
                        report.addError("bundled tiers/" + bundledResourceName, "parse",
                            "Failed to parse bundled category tier");
                        continue;
                    }

                    loadedTiers.put(tier.getId(), tier);
                } catch (Exception e) {
                    report.addError("bundled tiers/" + bundledResourceName, "parse",
                        "Failed to read bundled category tier: " + e.getMessage());
                }
            }
        }
    }

    public synchronized void publish(LoadCandidate candidate) {
        if (candidate == null || candidate.validationReport.hasErrors()) {
            return;
        }

        tiersById = new LinkedHashMap<>(candidate.byId);
        tiersAscending = Collections.unmodifiableList(new ArrayList<>(candidate.allAscending));
        equipmentCatalog = candidate.catalog;
    }

    public void bootstrapDefaultsIfDirectoryAbsent() {
        this.directoryExistedBeforeStartup = tiersDirectory.exists();

        if (!directoryExistedBeforeStartup && !tiersDirectory.mkdirs()) {
            return;
        }

        Set<String> bundledTierResources = new LinkedHashSet<>();
        for (int i = 1; i <= BUNDLED_LEGACY_TIER_COUNT; i++) {
            bundledTierResources.add(TIER_PREFIX + i + TIER_EXTENSION);
        }
        copyBundledEquipment();

        ValidationReport catalogReport = new ValidationReport();
        EquipmentCatalog catalog = EquipmentCatalogParser.parse(plugin.getResource("equipment.yml"), catalogReport);
        for (EquipmentCatalog.Category category : catalog.all()) {
            for (String progressionId : category.getProgression()) {
                bundledTierResources.add(progressionId + TIER_EXTENSION);
            }
        }
        for (String resourceName : bundledTierResources) {
            copyBundledTier(resourceName);
        }
    }

    private EquipmentCatalog loadEquipmentCatalog(ValidationReport report) {
        InputStream bundled = plugin.getResource("equipment.yml");
        return EquipmentCatalogParser.parse(bundled, equipmentFile, report);
    }

    private void validateCatalog(EquipmentCatalog catalog, Map<String, TierDefinition> byId,
                                 ValidationReport report) {
        if (isUnconfiguredCatalog(catalog)) {
            report.addWarning("equipment.yml", "categories",
                "No equipment categories are configured; category progression is disabled");
            return;
        }

        Set<String> referencedIds = new HashSet<>();
        int fallbackCount = 0;
        String fallbackId = null;

        for (EquipmentCatalog.Category category : catalog.all()) {
            if (category.isFallback()) {
                fallbackCount++;
                fallbackId = category.getId();
            }

            List<String> progression = category.getProgression();
            if (progression.isEmpty()) {
                report.addError("equipment.yml.categories." + category.getId(), "progression",
                    "Configured category progression must not be empty");
                continue;
            }

            for (int i = 0; i < progression.size(); i++) {
                String id = progression.get(i);
                TierDefinition tier = byId.get(id);
                if (tier == null) {
                    report.addError("equipment.yml.categories." + category.getId(), "progression",
                        "Progression references unknown tier id: " + id);
                } else if (tier.getLevel() != i + 1) {
                    report.addError("equipment.yml.categories." + category.getId(), "progression",
                        "Tier " + id + " level " + tier.getLevel() + " does not match progression position " + (i + 1));
                }

                if (!referencedIds.add(id.toLowerCase(Locale.ROOT))) {
                    report.addError("equipment.yml.categories." + category.getId(), "progression",
                        "Tier id " + id + " is shared by multiple category progressions");
                }
            }
        }

        if (fallbackCount != 1) {
            report.addError("equipment.yml.categories", "fallback",
                "Exactly one fallback category is required, found " + fallbackCount);
        } else if (!EquipmentCatalog.FALLBACK_CATEGORY_ID.equalsIgnoreCase(fallbackId)) {
            report.addError("equipment.yml.categories", "fallback",
                "Fallback category must be " + EquipmentCatalog.FALLBACK_CATEGORY_ID + ", got " + fallbackId);
        }
    }

    private boolean isUnconfiguredCatalog(EquipmentCatalog catalog) {
        List<EquipmentCatalog.Category> all = catalog.all();
        if (all.size() != 1) {
            return false;
        }
        EquipmentCatalog.Category only = all.get(0);
        return only.isFallback()
            && EquipmentCatalog.FALLBACK_CATEGORY_ID.equalsIgnoreCase(only.getId())
            && only.getProgression().isEmpty();
    }

    private List<TierDefinition> buildAscending(Map<String, TierDefinition> byId) {
        List<TierDefinition> sorted = new ArrayList<>(byId.values());
        sorted.sort(Comparator.comparingInt(TierDefinition::getLevel)
            .thenComparing(TierDefinition::getId, String.CASE_INSENSITIVE_ORDER));
        return sorted;
    }

    private void copyBundledEquipment() {
        InputStream stream = plugin.getResource("equipment.yml");
        if (stream == null || equipmentFile.exists()) {
            if (stream != null) {
                try { stream.close(); } catch (IOException ignored) { }
            }
            return;
        }
        try (InputStream in = stream) {
            Files.copy(in, equipmentFile.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to copy bundled equipment.yml - " + e.getMessage());
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
                                                  BreakPolicy.DefaultText breakDefaults) {
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
            TierDefinition tier = TierParser.parse(values, isolatedReport, breakDefaults);

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

            loadedTiers.put(id, tier);

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

    public Optional<String> findEquipmentCategory(Material material) {
        if (material == null) {
            return Optional.empty();
        }
        return Optional.of(equipmentCatalog.categoryForMaterial(material.name()).getId());
    }

    public Optional<TierDefinition> findForMaterialAndLevel(Material material, int level) {
        return resolveCategory(material)
            .flatMap(category -> findByIdAtLevel(category.getProgression(), level));
    }

    public Optional<TierDefinition> findExactNext(Material material, int currentLevel) {
        Optional<EquipmentCatalog.Category> category = resolveCategory(material);
        if (!category.isPresent()) {
            return Optional.empty();
        }
        for (String tierId : category.get().getProgression()) {
            TierDefinition tier = tiersById.get(tierId);
            if (tier != null && tier.getLevel() == currentLevel + 1) {
                return Optional.of(tier);
            }
        }
        return Optional.empty();
    }

    public int maxLevelFor(Material material) {
        Optional<EquipmentCatalog.Category> category = resolveCategory(material);
        if (!category.isPresent()) {
            return 0;
        }
        int max = 0;
        for (String tierId : category.get().getProgression()) {
            TierDefinition tier = tiersById.get(tierId);
            if (tier != null && tier.getLevel() > max) {
                max = tier.getLevel();
            }
        }
        return max;
    }

    private Optional<EquipmentCatalog.Category> resolveCategory(Material material) {
        Optional<String> categoryId = findEquipmentCategory(material);
        return categoryId.isPresent() ? equipmentCatalog.findCategory(categoryId.get()) : Optional.empty();
    }

    public Optional<TierDefinition> findByLevel(int level) {
        for (TierDefinition tier : tiersAscending) {
            if (tier.getLevel() == level) {
                return Optional.of(tier);
            }
        }
        return Optional.empty();
    }

    public Optional<TierDefinition> findByCategory(String categoryId, int level) {
        return equipmentCatalog.findCategory(categoryId)
            .flatMap(category -> findByIdAtLevel(category.getProgression(), level));
    }

    public Optional<TierDefinition> findByCategoryAndLevel(String categoryId, int level) {
        return findByCategory(categoryId, level);
    }

    public Optional<TierDefinition> findNext(String categoryId, int currentLevel) {
        return findCategoryTier(categoryId, currentLevel, false);
    }

    public Optional<TierDefinition> findNext(String categoryId, String currentTierId) {
        Optional<TierDefinition> current = findById(currentTierId);
        return current.isPresent()
            ? findNext(categoryId, current.get().getLevel())
            : findNext(categoryId, 0);
    }

    public Optional<TierDefinition> findExactNext(String categoryId, int currentLevel) {
        return findCategoryTier(categoryId, currentLevel, true);
    }

    private Optional<TierDefinition> findCategoryTier(String categoryId, int currentLevel, boolean exact) {
        Optional<EquipmentCatalog.Category> category = equipmentCatalog.findCategory(categoryId);
        if (!category.isPresent()) {
            return Optional.empty();
        }
        for (String tierId : category.get().getProgression()) {
            TierDefinition tier = tiersById.get(tierId);
            if (tier != null && (exact ? tier.getLevel() == currentLevel + 1 : tier.getLevel() > currentLevel)) {
                return Optional.of(tier);
            }
        }
        return Optional.empty();
    }

    private Optional<TierDefinition> findByIdAtLevel(List<String> ids, int level) {
        for (String id : ids) {
            TierDefinition tier = tiersById.get(id);
            if (tier != null && tier.getLevel() == level) {
                return Optional.of(tier);
            }
        }
        return Optional.empty();
    }

    public EquipmentCatalog getEquipmentCatalog() {
        return equipmentCatalog;
    }

    public EquipmentCatalog.Category categoryForMaterial(String material) {
        return equipmentCatalog.categoryForMaterial(material);
    }

    public Optional<TierDefinition> findNextLevel(int currentLevel) {
        for (TierDefinition tier : tiersAscending) {
            if (tier.getLevel() > currentLevel) {
                return Optional.of(tier);
            }
        }
        return Optional.empty();
    }

    public Optional<TierDefinition> findExactNext(int currentLevel) {
        return findByLevel(currentLevel + 1);
    }

    public List<TierDefinition> allAscending() {
        return Collections.unmodifiableList(new ArrayList<>(tiersAscending));
    }

    public List<TierDefinition> all() {
        return Collections.unmodifiableList(new ArrayList<>(tiersById.values()));
    }

    public synchronized TierDefinition create(String id, int level) {
        String validatedId = validateId(id);
        if (validatedId == null) {
            return null;
        }
        if (level < 0) {
            warn("Tier level must be >= 0 for " + validatedId + ", got " + level);
            return null;
        }
        if (tierIdTaken(validatedId)) {
            warn("Tier id already exists: " + validatedId);
            return null;
        }

        TierDefinition tier = defaultTier(validatedId, level);
        if (!TierFileWriter.write(tiersDirectory, tier)) {
            warn("Failed to write tier file for " + validatedId);
            return null;
        }
        installTier(tier);
        registerProgression(tier);
        return tier;
    }

    public synchronized TierDefinition clone(String sourceId, String newId, int newLevel) {
        TierDefinition source = tiersById.get(sourceId);
        if (source == null) {
            return null;
        }

        String validatedId = validateId(newId);
        if (validatedId == null) {
            return null;
        }
        if (newLevel < 0) {
            warn("Tier level must be >= 0 for " + validatedId + ", got " + newLevel);
            return null;
        }
        if (tierIdTaken(validatedId)) {
            warn("Tier id already exists: " + validatedId);
            return null;
        }

        TierDefinition clone = new TierDefinition(
            validatedId,
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

        if (!TierFileWriter.write(tiersDirectory, clone)) {
            warn("Failed to write tier file for " + validatedId);
            return null;
        }
        installTier(clone);
        registerProgression(clone);
        return clone;
    }

    public synchronized boolean delete(String id) {
        if (id == null || !tiersById.containsKey(id)) {
            return false;
        }
        Map<String, TierDefinition> copy = new LinkedHashMap<>(tiersById);
        copy.remove(id);
        tiersById = copy;
        tiersAscending = Collections.unmodifiableList(buildAscending(copy));

        File file = new File(tiersDirectory, id + TIER_EXTENSION);
        if (isInsideTiersDirectory(file) && file.isFile()) {
            try {
                Files.deleteIfExists(file.toPath());
            } catch (IOException failure) {
                warn("Failed to delete tier file " + file.getName() + ": " + failure.getMessage());
            }
        }
        return true;
    }

    public synchronized boolean save(TierDefinition tier) {
        if (tier == null || tier.getId() == null) {
            return false;
        }
        String id = tier.getId();
        if (!tiersById.containsKey(id)) {
            return false;
        }
        if (!TierFileWriter.write(tiersDirectory, tier)) {
            warn("Failed to write tier file for " + id);
            return false;
        }
        installTier(tier);
        return true;
    }

    private static TierDefinition defaultTier(String id, int level) {
        ForgeVariant variant = new ForgeVariant("base", "", Collections.emptyList(), 1.0d, null,
            Collections.singletonList("ANY"), Collections.emptyList(), Collections.emptyList(),
            Collections.emptyList());
        return new TierDefinition(
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
            Collections.singletonList(variant)
        );
    }

    private String validateId(String id) {
        if (id == null) {
            warn("Tier id is required");
            return null;
        }
        String trimmed = id.trim();
        if (trimmed.isEmpty() || !TIER_ID_PATTERN.matcher(trimmed).matches()) {
            warn("Invalid tier id '" + id + "'; use only letters, numbers, '_' and '-'");
            return null;
        }
        return trimmed;
    }

    private boolean tierIdTaken(String id) {
        for (String existing : tiersById.keySet()) {
            if (existing.equalsIgnoreCase(id)) {
                return true;
            }
        }
        File[] files = tiersDirectory.listFiles((dir, name) ->
            name.toLowerCase(Locale.ROOT).endsWith(TIER_EXTENSION) && !name.startsWith("."));
        if (files != null) {
            for (File file : files) {
                String name = file.getName();
                String base = name.substring(0, name.length() - TIER_EXTENSION.length());
                if (base.equalsIgnoreCase(id)) {
                    return true;
                }
            }
        }
        return new File(tiersDirectory, id + TIER_EXTENSION).exists();
    }

    private void installTier(TierDefinition tier) {
        Map<String, TierDefinition> copy = new LinkedHashMap<>(tiersById);
        copy.put(tier.getId(), tier);
        tiersById = copy;
        tiersAscending = Collections.unmodifiableList(buildAscending(copy));
    }

    private void registerProgression(TierDefinition tier) {
        EquipmentCatalog catalog = equipmentCatalog;
        if (catalog == null) {
            return;
        }
        String tierId = tier.getId().toLowerCase(Locale.ROOT);
        for (EquipmentCatalog.Category category : catalog.all()) {
            String prefix = category.getId().toLowerCase(Locale.ROOT) + "_";
            if (!tierId.startsWith(prefix)) {
                continue;
            }
            int nextLevel = category.getProgression().size() + 1;
            if (tier.getLevel() != nextLevel) {
                warn("Tier " + tier.getId() + " has level " + tier.getLevel() + " but the next "
                    + category.getId() + " progression level is " + nextLevel
                    + "; the equipment.yml progression entry was not added");
                return;
            }
            for (String existing : category.getProgression()) {
                if (existing.equalsIgnoreCase(tier.getId())) {
                    return;
                }
            }
            if (!EquipmentProgressionAppender.append(equipmentFile, category.getId(), tier.getId())) {
                warn("Failed to add " + tier.getId() + " to the " + category.getId()
                    + " progression in equipment.yml");
                return;
            }
            rebuildEquipmentCatalog();
            return;
        }
    }

    private void rebuildEquipmentCatalog() {
        ValidationReport report = new ValidationReport();
        EquipmentCatalog parsed = EquipmentCatalogParser.parse(
            plugin.getResource("equipment.yml"), equipmentFile, report);
        if (report.hasErrors()) {
            warn("Could not rebuild the equipment catalog after tier registration");
            return;
        }
        equipmentCatalog = parsed;
    }

    private boolean isInsideTiersDirectory(File file) {
        try {
            String directory = tiersDirectory.getCanonicalPath();
            String path = file.getCanonicalPath();
            return path.equals(directory) || path.startsWith(directory + File.separator);
        } catch (IOException failure) {
            return false;
        }
    }

    private void warn(String message) {
        if (plugin != null && plugin.getLogger() != null) {
            plugin.getLogger().warning(message);
        }
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
