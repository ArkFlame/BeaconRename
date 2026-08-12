package com.arkflame.flameforge.item;

import com.arkflame.flameforge.model.ForgeHistory;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.UUID;

public final class ItemIdentityService {
    private static final ItemIdentityService INSTANCE = new ItemIdentityService();
    private static final String LEGACY_PREFIX = "\u00A70\u00A70FLAMEFORGE:";
    private static final String KEY_REFORGE_COUNT = "reforge_count";
    private static final String KEY_HIGHEST_TIER = "highest_tier";
    private static final String KEY_LAST_TIER = "last_tier";
    private static final String KEY_LAST_OUTCOME = "last_outcome";
    private static final String KEY_FORGE_ID = "forge_id";
    private static final String KEY_SHORT_ID = "short_id";
    private static final String MODERN_NAMESPACE = "flameforge";
    private static final String MODERN_KEY = "state";
    private static final String HIDDEN_MARKER = "\u00A70\u00A71";
    private static final String VISIBLE_SHORT_ID_PREFIX = "Forge ID: #";
    private static final Pattern SHORT_ID_PATTERN = Pattern.compile("[A-HJ-NP-Z2-9]{8}");

    private volatile Boolean modernPdcAvailable;
    private Method getPdcMethod;
    private Method pdcSetMethod;
    private Method pdcGetMethod;
    private Method pdcHasMethod;
    private Method pdcRemoveMethod;
    private Method getAttributeModifiersMethod;
    private Method addAttributeModifierMethod;
    private Method removeAttributeModifierMethod;
    private Class<?> namespacedKeyClass;
    private Class<?> pdcTypeClass;
    private Class<?> attributeClass;
    private Class<?> attributeModifierClass;
    private Object integerType;
    private Object stringType;

    private final ItemIdentityCodec codec = new ItemIdentityCodec();
    private final ShortForgeIdRegistry shortForgeIdRegistry;

    private ItemIdentityService() {
        this(new ShortForgeIdRegistry());
    }

