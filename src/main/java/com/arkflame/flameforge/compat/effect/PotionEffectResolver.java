package com.arkflame.flameforge.compat.effect;

import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

public final class PotionEffectResolver {
    private static final int MAX_CACHE_ENTRIES = 256;
    private static final Object NOT_FOUND = new Object();

    private final Map<String, Object> cache = new HashMap<>(MAX_CACHE_ENTRIES);
    private int cacheSize = 0;

    private final Map<String, String[]> ALIASES;
    private final Map<String, PotionEffectType> EFFECTS_BY_NAME;

    public PotionEffectResolver() {
        this.ALIASES = buildAliases();
        this.EFFECTS_BY_NAME = buildEffectIndex();
    }

    private Map<String, String[]> buildAliases() {
        Map<String, String[]> aliases = new HashMap<>();
        aliases.put("speed", new String[]{"SPEED", "MOVEMENT_SPEED"});
        aliases.put("slow", new String[]{"SLOW", "MOVEMENT_SLOW"});
        aliases.put("fast_digging", new String[]{"FAST_DIGGING", "DIG_SPEED"});
        aliases.put("slow_digging", new String[]{"SLOW_DIGGING", "DIG_SLOW"});
        aliases.put("increase_damage", new String[]{"INCREASE_DAMAGE", "DAMAGE_BOOST"});
        aliases.put("jump", new String[]{"JUMP", "JUMP_BOOST"});
        aliases.put("confusion", new String[]{"CONFUSION", "CONFUSION"});
        aliases.put("regeneration", new String[]{"REGENERATION", "REGENERATION"});
        aliases.put("resistance", new String[]{"RESISTANCE", "DAMAGE_RESISTANCE"});
        aliases.put("fire_resistance", new String[]{"FIRE_RESISTANCE", "FIRE_RESISTANCE"});
        aliases.put("water_breathing", new String[]{"WATER_BREATHING", "WATER_BREATHING"});
        aliases.put("invisibility", new String[]{"INVISIBILITY", "INVISIBILITY"});
        aliases.put("blindness", new String[]{"BLINDNESS", "BLINDNESS"});
        aliases.put("night_vision", new String[]{"NIGHT_VISION", "NIGHT_VISION"});
        aliases.put("hunger", new String[]{"HUNGER", "HUNGER"});
        aliases.put("weakness", new String[]{"WEAKNESS", "WEAKNESS"});
        aliases.put("poison", new String[]{"POISON", "POISON"});
        aliases.put("wither", new String[]{"WITHER", "WITHER"});
        aliases.put("health_boost", new String[]{"HEALTH_BOOST", "HEALTH_BOOST"});
        aliases.put("absorption", new String[]{"ABSORPTION", "ABSORPTION"});
        aliases.put("saturation", new String[]{"SATURATION", "SATURATION"});
        aliases.put("levitation", new String[]{"LEVITATION", "LEVITATION"});
        aliases.put("saturation", new String[]{"SATURATION", "SATURATION"});
        aliases.put("glowing", new String[]{"GLOWING", "GLOWING"});
        aliases.put("levitation", new String[]{"LEVITATION", "LEVITATION"});
        aliases.put("luck", new String[]{"LUCK", "LUCK"});
        aliases.put("unluck", new String[]{"UNLUCK", "UNLUCK"});
        aliases.put("slow_falling", new String[]{"SLOW_FALLING", "SLOW_FALLING"});
        aliases.put("conduit_power", new String[]{"CONDUIT_POWER", "CONDUIT_POWER"});
        aliases.put("dolphins_grace", new String[]{"DOLPHINS_GRACE", "DOLPHINS_GRACE"});
        aliases.put("bad_omen", new String[]{"BAD_OMEN", "BAD_OMEN"});
        aliases.put("hero_of_the_village", new String[]{"HERO_OF_THE_VILLAGE", "HERO_OF_THE_VILLAGE"});
        aliases.put("darkness", new String[]{"DARKNESS", "DARKNESS"});
        return Collections.unmodifiableMap(aliases);
    }

    private Map<String, PotionEffectType> buildEffectIndex() {
        Map<String, PotionEffectType> index = new HashMap<>();
        if (PotionEffectType.values().length > 0) {
            for (PotionEffectType type : PotionEffectType.values()) {
                if (type != null && type.getName() != null) {
                    index.put(type.getName(), type);
                }
            }
        }
        return index;
    }

    public Optional<PotionEffectType> resolve(List<String> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            return Optional.empty();
        }
        for (String candidate : candidates) {
            if (candidate == null || candidate.isEmpty()) {
                continue;
            }
            PotionEffectType result = resolveSingle(candidate);
            if (result != null) {
                return Optional.of(result);
            }
        }
        return Optional.empty();
    }

    public Optional<PotionEffectType> resolve(String... candidates) {
        if (candidates == null || candidates.length == 0) {
            return Optional.empty();
        }
        List<String> list = new ArrayList<>(candidates.length);
        for (String c : candidates) {
            if (c != null && !c.isEmpty()) {
                list.add(c);
            }
        }
        return resolve(list);
    }

    private PotionEffectType resolveSingle(String key) {
        if (key == null || key.isEmpty()) {
            return null;
        }
        String normalized = normalize(key);
        Object cached = cache.get(normalized);
        if (cached == NOT_FOUND) {
            return null;
        }
        if (cached instanceof PotionEffectType) {
            return (PotionEffectType) cached;
        }

        PotionEffectType effect = lookupEffect(normalized);
        putInCache(normalized, effect != null ? effect : NOT_FOUND);
        return effect;
    }

    private PotionEffectType lookupEffect(String normalized) {
        String[] candidates = ALIASES.get(normalized);
        if (candidates != null) {
            for (String candidate : candidates) {
                PotionEffectType effect = tryParse(candidate);
                if (effect != null) {
                    return effect;
                }
            }
        }
        return tryParse(normalized);
    }

    private PotionEffectType tryParse(String name) {
        try {
            return PotionEffectType.getByName(name.toUpperCase());
        } catch (Exception e) {
            return null;
        }
    }

    private String normalize(String key) {
        return key.toUpperCase().replace("-", "_").replace(" ", "_");
    }

    private void putInCache(String key, Object value) {
        if (cacheSize >= MAX_CACHE_ENTRIES && !cache.containsKey(key)) {
            evictRandomEntry();
        }
        cache.put(key, value);
        if (value != NOT_FOUND) {
            cacheSize = Math.max(cacheSize, cache.size());
        }
    }

    private void evictRandomEntry() {
        for (Map.Entry<String, Object> entry : cache.entrySet()) {
            if (entry.getValue() != NOT_FOUND) {
                cache.remove(entry.getKey());
                break;
            }
        }
    }

    public void clearCache() {
        cache.clear();
        cacheSize = 0;
    }
}
