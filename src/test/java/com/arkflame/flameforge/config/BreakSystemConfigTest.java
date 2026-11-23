package com.arkflame.flameforge.config;

import com.arkflame.flameforge.model.FailureVariant;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BreakSystemConfigTest {

    @Test
    void discoversCustomVariantsAndHonoursEnableFlags() {
        ConfigSnapshot snapshot = ConfigSnapshot.builder()
            .putRoot("break-system.enabled", true)
            .putRoot("break-system.failure-variants.break.enabled", false)
            .putRoot("break-system.failure-variants.shatter.enabled", true)
            .putRoot("break-system.failure-variants.shatter.display-name", "<red>Shattered %base_name%")
            .putRoot("break-system.failure-variants.shatter.damage-reduction", 4)
            .putRoot("break-system.failure-variants.shatter.durability-half", false)
            .putRoot("break-system.failure-variants.shatter.message", "<red>Your item shattered.")
            .putRoot("break-system.failure-variants.shatter.lore",
                Collections.singletonList("<dark_red>Cannot be reforged."))
            .build();

        BreakSystemConfig config = BreakSystemConfig.from(snapshot);

        assertTrue(config.isEnabled());
        assertTrue(config.getVariantIds().contains("shatter"));
        assertFalse(config.isVariantEnabled("break"));
        assertTrue(config.isVariantEnabled("shatter"));
        assertEquals(Collections.singletonList("shatter"),
            new java.util.ArrayList<>(config.getEnabledVariantIds()).stream()
                .filter(id -> !"curse".equals(id)).collect(java.util.stream.Collectors.toList()));

        FailureVariant shatter = config.resolve("shatter", null);
        assertEquals(4.0, shatter.getDamageReduction());
        assertFalse(shatter.isDurabilityHalf());
        assertTrue(shatter.isMarkAsFailed());
        assertTrue(shatter.getMessage().contains("shattered"));
    }

    @Test
    void disabledBreakSystemStillResolvesVariants() {
        BreakSystemConfig config = BreakSystemConfig.from(ConfigSnapshot.builder()
            .putRoot("break-system.enabled", false).build());

        assertFalse(config.isEnabled());
        assertEquals(2.0, config.resolve("break", null).getDamageReduction());
    }

    @Test
    void parseChancesKeepsCustomVariantChances() {
        org.bukkit.configuration.file.YamlConfiguration yaml =
            new org.bukkit.configuration.file.YamlConfiguration();
        yaml.set("schema-version", 2);
        yaml.set("id", "tier");
        yaml.set("chances.success", "80.0");
        yaml.set("chances.break", "5.0");
        yaml.set("chances.curse", "5.0");
        yaml.set("chances.shatter", "10.0");
        org.bukkit.configuration.ConfigurationSection nested =
            yaml.getConfigurationSection("chances").createSection("fragment");
        nested.set("percent", "6.0");
        com.arkflame.flameforge.config.ValidationReport report =
            new com.arkflame.flameforge.config.ValidationReport();

        com.arkflame.flameforge.model.TierDefinition tier =
            com.arkflame.flameforge.config.TierParser.parse(yaml, report);

        assertEquals(10.0, tier.getChances().getVariantPercent("shatter").doubleValue());
        assertEquals(6.0, tier.getChances().getVariantPercent("fragment").doubleValue());
        assertEquals(5.0, tier.getChances().getBreakPercent().doubleValue());
    }
}