    ItemIdentityService(final ShortForgeIdRegistry shortForgeIdRegistry) {
        this.shortForgeIdRegistry = Objects.requireNonNull(shortForgeIdRegistry, "shortForgeIdRegistry");
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
            pdcHasMethod = pdcClass.getMethod("has", namespacedKeyClass, pdcTypeClass);
            pdcRemoveMethod = pdcClass.getMethod("remove", namespacedKeyClass);
            attributeClass = Class.forName("org.bukkit.attribute.Attribute");
            attributeModifierClass = Class.forName("org.bukkit.attribute.AttributeModifier");
            getAttributeModifiersMethod = ItemMeta.class.getMethod("getAttributeModifiers", attributeClass);
            addAttributeModifierMethod = ItemMeta.class.getMethod("addAttributeModifier", attributeClass, attributeModifierClass);
            removeAttributeModifierMethod = ItemMeta.class.getMethod("removeAttributeModifier", attributeClass, attributeModifierClass);
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

    public enum ForgeIdentityStatus {
        NONE,
        VALID,
        INVALID
    }

    public static final class ForgeIdentityRead {
        private final ForgeIdentityStatus status;
        private final ItemIdentityCodec.Identity identity;

        public ForgeIdentityRead(final ForgeIdentityStatus status, final ItemIdentityCodec.Identity identity) {
            this.status = status;
            this.identity = identity;
        }

        public ForgeIdentityStatus getStatus() {
            return status;
        }

        public ItemIdentityCodec.Identity getIdentity() {
            return identity;
        }
    }

    public ForgeIdentityRead readForgeIdentity(final ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return new ForgeIdentityRead(ForgeIdentityStatus.NONE, ItemIdentityCodec.Identity.empty());
        }

        if (Boolean.TRUE.equals(modernPdcAvailable)) {
            ItemIdentityCodec.Decoded decoded = readModernForgeIdentity(item);
            if (decoded != null && decoded.isValid()) {
                observeShortId(item, decoded.getIdentity());
                return new ForgeIdentityRead(ForgeIdentityStatus.VALID, decoded.getIdentity());
            }
            if (decoded != null && decoded.getResult() == ItemIdentityCodec.DecodeResult.INVALID_IDENTITY) {
                return new ForgeIdentityRead(ForgeIdentityStatus.INVALID, ItemIdentityCodec.Identity.empty());
            }
        }

        Optional<String> hiddenPayload = readHiddenLegacyPayload(item);
        if (hiddenPayload.isPresent()) {
            ItemIdentityCodec.Decoded decoded = codec.decodeFromString(hiddenPayload.get());
            if (decoded.isValid()) {
                observeShortId(item, decoded.getIdentity());
                return new ForgeIdentityRead(ForgeIdentityStatus.VALID, decoded.getIdentity());
            }
            return new ForgeIdentityRead(ForgeIdentityStatus.INVALID, ItemIdentityCodec.Identity.empty());
        }

        String legacyMarker = codec.getLegacyMarker();
        if (legacyMarker != null && hasLegacyV2Marker(item, legacyMarker)) {
            String payload = extractLegacyV2Payload(item, legacyMarker);
            if (payload != null) {
                ItemIdentityCodec.Decoded decoded = codec.decodeFromString(payload);
                if (decoded.isValid()) {
                    observeShortId(item, decoded.getIdentity());
                    return new ForgeIdentityRead(ForgeIdentityStatus.VALID, decoded.getIdentity());
                }
                return new ForgeIdentityRead(ForgeIdentityStatus.INVALID, ItemIdentityCodec.Identity.empty());
            }
            return new ForgeIdentityRead(ForgeIdentityStatus.INVALID, ItemIdentityCodec.Identity.empty());
        }

        OldReadResult oldResult = readOldIdentity(item);
        switch (oldResult.status) {
            case ABSENT:
                return new ForgeIdentityRead(ForgeIdentityStatus.NONE, ItemIdentityCodec.Identity.empty());
            case MALFORMED:
                return new ForgeIdentityRead(ForgeIdentityStatus.INVALID, ItemIdentityCodec.Identity.empty());
            case VALID:
                IdentityData old = oldResult.data;
                ItemIdentityCodec.Identity identity = ItemIdentityCodec.Identity.empty()
                        .withReforgeCount(old.getReforgeCount())
                        .withHighestTier(old.getHighestTier())
                        .withCurrentTier(old.getHighestTier())
                        .withLastTierId(old.getLastTier())
                        .withForgeId(old.getForgeId())
                        .withBaseMaterial(item.getType().name())
                        .withBaseDisplayName(defaultBaseDisplayName(item.getType()));
                observeShortId(item, identity);
                return new ForgeIdentityRead(ForgeIdentityStatus.VALID, identity);
            default:
                return new ForgeIdentityRead(ForgeIdentityStatus.NONE, ItemIdentityCodec.Identity.empty());
        }
    }

    private enum OldReadStatus {
        ABSENT,
        MALFORMED,
        VALID
    }

    private static final class OldReadResult {
        final OldReadStatus status;
        final IdentityData data;

        OldReadResult(OldReadStatus status, IdentityData data) {
            this.status = status;
            this.data = data;
        }
    }

    private OldReadResult readOldIdentity(final ItemStack item) {
        if (Boolean.TRUE.equals(modernPdcAvailable)) {
            final OldReadResult pdcResult = readOldIdentityFromPdc(item);
            if (pdcResult.status != OldReadStatus.ABSENT) return pdcResult;
        }
        return readOldIdentityFromLore(item);
    }

