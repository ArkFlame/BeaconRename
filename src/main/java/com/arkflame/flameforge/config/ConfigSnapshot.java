package com.arkflame.flameforge.config;

import com.arkflame.flameforge.model.TierDefinition;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

    public Map<String, Object> getMenuSettings(String menuId) {
        Map<String, Object> menu = menuSettings.get(menuId);
        return menu != null ? Collections.unmodifiableMap(menu) : null;
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

    private Map<String, Map<String, Object>> deepCopy(Map<String, Map<String, Object>> source) {
        Map<String, Map<String, Object>> copy = new HashMap<>();
        for (Map.Entry<String, Map<String, Object>> entry : source.entrySet()) {
            Map<String, Object> innerCopy = new HashMap<>();
            if (entry.getValue() != null) {
                innerCopy.putAll(entry.getValue());
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
                Map<String, Object> copy = new HashMap<>(settings);
                menuSettings.put(menuId, copy);
            }
            return this;
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
