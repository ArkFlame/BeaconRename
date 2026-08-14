package com.arkflame.flameforge.config;

import com.arkflame.flameforge.compat.material.MaterialResolver;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class EquipmentCatalogParser {
    private static final int SCHEMA_VERSION = 1;

    private EquipmentCatalogParser() {
    }

    public static EquipmentCatalog parse(InputStream bundled, File operatorFile, ValidationReport report) {
        YamlConfiguration baseline = load(bundled, report, "bundled equipment.yml");
        YamlConfiguration overlay = operatorFile != null && operatorFile.exists()
            ? YamlConfiguration.loadConfiguration(operatorFile) : null;
        if (overlay != null && (overlay.contains("legacy-tier-ids") || overlay.contains("legacy-tier-id"))) {
            report.addWarning("equipment.yml", "legacy-tier-ids", "Legacy tier IDs are ignored");
        }
        Map<String, Object> merged = toMap(baseline);
        if (overlay != null) {
            merged = merge(merged, toMap(overlay));
        }
        return parse(merged, report);
    }

    public static EquipmentCatalog parse(InputStream source, ValidationReport report) {
        if (source == null) {
            return EquipmentCatalog.empty();
        }
        return parse(source, null, report);
    }

    public static EquipmentCatalog parse(ConfigurationSection section, ValidationReport report) {
        return parse(toMap(section), report);
    }

    public static EquipmentCatalog parse(Map<String, Object> values, ValidationReport report) {
        Object schema = values.get("schema-version");
        if (schema == null && values.isEmpty()) {
            return EquipmentCatalog.empty();
        }
        if (!(schema instanceof Number) || ((Number) schema).intValue() != SCHEMA_VERSION) {
            report.addError("equipment.yml", "schema-version", "Unsupported schema version; expected 1");
            return EquipmentCatalog.empty();
        }

        Object rawCategories = values.get("categories");
        if (!(rawCategories instanceof Map)) {
            return EquipmentCatalog.empty();
        }

        List<EquipmentCatalog.Category> categories = new ArrayList<>();
        Set<String> progressionIds = new LinkedHashSet<>();
        Map<String, String> materialOwners = new LinkedHashMap<>();
        String fallbackId = null;
        Set<String> categoryIds = new LinkedHashSet<>();
        Map<?, ?> categoryMap = (Map<?, ?>) rawCategories;
        for (Map.Entry<?, ?> entry : categoryMap.entrySet()) {
            String key = String.valueOf(entry.getKey()).trim();
            if (!(entry.getValue() instanceof Map) || key.isEmpty()) {
                continue;
            }
            Map<?, ?> category = (Map<?, ?>) entry.getValue();
            String id = string(category.get("id"), key).trim();
            if (!categoryIds.add(id.toLowerCase(Locale.ROOT))) {
                report.addError("equipment.yml.categories", "id", "Duplicate category ID: " + id);
                continue;
            }
            boolean fallback = booleanValue(category.get("fallback"), false);
            if (fallback) {
                if (fallbackId != null) {
                    report.addError("equipment.yml.categories", "fallback", "Only one fallback category is allowed");
                }
                fallbackId = id;
            }

            List<String> resolvedMaterials = new ArrayList<>();
            for (Object rawMaterial : list(category.get("materials"))) {
                String materialName = String.valueOf(rawMaterial).trim();
                if (!MaterialResolver.getInstance().resolve(materialName).isPresent()) {
                    continue;
                }
                String normalized = MaterialResolver.getInstance().resolve(materialName).get().name();
                if (!fallback) {
                    String ownerId = materialOwners.get(normalized);
                    if (ownerId != null && !ownerId.equals(id)) {
                        report.addError("equipment.yml.categories." + id, "materials", "Material appears in multiple non-fallback categories: " + materialName);
                        continue;
                    }
                    materialOwners.put(normalized, id);
                }
                resolvedMaterials.add(normalized);
            }

            List<String> progression = new ArrayList<>();
            Set<String> progressionIdsForCategory = new LinkedHashSet<>();
            Object rawProgression = category.containsKey("progression")
                ? category.get("progression") : category.get("tier-ids");
            for (Object rawId : list(rawProgression)) {
                String progressionId = String.valueOf(rawId).trim();
                if (progressionId.isEmpty() || !progressionIdsForCategory.add(progressionId.toLowerCase(Locale.ROOT))) {
                    report.addError("equipment.yml.categories." + id, "progression", "Duplicate progression ID: " + progressionId);
                    continue;
                }
                if (!progressionIds.add(progressionId.toLowerCase(Locale.ROOT))) {
                    report.addError("equipment.yml.categories." + id, "progression", "Progression ID appears in multiple categories: " + progressionId);
                    continue;
                }
                progression.add(progressionId);
            }
            categories.add(new EquipmentCatalog.Category(id, fallback, resolvedMaterials, progression));
        }

        if (fallbackId == null && !categoryIds.contains(EquipmentCatalog.FALLBACK_CATEGORY_ID)) {
            categories.add(new EquipmentCatalog.Category(
                EquipmentCatalog.FALLBACK_CATEGORY_ID, true, Collections.emptyList(), Collections.emptyList()));
        }
        return new EquipmentCatalog(categories);
    }

    private static YamlConfiguration load(InputStream source, ValidationReport report, String path) {
        if (source == null) {
            return new YamlConfiguration();
        }
        try (InputStream input = source; InputStreamReader reader = new InputStreamReader(input, StandardCharsets.UTF_8)) {
            return YamlConfiguration.loadConfiguration(reader);
        } catch (IOException e) {
            report.addError(path, "parse", "Failed to read equipment catalog: " + e.getMessage());
            return new YamlConfiguration();
        }
    }

    private static Map<String, Object> toMap(ConfigurationSection section) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (String key : section.getKeys(false)) {
            Object value = section.get(key);
            result.put(key, normalize(value));
        }
        return result;
    }

    private static Object normalize(Object value) {
        if (value instanceof ConfigurationSection) {
            return toMap((ConfigurationSection) value);
        }
        if (value instanceof Map) {
            Map<String, Object> result = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : ((Map<?, ?>) value).entrySet()) {
                result.put(String.valueOf(entry.getKey()), normalize(entry.getValue()));
            }
            return result;
        }
        if (value instanceof List) {
            List<Object> result = new ArrayList<>();
            for (Object item : (List<?>) value) {
                result.add(normalize(item));
            }
            return result;
        }
        return value;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> merge(Map<String, Object> baseline, Map<String, Object> overlay) {
        Map<String, Object> result = new LinkedHashMap<>(baseline);
        for (Map.Entry<String, Object> entry : overlay.entrySet()) {
            Object baseValue = result.get(entry.getKey());
            Object overlayValue = entry.getValue();
            if (baseValue instanceof Map && overlayValue instanceof Map) {
                result.put(entry.getKey(), merge((Map<String, Object>) baseValue, (Map<String, Object>) overlayValue));
            } else {
                result.put(entry.getKey(), overlayValue);
            }
        }
        return result;
    }

    private static List<?> list(Object value) {
        return value instanceof List ? (List<?>) value : Collections.emptyList();
    }

    private static String string(Object value, String fallback) {
        return value == null ? fallback : String.valueOf(value);
    }

    private static boolean booleanValue(Object value, boolean fallback) {
        return value instanceof Boolean ? ((Boolean) value).booleanValue() : fallback;
    }
}
