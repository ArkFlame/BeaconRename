package com.arkflame.flameforge.item;

import com.arkflame.flameforge.model.AttributeSpec;
import com.arkflame.flameforge.model.EnchantSpec;
import com.arkflame.flameforge.model.ItemMutationSpec;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class ItemMutationService {
    private static final ItemMutationService INSTANCE = new ItemMutationService();
    private final EnchantmentResolver enchantResolver = EnchantmentResolver.getInstance();

    private ItemMutationService() {
    }

    public static ItemMutationService getInstance() {
        return INSTANCE;
    }

    public MutationResult mutate(final ItemStack input, final ItemMutationSpec spec, final MutationOptions options) {
        if (input == null) {
            return MutationResult.fail("null input");
        }
        if (spec == null) {
            return MutationResult.fail("null spec");
        }
        final ItemStack clone = input.clone();
        if (clone.getAmount() <= 0) {
            clone.setAmount(1);
        }
        if (spec.getAmount() > 0) {
            clone.setAmount(spec.getAmount());
        }
        final ItemMeta meta = clone.getItemMeta();
        if (meta == null) {
            return MutationResult.fail("item has no meta");
        }
        final List<String> warnings = new ArrayList<>();
        if (spec.getResultMaterial() != null) {
            try {
                final Material mat = org.bukkit.Material.valueOf(spec.getResultMaterial().toUpperCase());
                clone.setType(mat);
            } catch (IllegalArgumentException e) {
                warnings.add("unknown material: " + spec.getResultMaterial());
            }
        }
        if (spec.getResultName() != null) {
            applyName(meta, spec.getResultName());
        }
        if (options != null && options.getLoreMode() != null) {
            applyLore(meta, options.getLoreMode(), options.getLoreContent());
        }
        if (spec.getAddEnchants() != null && !spec.getAddEnchants().isEmpty()) {
            applyEnchants(meta, spec.getAddEnchants(), options);
        }
        if (spec.getAddAttributes() != null && !spec.getAddAttributes().isEmpty()) {
            final AttributeBridge.Result attrResult = AttributeBridge.apply(clone, spec.getAddAttributes());
            if (attrResult.hasExclusions()) {
                for (final AttributeSpec excluded : attrResult.getExcluded()) {
                    warnings.add("attribute excluded: " + excluded.getAttribute());
                }
            }
        }
        if (options != null) {
            if (options.isUnbreakable() != null) {
                final Optional<ItemStack> updated = AttributeBridge.getInstance().setUnbreakable(clone, options.isUnbreakable());
                if (!updated.isPresent()) {
                    warnings.add("unbreakable not supported");
                }
            }
            if (options.getCustomModelData() != null) {
                final Optional<ItemStack> updated = AttributeBridge.getInstance().setCustomModelData(clone, options.getCustomModelData());
                if (!updated.isPresent()) {
                    warnings.add("custom model data not supported");
                }
            }
        }
        try {
            clone.setItemMeta(meta);
        } catch (Exception e) {
            return MutationResult.fail("failed to apply meta: " + e.getMessage());
        }
        if (options != null && options.getIdentityData() != null) {
            final Optional<ItemStack> written = ItemIdentityService.getInstance().writeIdentity(clone, options.getIdentityData());
            if (written.isPresent()) {
                return MutationResult.success(written.get(), warnings);
            }
        }
        return MutationResult.success(clone, warnings);
    }

    public Optional<ItemStack> applyEnchantRemovals(final ItemStack item, final List<String> enchantKeys) {
        if (item == null) {
            return Optional.empty();
        }
        final ItemStack clone = item.clone();
        final ItemMeta meta = clone.getItemMeta();
        if (meta == null) {
            return Optional.empty();
        }
        if (enchantKeys == null || enchantKeys.isEmpty()) {
            return Optional.of(clone);
        }
        for (final String key : enchantKeys) {
            enchantResolver.resolve(key).ifPresent(enchant -> {
                try {
                    meta.removeEnchant(enchant);
                } catch (Exception e) {
                }
            });
        }
        try {
            clone.setItemMeta(meta);
        } catch (Exception e) {
            return Optional.empty();
        }
        return Optional.of(clone);
    }

    public Optional<ItemStack> clearAllEnchants(final ItemStack item) {
        if (item == null) {
            return Optional.empty();
        }
        final ItemStack clone = item.clone();
        final ItemMeta meta = clone.getItemMeta();
        if (meta == null) {
            return Optional.empty();
        }
        enchantResolver.clearFromMeta(meta);
        try {
            clone.setItemMeta(meta);
        } catch (Exception e) {
            return Optional.empty();
        }
        return Optional.of(clone);
    }

    public Optional<ItemStack> setUnbreakable(final ItemStack item, final boolean unbreakable) {
        if (item == null) {
            return Optional.empty();
        }
        return AttributeBridge.getInstance().setUnbreakable(item.clone(), unbreakable);
    }

    public Optional<ItemStack> setCustomModelData(final ItemStack item, final Integer data) {
        if (item == null) {
            return Optional.empty();
        }
        return AttributeBridge.getInstance().setCustomModelData(item.clone(), data);
    }

    private void applyName(final ItemMeta meta, final String name) {
        if (meta == null || name == null) {
            return;
        }
        try {
            final String translated = net.md_5.bungee.api.ChatColor.translateAlternateColorCodes('&', name);
            meta.setDisplayName(translated);
        } catch (Exception e) {
        }
    }

    private void applyLore(final ItemMeta meta, final ItemFactory.LoreMode mode, final List<String> content) {
        if (meta == null || mode == null) {
            return;
        }
        final List<String> lore;
        try {
            lore = meta.getLore();
        } catch (Exception e) {
            return;
        }
        final List<String> newLore = lore != null ? new ArrayList<>(lore) : new ArrayList<>();
        switch (mode) {
            case PRESERVE:
                break;
            case REPLACE:
                newLore.clear();
                if (content != null) {
                    for (final String line : content) {
                        newLore.add(net.md_5.bungee.api.ChatColor.translateAlternateColorCodes('&', line));
                    }
                }
                break;
            case APPEND:
                if (content != null) {
                    for (final String line : content) {
                        newLore.add(net.md_5.bungee.api.ChatColor.translateAlternateColorCodes('&', line));
                    }
                }
                break;
            case PREPEND:
                if (content != null) {
                    final List<String> combined = new ArrayList<>();
                    for (final String line : content) {
                        combined.add(net.md_5.bungee.api.ChatColor.translateAlternateColorCodes('&', line));
                    }
                    combined.addAll(newLore);
                    newLore.clear();
                    newLore.addAll(combined);
                }
                break;
        }
        try {
            meta.setLore(newLore);
        } catch (Exception e) {
        }
    }

    private void applyEnchants(final ItemMeta meta, final List<EnchantSpec> specs, final MutationOptions options) {
        if (meta == null || specs == null) {
            return;
        }
        final boolean isRelative = options != null && options.isRelativeEnchants();
        for (final EnchantSpec spec : specs) {
            final Optional<Enchantment> enchantOpt = enchantResolver.resolve(spec.getEnchantment());
            if (!enchantOpt.isPresent()) {
                continue;
            }
            final Enchantment enchant = enchantOpt.get();
            int targetLevel;
            if (isRelative) {
                final int currentLevel = getCurrentEnchantLevel(meta, enchant);
                targetLevel = enchantResolver.resolveLevel(spec.getMinLevel());
                if (targetLevel < 0) {
                    targetLevel = currentLevel + Math.abs(targetLevel);
                } else {
                    targetLevel = currentLevel + targetLevel;
                }
            } else {
                targetLevel = enchantResolver.resolveLevel(spec.getMinLevel());
            }
            targetLevel = enchantResolver.clampLevel(targetLevel, enchant.getMaxLevel());
            if (targetLevel <= 0) {
                try {
                    meta.removeEnchant(enchant);
                } catch (Exception e) {
                }
            } else {
                try {
                    meta.addEnchant(enchant, targetLevel, false);
                } catch (Exception e) {
                }
            }
        }
    }

    private int getCurrentEnchantLevel(final ItemMeta meta, final Enchantment enchant) {
        try {
            return meta.getEnchants().getOrDefault(enchant, 0);
        } catch (Exception e) {
            return 0;
        }
    }

    public static final class MutationResult {
        private final boolean success;
        private final ItemStack result;
        private final List<String> warnings;

        private MutationResult(final boolean success, final ItemStack result, final List<String> warnings) {
            this.success = success;
            this.result = result;
            this.warnings = warnings != null ? warnings : new ArrayList<>();
        }

        public static MutationResult success(final ItemStack result) {
            return new MutationResult(true, result, new ArrayList<>());
        }

        public static MutationResult success(final ItemStack result, final List<String> warnings) {
            return new MutationResult(true, result, warnings);
        }

        public static MutationResult fail(final String reason) {
            final List<String> list = new ArrayList<>();
            list.add(reason);
            return new MutationResult(false, null, list);
        }

        public boolean isSuccess() { return success; }
        public ItemStack getResult() { return result; }
        public List<String> getWarnings() { return warnings; }
    }

    public static final class MutationOptions {
        private final ItemFactory.LoreMode loreMode;
        private final List<String> loreContent;
        private final Boolean unbreakable;
        private final Integer customModelData;
        private final ItemIdentityService.IdentityData identityData;
        private final boolean relativeEnchants;

        private MutationOptions(final ItemFactory.LoreMode loreMode, final List<String> loreContent,
                                final Boolean unbreakable, final Integer customModelData,
                                final ItemIdentityService.IdentityData identityData,
                                final boolean relativeEnchants) {
            this.loreMode = loreMode != null ? loreMode : ItemFactory.LoreMode.PRESERVE;
            this.loreContent = loreContent;
            this.unbreakable = unbreakable;
            this.customModelData = customModelData;
            this.identityData = identityData;
            this.relativeEnchants = relativeEnchants;
        }

        public static Builder builder() {
            return new Builder();
        }

        public ItemFactory.LoreMode getLoreMode() { return loreMode; }
        public List<String> getLoreContent() { return loreContent; }
        public Boolean isUnbreakable() { return unbreakable; }
        public Integer getCustomModelData() { return customModelData; }
        public ItemIdentityService.IdentityData getIdentityData() { return identityData; }
        public boolean isRelativeEnchants() { return relativeEnchants; }

        public static final class Builder {
            private ItemFactory.LoreMode loreMode = ItemFactory.LoreMode.PRESERVE;
            private List<String> loreContent;
            private Boolean unbreakable;
            private Integer customModelData;
            private ItemIdentityService.IdentityData identityData;
            private boolean relativeEnchants = false;

            public Builder loreMode(final ItemFactory.LoreMode mode) { this.loreMode = mode; return this; }
            public Builder loreContent(final List<String> content) { this.loreContent = content; return this; }
            public Builder unbreakable(final Boolean unbreakable) { this.unbreakable = unbreakable; return this; }
            public Builder customModelData(final Integer data) { this.customModelData = data; return this; }
            public Builder identityData(final ItemIdentityService.IdentityData data) { this.identityData = data; return this; }
            public Builder relativeEnchants(final boolean relative) { this.relativeEnchants = relative; return this; }

            public MutationOptions build() {
                return new MutationOptions(loreMode, loreContent, unbreakable, customModelData, identityData, relativeEnchants);
            }
        }
    }
}
