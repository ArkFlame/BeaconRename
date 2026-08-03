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

        return new BreakPolicy(resetTier, false, resetDisplayName, resetLore,
            resetEnchantments, resetAttributes, resetPowers, false);
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
        BigDecimal weight = parseDecimal(variant, "weight", BigDecimal.ONE);
        List<String> applicableGroups = variant.getStringList("applicable-groups", Collections.singletonList("ANY"));
        String displayName = variant.getString("display-name", "");
        List<String> lore = variant.getStringList("lore", Collections.emptyList());

        List<EnchantSpec> enchantments = parseEnchantSpecs(variant.sub("enchantments"));
        List<ForgeAttributeDefinition> attributes = parseForgeAttributeDefinitions(variant.sub("attributes"));
        List<ForgePowerDefinition> powers = parseForgePowerDefinitions(variant.sub("powers"));

        return new ForgeVariant(variantId, displayName, lore, weight.doubleValue(), null,
            Collections.emptyList(), Collections.emptyMap(), powers);
    }

    private List<EnchantSpec> parseEnchantSpecs(YamlValues enchants) {
        if (enchants == null) {
            return Collections.emptyList();
        }

        ConfigurationSection section = enchants.getRawSection();
        if (section == null) {
            return Collections.emptyList();
        }

        Object raw = section.get("candidates");
        if (raw instanceof List) {
            List<EnchantSpec> result = new ArrayList<>();
            List<?> list = (List<?>) raw;
            int minLevel = enchants.getInt("min-level", 1);
            int maxLevel = enchants.getInt("max-level", Integer.MAX_VALUE);
            boolean unsafe = enchants.getBoolean("unsafe", false);

            for (Object item : list) {
                String enchantName = String.valueOf(item);
                result.add(EnchantSpec.of(enchantName, minLevel, maxLevel));
            }
            return result;
        }

        return Collections.emptyList();
    }

    private List<ForgeAttributeDefinition> parseForgeAttributeDefinitions(YamlValues attributes) {
        if (attributes == null) {
            return Collections.emptyList();
        }

        ConfigurationSection section = attributes.getRawSection();
        if (section == null) {
            return Collections.emptyList();
        }

        List<ForgeAttributeDefinition> result = new ArrayList<>();

        for (String key : section.getKeys(false)) {
            YamlValues attrValues = attributes.sub(key);
            ForgeAttributeDefinition.AttributeType type;
            try {
                type = ForgeAttributeDefinition.AttributeType.valueOf(key);
            } catch (IllegalArgumentException e) {
                report.addError(attrValues.getRootPath(), "type", "Unknown attribute type: " + key);
                continue;
            }
            BigDecimal value = parseDecimal(attrValues, "value", BigDecimal.ZERO);
            result.add(new ForgeAttributeDefinition(type, value.doubleValue()));
        }

        return result;
    }

    private List<ForgePowerDefinition> parseForgePowerDefinitions(YamlValues powers) {
        if (powers == null) {
            return Collections.emptyList();
        }

        ConfigurationSection section = powers.getRawSection();
        if (section == null) {
            return Collections.emptyList();
        }

        List<ForgePowerDefinition> result = new ArrayList<>();

        for (String key : section.getKeys(false)) {
            YamlValues powerValues = powers.sub(key);
            ForgePowerDefinition power = parseForgePowerDefinition(powerValues, key);
            if (power != null) {
                result.add(power);
            }
        }

        return result;
    }

    private ForgePowerDefinition parseForgePowerDefinition(YamlValues power, String powerId) {
        String typeStr = power.getString("type", "ON_HIT_POTION");
        ForgePowerDefinition.PowerType type;
        try {
            type = ForgePowerDefinition.PowerType.valueOf(typeStr);
        } catch (IllegalArgumentException e) {
            report.addError(power.getRootPath(), "type", "Unknown power type: " + typeStr);
            type = ForgePowerDefinition.PowerType.ON_HIT_POTION;
        }

        int cooldownTicks = power.getInt("cooldown-ticks", 0);
        BigDecimal chance = parseDecimal(power, "chance", BigDecimal.ONE);

        List<String> effectCandidates = power.getStringList("effect-candidates", Collections.emptyList());
        int durationTicks = power.getInt("duration-ticks", 0);
        int amplifier = power.getInt("amplifier", 0);
        int fireTicks = power.getInt("fire-ticks", 0);
        BigDecimal healAmount = parseDecimal(power, "heal-amount", BigDecimal.ZERO);
        BigDecimal horizontalStrength = parseDecimal(power, "horizontal-strength", BigDecimal.ONE);
        BigDecimal verticalStrength = parseDecimal(power, "vertical-strength", BigDecimal.ZERO);

        List<String> slotStrs = power.getStringList("activation-slots", Collections.emptyList());
        List<ForgePowerDefinition.ActivationSlot> activationSlots = new ArrayList<>();
        for (String slotStr : slotStrs) {
            try {
                activationSlots.add(ForgePowerDefinition.ActivationSlot.valueOf(slotStr));
            } catch (IllegalArgumentException ignored) {
            }
        }

        return new ForgePowerDefinition(powerId, type, cooldownTicks, chance,
            effectCandidates, durationTicks, amplifier, fireTicks, healAmount,
            horizontalStrength, verticalStrength, activationSlots);
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
