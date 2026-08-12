package com.arkflame.flameforge.config;

import com.arkflame.flameforge.model.ForgePowerDefinition;
import com.arkflame.flameforge.model.TierDefinition;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TierParserTest {

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
}
