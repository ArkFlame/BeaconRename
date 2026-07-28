package com.arkflame.flameforge.config;

import com.arkflame.flameforge.model.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
    void testDuplicateIdError() throws IOException {
        YamlConfiguration yaml1 = new YamlConfiguration();
        yaml1.set("schema-version", 1);
        yaml1.set("id", "duplicate_id");
        yaml1.set("priority", 1);
        yaml1.set("enabled", true);
        yaml1.set("cost.mode", "XP_ONLY");
        yaml1.set("cost.xp", 10);

        YamlConfiguration yaml2 = new YamlConfiguration();
        yaml2.set("schema-version", 1);
        yaml2.set("id", "duplicate_id");
        yaml2.set("priority", 2);
        yaml2.set("enabled", true);
        yaml2.set("cost.mode", "XP_ONLY");
        yaml2.set("cost.xp", 20);

        File tiersDir = tempDir.toFile();
        tiersDir.mkdirs();

        File file1 = new File(tiersDir, "tier1.yml");
        yaml1.save(file1);
        File file2 = new File(tiersDir, "tier2.yml");
        yaml2.save(file2);

        ValidationReport loadReport = new ValidationReport();
        Set<String> seenIds = new java.util.HashSet<>();
        loadReport.merge(loadTierFile(file1, seenIds));
        loadReport.merge(loadTierFile(file2, seenIds));

        assertTrue(loadReport.hasErrors(), "Should report duplicate ID error");
    }

    @Test
    void testInvalidWeightError() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("schema-version", 1);
        yaml.set("id", "test_tier");
        yaml.set("priority", 1);
        yaml.set("enabled", true);
        yaml.set("cost.mode", "XP_ONLY");
        yaml.set("outcomes.invalid_weight.type", "BREAK");
        yaml.set("outcomes.invalid_weight.weight", -1.0);

        TierParser.TierParseResult result = TierParser.parse(yaml, report);
        assertTrue(result.isSuccess());

        List<ValidationIssue> errors = report.getErrors();
        boolean hasWeightError = errors.stream()
            .anyMatch(e -> e.getMessage() != null && e.getMessage().contains("Weight must be positive"));
        assertTrue(hasWeightError, "Should report invalid weight error");
    }

    @Test
    void testEveryBundledTierParses() {
        for (int i = 1; i <= 7; i++) {
            String resourcePath = "tiers/tier" + i + ".yml";
            java.io.InputStream stream = getClass().getClassLoader().getResourceAsStream(resourcePath);

            assertNotNull(stream, "Bundled tier " + i + " should exist as resource: " + resourcePath);

            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(new java.io.InputStreamReader(stream));
            ValidationReport tierReport = new ValidationReport();
            TierParser.TierParseResult result = TierParser.parse(yaml, tierReport);

            assertTrue(result.isSuccess(), "Tier " + i + " should parse successfully");
            assertFalse(tierReport.hasErrors(), "Tier " + i + " should have no errors");
            assertNotNull(result.getTier(), "Tier " + i + " should have a TierDefinition");
            assertNotNull(result.getExtra(), "Tier " + i + " should have TierExtra");
        }
    }

    @Test
    void testBreakWeightsCostsPrioritiesExact() {
        BigDecimal[] expectedWeights = {
            new BigDecimal("1.0"),
            new BigDecimal("2.0"),
            new BigDecimal("3.0"),
            new BigDecimal("4.0"),
            new BigDecimal("5.0"),
            new BigDecimal("6.0"),
            new BigDecimal("7.0")
        };
        int[] expectedPriorities = {1, 2, 3, 4, 5, 6, 7};
        BigDecimal[] expectedXpCosts = {
            BigDecimal.ZERO,
            new BigDecimal("10"),
            new BigDecimal("25"),
            new BigDecimal("50"),
            new BigDecimal("100"),
            new BigDecimal("200"),
            new BigDecimal("500")
        };

        for (int i = 1; i <= 7; i++) {
            String resourcePath = "tiers/tier" + i + ".yml";
            java.io.InputStream stream = getClass().getClassLoader().getResourceAsStream(resourcePath);
            assertNotNull(stream, "Resource should exist: " + resourcePath);

            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(new java.io.InputStreamReader(stream));
            ValidationReport tierReport = new ValidationReport();
            TierParser.TierParseResult result = TierParser.parse(yaml, tierReport);
            assertTrue(result.isSuccess());

            TierDefinition tier = result.getTier();
            assertEquals(expectedPriorities[i - 1], tier.getTierLevel(), "Tier " + i + " priority should match");
            assertEquals(expectedWeights[i - 1], tier.getOutcomes().get(0).getWeight(), "Tier " + i + " weight should match");

            BigDecimal xpCost = tier.getCost().getXpCost();
            if (xpCost != null) {
                assertEquals(expectedXpCosts[i - 1], xpCost, "Tier " + i + " XP cost should match");
            }
        }
    }

    @Test
    void testSchemaVersionError() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("schema-version", 999);
        yaml.set("id", "test");
        yaml.set("priority", 1);

        TierParser.TierParseResult result = TierParser.parse(yaml, report);

        assertFalse(result.isSuccess());
        assertTrue(report.hasErrors());
    }

    @Test
    void testMissingIdError() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("schema-version", 1);
        yaml.set("priority", 1);

        TierParser.TierParseResult result = TierParser.parse(yaml, report);

        assertFalse(result.isSuccess());
        assertTrue(report.hasErrors());
    }

    @Test
    void testValidTierWithAllFields() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("schema-version", 1);
        yaml.set("id", "complete_tier");
        yaml.set("priority", 10);
        yaml.set("enabled", true);
        yaml.set("permission", "flameforge.complete");
        yaml.set("display.name", "&b&lComplete Tier");
        yaml.set("display.lore", java.util.Arrays.asList("&7Line 1", "&7Line 2"));
        yaml.set("display.material", "NETHER_STAR");
        yaml.set("display.custom-model-data", 123);
        yaml.set("cost.mode", "XP_AND_MONEY");
        yaml.set("cost.xp", 100.5);
        yaml.set("cost.money", 250.75);
        yaml.set("cooldown-seconds", 300L);
        yaml.set("pity.enabled", true);
        yaml.set("pity.threshold", 10);
        yaml.set("pity.bonus-weight", 2.5);
        yaml.set("animation.success-duration", 50);
        yaml.set("animation.fail-duration", 25);
        yaml.set("outcomes.test_outcome.type", "BREAK");
        yaml.set("outcomes.test_outcome.weight", 3.5);

        TierParser.TierParseResult result = TierParser.parse(yaml, report);

        assertTrue(result.isSuccess());
        assertFalse(report.hasErrors());

        TierDefinition tier = result.getTier();
        assertEquals("complete_tier", tier.getId());
        assertEquals(10, tier.getTierLevel());
        assertEquals(BigDecimal.valueOf(100.5), tier.getCost().getXpCost());
        assertEquals(BigDecimal.valueOf(250.75), tier.getCost().getMoneyCost());
        assertEquals(CostMode.XP_AND_MONEY, tier.getCost().getMode());

        TierParser.TierExtra extra = result.getExtra();
        assertTrue(extra.isEnabled());
        assertEquals("flameforge.complete", extra.getPermission());
        assertEquals(300L, extra.getCooldownSeconds());
        assertTrue(extra.getPity().isEnabled());
        assertEquals(10, extra.getPity().getThreshold());
        assertEquals(BigDecimal.valueOf(2.5), extra.getPity().getBonusWeight());

        TierParser.TierDisplay display = extra.getDisplay();
        assertEquals("&b&lComplete Tier", display.getName());
        assertEquals(2, display.getLore().size());
        assertEquals("NETHER_STAR", display.getMaterial());
        assertEquals(123, display.getCustomModelData());
    }

    @Test
    void testDefaultValues() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("schema-version", 1);
        yaml.set("id", "minimal_tier");
        yaml.set("priority", 1);

        TierParser.TierParseResult result = TierParser.parse(yaml, report);

        assertTrue(result.isSuccess());

        TierDefinition tier = result.getTier();
        assertEquals(1, tier.getTierLevel());
        assertEquals(BigDecimal.ZERO, tier.getCost().getXpCost());
        assertEquals(CostMode.XP_ONLY, tier.getCost().getMode());
        assertEquals(40, tier.getSuccessAnimationDuration());
        assertEquals(20, tier.getFailAnimationDuration());

        TierParser.TierExtra extra = result.getExtra();
        assertTrue(extra.isEnabled());
        assertEquals("", extra.getPermission());
        assertEquals(0L, extra.getCooldownSeconds());
        assertFalse(extra.getPity().isEnabled());
    }

    private ValidationReport loadTierFile(File file, Set<String> seenIds) {
        ValidationReport fileReport = new ValidationReport();
        try {
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
            TierParser.TierParseResult result = TierParser.parse(yaml, fileReport);

            if (!result.isSuccess()) {
                return fileReport;
            }

            TierDefinition tier = result.getTier();
            String id = tier.getId();

            if (seenIds.contains(id)) {
                fileReport.addError("", "id", "Duplicate tier id: " + id + " in file " + file.getName());
                return fileReport;
            }

            seenIds.add(id);
        } catch (Exception e) {
            fileReport.addError("", file.getName(), "Failed to parse tier file: " + e.getMessage());
        }
        return fileReport;
    }
}
