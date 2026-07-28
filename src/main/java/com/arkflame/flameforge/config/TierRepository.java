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
    private final Map<String, TierParser.TierExtra> extrasByTierId = new LinkedHashMap<>();

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
        tiersById.clear();
        extrasByTierId.clear();

        ValidationReport report = new ValidationReport();

        if (!tiersDirectory.exists() || !tiersDirectory.isDirectory()) {
            return report;
        }

        File[] files = tiersDirectory.listFiles((dir, name) ->
            name.endsWith(TIER_EXTENSION) && !name.startsWith("."));

        if (files == null) {
            return report;
        }

        Set<String> seenIds = new HashSet<>();

        for (File file : files) {
            ValidationReport fileReport = loadTierFile(file, seenIds);
            report.merge(fileReport);
        }

        sortTiers();
        return report;
    }

    private ValidationReport loadTierFile(File file, Set<String> seenIds) {
        ValidationReport report = new ValidationReport();

        try {
            org.bukkit.configuration.file.YamlConfiguration yaml =
                org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(file);

            YamlValues values = new YamlValues(yaml, report);
            TierParser.TierParseResult result = TierParser.parse(values, report);

            if (!result.isSuccess()) {
                return report;
            }

            TierDefinition tier = result.getTier();
            String id = tier.getId();

            if (seenIds.contains(id)) {
                report.addError("", "id", "Duplicate tier id: " + id + " in file " + file.getName());
                return report;
            }

            seenIds.add(id);
            tiersById.put(id, tier);
            if (result.getExtra() != null) {
                extrasByTierId.put(id, result.getExtra());
            }

        } catch (Exception e) {
            report.addError("", file.getName(), "Failed to parse tier file: " + e.getMessage());
        }

        return report;
    }

    private void sortTiers() {
        List<Map.Entry<String, TierDefinition>> entries = new ArrayList<>(tiersById.entrySet());

        Collections.sort(entries, (a, b) -> {
            TierDefinition tierA = a.getValue();
            TierDefinition tierB = b.getValue();

            int priorityCompare = Integer.compare(tierB.getTierLevel(), tierA.getTierLevel());
            if (priorityCompare != 0) {
                return priorityCompare;
            }

            return a.getKey().compareTo(b.getKey());
        });

        tiersById.clear();
        for (Map.Entry<String, TierDefinition> entry : entries) {
            tiersById.put(entry.getKey(), entry.getValue());
        }
    }

    public Optional<TierDefinition> find(String id) {
        return Optional.ofNullable(tiersById.get(id));
    }

    public List<TierDefinition> all() {
        return Collections.unmodifiableList(new ArrayList<>(tiersById.values()));
    }

    public Optional<TierParser.TierExtra> findExtra(String tierId) {
        return Optional.ofNullable(extrasByTierId.get(tierId));
    }

    public TierDefinition create(String id, int priority) {
        TierDefinition tier = TierDefinition.of(
            id,
            priority,
            com.arkflame.flameforge.model.TierCost.xpOnly(java.math.BigDecimal.ZERO),
            40,
            20,
            Collections.emptyList()
        );
        tiersById.put(id, tier);
        extrasByTierId.put(id, new TierParser.TierExtra(
            true, "", TierParser.TierDisplay.DEFAULT, 0L, TierParser.TierPity.DEFAULT
        ));
        sortTiers();
        return tier;
    }

    public TierDefinition clone(String sourceId, String newId) {
        TierDefinition source = tiersById.get(sourceId);
        if (source == null) {
            return null;
        }

        TierDefinition clone = TierDefinition.of(
            newId,
            source.getTierLevel(),
            source.getCost(),
            source.getSuccessAnimationDuration(),
            source.getFailAnimationDuration(),
            new ArrayList<>(source.getOutcomes())
        );

        TierParser.TierExtra sourceExtra = extrasByTierId.get(sourceId);
        TierParser.TierExtra cloneExtra = sourceExtra != null ? sourceExtra : new TierParser.TierExtra(
            true, "", TierParser.TierDisplay.DEFAULT, 0L, TierParser.TierPity.DEFAULT
        );

        tiersById.put(newId, clone);
        extrasByTierId.put(newId, cloneExtra);
        sortTiers();
        return clone;
    }

    public boolean delete(String id) {
        if (!tiersById.containsKey(id)) {
            return false;
        }
        tiersById.remove(id);
        extrasByTierId.remove(id);
        return true;
    }

    public boolean save(TierDefinition tier) {
        String id = tier.getId();
        if (!tiersById.containsKey(id)) {
            return false;
        }
        tiersById.put(id, tier);
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
