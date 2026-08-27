package com.arkflame.flameforge.compat.effect.particle;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class ParticleCatalog {
    private final Map<String, String> aliases;
    private final Map<String, List<String>> families;

    public ParticleCatalog() {
        Map<String, String> aliasMap = new LinkedHashMap<String, String>();
        alias(aliasMap, "explode", "EXPLOSION");
        alias(aliasMap, "poof", "POOF");
        alias(aliasMap, "explosion", "EXPLOSION");
        alias(aliasMap, "large_explosion", "EXPLOSION");
        alias(aliasMap, "huge_explosion", "EXPLOSION_EMITTER");
        alias(aliasMap, "firework", "FIREWORK");
        alias(aliasMap, "firework_spark", "FIREWORK");
        alias(aliasMap, "crit", "CRIT");
        alias(aliasMap, "magic_crit", "CRIT");
        alias(aliasMap, "smoke_normal", "SMOKE");
        alias(aliasMap, "largesmoke", "LARGE_SMOKE");
        alias(aliasMap, "smoke_large", "LARGE_SMOKE");
        alias(aliasMap, "spell", "EFFECT");
        alias(aliasMap, "spell_instant", "INSTANT_EFFECT");
        alias(aliasMap, "effect", "EFFECT");
        alias(aliasMap, "instant_effect", "INSTANT_EFFECT");
        alias(aliasMap, "witch", "WITCH");
        alias(aliasMap, "enchantment_table", "ENCHANT");
        alias(aliasMap, "note_block", "NOTE");
        alias(aliasMap, "ender_portal", "PORTAL");
        alias(aliasMap, "splash_pool", "SPLASH");
        alias(aliasMap, "particle_splash", "SPLASH");
        alias(aliasMap, "eye_of_ender", "END_ROD");
        alias(aliasMap, "mobspawner", "LARGE_SMOKE");
        alias(aliasMap, "item_crack", "ITEM");
        alias(aliasMap, "item_break", "ITEM");
        alias(aliasMap, "block_crack", "BLOCK");
        alias(aliasMap, "block_dust", "BLOCK");
        alias(aliasMap, "snow_shovel", "SNOWBALL");
        alias(aliasMap, "slime", "SLIME");
        alias(aliasMap, "item_slime", "SLIME");
        alias(aliasMap, "angry_villager", "ANGRY_VILLAGER");
        alias(aliasMap, "happy_villager", "HAPPY_VILLAGER");
        alias(aliasMap, "drip", "DRIP_WATER");
        alias(aliasMap, "redstone", "DUST");
        alias(aliasMap, "electric", "ELECTRIC_SPARK");
        alias(aliasMap, "electric_spark", "ELECTRIC_SPARK");
        alias(aliasMap, "bubble_column", "BUBBLE");
        alias(aliasMap, "underwater", "BUBBLE");
        this.aliases = Collections.unmodifiableMap(aliasMap);

        Map<String, List<String>> familyMap = new LinkedHashMap<String, List<String>>();
        family(familyMap, "EXPLOSION", "EXPLOSION", "EXPLOSION_NORMAL", "EXPLOSION_LARGE", "EXPLOSION_EMITTER");
        family(familyMap, "POOF", "POOF", "EXPLOSION_NORMAL");
        family(familyMap, "FIREWORK", "FIREWORK", "FIREWORKS_SPARK");
        family(familyMap, "ENCHANTED_HIT", "ENCHANTED_HIT", "CRIT", "MAGIC_CRIT");
        family(familyMap, "SMOKE", "SMOKE", "SMOKE_NORMAL", "LARGE_SMOKE", "SMOKE_LARGE");
        family(familyMap, "EFFECT", "EFFECT", "SPELL");
        family(familyMap, "INSTANT_EFFECT", "INSTANT_EFFECT", "SPELL_INSTANT");
        family(familyMap, "WITCH", "WITCH", "WITCH_MAGIC");
        family(familyMap, "ENCHANT", "ENCHANT", "ENCHANTMENT_TABLE");
        family(familyMap, "DUST", "DUST", "REDSTONE");
        family(familyMap, "BLOCK", "BLOCK", "BLOCK_CRACK", "BLOCK_DUST", "TILE_BREAK", "TILE_DUST");
        family(familyMap, "ITEM", "ITEM", "ITEM_CRACK", "ITEM_BREAK");
        family(familyMap, "VILLAGER", "VILLAGER_ANGRY", "ANGRY_VILLAGER", "VILLAGER_HAPPY", "HAPPY_VILLAGER");
        family(familyMap, "DRIP", "DRIP_WATER", "DRIP_LAVA");
        family(familyMap, "SNOWBALL", "SNOWBALL", "SNOWBALL_BREAK", "SNOW_SHOVEL");
        family(familyMap, "SLIME", "SLIME", "ITEM_SLIME");
        family(familyMap, "TOTEM", "TOTEM", "TOTEM_OF_UNDYING");
        family(familyMap, "BUBBLE", "BUBBLE", "BUBBLE_POP", "BUBBLE_COLUMN_UP");
        family(familyMap, "SPLASH", "SPLASH", "WATER_SPLASH");
        family(familyMap, "ELECTRIC_SPARK", "ELECTRIC_SPARK", "ELECTRIC");
        this.families = Collections.unmodifiableMap(familyMap);
    }

    private static void alias(Map<String, String> map, String key, String value) {
        map.put(key.toLowerCase(Locale.ROOT), value.toUpperCase(Locale.ROOT));
    }

    private static void family(Map<String, List<String>> map, String key, String... values) {
        map.put(key, Collections.unmodifiableList(Arrays.asList(values)));
    }

    public List<String> resolve(String raw) {
        if (raw == null || raw.trim().isEmpty()) return Collections.emptyList();
        String trimmed = raw.trim();
        String normalized = trimmed.toUpperCase(Locale.ROOT);
        LinkedHashSet<String> seen = new LinkedHashSet<String>();
        List<String> result = new ArrayList<String>();
        String alias = aliases.get(trimmed.toLowerCase(Locale.ROOT));
        if (alias != null) {
            addCandidate(result, seen, trimmed);
            if (!trimmed.equals(normalized)
                && ("BLOCK_CRACK".equals(normalized) || "ITEM_CRACK".equals(normalized))) {
                seen.remove(normalized);
            }
        }
        if (alias != null) addCandidate(result, seen, alias);
        List<String> family = families.get(normalized);
        if (family == null && alias != null) family = families.get(alias);
        if (family == null) {
            for (List<String> values : families.values()) {
                if (values.contains(normalized) || (alias != null && values.contains(alias))) {
                    family = values;
                    break;
                }
            }
        }
        if (alias == null) addCandidate(result, seen, normalized);
        if (family != null) {
            for (String candidate : family) addCandidate(result, seen, candidate);
        }
        return Collections.unmodifiableList(new ArrayList<String>(result));
    }

    private static void addCandidate(List<String> result, LinkedHashSet<String> seen, String candidate) {
        if (seen.add(candidate.toUpperCase(Locale.ROOT))) result.add(candidate);
    }

    public Map<String, String> getAliases() { return aliases; }
    public Map<String, List<String>> getFamilies() { return families; }
}
