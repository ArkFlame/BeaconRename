package com.arkflame.flameforge.item;

import com.arkflame.flameforge.model.ForgeHistory;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class ItemIdentityService {
    private static final ItemIdentityService INSTANCE = new ItemIdentityService();
    private static final String LEGACY_PREFIX = "\u00A70\u00A70FLAMEFORGE:";
    private static final String KEY_REFORGE_COUNT = "reforge_count";
    private static final String KEY_HIGHEST_TIER = "highest_tier";
    private static final String KEY_LAST_TIER = "last_tier";
    private static final String KEY_LAST_OUTCOME = "last_outcome";
    private static final String KEY_FORGE_ID = "forge_id";

    private volatile Boolean modernPdcAvailable;
    private Method getPdcMethod;
    private Method pdcSetMethod;
    private Method pdcGetMethod;
    private Class<?> namespacedKeyClass;
    private Class<?> pdcTypeClass;
    private Object integerType;
    private Object stringType;

    private ItemIdentityService() {
        initReflection();
    }

    private void initReflection() {
        try {
            namespacedKeyClass = Class.forName("org.bukkit.NamespacedKey");
            pdcTypeClass = Class.forName("org.bukkit.persistence.PersistentDataType");
            integerType = pdcTypeClass.getField("INTEGER").get(null);
            stringType = pdcTypeClass.getField("STRING").get(null);
            getPdcMethod = ItemMeta.class.getMethod("getPersistentDataContainer");
            Class<?> pdcClass = getPdcMethod.getReturnType();
            pdcSetMethod = pdcClass.getMethod("set", namespacedKeyClass, pdcTypeClass, Object.class);
            pdcGetMethod = pdcClass.getMethod("get", namespacedKeyClass, pdcTypeClass);
            modernPdcAvailable = true;
        } catch (Exception e) {
            modernPdcAvailable = false;
        }
    }

    public static ItemIdentityService getInstance() {
        return INSTANCE;
    }

    public Optional<ItemStack> writeIdentity(final ItemStack item, final IdentityData data) {
        if (item == null) {
            return Optional.empty();
        }
        final ItemStack clone = item.clone();
        if (Boolean.TRUE.equals(modernPdcAvailable)) {
            final boolean written = writeIdentityToPdc(clone, data);
            if (!written) {
                return Optional.empty();
            }
        }
        if (!writeLegacyLore(clone, data)) {
            return Optional.empty();
        }
        return Optional.of(clone);
    }

    public Optional<IdentityData> readIdentity(final ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return Optional.empty();
        }
        if (Boolean.TRUE.equals(modernPdcAvailable)) {
            final IdentityData data = readIdentityFromPdc(item);
            if (data != null) {
                return Optional.of(data);
            }
        }
        return readFromLegacyLore(item);
    }

    private boolean writeIdentityToPdc(final ItemStack item, final IdentityData data) {
        try {
            final ItemMeta meta = item.getItemMeta();
            if (meta == null) {
                return false;
            }
            final Object pdc = getPdcMethod.invoke(meta);
            if (pdc == null) {
                return false;
            }
            final Object countKey = namespacedKeyClass.getConstructor(String.class, String.class).newInstance("flameforge", KEY_REFORGE_COUNT);
            pdcSetMethod.invoke(pdc, countKey, integerType, data.getReforgeCount());
            final Object tierKey = namespacedKeyClass.getConstructor(String.class, String.class).newInstance("flameforge", KEY_HIGHEST_TIER);
            pdcSetMethod.invoke(pdc, tierKey, integerType, data.getHighestTier());
            if (data.getLastTier() != null) {
                final Object key = namespacedKeyClass.getConstructor(String.class, String.class).newInstance("flameforge", KEY_LAST_TIER);
                pdcSetMethod.invoke(pdc, key, stringType, data.getLastTier());
            }
            if (data.getLastOutcome() != null) {
                final Object key = namespacedKeyClass.getConstructor(String.class, String.class).newInstance("flameforge", KEY_LAST_OUTCOME);
                pdcSetMethod.invoke(pdc, key, stringType, data.getLastOutcome());
            }
            if (data.getForgeId() != null) {
                final Object key = namespacedKeyClass.getConstructor(String.class, String.class).newInstance("flameforge", KEY_FORGE_ID);
                pdcSetMethod.invoke(pdc, key, stringType, data.getForgeId().toString());
            }
            item.setItemMeta(meta);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private IdentityData readIdentityFromPdc(final ItemStack item) {
        try {
            final ItemMeta meta = item.getItemMeta();
            if (meta == null) {
                return null;
            }
            final Object pdc = getPdcMethod.invoke(meta);
            if (pdc == null) {
                return null;
            }
            final Object countKey = namespacedKeyClass.getConstructor(String.class, String.class).newInstance("flameforge", KEY_REFORGE_COUNT);
            final Object tierKey = namespacedKeyClass.getConstructor(String.class, String.class).newInstance("flameforge", KEY_HIGHEST_TIER);
            final Object lastTierKey = namespacedKeyClass.getConstructor(String.class, String.class).newInstance("flameforge", KEY_LAST_TIER);
            final Object lastOutcomeKey = namespacedKeyClass.getConstructor(String.class, String.class).newInstance("flameforge", KEY_LAST_OUTCOME);
            final Object forgeIdKey = namespacedKeyClass.getConstructor(String.class, String.class).newInstance("flameforge", KEY_FORGE_ID);
            final Object countVal = pdcGetMethod.invoke(pdc, countKey, integerType);
            final Object tierVal = pdcGetMethod.invoke(pdc, tierKey, integerType);
            final Object lastTierVal = pdcGetMethod.invoke(pdc, lastTierKey, stringType);
            final Object lastOutcomeVal = pdcGetMethod.invoke(pdc, lastOutcomeKey, stringType);
            final Object forgeIdVal = pdcGetMethod.invoke(pdc, forgeIdKey, stringType);
            final int reforgeCount = countVal != null ? ((Number) countVal).intValue() : 0;
            final int highestTier = tierVal != null ? ((Number) tierVal).intValue() : 0;
            final String lastTier = lastTierVal != null ? (String) lastTierVal : null;
            final String lastOutcome = lastOutcomeVal != null ? (String) lastOutcomeVal : null;
            final UUID forgeId = forgeIdVal != null ? UUID.fromString((String) forgeIdVal) : UUID.randomUUID();
            return new IdentityData(reforgeCount, highestTier, lastTier, lastOutcome, forgeId);
        } catch (Exception e) {
            return null;
        }
    }

    private Optional<IdentityData> readFromLegacyLore(final ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return Optional.empty();
        }
        final ItemMeta meta = item.getItemMeta();
        if (!meta.hasLore()) {
            return Optional.empty();
        }
        final List<String> lore = meta.getLore();
        if (lore == null || lore.isEmpty()) {
            return Optional.empty();
        }
        for (final String line : lore) {
            if (line != null && line.startsWith(LEGACY_PREFIX)) {
                return Optional.of(parseLegacyLine(line));
            }
        }
        return Optional.empty();
    }

    private boolean writeLegacyLore(final ItemStack item, final IdentityData data) {
        if (item == null || !item.hasItemMeta()) {
            return false;
        }
        final ItemMeta meta = item.getItemMeta();
        List<String> lore;
        try {
            lore = meta.getLore();
        } catch (Exception e) {
            return false;
        }
        if (lore == null) {
            lore = new ArrayList<>();
        } else {
            lore = new ArrayList<>(lore);
        }
        final String markerLine = formatLegacyLine(data);
        replaceOrAppendLegacyMarker(lore, markerLine);
        try {
            meta.setLore(lore);
            item.setItemMeta(meta);
        } catch (Exception e) {
            return false;
        }
        return true;
    }

    private boolean replaceOrAppendLegacyMarker(final List<String> lore, final String markerLine) {
        boolean found = false;
        for (int i = 0; i < lore.size(); i++) {
            final String line = lore.get(i);
            if (line != null && line.startsWith(LEGACY_PREFIX)) {
                lore.set(i, markerLine);
                found = true;
                break;
            }
        }
        if (!found) {
            lore.add(markerLine);
        }
        return true;
    }

    private String formatLegacyLine(final IdentityData data) {
        return LEGACY_PREFIX + data.getReforgeCount() + "|"
                + data.getHighestTier() + "|"
                + (data.getLastTier() != null ? data.getLastTier() : "") + "|"
                + (data.getLastOutcome() != null ? data.getLastOutcome() : "") + "|"
                + data.getForgeId().toString();
    }

    private IdentityData parseLegacyLine(final String line) {
        final String content = line.substring(LEGACY_PREFIX.length());
        final String[] parts = content.split("\\|", -1);
        try {
            final int reforgeCount = parts.length > 0 && !parts[0].isEmpty()
                    ? Integer.parseInt(parts[0]) : 0;
            final int highestTier = parts.length > 1 && !parts[1].isEmpty()
                    ? Integer.parseInt(parts[1]) : 0;
            final String lastTier = parts.length > 2 ? parts[2] : null;
            final String lastOutcome = parts.length > 3 ? parts[3] : null;
            final UUID forgeId = parts.length > 4 && !parts[4].isEmpty()
                    ? UUID.fromString(parts[4]) : UUID.randomUUID();
            return new IdentityData(reforgeCount, highestTier, lastTier, lastOutcome, forgeId);
        } catch (Exception e) {
            return new IdentityData(0, 0, null, null, UUID.randomUUID());
        }
    }

    public Optional<String> getMaterialGroup(final Material material) {
        if (material == null) {
            return Optional.empty();
        }
        final String name = material.name();
        if (name.endsWith("_SWORD")) return Optional.of("sword");
        if (name.endsWith("_AXE")) return Optional.of("axe");
        if (name.endsWith("_PICKAXE")) return Optional.of("pickaxe");
        if (name.endsWith("_SHOVEL")) return Optional.of("shovel");
        if (name.endsWith("_HOE")) return Optional.of("hoe");
        if (name.endsWith("_HELMET")) return Optional.of("helmet");
        if (name.endsWith("_CHESTPLATE")) return Optional.of("chestplate");
        if (name.endsWith("_LEGGINGS")) return Optional.of("leggings");
        if (name.endsWith("_BOOTS")) return Optional.of("boots");
        if (name.endsWith("_HORSE_ARMOR")) return Optional.of("horse_armor");
        if (name.contains("BOW")) return Optional.of("bow");
        if (name.contains("ARROW")) return Optional.of("arrow");
        if (name.contains("POTION")) return Optional.of("potion");
        if (name.contains("GOLDEN_APPLE")) return Optional.of("golden_apple");
        return Optional.empty();
    }

    public static final class IdentityData {
        private final int reforgeCount;
        private final int highestTier;
        private final String lastTier;
        private final String lastOutcome;
        private final UUID forgeId;

        public IdentityData(final int reforgeCount, final int highestTier,
                           final String lastTier, final String lastOutcome, final UUID forgeId) {
            this.reforgeCount = reforgeCount;
            this.highestTier = highestTier;
            this.lastTier = lastTier;
            this.lastOutcome = lastOutcome;
            this.forgeId = forgeId != null ? forgeId : UUID.randomUUID();
        }

        public static IdentityData fresh() {
            return new IdentityData(0, 0, null, null, UUID.randomUUID());
        }

        public IdentityData withReforgeCount(final int count) {
            return new IdentityData(count, this.highestTier, this.lastTier, this.lastOutcome, this.forgeId);
        }

        public IdentityData withHighestTier(final int tier) {
            return new IdentityData(this.reforgeCount, Math.max(this.highestTier, tier),
                    this.lastTier, this.lastOutcome, this.forgeId);
        }

        public IdentityData withLastTier(final String tier) {
            return new IdentityData(this.reforgeCount, this.highestTier, tier,
                    this.lastOutcome, this.forgeId);
        }

        public IdentityData withLastOutcome(final String outcome) {
            return new IdentityData(this.reforgeCount, this.highestTier, this.lastTier,
                    outcome, this.forgeId);
        }

        public IdentityData incrementReforge() {
            return new IdentityData(this.reforgeCount + 1, this.highestTier,
                    this.lastTier, this.lastOutcome, this.forgeId);
        }

        public int getReforgeCount() { return reforgeCount; }
        public int getHighestTier() { return highestTier; }
        public String getLastTier() { return lastTier; }
        public String getLastOutcome() { return lastOutcome; }
        public UUID getForgeId() { return forgeId; }
    }
}
