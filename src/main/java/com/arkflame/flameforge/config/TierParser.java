package com.arkflame.flameforge.config;

import com.arkflame.flameforge.model.*;
import org.bukkit.configuration.ConfigurationSection;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class TierParser {
    private static final int SUPPORTED_SCHEMA_VERSION = 1;

    private final ValidationReport report;
    private final YamlValues values;

    private TierParser(ValidationReport report, YamlValues values) {
        this.report = report;
        this.values = values;
    }

    public static TierParseResult parse(ConfigurationSection section, ValidationReport report) {
        YamlValues root = new YamlValues(section, report);
        return parse(root, report);
    }

    public static TierParseResult parse(YamlValues values, ValidationReport report) {
        TierParser parser = new TierParser(report, values);
        return parser.doParse();
    }

    private TierParseResult doParse() {
        int schemaVersion = values.getSchemaVersion("schema-version");
        if (schemaVersion != SUPPORTED_SCHEMA_VERSION) {
            report.addError(values.getRootPath(), "schema-version",
                "Unsupported schema version " + schemaVersion + ", expected " + SUPPORTED_SCHEMA_VERSION);
            return TierParseResult.failure();
        }

        String id = values.getString("id");
        if (id == null || id.isEmpty()) {
            report.addError(values.getRootPath(), "id", "Tier id is required");
            return TierParseResult.failure();
        }

        int priority = values.getInt("priority", 0);

        boolean enabled = values.getBoolean("enabled", true);

        String permission = values.getString("permission", "");

        YamlValues displayValues = values.sub("display");
        TierDisplay display = parseDisplay(displayValues);

        YamlValues costValues = values.sub("cost");
        TierCost cost = parseCost(costValues);

        long cooldownSeconds = values.getLong("cooldown-seconds", 0L);

        YamlValues pityValues = values.sub("pity");
        TierPity pity = parsePity(pityValues);

        YamlValues animationValues = values.sub("animation");
        TierAnimation animation = parseAnimation(animationValues);

        List<OutcomeDefinition> outcomes = parseOutcomes(values.sub("outcomes"));

        TierDefinition tier = TierDefinition.of(
            id,
            priority,
            cost,
            animation.getSuccessDuration(),
            animation.getFailDuration(),
            outcomes
        );

        TierExtra extra = new TierExtra(
            enabled,
            permission,
            display,
            cooldownSeconds,
            pity
        );

        return TierParseResult.success(tier, extra);
    }

    private TierDisplay parseDisplay(YamlValues display) {
        if (display == null || !display.getRawSection().contains("")) {
            return TierDisplay.DEFAULT;
        }

        String name = display.getString("name", "");
        List<String> lore = display.getStringList("lore", Collections.emptyList());
        String material = display.getString("material", "AIR");
        int customModelData = display.getInt("custom-model-data", -1);

        return new TierDisplay(name, lore, material, customModelData);
    }

    private TierCost parseCost(YamlValues cost) {
        if (cost == null) {
            return TierCost.xpOnly(BigDecimal.ZERO);
        }

        String modeStr = cost.getString("mode", "XP_ONLY");
        CostMode mode;
        try {
            mode = CostMode.valueOf(modeStr);
        } catch (IllegalArgumentException e) {
            report.addError(cost.getRootPath(), "mode", "Unknown cost mode: " + modeStr);
            mode = CostMode.XP_ONLY;
        }

        BigDecimal xpCost = parseDecimal(cost, "xp", BigDecimal.ZERO);
        BigDecimal moneyCost = parseDecimal(cost, "money", BigDecimal.ZERO);

        switch (mode) {
            case XP_ONLY:
                return TierCost.xpOnly(xpCost);
            case MONEY_ONLY:
                return TierCost.moneyOnly(moneyCost);
            case XP_AND_MONEY:
            case XP_OR_MONEY:
                return TierCost.xpAndMoney(xpCost, moneyCost);
            default:
                return TierCost.xpOnly(xpCost);
        }
    }

    private BigDecimal parseDecimal(YamlValues values, String path, BigDecimal def) {
        Object raw = values.getRawSection().get(path);
        if (raw == null) {
            return def;
        }
        if (raw instanceof Number) {
            return BigDecimal.valueOf(((Number) raw).doubleValue());
        }
        if (raw instanceof String) {
            try {
                return new BigDecimal((String) raw);
            } catch (NumberFormatException e) {
                report.addError(values.getRootPath(), path, "Invalid decimal: " + raw);
            }
        }
        return def;
    }

    private TierPity parsePity(YamlValues pity) {
        if (pity == null) {
            return TierPity.DEFAULT;
        }

        boolean enabled = pity.getBoolean("enabled", false);
        int threshold = pity.getInt("threshold", 0);
        BigDecimal bonusWeight = parseDecimal(pity, "bonus-weight", BigDecimal.ONE);

        return new TierPity(enabled, threshold, bonusWeight);
    }

    private TierAnimation parseAnimation(YamlValues animation) {
        if (animation == null) {
            return TierAnimation.DEFAULT;
        }

        int successDuration = animation.getInt("success-duration", 40);
        int failDuration = animation.getInt("fail-duration", 20);

        List<AnimationStep> successSteps = parseAnimationSteps(animation.sub("success-steps"));
        List<AnimationStep> failSteps = parseAnimationSteps(animation.sub("fail-steps"));

        return new TierAnimation(successDuration, failDuration, successSteps, failSteps);
    }

    private List<AnimationStep> parseAnimationSteps(YamlValues steps) {
        if (steps == null) {
            return Collections.emptyList();
        }

        ConfigurationSection section = steps.getRawSection();
        if (section == null) {
            return Collections.emptyList();
        }

        List<AnimationStep> result = new ArrayList<>();
        for (String key : section.getKeys(false)) {
            YamlValues stepValues = steps.sub(key);
            int delay = stepValues.getInt("delay", 0);
            String type = stepValues.getString("type", "NONE");
            String data = stepValues.getString("data", null);
            result.add(AnimationStep.of(delay, type, data));
        }
        return result;
    }

    private List<OutcomeDefinition> parseOutcomes(YamlValues outcomes) {
        if (outcomes == null) {
            return Collections.emptyList();
        }

        ConfigurationSection section = outcomes.getRawSection();
        if (section == null) {
            return Collections.emptyList();
        }

        List<OutcomeDefinition> result = new ArrayList<>();
        int order = 0;

        for (String key : section.getKeys(false)) {
            YamlValues outcomeValues = outcomes.sub(key);
            OutcomeDefinition outcome = parseOutcome(outcomeValues, key, order);
            if (outcome != null) {
                result.add(outcome);
                order++;
            }
        }

        return result;
    }

    private OutcomeDefinition parseOutcome(YamlValues outcome, String outcomeId, int displayOrder) {
        String typeStr = outcome.getString("type", "BREAK");
        OutcomeType type;
        try {
            type = OutcomeType.valueOf(typeStr);
        } catch (IllegalArgumentException e) {
            report.addError(outcome.getRootPath(), "type", "Unknown outcome type: " + typeStr);
            type = OutcomeType.BREAK;
        }

        BigDecimal weight = parseDecimal(outcome, "weight", BigDecimal.ONE);
        if (weight == null || weight.compareTo(BigDecimal.ZERO) <= 0) {
            report.addError(outcome.getRootPath(), "weight", "Weight must be positive");
            weight = BigDecimal.ONE;
        }

        ItemMutationSpec mutation = parseMutation(outcome.sub("mutation"));
        List<String> commands = outcome.getStringList("commands", Collections.emptyList());

        return OutcomeDefinition.of(outcomeId, type, weight, mutation, commands, displayOrder);
    }

    private ItemMutationSpec parseMutation(YamlValues mutation) {
        if (mutation == null || !mutation.getRawSection().contains("")) {
            return null;
        }

        String material = mutation.getString("material", null);
        String name = mutation.getString("name", null);
        int amount = mutation.getInt("amount", 1);

        List<EnchantSpec> enchants = parseEnchants(mutation.sub("enchants"));
        List<AttributeSpec> attributes = parseAttributes(mutation.sub("attributes"));

        return ItemMutationSpec.of(material, name, amount, enchants, attributes);
    }

    private List<EnchantSpec> parseEnchants(YamlValues enchants) {
        if (enchants == null) {
            return Collections.emptyList();
        }

        ConfigurationSection section = enchants.getRawSection();
        if (section == null) {
            return Collections.emptyList();
        }

        List<EnchantSpec> result = new ArrayList<>();
        for (String key : section.getKeys(false)) {
            YamlValues enchantValues = enchants.sub(key);
            String enchantName = enchantValues.getString("name", key);
            int minLevel = enchantValues.getInt("min-level", 1);
            int maxLevel = enchantValues.getInt("max-level", Integer.MAX_VALUE);
            result.add(EnchantSpec.of(enchantName, minLevel, maxLevel));
        }
        return result;
    }

    private List<AttributeSpec> parseAttributes(YamlValues attributes) {
        if (attributes == null) {
            return Collections.emptyList();
        }

        ConfigurationSection section = attributes.getRawSection();
        if (section == null) {
            return Collections.emptyList();
        }

        List<AttributeSpec> result = new ArrayList<>();
        for (String key : section.getKeys(false)) {
            YamlValues attrValues = attributes.sub(key);
            String attrName = attrValues.getString("name", key);
            double minValue = attrValues.getDouble("min-value", 0.0);
            double maxValue = attrValues.getDouble("max-value", 0.0);
            String operation = attrValues.getString("operation", "ADD_NUMBER");
            result.add(AttributeSpec.of(attrName, minValue, maxValue, operation));
        }
        return result;
    }

    public static final class TierParseResult {
        private final TierDefinition tier;
        private final TierExtra extra;
        private final boolean success;

        private TierParseResult(TierDefinition tier, TierExtra extra, boolean success) {
            this.tier = tier;
            this.extra = extra;
            this.success = success;
        }

        public static TierParseResult success(TierDefinition tier, TierExtra extra) {
            return new TierParseResult(tier, extra, true);
        }

        public static TierParseResult failure() {
            return new TierParseResult(null, null, false);
        }

        public boolean isSuccess() {
            return success;
        }

        public TierDefinition getTier() {
            return tier;
        }

        public TierExtra getExtra() {
            return extra;
        }
    }

    public static final class TierExtra {
        private final boolean enabled;
        private final String permission;
        private final TierDisplay display;
        private final long cooldownSeconds;
        private final TierPity pity;

        public TierExtra(boolean enabled, String permission, TierDisplay display,
                        long cooldownSeconds, TierPity pity) {
            this.enabled = enabled;
            this.permission = permission;
            this.display = display;
            this.cooldownSeconds = cooldownSeconds;
            this.pity = pity;
        }

        public boolean isEnabled() {
            return enabled;
        }

        public String getPermission() {
            return permission;
        }

        public TierDisplay getDisplay() {
            return display;
        }

        public long getCooldownSeconds() {
            return cooldownSeconds;
        }

        public TierPity getPity() {
            return pity;
        }
    }

    public static final class TierDisplay {
        public static final TierDisplay DEFAULT = new TierDisplay("", Collections.emptyList(), "AIR", -1);

        private final String name;
        private final List<String> lore;
        private final String material;
        private final int customModelData;

        public TierDisplay(String name, List<String> lore, String material, int customModelData) {
            this.name = name;
            this.lore = lore != null ? Collections.unmodifiableList(new ArrayList<>(lore)) : Collections.emptyList();
            this.material = material;
            this.customModelData = customModelData;
        }

        public String getName() {
            return name;
        }

        public List<String> getLore() {
            return lore;
        }

        public String getMaterial() {
            return material;
        }

        public int getCustomModelData() {
            return customModelData;
        }
    }

    public static final class TierPity {
        public static final TierPity DEFAULT = new TierPity(false, 0, BigDecimal.ONE);

        private final boolean enabled;
        private final int threshold;
        private final BigDecimal bonusWeight;

        public TierPity(boolean enabled, int threshold, BigDecimal bonusWeight) {
            this.enabled = enabled;
            this.threshold = threshold;
            this.bonusWeight = bonusWeight;
        }

        public boolean isEnabled() {
            return enabled;
        }

        public int getThreshold() {
            return threshold;
        }

        public BigDecimal getBonusWeight() {
            return bonusWeight;
        }
    }

    public static final class TierAnimation {
        public static final TierAnimation DEFAULT = new TierAnimation(40, 20, Collections.emptyList(), Collections.emptyList());

        private final int successDuration;
        private final int failDuration;
        private final List<AnimationStep> successSteps;
        private final List<AnimationStep> failSteps;

        public TierAnimation(int successDuration, int failDuration,
                            List<AnimationStep> successSteps, List<AnimationStep> failSteps) {
            this.successDuration = successDuration;
            this.failDuration = failDuration;
            this.successSteps = successSteps != null ? Collections.unmodifiableList(new ArrayList<>(successSteps)) : Collections.emptyList();
            this.failSteps = failSteps != null ? Collections.unmodifiableList(new ArrayList<>(failSteps)) : Collections.emptyList();
        }

        public int getSuccessDuration() {
            return successDuration;
        }

        public int getFailDuration() {
            return failDuration;
        }

        public List<AnimationStep> getSuccessSteps() {
            return successSteps;
        }

        public List<AnimationStep> getFailSteps() {
            return failSteps;
        }
    }
}
