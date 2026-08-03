package com.arkflame.flameforge.item;

import com.arkflame.flameforge.compat.material.MaterialResolver;
import com.arkflame.flameforge.model.AttributeSpec;
import com.arkflame.flameforge.model.EnchantSpec;
import com.arkflame.flameforge.model.ItemMatcherSpec;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class ItemMatcher {
    private static final ItemMatcher INSTANCE = new ItemMatcher();
    private final MaterialResolver materialResolver = MaterialResolver.getInstance();
    private final EnchantmentResolver enchantResolver = new EnchantmentResolver();

    private volatile Boolean modernCustomModelDataAvailable;
    private Method hasCustomModelDataMethod;
    private Method getCustomModelDataMethod;

    private ItemMatcher() {
        initReflection();
    }

    private void initReflection() {
        try {
            hasCustomModelDataMethod = ItemMeta.class.getMethod("hasCustomModelData");
            getCustomModelDataMethod = ItemMeta.class.getMethod("getCustomModelData");
            modernCustomModelDataAvailable = true;
        } catch (NoSuchMethodException e) {
            modernCustomModelDataAvailable = false;
        }
    }

    public static ItemMatcher getInstance() {
        return INSTANCE;
    }

    public MatchResult matches(final ItemStack item, final ItemMatcherSpec spec) {
        if (item == null || spec == null) {
            return MatchResult.fail("null input");
        }
        final List<String> failures = new ArrayList<>();
        if (!matchesMaterial(item, spec.getMaterial(), failures)) {
            return MatchResult.fail(failures);
        }
        if (!matchesName(item, spec.getName(), spec.isExactMatch(), failures)) {
            return MatchResult.fail(failures);
        }
        if (!matchesDurability(item, spec.getMinDurability(), spec.getMaxDurability(), failures)) {
            return MatchResult.fail(failures);
        }
        return MatchResult.success();
    }

    public MatchResult matchesFull(final ItemStack item, final FullMatchSpec fullSpec) {
        if (item == null || fullSpec == null) {
            return MatchResult.fail("null input");
        }
        final List<String> failures = new ArrayList<>();
        if (fullSpec.getMaterial() != null) {
            if (!matchesMaterial(item, fullSpec.getMaterial(), failures)) {
                return MatchResult.fail(failures);
            }
        }
        if (fullSpec.getMaterialGroup() != null) {
            if (!matchesMaterialGroup(item, fullSpec.getMaterialGroup(), failures)) {
                return MatchResult.fail(failures);
            }
        }
        if (fullSpec.getName() != null) {
            if (!matchesName(item, fullSpec.getName(), fullSpec.isExactNameMatch(), failures)) {
                return MatchResult.fail(failures);
            }
        }
        if (!matchesLore(item, fullSpec.getLorePatterns(), failures)) {
            return MatchResult.fail(failures);
        }
        if (!matchesCustomModelData(item, fullSpec.getCustomModelData(), failures)) {
            return MatchResult.fail(failures);
        }
        if (!matchesEnchants(item, fullSpec.getEnchantSpecs(), failures)) {
            return MatchResult.fail(failures);
        }
        if (!matchesReforgeBounds(item, fullSpec.getMinReforgeCount(), fullSpec.getMaxReforgeCount(), failures)) {
            return MatchResult.fail(failures);
        }
        if (!matchesAttributes(item, fullSpec.getAttributeSpecs(), failures)) {
            return MatchResult.fail(failures);
        }
        return MatchResult.success();
    }

    private boolean matchesMaterial(final ItemStack item, final String materialKey, final List<String> failures) {
        if (materialKey == null || materialKey.isEmpty()) {
            return true;
        }
        if (item == null || item.getType() == Material.AIR) {
            failures.add("item is null or air");
            return false;
        }
        final Optional<Material> matOpt = materialResolver.resolve(materialKey);
        if (!matOpt.isPresent()) {
            failures.add("unknown material: " + materialKey);
            return false;
        }
        if (item.getType() != matOpt.get()) {
            failures.add("material mismatch: expected " + materialKey + ", got " + item.getType().name());
            return false;
        }
        return true;
    }

    private boolean matchesMaterialGroup(final ItemStack item, final String group, final List<String> failures) {
        if (group == null || group.isEmpty()) {
            return true;
        }
        if (item == null || item.getType() == Material.AIR) {
            failures.add("item is null or air");
            return false;
        }
        final Optional<String> actualGroup = ItemIdentityService.getInstance().getMaterialGroup(item.getType());
        if (!actualGroup.isPresent() || !actualGroup.get().equalsIgnoreCase(group)) {
            failures.add("material group mismatch: expected " + group);
            return false;
        }
        return true;
    }

    private boolean matchesName(final ItemStack item, final String name, final boolean exact, final List<String> failures) {
        if (name == null || name.isEmpty()) {
            return true;
        }
        if (item == null || !item.hasItemMeta()) {
            failures.add("item has no meta for name check");
            return false;
        }
        final ItemMeta meta = item.getItemMeta();
        if (!meta.hasDisplayName()) {
            failures.add("item has no display name");
            return false;
        }
        final String displayName = meta.getDisplayName();
        if (exact) {
            if (!displayName.equals(name)) {
                failures.add("display name mismatch: expected '" + name + "', got '" + displayName + "'");
                return false;
            }
        } else {
            if (!displayName.contains(name)) {
                failures.add("display name does not contain: " + name);
                return false;
            }
        }
        return true;
    }

    private boolean matchesDurability(final ItemStack item, final int min, final int max, final List<String> failures) {
        if (min < 0 && max < 0) {
            return true;
        }
        if (item == null) {
            failures.add("item is null for durability check");
            return false;
        }
        final short durability = item.getDurability();
        final boolean minOk = min < 0 || durability >= min;
        final boolean maxOk = max < 0 || durability <= max;
        if (!minOk || !maxOk) {
            failures.add("durability " + durability + " out of range [" + min + ", " + max + "]");
            return false;
        }
        return true;
    }

    private boolean matchesLore(final ItemStack item, final List<String> patterns, final List<String> failures) {
        if (patterns == null || patterns.isEmpty()) {
            return true;
        }
        if (item == null || !item.hasItemMeta()) {
            failures.add("item has no meta for lore check");
            return false;
        }
        final ItemMeta meta = item.getItemMeta();
        if (!meta.hasLore()) {
            failures.add("item has no lore");
            return false;
        }
        final List<String> lore = meta.getLore();
        for (final String pattern : patterns) {
            boolean found = false;
            for (final String line : lore) {
                if (line != null && line.contains(pattern)) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                failures.add("lore missing pattern: " + pattern);
                return false;
            }
        }
        return true;
    }

    private boolean matchesCustomModelData(final ItemStack item, final Integer expected, final List<String> failures) {
        if (expected == null) {
            return true;
        }
        if (item == null || !item.hasItemMeta()) {
            failures.add("item has no meta for custom model data check");
            return false;
        }
        if (!Boolean.TRUE.equals(modernCustomModelDataAvailable)) {
            failures.add("custom model data not supported");
            return false;
        }
        try {
            final ItemMeta meta = item.getItemMeta();
            final boolean has = (Boolean) hasCustomModelDataMethod.invoke(meta);
            if (!has) {
                failures.add("item has no custom model data");
                return false;
            }
            final Integer actual = (Integer) getCustomModelDataMethod.invoke(meta);
            if (!expected.equals(actual)) {
                failures.add("custom model data mismatch: expected " + expected + ", got " + actual);
                return false;
            }
        } catch (Exception e) {
            failures.add("custom model data check failed: " + e.getMessage());
            return false;
        }
        return true;
    }

    private boolean matchesEnchants(final ItemStack item, final List<EnchantSpec> specs, final List<String> failures) {
        if (specs == null || specs.isEmpty()) {
            return true;
        }
        if (item == null || !item.hasItemMeta()) {
            failures.add("item has no meta for enchant check");
            return false;
        }
        final ItemMeta meta = item.getItemMeta();
        final Map<Enchantment, Integer> enchants;
        try {
            enchants = meta.getEnchants();
        } catch (Exception e) {
            failures.add("failed to read enchants: " + e.getMessage());
            return false;
        }
        for (final EnchantSpec spec : specs) {
            final Optional<Enchantment> enchantOpt = enchantResolver.resolve(spec.getEnchantment());
            if (!enchantOpt.isPresent()) {
                failures.add("unknown enchant: " + spec.getEnchantment());
                return false;
            }
            final Enchantment enchant = enchantOpt.get();
            if (!enchants.containsKey(enchant)) {
                failures.add("missing enchant: " + spec.getEnchantment());
                return false;
            }
            final int actualLevel = enchants.get(enchant);
            if (spec.getMinLevel() > 0 && actualLevel < spec.getMinLevel()) {
                failures.add("enchant " + spec.getEnchantment() + " level " + actualLevel + " below min " + spec.getMinLevel());
                return false;
            }
            if (spec.getMaxLevel() < Integer.MAX_VALUE && actualLevel > spec.getMaxLevel()) {
                failures.add("enchant " + spec.getEnchantment() + " level " + actualLevel + " above max " + spec.getMaxLevel());
                return false;
            }
        }
        return true;
    }

    private boolean matchesReforgeBounds(final ItemStack item, final int min, final int max, final List<String> failures) {
        if (min < 0 && max < 0) {
            return true;
        }
        final Optional<ItemIdentityService.IdentityData> identityOpt =
                ItemIdentityService.getInstance().readIdentity(item);
        if (!identityOpt.isPresent()) {
            if (min > 0 || max >= 0) {
                failures.add("item has no identity for reforge count check");
                return false;
            }
            return true;
        }
        final int count = identityOpt.get().getReforgeCount();
        final boolean minOk = min < 0 || count >= min;
        final boolean maxOk = max < 0 || count <= max;
        if (!minOk || !maxOk) {
            failures.add("reforge count " + count + " out of bounds [" + min + ", " + max + "]");
            return false;
        }
        return true;
    }

    private boolean matchesAttributes(final ItemStack item, final List<AttributeSpec> specs, final List<String> failures) {
        if (specs == null || specs.isEmpty()) {
            return true;
        }
        if (item == null || !item.hasItemMeta()) {
            failures.add("item has no meta for attribute check");
            return false;
        }
        final List<AttributeSpec> actualAttrs = AttributeBridge.getInstance().extractFromMeta(item.getItemMeta());
        for (final AttributeSpec spec : specs) {
            boolean found = false;
            for (final AttributeSpec actual : actualAttrs) {
                if (actual.getAttribute().equalsIgnoreCase(spec.getAttribute())
                        && actual.getOperation().equalsIgnoreCase(spec.getOperation())) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                failures.add("missing attribute: " + spec.getAttribute());
                return false;
            }
        }
        return true;
    }

    public static final class MatchResult {
        private final boolean matched;
        private final List<String> failures;

        private MatchResult(final boolean matched, final List<String> failures) {
            this.matched = matched;
            this.failures = failures != null ? failures : new ArrayList<>();
        }

        public static MatchResult success() {
            return new MatchResult(true, null);
        }

        public static MatchResult fail(final List<String> failures) {
            return new MatchResult(false, failures);
        }

        public static MatchResult fail(final String reason) {
            final List<String> list = new ArrayList<>();
            list.add(reason);
            return new MatchResult(false, list);
        }

        public boolean isMatched() { return matched; }
        public List<String> getFailures() { return failures; }
    }

    public static final class FullMatchSpec {
        private final String material;
        private final String materialGroup;
        private final String name;
        private final boolean exactNameMatch;
        private final List<String> lorePatterns;
        private final Integer customModelData;
        private final List<EnchantSpec> enchantSpecs;
        private final int minReforgeCount;
        private final int maxReforgeCount;
        private final List<AttributeSpec> attributeSpecs;

        private FullMatchSpec(final String material, final String materialGroup, final String name,
                             final boolean exactNameMatch, final List<String> lorePatterns,
                             final Integer customModelData, final List<EnchantSpec> enchantSpecs,
                             final int minReforgeCount, final int maxReforgeCount,
                             final List<AttributeSpec> attributeSpecs) {
            this.material = material;
            this.materialGroup = materialGroup;
            this.name = name;
            this.exactNameMatch = exactNameMatch;
            this.lorePatterns = lorePatterns;
            this.customModelData = customModelData;
            this.enchantSpecs = enchantSpecs;
            this.minReforgeCount = minReforgeCount;
            this.maxReforgeCount = maxReforgeCount;
            this.attributeSpecs = attributeSpecs;
        }

        public static Builder builder() {
            return new Builder();
        }

        public String getMaterial() { return material; }
        public String getMaterialGroup() { return materialGroup; }
        public String getName() { return name; }
        public boolean isExactNameMatch() { return exactNameMatch; }
        public List<String> getLorePatterns() { return lorePatterns; }
        public Integer getCustomModelData() { return customModelData; }
        public List<EnchantSpec> getEnchantSpecs() { return enchantSpecs; }
        public int getMinReforgeCount() { return minReforgeCount; }
        public int getMaxReforgeCount() { return maxReforgeCount; }
        public List<AttributeSpec> getAttributeSpecs() { return attributeSpecs; }

        public static final class Builder {
            private String material;
            private String materialGroup;
            private String name;
            private boolean exactNameMatch;
            private List<String> lorePatterns;
            private Integer customModelData;
            private List<EnchantSpec> enchantSpecs;
            private int minReforgeCount = -1;
            private int maxReforgeCount = -1;
            private List<AttributeSpec> attributeSpecs;

            public Builder material(final String material) { this.material = material; return this; }
            public Builder materialGroup(final String materialGroup) { this.materialGroup = materialGroup; return this; }
            public Builder name(final String name) { this.name = name; return this; }
            public Builder exactNameMatch(final boolean exact) { this.exactNameMatch = exact; return this; }
            public Builder lorePatterns(final List<String> patterns) { this.lorePatterns = patterns; return this; }
            public Builder customModelData(final Integer data) { this.customModelData = data; return this; }
            public Builder enchantSpecs(final List<EnchantSpec> specs) { this.enchantSpecs = specs; return this; }
            public Builder reforgeBounds(final int min, final int max) { this.minReforgeCount = min; this.maxReforgeCount = max; return this; }
            public Builder attributeSpecs(final List<AttributeSpec> specs) { this.attributeSpecs = specs; return this; }

            public FullMatchSpec build() {
                return new FullMatchSpec(material, materialGroup, name, exactNameMatch,
                        lorePatterns, customModelData, enchantSpecs,
                        minReforgeCount, maxReforgeCount, attributeSpecs);
            }
        }
    }
}
