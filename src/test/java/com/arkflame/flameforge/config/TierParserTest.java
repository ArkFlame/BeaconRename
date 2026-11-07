package com.arkflame.flameforge.config;

import com.arkflame.flameforge.model.BreakPolicy;
import com.arkflame.flameforge.model.ForgePowerDefinition;
import com.arkflame.flameforge.model.TierDefinition;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

class TierParserTest {

    @Test
    void negativeAndEmptyValuesReportErrorsWithoutThrowing() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("schema-version", 2);
        yaml.set("id", "bad_numbers");
        yaml.set("level", 1);
        yaml.set("input.cooldown-seconds", -5);
        yaml.set("requirements.money.enabled", true);
        yaml.set("requirements.money.amount", -10);
        yaml.set("requirements.xp.enabled", true);
        yaml.set("requirements.xp.amount", -1);
        yaml.set("variants.example.weight", 1.0);

        ValidationReport report = new ValidationReport();
        TierDefinition tier = TierParser.parse(yaml, report);

        assertTrue(report.hasErrors());
        assertNotNull(tier);
        assertEquals(0L, tier.getCooldownSeconds());
        assertEquals(0, tier.getRequirements().getMoney().getAmount().signum());
    }

    @Test
    void breakDefaultTextIsInjectedWhenBreakSectionOmitsIt() {
        BreakPolicy.DefaultText catalog = BreakPolicy.DefaultText.of("<red>Shattered %base_name%",
            Arrays.asList("<gray>Custom lore."));

        YamlConfiguration omitted = new YamlConfiguration();
        omitted.set("schema-version", 2);
        omitted.set("id", "break_omitted");
        omitted.set("level", 1);
        omitted.set("variants.example.weight", 1.0);
        BreakPolicy omittedPolicy = TierParser.parse(omitted, new ValidationReport(), catalog).getBreakPolicy();
        assertEquals("<red>Shattered %base_name%", omittedPolicy.getResultDisplayName());
        assertEquals(Arrays.asList("<gray>Custom lore."), omittedPolicy.getResultLore());

        YamlConfiguration sectionOnly = new YamlConfiguration();
        sectionOnly.set("schema-version", 2);
        sectionOnly.set("id", "break_section");
        sectionOnly.set("level", 1);
        sectionOnly.set("variants.example.weight", 1.0);
        sectionOnly.set("break.reset-tier", true);
        BreakPolicy sectionPolicy = TierParser.parse(sectionOnly, new ValidationReport(), catalog).getBreakPolicy();
        assertEquals("<red>Shattered %base_name%", sectionPolicy.getResultDisplayName());
        assertEquals(Arrays.asList("<gray>Custom lore."), sectionPolicy.getResultLore());
    }

    @Test
    void blankBreakDefaultTextFallsBackToEnglish() {
        BreakPolicy.DefaultText blank = BreakPolicy.DefaultText.of("  ", Collections.emptyList());

        assertEquals(BreakPolicy.DefaultText.ENGLISH.getDisplayName(), blank.getDisplayName());
        assertEquals(BreakPolicy.DefaultText.ENGLISH.getLore(), blank.getLore());
    }

    @Test
    void negativeLevelAndEmptyVariantsAreRejectedAsErrors() {
        YamlConfiguration negativeLevel = new YamlConfiguration();
        negativeLevel.set("schema-version", 2);
        negativeLevel.set("id", "negative_level");
        negativeLevel.set("level", -2);
        negativeLevel.set("variants.example.weight", 1.0);
        ValidationReport levelReport = new ValidationReport();
        assertNull(TierParser.parse(negativeLevel, levelReport));
        assertTrue(levelReport.hasErrors());

        YamlConfiguration emptyVariants = new YamlConfiguration();
        emptyVariants.set("schema-version", 2);
        emptyVariants.set("id", "empty_variants");
        emptyVariants.set("level", 1);
        ValidationReport variantReport = new ValidationReport();
        TierParser.parse(emptyVariants, variantReport);
        assertTrue(variantReport.hasErrors());
    }

    @Test
    void validRepresentativeTierParses() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("schema-version", 2);
        yaml.set("id", "representative");
        yaml.set("level", 3);
        yaml.set("enabled", true);
        yaml.set("display.name", "Representative");
        yaml.set("break.result-display-name", "<green>Result %base_name%</green>");
        yaml.set("break.result-lore", Arrays.asList("<gray>Result lore"));
        yaml.set("variants.example.weight", 2.5);

        ValidationReport report = new ValidationReport();
        TierDefinition tier = TierParser.parse(yaml, report);

        assertNotNull(tier);
        assertFalse(report.hasErrors());
        assertTrue(tier.isEnabled());
        assertFalse(tier.getVariants().isEmpty());
        assertTrue(tier.getBreakPolicy().getResultDisplayName().contains("%base_name%"));
        assertFalse(tier.getBreakPolicy().getResultLore().isEmpty());
    }

    @Test
    void invalidTierShapeOrSchemaReportsErrors() {
        YamlConfiguration missingId = new YamlConfiguration();
        missingId.set("schema-version", 2);
        missingId.set("level", 1);
        ValidationReport shapeReport = new ValidationReport();
        assertNull(TierParser.parse(missingId, shapeReport));
        assertTrue(shapeReport.hasErrors());

        YamlConfiguration unsupportedSchema = new YamlConfiguration();
        unsupportedSchema.set("schema-version", 99);
        unsupportedSchema.set("id", "unsupported");
        ValidationReport schemaReport = new ValidationReport();
        assertNull(TierParser.parse(unsupportedSchema, schemaReport));
        assertTrue(schemaReport.hasErrors());
    }

    @Test
    void invalidPowerBoundsRejectPower() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("schema-version", 2);
        yaml.set("id", "power-bounds");
        yaml.set("level", 2);
        yaml.set("variants.example.weight", 1.5);

        Map<String, Object> power = new HashMap<>();
        power.put("id", "bounded-power");
        power.put("type", ForgePowerDefinition.PowerType.values()[0].name());
        power.put("radius", new BigDecimal("16.1"));
        yaml.set("variants.example.powers", Arrays.asList(power));

        ValidationReport report = new ValidationReport();
        TierDefinition tier = TierParser.parse(yaml, report);

        assertNotNull(tier);
        assertTrue(report.hasErrors());
        assertTrue(tier.getVariants().get(0).getPowers().isEmpty());
    }

    @Test
    void parsesInventoryActivationAndBlockPowerTypes() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("schema-version", 2);
        yaml.set("id", "activation-scope");
        yaml.set("level", 1);
        yaml.set("variants.example.weight", 1);
        Map<String, Object> power = new HashMap<>();
        power.put("id", "block-potion");
        power.put("type", ForgePowerDefinition.PowerType.ON_BLOCK_POTION.name());
        power.put("activation-slots", Arrays.asList("INVENTORY"));
        yaml.set("variants.example.powers", Arrays.asList(power));

        ValidationReport report = new ValidationReport();
        TierDefinition tier = TierParser.parse(yaml, report);

        assertFalse(report.hasErrors());
        assertEquals(ForgePowerDefinition.PowerType.ON_BLOCK_POTION,
            tier.getVariants().get(0).getPowers().get(0).getPowerType());
        assertEquals(ForgePowerDefinition.ActivationSlot.INVENTORY,
            tier.getVariants().get(0).getPowers().get(0).getActivationSlots().get(0));
    }
}
