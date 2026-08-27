package com.arkflame.flameforge.config;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TierLoreStyleContractTest {

    private static final String LORE_BULLET = "<dark_gray>\u25cf ";
    private static final String BANNED_LINE = "Original material retained";
    private static final List<String> FRACTURE_LORE = Arrays.asList(
        "<dark_gray>Forge fracture",
        "<red>\u25cf Previous forge powers were stripped.",
        "<gray>Reforge the item."
    );
    private static final List<String> CURSE_LORE = Arrays.asList(
        "<gray>Forge curse",
        "<dark_gray>\u25cf <red>Previous forge powers were stripped.",
        "<dark_gray>\u25cf <dark_red>This item cannot be reforged."
    );

    private static final List<String> BUNDLED_TIER_FILES = buildBundledTierFiles();

    @Test
    void allBundledTierFilesAreChecked() {
        List<String> missing = new ArrayList<String>();
        for (String file : BUNDLED_TIER_FILES) {
            if (getClass().getClassLoader().getResourceAsStream("tiers/" + file) == null) {
                missing.add(file);
            }
        }
        assertTrue(missing.isEmpty(), "Bundled tier files not found: " + missing);
        assertEquals(35, BUNDLED_TIER_FILES.size());
    }

    @Test
    void noLoreLineContainsBannedOriginalMaterialText() {
        for (String file : BUNDLED_TIER_FILES) {
            for (String line : allVariantLoreLines(file)) {
                assertFalse(line.contains(BANNED_LINE),
                    "Banned line '" + BANNED_LINE + "' present in " + file + ": " + line);
            }
        }
    }

    @Test
    void everyVariantHasDescriptorAtLoreIndexZero() {
        for (String file : BUNDLED_TIER_FILES) {
            for (String variantId : variantIds(file)) {
                List<String> lore = variantLore(file, variantId);
                assertFalse(lore.isEmpty(), "Variant '" + variantId + "' in " + file + " has no descriptor lore line");
                assertFalse(lore.get(0).trim().isEmpty(),
                    "Variant '" + variantId + "' in " + file + " has blank descriptor lore line");
            }
        }
    }

    @Test
    void everyNonemptyLoreLinePastDescriptorStartsWithBullet() {
        for (String file : BUNDLED_TIER_FILES) {
            for (String variantId : variantIds(file)) {
                List<String> lore = variantLore(file, variantId);
                for (int index = 1; index < lore.size(); index++) {
                    String line = lore.get(index).trim();
                    if (line.isEmpty()) {
                        continue;
                    }
                    assertTrue(line.startsWith(LORE_BULLET),
                        "Variant '" + variantId + "' in " + file + " lore line " + index
                            + " does not start with '" + LORE_BULLET + "': " + line);
                }
            }
        }
    }

    @Test
    void noExplicitEmptyOrWhitespaceOnlyLoreLineExists() {
        for (String file : BUNDLED_TIER_FILES) {
            for (String variantId : variantIds(file)) {
                List<String> lore = variantLore(file, variantId);
                for (int index = 1; index < lore.size(); index++) {
                    assertFalse(lore.get(index).trim().isEmpty(),
                        "Variant '" + variantId + "' in " + file + " has explicit empty lore line at index " + index);
                }
            }
        }
    }

    @Test
    void categoryVariantSetsAndWeightTotalsAreExact() {
        Map<String, List<String>> expected = expectedCategoryVariants();
        Map<String, List<String>> expectedWeights = expectedCategoryWeights();
        Map<String, BigDecimal> totals = new HashMap<String, BigDecimal>();
        totals.put("weapon", new BigDecimal("100.0"));
        totals.put("armor", new BigDecimal("100.0"));
        totals.put("shield", new BigDecimal("100.0"));
        totals.put("amulet", new BigDecimal("100.0"));

        for (Map.Entry<String, List<String>> entry : expected.entrySet()) {
            String file = entry.getKey();
            List<String> actual = variantIds(file);
            assertEquals(new HashSet<String>(entry.getValue()), new HashSet<String>(actual), file);
            assertEquals(entry.getValue().size(), actual.size(), file);

            BigDecimal total = BigDecimal.ZERO;
            ConfigurationSection variants = load(file).getConfigurationSection("variants");
            for (int index = 0; index < actual.size(); index++) {
                String variantId = actual.get(index);
                BigDecimal weight = new BigDecimal(variants.getString(variantId + ".weight"));
                assertEquals(new BigDecimal(expectedWeights.get(file).get(index)), weight,
                    file + " " + variantId + " weight");
                total = total.add(weight);
            }
            String category = file.substring(0, file.indexOf('_'));
            assertEquals(totals.get(category), total, "Weight total for " + file);
        }
    }

    @Test
    void compatibilityTierVariantSetsRemainUnchanged() {
        Map<String, List<String>> expected = new HashMap<String, List<String>>();
        expected.put("tier1.yml", Arrays.asList("tempered", "venomous", "swift"));
        expected.put("tier2.yml", Arrays.asList("reinforced", "scorching", "restorative"));
        expected.put("tier3.yml", Arrays.asList("vampiric", "guardian", "hasty"));
        expected.put("tier4.yml", Arrays.asList("withered", "berserker", "leaping"));
        expected.put("tier5.yml", Arrays.asList("infernal", "aegis", "vital"));
        expected.put("tier6.yml", Arrays.asList("plaguebringer", "executioner", "restoration"));
        expected.put("tier7.yml", Arrays.asList("voidbound", "annihilator", "eternal"));
        for (Map.Entry<String, List<String>> entry : expected.entrySet()) {
            assertEquals(new HashSet<String>(entry.getValue()), new HashSet<String>(variantIds(entry.getKey())), entry.getKey());
        }
    }

    @Test
    void everyTierUsesCanonicalFractureAndCurseContracts() {
        for (String file : BUNDLED_TIER_FILES) {
            YamlConfiguration yaml = load(file);
            assertEquals(FRACTURE_LORE, yaml.getStringList("break.result-lore"), file + " fracture lore");
            assertEquals("<gradient:#7f1d1d:#111827><bold>Cursed %base_name%</bold></gradient>",
                yaml.getString("curse.display-name"), file + " curse name");
            assertEquals(CURSE_LORE, yaml.getStringList("curse.lore"), file + " curse lore");
            assertEquals(Arrays.asList("VANISHING_CURSE", "CURSE_OF_VANISHING"),
                yaml.getStringList("curse.enchantment-candidates"), file + " curse aliases");
        }
    }

    @Test
    void variantsHaveExactLevelsScopesAndPlayerFacingLore() {
        for (String file : BUNDLED_TIER_FILES) {
            YamlConfiguration yaml = load(file);
            String category = file.startsWith("tier") ? "Any" : titleCase(file.substring(0, file.indexOf('_')));
            int level = yaml.getInt("level");
            String roman = roman(level);
            String descriptor = "<gray>" + category + " Tier " + roman + " forged variant";
            ConfigurationSection variants = yaml.getConfigurationSection("variants");
            for (String variantId : variants.getKeys(false)) {
                List<String> lore = yaml.getStringList("variants." + variantId + ".lore");
                assertEquals(descriptor, lore.get(0), file + " " + variantId + " descriptor");
                for (int index = 1; index < lore.size(); index++) {
                    String line = lore.get(index);
                    assertTrue(line.startsWith(LORE_BULLET), file + " " + variantId + " bullet");
                    assertTrue(line.matches(".*<[a-z_]+>.*"), file + " " + variantId + " color");
                    assertTrue(line.endsWith("."), file + " " + variantId + " period");
                    String lower = line.toLowerCase();
                    assertFalse(lower.contains("duration") || lower.contains("ticks")
                        || lower.contains("cooldown: 0") || lower.contains("0 seconds"),
                        file + " " + variantId + " fake timing");
                }
                ConfigurationSection powers = variants.getConfigurationSection(variantId + ".powers");
                if (powers != null) {
                    for (String powerId : powers.getKeys(false)) {
                        String type = powers.getString(powerId + ".type", "");
                        if (type.startsWith("PASSIVE_")) {
                            String text = String.join(" ", lore).toLowerCase();
                            assertTrue(text.contains("inventory") || text.contains("equipped")
                                || text.contains("held") || text.contains("offhand"),
                                file + " " + variantId + " passive scope");
                        }
                    }
                }
            }
        }
    }

    @Test
    void minersAndBleedHeartAreAbsent() {
        for (String file : BUNDLED_TIER_FILES) {
            YamlConfiguration yaml = load(file);
            assertFalse(yaml.saveToString().contains("miners_charm"), file);
            ConfigurationSection variants = yaml.getConfigurationSection("variants");
            for (String variantId : variants.getKeys(false)) {
                ConfigurationSection powers = variants.getConfigurationSection(variantId + ".powers");
                if (powers == null) {
                    continue;
                }
                for (String powerId : powers.getKeys(false)) {
                    ConfigurationSection power = powers.getConfigurationSection(powerId);
                    if (power != null && "ON_HIT_BLEED".equals(power.getString("type"))) {
                        assertFalse(power.getStringList("particle-candidates").contains("HEART"),
                            file + " " + variantId + " bleed particle");
                    }
                }
            }
        }
    }

    private List<String> allVariantLoreLines(String file) {
        List<String> lines = new ArrayList<String>();
        for (String variantId : variantIds(file)) {
            lines.addAll(variantLore(file, variantId));
        }
        return lines;
    }

    private List<String> variantIds(String file) {
        ConfigurationSection variants = load(file).getConfigurationSection("variants");
        assertNotNull(variants, "No variants section in " + file);
        return new ArrayList<String>(variants.getKeys(false));
    }

    private List<String> variantLore(String file, String variantId) {
        YamlConfiguration yaml = load(file);
        List<String> lore = yaml.getStringList("variants." + variantId + ".lore");
        assertNotNull(lore, "Missing lore for variant '" + variantId + "' in " + file);
        return lore;
    }

    private YamlConfiguration load(String file) {
        InputStream stream = getClass().getClassLoader().getResourceAsStream("tiers/" + file);
        assertNotNull(stream, "Bundled tier file missing: " + file);
        return YamlConfiguration.loadConfiguration(new InputStreamReader(stream, StandardCharsets.UTF_8));
    }

    private static List<String> buildBundledTierFiles() {
        List<String> files = new ArrayList<String>();
        for (int level = 1; level <= 7; level++) {
            files.add("tier" + level + ".yml");
        }
        for (String category : Arrays.asList("weapon", "armor", "shield", "amulet")) {
            for (int level = 1; level <= 7; level++) {
                files.add(category + "_tier" + level + ".yml");
            }
        }
        return files;
    }

    private static Map<String, List<String>> expectedCategoryVariants() {
        Map<String, List<String>> expected = new HashMap<String, List<String>>();
        expected.put("weapon_tier1.yml", Arrays.asList("bloodletter", "venomous", "swift", "draining", "repulsing"));
        expected.put("weapon_tier2.yml", Arrays.asList("vampiric", "scorching", "detonating", "frostbite", "breaker"));
        expected.put("weapon_tier3.yml", Arrays.asList("plaguebringer", "stormbound", "withered", "siphoning", "emberlash"));
        expected.put("weapon_tier4.yml", Arrays.asList("electrified", "hemorrhaging", "executioner", "crippling", "vampiric_ii"));
        expected.put("weapon_tier5.yml", Arrays.asList("pestilent", "thunderlord", "infernal", "executioner_ii", "witherbrand"));
        expected.put("weapon_tier6.yml", Arrays.asList("annihilator", "reaper", "demolisher", "inferno", "plaguebound"));
        expected.put("weapon_tier7.yml", Arrays.asList("tempest", "plague_lord", "bloodstorm", "cataclysm", "soulreaver"));
        expected.put("armor_tier1.yml", Arrays.asList("fleet", "guarded", "mending", "featherstep"));
        expected.put("armor_tier2.yml", Arrays.asList("feathered", "curative", "warded", "antivenom"));
        expected.put("armor_tier3.yml", Arrays.asList("antidotal", "swiftguard", "reinforced", "mending_ii"));
        expected.put("armor_tier4.yml", Arrays.asList("arcane_ward", "guardian", "fleetward", "venomward"));
        expected.put("armor_tier5.yml", Arrays.asList("vital", "featherfall_ii", "spellguard", "resolute"));
        expected.put("armor_tier6.yml", Arrays.asList("purified", "juggernaut", "seraph_step", "lifeguard"));
        expected.put("armor_tier7.yml", Arrays.asList("seraphic", "immortal_guard", "sanctified", "phoenix_ward"));
        expected.put("shield_tier1.yml", Arrays.asList("steadfast", "mending_guard", "slowing_guard"));
        expected.put("shield_tier2.yml", Arrays.asList("repulsor", "mending_guard_ii", "hindering_guard"));
        expected.put("shield_tier3.yml", Arrays.asList("hindering", "venom_guard_lesser", "restoring_guard"));
        expected.put("shield_tier4.yml", Arrays.asList("restorative_guard", "repulsor_ii", "venom_guard_ii"));
        expected.put("shield_tier5.yml", Arrays.asList("venom_guard", "mercy_guard", "shoving_guard"));
        expected.put("shield_tier6.yml", Arrays.asList("stormwall", "blight_guard", "warding_guard"));
        expected.put("shield_tier7.yml", Arrays.asList("aegis", "doomguard", "savior"));
        expected.put("amulet_tier1.yml", Arrays.asList("curative", "fleet_charm", "iron_token", "medic_token"));
        expected.put("amulet_tier2.yml", Arrays.asList("ironheart", "sprinters_token", "menders_token"));
        expected.put("amulet_tier3.yml", Arrays.asList("curative_ii", "fleet_ii", "restorative_charm", "wardstone", "leapers_token"));
        expected.put("amulet_tier4.yml", Arrays.asList("windborne", "scholars_charm", "leaping_charm", "vital_token", "aegis_token"));
        expected.put("amulet_tier5.yml", Arrays.asList("guardian_charm", "vital_charm", "quickstep_relic", "restoration_relic"));
        expected.put("amulet_tier6.yml", Arrays.asList("swiftheart", "unyielding_charm", "ascendant_relic", "vital_ward"));
        expected.put("amulet_tier7.yml", Arrays.asList("eternal_charm", "paragon_charm", "celestial_relic", "miracle_relic"));
        return expected;
    }

    private static Map<String, List<String>> expectedCategoryWeights() {
        Map<String, List<String>> expected = new HashMap<String, List<String>>();
        for (String file : expectedCategoryVariants().keySet()) {
            String category = file.substring(0, file.indexOf('_'));
            int count = expectedCategoryVariants().get(file).size();
            List<String> weights = new ArrayList<String>();
            if ("weapon".equals(category)) {
                for (int index = 0; index < count; index++) {
                    weights.add("20.0");
                }
            } else if ("armor".equals(category)) {
                for (int index = 0; index < count; index++) {
                    weights.add("25.0");
                }
            } else if ("shield".equals(category)) {
                weights.add("34.0");
                weights.add("33.0");
                weights.add("33.0");
            } else {
                if (count == 3) {
                    weights.add("34.0");
                    weights.add("33.0");
                    weights.add("33.0");
                } else if (count == 5) {
                    for (int index = 0; index < count; index++) {
                        weights.add("20.0");
                    }
                } else {
                    for (int index = 0; index < count; index++) {
                        weights.add("25.0");
                    }
                }
            }
            expected.put(file, weights);
        }
        return expected;
    }

    private static String titleCase(String value) {
        return Character.toUpperCase(value.charAt(0)) + value.substring(1);
    }

    private static String roman(int level) {
        return Arrays.asList("", "I", "II", "III", "IV", "V", "VI", "VII").get(level);
    }
}
