package com.arkflame.flameforge.item;

import com.arkflame.flameforge.config.ConfigService;
import com.arkflame.flameforge.model.AttributeSpec;
import com.arkflame.flameforge.model.BreakPolicy;
import com.arkflame.flameforge.model.CurseDefinition;
import com.arkflame.flameforge.model.ForgeAttributeDefinition;
import com.arkflame.flameforge.model.ForgeVariant;
import com.arkflame.flameforge.model.TierDefinition;
import com.arkflame.flameforge.text.MessageArguments;
import com.arkflame.flameforge.text.TextRenderer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class ItemMutationService {
    private static final String LEGACY_PREFIX = "\u00A70\u00A70FLAMEFORGE:";
    private static final String POWER_PREFIX = "\u00A76FLAMEFORGE_PWR:";
    private static final String ATTRIBUTE_PREFIX = "\u00A75FLAMEFORGE_ATTR:";
    private static final String CURSED_PREFIX = "\u00A7cFLAMEFORGE_CURSED:";

    private final ItemIdentityService identityService;
    private final AttributeBridge attributeBridge;
    private final EnchantmentResolver enchantmentResolver;
    private final TextRenderer textRenderer;
    private final ItemDisplayNameResolver displayNameResolver;

    public ItemMutationService(
            ItemIdentityService identityService,
            AttributeBridge attributeBridge,
            EnchantmentResolver enchantmentResolver,
            TextRenderer textRenderer) {
        this(identityService, attributeBridge, enchantmentResolver, textRenderer, (ConfigService) null);
    }

    public ItemMutationService(
            ItemIdentityService identityService,
            AttributeBridge attributeBridge,
            EnchantmentResolver enchantmentResolver,
            TextRenderer textRenderer,
            ConfigService configService) {
        this(identityService, attributeBridge, enchantmentResolver, textRenderer,
                new ItemDisplayNameResolver(identityService, configService));
    }

    public ItemMutationService(
            ItemIdentityService identityService,
            AttributeBridge attributeBridge,
            EnchantmentResolver enchantmentResolver,
            TextRenderer textRenderer,
            ItemDisplayNameResolver displayNameResolver) {
        this.identityService = identityService;
        this.attributeBridge = attributeBridge;
        this.enchantmentResolver = enchantmentResolver;
        this.textRenderer = textRenderer;
        this.displayNameResolver = displayNameResolver;
    }

    public MutationResult mutateSuccess(
            ItemStack input,
            TierDefinition targetTier,
            ForgeVariant variant,
            ItemIdentityCodec.Identity identity,
            UUID forgeId) {
        if (input == null) {
            return MutationResult.fail("null input");
        }
        final String baseDisplayName = resolveBaseDisplayName(input, identity);
        final MessageArguments arguments = MessageArguments.create().string("base_name", baseDisplayName);
        final ItemStack clone = input.clone();
        clone.setAmount(1);
        final ItemMeta meta = clone.getItemMeta();
        if (meta == null) {
            return MutationResult.fail("item has no meta");
        }

        removeFlameForgeMetadata(meta);

        Map<Enchantment, Integer> baselineEnchants = readBaselineEnchants(input);
        restoreBaselineEnchants(meta, baselineEnchants);

        applyVariantEnchants(meta, variant);

        if (variant.getName() != null && !variant.getName().isEmpty()) {
            applyName(meta, variant.getName(), arguments);
        }

        if (variant.getLore() != null && !variant.getLore().isEmpty()) {
            applyLore(meta, variant.getLore(), arguments);
        }

        List<AttributeSpec> recordedAttributes = new ArrayList<>();
        if (variant.getAttributes() != null && !variant.getAttributes().isEmpty()) {
            recordedAttributes = applyVariantAttributes(clone, variant.getAttributes());
        }

        List<String> recordedPowers = variant.getPowerIds() != null ?
                new ArrayList<>(variant.getPowerIds()) : new ArrayList<>();

        try {
            clone.setItemMeta(meta);
        } catch (Exception e) {
            return MutationResult.fail("failed to apply meta: " + e.getMessage());
        }

        ItemIdentityCodec.Identity updatedIdentity = buildRichIdentityForSuccess(
                identity, targetTier.getLevel(), targetTier.getId(), variant.getId(),
                recordedAttributes, recordedPowers, forgeId, input, baseDisplayName);

        Optional<ItemStack> written = identityService.writeForgeIdentity(clone, updatedIdentity);
        if (written.isPresent()) {
            return MutationResult.success(written.get(), new ArrayList<>());
        }
        return MutationResult.fail("failed to write forge identity");
    }

    public MutationResult mutateBreak(
            ItemStack input,
            BreakPolicy policy,
            ItemIdentityCodec.Identity identity,
            UUID forgeId) {
        if (input == null) {
            return MutationResult.fail("null input");
        }
        final String baseDisplayName = resolveBaseDisplayName(input, identity);
        final MessageArguments arguments = MessageArguments.create().string("base_name", baseDisplayName);
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
            enchantmentResolver.clearFromMeta(meta);
        }

        if (policy.isResetAttributes()) {
            attributeBridge.removeFlameForgeAttributes(clone);
        }

        if (policy.isResetPowers()) {
            clearPowers(meta);
        }

        if (policy.isResetCustomModelData()) {
            attributeBridge.setCustomModelData(clone, null);
        }

        if (!policy.isDestroyItem()) {
            applyName(meta, policy.getResultDisplayName(), arguments);
            applyLore(meta, policy.getResultLore(), arguments);
        }

        try {
            clone.setItemMeta(meta);
        } catch (Exception e) {
            return MutationResult.fail("failed to apply meta: " + e.getMessage());
        }

        ItemIdentityCodec.Identity breakIdentity = buildRichIdentityForBreak(
                identity, policy, input, forgeId, baseDisplayName);
        Optional<ItemStack> written = identityService.writeForgeIdentity(clone, breakIdentity);
        if (!written.isPresent()) {
            return MutationResult.fail("failed to write forge identity");
        }

        if (policy.isDestroyItem()) {
            return MutationResult.destroyed();
        }

        return MutationResult.success(written.get(), new ArrayList<>());
    }

    public MutationResult mutateCurse(
            ItemStack input,
            CurseDefinition curse,
            boolean currentlyCursed,
            ItemIdentityCodec.Identity identity,
            UUID forgeId) {
        if (input == null) {
            return MutationResult.fail("null input");
        }
        final String baseDisplayName = resolveBaseDisplayName(input, identity);
        final MessageArguments arguments = MessageArguments.create().string("base_name", baseDisplayName);
        final ItemStack clone = input.clone();
        clone.setAmount(1);
        final ItemMeta meta = clone.getItemMeta();
        if (meta == null) {
            return MutationResult.fail("item has no meta");
        }

        if (curse.getName() != null && !curse.getName().isEmpty()) {
            applyName(meta, curse.getName(), arguments);
        }

        if (curse.getLore() != null && !curse.getLore().isEmpty()) {
            applyLore(meta, curse.getLore(), arguments);
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

        ItemIdentityCodec.Identity curseIdentity = buildRichIdentityForCurse(identity, forgeId);
        Optional<ItemStack> written = identityService.writeForgeIdentity(clone, curseIdentity);
        if (written.isPresent()) {
            return MutationResult.success(written.get(), new ArrayList<>());
        }
        return MutationResult.fail("failed to write forge identity");
    }

    private ItemIdentityCodec.Identity buildRichIdentityForSuccess(
            ItemIdentityCodec.Identity current,
            int targetTierLevel,
            String targetTierId,
            String variantId,
            List<AttributeSpec> attributes,
            List<String> powers,
            UUID forgeId,
            ItemStack originalInput,
            String baseDisplayName) {
        if (current == null) {
            current = ItemIdentityCodec.Identity.empty();
        }

        int existingHighestTier = current.getHighestTier();
        int newHighestTier = Math.max(existingHighestTier, targetTierLevel);

        List<String> attributeIds = new ArrayList<>();
        if (attributes != null) {
            for (AttributeSpec attr : attributes) {
                attributeIds.add(attr.getAttribute());
            }
        }

        List<String> powerIds = powers != null ? new ArrayList<>(powers) : new ArrayList<>();

        String baseMaterial = current.getBaseMaterial();
        if (baseMaterial == null && originalInput != null) {
            baseMaterial = originalInput.getType().name();
        }

        String identityBaseDisplayName = current.getBaseDisplayName();
        if (identityBaseDisplayName == null || identityBaseDisplayName.trim().isEmpty()) {
            identityBaseDisplayName = baseDisplayName;
        }

        UUID actualForgeId = forgeId != null ? forgeId : current.getForgeId();
        if (actualForgeId == null) {
            actualForgeId = UUID.randomUUID();
        }

        return current
                .withCurrentTier(targetTierLevel)
                .withHighestTier(newHighestTier)
                .withReforgeCount(current.getReforgeCount() + 1)
                .withLastTierId(targetTierId)
                .withLastVariantId(variantId)
                .withForgeId(actualForgeId)
                .withBaseMaterial(baseMaterial)
                .withBaseDisplayName(identityBaseDisplayName)
                .withActiveAttributeIds(attributeIds)
                .withActivePowerIds(powerIds)
                .withCursed(false);
    }

    private ItemIdentityCodec.Identity buildRichIdentityForBreak(
            ItemIdentityCodec.Identity current,
            BreakPolicy policy,
            ItemStack originalInput,
            UUID forgeId,
            String baseDisplayName) {
        if (current == null) {
            current = ItemIdentityCodec.Identity.empty();
        }

        UUID actualForgeId = forgeId != null ? forgeId : current.getForgeId();
        if (actualForgeId == null) {
            actualForgeId = UUID.randomUUID();
        }

        int currentTier = current.getCurrentTier();
        String lastTierId = current.getLastTierId();
        String lastVariantId = current.getLastVariantId();
        if (policy.isResetTier()) {
            currentTier = policy.getTargetTier();
            lastTierId = null;
            lastVariantId = null;
        }

        String baseMaterial = current.getBaseMaterial();
        if (baseMaterial == null && originalInput != null) {
            baseMaterial = originalInput.getType().name();
        }
        String identityBaseDisplayName = current.getBaseDisplayName();
        if (identityBaseDisplayName == null || identityBaseDisplayName.trim().isEmpty()) {
            identityBaseDisplayName = baseDisplayName;
        }

        ItemIdentityCodec.Identity result = current
                .withCurrentTier(currentTier)
                .withForgeId(actualForgeId)
                .withLastTierId(lastTierId)
                .withLastVariantId(lastVariantId)
                .withBaseMaterial(baseMaterial)
                .withBaseDisplayName(identityBaseDisplayName)
                .withActiveAttributeIds(policy.isResetAttributes()
                        ? Collections.emptyList() : current.getActiveAttributeIds())
                .withActivePowerIds(policy.isResetPowers()
                        ? Collections.emptyList() : current.getActivePowerIds())
                .withForgeEnchantments(policy.isResetEnchants()
                        ? Collections.emptyMap() : current.getForgeEnchantments())
                .withCursed(false);
        return result;
    }

    private ItemIdentityCodec.Identity buildRichIdentityForCurse(
            ItemIdentityCodec.Identity current,
            UUID forgeId) {
        if (current == null) {
            current = ItemIdentityCodec.Identity.empty();
        }

        UUID actualForgeId = forgeId != null ? forgeId : current.getForgeId();
        if (actualForgeId == null) {
            actualForgeId = UUID.randomUUID();
        }

        return current
                .withForgeId(actualForgeId)
                .withCursed(true);
    }

    private String resolveBaseDisplayName(final ItemStack input, final ItemIdentityCodec.Identity identity) {
        return displayNameResolver.resolve(input, identity);
    }

    private void removeFlameForgeMetadata(ItemMeta meta) {
        if (meta == null) {
            return;
        }
        try {
            List<String> lore = meta.getLore();
            if (lore != null) {
                List<String> cleaned = new ArrayList<>();
                for (String line : lore) {
                    if (line != null && !line.startsWith(LEGACY_PREFIX)
                            && !line.startsWith(POWER_PREFIX) && !line.startsWith(ATTRIBUTE_PREFIX)
                            && !line.startsWith(CURSED_PREFIX)) {
                        cleaned.add(line);
                    }
                }
                meta.setLore(cleaned);
            }
        } catch (Exception e) {
            throw new RuntimeException("failed to remove FlameForge metadata", e);
        }
    }

    private Map<Enchantment, Integer> readBaselineEnchants(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return new HashMap<>();
        }
        try {
            return new HashMap<>(item.getItemMeta().getEnchants());
        } catch (Exception e) {
            return new HashMap<>();
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
                throw new RuntimeException("failed to restore enchant " + entry.getKey().getName(), e);
            }
        }
    }

    private void applyVariantEnchants(ItemMeta meta, ForgeVariant variant) {
        if (variant == null || variant.getEnchantments() == null || variant.getEnchantments().isEmpty()) {
            return;
        }
        for (com.arkflame.flameforge.model.EnchantSpec spec : variant.getEnchantments()) {
            enchantmentResolver.resolve(spec.getEnchantmentId()).ifPresent(enchant -> {
                int level = spec.getLevel();
                if (spec.isUnsafe()) {
                    meta.addEnchant(enchant, level, true);
                } else {
                    int clamped = enchantmentResolver.clampLevel(level, enchant.getMaxLevel());
                    meta.addEnchant(enchant, clamped, false);
                }
            });
        }
    }

    private List<AttributeSpec> applyVariantAttributes(ItemStack item, List<ForgeAttributeDefinition> attributes) {
        List<AttributeSpec> recorded = new ArrayList<>();
        if (attributes == null || attributes.isEmpty()) {
            return recorded;
        }
        List<AttributeSpec> nativeSpecs = new ArrayList<>();
        for (ForgeAttributeDefinition attr : attributes) {
            AttributeSpec spec = AttributeSpec.of(attr.getId(), attr.getMultiplier(),
                    attr.getMultiplier(), "ADD");
            recorded.add(spec);
            if (attr.getType() == ForgeAttributeDefinition.AttributeType.ATTACK_DAMAGE_FLAT) {
                nativeSpecs.add(spec);
            }
        }
        attributeBridge.applyAttributes(item, nativeSpecs);
        return recorded;
    }

    private void applyName(ItemMeta meta, String name, MessageArguments arguments) {
        if (meta == null || name == null) {
            return;
        }
        try {
            String rendered = textRenderer.renderItemLegacyInheritedLiteral(name, arguments, "base_name", null);
            meta.setDisplayName(rendered);
        } catch (Exception e) {
            throw new RuntimeException("failed to apply name", e);
        }
    }

    private void applyLore(ItemMeta meta, List<String> lore, MessageArguments arguments) {
        if (meta == null || lore == null) {
            return;
        }
        List<String> rendered = new ArrayList<>();
        for (String line : lore) {
            String renderedLine = textRenderer.renderItemLegacyInheritedLiteral(line, arguments, "base_name", null);
            rendered.add(renderedLine);
        }
        try {
            meta.setLore(rendered);
        } catch (Exception e) {
            throw new RuntimeException("failed to apply lore", e);
        }
    }

    private void applyFirstSupportedCurse(ItemMeta meta, List<String> enchantKeys) {
        for (String key : enchantKeys) {
            Optional<Enchantment> opt = enchantmentResolver.resolve(key);
            if (opt.isPresent()) {
                Enchantment curse = opt.get();
                if (enchantmentResolver.isCursed(curse)) {
                    try {
                        int level = enchantmentResolver.resolveLevel(1);
                        meta.addEnchant(curse, level, false);
                        return;
                    } catch (Exception e) {
                        throw new RuntimeException("failed to apply curse enchant " + curse.getName(), e);
                    }
                }
            }
        }
    }

    private void setCursedFlag(ItemStack item, boolean cursed) {
        if (item == null) {
            return;
        }
        final ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return;
        }
        try {
            java.lang.reflect.Method getPdcMethod = ItemMeta.class.getMethod("getPersistentDataContainer");
            Object pdc = getPdcMethod.invoke(meta);
            if (pdc != null) {
                Class<?> nskClass = Class.forName("org.bukkit.NamespacedKey");
                Class<?> pdcTypeClass = Class.forName("org.bukkit.persistence.PersistentDataType");
                Object stringType = pdcTypeClass.getField("STRING").get(null);
                Object key = nskClass.getConstructor(String.class, String.class).newInstance("flameforge", "cursed");
                java.lang.reflect.Method setMethod = pdc.getClass().getMethod("set",
                        nskClass, pdcTypeClass, Object.class);
                setMethod.invoke(pdc, key, stringType, cursed ? "true" : "false");
                item.setItemMeta(meta);
                return;
            }
        } catch (Exception ignored) {
        }
        try {
            List<String> lore = meta.getLore();
            if (lore == null) {
                lore = new ArrayList<>();
            } else {
                lore = new ArrayList<>(lore);
            }
            lore.removeIf(l -> l != null && l.startsWith(CURSED_PREFIX));
            if (cursed) {
                lore.add(CURSED_PREFIX + "true");
            }
            meta.setLore(lore);
            item.setItemMeta(meta);
        } catch (Exception e) {
            throw new RuntimeException("failed to set cursed flag", e);
        }
    }

    private void clearPowers(ItemMeta meta) {
        if (meta == null) {
            return;
        }
        try {
            List<String> lore = meta.getLore();
            if (lore == null || lore.isEmpty()) {
                return;
            }
            List<String> cleaned = new ArrayList<>();
            for (String line : lore) {
                if (line != null && !line.startsWith(POWER_PREFIX)) {
                    cleaned.add(line);
                }
            }
            meta.setLore(cleaned);
        } catch (Exception e) {
            throw new RuntimeException("failed to clear powers", e);
        }
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