    private OldReadResult readOldIdentityFromPdc(final ItemStack item) {
        try {
            final ItemMeta meta = item.getItemMeta();
            if (meta == null) {
                return new OldReadResult(OldReadStatus.ABSENT, null);
            }
            final Object pdc = getPdcMethod.invoke(meta);
            if (pdc == null) {
                return new OldReadResult(OldReadStatus.ABSENT, null);
            }
            final Object countKey = namespacedKeyClass.getConstructor(String.class, String.class).newInstance("flameforge", KEY_REFORGE_COUNT);
            final Object tierKey = namespacedKeyClass.getConstructor(String.class, String.class).newInstance("flameforge", KEY_HIGHEST_TIER);
            final Object lastTierKey = namespacedKeyClass.getConstructor(String.class, String.class).newInstance("flameforge", KEY_LAST_TIER);
            final Object lastOutcomeKey = namespacedKeyClass.getConstructor(String.class, String.class).newInstance("flameforge", KEY_LAST_OUTCOME);
            final Object forgeIdKey = namespacedKeyClass.getConstructor(String.class, String.class).newInstance("flameforge", KEY_FORGE_ID);
            final boolean hasCount = (Boolean) pdcHasMethod.invoke(pdc, countKey, integerType);
            final boolean hasTier = (Boolean) pdcHasMethod.invoke(pdc, tierKey, integerType);
            final boolean hasLastTier = (Boolean) pdcHasMethod.invoke(pdc, lastTierKey, stringType);
            final boolean hasLastOutcome = (Boolean) pdcHasMethod.invoke(pdc, lastOutcomeKey, stringType);
            final boolean hasForgeId = (Boolean) pdcHasMethod.invoke(pdc, forgeIdKey, stringType);
            if (!hasCount && !hasTier && !hasLastTier && !hasLastOutcome && !hasForgeId) {
                return new OldReadResult(OldReadStatus.ABSENT, null);
            }
            if (!hasForgeId) {
                return new OldReadResult(OldReadStatus.MALFORMED, null);
            }
            final Object countVal = pdcGetMethod.invoke(pdc, countKey, integerType);
            final Object tierVal = pdcGetMethod.invoke(pdc, tierKey, integerType);
            final Object lastTierVal = pdcGetMethod.invoke(pdc, lastTierKey, stringType);
            final Object lastOutcomeVal = pdcGetMethod.invoke(pdc, lastOutcomeKey, stringType);
            final Object forgeIdVal = pdcGetMethod.invoke(pdc, forgeIdKey, stringType);
            final int reforgeCount = countVal != null ? ((Number) countVal).intValue() : 0;
            final int highestTier = tierVal != null ? ((Number) tierVal).intValue() : 0;
            final String lastTier = lastTierVal != null ? (String) lastTierVal : null;
            final String lastOutcome = lastOutcomeVal != null ? (String) lastOutcomeVal : null;
            final UUID forgeId;
            try {
                forgeId = UUID.fromString((String) forgeIdVal);
            } catch (Exception e) {
                return new OldReadResult(OldReadStatus.MALFORMED, null);
            }
            return new OldReadResult(OldReadStatus.VALID, new IdentityData(reforgeCount, highestTier, lastTier, lastOutcome, forgeId));
        } catch (Exception e) {
            return new OldReadResult(OldReadStatus.MALFORMED, null);
        }
    }

