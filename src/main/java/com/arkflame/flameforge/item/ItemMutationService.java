package com.arkflame.flameforge.item;

import com.arkflame.flameforge.compat.material.MaterialResolver;
import com.arkflame.flameforge.model.AttributeSpec;
import com.arkflame.flameforge.model.BreakPolicy;
import com.arkflame.flameforge.model.CurseDefinition;
import com.arkflame.flameforge.model.EnchantSpec;
import com.arkflame.flameforge.model.ForgeOutcomeCategory;
import com.arkflame.flameforge.model.ForgeVariant;
import com.arkflame.flameforge.text.TextRenderer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class ItemMutationService {
    private static final ItemMutationService INSTANCE = new ItemMutationService();
    private static final LegacyComponentSerializer LEGACY_SERIALIZER = LegacyComponentSerializer.legacySection();
    private static final String LEGACY_PREFIX = "\u00A70\u00A70FLAMEFORGE:";

    private final EnchantmentResolver enchantResolver = new EnchantmentResolver();
    private TextRenderer textRenderer;

    private ItemMutationService() {
    }

    public static ItemMutationService getInstance() {
        return INSTANCE;
    }

    public static void setTextRenderer(TextRenderer textRenderer) {
        INSTANCE.textRenderer = textRenderer;
    }

    public MutationResult mutateSuccess(ItemStack input, ForgeVariant variant,
                                       Map<Enchantment, Integer> baselineEnchants,
                                       List<AttributeSpec> baselineAttributes,
                                       List<String> baselinePowers,
                                       int targetTier,
                                       ItemIdentityService.IdentityData identityData) {
        if (input == null) {
            return MutationResult.fail("null input");
        }
        final ItemStack clone = input.clone();
        clone.setAmount(1);
        final ItemMeta meta = clone.getItemMeta();
        if (meta == null) {
            return MutationResult.fail("item has no meta");
        }

        removeFlameForgeMetadata(meta);

        restoreBaselineEnchants(meta, baselineEnchants);

        Map<Enchantment, Integer> rolledEnchants = buildRolledEnchants(variant, baselineEnchants);
        applyEnchants(meta, rolledEnchants);

        if (variant.getName() != null && !variant.getName().isEmpty()) {
            applyName(meta, variant.getName());
        }

        if (variant.getLore() != null && !variant.getLore().isEmpty()) {
            applyLore(meta, variant.getLore());
        }

        List<AttributeSpec> recordedAttributes = new ArrayList<>();
        if (variant.getAttributeModifiers() != null && !variant.getAttributeModifiers().isEmpty()) {
            recordedAttributes = applyVariantAttributes(clone, variant.getAttributeModifiers());
        }

        List<String> recordedPowers = variant.getPowerIds() != null ?
            new ArrayList<>(variant.getPowerIds()) : new ArrayList<>();

        try {
            clone.setItemMeta(meta);
        } catch (Exception e) {
            return MutationResult.fail("failed to apply meta: " + e.getMessage());
        }

        ItemIdentityService.IdentityData updatedIdentity = buildIdentityForSuccess(
            identityData, targetTier, recordedAttributes, recordedPowers);
        Optional<ItemStack> written = ItemIdentityService.getInstance().writeIdentity(clone, updatedIdentity);
        if (written.isPresent()) {
            List<String> warnings = new ArrayList<>();
            return MutationResult.success(written.get(), warnings);
        }
        return MutationResult.success(clone, new ArrayList<>());
    }

    public MutationResult mutateCurse(ItemStack input, CurseDefinition curse,
                                     boolean currentlyCursed,
                                     ItemIdentityService.IdentityData identityData) {
        if (input == null) {
            return MutationResult.fail("null input");
        }
        final ItemStack clone = input.clone();
        clone.setAmount(1);
        final ItemMeta meta = clone.getItemMeta();
        if (meta == null) {
            return MutationResult.fail("item has no meta");
        }

        if (curse.getName() != null && !curse.getName().isEmpty()) {
            applyName(meta, curse.getName());
        }

        if (curse.getLore() != null && !curse.getLore().isEmpty()) {
            applyLore(meta, curse.getLore());
        }

        if (!currentlyCursed && curse.getEnchantments() != null && !curse.getEnchantments().isEmpty()) {
            applyFirstSupportedCurse(meta, curse.getEnchantments());
        }

        setCursedFlag(clone, true);

        try {
            clone.setItemMeta(meta);
        } catch (Exception e) {
            return MutationResult.fail("failed to apply meta: " + e.getMessage());
        }

        if (identityData != null) {
            ItemIdentityService.getInstance().writeIdentity(clone, identityData);
        }

        return MutationResult.success(clone, new ArrayList<>());
    }

    public MutationResult mutateBreak(ItemStack input, BreakPolicy policy,
                                     ItemIdentityService.IdentityData identityData) {
        if (input == null) {
            return MutationResult.fail("null input");
        }
        final ItemStack clone = input.clone();
        clone.setAmount(1);
        clone.setType(input.getType());
        final ItemMeta meta = clone.getItemMeta();
        if (meta == null) {
            return MutationResult.fail("item has no meta");
        }

        if (policy == null) {
            policy = BreakPolicy.none();
        }

        if (policy.isResetName()) {
            meta.setDisplayName(null);
        }

        if (policy.isResetLore()) {
            meta.setLore(new ArrayList<>());
        }

        if (policy.isResetEnchants()) {
            enchantResolver.clearFromMeta(meta);
        }

        if (policy.isResetAttributes()) {
            clearAttributes(meta);
        }

        if (policy.isResetPowers()) {
        }

        AttributeBridge.getInstance().setCustomModelData(clone, null);

        try {
            clone.setItemMeta(meta);
        } catch (Exception e) {
            return MutationResult.fail("failed to apply meta: " + e.getMessage());
        }

        ItemIdentityService.IdentityData breakIdentity = buildBreakIdentity(identityData, policy);
        ItemIdentityService.getInstance().writeIdentity(clone, breakIdentity);

        if (policy.isDestroyItem()) {
            return MutationResult.destroyed();
        }

        return MutationResult.success(clone, new ArrayList<>());
    }

    private void removeFlameForgeMetadata(ItemMeta meta) {
        try {
            List<String> lore = meta.getLore();
            if (lore != null) {
                List<String> cleaned = new ArrayList<>();
                for (String line : lore) {
                    if (line != null && !line.startsWith(LEGACY_PREFIX)) {
                        cleaned.add(line);
                    }
                }
                meta.setLore(cleaned);
            }
        } catch (Exception e) {
        }
    }

    private void restoreBaselineEnchants(ItemMeta meta, Map<Enchantment, Integer> baselineEnchants) {
        if (baselineEnchants == null || baselineEnchants.isEmpty()) {
            return;
        }
        for (Map.Entry<Enchantment, Integer> entry : baselineEnchants.entrySet()) {
            try {
                meta.addEnchant(entry.getKey(), entry.getValue(), false);
            } catch (Exception e) {
            }
        }
    }

    private Map<Enchantment, Integer> buildRolledEnchants(ForgeVariant variant,
                                                          Map<Enchantment, Integer> baseline) {
        Map<Enchantment, Integer> result = new HashMap<>();
        if (baseline != null) {
            result.putAll(baseline);
        }
        if (variant != null && variant.getEnchantmentCandidates() != null) {
            for (String enchantKey : variant.getEnchantmentCandidates()) {
                enchantResolver.resolve(enchantKey).ifPresent(enchant -> {
                    int currentLevel = result.getOrDefault(enchant, 0);
                    int rolledLevel = enchantResolver.resolveLevel(1);
                    int effective = Math.max(currentLevel, rolledLevel);
                    effective = enchantResolver.clampLevel(effective, enchant.getMaxLevel());
                    result.put(enchant, effective);
                });
            }
        }
        return result;
    }

    private void applyEnchants(ItemMeta meta, Map<Enchantment, Integer> enchants) {
        if (meta == null || enchants == null) {
            return;
        }
        for (Map.Entry<Enchantment, Integer> entry : enchants.entrySet()) {
            try {
                meta.addEnchant(entry.getKey(), entry.getValue(), false);
            } catch (Exception e) {
            }
        }
    }

    private List<AttributeSpec> applyVariantAttributes(ItemStack item, Map<String, Integer> modifiers) {
        List<AttributeSpec> applied = new ArrayList<>();
        if (modifiers == null || modifiers.isEmpty()) {
            return applied;
        }
        for (Map.Entry<String, Integer> entry : modifiers.entrySet()) {
            AttributeSpec spec = AttributeSpec.of(entry.getKey(), entry.getValue().doubleValue(),
                entry.getValue().doubleValue(), "ADD");
            applied.add(spec);
        }
        AttributeBridge.apply(item, applied);
        return applied;
    }

    private void applyFirstSupportedCurse(ItemMeta meta, List<String> enchantKeys) {
        for (String key : enchantKeys) {
            Optional<Enchantment> opt = enchantResolver.resolve(key);
            if (opt.isPresent()) {
                Enchantment curse = opt.get();
                if (enchantResolver.isCursed(curse)) {
                    try {
                        int level = enchantResolver.resolveLevel(1);
                        meta.addEnchant(curse, level, false);
                        return;
                    } catch (Exception e) {
                    }
                }
            }
        }
    }

    private void setCursedFlag(ItemStack item, boolean cursed) {
    }

    private void clearAttributes(ItemMeta meta) {
    }

    private ItemIdentityService.IdentityData buildIdentityForSuccess(
            ItemIdentityService.IdentityData current,
            int targetTier,
            List<AttributeSpec> attributes,
            List<String> powers) {
        if (current == null) {
            current = ItemIdentityService.IdentityData.fresh();
        }
        return current
            .withHighestTier(targetTier)
            .withLastTier(String.valueOf(targetTier))
            .withLastOutcome("SUCCESS")
            .incrementReforge();
    }

    private ItemIdentityService.IdentityData buildBreakIdentity(
            ItemIdentityService.IdentityData current,
            BreakPolicy policy) {
        if (current == null) {
            current = ItemIdentityService.IdentityData.fresh();
        }
        ItemIdentityService.IdentityData result = current
            .withLastOutcome("BREAK");
        if (policy != null && policy.isResetTier()) {
            result = result.withHighestTier(0);
        }
        if (policy != null && policy.isResetIdentity()) {
            result = ItemIdentityService.IdentityData.fresh();
        }
        return result;
    }

    private void applyName(ItemMeta meta, String name) {
        if (meta == null || name == null) {
            return;
        }
        try {
            meta.setDisplayName(renderText(name));
        } catch (Exception e) {
        }
    }

    private void applyLore(ItemMeta meta, List<String> lore) {
        if (meta == null || lore == null) {
            return;
        }
        List<String> rendered = new ArrayList<>();
        for (String line : lore) {
            rendered.add(renderText(line));
        }
        try {
            meta.setLore(rendered);
        } catch (Exception e) {
        }
    }

    private String renderText(String input) {
        if (input == null) return "";
        if (textRenderer == null) return input;
        Component component = textRenderer.renderToComponent(input);
        return LEGACY_SERIALIZER.serialize(component);
    }

    public static final class MutationResult {
        private final boolean success;
        private final boolean destroyed;
        private final ItemStack result;
        private final List<String> warnings;

        private MutationResult(final boolean success, final boolean destroyed,
                              final ItemStack result, final List<String> warnings) {
            this.success = success;
            this.destroyed = destroyed;
            this.result = result;
            this.warnings = warnings != null ? warnings : new ArrayList<>();
        }

        public static MutationResult success(final ItemStack result) {
            return new MutationResult(true, false, result, new ArrayList<>());
        }

        public static MutationResult success(final ItemStack result, final List<String> warnings) {
            return new MutationResult(true, false, result, warnings);
        }

        public static MutationResult fail(final String reason) {
            final List<String> list = new ArrayList<>();
            list.add(reason);
            return new MutationResult(false, false, null, list);
        }

        public static MutationResult destroyed() {
            return new MutationResult(true, true, null, new ArrayList<>());
        }

        public boolean isSuccess() { return success; }
        public boolean isDestroyed() { return destroyed; }
        public ItemStack getResult() { return result; }
        public List<String> getWarnings() { return warnings; }
    }
}
