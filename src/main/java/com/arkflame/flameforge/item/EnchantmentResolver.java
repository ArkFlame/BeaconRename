package com.arkflame.flameforge.item;

import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class EnchantmentResolver {
    private static final EnchantmentResolver INSTANCE = new EnchantmentResolver();
    private static final Map<String, Enchantment> ALIASES = new ConcurrentHashMap<>();
    private static final Map<String, Enchantment> NAME_CACHE = new ConcurrentHashMap<>();

    static {
        ALIASES.put("sharpness", Enchantment.DAMAGE_ALL);
        ALIASES.put("sharp", Enchantment.DAMAGE_ALL);
        ALIASES.put("power", Enchantment.ARROW_DAMAGE);
        ALIASES.put("fireaspect", Enchantment.FIRE_ASPECT);
        ALIASES.put("fire_aspect", Enchantment.FIRE_ASPECT);
        ALIASES.put("knockback", Enchantment.KNOCKBACK);
        ALIASES.put("kb", Enchantment.KNOCKBACK);
        ALIASES.put("protection", Enchantment.PROTECTION_ENVIRONMENTAL);
        ALIASES.put("prot", Enchantment.PROTECTION_ENVIRONMENTAL);
        ALIASES.put("fire_protection", Enchantment.PROTECTION_FIRE);
        ALIASES.put("fire_prot", Enchantment.PROTECTION_FIRE);
        ALIASES.put("feather_falling", Enchantment.PROTECTION_FALL);
        ALIASES.put("feather_fall", Enchantment.PROTECTION_FALL);
        ALIASES.put("blast_protection", Enchantment.PROTECTION_EXPLOSIONS);
        ALIASES.put("blast_prot", Enchantment.PROTECTION_EXPLOSIONS);
        ALIASES.put("projectile_protection", Enchantment.PROTECTION_PROJECTILE);
        ALIASES.put("projectile_prot", Enchantment.PROTECTION_PROJECTILE);
        ALIASES.put("respiration", Enchantment.OXYGEN);
        ALIASES.put("breathing", Enchantment.OXYGEN);
        ALIASES.put("aqua_affinity", Enchantment.WATER_WORKER);
        ALIASES.put("aqua_infinity", Enchantment.ARROW_INFINITE);
        ALIASES.put("infinity", Enchantment.ARROW_INFINITE);
        ALIASES.put("efficiency", Enchantment.DIG_SPEED);
        ALIASES.put("eff", Enchantment.DIG_SPEED);
        ALIASES.put("unbreaking", Enchantment.DURABILITY);
        ALIASES.put("unbr", Enchantment.DURABILITY);
        ALIASES.put("fortune", Enchantment.LOOT_BONUS_BLOCKS);
        ALIASES.put("loot_bonus_blocks", Enchantment.LOOT_BONUS_BLOCKS);
        ALIASES.put("silk_touch", Enchantment.SILK_TOUCH);
        ALIASES.put("thorns", Enchantment.THORNS);
        ALIASES.put("lure", Enchantment.LURE);
        ALIASES.put("luck", Enchantment.LUCK);
        ALIASES.put("luck_of_the_sea", Enchantment.LUCK);
        ALIASES.put("lucks", Enchantment.LUCK);
        ALIASES.put("impaling", Enchantment.ARROW_DAMAGE);
    }

    private EnchantmentResolver() {
    }

    public static EnchantmentResolver getInstance() {
        return INSTANCE;
    }

    public Optional<Enchantment> resolve(final String key) {
        if (key == null || key.isEmpty()) {
            return Optional.empty();
        }
        final String normalized = key.toLowerCase().replace(" ", "_").replace("-", "_");
        if (NAME_CACHE.containsKey(normalized)) {
            return Optional.ofNullable(NAME_CACHE.get(normalized));
        }
        Enchantment enchant = resolveEnchantment(normalized);
        NAME_CACHE.put(normalized, enchant);
        return Optional.ofNullable(enchant);
    }

    private Enchantment resolveEnchantment(final String key) {
        Enchantment alias = ALIASES.get(key);
        if (alias != null) {
            return alias;
        }
        try {
            return Enchantment.getByName(key.toUpperCase());
        } catch (Exception e) {
            return null;
        }
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
}