    private OldReadResult readOldIdentityFromLore(final ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return new OldReadResult(OldReadStatus.ABSENT, null);
        }
        final ItemMeta meta = item.getItemMeta();
        if (!meta.hasLore()) {
            return new OldReadResult(OldReadStatus.ABSENT, null);
        }
        final List<String> lore = meta.getLore();
        if (lore == null || lore.isEmpty()) {
            return new OldReadResult(OldReadStatus.ABSENT, null);
        }
        for (final String line : lore) {
            if (line != null && line.startsWith(LEGACY_PREFIX)) {
                return parseLegacyLineResult(line);
            }
        }
        return new OldReadResult(OldReadStatus.ABSENT, null);
    }

    private OldReadResult parseLegacyLineResult(final String line) {
        final String content = line.substring(LEGACY_PREFIX.length());
        final String[] parts = content.split("\\|", -1);
        try {
            final int reforgeCount = parts.length > 0 && !parts[0].isEmpty()
                    ? Integer.parseInt(parts[0]) : 0;
            final int highestTier = parts.length > 1 && !parts[1].isEmpty()
                    ? Integer.parseInt(parts[1]) : 0;
            final String lastTier = parts.length > 2 ? parts[2] : null;
            final String lastOutcome = parts.length > 3 ? parts[3] : null;
            if (parts.length <= 4 || parts[4].isEmpty()) {
                return new OldReadResult(OldReadStatus.MALFORMED, null);
            }
            final UUID forgeId = UUID.fromString(parts[4]);
            return new OldReadResult(OldReadStatus.VALID, new IdentityData(reforgeCount, highestTier, lastTier, lastOutcome, forgeId));
        } catch (Exception e) {
            return new OldReadResult(OldReadStatus.MALFORMED, null);
        }
    }

    private ItemIdentityCodec.Decoded readModernForgeIdentity(final ItemStack item) {
        try {
            final ItemMeta meta = item.getItemMeta();
            if (meta == null) {
                return null;
            }
            final Object pdc = getPdcMethod.invoke(meta);
            if (pdc == null) {
                return null;
            }
            final Object key = namespacedKeyClass.getConstructor(String.class, String.class).newInstance(MODERN_NAMESPACE, MODERN_KEY);
            final boolean hasKey = (Boolean) pdcHasMethod.invoke(pdc, key, stringType);
            if (!hasKey) {
                return null;
            }
            final Object val = pdcGetMethod.invoke(pdc, key, stringType);
            if (val == null) {
                return null;
            }
            return codec.decodeFromString((String) val);
        } catch (Exception e) {
            return null;
        }
    }

    private boolean hasLegacyV2Marker(final ItemStack item, final String marker) {
        if (item == null || !item.hasItemMeta()) {
            return false;
        }
        final ItemMeta meta = item.getItemMeta();
        if (!meta.hasLore()) {
            return false;
        }
        final List<String> lore = meta.getLore();
        if (lore == null) {
            return false;
        }
        for (final String line : lore) {
            if (line != null && line.startsWith(marker)) {
                return true;
            }
        }
        return false;
    }

    private String extractLegacyV2Payload(final ItemStack item, final String marker) {
        if (item == null || !item.hasItemMeta()) {
            return null;
        }
        final ItemMeta meta = item.getItemMeta();
        if (!meta.hasLore()) {
            return null;
        }
        final List<String> lore = meta.getLore();
        if (lore == null) {
            return null;
        }
        for (final String line : lore) {
            if (line != null && line.startsWith(marker)) {
                return line.substring(marker.length());
            }
        }
        return null;
    }

    public Optional<ItemStack> writeForgeIdentity(final ItemStack item, final ItemIdentityCodec.Identity identity) {
        if (item == null || identity == null) {
            return Optional.empty();
        }
        final ItemStack clone = item.clone();
        final Optional<String> existingShortId = Boolean.TRUE.equals(modernPdcAvailable)
                ? readPdcShortId(clone) : readVisibleShortId(clone);
        final String shortId = existingShortId.filter(value -> shortForgeIdRegistry.claimExisting(identity.getForgeId(), value))
                .orElseGet(() -> shortForgeIdRegistry.claimOrGenerate(identity.getForgeId()));

        if (Boolean.TRUE.equals(modernPdcAvailable)) {
            if (!writeModernForgeIdentity(clone, identity, shortId)) {
                return Optional.empty();
            }
        }

        if (!writeIdentityLore(clone, identity, shortId)) {
            return Optional.empty();
        }

        return Optional.of(clone);
    }

    private boolean writeModernForgeIdentity(final ItemStack item, final ItemIdentityCodec.Identity identity,
                                             final String shortId) {
        try {
            final ItemMeta meta = item.getItemMeta();
            if (meta == null) {
                return false;
            }
            final Object pdc = getPdcMethod.invoke(meta);
            if (pdc == null) {
                return false;
            }
            final Object key = namespacedKeyClass.getConstructor(String.class, String.class).newInstance(MODERN_NAMESPACE, MODERN_KEY);
            final String payload = codec.encodeToString(identity);
            pdcSetMethod.invoke(pdc, key, stringType, payload);
            final Object shortIdKey = namespacedKeyClass.getConstructor(String.class, String.class)
                    .newInstance(MODERN_NAMESPACE, KEY_SHORT_ID);
            pdcSetMethod.invoke(pdc, shortIdKey, stringType, shortId);
            removeOldIdentityPdcValues(pdc);
            item.setItemMeta(meta);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private void removeOldIdentityPdcValues(final Object pdc) throws Exception {
        final String[] integerKeys = {KEY_REFORGE_COUNT, KEY_HIGHEST_TIER};
        for (final String keyComponent : integerKeys) {
            pdcRemoveMethod.invoke(pdc, namespacedKeyClass.getConstructor(String.class, String.class)
                    .newInstance(MODERN_NAMESPACE, keyComponent));
        }
        final String[] stringKeys = {KEY_LAST_TIER, KEY_LAST_OUTCOME, KEY_FORGE_ID};
        for (final String keyComponent : stringKeys) {
            pdcRemoveMethod.invoke(pdc, namespacedKeyClass.getConstructor(String.class, String.class)
                    .newInstance(MODERN_NAMESPACE, keyComponent));
        }
    }

    private boolean writeIdentityLore(final ItemStack item, final ItemIdentityCodec.Identity identity,
                                      final String shortId) {
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
        List<String> filtered = new ArrayList<>();
        for (final String line : lore) {
            if (!isIdentityLoreLine(line)) {
                filtered.add(line);
            }
        }
        filtered.add(encodeHiddenLegacyPayload(codec.encodeToString(identity)));
        filtered.add(buildIdentityLoreLine(shortId));
        try {
            meta.setLore(filtered);
            item.setItemMeta(meta);
        } catch (Exception e) {
            return false;
        }
        return true;
    }

    String encodeHiddenLegacyPayload(final String payload) {
        if (payload == null) {
            return "";
        }
        final byte[] bytes = payload.getBytes(StandardCharsets.US_ASCII);
        StringBuilder encoded = new StringBuilder(HIDDEN_MARKER.length() + bytes.length * 4 + 2);
        encoded.append(HIDDEN_MARKER);
        for (final byte value : bytes) {
            int unsigned = value & 0xFF;
            encoded.append('\u00A7').append(Character.forDigit(unsigned >>> 4, 16));
            encoded.append('\u00A7').append(Character.forDigit(unsigned & 0x0F, 16));
        }
        return encoded.append('\u00A7').append('r').toString();
    }

    Optional<String> decodeHiddenLegacyPayload(final String line) {
        if (line == null || !line.startsWith(HIDDEN_MARKER) || !line.endsWith("\u00A7r")) {
            return Optional.empty();
        }
        final String encoded = line.substring(HIDDEN_MARKER.length(), line.length() - 2);
        if (encoded.length() % 4 != 0) {
            return Optional.empty();
        }
        byte[] decoded = new byte[encoded.length() / 4];
        for (int i = 0; i < encoded.length(); i += 4) {
            if (encoded.charAt(i) != '\u00A7' || encoded.charAt(i + 2) != '\u00A7') {
                return Optional.empty();
            }
            int high = Character.digit(encoded.charAt(i + 1), 16);
            int low = Character.digit(encoded.charAt(i + 3), 16);
            if (high < 0 || low < 0) {
                return Optional.empty();
            }
            decoded[i / 4] = (byte) ((high << 4) | low);
        }
        return Optional.of(new String(decoded, StandardCharsets.US_ASCII));
    }

    Optional<String> readVisibleShortId(final ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return Optional.empty();
        }
        final ItemMeta meta = item.getItemMeta();
        if (meta == null || !meta.hasLore() || meta.getLore() == null) {
            return Optional.empty();
        }
        for (final String line : meta.getLore()) {
            if (line != null && line.startsWith(VISIBLE_SHORT_ID_PREFIX)) {
                String value = line.substring(VISIBLE_SHORT_ID_PREFIX.length());
                if (SHORT_ID_PATTERN.matcher(value).matches()) {
                    return Optional.of(value);
                }
            }
        }
        return Optional.empty();
    }

    String buildIdentityLoreLine(final String shortId) {
        if (shortId == null || !SHORT_ID_PATTERN.matcher(shortId).matches()) {
            throw new IllegalArgumentException("shortId");
        }
        return VISIBLE_SHORT_ID_PREFIX + shortId;
    }

    private Optional<String> readHiddenLegacyPayload(final ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return Optional.empty();
        }
        final ItemMeta meta = item.getItemMeta();
        if (meta == null || !meta.hasLore() || meta.getLore() == null) {
            return Optional.empty();
        }
        for (final String line : meta.getLore()) {
            Optional<String> payload = decodeHiddenLegacyPayload(line);
            if (payload.isPresent()) {
                return payload;
            }
            if (line != null && line.startsWith(HIDDEN_MARKER)) {
                return Optional.of("");
            }
        }
        return Optional.empty();
    }

    private Optional<String> readPdcShortId(final ItemStack item) {
        try {
            final ItemMeta meta = item.getItemMeta();
            if (meta == null) {
                return Optional.empty();
            }
            final Object pdc = getPdcMethod.invoke(meta);
            if (pdc == null) {
                return Optional.empty();
            }
            final Object key = namespacedKeyClass.getConstructor(String.class, String.class)
                    .newInstance(MODERN_NAMESPACE, KEY_SHORT_ID);
            if (!(Boolean) pdcHasMethod.invoke(pdc, key, stringType)) {
                return Optional.empty();
            }
            final Object value = pdcGetMethod.invoke(pdc, key, stringType);
            if (value instanceof String && SHORT_ID_PATTERN.matcher((String) value).matches()) {
                return Optional.of((String) value);
            }
        } catch (Exception ignored) {
            return Optional.empty();
        }
        return Optional.empty();
    }

    private void observeShortId(final ItemStack item, final ItemIdentityCodec.Identity identity) {
        Optional<String> shortId = Boolean.TRUE.equals(modernPdcAvailable)
                ? readPdcShortId(item) : readVisibleShortId(item);
        if (Boolean.TRUE.equals(modernPdcAvailable) && !shortId.isPresent()) {
            shortId = readVisibleShortId(item);
        }
        if (shortId.isPresent()) {
            shortForgeIdRegistry.claimExisting(identity.getForgeId(), shortId.get());
        }
    }

    private boolean isIdentityLoreLine(final String line) {
        return line != null && (line.startsWith(LEGACY_PREFIX)
                || line.startsWith(codec.getLegacyMarker())
                || line.startsWith(HIDDEN_MARKER)
                || line.startsWith(VISIBLE_SHORT_ID_PREFIX));
    }

    public boolean hasForgeIdentityMarker(final ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return false;
        }

        if (Boolean.TRUE.equals(modernPdcAvailable)) {
            try {
                final ItemMeta meta = item.getItemMeta();
                if (meta == null) {
                    return false;
                }
                final Object pdc = getPdcMethod.invoke(meta);
                if (pdc != null) {
                    final Object key = namespacedKeyClass.getConstructor(String.class, String.class).newInstance(MODERN_NAMESPACE, MODERN_KEY);
                    final boolean hasKey = (Boolean) pdcHasMethod.invoke(pdc, key, stringType);
                    if (hasKey) {
                        return true;
                    }
                }
            } catch (Exception e) {
            }
        }

        if (hasLegacyV2Marker(item, codec.getLegacyMarker())) {
            return true;
        }
        if (item.hasItemMeta()) {
            ItemMeta meta = item.getItemMeta();
            if (meta != null && meta.hasLore() && meta.getLore() != null) {
                for (String line : meta.getLore()) {
                    if (isIdentityLoreLine(line)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    public ItemIdentityCodec getCodec() {
        return codec;
    }

    public String defaultBaseDisplayName(final Material material) {
        if (material == null) {
            return "Unknown";
        }
        final String name = material.name();
        String base = name.toLowerCase(Locale.ROOT).replace("_", " ");
        if (base.endsWith(" of immortality")) {
            base = base.substring(0, base.length() - " of immortality".length());
        }
        StringBuilder result = new StringBuilder();
        boolean capitalizeNext = true;
        for (int i = 0; i < base.length(); i++) {
            char c = base.charAt(i);
            if (Character.isWhitespace(c) || c == '_') {
                if (result.length() > 0 && result.charAt(result.length() - 1) != ' ') {
                    result.append(' ');
                }
                capitalizeNext = true;
            } else {
                if (capitalizeNext) {
                    result.append(Character.toUpperCase(c));
                    capitalizeNext = false;
                } else {
                    result.append(c);
                }
            }
        }
        return result.toString().trim();
    }

    public boolean matchesMaterialGroup(final Material material, final String group) {
        if (material == null || group == null) {
            return false;
        }
        return matchesMaterialGroupName(material.name(), group);
    }

    public boolean matchesMaterialGroupName(final String materialName, final String group) {
        if (materialName == null || group == null) {
            return false;
        }
        final String normalizedGroup = group.toLowerCase(Locale.ROOT).replace("-", "_");
        if ("any".equals(normalizedGroup)) {
            return true;
        }
        if ("weapon".equals(normalizedGroup)) {
            return matchesMaterialGroupName(materialName, "sword")
                    || matchesMaterialGroupName(materialName, "axe")
                    || matchesMaterialGroupName(materialName, "bow")
                    || matchesMaterialGroupName(materialName, "spear")
                    || matchesMaterialGroupName(materialName, "mace");
        }
        if ("armor".equals(normalizedGroup)) {
            return matchesMaterialGroupName(materialName, "helmet")
                    || matchesMaterialGroupName(materialName, "chestplate")
                    || matchesMaterialGroupName(materialName, "leggings")
                    || matchesMaterialGroupName(materialName, "boots")
                    || matchesMaterialGroupName(materialName, "horse_armor");
        }
        final String upper = materialName.toUpperCase(Locale.ROOT);
        if ("sword".equals(normalizedGroup)) {
            return upper.endsWith("_SWORD");
        }
        if ("axe".equals(normalizedGroup)) {
            return upper.endsWith("_AXE");
        }
        if ("bow".equals(normalizedGroup)) {
            return upper.contains("BOW");
        }
        if ("spear".equals(normalizedGroup)) {
            return upper.endsWith("_SPEAR") || upper.equals("TRIDENT") || upper.equals("SPEAR");
        }
        if ("mace".equals(normalizedGroup)) {
            return upper.equals("MACE");
        }
        if ("helmet".equals(normalizedGroup)) {
            return upper.endsWith("_HELMET");
        }
        if ("chestplate".equals(normalizedGroup)) {
            return upper.endsWith("_CHESTPLATE");
        }
        if ("leggings".equals(normalizedGroup)) {
            return upper.endsWith("_LEGGINGS");
        }
        if ("boots".equals(normalizedGroup)) {
            return upper.endsWith("_BOOTS");
        }
        if ("horse_armor".equals(normalizedGroup)) {
            return upper.endsWith("_HORSE_ARMOR");
        }
        return normalizedGroup.equals(upper);
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
