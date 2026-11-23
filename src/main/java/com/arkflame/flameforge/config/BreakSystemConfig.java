package com.arkflame.flameforge.config;

import com.arkflame.flameforge.model.BreakPolicy;
import com.arkflame.flameforge.model.CurseDefinition;
import com.arkflame.flameforge.model.FailureVariant;
import com.arkflame.flameforge.model.TierDefinition;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Reads the root {@code break-system} section into modular failure variants.
 *
 * <p>The flat ConfigSnapshot leaf map keeps dotted keys, so a server-defined variant only
 * needs keys under {@code break-system.failure-variants.<id>} to be discovered. Missing
 * values fall back to the tier's legacy break/curse blocks, then to built-in defaults.
 */
public final class BreakSystemConfig {
    public static final String BREAK = "break";
    public static final String CURSE = "curse";
    private static final String ROOT = "break-system.";
    private static final String VARIANTS = ROOT + "failure-variants.";
    private static final List<String> DEFAULT_BREAK_LORE = Arrays.asList(
            "<dark_gray>Forge fracture",
            "<red>● Previous forge powers were stripped.",
            "<dark_red>● This item cannot be reforged or combined.");
    private static final List<String> DEFAULT_CURSE_LORE = Arrays.asList(
            "<dark_gray>Forge curse",
            "<dark_gray>● <red>Previous forge powers were stripped.",
            "<dark_gray>● <dark_red>This item cannot be reforged or combined.");

    private final boolean enabled;
    private final Set<String> variantIds;
    private final Map<String, Map<String, Object>> overrides;

    private BreakSystemConfig(boolean enabled, Set<String> variantIds,
                              Map<String, Map<String, Object>> overrides) {
        this.enabled = enabled;
        this.variantIds = Collections.unmodifiableSet(variantIds);
        Map<String, Map<String, Object>> copy = new LinkedHashMap<>();
        for (Map.Entry<String, Map<String, Object>> entry : overrides.entrySet()) {
            copy.put(entry.getKey(), Collections.unmodifiableMap(new LinkedHashMap<>(entry.getValue())));
        }
        this.overrides = Collections.unmodifiableMap(copy);
    }

    public static BreakSystemConfig from(ConfigSnapshot snapshot) {
        if (snapshot == null) {
            return new BreakSystemConfig(false, Collections.emptySet(), Collections.emptyMap());
        }
        Map<String, Object> root = snapshot.getRootSettings();
        boolean enabled = bool(root.get(ROOT + "enabled"), true);
        Set<String> ids = new LinkedHashSet<>();
        ids.add(BREAK);
        ids.add(CURSE);
        for (String key : root.keySet()) {
            if (key != null && key.startsWith(VARIANTS)) {
                String rest = key.substring(VARIANTS.length());
                int dot = rest.indexOf('.');
                String id = dot > 0 ? rest.substring(0, dot) : rest;
                if (!id.isEmpty()) ids.add(id);
            }
        }
        Map<String, Map<String, Object>> overrides = new LinkedHashMap<>();
        for (String id : ids) {
            Map<String, Object> values = new LinkedHashMap<>();
            String prefix = VARIANTS + id + ".";
            for (Map.Entry<String, Object> entry : root.entrySet()) {
                if (entry.getKey() != null && entry.getKey().startsWith(prefix)) {
                    values.put(entry.getKey().substring(prefix.length()), entry.getValue());
                }
            }
            overrides.put(id, values);
        }
        return new BreakSystemConfig(enabled, ids, overrides);
    }

    public boolean isEnabled() { return enabled; }
    public Set<String> getVariantIds() { return variantIds; }

    public boolean isVariantEnabled(String id) {
        if (id == null) return false;
        FailureVariant variant = resolve(id, null);
        return variant != null && variant.isEnabled();
    }

    public Collection<String> getEnabledVariantIds() {
        List<String> enabledIds = new ArrayList<>();
        for (String id : variantIds) {
            if (isVariantEnabled(id)) enabledIds.add(id);
        }
        return enabledIds;
    }

