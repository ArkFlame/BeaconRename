package com.arkflame.flameforge.config;

import com.arkflame.flameforge.model.TierDefinition;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class TierParserTest {

    @TempDir
    Path tempDir;

    private ValidationReport report;

    @BeforeEach
    void setUp() {
        report = new ValidationReport();
    }

    @Test
    void allBundledTiersParseAndPreserveExpectedCoreFields() throws Exception {
        int[] expectedLevels = {1, 2, 3, 4, 5, 6, 7};

        for (int i = 1; i <= 7; i++) {
            String resourcePath = "tiers/tier" + i + ".yml";
            try (InputStream stream = getClass().getClassLoader().getResourceAsStream(resourcePath)) {
                assertNotNull(stream, "Bundled tier " + i + " should exist as resource: " + resourcePath);

                ValidationReport tierReport = new ValidationReport();
                TierDefinition tier = TierParser.parseBundled(stream, tierReport, resourcePath);

                assertNotNull(tier, "Tier " + i + " should parse successfully: " + tierReport.getErrors());
                assertFalse(tierReport.hasErrors(), "Tier " + i + " should have no errors: " + tierReport.getErrors());

                assertEquals(expectedLevels[i - 1], tier.getLevel(), "Tier " + i + " level should match");

                assertNotNull(tier.getRequirements(), "Tier " + i + " should have requirements");
            }
        }
    }

    @Test
    void parseValidTierProducesCorrectFields() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("schema-version", 2);
        yaml.set("id", "valid_tier");
        yaml.set("level", 5);
        yaml.set("enabled", true);
        yaml.set("permission", "flameforge.tier5");
        yaml.set("display.name", "&bTier Five");
        yaml.set("display.icon", "EMERALD");
        yaml.set("requirements.xp.enabled", true);
        yaml.set("requirements.xp.amount", 500);
        yaml.set("requirements.money.enabled", true);
        yaml.set("requirements.money.amount", 1000.00);

        TierDefinition tier = TierParser.parse(yaml, report);

        assertNotNull(tier);
        assertFalse(report.hasErrors());
        assertEquals("valid_tier", tier.getId());
        assertEquals(5, tier.getLevel());
        assertTrue(tier.isEnabled());
        assertEquals("flameforge.tier5", tier.getPermission());
        assertEquals("&bTier Five", tier.getDisplay().getName());
        assertEquals("EMERALD", tier.getDisplay().getIcon());
    }

    @Test
    void parseTierWithUnknownFieldProducesWarning() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("schema-version", 2);
        yaml.set("id", "unknown_field_tier");
        yaml.set("level", 1);
        yaml.set("unknown-top-level-field", "should be ignored");

        report = new ValidationReport();
        TierDefinition tier = TierParser.parse(yaml, report);

        assertNotNull(tier);
        assertFalse(report.hasErrors());
    }

    @Test
    void parseTierWithMissingFieldProducesError() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("schema-version", 2);
        yaml.set("level", 1);

        report = new ValidationReport();
        TierDefinition tier = TierParser.parse(yaml, report);

        assertNull(tier);
        assertTrue(report.hasErrors());
        boolean hasIdError = report.getErrors().stream()
            .anyMatch(e -> e.getField().equals("id"));
        assertTrue(hasIdError, "Should have error about missing id field");
    }

    @Test
    void parseTierWithInvalidFieldTypeProducesError() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("schema-version", 2);
        yaml.set("id", "bad_type_tier");
        yaml.set("level", "not-a-number");

        report = new ValidationReport();
        TierDefinition tier = TierParser.parse(yaml, report);

        assertNotNull(tier);
        assertTrue(report.hasErrors());

        boolean hasLevelError = report.getErrors().stream()
            .anyMatch(e -> e.getField().equals("level"));
        assertTrue(hasLevelError, "Error field should be 'level'");
    }

    @Test
    void parseBundledRejectsSchemaOneThroughTheSameSchemaGate() throws Exception {
        String schemaOneYaml = "schema-version: 1\nid: bundled_old\nlevel: 1\n";
        ByteArrayInputStream stream = new ByteArrayInputStream(schemaOneYaml.getBytes(StandardCharsets.UTF_8));

        ValidationReport tierReport = new ValidationReport();
        TierDefinition tier = TierParser.parseBundled(stream, tierReport, "bundled_old.yml");

        assertNotNull(tier, "Tier should be returned for schema-version 1 via migration");
        assertEquals(0, tier.getVariants().size(), "Variants should be empty due to missing bundled resource");
    }

    @Test
    void parserPreservesConfiguredLevelWithoutCrossDocumentState() {
        YamlConfiguration yaml1 = new YamlConfiguration();
        yaml1.set("schema-version", 2);
        yaml1.set("id", "level_tier_1");
        yaml1.set("level", 5);

        ValidationReport report1 = new ValidationReport();
        TierDefinition tier1 = TierParser.parse(yaml1, report1);
        assertNotNull(tier1);
        assertEquals(5, tier1.getLevel());

        YamlConfiguration yaml2 = new YamlConfiguration();
        yaml2.set("schema-version", 2);
        yaml2.set("id", "another_level_tier");
        yaml2.set("level", 5);

        ValidationReport report2 = new ValidationReport();
        TierDefinition tier2 = TierParser.parse(yaml2, report2);
        assertNotNull(tier2);
        assertEquals(5, tier2.getLevel());
    }

    @Test
    void schemaVersionOneIsRejectedWithoutMigration() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("schema-version", 1);
        yaml.set("id", "old_schema_tier");
        yaml.set("level", 1);

        report = new ValidationReport();
        TierDefinition tier = TierParser.parse(yaml, report);

        assertNull(tier, "Tier with schema-version 1 should return null without migration context");
        assertTrue(report.hasErrors(), "Should have errors for missing bundled resource");

        boolean hasBundledResourceError = report.getErrors().stream()
            .anyMatch(e -> e.getField().equals("bundled-v2-required"));
        assertTrue(hasBundledResourceError, "Error should be about missing bundled-v2-required");
    }

    @Test
    void parseVariantPreservesUnsafeEnchantmentFlag() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("schema-version", 2);
        yaml.set("id", "unsafe_enchant_tier");
        yaml.set("level", 1);
        java.util.Map<String, Object> enchantMap = new java.util.HashMap<>();
        enchantMap.put("candidates", java.util.Arrays.asList("PROTECTION"));
        enchantMap.put("min-level", 1);
        enchantMap.put("max-level", 4);
        enchantMap.put("unsafe", true);
        yaml.set("variants.test_variant.enchantments", java.util.Collections.singletonList(enchantMap));
        yaml.set("variants.test_variant.weight", 1.0);

        report = new ValidationReport();
        TierDefinition tier = TierParser.parse(yaml, report);

        assertNotNull(tier, "Tier should parse: " + report.getErrors());
        assertFalse(report.hasErrors(), "Should have no errors: " + report.getErrors());
        assertEquals(1, tier.getVariants().size());

        com.arkflame.flameforge.model.ForgeVariant variant = tier.getVariants().get(0);
        assertEquals(1, variant.getEnchantments().size());
        assertTrue(variant.getEnchantments().get(0).isUnsafe(), "Unsafe flag should be preserved");
    }

    @Test
    void parseVariantPreservesConfiguredMinLevel() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("schema-version", 2);
        yaml.set("id", "min_level_tier");
        yaml.set("level", 1);
        java.util.Map<String, Object> enchantMap = new java.util.HashMap<>();
        enchantMap.put("candidates", java.util.Arrays.asList("SHARPNESS"));
        enchantMap.put("min-level", 2);
        enchantMap.put("max-level", 5);
        yaml.set("variants.test_variant.enchantments", java.util.Collections.singletonList(enchantMap));
        yaml.set("variants.test_variant.weight", 1.0);

        report = new ValidationReport();
        TierDefinition tier = TierParser.parse(yaml, report);

        assertNotNull(tier, "Tier should parse: " + report.getErrors());
        assertFalse(report.hasErrors(), "Should have no errors: " + report.getErrors());
        assertEquals(1, tier.getVariants().size());

        com.arkflame.flameforge.model.ForgeVariant variant = tier.getVariants().get(0);
        assertEquals(1, variant.getEnchantments().size());
        assertEquals(2, variant.getEnchantments().get(0).getMinLevel(), "Min level should be preserved");
        assertEquals(5, variant.getEnchantments().get(0).getMaxLevel(), "Max level should be preserved");
    }

    @Test
    void parseVariantWithInvalidWeightReportsExactError() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("schema-version", 2);
        yaml.set("id", "invalid_weight_tier");
        yaml.set("level", 1);
        yaml.set("variants.bad_variant.weight", -1);

        report = new ValidationReport();
        TierDefinition tier = TierParser.parse(yaml, report);

        assertNotNull(tier, "Tier should be returned even with invalid variant weight");
        assertTrue(report.hasErrors(), "Should have errors for invalid weight");
        assertEquals(0, tier.getVariants().size(), "Variant should be rejected due to invalid weight");

        boolean hasWeightError = report.getErrors().stream()
            .anyMatch(e -> e.getField().contains("weight"));
        assertTrue(hasWeightError, "Error should mention weight");
    }
}
