package com.arkflame.flameforge.item;

import com.arkflame.flameforge.compat.RuntimeCapabilities;
import com.arkflame.flameforge.compat.material.MaterialResolver;
import com.arkflame.flameforge.model.AttributeSpec;
import com.arkflame.flameforge.model.EnchantSpec;
import com.arkflame.flameforge.model.ItemMutationSpec;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

public final class ItemFactory {
    private static final ItemFactory INSTANCE = new ItemFactory();
    private final MaterialResolver materialResolver = MaterialResolver.getInstance();
    private final EnchantmentResolver enchantResolver = EnchantmentResolver.getInstance();
    private final RuntimeCapabilities capabilities = RuntimeCapabilities.getInstance();

    private ItemFactory() {
    }

    public static ItemFactory getInstance() {
        return INSTANCE;
    }

    public Optional<ItemStack> createPreview(final ItemMutationSpec spec, final ItemStack baseItem) {
        if (spec == null) {
            return Optional.empty();
        }
        final ItemStack preview = baseItem != null ? baseItem.clone() : createEmptyItem(spec);
        if (preview == null) {
            return Optional.empty();
        }
        final ItemMeta meta = preview.getItemMeta();
        if (meta == null) {
            return Optional.empty();
        }
        if (spec.getResultMaterial() != null) {
            materialResolver.resolve(spec.getResultMaterial()).ifPresent(preview::setType);
        }
        if (spec.getResultName() != null) {
            meta.setDisplayName(net.md_5.bungee.api.ChatColor.translateAlternateColorCodes('&', spec.getResultName()));
        }
        if (spec.getAmount() > 0) {
            preview.setAmount(spec.getAmount());
        }
        try {
            preview.setItemMeta(meta);
        } catch (Exception e) {
            return Optional.empty();
        }
        return Optional.of(preview);
    }

    public Optional<ItemStack> createItem(final ItemMutationSpec spec, final ItemStack baseItem,
                                         final CreationContext ctx) {
        if (spec == null) {
            return Optional.empty();
        }
        final ItemStack result = baseItem != null ? baseItem.clone() : createEmptyItem(spec);
        if (result == null) {
            return Optional.empty();
        }
        final ItemMeta meta = result.getItemMeta();
        if (meta == null) {
            return Optional.empty();
        }
        if (spec.getResultMaterial() != null) {
            materialResolver.resolve(spec.getResultMaterial()).ifPresent(result::setType);
        }
        if (spec.getResultName() != null) {
            applyName(meta, spec.getResultName());
        }
        if (spec.getAmount() > 0) {
            result.setAmount(spec.getAmount());
        }
        if (spec.getAddEnchants() != null && !spec.getAddEnchants().isEmpty()) {
            applyEnchantments(meta, spec.getAddEnchants());
        }
        if (spec.getAddAttributes() != null && !spec.getAddAttributes().isEmpty()) {
            AttributeBridge.apply(result, spec.getAddAttributes());
        }
        if (ctx != null && ctx.getLoreMode() != null) {
            applyLore(meta, ctx.getLoreMode(), ctx.getLoreContent());
        }
        if (ctx != null && ctx.isUnbreakable() != null) {
            AttributeBridge.getInstance().setUnbreakable(result, ctx.isUnbreakable());
        }
        if (ctx != null && ctx.getCustomModelData() != null) {
            AttributeBridge.getInstance().setCustomModelData(result, ctx.getCustomModelData());
        }
        try {
            result.setItemMeta(meta);
        } catch (Exception e) {
            return Optional.empty();
        }
        if (ctx != null && ctx.getIdentityData() != null) {
            ItemIdentityService.getInstance().writeIdentity(result, ctx.getIdentityData());
        }
        return Optional.of(result);
    }

    public Optional<ItemStack> withMetaFlags(final ItemStack item, final MetaFlagSet flags) {
        if (item == null || flags == null) {
            return Optional.empty();
        }
        final ItemStack clone = item.clone();
        final ItemMeta meta = clone.getItemMeta();
        if (meta == null) {
            return Optional.empty();
        }
        if (flags.isUnbreakable() != null) {
            AttributeBridge.getInstance().setUnbreakable(clone, flags.isUnbreakable());
        }
        if (flags.isGlow() != null && flags.isGlow()) {
            try {
                meta.addEnchant(org.bukkit.enchantments.Enchantment.DURABILITY, 1, true);
            } catch (Exception e) {
            }
        }
        try {
            clone.setItemMeta(meta);
        } catch (Exception e) {
            return Optional.empty();
        }
        return Optional.of(clone);
    }

