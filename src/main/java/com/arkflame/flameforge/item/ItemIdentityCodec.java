package com.arkflame.flameforge.item;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public final class ItemIdentityCodec {
    private static final int SCHEMA_VERSION = 2;
    private static final String MODERN_KEY = "flameforge:state";
    private static final String LEGACY_MARKER = "\u00A70\u00A70FLAMEFORGE:v2:";

    public static final class Identity {
        private final int schemaVersion;
        private final int currentTier;
        private final int highestTier;
        private final int reforgeCount;
        private final boolean cursed;
        private final String lastTierId;
        private final String lastVariantId;
        private final UUID forgeId;
        private final String baseMaterial;
        private final String baseDisplayName;
        private final Map<String, Integer> originalEnchantments;
        private final Map<String, Integer> forgeEnchantments;
        private final List<String> activeAttributeIds;
        private final List<String> activePowerIds;

        private Identity(int schemaVersion, int currentTier, int highestTier, int reforgeCount,
                        boolean cursed, String lastTierId, String lastVariantId, UUID forgeId,
                        String baseMaterial, String baseDisplayName,
                        Map<String, Integer> originalEnchantments,
                        Map<String, Integer> forgeEnchantments,
                        List<String> activeAttributeIds, List<String> activePowerIds) {
            this.schemaVersion = schemaVersion;
            this.currentTier = currentTier;
            this.highestTier = highestTier;
            this.reforgeCount = reforgeCount;
            this.cursed = cursed;
            this.lastTierId = lastTierId;
            this.lastVariantId = lastVariantId;
            this.forgeId = forgeId;
            this.baseMaterial = baseMaterial;
            this.baseDisplayName = baseDisplayName;
            this.originalEnchantments = originalEnchantments != null ?
                Collections.unmodifiableMap(new HashMap<>(originalEnchantments)) :
                Collections.emptyMap();
            this.forgeEnchantments = forgeEnchantments != null ?
                Collections.unmodifiableMap(new HashMap<>(forgeEnchantments)) :
                Collections.emptyMap();
            this.activeAttributeIds = activeAttributeIds != null ?
                Collections.unmodifiableList(new java.util.ArrayList<>(activeAttributeIds)) :
                Collections.emptyList();
            this.activePowerIds = activePowerIds != null ?
                Collections.unmodifiableList(new java.util.ArrayList<>(activePowerIds)) :
                Collections.emptyList();
        }

        public static Identity empty() {
            return new Identity(SCHEMA_VERSION, 0, 0, 0, false, null, null,
                UUID.randomUUID(), null, null,
                Collections.emptyMap(), Collections.emptyMap(),
                Collections.emptyList(), Collections.emptyList());
        }

        public Identity withCurrentTier(int tier) {
            return new Identity(schemaVersion, tier, Math.max(highestTier, tier), reforgeCount,
                cursed, lastTierId, lastVariantId, forgeId, baseMaterial, baseDisplayName,
                originalEnchantments, forgeEnchantments, activeAttributeIds, activePowerIds);
        }

        public Identity withHighestTier(int tier) {
            return new Identity(schemaVersion, currentTier, Math.max(highestTier, tier), reforgeCount,
                cursed, lastTierId, lastVariantId, forgeId, baseMaterial, baseDisplayName,
                originalEnchantments, forgeEnchantments, activeAttributeIds, activePowerIds);
        }

        public Identity withReforgeCount(int count) {
            return new Identity(schemaVersion, currentTier, highestTier, count,
                cursed, lastTierId, lastVariantId, forgeId, baseMaterial, baseDisplayName,
                originalEnchantments, forgeEnchantments, activeAttributeIds, activePowerIds);
        }

        public Identity withCursed(boolean cursed) {
            return new Identity(schemaVersion, currentTier, highestTier, reforgeCount,
                cursed, lastTierId, lastVariantId, forgeId, baseMaterial, baseDisplayName,
                originalEnchantments, forgeEnchantments, activeAttributeIds, activePowerIds);
        }

        public Identity withLastTierId(String tierId) {
            return new Identity(schemaVersion, currentTier, highestTier, reforgeCount,
                cursed, tierId, lastVariantId, forgeId, baseMaterial, baseDisplayName,
                originalEnchantments, forgeEnchantments, activeAttributeIds, activePowerIds);
        }

        public Identity withLastVariantId(String variantId) {
            return new Identity(schemaVersion, currentTier, highestTier, reforgeCount,
                cursed, lastTierId, variantId, forgeId, baseMaterial, baseDisplayName,
                originalEnchantments, forgeEnchantments, activeAttributeIds, activePowerIds);
        }

        public Identity withForgeId(UUID id) {
            return new Identity(schemaVersion, currentTier, highestTier, reforgeCount,
                cursed, lastTierId, lastVariantId, id, baseMaterial, baseDisplayName,
                originalEnchantments, forgeEnchantments, activeAttributeIds, activePowerIds);
        }

        public Identity withBaseMaterial(String material) {
            return new Identity(schemaVersion, currentTier, highestTier, reforgeCount,
                cursed, lastTierId, lastVariantId, forgeId, material, baseDisplayName,
                originalEnchantments, forgeEnchantments, activeAttributeIds, activePowerIds);
        }

        public Identity withBaseDisplayName(String name) {
            return new Identity(schemaVersion, currentTier, highestTier, reforgeCount,
                cursed, lastTierId, lastVariantId, forgeId, baseMaterial, name,
                originalEnchantments, forgeEnchantments, activeAttributeIds, activePowerIds);
        }

        public Identity withOriginalEnchantments(Map<String, Integer> enchants) {
            return new Identity(schemaVersion, currentTier, highestTier, reforgeCount,
                cursed, lastTierId, lastVariantId, forgeId, baseMaterial, baseDisplayName,
                enchants, forgeEnchantments, activeAttributeIds, activePowerIds);
        }

        public Identity withForgeEnchantments(Map<String, Integer> enchants) {
            return new Identity(schemaVersion, currentTier, highestTier, reforgeCount,
                cursed, lastTierId, lastVariantId, forgeId, baseMaterial, baseDisplayName,
                originalEnchantments, enchants, activeAttributeIds, activePowerIds);
        }

        public Identity withActiveAttributeIds(List<String> ids) {
            return new Identity(schemaVersion, currentTier, highestTier, reforgeCount,
                cursed, lastTierId, lastVariantId, forgeId, baseMaterial, baseDisplayName,
                originalEnchantments, forgeEnchantments, ids, activePowerIds);
        }

        public Identity withActivePowerIds(List<String> ids) {
            return new Identity(schemaVersion, currentTier, highestTier, reforgeCount,
                cursed, lastTierId, lastVariantId, forgeId, baseMaterial, baseDisplayName,
                originalEnchantments, forgeEnchantments, activeAttributeIds, ids);
        }

        public int getSchemaVersion() { return schemaVersion; }
        public int getCurrentTier() { return currentTier; }
        public int getHighestTier() { return highestTier; }
        public int getReforgeCount() { return reforgeCount; }
        public boolean isCursed() { return cursed; }
        public String getLastTierId() { return lastTierId; }
        public String getLastVariantId() { return lastVariantId; }
        public UUID getForgeId() { return forgeId; }
        public String getBaseMaterial() { return baseMaterial; }
        public String getBaseDisplayName() { return baseDisplayName; }
        public Map<String, Integer> getOriginalEnchantments() { return originalEnchantments; }
        public Map<String, Integer> getForgeEnchantments() { return forgeEnchantments; }
        public List<String> getActiveAttributeIds() { return activeAttributeIds; }
        public List<String> getActivePowerIds() { return activePowerIds; }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Identity)) return false;
            Identity that = (Identity) o;
            return schemaVersion == that.schemaVersion &&
                currentTier == that.currentTier &&
                highestTier == that.highestTier &&
                reforgeCount == that.reforgeCount &&
                cursed == that.cursed &&
                Objects.equals(forgeId, that.forgeId) &&
                Objects.equals(lastTierId, that.lastTierId) &&
                Objects.equals(lastVariantId, that.lastVariantId) &&
                Objects.equals(baseMaterial, that.baseMaterial) &&
                Objects.equals(baseDisplayName, that.baseDisplayName) &&
                Objects.equals(originalEnchantments, that.originalEnchantments) &&
                Objects.equals(forgeEnchantments, that.forgeEnchantments) &&
                Objects.equals(activeAttributeIds, that.activeAttributeIds) &&
                Objects.equals(activePowerIds, that.activePowerIds);
        }

        @Override
        public int hashCode() {
            return Objects.hash(schemaVersion, currentTier, highestTier, reforgeCount,
                cursed, forgeId, lastTierId, lastVariantId, baseMaterial, baseDisplayName,
                originalEnchantments, forgeEnchantments, activeAttributeIds, activePowerIds);
        }

        @Override
        public String toString() {
            return "Identity{schemaVersion=" + schemaVersion + ", currentTier=" + currentTier +
                ", highestTier=" + highestTier + ", reforgeCount=" + reforgeCount +
                ", cursed=" + cursed + ", forgeId=" + forgeId +
                ", lastTierId=" + lastTierId + ", lastVariantId=" + lastVariantId +
                ", baseMaterial=" + baseMaterial + ", baseDisplayName=" + baseDisplayName + "}";
        }
    }

    public enum DecodeResult {
        VALID,
        INVALID_IDENTITY,
        LEGACY_MIGRATED
    }

    public static final class Decoded {
        private final Identity identity;
        private final DecodeResult result;

        private Decoded(Identity identity, DecodeResult result) {
            this.identity = identity;
            this.result = result;
        }

        public static Decoded valid(Identity identity) {
            return new Decoded(identity, DecodeResult.VALID);
        }

        public static Decoded invalid() {
            return new Decoded(Identity.empty(), DecodeResult.INVALID_IDENTITY);
        }

        public static Decoded migrated(Identity identity) {
            return new Decoded(identity, DecodeResult.LEGACY_MIGRATED);
        }

        public Identity getIdentity() { return identity; }
        public DecodeResult getResult() { return result; }
        public boolean isValid() { return result == DecodeResult.VALID || result == DecodeResult.LEGACY_MIGRATED; }
    }

    public Decoded decodeFromString(String payload) {
        if (payload == null || payload.isEmpty()) {
            return Decoded.invalid();
        }
        try {
            byte[] bytes = Base64.getUrlDecoder().decode(payload);
            String json = new String(bytes, StandardCharsets.UTF_8);
            return decodeFromJson(json);
        } catch (IllegalArgumentException e) {
            return Decoded.invalid();
        }
    }

    private Decoded decodeFromJson(String json) {
        if (json == null || json.isEmpty()) {
            return Decoded.invalid();
        }
        try {
            int schemaVersion = readInt(json, "schemaVersion");
            if (schemaVersion != SCHEMA_VERSION) {
                return Decoded.invalid();
            }

            int currentTier = readInt(json, "currentTier");
            int highestTier = readInt(json, "highestTier");
            int reforgeCount = readInt(json, "reforgeCount");
            boolean cursed = readBool(json, "cursed");
            String lastTierId = readString(json, "lastTierId");
            String lastVariantId = readString(json, "lastVariantId");
            String forgeIdStr = readString(json, "forgeId");
            UUID forgeId = forgeIdStr != null ? UUID.fromString(forgeIdStr) : UUID.randomUUID();
            String baseMaterial = readString(json, "baseMaterial");
            String baseDisplayName = readString(json, "baseDisplayName");
            Map<String, Integer> originalEnchantments = readIntMap(json, "originalEnchantments");
            Map<String, Integer> forgeEnchantments = readIntMap(json, "forgeEnchantments");
            List<String> activeAttributeIds = readStringList(json, "activeAttributeIds");
            List<String> activePowerIds = readStringList(json, "activePowerIds");

            Identity identity = new Identity(schemaVersion, currentTier, highestTier, reforgeCount,
                cursed, lastTierId, lastVariantId, forgeId, baseMaterial, baseDisplayName,
                originalEnchantments, forgeEnchantments, activeAttributeIds, activePowerIds);

            return Decoded.valid(identity);
        } catch (Exception e) {
            return Decoded.invalid();
        }
    }

    public String encodeToString(Identity identity) {
        if (identity == null) {
            return "";
        }
        String json = encodeToJson(identity);
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String encodeToJson(Identity identity) {
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        sb.append("\"schemaVersion\":").append(identity.schemaVersion).append(",");
        sb.append("\"currentTier\":").append(identity.currentTier).append(",");
        sb.append("\"highestTier\":").append(identity.highestTier).append(",");
        sb.append("\"reforgeCount\":").append(identity.reforgeCount).append(",");
        sb.append("\"cursed\":").append(identity.cursed).append(",");
        sb.append("\"lastTierId\":").append(escape(identity.lastTierId)).append(",");
        sb.append("\"lastVariantId\":").append(escape(identity.lastVariantId)).append(",");
        sb.append("\"forgeId\":").append(escape(identity.forgeId != null ? identity.forgeId.toString() : null)).append(",");
        sb.append("\"baseMaterial\":").append(escape(identity.baseMaterial)).append(",");
        sb.append("\"baseDisplayName\":").append(escape(identity.baseDisplayName)).append(",");
        sb.append("\"originalEnchantments\":").append(mapToJson(identity.originalEnchantments)).append(",");
        sb.append("\"forgeEnchantments\":").append(mapToJson(identity.forgeEnchantments)).append(",");
        sb.append("\"activeAttributeIds\":").append(listToJson(identity.activeAttributeIds)).append(",");
        sb.append("\"activePowerIds\":").append(listToJson(identity.activePowerIds));
        sb.append("}");
        return sb.toString();
    }

    private String escape(String s) {
        if (s == null) return "null";
        StringBuilder sb = new StringBuilder();
        sb.append("\"");
        for (char c : s.toCharArray()) {
            switch (c) {
                case '"': sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default: sb.append(c); break;
            }
        }
        sb.append("\"");
        return sb.toString();
    }

    private String mapToJson(Map<String, Integer> map) {
        if (map == null || map.isEmpty()) return "{}";
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Integer> entry : map.entrySet()) {
            if (!first) sb.append(",");
            first = false;
            sb.append(escape(entry.getKey())).append(":").append(entry.getValue());
        }
        sb.append("}");
        return sb.toString();
    }

    private String listToJson(List<String> list) {
        if (list == null || list.isEmpty()) return "[]";
        StringBuilder sb = new StringBuilder("[");
        boolean first = true;
        for (String s : list) {
            if (!first) sb.append(",");
            first = false;
            sb.append(escape(s));
        }
        sb.append("]");
        return sb.toString();
    }

    private int readInt(String json, String key) {
        String pattern = "\"" + key + "\":";
        int idx = json.indexOf(pattern);
        if (idx < 0) return 0;
        int start = idx + pattern.length();
        int end = start;
        while (end < json.length()) {
            char c = json.charAt(end);
            if (c == ',' || c == '}' || c == ' ' || c == '\n' || c == '\r' || c == '\t') break;
            end++;
        }
        String val = json.substring(start, end).trim();
        try {
            return Integer.parseInt(val);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private boolean readBool(String json, String key) {
        String pattern = "\"" + key + "\":";
        int idx = json.indexOf(pattern);
        if (idx < 0) return false;
        int start = idx + pattern.length();
        int end = start;
        while (end < json.length()) {
            char c = json.charAt(end);
            if (c == ',' || c == '}' || c == ' ' || c == '\n' || c == '\r' || c == '\t') break;
            end++;
        }
        String val = json.substring(start, end).trim();
        return "true".equalsIgnoreCase(val);
    }

    private String readString(String json, String key) {
        String pattern = "\"" + key + "\":";
        int idx = json.indexOf(pattern);
        if (idx < 0) return null;
        int start = idx + pattern.length();
        if (start >= json.length() || json.charAt(start) != '"') return null;
        start++;
        int end = start;
        boolean escaped = false;
        while (end < json.length()) {
            char c = json.charAt(end);
            if (escaped) {
                escaped = false;
            } else if (c == '\\') {
                escaped = true;
            } else if (c == '"') {
                break;
            }
            end++;
        }
        String val = json.substring(start, end);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < val.length(); i++) {
            char c = val.charAt(i);
            if (c == '\\' && i + 1 < val.length()) {
                char next = val.charAt(++i);
                switch (next) {
                    case '"': sb.append('"'); break;
                    case '\\': sb.append('\\'); break;
                    case 'n': sb.append('\n'); break;
                    case 'r': sb.append('\r'); break;
                    case 't': sb.append('\t'); break;
                    default: sb.append(next); break;
                }
            } else {
                sb.append(c);
            }
        }
        return sb.length() == 0 ? null : sb.toString();
    }

    private Map<String, Integer> readIntMap(String json, String key) {
        String pattern = "\"" + key + "\":";
        int idx = json.indexOf(pattern);
        if (idx < 0) return Collections.emptyMap();
        int start = json.indexOf("{", idx);
        if (start < 0) return Collections.emptyMap();
        int end = findMatchingBrace(json, start);
        if (end < 0) return Collections.emptyMap();
        String mapContent = json.substring(start + 1, end);
        Map<String, Integer> result = new HashMap<>();
        if (mapContent.trim().isEmpty()) return result;
        int pos = 0;
        while (pos < mapContent.length()) {
            int keyStart = mapContent.indexOf("\"", pos);
            if (keyStart < 0) break;
            int keyEnd = findClosingQuote(mapContent, keyStart + 1);
            if (keyEnd < 0) break;
            String mapKey = mapContent.substring(keyStart + 1, keyEnd);
            int colonIdx = mapContent.indexOf(":", keyEnd);
            if (colonIdx < 0) break;
            int commaIdx = mapContent.indexOf(",", colonIdx);
            String numStr = commaIdx > colonIdx ?
                mapContent.substring(colonIdx + 1, commaIdx).trim() :
                mapContent.substring(colonIdx + 1).trim();
            try {
                int val = Integer.parseInt(numStr);
                result.put(mapKey, val);
            } catch (NumberFormatException ignored) {}
            pos = commaIdx > 0 ? commaIdx + 1 : mapContent.length();
        }
        return result;
    }

    private List<String> readStringList(String json, String key) {
        String pattern = "\"" + key + "\":";
        int idx = json.indexOf(pattern);
        if (idx < 0) return Collections.emptyList();
        int start = json.indexOf("[", idx);
        if (start < 0) return Collections.emptyList();
        int end = findMatchingBracket(json, start);
        if (end < 0) return Collections.emptyList();
        String listContent = json.substring(start + 1, end);
        List<String> result = new java.util.ArrayList<>();
        if (listContent.trim().isEmpty()) return result;
        int pos = 0;
        while (pos < listContent.length()) {
            int strStart = listContent.indexOf("\"", pos);
            if (strStart < 0) break;
            int strEnd = findClosingQuote(listContent, strStart + 1);
            if (strEnd < 0) break;
            result.add(listContent.substring(strStart + 1, strEnd));
            int commaIdx = listContent.indexOf(",", strEnd);
            pos = commaIdx > 0 ? commaIdx + 1 : listContent.length();
        }
        return result;
    }

    private int findClosingQuote(String s, int start) {
        boolean escaped = false;
        for (int i = start; i < s.length(); i++) {
            char c = s.charAt(i);
            if (escaped) {
                escaped = false;
            } else if (c == '\\') {
                escaped = true;
            } else if (c == '"') {
                return i;
            }
        }
        return -1;
    }

    private int findMatchingBrace(String s, int start) {
        int depth = 1;
        boolean escaped = false;
        for (int i = start + 1; i < s.length(); i++) {
            char c = s.charAt(i);
            if (escaped) {
                escaped = false;
            } else if (c == '\\') {
                escaped = true;
            } else if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0) return i;
            }
        }
        return -1;
    }

    private int findMatchingBracket(String s, int start) {
        int depth = 1;
        for (int i = start + 1; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '[') depth++;
            else if (c == ']') {
                depth--;
                if (depth == 0) return i;
            }
        }
        return -1;
    }

    public String getModernKey() {
        return MODERN_KEY;
    }

    public String getLegacyMarker() {
        return LEGACY_MARKER;
    }

    public int maxEncodedLength() {
        return 4000;
    }
}
