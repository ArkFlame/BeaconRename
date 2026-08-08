package com.arkflame.flameforge.config;

import com.arkflame.flameforge.model.TierDefinition;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class ConfigSnapshot {
    private final Map<String, Object> rootSettings;
    private final Map<String, Map<String, Object>> menuSettings;
    private final Map<String, Map<String, Object>> messageSettings;
    private final Map<String, Map<String, Object>> stationProfiles;
    private final Map<String, Map<String, Object>> itemGroups;
    private final Map<String, Map<String, Object>> catalysts;
    private final Map<String, Map<String, Object>> wards;
    private final Map<String, Map<String, Object>> announcements;
    private final Map<String, Object> auditSettings;
    private final List<TierDefinition> tiers;
    private final ValidationReport validationReport;
    private final long timestamp;

    private ConfigSnapshot(Builder builder) {
        this.rootSettings = Collections.unmodifiableMap(new HashMap<>(builder.rootSettings));
        this.menuSettings = Collections.unmodifiableMap(deepCopy(builder.menuSettings));
        this.messageSettings = Collections.unmodifiableMap(deepCopy(builder.messageSettings));
        this.stationProfiles = Collections.unmodifiableMap(deepCopy(builder.stationProfiles));
        this.itemGroups = Collections.unmodifiableMap(deepCopy(builder.itemGroups));
        this.catalysts = Collections.unmodifiableMap(deepCopy(builder.catalysts));
        this.wards = Collections.unmodifiableMap(deepCopy(builder.wards));
        this.announcements = Collections.unmodifiableMap(deepCopy(builder.announcements));
        this.auditSettings = Collections.unmodifiableMap(new HashMap<>(builder.auditSettings));
        this.tiers = Collections.unmodifiableList(builder.tiers);
        this.validationReport = builder.validationReport;
        this.timestamp = System.currentTimeMillis();
    }

    public static Builder builder() {
        return new Builder();
    }

    public Object getRootSetting(String key) {
        return rootSettings.get(key);
    }

    public Object getRootSetting(String key, Object def) {
        return rootSettings.getOrDefault(key, def);
    }

    public String getRootString(String key) {
        Object val = rootSettings.get(key);
        return val instanceof String ? (String) val : null;
    }

    public String getRootString(String key, String def) {
        Object val = rootSettings.get(key);
        return val instanceof String ? (String) val : def;
    }

    public int getRootInt(String key, int def) {
        Object val = rootSettings.get(key);
        if (val instanceof Number) {
            return ((Number) val).intValue();
        }
        return def;
    }

    public long getRootLong(String key, long def) {
        Object val = rootSettings.get(key);
        if (val instanceof Number) {
            return ((Number) val).longValue();
        }
        return def;
    }

    public double getRootDouble(String key, double def) {
        Object val = rootSettings.get(key);
        if (val instanceof Number) {
            return ((Number) val).doubleValue();
        }
        return def;
    }

    public boolean getRootBoolean(String key, boolean def) {
        Object val = rootSettings.get(key);
        if (val instanceof Boolean) {
            return (Boolean) val;
        }
        return def;
    }

    @SuppressWarnings("unchecked")
    public List<String> getRootStringList(String key) {
        Object val = rootSettings.get(key);
        if (val instanceof List) {
            List<Object> raw = (List<Object>) val;
            List<String> result = new ArrayList<>();
            for (Object item : raw) {
                if (item != null) {
                    result.add(item.toString());
                }
            }
            return Collections.unmodifiableList(result);
        }
        return Collections.emptyList();
    }

    public Map<String, Object> getMenuSettings(String menuId) {
        Map<String, Object> menu = menuSettings.get(menuId);
        return menu != null ? Collections.unmodifiableMap(menu) : null;
    }

    public Map<String, Map<String, Object>> getAllMenuSettings() {
        Map<String, Map<String, Object>> copy = new HashMap<>();
        for (Map.Entry<String, Map<String, Object>> entry : menuSettings.entrySet()) {
            Map<String, Object> innerCopy = new HashMap<>(entry.getValue());
            copy.put(entry.getKey(), Collections.unmodifiableMap(innerCopy));
        }
        return Collections.unmodifiableMap(copy);
    }

    public Map<String, Object> getMessageSettings(String messageId) {
        Map<String, Object> messages = messageSettings.get(messageId);
        return messages != null ? Collections.unmodifiableMap(messages) : null;
    }

    public Map<String, Object> getStationProfile(String profileId) {
        Map<String, Object> profile = stationProfiles.get(profileId);
        return profile != null ? Collections.unmodifiableMap(profile) : null;
    }

    public Map<String, Object> getItemGroup(String groupId) {
        Map<String, Object> group = itemGroups.get(groupId);
        return group != null ? Collections.unmodifiableMap(group) : null;
    }

    public Map<String, Object> getCatalyst(String catalystId) {
        Map<String, Object> catalyst = catalysts.get(catalystId);
        return catalyst != null ? Collections.unmodifiableMap(catalyst) : null;
    }

    public Map<String, Object> getWard(String wardId) {
        Map<String, Object> ward = wards.get(wardId);
        return ward != null ? Collections.unmodifiableMap(ward) : null;
    }

    public Map<String, Object> getAnnouncement(String announcementId) {
        Map<String, Object> announcement = announcements.get(announcementId);
        return announcement != null ? Collections.unmodifiableMap(announcement) : null;
    }

    public Object getAuditSetting(String key) {
        return auditSettings.get(key);
    }

    public Object getAuditSetting(String key, Object def) {
        return auditSettings.getOrDefault(key, def);
    }

    public Map<String, Object> getRootSettings() {
        return rootSettings;
    }

    public Map<String, Object> getAuditSettings() {
        return auditSettings;
    }

    public List<TierDefinition> getTiers() {
        return tiers;
    }

    public ValidationReport getValidationReport() {
        return validationReport;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public boolean isLoaded() {
        return !tiers.isEmpty() || !rootSettings.isEmpty();
    }

    public boolean hasValidationErrors() {
        return validationReport != null && validationReport.hasErrors();
    }

    public Optional<String> findMessageString(String dottedKey) {
        Map<String, Object> settings = messageSettings.get(dottedKey);
        if (settings == null) return Optional.empty();
        Object msg = settings.get("message");
        return Optional.ofNullable(msg == null ? null : msg.toString());
    }

    public List<String> findMessageLines(String dottedKey) {
        Map<String, Object> settings = messageSettings.get(dottedKey);
        if (settings == null) return Collections.emptyList();
        Object lines = settings.get("lines");
        if (lines instanceof List) {
            List<String> result = new ArrayList<>();
            for (Object line : (List<?>) lines) {
                if (line != null) result.add(line.toString());
            }
            return result;
        }
        return Collections.emptyList();
    }

    public Set<String> getStationProfileIds() {
        return stationProfiles.keySet();
    }

    private static Object normalize(Object value) {
        if (value instanceof org.bukkit.configuration.ConfigurationSection) {
            org.bukkit.configuration.ConfigurationSection section =
                    (org.bukkit.configuration.ConfigurationSection) value;
            Map<String, Object> normalized = new HashMap<>();
            for (String key : section.getKeys(false)) {
                normalized.put(key, normalize(section.get(key)));
            }
            return Collections.unmodifiableMap(normalized);
        } else if (value instanceof java.util.List) {
            java.util.List<?> list = (java.util.List<?>) value;
            java.util.List<Object> normalized = new java.util.ArrayList<>();
            for (Object item : list) {
                normalized.add(item != null ? normalize(item) : null);
            }
            return Collections.unmodifiableList(normalized);
        } else if (value instanceof Map) {
            Map<?, ?> map = (Map<?, ?>) value;
            Map<String, Object> normalized = new HashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                normalized.put(String.valueOf(entry.getKey()),
                        entry.getValue() != null ? normalize(entry.getValue()) : null);
            }
            return Collections.unmodifiableMap(normalized);
        }
        return value;
    }

    private static Map<String, Map<String, Object>> deepCopy(Map<String, Map<String, Object>> source) {
        Map<String, Map<String, Object>> copy = new HashMap<>();
        for (Map.Entry<String, Map<String, Object>> entry : source.entrySet()) {
            Map<String, Object> innerCopy = new HashMap<>();
            if (entry.getValue() != null) {
                for (Map.Entry<String, Object> valEntry : entry.getValue().entrySet()) {
                    innerCopy.put(valEntry.getKey(), normalize(valEntry.getValue()));
                }
            }
            copy.put(entry.getKey(), innerCopy);
        }
        return copy;
    }

    public static final class Builder {
        private final Map<String, Object> rootSettings = new HashMap<>();
        private final Map<String, Map<String, Object>> menuSettings = new HashMap<>();
        private final Map<String, Map<String, Object>> messageSettings = new HashMap<>();
        private final Map<String, Map<String, Object>> stationProfiles = new HashMap<>();
        private final Map<String, Map<String, Object>> itemGroups = new HashMap<>();
        private final Map<String, Map<String, Object>> catalysts = new HashMap<>();
        private final Map<String, Map<String, Object>> wards = new HashMap<>();
        private final Map<String, Map<String, Object>> announcements = new HashMap<>();
        private final Map<String, Object> auditSettings = new HashMap<>();
        private List<com.arkflame.flameforge.model.TierDefinition> tiers = Collections.emptyList();
        private ValidationReport validationReport = new ValidationReport();

        public Builder putRoot(String key, Object value) {
            rootSettings.put(key, value);
            return this;
        }

        public Builder putRootAll(Map<String, Object> values) {
            if (values != null) {
                rootSettings.putAll(values);
            }
            return this;
        }

        public Builder putMenu(String menuId, Map<String, Object> settings) {
            if (settings != null) {
                Map<String, Object> normalized = new HashMap<>();
                for (Map.Entry<String, Object> entry : settings.entrySet()) {
                    normalized.put(entry.getKey(),
                            entry.getValue() != null ? normalize(entry.getValue()) : null);
                }
                menuSettings.put(menuId, normalized);
            }
            return this;
        }

        public Builder mergeMenu(String menuId, Map<String, Object> operatorValues) {
            if (operatorValues == null) {
                return this;
            }
            Map<String, Object> existing = menuSettings.get(menuId);
            Map<String, Object> merged = existing != null
                    ? recursiveMerge(existing, operatorValues)
                    : operatorValues;
            return putMenu(menuId, merged);
        }

        private Map<String, Object> recursiveMerge(Map<String, Object> baseline, Map<String, Object> overlay) {
            Map<String, Object> result = new HashMap<>(baseline);
            for (Map.Entry<String, Object> entry : overlay.entrySet()) {
                String key = entry.getKey();
                Object overlayValue = toMapOrList(entry.getValue());
                Object baselineValue = baseline.get(key);
                if (overlayValue == null) {
                    continue;
                } else if (baselineValue instanceof Map && overlayValue instanceof Map) {
                    result.put(key, recursiveMerge(
                            (Map<String, Object>) baselineValue,
                            (Map<String, Object>) overlayValue));
                } else if (overlayValue instanceof List) {
                    result.put(key, normalizeList((List<?>) overlayValue));
                } else {
                    result.put(key, overlayValue);
                }
            }
            return result;
        }

        private Object toMapOrList(Object value) {
            if (value instanceof org.bukkit.configuration.ConfigurationSection) {
                org.bukkit.configuration.ConfigurationSection section =
                        (org.bukkit.configuration.ConfigurationSection) value;
                Map<String, Object> result = new HashMap<>();
                for (String key : section.getKeys(false)) {
                    result.put(key, toMapOrList(section.get(key)));
                }
                return result;
            } else if (value instanceof Map) {
                return value;
            } else if (value instanceof List) {
                return value;
            }
            return value;
        }

        private List<?> normalizeList(List<?> list) {
            List<Object> result = new ArrayList<>();
            for (Object item : list) {
                result.add(item != null ? normalize(item) : null);
            }
            return result;
        }

        public Builder putMessage(String messageId, Map<String, Object> settings) {
            if (settings != null) {
                Map<String, Object> copy = new HashMap<>(settings);
                messageSettings.put(messageId, copy);
            }
            return this;
        }

        public Builder putStationProfile(String profileId, Map<String, Object> settings) {
            if (settings != null) {
                Map<String, Object> copy = new HashMap<>(settings);
                stationProfiles.put(profileId, copy);
            }
            return this;
        }

        public Builder putItemGroup(String groupId, Map<String, Object> settings) {
            if (settings != null) {
                Map<String, Object> copy = new HashMap<>(settings);
                itemGroups.put(groupId, copy);
            }
            return this;
        }

        public Builder putCatalyst(String catalystId, Map<String, Object> settings) {
            if (settings != null) {
                Map<String, Object> copy = new HashMap<>(settings);
                catalysts.put(catalystId, copy);
            }
            return this;
        }

        public Builder putWard(String wardId, Map<String, Object> settings) {
            if (settings != null) {
                Map<String, Object> copy = new HashMap<>(settings);
                wards.put(wardId, copy);
            }
            return this;
        }

        public Builder putAnnouncement(String announcementId, Map<String, Object> settings) {
            if (settings != null) {
                Map<String, Object> copy = new HashMap<>(settings);
                announcements.put(announcementId, copy);
            }
            return this;
        }

        public Builder putAudit(String key, Object value) {
            auditSettings.put(key, value);
            return this;
        }

        public Builder putAuditAll(Map<String, Object> values) {
            if (values != null) {
                auditSettings.putAll(values);
            }
            return this;
        }

        public Builder tiers(List<com.arkflame.flameforge.model.TierDefinition> tiers) {
            this.tiers = tiers != null ? tiers : Collections.emptyList();
            return this;
        }

        public Builder validationReport(ValidationReport report) {
            this.validationReport = report != null ? report : new ValidationReport();
            return this;
        }

        public ConfigSnapshot build() {
            return new ConfigSnapshot(this);
        }
    }
}