    public Optional<ItemStack> withEnchants(final ItemStack item, final List<EnchantSpec> enchants) {
        if (item == null) {
            return Optional.empty();
        }
        final ItemStack clone = item.clone();
        final ItemMeta meta = clone.getItemMeta();
        if (meta == null) {
            return Optional.empty();
        }
        if (enchants != null && !enchants.isEmpty()) {
            applyEnchantments(meta, enchants);
        }
        try {
            clone.setItemMeta(meta);
        } catch (Exception e) {
            return Optional.empty();
        }
        return Optional.of(clone);
    }

    public Optional<ItemStack> withAttributes(final ItemStack item, final List<AttributeSpec> specs) {
        if (item == null) {
            return Optional.empty();
        }
        final ItemStack clone = item.clone();
        if (specs != null && !specs.isEmpty()) {
            AttributeBridge.apply(clone, specs);
        }
        return Optional.of(clone);
    }

    public Optional<ItemStack> withCustomModelData(final ItemStack item, final Integer data) {
        if (item == null) {
            return Optional.empty();
        }
        return AttributeBridge.getInstance().setCustomModelData(item.clone(), data);
    }

    private ItemStack createEmptyItem(final ItemMutationSpec spec) {
        if (spec.getResultMaterial() != null) {
            return materialResolver.makeItem(spec.getResultMaterial(), spec.getAmount() > 0 ? spec.getAmount() : 1)
                    .orElse(null);
        }
        return new ItemStack(Material.STONE, spec.getAmount() > 0 ? spec.getAmount() : 1);
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

    private void applyLore(final ItemMeta meta, final LoreMode mode, final List<String> content) {
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

    private void applyEnchantments(final ItemMeta meta, final List<EnchantSpec> specs) {
        if (meta == null || specs == null) {
            return;
        }
        for (final EnchantSpec spec : specs) {
            final Optional<Enchantment> enchantOpt = enchantResolver.resolve(spec.getEnchantment());
            if (!enchantOpt.isPresent()) {
                continue;
            }
            final Enchantment enchant = enchantOpt.get();
            int level = spec.getMinLevel();
            if (spec.getMaxLevel() < Integer.MAX_VALUE) {
                level = enchantResolver.clampLevel(level, enchant.getMaxLevel());
            }
            try {
                meta.addEnchant(enchant, level, false);
            } catch (Exception e) {
            }
        }
    }

    public enum LoreMode {
        PRESERVE,
        REPLACE,
        APPEND,
        PREPEND
    }

    public static final class CreationContext {
        private final LoreMode loreMode;
        private final List<String> loreContent;
        private final Boolean unbreakable;
        private final Integer customModelData;
        private final ItemIdentityService.IdentityData identityData;

        private CreationContext(final LoreMode loreMode, final List<String> loreContent,
                                final Boolean unbreakable, final Integer customModelData,
                                final ItemIdentityService.IdentityData identityData) {
            this.loreMode = loreMode;
            this.loreContent = loreContent;
            this.unbreakable = unbreakable;
            this.customModelData = customModelData;
            this.identityData = identityData;
        }

        public static Builder builder() {
            return new Builder();
        }

        public LoreMode getLoreMode() { return loreMode; }
        public List<String> getLoreContent() { return loreContent; }
        public Boolean isUnbreakable() { return unbreakable; }
        public Integer getCustomModelData() { return customModelData; }
        public ItemIdentityService.IdentityData getIdentityData() { return identityData; }

        public static final class Builder {
            private LoreMode loreMode = LoreMode.PRESERVE;
            private List<String> loreContent;
            private Boolean unbreakable;
            private Integer customModelData;
            private ItemIdentityService.IdentityData identityData;

            public Builder loreMode(final LoreMode mode) { this.loreMode = mode; return this; }
            public Builder loreContent(final List<String> content) { this.loreContent = content; return this; }
            public Builder unbreakable(final Boolean unbreakable) { this.unbreakable = unbreakable; return this; }
            public Builder customModelData(final Integer data) { this.customModelData = data; return this; }
            public Builder identityData(final ItemIdentityService.IdentityData data) { this.identityData = data; return this; }

            public CreationContext build() {
                return new CreationContext(loreMode, loreContent, unbreakable, customModelData, identityData);
            }
        }
    }

    public static final class MetaFlagSet {
        private final Boolean unbreakable;
        private final Boolean glow;
        private final Boolean hideAttributes;

        private MetaFlagSet(final Boolean unbreakable, final Boolean glow, final Boolean hideAttributes) {
            this.unbreakable = unbreakable;
            this.glow = glow;
            this.hideAttributes = hideAttributes;
        }

        public static MetaFlagSet of(final Boolean unbreakable, final Boolean glow, final Boolean hideAttributes) {
            return new MetaFlagSet(unbreakable, glow, hideAttributes);
        }

        public Boolean isUnbreakable() { return unbreakable; }
        public Boolean isGlow() { return glow; }
        public Boolean isHideAttributes() { return hideAttributes; }
    }
}
