package com.arkflame.flameforge.item;

import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class EnchantmentResolver {
    private static final int MAX_CACHE_ENTRIES = 256;
    private static final Map<String, String> LEGACY_ALIASES = new ConcurrentHashMap<>();
    private static final Map<String, Object> NAME_CACHE = new ConcurrentHashMap<>(MAX_CACHE_ENTRIES);
    private static final Object NOT_FOUND = new Object();

    static {
        LEGACY_ALIASES.put("DURABILITY", "DURABILITY");
        LEGACY_ALIASES.put("UNBREAKING", "DURABILITY");
        LEGACY_ALIASES.put("PROTECTION", "PROTECTION_ENVIRONMENTAL");
        LEGACY_ALIASES.put("FIRE_PROTECTION", "PROTECTION_FIRE");
        LEGACY_ALIASES.put("FEATHER_FALLING", "PROTECTION_FALL");
        LEGACY_ALIASES.put("BLAST_PROTECTION", "PROTECTION_EXPLOSIONS");
        LEGACY_ALIASES.put("PROJECTILE_PROTECTION", "PROTECTION_PROJECTILE");
        LEGACY_ALIASES.put("THORNS", "THORNS");
        LEGACY_ALIASES.put("RESPIRATION", "OXYGEN");
        LEGACY_ALIASES.put("AQUA_AFFINITY", "WATER_WORKER");
        LEGACY_ALIASES.put("INFINITY", "ARROW_INFINITE");
        LEGACY_ALIASES.put("POWER", "ARROW_DAMAGE");
        LEGACY_ALIASES.put("PUNCH", "ARROW_KNOCKBACK");
        LEGACY_ALIASES.put("FLAME", "ARROW_FIRE");
        LEGACY_ALIASES.put("SHARPNESS", "DAMAGE_ALL");
        LEGACY_ALIASES.put("SMITE", "DAMAGE_UNDEAD");
        LEGACY_ALIASES.put("BANE_OF_ARTHROPODS", "DAMAGE_ARTHROPODS");
        LEGACY_ALIASES.put("KNOCKBACK", "KNOCKBACK");
        LEGACY_ALIASES.put("FIRE_ASPECT", "FIRE_ASPECT");
        LEGACY_ALIASES.put("LOOTING", "LOOT_BONUS_MOBS");
        LEGACY_ALIASES.put("EFFICIENCY", "DIG_SPEED");
        LEGACY_ALIASES.put("SILK_TOUCH", "SILK_TOUCH");
        LEGACY_ALIASES.put("FORTUNE", "LOOT_BONUS_BLOCKS");
        LEGACY_ALIASES.put("LUCK", "LUCK");
        LEGACY_ALIASES.put("LUCK_OF_THE_SEA", "LUCK");
        LEGACY_ALIASES.put("LURE", "LURE");
        LEGACY_ALIASES.put("VANISHING_CURSE", "VANISHING_CURSE");
        LEGACY_ALIASES.put("CURSE_OF_VANISHING", "VANISHING_CURSE");
        LEGACY_ALIASES.put("BINDING_CURSE", "BINDING_CURSE");
        LEGACY_ALIASES.put("CURSE_OF_BINDING", "BINDING_CURSE");
    }

    public EnchantmentResolver() {
    }

    public Enchantment resolve(String... candidates) {
        if (candidates == null || candidates.length == 0) {
            return null;
        }
        for (String candidate : candidates) {
            Enchantment result = resolveSingle(candidate);
            if (result != null) {
                return result;
            }
        }
        return null;
    }

    public Optional<Enchantment> resolve(final String key) {
        if (key == null || key.isEmpty()) {
            return Optional.empty();
        }
        final String normalized = normalize(key);
        Object cached = NAME_CACHE.get(normalized);
        if (cached == NOT_FOUND) {
            return Optional.empty();
        }
        if (cached instanceof Enchantment) {
            return Optional.of((Enchantment) cached);
        }
        Enchantment enchant = resolveEnchantment(normalized);
        if (enchant != null) {
            putInCache(normalized, enchant);
            return Optional.of(enchant);
        }
        putInCache(normalized, NOT_FOUND);
        return Optional.empty();
    }

    private void putInCache(String key, Object value) {
        if (NAME_CACHE.size() >= MAX_CACHE_ENTRIES && !NAME_CACHE.containsKey(key)) {
            evictOneEntry();
        }
        NAME_CACHE.put(key, value);
    }

    private void evictOneEntry() {
        for (Map.Entry<String, Object> entry : NAME_CACHE.entrySet()) {
            if (entry.getValue() != NOT_FOUND) {
                NAME_CACHE.remove(entry.getKey());
                break;
            }
        }
    }

    private Enchantment resolveSingle(String key) {
        if (key == null || key.isEmpty()) {
            return null;
        }
        final String normalized = normalize(key);
        Object cached = NAME_CACHE.get(normalized);
        if (cached == NOT_FOUND) {
            return null;
        }
        if (cached instanceof Enchantment) {
            return (Enchantment) cached;
        }
        Enchantment enchant = resolveEnchantment(normalized);
        if (enchant != null) {
            putInCache(normalized, enchant);
        } else {
            putInCache(normalized, NOT_FOUND);
        }
        return enchant;
    }

    private Enchantment resolveEnchantment(final String key) {
        String aliased = LEGACY_ALIASES.get(key);
        if (aliased != null) {
            try {
                return Enchantment.getByName(aliased);
            } catch (Exception e) {
                return null;
            }
        }
        try {
            return Enchantment.getByName(key);
        } catch (Exception e) {
            return null;
        }
    }

    private String normalize(String key) {
        return key.toUpperCase().replace("-", "_").replace(" ", "_");
    }

    public boolean isApplicable(final Enchantment enchant, final Material material) {
        if (enchant == null || material == null) {
            return false;
        }
        try {
            return enchant.canEnchantItem(new org.bukkit.inventory.ItemStack(material));
        } catch (Exception e) {
            return false;
        }
    }

    public Map<Enchantment, Integer> resolveAll(final Map<String, Object> enchantSpecs) {
        final Map<Enchantment, Integer> result = new HashMap<>();
        if (enchantSpecs == null || enchantSpecs.isEmpty()) {
            return result;
        }
        for (final Map.Entry<String, Object> entry : enchantSpecs.entrySet()) {
            resolve(entry.getKey()).ifPresent(enchant -> {
                int level = resolveLevel(entry.getValue());
                if (level > 0) {
                    result.put(enchant, level);
                }
            });
        }
        return result;
    }

    public int resolveLevel(final Object levelSpec) {
        if (levelSpec == null) {
            return 1;
        }
        if (levelSpec instanceof Number) {
            return ((Number) levelSpec).intValue();
        }
        try {
            return Integer.parseInt(levelSpec.toString().trim());
        } catch (NumberFormatException e) {
            return 1;
        }
    }

    public int clampLevel(final int level, final int maxLevel) {
        if (level <= 0) {
            return 1;
        }
        if (maxLevel <= 0) {
            return level;
        }
        return Math.min(level, maxLevel);
    }

    public Optional<Map<Enchantment, Integer>> getEnchantsFromMeta(final ItemMeta meta) {
        if (meta == null) {
            return Optional.empty();
        }
        try {
            return Optional.ofNullable(meta.getEnchants());
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    public boolean applyToMeta(final ItemMeta meta, final Map<Enchantment, Integer> enchants) {
        if (meta == null || enchants == null || enchants.isEmpty()) {
            return false;
        }
        try {
            for (final Map.Entry<Enchantment, Integer> entry : enchants.entrySet()) {
                meta.addEnchant(entry.getKey(), entry.getValue(), false);
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public boolean removeFromMeta(final ItemMeta meta, final Enchantment enchant) {
        if (meta == null || enchant == null) {
            return false;
        }
        try {
            return meta.removeEnchant(enchant);
        } catch (Exception e) {
            return false;
        }
    }

    public void clearFromMeta(final ItemMeta meta) {
        if (meta == null) {
            return;
        }
        try {
            for (final Enchantment enchant : meta.getEnchants().keySet()) {
                meta.removeEnchant(enchant);
            }
        } catch (Exception e) {
        }
    }

    public void clearCache() {
        NAME_CACHE.clear();
    }

    public boolean isCursed(final Enchantment enchant) {
        if (enchant == null) {
            return false;
        }
        String name = enchant.getName();
        return "VANISHING_CURSE".equals(name) || "BINDING_CURSE".equals(name);
    }
}