    public FailureVariant resolve(String id, TierDefinition tier) {
        if (id == null) return null;
        Map<String, Object> values = overrides.containsKey(id)
                ? overrides.get(id) : Collections.emptyMap();
        BreakPolicy legacyBreak = tier != null ? tier.getBreakPolicy() : null;
        CurseDefinition legacyCurse = tier != null ? tier.getCurseDefinition() : null;
        boolean isBreak = BREAK.equals(id);
        boolean isCurse = CURSE.equals(id);

        boolean enabledValue = bool(values.get("enabled"), true);
        boolean destroy = bool(values.get("destroy-item"),
                isBreak && legacyBreak != null ? legacyBreak.isDestroyItem() : false);
        String display = str(values.get("display-name"),
                isBreak ? (legacyBreak != null ? legacyBreak.getResultDisplayName()
                        : BreakPolicy.DefaultText.ENGLISH.getDisplayName())
                : isCurse && legacyCurse != null ? legacyCurse.getName()
                : isCurse ? "<gradient:#7f1d1d:#111827><bold>Cursed %base_name%</bold></gradient>" : "");
        List<String> lore = list(values.get("lore"),
                isBreak ? (legacyBreak != null && !legacyBreak.getResultLore().isEmpty()
                        ? legacyBreak.getResultLore() : DEFAULT_BREAK_LORE)
                : isCurse && legacyCurse != null && !legacyCurse.getLore().isEmpty()
                        ? legacyCurse.getLore()
                : isCurse ? DEFAULT_CURSE_LORE : Collections.emptyList());
        List<String> enchants = list(values.get("enchantments"),
                isCurse && legacyCurse != null ? legacyCurse.getEnchantments() : Collections.emptyList());
        double damage = number(values.get("damage-reduction"), 2.0);
        int tierTarget = integer(values.get("target-tier"),
                isBreak && legacyBreak != null ? legacyBreak.getTargetTier() : 0);

        return new FailureVariant(id, enabledValue, destroy, display, lore,
                bool(values.get("durability-half"), true), damage, enchants,
                bool(values.get("reset-tier"),
                        isBreak && legacyBreak != null ? legacyBreak.isResetTier() : true),
                tierTarget,
                bool(values.get("reset-enchantments"),
                        isBreak && legacyBreak != null ? legacyBreak.isResetEnchants() : true),
                bool(values.get("reset-display-name"),
                        isBreak && legacyBreak != null ? legacyBreak.isResetName() : true),
                bool(values.get("reset-lore"),
                        isBreak && legacyBreak != null ? legacyBreak.isResetLore() : true),
                bool(values.get("reset-attributes"),
                        isBreak && legacyBreak != null ? legacyBreak.isResetAttributes() : true),
                bool(values.get("reset-powers"),
                        isBreak && legacyBreak != null ? legacyBreak.isResetPowers() : true),
                bool(values.get("reset-custom-model-data"),
                        isBreak && legacyBreak != null ? legacyBreak.isResetCustomModelData() : true),
                bool(values.get("mark-as-failed"), true),
                str(values.get("message"), ""),
                list(values.get("particle-candidates"), Collections.emptyList()),
                list(values.get("sound-candidates"), Collections.emptyList()),
                str(values.get("title"), ""),
                list(values.get("subtitle"), Collections.emptyList()));
    }

    private static boolean bool(Object value, boolean fallback) {
        if (value == null) return fallback;
        if (value instanceof Boolean) return (Boolean) value;
        return Boolean.parseBoolean(String.valueOf(value));
    }

    private static String str(Object value, String fallback) {
        if (value == null) return fallback;
        String text = String.valueOf(value);
        return text.trim().isEmpty() ? fallback : text;
    }

    private static double number(Object value, double fallback) {
        if (value == null) return fallback;
        try {
            return Double.parseDouble(String.valueOf(value));
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static int integer(Object value, int fallback) {
        if (value == null) return fallback;
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static List<String> list(Object value, List<String> fallback) {
        if (value instanceof List) {
            List<String> result = new ArrayList<>();
            for (Object item : (List<?>) value) {
                if (item != null) result.add(String.valueOf(item));
            }
            return result;
        }
        return fallback != null ? new ArrayList<>(fallback) : Collections.emptyList();
    }
}
