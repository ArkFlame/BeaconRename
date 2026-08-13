package com.arkflame.flameforge.config;

import com.arkflame.flameforge.model.*;
import org.bukkit.configuration.ConfigurationSection;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class TierParser {
    private static final int SUPPORTED_SCHEMA_VERSION_V1 = 1;
    private static final int SUPPORTED_SCHEMA_VERSION_V2 = 2;
    private static final String LEGACY_BACKUP_DIR = ".legacy-v1-backup";

    private final ValidationReport report;
    private final YamlValues values;
    private final MigrationContext migrationContext;

    public TierParser(ValidationReport report, YamlValues values, MigrationContext migrationContext) {
        this.report = report;
        this.values = values;
        this.migrationContext = migrationContext;
    }

    public static TierDefinition parse(ConfigurationSection section, ValidationReport report) {
        return parse(section, report, MigrationContext.noOp());
    }

    public static TierDefinition parse(ConfigurationSection section, ValidationReport report, MigrationContext migrationContext) {
        YamlValues root = new YamlValues(section, report);
        return parse(root, report, migrationContext);
    }

    public static TierDefinition parse(YamlValues values, ValidationReport report) {
        return parse(values, report, MigrationContext.noOp());
    }

    public static TierDefinition parse(YamlValues values, ValidationReport report, MigrationContext migrationContext) {
        TierParser parser = new TierParser(report, values, migrationContext);
        return parser.doParse();
    }

    public static TierDefinition parseBundled(InputStream bundledStream, ValidationReport report, String resourceName) {
        try {
            org.bukkit.configuration.file.YamlConfiguration bundledYaml =
                org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(
                    new InputStreamReader(bundledStream, StandardCharsets.UTF_8));
            YamlValues bundledValues = new YamlValues(bundledYaml, report);
            TierParser parser = new TierParser(report, bundledValues, MigrationContext.noOp());
            return parser.doParseV2();
        } catch (Exception e) {
            report.addError("", "bundled-parse",
                "Failed to parse bundled v2 resource " + resourceName + ": " + e.getMessage());
            return null;
        }
    }

    private TierDefinition doParse() {
        int schemaVersion = values.getSchemaVersion("schema-version");
        if (schemaVersion == SUPPORTED_SCHEMA_VERSION_V2) {
            return doParseV2();
        } else if (schemaVersion == SUPPORTED_SCHEMA_VERSION_V1) {
            return doParseV1();
        } else {
            report.addError(values.getRootPath(), "schema-version",
                "Unsupported schema version " + schemaVersion + ", expected " + SUPPORTED_SCHEMA_VERSION_V1 + " or " + SUPPORTED_SCHEMA_VERSION_V2);
            return null;
        }
    }

    private TierDefinition doParseV1() {
        String id = values.getString("id");
        if (id == null || id.isEmpty()) {
            report.addError(values.getRootPath(), "id", "Tier id is required");
            return null;
        }

        int priority = values.getInt("priority", 0);
        int level = priority + 1;

        if (migrationContext != null && migrationContext.shouldMigrate()) {
            File legacyFile = migrationContext.getLegacyFile();
            if (legacyFile != null && legacyFile.exists()) {
                String hash = hashLegacyFile(legacyFile);
                if (hash != null) {
                    File backupDir = new File(migrationContext.getTiersDirectory(), LEGACY_BACKUP_DIR);
                    File backupFile = new File(backupDir, legacyFile.getName() + "." + hash + ".yml");
                    try {
                        Files.createDirectories(backupDir.getParentFile().toPath());
                        Files.createDirectories(backupDir.toPath());
                        Files.copy(legacyFile.toPath(), backupFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                    } catch (IOException e) {
                        report.addError(values.getRootPath(), "migration-backup",
                            "Failed to backup v1 tier file: " + e.getMessage());
                    }
                }
            }
        }

        String bundledResourceName = "tier" + level + ".yml";
        InputStream bundledStream = migrationContext != null ?
            migrationContext.getBundledResource(bundledResourceName) : null;

        if (bundledStream == null) {
            report.addError(values.getRootPath(), "bundled-v2-required",
                "Schema v1 tier '" + id + "' requires bundled v2 resource '" + bundledResourceName +
                "' for level " + level + " but it was not found in the plugin JAR");
            return null;
        }

        if (migrationContext != null && migrationContext.shouldAtomicallyReplace()) {
            File legacyFile = migrationContext.getLegacyFile();
            if (legacyFile != null && legacyFile.exists()) {
                try {
                    Files.delete(legacyFile.toPath());
                } catch (IOException e) {
                    report.addError(values.getRootPath(), "migration-replace",
                        "Failed to atomically replace v1 tier file: " + e.getMessage());
                }
            }
        }

        return parseFromBundledV2(bundledStream, bundledResourceName);
    }

    private TierDefinition parseFromBundledV2(InputStream bundledStream, String resourceName) {
        try {
            org.bukkit.configuration.file.YamlConfiguration bundledYaml =
                org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(
                    new InputStreamReader(bundledStream, StandardCharsets.UTF_8));
            YamlValues bundledValues = new YamlValues(bundledYaml, report);
            TierParser parser = new TierParser(report, bundledValues, MigrationContext.noOp());
            return parser.doParseV2();
        } catch (Exception e) {
            report.addError(values.getRootPath(), "bundled-v2-parse",
                "Failed to parse bundled v2 resource " + resourceName + ": " + e.getMessage());
            return null;
        } finally {
            try {
                bundledStream.close();
            } catch (Exception ignored) {
            }
        }
    }

    private String hashLegacyFile(File file) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] fileBytes = Files.readAllBytes(file.toPath());
            byte[] hashBytes = md.digest(fileBytes);
            String hex = bytesToHex(hashBytes);
            return hex.substring(0, 12);
        } catch (NoSuchAlgorithmException | IOException e) {
            return null;
        }
    }

    private static String bytesToHex(byte[] bytes) {
        char[] hexChars = new char[bytes.length * 2];
        for (int i = 0; i < bytes.length; i++) {
            int v = bytes[i] & 0xFF;
            hexChars[i * 2] = Character.forDigit(v >>> 4, 16);
            hexChars[i * 2 + 1] = Character.forDigit(v & 0x0F, 16);
        }
        return new String(hexChars);
    }

    private TierDefinition doParseV2() {
        String id = values.getString("id");
        if (id == null || id.isEmpty()) {
            report.addError(values.getRootPath(), "id", "Tier id is required");
            return null;
        }

        int level = values.getInt("level", 0);

        boolean enabled = values.getBoolean("enabled", true);

        String permission = values.getString("permission", "");

        YamlValues displayValues = values.sub("display");
        TierDefinition.TierDisplay display = parseDisplay(displayValues);

        YamlValues inputValues = values.sub("input");
        InputConfig inputConfig = parseInput(inputValues);

        YamlValues requirementsValues = values.sub("requirements");
        TierRequirements requirements = parseRequirements(requirementsValues);

        YamlValues chancesValues = values.sub("chances");
        TierChances chances = parseChances(chancesValues);

        YamlValues breakValues = values.sub("break");
        BreakPolicy breakPolicy = parseBreakPolicy(breakValues);

        YamlValues curseValues = values.sub("curse");
        CurseDefinition curseDefinition = parseCurse(curseValues);

        YamlValues animationValues = values.sub("animation");
        ForgeAnimationProfile animationProfile = parseForgeAnimationProfile(animationValues);

        List<ForgeVariant> variants = parseVariants(values.sub("variants"));

        return new TierDefinition(
            id,
            level,
            enabled,
            permission,
            display,
            inputConfig.cooldownSeconds,
            inputConfig.allowedGroups,
            inputConfig.deniedMaterials,
            requirements,
            chances,
            breakPolicy,
            curseDefinition,
            animationProfile,
            variants
        );
    }

    private InputConfig parseInput(YamlValues input) {
        if (input == null || input.getRawSection() == null) {
            return new InputConfig(Collections.emptyList(), Collections.emptyList(), 0L);
        }

        List<String> allowedGroups = input.getStringList("allowed-groups", Collections.singletonList("ANY"));
        List<String> deniedMaterials = input.getStringList("denied-materials", Collections.emptyList());
        long cooldownSeconds = input.getLong("cooldown-seconds", 0L);

        return new InputConfig(allowedGroups, deniedMaterials, cooldownSeconds);
    }

    private static class InputConfig {
        final List<String> allowedGroups;
        final List<String> deniedMaterials;
        final long cooldownSeconds;

        InputConfig(List<String> allowedGroups, List<String> deniedMaterials, long cooldownSeconds) {
            this.allowedGroups = allowedGroups;
            this.deniedMaterials = deniedMaterials;
            this.cooldownSeconds = cooldownSeconds;
        }
    }

    private TierDefinition.TierDisplay parseDisplay(YamlValues display) {
        if (display == null || display.getRawSection() == null) {
            return new TierDefinition.TierDisplay("", Collections.emptyList(), false, "AIR");
        }

        String name = display.getString("name", "");
        List<String> lore = display.getStringList("lore", Collections.emptyList());
        boolean glow = display.getBoolean("glow", false);
        String icon = display.getString("icon", "AIR");

        return new TierDefinition.TierDisplay(name, lore, glow, icon);
    }

    private TierRequirements parseRequirements(YamlValues requirements) {
        if (requirements == null || requirements.getRawSection() == null) {
            return new TierRequirements(TierRequirements.Combine.ALL,
                new TierRequirements.XpRequirement(false, 0),
                new TierRequirements.MoneyRequirement(false, BigDecimal.ZERO),
                new TierRequirements.ItemsRequirement(false, Collections.emptyList()));
        }

        String combineStr = requirements.getString("combine", "ALL");
        TierRequirements.Combine combine;
        try {
            combine = TierRequirements.Combine.valueOf(combineStr);
        } catch (IllegalArgumentException e) {
            report.addError(requirements.getRootPath(), "combine", "Unknown combine mode: " + combineStr);
            combine = TierRequirements.Combine.ALL;
        }

        YamlValues xpValues = requirements.sub("xp");
        TierRequirements.XpRequirement xp = parseXpRequirement(xpValues);

        YamlValues moneyValues = requirements.sub("money");
        TierRequirements.MoneyRequirement money = parseMoneyRequirement(moneyValues);

        List<TierRequirements.ItemRequirement> items = parseItemRequirements(requirements.sub("items"));

        return new TierRequirements(combine, xp, money, new TierRequirements.ItemsRequirement(false, items));
    }

    private TierRequirements.XpRequirement parseXpRequirement(YamlValues xp) {
        if (xp == null || xp.getRawSection() == null) {
            return new TierRequirements.XpRequirement(false, 0);
        }

        boolean enabled = xp.getBoolean("enabled", false);
        BigDecimal amount = parseDecimal(xp, "amount", BigDecimal.ZERO);

        return new TierRequirements.XpRequirement(enabled, amount.intValue());
    }

    private TierRequirements.MoneyRequirement parseMoneyRequirement(YamlValues money) {
        if (money == null || money.getRawSection() == null) {
            return new TierRequirements.MoneyRequirement(false, BigDecimal.ZERO);
        }

        boolean enabled = money.getBoolean("enabled", false);
        BigDecimal amount = parseDecimal(money, "amount", BigDecimal.ZERO);

        return new TierRequirements.MoneyRequirement(enabled, amount);
    }

    private List<TierRequirements.ItemRequirement> parseItemRequirements(YamlValues items) {
        if (items == null || items.getRawSection() == null) {
            return Collections.emptyList();
        }

        List<TierRequirements.ItemRequirement> result = new ArrayList<>();

        YamlValues requiredSection = items.sub("required");
        if (requiredSection == null || requiredSection.getRawSection() == null) {
            return Collections.emptyList();
        }

        ConfigurationSection section = requiredSection.getRawSection();
        if (!(section.get("required") instanceof List)) {
            return Collections.emptyList();
        }

        Object raw = section.get("required");
        if (raw instanceof List) {
            List<?> list = (List<?>) raw;
            for (Object item : list) {
                if (item instanceof Map) {
                    Map<?, ?> itemMap = (Map<?, ?>) item;
                    List<String> materials = new ArrayList<>();
                    Object materialsObj = itemMap.get("materials");
                    if (materialsObj instanceof List) {
                        for (Object m : (List<?>) materialsObj) {
                            materials.add(String.valueOf(m));
                        }
                    }
                    int amount = 1;
                    Object amountObj = itemMap.get("amount");
                    if (amountObj instanceof Number) {
                        amount = ((Number) amountObj).intValue();
                    }
                    Object displayNameObj = itemMap.get("display-name");
                    String displayName = displayNameObj != null ? String.valueOf(displayNameObj) : "";
                    if (!materials.isEmpty()) {
                        result.add(new TierRequirements.ItemRequirement(materials, amount, displayName));
                    }
                }
            }
        }

        return result;
    }

    private TierChances parseChances(YamlValues chances) {
        if (chances == null || chances.getRawSection() == null) {
            return new TierChances(100.0, 0.0, 0.0);
        }

        BigDecimal success = parseDecimal(chances, "success", new BigDecimal("100.0"));
        BigDecimal breakChance = parseDecimal(chances, "break", BigDecimal.ZERO);
        BigDecimal curseChance = parseDecimal(chances, "curse", BigDecimal.ZERO);

        return new TierChances(success.doubleValue(), breakChance.doubleValue(), curseChance.doubleValue());
    }

    private BreakPolicy parseBreakPolicy(YamlValues breakPolicy) {
        if (breakPolicy == null || breakPolicy.getRawSection() == null) {
            return BreakPolicy.defaultPolicy();
        }

        boolean resetTier = breakPolicy.getBoolean("reset-tier", true);
        int targetTier = breakPolicy.getInt("target-tier", 0);
        boolean resetDisplayName = breakPolicy.getBoolean("reset-display-name", true);
        boolean resetLore = breakPolicy.getBoolean("reset-lore", true);
        boolean resetEnchantments = breakPolicy.getBoolean("reset-enchantments", true);
        boolean resetAttributes = breakPolicy.getBoolean("reset-attributes", true);
        boolean resetPowers = breakPolicy.getBoolean("reset-powers", true);
        boolean resetCustomModelData = breakPolicy.getBoolean("reset-custom-model-data", true);
        boolean destroyItem = breakPolicy.getBoolean("destroy-item", false);
        String resultDisplayName = breakPolicy.getString("result-display-name", null);
        List<String> resultLore = breakPolicy.getStringList("result-lore", Collections.emptyList());

        return new BreakPolicy(resetTier, targetTier, resetEnchantments, resetDisplayName, resetLore,
            resetAttributes, resetPowers, resetCustomModelData, destroyItem, resultDisplayName, resultLore);
    }

    private CurseDefinition parseCurse(YamlValues curse) {
        if (curse == null || curse.getRawSection() == null) {
            return new CurseDefinition("", Collections.emptyList(), Collections.emptyList());
        }

        String displayName = curse.getString("display-name", "");
        List<String> lore = curse.getStringList("lore", Collections.emptyList());
        List<String> enchantmentCandidates = curse.getStringList("enchantment-candidates", Collections.emptyList());

        return new CurseDefinition(displayName, lore, enchantmentCandidates);
    }

    private ForgeAnimationProfile parseForgeAnimationProfile(YamlValues animation) {
        if (animation == null || animation.getRawSection() == null) {
            return new ForgeAnimationProfile(20, 4, null, null, null, null, null, null);
        }

        int durationTicks = animation.getInt("duration-ticks", 20);
        int intervalTicks = animation.getInt("interval-ticks", 4);

        ForgeAnimationProfile.ChargeSound chargeSound = parseChargeSound(animation.sub("charge-sound"));
        ForgeAnimationProfile.ChargeParticle chargeParticle = parseChargeParticle(animation.sub("charge-particle"));
        ForgeAnimationProfile.ImpactParticle impactParticle = parseImpactParticle(animation.sub("impact-particle"));

        ForgeAnimationProfile.OutcomeFeedback successFeedback = parseOutcomeFeedback(animation.sub("success"));
        ForgeAnimationProfile.OutcomeFeedback breakFeedback = parseOutcomeFeedback(animation.sub("break"));
        ForgeAnimationProfile.OutcomeFeedback curseFeedback = parseOutcomeFeedback(animation.sub("curse"));

        return new ForgeAnimationProfile(durationTicks, intervalTicks, chargeSound, chargeParticle,
            impactParticle, successFeedback, breakFeedback, curseFeedback);
    }

    private ForgeAnimationProfile.ChargeSound parseChargeSound(YamlValues chargeSound) {
        if (chargeSound == null || chargeSound.getRawSection() == null) {
            return null;
        }

        List<String> candidates = chargeSound.getStringList("candidates", Collections.emptyList());
        BigDecimal volume = parseDecimal(chargeSound, "volume", BigDecimal.ONE);
        BigDecimal startPitch = parseDecimal(chargeSound, "start-pitch", new BigDecimal("0.50"));
        BigDecimal endPitch = parseDecimal(chargeSound, "end-pitch", new BigDecimal("2.00"));

        return new ForgeAnimationProfile.ChargeSound(candidates, volume, startPitch, endPitch);
    }

    private ForgeAnimationProfile.ChargeParticle parseChargeParticle(YamlValues chargeParticle) {
        if (chargeParticle == null || chargeParticle.getRawSection() == null) {
            return null;
        }

        List<String> candidates = chargeParticle.getStringList("candidates", Collections.emptyList());
        int count = chargeParticle.getInt("count", 12);
        BigDecimal radius = parseDecimal(chargeParticle, "radius", new BigDecimal("1.20"));

        return new ForgeAnimationProfile.ChargeParticle(candidates, count, radius);
    }

    private ForgeAnimationProfile.ImpactParticle parseImpactParticle(YamlValues impactParticle) {
        if (impactParticle == null || impactParticle.getRawSection() == null) {
            return null;
        }

        List<String> candidates = impactParticle.getStringList("candidates", Collections.emptyList());
        int count = impactParticle.getInt("count", 20);
        BigDecimal radius = parseDecimal(impactParticle, "radius", BigDecimal.ONE);

        return new ForgeAnimationProfile.ImpactParticle(candidates, count, radius);
    }

    private ForgeAnimationProfile.OutcomeFeedback parseOutcomeFeedback(YamlValues feedback) {
        if (feedback == null || feedback.getRawSection() == null) {
            return null;
        }

        List<String> soundCandidates = feedback.getStringList("sound-candidates", Collections.emptyList());
        List<String> particleCandidates = feedback.getStringList("particle-candidates", Collections.emptyList());
        String title = feedback.getString("title", null);

        return new ForgeAnimationProfile.OutcomeFeedback(soundCandidates, particleCandidates, title);
    }

    private List<ForgeVariant> parseVariants(YamlValues variants) {
        if (variants == null) {
            return Collections.emptyList();
        }

        ConfigurationSection section = variants.getRawSection();
        if (section == null) {
            return Collections.emptyList();
        }

        List<ForgeVariant> result = new ArrayList<>();

        for (String key : section.getKeys(false)) {
            YamlValues variantValues = variants.sub(key);
            ForgeVariant variant = parseVariant(variantValues, key);
            if (variant != null) {
                result.add(variant);
            }
        }

        return result;
    }

    private ForgeVariant parseVariant(YamlValues variant, String variantId) {
        ConfigurationSection section = variant.getRawSection();
        if (section == null) {
            return null;
        }

        BigDecimal weight = parseDecimal(variant, "weight", null);
        if (weight == null) {
            report.addError(variant.getRootPath(), "variant-weight",
                "Variant '" + variantId + "' has invalid weight");
            return null;
        }
        if (weight.compareTo(BigDecimal.ZERO) <= 0) {
            report.addError(variant.getRootPath(), "variant-weight",
                "Variant '" + variantId + "' weight must be > 0, got " + weight);
            return null;
        }
        if (weight.scale() > 6) {
            report.addError(variant.getRootPath(), "variant-weight",
                "Variant '" + variantId + "' weight scale must be <= 6, got " + weight.scale());
            return null;
        }
        Object rawApplicableGroups = section.get("applicable-groups");
        List<String> applicableGroups = new ArrayList<>();
        if (rawApplicableGroups instanceof List) {
            for (Object o : (List<?>) rawApplicableGroups) {
                applicableGroups.add(String.valueOf(o));
            }
        } else {
            applicableGroups.add("ANY");
        }
        String displayName = section.get("display-name") != null ? String.valueOf(section.get("display-name")) : "";
        Object rawLore = section.get("lore");
        List<String> lore = new ArrayList<>();
        if (rawLore instanceof List) {
            for (Object o : (List<?>) rawLore) {
                lore.add(String.valueOf(o));
            }
        }
        String icon = section.get("icon") != null ? String.valueOf(section.get("icon")) : null;

        List<EnchantSpec> enchantments = parseEnchantmentSpecs(section);
        List<ForgeAttributeDefinition> attributes = parseAttributeSpecs(section);
        List<ForgePowerDefinition> powers = parsePowerSpecs(section);

        return new ForgeVariant(variantId, displayName, lore, weight.doubleValue(), icon,
            applicableGroups, enchantments, attributes, powers);
    }

    private List<EnchantSpec> parseEnchantmentSpecs(ConfigurationSection section) {
        Object raw = section.get("enchantments");
        if (!(raw instanceof List)) {
            return Collections.emptyList();
        }
        List<EnchantSpec> result = new ArrayList<>();
        List<?> list = (List<?>) raw;
        for (int i = 0; i < list.size(); i++) {
            Object item = list.get(i);
            if (!(item instanceof Map)) {
                report.addError(section.getCurrentPath(), "enchantments",
                    "Enchantment at index " + i + " must be a map");
                continue;
            }
            Map<?, ?> map = (Map<?, ?>) item;
            List<String> candidates = new ArrayList<>();
            Object candidatesObj = map.get("candidates");
            if (candidatesObj instanceof List) {
                for (Object c : (List<?>) candidatesObj) {
                    candidates.add(String.valueOf(c));
                }
            }
            if (candidates.isEmpty()) {
                report.addError(section.getCurrentPath(), "enchantment-candidates",
                    "Enchantment candidates list cannot be empty");
                continue;
            }
            int minLevel = 1;
            int maxLevel = Integer.MAX_VALUE;
            boolean unsafe = false;
            Object minLevelObj = map.get("min-level");
            if (minLevelObj instanceof Number) {
                minLevel = ((Number) minLevelObj).intValue();
            }
            if (minLevel < 1) {
                report.addError(section.getCurrentPath(), "min-level",
                    "Enchantment min-level must be >= 1, got " + minLevel);
                continue;
            }
            Object maxLevelObj = map.get("max-level");
            if (maxLevelObj instanceof Number) {
                maxLevel = ((Number) maxLevelObj).intValue();
            }
            if (maxLevel < minLevel) {
                report.addError(section.getCurrentPath(), "max-level",
                    "Enchantment max-level must be >= min-level, got max=" + maxLevel + " min=" + minLevel);
                continue;
            }
            Object unsafeObj = map.get("unsafe");
            if (unsafeObj instanceof Boolean) {
                unsafe = ((Boolean) unsafeObj).booleanValue();
            }
            for (String enchantName : candidates) {
                result.add(EnchantSpec.of(enchantName, minLevel, minLevel, maxLevel, unsafe));
            }
        }
        return result;
    }

    private List<ForgeAttributeDefinition> parseAttributeSpecs(ConfigurationSection section) {
        Object raw = section.get("attributes");
        if (!(raw instanceof List)) {
            return Collections.emptyList();
        }
        List<ForgeAttributeDefinition> result = new ArrayList<>();
        List<?> list = (List<?>) raw;
        for (int i = 0; i < list.size(); i++) {
            Object item = list.get(i);
            if (!(item instanceof Map)) {
                report.addError(section.getCurrentPath(), "attributes",
                    "Attribute at index " + i + " must be a map");
                continue;
            }
            Map<?, ?> map = (Map<?, ?>) item;
            String id = map.get("id") != null ? String.valueOf(map.get("id")) : null;
            if (id == null || id.trim().isEmpty()) {
                report.addError(section.getCurrentPath(), "attribute-id",
                    "Attribute id cannot be blank");
                continue;
            }
            String typeStr = map.get("type") != null ? String.valueOf(map.get("type")) : null;
            ForgeAttributeDefinition.AttributeType type = null;
            if (typeStr == null) {
                report.addError(section.getCurrentPath(), "attribute-type",
                    "Attribute type is required for attribute '" + id + "'");
                continue;
            }
            try {
                type = ForgeAttributeDefinition.AttributeType.valueOf(typeStr);
            } catch (IllegalArgumentException e) {
                report.addError(section.getCurrentPath(), "attribute-type",
                    "Invalid attribute type '" + typeStr + "' for attribute '" + id + "'");
                continue;
            }
            double value = 0;
            Object valueObj = map.get("value");
            if (valueObj instanceof Number) {
                value = ((Number) valueObj).doubleValue();
            } else if (valueObj instanceof String) {
                try {
                    value = new BigDecimal((String) valueObj).doubleValue();
                } catch (NumberFormatException e) {
                    report.addError(section.getCurrentPath(), "attribute-value",
                        "Invalid attribute value '" + valueObj + "' for attribute '" + id + "'");
                    continue;
                }
            } else if (valueObj != null) {
                report.addError(section.getCurrentPath(), "attribute-value",
                    "Invalid attribute value type for attribute '" + id + "'");
                continue;
            }
            result.add(new ForgeAttributeDefinition(id, type, value));
        }
        return result;
    }

    private List<ForgePowerDefinition> parsePowerSpecs(ConfigurationSection section) {
        Object raw = section.get("powers");
        if (!(raw instanceof List)) {
            return Collections.emptyList();
        }
        List<ForgePowerDefinition> result = new ArrayList<>();
        List<?> list = (List<?>) raw;
        for (int i = 0; i < list.size(); i++) {
            Object item = list.get(i);
            if (!(item instanceof Map)) {
                report.addError(section.getCurrentPath(), "powers",
                    "Power at index " + i + " must be a map");
                continue;
            }
            Map<?, ?> map = (Map<?, ?>) item;
            String powerId = map.get("id") != null ? String.valueOf(map.get("id")) : null;
            if (powerId == null || powerId.trim().isEmpty()) {
                report.addError(section.getCurrentPath(), "power-id",
                    "Power id cannot be blank");
                continue;
            }
            String typeStr = map.get("type") != null ? String.valueOf(map.get("type")) : null;
            ForgePowerDefinition.PowerType type;
            if (typeStr == null) {
                report.addError(section.getCurrentPath(), "power-type",
                    "Power type is required for power '" + powerId + "'");
                continue;
            }
            try {
                type = ForgePowerDefinition.PowerType.valueOf(typeStr);
            } catch (IllegalArgumentException e) {
                report.addError(section.getCurrentPath(), "power-type",
                    "Invalid power type '" + typeStr + "' for power '" + powerId + "'");
                continue;
            }
            int cooldownTicks = 0;
            Object cooldownObj = map.get("cooldown-ticks");
            if (cooldownObj instanceof Number) {
                cooldownTicks = ((Number) cooldownObj).intValue();
            } else if (cooldownObj != null && !(cooldownObj instanceof String && ((String) cooldownObj).isEmpty())) {
                report.addError(section.getCurrentPath(), "cooldown-ticks",
                    "Invalid cooldown-ticks for power '" + powerId + "'");
            }
            int hitInterval = 1;
            Object hitIntervalObj = map.get("hit-interval");
            if (hitIntervalObj instanceof Number) {
                hitInterval = ((Number) hitIntervalObj).intValue();
                if (hitInterval <= 0) {
                    report.addError(section.getCurrentPath(), "hit-interval",
                        "hit-interval must be > 0 for power '" + powerId + "', got " + hitInterval);
                    continue;
                }
            } else if (hitIntervalObj != null) {
                report.addError(section.getCurrentPath(), "hit-interval",
                    "Invalid hit-interval for power '" + powerId + "'");
                continue;
            }
            BigDecimal chance = parsePowerDecimal(map, section, "chance", BigDecimal.ONE, BigDecimal.ZERO, BigDecimal.ONE, powerId);
            if (chance == null) {
                continue;
            }
            List<String> effectCandidates = new ArrayList<>();
            Object effectObj = map.get("effect-candidates");
            if (effectObj instanceof List) {
                for (Object o : (List<?>) effectObj) {
                    effectCandidates.add(String.valueOf(o));
                }
            }
            int durationTicks = 0;
            Object durationObj = map.get("duration-ticks");
            if (durationObj instanceof Number) {
                durationTicks = ((Number) durationObj).intValue();
            }
            int amplifier = 0;
            Object amplifierObj = map.get("amplifier");
            if (amplifierObj instanceof Number) {
                amplifier = ((Number) amplifierObj).intValue();
            }
            int fireTicks = 0;
            Object fireObj = map.get("fire-ticks");
            if (fireObj instanceof Number) {
                fireTicks = ((Number) fireObj).intValue();
            }
            BigDecimal healAmount = BigDecimal.ZERO;
            Object healObj = map.get("heal-amount");
            if (healObj instanceof Number) {
                healAmount = BigDecimal.valueOf(((Number) healObj).doubleValue());
            } else if (healObj instanceof String) {
                try {
                    healAmount = new BigDecimal((String) healObj);
                } catch (NumberFormatException e) {
                    report.addError(section.getCurrentPath(), "heal-amount",
                        "Invalid heal-amount decimal for power '" + powerId + "': " + healObj);
                    continue;
                }
            } else if (healObj != null) {
                report.addError(section.getCurrentPath(), "heal-amount",
                    "Invalid heal-amount for power '" + powerId + "'");
                continue;
            }
            BigDecimal horizontalStrength = BigDecimal.ONE;
            Object horizObj = map.get("horizontal-strength");
            if (horizObj instanceof Number) {
                horizontalStrength = BigDecimal.valueOf(((Number) horizObj).doubleValue());
            } else if (horizObj instanceof String) {
                try {
                    horizontalStrength = new BigDecimal((String) horizObj);
                } catch (NumberFormatException e) {
                    report.addError(section.getCurrentPath(), "horizontal-strength",
                        "Invalid horizontal-strength decimal for power '" + powerId + "': " + horizObj);
                    continue;
                }
            } else if (horizObj != null) {
                report.addError(section.getCurrentPath(), "horizontal-strength",
                    "Invalid horizontal-strength for power '" + powerId + "'");
                continue;
            }
            BigDecimal verticalStrength = BigDecimal.ZERO;
            Object vertObj = map.get("vertical-strength");
            if (vertObj instanceof Number) {
                verticalStrength = BigDecimal.valueOf(((Number) vertObj).doubleValue());
            } else if (vertObj instanceof String) {
                try {
                    verticalStrength = new BigDecimal((String) vertObj);
                } catch (NumberFormatException e) {
                    report.addError(section.getCurrentPath(), "vertical-strength",
                        "Invalid vertical-strength decimal for power '" + powerId + "': " + vertObj);
                    continue;
                }
            } else if (vertObj != null) {
                report.addError(section.getCurrentPath(), "vertical-strength",
                    "Invalid vertical-strength for power '" + powerId + "'");
                continue;
            }
            List<String> particleCandidates = new ArrayList<>();
            Object particleObj = map.get("particle-candidates");
            if (particleObj instanceof List) {
                for (Object o : (List<?>) particleObj) {
                    particleCandidates.add(String.valueOf(o));
                }
            } else if (particleObj != null) {
                report.addError(section.getCurrentPath(), "particle-candidates",
                    "Invalid particle-candidates for power '" + powerId + "'");
                continue;
            }
            BigDecimal radius = parsePowerDecimal(map, section, "radius", BigDecimal.ZERO,
                BigDecimal.ZERO, new BigDecimal("16"), powerId);
            BigDecimal damageAmount = parsePowerDecimal(map, section, "damage-amount", BigDecimal.ZERO,
                BigDecimal.ZERO, new BigDecimal("40"), powerId);
            Integer pulseCount = parsePowerInt(map, section, "pulse-count", 1, 1, 20, powerId);
            Integer pulseIntervalTicks = parsePowerInt(map, section, "pulse-interval-ticks", 10, 1, 200, powerId);
            Integer maxTargets = parsePowerInt(map, section, "max-targets", 1, 1, 16, powerId);
            Integer chainDelayTicks = parsePowerInt(map, section, "chain-delay-ticks", 0, 0, 40, powerId);
            Integer trailPoints = parsePowerInt(map, section, "trail-points", 8, 2, 32, powerId);
            BigDecimal primaryKnockbackMultiplier = parsePowerDecimal(map, section,
                "primary-knockback-multiplier", BigDecimal.ONE, BigDecimal.ONE, new BigDecimal("4"), powerId);
            BigDecimal secondaryDamageMultiplier = parsePowerDecimal(map, section,
                "secondary-damage-multiplier", BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ONE, powerId);
            if (radius == null || damageAmount == null || pulseCount == null || pulseIntervalTicks == null
                || maxTargets == null || chainDelayTicks == null || trailPoints == null
                || primaryKnockbackMultiplier == null || secondaryDamageMultiplier == null) {
                continue;
            }
            List<ForgePowerDefinition.ActivationSlot> activationSlots = new ArrayList<>();
            Object slotObj = map.get("activation-slots");
            if (slotObj instanceof List) {
                for (Object slotRaw : (List<?>) slotObj) {
                    String slotStr = String.valueOf(slotRaw).trim().toUpperCase(Locale.ROOT);
                    String normalized = slotStr.replace("_", "");
                    if (normalized.equals("MAINHAND")) {
                        activationSlots.add(ForgePowerDefinition.ActivationSlot.MAINHAND);
                    } else if (normalized.equals("OFFHAND")) {
                        activationSlots.add(ForgePowerDefinition.ActivationSlot.OFFHAND);
                    } else if (normalized.equals("ARMOR") || normalized.equals("HEADCHESTLEGSFEET")) {
                        activationSlots.add(ForgePowerDefinition.ActivationSlot.HEAD);
                        activationSlots.add(ForgePowerDefinition.ActivationSlot.CHEST);
                        activationSlots.add(ForgePowerDefinition.ActivationSlot.LEGS);
                        activationSlots.add(ForgePowerDefinition.ActivationSlot.FEET);
                    } else if (normalized.equals("HEAD") || normalized.equals("CHEST") || normalized.equals("LEGS") || normalized.equals("FEET") || normalized.equals("INVENTORY")) {
                        try {
                            activationSlots.add(ForgePowerDefinition.ActivationSlot.valueOf(normalized));
                        } catch (IllegalArgumentException e) {
                            report.addError(section.getCurrentPath(), "activation-slots",
                                "Unknown activation slot '" + slotStr + "' for power '" + powerId + "'");
                        }
                    } else {
                        report.addError(section.getCurrentPath(), "activation-slots",
                            "Unknown activation slot '" + slotStr + "' for power '" + powerId + "'");
                    }
                }
            }
            result.add(new ForgePowerDefinition(powerId, type, cooldownTicks, hitInterval, chance,
                effectCandidates, durationTicks, amplifier, fireTicks, healAmount,
                horizontalStrength, verticalStrength, activationSlots, particleCandidates, radius,
                damageAmount, pulseCount, pulseIntervalTicks, maxTargets, chainDelayTicks, trailPoints,
                primaryKnockbackMultiplier, secondaryDamageMultiplier));
        }
        return result;
    }

    private BigDecimal parsePowerDecimal(Map<?, ?> map, ConfigurationSection section, String key,
                                         BigDecimal defaultValue, BigDecimal min, BigDecimal max,
                                         String powerId) {
        Object raw = map.get(key);
        if (raw == null) {
            return defaultValue;
        }
        BigDecimal value;
        try {
            if (raw instanceof Number) {
                value = new BigDecimal(raw.toString());
            } else if (raw instanceof String) {
                value = new BigDecimal((String) raw);
            } else {
                throw new NumberFormatException("not decimal");
            }
        } catch (NumberFormatException e) {
            report.addError(section.getCurrentPath(), key,
                "Invalid " + key + " decimal for power '" + powerId + "': " + raw);
            return null;
        }
        if (value.compareTo(min) < 0 || value.compareTo(max) > 0) {
            report.addError(section.getCurrentPath(), key,
                key + " must be between " + min + " and " + max + " for power '" + powerId + "', got " + value);
            return null;
        }
        return value;
    }

    private Integer parsePowerInt(Map<?, ?> map, ConfigurationSection section, String key,
                                  int defaultValue, int min, int max, String powerId) {
        Object raw = map.get(key);
        if (raw == null) {
            return defaultValue;
        }
        if (!(raw instanceof Number) || raw instanceof Float || raw instanceof Double
            && ((Double) raw).doubleValue() % 1 != 0) {
            report.addError(section.getCurrentPath(), key,
                "Invalid " + key + " for power '" + powerId + "': " + raw);
            return null;
        }
        int value = ((Number) raw).intValue();
        if (value < min || value > max) {
            report.addError(section.getCurrentPath(), key,
                key + " must be between " + min + " and " + max + " for power '" + powerId + "', got " + value);
            return null;
        }
        return value;
    }

    private BigDecimal parseDecimal(YamlValues values, String path, BigDecimal def) {
        ConfigurationSection section = values.getRawSection();
        if (section == null) {
            return def;
        }
        Object raw = section.get(path);
        if (raw == null) {
            return def;
        }
        if (raw instanceof Number) {
            Number num = (Number) raw;
            if (num.doubleValue() % 1 == 0) {
                return BigDecimal.valueOf(num.longValue());
            }
            return BigDecimal.valueOf(num.doubleValue());
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

    public static final class MigrationContext {
        private final File tiersDirectory;
        private final File legacyFile;
        private final boolean migrate;
        private final boolean atomicReplace;
        private final java.util.function.Function<String, InputStream> resourceLoader;

        private MigrationContext(File tiersDirectory, File legacyFile, boolean migrate,
                                 boolean atomicReplace,
                                 java.util.function.Function<String, InputStream> resourceLoader) {
            this.tiersDirectory = tiersDirectory;
            this.legacyFile = legacyFile;
            this.migrate = migrate;
            this.atomicReplace = atomicReplace;
            this.resourceLoader = resourceLoader;
        }

        public static MigrationContext noOp() {
            return new MigrationContext(null, null, false, false, name -> null);
        }

        public static MigrationContext forMigration(File tiersDirectory, File legacyFile,
                                                     boolean atomicReplace,
                                                     java.util.function.Function<String, InputStream> resourceLoader) {
            return new MigrationContext(tiersDirectory, legacyFile, true, atomicReplace, resourceLoader);
        }

        public File getTiersDirectory() {
            return tiersDirectory;
        }

        public File getLegacyFile() {
            return legacyFile;
        }

        public boolean shouldMigrate() {
            return migrate;
        }

        public boolean shouldAtomicallyReplace() {
            return atomicReplace;
        }

        public InputStream getBundledResource(String name) {
            return resourceLoader.apply(name);
        }
    }
}
